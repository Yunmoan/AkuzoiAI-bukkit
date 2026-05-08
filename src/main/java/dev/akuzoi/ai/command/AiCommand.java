package dev.akuzoi.ai.command;

import dev.akuzoi.ai.config.Messages;
import dev.akuzoi.ai.service.AiChatService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public final class AiCommand implements CommandExecutor {
    private final AiChatService chatService;

    public AiCommand(AiChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get(chatService.plugin().getConfig(), "command.player-only", "Only players can use this command."));
            return true;
        }
        if (args.length == 0) {
            return true;
        }
        chatService.requestReply(player, "command", String.join(" ", Arrays.asList(args)));
        return true;
    }
}
