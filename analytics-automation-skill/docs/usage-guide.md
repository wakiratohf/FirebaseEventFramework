# Usage guide — Cho member triển khai analytics hằng ngày

Mục tiêu: dev không cần học toàn bộ skill — chỉ cần biết 4 slash command, viết spec YAML và đọc audit-report.

## Tình huống thường gặp

### 1. Tôi vừa join project, muốn hiểu analytics đang dùng gì

Đọc 2 file:
- `docs/analytics/event-spec.yaml` — danh sách screen / button / custom event
- `docs/analytics/IMPLEMENTATION.md` — note setup ban đầu

Không cần đọc source `firebase-events/` (module SDK).

### 2. Project mới — setup analytics từ đầu

**Cách KHUYẾN NGHỊ (fully automated, không cần viết YAML, ~30 giây):**

```bash
# Mở Claude Code ở root project
claude
> /analytics-scaffold
```

Claude scan code → auto-decide mọi thứ → sinh `docs/analytics/event-spec.yaml` + `docs/analytics/spec-review.md`. KHÔNG hỏi user gì trừ khi gặp ambiguous case (rất hiếm, thường 0 cái với project điển hình).

Output mẫu:
```
✓ Đã sinh spec tại docs/analytics/event-spec.yaml

📊 Summary:
   11 screens, 2 popups
   23 buttons tracked, 5 skipped (toolbar), 0 cần hỏi
   2 custom events từ logEvent()
   Features: IAP, ADS, RATE_DIALOG, ONBOARDING
   Permissions: POST_NOTIFICATION, LOCATION, ALARM

Bước kế: /analytics-setup
```

Sau đó:
```
> /analytics-setup       # sinh code Kotlin + patch Application.kt
> /analytics-audit       # quét điểm log thiếu
```

Auto-decisions bao gồm:
- **Screens**: tất cả Activity + Fragment (auto-tracked)
- **Popups**: tất cả Dialog + BottomSheet
- **Buttons**: `btn_*` + action keyword → track; toolbar/nav → skip
- **Features**: auto-detect từ imports (BillingClient → IAP, AdMob → ADS, RateUsDialog → RATE_DIALOG…)
- **Permissions**: auto-detect từ AndroidManifest, map sang `AllowPermission` enum
- **Custom events**: từ `FirebaseAnalytics.logEvent()` call sites, infer params + types

**Xem `docs/analytics/spec-review.md`** để biết Claude đã decide gì → edit YAML nếu cần.

**Cách cũ (manual, nếu muốn kiểm soát hoàn toàn):**

```bash
cp templates/event-spec.template.yaml docs/analytics/event-spec.yaml
# Tự gõ YAML
> /analytics-setup
```

### 3. Tôi cần thêm 1 màn hình mới (vd: ProfileActivity)

1. Tạo class Kotlin như bình thường:
   ```kotlin
   class ProfileActivity : BaseTrackedActivity() {
       override fun screenName() = ScreenName.PROFILE
   }
   ```
   (compile sẽ fail vì `ScreenName.PROFILE` chưa có)

2. Bổ sung vào `docs/analytics/event-spec.yaml`:
   ```yaml
   screens:
     # ... existing ...
     - id: profile
       class: ProfileActivity
   ```

3. Regen:
   ```bash
   > /analytics-generate
   ```
   → `ScreenName.kt` thêm dòng `const val PROFILE = "profile"` → compile pass.

### 4. Tôi cần thêm 1 nút trackable

1. Tạo nút trong XML/Compose như bình thường.

2. Bổ sung vào spec:
   ```yaml
   buttons:
     # ... existing ...
     - screen: profile
       id: btn_logout
       name: LogoutButton
   ```

3. Regen:
   ```bash
   > /analytics-generate
   ```
   → `ButtonName.BTN_LOGOUT` được sinh.

4. Gắn log trong code:
   ```kotlin
   binding.btnLogout.setOnClickListener {
       AnalyticsEventsUtils.logClickBtn(ScreenName.PROFILE, ButtonName.BTN_LOGOUT)
       logout()
   }
   ```

5. Audit để chắc:
   ```bash
   > /analytics-audit
   ```

### 5. Tôi cần thêm 1 custom event đặc thù dự án

Ví dụ: `profile_avatar_changed_ev`.

1. Bổ sung vào spec:
   ```yaml
   custom_events:
     # ... existing ...
     - name: profile_avatar_changed_ev
       description: "User đổi avatar"
       params:
         - { name: source, type: string, enum: [camera, gallery, default] }
         - { name: file_size_kb, type: long }
       trigger_hint:
         file: ProfileViewModel.kt
         method: onAvatarUpdated
   ```

2. Regen:
   ```bash
   > /analytics-generate
   ```
   → sinh `ProfileAvatarChangedEv.kt` (class) + `logProfileAvatarChanged()` (helper trong `AnalyticsEventsUtils`).

