package dev.akuzoi.ai.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.akuzoi.ai.ai.OpenAiLikeClient;
import dev.akuzoi.ai.behavior.BehaviorTracker;
import dev.akuzoi.ai.config.AkuzoiRoleRegistry;
import dev.akuzoi.ai.config.Messages;
import dev.akuzoi.ai.config.PluginSettings;
import dev.akuzoi.ai.gift.GiftResult;
import dev.akuzoi.ai.gift.GiftService;
import dev.akuzoi.ai.memory.ChatMessage;
import dev.akuzoi.ai.memory.ConversationMemory;
import dev.akuzoi.ai.memory.MemoryManager;
import dev.akuzoi.ai.prompt.PromptManager;

/**
 * 聊天服务入口，负责拼提示词、发请求，再把结果送回去。
 */
public final class AiChatService {
    /** 把 think 标签里的内容单独拿出来。 */
    private static final Pattern THINK_PATTERN = Pattern.compile("<think>([\\s\\S]*?)</think>", Pattern.CASE_INSENSITIVE);
    /** 识别位置查询标记，比如 [[LOCATE:PlayerName]]。 */
    private static final Pattern LOCATE_PATTERN = Pattern.compile("\\[\\[LOCATE:([^\\]]+)\\]\\]", Pattern.CASE_INSENSITIVE);
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final PromptManager promptManager;
    private final MemoryManager memoryManager;
    private final OpenAiLikeClient aiClient;
    private final BehaviorTracker behaviorTracker;
    private final GiftService giftService;
    private final AkuzoiRoleRegistry roleRegistry;
    private final Map<UUID, Long> randomCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> proactiveCooldowns = new ConcurrentHashMap<>();

