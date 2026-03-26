package com.oldmarket.wallscreen.config;

import com.oldmarket.wallscreen.WallScreenPlugin;
import com.oldmarket.wallscreen.model.ControlBinding;
import com.oldmarket.wallscreen.model.FrameTile;
import com.oldmarket.wallscreen.model.ScreenOptions;
import com.oldmarket.wallscreen.model.WallScreen;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ScreenConfigIO {

    private ScreenConfigIO() {
    }

    public static ScreenOptions readDefaultOptions(WallScreenPlugin plugin) {
        boolean vncEnabled = plugin.getConfig().getBoolean("defaults.vnc.enabled", false);
        String host = plugin.getConfig().getString("defaults.vnc.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("defaults.vnc.port", 5900);
        String password = plugin.getConfig().getString("defaults.vnc.password", "");
        int fps = plugin.getConfig().getInt("defaults.fps", 4);
        int widthPx = plugin.getConfig().getInt("defaults.width-px", 256);
        int heightPx = plugin.getConfig().getInt("defaults.height-px", 256);
        int bitrate = plugin.getConfig().getInt("defaults.bitrate-kbps", 1200);
        int connectTimeout = plugin.getConfig().getInt("defaults.vnc.connect-timeout-ms", 3000);
        int readTimeout = plugin.getConfig().getInt("defaults.vnc.read-timeout-ms", 3000);
        return new ScreenOptions(fps, widthPx, heightPx, bitrate, vncEnabled, host, port, password, connectTimeout, readTimeout);
    }

    public static List<WallScreen> load(WallScreenPlugin plugin) {
        List<WallScreen> result = new ArrayList<WallScreen>();
        ConfigurationSection screensSection = plugin.getConfig().getConfigurationSection("screens");
        if (screensSection == null) {
            return result;
        }
        ScreenOptions defaults = readDefaultOptions(plugin);
        for (String screenName : screensSection.getKeys(false)) {
            ConfigurationSection section = screensSection.getConfigurationSection(screenName);
            if (section == null) {
                continue;
            }
            String world = section.getString("world");
            int widthTiles = section.getInt("width-tiles");
            int heightTiles = section.getInt("height-tiles");
            ScreenOptions options = defaults.copy();
            ConfigurationSection optionSection = section.getConfigurationSection("options");
            if (optionSection != null) {
                options.setVncHost(optionSection.getString("vnc-host", options.getVncHost()));
                options.setVncPort(optionSection.getInt("vnc-port", options.getVncPort()));
                options.setVncPassword(optionSection.getString("vnc-password", options.getVncPassword()));
                options.setWidthPx(optionSection.getInt("width-px", options.getWidthPx()));
                options.setHeightPx(optionSection.getInt("height-px", options.getHeightPx()));
                options.setFps(optionSection.getInt("fps", options.getFps()));
                options.setBitrateKbps(optionSection.getInt("bitrate-kbps", options.getBitrateKbps()));
                options.setVncEnabled(optionSection.getBoolean("vnc-enabled", options.isVncEnabled()));
            }

            List<FrameTile> tiles = new ArrayList<FrameTile>();
            ConfigurationSection tileSection = section.getConfigurationSection("tiles");
            if (tileSection != null) {
                for (String tileKey : tileSection.getKeys(false)) {
                    ConfigurationSection t = tileSection.getConfigurationSection(tileKey);
                    if (t == null) {
                        continue;
                    }
                    UUID uuid = UUID.fromString(t.getString("uuid"));
                    int tileX = t.getInt("tile-x");
                    int tileY = t.getInt("tile-y");
                    short mapId = (short) t.getInt("map-id");
                    tiles.add(new FrameTile(uuid, tileX, tileY, mapId));
                }
            }

            List<ControlBinding> bindings = new ArrayList<ControlBinding>();
            ConfigurationSection bindSection = section.getConfigurationSection("bindings");
            if (bindSection != null) {
                for (String bindKey : bindSection.getKeys(false)) {
                    ConfigurationSection b = bindSection.getConfigurationSection(bindKey);
                    if (b == null) {
                        continue;
                    }
                    bindings.add(new ControlBinding(
                            b.getString("world", world),
                            b.getInt("x"),
                            b.getInt("y"),
                            b.getInt("z"),
                            b.getString("action", "")
                    ));
                }
            }

            result.add(new WallScreen(screenName, world, widthTiles, heightTiles, options, tiles, bindings));
        }
        return result;
    }

    public static void save(WallScreenPlugin plugin, List<WallScreen> screens) {
        plugin.getConfig().set("screens", null);
        for (WallScreen screen : screens) {
            String path = "screens." + screen.getName();
            plugin.getConfig().set(path + ".world", screen.getWorldName());
            plugin.getConfig().set(path + ".width-tiles", screen.getWidthTiles());
            plugin.getConfig().set(path + ".height-tiles", screen.getHeightTiles());

            ScreenOptions options = screen.getOptions();
            plugin.getConfig().set(path + ".options.vnc-enabled", options.isVncEnabled());
            plugin.getConfig().set(path + ".options.vnc-host", options.getVncHost());
            plugin.getConfig().set(path + ".options.vnc-port", options.getVncPort());
            plugin.getConfig().set(path + ".options.vnc-password", options.getVncPassword());
            plugin.getConfig().set(path + ".options.width-px", options.getWidthPx());
            plugin.getConfig().set(path + ".options.height-px", options.getHeightPx());
            plugin.getConfig().set(path + ".options.fps", options.getFps());
            plugin.getConfig().set(path + ".options.bitrate-kbps", options.getBitrateKbps());

            int index = 0;
            for (FrameTile tile : screen.getTiles()) {
                String tilePath = path + ".tiles.tile-" + index;
                plugin.getConfig().set(tilePath + ".uuid", tile.getItemFrameUuid().toString());
                plugin.getConfig().set(tilePath + ".tile-x", tile.getTileX());
                plugin.getConfig().set(tilePath + ".tile-y", tile.getTileY());
                plugin.getConfig().set(tilePath + ".map-id", (int) tile.getMapId());
                index++;
            }

            int bindIndex = 0;
            for (ControlBinding binding : screen.getBindings()) {
                String bindPath = path + ".bindings.bind-" + bindIndex;
                plugin.getConfig().set(bindPath + ".world", binding.getWorld());
                plugin.getConfig().set(bindPath + ".x", binding.getX());
                plugin.getConfig().set(bindPath + ".y", binding.getY());
                plugin.getConfig().set(bindPath + ".z", binding.getZ());
                plugin.getConfig().set(bindPath + ".action", binding.getAction());
                bindIndex++;
            }
        }
        plugin.saveConfig();
    }
}
