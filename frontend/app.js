// Config
const CHAT_SYNC_PATH = "/ai/autocare/chat/sync";
const CHAT_STREAM_PATH = "/ai/autocare/chat/stream";
const API_BASE = ((document.querySelector('meta[name="autocare-api-base"]') || {}).content || "http://localhost:8080").replace(/\/+$/, "");

/** Maps each UserRole to its SecurityConfig in-memory account and local-dev default password. */
const ACCOUNTS = {
  OWNER: { username: "owner", defaultPassword: "owner-pass" },
  ADVISOR: { username: "advisor", defaultPassword: "advisor-pass" },
  ADMIN: { username: "admin", defaultPassword: "admin-pass" },
};

const ROLE_NAMES = {
  OWNER: "Vehicle Owner",
  ADVISOR: "Service Advisor",
  ADMIN: "Administrator",
};

const el = {
  app: document.querySelector(".app"),
  sidebar: document.getElementById("appSidebar"),
  sidebarResizer: document.getElementById("sidebarResizer"),
  themeToggle: document.getElementById("themeToggle"),
  prefsToggle: document.getElementById("prefsToggle"),
  prefsBody: document.getElementById("prefsBody"),
  chatsToggle: document.getElementById("chatsToggle"),
  chatsBody: document.getElementById("chatsBody"),
  chatHistory: document.getElementById("chatHistory"),
  messages: document.getElementById("messages"),
  welcome: document.getElementById("welcome"),
  form: document.getElementById("composer"),
  input: document.getElementById("input"),
  send: document.getElementById("send"),
  vehicleFamily: document.getElementById("vehicleFamily"),
  language: document.getElementById("language"),
  asyncMode: document.getElementById("asyncMode"),
  newChat: document.getElementById("newChat"),
  loginButton: document.getElementById("loginButton"),
  accountButton: document.getElementById("accountButton"),
  accountMenu: document.getElementById("accountMenu"),
  accountName: document.getElementById("accountName"),
  accountRole: document.getElementById("accountRole"),
  signOut: document.getElementById("signOut"),
  loginDialog: document.getElementById("loginDialog"),
  loginForm: document.getElementById("loginForm"),
  loginCancel: document.getElementById("loginCancel"),
  loginUsername: document.getElementById("loginUsername"),
  loginPassword: document.getElementById("loginPassword"),
  loginError: document.getElementById("loginError"),
  adminLink: document.getElementById("adminLink"),
  monitoringPanel: document.getElementById("monitoringPanel"),
  monitoringHistory: document.getElementById("monitoringHistory"),
  monitoringDetails: document.getElementById("monitoringDetails"),
  closeMonitoring: document.getElementById("closeMonitoring"),
};

// State
let auth = null;
let sessionId = "";
let busy = false;
let monitoringTurns = [];
let lastAttemptedUsername = "";
let sidebarWidth = 280;
let chatMemory = new Map();
let activeHistoryId = null;

function newSessionId() {
  if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
  return "sess-" + Math.random().toString(36).slice(2, 12);
}

// Theme
function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  el.themeToggle.setAttribute(
    "aria-label",
    theme === "light" ? "Switch to dark theme" : "Switch to light theme",
  );
}

function initTheme() {
  const media = window.matchMedia("(prefers-color-scheme: dark)");
  let themeLocked = Boolean(window.autocareThemeStored);
  applyTheme(document.documentElement.dataset.theme === "dark" ? "dark" : "light");
  el.themeToggle.addEventListener("click", () => {
    const theme = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    themeLocked = true;
    applyTheme(theme);
    try {
      localStorage.setItem("autocare.ui.theme", theme);
    } catch (error) {
      // The selected theme remains active until the page reloads.
    }
  });
  media.addEventListener("change", (event) => {
    if (!themeLocked) applyTheme(event.matches ? "dark" : "light");
  });
}

// Sidebar (sections, resize)
function saveSectionState() {
  const state = {
    prefs: el.prefsToggle.getAttribute("aria-expanded") === "true",
    chats: el.chatsToggle.getAttribute("aria-expanded") === "true",
  };
  try {
    localStorage.setItem("autocare.ui.sections", JSON.stringify(state));
  } catch (error) {
    // Keep the current disclosure state for this page session.
  }
}

