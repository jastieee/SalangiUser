package com.nssi.salangikopu.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.nssi.salangikopu.R;

public class SplashActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView[] letters;
    private Runnable waveRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView tvLoading = findViewById(R.id.tvLoading);

        // SÁLÁNG Í KO PÛ letters
        letters = new TextView[]{
                findViewById(R.id.ltrS),
                findViewById(R.id.ltrA),
                findViewById(R.id.ltrL),
                findViewById(R.id.ltrA2),
                findViewById(R.id.ltrN),
                findViewById(R.id.ltrG),
                findViewById(R.id.ltrI),
                findViewById(R.id.ltrK),
                findViewById(R.id.ltrO),
                findViewById(R.id.ltrP),
                findViewById(R.id.ltrU)
        };

        startWaveAnimation();

        tvLoading.postDelayed(() -> {
            Animation bounce = AnimationUtils.loadAnimation(this, R.anim.bounce);
            tvLoading.startAnimation(bounce);
        }, 200);

        handler.postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
        }, 2000);
    }

    private void startWaveAnimation() {
        waveRunnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < letters.length; i++) {
                    final TextView letter = letters[i];
                    handler.postDelayed(() -> {
                        Animation bounce = AnimationUtils.loadAnimation(SplashActivity.this, R.anim.bounce);
                        letter.startAnimation(bounce);
                    }, i * 120);
                }

                handler.postDelayed(this, 1500);
            }
        };

        handler.post(waveRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (waveRunnable != null) {
            handler.removeCallbacks(waveRunnable);
        }
    }
}