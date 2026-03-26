package com.oldmarket.wallscreen.service;

import com.oldmarket.wallscreen.WallScreenPlugin;
import com.oldmarket.wallscreen.config.ScreenConfigIO;
import com.oldmarket.wallscreen.model.ControlBinding;
import com.oldmarket.wallscreen.model.FrameTile;
import com.oldmarket.wallscreen.model.ScreenOptions;
import com.oldmarket.wallscreen.model.WallScreen;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScreenManager {

    private final WallScreenPlugin plugin;
    private final Map<String, WallScreen> screens = new LinkedHashMap<String, WallScreen>();

    public ScreenManager(WallScreenPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        shutdownAll();
        screens.clear();
        for (WallScreen screen : ScreenConfigIO.load(plugin)) {
            screen.initializeRuntime();
            screens.put(screen.getName().toLowerCase(), screen);
        }
    }

    public void save() {
        ScreenConfigIO.save(plugin, new ArrayList<WallScreen>(screens.values()));
    }

    public Collection<WallScreen> getScreens() {
        return Collections.unmodifiableCollection(screens.values());
    }

    public WallScreen getScreen(String name) {
        return name == null ? null : screens.get(name.toLowerCase());
    }

    public boolean removeScreen(String name) {
        WallScreen removed = screens.remove(name.toLowerCase());
        if (removed != null) {
            removed.shutdownRuntime();
            save();
            return true;
        }
        return false;
    }

    public void shutdownAll() {
        for (WallScreen screen : screens.values()) {
            screen.shutdownRuntime();
        }
    }

    public WallScreen getByFrame(UUID frameUuid) {
        for (WallScreen screen : screens.values()) {
            if (screen.getTileByFrame(frameUuid) != null) {
                return screen;
            }
        }
        return null;
    }

    public WallScreen createScreenFromLooking(Player player, String name, int widthTiles, int heightTiles, ScreenOptions options) {
        ItemFrame base = findLookedAtFrame(player, 6.0D);
        if (base == null) {
            return null;
        }
        List<ItemFrame> orderedFrames = collectRectangle(base, widthTiles, heightTiles);
        if (orderedFrames.size() != widthTiles * heightTiles) {
            return null;
        }

        List<FrameTile> tiles = new ArrayList<FrameTile>();
        World world = base.getWorld();

        for (int tileY = 0; tileY < heightTiles; tileY++) {
            for (int tileX = 0; tileX < widthTiles; tileX++) {
                int index = tileY * widthTiles + tileX;
                ItemFrame frame = orderedFrames.get(index);
                MapView mapView = Bukkit.createMap(world);
                short mapId = mapView.getId();
                frame.setItem(new ItemStack(Material.MAP, 1, mapId));
                tiles.add(new FrameTile(frame.getUniqueId(), tileX, tileY, mapId));
            }
        }

        WallScreen screen = new WallScreen(name, world.getName(), widthTiles, heightTiles, options, tiles);
        screen.initializeRuntime();
        screens.put(name.toLowerCase(), screen);
        save();
        return screen;
    }

    public boolean triggerBoundControl(Location location, Player player) {
        if (location == null) {
            return false;
        }
        String key = location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        int step = plugin.getConfig().getInt("input.mouse-step-px", 24);
        for (WallScreen screen : screens.values()) {
            for (ControlBinding binding : screen.getBindings()) {
                if (binding.getLocationKey().equals(key)) {
                    return screen.triggerAction(binding.getAction(), step);
                }
            }
        }
        return false;
    }

    public boolean bindLookedBlock(Player player, String screenName, String action) {
        WallScreen screen = getScreen(screenName);
        if (screen == null) {
            return false;
        }
        Block block = player.getTargetBlock((java.util.Set<Material>) null, 6);
        if (block == null || block.getType() == Material.AIR) {
            return false;
        }
        screen.addBinding(new ControlBinding(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), action));
        save();
        return true;
    }

    public boolean unbindLookedBlock(Player player, String screenName) {
        WallScreen screen = getScreen(screenName);
        if (screen == null) {
            return false;
        }
        Block block = player.getTargetBlock((java.util.Set<Material>) null, 6);
        if (block == null || block.getType() == Material.AIR) {
            return false;
        }
        String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        int before = screen.getBindings().size();
        screen.clearBindingAt(key);
        save();
        return before != screen.getBindings().size();
    }

    private ItemFrame findLookedAtFrame(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector direction = eye.getDirection().normalize();
        ItemFrame best = null;
        double bestDistance = maxDistance + 1.0D;
        for (Entity entity : player.getWorld().getEntities()) {
            if (!(entity instanceof ItemFrame)) {
                continue;
            }
            double distance = entity.getLocation().distance(eye);
            if (distance > maxDistance) {
                continue;
            }
            org.bukkit.util.Vector toEntity = entity.getLocation().toVector().subtract(eye.toVector()).normalize();
            double dot = toEntity.dot(direction);
            if (dot < 0.985D) {
                continue;
            }
            if (distance < bestDistance) {
                best = (ItemFrame) entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    private List<ItemFrame> collectRectangle(ItemFrame base, int widthTiles, int heightTiles) {
        List<ItemFrame> result = new ArrayList<ItemFrame>();
        World world = base.getWorld();
        Location baseLoc = base.getLocation();
        int facingX = base.getFacing().getModX();
        int facingZ = base.getFacing().getModZ();

        int rightX;
        int rightZ;
        if (facingX != 0) {
            rightX = 0;
            rightZ = -facingX;
        } else {
            rightX = facingZ;
            rightZ = 0;
        }

        for (int tileY = 0; tileY < heightTiles; tileY++) {
            for (int tileX = 0; tileX < widthTiles; tileX++) {
                Location expected = baseLoc.clone().add(rightX * tileX, tileY, rightZ * tileX);
                ItemFrame frame = findFrameAt(world, expected, base.getFacing());
                if (frame == null) {
                    return Collections.emptyList();
                }
                result.add(frame);
            }
        }
        return result;
    }

    private ItemFrame findFrameAt(World world, Location location, org.bukkit.block.BlockFace face) {
        for (Entity entity : world.getNearbyEntities(location, 0.3D, 0.3D, 0.3D)) {
            if (entity instanceof ItemFrame) {
                ItemFrame frame = (ItemFrame) entity;
                if (frame.getFacing() == face) {
                    return frame;
                }
            }
        }
        return null;
    }
}
