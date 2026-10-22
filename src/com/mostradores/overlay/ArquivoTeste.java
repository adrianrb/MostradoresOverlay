package com.mostradores.overlay;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.RandomAccessFile;

public final class ArquivoTeste {

    public static final String NOME = "dummy_5gb.bin";
    private static final long TAMANHO = 5L * 1024L * 1024L * 1024L;
    private static final int BLOCO = 1024 * 1024;

    private ArquivoTeste() {
    }

    public static final class Resultado {
        public final boolean ok;
        public final String mensagem;

        Resultado(boolean ok, String mensagem) {
            this.ok = ok;
            this.mensagem = mensagem;
        }
    }

    public static long tamanhoAlvo() {
        return TAMANHO;
    }

    public static Resultado criar(Context context, ProgressoListener listener) {
        File volume = volumeExterno(context);
        if (volume == null) {
            return new Resultado(false, "Cartão externo não encontrado.");
        }

        java.util.List<File> candidatos = locaisDeGravacao(context, volume);
        long livre = volume.getUsableSpace();
        if (livre < TAMANHO + (256L * 1024L * 1024L)) {
            return new Resultado(false, "Espaço insuficiente no cartão: "
                    + gigas(livre) + " GB livres, precisa de ~5,3 GB.");
        }

        StringBuilder tentativas = new StringBuilder();
        for (File pasta : candidatos) {
            File destino = new File(pasta, NOME);
            Resultado resultado = gravar(destino, listener);
            if (resultado.ok) {
                return resultado;
            }
            tentativas.append("\n- ").append(pasta.getAbsolutePath())
                    .append(": ").append(resultado.mensagem);
        }
        return new Resultado(false, "Não foi possível gravar no cartão:" + tentativas);
    }

    private static java.util.List<File> locaisDeGravacao(Context context, File volume) {
        java.util.List<File> pastas = new java.util.ArrayList<>();
        pastas.add(new File(volume, "Download"));
        pastas.add(new File(volume,
                "Android/data/" + context.getPackageName() + "/files"));
        pastas.add(volume);
        return pastas;
    }

    private static Resultado gravar(File destino, ProgressoListener listener) {
        File pasta = destino.getParentFile();
        if (pasta != null && !pasta.isDirectory() && !pasta.mkdirs()) {
            return new Resultado(false, "pasta inexistente (" + pasta.getAbsolutePath() + ")");
        }
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(destino, "rw");
            raf.setLength(0);
            byte[] bloco = new byte[BLOCO];
            long restante = TAMANHO;
            long gravado = 0;
            long proximoAviso = 0;
            while (restante > 0) {
                int tamanho = (int) Math.min(BLOCO, restante);
                raf.write(bloco, 0, tamanho);
                restante -= tamanho;
                gravado += tamanho;
                if (listener != null && gravado >= proximoAviso) {
                    listener.progresso(gravado * 100f / TAMANHO);
                    proximoAviso += 512L * 1024L * 1024L;
                }
            }
            raf.getFD().sync();
            raf.close();
            raf = null;
            return new Resultado(true, "Arquivo criado em " + destino.getAbsolutePath()
                    + " (" + gigas(TAMANHO) + " GB).");
        } catch (Exception e) {
            if (destino.exists()) {
                destino.delete();
            }
            return new Resultado(false, e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static Resultado remover(Context context) {
        File arquivo = arquivo(context);
        if (arquivo == null || !arquivo.exists()) {
            return new Resultado(false, "Arquivo não existe.");
        }
        if (arquivo.delete()) {
            return new Resultado(true, "Arquivo removido.");
        }
        return new Resultado(false, "Não foi possível remover o arquivo.");
    }

    public static boolean existe(Context context) {
        File arquivo = arquivo(context);
        return arquivo != null && arquivo.exists();
    }

    public static File arquivo(Context context) {
        File volume = volumeExterno(context);
        if (volume == null) {
            return null;
        }
        for (File pasta : locaisDeGravacao(context, volume)) {
            File candidato = new File(pasta, NOME);
            if (candidato.exists()) {
                return candidato;
            }
        }
        return new File(locaisDeGravacao(context, volume).get(0), NOME);
    }

    private static File volumeExterno(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                android.os.storage.StorageManager sm =
                        (android.os.storage.StorageManager)
                                context.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    for (android.os.storage.StorageVolume volume : sm.getStorageVolumes()) {
                        if (volume.isPrimary()) {
                            continue;
                        }
                        File diretorio = volume.getDirectory();
                        if (diretorio != null && diretorio.isDirectory()) {
                            return diretorio;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        File legado = new File("/storage/92B4-4E00");
        if (legado.isDirectory()) {
            return legado;
        }
        return null;
    }

    private static String gigas(long bytes) {
        return String.format(java.util.Locale.forLanguageTag("pt-BR"),
                "%.1f", bytes / (double) (1024L * 1024L * 1024L));
    }

    public interface ProgressoListener {
        void progresso(float percentual);
    }
}
