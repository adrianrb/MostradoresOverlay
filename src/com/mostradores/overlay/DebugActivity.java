package com.mostradores.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DebugActivity extends Activity {

    private static final int REQ_ARMAZENAMENTO = 12;

    private TextView txtTeste;
    private Button btnCriarDummy;
    private Button btnRemoverDummy;
    private final ExecutorService executorDummy = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        txtTeste = findViewById(R.id.txtTeste);
        btnCriarDummy = findViewById(R.id.btnCriarDummy);
        btnRemoverDummy = findViewById(R.id.btnRemoverDummy);

        btnCriarDummy.setOnClickListener(v -> criarDummy());
        btnRemoverDummy.setOnClickListener(v -> removerDummy());
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarTeste();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorDummy.shutdownNow();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ARMAZENAMENTO) {
            atualizarTeste();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_ARMAZENAMENTO) {
            if (temPermissaoArmazenamento()) {
                criarDummy();
            } else {
                Toast.makeText(this, R.string.toast_sem_armazenamento, Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean temPermissaoArmazenamento() {
        if (Build.VERSION.SDK_INT >= 30) {
            return android.os.Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void solicitarPermissaoArmazenamento() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_ARMAZENAMENTO);
            } catch (Exception e) {
                try {
                    startActivityForResult(new Intent(
                            Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                            REQ_ARMAZENAMENTO);
                } catch (Exception e2) {
                    Toast.makeText(this, R.string.toast_sem_armazenamento,
                            Toast.LENGTH_LONG).show();
                }
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQ_ARMAZENAMENTO);
        }
    }

    private void criarDummy() {
        if (!temPermissaoArmazenamento()) {
            solicitarPermissaoArmazenamento();
            return;
        }
        btnCriarDummy.setEnabled(false);
        btnRemoverDummy.setEnabled(false);
        executorDummy.execute(() -> {
            ArquivoTeste.Resultado resultado = ArquivoTeste.criar(DebugActivity.this,
                    percentual -> runOnUiThread(() -> txtTeste.setText(
                            getString(R.string.dummy_criando, Math.round(percentual)))));
            runOnUiThread(() -> {
                btnCriarDummy.setEnabled(true);
                btnRemoverDummy.setEnabled(true);
                Toast.makeText(this, resultado.mensagem, Toast.LENGTH_LONG).show();
                atualizarTeste();
            });
        });
    }

    private void removerDummy() {
        btnCriarDummy.setEnabled(false);
        btnRemoverDummy.setEnabled(false);
        executorDummy.execute(() -> {
            ArquivoTeste.Resultado resultado = ArquivoTeste.remover(DebugActivity.this);
            runOnUiThread(() -> {
                btnCriarDummy.setEnabled(true);
                btnRemoverDummy.setEnabled(true);
                Toast.makeText(this, resultado.mensagem, Toast.LENGTH_LONG).show();
                atualizarTeste();
            });
        });
    }

    private void atualizarTeste() {
        if (txtTeste == null) {
            return;
        }
        java.io.File arquivo = ArquivoTeste.arquivo(this);
        if (arquivo == null) {
            txtTeste.setText("Cartão externo não detectado.");
            return;
        }
        if (!temPermissaoArmazenamento()) {
            txtTeste.setText(R.string.armazenamento_sem_permissao);
            return;
        }
        if (arquivo.exists()) {
            long bytes = arquivo.length();
            String tamanho = String.format(java.util.Locale.forLanguageTag("pt-BR"),
                    "%.1f GB", bytes / (double) (1024L * 1024L * 1024L));
            txtTeste.setText(getString(R.string.dummy_presente, tamanho));
        } else {
            txtTeste.setText(getString(R.string.dummy_ausente));
        }
    }
}
