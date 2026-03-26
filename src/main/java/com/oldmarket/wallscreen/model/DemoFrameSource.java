package com.oldmarket.wallscreen.model;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class DemoFrameSource implements WallScreen.FrameSource {

    private final int width;
    private final int height;
    private long tick;

    public DemoFrameSource(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public BufferedImage nextFrame(int cursorX, int cursorY, boolean active, ScreenOptions options) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x + (int) tick) % 256;
                int b = (y + (int) (tick * 2L)) % 256;
                int gr = ((x + y) / 2 + (int) tick) % 256;
                int rgb = (r << 16) | (gr << 8) | b;
                image.setRGB(x, y, rgb);
            }
        }

        g.setColor(new Color(15, 15, 15, 180));
        g.fillRoundRect(8, 8, Math.max(120, width - 16), 60, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 18));
        g.drawString("WallScreen Demo", 18, 30);
        g.setFont(new Font("Arial", Font.PLAIN, 13));
        g.drawString("Resolution: " + options.getWidthPx() + "x" + options.getHeightPx(), 18, 47);
        g.drawString("FPS: " + options.getFps() + " | Bitrate: " + options.getBitrateKbps() + " kbps", 18, 62);

        if (active) {
            drawCrosshair(g, cursorX, cursorY);
        }
        g.dispose();
        tick++;
        return image;
    }

    private void drawCrosshair(Graphics2D g, int x, int y) {
        g.setColor(Color.WHITE);
        g.drawLine(x - 8, y, x + 8, y);
        g.drawLine(x, y - 8, x, y + 8);
        g.setColor(Color.RED);
        g.drawOval(x - 4, y - 4, 8, 8);
    }
}
