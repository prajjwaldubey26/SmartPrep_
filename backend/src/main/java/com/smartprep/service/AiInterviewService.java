package com.smartprep.service;

import com.smartprep.domain.InterviewDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AiInterviewService {
    private static final int QUESTIONS_PER_SESSION = 5;


    private final LlmClient llmClient;

    public AiInterviewService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public int questionsPerSession() {
        return QUESTIONS_PER_SESSION;
    }

    public String generateQuestion(
            InterviewDomain domain,
            String difficulty,
            int orderNo,
            String previousAnswer,
            List<String> avoidQuestions) {
        List<String> avoid = avoidQuestions == null ? List.of() : avoidQuestions.stream()
                .filter(q -> q != null && !q.isBlank())
                .map(String::trim)
                .distinct()
                .limit(40)
                .toList();

        String angle = pickAngle(domain);
        String avoidBlock = avoid.isEmpty()
                ? "(none yet)"
                : avoid.stream().map(q -> "- " + q).collect(Collectors.joining("\n"));

        if (llmClient.isEnabled()) {
            try {
                String system = """
                        You are the SMARTPREP interviewer for exactly one domain.
                        Adapt your persona, question style, and difficulty to that domain.
                        Style rules:
                        - If the domain is Management Consulting, ask case-study and market-sizing questions.
                        - If the domain is Software Engineering, focus on technical depth, edge cases, and system design.
                        - If the domain is UX/UI Design, focus on user research, design thinking, and portfolio walkthroughs.
                        - For every other domain, follow the question style in the user message and stay inside its focus areas.
                        Generate ONE fresh question a real interviewer in that domain would ask.
                        Rules:
                        - Return ONLY the question text. No numbering, labels, markdown, or preamble.
                        - Do NOT reuse or lightly rephrase any question in the avoid list.
                        - Make it specific to the domain, difficulty, and suggested angle.
                        - Prefer a concrete scenario over a textbook prompt.
                        - If this is question 1, open with a domain-specific scenario or behavioral prompt. Do not ask a generic warm-up.
                        - If the previous answer was weak, ask a sharper follow-up on a NEW facet of the same focus area.
                        """;
                String user = "Domain: " + domain.name()
                        + "\nDomain id: " + domain.id()
                        + "\nDescription: " + domain.description()
                        + "\nFocus areas: " + domain.focusAreasText()
                        + "\nQuestion style: " + domain.questionStyle()
                        + "\nDifficulty: " + difficulty
                        + "\nQuestion number in session: " + orderNo + " of " + QUESTIONS_PER_SESSION
                        + "\nSuggested angle: " + angle
                        + "\nSession seed: " + ThreadLocalRandom.current().nextInt(100000, 999999)
                        + "\nPrevious answer: " + (previousAnswer == null || previousAnswer.isBlank() ? "(none)" : previousAnswer)
                        + "\nAvoid repeating these questions:\n" + avoidBlock;
                String generated = llmClient.chat(system, List.of(Map.of("role", "user", "content", user)), 0.95);
                String cleaned = cleanQuestion(generated);
                if (!cleaned.isBlank() && !isTooSimilar(cleaned, avoid)) {
                    return cleaned;
                }
            } catch (Exception ignored) {
                // fallback below
            }
        }

        return fallbackQuestion(domain, difficulty, orderNo, previousAnswer, avoid, angle);
    }

    public String upgradePlan(String role, double averageScore) {
        if (averageScore >= 8) {
            return "Advance to harder " + role + " mocks and pressure-test trade-offs. Drill one weak follow-up style daily: 'why this over the alternative?'";
        }
        if (averageScore >= 5) {
            return "For " + role + ", enforce structure every answer (Approach -> Example -> Trade-off -> Result). Re-run only the lowest-scoring question type for 3 days.";
        }
        return "Reset fundamentals for " + role + ": 8-minute outline drills (no full speeches). Every answer must include ownership + one number before you increase difficulty.";
    }

    public EvaluationResult evaluate(InterviewDomain domain, String question, String answer, String difficulty) {
        if (llmClient.isEnabled()) {
            try {
                String system = """
                        You are a warm but rigorous SMARTPREP coach for exactly one interview domain.
                        Score the answer with that domain's rubric, not a generic software rubric.
                        If the domain is Management Consulting, reward structure, math, and a recommendation.
                        If the domain is Software Engineering, reward technical depth, edge cases, and trade-offs.
                        If the domain is UX/UI Design, reward user research, design thinking, and a clear walkthrough.
                        For any other domain, score against the focus areas in the user message.
                        Teach the candidate: what was right for this domain, what was wrong, and a stronger answer.
                        Return exactly this format (one field per line, no markdown):
                        SCORE: <0-10 integer>
                        VERDICT: <Correct | Partially correct | Incorrect>
                        STRENGTHS: <one sentence on what they did well for this domain>
                        IMPROVEMENTS: <one concrete fix tied to a focus area>
                        WHY_RIGHT: <1-2 sentences: what was correct and why it matters in this domain>
                        WHY_WRONG: <1-2 sentences: what was missing/wrong for this domain>
                        BETTER_ANSWER: <3-5 sentence outline of a stronger answer using the focus areas>
                        FEEDBACK: <2-3 coaching sentences referencing their actual content>
                        SPOKEN: <40-80 words, conversational spoken coaching. Use short sentences. Sound human, calm, and clear. Do not use bullet points, markdown, or labels.>
                        Be specific. Do not be generic.
                        """;
                String user = "Domain: " + domain.name()
                        + "\nDescription: " + domain.description()
                        + "\nFocus areas: " + domain.focusAreasText()
                        + "\nQuestion style: " + domain.questionStyle()
                        + "\nDifficulty: " + difficulty
                        + "\nQuestion: " + question
                        + "\nAnswer: " + answer;
                String raw = llmClient.chat(system, List.of(Map.of("role", "user", "content", user)));
                return parseLlmEvaluation(raw, question, answer, difficulty, domain);
            } catch (Exception ignored) {
                // fallback
            }
        }
        return evaluateLocal(question, answer, difficulty, domain);
    }

    private String fallbackQuestion(
            InterviewDomain domain,
            String difficulty,
            int orderNo,
            String previousAnswer,
            List<String> avoid,
            String angle) {
        List<String> bank = new ArrayList<>(domain.fallbackQuestions());
        if (orderNo > 1) {
            Collections.shuffle(bank, ThreadLocalRandom.current());
        }

        String chosen = bank.stream()
                .filter(q -> !isTooSimilar(q, avoid))
                .findFirst()
                .orElseGet(() -> bank.get(ThreadLocalRandom.current().nextInt(bank.size())));

        String intensity = switch (difficulty.toUpperCase(Locale.ROOT)) {
            case "EASY" -> "Keep it practical and include one concrete example.";
            case "HARD" -> "Push into trade-offs, failure modes, and measurable impact.";
            default -> "Balance structure, technical depth, and a clear close.";
        };

        if (previousAnswer != null) {
            String prev = previousAnswer.toLowerCase(Locale.ROOT);
            if (prev.length() < 80 || containsAny(prev, "not sure", "idk", "don't know", "dont know")) {
                return "Follow-up on " + angle + ": " + chosen
                        + " Start with your approach in one sentence, then give one example. " + intensity;
            }
        }
        return chosen + " Focus on " + angle + ". " + intensity;
    }

    private String pickAngle(InterviewDomain domain) {
        List<String> angles = domain.angles();
        if (angles == null || angles.isEmpty()) {
            return domain.focusAreas().get(0);
        }
        return angles.get(ThreadLocalRandom.current().nextInt(angles.size()));
    }

    private String cleanQuestion(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim()
                .replaceAll("^[\"']+|[\"']+$", "")
                .replaceAll("(?i)^question\\s*\\d*\\s*[:.\\-]\\s*", "")
                .replaceAll("^#+\\s*", "");
        int newline = cleaned.indexOf('\n');
        if (newline > 40) {
            cleaned = cleaned.substring(0, newline).trim();
        }
        return cleaned;
    }

    private boolean isTooSimilar(String candidate, List<String> avoid) {
        String norm = normalize(candidate);
        for (String prior : avoid) {
            String p = normalize(prior);
            if (p.isBlank()) continue;
            if (norm.equals(p)) return true;
            if (norm.contains(p) || p.contains(norm)) return true;
            if (tokenOverlap(norm, p) >= 0.72) return true;
        }
        return false;
    }

    private double tokenOverlap(String a, String b) {
        String[] left = a.split("\\s+");
        String[] right = b.split("\\s+");
        if (left.length == 0 || right.length == 0) return 0;
        java.util.Set<String> setB = new java.util.HashSet<>();
        Collections.addAll(setB, right);
        int hits = 0;
        for (String token : left) {
            if (token.length() < 4) continue;
            if (setB.contains(token)) hits++;
        }
        int meaningful = 0;
        for (String token : left) {
            if (token.length() >= 4) meaningful++;
        }
        return meaningful == 0 ? 0 : (double) hits / meaningful;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private EvaluationResult evaluateLocal(String question, String answer, String difficulty, InterviewDomain domain) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String a = answer == null ? "" : answer.trim();
        String lower = a.toLowerCase(Locale.ROOT);
        int words = a.isEmpty() ? 0 : a.split("\\s+").length;

        int score = 3;
        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();

        if (words >= 40) score += 1;
        if (words >= 90) score += 1;
        if (words >= 140) score += 1;
        else if (words < 40) gaps.add("Answer is too short for this question - aim for 60-120 seconds of content.");

        if (lower.startsWith("i ") || lower.startsWith("i'") || containsAny(lower, " i ", " i'", " my ")) {
            score += 1;
            strengths.add("Ownership language is present.");
        } else {
            gaps.add("Ownership is unclear - use I-verbs for decisions and actions.");
        }

        boolean structured = containsAny(lower, "first", "then", "next", "finally", "because", "approach", "trade");
        if (structured) {
            score += 1;
            strengths.add("Some structure/reasoning markers are visible.");
        } else {
            gaps.add("Add explicit structure (approach, then example, then trade-off/result).");
        }

        boolean measurable = containsAny(lower, "%", "percent", "ms", "qps", "users", "reduced", "increased", "by ");
        if (measurable) {
            score += 1;
            strengths.add("Includes a measurable signal.");
        } else {
            gaps.add("Add one metric or observable outcome.");
        }

        if (containsAny(q, "trade-off", "tradeoff", "compare", "vs")) {
            if (containsAny(lower, "trade", "versus", "instead", "however", "on the other")) {
                score += 1;
                strengths.add("Addresses comparison/trade-offs asked by the question.");
            } else {
                gaps.add("Question asked for trade-offs/comparison - you barely addressed alternatives.");
                score = Math.max(2, score - 1);
            }
        }
        if (containsAny(q, "design", "system", "architecture", "scal")) {
            if (containsAny(lower, "cache", "queue", "database", "api", "latency", "throughput", "shard", "replica")) {
                score += 1;
                strengths.add("Uses real system-design vocabulary tied to components.");
            } else {
                gaps.add("For a design question, name components and bottlenecks (API, store, cache, async path).");
            }
        }
        if (containsAny(q, "conflict", "failure", "feedback", "why this role", "behavioral")) {
            if (containsAny(lower, "result", "learned", "next time", "afterward", "impact")) {
                score += 1;
                strengths.add("Closes with learning/result, which behavioral interviews reward.");
            } else {
                gaps.add("Behavioral answers need a clear result and learning loop.");
            }
        }

        if ("HARD".equalsIgnoreCase(difficulty) && !containsAny(lower, "edge", "fail", "limit", "risk", "trade")) {
            gaps.add("Hard mode expects edge cases/risks - call one out explicitly.");
            score = Math.max(2, score - 1);
        }
        if (domain != null) {
            boolean hitFocus = domain.focusAreas().stream()
                    .anyMatch(area -> containsAny(lower, area.toLowerCase(Locale.ROOT).split("\\s+")));
            if (hitFocus) {
                strengths.add("Touches this domain's focus: " + domain.focusAreasText() + ".");
            } else {
                gaps.add("For " + domain.name() + ", be explicit about " + domain.focusAreasText() + ".");
            }
        }

        score = Math.max(1, Math.min(10, score));

        if (strengths.isEmpty()) strengths.add("You attempted the prompt directly.");
        String strengthText = strengths.get(0);
        String improvementText = gaps.isEmpty()
                ? "Tighten the closer: restate decision + impact in one final sentence."
                : gaps.get(0);
        if (gaps.size() > 1) {
            improvementText = gaps.get(0) + " Also: " + gaps.get(1);
        }

        String verdict = score >= 8 ? "Correct" : score >= 5 ? "Partially correct" : "Incorrect";
        String whyRight = strengthText;
        String whyWrong = improvementText;
        String betterAnswer = domain == null
                ? "Start with your approach in one sentence, give one concrete example with a number, "
                    + "name one trade-off, then close with the result."
                : "Answer as a " + domain.name() + " interviewer expects. Cover "
                    + domain.focusAreasText()
                    + ". Lead with the decision, give one concrete example, then name the trade-off.";
        String feedback = "On \"" + shorten(question, 70) + "\", your answer scored " + score
                + "/10 based on ownership, structure, specificity, and fit to the ask. "
                + (words > 0 ? ("Length ~" + words + " words. ") : "")
                + "What worked: " + whyRight + " What to fix: " + whyWrong;
        String spoken = "You scored " + score + " out of 10. "
                + "What worked: " + whyRight + " "
                + "What to fix: " + whyWrong + " "
                + "Next time, lead with your approach, add one example with a number, then close with the result.";

        return new EvaluationResult(
                score, feedback, strengthText, improvementText, verdict, whyRight, whyWrong, betterAnswer, spoken);
    }

    private EvaluationResult parseLlmEvaluation(
            String raw, String question, String answer, String difficulty, InterviewDomain domain) {
        Integer score = extractInt(raw, "SCORE");
        String strengths = extractLine(raw, "STRENGTHS");
        String improvements = extractLine(raw, "IMPROVEMENTS");
        String feedback = extractLine(raw, "FEEDBACK");
        String verdict = extractLine(raw, "VERDICT");
        String whyRight = extractLine(raw, "WHY_RIGHT");
        String whyWrong = extractLine(raw, "WHY_WRONG");
        String betterAnswer = extractMultiline(raw, "BETTER_ANSWER");
        String spoken = extractMultiline(raw, "SPOKEN");

        if (score == null || feedback == null || feedback.isBlank()) {
            return evaluateLocal(question, answer, difficulty, domain);
        }

        EvaluationResult localFallback = null;
        if (whyRight == null || whyRight.isBlank() || whyWrong == null || whyWrong.isBlank()) {
            localFallback = evaluateLocal(question, answer, difficulty, domain);
        }

        String safeStrengths = blank(strengths, "Solid attempt.");
        String safeImprovements = blank(improvements, "Add one concrete example and a metric.");
        String safeVerdict = blank(verdict, score >= 8 ? "Correct" : score >= 5 ? "Partially correct" : "Incorrect");
        String safeWhyRight = blank(whyRight, localFallback != null ? localFallback.whyRight() : safeStrengths);
        String safeWhyWrong = blank(whyWrong, localFallback != null ? localFallback.whyWrong() : safeImprovements);
        String safeBetter = blank(betterAnswer, localFallback != null ? localFallback.betterAnswer()
                : "Lead with approach, give one example with a metric, name a trade-off, then close with impact.");
        String safeSpoken = blank(spoken,
                "You scored " + score + " out of 10. What worked: " + safeWhyRight
                        + " What to fix: " + safeWhyWrong + " Try the stronger outline next.");

        return new EvaluationResult(
                Math.max(0, Math.min(10, score)),
                feedback,
                safeStrengths,
                safeImprovements,
                safeVerdict,
                safeWhyRight,
                safeWhyWrong,
                safeBetter,
                safeSpoken);
    }

    private Integer extractInt(String raw, String key) {
        for (String line : raw.split("\\R")) {
            String t = line.trim();
            if (t.toUpperCase(Locale.ROOT).startsWith(key)) {
                String num = t.replaceAll("[^0-9]", "");
                if (!num.isBlank()) return Integer.parseInt(num);
            }
        }
        return null;
    }

    private String extractLine(String raw, String key) {
        for (String line : raw.split("\\R")) {
            String t = line.trim();
            if (t.toUpperCase(Locale.ROOT).startsWith(key)) {
                int idx = t.indexOf(':');
                return idx >= 0 ? t.substring(idx + 1).trim() : t;
            }
        }
        return null;
    }

    private String extractMultiline(String raw, String key) {
        String[] lines = raw.split("\\R");
        StringBuilder sb = new StringBuilder();
        boolean capturing = false;
        for (String line : lines) {
            String t = line.trim();
            String upper = t.toUpperCase(Locale.ROOT);
            if (upper.startsWith(key)) {
                capturing = true;
                int idx = t.indexOf(':');
                if (idx >= 0) sb.append(t.substring(idx + 1).trim());
                continue;
            }
            if (capturing) {
                if (upper.matches("^(SCORE|VERDICT|STRENGTHS|IMPROVEMENTS|WHY_RIGHT|WHY_WRONG|BETTER_ANSWER|FEEDBACK|SPOKEN)\\b.*")) {
                    break;
                }
                if (!t.isBlank()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(t);
                }
            }
        }
        String value = sb.toString().trim();
        return value.isBlank() ? null : value;
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "...";
    }

    public record EvaluationResult(
            int score,
            String feedback,
            String strengths,
            String improvements,
            String verdict,
            String whyRight,
            String whyWrong,
            String betterAnswer,
            String spokenFeedback) {}
}
