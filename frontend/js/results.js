document.addEventListener("DOMContentLoaded", async () => {
  if (!Auth.requireAuth()) return;

  const params = new URLSearchParams(window.location.search);
  const sessionId = params.get("sessionId") || localStorage.getItem("smartprep_last_session");
  const list = document.getElementById("breakdownList");

  if (!sessionId) {
    list.innerHTML = `
      <div class="list-item">
        <div>
          <h3>No session selected</h3>
          <p>Open a session from <a href="sessions.html">Past sessions</a> or finish a new mock.</p>
        </div>
      </div>`;
    return;
  }

  try {
    const report = await Api.get(`/interviews/${sessionId}/report`);
    localStorage.setItem("smartprep_last_session", sessionId);

    document.getElementById("overallScore").textContent =
      report.averageScore != null ? Number(report.averageScore).toFixed(1) : "—";
    document.getElementById("questionCount").textContent = report.questionCount ?? "—";
    document.getElementById("reportRole").textContent = report.role || "—";
    document.getElementById("reportSub").textContent = `${report.role} · ${report.difficulty} · ${report.status}`;
    document.getElementById("planTitle").textContent = report.upgradePlanTitle || "Personalized next steps";
    document.getElementById("planText").textContent =
      report.upgradePlan || "Practice structured answers and deeper technical trade-offs.";

    const items = report.answers || [];
    if (!items.length) {
      list.innerHTML = `
        <div class="list-item">
          <div>
            <h3>No answered questions in this session</h3>
            <p>This session may have ended before answers were submitted.</p>
          </div>
        </div>`;
      return;
    }

    list.innerHTML = items
      .map(
        (item, index) => `
        <article class="session-detail-card">
          <div class="session-detail-head">
            <h3>Q${index + 1}. ${escapeHtml(item.questionText)}</h3>
            <span class="badge">${item.score != null ? item.score : "—"}/10</span>
          </div>
          ${item.verdict ? `<div class="verdict-pill">${escapeHtml(item.verdict)}</div>` : ""}
          <div class="detail-block">
            <h4>Your answer</h4>
            <p>${escapeHtml(item.answerText || "—")}</p>
          </div>
          <div class="detail-block">
            <h4>Coach feedback</h4>
            <p>${escapeHtml(item.feedback || "No feedback")}</p>
          </div>
          <div class="teach-grid">
            <div class="detail-block">
              <h4>What was right</h4>
              <p>${escapeHtml(item.whyRight || item.strengths || "—")}</p>
            </div>
            <div class="detail-block">
              <h4>What was wrong</h4>
              <p>${escapeHtml(item.whyWrong || item.improvements || "—")}</p>
            </div>
          </div>
          <div class="detail-block better-answer">
            <h4>Stronger answer</h4>
            <p>${escapeHtml(item.betterAnswer || "—")}</p>
          </div>
        </article>`
      )
      .join("");
  } catch (error) {
    console.warn(error.message);
    list.innerHTML = `
      <div class="list-item">
        <div>
          <h3>Could not load report</h3>
          <p>${escapeHtml(error.message)}. Try another session from <a href="sessions.html">Past sessions</a>.</p>
        </div>
      </div>`;
  }
});

function escapeHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
