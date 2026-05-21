---
description: Sinh QC test checklist từ spec. Bonus mode `--watch` để tail Logcat auto-tick.
---

Sinh `docs/analytics/test-checklist.md` cho QC verify analytics khi test bản build mới.

## Quy trình

1. Detect spec path. Default: `docs/analytics/event-spec.yaml`.
2. Chạy mode generate:
   ```bash
   python .claude/skills/firebase-events-impl/scripts/verify_events.py \
       --spec <SPEC> \
       --out docs/analytics/test-checklist.md \
       --mode=generate
   ```
3. In path file checklist cho user.
4. Hướng dẫn QC enable DebugView:
   ```bash
   adb shell setprop debug.firebase.analytics.app <APP_ID>
   ```
5. (Optional) Đề xuất chạy mode watch nếu có thiết bị test sẵn (xem bên dưới).

## Watch mode — auto-tick

Tail Logcat trong khi tester click theo checklist, script auto-tick item nào fire đúng:

```bash
adb logcat -s AnalyticsEvents:D | \
    python .claude/skills/firebase-events-impl/scripts/verify_events.py \
        --spec docs/analytics/event-spec.yaml \
        --mode=watch \
        --timeout=300
```

Parser regex match:
- `eventName: (\w+)` → fire ghi nhận
- `Key: (\w+) | Value: (.+)` → param values

Sau timeout: in tổng số tick, exit code 1 nếu có item miss → fail CI test.

## Format checklist

```markdown
## Màn hình `HomeActivity` (id=`home`)

- [ ] Mở màn → log `screen_view_ev` với `screen_name="Home"`, ...
- [ ] Bấm `ConnectButton` → log `click_btn_ev` với `click_btn_ev_name="HomeConnectButton"`
- [ ] Bấm `PremiumButton` → log `click_btn_ev` ...
  - [ ] Side-effect: log `iap_ev` với `where="home_pro_icon"`

## Custom events

- [ ] `vpn_connect_ev` — User kết nối VPN
  - param `server_country` (string) có giá trị đúng
  - param `protocol` (string) ∈ [openvpn, wireguard, ikev2]
```

Item chi tiết đến mức tester không cần đọc code:
- Tên màn → human-readable
- Tên event Firebase + key param + giá trị expected
- Side-effect (vd click `btn_premium` → trigger `iap_ev`)

## User properties section

```markdown
## User properties

- [ ] `preferred_protocol` set đúng khi user đổi trong Settings (giá trị ∈ [openvpn, wireguard, ikev2])
- [ ] `is_premium` set đúng sau giao dịch IAP thành công
```

## Lưu ý

- Checklist generate từ spec hiện tại → nếu spec đổi sau, regenerate.
- Watch mode chỉ tick được event đã có trong checklist; event ngoài spec (vd debug log) bị ignore.
- DebugView có delay ~10s — watch script có grace period, không panic nếu chưa tick ngay.
