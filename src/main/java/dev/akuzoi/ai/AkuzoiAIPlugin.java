package dev.akuzoi.ai;

import dev.akuzoi.ai.ai.OpenAiLikeClient;
import dev.akuzoi.ai.behavior.BehaviorTracker;
import dev.akuzoi.ai.command.AdminCommand;
import dev.akuzoi.ai.command.AiCommand;
import dev.akuzoi.ai.config.AkuzoiRoleRegistry;
import dev.akuzoi.ai.config.ConfigUpdater;
import dev.akuzoi.ai.config.PluginSettings;
import dev.akuzoi.ai.gift.GiftService;
import dev.akuzoi.ai.listener.ChatListener;
import dev.akuzoi.ai.listener.PlayerInteractionListener;
import dev.akuzoi.ai.listener.SystemMessageListener;
import dev.akuzoi.ai.memory.MemoryManager;
import dev.akuzoi.ai.memory.MemoryStore;
import dev.akuzoi.ai.memory.SqliteMemoryStore;
import dev.akuzoi.ai.prompt.PromptManager;
import dev.akuzoi.ai.service.AiChatService;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public final class AkuzoiAIPlugin extends JavaPlugin {
    private PluginSettings settings;
    private PromptManager promptManager;
    private MemoryManager memoryManager;
    private OpenAiLikeClient aiClient;
    private BehaviorTracker behaviorTracker;
    private GiftService giftService;
    private AiChatService chatService;
    private MemoryStore memoryStore;
    private AkuzoiRoleRegistry roleRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("system-prompt.txt");
        saveResourceIfMissing("prompt-template.txt");
        initializeMemoryStore();
        reloadInternal();

        PluginCommand aiCommand = getCommand("ai");
        if (aiCommand != null) {
            aiCommand.setExecutor(new AiCommand(chatService));
        }

        PluginCommand adminCommand = getCommand("akuzoiai");
        if (adminCommand != null) {
            adminCommand.setExecutor(new AdminCommand(this));
        }

        getLogger().info("AkuzoiAI enabled.");
    }

    @Override
    public void onDisable() {
        if (memoryManager != null) {
            memoryManager.saveAll();
        }
        closeMemoryStore();
        HandlerList.unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("AkuzoiAI disabled.");
    }

    public void reloadInternal() {
        reloadConfig();
        ConfigUpdater.update(this);
        reloadConfig();
        HandlerList.unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        this.settings = PluginSettings.from(getConfig());
        this.roleRegistry = AkuzoiRoleRegistry.load(this);
        this.promptManager = new PromptManager(this, settings);
        this.memoryManager = new MemoryManager(settings, memoryStore);
        try {
            this.memoryManager.loadAll();
        } catch (IOException exception) {
            getLogger().warning("Failed to load persisted memories: " + exception.getMessage());
        }
        this.aiClient = new OpenAiLikeClient(settings, roleRegistry);
        this.behaviorTracker = new BehaviorTracker(settings.behaviorRetentionSeconds());
        this.giftService = new GiftService(settings);
        this.chatService = new AiChatService(this, settings, promptManager, memoryManager, aiClient, behaviorTracker, giftService, roleRegistry);
        if (settings.useAkuzoiOfficialService()) {
            var roleInfo = roleRegistry.resolve(settings.akuzoiRole());
            getLogger().info(String.format("Akuzoi mode enabled. role=%s endpoint=%s/%s triggerNames=%s displayName=%s",
                    settings.akuzoiRole(),
                    "https://api.zyghit.cn/akuzoiai/endpoint",
                    roleInfo.endpointId(),
                    roleInfo.names(),
                    roleInfo.primaryName()));
        } else {
            getLogger().info(String.format("Custom mode enabled. displayName=%s",
                    settings.triggerNames().stream().filter(s -> s != null && !s.isBlank()).findFirst().orElse("AkuzoiAI")));
        }
        registerListeners();
    }

    public MemoryManager memoryManager() {
        return memoryManager;
    }

    public PluginSettings settings() {
        return settings;
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatListener(this, chatService, settings, behaviorTracker), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractionListener(this, chatService, settings, behaviorTracker), this);
        getServer().getPluginManager().registerEvents(new SystemMessageListener(this, chatService, settings), this);
    }

    private void initializeMemoryStore() {
        try {
            memoryStore = new SqliteMemoryStore(getDataFolder().toPath().resolve("memory.sqlite"));
        } catch (IOException exception) {
            getLogger().warning("SQLite memory store unavailable, falling back to in-memory only: " + exception.getMessage());
            memoryStore = null;
        }
    }

    private void closeMemoryStore() {
        if (memoryStore == null) {
            return;
        }
        try {
            memoryStore.close();
        } catch (IOException exception) {
            getLogger().warning("Failed to close memory store: " + exception.getMessage());
        }
    }

    private void saveResourceIfMissing(String name) {
        if (!getDataFolder().toPath().resolve(name).toFile().exists()) {
            saveResource(name, false);
        }
    }
}
