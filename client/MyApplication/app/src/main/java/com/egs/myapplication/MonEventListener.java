package com.egs.myapplication;

public interface MonEventListener {
    void onOpen();
    void onMessage(String text);
    void onClosing(String reason);
}