    public AiChatService(JavaPlugin plugin, PluginSettings settings, PromptManager promptManager, MemoryManager memoryManager, OpenAiLikeClient aiClient, BehaviorTracker behaviorTracker, GiftService giftService, AkuzoiRoleRegistry roleRegistry) {
        this.plugin = plugin;
        this.settings = settings;
        this.promptManager = promptManager;
        this.memoryManager = memoryManager;
        this.aiClient = aiClient;
        this.behaviorTracker = behaviorTracker;
        this.giftService = giftService;
        this.roleRegistry = roleRegistry;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public void requestReply(Player player, String trigger, String message) {
        requestReply(player, trigger, message, true);
    }

    /**
     * 发起一次回复请求。
     *
     * @param player 触发玩家，系统触发时可能为 null
     * @param trigger 触发来源（chat/random/proactive/system 等）
     * @param message 原始输入文本
     * @param showThinking 是否先展示“思考中”提示
     */
    public void requestReply(Player player, String trigger, String message, boolean showThinking) {
        String playerName = player == null ? "system" : player.getName();
        String userPrompt = promptManager.renderUserPrompt(trigger, playerName, message) + giftService.instructionPrompt(trigger, message);
        debug("Request trigger=" + trigger + ", player=" + playerName + ", message=" + message);

        boolean actionTrigger = "proactive".equalsIgnoreCase(trigger) || "behavior".equalsIgnoreCase(trigger) || "system".equalsIgnoreCase(trigger);
        if (showThinking && settings.showThinkingMessage()) {
            if ((actionTrigger && settings.actionbarEnabled()) || "system".equalsIgnoreCase(trigger)) {
                sendActionbar(player, settings.actionbarThinkingText());
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(color(settings.chatPrefix() + settings.thinkingMessage()));
                    }
                });
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                compressIfNeeded(player);
                List<ChatMessage> requestMessages = buildMessages(player, userPrompt);
                String rawReply;
                if (settings.streamEnabled()) {
                    StringBuilder streamed = new StringBuilder();
                    aiClient.chatStream(requestMessages, settings.maxTokens(), streamed::append);
                    rawReply = streamed.toString();
                } else {
                    rawReply = aiClient.chat(requestMessages, settings.maxTokens());
                }
                String withLocation = applyLocationTool(rawReply);
                ThinkExtraction thinkExtraction = extractThink(withLocation);
                if (!thinkExtraction.think().isBlank() && settings.thinkTagToConsole()) {
                    plugin.getLogger().info("[AkuzoiAI/THINK] player=" + playerName + "\n" + thinkExtraction.think());
                }
                GiftResult giftResult = player == null ? GiftResult.none() : runGiftOnMainThread(player, thinkExtraction.visibleReply());
                String reply = buildVisibleReply(thinkExtraction.visibleReply(), giftResult);
                if (giftResult.failureMessage() != null && !giftResult.failureMessage().isBlank()) {
                    reply = reply.isBlank() ? giftResult.failureMessage() : reply + "\n" + giftResult.failureMessage();
                }
                if (settings.logRepliesToConsole()) {
                    plugin.getLogger().info("[AkuzoiAI/REPLY] player=" + playerName + ", trigger=" + trigger + "\n" + reply);
                }
                if (settings.globalBroadcastVisibleReplies()) {
                    plugin.getLogger().info("[AkuzoiAI/BROADCAST] reply will be broadcast to all players.");
                }
                if (player != null) {
                    memoryManager.remember(player, ChatMessage.user(userPrompt), ChatMessage.assistant(reply));
                }
                final String finalReply = reply;
                Bukkit.getScheduler().runTask(plugin, () -> sendReply(player, finalReply));
            } catch (Exception exception) {
                plugin.getLogger().warning("AI request failed: " + exception.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(color(settings.chatPrefix() + settings.errorMessage())));
            }
        });
    }

    public void requestProactiveReply(Player player) {
        String locale = Messages.locale(plugin.getConfig());
        String behavior = behaviorTracker.summarize(player, settings.behaviorWindowSeconds());
        String prompt = Messages.format(Messages.get(plugin.getConfig(), locale, "prompt.proactive", "Please start a natural conversation with the player based on recent behavior: {behavior}"), "behavior", behavior);
        requestReply(player, "proactive", prompt, settings.proactiveShowThinkingMessage());
    }

    public boolean shouldNameTrigger(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (settings.useAkuzoiOfficialService()) {
            AkuzoiRoleRegistry.RoleInfo roleInfo = roleRegistry.resolve(settings.akuzoiRole());
            for (String name : roleInfo.names()) {
                if (name != null && !name.isBlank() && lower.contains(name.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }

        for (String name : settings.triggerNames()) {
            if (!name.isBlank() && lower.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldRandomTrigger(Player player) {
        if (!settings.randomChatEnabled() || Math.random() > settings.randomChatProbability()) {
            return false;
        }
        return checkCooldown(randomCooldowns, player.getUniqueId(), settings.randomChatCooldownSeconds());
    }

    public boolean shouldProactiveTrigger(Player player) {
        if (!settings.proactiveEnabled() || Math.random() > settings.proactiveProbability()) {
            return false;
        }
        return checkCooldown(proactiveCooldowns, player.getUniqueId(), settings.proactiveCooldownSeconds());
    }

    public boolean shouldBehaviorTrigger(Player player) {
        if (!settings.proactiveEnabled() || Math.random() > settings.behaviorTriggerProbability()) {
            return false;
        }
        return checkCooldown(proactiveCooldowns, player.getUniqueId(), settings.proactiveCooldownSeconds());
    }

    private void sendActionbar(Player player, String text) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendActionBar(color(text));
            }
        });
    }

    private GiftResult runGiftOnMainThread(Player player, String rawReply) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
            if (!player.isOnline()) {
                return GiftResult.none();
            }
            return giftService.tryGift(player, rawReply);
        }).get();
    }

    private String buildVisibleReply(String rawReply, GiftResult giftResult) {
        String reply = giftService.stripGiftSignal(rawReply);
        if (giftResult.given()) {
            reply = reply + "\n" + giftService.successMessage(giftResult.description());
        }
        return reply.trim();
    }

    private ThinkExtraction extractThink(String rawReply) {
        if (rawReply == null || rawReply.isBlank()) {
            return new ThinkExtraction("", "");
        }
        Matcher matcher = THINK_PATTERN.matcher(rawReply);
        StringBuilder think = new StringBuilder();
        while (matcher.find()) {
            if (!think.isEmpty()) {
                think.append("\n---\n");
            }
            think.append(matcher.group(1).trim());
        }
        String visible = matcher.replaceAll("").trim();
        return new ThinkExtraction(think.toString().trim(), visible);
    }

    private boolean checkCooldown(Map<UUID, Long> cooldowns, UUID uuid, long cooldownSeconds) {
        long now = System.currentTimeMillis();
        long availableAt = cooldowns.getOrDefault(uuid, 0L);
        if (availableAt > now) {
            return false;
        }
        cooldowns.put(uuid, now + cooldownSeconds * 1000L);
        return true;
    }

    private String applyLocationTool(String reply) {
        if (reply == null || reply.isBlank()) {
            return "";
        }
        Matcher matcher = LOCATE_PATTERN.matcher(reply);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String target = matcher.group(1) == null ? "" : matcher.group(1).trim();
            String replacement;
            if (!settings.locationLookupEnabled()) {
                replacement = "[位置查询未启用]";
            } else if (target.isBlank()) {
                replacement = "[未指定要查询的玩家]";
            } else {
                Player targetPlayer = Bukkit.getPlayerExact(target);
                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    replacement = "[玩家 " + target + " 不在线]";
                } else {
                    replacement = "[玩家 " + targetPlayer.getName() + " 坐标: "
                            + targetPlayer.getWorld().getName()
                            + " x=" + targetPlayer.getLocation().getBlockX()
                            + " y=" + targetPlayer.getLocation().getBlockY()
                            + " z=" + targetPlayer.getLocation().getBlockZ()
                            + "]";
                }
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private List<ChatMessage> buildMessages(Player player, String userPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(promptManager.systemPrompt()));
        ConversationMemory memory = memoryManager.memoryFor(player);
        if (!memory.summary().isBlank()) {
            messages.add(ChatMessage.system("以下是较早对话的摘要：\n" + memory.summary()));
        }
        messages.addAll(memoryManager.contextFor(player));
        messages.add(ChatMessage.user(userPrompt));
        return messages;
    }

    private void compressIfNeeded(Player player) throws IOException, InterruptedException {
        if (!settings.compressionEnabled() || settings.maxMemoryMessages() >= 0) {
            return;
        }
        ConversationMemory memory = memoryManager.memoryFor(player);
        if (memory.size() < settings.compressAfterMessages()) {
            return;
        }
        List<ChatMessage> snapshot = memory.snapshot();
        int keep = Math.max(4, settings.compressAfterMessages() / 4);
        int compressUntil = Math.max(0, snapshot.size() - keep);
        if (compressUntil == 0) {
            return;
        }

        StringBuilder content = new StringBuilder();
        if (!memory.summary().isBlank()) {
            content.append("已有摘要：\n").append(memory.summary()).append("\n\n");
        }
        content.append("需要压缩的历史消息：\n");
        for (int i = 0; i < compressUntil; i++) {
            ChatMessage message = snapshot.get(i);
            content.append(message.role()).append(": ").append(message.content()).append('\n');
        }

        List<ChatMessage> summaryMessages = List.of(
                ChatMessage.system("你负责把 Minecraft 服务器 AI 聊天历史压缩成简洁摘要，保留玩家偏好、重要事实、未解决事项和语气。"),
                ChatMessage.user(content.toString())
        );
        String summary = aiClient.chat(summaryMessages, settings.summaryMaxTokens());
        memory.summary(summary);
        memory.replaceMessages(snapshot.subList(compressUntil, snapshot.size()));
    }

    private void sendReply(Player player, String reply) {
        if (reply == null || reply.isBlank()) {
            return;
        }
        String speaker = resolveDisplayName();
        boolean broadcast = settings.globalBroadcastVisibleReplies();
        for (String line : reply.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                String msg = color("<" + speaker + "> " + trimmed);
                if (broadcast) {
                    Bukkit.broadcastMessage(msg);
                } else if (player != null && player.isOnline()) {
                    player.sendMessage(msg);
                }
            }
        }
    }

    private String resolveDisplayName() {
        if (settings.useAkuzoiOfficialService()) {
            return roleRegistry.resolve(settings.akuzoiRole()).primaryName();
        }
        for (String name : settings.triggerNames()) {
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "AkuzoiAI";
    }

    private void debug(String message) {
        if (settings.debugEnabled()) {
            plugin.getLogger().info("[AkuzoiAI/DEBUG] " + message);
        }
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private record ThinkExtraction(String think, String visibleReply) {
    }
}
