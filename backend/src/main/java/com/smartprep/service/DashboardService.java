package com.smartprep.service;

import com.smartprep.entity.InterviewSession;
import com.smartprep.entity.User;
import com.smartprep.repository.InterviewSessionRepository;
import com.smartprep.repository.UserRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {
    private final InterviewSessionRepository sessionRepository;
    private final UserRepository userRepository;

    public DashboardService(InterviewSessionRepository sessionRepository, UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    public Map<String, Object> summary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        List<InterviewSession> sessions = sessionRepository.findByUserIdOrderByStartedAtDesc(userId);

        double average = sessions.stream()
                .map(InterviewSession::getAverageScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

        List<Map<String, Object>> recent = sessions.stream().limit(8).map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("role", s.getRole());
            row.put("difficulty", s.getDifficulty());
            row.put("status", s.getStatus());
            row.put("averageScore", s.getAverageScore());
            row.put("startedAt", s.getStartedAt());
            return row;
        }).toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSessions", sessions.size());
        summary.put("averageScore", sessions.isEmpty() ? null : average);
        summary.put("focusArea", user.getTargetRole());
        summary.put("recentSessions", recent);
        summary.put("sessionsPage", "/sessions.html");
        return summary;
    }

    public Map<String, Object> sessions(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<InterviewSession> sessions = sessionRepository.findByUserIdOrderByStartedAtDesc(userId);
        List<Map<String, Object>> rows = sessions.stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("role", s.getRole());
            row.put("difficulty", s.getDifficulty());
            row.put("status", s.getStatus());
            row.put("averageScore", s.getAverageScore());
            row.put("startedAt", s.getStartedAt());
            row.put("endedAt", s.getEndedAt());
            return row;
        }).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalSessions", rows.size());
        body.put("sessions", rows);
        return body;
    }
}
