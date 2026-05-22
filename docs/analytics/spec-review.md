# Spec review — scaffold results

- Project mode detected: **view_binding** (0 composable / 6 activity+fragment = 0% compose)
  - Formula: `composable_screens / (composable + activity + fragment)`
  - Thresholds: ≥80% → compose · ≤20% → view_binding · else → hybrid
  - Override by editing `project.mode` in spec if detection is wrong.
- Screens found: 6
- Popups found: 5
- Button candidates: 20 (high 0, medium 17, low 3)

## ⚠ Low-confidence button candidates

Could not infer which screen these belong to. Fix `screen:` field in spec or add `// @analytics:` comment.

- `com/example/firebaseeventframework/ui/dialogs/RateDialog.kt:86` — TextButton(...) — via Text label (proposed id=`btn_đánh_giá_5_sao`, screen=`TODO_screen`)
- `com/example/firebaseeventframework/ui/dialogs/RateDialog.kt:95` — TextButton(...) — via Text label (proposed id=`btn_không_thích`, screen=`TODO_screen`)
- `com/example/firebaseeventframework/ui/dialogs/RateDialog.kt:101` — TextButton(...) — via Text label (proposed id=`btn_để_sau`, screen=`TODO_screen`)

## Other notes

- `com/example/firebaseeventframework/ui/subscription/SubscriptionScreen.kt:330` — `Box` với `Modifier.clickable` chưa rõ identity. Đề xuất id `btn_clickable_l330`.
- `com/example/firebaseeventframework/ui/subscription/SubscriptionScreen.kt:383` — `Text` với `Modifier.clickable` chưa rõ identity. Đề xuất id `btn_clickable_l383`.

## Next steps
1. Edit `docs/analytics/event-spec.yaml`, fix any `TODO_*` values.
2. Run validate_spec.py to check naming convention.
3. Run generate_kotlin.py to emit Kotlin files.