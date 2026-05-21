# Workflow chi tiết với Claude Code

## 1. Câu chuyện end-to-end — dự án mới VPN-Lite

```
T+0    Leader Hà tạo project VPN-Lite, include :firebase-events theo INTEGRATION.md
T+0:30 Leader copy thư mục .claude/ vào root project
T+0:30 PM Lan điền docs/analytics/event-spec.yaml (10 screen, 30 button, 3 custom event)
       commit, PR
T+1h   Dev Tuấn pull về, mở terminal:
       $ claude
       > /analytics-setup
       (Claude validate spec, sinh ScreenName/ButtonName/PopupName/AnalyticsEventsUtils,
        gen BaseTrackedActivity, patch Application.onCreate. 12 file.)
T+2h   Dev Tuấn:
       > /analytics-audit
       (Claude quét toàn project, list 18 điểm thiếu tracking, line number cụ thể.)
       > /analytics-generate --apply-missing
       (Claude sửa từng nơi, hỏi xác nhận, Tuấn duyệt từng cái như review PR.)
T+4h   Tuấn commit. CI tự chạy validate_spec.py + audit_project.py --fail-on-missing.
T+1d   QC Hà:
       > /analytics-verify
       (Claude sinh test-checklist.md từ spec. Hà tick từng cái khi test.)
       (Optional) > /analytics-verify --watch-logcat
       (Claude đọc Logcat realtime, auto tick item nào fire đúng.)
```

**Tổng thời gian:** 1 buổi sáng cho dev, 1 buổi chiều cho QC. So với 2-3 ngày trước đây.

## 2. Bốn slash command — vai trò từng cái

| Command | Mục đích | Chạy bao giờ |
|---|---|---|
| `/analytics-setup` | Khởi tạo lần đầu cho dự án mới | 1 lần / dự án |
| `/analytics-generate` | Re-generate khi spec đổi | Mỗi lần PM update YAML |
| `/analytics-audit` | Quét điểm log thiếu trong code | Trước mỗi PR lớn, hoặc trong CI |
| `/analytics-verify` | Sinh checklist + smoke test cho QC | Trước khi handover cho QC |

## 3. `/analytics-setup` — chi tiết

**Tiền điều kiện:**
- File `docs/analytics/event-spec.yaml` tồn tại (template ở `templates/event-spec.template.yaml`)
- Module `:firebase-events` đã `include` trong `settings.gradle.kts`
- `app/build.gradle.kts` đã có `implementation(project(":firebase-events"))`

**Skill làm gì:**
1. **Validate spec** qua `validate_spec.py`:
   - Check naming convention (regex Firebase)
   - Check duplicate id
   - Check foreign-key: mọi `buttons[].screen` phải khớp `screens[].id`
   - Check ngưỡng (Firebase: 40 char, 25 param, 100 char value)
   - Nếu lỗi → dừng, in báo cáo lỗi
2. **Khảo sát project:**
   - Đọc `AndroidManifest.xml` lấy package name + Application class
   - Liệt kê Activity / Fragment hiện có
3. **Sinh code** qua `generate_kotlin.py`:
   - `<package>/event/ScreenName.kt`
   - `<package>/event/ButtonName.kt`
   - `<package>/event/PopupName.kt`
   - `<package>/event/AnalyticsEventsUtils.kt`
   - `<package>/event/project_events/*.kt` cho mỗi custom_event
   - `<package>/ui/base/BaseTrackedActivity.kt` (nếu chưa có)
4. **Patch `Application` class:**
   - Thêm `AnalyticsModule.init(...)` trong `onCreate()` theo DEMO_IMPLEMENTATION_GUIDE.md
   - Thêm `ProcessLifecycleOwner` observer cho session timing
   - Thêm consent restore từ SharedPreferences
5. **Tạo doc:** `docs/analytics/IMPLEMENTATION.md` ghi lại quyết định + checklist
6. **Output:** report ngắn — sinh X file, patch Y dòng, các điểm tiếp theo.

**Không bao giờ làm:** sửa source `firebase-events/`, sửa file có header AUTO-GENERATED.

## 4. `/analytics-generate` — chi tiết

