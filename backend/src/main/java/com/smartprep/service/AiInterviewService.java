package com.smartprep.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiInterviewService {
    private static final int QUESTIONS_PER_SESSION = 5;

    private static final Map<String, List<String>> BANK = Map.of(
            "Software Engineer", List.of(
                    "Explain how you would design a URL shortener. What trade-offs matter most at read-heavy scale?",
                    "Walk me through hashing collisions and when you'd choose chaining vs open addressing.",
                    "Describe a production bug you debugged end-to-end. What signals proved the root cause?",
                    "How do you design a testable API endpoint including contracts, failure modes, and observability?",
                    "Compare REST vs event-driven design for a notification system. When is each the wrong choice?"),
            "Data Analyst", List.of(
                    "Define a north-star metric for an interview-prep product and explain leading indicators.",
                    "Explain correlation vs causation with a product example and how you'd validate causality.",
                    "A funnel drops between step 2 and 3. Walk me through your investigation order.",
                    "Explain cohort retention analysis: formula, pitfalls, and how you'd present it.",
                    "How do you communicate uncertain findings to executives without overclaiming?"),
            "Product Manager", List.of(
                    "Prioritize the next quarter for SMARTPREP. What do you cut and why?",
                    "Tell me about saying no to a stakeholder. What decision framework did you use?",
                    "Design onboarding for first-time mock interview users. What is the activation moment?",
                    "How would you measure whether AI feedback quality is actually improving outcomes?",
                    "What risks come with adaptive questioning, and how would you mitigate them?"),
            "System Design", List.of(
                    "Design a real-time interview session service for 100k concurrent users. Where are the bottlenecks?",
                    "How would you store and query interview transcripts efficiently for search and replay?",
                    "Design a rate-limited AI evaluation pipeline with strict cost controls.",
                    "Where would you place caching in an interview analytics dashboard and why?",
                    "How do you keep the product reliable if the AI provider is down for 30 minutes?"),
            "HR / Behavioral", List.of(
                    "Tell me about a conflict on a team and how you resolved it without escalating early.",
                    "Describe a failure that changed how you collaborate. What systems did you put in place?",
                    "How do you operate when requirements are incomplete and the deadline is fixed?",
                    "Give an example of receiving hard feedback and the behavior you changed afterward.",
                    "Why this role, and why now? Connect your evidence to the job's top outcomes.")
    );

    private final LlmClient llmClient;

    public AiInterviewService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public int questionsPerSession() {
        return QUESTIONS_PER_SESSION;
    }

    public String generateQuestion(String role, String difficulty, int orderNo, String previousAnswer) {
        List<String> questions = BANK.getOrDefault(role, BANK.get("Software Engineer"));
        String base = questions.get(Math.min(Math.max(orderNo - 1, 0), questions.size() - 1));

        if (llmClient.isEnabled()) {
            try {
                String system = "You are a strict interview question generator for SMARTPREP. "
                        + "Return ONE interview question only. No preamble. Make it specific to role and difficulty. "
                        + "If previous answer is weak/vague, ask a sharper follow-up on the same topic.";
                String user = "Role: " + role + "\nDifficulty: " + difficulty + "\nQuestion number: " + orderNo
                        + "\nSeed topic: " + base
                        + "\nPrevious answer: " + (previousAnswer == null ? "(none)" : previousAnswer);
                return llmClient.chat(system, List.of(Map.of("role", "user", "content", user)));
            } catch (Exception ignored) {
                // fallback below
            }
        }

        String intensity = switch (difficulty.toUpperCase(Locale.ROOT)) {
            case "EASY" -> "Give a practical answer with one concrete example.";
            case "HARD" -> "Push into trade-offs, failure modes, and measurable impact.";
            default -> "Balance structure, technical depth, and a clear close.";
        };

        if (previousAnswer != null) {
            String prev = previousAnswer.toLowerCase(Locale.ROOT);
            if (prev.length() < 80 || containsAny(prev, "not sure", "idk", "don't know", "dont know")) {
                return "Follow-up: Your previous answer was too thin. " + base
                        + " Start with your approach in one sentence, then give one example. " + intensity;
            }
            if (!containsAny(prev, "because", "trade", "result", "impact", "example", "metric", "%")) {
                return "Follow-up on depth: " + base
                        + " This time include one trade-off and one measurable result. " + intensity;
            }
        }
        return base + " " + intensity;
    }

    public EvaluationResult evaluate(String question, String answer, String difficulty) {
        if (llmClient.isEnabled()) {
            try {
                String system = """
                        You are a warm but rigorous interview coach for SMARTPREP.
                        Teach the candidate: celebrate what was right, explain what was wrong, and show a better answer.
                        Return exactly this format (one field per line, no markdown):
                        SCORE: <0-10 integer>
                        VERDICT: <Correct | Partially correct | Incorrect>
                        STRENGTHS: <one sentence on what they did well>
                        IMPROVEMENTS: <one concrete fix>
                        WHY_RIGHT: <1-2 sentences: what was correct and why it matters>
                        WHY_WRONG: <1-2 sentences: what was missing/wrong and why that hurts the answer>
                        BETTER_ANSWER: <3-5 sentence outline of a stronger answer>
                        FEEDBACK: <2-3 coaching sentences referencing their actual content>
                        SPOKEN: <40-80 words, conversational spoken coaching. Use short sentences. Sound human, calm, and clear. Do not use bullet points, markdown, or labels.>
                        Be specific. Do not be generic.
                        """;
                String user = "Difficulty: " + difficulty + "\nQuestion: " + question + "\nAnswer: " + answer;
                String raw = llmClient.chat(system, List.of(Map.of("role", "user", "content", user)));
                return parseLlmEvaluation(raw, question, answer, difficulty);
            } catch (Exception ignored) {
                // fallback
            }
        }
        return evaluateLocal(question, answer, difficulty);
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

    private EvaluationResult evaluateLocal(String question, String answer, String difficulty) {
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
        String betterAnswer = "Start with your approach in one sentence, give one concrete example with a number, "
                + "name one trade-off, then close with the result. That structure scores consistently higher.";
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

    private EvaluationResult parseLlmEvaluation(String raw, String question, String answer, String difficulty) {
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
            return evaluateLocal(question, answer, difficulty);
        }

        EvaluationResult localFallback = null;
        if (whyRight == null || whyRight.isBlank() || whyWrong == null || whyWrong.isBlank()) {
            localFallback = evaluateLocal(question, answer, difficulty);
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
