package dev.akuzoi.ai.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConversationMemory {
    private final List<ChatMessage> messages = new ArrayList<>();
    private String summary = "";

    public synchronized void add(ChatMessage message) {
        messages.add(message);
    }

    public synchronized List<ChatMessage> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public synchronized void replaceMessages(List<ChatMessage> replacement) {
        messages.clear();
        messages.addAll(replacement);
    }

    public synchronized int size() {
        return messages.size();
    }

    public synchronized String summary() {
        return summary;
    }

    public synchronized void summary(String summary) {
        this.summary = summary == null ? "" : summary;
    }

    public synchronized void clear() {
        messages.clear();
        summary = "";
    }
}
