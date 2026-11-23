package com.smartcal.app

import java.util.Calendar

object JalaliHelper {
    data class JalaliDate(val year: Int, val month: Int, val day: Int)
    data class GregorianDate(val year: Int, val month: Int, val day: Int)

    fun todayJalali(): JalaliDate {
        val cal = Calendar.getInstance()
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return JalaliDate(jy, jm, jd)
    }

    fun jalaliToGregorian(jyIn: Int, jm: Int, jd: Int): GregorianDate {
        val jy = jyIn + 1595
        var days = -355668 + (365 * jy) + ((jy / 33) * 8) + (((jy % 33) + 3) / 4) + jd
        days += if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days -= 1
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days += 1
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val isGLeap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
        val salA = intArrayOf(0, 31, if (isGLeap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 12 && gd > salA[gm + 1]) {
            gd -= salA[gm + 1]
            gm += 1
        }
        return GregorianDate(gy, gm + 1, gd)
    }

    fun isJalaliLeap(jy: Int): Boolean {
        val g1 = jalaliToGregorian(jy, 12, 29)
        val g2 = jalaliToGregorian(jy + 1, 1, 1)
        val c1 = Calendar.getInstance().apply { set(g1.year, g1.month - 1, g1.day, 12, 0, 0) }
        val c2 = Calendar.getInstance().apply { set(g2.year, g2.month - 1, g2.day, 12, 0, 0) }
        val diffDays = ((c2.timeInMillis - c1.timeInMillis) / 86400000L).toInt()
        return diffDays >= 2
    }

    fun jalaliMonthLength(jy: Int, jm: Int): Int {
        return when {
            jm <= 6 -> 31
            jm <= 11 -> 30
            else -> if (isJalaliLeap(jy)) 30 else 29
        }
    }

    fun gregorianMonthLength(gy: Int, gm: Int): Int {
        val isLeap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
        return when (gm) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeap) 29 else 28
            else -> 30
        }
    }
}
