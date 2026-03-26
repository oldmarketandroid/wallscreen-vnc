package com.oldmarket.wallscreen.model;

import com.oldmarket.wallscreen.control.KeysymMapper;
import com.oldmarket.wallscreen.render.TileRenderer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.map.MapView;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WallScreen {

    private final String name;
    private final String worldName;
    private final int widthTiles;
    private final int heightTiles;
    private final ScreenOptions options;
    private final List<FrameTile> tiles;
    private final List<ControlBinding> bindings = new ArrayList<ControlBinding>();
    private transient FrameSource frameSource;
    private transient int cursorX;
    private transient int cursorY;
    private transient boolean demoEnabled;
    private transient BufferedImage lastCanvasFrame;
    private transient Map<Integer, FrameTile> tilesByKey;
    private transient LinkedHashSet<Integer> dirtyTileKeys;
    private transient LinkedHashSet<Integer> priorityTileKeys;

    public WallScreen(String name, String worldName, int widthTiles, int heightTiles, ScreenOptions options, List<FrameTile> tiles) {
        this(name, worldName, widthTiles, heightTiles, options, tiles, Collections.<ControlBinding>emptyList());
    }

    public WallScreen(String name, String worldName, int widthTiles, int heightTiles, ScreenOptions options, List<FrameTile> tiles, List<ControlBinding> bindings) {
        this.name = name;
        this.worldName = worldName;
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;
        this.options = options;
        this.tiles = new ArrayList<FrameTile>(tiles);
        if (bindings != null) {
            this.bindings.addAll(bindings);
        }
        this.cursorX = Math.max(0, options.getWidthPx() / 2);
        this.cursorY = Math.max(0, options.getHeightPx() / 2);
        rebuildTileIndex();
        resetRenderState();
    }

    public void initializeRuntime() {
        shutdownRuntime();
        resetRenderState();
        this.demoEnabled = !options.isVncEnabled();
        this.frameSource = demoEnabled ? new DemoFrameSource(options.getWidthPx(), options.getHeightPx()) : new VncFrameSource(name, options);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        for (FrameTile tile : tiles) {
            ItemFrame itemFrame = findItemFrame(world, tile.getItemFrameUuid());
            if (itemFrame == null) {
                continue;
            }
            MapView view = Bukkit.getMap(tile.getMapId());
            if (view == null) {
                continue;
            }
            for (org.bukkit.map.MapRenderer renderer : new ArrayList<org.bukkit.map.MapRenderer>(view.getRenderers())) {
                view.removeRenderer(renderer);
            }
            view.addRenderer(new TileRenderer(this, tile));
        }
    }

    public void shutdownRuntime() {
        if (frameSource instanceof Closeable) {
            try {
                ((Closeable) frameSource).close();
            } catch (Exception ignored) {
            }
        }
        frameSource = null;
        resetRenderState();
    }

    public void reconnect() {
        shutdownRuntime();
        initializeRuntime();
    }

    private void rebuildTileIndex() {
        this.tilesByKey = new HashMap<Integer, FrameTile>();
        for (FrameTile tile : tiles) {
            tilesByKey.put(tileKey(tile.getTileX(), tile.getTileY()), tile);
        }
    }

    private void resetRenderState() {
        this.lastCanvasFrame = null;
        this.dirtyTileKeys = new LinkedHashSet<Integer>();
        this.priorityTileKeys = new LinkedHashSet<Integer>();
        enqueueFullRefresh();
    }

    private ItemFrame findItemFrame(World world, UUID uuid) {
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity instanceof ItemFrame && uuid.equals(entity.getUniqueId())) {
                return (ItemFrame) entity;
            }
        }
        return null;
    }

    public BufferedImage nextFrame() {
        if (frameSource == null) {
            initializeRuntime();
        }
        return frameSource.nextFrame(cursorX, cursorY, !demoEnabled || options.isVncEnabled(), options);
    }

    public void captureRenderedFrame(BufferedImage fullFrame) {
        if (fullFrame == null) {
            return;
        }

        BufferedImage canvasFrame = new BufferedImage(getCanvasWidthPixels(), getCanvasHeightPixels(), BufferedImage.TYPE_INT_RGB);
        Graphics2D canvasGraphics = canvasFrame.createGraphics();
        canvasGraphics.drawImage(fullFrame, 0, 0, canvasFrame.getWidth(), canvasFrame.getHeight(), null);
        canvasGraphics.dispose();

        if (lastCanvasFrame == null || lastCanvasFrame.getWidth() != canvasFrame.getWidth() || lastCanvasFrame.getHeight() != canvasFrame.getHeight()) {
            lastCanvasFrame = canvasFrame;
            enqueueFullRefresh();
            return;
        }

        for (FrameTile tile : tiles) {
            if (isTileDifferent(lastCanvasFrame, canvasFrame, tile)) {
                dirtyTileKeys.add(tileKey(tile.getTileX(), tile.getTileY()));
            }
        }
        lastCanvasFrame = canvasFrame;
    }

    public int flushPendingTileUpdates(int maxTiles) {
        if (lastCanvasFrame == null || maxTiles <= 0) {
            return 0;
        }
        int flushed = 0;
        while (flushed < maxTiles) {
            Integer key = pollNextTileKey();
            if (key == null) {
                break;
            }
            FrameTile tile = tilesByKey.get(key);
            if (tile == null) {
                continue;
            }
            tile.setCurrentTile(extractTileImage(lastCanvasFrame, tile));
            flushed++;
        }
        return flushed;
    }

    public void refreshInteractiveFeedback(int maxTiles) {
        captureRenderedFrame(nextFrame());
        flushPendingTileUpdates(Math.max(1, maxTiles));
    }

    public void prioritizeTile(int tileX, int tileY, boolean includeNeighbors) {
        enqueuePriority(tileX, tileY);
        if (!includeNeighbors) {
            return;
        }
        enqueuePriority(tileX - 1, tileY);
        enqueuePriority(tileX + 1, tileY);
        enqueuePriority(tileX, tileY - 1);
        enqueuePriority(tileX, tileY + 1);
        enqueuePriority(tileX - 1, tileY - 1);
        enqueuePriority(tileX + 1, tileY - 1);
        enqueuePriority(tileX - 1, tileY + 1);
        enqueuePriority(tileX + 1, tileY + 1);
    }

    public void prioritizeLogicalPoint(int logicalX, int logicalY, boolean includeNeighbors) {
        int tileX = clamp((int) Math.floor((logicalX / (double) Math.max(1, options.getWidthPx())) * widthTiles), 0, Math.max(0, widthTiles - 1));
        int tileY = clamp((int) Math.floor((logicalY / (double) Math.max(1, options.getHeightPx())) * heightTiles), 0, Math.max(0, heightTiles - 1));
        prioritizeTile(tileX, tileY, includeNeighbors);
    }

    public void enqueueFullRefresh() {
        for (FrameTile tile : tiles) {
            dirtyTileKeys.add(tileKey(tile.getTileX(), tile.getTileY()));
        }
    }

    private void enqueuePriority(int tileX, int tileY) {
        if (tileX < 0 || tileX >= widthTiles || tileY < 0 || tileY >= heightTiles) {
            return;
        }
        priorityTileKeys.add(tileKey(tileX, tileY));
    }

    private Integer pollNextTileKey() {
        if (!priorityTileKeys.isEmpty()) {
            Iterator<Integer> iterator = priorityTileKeys.iterator();
            Integer key = iterator.next();
            iterator.remove();
            dirtyTileKeys.remove(key);
            return key;
        }
        if (!dirtyTileKeys.isEmpty()) {
            Iterator<Integer> iterator = dirtyTileKeys.iterator();
            Integer key = iterator.next();
            iterator.remove();
            return key;
        }
        return null;
    }

    private boolean isTileDifferent(BufferedImage previous, BufferedImage current, FrameTile tile) {
        int srcX = tile.getTileX() * 128;
        int srcY = (heightTiles - 1 - tile.getTileY()) * 128;

        int[] previousData = ((DataBufferInt) previous.getRaster().getDataBuffer()).getData();
        int[] currentData = ((DataBufferInt) current.getRaster().getDataBuffer()).getData();
        int stride = current.getWidth();

        for (int y = 0; y < 128; y++) {
            int rowOffset = (srcY + y) * stride + srcX;
            for (int x = 0; x < 128; x++) {
                if (previousData[rowOffset + x] != currentData[rowOffset + x]) {
                    return true;
                }
            }
        }
        return false;
    }

    private BufferedImage extractTileImage(BufferedImage canvasFrame, FrameTile tile) {
        int srcX = tile.getTileX() * 128;
        int srcY = (heightTiles - 1 - tile.getTileY()) * 128;
        BufferedImage section = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = section.createGraphics();
        graphics.drawImage(canvasFrame,
                0, 0, 128, 128,
                srcX, srcY, srcX + 128, srcY + 128,
                null);
        graphics.dispose();
        return section;
    }

    public void useDemoSource(boolean enabled) {
        this.demoEnabled = enabled;
        shutdownRuntime();
        if (enabled || !options.isVncEnabled()) {
            this.frameSource = new DemoFrameSource(options.getWidthPx(), options.getHeightPx());
            this.demoEnabled = true;
        } else {
            this.frameSource = new VncFrameSource(name, options);
            this.demoEnabled = false;
        }
        enqueueFullRefresh();
    }

    public boolean isDemoEnabled() { return demoEnabled; }
    public int getCursorX() { return cursorX; }
    public int getCursorY() { return cursorY; }

    public void setCursorPosition(int cursorX, int cursorY) {
        this.cursorX = clamp(cursorX, 0, Math.max(0, options.getWidthPx() - 1));
        this.cursorY = clamp(cursorY, 0, Math.max(0, options.getHeightPx() - 1));
        prioritizeLogicalPoint(this.cursorX, this.cursorY, true);
        if (frameSource instanceof VncFrameSource) {
            ((VncFrameSource) frameSource).movePointer(this.cursorX, this.cursorY);
        }
    }

    public void moveCursorBy(int dx, int dy) {
        setCursorPosition(cursorX + dx, cursorY + dy);
    }

    public void clickLeftAt(int cursorX, int cursorY) {
        this.cursorX = clamp(cursorX, 0, Math.max(0, options.getWidthPx() - 1));
        this.cursorY = clamp(cursorY, 0, Math.max(0, options.getHeightPx() - 1));
        prioritizeLogicalPoint(this.cursorX, this.cursorY, true);
        if (frameSource instanceof VncFrameSource) {
            ((VncFrameSource) frameSource).clickLeftAt(this.cursorX, this.cursorY);
        }
    }

    public void clickLeft() {
        prioritizeLogicalPoint(cursorX, cursorY, true);
        if (frameSource instanceof VncFrameSource) {
            ((VncFrameSource) frameSource).clickLeft(cursorX, cursorY);
        }
    }

    public void clickRight() {
        prioritizeLogicalPoint(cursorX, cursorY, true);
        if (frameSource instanceof VncFrameSource) {
            ((VncFrameSource) frameSource).clickRight(cursorX, cursorY);
        }
    }

    public void sendKey(String keyName) {
        if (!(frameSource instanceof VncFrameSource)) {
            return;
        }
        Integer keysym = KeysymMapper.toKeysym(keyName);
        if (keysym != null) {
            ((VncFrameSource) frameSource).sendKeyPress(keysym.intValue());
        }
    }

    public boolean triggerAction(String action, int mouseStepPx) {
        if (action == null) {
            return false;
        }
        String normalized = action.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        String upper = normalized.toUpperCase();
        if (upper.equals("MOUSE_LEFT") || upper.equals("LEFT") || upper.equals("MOUSE-LEFT")) {
            moveCursorBy(-mouseStepPx, 0);
            refreshInteractiveFeedback(1);
            return true;
        }
        if (upper.equals("MOUSE_RIGHT") || upper.equals("RIGHT") || upper.equals("MOUSE-RIGHT")) {
            moveCursorBy(mouseStepPx, 0);
            refreshInteractiveFeedback(1);
            return true;
        }
        if (upper.equals("MOUSE_UP") || upper.equals("UP") || upper.equals("MOUSE-UP")) {
            moveCursorBy(0, -mouseStepPx);
            refreshInteractiveFeedback(1);
            return true;
        }
        if (upper.equals("MOUSE_DOWN") || upper.equals("DOWN") || upper.equals("MOUSE-DOWN")) {
            moveCursorBy(0, mouseStepPx);
            refreshInteractiveFeedback(1);
            return true;
        }
        if (upper.equals("LMB") || upper.equals("LEFT_CLICK") || upper.equals("MOUSE_LMB")) {
            clickLeft();
            refreshInteractiveFeedback(1);
            return true;
        }
        if (upper.equals("RMB") || upper.equals("RIGHT_CLICK") || upper.equals("MOUSE_RMB")) {
            clickRight();
            refreshInteractiveFeedback(1);
            return true;
        }
        if (upper.startsWith("KEY:")) {
            sendKey(normalized.substring(4));
            return true;
        }
        return false;
    }

    private int clamp(int value, int min, int max) {
        if (value < min) { return min; }
        if (value > max) { return max; }
        return value;
    }

    private int tileKey(int tileX, int tileY) {
        return (tileY << 16) | (tileX & 0xFFFF);
    }

    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public int getWidthTiles() { return widthTiles; }
    public int getHeightTiles() { return heightTiles; }
    public ScreenOptions getOptions() { return options; }
    public List<FrameTile> getTiles() { return Collections.unmodifiableList(tiles); }
    public List<ControlBinding> getBindings() { return Collections.unmodifiableList(bindings); }
    public void clearBindingAt(String locationKey) { bindings.removeIf(b -> b.getLocationKey().equals(locationKey)); }
    public void addBinding(ControlBinding binding) { clearBindingAt(binding.getLocationKey()); bindings.add(binding); }

    public int getWidthPixels() { return options.getWidthPx(); }
    public int getHeightPixels() { return options.getHeightPx(); }
    public int getCanvasWidthPixels() { return Math.max(1, widthTiles * 128); }
    public int getCanvasHeightPixels() { return Math.max(1, heightTiles * 128); }

    public int toLogicalX(int canvasX) {
        return scaleCoordinate(canvasX, getCanvasWidthPixels(), Math.max(1, options.getWidthPx()));
    }

    public int toLogicalY(int canvasY) {
        return scaleCoordinate(canvasY, getCanvasHeightPixels(), Math.max(1, options.getHeightPx()));
    }

    private int scaleCoordinate(int value, int sourceMax, int destMax) {
        if (sourceMax <= 1 || destMax <= 1) {
            return 0;
        }
        double factor = value / (double) Math.max(1, sourceMax - 1);
        int scaled = (int) Math.round(factor * (destMax - 1));
        if (scaled < 0) { return 0; }
        if (scaled >= destMax) { return destMax - 1; }
        return scaled;
    }

    public FrameTile getTileByFrame(UUID uuid) {
        for (FrameTile tile : tiles) {
            if (tile.getItemFrameUuid().equals(uuid)) {
                return tile;
            }
        }
        return null;
    }

    public interface FrameSource {
        BufferedImage nextFrame(int cursorX, int cursorY, boolean active, ScreenOptions options);
    }
}
