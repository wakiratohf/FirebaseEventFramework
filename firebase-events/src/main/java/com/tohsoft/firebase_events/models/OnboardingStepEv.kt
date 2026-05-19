package com.tohsoft.firebase_events.models

import android.os.Bundle

/**
 * Model for event 'onboarding_step'
 *
 * - Log qua từng màn Tutorial
 * */
data class OnboardingStepEv(
    /** Tên màn hình tutorial */
    private val stepName: String,
    
    private val completed: String,

    /** Ví dụ từ chối vì không cấp quyền Camera */
    private val reason: String
) {

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString("step_name", stepName)
        bundle.putString("completed", completed)
        bundle.putString("reason", reason)
        return bundle
    }
}
