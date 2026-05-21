---
description: Tự động wrap mọi @Composable screen với `TrackedScreen(ScreenName.XXX) { ... }`. Đọc spec để biết list screen, scan tìm Composable function tương ứng, inject wrapper. Idempotent.
---

Time saver lớn cho project Compose — sau khi `/analytics-setup` sinh code, dev không cần wrap tay từng `@Composable fun XxxScreen` nữa.

## Quy trình

1. Đọc `event-spec.yaml`, lấy danh sách screen có `kind: composable`
2. Cho mỗi screen, tìm file Kotlin chứa `@Composable fun <className>(...)`
3. Inject vào file:
   - Imports: `import com.<package>.event.TrackedScreen` + `ScreenName`
   - Wrap body: `TrackedScreen(ScreenName.XXX) { <body cũ> }`

## Lệnh

```bash
python .claude/skills/firebase-events-impl/scripts/apply_track_screen.py \
    --spec docs/analytics/event-spec.yaml \
    --src app/src/main/java \
    [--dry-run] [--no-confirm] [--only HomeScreen,SettingsScreen]
```

## Trước → Sau

**Trước (chưa wrap):**
```kotlin
@Composable
fun HomeScreen(onConnect: () -> Unit) {
    Button(onClick = onConnect) {
        Text("Connect")
    }
}
```

**Sau (đã wrap):**
```kotlin
import com.test.event.TrackedScreen
import com.test.event.ScreenName

@Composable
fun HomeScreen(onConnect: () -> Unit) {
    TrackedScreen(ScreenName.HOME) {
        Button(onClick = onConnect) {
            Text("Connect")
        }
    }
}
```

## Idempotent

Re-run lần 2:
```
─ already       HomeScreen        app/.../HomeScreen.kt
─ already       SettingsScreen    app/.../SettingsScreen.kt
```

Detect qua regex `TrackedScreen\s*\(\s*ScreenName\.[A-Z_]+\s*[,)]` — chỉ wrap khi chưa có.

## Flags

- `--dry-run` — print plan, không write file
- `--no-confirm` — skip confirmation prompt (CI mode)
- `--only ClassA,ClassB` — chỉ wrap subset screen (vd debug từng screen)

## Edge cases handled

- Empty body Composable → skip (nothing to wrap)
- Single-expression `fun X() = expr` → skip (no body block)
- Composable name không match spec → skip
- File không có @Composable fun → skip

## Khi nào dùng

- Sau `/analytics-setup` (lần đầu generate Kotlin)
- Sau khi PM thêm screen mới vào spec + chạy `/analytics-generate` → chạy command này để wrap screen mới
- Sau khi rename screen ID trong spec → re-run để cập nhật `ScreenName.XXX` constant

## Cảnh báo

- Script CHỈ wrap, không unwrap. Nếu user xóa screen khỏi spec, file vẫn giữ `TrackedScreen(...)` wrapper — phải unwrap tay.
- Script làm idempotent qua marker `TrackedScreen(ScreenName.XXX)`. Nếu code có custom wrapper khác (vd `MyTracker { ... }`) cũng có pattern tương tự → có thể conflict. Đọc `--dry-run` trước khi run thật trong project legacy.
- Format Kotlin có thể bị xộc xệch (extra blank lines). Run `Android Studio → Code → Reformat Code` sau khi wrap.
