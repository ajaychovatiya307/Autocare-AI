const passwordInput = document.getElementById("adminPassword");
const versionsBody = document.getElementById("versions");
const versionsMessage = document.getElementById("versionsMessage");
const ingestForm = document.getElementById("ingestForm");
const validatedVersions = new Set();

function authHeaders(json = false) {
  const headers = {
    Authorization: "Basic " + btoa(`admin:${passwordInput.value}`),
  };
  if (json) headers["Content-Type"] = "application/json";
  return headers;
}

async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { ...authHeaders(Boolean(options.body)), ...options.headers },
  });
  if (!response.ok) {
    const bodyText = await response.text();
    let detail = bodyText;
    try {
      const body = JSON.parse(bodyText);
      detail = body.message || body.error || "";
    } catch { }
    const labels = { 400: "Invalid request", 403: "Administrator access required", 503: "A required service is unavailable" };
    throw new Error(`${labels[response.status] || `Request failed (${response.status})`}${detail ? `: ${detail}` : ""}`);
  }
  if (response.status === 204) return null;
  return response.json();
}

function showMessage(target, text, isError = false) {
  target.textContent = text;
  target.className = text ? `message ${isError ? "error" : "success"}` : "";
}

function createButton(label, callback, disabled = false) {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = label;
  button.disabled = disabled;
  button.addEventListener("click", callback);
  return button;
}

async function loadVersions() {
  showMessage(versionsMessage, "");
  try {
    const documents = await request("/kb/documents");
    versionsBody.replaceChildren();
    for (const documentVersion of documents) {
      const row = document.createElement("tr");
      for (const value of [
        documentVersion.docCode,
        documentVersion.version,
        documentVersion.status,
        documentVersion.effectiveDate,
        documentVersion.chunkCount,
        `${documentVersion.audience} / ${documentVersion.vehicleFamily || "all"}`,
      ]) {
        const cell = document.createElement("td");
        cell.textContent = value;
        row.appendChild(cell);
      }

      const actions = document.createElement("td");
      const actionGroup = document.createElement("div");
      actionGroup.className = "version-actions";
      const key = `${documentVersion.docCode}|${documentVersion.version}`;
      if (documentVersion.status === "RETIRED") {
        actionGroup.append(
          createButton("Run validation", () => validateVersion(documentVersion)),
          createButton("Activate", () => activateVersion(documentVersion), !validatedVersions.has(key)),
        );
      }
      if (documentVersion.status === "ACTIVE") {
        actionGroup.append(createButton("Roll back", () => rollbackVersion(documentVersion.docCode)));
      }
      actions.appendChild(actionGroup);
      row.appendChild(actions);
      versionsBody.appendChild(row);
    }
    if (documents.length === 0) showMessage(versionsMessage, "No KB document versions have been ingested.");
  } catch (error) {
    showMessage(versionsMessage, error.message, true);
  }
}

async function validateVersion(documentVersion) {
  const target = versionsMessage;
  try {
    showMessage(target, `Validating ${documentVersion.docCode} ${documentVersion.version}...`);
    const result = await request(
      `/kb/documents/${encodeURIComponent(documentVersion.docCode)}/versions/${encodeURIComponent(documentVersion.version)}/validate`,
      { method: "POST" },
    );
    const key = `${documentVersion.docCode}|${documentVersion.version}`;
    if (result.passed) validatedVersions.add(key);
    else validatedVersions.delete(key);
    const passed = result.results.filter((item) => item.passed).length;
    const details = result.results
      .map((item) => `${item.passed ? "PASS" : "FAIL"} Q${item.number}: ${item.question}`)
      .join("\n");
    showMessage(target, `Validation ${result.passed ? "passed" : "failed"} (${passed}/${result.results.length} questions).\n${details}`);
    await loadVersions();
  } catch (error) {
    showMessage(target, error.message, true);
  }
}

async function activateVersion(documentVersion) {
  try {
    await request(
      `/kb/documents/${encodeURIComponent(documentVersion.docCode)}/versions/${encodeURIComponent(documentVersion.version)}/activate`,
      { method: "POST" },
    );
    validatedVersions.delete(`${documentVersion.docCode}|${documentVersion.version}`);
    showMessage(versionsMessage, `Activated ${documentVersion.docCode} ${documentVersion.version}.`);
    await loadVersions();
  } catch (error) {
    showMessage(versionsMessage, error.message, true);
  }
}

async function rollbackVersion(docCode) {
  try {
    const result = await request(`/kb/documents/${encodeURIComponent(docCode)}/rollback`, { method: "POST" });
    showMessage(versionsMessage, `Rolled ${docCode} back to ${result.activeVersion}.`);
    await loadVersions();
  } catch (error) {
    showMessage(versionsMessage, error.message, true);
  }
}

ingestForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const values = Object.fromEntries(new FormData(ingestForm).entries());
  try {
    const result = await request("/kb/ingest", {
      method: "POST",
      body: JSON.stringify(values),
    });
    showMessage(versionsMessage, `Ingested ${result.docCode} ${result.version} as ${result.status}; ${result.chunkCount} chunks.`);
    ingestForm.reset();
    await loadVersions();
  } catch (error) {
    showMessage(versionsMessage, error.message, true);
  }
});

document.getElementById("refreshButton").addEventListener("click", loadVersions);
loadVersions();
