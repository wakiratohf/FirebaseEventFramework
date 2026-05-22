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
> - **`:TOH-Ad`** is the real TOHSOFT ads library (AdMob + UMP), the only module
>   that depends on a concrete ad SDK. Its analytics glue lives under
>   [`com.tohsoft.ads.analytics`](../../TOH-Ad/src/main/java/com/tohsoft/ads/analytics/):
>   [`AdsAnalytics`](../../TOH-Ad/src/main/java/com/tohsoft/ads/analytics/AdsAnalytics.kt)
>   bootstraps the tracker and attaches an `AdWrapperListener` to the banner,
>   [`BannerAd`](../../TOH-Ad/src/main/java/com/tohsoft/ads/analytics/BannerAd.kt)
>   is the Compose wrapper, and
>   [`TohAdRevenue`](../../TOH-Ad/src/main/java/com/tohsoft/ads/analytics/TohAdRevenue.kt)
>   adapts AdMob's `AdValue` to [`AdRevenueLike`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/AdRevenueLike.kt).
> - **`:app`** just initializes `:TOH-Ad` and drops `BannerAd` into its UI — no
>   ad-event wiring of its own.

---

## Why the ad bridge lives in `:TOH-Ad`, not `:app`

`AdsEventTracker` deliberately takes primitives (`adType`, `action`, `result`,
`screenName`, `duration`) and an `AdRevenueLike` adapter — never a concrete
mediation type. That keeps `:app-event` copy-pasteable across products that use
different ad SDKs (AdMob, AppLovin, a custom wrapper…).

The thin glue that knows the SDK lives in **`:TOH-Ad`** — the module that wraps
the ad library (`AdsConfig`, `AdsModule`, `wrapper/`). Its
`com.tohsoft.ads.analytics` package depends on `:app-event` + `:firebase-events`
and forwards each ad callback. Putting the bridge there (rather than in `:app`)
means a host app only initializes `:TOH-Ad` and uses its composables; it never
touches `AdsEventTracker` directly.

> The upstream library's `AdWrapperListener` only exposed
> `onAdStartLoad/onAdLoaded/onAdFailedToLoad/onAdClicked/onAdOpened/onAdClosed`.
> Two hooks were **added to the vendored copy** so all four events still fire:
> `onAdImpression()` (→ `show_ad_ev`) and
> `onPaidEvent(adValue, adUnitId, adSource)` (→ `paid_ad_impression`), wired into
> `AdViewWrapper`'s bottom-banner path (`setOnPaidEventListener` + the AdMob
> `onAdImpression` callback). Bridging is **banner-only** for now.

