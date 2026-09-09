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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LlmClient {
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final String chatCompletionsUrl;
    private final int maxTokens;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public LlmClient(
            @Value("${smartprep.ai.enabled:true}") boolean enabled,
            @Value("${smartprep.ai.api-key:}") String apiKey,
            @Value("${smartprep.ai.nvidia-api-key:}") String nvidiaApiKey,
            @Value("${smartprep.ai.openai-api-key:}") String openaiApiKey,
            @Value("${smartprep.ai.base-url:https://integrate.api.nvidia.com/v1}") String baseUrl,
            @Value("${smartprep.ai.model:meta/llama-3.1-70b-instruct}") String model,
            @Value("${smartprep.ai.max-tokens:1024}") int maxTokens) {
        this.apiKey = firstNonBlank(apiKey, nvidiaApiKey, openaiApiKey);
        this.enabled = enabled && !this.apiKey.isBlank();
        this.model = blankToDefault(model, "meta/llama-3.1-70b-instruct");
        this.maxTokens = maxTokens > 0 ? maxTokens : 1024;
        this.chatCompletionsUrl = normalizeChatUrl(baseUrl);

        if (this.enabled) {
            log.info("SMARTPREP live AI enabled. providerUrl={}, model={}", this.chatCompletionsUrl, this.model);
        } else {
            log.warn("SMARTPREP live AI disabled. Using local coach fallback. Set NVIDIA_API_KEY (or SMARTPREP_AI_API_KEY) and SMARTPREP_AI_ENABLED=true.");
        }
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
            if (messages != null) {
                payloadMessages.addAll(messages);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", 0.6);
            body.put("top_p", 0.9);
            body.put("max_tokens", maxTokens);
            body.put("stream", false);
            body.put("messages", payloadMessages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsUrl))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.error("NVIDIA/LLM HTTP {} body={}", response.statusCode(), abbreviate(response.body(), 500));
                throw new IllegalStateException("LLM HTTP " + response.statusCode() + ": " + abbreviate(response.body(), 300));
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                // some models may return content as array parts
                JsonNode alt = root.path("choices").path(0).path("message").path("reasoning_content");
                if (!alt.isMissingNode() && !alt.asText().isBlank()) {
                    return alt.asText().trim();
                }
                throw new IllegalStateException("Empty LLM response");
            }
            return content.asText().trim();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("LLM request failed: {}", ex.getMessage());
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private static String normalizeChatUrl(String baseUrl) {
        String base = blankToDefault(baseUrl, "https://integrate.api.nvidia.com/v1").trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String abbreviate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
