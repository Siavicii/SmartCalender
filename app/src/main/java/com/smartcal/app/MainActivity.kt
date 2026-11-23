package com.smartcal.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import android.view.WindowManager
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.util.TimeZone
import org.json.JSONArray
import android.provider.CalendarContract
import android.content.ContentValues
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val GITHUB_REPO = "Siavicii/SmartCalender-Android"
    private val CHANNEL_ID = "smartcal_status_date_channel"
    private val ALERT_CHANNEL_ID = "smartcal_alerts_channel"
    private val NOTIF_ID = 6201
    private var alertSeqId = 6300

    private var pendingWebPermRequest: PermissionRequest? = null
    private var lastBarColor: Int = Color.parseColor("#F4F6FA")
    private var lastLightBg: Boolean = true

    private fun applySystemBars(colorInt: Int, lightBackground: Boolean) {
        lastBarColor = colorInt
        lastLightBg = lightBackground
        try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = colorInt
            window.navigationBarColor = colorInt

            val ctrl = WindowInsetsControllerCompat(window, window.decorView)
            ctrl.isAppearanceLightStatusBars = lightBackground
            ctrl.isAppearanceLightNavigationBars = lightBackground

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(
                    if (lightBackground) mask else 0,
                    mask
                )
            } else {
                @Suppress("DEPRECATION")
                var flags = window.decorView.systemUiVisibility
                @Suppress("DEPRECATION")
                flags = if (lightBackground) {
                    flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    flags = if (lightBackground) {
                        flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    } else {
                        flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                    }
                }
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = flags
            }
        } catch (_: Exception) {}
    }

    private var hasRequestedStartupPerms = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applySystemBars(lastBarColor, lastLightBg)
        }
    }

    private var isRequestingPermission = false
    private var userLeftApp = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isRequestingPermission) {
            userLeftApp = true
        }
    }

    override fun onStop() {
        super.onStop()
        // Only switch launcher activity-alias when user truly leaves the app (never during first launch or permission dialogs)
        if (!isChangingConfigurations && !isRequestingPermission) {
            userLeftApp = false
            try {
                IconUpdater.updateTodayIcon(this)
            } catch (_: Exception) {}
        }
    }

    @Suppress("DEPRECATION")
    private fun enableHighRefreshRate() {
        try {
            val disp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                windowManager.defaultDisplay
            } ?: return
            val modes = disp.supportedModes
            if (modes.isNullOrEmpty()) return
            val curMode = disp.mode
            val sameResModes = modes.filter {
                it.physicalWidth == curMode.physicalWidth && it.physicalHeight == curMode.physicalHeight
            }
            val bestMode = (if (sameResModes.isNotEmpty()) sameResModes else modes.toList())
                .maxByOrNull { it.refreshRate } ?: return
            val lp = window.attributes
            if (lp.preferredDisplayModeId != bestMode.modeId || lp.preferredRefreshRate != bestMode.refreshRate) {
                lp.preferredDisplayModeId = bestMode.modeId
                lp.preferredRefreshRate = bestMode.refreshRate
                window.attributes = lp
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        enableHighRefreshRate()
        val isNightNow = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bootBgColor = if (isNightNow) Color.parseColor("#0F172A") else Color.parseColor("#F4F6FA")
        window.decorView.setBackgroundColor(bootBgColor)
        applySystemBars(bootBgColor, !isNightNow)
        webView = WebView(this).apply {
            setBackgroundColor(bootBgColor)
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
        setContentView(webView)
        webView.postDelayed({ checkForGitHubUpdate() }, 2200L)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setGeolocationEnabled(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!hasRequestedStartupPerms) {
                    hasRequestedStartupPerms = true
                    webView.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            requestEssentialPermissionsIfNeeded()
                        }
                    }, 1500L)
                }
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val wantsAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                if (wantsAudio && ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingWebPermRequest = request
                    isRequestingPermission = true
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        2002
                    )
                } else {
                    try {
                        request.grant(request.resources)
                    } catch (_: Exception) {}
                }
            }
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusChannel = NotificationChannel(
                CHANNEL_ID,
                "تاریخ روز در نوار وضعیت",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش دائمی تاریخ روز در نوار وضعیت بالای گوشی"
                setShowBadge(false)
            }
            nm.createNotificationChannel(statusChannel)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "هشدار هوا، آلودگی و یادآورها",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ارسال هشدار تغییر هوا، آلودگی هوا و یادآور یادداشت‌ها"
                setShowBadge(true)
            }
            nm.createNotificationChannel(alertChannel)
        }
    }

    private fun requestEssentialPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        // Permissions (microphone, notifications, location, calendar) are requested on-demand only.
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        isRequestingPermission = false
        if (requestCode == 1004) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            runOnUiThread {
                webView.evaluateJavascript("window.onNativeCalendarPermissionResult && window.onNativeCalendarPermissionResult($granted);", null)
            }
        }
        val req = pendingWebPermRequest
        pendingWebPermRequest = null
        if (req != null) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            runOnUiThread {
                try {
                    if (granted) req.grant(req.resources) else req.deny()
                } catch (_: Exception) {}
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val lParts = latest.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val cParts = current.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val l = lParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun checkForGitHubUpdate(manual: Boolean = false) {
        Thread {
            try {
                val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "SmartCalender-Android")
                }
                if (conn.responseCode != 200) {
                    if (manual) runOnUiThread {
                        android.widget.Toast.makeText(this@MainActivity, "خطا در ارتباط با سرور گیت‌هاب", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val latestTag = json.optString("tag_name", "").removePrefix("v").trim()
                val assets = json.optJSONArray("assets")

                var apkUrl = json.optString("html_url", "")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", apkUrl)
                            break
                        }
                    }
                }

                val currentVer = packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
                // Unified update notification is handled cleanly by the in-app banner (#appUpdateBanner)
                // to avoid showing two overlapping update prompts after upgrading.
                if (latestTag.isNotEmpty() && isNewerVersion(latestTag, currentVer) && apkUrl.isNotEmpty()) {
                    // Handled by checkForAppUpdate() inside WebView
                }
             else if (manual) {
                    runOnUiThread {
                        android.widget.Toast.makeText(this@MainActivity, "شما از آخرین نسخه ($currentVer) استفاده می‌کنید ✅", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun createDayNumberBitmap(dayText: String): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isFakeBoldText = false
            style = Paint.Style.FILL
            textSize = 100f
        }
        val bounds = Rect()
        paint.getTextBounds(dayText, 0, dayText.length, bounds)
        val target = size * 0.88f
        val scale = minOf(
            target / bounds.width().coerceAtLeast(1),
            target / bounds.height().coerceAtLeast(1)
        )
        paint.textSize = paint.textSize * scale
        paint.getTextBounds(dayText, 0, dayText.length, bounds)
        val x = size / 2f - bounds.exactCenterX()
        val y = size / 2f - bounds.exactCenterY()
        canvas.drawText(dayText, x, y, paint)
        return bmp
    }


    private fun pushNativeDarkStateToWebView(isNight: Boolean) {
        if (!this::webView.isInitialized) return
        runOnUiThread {
            webView.evaluateJavascript(
                "window.__nativeSystemDark=$isNight; if(typeof applyCfg==='function'){applyCfg();} if(typeof render==='function'){render();}",
                null
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isNight = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        pushNativeDarkStateToWebView(isNight)
    }

    override fun onResume() {
        super.onResume()
        userLeftApp = false
        isRequestingPermission = false
        enableHighRefreshRate()
        if (::webView.isInitialized) { try { webView.onResume() } catch (_: Exception) {} }
        // Do NOT toggle launcher activity-alias in onResume(); Android kills the foreground Activity on first install!
        // Only schedule the midnight alarm safely here; icon alias swap happens cleanly in onStop().
        try {
            IconUpdater.scheduleNextMidnight(this)
        } catch (_: Exception) {}
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        pushNativeDarkStateToWebView(isNight)
    }

    override fun onDestroy() {
        if (this::webView.isInitialized) {
            try {
                webView.stopLoading()
                webView.removeJavascriptInterface("AndroidBridge")
                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                webView.clearHistory()
                webView.destroy()
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun syncStatusBarColor(hexColor: String, isLightIcons: Boolean) {
            runOnUiThread {
                try {
                    val parsedColor = Color.parseColor(hexColor)
                    // Use actual color luminance so light backgrounds ALWAYS get dark/black icons
                    val isLightBg = ColorUtils.calculateLuminance(parsedColor) > 0.45
                    applySystemBars(parsedColor, isLightBg)
                } catch (_: Exception) {}
            }
        }


        @JavascriptInterface
        fun syncCalendarAndLangToIcon(calType: String, lang: String) {
            runOnUiThread {
                try {
                    IconUpdater.updatePreferencesAndIcon(this@MainActivity, calType, lang, deferIconSwitch = true)
                } catch (_: Exception) {}
            }
        }



        @JavascriptInterface
        fun openExternalUrl(url: String) {
            runOnUiThread {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface
        fun showNativeToast(msg: String) {
            runOnUiThread {
                try {
                    android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface
        fun requestMicAndNotifPermissions() {
            runOnUiThread { requestEssentialPermissionsIfNeeded() }
        }

        @JavascriptInterface
        fun showAlertNotification(title: String, body: String): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    runOnUiThread { requestEssentialPermissionsIfNeeded() }
                    return false
                }
                val launchIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pi = PendingIntent.getActivity(
                    this@MainActivity,
                    alertSeqId,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notif = NotificationCompat.Builder(this@MainActivity, ALERT_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pi)
                    .build()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(alertSeqId++, notif)
                true
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun showStatusDateNotification(dayNum: String, title: String, body: String): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    runOnUiThread { requestEssentialPermissionsIfNeeded() }
                    return false
                }
                val launchIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pi = PendingIntent.getActivity(
                    this@MainActivity,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val dayIcon = IconCompat.createWithBitmap(createDayNumberBitmap(dayNum))
                val notif = NotificationCompat.Builder(this@MainActivity, CHANNEL_ID)
                    .setSmallIcon(dayIcon)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setOngoing(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setContentIntent(pi)
                    .build()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, notif)
                IconUpdater.setStatusNotifEnabled(this@MainActivity, true)
                true
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun cancelStatusDateNotification() {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIF_ID)
                IconUpdater.setStatusNotifEnabled(this@MainActivity, false)
            } catch (_: Exception) {}
        }


        @JavascriptInterface
        fun hasCalendarPermission(): Boolean {
            val r = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            val w = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
            return r && w
        }

        @JavascriptInterface
        fun requestCalendarPermission() {
            runOnUiThread {
                isRequestingPermission = true
                    ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                    1004
                )
            }
        }

        @JavascriptInterface
        fun getCalendarEvents(startMs: Long, endMs: Long): String {
            if (!hasCalendarPermission()) return "[]"
            val arr = JSONArray()
            try {
                val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                android.content.ContentUris.appendId(builder, startMs)
                android.content.ContentUris.appendId(builder, endMs)
                val proj = arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.CALENDAR_DISPLAY_NAME
                )
                contentResolver.query(builder.build(), proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cur ->
                    var count = 0
                    while (cur.moveToNext() && count < 300) {
                        val obj = JSONObject()
                        obj.put("title", cur.getString(0) ?: "")
                        obj.put("startMs", cur.getLong(1))
                        obj.put("endMs", cur.getLong(2))
                        obj.put("allDay", cur.getInt(3) == 1)
                        obj.put("calendarName", cur.getString(4) ?: "Google")
                        arr.put(obj)
                        count++
                    }
                }
            } catch (_: Exception) {}
            return arr.toString()
        }

        @JavascriptInterface
        fun addCalendarEvent(title: String, desc: String, startMs: Long, endMs: Long, allDay: Boolean): Boolean {
            if (!hasCalendarPermission()) return false
            return try {
                var calId = -1L
                val proj = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
                val sel = "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
                contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, sel, null, "${CalendarContract.Calendars.IS_PRIMARY} DESC")?.use { c ->
                    if (c.moveToFirst()) calId = c.getLong(0)
                }
                if (calId == -1L) return false
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calId)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, desc)
                    put(CalendarContract.Events.DTSTART, startMs)
                    put(CalendarContract.Events.DTEND, if (endMs > startMs) endMs else startMs + 3600000L)
                    put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }
                contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
            } catch (_: Exception) {
                false
            }
        }


        @JavascriptInterface
        fun checkUpdateManual() {
            runOnUiThread {
                android.widget.Toast.makeText(this@MainActivity, "در حال بررسی نسخه جدید...", android.widget.Toast.LENGTH_SHORT).show()
            }
            checkForGitHubUpdate(true)
        }

        @JavascriptInterface
        fun scheduleNativeAlarm(triggerMs: Long, title: String, body: String, reqCode: Int) {
            try {
                val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val intent = Intent(this@MainActivity, AlarmReceiver::class.java).apply {
                    putExtra("title", title)
                    putExtra("body", body)
                    putExtra("reqCode", reqCode)
                }
                val pi = PendingIntent.getBroadcast(
                    this@MainActivity, reqCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerMs, pi)
                } else {
                    am.set(android.app.AlarmManager.RTC_WAKEUP, triggerMs, pi)
                }
            } catch (_: Exception) {}
        }


        @JavascriptInterface
        fun updateWidgetData(city: String, weather: String, summary: String) {
            SmartCalWidgetProvider.saveContent(this@MainActivity, city, weather, summary)
        }

        @JavascriptInterface
        fun isSystemDarkMode(): Boolean {
            return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        @JavascriptInterface
        fun getSystemThemeJson(): String {
            return try {
                val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                val brand = (Build.MANUFACTURER ?: "Android").trim()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    fun hexOf(resId: Int): String {
                        val c = ContextCompat.getColor(this@MainActivity, resId)
                        return String.format("#%06X", 0xFFFFFF and c)
                    }
                    val priLight = hexOf(android.R.color.system_accent1_600)
                    val priDark = hexOf(android.R.color.system_accent1_200)
                    val bgLight = hexOf(android.R.color.system_neutral1_50)
                    val cardLight = hexOf(android.R.color.system_neutral1_10)
                    val subLight = hexOf(android.R.color.system_neutral2_100)
                    val bgDark = hexOf(android.R.color.system_neutral1_900)
                    val cardDark = hexOf(android.R.color.system_neutral1_800)
                    val subDark = hexOf(android.R.color.system_neutral2_700)
                    """{"supported":true,"isDark":$isNight,"uiStyle":"android17","brand":"$brand","priLight":"$priLight","priDark":"$priDark","bgLight":"$bgLight","cardLight":"$cardLight","subLight":"$subLight","bgDark":"$bgDark","cardDark":"$cardDark","subDark":"$subDark"}"""
                } else {
                    """{"supported":false,"isDark":$isNight,"uiStyle":"android17","brand":"$brand"}"""
                }
            } catch (_: Exception) {
                """{"supported":false,"isDark":false,"uiStyle":"android17","brand":"Android"}"""
            }
        }

        @JavascriptInterface
        fun hasNotificationPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        @JavascriptInterface
        fun requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runOnUiThread {
                    isRequestingPermission = true
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        1011
                    )
                }
            }
        }

        @JavascriptInterface
        fun hasLocationPermission(): Boolean {
            val fine = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }

        @JavascriptInterface
        fun requestLocationPermission() {
            runOnUiThread {
                isRequestingPermission = true
                    ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    1012
                )
            }
        }

        @JavascriptInterface
        fun syncWidgetCalendarData(json: String) {
            SmartCalMonthWidgetProvider.saveCalendarSync(this@MainActivity, json)
        }

        @JavascriptInterface
        fun syncWidgetTheme(uiStyle: String, themeMode: String, accentHex: String, calType: String, lang: String) {
            SmartCalWidgetProvider.saveTheme(this@MainActivity, uiStyle, themeMode, accentHex, calType, lang)
        }

        @JavascriptInterface
        fun fetchUrlNative(urlString: String): String {
            return try {
                val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) SmartCal/63.0")
                }
                if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else ""
            } catch (_: Exception) {
                ""
            }
        }
    }
}
