---
description: Clone `firebase-events` từ GitHub repo public, copy module vào project, wire Gradle. Chạy 1 lần trước khi dùng các analytics command khác.
---

Tích hợp 3 module runtime (`:firebase-events` + `:firebase-events-lint` + `:app-events`) vào project Android target qua 5 phase: clone → copy → wire settings.gradle → wire app/build.gradle → align Gradle. Mỗi lần chạy luôn pull code mới nhất từ upstream và force re-copy module vào target.

## Quy trình

1. Detect project root (default: cwd). Confirm có `settings.gradle.kts` / `settings.gradle` / `app/`.
2. Hỏi user (hoặc dùng default):
   - `--repo-url` (default: `https://github.com/wakiratohf/FirebaseEventFramework.git`)
   - `--branch` (default: `main`)
3. Đề xuất dry-run trước:
   ```bash
   python .claude/skills/firebase-events-impl/scripts/setup_module.py \
       --target . \
       --dry-run
   ```
4. Nếu user OK với plan → chạy thật:
   ```bash
   python .claude/skills/firebase-events-impl/scripts/setup_module.py \
       --target . \
       --yes
   ```
5. Sau khi xong, đề xuất next step:
   - `Android Studio → File → Sync Project with Gradle Files`
   - `./gradlew :app:assembleDebug` để verify build OK
   - Tiếp `/analytics-scaffold` hoặc `/analytics-setup`

## 4 phase chi tiết

| Phase | Làm gì | Idempotent? |
|---|---|---|
| **1. CLONE** | `git clone --depth 1 --branch main <url> .claude-cache/firebase-events-upstream/`. Lần 2+: `git fetch` + `reset --hard origin/<branch>` (luôn lấy code mới nhất). | Có — tự cập nhật từ upstream |
| **2. COPY** | Copy 3 module sang target root (sibling của `app/`): `firebase-events/`, `firebase-events-lint/`, `app-events/`. Skip `.git/`, `build/`, `.gradle/`, `*.iml`. **LUÔN force-refresh** — code module có thể đổi trước khi VERSION bump. | **KHÔNG idempotent** (theo thiết kế) — mục đích chính là sync code mới |
| **3. WIRE** | Patch `settings.gradle(.kts)` thêm `include(":firebase-events")` + `include(":firebase-events-lint")` + `include(":app-events")` sau dòng `include(":app")` cuối cùng | Có — regex check sự tồn tại trước khi insert |
| **4. APPWIRE** | Patch `app/build.gradle(.kts)`: inject `implementation(project(":firebase-events"))` + `lintChecks(project(":firebase-events-lint"))` + `implementation(project(":app-events"))` vào trong `dependencies { }` block. Phân loại theo suffix module: `-lint` → `lintChecks`, còn lại → `implementation`. | Có |
| **5. ALIGN** | Scan compat 3 module vs target (SDK level, Kotlin, AGP, libs.versions.toml entries), sinh report. | Có — chỉ scan |

Cả 3 file Gradle bị sửa đều có backup `.bak` ở cùng thư mục.

## Flags hữu ích

- `--dry-run` — print plan, không ghi
- `--yes` / `-y` — skip confirm prompt (CI mode)
- `--force-reclone` — wipe cache, clone lại từ đầu (khi cache corrupt)
- `--skip-clone` — chỉ copy + wire (cache đã có sẵn / offline)
- `--skip-copy` — chỉ wire Gradle (module đã copy vào target rồi)
- `--skip-wire` — chỉ clone + copy, không sửa Gradle
- `--repo-url <url>` — override URL repo (cho fork riêng)
- `--branch <ref>` — override branch / tag / commit SHA
- `--cache-dir <path>` — override cache location (default: `<target>/.claude-cache/firebase-events-upstream`)

## Khi nào dùng

| Tình huống | Hành động |
|---|---|
| Project mới chưa có module `:firebase-events` | Chạy `/analytics-import-module` từ đầu |
| Module đã có sẵn, chỉ thiếu Gradle wiring | `setup_module.py --skip-clone --skip-copy` |
| Bump version module sau khi maintainer push commit mới | `setup_module.py --force-reclone` |
| Project có fork riêng module | `setup_module.py --repo-url <fork-url>` |
| Module được setup qua git submodule, không cần clone | Skip command này, dùng module ở submodule path |

## Cảnh báo

- **Repo phải public** hoặc user phải có SSH key auth (HTTPS với token cũng OK).
- **Cache nằm trong project** (`.claude-cache/`) — đã được installer add vào `.gitignore`.
- **Backup `.bak`** không bị git track (suffix `.bak` thường trong `.gitignore` default) — clean tay nếu muốn.
- **Sửa tay module sau khi import**: ❌ NHẮC user KHÔNG nên — lần `--force-reclone` sau sẽ overwrite. Nếu cần custom, fork repo + đổi `--repo-url`.

## Sample full first-time workflow

```bash
# 1. Install skill vào project
cd ~/AndroidStudioProjects/MyApp
unzip ~/Downloads/analytics-automation.zip
./analytics-automation/install.sh .

# 2. Import module (qua slash command)
claude
> /analytics-import-module
# Confirm dry-run plan → confirm thật → Android Studio Sync

# 3. Scaffold + setup analytics
> /analytics-scaffold
> /analytics-setup
> /analytics-audit
```
