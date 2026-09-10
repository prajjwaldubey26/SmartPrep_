package com.smartprep.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "evaluations")
public class Evaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "answer_id", nullable = false, unique = true)
    private Long answerId;

    @Column(nullable = false)
    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    @Column(length = 40)
    private String verdict;

    @Column(name = "why_right", columnDefinition = "TEXT")
    private String whyRight;

    @Column(name = "why_wrong", columnDefinition = "TEXT")
    private String whyWrong;

    @Column(name = "better_answer", columnDefinition = "TEXT")
    private String betterAnswer;

    @Column(name = "spoken_feedback", columnDefinition = "TEXT")
    private String spokenFeedback;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAnswerId() { return answerId; }
    public void setAnswerId(Long answerId) { this.answerId = answerId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getImprovements() { return improvements; }
    public void setImprovements(String improvements) { this.improvements = improvements; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public String getWhyRight() { return whyRight; }
    public void setWhyRight(String whyRight) { this.whyRight = whyRight; }
    public String getWhyWrong() { return whyWrong; }
    public void setWhyWrong(String whyWrong) { this.whyWrong = whyWrong; }
    public String getBetterAnswer() { return betterAnswer; }
    public void setBetterAnswer(String betterAnswer) { this.betterAnswer = betterAnswer; }
    public String getSpokenFeedback() { return spokenFeedback; }
    public void setSpokenFeedback(String spokenFeedback) { this.spokenFeedback = spokenFeedback; }
}
