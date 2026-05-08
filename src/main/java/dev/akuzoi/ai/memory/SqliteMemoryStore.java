package dev.akuzoi.ai.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class SqliteMemoryStore implements MemoryStore {
    private final Connection connection;

    public SqliteMemoryStore(Path databaseFile) throws IOException {
        try {
            Files.createDirectories(databaseFile.getParent());
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            initialize();
        } catch (SQLException exception) {
            throw new IOException("Failed to open SQLite database", exception);
        }
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS memories (\n" +
                    "  memory_key TEXT PRIMARY KEY,\n" +
                    "  summary TEXT NOT NULL DEFAULT '',\n" +
                    "  updated_at INTEGER NOT NULL\n" +
                    ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS memory_messages (\n" +
                    "  memory_key TEXT NOT NULL,\n" +
                    "  position INTEGER NOT NULL,\n" +
                    "  role TEXT NOT NULL,\n" +
                    "  content TEXT NOT NULL,\n" +
                    "  PRIMARY KEY (memory_key, position)\n" +
                    ")");
        }
    }

    @Override
    public Optional<PersistedMemory> load(String key) throws IOException {
        try {
            String summary = "";
            try (PreparedStatement statement = connection.prepareStatement("SELECT summary FROM memories WHERE memory_key = ?")) {
                statement.setString(1, key);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        summary = rs.getString(1);
                    } else {
                        return Optional.empty();
                    }
                }
            }

            List<ChatMessage> messages = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT role, content FROM memory_messages WHERE memory_key = ? ORDER BY position ASC")) {
                statement.setString(1, key);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        messages.add(new ChatMessage(rs.getString(1), rs.getString(2)));
                    }
                }
            }
            return Optional.of(new PersistedMemory(summary, messages));
        } catch (SQLException exception) {
            throw new IOException("Failed to load memory " + key, exception);
        }
    }

    @Override
    public void save(String key, PersistedMemory memory) throws IOException {
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT INTO memories(memory_key, summary, updated_at) VALUES(?, ?, ?) " +
                            "ON CONFLICT(memory_key) DO UPDATE SET summary = excluded.summary, updated_at = excluded.updated_at")) {
                upsert.setString(1, key);
                upsert.setString(2, memory.summary() == null ? "" : memory.summary());
                upsert.setLong(3, System.currentTimeMillis());
                upsert.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM memory_messages WHERE memory_key = ?")) {
                delete.setString(1, key);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO memory_messages(memory_key, position, role, content) VALUES(?, ?, ?, ?)")) {
                int index = 0;
                for (ChatMessage message : memory.messages()) {
                    insert.setString(1, key);
                    insert.setInt(2, index++);
                    insert.setString(3, message.role());
                    insert.setString(4, message.content());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            throw new IOException("Failed to save memory " + key, exception);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            try (PreparedStatement deleteMessages = connection.prepareStatement("DELETE FROM memory_messages WHERE memory_key = ?");
                 PreparedStatement deleteMemory = connection.prepareStatement("DELETE FROM memories WHERE memory_key = ?")) {
                deleteMessages.setString(1, key);
                deleteMessages.executeUpdate();
                deleteMemory.setString(1, key);
                deleteMemory.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IOException("Failed to delete memory " + key, exception);
        }
    }

    @Override
    public void deleteAll() throws IOException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM memory_messages");
            statement.executeUpdate("DELETE FROM memories");
        } catch (SQLException exception) {
            throw new IOException("Failed to delete all memories", exception);
        }
    }

    @Override
    public Collection<String> loadKeys() throws IOException {
        List<String> keys = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT memory_key FROM memories")) {
            while (rs.next()) {
                keys.add(rs.getString(1));
            }
        } catch (SQLException exception) {
            throw new IOException("Failed to load memory keys", exception);
        }
        return keys;
    }

    @Override
    public void close() throws IOException {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IOException("Failed to close SQLite database", exception);
        }
    }
}
