package com.smartprep.dto;

import jakarta.validation.constraints.NotBlank;

public class StartInterviewRequest {
    @NotBlank
    private String domainId;

    @NotBlank
    private String difficulty;

    public String getDomainId() { return domainId; }
    public void setDomainId(String domainId) { this.domainId = domainId; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
}
