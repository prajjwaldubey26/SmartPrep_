package com.smartprep.dto;

public class AuthResponse {
    private String token;
    private Long userId;
    private String name;
    private String email;
    private String targetRole;

    public AuthResponse(String token, Long userId, String name, String email, String targetRole) {
        this.token = token;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.targetRole = targetRole;
    }

    public String getToken() { return token; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getTargetRole() { return targetRole; }
}
