package com.oldmarket.wallscreen.control;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class KeysymMapper {
    private static final Map<String, Integer> SPECIAL = new HashMap<String, Integer>();
    static {
        SPECIAL.put("ENTER", 0xFF0D);
        SPECIAL.put("RETURN", 0xFF0D);
        SPECIAL.put("ESC", 0xFF1B);
        SPECIAL.put("ESCAPE", 0xFF1B);
        SPECIAL.put("TAB", 0xFF09);
        SPECIAL.put("SPACE", 0x20);
        SPECIAL.put("BACKSPACE", 0xFF08);
        SPECIAL.put("UP", 0xFF52);
        SPECIAL.put("DOWN", 0xFF54);
        SPECIAL.put("LEFT", 0xFF51);
        SPECIAL.put("RIGHT", 0xFF53);
        SPECIAL.put("SHIFT", 0xFFE1);
        SPECIAL.put("CTRL", 0xFFE3);
        SPECIAL.put("CONTROL", 0xFFE3);
        SPECIAL.put("ALT", 0xFFE9);
        SPECIAL.put("F1", 0xFFBE);
        SPECIAL.put("F2", 0xFFBF);
        SPECIAL.put("F3", 0xFFC0);
        SPECIAL.put("F4", 0xFFC1);
        SPECIAL.put("F5", 0xFFC2);
        SPECIAL.put("F6", 0xFFC3);
        SPECIAL.put("F7", 0xFFC4);
        SPECIAL.put("F8", 0xFFC5);
        SPECIAL.put("F9", 0xFFC6);
        SPECIAL.put("F10", 0xFFC7);
        SPECIAL.put("F11", 0xFFC8);
        SPECIAL.put("F12", 0xFFC9);
    }

    private KeysymMapper() {}

    public static Integer toKeysym(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String t = token.trim();
        if (t.length() == 1) {
            return (int) t.charAt(0);
        }
        String upper = t.toUpperCase(Locale.ROOT);
        if (SPECIAL.containsKey(upper)) {
            return SPECIAL.get(upper);
        }
        if (upper.startsWith("0X")) {
            try {
                return Integer.parseInt(upper.substring(2), 16);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
