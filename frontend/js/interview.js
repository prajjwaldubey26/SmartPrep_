let sessionId = null;
let currentQuestion = null;
let questionIndex = 1;
let timerSeconds = 0;
let timerHandle = null;

document.addEventListener("DOMContentLoaded", () => {
  if (!Auth.requireAuth()) return;

  document.getElementById("startForm").addEventListener("submit", onStart);
  document.getElementById("submitAnswerBtn").addEventListener("click", onSubmitAnswer);
  document.getElementById("endBtn").addEventListener("click", onEndSession);
});

async function onStart(e) {
  e.preventDefault();
  const setupError = document.getElementById("setupError");
  setupError.classList.add("hidden");

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
    renderQuestion(currentQuestion);
    startTimer();
    setStatus("Listening");
  } catch (error) {
    setupError.textContent = error.message;
    setupError.classList.remove("hidden");
    setStatus("Ready");
  }
}

async function onSubmitAnswer() {
  const roomError = document.getElementById("roomError");
  roomError.classList.add("hidden");
  const answer = document.getElementById("answer").value.trim();
  if (!answer) {
    roomError.textContent = "Please write an answer before submitting.";
    roomError.classList.remove("hidden");
    return;
  }

  try {
    setStatus("Evaluating…");
    const data = await Api.post(`/interviews/${sessionId}/answer`, {
      questionId: currentQuestion.id,
      answerText: answer,
    });

    showFeedback(data.evaluation);
    document.getElementById("answer").value = "";

    if (data.completed) {
      setStatus("Complete");
      stopTimer();
      window.location.href = `results.html?sessionId=${sessionId}`;
      return;
    }

    currentQuestion = data.nextQuestion;
    questionIndex += 1;
    renderQuestion(currentQuestion);
    setStatus("Listening");
  } catch (error) {
    roomError.textContent = error.message;
    roomError.classList.remove("hidden");
    setStatus("Listening");
  }
}

async function onEndSession() {
  if (!sessionId) return;
  try {
    setStatus("Wrapping up…");
    await Api.post(`/interviews/${sessionId}/end`, {});
    stopTimer();
    window.location.href = `results.html?sessionId=${sessionId}`;
  } catch (error) {
    document.getElementById("roomError").textContent = error.message;
    document.getElementById("roomError").classList.remove("hidden");
  }
}

function renderQuestion(question) {
  document.getElementById("roundLabel").textContent = `Question ${questionIndex}`;
  document.getElementById("questionText").textContent = question.questionText;
  document.getElementById("feedbackBox").classList.add("hidden");
}

function showFeedback(evaluation) {
  if (!evaluation) return;
  document.getElementById("feedbackBox").classList.remove("hidden");
  document.getElementById("feedbackScore").textContent = evaluation.score;
  document.getElementById("feedbackText").textContent =
    evaluation.feedback || "Keep refining structure and depth.";
  document.getElementById("liveHint").textContent = evaluation.strengths
    ? `Strength: ${evaluation.strengths}`
    : "Adaptive next question ready.";
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