function setSectionExpanded(toggle, body, expanded) {
  toggle.setAttribute("aria-expanded", String(expanded));
  body.hidden = !expanded;
}

function initSections() {
  let state = null;
  try {
    state = JSON.parse(localStorage.getItem("autocare.ui.sections") || "null");
  } catch (error) {
    state = null;
  }
  setSectionExpanded(el.prefsToggle, el.prefsBody, typeof state?.prefs === "boolean" ? state.prefs : true);
  setSectionExpanded(el.chatsToggle, el.chatsBody, typeof state?.chats === "boolean" ? state.chats : true);

  for (const [toggle, body] of [
    [el.prefsToggle, el.prefsBody],
    [el.chatsToggle, el.chatsBody],
  ]) {
    toggle.addEventListener("click", () => {
      setSectionExpanded(toggle, body, toggle.getAttribute("aria-expanded") !== "true");
      saveSectionState();
    });
  }
}

function clampSidebarWidth(width) {
  return Math.max(220, Math.min(420, Math.round(width)));
}

function setSidebarWidth(width) {
  sidebarWidth = clampSidebarWidth(width);
  el.app.style.setProperty("--sidebar-w", `${sidebarWidth}px`);
  el.sidebarResizer.setAttribute("aria-valuenow", String(sidebarWidth));
}

function saveSidebarWidth() {
  try {
    localStorage.setItem("autocare.ui.sidebarWidth", String(sidebarWidth));
  } catch (error) {
    // Keep the resized width for this page session.
  }
}

function initSidebarResize() {
  let storedWidth = null;
  try {
    storedWidth = Number(localStorage.getItem("autocare.ui.sidebarWidth"));
  } catch (error) {
    storedWidth = null;
  }
  setSidebarWidth(Number.isFinite(storedWidth) && storedWidth ? storedWidth : 280);

  let pendingX = null;
  let frame = 0;
  const updateFromPointer = () => {
    frame = 0;
    if (pendingX !== null) {
      setSidebarWidth(pendingX - el.sidebar.getBoundingClientRect().left);
      pendingX = null;
    }
  };

  el.sidebarResizer.addEventListener("pointerdown", (event) => {
    if (event.button !== 0) return;
    el.sidebarResizer.setPointerCapture(event.pointerId);
    el.app.classList.add("is-resizing");
    pendingX = event.clientX;
    if (!frame) frame = requestAnimationFrame(updateFromPointer);
  });
  el.sidebarResizer.addEventListener("pointermove", (event) => {
    if (!el.app.classList.contains("is-resizing")) return;
    pendingX = event.clientX;
    if (!frame) frame = requestAnimationFrame(updateFromPointer);
  });
  const finishResize = () => {
    if (!el.app.classList.contains("is-resizing")) return;
    el.app.classList.remove("is-resizing");
    if (frame) cancelAnimationFrame(frame);
    frame = 0;
    if (pendingX !== null) updateFromPointer();
    saveSidebarWidth();
  };
  el.sidebarResizer.addEventListener("pointerup", finishResize);
  el.sidebarResizer.addEventListener("pointercancel", finishResize);
  el.sidebarResizer.addEventListener("keydown", (event) => {
    let nextWidth = sidebarWidth;
    if (event.key === "ArrowLeft") nextWidth -= 16;
    else if (event.key === "ArrowRight") nextWidth += 16;
    else if (event.key === "Home") nextWidth = 220;
    else if (event.key === "End") nextWidth = 420;
    else if (event.key === "Enter") nextWidth = 280;
    else return;
    event.preventDefault();
    setSidebarWidth(nextWidth);
    saveSidebarWidth();
  });
  el.sidebarResizer.addEventListener("dblclick", () => {
    setSidebarWidth(280);
    saveSidebarWidth();
  });
}

// Auth
function openLoginDialog() {
  el.loginError.hidden = true;
  el.loginError.textContent = "";
  el.loginPassword.value = "";
  el.loginUsername.value = lastAttemptedUsername;
  if (!el.loginDialog.open) el.loginDialog.showModal();
  (el.loginUsername.value ? el.loginPassword : el.loginUsername).focus();
}

function setLoginError(message, focusTarget) {
  el.loginError.textContent = message;
  el.loginError.hidden = false;
  if (focusTarget) focusTarget.focus();
}

