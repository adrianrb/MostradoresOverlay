package com.mostradores.overlay;

import android.app.Application;
import android.content.Intent;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MostradoresApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler anterior =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable erro) {
                try {
                    StringWriter detalhes = new StringWriter();
                    erro.printStackTrace(new PrintWriter(detalhes));
                    detalhes.write("\nThread: " + thread.getName());
                    Intent intent = new Intent(MostradoresApp.this, CrashActivity.class);
                    intent.putExtra(CrashActivity.EXTRA_TRACE, detalhes.toString());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } catch (Throwable ignored) {
                }
                if (anterior != null) {
                    anterior.uncaughtException(thread, erro);
                }
            }
        });
    }
}
