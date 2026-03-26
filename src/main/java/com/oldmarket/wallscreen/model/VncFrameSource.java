package com.oldmarket.wallscreen.model;

import com.oldmarket.wallscreen.WallScreenPlugin;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class VncFrameSource implements WallScreen.FrameSource, Closeable {

    private static final long RECONNECT_DELAY_MS = 4000L;
    private static final long ANNOUNCE_MIN_INTERVAL_MS = 8000L;

    private final String screenName;
    private final ScreenOptions options;
    private final Object imageLock = new Object();
    private volatile boolean running;
    private volatile boolean connected;
    private volatile String status = "Idle";
    private volatile int remoteWidth;
    private volatile int remoteHeight;
    private volatile BufferedImage latestFrame;
    private volatile int lastCursorX;
    private volatile int lastCursorY;
    private volatile int lastSentPointerX;
    private volatile int lastSentPointerY;
    private volatile long lastAnnounceAt;
    private volatile String lastAnnouncedMessage = "";

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread worker;

    public VncFrameSource(String screenName, ScreenOptions options) {
        this.screenName = screenName;
        this.options = options;
        this.latestFrame = new BufferedImage(Math.max(1, options.getWidthPx()), Math.max(1, options.getHeightPx()), BufferedImage.TYPE_INT_RGB);
        this.remoteWidth = options.getWidthPx();
        this.remoteHeight = options.getHeightPx();
        start();
    }

    private void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop();
            }
        }, "WallScreen-VNC-" + options.getVncHost() + ":" + options.getVncPort());
        worker.setDaemon(true);
        worker.start();
    }

    private void runLoop() {
        while (running) {
            try {
                connectAndStream();
            } catch (SocketTimeoutException ex) {
                setStatus("Timeout: " + ex.getMessage(), false);
            } catch (EOFException ex) {
                setStatus("Disconnected", false);
            } catch (Exception ex) {
                setStatus("VNC error: " + safeMessage(ex), false);
            } finally {
                connected = false;
                closeSocket();
            }

            if (!running) {
                break;
            }

            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void connectAndStream() throws Exception {
        setStatus("Connecting to " + options.getVncHost() + ":" + options.getVncPort(), true);
        socket = new Socket();
        socket.connect(new InetSocketAddress(options.getVncHost(), options.getVncPort()), Math.max(1000, options.getConnectTimeoutMs()));
        socket.setSoTimeout(Math.max(1000, options.getReadTimeoutMs()));
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        int versionMode = performProtocolHandshake();
        performSecurityHandshake(versionMode);
        performInitialization();
        connected = true;
        setStatus("Connected to " + options.getVncHost() + ":" + options.getVncPort() + " remote=" + remoteWidth + "x" + remoteHeight, true);

        sendSetPixelFormat();
        sendSetEncodings();
        sendFramebufferUpdateRequest(false);

        while (running && !socket.isClosed()) {
            int messageType = in.readUnsignedByte();
            switch (messageType) {
                case 0:
                    readFramebufferUpdate();
                    sendFramebufferUpdateRequest(true);
                    break;
                case 1:
                    skipSetColorMapEntries();
                    break;
                case 2:
                    break;
                case 3:
                    skipServerCutText();
                    break;
                default:
                    throw new IOException("Unsupported server message type: " + messageType);
            }
        }
    }

    private int performProtocolHandshake() throws IOException {
        byte[] versionBytes = new byte[12];
        in.readFully(versionBytes);
        String serverVersion = new String(versionBytes, StandardCharsets.US_ASCII);
        if (!serverVersion.startsWith("RFB ")) {
            throw new IOException("Invalid VNC server header: " + serverVersion);
        }

        int mode = serverVersion.contains("003.003") ? 33 : 38;
        String clientVersion = mode == 33 ? "RFB 003.003\n" : "RFB 003.008\n";
        out.write(clientVersion.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return mode;
    }

    private void performSecurityHandshake(int versionMode) throws Exception {
        int securityType;
        if (versionMode == 33) {
            securityType = in.readInt();
            if (securityType == 0) {
                throw new IOException(readFailureReason33());
            }
        } else {
            int count = in.readUnsignedByte();
            if (count == 0) {
                throw new IOException(readFailureReason38());
            }
            byte[] types = new byte[count];
            in.readFully(types);
            securityType = chooseSecurityType(types);
            if (securityType == -1) {
                throw new IOException("Server security types do not include None or VNC authentication");
            }
            out.writeByte(securityType);
            out.flush();
        }

        if (securityType == 1) {
            if (versionMode != 33) {
                readSecurityResult();
            }
            return;
        }

        if (securityType != 2) {
            throw new IOException("Unsupported security type: " + securityType);
        }

        byte[] challenge = new byte[16];
        in.readFully(challenge);
        byte[] response = encryptVncChallenge(challenge, options.getVncPassword());
        out.write(response);
        out.flush();
        readSecurityResult();
    }

    private void performInitialization() throws IOException {
        out.writeByte(1);
        out.flush();

        remoteWidth = in.readUnsignedShort();
        remoteHeight = in.readUnsignedShort();
        byte[] serverPixelFormat = new byte[16];
        in.readFully(serverPixelFormat);
        int nameLength = in.readInt();
        if (nameLength > 0) {
            byte[] nameBytes = new byte[nameLength];
            in.readFully(nameBytes);
        }

        synchronized (imageLock) {
            latestFrame = new BufferedImage(Math.max(1, remoteWidth), Math.max(1, remoteHeight), BufferedImage.TYPE_INT_RGB);
        }
    }

    private void sendSetPixelFormat() throws IOException {
        out.writeByte(0);
        out.write(new byte[3]);
        out.writeByte(32);
        out.writeByte(24);
        out.writeByte(0);
        out.writeByte(1);
        out.writeShort(255);
        out.writeShort(255);
        out.writeShort(255);
        out.writeByte(16);
        out.writeByte(8);
        out.writeByte(0);
        out.write(new byte[3]);
        out.flush();
    }

    private void sendSetEncodings() throws IOException {
        out.writeByte(2);
        out.writeByte(0);
        out.writeShort(2);
        out.writeInt(0);
        out.writeInt(-223);
        out.flush();
    }

    private void sendFramebufferUpdateRequest(boolean incremental) throws IOException {
        out.writeByte(3);
        out.writeByte(incremental ? 1 : 0);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(remoteWidth);
        out.writeShort(remoteHeight);
        out.flush();
    }

    private void readFramebufferUpdate() throws IOException {
        in.readUnsignedByte();
        int rectangles = in.readUnsignedShort();
        BufferedImage workingCopy;
        synchronized (imageLock) {
            workingCopy = copyImage(latestFrame);
        }

        for (int i = 0; i < rectangles; i++) {
            int x = in.readUnsignedShort();
            int y = in.readUnsignedShort();
            int width = in.readUnsignedShort();
            int height = in.readUnsignedShort();
            int encoding = in.readInt();

            if (encoding == 0) {
                readRawRectangle(workingCopy, x, y, width, height);
            } else if (encoding == 1) {
                int srcX = in.readUnsignedShort();
                int srcY = in.readUnsignedShort();
                copyRect(workingCopy, srcX, srcY, x, y, width, height);
            } else if (encoding == -223) {
                remoteWidth = width;
                remoteHeight = height;
                workingCopy = resizeCanvas(workingCopy, remoteWidth, remoteHeight);
                setStatus("Connected to " + options.getVncHost() + ":" + options.getVncPort() + " remote=" + remoteWidth + "x" + remoteHeight, true);
            } else {
                throw new IOException("Unsupported rectangle encoding: " + encoding);
            }
        }

        synchronized (imageLock) {
            latestFrame = workingCopy;
        }
    }

    private void readRawRectangle(BufferedImage image, int x, int y, int width, int height) throws IOException {
        byte[] row = new byte[width * 4];
        for (int py = 0; py < height; py++) {
            in.readFully(row);
            for (int px = 0; px < width; px++) {
                int offset = px * 4;
                int blue = row[offset] & 0xFF;
                int green = row[offset + 1] & 0xFF;
                int red = row[offset + 2] & 0xFF;
                int rgb = (red << 16) | (green << 8) | blue;
                int drawX = x + px;
                int drawY = y + py;
                if (drawX >= 0 && drawX < image.getWidth() && drawY >= 0 && drawY < image.getHeight()) {
                    image.setRGB(drawX, drawY, rgb);
                }
            }
        }
    }

    private void copyRect(BufferedImage image, int srcX, int srcY, int destX, int destY, int width, int height) {
        BufferedImage sub = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sub.createGraphics();
        g.drawImage(image, 0, 0, width, height, srcX, srcY, srcX + width, srcY + height, null);
        g.dispose();

        Graphics2D ig = image.createGraphics();
        ig.drawImage(sub, destX, destY, null);
        ig.dispose();
    }

    private void skipSetColorMapEntries() throws IOException {
        in.readUnsignedByte();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        long bytes = count * 6L;
        skipFully(bytes);
    }

    private void skipServerCutText() throws IOException {
        skipFully(3);
        int length = in.readInt();
        skipFully(length);
    }

    private void skipFully(long bytes) throws IOException {
        while (bytes > 0) {
            int skipped = (int) Math.min(Integer.MAX_VALUE, bytes);
            int actual = in.skipBytes(skipped);
            if (actual <= 0) {
                throw new EOFException("Unexpected EOF while skipping " + bytes + " bytes");
            }
            bytes -= actual;
        }
    }

    private int chooseSecurityType(byte[] types) {
        for (byte type : types) {
            if ((type & 0xFF) == 2) {
                return 2;
            }
        }
        for (byte type : types) {
            if ((type & 0xFF) == 1) {
                return 1;
            }
        }
        return -1;
    }

    private void readSecurityResult() throws IOException {
        int statusCode = in.readInt();
        if (statusCode != 0) {
            throw new IOException(readFailureReason38());
        }
    }

    private String readFailureReason33() throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > 8192) {
            return "Connection rejected by server";
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private String readFailureReason38() throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > 8192) {
            return "Authentication failed";
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private byte[] encryptVncChallenge(byte[] challenge, String password) throws Exception {
        byte[] keyBytes = new byte[8];
        byte[] passwordBytes = password == null ? new byte[0] : password.getBytes(StandardCharsets.ISO_8859_1);
        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = i < passwordBytes.length ? reverseBits(passwordBytes[i]) : 0;
        }

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "DES");
        Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);

        byte[] response = new byte[16];
        byte[] block1 = cipher.doFinal(Arrays.copyOfRange(challenge, 0, 8));
        byte[] block2 = cipher.doFinal(Arrays.copyOfRange(challenge, 8, 16));
        System.arraycopy(block1, 0, response, 0, 8);
        System.arraycopy(block2, 0, response, 8, 8);
        return response;
    }

    private byte reverseBits(byte value) {
        int v = value & 0xFF;
        int r = 0;
        for (int i = 0; i < 8; i++) {
            r = (r << 1) | (v & 1);
            v >>= 1;
        }
        return (byte) r;
    }

    private BufferedImage copyImage(BufferedImage source) {
        BufferedImage result = new BufferedImage(Math.max(1, source.getWidth()), Math.max(1, source.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return result;
    }

    private BufferedImage resizeCanvas(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return result;
    }

    @Override
    public BufferedImage nextFrame(int cursorX, int cursorY, boolean active, ScreenOptions options) {
        lastCursorX = cursorX;
        lastCursorY = cursorY;

        BufferedImage base;
        synchronized (imageLock) {
            base = copyImage(latestFrame);
        }

        BufferedImage scaled = new BufferedImage(Math.max(1, options.getWidthPx()), Math.max(1, options.getHeightPx()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(base, 0, 0, scaled.getWidth(), scaled.getHeight(), null);

        if (active) {
            drawCursor(g, cursorX, cursorY);
        }
        g.dispose();
        return scaled;
    }

    private void drawCursor(Graphics2D g, int x, int y) {
        g.setColor(Color.WHITE);
        g.drawLine(x - 8, y, x + 8, y);
        g.drawLine(x, y - 8, x, y + 8);
        g.setColor(Color.RED);
        g.drawOval(x - 4, y - 4, 8, 8);
    }

    public void movePointer(int screenX, int screenY) {
        lastCursorX = screenX;
        lastCursorY = screenY;
        sendPointerEventAsync(screenX, screenY, 0);
    }

    public void clickLeft(int screenX, int screenY) {
        clickButtonAt(screenX, screenY, 1);
    }

    public void clickRight(int screenX, int screenY) {
        clickButtonAt(screenX, screenY, 4);
    }

    public void clickLeftAt(int screenX, int screenY) {
        clickButtonAt(screenX, screenY, 1);
    }

    private void clickButtonAt(int screenX, int screenY, int buttonMask) {
        sendReleaseAtCurrentPosition();
        lastCursorX = screenX;
        lastCursorY = screenY;
        sendPointerEventAsync(screenX, screenY, 0);
        sendPointerEventAsync(screenX, screenY, buttonMask);
        try {
            Thread.sleep(12L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        sendPointerEventAsync(screenX, screenY, 0);
    }

    public void sendKeyPress(int keysym) {
        sendKeyEvent(keysym, true);
        sendKeyEvent(keysym, false);
    }

    private void sendKeyEvent(int keysym, boolean down) {
        if (!connected || out == null) {
            return;
        }
        synchronized (this) {
            try {
                out.writeByte(4);
                out.writeByte(down ? 1 : 0);
                out.writeShort(0);
                out.writeInt(keysym);
                out.flush();
            } catch (IOException ex) {
                setStatus("Key send failed: " + safeMessage(ex), false);
                connected = false;
                closeSocket();
            }
        }
    }

    private void sendReleaseAtCurrentPosition() {
        sendPointerEventAsync(lastSentPointerX, lastSentPointerY, 0);
    }

    private void sendPointerEventAsync(int scaledX, int scaledY, int buttonMask) {
        if (!connected || out == null) {
            return;
        }
        int remoteX = scaleCoordinate(scaledX, Math.max(1, options.getWidthPx()), Math.max(1, remoteWidth));
        int remoteY = scaleCoordinate(scaledY, Math.max(1, options.getHeightPx()), Math.max(1, remoteHeight));
        synchronized (this) {
            try {
                out.writeByte(5);
                out.writeByte(buttonMask);
                out.writeShort(remoteX);
                out.writeShort(remoteY);
                out.flush();
                lastSentPointerX = scaledX;
                lastSentPointerY = scaledY;
            } catch (IOException ex) {
                setStatus("Pointer send failed: " + safeMessage(ex), false);
                connected = false;
                closeSocket();
            }
        }
    }

    private int scaleCoordinate(int value, int sourceMax, int destMax) {
        if (sourceMax <= 1 || destMax <= 1) {
            return 0;
        }
        double factor = value / (double) Math.max(1, sourceMax - 1);
        int scaled = (int) Math.round(factor * (destMax - 1));
        if (scaled < 0) {
            return 0;
        }
        if (scaled >= destMax) {
            return destMax - 1;
        }
        return scaled;
    }

    private void setStatus(String newStatus, boolean important) {
        String normalized = newStatus == null ? "Unknown" : newStatus;
        if (normalized.equals(status)) {
            return;
        }
        status = normalized;
        announceStatus(normalized, important);
    }

    private void announceStatus(String message, boolean important) {
        if (!WallScreenPlugin.isDebugModeStatic()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!important && message.equals(lastAnnouncedMessage) && now - lastAnnounceAt < ANNOUNCE_MIN_INTERVAL_MS) {
            return;
        }
        if (!important && now - lastAnnounceAt < ANNOUNCE_MIN_INTERVAL_MS) {
            return;
        }
        lastAnnouncedMessage = message;
        lastAnnounceAt = now;
        String full = "[WallScreen] " + screenName + ": " + message;
        try {
            org.bukkit.Bukkit.getLogger().info(full);
        } catch (Throwable ignored) {
        }
    }

    private String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message == null || message.trim().isEmpty() ? t.getClass().getSimpleName() : message;
    }

    private void closeSocket() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        in = null;
        out = null;
        socket = null;
    }

    @Override
    public void close() {
        running = false;
        closeSocket();
        if (worker != null) {
            worker.interrupt();
        }
    }
}
