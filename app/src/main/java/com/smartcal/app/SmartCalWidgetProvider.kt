package com.smartcal.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

class SmartCalWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateSingleWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        const val PREFS = "smartcal_widget_prefs"
        const val KEY_UI = "w_ui"
        const val KEY_THEME = "w_theme"
        const val KEY_ACCENT = "w_accent"
        const val KEY_CAL = "w_cal"
        const val KEY_LANG = "w_lang"

        val FA_MONTHS = arrayOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )
        val KU_MONTHS = arrayOf(
            "خاکەلێوە", "گوڵان", "جۆزەردان", "پووشپەڕ", "گەلاوێژ", "خەرمانان",
            "ڕەزبەر", "گەڵاڕێزان", "سەرماوەز", "بەفرانبار", "ڕێبەندان", "ڕەشەمە"
        )
        val EN_MONTHS_SHORT = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        val EN_MONTHS_FULL = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val FA_DAYS = arrayOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه", "شنبه")
        val KU_DAYS = arrayOf("یەکشەممە", "دووشەممە", "سێشەممە", "چوارشەممە", "پێنجشەممە", "هەینی", "شەممە")
        val EN_DAYS = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

        fun toFaDigits(n: Int): String {
            val map = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
            return n.toString().map { if (it in '0'..'9') map[it - '0'] else it }.joinToString("")
        }

        fun saveTheme(context: Context, ui: String?, theme: String?, accent: String?, calType: String?, lang: String?) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (!ui.isNullOrBlank()) prefs.putString(KEY_UI, ui)
            if (!theme.isNullOrBlank()) prefs.putString(KEY_THEME, theme)
            if (!accent.isNullOrBlank()) prefs.putString(KEY_ACCENT, accent)
            if (!calType.isNullOrBlank()) prefs.putString(KEY_CAL, calType)
            if (!lang.isNullOrBlank()) prefs.putString(KEY_LANG, lang)
            prefs.apply()
            updateAllWidgets(context)
        }

        // Kept for backward compatibility with JS bridge (no weather/clutter shown on widgets)
        fun saveContent(context: Context, city: String?, weather: String?, summary: String?) {
            // Intentionally unused: pure minimalist One UI 9 calendar widgets
        }

        fun updateAllWidgets(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val cn = ComponentName(context, SmartCalWidgetProvider::class.java)
                val ids = mgr.getAppWidgetIds(cn)
                for (id in ids) {
                    updateSingleWidget(context, mgr, id)
                }
            } catch (_: Exception) {}
            SmartCalMonthWidgetProvider.updateAllWidgets(context)
        }

        data class ThemePalette(
            val bgStart: Int,
            val bgEnd: Int,
            val stroke: Int,
            val strokeWidth: Float,
            val radiusDp: Float,
            val txtPri: Int,
            val txtSec: Int,
            val accent: Int,
            val weekend: Int
        )

        fun resolvePalette(context: Context): ThemePalette {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val ui = prefs.getString(KEY_UI, "android17") ?: "android17"
            val themeMode = prefs.getString(KEY_THEME, "auto") ?: "auto"
            val accentHex = prefs.getString(KEY_ACCENT, "#1B6EF3") ?: "#1B6EF3"
            val sysDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isDark = themeMode == "dark" || (themeMode == "auto" && sysDark)

            val acc = try { Color.parseColor(accentHex) } catch (_: Exception) {
                if (isDark) Color.parseColor("#6EA8FF") else Color.parseColor("#1B6EF3")
            }
            val wknd = if (isDark) Color.parseColor("#F87171") else Color.parseColor("#DC2626")

            return when (ui) {
                "ios26" -> if (isDark) {
                    ThemePalette(Color.parseColor("#DD161C2E"), Color.parseColor("#DD1D2740"), Color.parseColor("#40FFFFFF"), 2.0f, 30f, Color.parseColor("#F8FAFC"), Color.parseColor("#CBD5E1"), acc, wknd)
                } else {
                    ThemePalette(Color.parseColor("#EBEEF5FF"), Color.parseColor("#EBF4F0FF"), Color.parseColor("#D8FFFFFF"), 2.2f, 30f, Color.parseColor("#0F172A"), Color.parseColor("#475569"), acc, wknd)
                }
                "android17" -> if (isDark) {
                    ThemePalette(Color.parseColor("#F2171C24"), Color.parseColor("#F21C222D"), Color.parseColor("#2694A3B8"), 1.5f, 28f, Color.parseColor("#F8FAFC"), Color.parseColor("#94A3B8"), acc, wknd)
                } else {
                    ThemePalette(Color.parseColor("#F5FFFFFF"), Color.parseColor("#F5F4F7FC"), Color.parseColor("#1F1B6EF3"), 1.5f, 28f, Color.parseColor("#0F172A"), Color.parseColor("#64748B"), acc, wknd)
                }
                "glass" -> if (isDark) {
                    ThemePalette(Color.parseColor("#D0141B2D"), Color.parseColor("#D01E1B4B"), Color.parseColor("#3DFFFFFF"), 1.8f, 28f, Color.parseColor("#F8FAFC"), Color.parseColor("#CBD5E1"), acc, wknd)
                } else {
                    ThemePalette(Color.parseColor("#DCEAF2FE"), Color.parseColor("#DCF3E8FF"), Color.parseColor("#CCFFFFFF"), 1.8f, 28f, Color.parseColor("#0F172A"), Color.parseColor("#475569"), acc, wknd)
                }
                "cyber" -> ThemePalette(
                    Color.parseColor("#EE12092A"), Color.parseColor("#EE071A26"), Color.parseColor("#998B5CF6"), 2.2f, 20f, Color.parseColor("#F8FAFC"), Color.parseColor("#A5B4FC"), acc, Color.parseColor("#FB7185")
                )
                "minimal" -> if (isDark) {
                    ThemePalette(Color.parseColor("#F5111113"), Color.parseColor("#F5111113"), Color.parseColor("#33FFFFFF"), 1.6f, 16f, Color.parseColor("#FAFAFA"), Color.parseColor("#A1A1AA"), acc, wknd)
                } else {
                    ThemePalette(Color.parseColor("#FAFFFFFF"), Color.parseColor("#FAFFFFFF"), Color.parseColor("#24000000"), 1.6f, 16f, Color.parseColor("#09090B"), Color.parseColor("#52525B"), acc, wknd)
                }
                "bento" -> if (isDark) {
                    ThemePalette(Color.parseColor("#F5161B24"), Color.parseColor("#F5161B24"), Color.parseColor("#22FFFFFF"), 1.4f, 30f, Color.parseColor("#F8FAFC"), Color.parseColor("#94A3B8"), acc, wknd)
                } else {
                    ThemePalette(Color.parseColor("#FAFFFFFF"), Color.parseColor("#FAF8FAFC"), Color.parseColor("#180F172A"), 1.4f, 30f, Color.parseColor("#0F172A"), Color.parseColor("#64748B"), acc, wknd)
                }
                "neumorph" -> if (isDark) {
                    ThemePalette(Color.parseColor("#F51A1E26"), Color.parseColor("#F514171E"), Color.parseColor("#18FFFFFF"), 1.6f, 26f, Color.parseColor("#F1F5F9"), Color.parseColor("#94A3B8"), acc, wknd)
                } else {
                    ThemePalette(Color.parseColor("#F5E8ECF2"), Color.parseColor("#F5DFE4EC"), Color.parseColor("#CCFFFFFF"), 1.8f, 26f, Color.parseColor("#1E293B"), Color.parseColor("#64748B"), acc, wknd)
                }
                "persian" -> if (isDark) {
                    ThemePalette(Color.parseColor("#F21C1712"), Color.parseColor("#F2261E16"), Color.parseColor("#55B4823C"), 1.8f, 24f, Color.parseColor("#FDF8F0"), Color.parseColor("#D6C3A5"), acc, wknd)
                } else {
                    ThemePalette(Color.parseColor("#F5FCF9F2"), Color.parseColor("#F5F3EBD9"), Color.parseColor("#55B4823C"), 1.8f, 24f, Color.parseColor("#2C2217"), Color.parseColor("#78634B"), acc, wknd)
                }
                "nordic" -> if (isDark) {
                    ThemePalette(Color.parseColor("#F21E293B"), Color.parseColor("#F20F172A"), Color.parseColor("#3394A3B8"), 1.5f, 24f, Color.parseColor("#F8FAFC"), Color.parseColor("#94A3B8"), acc, wknd)
                } else {
                    ThemePalette(Color.parseColor("#F5F8FAFC"), Color.parseColor("#F5E8EEF2"), Color.parseColor("#220F172A"), 1.5f, 24f, Color.parseColor("#0F172A"), Color.parseColor("#475569"), acc, wknd)
                }
                else -> if (isDark) {
                    ThemePalette(Color.parseColor("#EB161B26"), Color.parseColor("#EB111620"), Color.parseColor("#24FFFFFF"), 1.5f, 26f, Color.parseColor("#F8FAFC"), Color.parseColor("#94A3B8"), acc, wknd)
                } else {
                    ThemePalette(Color.parseColor("#F2FFFFFF"), Color.parseColor("#F2F4F7FB"), Color.parseColor("#180F172A"), 1.5f, 26f, Color.parseColor("#0F172A"), Color.parseColor("#64748B"), acc, wknd)
                }
            }
        }

        fun renderThemeCardBitmap(palette: ThemePalette, w: Int = 600, h: Int = 600): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val pad = palette.strokeWidth + 2f
            val rect = RectF(pad, pad, w - pad, h - pad)
            val rad = palette.radiusDp * 2.6f

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), palette.bgStart, palette.bgEnd, Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(rect, rad, rad, fillPaint)

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = palette.strokeWidth * 1.4f
                color = palette.stroke
            }
            canvas.drawRoundRect(rect, rad, rad, strokePaint)
            return bmp
        }

        private fun updateSingleWidget(context: Context, mgr: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val calType = prefs.getString(KEY_CAL, "jalali") ?: "jalali"
            val lang = prefs.getString(KEY_LANG, "fa") ?: "fa"
            val palette = resolvePalette(context)

            val cal = Calendar.getInstance()
            val gy = cal.get(Calendar.YEAR)
            val gm = cal.get(Calendar.MONTH) + 1
            val gd = cal.get(Calendar.DAY_OF_MONTH)
            val dow = (cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
            val jalali = JalaliHelper.gregorianToJalali(gy, gm, gd)

            val weekdayStr: String
            val dayNumStr: String
            val monthYearStr: String
            val subDateStr: String

            when {
                calType == "gregorian" || lang == "en" -> {
                    weekdayStr = EN_DAYS[dow]
                    dayNumStr = gd.toString()
                    monthYearStr = "${EN_MONTHS_FULL[(gm - 1).coerceIn(0, 11)]} $gy"
                    subDateStr = "${toFaDigits(jalali.day)} ${FA_MONTHS[(jalali.month - 1).coerceIn(0, 11)]} ${toFaDigits(jalali.year)}"
                }
                calType == "kurdish" || lang == "ku" -> {
                    val kuYear = jalali.year + 1321
                    weekdayStr = KU_DAYS[dow]
                    dayNumStr = toFaDigits(jalali.day)
                    monthYearStr = "${KU_MONTHS[(jalali.month - 1).coerceIn(0, 11)]} ${toFaDigits(kuYear)}"
                    subDateStr = "$gd ${EN_MONTHS_SHORT[(gm - 1).coerceIn(0, 11)]} $gy"
                }
                else -> {
                    weekdayStr = FA_DAYS[dow]
                    dayNumStr = toFaDigits(jalali.day)
                    monthYearStr = "${FA_MONTHS[(jalali.month - 1).coerceIn(0, 11)]} ${toFaDigits(jalali.year)}"
                    subDateStr = "$gd ${EN_MONTHS_SHORT[(gm - 1).coerceIn(0, 11)]} $gy"
                }
            }

            val opts = mgr.getAppWidgetOptions(appWidgetId)
            val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 130)
            val isCompact = minH in 1..95

            val views = RemoteViews(context.packageName, R.layout.widget_smartcal)
            views.setImageViewBitmap(R.id.widgetBgImage, renderThemeCardBitmap(palette, 540, 540))

            views.setTextViewText(R.id.widgetWeekday, weekdayStr)
            views.setTextColor(R.id.widgetWeekday, palette.accent)

            views.setTextViewText(R.id.widgetDayNum, dayNumStr)
            views.setTextColor(R.id.widgetDayNum, palette.txtPri)
            views.setTextViewTextSize(R.id.widgetDayNum, TypedValue.COMPLEX_UNIT_SP, if (isCompact) 32f else 44f)

            views.setTextViewText(R.id.widgetMonthYear, monthYearStr)
            views.setTextColor(R.id.widgetMonthYear, palette.txtPri)

            if (isCompact) {
                views.setViewVisibility(R.id.widgetSubDate, View.GONE)
            } else {
                views.setViewVisibility(R.id.widgetSubDate, View.VISIBLE)
                views.setTextViewText(R.id.widgetSubDate, subDateStr)
                views.setTextColor(R.id.widgetSubDate, palette.txtSec)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 6460, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pi)

            mgr.updateAppWidget(appWidgetId, views)
        }
    }
}
