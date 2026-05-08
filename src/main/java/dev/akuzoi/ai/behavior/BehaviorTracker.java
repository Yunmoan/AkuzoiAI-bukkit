package dev.akuzoi.ai.behavior;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BehaviorTracker {
    private final Map<UUID, Deque<BehaviorRecord>> records = new ConcurrentHashMap<>();
    private final long retentionMillis;

    public BehaviorTracker(long retentionSeconds) {
        this.retentionMillis = Math.max(1L, retentionSeconds) * 1000L;
    }

    public void recordChat(Player player, String message) {
        record(player, "聊天：" + message);
    }

    public void recordInteract(Player player, Action action, Block block, ItemStack item) {
        List<String> parts = new ArrayList<>();
        parts.add("交互动作=" + action.name().toLowerCase(Locale.ROOT));
        if (block != null) {
            parts.add("方块=" + block.getType().name().toLowerCase(Locale.ROOT));
        }
        if (item != null && item.getType() != Material.AIR) {
            parts.add("手持=" + item.getType().name().toLowerCase(Locale.ROOT));
        }
        record(player, String.join("，", parts));
    }

    public void recordMove(Player player) {
        record(player, "移动到 " + formatLocation(player));
    }

    public void recordItemHeld(Player player, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            record(player, "切换手持物品为 " + item.getType().name().toLowerCase(Locale.ROOT));
        }
    }

    public void recordEntityInteraction(Player player, Entity entity) {
        record(player, "与实体交互：" + entity.getType().name().toLowerCase(Locale.ROOT));
    }

    public String summarize(Player player, long windowSeconds) {
        Deque<BehaviorRecord> deque = records.get(player.getUniqueId());
        if (deque == null || deque.isEmpty()) {
            return summarizeLocation(player);
        }
        long now = System.currentTimeMillis();
        long since = now - Math.max(1L, windowSeconds) * 1000L;
        List<String> recent = new ArrayList<>();
        synchronized (deque) {
            prune(deque, now);
            for (BehaviorRecord record : deque) {
                if (record.timestamp() >= since) {
                    recent.add(record.description());
                }
            }
        }
        if (recent.isEmpty()) {
            return summarizeLocation(player);
        }
        int from = Math.max(0, recent.size() - 12);
        return String.join("；", recent.subList(from, recent.size())) + summarizeLocationSuffix(player);
    }

    public String locationSummary(Player player) {
        return summarizeLocation(player);
    }

    private void record(Player player, String description) {
        Deque<BehaviorRecord> deque = records.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (deque) {
            deque.addLast(new BehaviorRecord(now, description));
            prune(deque, now);
        }
    }

    private void prune(Deque<BehaviorRecord> deque, long now) {
        while (!deque.isEmpty() && now - deque.peekFirst().timestamp() > retentionMillis) {
            deque.removeFirst();
        }
    }

    private String formatLocation(Player player) {
        return player.getWorld().getName() + " x=" + player.getLocation().getBlockX()
                + " y=" + player.getLocation().getBlockY()
                + " z=" + player.getLocation().getBlockZ();
    }

    private String summarizeLocationSuffix(Player player) {
        return "。当前位置：" + formatLocation(player);
    }

    private String summarizeLocation(Player player) {
        return "当前位置：" + formatLocation(player);
    }
}
