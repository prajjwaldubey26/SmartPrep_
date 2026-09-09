package com.smartprep.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LlmClient {
    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public LlmClient(
            @Value("${smartprep.ai.enabled:false}") boolean enabled,
            @Value("${smartprep.ai.api-key:}") String apiKey,
            @Value("${smartprep.ai.model:gpt-4o-mini}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = enabled && !this.apiKey.isBlank();
        this.model = model;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String chat(String systemPrompt, List<Map<String, String>> messages) {
        if (!enabled) {
            throw new IllegalStateException("LLM disabled");
        }
        try {
            List<Map<String, String>> payloadMessages = new ArrayList<>();
            payloadMessages.add(Map.of("role", "system", "content", systemPrompt));
            payloadMessages.addAll(messages);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", 0.4);
            body.put("messages", payloadMessages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("LLM HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("Empty LLM response");
            }
            return content.asText().trim();
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }
}
