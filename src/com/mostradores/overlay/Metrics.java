package com.mostradores.overlay;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

public final class Metrics {

    private static final long GIB = 1073741824L;

    private Metrics() {
    }

    public static final class Volume {
        public final String rotulo;
        public final java.io.File diretorio;

        public Volume(String rotulo, java.io.File diretorio) {
            this.rotulo = rotulo;
            this.diretorio = diretorio;
        }
    }

    public static final class Uso {
        public final float percentual;
        public final String detalhe;

        public Uso(float percentual, String detalhe) {
            this.percentual = percentual;
            this.detalhe = detalhe;
        }
    }

    private static final String[] CAMINHOS_EXTERNOS = {
            "/storage/emulated/0",
            "/sdcard",
            "/storage/self/primary",
            "/mnt/sdcard"
    };

    public static java.util.List<Volume> volumes(Context context) {
        java.util.List<Volume> volumes = new java.util.ArrayList<>();
        java.io.File interno = Environment.getDataDirectory();
        long[] espacoInterno = espaco(interno);
        long totalInterno = espacoInterno == null ? -1 : espacoInterno[0];
        volumes.add(new Volume("INTERNO", interno));

        java.io.File externo = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            externo = externoViaStorageManager(context, totalInterno);
        }
        if (externo == null) {
            externo = externoViaCaminhos(totalInterno);
        }
        if (externo != null) {
            volumes.add(new Volume("SD CARD", externo));
        }
        return volumes;
    }

    private static java.io.File externoViaStorageManager(Context context, long totalInterno) {
        try {
            android.os.storage.StorageManager sm = (android.os.storage.StorageManager)
                    context.getSystemService(Context.STORAGE_SERVICE);
            if (sm == null) {
                return null;
            }
            java.util.List<android.os.storage.StorageVolume> lista = sm.getStorageVolumes();
            for (android.os.storage.StorageVolume volume : lista) {
                if (volume.isPrimary()) {
                    continue;
                }
                java.io.File diretorio = volume.getDirectory();
                if (diretorio == null || !diretorio.isDirectory()) {
                    continue;
                }
                if (mesmoVolume(espaco(diretorio), totalInterno)) {
                    continue;
                }
                return diretorio;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static java.io.File externoViaCaminhos(long totalInterno) {
        for (String caminho : CAMINHOS_EXTERNOS) {
            java.io.File candidato = new java.io.File(caminho);
            if (!candidato.isDirectory()) {
                continue;
            }
            if (mesmoVolume(espaco(candidato), totalInterno)) {
                continue;
            }
            long[] espaco = espaco(candidato);
            if (espaco != null && espaco[0] > 0) {
                return candidato;
            }
        }
        return null;
    }

    private static boolean mesmoVolume(long[] tamanho, long totalInterno) {
        if (tamanho == null || tamanho[0] <= 0) {
            return true;
        }
        return tamanho[0] == totalInterno;
    }

    public static Uso uso(java.io.File diretorio) {
        long[] d = espaco(diretorio);
        if (d == null || d[0] <= 0) {
            return null;
        }
        return new Uso(clamp(100f * (d[0] - d[1]) / d[0]),
                formatBytes(d[0] - d[1]) + "/" + formatBytes(d[0]));
    }

    private static long[] espaco(java.io.File diretorio) {
        try {
            StatFs stat = new StatFs(diretorio.getPath());
            long total = stat.getBlockCountLong() * stat.getBlockSizeLong();
            long livre = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            return new long[]{total, livre};
        } catch (Exception e) {
            return null;
        }
    }

    public static final class CpuSampler {

        public static final String FONTE_STAT = "/proc/stat";
        public static final String FONTE_FREQUENCIA = "frequência dos núcleos";
        public static final String FONTE_NENHUMA = "indisponível";

        private long lastIdle;
        private long lastTotal;
        private boolean primed;
        private String fonte = FONTE_NENHUMA;

        public synchronized float sample() {
            long[] stat = readProcStat();
            if (stat != null) {
                long idle = stat[0];
                long total = stat[1];
                if (!primed || total <= lastTotal) {
                    lastIdle = idle;
                    lastTotal = total;
                    primed = true;
                    return sampleFrequencia();
                }
                long deltaIdle = idle - lastIdle;
                long deltaTotal = total - lastTotal;
                lastIdle = idle;
                lastTotal = total;
                if (deltaTotal <= 0) {
                    return sampleFrequencia();
                }
                fonte = FONTE_STAT;
                return clamp(100f * (1f - (float) deltaIdle / (float) deltaTotal));
            }
            return sampleFrequencia();
        }

        public synchronized String fonte() {
            return fonte;
        }

        private float sampleFrequencia() {
            int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
            long soma = 0;
            int lidos = 0;
            for (int i = 0; i < cores; i++) {
                long atual = readLong("/sys/devices/system/cpu/cpu" + i
                        + "/cpufreq/scaling_cur_freq");
                long maximo = readLong("/sys/devices/system/cpu/cpu" + i
                        + "/cpufreq/cpuinfo_max_freq");
                if (atual <= 0 || maximo <= 0) {
                    continue;
                }
                if (atual > maximo) {
                    atual = maximo;
                }
                soma += atual * 100L / maximo;
                lidos++;
            }
            if (lidos == 0) {
                fonte = FONTE_NENHUMA;
                return -1f;
            }
            fonte = FONTE_FREQUENCIA;
            return clamp(soma / (float) lidos);
        }

        private long[] readProcStat() {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader("/proc/stat"));
                String line = reader.readLine();
                if (line == null || !line.startsWith("cpu ")) {
                    return null;
                }
                String[] parts = line.trim().split("\\s+");
                long total = 0;
                for (int i = 1; i < parts.length && i <= 8; i++) {
                    total += Long.parseLong(parts[i]);
                }
                long idle = Long.parseLong(parts[4]);
                long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0;
                return new long[]{idle + iowait, total};
            } catch (Exception e) {
                return null;
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        private long readLong(String caminho) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(caminho));
                String line = reader.readLine();
                if (line == null) {
                    return -1;
                }
                return Long.parseLong(line.trim());
            } catch (Exception e) {
                return -1;
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    public static float ramPercent(Context context) {
        ActivityManager.MemoryInfo info = memoryInfo(context);
        if (info == null || info.totalMem <= 0) {
            return 0f;
        }
        long used = info.totalMem - info.availMem;
        return clamp(100f * used / info.totalMem);
    }

    public static String ramDetail(Context context) {
        ActivityManager.MemoryInfo info = memoryInfo(context);
        if (info == null) {
            return "";
        }
        long used = info.totalMem - info.availMem;
        return formatBytes(used) + "/" + formatBytes(info.totalMem);
    }

    private static ActivityManager.MemoryInfo memoryInfo(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return null;
        }
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return info;
    }

    private static String formatBytes(long bytes) {
        double gib = bytes / (double) GIB;
        if (gib >= 1d) {
            return String.format(Locale.forLanguageTag("pt-BR"), "%.1f GB", gib);
        }
        double mib = bytes / 1048576d;
        return String.format(Locale.forLanguageTag("pt-BR"), "%.0f MB", mib);
    }

    private static float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 100f) {
            return 100f;
        }
        return value;
    }
}
