---
description: Scan dependencies Gradle của module `firebase-events` vs app target, phát hiện mismatch (SDK level, Kotlin, AGP, catalog entries), sinh report.
---

Standalone command để chạy lại Gradle compat scan sau khi đã setup. Cũng tự động chạy trong phase 5 của `/analytics-import-module`.

## Quy trình

1. Detect target path (default: cwd).
2. Detect module: `<target>/firebase-events/` + `<target>/firebase-events-lint/`. Nếu không có → báo lỗi, đề xuất `/analytics-import-module` trước.
3. Chạy:
   ```bash
   python .claude/skills/firebase-events-impl/scripts/align_gradle.py \
       --target . \
       --out docs/analytics/gradle-align-report.md
   ```
4. Đọc report, summarize cho user theo severity:
   - **HIGH**: SDK level mismatch (compileSdk, minSdk) → block build
   - **MEDIUM**: Kotlin / AGP version mismatch hoặc catalog entry thiếu
   - **LOW**: Minor version mismatch dep
5. Đề xuất next action: sửa tay `app/build.gradle.kts` theo "Fix" trong report.

## 5 loại mismatch phát hiện

| Severity | Loại | Khi nào |
|---|---|---|
| HIGH | `compileSdk` target < module | Module compile với API mới hơn target |
| HIGH | `minSdk` target < module | Manifest merger sẽ fail |
| MEDIUM | Kotlin major.minor khác | Risk binary incompat (metadata format) |
| MEDIUM | AGP major.minor khác | Build tool incompat |
| MEDIUM | Catalog entry missing | Module dùng `libs.X` nhưng `libs.versions.toml` không có |
| LOW | `targetSdk` mismatch | Không block build, behavior differ |
| LOW | Dep version mismatch | Gradle auto-resolve, có thể không như expect |

## Flags

- `--apply` — auto-add placeholder entries vào `libs.versions.toml` (KHÔNG sửa `app/build.gradle.kts`)
- `--fail-on=high|medium|low|none` — exit code 1 nếu có finding tại severity này (default: `high`, dùng cho CI)
- `--quiet` — minimal console output
- `--module-dir <path>` / `--lint-module-dir <path>` — override module location
- `--out <path>` — override report output path

## Tại sao không tự sửa `app/build.gradle.kts`?

Sửa `compileSdk` / `minSdk` / Kotlin / AGP version có thể chain-break các module khác trong project hoặc transitive deps. Skill chỉ:

1. **Báo cáo** mismatch với suggested fix cụ thể
2. **Cho `--apply`** chỉ giới hạn ở `libs.versions.toml` — chỉ ADD placeholder, không sửa version đã có

User tự quyết bump khi nào + verify build sau khi sửa.

## Format report

`docs/analytics/gradle-align-report.md`:

```markdown
# Gradle alignment report

## Summary
- HIGH:   2
- MEDIUM: 3
- LOW:    0

## Side-by-side
| | Module | Target |
|---|---|---|
| compileSdk | 36 | 34 |
| minSdk     | 24 | 21 |
| Kotlin     | 2.0.21 | 1.9.0 |
| AGP        | 8.5.0 | 8.1.0 |

## Findings

### HIGH — 2

#### HIGH.1 [SDK] Target compileSdk (34) thấp hơn module yêu cầu (36)
...
**Fix:** Sửa `app/build.gradle.kts`: ...
```

## CI integration

```yaml
# .github/workflows/gradle-align.yml
- name: Gradle align check
  run: |
    python .claude/skills/firebase-events-impl/scripts/align_gradle.py \
        --target . \
        --fail-on=high
```

CI fail nếu có HIGH severity → block merge khi compileSdk/minSdk mismatch.

## Khi nào dùng

- Sau `/analytics-import-module` (đã tự chạy phase 5, nhưng có thể re-run riêng)
- Sau khi maintainer module bump version → check lại compat
- Sau khi sửa `app/build.gradle.kts` → confirm đã align
- Trong CI/PR check để block merge nếu mismatch
