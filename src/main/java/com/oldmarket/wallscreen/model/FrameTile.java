package com.oldmarket.wallscreen.model;

import java.awt.image.BufferedImage;
import java.util.UUID;

public class FrameTile {

    private final UUID itemFrameUuid;
    private final int tileX;
    private final int tileY;
    private final short mapId;
    private transient BufferedImage currentTile;

    public FrameTile(UUID itemFrameUuid, int tileX, int tileY, short mapId) {
        this.itemFrameUuid = itemFrameUuid;
        this.tileX = tileX;
        this.tileY = tileY;
        this.mapId = mapId;
    }

    public UUID getItemFrameUuid() {
        return itemFrameUuid;
    }

    public int getTileX() {
        return tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public short getMapId() {
        return mapId;
    }

    public BufferedImage getCurrentTile() {
        return currentTile;
    }

    public void setCurrentTile(BufferedImage currentTile) {
        this.currentTile = currentTile;
    }
}
