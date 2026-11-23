package com.smartcal.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import org.json.JSONObject
import java.util.Calendar

class SmartCalMonthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, id)
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val prefs = context.getSharedPreferences(SmartCalWidgetProvider.PREFS, Context.MODE_PRIVATE)
        val curOffset = prefs.getInt(KEY_MONTH_OFFSET, 0)
        when (action) {
            ACTION_PREV_MONTH -> {
                prefs.edit().putInt(KEY_MONTH_OFFSET, curOffset - 1).apply()
                updateAllWidgets(context)
            }
            ACTION_NEXT_MONTH -> {
                prefs.edit().putInt(KEY_MONTH_OFFSET, curOffset + 1).apply()
                updateAllWidgets(context)
            }
            ACTION_RESET_TODAY -> {
                prefs.edit().putInt(KEY_MONTH_OFFSET, 0).apply()
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        private const val ACTION_PREV_MONTH = "com.smartcal.app.WIDGET_PREV_MONTH"
        private const val ACTION_NEXT_MONTH = "com.smartcal.app.WIDGET_NEXT_MONTH"
        private const val ACTION_RESET_TODAY = "com.smartcal.app.WIDGET_RESET_TODAY"
        private const val KEY_MONTH_OFFSET = "w_month_offset"
        private const val KEY_CAL_SYNC_JSON = "w_cal_sync_json"

        // Base weekday labels starting from Saturday (0=Sat, 1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri)
        private val FA_WD_SAT = arrayOf("ش", "ی", "د", "س", "چ", "پ", "ج")
        private val KU_WD_SAT = arrayOf("ش", "ی", "د", "س", "چ", "پ", "هـ")
        private val EN_WD_SAT = arrayOf("S", "S", "M", "T", "W", "T", "F")

        fun saveCalendarSync(context: Context, json: String) {
            context.getSharedPreferences(SmartCalWidgetProvider.PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_CAL_SYNC_JSON, json).apply()
            updateAllWidgets(context)
        }

        fun resetAndUpdateAll(context: Context) {
            context.getSharedPreferences(SmartCalWidgetProvider.PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_MONTH_OFFSET, 0).apply()
            updateAllWidgets(context)
        }

        fun updateAllWidgets(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val cn = ComponentName(context, SmartCalMonthWidgetProvider::class.java)
                val ids = mgr.getAppWidgetIds(cn)
                for (id in ids) {
                    updateSingleWidget(context, mgr, id)
                }
            } catch (_: Exception) {}
        }

        private data class SyncedCalData(
            val firstDay: Int,
            val weekends: Set<Int>,
            val holidays: Set<String>,
            val events: Set<String>,
            val notes: Set<String>
        )

        private fun loadSyncedData(context: Context, isGreg: Boolean): SyncedCalData {
            val defaultFirst = if (isGreg) 1 else 0 // 1=Sun for Greg, 0=Sat for Jalali/Kurdish
            val defaultWknds = if (isGreg) setOf(1) else setOf(6) // 1=Sun, 6=Fri
            val raw = context.getSharedPreferences(SmartCalWidgetProvider.PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CAL_SYNC_JSON, null) ?: return SyncedCalData(defaultFirst, defaultWknds, emptySet(), emptySet(), emptySet())

            return try {
                val obj = JSONObject(raw)
                val fd = obj.optInt("firstDay", defaultFirst).coerceIn(0, 6)
                val wkSet = mutableSetOf<Int>()
                val wkArr = obj.optJSONArray("weekends")
                if (wkArr != null && wkArr.length() > 0) {
                    for (i in 0 until wkArr.length()) wkSet.add(wkArr.optInt(i))
                } else {
                    wkSet.addAll(defaultWknds)
                }
                fun toSet(key: String): Set<String> {
                    val out = HashSet<String>()
                    val arr = obj.optJSONArray(key) ?: return out
                    for (i in 0 until arr.length()) out.add(arr.optString(i))
                    return out
                }
                SyncedCalData(fd, wkSet, toSet("holidays"), toSet("events"), toSet("notes"))
            } catch (_: Exception) {
                SyncedCalData(defaultFirst, defaultWknds, emptySet(), emptySet(), emptySet())
            }
        }

        private fun renderMonthGridBitmap(
            palette: SmartCalWidgetProvider.Companion.ThemePalette,
            headers: Array<String>,
            isRtl: Boolean,
            startCol: Int,
            daysInMonth: Int,
            todayDay: Int,
            weekendCols: Set<Int>,
            holidayDays: Set<Int>,
            eventDays: Set<Int>,
            noteDays: Set<Int>,
            useFaDigits: Boolean,
            targetW: Int,
            targetH: Int
        ): Bitmap {
            val w = targetW.coerceIn(720, 1200)
            val h = targetH.coerceIn(420, 960)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val cols = 7
            val colW = w.toFloat() / cols
            val totalCells = startCol + daysInMonth
            val rows = if (totalCells > 35) 6 else 5
            val headerH = (h * 0.145f).coerceIn(58f, 92f)
            val rowH = (h - headerH - 8f) / rows
            val cellMin = minOf(colW, rowH)

            val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = (cellMin * 0.44f).coerceIn(32f, 52f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = (cellMin * 0.56f).coerceIn(40f, 66f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val todayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = (cellMin * 0.56f).coerceIn(40f, 66f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                color = Color.WHITE
            }
            val todayCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = palette.accent
            }
            val eventDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = palette.accent
            }
            val noteDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#10B981")
            }
            val bounds = Rect()

            // Draw weekday headers
            for (c in 0 until cols) {
                val drawCol = if (isRtl) (cols - 1 - c) else c
                val cx = drawCol * colW + colW / 2f
                headPaint.color = if (weekendCols.contains(c)) palette.weekend else palette.txtSec
                val label = headers[c]
                headPaint.getTextBounds(label, 0, label.length, bounds)
                canvas.drawText(label, cx, headerH / 2f - bounds.exactCenterY(), headPaint)
            }

            // Subtle divider
            val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.stroke
                strokeWidth = 2.2f
            }
            canvas.drawLine(12f, headerH, w - 12f, headerH, divPaint)

            // Draw month days with crisp typography, holiday red highlight, and event/note dots
            val circleRadius = (cellMin * 0.43f)
            val dotRadius = (cellMin * 0.068f).coerceIn(4.5f, 8.5f)
            for (d in 1..daysInMonth) {
                val idx = startCol + (d - 1)
                val r = idx / cols
                val c = idx % cols
                val drawCol = if (isRtl) (cols - 1 - c) else c
                val cx = drawCol * colW + colW / 2f
                val cy = headerH + 4f + r * rowH + rowH / 2f
                val numStr = if (useFaDigits) SmartCalWidgetProvider.toFaDigits(d) else d.toString()

                val isToday = (d == todayDay)
                val isHoliday = holidayDays.contains(d) || weekendCols.contains(c)
                val hasEvent = eventDays.contains(d)
                val hasNote = noteDays.contains(d)

                if (isToday) {
                    canvas.drawCircle(cx, cy, circleRadius, todayCirclePaint)
                    todayTextPaint.getTextBounds(numStr, 0, numStr.length, bounds)
                    val textOffsetY = if (hasEvent || hasNote) -dotRadius * 0.6f else 0f
                    canvas.drawText(numStr, cx, cy + textOffsetY - bounds.exactCenterY(), todayTextPaint)
                    if (hasEvent || hasNote) {
                        val whiteDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                        canvas.drawCircle(cx, cy + circleRadius * 0.62f, dotRadius * 0.85f, whiteDot)
                    }
                } else {
                    dayPaint.color = if (isHoliday) palette.weekend else palette.txtPri
                    dayPaint.getTextBounds(numStr, 0, numStr.length, bounds)
                    val textOffsetY = if (hasEvent || hasNote) -dotRadius * 0.7f else 0f
                    canvas.drawText(numStr, cx, cy + textOffsetY - bounds.exactCenterY(), dayPaint)

                    val dotY = cy + (cellMin * 0.34f)
                    if (hasEvent && hasNote) {
                        canvas.drawCircle(cx - dotRadius * 1.4f, dotY, dotRadius, eventDotPaint)
                        canvas.drawCircle(cx + dotRadius * 1.4f, dotY, dotRadius, noteDotPaint)
                    } else if (hasEvent) {
                        eventDotPaint.color = if (isHoliday) palette.weekend else palette.accent
                        canvas.drawCircle(cx, dotY, dotRadius, eventDotPaint)
                    } else if (hasNote) {
                        canvas.drawCircle(cx, dotY, dotRadius, noteDotPaint)
                    }
                }
            }
            return bmp
        }

        private fun makeActionPi(context: Context, action: String, reqCode: Int): PendingIntent {
            val i = Intent(context, SmartCalMonthWidgetProvider::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(
                context, reqCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun updateSingleWidget(context: Context, mgr: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(SmartCalWidgetProvider.PREFS, Context.MODE_PRIVATE)
            val calType = prefs.getString(SmartCalWidgetProvider.KEY_CAL, "jalali") ?: "jalali"
            val lang = prefs.getString(SmartCalWidgetProvider.KEY_LANG, "fa") ?: "fa"
            val monthOffset = prefs.getInt(KEY_MONTH_OFFSET, 0)
            val palette = SmartCalWidgetProvider.resolvePalette(context)

            val now = Calendar.getInstance()
            val curGy = now.get(Calendar.YEAR)
            val curGm = now.get(Calendar.MONTH) + 1
            val curGd = now.get(Calendar.DAY_OF_MONTH)
            val curJ = JalaliHelper.gregorianToJalali(curGy, curGm, curGd)

            val isGreg = (calType == "gregorian" || lang == "en")
            val isKu = (!isGreg && (calType == "kurdish" || lang == "ku"))
            val synced = loadSyncedData(context, isGreg)

            val firstDay = synced.firstDay // 0=Sat, 1=Sun, 2=Mon, ...
            val baseHeaders = when {
                isGreg -> EN_WD_SAT
                isKu -> KU_WD_SAT
                else -> FA_WD_SAT
            }
            val headers = Array(7) { c -> baseHeaders[(firstDay + c) % 7] }
            val weekendCols = mutableSetOf<Int>()
            for (c in 0 until 7) {
                val satIdx = (firstDay + c) % 7
                if (synced.weekends.contains(satIdx)) weekendCols.add(c)
            }

            val titleStr: String
            val startCol: Int
            val daysInMonth: Int
            val todayDay: Int
            val isRtl: Boolean
            val useFaDigits: Boolean
            val targetYear: Int
            val targetMonth: Int

            if (isGreg) {
                val targetCal = Calendar.getInstance().apply {
                    set(curGy, curGm - 1, 1)
                    add(Calendar.MONTH, monthOffset)
                }
                targetYear = targetCal.get(Calendar.YEAR)
                targetMonth = targetCal.get(Calendar.MONTH) + 1
                titleStr = "${SmartCalWidgetProvider.EN_MONTHS_FULL[(targetMonth - 1).coerceIn(0, 11)]} $targetYear"
                val dow = targetCal.get(Calendar.DAY_OF_WEEK) // Sun=1..Sat=7
                val satIdx = if (dow == Calendar.SATURDAY) 0 else dow
                startCol = ((satIdx - firstDay) % 7 + 7) % 7
                daysInMonth = JalaliHelper.gregorianMonthLength(targetYear, targetMonth)
                todayDay = if (targetYear == curGy && targetMonth == curGm) curGd else -1
                isRtl = false
                useFaDigits = false
            } else {
                val totalMonths = (curJ.year * 12) + (curJ.month - 1) + monthOffset
                targetYear = totalMonths / 12
                targetMonth = (totalMonths % 12) + 1
                val firstG = JalaliHelper.jalaliToGregorian(targetYear, targetMonth, 1)
                val firstCal = Calendar.getInstance().apply { set(firstG.year, firstG.month - 1, firstG.day) }
                val dow = firstCal.get(Calendar.DAY_OF_WEEK)
                val satIdx = if (dow == Calendar.SATURDAY) 0 else dow
                startCol = ((satIdx - firstDay) % 7 + 7) % 7
                daysInMonth = JalaliHelper.jalaliMonthLength(targetYear, targetMonth)
                todayDay = if (targetYear == curJ.year && targetMonth == curJ.month) curJ.day else -1
                titleStr = if (isKu) {
                    val kuY = targetYear + 1321
                    "${SmartCalWidgetProvider.KU_MONTHS[(targetMonth - 1).coerceIn(0, 11)]} ${SmartCalWidgetProvider.toFaDigits(kuY)}"
                } else {
                    "${SmartCalWidgetProvider.FA_MONTHS[(targetMonth - 1).coerceIn(0, 11)]} ${SmartCalWidgetProvider.toFaDigits(targetYear)}"
                }
                isRtl = true
                useFaDigits = true
            }

            val holidayDays = mutableSetOf<Int>()
            val eventDays = mutableSetOf<Int>()
            val noteDays = mutableSetOf<Int>()
            for (d in 1..daysInMonth) {
                val key = "$targetYear-$targetMonth-$d"
                if (synced.holidays.contains(key)) holidayDays.add(d)
                if (synced.events.contains(key)) eventDays.add(d)
                if (synced.notes.contains(key)) noteDays.add(d)
            }

            // Compute exact canvas proportions from actual widget size so numbers fill edge-to-edge crisply
            val opts = mgr.getAppWidgetOptions(appWidgetId)
            val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280).coerceAtLeast(180)
            val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200).coerceAtLeast(130)
            val gridHdp = (minH - 46).coerceAtLeast(95)
            val canvasW = 980
            val canvasH = ((canvasW.toFloat() * gridHdp.toFloat()) / minW.toFloat()).toInt().coerceIn(420, 960)

            val views = RemoteViews(context.packageName, R.layout.widget_smartcal_month)
            views.setImageViewBitmap(R.id.monthWidgetBg, SmartCalWidgetProvider.renderThemeCardBitmap(palette, 840, 680))
            views.setTextViewText(R.id.monthTitleBtn, titleStr)
            views.setTextColor(R.id.monthTitleBtn, palette.txtPri)
            views.setTextColor(R.id.monthPrevBtn, palette.txtSec)
            views.setTextColor(R.id.monthNextBtn, palette.txtSec)

            val gridBmp = renderMonthGridBitmap(
                palette, headers, isRtl, startCol, daysInMonth, todayDay,
                weekendCols, holidayDays, eventDays, noteDays, useFaDigits,
                canvasW, canvasH
            )
            views.setImageViewBitmap(R.id.monthGridImage, gridBmp)

            if (isRtl) {
                views.setOnClickPendingIntent(R.id.monthPrevBtn, makeActionPi(context, ACTION_NEXT_MONTH, 6471))
                views.setOnClickPendingIntent(R.id.monthNextBtn, makeActionPi(context, ACTION_PREV_MONTH, 6472))
            } else {
                views.setOnClickPendingIntent(R.id.monthPrevBtn, makeActionPi(context, ACTION_PREV_MONTH, 6471))
                views.setOnClickPendingIntent(R.id.monthNextBtn, makeActionPi(context, ACTION_NEXT_MONTH, 6472))
            }
            views.setOnClickPendingIntent(R.id.monthTitleBtn, makeActionPi(context, ACTION_RESET_TODAY, 6473))

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPi = PendingIntent.getActivity(
                context, 6474, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.monthGridImage, openPi)

            mgr.updateAppWidget(appWidgetId, views)
        }
    }
}
