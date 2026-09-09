package com.smartprep.controller;

import com.smartprep.dto.StartInterviewRequest;
import com.smartprep.dto.SubmitAnswerRequest;
import com.smartprep.service.InterviewService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {
    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping("/start")
    public Map<String, Object> start(Authentication auth, @Valid @RequestBody StartInterviewRequest request) {
        return interviewService.start(currentUserId(auth), request);
    }

    @PostMapping("/{sessionId}/answer")
    public Map<String, Object> answer(
            Authentication auth,
            @PathVariable Long sessionId,
            @Valid @RequestBody SubmitAnswerRequest request) {
        return interviewService.submitAnswer(currentUserId(auth), sessionId, request);
    }

    @PostMapping("/{sessionId}/end")
    public Map<String, Object> end(Authentication auth, @PathVariable Long sessionId) {
        return interviewService.end(currentUserId(auth), sessionId);
    }

    @GetMapping("/{sessionId}/report")
    public Map<String, Object> report(Authentication auth, @PathVariable Long sessionId) {
        return interviewService.report(currentUserId(auth), sessionId);
    }

    private Long currentUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }
}
