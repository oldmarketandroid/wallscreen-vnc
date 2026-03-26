package com.oldmarket.wallscreen.control;

import com.oldmarket.wallscreen.service.ScreenManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class BoundControlListener implements Listener {

    private final ScreenManager screenManager;

    public BoundControlListener(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) {
            return;
        }
        if (!isSupported(block.getType())) {
            return;
        }
        boolean triggered = screenManager.triggerBoundControl(block.getLocation(), event.getPlayer());
        if (triggered) {
            event.setCancelled(true);
        }
    }

    private boolean isSupported(Material material) {
        String name = material.name();
        return name.endsWith("BUTTON") || name.contains("PLATE") || name.contains("PRESSURE") || name.endsWith("LEVER");
    }
}
