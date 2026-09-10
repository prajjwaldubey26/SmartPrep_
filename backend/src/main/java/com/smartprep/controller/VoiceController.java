package com.smartprep.controller;

import com.smartprep.dto.SpeakRequest;
import com.smartprep.service.TtsService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {
    private final TtsService ttsService;

    public VoiceController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cloudTts", ttsService.isCloudEnabled());
        body.put("provider", ttsService.provider());
        body.put("fallback", "browser-neural");
        return body;
    }

    @PostMapping("/speak")
    public ResponseEntity<byte[]> speak(@Valid @RequestBody SpeakRequest request) {
        TtsService.SpeechResult speech = ttsService.synthesize(request.getText());
        if (speech == null || speech.audio() == null || speech.audio().length == 0) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloud TTS unavailable. Client should use browser neural voice fallback.");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Smartprep-Tts-Provider", speech.provider())
                .contentType(MediaType.parseMediaType(speech.contentType()))
                .body(speech.audio());
    }
}
