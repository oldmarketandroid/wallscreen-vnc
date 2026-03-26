package com.oldmarket.wallscreen.model;

public class ScreenOptions {

    private int fps;
    private int widthPx;
    private int heightPx;
    private int bitrateKbps;
    private boolean vncEnabled;
    private String vncHost;
    private int vncPort;
    private String vncPassword;
    private int connectTimeoutMs;
    private int readTimeoutMs;

    public ScreenOptions(int fps, int widthPx, int heightPx, int bitrateKbps,
                         boolean vncEnabled, String vncHost, int vncPort,
                         String vncPassword, int connectTimeoutMs, int readTimeoutMs) {
        this.fps = fps;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.bitrateKbps = bitrateKbps;
        this.vncEnabled = vncEnabled;
        this.vncHost = vncHost;
        this.vncPort = vncPort;
        this.vncPassword = vncPassword;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public ScreenOptions copy() {
        return new ScreenOptions(fps, widthPx, heightPx, bitrateKbps, vncEnabled, vncHost, vncPort, vncPassword, connectTimeoutMs, readTimeoutMs);
    }

    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps; }
    public int getWidthPx() { return widthPx; }
    public void setWidthPx(int widthPx) { this.widthPx = widthPx; }
    public int getHeightPx() { return heightPx; }
    public void setHeightPx(int heightPx) { this.heightPx = heightPx; }
    public int getBitrateKbps() { return bitrateKbps; }
    public void setBitrateKbps(int bitrateKbps) { this.bitrateKbps = bitrateKbps; }
    public boolean isVncEnabled() { return vncEnabled; }
    public void setVncEnabled(boolean vncEnabled) { this.vncEnabled = vncEnabled; }
    public String getVncHost() { return vncHost; }
    public void setVncHost(String vncHost) { this.vncHost = vncHost; }
    public int getVncPort() { return vncPort; }
    public void setVncPort(int vncPort) { this.vncPort = vncPort; }
    public String getVncPassword() { return vncPassword; }
    public void setVncPassword(String vncPassword) { this.vncPassword = vncPassword; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
