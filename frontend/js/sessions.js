document.addEventListener("DOMContentLoaded", async () => {
  if (!Auth.requireAuth()) return;

  const list = document.getElementById("sessionsList");
  const errorEl = document.getElementById("sessionsError");

  try {
    const data = await Api.get("/dashboard/sessions");
    const sessions = data.sessions || [];

    document.getElementById("sessionsTotal").textContent = data.totalSessions ?? sessions.length;

    const completed = sessions.filter((s) => String(s.status || "").toUpperCase() === "COMPLETED");
    document.getElementById("sessionsCompleted").textContent = completed.length;

    const scored = sessions
      .map((s) => s.averageScore)
      .filter((v) => v != null && !Number.isNaN(Number(v)));
    document.getElementById("sessionsAvg").textContent = scored.length
      ? (scored.reduce((a, b) => a + Number(b), 0) / scored.length).toFixed(1)
      : "—";

    document.getElementById("sessionsSub").textContent = sessions.length
      ? `${sessions.length} saved mock${sessions.length === 1 ? "" : "s"}. Click one to review every question and coaching note.`
      : "No sessions yet — finish a mock interview and it will appear here.";

    if (!sessions.length) {
      list.innerHTML = `
        <div class="list-item">
          <div>
            <h3>No sessions yet</h3>
            <p>Start your first mock interview to build history.</p>
          </div>
          <a class="pill" href="interview.html"><span class="pill-dot">+</span>Begin</a>
        </div>`;
      return;
    }

    list.innerHTML = sessions
      .map((item) => {
        const score =
          item.averageScore != null ? Number(item.averageScore).toFixed(1) : "—";
        const when = formatDate(item.startedAt);
        const ended = item.endedAt ? ` · ended ${formatDate(item.endedAt)}` : "";
        return `
          <a class="list-item session-row" href="results.html?sessionId=${item.id}">
            <div>
              <h3>${escapeHtml(item.role || "Interview")}</h3>
              <p>${escapeHtml(item.difficulty || "—")} · ${escapeHtml(item.status || "—")} · ${escapeHtml(when)}${escapeHtml(ended)}</p>
            </div>
            <div class="flex items-center gap-3">
              <span class="badge">${score}</span>
              <span class="pill pill-light"><span class="pill-dot">→</span>Details</span>
            </div>
          </a>`;
      })
      .join("");
  } catch (error) {
    errorEl.textContent = error.message || "Could not load sessions.";
    errorEl.classList.remove("hidden");
    list.innerHTML = `
      <div class="list-item">
        <div>
          <h3>Could not load history</h3>
          <p>Check that the backend is awake and Postgres is connected for durable history.</p>
        </div>
      </div>`;
  }
});

function formatDate(value) {
  if (!value) return "";
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function escapeHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