> **Using a different ad SDK?** Don't depend on `:TOH-Ad`. Instead write your own
> thin bridge that forwards your SDK's callbacks into `AdsEventTracker` and
> provides an `AdRevenueLike` adapter — exactly what the analytics package does for
> TOH-Ad. The generic bridge pattern is documented under [Integration](#integration) below.

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

### A. The `:TOH-Ad` module (this repo)

If you use the bundled `:TOH-Ad` module, banner ad-event tracking is **already
wired** — you don't touch `AdsEventTracker` at all. Two steps:

1. **Initialize** from `Application.onCreate()`, **after** `AnalyticsModule.init`
   (the tracker logs `ad_engagement_level = 0` on first run, so the analytics
   module must already be up):

   ```kotlin
   AnalyticsModule.init(/* … */, isTestMode = isDebug)
   // TOH-Ad core: boots MobileAds (off-main-thread) + reads ad ids from assets.
   AdsConfig.getInstance().init(this)._setTestMode(isDebug)._setShowLog(isDebug)
   AdsModule.getInstance().init(this)
   // Analytics bridge: AdsEventTracker.init(context, isTestMode).
   AdsAnalytics.init(this, isTestMode = isDebug)
   ```

2. **Show the ad.** The Compose `BannerAd` asks `AdsModule` to show the bottom
   banner and attaches the analytics `AdWrapperListener` for you via
   `AdsAnalytics.attachBanner`. Pass `screenName` so `click_ad_ev` records where
   the click happened:

   ```kotlin
   BannerAd(
       modifier = Modifier.fillMaxWidth(),
       screenName = ScreenName.HOME,
   )
   ```

   The bridge maps the TOH-Ad / AdMob banner callbacks to events as follows:

   | Callback | Logged |
   |---|---|
   | first show (logged by `attachBanner`) | `load_ad_ev` (action `load`) |
   | `onAdStartLoad` (retry / reload) | `load_ad_ev` (action `load`) |
   | `onAdLoaded` | `load_ad_ev` (action `loaded`) |
   | `onAdFailedToLoad` | `load_ad_ev` (action `load_failed`) |
   | `onAdImpression` *(added hook)* | `show_ad_ev` (result `success`) + bumps `ad_engagement_level`; records the show time |
   | `onAdClicked` | `click_ad_ev` (duration = seconds since impression) |
   | `onPaidEvent` *(added hook)* | `paid_ad_impression` (via [`TohAdRevenue`](../../TOH-Ad/src/main/java/com/tohsoft/ads/analytics/TohAdRevenue.kt)) |

   > The very first `load` is logged explicitly by `attachBanner` because the
   > wrapper starts loading synchronously inside `showBannerBottom`, before the
   > listener can attach; subsequent retries/reloads flow through `onAdStartLoad`.

   > Banners auto-refresh, so `onAdImpression` fires once per refresh — each
   > refresh re-logs `show_ad_ev` and bumps `ad_engagement_level`. That's the
   > intended behaviour for banners.

The bridge's ad-type / action constants live in
[`com.tohsoft.ads.analytics.AdAnalyticsConstants`](../../TOH-Ad/src/main/java/com/tohsoft/ads/analytics/AdAnalyticsConstants.kt)
(`AdType`, `AdAction`); the `result` constant reuses
[`AdResult`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/AdResult.kt).
Add interstitial / app-open bridges the same way: forward the wrapper's
`AdWrapperListener` callbacks into the matching `AdsEventTracker` method.

### B. A different ad SDK (write your own bridge)

Don't depend on `:TOH-Ad`. Write a thin bridge that knows your SDK and forwards
each callback into `AdsEventTracker`. Initialize once from `Application.onCreate()`
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
framework-neutral `AdRevenueLike` interface (compare with `:TOH-Ad`'s
[`TohAdRevenue`](../../TOH-Ad/src/main/java/com/tohsoft/ads/analytics/TohAdRevenue.kt)):

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
parameter is only added when non-empty). With `:TOH-Ad`, pass it through
`AdsAnalytics.init(context, isTestMode = …)` (and `AdsConfig._setTestMode(…)` to
force test ad units); with a custom bridge, pass it to `AdsEventTracker.init`. Use
`BuildConfig.DEBUG` or your project's equivalent test-ad flag.

---

## Reference

- Tracker API: [`AdsEventTracker`](../src/main/java/com/tohsoft/app_event/AdsEventTracker.kt).
- Ad bridge (`:TOH-Ad`, package `com.tohsoft.ads.analytics`):
  [`AdsAnalytics`](../../TOH-Ad/src/main/java/com/tohsoft/ads/analytics/AdsAnalytics.kt),
  [`BannerAd`](../../TOH-Ad/src/main/java/com/tohsoft/ads/analytics/BannerAd.kt),
  [`TohAdRevenue`](../../TOH-Ad/src/main/java/com/tohsoft/ads/analytics/TohAdRevenue.kt),
  [`AdAnalyticsConstants`](../../TOH-Ad/src/main/java/com/tohsoft/ads/analytics/AdAnalyticsConstants.kt) (`AdType` / `AdAction`).
- Added listener hooks on the vendored library:
  [`AdWrapperListener`](../../TOH-Ad/src/main/java/com/tohsoft/ads/wrapper/AdWrapperListener.kt) (`onAdImpression` / `onPaidEvent`),
  wired in [`AdViewWrapper`](../../TOH-Ad/src/main/java/com/tohsoft/ads/wrapper/AdViewWrapper.kt).
- Firebase log layer:
  [`AnalyticsEvents`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
  [`AnalyticsUserProperties.logAdEngagementLevel`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsUserProperties.kt).
- Remote-config toggles: `loadAdEvEnable`, `showAdEvEnable`, `clickAdEvEnable`,
  `paidAdImpressionEvEnable` in `EventConfigs`.
