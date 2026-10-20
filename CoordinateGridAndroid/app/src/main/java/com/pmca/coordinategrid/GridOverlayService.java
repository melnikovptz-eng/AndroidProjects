package com.pmca.coordinategrid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class GridOverlayService extends Service {
    public static final String ACTION_START = "com.pmca.coordinategrid.START";
    public static final String ACTION_STOP = "com.pmca.coordinategrid.STOP";
    private static final String CHANNEL_ID = "coordinate_grid";
    private WindowManager windowManager;
    private GridView gridView;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            removeGrid();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(71, makeNotification());
        if (gridView == null) {
            gridView = new GridView();
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            try {
                windowManager.addView(gridView, params);
            } catch (Exception e) {
                gridView = null;
                stopForeground(true);
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void removeGrid() {
        if (gridView != null) {
            try { windowManager.removeView(gridView); } catch (Exception ignored) {}
            gridView = null;
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Сетка координат", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Управление прозрачной сеткой координат");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private Notification makeNotification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("Сетка координат включена")
                .setContentText("Чтобы выключить, открой приложение и нажми «Выключить сетку».")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        removeGrid();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private final class GridView extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint major = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GridView() {
            super(GridOverlayService.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            line.setColor(0x66708090);
            line.setStrokeWidth(1f);
            major.setColor(0xCC1677FF);
            major.setStrokeWidth(1.6f);
            label.setColor(Color.rgb(0, 55, 130));
            label.setTextSize(20f);
            label.setFakeBoldText(true);
            bgPaint.setColor(0xDDFFFFFF);
            bgPaint.setStyle(Paint.Style.FILL);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            for (int x = 0; x <= w; x += 50) {
                boolean big = x % 100 == 0;
                canvas.drawLine(x, 0, x, h, big ? major : line);
                if (big && x > 0) {
                    float tx = Math.min(x + 3, Math.max(0, w - 42));
                    canvas.drawRect(tx - 2, 2, tx + 39, 25, bgPaint);
                    canvas.drawText(Integer.toString(x), tx, 20, label);
                }
            }
            for (int y = 0; y <= h; y += 50) {
                boolean big = y % 100 == 0;
                canvas.drawLine(0, y, w, y, big ? major : line);
                if (big && y > 0) {
                    float ty = Math.min(y - 3, h - 4);
                    canvas.drawRect(2, ty - 21, 49, ty + 3, bgPaint);
                    canvas.drawText(Integer.toString(y), 4, ty - 3, label);
                }
            }
            canvas.drawCircle(5, 5, 4, major);
        }
    }
}
