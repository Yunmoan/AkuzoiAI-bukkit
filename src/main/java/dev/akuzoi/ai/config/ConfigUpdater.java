package dev.akuzoi.ai.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class ConfigUpdater {
    private static final int CURRENT_CONFIG_VERSION = 10;
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigUpdater() {
    }

    public static void update(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        int existingVersion = config.getInt("config-version", 1);
        boolean changed = false;

        if (existingVersion < CURRENT_CONFIG_VERSION) {
            backupConfig(plugin);
            migrateConfig(plugin, config, existingVersion);
            config.set("config-version", CURRENT_CONFIG_VERSION);
            changed = true;
        }

        changed |= set(config, "messages.locale", "zh_CN");
        changed |= set(config, "messages.plugin-name", "AkuzoiAI");
        changed |= set(config, "messages.plugin-description", "Using AI chat plugin for Paper/Spigot 1.20+");
        changed |= set(config, "messages.command.player-only", "Only players can use this command.");
        changed |= set(config, "messages.command.admin.usage", "/akuzoiai <reload|clear|info>");
        changed |= set(config, "messages.command.admin.reloaded", "AkuzoiAI reloaded.");
        changed |= set(config, "messages.command.admin.clear.self", "Your AI memory has been cleared.");
        changed |= set(config, "messages.command.admin.clear.all", "All AI memories cleared.");
        changed |= set(config, "messages.command.admin.clear.global", "Global AI memory cleared.");
        changed |= set(config, "messages.command.admin.clear.player", "AI memory cleared for {player}.");
        changed |= set(config, "messages.command.admin.clear.player-offline", "Player is not online: {player}");
        changed |= set(config, "messages.command.admin.clear.no-permission-shared", "You do not have permission to clear shared memory in the current memory mode.");
        changed |= set(config, "messages.command.admin.clear.no-permission-global", "You do not have permission to clear global memory.");
        changed |= set(config, "messages.command.admin.clear.no-permission-player", "You do not have permission to clear other players' memory.");
        changed |= set(config, "messages.command.admin.info.title", "AkuzoiAI info:");
        changed |= set(config, "messages.command.admin.info.version", "- version: {version}");
        changed |= set(config, "messages.command.admin.info.memory-mode", "- memory mode: {memoryMode}");
        changed |= set(config, "messages.command.admin.info.model", "- model: {model}");
        changed |= set(config, "messages.command.admin.info.base-url", "- base url: {baseUrl}");
        changed |= set(config, "messages.command.admin.info.debug", "- debug: {debug}");
        changed |= set(config, "messages.command.admin.info.log-replies", "- log replies to console: {value}");
        changed |= set(config, "messages.command.admin.info.thinking-visible", "- thinking visible: {value}");
        changed |= set(config, "messages.command.admin.info.time-enabled", "- time enabled: {value}");
        changed |= set(config, "messages.command.admin.info.time-zone", "- time zone: {timeZone}");
        changed |= set(config, "messages.command.admin.info.proactive-enabled", "- proactive enabled: {value}");
        changed |= set(config, "messages.command.admin.info.gift-enabled", "- gift enabled: {value}");
        changed |= set(config, "messages.prompt.proactive", "Please start a natural conversation with the player based on recent behavior: {behavior}");
        changed |= set(config, "messages.command.usage", "/ai <message>");
        changed |= set(config, "actionbar.enabled", true);
        changed |= set(config, "actionbar.thinking", "AI看了一眼");
        changed |= set(config, "behavior.trigger-probability", 0.35);
        changed |= set(config, "ai.type", "custom");
        changed |= set(config, "ai.akuzoi.api-key", "");
        changed |= set(config, "ai.akuzoi.role", "qing");
        changed |= set(config, "proactive.broadcast-visible-reply", true);
        changed |= set(config, "proactive.show-thinking-message", false);
        changed |= set(config, "system-message.enabled", true);
        changed |= set(config, "system-message.probability", 0.15);
        changed |= set(config, "system-message.show-thinking-message", false);

        if (changed) {
            plugin.saveConfig();
            plugin.getLogger().info("config.yml has been updated with missing default options.");
        }
    }

    private static void migrateConfig(JavaPlugin plugin, FileConfiguration config, int existingVersion) {
        if (existingVersion < 2) {
            migrateV1ToV2(plugin, config);
        }
        if (existingVersion < 3) {
            migrateV2ToV3(plugin, config);
        }
        if (existingVersion < 4) {
            migrateV3ToV4(plugin, config);
        }
        if (existingVersion < 5) {
            migrateV4ToV5(plugin, config);
        }
        if (existingVersion < 6) {
            migrateV5ToV6(plugin, config);
        }
        if (existingVersion < 7) {
            migrateV6ToV7(plugin, config);
        }
        if (existingVersion < 8) {
            migrateV7ToV8(plugin, config);
        }
        if (existingVersion < 9) {
            migrateV8ToV9(plugin, config);
        }
        if (existingVersion < 10) {
            migrateV9ToV10(plugin, config);
        }
    }

    private static void migrateV1ToV2(JavaPlugin plugin, FileConfiguration config) {
        if (!config.isSet("gift.trigger-token")) {
            config.set("gift.trigger-token", "[[GIVE_ITEM]]");
        }
        if (!config.isSet("debug.think-tag-to-console")) {
            config.set("debug.think-tag-to-console", true);
        }
        plugin.getLogger().info("Applied migration v1 -> v2 for config.yml.");
    }

    private static void migrateV2ToV3(JavaPlugin plugin, FileConfiguration config) {
        if (!config.isSet("messages.locale")) {
            config.set("messages.locale", "zh_CN");
        }
        plugin.getLogger().info("Applied migration v2 -> v3 for config.yml.");
    }

    private static void migrateV3ToV4(JavaPlugin plugin, FileConfiguration config) {
        if (!config.isSet("actionbar.enabled")) {
            config.set("actionbar.enabled", true);
        }
        if (!config.isSet("actionbar.thinking")) {
            config.set("actionbar.thinking", "AI看了一眼");
        }
        plugin.getLogger().info("Applied migration v3 -> v4 for config.yml.");
    }

    private static void migrateV4ToV5(JavaPlugin plugin, FileConfiguration config) {
        if (!config.isSet("messages.command.admin.usage")) {
            config.set("messages.command.admin.usage", "/akuzoiai <reload|clear|info>");
        }
        plugin.getLogger().info("Applied migration v4 -> v5 for config.yml.");
    }

    private static void migrateV5ToV6(JavaPlugin plugin, FileConfiguration config) {
        if (!config.isSet("behavior.trigger-probability")) {
            config.set("behavior.trigger-probability", config.getDouble("proactive.probability", 0.35));
        }
        plugin.getLogger().info("Applied migration v5 -> v6 for config.yml.");
    }

    private static void migrateV6ToV7(JavaPlugin plugin, FileConfiguration config) {
        if (!config.isSet("ai.type")) {
            config.set("ai.type", "custom");
        }
        if (!config.isSet("ai.akuzoi.api-key")) {
            config.set("ai.akuzoi.api-key", "");
        }
        if (!config.isSet("ai.akuzoi.role")) {
            config.set("ai.akuzoi.role", "qing");
        }
        plugin.getLogger().info("Applied migration v6 -> v7 for config.yml.");
    }

    private static void migrateV7ToV8(JavaPlugin plugin, FileConfiguration config) {
        if (!config.isSet("proactive.broadcast-visible-reply")) {
            config.set("proactive.broadcast-visible-reply", true);
        }
        if (!config.isSet("proactive.show-thinking-message")) {
            config.set("proactive.show-thinking-message", false);
        }
        plugin.getLogger().info("Applied migration v7 -> v8 for config.yml.");
    }

    private static void migrateV8ToV9(JavaPlugin plugin, FileConfiguration config) {
        if (!config.isSet("system-message.enabled")) {
            config.set("system-message.enabled", true);
        }
        if (!config.isSet("system-message.probability")) {
            config.set("system-message.probability", 0.15);
        }
        if (!config.isSet("system-message.show-thinking-message")) {
            config.set("system-message.show-thinking-message", false);
        }
        if (!config.isSet("proactive.broadcast-visible-reply")) {
            config.set("proactive.broadcast-visible-reply", true);
        }
        plugin.getLogger().info("Applied migration v8 -> v9 for config.yml.");
    }

    private static void migrateV9ToV10(JavaPlugin plugin, FileConfiguration config) {
        if (!config.isSet("location-lookup.enabled")) {
            config.set("location-lookup.enabled", false);
        }
        plugin.getLogger().info("Applied migration v9 -> v10 for config.yml.");
    }

    private static void backupConfig(JavaPlugin plugin) {
        Path configPath = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.exists(configPath)) {
            return;
        }

        Path backupPath = plugin.getDataFolder().toPath().resolve(
                "config.yml.bak-" + LocalDateTime.now().format(BACKUP_FORMAT)
        );
        try {
            Files.copy(configPath, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
            plugin.getLogger().info("Backed up existing config to " + backupPath.getFileName() + ".");
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to back up config.yml: " + ex.getMessage());
        }
    }

    private static boolean set(FileConfiguration config, String path, Object value) {
        if (config.isSet(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }
}
