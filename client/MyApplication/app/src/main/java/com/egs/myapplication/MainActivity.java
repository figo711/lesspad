package com.egs.myapplication;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.yoimerdr.android.virtualjoystick.views.JoystickView;

import java.lang.Character;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private SensorManager mSensorManager;
    private Sensor mGyroscope;

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            // Les valeurs de rotation sont dans event.values
            // Les valeurs sont en radians par seconde
            float x = event.values[0];
            float y = (float) (event.values[1] * 57.29578);
            float z = event.values[2];

            // Faire quelque chose avec les valeurs
            // Par exemple, afficher les valeurs dans un TextView

            // webSocketClient.sendMessage("gyroscope_value_"+y);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private AlertDialog.Builder builder;


    private WSClient webSocketClient;
    private MonEventListener monEventListener;

    private String SERVER_IP = "192.168.183.82"; // 192.168.18.233
    private static final String SERVER_PORT = "12345";
    private String SERVER_URL = String.format("ws://%s:%s", SERVER_IP, SERVER_PORT);

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

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        mSensorManager.registerListener(mSensorEventListener, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);

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

    private AlertDialog.Builder getBuilder() {
        InputFilter filter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    if (!Character.isDigit(source.charAt(i)) &&
                            !Pattern.matches("\\p{Punct}", Character.toString(source.charAt(i)))) {
                        return "";
                    }
                }
                return null;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Entrez l'IP du serveur: ");
        EditText input = new EditText(this);
        input.setFilters(new InputFilter[]{ filter });
        input.setText(SERVER_IP);
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, i) -> {
            String text = input.getText().toString();

            SERVER_IP = text;

            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();

            SERVER_URL = String.format("ws://%s:%s", SERVER_IP, SERVER_PORT);

            System.out.println(SERVER_URL);
        });
        builder.setNegativeButton("Annuler", (dialog, i) -> {
            dialog.cancel();
        });
        return builder;
    }

    private void setupButtons() {
        // 1 joystick
        JoystickView joystick = findViewById(R.id.vJoystick);
        joystick.setMoveListener(direction -> {
            if (direction != lastDirection) {
                String finalName2 = "joystick_release_" + lastDirection.name().toLowerCase();
                webSocketClient.sendMessage(finalName2);

                lastDirection = direction;
                String finalName = "joystick_press_" + direction.name().toLowerCase();
                webSocketClient.sendMessage(finalName);
            }
        });
        // 2 middle buttons
        findViewById(R.id.button_setup).setOnClickListener(view -> {
            if (webSocketClient != null) {
                webSocketClient.closeWebSocket();
                webSocketClient = null;
                updateConnectionStatus(false);
            } else {
                // builder = getBuilder();
                // builder.show();

                webSocketClient = new WSClient();
                webSocketClient.connectWebSocket(SERVER_URL, monEventListener);
            }
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

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(mSensorEventListener, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mSensorEventListener);
    }
}