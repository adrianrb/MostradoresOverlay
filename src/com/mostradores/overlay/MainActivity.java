package com.mostradores.overlay;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 10;
    private static final int REQ_NOTIF = 11;
    private static final int REQ_ARMAZENAMENTO = 12;

    private SharedPreferences prefs;
    private TextView txtStatus;
    private TextView txtCpuFonte;
    private TextView txtTamanho;
    private TextView txtOpacidade;
    private TextView txtIntervalo;
    private Button btnIniciar;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);

        txtStatus = findViewById(R.id.txtStatus);
        txtCpuFonte = findViewById(R.id.txtCpuFonte);
        txtTamanho = findViewById(R.id.txtTamanho);
        txtOpacidade = findViewById(R.id.txtOpacidade);
        txtIntervalo = findViewById(R.id.txtIntervalo);
        btnIniciar = findViewById(R.id.btnIniciar);

        Button btnPermissao = findViewById(R.id.btnPermissao);
        Button btnPermissaoArmazenamento = findViewById(R.id.btnPermissaoArmazenamento);
        btnPermissao.setOnClickListener(v -> abrirConfigSobreposicao());
        btnIniciar.setOnClickListener(v -> alternarServico());
        btnPermissaoArmazenamento.setOnClickListener(v -> solicitarPermissaoArmazenamento());

        configurarSeek(R.id.skTamanho, true);
        configurarSeek(R.id.skOpacidade, false);
        configurarSeek(R.id.skIntervalo, false);
        atualizarRotulos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        atualizarStatus();
        if (requestCode == REQ_ARMAZENAMENTO) {
            Toast.makeText(this, temPermissaoArmazenamento()
                    ? R.string.toast_sem_armazenamento_ok
                    : R.string.toast_sem_armazenamento, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        atualizarStatus();
        if (requestCode == REQ_ARMAZENAMENTO) {
            Toast.makeText(this, temPermissaoArmazenamento()
                    ? R.string.toast_sem_armazenamento_ok
                    : R.string.toast_sem_armazenamento, Toast.LENGTH_LONG).show();
        }
    }

    private void configurarSeek(int id, boolean tamanho) {
        SeekBar bar = findViewById(id);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (tamanho) {
                    prefs.edit().putInt("escala", 50 + progress).apply();
                } else if (seekBar.getId() == R.id.skOpacidade) {
                    prefs.edit().putInt("opacidade", 25 + progress * 75 / 100).apply();
                } else {
                    prefs.edit().putInt("intervalo", 1 + Math.round(progress * 9 / 100f)).apply();
                }
                atualizarRotulos();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void atualizarRotulos() {
        txtTamanho.setText(prefs.getInt("escala", 90) + "%");
        txtOpacidade.setText(prefs.getInt("opacidade", 85) + "%");
        txtIntervalo.setText(prefs.getInt("intervalo", 2) + " s");
    }

    private void atualizarStatus() {
        boolean sobreposicao = Settings.canDrawOverlays(this);
        boolean ativo = servicoAtivo();
        prefs.edit().putBoolean("ativo", ativo).apply();

        if (ativo) {
            txtStatus.setText(R.string.status_ativo);
            txtStatus.setTextColor(0xFF2E7D32);
        } else if (sobreposicao) {
            txtStatus.setText(R.string.status_pronto);
            txtStatus.setTextColor(0xFF1565C0);
        } else {
            txtStatus.setText(R.string.status_sem_permissao);
            txtStatus.setTextColor(0xFFC62828);
        }
        btnIniciar.setText(ativo ? R.string.btn_parar : R.string.btn_iniciar);

        String fonte = prefs.getString("cpu_fonte", null);
        if (fonte == null || fonte.length() == 0) {
            txtCpuFonte.setText(R.string.cpu_aguardando);
        } else if (Metrics.CpuSampler.FONTE_FREQUENCIA.equals(fonte)) {
            txtCpuFonte.setText(R.string.cpu_freq_aviso);
        } else {
            txtCpuFonte.setText(getString(R.string.cpu_via, fonte));
        }
    }

    private boolean servicoAtivo() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) {
                return prefs.getBoolean("ativo", false);
            }
            List<ActivityManager.RunningServiceInfo> servicos = am.getRunningServices(64);
            for (ActivityManager.RunningServiceInfo info : servicos) {
                if (OverlayService.class.getName().equals(info.service.getClassName())) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private void alternarServico() {
        if (servicoAtivo()) {
            stopService(new Intent(this, OverlayService.class));
            handler.postDelayed(this::atualizarStatus, 300);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_permissao, Toast.LENGTH_LONG).show();
            abrirConfigSobreposicao();
            return;
        }
        pedirPermissaoNotificacoes();
        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        handler.postDelayed(this::atualizarStatus, 300);
    }

    private boolean temPermissaoArmazenamento() {
        if (Build.VERSION.SDK_INT >= 30) {
            return android.os.Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void solicitarPermissaoArmazenamento() {
        if (temPermissaoArmazenamento()) {
            Toast.makeText(this, R.string.toast_sem_armazenamento_ok,
                    Toast.LENGTH_SHORT).show();
            return;
        }
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

    private void pedirPermissaoNotificacoes() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private void abrirConfigSobreposicao() {
        if (Settings.canDrawOverlays(this)) {
            return;
        }
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQ_OVERLAY);
    }
}
