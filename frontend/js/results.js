document.addEventListener("DOMContentLoaded", async () => {
  if (!Auth.requireAuth()) return;

  const params = new URLSearchParams(window.location.search);
  const sessionId = params.get("sessionId") || localStorage.getItem("smartprep_last_session");

  if (!sessionId) return;

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

    const list = document.getElementById("breakdownList");
    const items = report.answers || [];
    if (!items.length) return;

    list.innerHTML = items
      .map(
        (item, index) => `
        <div class="list-item">
          <div>
            <h3>Q${index + 1}. ${escapeHtml(item.questionText)}</h3>
            <p>${escapeHtml(item.feedback || "No feedback")}</p>
          </div>
          <span class="badge">${item.score != null ? item.score : "—"}/10</span>
        </div>`
      )
      .join("");
  } catch (error) {
    console.warn(error.message);
  }
});

function escapeHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
