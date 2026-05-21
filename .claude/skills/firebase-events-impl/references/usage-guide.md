# Usage Guide — Cheat Sheet

Tài liệu thực dụng cho dev/PM/QC dùng skill `firebase-events-impl` hàng ngày.

## Install skill vào project Android

```bash
# Từ project root
mkdir -p .claude/skills
unzip ~/Downloads/firebase-events-impl.zip -d .claude/skills/

# Install Python deps
pip install -r .claude/skills/firebase-events-impl/scripts/requirements.txt
```

Cấu trúc sau install:
```
your-android-project/
├── app/
├── firebase-events/                 ← module runtime, đã có sẵn
└── .claude/
    └── skills/
        └── firebase-events-impl/
            ├── SKILL.md
            ├── scripts/
            ├── assets/
            ├── templates/
            ├── references/
            └── commands/
```

## Workflow theo role

### Lead / PM — quản spec

1. **Tạo lần đầu:**
   ```bash
   cp .claude/skills/firebase-events-impl/templates/event-spec.template.yaml \
      docs/analytics/event-spec.yaml
   # → Sửa project.name, package, application_class, mode, features
   ```
   Hoặc dùng scaffold để skill tự scan:
   ```bash
   /analytics-scaffold
   ```

2. **Mỗi sprint:** review PR thay đổi `event-spec.yaml` như review code. Mỗi screen / button mới ⇒ phải có entry.

3. **Trước release:** đảm bảo CI pass `audit --fail-on=high`.

### Dev — implement code

1. Pull spec mới về:
   ```bash
   git pull
   /analytics-generate          # → sinh code Kotlin mới
   ```

2. Wiring lần đầu trong Application:
   ```kotlin
   class App : Application() {
       override fun onCreate() {
           super.onCreate()
           AnalyticsModule.init(this)
           // ... other init
       }
   }
   ```

3. Activity:
   ```kotlin
   class HomeActivity : BaseTrackedActivity() {
       override fun getScreenName() = ScreenName.HOME
       // ... onCreate, etc.
   }
   ```

4. Mỗi click:
   ```kotlin
   binding.btnConnect.setOnClickListener {
       logClickBtn(ClickBtnEvCatalog.BTN_CONNECT)
       viewModel.connect()
   }
   ```

5. Trước commit:
   ```bash
   /analytics-audit
   ```

### QC — verify tracking

1. Lấy checklist:
   ```bash
   /analytics-verify
   # → tạo docs/analytics/test-checklist.md
   ```

2. Bật DebugView trên thiết bị test:
   ```bash
   adb shell setprop debug.firebase.analytics.app com.example.vpnlite
   ```

3. (Optional) Auto-tick mode:
   ```bash
   adb logcat -s AnalyticsEvents:D | \
       python .claude/skills/firebase-events-impl/scripts/verify_events.py \
           --spec docs/analytics/event-spec.yaml \
           --mode=watch \
           --timeout=300
   ```
   Tester click theo checklist; script auto-tick item nào fire đúng, exit code 1 nếu có item miss.

## Quick reference — 5 slash command

| Command | Khi nào | Output |
|---|---|---|
| `/analytics-scaffold` | Project chưa có spec | `docs/analytics/event-spec.yaml` (draft) + `spec-review.md` |
| `/analytics-setup` | Lần đầu, đã có spec | Toàn bộ Kotlin files + hướng dẫn wiring |
| `/analytics-generate` | Mỗi lần spec đổi | Diff + update Kotlin files |
| `/analytics-audit` | Trước commit / trong CI | `docs/analytics/audit-report.md` |
| `/analytics-verify` | Bắt đầu sprint QC | `docs/analytics/test-checklist.md` |

## Cheat — common operation

### "Tôi muốn thêm 1 screen mới"

1. Sửa `event-spec.yaml`:
   ```yaml
   screens:
     - id: profile
       class: ProfileActivity
   ```
2. `/analytics-generate` → confirm diff → apply
3. Tạo `ProfileActivity.kt`:
   ```kotlin
   class ProfileActivity : BaseTrackedActivity() {
       override fun getScreenName() = ScreenName.PROFILE
   }
   ```
4. `/analytics-audit` → verify clean

### "Tôi muốn thêm button"

1. Sửa `event-spec.yaml`:
   ```yaml
   buttons:
     - screen: profile
       id: btn_logout
       name: LogoutButton
   ```
2. `/analytics-generate`
3. Trong code:
   ```kotlin
   binding.btnLogout.setOnClickListener {
       logClickBtn(ClickBtnEvCatalog.BTN_LOGOUT)
       authViewModel.logout()
   }
   ```

### "Tôi muốn custom event"

