package com.egs.myapplication;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.yoimerdr.android.virtualjoystick.views.JoystickView;

public class MainActivity extends AppCompatActivity {

    private WSClient webSocketClient;
    private MonEventListener monEventListener;

    private static final String SERVER_URL = "ws://192.168.1.38:12345";

    private JoystickView.Direction lastDirection = JoystickView.Direction.NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        hideSystemUI();
        setupButtons();

        monEventListener = new MonEventListener() {
            @Override
            public void onOpen() {
                System.out.println("WebSocket ouvert.");

                updateConnectionStatus(true);
                vibrate();

                webSocketClient.sendMessage("Hello from Android!");
            }

            @Override
            public void onMessage(String text) {
                System.out.println("Message reçu : " + text);
            }

            @Override
            public void onClosing(String reason) {
                System.out.println("Connexion fermée : " + reason);

                updateConnectionStatus(false);
            }
        };

        webSocketClient = new WSClient();
        webSocketClient.connectWebSocket(SERVER_URL, monEventListener);
    }

    private void setupButtons() {
        // 1 joystick
        JoystickView joystick = findViewById(R.id.vJoystick);
        joystick.setMoveListener(direction -> {
            //if (direction != lastDirection) {
            //    lastDirection = direction;
                String finalName = "joystick_" + direction.name().toLowerCase();
                webSocketClient.sendMessage(finalName);
            //}
        });
        // 2 middle buttons
        findViewById(R.id.button_setup).setOnClickListener(view -> {
            if (webSocketClient != null) {
                webSocketClient.closeWebSocket();
                updateConnectionStatus(false);
            }

            webSocketClient = new WSClient();
            webSocketClient.connectWebSocket(SERVER_URL, monEventListener);
        });
        // 4 buttons
        findViewById(R.id.button_top).setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    webSocketClient.sendMessage("press_top");
                    break;
                case MotionEvent.ACTION_UP:
                    webSocketClient.sendMessage("release_top");
                    v.performClick();
                    break;
            }
            return true;
        });
        findViewById(R.id.button_left).setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    webSocketClient.sendMessage("press_left");
                    break;
                case MotionEvent.ACTION_UP:
                    webSocketClient.sendMessage("release_left");
                    v.performClick();
                    break;
            }
            return true;
        });
        findViewById(R.id.button_right).setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    webSocketClient.sendMessage("press_right");
                    break;
                case MotionEvent.ACTION_UP:
                    webSocketClient.sendMessage("release_right");
                    v.performClick();
                    break;
            }
            return true;
        });
        findViewById(R.id.button_bottom).setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    webSocketClient.sendMessage("press_bottom");
                    break;
                case MotionEvent.ACTION_UP:
                    webSocketClient.sendMessage("release_bottom");
                    v.performClick();
                    break;
            }
            return true;
        });
    }

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            // Deprecated in API 26
            v.vibrate(500);
        }
    }

    private void updateConnectionStatus(boolean isConnected) {
        TextView connectedStatus = findViewById(R.id.connection_status);

        int iconId = isConnected ? android.R.drawable.presence_online
                : android.R.drawable.presence_offline;
        int colorId = Color.parseColor(isConnected ? "#4CAF50" : "#ff7979");
        String textId = isConnected ? "Connected" : "Not connected";

        Drawable statusIcon = AppCompatResources.getDrawable(this, iconId);
        connectedStatus.setText(textId);
        connectedStatus.setCompoundDrawablesWithIntrinsicBounds(statusIcon,
                null, null, null);
        connectedStatus.setTextColor(colorId);
    }

    private void hideSystemUI() {
        int newUiOptions = getWindow().getDecorView().getSystemUiVisibility();

        // Status bar hiding: Backwards compatible to Jellybean
        newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
        newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocketClient != null) {
            webSocketClient.closeWebSocket();
        }
    }
}