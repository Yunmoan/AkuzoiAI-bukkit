package dev.akuzoi.ai.memory;

import dev.akuzoi.ai.config.MemoryMode;
import dev.akuzoi.ai.config.PluginSettings;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryManager {
    private static final String GLOBAL_KEY = "global";

    private final PluginSettings settings;
    private final MemoryStore store;
    private final Map<String, ConversationMemory> memories = new ConcurrentHashMap<>();

    public MemoryManager(PluginSettings settings, MemoryStore store) {
        this.settings = settings;
        this.store = store;
    }

    public void loadAll() throws IOException {
        if (store == null) {
            return;
        }
        Collection<String> keys = store.loadKeys();
        for (String key : keys) {
            store.load(key).ifPresent(memory -> {
                ConversationMemory conversationMemory = new ConversationMemory();
                conversationMemory.summary(memory.summary());
                conversationMemory.replaceMessages(memory.messages());
                memories.put(key, conversationMemory);
            });
        }
    }

    public ConversationMemory memoryFor(Player player) {
        if (settings.memoryMode() == MemoryMode.NONE) {
            return new ConversationMemory();
        }
        String key = settings.memoryMode() == MemoryMode.GLOBAL ? GLOBAL_KEY : player.getUniqueId().toString();
        return memories.computeIfAbsent(key, ignored -> new ConversationMemory());
    }

    public List<ChatMessage> contextFor(Player player) {
        ConversationMemory memory = memoryFor(player);
        List<ChatMessage> messages = memory.snapshot();
        int max = settings.maxMemoryMessages();
        if (max < 0 || messages.size() <= max) {
            return messages;
        }
        return messages.subList(Math.max(0, messages.size() - max), messages.size());
    }

    public void remember(Player player, ChatMessage userMessage, ChatMessage assistantMessage) {
        if (settings.memoryMode() == MemoryMode.NONE) {
            return;
        }
        ConversationMemory memory = memoryFor(player);
        memory.add(userMessage);
        memory.add(assistantMessage);
        trimIfNeeded(memory);
        persist(player, memory);
    }

    public void persist(Player player, ConversationMemory memory) {
        if (store == null || settings.memoryMode() == MemoryMode.NONE) {
            return;
        }
        try {
            store.save(memoryKey(player), new PersistedMemory(memory.summary(), memory.snapshot()));
        } catch (IOException exception) {
            throw new RuntimeException("Failed to persist memory", exception);
        }
    }

    public void clear(Player player) {
        String key = memoryKey(player);
        memories.remove(key);
        deleteSafely(key);
    }

    public void clearGlobal() {
        memories.remove(GLOBAL_KEY);
        deleteSafely(GLOBAL_KEY);
    }

    public void clearAll() {
        memories.clear();
        if (store == null) {
            return;
        }
        try {
            store.deleteAll();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to clear stored memories", exception);
        }
    }

    public void saveAll() {
        if (store == null || settings.memoryMode() == MemoryMode.NONE) {
            return;
        }
        for (Map.Entry<String, ConversationMemory> entry : memories.entrySet()) {
            try {
                store.save(entry.getKey(), new PersistedMemory(entry.getValue().summary(), entry.getValue().snapshot()));
            } catch (IOException exception) {
                throw new RuntimeException("Failed to save memory " + entry.getKey(), exception);
            }
        }
    }

    private String memoryKey(Player player) {
        return settings.memoryMode() == MemoryMode.GLOBAL ? GLOBAL_KEY : player.getUniqueId().toString();
    }

    private void deleteSafely(String key) {
        if (store == null) {
            return;
        }
        try {
            store.delete(key);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to delete memory " + key, exception);
        }
    }

    private void trimIfNeeded(ConversationMemory memory) {
        int max = settings.maxMemoryMessages();
        if (max >= 0 && memory.size() > max) {
            List<ChatMessage> snapshot = memory.snapshot();
            memory.replaceMessages(snapshot.subList(Math.max(0, snapshot.size() - max), snapshot.size()));
        }
    }
}
