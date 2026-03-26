package com.oldmarket.wallscreen.input;

import com.oldmarket.wallscreen.WallScreenPlugin;
import com.oldmarket.wallscreen.model.FrameTile;
import com.oldmarket.wallscreen.model.WallScreen;
import com.oldmarket.wallscreen.service.ScreenManager;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

public class ScreenClickListener implements Listener {

    private static final double FRAME_DISPLAY_SIZE = 0.75D;
    private static final double UV_TOLERANCE = 0.12D;

    private final WallScreenPlugin plugin;
    private final ScreenManager screenManager;

    public ScreenClickListener(WallScreenPlugin plugin, ScreenManager screenManager) {
        this.plugin = plugin;
        this.screenManager = screenManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof ItemFrame)) {
            return;
        }
        ItemFrame frame = (ItemFrame) clicked;
        WallScreen screen = screenManager.getByFrame(frame.getUniqueId());
        if (screen == null) {
            return;
        }

        event.setCancelled(true);

        FrameTile tile = screen.getTileByFrame(frame.getUniqueId());
        if (tile == null) {
            return;
        }

        Vector uv = calculateLocalUv(event.getPlayer(), frame);
        if (uv == null) {
            return;
        }

        int canvasX = tile.getTileX() * 128 + clamp((int) Math.floor(uv.getX() * 128.0D), 0, 127);
        int canvasY = (screen.getHeightTiles() - 1 - tile.getTileY()) * 128 + clamp((int) Math.floor(uv.getY() * 128.0D), 0, 127);
        int logicalX = screen.toLogicalX(canvasX);
        int logicalY = screen.toLogicalY(canvasY);

        screen.prioritizeTile(tile.getTileX(), tile.getTileY(), plugin.isNeighborPriorityEnabled());
        screen.clickLeftAt(logicalX, logicalY);
        screen.refreshInteractiveFeedback(1);
    }

    private Vector calculateLocalUv(Player player, ItemFrame frame) {
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector();
        Vector direction = eye.getDirection().normalize();
        Vector center = frame.getLocation().toVector();
        BlockFace face = frame.getFacing();
        Vector normal = new Vector(face.getModX(), face.getModY(), face.getModZ());

        double denominator = direction.dot(normal);
        if (Math.abs(denominator) < 1.0E-5D) {
            return null;
        }

        double t = center.clone().subtract(origin).dot(normal) / denominator;
        if (t <= 0.0D) {
            return null;
        }

        Vector hit = origin.clone().add(direction.multiply(t));
        Vector relative = hit.subtract(center);

        Vector right = getRightVector(face);
        Vector up = new Vector(0, 1, 0);
        double localX = 0.5D + (relative.dot(right) / FRAME_DISPLAY_SIZE);
        double localY = 0.5D - (relative.dot(up) / FRAME_DISPLAY_SIZE);

        if (localX < -UV_TOLERANCE || localX > 1.0D + UV_TOLERANCE || localY < -UV_TOLERANCE || localY > 1.0D + UV_TOLERANCE) {
            return null;
        }
        return new Vector(clamp(localX, 0.0D, 1.0D), clamp(localY, 0.0D, 1.0D), 0);
    }

    private Vector getRightVector(BlockFace face) {
        if (face == BlockFace.NORTH) {
            return new Vector(-1, 0, 0);
        }
        if (face == BlockFace.SOUTH) {
            return new Vector(1, 0, 0);
        }
        if (face == BlockFace.EAST) {
            return new Vector(0, 0, -1);
        }
        if (face == BlockFace.WEST) {
            return new Vector(0, 0, 1);
        }
        return new Vector(1, 0, 0);
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
