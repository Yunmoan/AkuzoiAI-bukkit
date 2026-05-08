package dev.akuzoi.ai.memory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

public interface MemoryStore extends Closeable {
    Optional<PersistedMemory> load(String key) throws IOException;

    void save(String key, PersistedMemory memory) throws IOException;

    void delete(String key) throws IOException;

    void deleteAll() throws IOException;

    Collection<String> loadKeys() throws IOException;

    @Override
    void close() throws IOException;
}
