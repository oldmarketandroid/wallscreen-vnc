package com.oldmarket.wallscreen.render;

import com.oldmarket.wallscreen.model.FrameTile;
import com.oldmarket.wallscreen.model.WallScreen;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class TileRenderer extends MapRenderer {

    private final WallScreen screen;
    private final FrameTile tile;

    public TileRenderer(WallScreen screen, FrameTile tile) {
        super(false);
        this.screen = screen;
        this.tile = tile;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        BufferedImage source = tile.getCurrentTile();
        if (source == null) {
            source = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = source.createGraphics();
            g.drawString("Loading...", 20, 64);
            g.dispose();
        }
        canvas.drawImage(0, 0, source);
    }

    public FrameTile getTile() {
        return tile;
    }

    public WallScreen getScreen() {
        return screen;
    }
}