function closeAccountMenu(returnFocus) {
  el.accountMenu.hidden = true;
  el.accountButton.setAttribute("aria-expanded", "false");
  if (returnFocus && !el.loginDialog.open) el.accountButton.focus();
}

function updateAdminLink() {
  const isAdmin = auth?.role === "ADMIN";
  el.adminLink.hidden = !isAdmin;
  if (!isAdmin) closeMonitoringPanel();
}

function onAuthChanged() {
  const signedIn = auth !== null;
  el.loginButton.hidden = signedIn;
  el.accountButton.hidden = !signedIn;
  el.accountMenu.hidden = true;
  el.accountButton.setAttribute("aria-expanded", "false");
  if (signedIn) {
    el.accountName.textContent = auth.username;
    el.accountRole.textContent = ROLE_NAMES[auth.role];
    el.accountButton.querySelector(".account-avatar").textContent = auth.username.charAt(0).toUpperCase();
    el.accountButton.querySelector(".account-username").textContent = auth.username;
    el.accountButton.setAttribute("aria-label", `Account: ${auth.username}`);
  }
  updateAdminLink();
  el.input.placeholder = signedIn
    ? "Ask about service schedules, warranty, bookings…"
    : "Sign in to ask a question…";
  const welcomeText = signedIn
    ? "Pick a starter below or type your own question."
    : "Sign in, then pick a starter below or type your own question.";
  const welcomeSubline = el.welcome.querySelector("p");
  if (welcomeSubline) welcomeSubline.textContent = welcomeText;
  el.newChat.disabled = !signedIn || busy;
  renderChatHistory();
}

function signOut(options = {}) {
  auth = null;
  el.loginPassword.value = "";
  closeAccountMenu(false);
  closeMonitoringPanel();
  if (!options.keepConversation) startNewChat(false);
  onAuthChanged();
  if (!options.loginAfter) el.loginButton.focus();
}

function handleLoginSubmit(event) {
  event.preventDefault();
  const username = el.loginUsername.value.trim();
  const password = el.loginPassword.value;
  if (username) lastAttemptedUsername = username;
  if (!username || !password.trim()) {
    setLoginError("Enter your username and password.", !username ? el.loginUsername : el.loginPassword);
    return;
  }
  const normalizedUsername = username.toLowerCase();
  const entry = Object.values(ACCOUNTS).find((account) => account.username === normalizedUsername);
  if (!entry) {
    setLoginError("Unknown username. Use owner, advisor or admin.", el.loginUsername);
    return;
  }
  const role = Object.keys(ACCOUNTS).find((accountRole) => ACCOUNTS[accountRole] === entry);
  auth = { username: normalizedUsername, role, password };
  el.loginPassword.value = "";
  el.loginDialog.close();
  onAuthChanged();
  el.input.focus();
}

function closeMonitoringPanel() {
  el.monitoringPanel.hidden = true;
  el.adminLink.setAttribute("aria-expanded", "false");
}

function openMonitoringPanel() {
  if (auth?.role !== "ADMIN") return;
  el.monitoringPanel.hidden = false;
  el.adminLink.setAttribute("aria-expanded", "true");
}

// History
function historyStorageKey(username) {
  return `autocare.chats.v1.${username}`;
}

function readConversations(username) {
  if (chatMemory.has(username)) return chatMemory.get(username);
  let conversations = [];
  try {
    const stored = localStorage.getItem(historyStorageKey(username));
    const parsed = stored ? JSON.parse(stored) : [];
    conversations = Array.isArray(parsed) ? parsed.filter(
      (conversation) => conversation && typeof conversation.id === "string" && Array.isArray(conversation.messages),
    ) : [];
  } catch (error) {
    conversations = [];
  }
  conversations = conversations.slice(0, 50);
  chatMemory.set(username, conversations);
  return conversations;
}

function persistConversations(username, conversations) {
  conversations.sort((left, right) => new Date(right.updatedAt) - new Date(left.updatedAt));
  conversations.splice(50);
  for (const conversation of conversations) conversation.messages = conversation.messages.slice(-200);
  chatMemory.set(username, conversations);
  try {
    localStorage.setItem(historyStorageKey(username), JSON.stringify(conversations));
  } catch (error) {
    // Keep the in-memory history for the rest of this page session.
  }
}

