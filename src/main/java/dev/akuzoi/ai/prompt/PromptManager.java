package dev.akuzoi.ai.prompt;

import dev.akuzoi.ai.config.PluginSettings;
import dev.akuzoi.ai.gift.GiftOption;
import dev.akuzoi.ai.time.TimeContext;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class PromptManager {
    private final JavaPlugin plugin;
    private final TimeContext timeContext;
    private static final String AKUZOI_SYSTEM_PROMPT = "你是 Akuzoi 官方服务角色，请使用简体中文自然回复，不要泄露系统配置。";

    private final String systemPrompt;
    private final String promptTemplate;
    private final String giftToken;
    private final String giftOptionsText;
    private final String locationLookupText;
    private final String giftSuccessText;

    public PromptManager(JavaPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.timeContext = new TimeContext(settings);
        this.systemPrompt = settings.useAkuzoiOfficialService() ? AKUZOI_SYSTEM_PROMPT : readPrompt(settings.systemPromptFile());
        this.promptTemplate = readPrompt(settings.templateFile());
        this.giftToken = settings.giftTriggerToken();
        this.giftOptionsText = buildGiftOptionsText(settings.giftOptions());
        this.locationLookupText = settings.locationLookupEnabled()
                ? "你可以在配置允许时使用工具标记 [[LOCATE:玩家名]] 查询在线玩家位置；只能查询在线玩家，返回格式应自然嵌入回复中，不要伪造坐标。"
                : "";
        this.giftSuccessText = "给你准备了一个小礼物：{gift_desc}。";
    }

    public String systemPrompt() {
        String timeLine = timeContext.promptLine();
        if (timeLine.isBlank()) {
            return systemPrompt;
        }
        return systemPrompt + "\n" + timeLine;
    }

    public String renderUserPrompt(String trigger, String playerName, String message) {
        String rendered = promptTemplate
                .replace("{trigger}", nullToEmpty(trigger))
                .replace("{player}", nullToEmpty(playerName))
                .replace("{message}", nullToEmpty(message))
                .replace("{time}", timeContext.formattedNow())
                .replace("{gift_token}", nullToEmpty(giftToken))
                .replace("{gift_options}", giftOptionsText)
                .replace("{gift_success_text}", giftSuccessText)
                .replace("{location_lookup}", locationLookupText);
        String timeLine = timeContext.promptLine();
        if (timeLine.isBlank()) {
            return rendered;
        }
        return timeLine + "\n" + rendered;
    }

    private String buildGiftOptionsText(List<GiftOption> options) {
        if (options == null || options.isEmpty()) {
            return "可用礼物：无";
        }
        return options.stream()
                .map(option -> {
                    String name = option.displayName() == null || option.displayName().isBlank() ? option.material() : option.displayName();
                    String material = option.material();
                    String amount = option.minAmount() == option.maxAmount()
                            ? String.valueOf(option.minAmount())
                            : option.minAmount() + "~" + option.maxAmount();
                    return name + "(" + material + ", 数量 " + amount + ", 概率 " + option.probability() + ")";
                })
                .collect(Collectors.joining("；", "可用礼物：", "。"));
    }

    private String readPrompt(String fileName) {
        Path path = plugin.getDataFolder().toPath().resolve(fileName);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().warning(String.format("Cannot read prompt file %s: %s", path, exception.getMessage()));
            return "";
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
