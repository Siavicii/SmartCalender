package com.smartcal.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import java.util.Calendar

object IconUpdater {
    const val ACTION_MIDNIGHT_UPDATE = "com.smartcal.app.ACTION_MIDNIGHT_ICON_UPDATE"
    private const val PREFS = "smartcal_icon_prefs"
    private const val KEY_LAST_ALIAS = "last_enabled_alias"
    private const val KEY_CAL_TYPE = "cal_type"
    private const val KEY_LANG = "app_lang"
    private const val KEY_STATUS_NOTIF_ENABLED = "status_notif_enabled"
    private const val DEFAULT_ALIAS = ".M8D30"
    private const val STATUS_CHANNEL_ID = "smartcal_status_date_channel"
    private const val STATUS_NOTIF_ID = 6201

    private val FA_MONTHS = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    private val KU_MONTHS = arrayOf(
        "خاکەلێوە", "گوڵان", "جۆزەردان", "پووشپەڕ", "گەلاوێژ", "خەرمانان",
        "ڕەزبەر", "گەڵاڕێزان", "سەرماوەز", "بەفرانبار", "ڕێبەندان", "ڕەشەمە"
    )
    private val EN_MONTHS = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private fun toPersianDigits(n: Int): String {
        val map = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return n.toString().map { if (it in '0'..'9') map[it - '0'] else it }.joinToString("")
    }

    fun setStatusNotifEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STATUS_NOTIF_ENABLED, enabled)
            .apply()
    }

    fun updatePreferencesAndIcon(context: Context, calType: String?, lang: String?, deferIconSwitch: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (!calType.isNullOrBlank()) editor.putString(KEY_CAL_TYPE, calType)
        if (!lang.isNullOrBlank()) editor.putString(KEY_LANG, lang)
        editor.apply()
        if (!deferIconSwitch) {
            updateTodayIcon(context)
        } else {
            scheduleNextMidnight(context)
        }
        refreshStatusDateNotificationIfEnabled(context)
    }

    fun updateTodayIcon(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val calType = prefs.getString(KEY_CAL_TYPE, "jalali") ?: "jalali"
        val lang = prefs.getString(KEY_LANG, "fa") ?: "fa"

        val cal = Calendar.getInstance()
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val jalali = JalaliHelper.gregorianToJalali(gy, gm, gd)

        val targetAlias = when {
            calType == "gregorian" || lang == "en" -> {
                val gMax = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[(gm - 1).coerceIn(0, 11)]
                ".GM${gm.coerceIn(1, 12)}D${gd.coerceIn(1, gMax)}"
            }
            calType == "kurdish" || lang == "ku" -> {
                val jm = jalali.month.coerceIn(1, 12)
                val maxD = if (jm <= 6) 31 else 30
                ".KM${jm}D${jalali.day.coerceIn(1, maxD)}"
            }
            else -> {
                val jm = jalali.month.coerceIn(1, 12)
                val maxD = if (jm <= 6) 31 else 30
                ".M${jm}D${jalali.day.coerceIn(1, maxD)}"
            }
        }

        val lastAlias = prefs.getString(KEY_LAST_ALIAS, DEFAULT_ALIAS) ?: DEFAULT_ALIAS

        if (lastAlias != targetAlias) {
            val pm = context.packageManager
            val pkg = context.packageName

            try {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, "$pkg$targetAlias"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {}

            try {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, "$pkg$lastAlias"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {}

            if (lastAlias != DEFAULT_ALIAS && targetAlias != DEFAULT_ALIAS) {
                try {
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, "$pkg$DEFAULT_ALIAS"),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (_: Exception) {}
            }

            prefs.edit().putString(KEY_LAST_ALIAS, targetAlias).apply()
        }
        scheduleNextMidnight(context)
    }

    fun refreshStatusDateNotificationIfEnabled(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_STATUS_NOTIF_ENABLED, false)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val calType = prefs.getString(KEY_CAL_TYPE, "jalali") ?: "jalali"
            val lang = prefs.getString(KEY_LANG, "fa") ?: "fa"

            val cal = Calendar.getInstance()
            val gy = cal.get(Calendar.YEAR)
            val gm = cal.get(Calendar.MONTH) + 1
            val gd = cal.get(Calendar.DAY_OF_MONTH)
            val jalali = JalaliHelper.gregorianToJalali(gy, gm, gd)

            val dayNum: String
            val shortMonth: String
            val title: String
            val body: String

            when {
                calType == "gregorian" || lang == "en" -> {
                    dayNum = gd.toString()
                    shortMonth = EN_MONTHS[(gm - 1).coerceIn(0, 11)].take(3).uppercase()
                    title = "$gd ${EN_MONTHS[(gm - 1).coerceIn(0, 11)]} $gy"
                    body = "Smart Cal"
                }
                calType == "kurdish" || lang == "ku" -> {
                    val kuYear = jalali.year + 1321
                    dayNum = toPersianDigits(jalali.day)
                    shortMonth = KU_MONTHS[(jalali.month - 1).coerceIn(0, 11)]
                    title = "${toPersianDigits(jalali.day)}ی ${KU_MONTHS[(jalali.month - 1).coerceIn(0, 11)]} ${toPersianDigits(kuYear)}"
                    body = "$gd ${EN_MONTHS[(gm - 1).coerceIn(0, 11)]} $gy"
                }
                else -> {
                    dayNum = toPersianDigits(jalali.day)
                    shortMonth = FA_MONTHS[(jalali.month - 1).coerceIn(0, 11)]
                    title = "${toPersianDigits(jalali.day)} ${FA_MONTHS[(jalali.month - 1).coerceIn(0, 11)]} ${toPersianDigits(jalali.year)}"
                    body = "$gd ${EN_MONTHS[(gm - 1).coerceIn(0, 11)]} $gy"
                }
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dayIcon = IconCompat.createWithBitmap(createDayNumberBitmap(dayNum))
            val notif = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
                .setSmallIcon(dayIcon)
                .setContentTitle(title)
                .setContentText(body)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pi)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(STATUS_NOTIF_ID, notif)
        } catch (_: Exception) {}
    }

    fun createDateCardBitmap(dayText: String, monthText: String): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 44f, 44f, bgPaint)

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6EA8FF")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(68f, 18f, 124f, 24f, 3f, 3f, pillPaint)

        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = if (dayText.length >= 2) 86f else 96f
            textAlign = Paint.Align.CENTER
        }
        val numBounds = Rect()
        numPaint.getTextBounds(dayText, 0, dayText.length, numBounds)
        canvas.drawText(dayText, size / 2f, 82f - numBounds.exactCenterY(), numPaint)

        val monPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6EA8FF")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = if (monthText.length >= 7) 25f else 31f
            textAlign = Paint.Align.CENTER
        }
        val monBounds = Rect()
        monPaint.getTextBounds(monthText, 0, monthText.length, monBounds)
        canvas.drawText(monthText, size / 2f, 148f - monBounds.exactCenterY(), monPaint)

        return bmp
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

    fun scheduleNextMidnight(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DateChangeReceiver::class.java).apply {
            action = ACTION_MIDNIGHT_UPDATE
        }
        val pi = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnight.timeInMillis, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, nextMidnight.timeInMillis, pi)
        }
    }
}