function historyGroup(updatedAt) {
  const date = new Date(updatedAt);
  if (Number.isNaN(date.getTime())) return "Older";
  const today = new Date();
  const dateDay = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate());
  const todayDay = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
  const daysAgo = Math.floor((todayDay - dateDay) / 86400000);
  if (daysAgo <= 0) return "Today";
  if (daysAgo === 1) return "Yesterday";
  if (daysAgo <= 7) return "Previous 7 days";
  return "Older";
}

function renderChatHistory() {
  el.chatHistory.replaceChildren();
  if (!auth) {
    const empty = document.createElement("p");
    empty.className = "history-empty";
    empty.textContent = "Sign in to see your chats.";
    el.chatHistory.appendChild(empty);
    return;
  }
  const conversations = readConversations(auth.username)
    .slice()
    .sort((left, right) => new Date(right.updatedAt) - new Date(left.updatedAt));
  if (!conversations.length) {
    const empty = document.createElement("p");
    empty.className = "history-empty";
    empty.textContent = "No chats yet. Your conversations will appear here.";
    el.chatHistory.appendChild(empty);
    return;
  }

  const groups = new Map([
    ["Today", []],
    ["Yesterday", []],
    ["Previous 7 days", []],
    ["Older", []],
  ]);
  for (const conversation of conversations) groups.get(historyGroup(conversation.updatedAt)).push(conversation);
  for (const [label, items] of groups) {
    if (!items.length) continue;
    const group = document.createElement("div");
    group.className = "history-group";
    const heading = document.createElement("h3");
    heading.className = "history-group-label";
    heading.textContent = label;
    const list = document.createElement("ul");
    list.className = "history-list";
    list.setAttribute("role", "list");
    for (const conversation of items) {
      const item = document.createElement("li");
      item.className = "history-item";
      const button = document.createElement("button");
      button.className = "history-open";
      button.type = "button";
      button.dataset.conversationId = conversation.id;
      button.disabled = busy;
      if (conversation.id === activeHistoryId) button.setAttribute("aria-current", "page");
      const title = document.createElement("span");
      title.className = "history-title";
      title.textContent = conversation.title;
      button.appendChild(title);
      item.appendChild(button);
      list.appendChild(item);
    }
    group.append(heading, list);
    el.chatHistory.appendChild(group);
  }
}

function saveCurrentMessage(kind, text, at = new Date().toISOString(), citations) {
  if (!auth) return;
  const conversations = readConversations(auth.username);
  let conversation = conversations.find((item) => item.id === sessionId);
  if (!conversation && kind === "user") {
    const title = text.replace(/\s+/g, " ").trim();
    conversation = {
      id: sessionId,
      title: title.length > 60 ? `${title.slice(0, 60)}…` : title,
      createdAt: at,
      updatedAt: at,
      messages: [],
    };
    conversations.unshift(conversation);
  }
  if (!conversation) return;
  const message = { kind, text, at };
  if (kind === "bot" && Array.isArray(citations) && citations.length) {
    message.citations = citations.map(({ docCode, section, version }) => ({ docCode, section, version }));
  }
  conversation.messages.push(message);
  conversation.updatedAt = new Date().toISOString();
  activeHistoryId = sessionId;
  persistConversations(auth.username, conversations);
  renderChatHistory();
}

function resetMonitoringState() {
  monitoringTurns = [];
  renderMonitoringHistory();
  el.monitoringDetails.replaceChildren();
  const empty = document.createElement("p");
  empty.className = "monitoring-empty";
  empty.textContent = "Send a message to view its monitoring details.";
  el.monitoringDetails.appendChild(empty);
  if (!el.monitoringPanel.hidden && auth?.role === "ADMIN") loadMonitoringSession();
}

function startNewChat(focusInput = true) {
  sessionId = newSessionId();
  activeHistoryId = null;
  resetMonitoringState();
  el.messages.replaceChildren(el.welcome);
  renderChatHistory();
  if (focusInput) el.input.focus();
}

