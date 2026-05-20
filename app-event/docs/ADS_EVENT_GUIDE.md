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
>   in `FirebasePrefs` (key `ad_showed_count`). It is **ads-SDK-agnostic**.
> - **`:ads`** owns the concrete AdMob bridge — it is the only module that depends
>   on the Google Mobile Ads SDK. [`BannerAd`](../../ads/src/main/java/com/tohsoft/ads/BannerAd.kt)
>   forwards AdMob `AdListener` / `OnPaidEventListener` callbacks into
>   `AdsEventTracker`, [`AdsManager.initialize`](../../ads/src/main/java/com/tohsoft/ads/AdsManager.kt)
>   bootstraps the tracker, and [`AdMobAdRevenue`](../../ads/src/main/java/com/tohsoft/ads/AdMobAdRevenue.kt)
>   adapts AdMob's `AdValue` to [`AdRevenueLike`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/AdRevenueLike.kt).
> - **`:app`** just initializes `:ads` and drops `BannerAd` into its UI — no
>   ad-event wiring of its own.

---

## Why the AdMob bridge lives in `:ads`, not `:app`

`AdsEventTracker` deliberately takes primitives (`adType`, `action`, `result`,
`screenName`, `duration`) and an `AdRevenueLike` adapter — never a concrete
mediation type. That keeps `:app-event` copy-pasteable across products that use
different ad SDKs (AdMob, AppLovin, a custom wrapper…).

The thin glue that knows the SDK lives in **`:ads`** — the module that already
wraps AdMob (`AdsManager`, `BannerAd`, `AdsConfig`). It depends on `:app-event`
and `:firebase-events` and forwards each SDK callback. Putting the bridge there
(rather than in `:app`) means a host app only initializes `:ads` and uses its
composables; it never touches `AdsEventTracker` directly.

> **Using a different ad SDK?** Don't depend on `:ads`. Instead write your own
> thin bridge that forwards your SDK's callbacks into `AdsEventTracker` and
> provides an `AdRevenueLike` adapter — exactly what `:ads` does for AdMob. The
> generic bridge pattern is documented under [Integration](#integration) below.

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

### A. AdMob via the `:ads` module (this repo)

If you use the bundled `:ads` module, ad-event tracking is **already wired** —
you don't touch `AdsEventTracker` at all. Two steps:

1. **Initialize** from `Application.onCreate()`, **after** `AnalyticsModule.init`
   (the tracker logs `ad_engagement_level = 0` on first run, so the analytics
   module must already be up):

   ```kotlin
   AnalyticsModule.init(/* … */, isTestMode = isDebug)
   // Boots MobileAds + AdsEventTracker.init(context, isTestMode).
   AdsManager.initialize(this, isTestMode = isDebug)
   ```

2. **Show the ad.** `BannerAd` attaches an `AdListener` + `OnPaidEventListener`
   internally and forwards every callback for you. Pass `screenName` so
   `click_ad_ev` records where the click happened:

   ```kotlin
   BannerAd(
       modifier = Modifier.fillMaxWidth(),
       screenName = ScreenName.HOME,
   )
   ```

   `BannerAd` maps the AdMob callbacks to events as follows:

   | AdMob callback | Logged |
   |---|---|
   | before `loadAd(…)` | `load_ad_ev` (action `load`) |
   | `onAdLoaded` | `load_ad_ev` (action `loaded`) |
   | `onAdFailedToLoad` | `load_ad_ev` (action `load_failed`) |
   | `onAdImpression` | `show_ad_ev` (result `success`) + bumps `ad_engagement_level`; records the show time |
   | `onAdClicked` | `click_ad_ev` (duration = seconds since impression) |
   | `onPaidEvent` | `paid_ad_impression` (via [`AdMobAdRevenue`](../../ads/src/main/java/com/tohsoft/ads/AdMobAdRevenue.kt)) |

   > Banners auto-refresh, so `onAdImpression` fires once per refresh — each
   > refresh re-logs `show_ad_ev` and bumps `ad_engagement_level`. That's the
   > intended behaviour for banners.

The `:ads` ad-type / action / result constants live in
[`com.tohsoft.ads.models`](../../ads/src/main/java/com/tohsoft/ads/models/)
(`AdType`, `AdAction`, `AdResult`). Add interstitial / rewarded / native bridges
the same way: forward the SDK callback into the matching `AdsEventTracker` method.

### B. A different ad SDK (write your own bridge)

Don't depend on `:ads`. Write a thin bridge that knows your SDK and forwards each
callback into `AdsEventTracker`. Initialize once from `Application.onCreate()`
(after `AnalyticsModule.init` and your ads SDK):

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

The revenue adapter is a one-liner wrapping the SDK payload into the
framework-neutral `AdRevenueLike` interface (compare with `:ads`'
[`AdMobAdRevenue`](../../ads/src/main/java/com/tohsoft/ads/AdMobAdRevenue.kt)):

```kotlin
class AdRevenueLikeAdapter(private val revenue: SdkAdValue) : AdRevenueLike {
    override fun toBundle(): Bundle = bundleOf(
        "value" to revenue.valueMicros / 1_000_000.0,
        "currency" to revenue.currencyCode,
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
parameter is only added when non-empty). With `:ads`, pass it through
`AdsManager.initialize(context, isTestMode = …)`; with a custom bridge, pass it
to `AdsEventTracker.init`. Use `BuildConfig.DEBUG` or your project's equivalent
test-ad flag.

---

## Reference

- Tracker API: [`AdsEventTracker`](../src/main/java/com/tohsoft/app_event/AdsEventTracker.kt).
- AdMob bridge (`:ads`):
  [`AdsManager`](../../ads/src/main/java/com/tohsoft/ads/AdsManager.kt),
  [`BannerAd`](../../ads/src/main/java/com/tohsoft/ads/BannerAd.kt),
  [`AdMobAdRevenue`](../../ads/src/main/java/com/tohsoft/ads/AdMobAdRevenue.kt),
  [`com.tohsoft.ads.models`](../../ads/src/main/java/com/tohsoft/ads/models/) (`AdType` / `AdAction` / `AdResult`).
- Firebase log layer:
  [`AnalyticsEvents`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
  [`AnalyticsUserProperties.logAdEngagementLevel`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsUserProperties.kt).
- Remote-config toggles: `loadAdEvEnable`, `showAdEvEnable`, `clickAdEvEnable`,
  `paidAdImpressionEvEnable` in `EventConfigs`.
