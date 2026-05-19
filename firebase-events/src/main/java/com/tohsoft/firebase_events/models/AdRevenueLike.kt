package com.tohsoft.firebase_events.models

import android.os.Bundle

/**
 * Adapter interface for paid-ad-impression revenue payloads.
 *
 * The SDK does not depend on any concrete ad-revenue class. Each project must
 * provide a class that implements this interface (typically a thin wrapper
 * around the mediation SDK's revenue object) and pass it to
 * [com.tohsoft.firebase_events.AnalyticsEvents.logPaidAdImpressionEv].
 *
 * The bundle returned by [toBundle] is forwarded as-is to Firebase Analytics
 * as parameters of the `paid_ad_impression` event. Keys must respect the
 * 40-character Firebase parameter-name limit.
 */
interface AdRevenueLike {
    fun toBundle(): Bundle
}
