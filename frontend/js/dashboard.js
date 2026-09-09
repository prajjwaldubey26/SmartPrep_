document.addEventListener("DOMContentLoaded", async () => {
  if (!Auth.requireAuth()) return;

  const user = Auth.getUser();
  if (user?.name) {
    document.getElementById("welcomeTitle").textContent = `Hello, ${user.name.split(" ")[0]}`;
  }

  try {
    const summary = await Api.get("/dashboard/summary");
    document.getElementById("statSessions").textContent = summary.totalSessions ?? 0;
    document.getElementById("statScore").textContent =
      summary.averageScore != null ? Number(summary.averageScore).toFixed(1) : "—";
    document.getElementById("statFocus").textContent = summary.focusArea || user?.targetRole || "—";

    const history = summary.recentSessions || [];
    const list = document.getElementById("historyList");
    if (history.length) {
      list.innerHTML = history
        .map(
          (item) => `
          <div class="list-item">
            <div>
              <h3>${item.role}</h3>
              <p>${item.difficulty} · ${item.status} · ${formatDate(item.startedAt)}</p>
            </div>
            <div class="flex items-center gap-3">
              <span class="badge">${item.averageScore != null ? Number(item.averageScore).toFixed(1) : "—"}</span>
              <a class="pill pill-light" href="results.html?sessionId=${item.id}">
                <span class="pill-dot">→</span>
                Report
              </a>
            </div>
          </div>`
        )
        .join("");
    }
  } catch (error) {
    console.warn(error.message);
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
