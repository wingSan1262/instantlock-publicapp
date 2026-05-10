package com.risyan.quickshutdownphone.screen_content_guard.service_utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.risyan.quickshutdownphone.MyApp
import com.risyan.quickshutdownphone.base.data.SharedPrefApi
import com.risyan.quickshutdownphone.base.showIncognitoWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Detects the current foreground app and determines if it's a social media app
 * that may require different NSFW detection thresholds
 */
class ForegroundAppDetector(
    private val service: AccessibilityService,
    private val ownerScope: CoroutineScope? = null,
    private val sharedPreferences: SharedPrefApi = MyApp.getInstance().sharedPrefApi
) {

    private var currentPackageName: String? = null

    // Social media apps that often have more NSFW content
    private val socialMediaPackages = setOf(
        "com.twitter.android",           // Twitter/X
        "com.zhiliaoapp.musically",      // TikTok
        "com.instagram.android",         // Instagram
        "com.reddit.frontpage",          // Reddit
        "com.tumblr",                    // Tumblr
        "com.snapchat.android",          // Snapchat
        "com.facebook.katana",           // Facebook
        "com.facebook.orca",             // Messenger
        "com.discord",                   // Discord
        "com.pinterest",                 // Pinterest
        "jp.naver.line.android",         // LINE
        "com.whatsapp",                  // WhatsApp
        "org.telegram.messenger",        // Telegram
        "com.vkontakte.android",         // VK
        "com.ss.android.ugc.aweme",      // TikTok CN
    )

    // Video/streaming apps
    private val videoStreamingPackages = setOf(
        "com.google.android.youtube",    // YouTube
        "tv.twitch.android.app",         // Twitch
        "com.netflix.mediaclient",       // Netflix
        "com.amazon.avod.thirdpartyclient", // Prime Video
        "com.hulu.plus",                 // Hulu
    )

    // Dating apps (higher risk)
    private val datingAppPackages = setOf(
        "com.tinder",                    // Tinder
        "com.bumble.app",                // Bumble
        "com.okcupid.okcupid",          // OKCupid
        "com.match.android.matchmobile", // Match
        "net.hinge.app",                 // Hinge
        "com.grind.android",             // Grindr
    )

    // Safe/system packages immune to any NSFW detection
    private val safePackages = setOf(
        "com.android.systemui",                      // System UI / Status bar
        "com.android.settings",                      // Android Settings
        "com.google.android.packageinstaller",        // Package Installer
        "com.android.packageinstaller",              // Package Installer (AOSP)
        "com.android.launcher",                      // AOSP Launcher
        "com.google.android.apps.nexuslauncher",     // Pixel Launcher
        "com.sec.android.app.launcher",              // Samsung Launcher
        "com.miui.home",                             // MIUI Launcher
        "com.huawei.android.launcher",               // Huawei Launcher
        "com.vivo.launcher",                         // Vivo Launcher
        "com.oppo.launcher",                         // Oppo Launcher
        "com.android.dialer",                        // Phone/Dialer (AOSP)
        "com.google.android.dialer",                 // Google Dialer
        "com.android.contacts",                      // Contacts
        "com.android.phone",                         // Phone app
        "com.android.server.telecom",                // Telecom Service
        "com.android.inputmethod.latin",             // Keyboard (AOSP)
        "com.google.android.inputmethod.latin",      // Gboard
        "com.samsung.android.honeyboard",            // Samsung Keyboard
        "android",                                   // Android OS itself
        "com.android.keyguard",                      // Keyguard / Lock screen
        "com.android.permissioncontroller",          // Permission dialogs
        "com.google.android.permissioncontroller",   // Permission dialogs (Google)
    )

    // Browsers to blacklist (except Chrome)
    private val blacklistedBrowsers = setOf(
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus", "org.mozilla.klar", // Firefox
        "com.opera.browser", "com.opera.mini.native", "com.opera.gx", // Opera
        "com.microsoft.emmx", "com.microsoft.emmx.beta", "com.microsoft.emmx.canary", // Edge
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta", // Samsung
        "com.UCMobile.intl", "com.uc.browser.en", // UC Browser
        "com.brave.browser", "com.vivaldi.browser", "com.yandex.browser",
        "com.duckduckgo.mobile.android", "org.torproject.torbrowser",
        "com.kiwibrowser.browser", "com.ecosia.android", "org.bromite.bromite",
        "com.mi.globalbrowser", "com.vivo.browser", "com.huawei.browser", // OEM
        "com.android.browser", "com.google.android.browser", // Generic/AOSP
        "com.hsv.freeadblocker", "com.hsv.freeadblockerbrowser"
    )

    /**
     * Update current package from AccessibilityEvent
     */
    fun updateFromEvent(event: AccessibilityEvent?) {
        event?.packageName?.let {
            currentPackageName = it.toString()
        }
    }

    /**
     * Get current foreground package name
     */
    fun getCurrentPackage(): String? = currentPackageName

    /**
     * Check if current app is a safe/system package immune to NSFW detection
     */
    fun isSafePackage(): Boolean {
        return currentPackageName in safePackages
    }

    /**
     * Check if current app is Twitter/X
     */
    fun isTwitter(): Boolean {
        return currentPackageName == "com.twitter.android"
    }

    /**
     * Check if current app is TikTok
     */
    fun isTikTok(): Boolean {
        return currentPackageName == "com.zhiliaoapp.musically" ||
               currentPackageName == "com.ss.android.ugc.aweme"
    }

    /**
     * Check if current app is Instagram
     */
    fun isInstagram(): Boolean {
        return currentPackageName == "com.instagram.android"
    }

    /**
     * Check if current app is Reddit
     */
    fun isReddit(): Boolean {
        return currentPackageName == "com.reddit.frontpage"
    }

    /**
     * Check if current app is a social media app
     */
    fun isSocialMedia(): Boolean {
        return currentPackageName in socialMediaPackages
    }

    /**
     * Check if current app is a video streaming app
     */
    fun isVideoStreaming(): Boolean {
        return currentPackageName in videoStreamingPackages
    }

    /**
     * Check if current app is a dating app (higher NSFW risk)
     */
    fun isDatingApp(): Boolean {
        return currentPackageName in datingAppPackages
    }

    /**
     * Check if current app is a blacklisted browser (except Chrome)
     */
    fun isBlacklistedBrowser(): Boolean {
        return currentPackageName in blacklistedBrowsers
    }

    /**
     * Check if current app is Telegram
     */
    fun isTelegram(): Boolean {
        return currentPackageName == "org.telegram.messenger"
    }

    /**
     * Check if current app is blacklisted (browser except Chrome, Telegram, or blacklisted social media)
     */
    fun isBlacklistedApp(): Boolean {
        // Chrome package: com.android.chrome
        return (isBlacklistedBrowser() || isTelegram() || isBlacklistedSocialMedia())
    }

    /**
     * Get risk level for current app
     * @return Risk multiplier (1.0 = normal, higher = more strict detection)
     */
    fun getRiskMultiplier(): Float {
        return when {
            isDatingApp() -> 0.7f        // More strict for dating apps
            isSocialMedia() -> 0.85f     // Moderately strict for social media
            isVideoStreaming() -> 0.9f   // Slightly strict for video apps
            else -> 1.0f                 // Normal threshold for other apps
        }
    }

    /**
     * Get adjusted NSFW threshold based on current app
     * @param baseThreshold The base threshold (default 0.7)
     * @return Adjusted threshold
     */
    fun getAdjustedThreshold(baseThreshold: Float = 0.7f): Float {
        return baseThreshold * getRiskMultiplier()
    }

    /**
     * Check if current app is a blacklisted social media app
     * Currently: Twitter/X and Reddit only
     */
    fun isBlacklistedSocialMedia(): Boolean {
        return currentPackageName == "com.twitter.android" ||  // Twitter/X
               currentPackageName == "com.reddit.frontpage"     // Reddit
    }

    /**
     * Get app category name for logging
     */
    fun getAppCategory(): String {
        return when {
            isDatingApp() -> "Dating App"
            isTwitter() -> "Twitter/X"
            isTikTok() -> "TikTok"
            isInstagram() -> "Instagram"
            isReddit() -> "Reddit"
            isSocialMedia() -> "Social Media"
            isVideoStreaming() -> "Video Streaming"
            else -> "Other"
        }
    }

    /**
     * Track if current foreground app is a blacklisted social media app
     * (Twitter/X or Reddit) and increment counter similar to trackIfBlank
     *
     * @param context The context to show warnings/dialogs
     */
    fun trackIfBlacklisted(context: Context) {
        if (ownerScope == null) return

        ownerScope.launch(Dispatchers.Main) {
            try {
                val isBlacklisted = isBlacklistedApp()

                if (!isBlacklisted) {
                    return@launch
                }

                // Show warning dialog similar to incognito warning
                context.showIncognitoWarning { isManualDismiss ->
                    if (!isManualDismiss) {
                        return@showIncognitoWarning
                    }
                    ownerScope.launch(Dispatchers.Main) {
                        sharedPreferences.setCurrentBlankImageCounter(
                            sharedPreferences.getCurrentBlankImageCounter() + 1
                        )
                        sharedPreferences.setCurrentSafeCounter(
                            (sharedPreferences.getCurrentSafeCounter() - 1).coerceAtLeast(0)
                        )
                    }
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }
}
