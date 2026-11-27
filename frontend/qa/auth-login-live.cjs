const { artifactPath, browserExecutable } = require('./support/runtime.cjs');
const { chromium } = require('playwright-core');
const { spawn } = require('node:child_process');
const readline = require('node:readline');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

// Real frontend -> real fixture backend. No API interception and no OTP HTTP/debug endpoint.
(async () => {
  const api = process.env.AUTH_QA_API_ORIGIN;
  const email = process.env.AUTH_QA_EMAIL;
  const affiliateId = process.env.AUTH_QA_AFFILIATE_ID;
  if (!/^http:\/\/localhost:\d+$/.test(api || '') || !email || !affiliateId) throw new Error('Controlled fixture configuration required');
  const origin = 'http://localhost:5180';
  const vite = spawn(process.execPath, ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '5180', '--strictPort'], {
    cwd: path.join(__dirname, '..'), env: { ...process.env, VITE_API_BASE_URL: `${api}/api/v1` }, windowsHide: true, stdio: 'ignore',
  });
  let browser;
  const trace = [];
  const checks = [];
  const errors = [];
  try {
    let ready = false;
    for (let attempt = 0; attempt < 100; attempt++) {
      if (vite.exitCode !== null) throw new Error('Fixture frontend port unavailable');
      try { if ((await fetch(origin)).ok) { ready = true; break; } } catch {}
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    assert(ready, 'Fixture frontend did not start');
    const apiModule = await fetch(`${origin}/src/Services/api.js`).then(response => response.text());
    assert(apiModule.includes(api + '/api/v1'), 'Frontend is not configured for the controlled backend; refusing submissions');
    browser = await chromium.launch({ headless: true,
      executablePath: process.env.CHROME_PATH || browserExecutable() });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1050 }, reducedMotion: 'reduce' });
    page.on('pageerror', error => errors.push(error.name));
    const cdp = await page.context().newCDPSession(page);
    await cdp.send('Network.enable');
    const methods = new Map();
    cdp.on('Network.requestWillBeSent', ({ requestId, request }) => methods.set(requestId, request.method));
    cdp.on('Network.responseReceived', ({ requestId, response }) => {
      if (!response.url.startsWith(api + '/api/v1/')) return;
      const contract = Object.entries(response.headers).find(([name]) => name.toLowerCase() === 'x-zaylope-auth-contract')?.[1];
      trace.push({ url: response.url, method: methods.get(requestId), status: response.status, ...(contract ? { authContract: contract } : {}) });
    });
    await page.goto(`${origin}/login`);
    await page.locator('#login-identifier').fill('Sai@kin.com');
    await page.getByRole('button', { name: 'Get verification code', exact: true }).click();
    await page.getByText('No account found. Check your email or create an account.').waitFor();
    assert.equal(await page.getByTestId('otp-code-input').count(), 0);
    assert.equal(await page.getByRole('button', { name: /Pause motion/ }).count(), 0);
    await page.screenshot({ path: artifactPath('explicit-login-after.png'), fullPage: true });
    await page.setViewportSize({width:375,height:900});
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    const cycles = await page.locator('.zp-auth-bg-glow').first().evaluate(el => getComputedStyle(el).animationIterationCount);
    assert.equal(cycles, '1');
    await page.emulateMedia({reducedMotion:'reduce'});
    assert.equal(await page.locator('.zp-auth-bg-glow').first().evaluate(el => getComputedStyle(el).animationName), 'none');
    await page.screenshot({ path: artifactPath('explicit-login-after-375.png'), fullPage: true });
    await page.setViewportSize({width:1440,height:1050});
    checks.push('Unknown email remains editable on login with no OTP input or expiry countdown');
    for (const [identifier, message] of [
      [process.env.AUTH_QA_UNKNOWN_MOBILE, 'No account found. Check your mobile number or create an account.'],
      [process.env.AUTH_QA_UNVERIFIED, 'This contact needs verification. Please recover your saved registration or contact support.'],
      [process.env.AUTH_QA_RESTRICTED, 'We cannot sign you in with this account. Please contact support.'],
    ]) {
      await page.locator('#login-identifier').fill(identifier);
      await page.getByRole('button', {name:'Get verification code', exact:true}).click();
      await page.getByText(message, {exact:true}).waitFor();
      assert.equal(await page.getByTestId('otp-code-input').count(),0);
    }
    checks.push('Actual MySQL unknown mobile, unverified and restricted identities remain on login');
    await page.locator('#login-identifier').fill(email);
    assert.equal(await page.getByRole('button', { name: 'Get verification code', exact: true }).isDisabled(), false);
    await page.getByRole('button', { name: 'Get verification code', exact: true }).click();
    await page.getByTestId('otp-code-input').waitFor();
    process.stdout.write('REQUEST_CODE\n');
    const input = readline.createInterface({ input: process.stdin });
    const code = await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Private fixture code handoff timed out')), 15000);
      input.once('line', value => { clearTimeout(timeout); input.close(); resolve(value.trim()); });
    });
    assert(/^\d{6}$/.test(code), 'Invalid private test-provider handoff');
    await page.getByTestId('otp-code-input').fill(code);
    await page.getByRole('button', { name: 'Verify & Log in', exact: true }).click();
    await page.getByRole('heading', { name: 'Welcome back, QA Database Member!' }).waitFor();
    assert((await page.getByTestId('member-welcome-page').innerText()).includes(affiliateId));
    await page.screenshot({ path: artifactPath('auth-welcome-mysql.png'), fullPage: true });
    await page.setViewportSize({ width: 375, height: 1000 });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.screenshot({ path: artifactPath('auth-welcome-mysql-375.png'), fullPage: true });
    checks.push('Actual OTP verification/cookie session renders MySQL applicant name, active membership and existing Affiliate ID');
    await page.getByRole('button', {name:'Continue to my account',exact:true}).click();
    await page.getByTestId('welcome-membership-details').waitFor();
    await page.reload();
    await page.getByRole('heading', {name:'Welcome back, QA Database Member!'}).waitFor();
    checks.push('Protected account overview opens and authenticated membership survives refresh');
    await page.getByRole('button', { name: 'Log out', exact: true }).click();
    await page.locator('#login-identifier').waitFor();
    await page.goto(`${origin}/welcome`);
    await page.locator('#login-identifier').waitFor();
    checks.push('Actual logout revokes access and protected welcome redirects to login');
    assert(trace.some(entry => entry.authContract === 'verified-contact-v1'));
    assert(trace.some(entry => entry.url.endsWith('/auth/login/otp/request') && entry.status === 200));
    assert(trace.some(entry => entry.url.endsWith('/auth/login/otp/verify') && entry.status === 200));
    assert(trace.some(entry => entry.url.endsWith('/applicants/me/membership') && entry.status === 200));
    assert.deepEqual(errors, []);
    checks.push('Chrome DevTools Protocol trace confirms intended fixture backend and auth contract; no intercepted API responses');
    fs.writeFileSync(artifactPath('auth-login-live-results.json'), JSON.stringify({ origin, api, mode: 'Real HTTP/MySQL; fake delivery provider; no API interception', checks, pageErrors: errors, network: trace }, null, 2));
    process.stdout.write('BROWSER_OK\n');
  } finally {
    if (browser) await browser.close();
    vite.kill();
  }
})().catch(error => {
  process.stderr.write(`${error.name}: ${String(error.message).replace(/\b\d{6}\b/g, '[redacted]')}\n`);
  process.exitCode = 1;
});
