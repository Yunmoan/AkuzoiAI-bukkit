package dev.akuzoi.ai.command;

import dev.akuzoi.ai.AkuzoiAIPlugin;
import dev.akuzoi.ai.config.MemoryMode;
import dev.akuzoi.ai.config.Messages;
import dev.akuzoi.ai.config.PluginSettings;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AdminCommand implements CommandExecutor {
    private final AkuzoiAIPlugin plugin;

    public AdminCommand(AkuzoiAIPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Messages.get(plugin.getConfig(), "command.admin.usage", "/akuzoiai <reload|clear|info>"));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadInternal();
            sender.sendMessage(Messages.get(plugin.getConfig(), "command.admin.reloaded", "AkuzoiAI reloaded."));
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            handleClear(sender, args);
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            handleInfo(sender);
            return true;
        }
        sender.sendMessage(Messages.get(plugin.getConfig(), "command.admin.usage", "/akuzoiai <reload|clear|info>"));
        return true;
    }

    private void handleClear(CommandSender sender, String[] args) {
        PluginSettings settings = plugin.settings();
        boolean admin = sender.hasPermission("akuzoiai.admin");

        if (args.length < 2) {
            if (sender instanceof Player player && settings.memoryMode() == MemoryMode.PLAYER) {
                plugin.memoryManager().clear(player);
                sender.sendMessage(Messages.get(plugin.getConfig(), "command.admin.clear.self", "Your AI memory has been cleared."));
                return;
            }
            if (!admin) {
                sender.sendMessage(Messages.get(plugin.getConfig(), "command.admin.clear.no-permission-shared", "You do not have permission to clear shared memory in the current memory mode."));
                return;
            }
            plugin.memoryManager().clearAll();
            sender.sendMessage(Messages.get(plugin.getConfig(), "command.admin.clear.all", "All AI memories cleared."));
            return;
        }

        if (args[1].equalsIgnoreCase("global")) {
            if (!admin) {
                sender.sendMessage(Messages.get(plugin.getConfig(), "command.admin.clear.no-permission-global", "You do not have permission to clear global memory."));
                return;
            }
            plugin.memoryManager().clearGlobal();
            sender.sendMessage(Messages.get(plugin.getConfig(), "command.admin.clear.global", "Global AI memory cleared."));
            return;
        }

        if (!admin) {
            if (sender instanceof Player player && args[1].equalsIgnoreCase(player.getName()) && settings.memoryMode() == MemoryMode.PLAYER) {
                plugin.memoryManager().clear(player);
                sender.sendMessage(Messages.get(plugin.getConfig(), "command.admin.clear.self", "Your AI memory has been cleared."));
                return;
            }
            sender.sendMessage(Messages.get(plugin.getConfig(), "command.admin.clear.no-permission-player", "You do not have permission to clear other players' memory."));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Messages.format(
                    Messages.get(plugin.getConfig(), "command.admin.clear.player-offline", "Player is not online: {player}"),
                    "player", args[1]
            ));
            return;
        }
        plugin.memoryManager().clear(target);
        sender.sendMessage(Messages.format(
                Messages.get(plugin.getConfig(), "command.admin.clear.player", "AI memory cleared for {player}."),
                "player", target.getName()
        ));
    }

    private void handleInfo(CommandSender sender) {
        PluginSettings settings = plugin.settings();
        sender.sendMessage(Messages.get(plugin.getConfig(), "command.admin.info.title", "AkuzoiAI info:"));
        sender.sendMessage(Messages.format(Messages.get(plugin.getConfig(), "command.admin.info.version", "- version: {version}"), "version", plugin.getDescription().getVersion()));
        sender.sendMessage(Messages.format(Messages.get(plugin.getConfig(), "command.admin.info.memory-mode", "- memory mode: {memoryMode}"), "memoryMode", settings.memoryMode().name().toLowerCase()));
        sender.sendMessage(Messages.format(Messages.get(plugin.getConfig(), "command.admin.info.model", "- model: {model}"), "model", settings.model()));
        sender.sendMessage(Messages.format(Messages.get(plugin.getConfig(), "command.admin.info.base-url", "- base url: {baseUrl}"), "baseUrl", settings.baseUrl()));
        sender.sendMessage(Messages.format(Messages.get(plugin.getConfig(), "command.admin.info.debug", "- debug: {debug}"), "debug", settings.debugEnabled()));
        sender.sendMessage(Messages.format(Messages.get(plugin.getConfig(), "command.admin.info.log-replies", "- log replies to console: {value}"), "value", settings.logRepliesToConsole()));
        sender.sendMessage(Messages.format(Messages.get(plugin.getConfig(), "command.admin.info.thinking-visible", "- thinking visible: {value}"), "value", settings.showThinkingMessage()));
        sender.sendMessage(Messages.format(Messages.get(plugin.getConfig(), "command.admin.info.time-enabled", "- time enabled: {value}"), "value", settings.timeEnabled()));
        sender.sendMessage(Messages.format(Messages.get(plugin.getConfig(), "command.admin.info.time-zone", "- time zone: {timeZone}"), "timeZone", settings.timeZone()));
        sender.sendMessage(Messages.format(Messages.get(plugin.getConfig(), "command.admin.info.proactive-enabled", "- proactive enabled: {value}"), "value", settings.proactiveEnabled()));
        sender.sendMessage(Messages.format(Messages.get(plugin.getConfig(), "command.admin.info.gift-enabled", "- gift enabled: {value}"), "value", settings.giftEnabled()));
    }
}
