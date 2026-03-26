package com.oldmarket.wallscreen.command;

import com.oldmarket.wallscreen.WallScreenPlugin;
import com.oldmarket.wallscreen.config.ScreenConfigIO;
import com.oldmarket.wallscreen.model.ScreenOptions;
import com.oldmarket.wallscreen.model.WallScreen;
import com.oldmarket.wallscreen.service.ScreenManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class WallScreenCommand implements CommandExecutor, TabCompleter {

    private final WallScreenPlugin plugin;
    private final ScreenManager screenManager;

    public WallScreenCommand(WallScreenPlugin plugin, ScreenManager screenManager) {
        this.plugin = plugin;
        this.screenManager = screenManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            plugin.reloadConfig();
            screenManager.load();
            sender.sendMessage(ChatColor.GREEN + "WallScreen config reloaded.");
            return true;
        }
        if ("list".equals(sub)) {
            Collection<WallScreen> screens = screenManager.getScreens();
            if (screens.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No screens registered.");
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + "Registered screens:");
            for (WallScreen screen : screens) {
                sender.sendMessage(ChatColor.GRAY + "- " + screen.getName()
                        + " (" + screen.getWidthTiles() + "x" + screen.getHeightTiles()
                        + ", host=" + screen.getOptions().getVncHost() + ":" + screen.getOptions().getVncPort()
                        + ", res=" + screen.getOptions().getWidthPx() + "x" + screen.getOptions().getHeightPx()
                        + ", fps=" + screen.getOptions().getFps() + ")");
            }
            return true;
        }
        if ("remove".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /wallscreen remove <name>");
                return true;
            }
            boolean removed = screenManager.removeScreen(args[1]);
            sender.sendMessage(removed ? ChatColor.GREEN + "Screen removed." : ChatColor.RED + "Screen not found.");
            return true;
        }
        if ("create".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /wallscreen create <name> <widthTiles> <heightTiles> [host] [port] [password|-] [widthPx] [heightPx] [fps] [bitrateKbps]");
                return true;
            }
            int widthTiles;
            int heightTiles;
            try {
                widthTiles = Integer.parseInt(args[2]);
                heightTiles = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Width and height must be numbers.");
                return true;
            }
            ScreenOptions options = ScreenConfigIO.readDefaultOptions(plugin);
            if (args.length >= 5 && !"-".equals(args[4])) {
                options.setVncHost(args[4]);
                options.setVncEnabled(true);
            }
            if (args.length >= 6 && !"-".equals(args[5])) {
                options.setVncPort(parseInt(args[5], options.getVncPort()));
                options.setVncEnabled(true);
            }
            if (args.length >= 7) {
                options.setVncPassword("-".equals(args[6]) ? "" : args[6]);
                options.setVncEnabled(true);
            }
            if (args.length >= 8 && !"-".equals(args[7])) { options.setWidthPx(parseInt(args[7], options.getWidthPx())); }
            if (args.length >= 9 && !"-".equals(args[8])) { options.setHeightPx(parseInt(args[8], options.getHeightPx())); }
            if (args.length >= 10 && !"-".equals(args[9])) { options.setFps(parseInt(args[9], options.getFps())); }
            if (args.length >= 11 && !"-".equals(args[10])) { options.setBitrateKbps(parseInt(args[10], options.getBitrateKbps())); }

            WallScreen screen = screenManager.createScreenFromLooking((Player) sender, args[1], widthTiles, heightTiles, options);
            if (screen == null) {
                sender.sendMessage(ChatColor.RED + "Could not create screen. Look at the bottom-left frame of a full rectangle of item frames.");
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + "Screen created: " + screen.getName());
            return true;
        }
        if ("demo".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /wallscreen demo <name> <on|off>");
                return true;
            }
            WallScreen screen = requireScreen(sender, args[1]);
            if (screen == null) {
                return true;
            }
            boolean enabled = "on".equalsIgnoreCase(args[2]);
            screen.useDemoSource(enabled);
            screenManager.save();
            sender.sendMessage(ChatColor.GREEN + "Demo mode for " + screen.getName() + ": " + enabled);
            return true;
        }
        if ("reconnect".equals(sub)) {
            WallScreen screen = requireScreen(sender, args.length >= 2 ? args[1] : null);
            if (screen == null) {
                return true;
            }
            screen.reconnect();
            sender.sendMessage(ChatColor.GREEN + "Reconnect requested for " + screen.getName());
            return true;
        }
        if ("setvnc".equals(sub)) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /wallscreen setvnc <name> <host> <port> [password|-]");
                return true;
            }
            WallScreen screen = requireScreen(sender, args[1]);
            if (screen == null) {
                return true;
            }
            screen.getOptions().setVncHost(args[2]);
            screen.getOptions().setVncPort(parseInt(args[3], screen.getOptions().getVncPort()));
            if (args.length >= 5) {
                screen.getOptions().setVncPassword("-".equals(args[4]) ? "" : args[4]);
            }
            screen.getOptions().setVncEnabled(true);
            screen.reconnect();
            screenManager.save();
            sender.sendMessage(ChatColor.GREEN + "VNC settings updated.");
            return true;
        }
        if ("setres".equals(sub)) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /wallscreen setres <name> <widthPx> <heightPx>");
                return true;
            }
            WallScreen screen = requireScreen(sender, args[1]);
            if (screen == null) {
                return true;
            }
            screen.getOptions().setWidthPx(parseInt(args[2], screen.getOptions().getWidthPx()));
            screen.getOptions().setHeightPx(parseInt(args[3], screen.getOptions().getHeightPx()));
            screen.reconnect();
            screenManager.save();
            sender.sendMessage(ChatColor.GREEN + "Resolution updated.");
            return true;
        }
        if ("setfps".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /wallscreen setfps <name> <fps>");
                return true;
            }
            WallScreen screen = requireScreen(sender, args[1]);
            if (screen == null) {
                return true;
            }
            screen.getOptions().setFps(parseInt(args[2], screen.getOptions().getFps()));
            screenManager.save();
            sender.sendMessage(ChatColor.GREEN + "FPS updated.");
            return true;
        }
        if ("setbitrate".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /wallscreen setbitrate <name> <kbps>");
                return true;
            }
            WallScreen screen = requireScreen(sender, args[1]);
            if (screen == null) {
                return true;
            }
            screen.getOptions().setBitrateKbps(parseInt(args[2], screen.getOptions().getBitrateKbps()));
            screenManager.save();
            sender.sendMessage(ChatColor.GREEN + "Bitrate updated.");
            return true;
        }
        if ("bind".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /wallscreen bind <screen> <action>");
                sender.sendMessage(ChatColor.GRAY + "Actions: mouse-left mouse-right mouse-up mouse-down lmb rmb key:A key:ENTER key:SPACE");
                return true;
            }
            String action = join(args, 2);
            boolean ok = screenManager.bindLookedBlock((Player) sender, args[1], action);
            sender.sendMessage(ok ? ChatColor.GREEN + "Control bound: " + action : ChatColor.RED + "Could not bind control. Look at a button, lever, or pressure plate.");
            return true;
        }
        if ("unbind".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /wallscreen unbind <screen>");
                return true;
            }
            boolean ok = screenManager.unbindLookedBlock((Player) sender, args[1]);
            sender.sendMessage(ok ? ChatColor.GREEN + "Control unbound." : ChatColor.RED + "Binding not found on looked block.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen reload");
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen list");
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen create <name> <widthTiles> <heightTiles> [host] [port] [password|-] [widthPx] [heightPx] [fps] [bitrateKbps]");
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen setvnc <name> <host> <port> [password|-]");
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen setres <name> <widthPx> <heightPx>");
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen setfps <name> <fps>");
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen setbitrate <name> <kbps>");
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen reconnect <name>");
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen bind <screen> <action>");
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen unbind <screen>");
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen remove <name>");
        sender.sendMessage(ChatColor.YELLOW + "/wallscreen demo <name> <on|off>");
    }

    private WallScreen requireScreen(CommandSender sender, String name) {
        WallScreen screen = screenManager.getScreen(name);
        if (screen == null) {
            sender.sendMessage(ChatColor.RED + "Screen not found.");
        }
        return screen;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String join(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("reload", "list", "create", "remove", "demo", "reconnect", "setvnc", "setres", "setfps", "setbitrate", "bind", "unbind"), args[0]);
        }
        if (args.length == 2 && Arrays.asList("remove", "demo", "reconnect", "setvnc", "setres", "setfps", "setbitrate", "bind", "unbind").contains(args[0].toLowerCase())) {
            List<String> names = new ArrayList<String>();
            for (WallScreen screen : screenManager.getScreens()) {
                names.add(screen.getName());
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && "demo".equalsIgnoreCase(args[0])) {
            return filter(Arrays.asList("on", "off"), args[2]);
        }
        if (args.length == 3 && "bind".equalsIgnoreCase(args[0])) {
            return filter(Arrays.asList("mouse-left", "mouse-right", "mouse-up", "mouse-down", "lmb", "rmb", "key:ENTER", "key:SPACE", "key:ESC", "key:A", "key:D", "key:W", "key:S"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> source, String token) {
        List<String> result = new ArrayList<String>();
        String lower = token.toLowerCase();
        for (String entry : source) {
            if (entry.toLowerCase().startsWith(lower)) {
                result.add(entry);
            }
        }
        return result;
    }
}
