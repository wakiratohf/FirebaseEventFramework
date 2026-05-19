package com.tohsoft.firebase_events.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class ClickBtnEvIssueRegistry : IssueRegistry() {

    override val issues: List<Issue> = listOf(
        ButtonNameConventionDetector.ISSUE_UNDERSCORE,
        ButtonNameConventionDetector.ISSUE_BTN_PREFIX,
        ButtonNameConventionDetector.ISSUE_NOT_CAMEL_CASE,
        ButtonNameConventionDetector.ISSUE_EMPTY,
    )

    override val api: Int = CURRENT_API
    override val minApi: Int = 14

    override val vendor: Vendor = Vendor(
        vendorName = "TOHSoft",
        identifier = "firebase-events-lint",
        feedbackUrl = "https://github.com/TOHSOFT/FirebaseEventFramework/issues",
    )
}
