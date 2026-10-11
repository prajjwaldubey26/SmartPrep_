package com.smartprep.domain;

import java.util.List;

public record InterviewDomain(
        String id,
        String group,
        String name,
        String icon,
        String description,
        List<String> focusAreas,
        String questionStyle,
        List<String> fallbackQuestions,
        List<String> angles
) {
    public String focusAreasText() {
        return String.join(", ", focusAreas);
    }
}
