package com.oldmarket.wallscreen.model;

public class ControlBinding {
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final String action;

    public ControlBinding(String world, int x, int y, int z, String action) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.action = action;
    }

    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getAction() { return action; }

    public String getLocationKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
