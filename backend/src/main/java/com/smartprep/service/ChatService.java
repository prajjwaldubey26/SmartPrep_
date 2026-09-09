package com.smartprep.service;

import com.smartprep.dto.ChatRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String SYSTEM = """
            You are SMARTPREP Coach, a friendly and elite interview coach for technical, product, data, and behavioral rounds.
            Personality:
            - Warm and conversational like a great mentor chat, especially on greetings (hi/hello/hey).
            - On greetings, reply naturally in 2-4 sentences, introduce yourself briefly, then offer 2-3 concrete next prompts.
            Rules:
            - Never give vague advice for prep questions. Include frameworks, sample phrasing, and next actions.
            - Tailor to the user's exact question and role if mentioned.
            - Prefer short sections with bullets and one mini example answer when coaching.
            - If the user asks for a sample answer, write a full 45-90 second spoken answer.
            - Push for measurable impact, trade-offs, and interviewer expectations.
            - Do not mention that you are a language model or NVIDIA/OpenAI.
            """;

    private final LlmClient llmClient;

    public ChatService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public Map<String, String> reply(ChatRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isBlank()) {
            return Map.of("reply", "Ask a specific interview question, role, or weak area. Example: \"Give me a STAR answer for conflict with a teammate as an SDE.\"");
        }

        if (llmClient.isEnabled()) {
            try {
                List<Map<String, String>> messages = new ArrayList<>();
                if (request.getHistory() != null) {
                    for (ChatRequest.ChatMessage m : request.getHistory()) {
                        if (m == null || m.getContent() == null || m.getContent().isBlank()) continue;
                        String role = "assistant".equalsIgnoreCase(m.getRole()) ? "assistant" : "user";
                        messages.add(Map.of("role", role, "content", m.getContent()));
                    }
                }
                messages.add(Map.of("role", "user", "content", message));
                return Map.of("reply", llmClient.chat(SYSTEM, messages));
            } catch (Exception ex) {
                log.error("Live AI chat failed, using local coach fallback: {}", ex.getMessage());
            }
        }

        return Map.of("reply", craftLocal(message));
    }

    private String craftLocal(String original) {
        String lower = original.toLowerCase(Locale.ROOT);
        String role = detectRole(lower);
        boolean wantsSample = containsAny(lower, "sample", "example answer", "script", "how would you answer", "give me an answer", "mock answer");
        boolean wantsCritique = containsAny(lower, "review", "critique", "improve this", "feedback on");

        if (wantsCritique) {
            return critiqueAnswer(original, role);
        }
        if (containsAny(lower, "star", "behavioral", "hr", "conflict", "failure", "leadership", "tell me about a time")) {
            return behavioralCoach(original, role, wantsSample);
        }
        if (containsAny(lower, "system design", "design a", "url shortener", "scalability", "architecture", "rate limit", "cache")) {
            return systemDesignCoach(original, role, wantsSample);
        }
        if (containsAny(lower, "coding", "dsa", "algorithm", "leetcode", "array", "tree", "graph", "complexity", "optimize")) {
            return codingCoach(original, role, wantsSample);
        }
        if (containsAny(lower, "sql", "dashboard", "metric", "funnel", "cohort", "ab test", "data analyst", "analytics")) {
            return dataCoach(original, role, wantsSample);
        }
        if (containsAny(lower, "product", "prioritize", "roadmap", "stakeholder", "north star", "onboarding")) {
            return productCoach(original, role, wantsSample);
        }
        if (containsAny(lower, "nervous", "anxiety", "confidence", "freeze", "blank")) {
            return confidenceCoach(role);
        }
        if (containsAny(lower, "prepare", "roadmap", "plan", "week", "study", "schedule")) {
            return studyPlan(role);
        }
        if (containsAny(lower, "hello", "hi", "hey", "help")) {
            return "I coach specific interview moments, not generic tips.\n"
                    + "Try one of these:\n"
                    + "- \"Sample STAR answer for missed deadline (" + role + ")\"\n"
                    + "- \"Walk me through designing a notification system\"\n"
                    + "- \"Critique this answer: ...\"\n"
                    + "- \"7-day prep plan for " + role + "\"";
        }

        return deepFallback(original, role);
    }

    private String behavioralCoach(String original, String role, boolean sample) {
        String theme = detectBehavioralTheme(original.toLowerCase(Locale.ROOT));
        StringBuilder sb = new StringBuilder();
        sb.append("Target: ").append(role).append(" behavioral / HR\n");
        sb.append("Theme detected: ").append(theme).append("\n\n");
        sb.append("Interviewer wants evidence of ownership, judgment, and learning - not a story dump.\n\n");
        sb.append("Answer skeleton (45-70 sec):\n");
        sb.append("1) Situation (10%) - one concrete context\n");
        sb.append("2) Task (10%) - your responsibility, not the team's\n");
        sb.append("3) Action (60%) - 3 decisions you made + why\n");
        sb.append("4) Result (20%) - metric + what you changed afterward\n\n");
        sb.append("Must include:\n");
        sb.append("- Your personal verbs (I investigated / I proposed / I negotiated)\n");
        sb.append("- One trade-off you considered\n");
        sb.append("- One quantified outcome (time, %, bugs, revenue, CSAT)\n\n");
        if (sample) {
            sb.append("Sample answer (").append(theme).append("):\n");
            sb.append(sampleBehavioral(theme, role)).append("\n\n");
        } else {
            sb.append("Prompt to practice now:\n\"");
            sb.append(practicePrompt(theme, role)).append("\"\n\n");
            sb.append("Reply with your draft and I'll score it line-by-line.");
        }
        return sb.toString();
    }

    private String systemDesignCoach(String original, String role, boolean sample) {
        String topic = extractDesignTopic(original);
        StringBuilder sb = new StringBuilder();
        sb.append("System design drill: ").append(topic).append("\n");
        sb.append("Role lens: ").append(role).append("\n\n");
        sb.append("Open with clarification questions first:\n");
        sb.append("- Read vs write ratio?\n");
        sb.append("- Latency SLO?\n");
        sb.append("- Expected QPS / storage growth?\n");
        sb.append("- Consistency vs availability preference?\n\n");
        sb.append("Then present in this order:\n");
        sb.append("1) Requirements (functional + non-functional)\n");
        sb.append("2) Back-of-envelope estimates\n");
        sb.append("3) API + data model\n");
        sb.append("4) High-level components\n");
        sb.append("5) Deep dive on the hardest bottleneck\n");
        sb.append("6) Failure modes + observability\n\n");
        sb.append("For ").append(topic).append(", prioritize these talking points:\n");
        for (String point : designPoints(topic)) {
            sb.append("- ").append(point).append("\n");
        }
        sb.append("\nStrong closer: \"The main trade-off I chose is X because Y; if traffic shifts to Z I'd revisit W.\"\n");
        if (sample) {
            sb.append("\n60-second opening script:\n");
            sb.append("\"I'd clarify scale and SLOs first. Assuming ~").append(assumptions(topic));
            sb.append(" I'd split the design into write path, read path, and metadata. ");
            sb.append("Core components: API gateway, app servers, primary store, cache, and async workers for secondary work. ");
            sb.append("The riskiest part is ").append(riskiest(topic)).append(", so I'd protect it with ");
            sb.append(mitigation(topic)).append(".\"");
        } else {
            sb.append("\nSend your high-level diagram in text and I'll pressure-test the weak points.");
        }
        return sb.toString();
    }

    private String codingCoach(String original, String role, boolean sample) {
        StringBuilder sb = new StringBuilder();
        sb.append("Coding interview coach (").append(role).append(")\n\n");
        sb.append("Use this spoken loop every time:\n");
        sb.append("1) Restate + constraints + examples\n");
        sb.append("2) Brute force idea + complexity\n");
        sb.append("3) Optimized approach + why it works\n");
        sb.append("4) Code in small chunks while narrating invariants\n");
        sb.append("5) Dry-run + edge cases + complexity recap\n\n");
        sb.append("Edge cases interviewers listen for: empty input, duplicates, negatives, overflow, single-element, already sorted.\n\n");
        if (containsAny(original.toLowerCase(Locale.ROOT), "two sum", "array", "hash")) {
            sb.append("Likely pattern: Hash map for O(n) lookups.\n");
            sb.append("Talk track: \"I'll store value->index while scanning; for each x check if target-x exists.\"\n");
        } else if (containsAny(original.toLowerCase(Locale.ROOT), "tree", "bst", "binary")) {
            sb.append("Likely pattern: DFS/BFS with recursion stack or queue.\n");
            sb.append("Talk track: \"I'll define the recursive contract first, then handle null base case.\"\n");
        } else if (containsAny(original.toLowerCase(Locale.ROOT), "graph", "bfs", "dfs", "shortest")) {
            sb.append("Likely pattern: BFS for shortest unweighted path; Dijkstra if weighted.\n");
        } else {
            sb.append("If stuck: classify as array/hash, two pointers, sliding window, tree/graph, DP, or heap.\n");
        }
        sb.append("\nRed flags to avoid: silent coding, no complexity, no tests, mutable shared state without explaining.\n");
        if (sample) {
            sb.append("\nSample opener:\n");
            sb.append("\"Before coding, I want to confirm inputs/outputs and constraints. ");
            sb.append("My first approach would be brute force at O(n^2). ");
            sb.append("I can improve it using ... which brings average time to O(n) with O(n) space. ");
            sb.append("I'll implement that and then test empty, single, and duplicate cases.\"");
        } else {
            sb.append("\nPaste the problem statement and your current approach - I'll tell you what an interviewer would challenge next.");
        }
        return sb.toString();
    }

    private String dataCoach(String original, String role, boolean sample) {
        StringBuilder sb = new StringBuilder();
        sb.append("Data / analytics interview coach\n");
        sb.append("Role: ").append(role).append("\n\n");
        sb.append("Framework: Metric -> Segment -> Diagnose -> Decide\n");
        sb.append("1) Define the decision the metric should drive\n");
        sb.append("2) Write the exact formula and grain (user-day, session, order)\n");
        sb.append("3) Segment by cohort, channel, device, new vs returning\n");
        sb.append("4) Separate product bug vs behavior shift vs seasonality\n");
        sb.append("5) Recommend one action + how you'll measure success\n\n");
        if (containsAny(original.toLowerCase(Locale.ROOT), "funnel")) {
            sb.append("Funnel drop investigation order:\n");
            sb.append("- Tracking integrity first (event missing / double counting)\n");
            sb.append("- Then UX friction at the drop step\n");
            sb.append("- Then audience mix change\n");
            sb.append("- Then experiment / release correlation\n");
        }
        if (sample) {
            sb.append("\nSample 45-sec answer:\n");
            sb.append("\"I'd first verify event tracking for step 2 and 3. If instrumentation is clean, I'd compare conversion by segment and release window. ");
            sb.append("If one segment tanked, I'd inspect that journey; if all segments tanked after a deploy, I'd roll back or feature-flag. ");
            sb.append("I'd close with a recommended fix and a 7-day holdout metric.\"");
        }
        return sb.toString();
    }

    private String productCoach(String original, String role, boolean sample) {
        return String.join("\n",
                "Product interview coach (" + role + ")",
                "",
                "Use RICE + risk when prioritizing:",
                "- Reach: who is affected this quarter",
                "- Impact: what changes in retention/activation/revenue",
                "- Confidence: evidence quality",
                "- Effort: eng weeks",
                "",
                "Always say what you will NOT build and why.",
                sample
                        ? "Sample closer: \"I'd ship adaptive follow-ups before social features because it directly improves mock quality, our core retention loop.\""
                        : "Tell me the feature list and constraints - I'll force-rank with a PM-style narrative.");
    }

    private String confidenceCoach(String role) {
        return String.join("\n",
                "Confidence under pressure (" + role + ")",
                "",
                "Use a recovery script when you blank:",
                "1) \"Let me structure this for 10 seconds.\"",
                "2) Write 3 bullets: context / approach / trade-off",
                "3) Speak the first bullet only, then expand",
                "",
                "Daily micro-drill (8 minutes):",
                "- 1 behavioral prompt aloud",
                "- 1 technical prompt outline only",
                "- Record and cut filler words",
                "",
                "Do not memorize paragraphs. Memorize structures.");
    }

    private String studyPlan(String role) {
        return String.join("\n",
                "7-day precision plan for " + role,
                "",
                "Day 1: Build 5 STAR stories with metrics",
                "Day 2: Role fundamentals flash drills (20 prompts, outline-only)",
                "Day 3: Timed coding/system/product block (role-specific)",
                "Day 4: Full SMARTPREP mock + note top 3 weaknesses",
                "Day 5: Only weak-area reps (no new topics)",
                "Day 6: Second full mock at harder difficulty",
                "Day 7: Light review + delivery polish (pace, clarity, closers)",
                "",
                "Success criteria: each answer has structure + one metric/trade-off + clean close.",
                "If you tell me years of experience and target company type, I'll specialize this further.");
    }

    private String critiqueAnswer(String original, String role) {
        String answer = original;
        int idx = Math.max(original.toLowerCase(Locale.ROOT).indexOf("critique"),
                Math.max(original.toLowerCase(Locale.ROOT).indexOf("review"),
                        original.toLowerCase(Locale.ROOT).indexOf("improve this")));
        if (idx >= 0) {
            int colon = original.indexOf(':', idx);
            if (colon > 0 && colon < original.length() - 1) {
                answer = original.substring(colon + 1).trim();
            }
        }
        int words = answer.isBlank() ? 0 : answer.split("\\s+").length;
        String lower = answer.toLowerCase(Locale.ROOT);
        boolean hasMetric = containsAny(lower, "%", "percent", "increased", "reduced", "by ", "ms", "qps", "users");
        boolean hasStructure = containsAny(lower, "first", "then", "because", "result", "impact", "trade");
        boolean hasOwnership = lower.startsWith("i ") || lower.startsWith("i'") || containsAny(lower, " i ", " i'", " my ");

        List<String> gaps = new ArrayList<>();
        if (words < 60) gaps.add("Too thin for an interview answer - expand Actions with 2-3 concrete steps.");
        if (!hasOwnership) gaps.add("Ownership is unclear - rewrite with I-verbs.");
        if (!hasStructure) gaps.add("Structure is weak - add explicit Situation/Action/Result or Approach/Trade-off.");
        if (!hasMetric) gaps.add("No measurable outcome - add a number or observable result.");

        String score;
        int points = 4;
        if (words >= 80) points++;
        if (hasOwnership) points++;
        if (hasStructure) points += 2;
        if (hasMetric) points += 2;
        points = Math.min(10, points);
        score = points + "/10";

        StringBuilder sb = new StringBuilder();
        sb.append("Line-by-line coach review (").append(role).append(")\n");
        sb.append("Draft score: ").append(score).append("\n\n");
        if (gaps.isEmpty()) {
            sb.append("This is already strong. Tighten the closer and cut filler.\n");
        } else {
            sb.append("Priority fixes:\n");
            for (int i = 0; i < gaps.size(); i++) {
                sb.append(i + 1).append(") ").append(gaps.get(i)).append("\n");
            }
        }
        sb.append("\nRewrite formula:\n");
        sb.append("\"In [context], I owned [task]. I did A, then B, and chose C over D because E. As a result, [metric]. Next time I'd [learning].\"\n");
        sb.append("\nPaste a revised version and I'll re-score it.");
        return sb.toString();
    }

    private String deepFallback(String original, String role) {
        return String.join("\n",
                "I need a sharper ask to coach precisely for " + role + ".",
                "You asked: \"" + shorten(original, 120) + "\"",
                "",
                "Pick one mode:",
                "1) \"Sample answer for: <interview question>\"",
                "2) \"Critique this answer: <paste>\"",
                "3) \"Design drill for: <system/product/data topic>\"",
                "4) \"Prep plan for <role>, <experience>, <timeline>\"",
                "",
                "The more specific your prompt, the more specific my drill.");
    }

    private String detectRole(String lower) {
        if (containsAny(lower, "data analyst", "analytics", "sql")) return "Data Analyst";
        if (containsAny(lower, "product manager", "pm interview", "product ")) return "Product Manager";
        if (containsAny(lower, "system design")) return "System Design";
        if (containsAny(lower, "hr", "behavioral")) return "HR / Behavioral";
        if (containsAny(lower, "sde", "software", "backend", "frontend", "fullstack")) return "Software Engineer";
        return "Software Engineer";
    }

    private String detectBehavioralTheme(String lower) {
        if (containsAny(lower, "conflict", "disagreement")) return "conflict";
        if (containsAny(lower, "fail", "mistake", "wrong")) return "failure";
        if (containsAny(lower, "lead", " mentorship", "owned")) return "leadership";
        if (containsAny(lower, "deadline", "pressure", "urgent")) return "pressure";
        if (containsAny(lower, "feedback")) return "feedback";
        return "ownership";
    }

    private String sampleBehavioral(String theme, String role) {
        return switch (theme) {
            case "conflict" -> "\"On a " + role + " project, a teammate and I disagreed on shipping a shortcut that skipped tests. "
                    + "My task was to protect release quality without blocking the date. I mapped the risk, proposed a feature-flagged rollout with a 1-day test debt plan, "
                    + "and aligned with the tech lead. We shipped on time and caught a null-pointer in staging that would have hit ~12% of sessions. "
                    + "I learned to convert conflicts into shared risk metrics instead of preference debates.\"";
            case "failure" -> "\"I once shipped an API change that broke a client pagination contract. I owned the incident, wrote the timeline, rolled back in 18 minutes, "
                    + "and added contract tests plus a checklist for breaking changes. Repeat incidents of that class dropped to zero the next quarter.\"";
            default -> "\"When requirements were ambiguous for a " + role + " deliverable, I drafted assumptions, got stakeholder sign-off in writing, "
                    + "and delivered an MVP in two weeks that lifted activation by 9%. The key was making uncertainty explicit early.\"";
        };
    }

    private String practicePrompt(String theme, String role) {
        return switch (theme) {
            case "conflict" -> "Tell me about a time you disagreed with a teammate on a " + role + " decision.";
            case "failure" -> "Describe a failure in your " + role + " work and what you changed afterward.";
            case "pressure" -> "Tell me about delivering under a tight deadline as a " + role + ".";
            default -> "Give an example of ownership when requirements were unclear in a " + role + " project.";
        };
    }

    private String extractDesignTopic(String original) {
        String lower = original.toLowerCase(Locale.ROOT);
        if (lower.contains("url shortener")) return "URL shortener";
        if (lower.contains("notification")) return "notification system";
        if (lower.contains("chat")) return "chat/messaging service";
        if (lower.contains("rate limit")) return "rate limiter";
        if (lower.contains("feed")) return "news feed";
        if (lower.contains("interview")) return "interview session platform";
        Matcher m = Pattern.compile("design (?:a |an |the )?([a-z0-9 \\-/]{3,60})", Pattern.CASE_INSENSITIVE).matcher(original);
        if (m.find()) return m.group(1).trim();
        return "the system in your question";
    }

    private List<String> designPoints(String topic) {
        String t = topic.toLowerCase(Locale.ROOT);
        if (t.contains("url")) {
            return List.of("Base62 encoding vs hash collisions", "301/302 redirect strategy", "hot-key caching", "custom alias uniqueness", "analytics async pipeline");
        }
        if (t.contains("notification")) {
            return List.of("fan-out on write vs read", "provider failover", "dedupe keys", "user preference store", "retry/backoff and DLQ");
        }
        if (t.contains("rate")) {
            return List.of("token bucket vs sliding window", "per-user vs per-IP keys", "redis centralization trade-offs", "burst handling", "fairness under attack");
        }
        return List.of("clear read/write paths", "data partitioning strategy", "cache invalidation", "backpressure under spikes", "idempotency for retries");
    }

    private String assumptions(String topic) {
        return topic.toLowerCase(Locale.ROOT).contains("url") ? "100M new links/month and heavy read traffic"
                : "medium write volume with bursty reads and a 200ms p95 target";
    }

    private String riskiest(String topic) {
        return topic.toLowerCase(Locale.ROOT).contains("url") ? "hot short links overwhelming storage"
                : "the consistency of the write path under retries";
    }

    private String mitigation(String topic) {
        return topic.toLowerCase(Locale.ROOT).contains("url") ? "caching + key partitioning + async analytics"
                : "idempotency keys, queues, and explicit retry budgets";
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private String shorten(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "...";
    }
}
