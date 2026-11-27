const { qaUrl, artifactPath, browserExecutable } = require('./support/runtime.cjs');
const { chromium } = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');

(async () => {
  const browser = await chromium.launch({ headless: true,
    executablePath: process.env.CHROME_PATH || browserExecutable() });
  const checks = [];
  const errors = [];
  const context = await browser.newContext({ viewport: { width: 1440, height: 1050 } });
  const page = await context.newPage();
  const base = process.env.LOGIN_QA_ORIGIN || qaUrl('');
  let authenticated = false;
  let requests = [];
  let verifyMode = 'incorrect';
  let logoutFails = false;
  let destination = 'WELCOME';
  const session = () => authenticated ? {
    authenticated: true, sessionState: 'ACTIVE', sessionScope: 'AUTHENTICATED',
    fullName: 'Test Member', eligibleStep: destination, canAccessDashboard: destination === 'WELCOME',
    affiliateId: 'QA-AFFILIATE-71', membershipStatus: 'ACTIVE',
  } : { authenticated: false, sessionState: 'NONE' };
  const challenge = () => ({ accepted: true, challengeId: 'qa-challenge',
    resendAfterSeconds: 60, expiresInSeconds: 300, serverTime: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 300000).toISOString(),
    resendAvailableAt: new Date(Date.now() + 60000).toISOString(),
    message: "If your details match an eligible account, you'll receive a 6-digit verification code." });
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/auth/session')) return route.fulfill({ json: session() });
    if (url.pathname.endsWith('/auth/login/otp/request')) {
      requests.push(route.request().postDataJSON());
      await new Promise(resolve => setTimeout(resolve, 250));
      return route.fulfill({ json: challenge() });
    }
    if (url.pathname.endsWith('/auth/login/otp/verify')) {
      if (verifyMode !== 'success') return route.fulfill({ status: verifyMode === 'expired' ? 410 : 400,
        json: { code: verifyMode === 'expired' ? 'OTP_EXPIRED' : 'INVALID_AUTH_CHALLENGE' } });
      authenticated = true;
      return route.fulfill({ json: { authenticated: true, fullName: 'Test Member', eligibleStep: destination } });
    }
    if (url.pathname.endsWith('/applicants/me/membership')) return route.fulfill({ json: {
      applicantName: 'Test Member', affiliateId: 'QA-AFFILIATE-71', membershipStatus: 'ACTIVE',
      amountPaise: 59900, currency: 'INR', activatedAt: '2026-11-26T10:00:00Z',
    } });
    if (url.pathname.endsWith('/auth/logout')) {
      if (logoutFails) return route.fulfill({ status: 503, json: { code: 'DATABASE_UNAVAILABLE' } });
      authenticated = false;
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: {} });
  });
  const goLogin = async () => {
    await page.goto(`${base}/login`);
    await page.locator('#login-identifier').waitFor();
  };
  const enter = async value => {
    await page.locator('#login-identifier').fill(value);
    await page.locator('#login-identifier').blur();
  };
  try {
    // Reconstruct only the original valid prop for an honest, labelled baseline comparison.
    await page.route('**/src/Pages/Login/Login.jsx*', async route => {
      const response = await route.fetch();
      const body = await response.text();
      assert(body.includes('error: identifierFieldError,'));
      await route.fulfill({ response, body: body.replace('error: identifierFieldError,',
        'valid: Boolean(trimmedIdentifier) && !liveIdentifierError && !formError, error: identifierFieldError,') });
    });
    await goLogin();
    await enter('sai@gmi.com');
    await page.waitForTimeout(500);
    assert.equal(await page.locator('.zp-dot-valid.is-visible').count(), 1);
    await page.screenshot({ path: artifactPath('login-validation-before.png'), fullPage: true });
    await page.unroute('**/src/Pages/Login/Login.jsx*');
    await goLogin();
    await enter('sai@gmi.com');
    assert.match(await page.locator('.zp-email-suggestion').innerText(), /sai@gmail.com/);
    assert.equal(await page.locator('#login-identifier').inputValue(), 'sai@gmi.com');
    assert.equal(await page.locator('.zp-dot-valid.is-visible').count(), 0);
    assert.equal(requests.length, 0);
    await page.screenshot({ path: artifactPath('login-validation-after.png'), fullPage: true });
    checks.push('Optional gmi.com correction; no silent change, green account indicator, or keystroke lookup');
    await page.getByRole('button', { name: 'Use suggested address' }).click();
    assert.equal(await page.locator('#login-identifier').inputValue(), 'sai@gmail.com');
    for (const [value, suggestion] of [['user@yhoo.com', 'user@yahoo.com'], ['user@outllook.com', 'user@outlook.com']]) {
      await enter(value);
      assert.match(await page.locator('.zp-email-suggestion').innerText(), new RegExp(suggestion.replace('.', '\\.')));
    }
    for (const value of ['bad@@example.com', 'user@example..com', '12345', '+1 9876543210', '5123456789']) {
      await enter(value);
      await page.getByRole('button', { name: 'Get verification code' }).click();
      assert.equal(await page.locator('#login-identifier').getAttribute('aria-invalid'), 'true');
    }
    assert.equal(requests.length, 0);
    checks.push('Invalid email and Indian mobile inputs never request OTP; Gmail/Yahoo/Outlook suggestions');
    for (const identifier of ['unknown@custom-domain.dev', '+91 98765 43210', 'Other.Member@CUSTOM-DOMAIN.DEV']) {
      await enter(identifier);
      await page.getByRole('button', { name: 'Get verification code' }).click();
      await page.getByText('Checking login eligibility…').waitFor();
      await page.getByRole('heading', { name: 'Verify your login' }).waitFor();
      assert.match(await page.locator('.zp-login-otp-destination').innerText(), /••/);
      assert.match(await page.locator('.zp-otp-timer-note').innerText(), /\d\d:\d\d/);
      assert.equal(await page.getByRole('button', { name: /Resend in/ }).isDisabled(), true);
      await page.getByRole('button', { name: 'Change details' }).click();
    }
    assert.equal(requests[1].identifier, '+919876543210');
    assert.equal(requests[2].identifier, 'Other.Member@custom-domain.dev');
    checks.push('Generic OTP navigation for unknown and eligible fixtures; masking, expiry and resend cooldown; custom-domain case normalization');
    await enter('Member@custom-domain.dev');
    await page.getByRole('button', { name: 'Get verification code' }).click();
    const otp = page.getByTestId('otp-code-input');
    await otp.waitFor();
    assert.equal(await otp.getAttribute('inputmode'), 'numeric');
    await otp.evaluate(el => {
      const event = new Event('paste', { bubbles: true, cancelable: true });
      Object.defineProperty(event, 'clipboardData', { value: { getData: () => '123456' } });
      el.dispatchEvent(event);
    });
    assert.equal(await otp.inputValue(), '123456');
    await page.getByRole('button', { name: 'Verify & Log in' }).click();
    await page.getByText(/That code is incorrect or expired/).waitFor();
    verifyMode = 'expired';
    await page.getByRole('button', { name: 'Verify & Log in' }).click();
    await page.getByText('This verification code has expired. Request a new code below.').waitFor();
    checks.push('Full-code paste, numeric keyboard, incorrect and expired OTP errors');
    verifyMode = 'success';
    await page.getByRole('button', { name: 'Verify & Log in' }).click();
    await page.getByRole('heading', { name: 'Welcome back, Test Member!' }).waitFor();
    assert.match(await page.getByTestId('member-welcome-page').innerText(), /QA-AFFILIATE-71/);
    await page.screenshot({ path: artifactPath('login-welcome-test-provider.png'), fullPage: true });
    checks.push('Successful mocked authentication renders personalized welcome and existing Affiliate ID');
    logoutFails = true;
    await page.getByRole('button', { name: 'Log out', exact: true }).click();
    await page.getByText('Unable to sign out. Please check your connection and try again.').waitFor();
    assert.equal(authenticated, true);
    logoutFails = false;
    await page.getByRole('button', { name: 'Log out', exact: true }).click();
    await page.locator('#login-identifier').waitFor();
    await page.goto(`${base}/welcome`);
    await page.locator('#login-identifier').waitFor();
    checks.push('Failed logout offers retry; confirmed logout and protected welcome redirect');
    authenticated = true;
    await page.goto(`${base}/login`);
    await page.getByRole('button', { name: 'Sign out / Use another account' }).waitFor();
    logoutFails = true;
    await page.getByRole('button', { name: 'Sign out / Use another account' }).click();
    await page.getByText('Unable to sign out. Please check your connection and try again.').waitFor();
    assert.equal(await page.locator('#login-identifier').count(), 0);
    logoutFails = false;
    await page.getByRole('button', { name: 'Sign out / Use another account' }).click();
    await page.locator('#login-identifier').waitFor();
    checks.push('Login session card also preserves authenticated state after failed logout');
    for (const width of [375, 768]) {
      await page.setViewportSize({ width, height: 950 });
      await enter('sai@gmi.com');
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      await page.screenshot({ path: artifactPath(`login-validation-after-${width}.png`), fullPage: true });
    }
    checks.push('375px and 768px layouts have no horizontal overflow');
    assert.deepEqual(errors, []);
  } finally {
    fs.writeFileSync(artifactPath('login-validation-results.json'), JSON.stringify({
      mode: 'Browser UI with intercepted API fixtures; no live database or provider',
      beforeScreenshot: 'Reconstructed original valid prop on current UI; not a historical full-page capture',
      checks, pageErrors: errors,
    }, null, 2));
    await browser.close();
  }
  console.log(`Passed ${checks.length} browser checks (mocked API).`);
})().catch(error => { console.error(error); process.exitCode = 1; });
