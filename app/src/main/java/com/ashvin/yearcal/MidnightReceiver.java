package com.ashvin.yearcal;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class MidnightReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, YearCalWidget.class));
        for (int id : ids) YearCalWidget.updateWidget(ctx, mgr, id);
        YearCalWidget.scheduleMidnightAlarm(ctx);
    }
}
