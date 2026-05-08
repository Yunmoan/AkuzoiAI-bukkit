package dev.akuzoi.ai.listener;

import dev.akuzoi.ai.behavior.BehaviorTracker;
import dev.akuzoi.ai.config.PluginSettings;
import dev.akuzoi.ai.service.AiChatService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatListener implements Listener {
    private final JavaPlugin plugin;
    private final AiChatService chatService;
    private final PluginSettings settings;
    private final BehaviorTracker behaviorTracker;

    public ChatListener(JavaPlugin plugin, AiChatService chatService, PluginSettings settings, BehaviorTracker behaviorTracker) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.settings = settings;
        this.behaviorTracker = behaviorTracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        behaviorTracker.recordChat(player, message);
        if (chatService.shouldNameTrigger(message)) {
            requestAfterChatBroadcast(player, "name", message);
            return;
        }
        if (settings.randomChatEnabled() && chatService.shouldRandomTrigger(player)) {
            requestAfterChatBroadcast(player, "random", message);
        }
    }

    private void requestAfterChatBroadcast(Player player, String trigger, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> chatService.requestReply(player, trigger, message, true));
    }
}