1. Sửa spec:
   ```yaml
   custom_events:
     - name: profile_avatar_changed_ev
       params:
         - { name: source, type: string, enum: [camera, gallery] }
   ```
2. `/analytics-generate` → sinh `ProfileAvatarChangedEv.kt`
3. Call site:
   ```kotlin
   AnalyticsEventsUtils.logProjectEvent(
       ProfileAvatarChangedEv(source = ProfileAvatarChangedEv.SOURCE_CAMERA)
   )
   ```

### "Tôi quên gắn log ở Activity X"

```bash
/analytics-audit
# Đọc report → find P1 entry cho X → áp dụng fix gợi ý
```

Hoặc auto-fix:
```bash
python .claude/skills/firebase-events-impl/scripts/audit_project.py \
    --spec docs/analytics/event-spec.yaml \
    --src app/src/main \
    --emit-fixes
# → .audit-fixes.json

/analytics-generate --apply-missing
```

### "Spec hash khác trên CI"

Có người sửa tay file generated. Audit P6 sẽ phát hiện. Fix:
```bash
git status                    # tìm file Kotlin trong event/ bị modify
git diff <file>               # xem họ sửa gì
# Quyết định:
#   - Nếu đáng giữ → đưa logic vào AnalyticsEventsUtilsExt.kt (file riêng)
#   - Nếu không → /analytics-generate sẽ overwrite về spec
```

## Common errors

### `ERROR: spec validation failed`

→ Đọc lỗi từ `validate_spec.py`. Thường là:
- Tên không snake_case
- Tên dài quá Firebase limit
- FK không hợp lệ (button.screen không có trong screens[])

### `KotlinCompiler: Unresolved reference: ScreenName`

→ Chưa chạy generate. Hoặc package trong spec sai. Kiểm tra:
```bash
ls app/src/main/java/<your/package>/event/
```

### `Lint: ClickBtnEvUnderscore`

→ `button.name` chứa `_`. Fix trong spec, regenerate. KHÔNG sửa file generated.

### `Empty screen_view_ev` trên DebugView

→ Activity quên extends `BaseTrackedActivity`. Hoặc Fragment quên extends `BaseTrackedFragment`. Hoặc Composable quên wrap `TrackedScreen`.

## CI/CD integration

`.github/workflows/analytics.yml`:
```yaml
name: Analytics check

on:
  pull_request:
    paths:
      - 'docs/analytics/**'
      - 'app/src/**'
      - '.claude/skills/firebase-events-impl/**'

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.11' }
      - run: pip install -r .claude/skills/firebase-events-impl/scripts/requirements.txt
      - name: Validate spec
        run: |
          python .claude/skills/firebase-events-impl/scripts/validate_spec.py \
              docs/analytics/event-spec.yaml
      - name: Check generated up-to-date
        run: |
          python .claude/skills/firebase-events-impl/scripts/generate_kotlin.py \
              --spec docs/analytics/event-spec.yaml \
              --src app/src/main/java \
              --templates .claude/skills/firebase-events-impl/assets/kotlin_templates \
              --dry-run --no-confirm
          # Fails with exit 0 but prints diff. Compare with:
          git diff --exit-code app/src/main/java
      - name: Audit (block on HIGH)
        run: |
          python .claude/skills/firebase-events-impl/scripts/audit_project.py \
              --spec docs/analytics/event-spec.yaml \
              --src app/src/main \
              --out audit-report.md \
              --fail-on=high
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: audit-report
          path: audit-report.md
```

## FAQ ngắn

**Q: Tôi có nhiều product flavor, mỗi flavor cần spec riêng?**
A: Tạo nhiều file: `event-spec.free.yaml`, `event-spec.pro.yaml`. Generate vào 2 src dir khác nhau. Hoặc gộp vào 1 spec, dùng `ignore_screens` trên screen flavor-specific.

**Q: Tôi muốn version spec — kế hoạch?**
A: Bump `project.version` trong spec; commit kèm code thay đổi. Audit không enforce version, chỉ làm context.

**Q: Có thể opt-out tracking trong screen test/debug?**
A: 3 cách:
- Thêm `ignore_screens: [debug_screen]` vào spec
- Comment `// @no-analytics` ở class declaration
- Override `shouldTrackScreen(): Boolean = BuildConfig.DEBUG.not()`

**Q: Skill có support React Native / Flutter không?**
A: Chưa. v1 chỉ cho Kotlin Android. Roadmap có thể mở rộng.

**Q: Có chạy được trong Claude Code không?**
A: Có — 5 slash command (`/analytics-*`) là Claude Code commands. Cũng chạy được tay qua `python ...` (xem CI section).
