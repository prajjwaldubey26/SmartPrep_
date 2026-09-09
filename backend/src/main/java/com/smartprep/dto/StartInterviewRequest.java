package com.smartprep.dto;

import jakarta.validation.constraints.NotBlank;

public class StartInterviewRequest {
    @NotBlank
    private String role;

    @NotBlank
    private String difficulty;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
}
