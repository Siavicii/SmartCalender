package com.smartcal.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        IconUpdater.updateTodayIcon(context)
        IconUpdater.refreshStatusDateNotificationIfEnabled(context)
        SmartCalMonthWidgetProvider.resetAndUpdateAll(context)
        SmartCalWidgetProvider.updateAllWidgets(context)
    }
}
