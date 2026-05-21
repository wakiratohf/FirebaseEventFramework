---
description: Tái sinh code Kotlin khi `event-spec.yaml` thay đổi. Show diff trước khi ghi.
---

Chạy mỗi khi spec đổi (thêm/sửa screen, button, popup, custom event, hoặc bump features).

## Quy trình

1. Phát hiện spec path. Auto-detect: `docs/analytics/event-spec.yaml`. Hỏi nếu khác.
2. Validate spec trước:
   ```bash
   python .claude/skills/firebase-events-impl/scripts/validate_spec.py <SPEC>
   ```
   Fail → in lỗi, không chạy generate.
3. Dry-run trước:
   ```bash
   python .claude/skills/firebase-events-impl/scripts/generate_kotlin.py \
       --spec <SPEC> \
       --src app/src/main/java \
       --templates .claude/skills/firebase-events-impl/assets/kotlin_templates \
       --dry-run
   ```
4. Show plan cho user: `+` create, `~` update, `·` unchanged, `!` drift-overwrite (file generated bị sửa tay).
5. **Cảnh báo nếu có `!` drift-overwrite** — hỏi user xác nhận trước khi overwrite mất data tay.
6. Nếu OK → chạy không dry-run:
   ```bash
   python .claude/skills/firebase-events-impl/scripts/generate_kotlin.py \
       --spec <SPEC> \
       --src app/src/main/java \
       --templates .claude/skills/firebase-events-impl/assets/kotlin_templates \
       --no-confirm
   ```

## Flags hữu ích

- `--apply-missing`: đọc `.audit-fixes.json` (sinh bởi `audit_project.py --emit-fixes`) và apply auto-fix (vd inject `extends BaseTrackedActivity` vào Activity đang miss).
- `--with-helpers`: luôn emit cả `BaseTrackedActivity`/`BaseTrackedFragment` và `TrackedComposables` bất kể mode (cho project muốn flexibility).

## Idempotency

Chạy 2 lần liên tiếp với cùng spec → 0 thay đổi (`✓ All files up-to-date`). Nếu thấy update không mong muốn → có thể:
- Module skill mới bump version → template thay đổi
- Spec đang được edit dở (test save?)
- File generated bị sửa tay (drift) → audit P6 sẽ flag
