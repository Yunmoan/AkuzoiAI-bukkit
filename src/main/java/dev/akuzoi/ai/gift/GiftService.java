package dev.akuzoi.ai.gift;

import dev.akuzoi.ai.config.PluginSettings;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class GiftService {
    private final PluginSettings settings;
    private final Random random = new Random();

    public GiftService(PluginSettings settings) {
        this.settings = settings;
    }

    public GiftResult tryGift(Player player, String aiReply) {
        if (!settings.giftEnabled() || aiReply == null) {
            return GiftResult.none();
        }
        if (!containsGiftSignal(aiReply)) {
            return GiftResult.none();
        }
        for (GiftOption option : settings.giftOptions()) {
            if (random.nextDouble() > option.probability()) {
                continue;
            }
            Material material = resolveMaterial(option.material());
            if (material == null || !material.isItem()) {
                continue;
            }
            int amount = randomAmount(option);
            ItemStack stack = new ItemStack(material, amount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            String displayName = option.displayName();
            String description = amount + "x " + (displayName == null || displayName.isBlank() ? material.getKey().toString() : displayName);
            return GiftResult.given(description);
        }
        return GiftResult.failed(settings.giftFailureMessage());
    }

    public String instructionPrompt(String trigger, String message) {
        if (!settings.giftEnabled() || settings.giftOptions().isEmpty()) {
            return "";
        }
        boolean explicitRequest = isExplicitGiftRequest(message);
        StringBuilder builder = new StringBuilder();
        builder.append("\n\n可选交互能力：如需尝试给玩家奖励，请在回复末尾单独写一行 ")
                .append(settings.giftTriggerToken())
                .append("。不要在正文中说出具体物品名、数量或概率，奖励细节由插件处理。")
                .append("如果奖励未成功，也不要伪造已经发放。\n");
        if (explicitRequest && settings.giftOnExplicitRequestBoost()) {
            builder.append("玩家正在明确请求物品，请优先尝试附加礼物触发标记。\n");
        }
        return builder.toString();
    }

    private boolean isExplicitGiftRequest(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("给我点东西") || lower.contains("送我礼物") || lower.contains("奖励我") || lower.contains("给点东西") || lower.contains("给我一个") || lower.contains("give me") || lower.contains("gift me");
    }

    public String successMessage(String giftDescription) {
        return "给你准备了一个小礼物：" + giftDescription + "。";
    }

    public String stripGiftSignal(String reply) {
        if (reply == null || settings.giftTriggerToken().isBlank()) {
            return reply;
        }
        return reply.replace(settings.giftTriggerToken(), "").trim();
    }

    private Material resolveMaterial(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT).replace(':', '.');
        NamespacedKey key = NamespacedKey.fromString(normalized.replace('.', ':'));
        if (key != null) {
            Material material = Registry.MATERIAL.get(key);
            if (material != null) {
                return material;
            }
        }
        return Material.matchMaterial(normalized.replace("minecraft.", ""));
    }

    private boolean containsGiftSignal(String aiReply) {
        String token = settings.giftTriggerToken();
        return token != null && !token.isBlank() && aiReply.contains(token);
    }

    private int randomAmount(GiftOption option) {
        int min = Math.max(1, option.minAmount());
        int max = Math.max(min, option.maxAmount());
        return min + random.nextInt(max - min + 1);
    }
}