function openConversation(conversation) {
  sessionId = conversation.id;
  activeHistoryId = conversation.id;
  resetMonitoringState();
  el.messages.replaceChildren();
  for (const message of conversation.messages) {
    if (message.kind === "user") addMessage("user", message.text, message.at);
    else if (message.kind === "bot") {
      const rendered = addMessage("bot", message.text);
      appendSources(rendered, message.citations);
    } else if (message.kind === "bot error") addMessage("bot error", message.text);
  }
  renderChatHistory();
  scrollToEnd();
  el.input.focus();
}

// Monitoring
function renderMonitoringHistory() {
  el.monitoringHistory.replaceChildren();
  if (!monitoringTurns.length) {
    const empty = document.createElement("p");
    empty.className = "monitoring-empty";
    empty.textContent = "No turns recorded in this session.";
    el.monitoringHistory.appendChild(empty);
    return;
  }
  for (const turn of monitoringTurns) {
    const button = document.createElement("button");
    button.className = "monitoring-history-item";
    button.type = "button";
    button.textContent = `${timeNow(turn.timestamp)} · ${turn.citationStatus}`;
    button.setAttribute("aria-label", `View turn ${turn.correlationId}`);
    button.addEventListener("click", () => loadMonitoringTurn(turn));
    el.monitoringHistory.appendChild(button);
  }
}

function addMonitoringRow(container, step) {
  const row = document.createElement("article");
  row.className = "monitoring-row";
  const heading = document.createElement("div");
  heading.className = "monitoring-row-heading";
  const name = document.createElement("strong");
  name.textContent = step.component;
  const badge = document.createElement("span");
  badge.className = "monitoring-type";
  badge.textContent = step.type;
  heading.append(name, badge);
  const statusChip = document.createElement("span");
  statusChip.className = `monitoring-status status-${String(step.status).toLowerCase()}`;
  statusChip.textContent = step.status;
  const description = document.createElement("p");
  description.textContent = `${step.detail} · ${Number(step.durationMs) || 0} ms`;
  row.dataset.sequence = String(step.seq);
  row.append(heading, statusChip, description);
  container.appendChild(row);
}

function renderMonitoringDetails(turn) {
  el.monitoringDetails.replaceChildren();
  const summary = document.createElement("p");
  summary.className = "monitoring-summary";
  summary.textContent = `Turn ${turn.correlationId} · ${turn.role} · Citation: ${turn.citationStatus} · Escalated: ${turn.escalated}`;
  el.monitoringDetails.appendChild(summary);
  const query = document.createElement("p");
  query.className = "monitoring-query";
  query.textContent = `Redacted query: ${turn.redactedQuery || "loading…"}`;
  el.monitoringDetails.appendChild(query);
  for (const step of [...turn.steps].sort((left, right) => left.seq - right.seq)) {
    addMonitoringRow(el.monitoringDetails, step);
  }
}

async function loadMonitoringTurn(turn) {
  if (auth?.role !== "ADMIN") return;
  const requestedSessionId = sessionId;
  const requestedUsername = auth.username;
  try {
    const response = await fetch(
      `${API_BASE}/ai/autocare/monitoring/${encodeURIComponent(turn.correlationId)}`,
      { headers: { Accept: "application/json", Authorization: "Basic " + btoa(`${auth.username}:${auth.password}`) } },
    );
    if (!response.ok) await responseError(response);
    const trace = await response.json();
    if (sessionId !== requestedSessionId || auth?.role !== "ADMIN" || auth.username !== requestedUsername) return;
    Object.assign(turn, {
      sessionId: trace.sessionId,
      role: trace.actor,
      timestamp: trace.timestamp,
      redactedQuery: trace.redactedQuery,
      citationStatus: trace.citationStatus,
      escalated: Boolean(trace.escalated),
      steps: Array.isArray(trace.steps) ? trace.steps : [],
    });
    renderMonitoringHistory();
    renderMonitoringDetails(turn);
  } catch (error) {
    if (sessionId !== requestedSessionId || auth?.role !== "ADMIN" || auth.username !== requestedUsername) return;
    el.monitoringDetails.replaceChildren();
    const errorMessage = document.createElement("p");
    errorMessage.className = "monitoring-empty";
    errorMessage.textContent = error instanceof ChatApiError && error.status === 403
      ? "Monitoring is restricted to the administrator account."
      : "Could not load the monitoring trace for this turn.";
    el.monitoringDetails.appendChild(errorMessage);
  }
}

