package com.mostradores.overlay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class CrashActivity extends Activity {

    public static final String EXTRA_TRACE = "trace";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            int pad = Math.round(16 * getResources().getDisplayMetrics().density);

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(pad, pad, pad, pad);

            TextView title = new TextView(this);
            title.setText("O app encontrou um erro");
            title.setTextSize(20);
            title.setTextColor(0xFFC62828);
            box.addView(title);

            String detalhes = getIntent().getStringExtra(EXTRA_TRACE);
            final String trace = detalhes != null ? detalhes : "(sem detalhes)";

            TextView body = new TextView(this);
            body.setTextIsSelectable(true);
            body.setText(trace);
            body.setTextSize(11);
            body.setTextColor(0xFF212121);
            body.setPadding(0, pad, 0, pad);
            box.addView(body);

            Button copiar = new Button(this);
            copiar.setText("Copiar erro");
            copiar.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("erro", trace));
                    Toast.makeText(this, "Erro copiado", Toast.LENGTH_SHORT).show();
                }
            });
            box.addView(copiar);

            Button fechar = new Button(this);
            fechar.setText("Fechar");
            fechar.setOnClickListener(v -> finish());
            box.addView(fechar);

            ScrollView scroll = new ScrollView(this);
            scroll.addView(box);
            setContentView(scroll);
        } catch (Throwable ignored) {
            finish();
        }
    }
}
