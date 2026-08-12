// FryPow web UI E2E — plain playwright script (no test-runner dependency).
// Usage: NODE_PATH=<global node_modules> node e2e_frypow.js
// Env: FRYMINER_URL (default http://localhost:8080), ARTIFACTS_DIR (screenshots)
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const BASE = process.env.FRYMINER_URL || 'http://localhost:8080';
const ART = process.env.ARTIFACTS_DIR || path.join(require('os').tmpdir(), 'frypow-e2e-artifacts');
fs.mkdirSync(ART, { recursive: true });

const results = [];
const consoleErrors = [];

function record(name, ok, detail) {
  results.push({ name, ok, detail: detail || '' });
  console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}${detail ? ' — ' + detail : ''}`);
}

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  page.on('pageerror', e => consoleErrors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push('console.error: ' + m.text()); });

  const shot = n => page.screenshot({ path: path.join(ART, n + '.png'), fullPage: true }).catch(() => {});

  try {
    // ---- Flow 1: landing + tabs ----
    await page.goto(BASE, { waitUntil: 'load', timeout: 30000 });
    await shot('01-landing');
    const tabs = await page.locator('.tab').allTextContents();
    record('landing: page loads with 4 tabs',
      tabs.length === 4 && /Configure/.test(tabs[0]) && /Update/.test(tabs[3]),
      'tabs=' + JSON.stringify(tabs));

    // ---- Flow 2: Update tab (Bug 1) ----
    await page.click('.tab:has-text("Update")');
    await page.waitForFunction(() => {
      const r = document.getElementById('remoteVersion');
      return r && r.textContent.trim() !== 'Checking...';
    }, { timeout: 30000 });
    const localVer = (await page.locator('#localVersion').textContent()).trim();
    const remoteVer = (await page.locator('#remoteVersion').textContent()).trim();
    const updStatus = (await page.locator('#updateStatus').textContent()).trim();
    await shot('02-update-tab');
    record('update: no "Network error"', !/Network error/i.test(updStatus), 'status=' + updStatus);
    record('update: remote version is 7-hex (not "?")', /^[0-9a-f]{7}$/.test(remoteVer), 'remote=' + remoteVer);
    record('update: local version present', /^([0-9a-f]{7}|unknown)$/.test(localVer), 'local=' + localVer);

    // ---- Flow 3: Configure + save (Bug 2) ----
    await page.click('.tab:has-text("Configure")');
    await page.selectOption('#miner', 'doge');
    await page.fill('#wallet', 'D5nsUsiivbNv2nmuNE9x2ybkkCTEL4ceHj');
    await page.fill('#worker', 'e2ework');
    await page.fill('#threads', '1');
    await page.fill('#pool', 'doge.millpools.cc:5567');
    await page.fill('#password', 'x');
    await shot('03-config-filled');
    await page.click('button[type="submit"]');
    await page.waitForFunction(() => {
      const m = document.getElementById('message');
      return m && m.textContent.includes('Configuration saved') || m.textContent.includes('❌');
    }, { timeout: 20000 });
    const saveMsg = (await page.locator('#message').textContent()).trim();
    await shot('04-config-saved');
    record('save: success banner shown', /Configuration saved/.test(saveMsg), 'msg=' + saveMsg);
    record('save: NOT "No configuration found"', !/No configuration found/.test(saveMsg), '');

    // ---- Flow 4: persistence across reload ----
    await page.reload({ waitUntil: 'load' });
    await page.waitForTimeout(2500); // loadConfig() fetch
    const persistedWallet = await page.inputValue('#wallet');
    const persistedPool = await page.inputValue('#pool');
    const persistedWorker = await page.inputValue('#worker');
    await shot('05-config-persisted');
    record('persistence: wallet round-trips', persistedWallet === 'D5nsUsiivbNv2nmuNE9x2ybkkCTEL4ceHj', 'wallet=' + persistedWallet);
    record('persistence: pool round-trips (prefix stripped)', persistedPool === 'doge.millpools.cc:5567', 'pool=' + persistedPool);
    record('persistence: worker round-trips', persistedWorker === 'e2ework', 'worker=' + persistedWorker);

    // ---- Flow 5: start mining (Bug 3) ----
    await page.click('button:has-text("Start Mining")');
    await page.waitForFunction(() => {
      const m = document.getElementById('message');
      return m && (m.textContent.includes('Mining started') || m.textContent.includes('⚠️') || m.textContent.includes('❌'));
    }, { timeout: 30000 });
    const startMsg = (await page.locator('#message').textContent()).trim();
    await shot('06-mining-started');
    record('start: "Mining started" banner', /Mining started/.test(startMsg), 'msg=' + startMsg);

    // ---- Flow 6: statistics shows hashrate ----
    await page.waitForTimeout(20000); // let the miner produce log output
    await page.click('.tab:has-text("Statistics")');
    let hashrate = '';
    try {
      await page.waitForFunction(() => {
        const h = document.getElementById('hashrate');
        return h && /[0-9]/.test(h.textContent) && !/^0 H\/s$/.test(h.textContent.trim());
      }, { timeout: 30000 });
      hashrate = (await page.locator('#hashrate').textContent()).trim();
    } catch (e) {
      hashrate = (await page.locator('#hashrate').textContent().catch(() => '(unreadable)')).trim();
    }
    await shot('07-statistics');
    record('stats: hashrate shown while mining', /[0-9]/.test(hashrate) && hashrate !== '0 H/s' && hashrate !== '--', 'hashrate=' + hashrate);

    // ---- Flow 7: monitor tab ----
    await page.click('.tab:has-text("Monitor")');
    await page.waitForTimeout(4000);
    const statusText = (await page.locator('#statusText').textContent()).trim();
    await shot('08-monitor');
    record('monitor: renders with status', statusText.length > 0, 'status=' + statusText);

    // ---- Flow 8: stop mining ----
    await page.click('.tab:has-text("Configure")');
    await page.click('button:has-text("Stop Mining")');
    await page.waitForFunction(() => {
      const m = document.getElementById('message');
      return m && (m.textContent.includes('Mining stopped') || m.textContent.includes('❌'));
    }, { timeout: 30000 });
    const stopMsg = (await page.locator('#message').textContent()).trim();
    await shot('09-mining-stopped');
    record('stop: "Mining stopped" banner', /Mining stopped/.test(stopMsg), 'msg=' + stopMsg);

    // ---- Flow 9: rapid start/stop race ----
    await page.click('button:has-text("Start Mining")');
    await page.waitForTimeout(400);
    await page.click('button:has-text("Stop Mining")');
    await page.waitForFunction(() => {
      const m = document.getElementById('message');
      return m && (m.textContent.includes('Mining stopped') || m.textContent.includes('❌'));
    }, { timeout: 40000 });
    const raceMsg = (await page.locator('#message').textContent()).trim();
    await shot('10-race');
    record('race: rapid start/stop settles to stopped without crash', /Mining stopped/.test(raceMsg), 'msg=' + raceMsg);

  } catch (e) {
    record('suite: unexpected error', false, e.message);
    await shot('99-error');
  } finally {
    await browser.close();
  }

  const fails = results.filter(r => !r.ok);
  console.log('----------------------------------------');
  console.log(`E2E SUMMARY: ${results.length - fails.length}/${results.length} passed`);
  if (consoleErrors.length) {
    console.log('Browser console errors (' + consoleErrors.length + '):');
    consoleErrors.slice(0, 10).forEach(e => console.log('  ' + e));
  } else {
    console.log('Browser console errors: none');
  }
  console.log('Artifacts: ' + ART);
  process.exit(fails.length ? 1 : 0);
})();
