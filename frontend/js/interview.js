let sessionId = null;
let currentQuestion = null;
let questionIndex = 1;
let timerSeconds = 0;
let timerHandle = null;
let voiceEnabled = true;
let lastSpokenFeedback = "";
let advancing = false;
let isEvaluating = false;
let interviewComplete = false;
let queuedQuestion = null;
let selectedDomainId = "software-engineering";
const EVAL_SLOW_MS = 10000;

document.addEventListener("DOMContentLoaded", async () => {
  if (!Auth.requireAuth()) return;

  renderDomainPicker();
  document.getElementById("startForm").addEventListener("submit", onStart);
  document.getElementById("submitAnswerBtn").addEventListener("click", onSubmitAnswer);
  document.getElementById("nextQuestionBtn").addEventListener("click", onNextQuestion);
  document.getElementById("endBtn").addEventListener("click", onEndSession);
  document.getElementById("micBtn").addEventListener("click", onMicToggle);
  document.getElementById("replayQuestionBtn").addEventListener("click", () => speakCurrentQuestion(false));
  document.getElementById("stopVoiceBtn").addEventListener("click", () => {
    VoiceCoach.stopListening();
    VoiceCoach.stopSpeaking();
    setStatus(VoiceCoach.isListening() ? "Listening" : "Ready");
  });
  document.getElementById("replayFeedbackBtn").addEventListener("click", () => {
    if (lastSpokenFeedback) VoiceCoach.speak(lastSpokenFeedback);
  });
  document.getElementById("voiceEnabled").addEventListener("change", (e) => {
    voiceEnabled = e.target.checked;
    document.getElementById("voiceDock").classList.toggle("voice-dock-disabled", !voiceEnabled);
  });

  VoiceCoach.on("onListeningChange", (active) => {
    const micBtn = document.getElementById("micBtn");
    const micLabel = document.getElementById("micLabel");
    const orb = document.getElementById("roomOrb");
    micBtn.classList.toggle("is-listening", active);
    micBtn.setAttribute("aria-pressed", active ? "true" : "false");
    micLabel.textContent = active ? "Listening… tap to stop" : "Tap to speak";
    orb.classList.toggle("orb-listening", active);
    if (active) setStatus("Listening");
  });

  VoiceCoach.on("onPartial", (text) => {
    document.getElementById("answer").value = text;
    document.getElementById("voiceLiveLine").textContent = text || "Hearing you…";
  });

  VoiceCoach.on("onTranscript", (text) => {
    document.getElementById("answer").value = text;
    document.getElementById("voiceLiveLine").textContent = "Answer captured. Submit when ready.";
  });

  VoiceCoach.on("onSpeakStart", () => {
    document.getElementById("roomOrb").classList.add("orb-speaking");
    setStatus("Coach speaking");
  });

  VoiceCoach.on("onSpeakEnd", () => {
    document.getElementById("roomOrb").classList.remove("orb-speaking");
    if (!VoiceCoach.isListening() && !advancing) setStatus("Your turn");
  });

  VoiceCoach.on("onError", (message) => {
    const roomError = document.getElementById("roomError");
    if (!document.getElementById("roomView").classList.contains("hidden")) {
      roomError.textContent = message;
      roomError.classList.remove("hidden");
    }
  });

  const info = await VoiceCoach.init();
  const hint = document.getElementById("voiceProviderHint");
  if (!info.sttSupported) {
    hint.textContent = "Mic dictation needs Chrome/Edge. Voice playback still works.";
  } else {
    hint.textContent = `Voice engine: ${VoiceCoach.getProviderLabel()}`;
  }
});

function renderDomainPicker() {
  const picker = document.getElementById("domainPicker");
  const hidden = document.getElementById("domainId");
  const domains = window.SMARTPREP_DOMAINS || [];
  const groups = [];
  domains.forEach((domain) => {
    if (!groups.includes(domain.group)) groups.push(domain.group);
  });
  picker.innerHTML = "";
  groups.forEach((group) => {
    const section = document.createElement("section");
    const label = document.createElement("p");
    label.className = "domain-group-label";
    label.textContent = group;
    const grid = document.createElement("div");
    grid.className = "domain-grid";
    domains.filter((domain) => domain.group === group).forEach((domain) => {
      const card = document.createElement("button");
      card.type = "button";
      card.className = "domain-card" + (domain.id === selectedDomainId ? " is-selected" : "");
      card.setAttribute("role", "option");
      card.setAttribute("aria-selected", domain.id === selectedDomainId ? "true" : "false");
      card.innerHTML =
        `<span class="domain-card-name"></span>` +
        `<span class="domain-card-desc"></span>`;
      card.querySelector(".domain-card-name").textContent = domain.name;
      card.querySelector(".domain-card-desc").textContent = domain.description;
      card.addEventListener("click", () => {
        selectedDomainId = domain.id;
        hidden.value = domain.id;
        picker.querySelectorAll(".domain-card").forEach((node) => {
          const on = node === card;
          node.classList.toggle("is-selected", on);
          node.setAttribute("aria-selected", on ? "true" : "false");
        });
      });
      grid.appendChild(card);
    });
    section.appendChild(label);
    section.appendChild(grid);
    picker.appendChild(section);
  });
  hidden.value = selectedDomainId;
}

