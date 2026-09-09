package com.smartprep.controller;

import com.smartprep.service.LlmClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final LlmClient llmClient;
    private final String model;
    private final String baseUrl;

    public HealthController(
            LlmClient llmClient,
            @Value("${smartprep.ai.model:meta/llama-3.1-70b-instruct}") String model,
            @Value("${smartprep.ai.base-url:https://integrate.api.nvidia.com/v1}") String baseUrl) {
        this.llmClient = llmClient;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "smartprep-backend");
        body.put("aiEnabled", llmClient.isEnabled());
        body.put("aiProvider", baseUrl);
        body.put("aiModel", model);
        return body;
    }
}
