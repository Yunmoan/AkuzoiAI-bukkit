package dev.akuzoi.ai.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public final class Messages {
    private Messages() {
    }

    public static String locale(FileConfiguration config) {
        return config.getString("messages.locale", "zh_CN");
    }

    public static String get(FileConfiguration config, String path, String fallback) {
        return config.getString("messages." + path, fallback);
    }

    public static String get(FileConfiguration config, String locale, String path, String fallback) {
        String localized = config.getString("messages." + locale.toLowerCase(Locale.ROOT) + "." + path);
        return localized != null ? localized : get(config, path, fallback);
    }

    public static String format(String template, Object... args) {
        String result = template;
        for (int i = 0; i + 1 < args.length; i += 2) {
            result = result.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return result;
    }
}