async function onStart(e) {
  e.preventDefault();
  const setupError = document.getElementById("setupError");
  setupError.classList.add("hidden");
  voiceEnabled = document.getElementById("voiceEnabled").checked;

  const domainId = document.getElementById("domainId").value;
  const domain = (window.SMARTPREP_DOMAINS || []).find((item) => item.id === domainId);
  const difficulty = document.getElementById("difficulty").value;
  if (!domain) {
    setupError.textContent = "Choose a domain before starting.";
    setupError.classList.remove("hidden");
    return;
  }

  try {
    setStatus("Starting…");
    const data = await Api.post("/interviews/start", {
      role: domain.name,
      domainId,
      difficulty,
    });
    sessionId = data.sessionId;
    currentQuestion = data.question;
    questionIndex = 1;

    document.getElementById("setupView").classList.add("hidden");
    document.getElementById("roomView").classList.remove("hidden");
    document.getElementById("roleLabel").textContent = data.role || domain.name;
    document.getElementById("voiceDock").classList.toggle("voice-dock-disabled", !voiceEnabled);
    renderQuestion(currentQuestion);
    startTimer();
    setStatus("Your turn");
    await speakCurrentQuestion(true);
  } catch (error) {
    setupError.textContent = error.message;
    setupError.classList.remove("hidden");
    setStatus("Ready");
  }
}

async function onSubmitAnswer() {
  if (isEvaluating) return;
  const roomError = document.getElementById("roomError");
  roomError.classList.add("hidden");
  VoiceCoach.stopListening();
  VoiceCoach.stopSpeaking();

  const answer = document.getElementById("answer").value.trim();
  if (!answer) {
    roomError.textContent = "Please speak or write an answer before submitting.";
    roomError.classList.remove("hidden");
    return;
  }

  isEvaluating = true;
  advancing = true;
  queuedQuestion = null;
  interviewComplete = false;
  setEvaluating(true, "Evaluating your answer...");
  const slowTimer = setTimeout(() => {
    if (isEvaluating) {
      setEvaluating(true, "Still analyzing your answer. This can take a moment.");
    }
  }, EVAL_SLOW_MS);

  try {
    setStatus("Evaluating…");
    const data = await Api.post(`/interviews/${sessionId}/answer`, {
      questionId: currentQuestion.id,
      answerText: answer,
    });

    showFeedback(data.evaluation);
    document.getElementById("answer").value = "";
    document.getElementById("voiceLiveLine").textContent = "Review the feedback, then continue when you are ready.";
    interviewComplete = Boolean(data.completed);
    queuedQuestion = data.nextQuestion || null;
    revealNextAction();

    if (voiceEnabled && data.evaluation) {
      lastSpokenFeedback =
        data.evaluation.spokenFeedback ||
        buildFallbackSpeech(data.evaluation);
      VoiceCoach.speak(lastSpokenFeedback);
    }

    setStatus(interviewComplete ? "Review feedback" : "Feedback ready");
  } catch (error) {
    roomError.textContent = error.message;
    roomError.classList.remove("hidden");
    setStatus("Your turn");
  } finally {
    clearTimeout(slowTimer);
    isEvaluating = false;
    advancing = false;
    setEvaluating(false);
  }
}

async function onNextQuestion() {
  const nextBtn = document.getElementById("nextQuestionBtn");
  const roomError = document.getElementById("roomError");
  if (isEvaluating || nextBtn.disabled) return;
  roomError.classList.add("hidden");
  VoiceCoach.stopListening();
  VoiceCoach.stopSpeaking();

  if (interviewComplete) {
    stopTimer();
    window.location.href = `results.html?sessionId=${sessionId}`;
    return;
  }

  nextBtn.disabled = true;
  try {
    let question = queuedQuestion;
    if (!question) {
      setStatus("Next question…");
      const data = await Api.post(`/interviews/${sessionId}/next`, {});
      question = data.question;
      if (data.questionIndex) questionIndex = data.questionIndex;
      else questionIndex += 1;
    } else {
      questionIndex += 1;
    }
    queuedQuestion = null;
    currentQuestion = question;
    renderQuestion(currentQuestion);
    setStatus("Your turn");
    await speakCurrentQuestion(true);
  } catch (error) {
    roomError.textContent = error.message;
    roomError.classList.remove("hidden");
    nextBtn.disabled = false;
    setStatus("Feedback ready");
  }
}

