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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LlmClient {
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final Pattern HARMONY_FINAL = Pattern.compile(
            "(?s)<\\|channel\\|>final<\\|message\\|>(.*?)(?:<\\|end\\|>|<\\|return\\|>|$)");
    private static final Pattern HARMONY_ANALYSIS = Pattern.compile(
            "(?s)<\\|channel\\|>analysis<\\|message\\|>.*?(?=<\\|channel\\|>|<\\|end\\|>|$)");
    private static final Pattern HARMONY_MARKERS = Pattern.compile("<\\|[^|]*\\|>");

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
        return chat(systemPrompt, messages, 0.6);
    }

    public String chat(String systemPrompt, List<Map<String, String>> messages, double temperature) {
        return chat(systemPrompt, messages, temperature, maxTokens, Duration.ofSeconds(90), null);
    }

    /**
     * Coach replies need room for the written answer. gpt-oss spends the token
     * budget on reasoning first, so a 1024 cap comes back as a short generic line.
     */
    public String coachReply(String systemPrompt, List<Map<String, String>> messages) {
        Duration timeout = Duration.ofSeconds(50);
        try {
            return chat(systemPrompt, messages, 0.4, 4096, timeout, "low");
        } catch (IllegalStateException ex) {
            if (!isEmptyAnswer(ex)) {
                throw ex;
            }
            log.warn("Coach reply was empty after reasoning. Retrying once with a full answer budget.");
            return chat(systemPrompt, messages, 0.4, 4096, timeout, "low");
        }
    }

    public String chat(
            String systemPrompt,
            List<Map<String, String>> messages,
            double temperature,
            int requestMaxTokens,
            Duration timeout) {
        return chat(systemPrompt, messages, temperature, requestMaxTokens, timeout, null);
    }

    public String chat(
            String systemPrompt,
            List<Map<String, String>> messages,
            double temperature,
            int requestMaxTokens,
            Duration timeout,
            String reasoningEffort) {
        if (!enabled) {
            throw new IllegalStateException("LLM disabled");
        }
        try {
            List<Map<String, String>> payloadMessages = new ArrayList<>();
            payloadMessages.add(Map.of("role", "system", "content", systemPrompt));
            if (messages != null) {
                payloadMessages.addAll(messages);
            }

            int tokenBudget = requestMaxTokens > 0 ? requestMaxTokens : maxTokens;
            Duration requestTimeout = timeout == null ? Duration.ofSeconds(90) : timeout;

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", Math.max(0.0, Math.min(1.5, temperature)));
            body.put("top_p", 0.95);
            body.put("max_tokens", tokenBudget);
            body.put("stream", false);
            body.put("messages", payloadMessages);
            if (reasoningEffort != null && !reasoningEffort.isBlank() && model.toLowerCase().contains("gpt-oss")) {
                body.put("reasoning_effort", reasoningEffort);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsUrl))
                    .timeout(requestTimeout)
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
            String answer = visibleAnswer(root.path("choices").path(0).path("message").path("content"));
            if (answer.isBlank()) {
                throw new IllegalStateException("Empty LLM response");
            }
            return answer;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("LLM request failed: {}", ex.getMessage());
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private static boolean isEmptyAnswer(IllegalStateException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        return message.contains("Empty LLM response");
    }

    private static String visibleAnswer(JsonNode content) {
        return stripHarmony(textOf(content)).trim();
    }

    private static String textOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : node) {
                if (part.isTextual()) {
                    sb.append(part.asText());
                } else if (part.hasNonNull("text")) {
                    sb.append(part.path("text").asText());
                } else if (part.has("content")) {
                    sb.append(textOf(part.path("content")));
                }
            }
            return sb.toString();
        }
        if (node.isObject() && node.hasNonNull("text")) {
            return node.path("text").asText();
        }
        return "";
    }

    private static String stripHarmony(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        Matcher finalChannel = HARMONY_FINAL.matcher(raw);
        String answer = null;
        while (finalChannel.find()) {
            answer = finalChannel.group(1);
        }
        String text = answer != null ? answer : HARMONY_ANALYSIS.matcher(raw).replaceAll("");
        return HARMONY_MARKERS.matcher(text).replaceAll("").trim();
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
