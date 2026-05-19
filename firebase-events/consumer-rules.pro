-keep class com.tohsoft.firebase_events.models.** {*;}
# Public adapter interface — host apps implement WebhookSender to plug a test-log transport.
-keep interface com.tohsoft.firebase_events.utils.WebhookSender { *; }