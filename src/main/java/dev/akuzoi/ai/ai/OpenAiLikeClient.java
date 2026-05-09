package dev.akuzoi.ai.ai;

import dev.akuzoi.ai.config.AkuzoiRoleRegistry;
import dev.akuzoi.ai.config.PluginSettings;
import dev.akuzoi.ai.memory.ChatMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 负责向模型服务发起请求。
public final class OpenAiLikeClient {
    private static final String AKUZOI_ENDPOINT_BASE = "https://api.zyghit.cn/akuzoiai/endpoint";
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\\\\\"])*)\\\"");
    private static final Pattern SSE_DATA_PATTERN = Pattern.compile("^data:\\s*(.+)$", Pattern.MULTILINE);

    private final PluginSettings settings;
    private final AkuzoiRoleRegistry roleRegistry;
    private final HttpClient httpClient;

    public OpenAiLikeClient(PluginSettings settings, AkuzoiRoleRegistry roleRegistry) {
        this.settings = settings;
        this.roleRegistry = roleRegistry;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .build();
    }

    public String chat(List<ChatMessage> messages, int maxTokens) throws IOException, InterruptedException {
        String baseUrl = settings.baseUrl();
        String apiKey = settings.apiKey();
        String model = settings.model();

        if (settings.useAkuzoiOfficialService()) {
            AkuzoiRoleRegistry.RoleInfo roleInfo = roleRegistry.resolve(settings.akuzoiRole());
            baseUrl = AKUZOI_ENDPOINT_BASE + "/" + roleInfo.endpointId();
            apiKey = settings.akuzoiApiKey();
            model = roleInfo.endpointId();
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException(settings.useAkuzoiOfficialService() ? "ai.akuzoi.api-key is empty" : "ai.api-key is empty");
        }

        String body = buildRequestBody(messages, maxTokens, model, false);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("AI API returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return extractFirstContent(response.body());
    }

    public void chatStream(List<ChatMessage> messages, int maxTokens, Consumer<String> onChunk)
            throws IOException, InterruptedException {
        String baseUrl = settings.baseUrl();
        String apiKey = settings.apiKey();
        String model = settings.model();

        if (settings.useAkuzoiOfficialService()) {
            AkuzoiRoleRegistry.RoleInfo roleInfo = roleRegistry.resolve(settings.akuzoiRole());
            baseUrl = AKUZOI_ENDPOINT_BASE + "/" + roleInfo.endpointId();
            apiKey = settings.akuzoiApiKey();
            model = roleInfo.endpointId();
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException(settings.useAkuzoiOfficialService() ? "ai.akuzoi.api-key is empty" : "ai.api-key is empty");
        }

        String body = buildRequestBody(messages, maxTokens, model, true);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        // 按行接收 SSE 数据。        HttpResponse<HttpResponse.BodySubscribers.LineSubscriber> response =
                httpClient.send(request, HttpResponse.BodySubscribers.fromLineSubscriber(
                        new SSELineSubscriber(onChunk)));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("AI API returned HTTP " + response.statusCode());
        }
    }

    private static class SSELineSubscriber implements java.util.function.Consumer<String> {
        private final Consumer<String> onChunk;
        private static final Pattern DELTA_CONTENT_PATTERN = Pattern.compile(
                "\\\"delta\\\"\\s*:\\s*\\{[^}]*\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\\\\\"])*)\\\"");

        public SSELineSubscriber(Consumer<String> onChunk) {
            this.onChunk = onChunk;
        }

        @Override
        public void accept(String line) {
            if (line == null || line.isEmpty() || line.startsWith(":")) return;

            // 结束了就收手。            if (line.trim().equals("data: [DONE]")) return;
            Matcher dataMatcher = SSE_DATA_PATTERN.matcher(line);
            if (!dataMatcher.find()) return;

            String json = dataMatcher.group(1).trim();
            if (json.isEmpty() || json.equals("[DONE]")) return;

            // 这一行里有正文才往外传。            Matcher contentMatcher = DELTA_CONTENT_PATTERN.matcher(json);
            if (contentMatcher.find()) {
                String content = unescapeJsonStatic(contentMatcher.group(1));
                if (!content.isEmpty()) {
                    onChunk.accept(content);
                }
            }
        }

        // 常见转义先处理掉，别让内容乱了。        private static String unescapeJsonStatic(String value) {
            if (value == null) return "";
            StringBuilder builder = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\\' && i + 1 < value.length()) {
                    char next = value.charAt(++i);
                    switch (next) {
                        case '"' -> builder.append('"');
                        case '\\' -> builder.append('\\');
                        case '/' -> builder.append('/');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> {
                            if (i + 4 < value.length()) {
                                String hex = value.substring(i + 1, i + 5);
                                builder.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            }
                        }
                        default -> builder.append(next);
                    }
                } else {
                    builder.append(c);
                }
            }
            return builder.toString();
        }
    }

    // 请求体在这里统一拼，普通和流式只差一个 stream 标记。
    private String buildRequestBody(List<ChatMessage> messages, int maxTokens, String model, boolean stream) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        appendJsonField(builder, "model", model);
        builder.append(',');
        builder.append("\"temperature\":").append(settings.temperature()).append(',');
        builder.append("\"max_tokens\":").append(maxTokens).append(',');
        if (stream) {
            builder.append("\"stream\":true,");
        }
        builder.append("\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (i > 0) builder.append(',');
            builder.append('{');
            appendJsonField(builder, "role", message.role());
            builder.append(',');
            appendJsonField(builder, "content", message.content());
            builder.append('}');
        }
        builder.append("]}");
        return builder.toString();
    }

    private void appendJsonField(StringBuilder builder, String key, String value) {
        builder.append('"').append(escapeJson(key)).append("\":\"").append(escapeJson(value)).append('"');
    }

    private String extractFirstContent(String json) throws IOException {
        Matcher matcher = CONTENT_PATTERN.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1)).trim();
        }
        throw new IOException("Cannot parse AI response content");
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    private String unescapeJson(String value) {
        return SSELineSubscriber.unescapeJsonStatic(value);
    }
}