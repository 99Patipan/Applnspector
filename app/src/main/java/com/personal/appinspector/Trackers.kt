package com.personal.appinspector

data class Tracker(
    val name: String,
    val category: String,
    val severity: Int, // 1 = crash/ต่ำ, 2 = analytics, 3 = โฆษณา/attribution
    val sigs: List<String>
)

private fun t(name: String, cat: String, sev: Int, vararg s: String) = Tracker(name, cat, sev, s.toList())

// ลายเซ็นแบบ dex type descriptor เช่น "Lcom/facebook/ads/"
// เพิ่ม tracker ของคุณเองได้ที่นี่ (หรือแปลงจากฐานข้อมูล Exodus Privacy มาใส่)
val TRACKERS: List<Tracker> = listOf(
    t("Google Analytics / Firebase Analytics", "Analytics", 2, "Lcom/google/firebase/analytics/", "Lcom/google/android/gms/analytics/"),
    t("Google AdMob", "โฆษณา", 3, "Lcom/google/android/gms/ads/"),
    t("Firebase Crashlytics", "Crash", 1, "Lcom/google/firebase/crashlytics/"),
    t("Facebook Login", "Identification", 2, "Lcom/facebook/login/"),
    t("Facebook Ads", "โฆษณา", 3, "Lcom/facebook/ads/"),
    t("Facebook Analytics", "Analytics", 2, "Lcom/facebook/appevents/"),
    t("AppsFlyer", "Attribution", 3, "Lcom/appsflyer/"),
    t("Adjust", "Attribution", 3, "Lcom/adjust/sdk/"),
    t("Branch", "Attribution", 2, "Lio/branch/"),
    t("Singular", "Attribution", 3, "Lcom/singular/sdk/"),
    t("Kochava", "Attribution", 3, "Lcom/kochava/"),
    t("Tenjin", "Attribution", 3, "Lcom/tenjin/"),
    t("Mixpanel", "Analytics", 2, "Lcom/mixpanel/android/"),
    t("Amplitude", "Analytics", 2, "Lcom/amplitude/"),
    t("Segment", "Analytics", 2, "Lcom/segment/analytics/"),
    t("Flurry", "Analytics", 3, "Lcom/flurry/"),
    t("Yandex Metrica", "Analytics", 2, "Lcom/yandex/metrica/"),
    t("Umeng", "Analytics", 3, "Lcom/umeng/analytics/"),
    t("Baidu Mobstat", "Analytics", 3, "Lcom/baidu/mobstat/"),
    t("Microsoft App Center", "Analytics", 1, "Lcom/microsoft/appcenter/"),
    t("Unity Ads", "โฆษณา", 3, "Lcom/unity3d/ads/"),
    t("AppLovin", "โฆษณา", 3, "Lcom/applovin/"),
    t("ironSource", "โฆษณา", 3, "Lcom/ironsource/"),
    t("Vungle / Liftoff", "โฆษณา", 3, "Lcom/vungle/"),
    t("Chartboost", "โฆษณา", 3, "Lcom/chartboost/"),
    t("InMobi", "โฆษณา", 3, "Lcom/inmobi/"),
    t("Mintegral", "โฆษณา", 3, "Lcom/mbridge/"),
    t("Pangle (TikTok Ads)", "โฆษณา", 3, "Lcom/bytedance/sdk/openadsdk/"),
    t("Smaato", "โฆษณา", 3, "Lcom/smaato/"),
    t("Sentry", "Crash", 1, "Lio/sentry/"),
    t("Bugsnag", "Crash", 1, "Lcom/bugsnag/"),
    t("OneSignal", "Push", 2, "Lcom/onesignal/"),
    t("Braze (Appboy)", "Engagement", 2, "Lcom/braze/", "Lcom/appboy/"),
    t("CleverTap", "Engagement", 2, "Lcom/clevertap/")
)
