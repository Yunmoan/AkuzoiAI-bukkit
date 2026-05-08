package dev.akuzoi.ai.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AkuzoiRoleRegistry {
    private static final Pattern ENTRY_PATTERN = Pattern.compile("\"([a-zA-Z0-9_-]+)\"\\s*:\\s*\\{([\\s\\S]*?)\\}");
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME_STRING_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME_ARRAY_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\\[([\\s\\S]*?)\\]");

    private final Map<String, RoleInfo> roles;

    private AkuzoiRoleRegistry(Map<String, RoleInfo> roles) {
        this.roles = roles;
    }

    public static AkuzoiRoleRegistry load(JavaPlugin plugin) {
        Map<String, RoleInfo> result = new HashMap<>();
        try (var in = plugin.getResource("akuzoi-roles.json")) {
            if (in != null) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                parseRoles(json, result);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning(String.format("Failed to read akuzoi-roles.json: %s", ex.getMessage()));
        }

        result.putIfAbsent("default", new RoleInfo(List.of("AkuzoiAI"), "qing"));
        return new AkuzoiRoleRegistry(result);
    }

    public RoleInfo resolve(String configuredRole) {
        if (configuredRole != null && roles.containsKey(configuredRole)) {
            return roles.get(configuredRole);
        }
        RoleInfo fallback = roles.get("default");
        return fallback != null ? fallback : new RoleInfo(List.of("AkuzoiAI"), "qing");
    }

    private static void parseRoles(String json, Map<String, RoleInfo> output) {
        Matcher entryMatcher = ENTRY_PATTERN.matcher(json);
        while (entryMatcher.find()) {
            String roleKey = entryMatcher.group(1);
            String body = entryMatcher.group(2);

            String id = extractWithPattern(body, ID_PATTERN);
            if (id == null || id.isBlank()) {
                continue;
            }

            List<String> names = extractNames(body);
            if (names.isEmpty()) {
                names = List.of(roleKey);
            }

            output.put(roleKey, new RoleInfo(names, id));
        }
    }

    private static List<String> extractNames(String body) {
        String single = extractWithPattern(body, NAME_STRING_PATTERN);
        if (single != null && !single.isBlank()) {
            return List.of(single);
        }

        Matcher arrayMatcher = NAME_ARRAY_PATTERN.matcher(body);
        if (!arrayMatcher.find()) {
            return List.of();
        }

        String arrayBody = arrayMatcher.group(1).trim();
        if (arrayBody.isBlank()) {
            return List.of();
        }

        String[] parts = arrayBody.split(",");
        List<String> names = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1).trim();
            }
            if (!value.isBlank()) {
                names.add(value);
            }
        }
        return names;
    }

    private static String extractWithPattern(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    public record RoleInfo(List<String> names, String endpointId) {
        public String primaryName() {
            return names.isEmpty() ? "AkuzoiAI" : names.get(0);
        }
    }
}
