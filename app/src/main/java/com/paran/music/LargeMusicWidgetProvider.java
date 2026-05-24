package com.paran.music;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

public class LargeMusicWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_music_large);
            views.setTextViewText(R.id.widget_large_title, context.getString(R.string.app_name));
            views.setOnClickPendingIntent(R.id.widget_large_play, serviceIntent(context, PlayerService.ACTION_TOGGLE, widgetId + 100));
            views.setOnClickPendingIntent(R.id.widget_large_previous, serviceIntent(context, PlayerService.ACTION_PREVIOUS, widgetId + 101));
            views.setOnClickPendingIntent(R.id.widget_large_next, serviceIntent(context, PlayerService.ACTION_NEXT, widgetId + 102));
            appWidgetManager.updateAppWidget(widgetId, views);
        }
    }

    private PendingIntent serviceIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, PlayerService.class).setAction(action);
        int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getService(context, requestCode, intent, flags);
    }
}
