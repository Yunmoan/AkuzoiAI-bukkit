package dev.akuzoi.ai.config;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import dev.akuzoi.ai.gift.GiftOption;

// 插件运行时用到的参数都放在这里。
public record PluginSettings(
        String aiType,
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        int maxTokens,
        int timeoutSeconds,
        boolean streamEnabled,
        String systemPromptFile,
        String templateFile,
        String akuzoiApiKey,
        String akuzoiRole,
        boolean debugEnabled,
        boolean logRepliesToConsole,
        boolean showThinkingMessage,
        boolean thinkTagToConsole,
        boolean actionbarEnabled,
        String actionbarThinkingText,
        boolean timeEnabled,
        String timeZone,
        String timeFormat,
        MemoryMode memoryMode,
        int maxMemoryMessages,
        boolean compressionEnabled,
        int compressAfterMessages,
        int summaryMaxTokens,
        List<String> triggerNames,
        boolean commandEnabled,
        boolean randomChatEnabled,
        double randomChatProbability,
        long randomChatCooldownSeconds,
        boolean proactiveEnabled,
        long proactiveAfterInteractionSeconds,
        long proactiveCooldownSeconds,
        double proactiveProbability,
        boolean globalBroadcastVisibleReplies,
        boolean proactiveShowThinkingMessage,
        boolean systemMessageEnabled,
        double systemMessageProbability,
        boolean systemMessageShowThinkingMessage,
        boolean locationLookupEnabled,
        boolean giftOnExplicitRequestBoost,
        double behaviorTriggerProbability,
        long behaviorWindowSeconds,
        long behaviorRetentionSeconds,
        boolean giftEnabled,
        String giftTriggerToken,
        String giftSuccessMessage,
        String giftFailureMessage,
        List<GiftOption> giftOptions,
        String chatPrefix,
        String thinkingMessage,
        String errorMessage
) {
    // 从配置文件里读出各项开关和参数。
    public static PluginSettings from(FileConfiguration config) {
        return new PluginSettings(
                normalizeType(config.getString("ai.type", "custom")),
                config.getString("ai.base-url", "https://api.openai.com/v1/chat/completions"),
                config.getString("ai.api-key", ""),
                config.getString("ai.model", "gpt-4o-mini"),
                config.getDouble("ai.temperature", 0.7),
                config.getInt("ai.max-tokens", 512),
                config.getString("ai.timeout-seconds", "30").matches("\\d+") ? config.getInt("ai.timeout-seconds", 30) : 30,
                config.getBoolean("ai.stream-enabled", false),
                config.getString("prompt.system-file", "system-prompt.txt"),
                config.getString("prompt.template-file", "prompt-template.txt"),
                config.getString("ai.akuzoi.api-key", ""),
                config.getString("ai.akuzoi.role", "qing"),
                config.getBoolean("debug.enabled", false),
                config.getBoolean("debug.log-replies-to-console", true),
                config.getBoolean("debug.show-thinking-message", true),
                config.getBoolean("debug.think-tag-to-console", true),
                config.getBoolean("actionbar.enabled", true),
                config.getString("actionbar.thinking", "AI看了一眼"),
                config.getBoolean("time.enabled", true),
                config.getString("time.zone", "Asia/Shanghai"),
                config.getString("time.format", "yyyy-MM-dd HH:mm:ss z"),
                MemoryMode.from(config.getString("memory.mode", "player")),
                config.getInt("memory.max-messages", 20),
                config.getBoolean("memory.compression-enabled", true),
                config.getInt("memory.compress-after-messages", 40),
                config.getInt("memory.summary-max-tokens", 256),
                config.getStringList("trigger.names"),
                config.getBoolean("trigger.command-enabled", true),
                config.getBoolean("trigger.random-chat-enabled", true),
                clampProbability(config.getDouble("trigger.random-chat-probability", 0.05)),
                config.getLong("trigger.random-chat-cooldown-seconds", 60),
                config.getBoolean("proactive.enabled", true),
                config.getLong("proactive.after-interaction-seconds", 180),
                config.getLong("proactive.cooldown-seconds", 600),
                clampProbability(config.getDouble("proactive.probability", 0.35)),
                config.getBoolean("broadcast.visible-replies-global", config.getBoolean("proactive.broadcast-visible-reply", true)),
                config.getBoolean("proactive.show-thinking-message", false),
                config.getBoolean("system-message.enabled", true),
                clampProbability(config.getDouble("system-message.probability", 0.15)),
                config.getBoolean("system-message.show-thinking-message", false),
                config.getBoolean("location-lookup.enabled", false),
                config.getBoolean("behavior.gift-on-explicit-request-boost", true),
                clampProbability(config.getDouble("behavior.trigger-probability", 0.35)),
                config.getLong("behavior.window-seconds", 8),
                config.getLong("behavior.retention-seconds", 30),
                config.getBoolean("gift.enabled", true),
                config.getString("gift.trigger-token", "[[GIVE_ITEM]]"),
                config.getString("gift.success-message", "给你准备了一个小礼物：{gift_desc}。"),
                config.getString("gift.deny-failure-message", "物品在传输的过程中好像弄丢了。。。"),
                loadGiftOptions(config),
                config.getString("chat.prefix", "&b[AkuzoiAI]&r "),
                config.getString("chat.thinking", "&7AI 正在思考..."),
                config.getString("chat.error", "&cAI 请求失败，请稍后再试。")
        );
    }

    // 只有切到官方模式时，才会走那套固定入口。
    public boolean useAkuzoiOfficialService() {
        return "akuzoi".equals(aiType);
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return "custom";
        }
        String t = type.trim().toLowerCase();
        return "akuzoi".equals(t) ? "akuzoi" : "custom";
    }

    private static List<GiftOption> loadGiftOptions(FileConfiguration config) {
        List<GiftOption> options = new ArrayList<>();
        List<?> list = config.getList("gift.options");
        if (list != null) {
            for (Object item : list) {
                if (item instanceof ConfigurationSection section) {
                    options.add(loadGiftOption(section));
                } else if (item instanceof java.util.Map<?, ?> map) {
                    Object materialValue = map.containsKey("material") ? map.get("material") : "APPLE";
                    String material = String.valueOf(materialValue);
                    String displayName = map.containsKey("display-name") ? String.valueOf(map.get("display-name")) : null;
                    int min = toInt(map.get("min-amount"), 1);
                    int max = toInt(map.get("max-amount"), min);
                    double probability = clampProbability(toDouble(map.get("probability"), 1.0));
                    options.add(new GiftOption(material, displayName, min, max, probability));
                }
            }
        }
        if (options.isEmpty()) {
            options.add(new GiftOption("APPLE", null, 1, 2, 0.5));
            options.add(new GiftOption("BREAD", null, 1, 3, 0.3));
        }
        return options;
    }

    private static GiftOption loadGiftOption(ConfigurationSection section) {
        return new GiftOption(
                section.getString("material", "APPLE"),
                section.getString("display-name"),
                section.getInt("min-amount", 1),
                section.getInt("max-amount", 1),
                clampProbability(section.getDouble("probability", 1.0))
        );
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double toDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double clampProbability(double value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 1);
    }
}