async function loadMonitoringSession() {
  if (auth?.role !== "ADMIN") return;
  const requestedSessionId = sessionId;
  const requestedUsername = auth.username;
  try {
    const response = await fetch(
      `${API_BASE}/ai/autocare/monitoring`,
      { headers: { Accept: "application/json", Authorization: "Basic " + btoa(`${auth.username}:${auth.password}`) } },
    );
    if (!response.ok) await responseError(response);
    const turns = await response.json();
    if (sessionId !== requestedSessionId || auth?.role !== "ADMIN" || auth.username !== requestedUsername) return;
    const persistedTurns = turns.map((turn) => ({
      correlationId: turn.correlationId,
      sessionId: turn.sessionId,
      timestamp: turn.timestamp,
      role: turn.actor,
      citationStatus: turn.citationStatus,
      escalated: turn.escalated,
      redactedQuery: turn.redactedQuery,
      steps: [],
    }));
    const combined = new Map(
      [...persistedTurns, ...monitoringTurns].map((turn) => [turn.correlationId, turn]),
    );
    monitoringTurns = [...combined.values()].sort(
      (left, right) => new Date(right.timestamp) - new Date(left.timestamp),
    );
    renderMonitoringHistory();
    if (monitoringTurns.length) loadMonitoringTurn(monitoringTurns[0]);
  } catch (error) {
    if (sessionId !== requestedSessionId || auth?.role !== "ADMIN" || auth.username !== requestedUsername) return;
    const message = error instanceof ChatApiError && error.status === 403
      ? "Monitoring is restricted to the administrator account."
      : "Could not load persisted monitoring records.";
    el.monitoringDetails.replaceChildren();
    const errorMessage = document.createElement("p");
    errorMessage.className = "monitoring-empty";
    errorMessage.textContent = message;
    el.monitoringDetails.appendChild(errorMessage);
  }
}

function monitorTurn(data, role) {
  if (auth?.role !== "ADMIN" || role !== "ADMIN" || !data || !data.correlationId) return;
  const turn = {
    correlationId: data.correlationId,
    sessionId: data.sessionId || sessionId,
    timestamp: data.timestamp || new Date().toISOString(),
    role,
    redactedQuery: "",
    citationStatus: "LOADING",
    escalated: false,
    steps: [],
  };
  monitoringTurns = [turn, ...monitoringTurns.filter(
    (existing) => existing.correlationId !== turn.correlationId,
  )];
  renderMonitoringHistory();
  renderMonitoringDetails(turn);
  loadMonitoringTurn(turn);
}

// Chat
function clearWelcome() {
  if (el.welcome && el.welcome.isConnected) el.welcome.remove();
}

function scrollToEnd() {
  el.messages.scrollTop = el.messages.scrollHeight;
}

function animateAnswer(element, text) {
  const reducedMotion = window.matchMedia
    && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reducedMotion || text.length < 2) {
    element.textContent = text;
    return Promise.resolve();
  }
  const duration = Math.min(1800, Math.max(450, text.length * 10))
    * (0.92 + Math.random() * 0.16);
  const startedAt = performance.now();
  let revealedLength = 0;
  return new Promise((resolve) => {
    function revealFrame(now) {
      const progress = Math.min((now - startedAt) / duration, 1);
      revealedLength = Math.max(
        revealedLength,
        Math.min(text.length, Math.floor(progress * text.length) + Math.floor(Math.random() * 3)),
      );
      element.textContent = text.slice(0, revealedLength);
      scrollToEnd();
      if (progress < 1) requestAnimationFrame(revealFrame);
      else resolve();
    }
    requestAnimationFrame(revealFrame);
  });
}

