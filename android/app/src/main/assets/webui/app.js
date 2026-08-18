const $ = (id) => document.getElementById(id);
const PAGE = document.body.dataset.page;

async function api(path, opts) {
  const res = await fetch(path, opts);
  return res.json();
}

function fmtUptime(s) {
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return h + "h " + m + "m";
}

const WORDS = {
  idle: "Stopped",
  starting: "Starting",
  mining: "Mining",
  stopping: "Stopping",
  error: "Error",
  unsupported: "Unsupported",
};

let lost = false;
function setLost(v) {
  if (v === lost) return;
  lost = v;
  $("banner").hidden = !v;
}

async function refreshStatus() {
  let s;
  try {
    s = await api("/api/status");
  } catch (e) {
    setLost(true);
    return;
  }
  setLost(false);
  const word = $("status-word");
  word.textContent = WORDS[s.state] || s.state;
  word.className = s.state;
  $("coin-line").textContent = s.coinId ? "Coin: " + s.coinId : "No coin selected";
  $("detail").textContent = s.detail || "";
  $("detail").hidden = !s.detail;
  $("hashrate").textContent = s.hashrateDisplay || "--";
  $("accepted").textContent = s.accepted;
  $("uptime").textContent = fmtUptime(s.uptimeSeconds || 0);
  $("efficiency").textContent = (s.efficiencyPercent ?? 100) + "%";
  $("algo").textContent = s.algo || "--";
  $("pool").textContent = s.pool || "--";
  $("worker").textContent = s.worker || "--";
  $("difficulty").textContent = s.difficulty || "--";
  $("rejected").textContent = s.rejected;
  const busy = s.mining || s.state === "starting";
  $("btn-start").hidden = busy;
  $("btn-stop").hidden = !busy;
}

async function loadDevice() {
  try {
    const d = await api("/api/device");
    $("device").textContent =
      d.model + " · Android " + d.androidVersion + " · " + d.abi + (d.ip ? " · " + d.ip : "");
  } catch (e) {
    /* non-fatal; the status poll drives the lost banner */
  }
}

async function act(path, btn) {
  btn.disabled = true;
  $("action-msg").textContent = "";
  try {
    const r = await api(path, { method: "POST" });
    if (!r.ok) $("action-msg").textContent = r.detail || r.error || "Request failed";
  } catch (e) {
    $("action-msg").textContent = "Connection failed";
  } finally {
    btn.disabled = false;
    refreshStatus();
  }
}

function initDashboard() {
  loadDevice();
  refreshStatus();
  setInterval(refreshStatus, 3000);
  $("btn-start").addEventListener("click", () => act("/api/mining/start", $("btn-start")));
  $("btn-stop").addEventListener("click", () => act("/api/mining/stop", $("btn-stop")));
}

const FIELDS = ["coinId", "wallet", "dogeWallet", "ltcWallet", "worker", "threads", "pool", "password"];

async function loadConfig() {
  try {
    const c = await api("/api/config");
    FIELDS.forEach((f) => {
      $(f).value = c[f] ?? "";
    });
    $("startOnBoot").checked = !!c.startOnBoot;
  } catch (e) {
    $("save-status").textContent = "Could not load the current settings";
    $("save-status").className = "err";
  }
}

async function saveConfig(ev) {
  ev.preventDefault();
  const body = {};
  FIELDS.forEach((f) => {
    body[f] = $(f).value;
  });
  body.threads = parseInt(body.threads, 10) || 1;
  body.startOnBoot = $("startOnBoot").checked;
  $("btn-save").disabled = true;
  $("save-status").textContent = "";
  const errs = $("field-errors");
  errs.replaceChildren();
  try {
    const r = await api("/api/config", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (r.ok) {
      $("save-status").textContent = "Saved";
      $("save-status").className = "ok";
    } else {
      $("save-status").textContent = r.detail || r.error || "Save failed";
      $("save-status").className = "err";
      (r.errors || []).forEach((e) => {
        const li = document.createElement("li");
        li.textContent = e.field + ": " + e.message;
        errs.appendChild(li);
      });
    }
  } catch (e) {
    $("save-status").textContent = "Connection failed";
    $("save-status").className = "err";
  } finally {
    $("btn-save").disabled = false;
  }
}

function initConfig() {
  loadConfig();
  $("config-form").addEventListener("submit", saveConfig);
}

if (PAGE === "dashboard") initDashboard();
else if (PAGE === "config") initConfig();
