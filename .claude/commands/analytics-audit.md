---
description: Scan codebase tìm chỗ thiếu / sai analytics tracking. Sinh `audit-report.md`.
---

Chạy `audit_project.py` để phát hiện P1-P14 patterns (xem `references/audit-patterns.md`).

## Quy trình

1. Detect spec path. Default: `docs/analytics/event-spec.yaml`.
2. Detect src path. Default: `app/src/main` (chú ý: `main`, không phải `main/java` — audit scan cả `kotlin/` nếu có).
3. Chạy:
   ```bash
   python .claude/skills/firebase-events-impl/scripts/audit_project.py \
       --spec <SPEC> \
       --src <SRC> \
       --out docs/analytics/audit-report.md
   ```
4. Đọc report file vừa sinh, summarize cho user theo severity:
   - **HIGH**: P1 (Activity miss base), P2 (Fragment miss lifecycle), P6 (file generated drift)
   - **MEDIUM**: P3 (click miss log), P4 (popup miss log), P5 (custom event miss call site), P11-P14
   - **LOW**: P7 (screen có code, không trong spec), P13 (hoisted button info)
5. Đề xuất fix theo thứ tự ưu tiên (HIGH trước).

## Tùy chọn

### `--emit-fixes`
Sinh thêm `.audit-fixes.json` chứa structured fixes. Sau đó `/analytics-generate --apply-missing` để áp dụng:
```bash
python .claude/skills/firebase-events-impl/scripts/audit_project.py \
    --spec <SPEC> --src <SRC> \
    --out docs/analytics/audit-report.md \
    --emit-fixes
/analytics-generate --apply-missing
```

### `--fail-on=high`
Cho CI mode — exit code 1 nếu có HIGH issue:
```bash
python .claude/skills/firebase-events-impl/scripts/audit_project.py \
    --spec docs/analytics/event-spec.yaml \
    --src app/src/main \
    --out audit-report.md \
    --fail-on=high
```

## Đọc report

Report format:
```markdown
## HIGH — 1

### [P1] `path/HomeActivity.kt:12`
Activity `HomeActivity` does not extend BaseTrackedActivity

**Fix:** Change `class HomeActivity : AppCompatActivity()` to
`class HomeActivity : BaseTrackedActivity()` and override
`getScreenName() = ScreenName.HOME`.
```

Mỗi issue có: ID pattern (`Pxx`), location (`file:line`), description ngắn, fix suggestion cụ thể.

## Bypass

Một số file user muốn skip hoàn toàn:
- Class declaration có comment `// @no-analytics` trên cùng dòng → skip mọi pattern.
- Lambda body có `// @no-click-track` ở dòng đầu → skip P3/P11/P12 trên lambda đó.
- Screen ID có trong `ignore_screens: [...]` của spec → skip P11–P14 trên file đó.

False positive khác → báo cáo (paste vào chat) để skill maintainer fix regex.