3. Gọi ở `ProfileViewModel.onAvatarUpdated`:
   ```kotlin
   AnalyticsEventsUtils.logProfileAvatarChanged(
       source = ProfileAvatarChangedEv.SOURCE_CAMERA,  // const từ companion object
       fileSizeKb = 120L,
   )
   ```

### 6. Quên gắn tracking ở đâu đó — làm sao biết?

```bash
> /analytics-audit
```

Đọc `docs/analytics/audit-report.md`. Mỗi issue có:
- File + line cụ thể
- Severity: HIGH/MEDIUM/LOW
- Suggestion: cách fix

Nếu là auto-fixable → chạy:
```bash
> /analytics-generate --apply-missing
```
Claude sẽ sửa từng chỗ, hỏi confirm.

### 7. QC bảo cần checklist test analytics

```bash
> /analytics-verify
```

Sinh `docs/analytics/test-checklist.md` với mọi item cần verify. QC tick từng cái trong khi test.

Bonus — auto tick từ Logcat:
```bash
adb shell setprop debug.firebase.analytics.app com.example.app
adb logcat -s AnalyticsEvents:D | python .claude/skills/firebase-events-impl/scripts/verify_events.py \
  --mode=watch --checklist docs/analytics/test-checklist.md --timeout 600
```
Mỗi event fire đúng → `[x]` tự động vào checklist. Sau 10 phút inactive (hoặc Ctrl+C) → in báo cáo MISSING.

### 8. PM update spec, conflict merge — fix sao?

Nếu chỉ thay đổi YAML → resolve YAML như mọi merge bình thường, rồi:
```bash
> /analytics-generate
```
Claude regen các Kotlin file để khớp YAML mới. KHÔNG resolve Kotlin generated bằng tay — luôn regen.

### 9. Tôi sửa tay file `ScreenName.kt` rồi commit — sao nó bị overwrite?

Vì có header `// AUTO-GENERATED ... DO NOT EDIT`. Mỗi lần `/analytics-generate` chạy là overwrite.

Đúng cách:
- **Không** sửa file generated.
- Nếu cần thêm const → bổ sung vào `event-spec.yaml` → regen.
- Nếu cần logic Kotlin custom → tạo file mới (vd `ScreenNameExt.kt`).

Audit phát hiện drift sẽ warn HIGH severity (`P7`).

### 10. Tôi không dùng Claude Code — chạy script Python thủ công được không?

Được. Script chạy độc lập:
```bash
# Validate
python .claude/skills/firebase-events-impl/scripts/validate_spec.py docs/analytics/event-spec.yaml

# Generate
python .claude/skills/firebase-events-impl/scripts/generate_kotlin.py \
    --spec docs/analytics/event-spec.yaml \
    --src app/src/main/java \
    --templates .claude/skills/firebase-events-impl/assets/kotlin_templates \
    --with-base-activity --with-helpers

# Audit
python .claude/skills/firebase-events-impl/scripts/audit_project.py \
    --spec docs/analytics/event-spec.yaml --src app/src/main \
    --out docs/analytics/audit-report.md \
    --fixes-out docs/analytics/.audit-fixes.json

# Verify checklist
python .claude/skills/firebase-events-impl/scripts/verify_events.py \
    --mode=generate --spec docs/analytics/event-spec.yaml \
    --out docs/analytics/test-checklist.md
```

Khác biệt: không có "hỏi xác nhận từng file" — script áp dụng thẳng. Phù hợp cho CI.

## Cheat sheet 1 trang

| Tôi muốn… | Bước |
|---|---|
| Setup project mới (không viết YAML) | `/analytics-scaffold` → `/analytics-setup` |
| Setup project mới (viết YAML tự) | Edit `event-spec.yaml` → `/analytics-setup` |
| Thêm screen | Edit spec → `/analytics-generate` → extend `BaseTrackedActivity` |
| Thêm button | Edit spec → `/analytics-generate` → gắn `logClickBtn` |
| Thêm custom event | Edit spec → `/analytics-generate` → gọi `AnalyticsEventsUtils.logXxx` |
| Tìm chỗ tracking thiếu | `/analytics-audit` |
| Sửa các chỗ thiếu | `/analytics-generate --apply-missing` |
| QC checklist | `/analytics-verify` |
| Verify trên Firebase | `adb shell setprop debug.firebase.analytics.app <appId>` |

## Module API reference (dùng khi sửa wrapper)

Xem trực tiếp trong source module:
- `firebase-events/docs/EVENT_CATALOG.md` — schema 13 event chuẩn
- `firebase-events/docs/CONTEXT.md` — naming convention
- `firebase-events/docs/PROJECT_EVENT_TEMPLATE.md` — pattern wrapper

Hoặc đọc `.claude/skills/firebase-events-impl/references/event-catalog.md` (tóm tắt cho Claude).
