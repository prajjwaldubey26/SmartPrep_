package com.smartprep.domain;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class InterviewDomains {
    private InterviewDomains() {}

    private static final List<InterviewDomain> ALL = List.of(
            domain("software-engineering", "Technology", "Software Engineering", "💻",
                    "Coding depth, APIs, debugging, and system design trade-offs.",
                    List.of("Technical depth", "Edge cases", "System design", "Testing"),
                    "Ask for technical depth, edge cases, and system design. Do not drift into generic HR.",
                    List.of("debugging production incidents", "API contracts", "concurrency", "testing strategy"),
                    List.of(
                            "Design idempotent payment retries so a client timeout cannot double-charge the customer. What do you store, and what fails closed?",
                            "Walk through diagnosing intermittent 502s behind an API gateway. Which signals do you check first, and why?",
                            "Compare optimistic and pessimistic locking for seat booking. Give one case where each is the wrong choice.",
                            "How would you add feature flags to a high-traffic Java service without making rollback dangerous?")),
            domain("data-science", "Technology", "Data Science", "📈",
                    "Metrics, experiments, modeling judgment, and clear data stories.",
                    List.of("Experiment design", "Statistics", "Feature thinking", "Communication"),
                    "Ask experiment, statistics, and modeling-judgment questions. Require a metric and a caveat.",
                    List.of("experiment design", "causal inference", "feature leakage", "metric definition"),
                    List.of(
                            "A signup-to-paid funnel dropped between step 2 and 3. Walk through the investigation order and the first chart you would plot.",
                            "Design an A/B test for a new onboarding checklist. What is the primary metric, and what would make you stop the test early?",
                            "Explain a time a model looked accurate in offline eval and failed in production. What leakage or shift caused it?",
                            "How would you define a north-star metric for an interview-prep product, and which leading indicators would you distrust?")),
            domain("ai-ml", "Technology", "AI/ML", "🤖",
                    "Model choice, evaluation, failure modes, and production ML.",
                    List.of("Model evaluation", "Data leakage", "Latency vs quality", "Safety"),
                    "Ask about model choice, evaluation, failure modes, and production constraints. Avoid generic coding trivia.",
                    List.of("evaluation design", "hallucination risk", "latency budgets", "human feedback"),
                    List.of(
                            "You must score interview answers with an LLM. How do you evaluate the evaluator, and what failure would you refuse to ship?",
                            "When would you choose a smaller model over a larger one for a coaching product? Name the latency, cost, and quality trade-off.",
                            "A prompt change raised offline scores but users say feedback feels generic. How do you debug that?",
                            "How do you stop a coaching model from rewarding confident but wrong technical answers?")),
            domain("cloud-devops", "Technology", "Cloud/DevOps", "☁️",
                    "Delivery, reliability, infrastructure, and incident response.",
                    List.of("CI/CD", "Observability", "Reliability", "Cost"),
                    "Ask about delivery, reliability, incidents, and infrastructure trade-offs.",
                    List.of("incident response", "rollouts", "observability", "cost control"),
                    List.of(
                            "A deploy raised error rate from 0.2% to 2% for one endpoint. Walk through the first 15 minutes of the incident.",
                            "Design a rollback for a database migration that already wrote new columns. What must be backward compatible?",
                            "How would you alert on a Spring Boot API so on-call can tell user impact from noise?",
                            "Where would you spend the next reliability dollar: caching, multi-AZ, or better load tests? Defend the choice.")),
            domain("cybersecurity", "Technology", "Cybersecurity", "🔐",
                    "Threats, secure design, and how you respond when something breaks.",
                    List.of("Threat modeling", "Auth", "Incident response", "Least privilege"),
                    "Ask threat-modeling, auth, and incident questions. Push on what an attacker can actually do.",
                    List.of("threat modeling", "authentication", "secrets", "incident response"),
                    List.of(
                            "Threat-model a mock-interview app that stores answers and speaks them back. What is the highest-impact abuse?",
                            "A JWT signing key may have leaked. What do you rotate, revoke, and tell users, in order?",
                            "How would you design file upload for resumes so a malicious PDF cannot become remote code execution?",
                            "Explain least privilege for a CI job that deploys to production. What should that job never be able to read?")),
            domain("frontend", "Technology", "Frontend", "🎨",
                    "UI architecture, accessibility, performance, and state.",
                    List.of("Component design", "Accessibility", "Performance", "State management"),
                    "Ask about UI architecture, accessibility, performance, and state. Use a concrete screen, not a generic coding puzzle.",
                    List.of("accessibility", "rendering performance", "state design", "component API"),
                    List.of(
                            "The interview room re-renders the whole page on every partial speech transcript. How do you fix the jank without losing the live text?",
                            "How would you make a voice-coach screen usable with a keyboard and a screen reader?",
                            "Design the component state for a multi-step mock setup: domain, difficulty, and voice. What belongs in the URL?",
                            "A button shows no feedback while a slow sign-in request runs. What states do you design, and how do you block double submits?")),
            domain("product-management", "Business", "Product Management", "📊",
                    "Product sense, metrics, prioritization, and stakeholders.",
                    List.of("Product sense", "Metrics", "Prioritization", "Stakeholder management"),
                    "Ask product-sense, metrics, prioritization, and stakeholder questions. Force a decision and a metric.",
                    List.of("prioritization", "activation metrics", "stakeholder trade-offs", "MVP scope"),
                    List.of(
                            "Prioritize the next quarter for an AI mock-interview product. What do you cut, and which metric would prove you were right?",
                            "Should voice interviews ship before scoring explainability? Decide, and name the user you are willing to disappoint.",
                            "A stakeholder wants company-specific interview packs next week. How do you scope the MVP?",
                            "Activation is flat after onboarding changes. What would you instrument before proposing a redesign?")),
            domain("management-consulting", "Business", "Management Consulting", "🧭",
                    "Cases, market sizing, and structured recommendations.",
                    List.of("Case structure", "Market sizing", "Hypotheses", "Recommendation"),
                    "Ask case-study and market-sizing questions. Require a structure, a hypothesis, and a clear recommendation.",
                    List.of("market sizing", "profitability cases", "hypothesis trees", "recommendation"),
                    List.of(
                            "Estimate the annual revenue of a paid AI mock-interview product for Indian engineering students. State your assumptions out loud.",
                            "A campus placement firm's profit fell 20% while revenue was flat. Structure the case and name the first analysis.",
                            "A client wants to enter interview-coaching. Should they build, partner, or buy? Give a recommendation and the risk that would reverse it.",
                            "Market-size the corporate market for interview coaching in one country, then say what would make your estimate 2x too high.")),
            domain("finance-ib", "Business", "Finance/Investment Banking", "🏦",
                    "Valuation, markets, and how you explain a deal.",
                    List.of("Valuation", "Accounting", "Markets", "Deal judgment"),
                    "Ask valuation, accounting, markets, and deal-judgment questions. Require the drivers, not slogans.",
                    List.of("valuation", "accounting linkages", "deal rationale", "market awareness"),
                    List.of(
                            "Walk through how a $10 increase in depreciation flows through the three statements.",
                            "When would you use a DCF instead of trading comps for a high-growth software company, and where does each mislead?",
                            "A founder says their company is worth 20x revenue because a peer traded there last year. How do you push back?",
                            "Explain why an all-stock acquisition can be accretive and still be a bad deal.")),
            domain("sales", "Business", "Sales", "🤝",
                    "Discovery, objection handling, and closing with a real buyer.",
                    List.of("Discovery", "Objection handling", "Value selling", "Closing"),
                    "Ask discovery, objection-handling, and closing questions with a specific buyer. Do not accept feature lists.",
                    List.of("discovery questions", "objection handling", "multi-threading", "closing"),
                    List.of(
                            "A placement head says, 'Students already use free ChatGPT.' How do you discover whether that is a real objection?",
                            "You have 20 minutes with a VP who did not ask for this meeting. What do you ask before you pitch?",
                            "The champion likes the product and procurement has gone quiet. What do you do this week?",
                            "Handle this objection: 'We will pilot with five students and decide in six months.'")),
            domain("ux-ui-design", "Design & Marketing", "UX/UI Design", "✏️",
                    "Research, design thinking, and walking through a product decision.",
                    List.of("User research", "Design thinking", "Usability", "Portfolio walkthrough"),
                    "Focus on user research, design thinking, and portfolio walkthroughs. Ask what the user struggled with, not which tool was used.",
                    List.of("user research", "information architecture", "usability", "critique"),
                    List.of(
                            "Walk through a product flow you designed. What did the first usability test prove you were wrong about?",
                            "A candidate freezes on a voice interview screen. How would you redesign the first 10 seconds?",
                            "How do you decide what to put on an interview-setup screen when there are 19 domains?",
                            "Critique a dashboard that shows only an average score. What would you add so a student knows what to practice?")),
            domain("digital-marketing", "Design & Marketing", "Digital Marketing", "📣",
                    "Channels, experiments, and how you read campaign results.",
                    List.of("Channel strategy", "Experimentation", "Funnel metrics", "Positioning"),
                    "Ask channel, experiment, and funnel questions. Require a metric and a hypothesis.",
                    List.of("channel choice", "creative testing", "attribution", "positioning"),
                    List.of(
                            "CAC doubled on paid social while signup volume held. What do you check before you cut the channel?",
                            "Design a two-week test to see if 'practice out loud' outperforms 'get a score' as the homepage promise.",
                            "How would you explain last-click attribution lying about a campus-ambassador channel?",
                            "You can fund one channel for a student interview product: campus WhatsApp, search, or creators. Choose and say what would falsify it.")),
            domain("content-strategy", "Design & Marketing", "Content Strategy", "📝",
                    "Audience, narrative, and content that changes a decision.",
                    List.of("Audience", "Narrative", "Distribution", "Measurement"),
                    "Ask about audience, narrative, and distribution. A good answer names who the piece is for and what they should do next.",
                    List.of("audience definition", "narrative", "editorial judgment", "distribution"),
                    List.of(
                            "Outline a content series that gets a final-year student to complete one mock interview this week. Who is it not for?",
                            "A blog post has traffic and no signups. How do you decide whether the problem is topic, promise, or call to action?",
                            "How would you brief a writer on a system-design guide so it does not become generic advice?",
                            "What would you stop publishing, and what evidence would you need?")),
            domain("web3-blockchain", "Emerging", "Web3/Blockchain", "⛓️",
                    "Protocols, trust assumptions, and product trade-offs on-chain.",
                    List.of("Trust model", "Token design", "Security", "User experience"),
                    "Ask about trust assumptions, security, and user experience. Do not reward jargon without a threat or a user.",
                    List.of("trust assumptions", "wallet UX", "smart-contract risk", "token incentives"),
                    List.of(
                            "A user must sign a transaction to save an interview score. Why is that a bad default, and what would you put on-chain instead?",
                            "Explain how you would threat-model a token that rewards daily mock interviews. What behavior does it accidentally pay for?",
                            "Walk through what happens if a bridge used by your app pauses withdrawals. What does the product tell the user?",
                            "When is a signature enough, and when do you need a transaction? Give one product example of each.")),
            domain("climate-tech", "Emerging", "Climate Tech", "🌱",
                    "Climate impact, measurement, and shipping under real constraints.",
                    List.of("Impact measurement", "Systems thinking", "Policy constraints", "Go-to-market"),
                    "Ask about impact measurement, constraints, and go-to-market. Require a unit of impact, not a slogan.",
                    List.of("impact metrics", "MRV", "buyer constraints", "systems trade-offs"),
                    List.of(
                            "A carbon-accounting startup claims 'tons saved' from software recommendations. What would you need to see before you believed it?",
                            "Who is the buyer for industrial heat-pump monitoring, and what internal objection kills the deal?",
                            "Compare selling a climate product to a sustainability team versus the plant manager. What changes in the pitch?",
                            "A policy credit your model depends on might be repealed. How do you talk about that risk in a product interview?")),
            domain("healthtech", "Emerging", "HealthTech", "🩺",
                    "Clinical workflows, privacy, and products people trust with care.",
                    List.of("Clinical workflow", "Privacy", "Safety", "Adoption"),
                    "Ask about clinical workflow, privacy, safety, and adoption. Do not treat a hospital like a consumer app.",
                    List.of("clinical workflow", "privacy", "safety cases", "adoption"),
                    List.of(
                            "Design the first screen of a tool that drafts clinical notes. What must a clinician verify before anything is saved?",
                            "A founder wants to store interview-style patient answers to train a model. What privacy line would you refuse to cross?",
                            "Why might a nurse ignore an alert that engineering thinks is high priority? How would you find out?",
                            "Walk through a failure mode where a confident but wrong health recommendation reaches a patient. How do you prevent it?")),
            domain("executive-leadership", "Leadership", "Executive Leadership", "🏛️",
                    "Strategy, org decisions, and leading through ambiguity.",
                    List.of("Strategy", "Org design", "Decision quality", "Communication"),
                    "Ask strategy, org, and decision-quality questions. Push for the trade-off and who was accountable.",
                    List.of("strategy choices", "org design", "hard decisions", "communication"),
                    List.of(
                            "You have to cut one of three bets: new market, reliability, or a hiring surge. How do you decide and tell the team?",
                            "Describe a strategy you killed. What evidence made continuing more expensive than stopping?",
                            "Two senior leaders disagree in front of their teams. What do you do in the meeting, and what do you do after?",
                            "How do you know an organization is scaling process faster than it is scaling judgment?")),
            domain("hr-people-ops", "Leadership", "HR/People Ops", "👥",
                    "Hiring, performance, and building a fair people system.",
                    List.of("Hiring", "Performance", "Culture", "Employee relations"),
                    "Ask hiring, performance, culture, and employee-relations questions. Require a fair process, not a vibe.",
                    List.of("structured hiring", "performance", "difficult conversations", "policy"),
                    List.of(
                            "Design a hiring loop for engineers that reduces bias without making the loop endless. What do you standardize?",
                            "A manager wants to skip a performance plan and exit someone this week. How do you handle that conversation?",
                            "How would you measure whether a new leveling rubric is fair six months later?",
                            "Tell me how you would coach a manager whose team likes them and whose results are slipping.")),
            domain("customer-success", "Leadership", "Customer Success", "💬",
                    "Retention, expansion, and saving an account that is slipping.",
                    List.of("Retention", "Onboarding", "Expansion", "Executive alignment"),
                    "Ask retention, onboarding, and expansion questions about a specific account. Require a next step with an owner.",
                    List.of("retention risk", "onboarding", "executive alignment", "expansion"),
                    List.of(
                            "Usage dropped 40% at a logo that renewed last month. What do you do in the first two conversations?",
                            "Onboarding stalls because the champion cannot get IT to approve the integration. How do you unblock it?",
                            "When is an expansion conversation premature, and what evidence would make it appropriate?",
                            "An executive sponsor left. How do you rebuild access before the renewal is at risk?"))
    );

    private static final Map<String, InterviewDomain> BY_ID = ALL.stream()
            .collect(Collectors.toMap(InterviewDomain::id, Function.identity()));

    public static List<InterviewDomain> all() {
        return ALL;
    }

    public static InterviewDomain require(String id) {
        if (id == null) return null;
        return BY_ID.get(id.trim());
    }

    private static InterviewDomain domain(
            String id,
            String group,
            String name,
            String icon,
            String description,
            List<String> focusAreas,
            String questionStyle,
            List<String> angles,
            List<String> fallbackQuestions) {
        return new InterviewDomain(id, group, name, icon, description, focusAreas, questionStyle, fallbackQuestions, angles);
    }
}
