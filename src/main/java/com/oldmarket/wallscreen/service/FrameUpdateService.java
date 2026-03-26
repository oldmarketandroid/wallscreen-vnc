package com.oldmarket.wallscreen.service;

import com.oldmarket.wallscreen.WallScreenPlugin;
import com.oldmarket.wallscreen.model.WallScreen;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.awt.image.BufferedImage;

public class FrameUpdateService {

    private final WallScreenPlugin plugin;
    private final ScreenManager screenManager;
    private BukkitTask task;
    private long tickCounter;

    public FrameUpdateService(WallScreenPlugin plugin, ScreenManager screenManager) {
        this.plugin = plugin;
        this.screenManager = screenManager;
    }

    public void start() {
        stop();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        tickCounter++;
        int maxTileUpdatesPerTick = plugin.getMaxTileUpdatesPerTick();
        for (WallScreen screen : screenManager.getScreens()) {
            if (shouldGrabNewFrame(screen)) {
                BufferedImage fullFrame = screen.nextFrame();
                screen.captureRenderedFrame(fullFrame);
            }
            screen.flushPendingTileUpdates(maxTileUpdatesPerTick);
        }
    }

    private boolean shouldGrabNewFrame(WallScreen screen) {
        int fps = Math.max(1, screen.getOptions().getFps());
        int ticksPerFrame = Math.max(1, Math.round(20.0f / fps));
        return tickCounter % ticksPerFrame == 0L;
    }
}
