---
description: Đối chiếu catalog refs trong module với target's `libs.versions.toml`. Nếu target đã có lib cùng coordinate nhưng key khác → patch module file dùng key target (KHÔNG sửa target catalog).
---

Standalone command để re-run reconcile sau khi đã setup. Tự động chạy trong Phase 5.5 của `/analytics-import-module`.

## Vấn đề skill xử lý

Module upstream dùng catalog refs với key style của repo gốc (vd `libs.androidx.core.ktx`). Target project có thể đã khai báo cùng lib nhưng key khác (vd `libs.core.ktx`). Khi đó Gradle sync fail vì:

```
> Could not get unknown property 'androidx.core.ktx' for ...
```

## Quy trình

1. Load source catalog từ `.claude-cache/firebase-events-upstream/gradle/libs.versions.toml`
2. Load target catalog từ `gradle/libs.versions.toml`
3. Scan mỗi module's `build.gradle.kts` tìm tất cả `libs.X.Y.Z` references (cả libraries lẫn `libs.plugins.X`)
4. Cho mỗi ref:
   - Lookup coordinate trong source catalog (vd `libs.androidx.core.ktx` → `androidx.core:core-ktx`)
   - Tìm key trong target catalog có cùng coordinate
   - Nếu key target ≠ key upstream → **patch module file**: đổi `libs.androidx.core.ktx` → `libs.<target_key>`
5. Sinh report `docs/analytics/catalog-reconcile-report.md`

## Quy tắc patch

| Scenario | Hành động |
|---|---|
| Source ref + target có cùng coord + cùng key | Không làm gì |
| Source ref + target có cùng coord + key khác | **Patch module file** đổi ref sang key target |
| Source ref + target KHÔNG có coord tương ứng | Để nguyên + báo missing (user thêm tay vào target catalog) |
| Module dùng direct coord (`"group:artifact:version"`) | Bỏ qua — không phải catalog ref |
| Module dùng ref mà source catalog không có | Bỏ qua — không xác định được coord |

KHÔNG bao giờ:
- Tự thêm key mới vào target catalog (giữ catalog target sạch)
- Sửa version trong target catalog
- Sửa `app/build.gradle.kts`

## Convention key conversion (Gradle TOML rules)

TOML key dùng `-`, `_`, `.` đều convert sang `.` trong Kotlin DSL accessor:

| TOML key | Kotlin accessor |
|---|---|
| `androidx-core-ktx` | `libs.androidx.core.ktx` |
| `androidx_core_ktx` | `libs.androidx.core.ktx` |
| `androidx.core.ktx` | `libs.androidx.core.ktx` |
| `androidxCoreKtx` | `libs.androidxCoreKtx` (camelCase không có separator → giữ nguyên) |

Skill chuẩn hóa cả 3 form đầu khi so sánh.

## Flags

- `--dry-run` — print plan, không patch
- `--cache-dir <path>` — override cache location
- `--modules firebase-events firebase-events-lint app-events` — modules cần scan
- `--out <path>` — report output path
- `--quiet` — minimal console output

## Khi nào dùng

- **Sau `/analytics-import-module`** (tự động trong phase 5.5)
- **Sau khi target's `libs.versions.toml` thay đổi** (ai đó rename key)
- **Sau khi maintainer module bump version** + có catalog refs mới
- **Khi Gradle sync báo "unknown property 'libs.xxx'"** — chạy command này trước khi đi tìm bug khác

## Backup + idempotency

- Mỗi module file bị sửa được backup `.bak` (chỉ 1 lần — backup gốc, không bị overwrite ở lần chạy thứ 2)
- Idempotent — chạy lại sau khi đã patch sẽ thấy "All refs match target catalog keys"
- Lưu ý: vì `/analytics-import-module` luôn force re-copy module (overwrite mọi sửa đổi tay), reconcile phải chạy SAU copy mỗi lần — đây là lý do nó nằm trong phase 5.5 của setup_module.

## Lệnh chạy standalone

```bash
python .claude/skills/firebase-events-impl/scripts/reconcile_catalog.py --target .
```

## Exit codes

- `0` — Patched OK hoặc không cần patch
- `1` — Có lib trong source catalog mà target không có (cần user thêm tay)
- `2` — Lỗi (Python < 3.11, missing catalog file fatal)
