---
description: Scan project Android code để tự sinh draft `event-spec.yaml` và `spec-review.md`. Dùng khi project chưa có spec.
---

Chạy `scaffold_spec.py` để scan toàn bộ source Android, tự đề xuất `screens`, `popups`, `buttons` cho `event-spec.yaml`.

## Quy trình

1. Hỏi user (hoặc tự detect) đường dẫn `app/src/main/java` (default: `app/src/main/java`).
2. Hỏi đường dẫn output spec (default: `docs/analytics/event-spec.yaml`).
3. Chạy:
   ```bash
   python .claude/skills/firebase-events-impl/scripts/scaffold_spec.py \
       --src <SRC_PATH> \
       --out <OUTPUT_SPEC> \
       --review-out docs/analytics/spec-review.md
   ```
4. Đọc `spec-review.md` ra cho user, đặc biệt:
   - Mode auto-detect (`view_binding` / `compose` / `hybrid`)
   - Confidence levels (high/medium/low)
   - Low-confidence items cần user review
5. Hướng dẫn next step: `/analytics-setup` sau khi review draft.

## Lưu ý

- Đây là draft — user PHẢI review từng entry trước khi generate code.
- Tên button auto-detect có thể chưa CamelCase chuẩn (vd lấy từ `Text("Connect Now")` → `"ConnectNow"`). Cần đổi tay.
- Custom event và user property KHÔNG được scaffold tự động — phải user tự khai báo.
- Nếu codebase rỗng / không Kotlin file → in lỗi và đề xuất tạo spec từ template.