Cùng pipeline với setup nhưng:
- **Không** patch `Application.kt` nếu đã có `AnalyticsModule.init`
- **Diff trước, ghi sau:** show diff của mỗi file generated cho user duyệt
- **Detect breaking change:** nếu spec xoá `screens[].id` đang được dùng → warn "Có thể vỡ Firebase dashboard"

**Tham số:**
- `--dry-run`: chỉ preview, không ghi (cho CI)
- `--apply-missing`: kết hợp với audit, tự sửa các điểm thiếu (hỏi xác nhận từng chỗ)
- `--no-confirm`: skip xác nhận, áp dụng tất cả (chỉ dùng trong CI)
- `--only=screens|buttons|popups|custom_events`: regen từng phần

## 5. `/analytics-audit` — chi tiết

Quét Kotlin/Java files trong `app/src/main/` (và optional `module_xxx/src/main/`):

**Pattern phát hiện** (chi tiết trong `references/audit-patterns.md`):

| Issue | Severity | Auto-fixable |
|---|---|---|
| Activity không extends `BaseTrackedActivity` (và không override `logScreenStart/Stop`) | HIGH | ✅ Yes (đổi parent class) |
| Fragment không gắn `logScreenStart` trong `onResume` | HIGH | ✅ Yes (gắn vào onResume) |
| `setOnClickListener` không có `logClickBtn` lân cận | MEDIUM | ✅ Yes (gắn dòng log) |
| `Dialog.show()` chưa log popup name | LOW | ⚠ Hint only (cần member quyết popup ở screen nào) |
| Spec có custom_event chưa có call site nào trong code | MEDIUM | ⚠ Hint only (gắn TODO comment ở trigger_hint.file) |
| Activity/Fragment trong code không có trong spec | INFO | ❌ Yêu cầu user bổ sung vào YAML |
| File có header AUTO-GENERATED bị sửa tay (hash content ≠ hash lúc gen) | HIGH | ❌ Đề xuất regen |

**Output:**
- `docs/analytics/audit-report.md`: markdown table với line number, severity, suggestion
- `docs/analytics/.audit-fixes.json`: machine-readable cho `/analytics-generate --apply-missing`

**Tham số:**
- `--fail-on=high|medium|low`: exit code ≠ 0 (cho CI gate)
- `--only=screens|buttons|popups`: thu hẹp scope

## 6. `/analytics-verify` — chi tiết

Mục tiêu: cho QC verify không cần "intuition" hay "kinh nghiệm".

**Phase 1 — Generate checklist:**
1. Đọc `event-spec.yaml`
2. Sinh `docs/analytics/test-checklist.md`:
   ```markdown
   # Test checklist — VPN Lite v1.2.0

   ## Auto-tracked events
   - [ ] App cold start → log `time_open_app_ev`
   - [ ] App background → log `app_exit`
   
   ## Màn hình Home (HomeActivity)
   - [ ] Khi vào màn → log `screen_view_ev` với screen_name="Home"
   - [ ] Khi thoát màn → log `screen_view_ev` với screen_state=stop, duration > 0
   - [ ] Bấm "ConnectButton" → log `click_btn_ev` với name="Home_ConnectButton"
   - [ ] Bấm "PremiumButton" → log `click_btn_ev` + `iap_ev` (where=home_pro_icon)

   ## Custom events
   - [ ] Bấm Connect → server kết nối thành công → log `vpn_connect_ev`
         params: server_country (string), protocol (one of openvpn/wireguard/ikev2),
                 connect_result=1
   - [ ] Bấm Connect → kết nối fail → log `vpn_connect_ev` với connect_result=0
   ```

**Phase 2 — Auto-tick từ Logcat (optional):**
- `--watch-logcat`: skill spawn `adb logcat | grep AnalyticsEvents` trong background
- Mỗi event fire → match với checklist item → tick `[x]`
- Sau 5 phút inactivity hoặc khi user nhập "stop" → in báo cáo:
  ```
  ✅ 24/27 events verified
  ❌ 3 events MISSING:
     - vpn_connect_ev với connect_result=0
     - iap_ev với result=0 (user cancel)
     - paid_ad_impression sau khi xem reward ad
  ```

## 7. Cách spec YAML khớp với 13 event chuẩn

Spec **không** yêu cầu khai báo lại 13 event chuẩn của module — skill đã biết. Member chỉ khai báo phần riêng của project.