function timeNow(iso) {
  const date = iso ? new Date(iso) : new Date();
  return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function addMessage(kind, text, at) {
  clearWelcome();
  const wrap = document.createElement("div");
  wrap.className = `msg ${kind}`;
  const avatar = document.createElement("div");
  avatar.className = "avatar";
  avatar.textContent = kind === "user" ? "YOU" : "AI";
  const body = document.createElement("div");
  const bubble = document.createElement("div");
  bubble.className = "bubble";
  bubble.textContent = text;
  body.appendChild(bubble);
  if (kind === "user") {
    const stamp = document.createElement("div");
    stamp.className = "stamp";
    stamp.textContent = timeNow(at);
    body.appendChild(stamp);
  }
  wrap.append(avatar, body);
  el.messages.appendChild(wrap);
  scrollToEnd();
  return wrap;
}

function appendSources(message, citations) {
  if (!Array.isArray(citations) || !citations.length) return;
  const sources = document.createElement("div");
  sources.className = "sources";
  const label = document.createElement("span");
  label.className = "sources-label";
  label.textContent = "Sources";
  sources.appendChild(label);
  const list = document.createElement("div");
  list.className = "sources-list";
  for (const citation of citations) {
    const pill = document.createElement("span");
    pill.className = "source-pill";
    pill.textContent = `${citation.docCode} · ${citation.section} (${citation.version})`;
    list.appendChild(pill);
  }
  sources.append(label, list);
  message.querySelector(".bubble").parentElement.appendChild(sources);
}

function addTyping() {
  clearWelcome();
  const wrap = document.createElement("div");
  wrap.className = "msg bot";
  wrap.innerHTML = '<div class="avatar">AI</div><div><div class="bubble"><div class="typing"><span></span><span></span><span></span></div></div></div>';
  el.messages.appendChild(wrap);
  scrollToEnd();
  return wrap;
}

class ChatApiError extends Error {
  constructor(status) {
    super();
    this.status = status;
  }
}

function friendlyError(status) {
  switch (status) {
    case 400: return "Please check your message and try again.";
    case 401: return "Your sign-in was rejected. Please sign in again.";
    case 403: return "This account does not have permission for that action.";
    case 404: return "No matching information was found. Please contact a service advisor.";
    case 429: return "You have sent too many requests. Please wait a moment and retry.";
    case 503: return "AutoCare AI is temporarily unavailable. Please retry shortly.";
    default: return "The request could not be completed. Please try again.";
  }
}

async function responseError(response) {
  const body = await response.json().catch(() => null);
  throw new ChatApiError(body && Number.isInteger(body.status) ? body.status : response.status);
}

function parseSseFrame(frame) {
  let eventName = "message";
  const data = [];
  for (const line of frame.split(/\r?\n/)) {
    if (line.startsWith("event:")) eventName = line.slice(6).trim();
    if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
  }
  if (!data.length) return null;
  const payload = JSON.parse(data.join("\n"));
  if (eventName === "error") throw new ChatApiError(payload.status);
  return payload;
}

async function readStream(response, onMessage) {
  if (!response.body) throw new Error("Streaming is unavailable in this browser.");
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let lastMessage = null;
  function consumeFrames() {
    const frames = buffer.split(/\r?\n\r?\n/);
    buffer = frames.pop() || "";
    for (const frame of frames) {
      const payload = parseSseFrame(frame);
      if (payload) {
        lastMessage = payload;
        onMessage(payload);
      }
    }
  }
  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
    consumeFrames();
    if (done) break;
  }
  if (buffer.trim()) {
    const payload = parseSseFrame(buffer);
    if (payload) {
      lastMessage = payload;
      onMessage(payload);
    }
  }
  return lastMessage;
}

async function callBackend(payload, streaming, onMessage) {
  const response = await fetch(API_BASE + (streaming ? CHAT_STREAM_PATH : CHAT_SYNC_PATH), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: streaming ? "text/event-stream" : "application/json",
      Authorization: "Basic " + btoa(`${auth.username}:${auth.password}`),
    },
    body: JSON.stringify(payload),
  });
  if (!response.ok) await responseError(response);
  if (streaming) return readStream(response, onMessage);
  return response.json();
}

function setBusy(value) {
  busy = value;
  el.send.disabled = value;
  el.newChat.disabled = value || !auth;
  el.chatHistory.querySelectorAll(".history-open").forEach((button) => {
    button.disabled = value;
  });
}

