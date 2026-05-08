package dev.akuzoi.ai.memory;

import java.util.List;

public record PersistedMemory(String summary, List<ChatMessage> messages) {
}
