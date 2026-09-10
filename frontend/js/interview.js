let sessionId = null;
let currentQuestion = null;
let questionIndex = 1;
let timerSeconds = 0;
let timerHandle = null;
let voiceEnabled = true;
let lastSpokenFeedback = "";
let advancing = false;

document.addEventListener("DOMContentLoaded", async () => {
  if (!Auth.requireAuth()) return;

  document.getElementById("startForm").addEventListener("submit", onStart);
  document.getElementById("submitAnswerBtn").addEventListener("click", onSubmitAnswer);
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

async function onStart(e) {
  e.preventDefault();
  const setupError = document.getElementById("setupError");
  setupError.classList.add("hidden");
  voiceEnabled = document.getElementById("voiceEnabled").checked;

  const role = document.getElementById("role").value;
  const difficulty = document.getElementById("difficulty").value;

  try {
    setStatus("Starting…");
    const data = await Api.post("/interviews/start", { role, difficulty });
    sessionId = data.sessionId;
    currentQuestion = data.question;
    questionIndex = 1;

    document.getElementById("setupView").classList.add("hidden");
    document.getElementById("roomView").classList.remove("hidden");
    document.getElementById("roleLabel").textContent = role;
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

  try {
    advancing = true;
    setStatus("Evaluating…");
    const data = await Api.post(`/interviews/${sessionId}/answer`, {
      questionId: currentQuestion.id,
      answerText: answer,
    });

    showFeedback(data.evaluation);
    document.getElementById("answer").value = "";
    document.getElementById("voiceLiveLine").textContent = "Coaching ready.";

    if (voiceEnabled && data.evaluation) {
      lastSpokenFeedback =
        data.evaluation.spokenFeedback ||
        buildFallbackSpeech(data.evaluation);
      await VoiceCoach.speak(lastSpokenFeedback);
    }

    if (data.completed) {
      setStatus("Complete");
      stopTimer();
      advancing = false;
      window.location.href = `results.html?sessionId=${sessionId}`;
      return;
    }

    currentQuestion = data.nextQuestion;
    questionIndex += 1;
    renderQuestion(currentQuestion);
    advancing = false;
    setStatus("Your turn");
    await speakCurrentQuestion(true);
  } catch (error) {
    advancing = false;
    roomError.textContent = error.message;
    roomError.classList.remove("hidden");
    setStatus("Your turn");
  }
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
  document.getElementById("liveHint").textContent = evaluation.strengths
    ? `Strength: ${evaluation.strengths}`
    : "Adaptive next question ready.";
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
