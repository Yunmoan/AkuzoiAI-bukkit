package dev.akuzoi.ai.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import dev.akuzoi.ai.behavior.BehaviorTracker;
import dev.akuzoi.ai.config.PluginSettings;
import dev.akuzoi.ai.service.AiChatService;

public final class PlayerInteractionListener implements Listener {
    private final JavaPlugin plugin;
    private final AiChatService chatService;
    private final PluginSettings settings;
    private final BehaviorTracker behaviorTracker;
    private final Map<UUID, Long> scheduledAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMoveRecord = new ConcurrentHashMap<>();

    public PlayerInteractionListener(JavaPlugin plugin, AiChatService chatService, PluginSettings settings, BehaviorTracker behaviorTracker) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.settings = settings;
        this.behaviorTracker = behaviorTracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!settings.proactiveEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        behaviorTracker.recordInteract(player, event.getAction(), event.getClickedBlock(), event.getItem());
        scheduleProactive(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        behaviorTracker.recordEntityInteraction(player, event.getRightClicked());
        scheduleProactive(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        behaviorTracker.recordItemHeld(player, player.getInventory().getItem(event.getNewSlot()));
        scheduleProactive(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long last = lastMoveRecord.getOrDefault(player.getUniqueId(), 0L);
        if (now - last >= 3000L) {
            lastMoveRecord.put(player.getUniqueId(), now);
            behaviorTracker.recordMove(player);
        }
    }

    private void scheduleProactive(Player player) {
        if (!settings.proactiveEnabled()) {
            return;
        }
        long scheduleTime = System.currentTimeMillis();
        scheduledAt.put(player.getUniqueId(), scheduleTime);
        long delayTicks = Math.max(1L, settings.proactiveAfterInteractionSeconds() * 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            long latest = scheduledAt.getOrDefault(player.getUniqueId(), -1L);
            if (latest != scheduleTime || !player.isOnline()) {
                return;
            }
            if (chatService.shouldBehaviorTrigger(player)) {
                chatService.requestProactiveReply(player);
            }
        }, delayTicks);
    }
}
