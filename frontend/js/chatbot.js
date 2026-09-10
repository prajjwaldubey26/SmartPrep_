const STORE_KEY = "smartprep_chat_store_v2";
const LEGACY_KEY = "smartprep_chat";

document.addEventListener("DOMContentLoaded", () => {
  if (!Auth.requireAuth()) return;

  const thread = document.getElementById("chatThread");
  const form = document.getElementById("chatForm");
  const input = document.getElementById("chatInput");
  const errorBox = document.getElementById("chatError");
  const sendBtn = document.getElementById("sendBtn");
  const historyList = document.getElementById("chatHistoryList");
  const emptyState = document.getElementById("emptyState");
  const titleLabel = document.getElementById("chatTitleLabel");

  configureMarkdown();

  let store = loadStore();
  if (!store.activeId || !store.conversations.find((c) => c.id === store.activeId)) {
    const fresh = createConversation();
    store.conversations.unshift(fresh);
    store.activeId = fresh.id;
    saveStore(store);
  }

  renderHistory();
  renderActiveChat();

  document.getElementById("newChatBtn").addEventListener("click", () => {
    const fresh = createConversation();
    store.conversations.unshift(fresh);
    store.activeId = fresh.id;
    saveStore(store);
    renderHistory();
    renderActiveChat();
    input.focus();
  });

  document.querySelectorAll(".suggest-chip").forEach((chip) => {
    chip.addEventListener("click", () => {
      input.value = chip.dataset.prompt || chip.textContent;
      input.focus();
      autoGrow();
      form.requestSubmit();
    });
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

    const chat = getActiveChat();
    appendMessage("user", message, true);
    maybeSetTitle(chat, message);
    input.value = "";
    autoGrow();
    updateEmptyState();
    setSending(true);

    const typing = appendTyping();

    try {
      const payloadHistory = chat.messages.slice(-12);
      const data = await Api.post("/chat", {
        message,
        history: payloadHistory,
      });
      typing.remove();
      appendMessage("assistant", data.reply || "Let's try that again with a clearer question.", true);
      touchActive();
      renderHistory();
    } catch (err) {
      typing.remove();
      errorBox.textContent = err.message || "Chat failed";
      errorBox.classList.remove("hidden");
    } finally {
      setSending(false);
      input.focus();
    }
  });

  function getActiveChat() {
    return store.conversations.find((c) => c.id === store.activeId);
  }

  function renderActiveChat() {
    const chat = getActiveChat();
    thread.innerHTML = "";
    titleLabel.textContent = chat?.title || "SMARTPREP Coach";
    (chat?.messages || []).forEach((m) => appendMessage(m.role, m.content, false));
    updateEmptyState();
    scrollThread();
  }

  function renderHistory() {
    historyList.innerHTML = "";
    if (!store.conversations.length) {
      historyList.innerHTML = `<p class="gpt-history-empty">No chats yet</p>`;
      return;
    }

    store.conversations.forEach((chat) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "gpt-history-item" + (chat.id === store.activeId ? " is-active" : "");
      btn.innerHTML = `
        <span class="gpt-history-title">${escapeHtml(chat.title || "New chat")}</span>
        <span class="gpt-history-meta">${escapeHtml(formatWhen(chat.updatedAt))}</span>`;
      btn.addEventListener("click", () => {
        store.activeId = chat.id;
        saveStore(store);
        renderHistory();
        renderActiveChat();
      });
      historyList.appendChild(btn);
    });
  }

  function appendMessage(role, content, persist) {
    const row = document.createElement("div");
    row.className = `chat-bubble-row ${role === "user" ? "is-user" : "is-bot"}`;

    const bubble = document.createElement("div");
    bubble.className = "chat-bubble";

    const roleEl = document.createElement("div");
    roleEl.className = "chat-bubble-role";
    roleEl.textContent = role === "user" ? "You" : "Coach";

    const textEl = document.createElement("div");
    textEl.className = "chat-bubble-text" + (role === "user" ? "" : " md-body");

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
      const chat = getActiveChat();
      chat.messages.push({ role, content });
      chat.updatedAt = Date.now();
      saveStore(store);
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

  function maybeSetTitle(chat, firstUserMessage) {
    if (!chat || (chat.title && chat.title !== "New chat")) return;
    chat.title = firstUserMessage.replace(/\s+/g, " ").trim().slice(0, 42);
    if (firstUserMessage.length > 42) chat.title += "…";
    titleLabel.textContent = chat.title;
    saveStore(store);
    renderHistory();
  }

  function touchActive() {
    const chat = getActiveChat();
    if (!chat) return;
    chat.updatedAt = Date.now();
    store.conversations = [
      chat,
      ...store.conversations.filter((c) => c.id !== chat.id),
    ];
    saveStore(store);
  }

  function updateEmptyState() {
    const chat = getActiveChat();
    const empty = !chat || !chat.messages.length;
    emptyState.classList.toggle("hidden", !empty);
    thread.classList.toggle("hidden", empty);
  }

  function setSending(busy) {
    sendBtn.disabled = busy;
    input.disabled = busy;
  }

  function autoGrow() {
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 160)}px`;
  }

  function scrollThread() {
    thread.scrollTop = thread.scrollHeight;
  }
});

function createConversation() {
  return {
    id: `c_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    title: "New chat",
    updatedAt: Date.now(),
    messages: [],
  };
}

function loadStore() {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed && Array.isArray(parsed.conversations)) return parsed;
    }
  } catch {
    /* fall through */
  }

  // Migrate legacy single-thread history
  try {
    const legacy = JSON.parse(localStorage.getItem(LEGACY_KEY) || "[]");
    if (Array.isArray(legacy) && legacy.length) {
      const migrated = createConversation();
      migrated.title = "Previous chat";
      migrated.messages = legacy
        .filter((m) => m && m.content)
        .map((m) => ({
          role: m.role === "assistant" ? "assistant" : "user",
          content: m.content,
        }));
      const store = { conversations: [migrated], activeId: migrated.id };
      saveStore(store);
      return store;
    }
  } catch {
    /* ignore */
  }

  return { conversations: [], activeId: null };
}

function saveStore(store) {
  localStorage.setItem(
    STORE_KEY,
    JSON.stringify({
      activeId: store.activeId,
      conversations: store.conversations.slice(0, 40),
    })
  );
}

function formatWhen(ts) {
  if (!ts) return "";
  try {
    return new Date(ts).toLocaleDateString(undefined, { month: "short", day: "numeric" });
  } catch {
    return "";
  }
}

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
  text = text.replace(/<br\s*\/?>/gi, "\n");
  text = text.replace(/<\/?p>/gi, "\n");
  text = text.replace(/<\/?(div|span)[^>]*>/gi, "");
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
