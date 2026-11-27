const { qaUrl, artifactPath, browserExecutable } = require('./support/runtime.cjs');
const { chromium } = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');

(async () => {
  const browser = await chromium.launch({ headless: true,
    executablePath: browserExecutable() });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' });
  await page.clock.install({ time: new Date('2026-11-26T12:00:00Z') });
  const requests = [];
  const checks = [];
  const errors = [];
  let networkFailure = false;
  let draftSession = false;
  page.on('pageerror', error => errors.push(error.name));
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url()).pathname;
    if (url.endsWith('/auth/session')) return route.fulfill({ json: draftSession
      ? { authenticated: true, identityAuthenticated: false, sessionState: 'ACTIVE', sessionScope: 'ONBOARDING', fullName: 'QA Saved Applicant', eligibleStep: 'VERIFY' }
      : { authenticated: false, identityAuthenticated: false, sessionState: 'NONE' } });
    requests.push(url);
    if (networkFailure) return route.abort('connectionfailed');
    if (url.endsWith('/otp/request')) {
      const identifier = request.postDataJSON().identifier;
      if (identifier === 'gopi@gm.com' || identifier === 'iplimit@gmail.com') {
        const ip = identifier === 'iplimit@gmail.com';
        const serverNow = Date.now();
        return route.fulfill({ status: 429, json: { code: 'OTP_RESEND_TOO_SOON', rateLimitScope: ip ? 'IP' : 'IDENTIFIER',
          retryAfterSeconds: ip ? 90 : 26, serverTime: new Date(serverNow).toISOString(), retryAvailableAt: new Date(serverNow + (ip ? 90000 : 26000)).toISOString() } });
      }
      return route.fulfill({ json: { accepted: true, challengeId: 'controlled-challenge', resendAfterSeconds: 60,
        expiresInSeconds: 300, serverTime: new Date().toISOString(), expiresAt: new Date(Date.now() + 300000).toISOString(),
        resendAvailableAt: new Date(Date.now() + 60000).toISOString(), message: "If your details match an eligible account, you'll receive a 6-digit verification code." } });
    }
    if (url.endsWith('/otp/verify')) return route.fulfill({ status: 400, json: { code: 'INVALID_AUTH_CHALLENGE' } });
    return route.fulfill({ json: {} });
  });
  try {
    await page.goto(qaUrl('/login'));
    await page.locator('#login-identifier').fill('gopi@gm.com');
    await page.getByRole('button', { name: 'Get verification code', exact: true }).click();
    await page.getByRole('button', { name: 'Try again in 00:26', exact: true }).waitFor();
    assert(await page.getByRole('button', { name: /Try again in/ }).isDisabled());
    await page.screenshot({ path: artifactPath('auth-cooldown-after.png'), fullPage: true });
    const seconds = text => { const [, minutes, remainder] = text.match(/(\d+):(\d+)/); return Number(minutes) * 60 + Number(remainder); };
    const beforeTick = seconds(await page.getByRole('button', { name: /Try again in/ }).innerText());
    await page.clock.fastForward(1000);
    await page.waitForFunction(previous => {
      const text = [...document.querySelectorAll('button')].find(button => button.textContent.includes('Try again in'))?.textContent;
      const match = text?.match(/(\d+):(\d+)/);
      return match && Number(match[1]) * 60 + Number(match[2]) < previous;
    }, beforeTick);
    assert.equal(requests.length, 1);
    checks.push('Authoritative 429 becomes live MM:SS countdown on disabled request button; no duplicate request');
    await page.locator('#login-identifier').fill('other@corp.example.dev');
    assert.equal(await page.getByRole('button', { name: 'Get verification code', exact: true }).isDisabled(), false);
    await page.locator('#login-identifier').fill('gopi@gm.com');
    assert.equal(await page.getByRole('button', { name: /Try again in/ }).isDisabled(), true);
    checks.push('Unrelated identifier clears obsolete feedback; returning to original preserves its cooldown');
    await page.locator('#login-identifier').fill('iplimit@gmail.com');
    await page.getByRole('button', { name: 'Get verification code', exact: true }).click();
    await page.getByRole('button', { name: 'Try again in 01:30', exact: true }).waitFor();
    await page.locator('#login-identifier').fill('different@gmail.com');
    assert.equal(await page.getByRole('button', { name: 'Try again in 01:30', exact: true }).isDisabled(), true);
    await page.clock.fastForward(90000);
    await page.getByRole('button', { name: 'Get verification code', exact: true }).waitFor();
    checks.push('IP cooldown applies across identifiers and expires without resetting server state');
    networkFailure = true;
    await page.getByRole('button', { name: 'Get verification code', exact: true }).click();
    await page.getByText('Unable to reach ZayloPe servers. Check your internet connection and try again.').waitFor();
    assert.equal(await page.locator('#login-identifier').inputValue(), 'different@gmail.com');
    assert.equal(await page.locator('#login-identifier').getAttribute('aria-invalid'), 'false');
    networkFailure = false;
    await page.locator('#login-identifier').fill('draft@gmail.com');
    assert.equal(await page.getByText('Unable to reach ZayloPe servers. Check your internet connection and try again.').count(), 0);
    checks.push('Network failure preserves contact and has compact retry feedback; editing clears stale error');
    await page.getByRole('button', { name: 'Resume unfinished registration', exact: true }).click();
    await page.getByRole('button', { name: 'Get recovery code', exact: true }).click();
    await page.getByRole('heading', { name: 'Verify your recovery' }).waitFor();
    await page.getByTestId('otp-code-input').fill('123456');
    await page.getByRole('button', { name: 'Verify & Continue', exact: true }).click();
    await page.getByText(/That code is incorrect or expired/).waitFor();
    assert(requests.includes('/api/v1/auth/recovery/otp/request'));
    assert(requests.includes('/api/v1/auth/recovery/otp/verify'));
    checks.push('Explicit recovery uses distinct request/verify endpoints and retains incorrect-code guidance');
    await page.getByRole('button', { name: 'Change details', exact: true }).click();
    await page.getByRole('button', { name: 'Back to login', exact: true }).click();
    assert.equal(await page.getByRole('button', { name: /Try again in/ }).isDisabled(), true);
    for (const width of [375, 768]) {
      await page.setViewportSize({ width, height: 1000 });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      await page.screenshot({ path: artifactPath(`auth-cooldown-after-${width}.png`), fullPage: true });
    }
    checks.push('Recovery cannot bypass identifier cooldown; mobile/tablet layouts have no horizontal overflow');
    draftSession = true;
    await page.goto(qaUrl('/login'));
    await page.getByRole('heading', { name: 'Saved registration', exact: true }).waitFor();
    await page.getByText('Your draft is saved. Contact ownership has not been verified.').waitFor();
    assert.equal(await page.locator('.zp-status-success').count(), 0);
    checks.push('An onboarding cookie is shown as an unverified saved draft, without authenticated welcome or green success');
    assert.deepEqual(errors, []);
  } finally {
    fs.writeFileSync(artifactPath('auth-login-cooldown-results.json'), JSON.stringify({ mode: 'Intercepted responses for UI error paths', checks, pageErrors: errors, requestPaths: requests }, null, 2));
    await browser.close();
  }
  console.log(`Passed ${checks.length} login cooldown browser scenarios.`);
})().catch(error => { console.error(error); process.exitCode = 1; });


