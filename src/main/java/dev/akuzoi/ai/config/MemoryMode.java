package dev.akuzoi.ai.config;

import java.util.Locale;

public enum MemoryMode {
    NONE,
    PLAYER,
    GLOBAL;

    public static MemoryMode from(String raw) {
        if (raw == null) {
            return PLAYER;
        }
        try {
            return MemoryMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PLAYER;
        }
    }
}