async function sendQuery(query) {
  const trimmedQuery = query.trim();
  if (busy || !trimmedQuery) return;
  if (!auth) {
    el.input.value = trimmedQuery;
    autoGrow();
    openLoginDialog();
    return;
  }
  setBusy(true);
  const role = auth.role;
  const vehicleFamily = el.vehicleFamily.value;
  const asyncMode = el.asyncMode.checked;
  const userAt = new Date().toISOString();
  addMessage("user", trimmedQuery, userAt);
  saveCurrentMessage("user", trimmedQuery, userAt);
  const typing = addTyping();
  let streamingMessage = null;
  let asyncAnswer = "";
  const payload = {
    sessionId,
    role,
    vehicleFamily: vehicleFamily || null,
    query: trimmedQuery,
    language: el.language.value,
  };
  try {
    const data = asyncMode
      ? await callBackend(payload, true, (chunk) => { asyncAnswer += chunk.answer || ""; })
      : await callBackend(payload, false);
    typing.remove();
    let answerMessage;
    const answer = asyncMode ? (asyncAnswer || (data && data.answer) || "(empty answer)") : ((data && data.answer) || "(empty answer)");
    if (asyncMode) {
      answerMessage = addMessage("bot", "");
      streamingMessage = answerMessage;
      await animateAnswer(answerMessage.querySelector(".bubble"), answer);
    } else {
      answerMessage = addMessage("bot", answer);
    }
    appendSources(answerMessage, data && data.citations);
    saveCurrentMessage("bot", answer, new Date().toISOString(), data && data.citations);
    monitorTurn(data, role);
  } catch (error) {
    typing.remove();
    if (streamingMessage) streamingMessage.remove();
    const message = error instanceof ChatApiError
      ? friendlyError(error.status)
      : "AutoCare AI could not be reached. Please check the connection and try again.";
    addMessage("bot error", message);
    saveCurrentMessage("bot error", message);
    if (error instanceof ChatApiError && error.status === 401 && auth) {
      lastAttemptedUsername = auth.username;
      signOut({ keepConversation: true, loginAfter: true });
      openLoginDialog();
      setLoginError("Incorrect username or password.");
    }
  } finally {
    setBusy(false);
    if (!el.loginDialog.open) el.input.focus();
  }
}

function autoGrow() {
  el.input.style.height = "auto";
  el.input.style.height = `${Math.min(el.input.scrollHeight, 160)}px`;
}

// Wiring
el.loginButton.addEventListener("click", openLoginDialog);
el.loginCancel.addEventListener("click", () => el.loginDialog.close());
el.loginForm.addEventListener("submit", handleLoginSubmit);
el.accountButton.addEventListener("click", () => {
  const opening = el.accountMenu.hidden;
  el.accountMenu.hidden = !opening;
  el.accountButton.setAttribute("aria-expanded", String(opening));
  if (opening) el.signOut.focus();
});
el.signOut.addEventListener("click", () => signOut());
document.addEventListener("pointerdown", (event) => {
  if (!el.accountMenu.hidden && !el.accountMenu.contains(event.target) && !el.accountButton.contains(event.target)) {
    closeAccountMenu(false);
  }
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !el.accountMenu.hidden && !el.loginDialog.open) {
    closeAccountMenu(true);
  }
});
el.form.addEventListener("submit", (event) => {
  event.preventDefault();
  const value = el.input.value;
  if (!auth) {
    el.input.value = value.trim();
    autoGrow();
    openLoginDialog();
    return;
  }
  el.input.value = "";
  autoGrow();
  sendQuery(value);
});
el.input.addEventListener("input", autoGrow);
el.input.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    el.form.requestSubmit();
  }
});
el.messages.addEventListener("click", (event) => {
  const chip = event.target.closest(".chip");
  if (!chip) return;
  if (!auth) {
    el.input.value = chip.textContent;
    autoGrow();
    openLoginDialog();
  } else {
    sendQuery(chip.textContent);
  }
});
el.newChat.addEventListener("click", () => {
  if (!busy && auth) startNewChat();
});
el.chatHistory.addEventListener("click", (event) => {
  const button = event.target.closest(".history-open");
  if (!button || busy || !auth) return;
  const conversation = readConversations(auth.username).find((item) => item.id === button.dataset.conversationId);
  if (conversation) openConversation(conversation);
});
el.adminLink.addEventListener("click", () => {
  if (el.monitoringPanel.hidden) {
    openMonitoringPanel();
    loadMonitoringSession();
  } else closeMonitoringPanel();
});
el.closeMonitoring.addEventListener("click", closeMonitoringPanel);

// Init
initTheme();
initSidebarResize();
initSections();
sessionId = newSessionId();
onAuthChanged();
el.loginButton.focus();
