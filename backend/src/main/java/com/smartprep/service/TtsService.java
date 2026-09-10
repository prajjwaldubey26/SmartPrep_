package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TtsService {
    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    private final String elevenLabsKey;
    private final String elevenLabsVoiceId;
    private final String openAiKey;
    private final String openAiVoice;
    private final String openAiModel;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public TtsService(
            @Value("${smartprep.tts.elevenlabs-api-key:}") String elevenLabsKey,
            @Value("${smartprep.tts.elevenlabs-voice-id:EXAVITQu4vr4xnSDxMaL}") String elevenLabsVoiceId,
            @Value("${smartprep.tts.openai-api-key:}") String ttsOpenAiKey,
            @Value("${smartprep.ai.openai-api-key:}") String sharedOpenAiKey,
            @Value("${smartprep.tts.openai-voice:nova}") String openAiVoice,
            @Value("${smartprep.tts.openai-model:gpt-4o-mini-tts}") String openAiModel) {
        this.elevenLabsKey = trim(elevenLabsKey);
        this.elevenLabsVoiceId = blankToDefault(elevenLabsVoiceId, "EXAVITQu4vr4xnSDxMaL"); // Sarah - natural interviewer
        this.openAiKey = firstNonBlank(ttsOpenAiKey, sharedOpenAiKey);
        this.openAiVoice = blankToDefault(openAiVoice, "nova");
        this.openAiModel = blankToDefault(openAiModel, "gpt-4o-mini-tts");
    }

    public boolean isCloudEnabled() {
        return !elevenLabsKey.isBlank() || !openAiKey.isBlank();
    }

    public String provider() {
        if (!elevenLabsKey.isBlank()) return "elevenlabs";
        if (!openAiKey.isBlank()) return "openai";
        return "browser";
    }

    public SpeechResult synthesize(String text) {
        String cleaned = sanitize(text);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Text is empty");
        }
        if (cleaned.length() > 1200) {
            cleaned = cleaned.substring(0, 1197) + "...";
        }

        if (!elevenLabsKey.isBlank()) {
            try {
                return synthesizeElevenLabs(cleaned);
            } catch (Exception ex) {
                log.warn("ElevenLabs TTS failed, trying next provider: {}", ex.getMessage());
            }
        }
        if (!openAiKey.isBlank()) {
            try {
                return synthesizeOpenAi(cleaned);
            } catch (Exception ex) {
                log.warn("OpenAI TTS failed: {}", ex.getMessage());
            }
        }
        return null;
    }

    private SpeechResult synthesizeElevenLabs(String text) throws Exception {
        Map<String, Object> voiceSettings = new LinkedHashMap<>();
        voiceSettings.put("stability", 0.42);
        voiceSettings.put("similarity_boost", 0.85);
        voiceSettings.put("style", 0.35);
        voiceSettings.put("use_speaker_boost", true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        body.put("model_id", "eleven_multilingual_v2");
        body.put("voice_settings", voiceSettings);

        String url = "https://api.elevenlabs.io/v1/text-to-speech/"
                + URLEncoder.encode(elevenLabsVoiceId, StandardCharsets.UTF_8)
                + "?output_format=mp3_44100_128";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("xi-api-key", elevenLabsKey)
                .header("Accept", "audio/mpeg")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("ElevenLabs HTTP " + response.statusCode());
        }
        return new SpeechResult(response.body(), "audio/mpeg", "elevenlabs");
    }

    private SpeechResult synthesizeOpenAi(String text) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openAiModel);
        body.put("input", text);
        body.put("voice", openAiVoice);
        body.put("response_format", "mp3");
        body.put("speed", 0.95);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/audio/speech"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + openAiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 300) {
            // Fallback older tts-1-hd if mini model unavailable
            if (!"tts-1-hd".equals(openAiModel)) {
                body.put("model", "tts-1-hd");
                request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.openai.com/v1/audio/speech"))
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + openAiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build();
                response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            }
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI TTS HTTP " + response.statusCode());
            }
        }
        return new SpeechResult(response.body(), "audio/mpeg", "openai");
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text
                .replaceAll("[#*_`]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) return value.trim();
        }
        return "";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record SpeechResult(byte[] audio, String contentType, String provider) {}
}
