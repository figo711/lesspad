package com.egs.myapplication;

import androidx.annotation.NonNull;

import java.util.EventListener;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WSClient {

    private WebSocket webSocket;


    public void connectWebSocket(String serverUrl, MonEventListener eventListener) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(serverUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull okhttp3.Response response) {
                super.onOpen(webSocket, response);

                if (eventListener != null) {
                    eventListener.onOpen();
                }
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                super.onMessage(webSocket, text);

                if (eventListener != null) {
                    eventListener.onMessage(text);
                }
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
                super.onMessage(webSocket, bytes);
                System.out.println("Message binaire reçu : " + bytes.hex());
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                super.onClosing(webSocket, code, reason);

                if (eventListener != null) {
                    eventListener.onClosing(reason);
                }

                webSocket.close(1000, null);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, okhttp3.Response response) {
                super.onFailure(webSocket, t, response);
                System.err.println("Erreur WebSocket : " + t.getMessage());
            }
        });

        client.dispatcher().executorService().shutdown(); // Optionnel, libérer des ressources
    }

    public void sendMessage(String message) {
        if (webSocket != null) {
            webSocket.send(message);
            System.out.println("Message envoyé : " + message);
        } else {
            System.err.println("WebSocket non connecté.");
        }
    }

    public void closeWebSocket() {
        if (webSocket != null) {
            webSocket.close(1000, "Fin de la session");
        }
    }
}
