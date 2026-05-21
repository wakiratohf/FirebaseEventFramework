# Giải pháp Log Events — Tự động hoá đa dự án

## 1. Bối cảnh

Team đã có **`:firebase-events` v1.0.0** — module Android library hoàn chỉnh:
- 13 event chuẩn (screen view, click, ad, IAP, lifecycle…)
- 9 user property (language, country, tier, permission…)
- Pluggable provider (Firebase Analytics + AppsFlyer + custom webhook)
- Test-log mode (Telegram + Webhook) cho QA realtime
- Soft validator naming convention
- Doc đầy đủ: README, INTEGRATION, EVENT_CATALOG, CONFIGURATION, MIGRATION, SAMPLE_APP, CONTEXT, DEMO_IMPLEMENTATION_GUIDE, PROJECT_EVENT_TEMPLATE

**Phần thiếu để giải bài toán "scale lên nhiều dự án":** tự động hoá **lớp triển khai** cho từng project, thay vì copy-paste thủ công.

## 2. Hai lớp giải pháp

```
┌──────────────────────────────────────────────────────────────┐
│ LỚP 1 — RUNTIME (đã có: firebase-events v1.0.0)              │
│   Module Android library, copy vào project mới, dùng AAR/Maven│
│   Không sửa khi triển khai dự án mới                          │
└──────────────────────────────────────────────────────────────┘
                              ▲
                              │ depends on
                              │
┌──────────────────────────────────────────────────────────────┐
│ LỚP 2 — DESIGN-TIME (CẦN LÀM: Claude Code skill)             │
│   /analytics-setup, /analytics-generate, /analytics-audit,    │
│   /analytics-verify                                           │
│   Sinh code project-specific từ event-spec.yaml               │
└──────────────────────────────────────────────────────────────┘
                              ▲
                              │ feeds spec
                              │
┌──────────────────────────────────────────────────────────────┐
│ LỚP 0 — SPEC (mỗi dự án 1 file YAML, PM/Leader maintain)     │
│   event-spec.yaml — screens, buttons, popups, custom events  │
└──────────────────────────────────────────────────────────────┘
```

### Vì sao tách 3 lớp?

- **Lớp 1 (firebase-events)** chứa thứ "không đổi giữa các dự án": API, validator, lifecycle, transport. Module này stable; bump version khi có built-in event mới.
- **Lớp 0 (YAML spec)** chứa thứ "thay đổi theo dự án": màn hình nào, nút nào, custom event nào. PM/Leader maintain.
- **Lớp 2 (Claude Code skill)** = "biên dịch viên": đọc YAML → sinh `ScreenName.kt`, `ButtonName.kt`, `PopupName.kt`, `AnalyticsEventsUtils.kt` + đề xuất chỗ gắn tracking trong Activity/Fragment.

Khi convention thay đổi → sửa template trong skill, regen mọi project. Không phải edit hand 50 nơi.

## 3. Mapping với module firebase-events đã có

Code Claude Code sinh ra phải khớp đúng pattern trong `PROJECT_EVENT_TEMPLATE.md`:

```
app/src/main/java/<package>/event/             ← Claude Code SINH
├── ScreenName.kt                             ← const val HOME = "home"
├── ButtonName.kt                             ← const val BTN_CONNECT = "btn_connect"
├── PopupName.kt                              ← const val RATE_DIALOG = "rate_dialog"
├── AnalyticsEventsUtils.kt                   ← thin wrapper (logScreenStart, logClickBtn…)
└── project_events/                           ← Claude Code SINH (custom events)
    ├── VpnConnectEv.kt                       ← implement AnalyticsEvent interface
    └── ServerSelectedEv.kt

app/src/main/java/<package>/ui/base/          ← Claude Code SINH (nếu chưa có)
└── BaseTrackedActivity.kt                    ← auto log screen view
```

Module `:firebase-events` không bị động.

## 4. Cấu trúc deliverable

```
analytics-automation/
├── 01-overview.md                       ← bạn đang đọc
├── 02-architecture.md                   ← spec YAML ↔ firebase-events API
├── 03-claude-code-workflow.md           ← chi tiết 4 slash command
├── 04-roadmap.md                        ← lộ trình 2 tuần (vì module đã có)
│
├── .claude/                             ← drop vào root mỗi project
│   ├── skills/firebase-events-impl/
│   │   ├── SKILL.md                     ← bộ não của skill
│   │   ├── references/                  ← Claude đọc khi cần ngữ cảnh sâu
│   │   ├── scripts/                     ← Python: validate/generate/audit/verify
│   │   └── assets/kotlin_templates/     ← Jinja templates
│   └── commands/                        ← 4 slash command .md
│
├── templates/
│   ├── event-spec.template.yaml
│   ├── event-spec.weather-example.yaml
│   └── event-spec.vpn-example.yaml
│
└── docs/
    ├── usage-guide.md                   ← cho member dùng hằng ngày
    └── faq.md
```

## 5. Outcome đạt được

| Tiêu chí | Trước | Sau |
|---|---|---|
| Setup module logging cho dự án mới | 2–3 ngày | **15–30 phút** (điền YAML + `/analytics-generate`) |
| Tỷ lệ sai naming, sai param | 15–30% | **<3%** (validator chặn trước khi commit) |
| Discover điểm log thiếu | Code-review thủ công | **Tự động** (`/analytics-audit`) |
| Khi convention đổi | Sửa từng app | **Sửa template trong skill 1 chỗ → regen** |
| QC verify analytics | 1–2 ngày | **<4 giờ** (`/analytics-verify` xuất checklist) |
| Onboard member mới | 2–3 ngày học pattern | **<2 giờ** (chỉ học YAML format) |

## 6. Đọc theo thứ tự nào?

| Vai trò | Lộ trình đọc |
|---|---|
| PM / Leader | `01-overview.md` → `04-roadmap.md` → `templates/event-spec.template.yaml` |
| Senior Dev Android (setup tool) | `02-architecture.md` → `.claude/skills/firebase-events-impl/SKILL.md` → `03-claude-code-workflow.md` |
| Member triển khai dự án | `docs/usage-guide.md` (đủ) |
| QC tester | `docs/usage-guide.md` mục "Verify checklist" |
