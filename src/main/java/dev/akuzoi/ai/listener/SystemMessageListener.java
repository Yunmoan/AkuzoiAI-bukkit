package dev.akuzoi.ai.listener;

import dev.akuzoi.ai.config.PluginSettings;
import dev.akuzoi.ai.service.AiChatService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class SystemMessageListener implements Listener {
    private final JavaPlugin plugin;
    private final AiChatService chatService;
    private final PluginSettings settings;

    public SystemMessageListener(JavaPlugin plugin, AiChatService chatService, PluginSettings settings) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!settings.systemMessageEnabled()) {
            return;
        }
        String message = event.getPlayer().getName() + " 获得了成就。";
        maybeReply(message);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!settings.systemMessageEnabled()) {
            return;
        }
        String message = event.getDeathMessage();
        if (message == null || message.isBlank()) {
            message = event.getEntity().getName() + " 死了。";
        }
        maybeReply(message);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (!settings.systemMessageEnabled()) {
            return;
        }
        maybeReply(event.getPlayer().getName() + " 加入了游戏");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        if (!settings.systemMessageEnabled()) {
            return;
        }
        maybeReply(event.getPlayer().getName() + " 离开了游戏");
    }

    private void maybeReply(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        boolean match = lower.contains("获得了成就") || lower.contains("死了") || lower.contains("加入了游戏") || lower.contains("离开了游戏");
        if (!match || Math.random() > settings.systemMessageProbability()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> chatService.requestReply(null, "system", message, settings.systemMessageShowThinkingMessage()));
    }
}
