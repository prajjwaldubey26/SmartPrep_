package com.smartprep.controller;

import com.smartprep.service.LlmClient;
import com.smartprep.service.TtsService;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final LlmClient llmClient;
    private final TtsService ttsService;
    private final String model;
    private final String baseUrl;
    private final Environment environment;
    private final DataSource dataSource;

    public HealthController(
            LlmClient llmClient,
            TtsService ttsService,
            Environment environment,
            DataSource dataSource,
            @Value("${smartprep.ai.model:meta/llama-3.1-70b-instruct}") String model,
            @Value("${smartprep.ai.base-url:https://integrate.api.nvidia.com/v1}") String baseUrl) {
        this.llmClient = llmClient;
        this.ttsService = ttsService;
        this.environment = environment;
        this.dataSource = dataSource;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "smartprep-backend");
        body.put("profiles", environment.getActiveProfiles());
        body.put("database", detectDatabase());
        body.put("aiEnabled", llmClient.isEnabled());
        body.put("aiProvider", baseUrl);
        body.put("aiModel", model);
        body.put("ttsProvider", ttsService.provider());
        body.put("cloudTts", ttsService.isCloudEnabled());
        return body;
    }

    private String detectDatabase() {
        try (var connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            String url = connection.getMetaData().getURL();
            boolean durable = url != null && !url.contains(":h2:mem:");
            return durable ? product + " (persistent)" : product + " (in-memory, resets on restart)";
        } catch (Exception ex) {
            return "unavailable";
        }
    }
}
