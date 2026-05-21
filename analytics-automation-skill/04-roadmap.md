# Roadmap triển khai — 2 tuần

Vì module `:firebase-events` v1.0.0 đã hoàn thành, roadmap rút từ 4 tuần xuống **2 tuần**.

## Tuần 1 — Build Claude Code skill + slash commands

**Owner:** 1 dev (Android hoặc backend, biết Python + Kotlin). Có thể là leader tự làm.

| Ngày | Việc | Định nghĩa hoàn thành |
|---|---|---|
| D1 | Hoàn thiện `SKILL.md` + `validate_spec.py` | Validator chạy được trên spec mẫu, throw lỗi đúng cho 5 case sai cố ý |
| D2 | `generate_kotlin.py` + Jinja templates (5 file) | Sinh được `ScreenName/ButtonName/PopupName/AnalyticsEventsUtils/CustomEvent` từ spec mẫu |
| D3 | 4 slash command `.md` + integration với scripts | Chạy được `/analytics-setup` end-to-end trên dự án test |
| D4 | `audit_project.py` (Kotlin AST scanning) | Detect được Activity chưa extends `BaseTrackedActivity`, button chưa log |
| D5 | `verify_events.py` + checklist generator | Sinh checklist markdown đúng từ spec mẫu |

**Deliverable:** Skill chạy hoàn chỉnh cho **1 spec mẫu** (Weather example) end-to-end.

## Tuần 2 — Pilot + onboarding

| Ngày | Việc | Định nghĩa hoàn thành |
|---|---|---|
| D1 | Migrate Weather sang skill: viết `event-spec.weather.yaml` từ code hiện có, chạy `/analytics-generate`, so sánh với code tay | Code generated khớp ≥95% với code tay đã viết |
| D2 | Pilot 1 dự án mới nhỏ (VPN-Lite). Đo thời gian setup | Setup xong analytics < 1 ngày (vs baseline 2-3 ngày) |
| D3 | Workshop nội bộ 2h: dạy team dùng skill | ≥80% team join, có cheat sheet 1 trang |
| D4 | Quay video demo 10 phút (record màn hình chạy `/analytics-setup` → `/audit` → `/verify`) | Video upload nội bộ |
| D5 | Viết FAQ + `usage-guide.md` cuối cùng dựa trên feedback workshop | Doc commit, lock skill v1.0.0 |

**Deliverable:** Skill v1.0.0 ready cho mọi dự án mới của team.

## Sau 2 tuần — Vận hành

| Định kỳ | Việc |
|---|---|
| Mỗi tuần | Leader review `audit-report.md` của các dự án đang chạy |
| Mỗi tháng | Bump version skill nếu có template mới / pattern mới phát hiện được |
| Mỗi quý | Cập nhật `references/event-catalog.md` khi module `:firebase-events` thêm event built-in |
| Mỗi release Firebase đổi limit | Cập nhật `validate_spec.py` 1 chỗ |

## Tổ chức repo

**Lựa chọn:** skill nằm ở repo nào?

- **Option A — Trong repo `firebase-events` (đi kèm module):** `firebase-events/.claude-skill/` rồi mỗi project copy sang. Pro: 1 source. Con: project bị bloat thêm Python.
- **Option B — Repo riêng `firebase-events-tooling`:** project clone về `.claude/`. Pro: tách dependency. Con: thêm 1 repo phải maintain.
- **Option C — Submodule git:** mỗi project add submodule. Pro: version explicit. Con: dev hay quên `git submodule update`.

**Khuyến nghị:** **Option A** ngay từ đầu. Module `firebase-events` đã là single source of truth cho runtime, để chung tooling là tự nhiên. Mỗi project khi setup chỉ cần:
```bash
cp -R firebase-events/.claude-skill .claude
```

Hoặc nâng cấp lên Option B sau khi team quen.

## Rủi ro & cách giảm

| Rủi ro | Mức | Mitigation |
|---|---|---|
| Member không quen Claude Code | Trung bình | Mỗi script Python chạy được độc lập (không cần Claude Code). Member có thể dùng `python scripts/generate_kotlin.py` thay slash command. |
| Skill miss case đặc biệt (ViewModel, Compose, multi-module) | Cao | v1.0.0 chỉ cover XML View + AppCompatActivity/Fragment. Compose/multi-module → roadmap v1.1+. Audit script có flag `--ignore` để skip file/folder. |
| Spec YAML trở thành "tài liệu chết" không ai update | Cao | CI fail nếu audit phát hiện custom_event trong spec chưa có call site trong code → buộc update song song. |
| Firebase đổi quy định limit | Trung bình | Sửa `validate_spec.py` + `EventNameValidator.kt` 1 lần, regen toàn bộ project. |
| Code generated bị merge conflict | Trung bình | Header `AUTO-GENERATED` + script `generate --force` dễ resolve. Khuyến nghị: file generated ở folder riêng, không trộn với code tay. |

## KPI đo lường (đo 3 tháng sau go-live)

| Metric | Baseline | Target |
|---|---|---|
| Time-to-first-event dự án mới | 2–3 ngày | < 4 giờ |
| % event sai naming convention bị reject ở review | 15–30% | < 3% |
| Số bug analytics phát hiện sau release | 5–10 / app | ≤ 2 / app |
| Số dòng code logging tay / app | ~800 LOC | < 200 LOC |
| Thời gian QC verify analytics | 1–2 ngày | < 4 giờ |
| Số lần phải hỏi leader về convention | 5–10 / dự án | 0–1 / dự án |
