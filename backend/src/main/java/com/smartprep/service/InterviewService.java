package com.smartprep.service;

import com.smartprep.domain.InterviewDomain;
import com.smartprep.domain.InterviewDomains;
import com.smartprep.dto.StartInterviewRequest;
import com.smartprep.dto.SubmitAnswerRequest;
import com.smartprep.entity.*;
import com.smartprep.repository.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InterviewService {
    private final InterviewSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final EvaluationRepository evaluationRepository;
    private final AiInterviewService aiInterviewService;

    public InterviewService(
            InterviewSessionRepository sessionRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            EvaluationRepository evaluationRepository,
            AiInterviewService aiInterviewService) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.evaluationRepository = evaluationRepository;
        this.aiInterviewService = aiInterviewService;
    }

    @Transactional
    public Map<String, Object> start(Long userId, StartInterviewRequest request) {
        InterviewDomain domain = requireDomain(request.getDomainId());
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setRole(domain.name());
        session.setDomainId(domain.id());
        session.setDifficulty(request.getDifficulty().toUpperCase(Locale.ROOT));
        session.setStatus("IN_PROGRESS");
        sessionRepository.save(session);

        Question question = createQuestion(session, 1, null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", session.getId());
        response.put("domainId", session.getDomainId());
        response.put("role", session.getRole());
        response.put("difficulty", session.getDifficulty());
        response.put("question", toQuestionMap(question));
        return response;
    }

    @Transactional
    public Map<String, Object> submitAnswer(Long userId, Long sessionId, SubmitAnswerRequest request) {
        InterviewSession session = requireOwnedSession(userId, sessionId);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session is not active");
        }

        Question question = questionRepository.findByIdAndSessionId(request.getQuestionId(), sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        if (answerRepository.findByQuestionId(question.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Question already answered");
        }

        Answer answer = new Answer();
        answer.setQuestionId(question.getId());
        answer.setAnswerText(request.getAnswerText().trim());
        answerRepository.save(answer);

        AiInterviewService.EvaluationResult result = aiInterviewService.evaluate(
                domainOf(session), question.getQuestionText(), answer.getAnswerText(), session.getDifficulty());

        Evaluation evaluation = new Evaluation();
        evaluation.setAnswerId(answer.getId());
        evaluation.setScore(result.score());
        evaluation.setFeedback(result.feedback());
        evaluation.setStrengths(result.strengths());
        evaluation.setImprovements(result.improvements());
        evaluation.setVerdict(result.verdict());
        evaluation.setWhyRight(result.whyRight());
        evaluation.setWhyWrong(result.whyWrong());
        evaluation.setBetterAnswer(result.betterAnswer());
        evaluation.setSpokenFeedback(result.spokenFeedback());
        evaluationRepository.save(evaluation);

        boolean completed = question.getOrderNo() >= aiInterviewService.questionsPerSession();

        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> evaluationMap = new LinkedHashMap<>();
        evaluationMap.put("score", evaluation.getScore());
        evaluationMap.put("feedback", evaluation.getFeedback());
        evaluationMap.put("strengths", evaluation.getStrengths());
        evaluationMap.put("improvements", evaluation.getImprovements());
        evaluationMap.put("verdict", evaluation.getVerdict());
        evaluationMap.put("whyRight", evaluation.getWhyRight());
        evaluationMap.put("whyWrong", evaluation.getWhyWrong());
        evaluationMap.put("betterAnswer", evaluation.getBetterAnswer());
        evaluationMap.put("spokenFeedback", evaluation.getSpokenFeedback());
        response.put("evaluation", evaluationMap);
        response.put("completed", completed);

        response.put("nextQuestion", null);
        if (completed) {
            finalizeSession(session);
        }
        return response;
    }

    @Transactional
    public Map<String, Object> nextQuestion(Long userId, Long sessionId) {
        InterviewSession session = requireOwnedSession(userId, sessionId);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session is not active");
        }

        List<Question> questions = questionRepository.findBySessionIdOrderByOrderNoAsc(sessionId);
        if (questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No question to continue from");
        }

        Question latest = questions.get(questions.size() - 1);
        if (answerRepository.findByQuestionId(latest.getId()).isEmpty()) {
            return questionResponse(latest);
        }
        if (latest.getOrderNo() >= aiInterviewService.questionsPerSession()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview is already complete");
        }

        String previousAnswer = answerRepository.findByQuestionId(latest.getId())
                .map(Answer::getAnswerText)
                .orElse(null);
        Question next = createQuestion(session, latest.getOrderNo() + 1, previousAnswer);
        return questionResponse(next);
    }

    @Transactional
    public Map<String, Object> end(Long userId, Long sessionId) {
        InterviewSession session = requireOwnedSession(userId, sessionId);
        finalizeSession(session);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", session.getId());
        result.put("status", session.getStatus());
        result.put("averageScore", session.getAverageScore());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> report(Long userId, Long sessionId) {
        InterviewSession session = requireOwnedSession(userId, sessionId);
        List<Question> questions = questionRepository.findBySessionIdOrderByOrderNoAsc(sessionId);
        List<Long> questionIds = questions.stream().map(Question::getId).toList();
        List<Answer> answers = answerRepository.findByQuestionIdIn(questionIds);
        Map<Long, Answer> answerByQuestion = new HashMap<>();
        for (Answer a : answers) {
            answerByQuestion.put(a.getQuestionId(), a);
        }
        List<Long> answerIds = answers.stream().map(Answer::getId).toList();
        Map<Long, Evaluation> evalByAnswer = new HashMap<>();
        for (Evaluation e : evaluationRepository.findByAnswerIdIn(answerIds)) {
            evalByAnswer.put(e.getAnswerId(), e);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Question q : questions) {
            Answer a = answerByQuestion.get(q.getId());
            if (a == null) continue;
            Evaluation e = evalByAnswer.get(a.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questionText", q.getQuestionText());
            row.put("answerText", a.getAnswerText());
            row.put("score", e != null ? e.getScore() : null);
            row.put("feedback", e != null ? e.getFeedback() : null);
            row.put("strengths", e != null ? e.getStrengths() : null);
            row.put("improvements", e != null ? e.getImprovements() : null);
            row.put("verdict", e != null ? e.getVerdict() : null);
            row.put("whyRight", e != null ? e.getWhyRight() : null);
            row.put("whyWrong", e != null ? e.getWhyWrong() : null);
            row.put("betterAnswer", e != null ? e.getBetterAnswer() : null);
            rows.add(row);
        }

        double avg = session.getAverageScore() != null
                ? session.getAverageScore()
                : rows.stream().filter(r -> r.get("score") != null).mapToInt(r -> (Integer) r.get("score")).average().orElse(0);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sessionId", session.getId());
        report.put("domainId", session.getDomainId());
        report.put("role", session.getRole());
        report.put("difficulty", session.getDifficulty());
        report.put("status", session.getStatus());
        report.put("averageScore", avg);
        report.put("questionCount", rows.size());
        report.put("answers", rows);
        report.put("upgradePlanTitle", "Personalized next steps");
        report.put("upgradePlan", aiInterviewService.upgradePlan(session.getRole(), avg));
        return report;
    }

    private Question createQuestion(InterviewSession session, int orderNo, String previousAnswer) {
        List<String> avoid = collectAvoidQuestions(session);
        Question question = new Question();
        question.setSessionId(session.getId());
        question.setOrderNo(orderNo);
        InterviewDomain domain = domainOf(session);
        question.setCategory(domain.id());
        question.setQuestionText(aiInterviewService.generateQuestion(
                domain, session.getDifficulty(), orderNo, previousAnswer, avoid));
        return questionRepository.save(question);
    }

    private List<String> collectAvoidQuestions(InterviewSession session) {
        List<String> avoid = new ArrayList<>();
        for (Question q : questionRepository.findBySessionIdOrderByOrderNoAsc(session.getId())) {
            if (q.getQuestionText() != null && !q.getQuestionText().isBlank()) {
                avoid.add(q.getQuestionText());
            }
        }

        List<InterviewSession> recent = sessionRepository.findByUserIdOrderByStartedAtDesc(session.getUserId());
        int counted = 0;
        for (InterviewSession prior : recent) {
            if (prior.getId().equals(session.getId())) continue;
            boolean sameTrack = session.getDomainId() != null
                    ? session.getDomainId().equalsIgnoreCase(prior.getDomainId())
                    : session.getRole().equalsIgnoreCase(prior.getRole());
            if (!sameTrack) continue;
            for (Question q : questionRepository.findBySessionIdOrderByOrderNoAsc(prior.getId())) {
                if (q.getQuestionText() != null && !q.getQuestionText().isBlank()) {
                    avoid.add(q.getQuestionText());
                }
            }
            counted++;
            if (counted >= 6) break;
        }
        return avoid;
    }

    private void finalizeSession(InterviewSession session) {
        List<Question> questions = questionRepository.findBySessionIdOrderByOrderNoAsc(session.getId());
        List<Long> questionIds = questions.stream().map(Question::getId).toList();
        List<Answer> answers = answerRepository.findByQuestionIdIn(questionIds);
        List<Long> answerIds = answers.stream().map(Answer::getId).toList();
        List<Evaluation> evaluations = evaluationRepository.findByAnswerIdIn(answerIds);
        double avg = evaluations.stream().mapToInt(Evaluation::getScore).average().orElse(0);
        session.setAverageScore(avg);
        session.setStatus("COMPLETED");
        session.setEndedAt(Instant.now());
        sessionRepository.save(session);
    }

    private InterviewDomain requireDomain(String domainId) {
        InterviewDomain domain = InterviewDomains.require(domainId);
        if (domain == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid interview domain");
        }
        return domain;
    }

    private InterviewDomain domainOf(InterviewSession session) {
        InterviewDomain domain = InterviewDomains.require(session.getDomainId());
        if (domain != null) return domain;
        return InterviewDomains.all().stream()
                .filter(item -> item.name().equalsIgnoreCase(session.getRole()))
                .findFirst()
                .orElseGet(() -> InterviewDomains.require("software-engineering"));
    }

    private InterviewSession requireOwnedSession(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }
        return session;
    }

    private Map<String, Object> questionResponse(Question question) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("question", toQuestionMap(question));
        response.put("questionIndex", question.getOrderNo());
        return response;
    }

    private Map<String, Object> toQuestionMap(Question question) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", question.getId());
        map.put("questionText", question.getQuestionText());
        map.put("category", question.getCategory());
        map.put("orderNo", question.getOrderNo());
        return map;
    }
}
