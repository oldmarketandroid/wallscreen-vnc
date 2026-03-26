package com.oldmarket.wallscreen;

import com.oldmarket.wallscreen.command.WallScreenCommand;
import com.oldmarket.wallscreen.control.BoundControlListener;
import com.oldmarket.wallscreen.input.ScreenClickListener;
import com.oldmarket.wallscreen.service.FrameUpdateService;
import com.oldmarket.wallscreen.service.ScreenManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class WallScreenPlugin extends JavaPlugin {

    private static WallScreenPlugin instance;

    private ScreenManager screenManager;
    private FrameUpdateService frameUpdateService;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.screenManager = new ScreenManager(this);
        this.screenManager.load();
        this.frameUpdateService = new FrameUpdateService(this, screenManager);
        this.frameUpdateService.start();

        PluginCommand command = getCommand("wallscreen");
        if (command != null) {
            WallScreenCommand executor = new WallScreenCommand(this, screenManager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new ScreenClickListener(this, screenManager), this);
        getServer().getPluginManager().registerEvents(new BoundControlListener(screenManager), this);
        if (isDebugMode()) {
            getLogger().info("WallScreenVNC enabled.");
        }
    }

    @Override
    public void onDisable() {
        if (frameUpdateService != null) {
            frameUpdateService.stop();
        }
        if (screenManager != null) {
            screenManager.shutdownAll();
            screenManager.save();
        }
        if (isDebugMode()) {
            getLogger().info("WallScreenVNC disabled.");
        }
    }

    public ScreenManager getScreenManager() {
        return screenManager;
    }

    public boolean isDebugMode() {
        return getConfig().getBoolean("debug", false);
    }

    public int getMaxTileUpdatesPerTick() {
        return Math.max(1, getConfig().getInt("performance.max-tile-updates-per-tick", 4));
    }

    public boolean isNeighborPriorityEnabled() {
        return getConfig().getBoolean("performance.prioritize-neighbor-tiles", true);
    }

    public static boolean isDebugModeStatic() {
        return instance != null && instance.isDebugMode();
    }
}
