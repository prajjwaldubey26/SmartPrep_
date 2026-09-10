const SUGGESTED_OPENER =
  "Hi — I'm your SMARTPREP interview coach. Ask me about STAR answers, system design, coding rounds, HR prep, or how to improve after a mock.";

document.addEventListener("DOMContentLoaded", () => {
  if (!Auth.requireAuth()) return;

  const thread = document.getElementById("chatThread");
  const form = document.getElementById("chatForm");
  const input = document.getElementById("chatInput");
  const errorBox = document.getElementById("chatError");
  const sendBtn = document.getElementById("sendBtn");

  configureMarkdown();

  const history = loadHistory();
  if (!history.length) {
    appendMessage("assistant", SUGGESTED_OPENER, false);
  } else {
    history.forEach((m) => appendMessage(m.role, m.content, false));
  }
  scrollThread();

  document.querySelectorAll(".suggest-chip").forEach((chip) => {
    chip.addEventListener("click", () => {
      input.value = chip.dataset.prompt || chip.textContent;
      input.focus();
      autoGrow();
    });
  });

  document.getElementById("clearChatBtn").addEventListener("click", () => {
    localStorage.removeItem("smartprep_chat");
    thread.innerHTML = "";
    appendMessage("assistant", SUGGESTED_OPENER, true);
  });

  input.addEventListener("input", autoGrow);
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      form.requestSubmit();
    }
  });

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    errorBox.classList.add("hidden");
    const message = input.value.trim();
    if (!message) return;

    appendMessage("user", message, true);
    input.value = "";
    autoGrow();
    setSending(true);

    const typing = appendTyping();

    try {
      const payloadHistory = loadHistory().slice(-12);
      const data = await Api.post("/chat", {
        message,
        history: payloadHistory,
      });
      typing.remove();
      appendMessage("assistant", data.reply || "Let's try that again with a clearer question.", true);
    } catch (err) {
      typing.remove();
      errorBox.textContent = err.message || "Chat failed";
      errorBox.classList.remove("hidden");
    } finally {
      setSending(false);
      input.focus();
    }
  });

  function appendMessage(role, content, persist) {
    const row = document.createElement("div");
    row.className = `chat-bubble-row ${role === "user" ? "is-user" : "is-bot"}`;

    const bubble = document.createElement("div");
    bubble.className = "chat-bubble";

    const roleEl = document.createElement("div");
    roleEl.className = "chat-bubble-role";
    roleEl.textContent = role === "user" ? "You" : "Coach";

    const textEl = document.createElement("div");
    textEl.className = "chat-bubble-text" + (role === "assistant" || role === "bot" ? " md-body" : "");

    if (role === "user") {
      textEl.textContent = content;
    } else {
      textEl.innerHTML = renderCoachMarkdown(content);
    }

    bubble.appendChild(roleEl);
    bubble.appendChild(textEl);
    row.appendChild(bubble);
    thread.appendChild(row);

    if (persist) {
      const next = loadHistory();
      next.push({ role, content });
      saveHistory(next);
    }
    scrollThread();
    return row;
  }

  function appendTyping() {
    const row = document.createElement("div");
    row.className = "chat-bubble-row is-bot";
    row.innerHTML = `
      <div class="chat-bubble chat-typing">
        <span></span><span></span><span></span>
      </div>`;
    thread.appendChild(row);
    scrollThread();
    return row;
  }

  function setSending(busy) {
    sendBtn.disabled = busy;
    input.disabled = busy;
  }

  function autoGrow() {
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 140)}px`;
  }

  function scrollThread() {
    thread.scrollTop = thread.scrollHeight;
  }

  function loadHistory() {
    try {
      return JSON.parse(localStorage.getItem("smartprep_chat") || "[]");
    } catch {
      return [];
    }
  }

  function saveHistory(items) {
    localStorage.setItem("smartprep_chat", JSON.stringify(items.slice(-40)));
  }
});

function configureMarkdown() {
  if (typeof marked === "undefined") return;
  marked.setOptions({
    gfm: true,
    breaks: true,
    headerIds: false,
    mangle: false,
  });
}

function renderCoachMarkdown(raw) {
  const cleaned = normalizeCoachText(raw);
  if (typeof marked !== "undefined") {
    const html = marked.parse(cleaned);
    if (typeof DOMPurify !== "undefined") {
      return DOMPurify.sanitize(html, {
        USE_PROFILES: { html: true },
      });
    }
    return html;
  }
  return fallbackFormat(cleaned);
}

function normalizeCoachText(raw) {
  let text = String(raw || "").replace(/\r\n/g, "\n").trim();
  // Models sometimes emit literal <br> — convert to markdown newlines
  text = text.replace(/<br\s*\/?>/gi, "\n");
  text = text.replace(/<\/?p>/gi, "\n");
  text = text.replace(/<\/?(div|span)[^>]*>/gi, "");
  // Collapse crazy whitespace inside table rows a bit
  text = text.replace(/\n{4,}/g, "\n\n\n");
  return text.trim();
}

function fallbackFormat(text) {
  const escaped = escapeHtml(text);
  return escaped
    .replace(/^### (.+)$/gm, "<h3>$1</h3>")
    .replace(/^## (.+)$/gm, "<h2>$1</h2>")
    .replace(/^# (.+)$/gm, "<h2>$1</h2>")
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/^\- (.+)$/gm, "<li>$1</li>")
    .replace(/(<li>.*<\/li>\n?)+/g, (block) => `<ul>${block}</ul>`)
    .replace(/\n\n/g, "</p><p>")
    .replace(/\n/g, "<br>");
}

function escapeHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
