package com.ashvin.yearcal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class YearCalWidget extends AppWidgetProvider {

    static final String ACTION_MIDNIGHT = "com.ashvin.yearcal.MIDNIGHT_UPDATE";

    // ── colour palette ────────────────────────────────────────────────────────
    // COL_FUTURE is pure white — same as Nothing OS widget text, no blue tint.
    // Anti-aliased circles picked up grey halos that made them look dim/bluish;
    // axis-aligned rectangles need no AA and render the colour as-is.
    private static final int COL_BG     = Color.rgb(18,  18,  20);
    private static final int COL_FUTURE = Color.rgb(150, 149, 144);  // changed to warmer yellow tint
    private static final int COL_WEEKEND = Color.rgb(77, 25, 0); // dull red
    private static final int COL_PAST   = Color.rgb(48,  48,  52);
    private static final int COL_TODAY  = Color.rgb(210, 40,  40);
    private static final int COL_YEAR   = Color.rgb(195, 195, 200);
    private static final int COL_SUFFIX = Color.rgb(100, 100, 108);

    // ── layout constants ──────────────────────────────────────────────────────
    // Each month block is exactly square: 7 columns × 6 rows, so
    //   cell_h = (M/6)  and  cell_w = (M/7)  gives  7·cell_w = 6·cell_h = M.
    // Gap between months = M × GAP_RATIO.
    // Grid fills the full (W − 2·PAD) width, equal left and right padding.
    private static final int   CANVAS_W  = 560;
    private static final float PAD       = 18f;
    private static final float GAP_RATIO = 0.065f;  // gap as fraction of M

    // M = (W − 2·PAD) / (3 + 2·GAP_RATIO)
    private static final float M     = (CANVAS_W - 2f * PAD) / (3f + 2f * GAP_RATIO);
    private static final float GAP   = M * GAP_RATIO;
    private static final float CELL_W = M / 7f;
    private static final float CELL_H = M / 6f;   // taller than CELL_W → square months
    private static final float GRID_H = 4f * M + 3f * GAP;

    // dot sizes — squares, half-side in pixels
    private static final float SQ_HALF    = CELL_W * 0.215f;  // regular dot square
    private static final float TODAY_HALF = CELL_W * 0.32f;   // today's red square (larger)

    // text sizes — "2026" and "186" as large as reasonably fits
    private static final float NUM_SZ = 52f;
    private static final float SUF_SZ = 27f;
    private static final float BOT_PAD = 12f;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateWidget(ctx, mgr, id);
        scheduleMidnightAlarm(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        if (ACTION_MIDNIGHT.equals(a) || Intent.ACTION_BOOT_COMPLETED.equals(a)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, YearCalWidget.class));
            for (int id : ids) updateWidget(ctx, mgr, id);
            scheduleMidnightAlarm(ctx);
        }
    }

    static void updateWidget(Context ctx, AppWidgetManager mgr, int id) {
        Bitmap bmp = drawCalendar();
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout);
        v.setImageViewBitmap(R.id.widget_image, bmp);
        mgr.updateAppWidget(id, v);
    }

    // ── core drawing ──────────────────────────────────────────────────────────
    private static Bitmap drawCalendar() {
        // Canvas height: pad + grid + gap-to-text + text ascent + bot pad
        // Text baseline sits BOT_PAD above the bottom edge.
        Paint pNum = makePaint(COL_TODAY,  NUM_SZ, true);
        Paint pSuf = makePaint(COL_SUFFIX, SUF_SZ, false);
        Paint.FontMetrics fm = pNum.getFontMetrics();
        // full text block height = ascent magnitude (above baseline)
        float textBlockH = -fm.ascent + fm.descent;
        int   H = Math.round(PAD + GRID_H + 14f + textBlockH + BOT_PAD);

        Bitmap bmp = Bitmap.createBitmap(CANVAS_W, H, Bitmap.Config.ARGB_8888);
        Canvas c   = new Canvas(bmp);

        // ── rounded background ─────────────────────────────────────────────
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(COL_BG);
        c.drawRoundRect(new RectF(0, 0, CANVAS_W, H), 44, 44, bg);

        // ── today info ─────────────────────────────────────────────────────
        Calendar today  = Calendar.getInstance();
        int todayYear   = today.get(Calendar.YEAR);
        int todayMonth  = today.get(Calendar.MONTH) + 1;
        int todayDay    = today.get(Calendar.DAY_OF_MONTH);

        // Dot paints — NO anti-aliasing: axis-aligned rectangles don't need it,
        // and disabling it means the colour renders with zero grey fringe → crisp white.
        Paint pFuture = new Paint(); pFuture.setColor(COL_FUTURE);
        Paint pWeekend = new Paint(); pWeekend.setColor(COL_WEEKEND);
        Paint pPast   = new Paint(); pPast.setColor(COL_PAST);
        Paint pToday  = new Paint(); pToday.setColor(COL_TODAY);

        // ── draw 12 months ─────────────────────────────────────────────────
        for (int month = 1; month <= 12; month++) {
            int ri = (month - 1) / 3;
            int ci = (month - 1) % 3;
            float mx = PAD + ci * (M + GAP);
            float my = PAD + ri * (M + GAP);

            // ISO weekday of 1st: Mon=0 … Sun=6
            Calendar cal  = new GregorianCalendar(todayYear, month - 1, 1);
            int startWd   = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            int numDays   = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

            for (int day = 1; day <= numDays; day++) {
                int lin     = startWd + (day - 1);
                int week    = lin / 7;
                int weekday = lin % 7;

                // Centre of this cell in canvas pixels
                float cx = mx + weekday * CELL_W + CELL_W * 0.5f;
                float cy = my + week    * CELL_H + CELL_H * 0.5f;

                boolean isToday = (month == todayMonth && day == todayDay);
                boolean isPast  = (month < todayMonth)
                               || (month == todayMonth && day < todayDay);

                if (isToday) {
                    float h = TODAY_HALF;
                    c.drawRect(cx - h, cy - h, cx + h, cy + h, pToday);
                } else {
                    float h = SQ_HALF;
                    boolean isWeekend = (weekday == 5 || weekday == 6); // Sat=5, Sun=6 in Mon-first scheme
                    Paint dotPaint = isPast ? pPast : (isWeekend ? pWeekend : pFuture);
                    c.drawRect(
                        (float) Math.round(cx - h), (float) Math.round(cy - h),
                        (float) Math.round(cx + h), (float) Math.round(cy + h),
                        dotPaint
                    );
                }
            }
        }

        // ── bottom text ────────────────────────────────────────────────────
        // Baseline is placed so descenders land BOT_PAD above the widget bottom.
        float baseline = H - BOT_PAD - fm.descent;

        Paint pYear = makePaint(COL_YEAR, NUM_SZ, true);
        c.drawText(String.valueOf(todayYear), PAD, baseline, pYear);

        // Days remaining — recomputed every draw, so always accurate
        Calendar eoy = new GregorianCalendar(todayYear, 11, 31);
        eoy.set(Calendar.HOUR_OF_DAY, 23);
        eoy.set(Calendar.MINUTE, 59);
        long diffMs   = eoy.getTimeInMillis() - today.getTimeInMillis();
        int  daysLeft = (int) (diffMs / (1000L * 60 * 60 * 24));

        String numStr = String.valueOf(daysLeft);
        String sufStr = " days left";
        float  numW   = pNum.measureText(numStr);
        float  sufW   = pSuf.measureText(sufStr);

        float rx = CANVAS_W - PAD - numW - sufW;
        c.drawText(numStr, rx,       baseline, pNum);
        c.drawText(sufStr, rx + numW, baseline, pSuf);

        return bmp;
    }

    private static Paint makePaint(int color, float size, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }

    // ── midnight alarm ─────────────────────────────────────────────────────────
    static void scheduleMidnightAlarm(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_MIDNIGHT);
        i.setPackage(ctx.getPackageName());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i, flags);

        Calendar midnight = Calendar.getInstance();
        midnight.add(Calendar.DAY_OF_YEAR, 1);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 5);
        midnight.set(Calendar.MILLISECOND, 0);

        am.cancel(pi);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            try {
                am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, midnight.getTimeInMillis(), pi);
            } catch (SecurityException e) {
                am.set(AlarmManager.RTC, midnight.getTimeInMillis(), pi);
            }
        } else {
            am.setExact(AlarmManager.RTC, midnight.getTimeInMillis(), pi);
        }
    }
}