function setEvaluating(active, message) {
  const status = document.getElementById("evalStatus");
  const submitBtn = document.getElementById("submitAnswerBtn");
  const nextBtn = document.getElementById("nextQuestionBtn");
  status.classList.toggle("hidden", !active);
  if (message) document.getElementById("evalStatusText").textContent = message;
  submitBtn.disabled = active;
  if (active) nextBtn.classList.add("hidden");
}

function revealNextAction() {
  const nextBtn = document.getElementById("nextQuestionBtn");
  const label = document.getElementById("nextQuestionLabel");
  label.textContent = interviewComplete ? "Finish Interview" : "Next Question";
  nextBtn.disabled = false;
  nextBtn.classList.remove("hidden");
}

async function onEndSession() {
  if (!sessionId) return;
  try {
    VoiceCoach.stopListening();
    VoiceCoach.stopSpeaking();
    setStatus("Wrapping up…");
    await Api.post(`/interviews/${sessionId}/end`, {});
    stopTimer();
    window.location.href = `results.html?sessionId=${sessionId}`;
  } catch (error) {
    document.getElementById("roomError").textContent = error.message;
    document.getElementById("roomError").classList.remove("hidden");
  }
}

function onMicToggle() {
  if (!voiceEnabled) {
    document.getElementById("roomError").textContent = "Enable voice coach from setup to use the mic.";
    document.getElementById("roomError").classList.remove("hidden");
    return;
  }
  document.getElementById("roomError").classList.add("hidden");
  if (VoiceCoach.isListening()) {
    VoiceCoach.stopListening();
    setStatus("Your turn");
    return;
  }
  VoiceCoach.stopSpeaking();
  const started = VoiceCoach.startListening();
  if (started) {
    document.getElementById("voiceLiveLine").textContent = "Speak naturally — I’m capturing your answer.";
  }
}

function renderQuestion(question) {
  document.getElementById("roundLabel").textContent = `Question ${questionIndex}`;
  document.getElementById("questionText").textContent = question.questionText;
  document.getElementById("feedbackBox").classList.add("hidden");
  document.getElementById("nextQuestionBtn").classList.add("hidden");
  document.getElementById("evalStatus").classList.add("hidden");
  document.getElementById("submitAnswerBtn").disabled = false;
  document.getElementById("liveHint").textContent =
    "After each answer the coach scores you, explains what worked, what missed, and how a stronger answer sounds.";
}

async function speakCurrentQuestion(withIntro) {
  if (!voiceEnabled || !currentQuestion) return;
  const intro =
    questionIndex === 1
      ? "Welcome to your SMARTPREP mock interview. Here is your first question. "
      : "Next question. ";
  const line = `${withIntro ? intro : ""}${currentQuestion.questionText}`;
  await VoiceCoach.speak(line);
}

function showFeedback(evaluation) {
  if (!evaluation) return;
  document.getElementById("feedbackBox").classList.remove("hidden");
  document.getElementById("feedbackScore").textContent = evaluation.score;
  document.getElementById("feedbackVerdict").textContent = evaluation.verdict || "Reviewed";
  document.getElementById("feedbackText").textContent =
    evaluation.feedback || "Keep refining structure and depth.";
  document.getElementById("feedbackWhyRight").textContent =
    evaluation.whyRight || evaluation.strengths || "You addressed the prompt.";
  document.getElementById("feedbackWhyWrong").textContent =
    evaluation.whyWrong || evaluation.improvements || "Add more specificity.";
  document.getElementById("feedbackBetter").textContent =
    evaluation.betterAnswer || "Lead with approach, then example, then trade-off and result.";
  document.getElementById("liveHint").textContent = "Review this feedback, then choose when to continue.";
  lastSpokenFeedback = evaluation.spokenFeedback || buildFallbackSpeech(evaluation);
}

function buildFallbackSpeech(evaluation) {
  return `You scored ${evaluation.score} out of 10. Verdict: ${evaluation.verdict || "reviewed"}. What worked: ${
    evaluation.whyRight || evaluation.strengths || "your attempt"
  }. What to fix: ${evaluation.whyWrong || evaluation.improvements || "add more structure"}.`;
}

function setStatus(text) {
  const chip = document.getElementById("statusChip");
  chip.innerHTML = `<span class="status-dot"></span>${text}`;
}

function startTimer() {
  timerSeconds = 0;
  stopTimer();
  timerHandle = setInterval(() => {
    timerSeconds += 1;
    const m = String(Math.floor(timerSeconds / 60)).padStart(2, "0");
    const s = String(timerSeconds % 60).padStart(2, "0");
    document.getElementById("timerLabel").textContent = `${m}:${s}`;
  }, 1000);
}

function stopTimer() {
  if (timerHandle) {
    clearInterval(timerHandle);
    timerHandle = null;
  }
}
