package com.example.aibrowser;

interface IAiProxy {
    void startProxy(String configJson);
    void stopProxy();
    String getProxyPort();
}