```yaml
project:
  name: "VPN Lite"
  package: "com.example.vpnlite"
  application_class: "com.example.vpnlite.VpnLiteApp"
  
  # Bật/tắt các nhóm helper trong AnalyticsEventsUtils.kt
  features:
    iap: true              # → sinh logIapAttempt(...)
    ads: true              # → sinh logAdLoad/Show/Click(...)
    rate_dialog: true      # → sinh logRateDialog(...)
    onboarding: true       # → sinh logOnboardingStep(...)
    appsflyer: false       # → skip AppsFlyer helper
    permissions:           # → sinh logAllowPermission cho từng perm
      - NOTIFICATION
      - LOCATION
      - CAMERA

screens:
  - id: home
    class: HomeActivity
  - id: server_list
    class: ServerListFragment
    parent: home

popups:
  - id: rate_dialog
  - id: permission_request

buttons:
  - screen: home
    id: btn_connect
    name: ConnectButton
  - screen: home
    id: btn_premium
    name: PremiumButton
    triggers:
      - event: iap_ev
        params:
          where: home_pro_icon

custom_events:
  - name: vpn_connect_ev
    params:
      - { name: server_country, type: string }
      - { name: protocol, type: string, enum: [openvpn, wireguard, ikev2] }
      - { name: connect_result, type: int, enum: [0, 1] }
    trigger_hint:
      file: VpnConnectionManager.kt
      method: onConnectionResult

user_properties:
  - name: preferred_protocol
    type: string
    enum: [openvpn, wireguard, ikev2]
    trigger: "Khi user chọn protocol mặc định trong Settings"
```

## 8. Lifecycle 1 dự án + 1 lần thay đổi spec

```
T+0     PM tạo event-spec.yaml v1.0
T+0:30  Dev /analytics-setup → 12 file Kotlin được sinh
T+1d    Dev /analytics-audit → fix 18 điểm thiếu
T+2d    QC /analytics-verify → pass 27/30, fix 3 còn lại
T+30d   PM thêm 2 custom_event mới + 5 button vào spec → v1.1
T+30d   Dev /analytics-generate → diff 4 file, duyệt rồi apply
T+30d   Dev /analytics-audit → thấy 2 nơi cần gắn vpn_disconnect_ev
T+30d   Dev /analytics-generate --apply-missing → áp dụng các fix
T+30d   QC /analytics-verify → checklist tự cập nhật với event mới
```

## 9. Tích hợp CI/CD

```yaml
# .github/workflows/analytics-check.yml
name: Analytics Spec Check
on: [pull_request]
jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.11' }
      - run: pip install -r .claude/skills/firebase-events-impl/scripts/requirements.txt

      - name: Validate event spec
        run: python .claude/skills/firebase-events-impl/scripts/validate_spec.py docs/analytics/event-spec.yaml

      - name: Audit project for missing tracking
        run: python .claude/skills/firebase-events-impl/scripts/audit_project.py app/src --fail-on=high

      - name: Detect drift in generated code
        run: |
          python .claude/skills/firebase-events-impl/scripts/generate_kotlin.py \
            docs/analytics/event-spec.yaml --out /tmp/gen --no-confirm
          diff -r /tmp/gen app/src/main/java/com/example/*/event/ \
            || (echo "Generated code is out of sync. Run /analytics-generate." && exit 1)
```

CI fail nếu:
- Spec sai naming
- Có Activity/Fragment chưa gắn tracking (severity HIGH)
- File generated bị sửa tay (drift)

## 10. Khi nào skill **không** tự sửa code?

Skill **luôn hỏi xác nhận** trước khi sửa file Activity/Fragment. Một số case skill từ chối auto-fix và yêu cầu human:

- **Phát hiện logic phức tạp ở `setOnClickListener`**: nếu callback >20 dòng có if/else, skill chỉ in TODO comment ở đầu lambda, không insert log.
- **Dialog show từ context không xác định**: skill không thể biết popup này thuộc screen nào — yêu cầu user clarify.
- **Custom event mà `trigger_hint.file` không tồn tại**: in TODO ở `docs/analytics/audit-report.md`.
- **Activity dùng ViewBinding generated**: nếu chưa có import, skill thêm import trước khi insert log.
