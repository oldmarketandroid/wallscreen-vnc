package com.oldmarket.wallscreen.model;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class PlaceholderVncFrameSource implements WallScreen.FrameSource {

    private final int width;
    private final int height;

    public PlaceholderVncFrameSource(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public BufferedImage nextFrame(int cursorX, int cursorY, boolean active, ScreenOptions options) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(12, 12, 12));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(30, 150, 255));
        g.fillRect(0, 0, width, 28);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 18));
        g.drawString("VNC placeholder", 12, 20);
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.drawString("Host: " + options.getVncHost() + ":" + options.getVncPort(), 12, 55);
        g.drawString("FPS: " + options.getFps(), 12, 75);
        g.drawString("Bitrate: " + options.getBitrateKbps() + " kbps", 12, 95);
        g.drawString("Replace PlaceholderVncFrameSource", 12, 125);
        g.drawString("with a real RFB/VNC client.", 12, 145);
        if (active) {
            g.setColor(Color.RED);
            g.drawOval(cursorX - 4, cursorY - 4, 8, 8);
        }
        g.dispose();
        return image;
    }
}
