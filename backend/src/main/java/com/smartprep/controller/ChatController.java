package com.smartprep.controller;

import com.smartprep.dto.ChatRequest;
import com.smartprep.service.ChatService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public Map<String, String> chat(Authentication auth, @Valid @RequestBody ChatRequest request) {
        // auth required by security; userId available if needed later
        return chatService.reply(request);
    }
}
