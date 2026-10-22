package com.mostradores.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OverlayService extends Service {

    public static final String ACTION_STOP = "com.mostradores.overlay.STOP";

    private static final String PREFS = "cfg";
    private static final String CHANNEL_ID = "monitor";
    private static final int NOTIF_ID = 1;
    private static final int BASE_GAUGE_DP = 68;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private FrameLayout root;
    private LinearLayout colunas;
    private GaugeView gaugeCpu;
    private GaugeView gaugeRam;
    private final java.util.List<GaugeView> gaugeArmazenamento = new java.util.ArrayList<>();
    private final java.util.List<java.io.File> diretoriosArmazenamento = new java.util.ArrayList<>();
    private final java.util.List<GaugeView> todosMedidores = new java.util.ArrayList<>();
    private Handler mainHandler;
    private ScheduledExecutorService executor;
    private final Metrics.CpuSampler cpuSampler = new Metrics.CpuSampler();

    private int appliedScale = -1;
    private int appliedOpacity = -1;
    private long lastCollect;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            try {
                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                int interval = Math.max(1, prefs.getInt("intervalo", 2));
                long now = System.currentTimeMillis();
                if (now - lastCollect < interval * 1000L) {
                    return;
                }
                lastCollect = now;

                final float cpu = cpuSampler.sample();
                final String cpuDetail = cpuDetail(cpuSampler.fonte());
                final float ram = Metrics.ramPercent(OverlayService.this);
                final String ramDetail = Metrics.ramDetail(OverlayService.this);

                final int totalArmazenamentos = diretoriosArmazenamento.size();
                final float[] armazenamentoPct = new float[totalArmazenamentos];
                final String[] armazenamentoDet = new String[totalArmazenamentos];
                for (int i = 0; i < totalArmazenamentos; i++) {
                    Metrics.Uso uso = Metrics.uso(diretoriosArmazenamento.get(i));
                    armazenamentoPct[i] = uso == null ? -1f : uso.percentual;
                    armazenamentoDet[i] = uso == null ? "sem acesso" : uso.detalhe;
                }

                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString("cpu_fonte", cpuSampler.fonte())
                        .apply();

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (root == null) {
                            return;
                        }
                        applyPreferences();
                        if (gaugeCpu != null) {
                            gaugeCpu.setPercent(cpu, cpuDetail);
                            gaugeRam.setPercent(ram, ramDetail);
                        }
                        for (int i = 0; i < gaugeArmazenamento.size()
                                && i < armazenamentoPct.length; i++) {
                            gaugeArmazenamento.get(i).setPercent(
                                    armazenamentoPct[i], armazenamentoDet[i]);
                        }
                    }
                });
            } catch (RuntimeException ignored) {
            }
        }
    };

    private String cpuDetail(String fonte) {
        int cores = Runtime.getRuntime().availableProcessors();
        if (Metrics.CpuSampler.FONTE_FREQUENCIA.equals(fonte)) {
            return "frequência";
        }
        if (Metrics.CpuSampler.FONTE_NENHUMA.equals(fonte)) {
            return "sem dado";
        }
        return cores + (cores == 1 ? " núcleo" : " núcleos");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(getMainLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        startForegroundCompat();
        buildOverlay();
        applyPreferences();

        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("ativo", true).apply();

        executor = Executors.newSingleThreadScheduledExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                cpuSampler.sample();
            }
        });
        executor.scheduleWithFixedDelay(ticker, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        applyPreferences();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (windowManager != null && root != null) {
            try {
                windowManager.removeViewImmediate(root);
            } catch (RuntimeException ignored) {
            }
        }
        root = null;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("ativo", false).apply();
        stopForeground(true);
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notif_canal),
                        NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundCompat() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, 0);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openIntent = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(
                this, 1, stop, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentTitle(getString(R.string.notif_titulo))
                .setContentText(getString(R.string.notif_texto))
                .setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .setShowWhen(false);
        builder.addAction(new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat),
                getString(R.string.acao_parar),
                stopIntent).build());
        return builder.build();
    }

    private void buildOverlay() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int gaugePx = Math.round(BASE_GAUGE_DP * metrics.density);
        int margin = Math.round(4 * metrics.density);
        int pad = Math.round(8 * metrics.density);

        todosMedidores.clear();
        gaugeArmazenamento.clear();
        diretoriosArmazenamento.clear();

        gaugeCpu = criarMedidor("CPU", getColor(R.color.cpu), gaugePx, margin);
        gaugeRam = criarMedidor("RAM", getColor(R.color.ram), gaugePx, margin);
        todosMedidores.add(gaugeCpu);
        todosMedidores.add(gaugeRam);

        for (Metrics.Volume volume : Metrics.volumes(this)) {
            GaugeView medidor = criarMedidor(volume.rotulo, getColor(R.color.disk),
                    gaugePx, margin);
            todosMedidores.add(medidor);
            gaugeArmazenamento.add(medidor);
            diretoriosArmazenamento.add(volume.diretorio);
        }

        colunas = new LinearLayout(this);
        colunas.setOrientation(LinearLayout.VERTICAL);
        colunas.setPadding(pad, pad, pad, pad);
        LinearLayout linha = null;
        for (int i = 0; i < todosMedidores.size(); i++) {
            if (i % 3 == 0) {
                linha = new LinearLayout(this);
                linha.setOrientation(LinearLayout.HORIZONTAL);
                colunas.addView(linha);
            }
            linha.addView(todosMedidores.get(i));
        }

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(0xFFFFFFFF);
        close.setTextSize(13);
        close.setGravity(Gravity.CENTER);
        int closeSize = Math.round(22 * metrics.density);
        FrameLayout.LayoutParams closeParams =
                new FrameLayout.LayoutParams(closeSize, closeSize, Gravity.TOP | Gravity.END);
        close.setLayoutParams(closeParams);
        close.setBackgroundResource(R.drawable.close_bg);
        close.setContentDescription(getString(R.string.fechar));
        close.setClickable(true);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSelf();
            }
        });

        root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.panel_bg);
        root.addView(colunas);
        root.addView(close);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        params.x = prefs.getInt("x", Math.round(12 * metrics.density));
        params.y = prefs.getInt("y", Math.round(160 * metrics.density));

        root.setOnTouchListener(new View.OnTouchListener() {
            private static final long DUPLO_TOQUE_MS = 350;
            private static final int TOLERANCIA_TOQUE_PX = 24;

            private float startX;
            private float startY;
            private int originX;
            private int originY;
            private boolean arrastou;
            private long ultimoToque;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        originX = params.x;
                        originY = params.y;
                        arrastou = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;
                        if (!arrastou && Math.hypot(dx, dy) > TOLERANCIA_TOQUE_PX) {
                            arrastou = true;
                        }
                        if (arrastou) {
                            params.x = originX + Math.round(dx);
                            params.y = originY + Math.round(dy);
                            clampPosition();
                            windowManager.updateViewLayout(root, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (arrastou) {
                            getSharedPreferences(PREFS, MODE_PRIVATE)
                                    .edit()
                                    .putInt("x", params.x)
                                    .putInt("y", params.y)
                                    .apply();
                            return true;
                        }
                        long agora = System.currentTimeMillis();
                        if (agora - ultimoToque <= DUPLO_TOQUE_MS) {
                            ultimoToque = 0;
                            abrirConfiguracoes();
                        } else {
                            ultimoToque = agora;
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        windowManager.addView(root, params);
    }

    private void abrirConfiguracoes() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (RuntimeException ignored) {
        }
    }

    private GaugeView criarMedidor(String rotulo, int cor, int tamanhoPx, int margem) {
        GaugeView medidor = new GaugeView(this, rotulo, cor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tamanhoPx, tamanhoPx);
        lp.setMargins(margem, margem, margem, margem);
        medidor.setLayoutParams(lp);
        return medidor;
    }

    private void clampPosition() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int minVisible = Math.round(40 * metrics.density);
        int width = root != null && root.getWidth() > 0 ? root.getWidth() : 0;
        int height = root != null && root.getHeight() > 0 ? root.getHeight() : 0;
        int minX = -(width - minVisible);
        int maxX = metrics.widthPixels - minVisible;
        int maxY = metrics.heightPixels - minVisible;
        if (params.x < minX) {
            params.x = minX;
        }
        if (params.x > maxX) {
            params.x = maxX;
        }
        if (params.y < 0) {
            params.y = 0;
        }
        if (params.y > maxY) {
            params.y = maxY;
        }
    }

    private void applyPreferences() {
        if (root == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int scale = prefs.getInt("escala", 90);
        int opacity = prefs.getInt("opacidade", 85);

        if (opacity != appliedOpacity) {
            appliedOpacity = opacity;
            root.setAlpha(Math.max(0.25f, Math.min(1f, opacity / 100f)));
        }

        if (scale != appliedScale) {
            appliedScale = scale;
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int size = Math.round(BASE_GAUGE_DP * metrics.density * scale / 100f);
            int margin = Math.round(4 * metrics.density);
            for (GaugeView gauge : todosMedidores) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) gauge.getLayoutParams();
                lp.width = size;
                lp.height = size;
                lp.setMargins(margin, margin, margin, margin);
                gauge.setLayoutParams(lp);
            }
            if (windowManager != null && root.getParent() != null) {
                windowManager.updateViewLayout(root, params);
            }
        }
    }
}
