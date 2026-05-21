# firebase-events-impl

**Skill cho Claude / Claude Code** — tự động hoá tích hợp Firebase Analytics event tracking cho dự án Android dùng module runtime `:firebase-events`.

Hỗ trợ đồng thời **View Binding**, **findViewById** truyền thống, và **Jetpack Compose** thông qua 3 mode: `view_binding`, `compose`, `hybrid`.

## Quick start

```bash
# 1. Trong project Android root:
mkdir -p .claude/skills
unzip firebase-events-impl.zip -d .claude/skills/

# 2. Install Python deps:
pip install -r .claude/skills/firebase-events-impl/scripts/requirements.txt

# 3. Tạo spec (chọn 1 trong 2):
#    a) Để Claude scan code đề xuất:
/analytics-scaffold

#    b) Hoặc copy template tay:
cp .claude/skills/firebase-events-impl/templates/event-spec.template.yaml \
   docs/analytics/event-spec.yaml
# → sửa project.package, mode, screens, buttons, ...

# 4. Generate Kotlin:
/analytics-setup

# 5. Verify:
/analytics-audit
/analytics-verify
```

## Cấu trúc

```
firebase-events-impl/
├── SKILL.md                    Định nghĩa skill (Claude đọc đầu tiên)
├── README.md                   File này
├── scripts/                    5 Python script — chạy được độc lập với CI
│   ├── validate_spec.py
│   ├── generate_kotlin.py
│   ├── audit_project.py
│   ├── verify_events.py
│   ├── scaffold_spec.py
│   └── requirements.txt
├── assets/
│   └── kotlin_templates/       9 Jinja2 template Kotlin
├── templates/
│   └── event-spec.template.yaml
├── commands/                   5 slash command cho Claude Code
│   ├── analytics-scaffold.md
│   ├── analytics-setup.md
│   ├── analytics-generate.md
│   ├── analytics-audit.md
│   └── analytics-verify.md
└── references/                 Claude đọc on-demand
    ├── event-catalog.md            13 event chuẩn + 9 user property
    ├── naming-conventions.md       Quy tắc đặt tên + Firebase limits
    ├── audit-patterns.md           P1–P14 chi tiết
    ├── compose-guide.md            Tracking trong Jetpack Compose
    ├── view-binding-guide.md       Tracking với ViewBinding/findViewById
    ├── kotlin-templates-guide.md   Map spec → file Kotlin
    └── usage-guide.md              Cheat sheet hàng ngày
```

## Yêu cầu

- **Python 3.11+** với PyYAML ≥6.0, Jinja2 ≥3.1
- Project Android có module runtime `:firebase-events` v1.0.0+
- (Optional) Claude Code CLI để dùng slash command — không bắt buộc, mọi script chạy được bằng `python` thuần.

## 5 slash command

| Command | Khi nào dùng |
|---|---|
| `/analytics-scaffold` | Project chưa có spec — Claude scan code đề xuất draft |
| `/analytics-setup` | Đã có spec, generate code lần đầu |
| `/analytics-generate` | Sửa spec → tái sinh code |
| `/analytics-audit` | Tìm Activity/Fragment/Compose thiếu log |
| `/analytics-verify` | Sinh QC test checklist (+ watch mode auto-tick) |

## License

Apache 2.0 — theo module runtime `firebase-events`.

## Version

v1.0.0 — xem `SKILL.md` frontmatter cho version chính thức.
