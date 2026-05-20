# Ad events — Integration guide

Logs the four ad analytics events plus the `ad_engagement_level` user property:

| Event | When |
|---|---|
| `load_ad_ev` | An ad starts loading (action: `load` / `retry` / `loaded` / `load_failed`). |
| `show_ad_ev` | An ad is shown (result success/failed). Also bumps `ad_engagement_level`. |
| `click_ad_ev` | The user clicks an ad. |
| `paid_ad_impression` | A paid impression fires (impression-level ad revenue). |

> Module split:
> - `:firebase-events` owns the Firebase log + payload formatters
>   ([`AnalyticsEvents`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
>   `LoadAdEv` / `ShowAdEv` / `ClickAdEv` / `PaidAdImpressionEv`,
>   [`AdRevenueLike`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/AdRevenueLike.kt))
>   and the `ad_engagement_level` thresholds in `AnalyticsUserProperties`.
> - `:app-event` (this module) owns [`AdsEventTracker`](../src/main/java/com/tohsoft/app_event/AdsEventTracker.kt):
>   it builds those payloads from primitives and persists the ad-showed counter
>   in `FirebasePrefs` (key `ad_showed_count`).
> - **`:app`** owns the bridge from the concrete ads SDK. Neither
>   `:firebase-events` nor `:app-event` depends on any ads SDK — the host app
>   forwards SDK callbacks into `AdsEventTracker` and provides an
>   [`AdRevenueLike`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/AdRevenueLike.kt)
>   adapter for the revenue payload.

---

## Why the SDK bridge stays in `:app`

`AdsEventTracker` deliberately takes primitives (`adType`, `action`, `result`,
`screenName`, `duration`) and an `AdRevenueLike` adapter — never a concrete
mediation type. That keeps this module copy-pasteable across products that use
different ad SDKs (AdMob, AppLovin, a custom wrapper…). The thin glue that knows
the SDK lives in `:app`.

---

## `ad_engagement_level`

A monotonic count of ads shown, persisted in `FirebasePrefs`
(`ad_showed_count`). `AnalyticsUserProperties.logAdEngagementLevel` only sets
the property at the thresholds `0, 1, 5, 10, 20, 50, 100, 200, 500` and stops
above 500. The counter survives process death and is shared across modules
through the `firebase_prefs` file.

**Migrating an existing counter.** If your app already tracked this count in
another store, pass it as `legacyShowedCount` to `init` on the first run after
adopting the tracker. The tracker seeds `FirebasePrefs` with that value
**silently** (no re-log), so users who already crossed thresholds don't
re-trigger them. Omit it (`-1`) for a fresh integration — the tracker starts at
0 and logs level 0 once.

---

## Integration

Write a thin bridge in `:app` that knows your ads SDK and forwards each callback
into `AdsEventTracker`. Initialize once from `Application.onCreate()` (after the
ads SDK is set up):

```kotlin
object AdEventsBridge {
    private val isTestMode = BuildConfig.DEBUG // or your project's test-ad flag

    fun init(context: Context) {
        AdsEventTracker.init(context, isTestMode)
        // Register your mediation SDK's ad-events listener and forward:
        adsSdk.setAdEventsListener(object : AdEventsListener {
            override fun onAdStartLoad(adType: String, action: String, adId: String?) {
                AdsEventTracker.logLoadAd(adType, action, adId)
            }
            override fun onAdShowed(adType: String, result: String, adId: String?) {
                AdsEventTracker.logShowAd(context, adType, result, adId)
            }
            override fun onAdClicked(adType: String, screenName: String, duration: Int) {
                AdsEventTracker.logClickAd(adType, screenName, duration)
            }
            override fun onAdRevenuePaid(revenue: SdkAdValue) {
                AdsEventTracker.logPaidAd(AdRevenueLikeAdapter(revenue))
            }
        })
    }
}
```

The `AdRevenueLikeAdapter` is a one-liner wrapping the SDK revenue payload into
the framework-neutral `AdRevenueLike` interface:

```kotlin
class AdRevenueLikeAdapter(private val revenue: SdkAdValue) : AdRevenueLike {
    override fun toBundle(): Bundle = bundleOf(
        "value", revenue.valueMicros / 1_000_000.0,
        "currency", revenue.currencyCode,
        // keys must respect Firebase's 40-char parameter-name limit
    )
}
```

If you migrate from an older counter, read it from your old store and pass it:

```kotlin
val legacyCount = oldPrefs.getInt("PREF_AD_SHOWED_COUNT", -1)
AdsEventTracker.init(context, isTestMode, legacyCount)
```

---

## `isTestMode`

When true, the optional `ad_id` parameter is attached to `load_ad_ev` /
`show_ad_ev` to ease debugging. In production builds it is omitted (the Firebase
parameter is only added when non-empty). Pass `BuildConfig.DEBUG` or your
project's equivalent test-ad flag.

---

## Reference

- API: [`AdsEventTracker`](../src/main/java/com/tohsoft/app_event/AdsEventTracker.kt).
- Firebase log layer:
  [`AnalyticsEvents`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
  [`AnalyticsUserProperties.logAdEngagementLevel`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsUserProperties.kt).
- Remote-config toggles: `loadAdEvEnable`, `showAdEvEnable`, `clickAdEvEnable`,
  `paidAdImpressionEvEnable` in `EventConfigs`.
