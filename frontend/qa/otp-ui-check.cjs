const { artifactPath, browserExecutable } = require('./support/runtime.cjs');
const { chromium } = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: browserExecutable(),
  });
  const context = await browser.newContext();
  const page = await context.newPage();
  let registered = false;
  let outage = false;
  let mobileSends = 0;
  let emailSends = 0;
  let mobileExpiry = null;
  let mobileCooldown = null;
  let emailExpiry = null;
  let emailCooldown = null;
  let mobileVerified = false;
  let emailVerified = false;
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));

  const challengeStatus = (verified, expiresAt) => {
    if (verified) return 'NOT_ACTIVE';
    if (!expiresAt) return 'NOT_SENT';
    return Date.parse(expiresAt) <= Date.now() ? 'EXPIRED' : 'ACTIVE';
  };
  const status = () => ({
    serverTime: new Date().toISOString(),
    deliveryMode: 'MOCK',
    mobile: {
      maskedDestination: '+91 •••••• 3210',
      verified: mobileVerified,
      enabled: true,
      expiresAt: mobileExpiry,
      resendAvailableAt: mobileCooldown,
      challengeStatus: challengeStatus(mobileVerified, mobileExpiry),
    },
    email: {
      maskedDestination: 't••••@example.test',
      verified: emailVerified,
      enabled: true,
      expiresAt: emailExpiry,
      resendAvailableAt: emailCooldown,
      challengeStatus: challengeStatus(emailVerified, emailExpiry),
    },
  });

  await page.route('http://localhost:8080/api/v1/**', async route => {
    const url = new URL(route.request().url()).pathname;
    const json = (data, code = 200) => route.fulfill({
      status: code,
      contentType: 'application/json',
      body: JSON.stringify(data),
    });
    if (url.endsWith('/applicants/register')) {
      registered = true;
      return json({ applicantReference: 'APP-TEST', message: 'Draft saved' }, 201);
    }
    if (url.endsWith('/verification/status')) {
      return registered ? json(status()) : json({ code: 'UNAUTHENTICATED' }, 401);
    }
    if (url.endsWith('/mobile/send')) {
      mobileSends++;
      if (outage) return json({ detail: 'Verification code delivery is unavailable.' }, 503);
      mobileExpiry = new Date(Date.now() + 300000).toISOString();
      mobileCooldown = new Date(Date.now() + 2000).toISOString();
      await new Promise(resolve => setTimeout(resolve, 250));
      return json({
        deliveryMode: 'MOCK',
        serverTime: new Date().toISOString(),
        expiresAt: mobileExpiry,
        resendAvailableAt: mobileCooldown,
      });
    }
    if (url.endsWith('/mobile/verify')) {
      if (route.request().postDataJSON().code !== '012345') {
        return json({ detail: 'The verification code is incorrect.', code: 'INVALID_OTP' }, 400);
      }
      mobileVerified = true;
      return json({ mobileVerified: true, emailVerified: false });
    }
    if (url.endsWith('/email/send')) {
      emailSends++;
      if (!mobileVerified) return json({ detail: 'Verify mobile first.' }, 403);
      emailExpiry = new Date(Date.now() + 300000).toISOString();
      emailCooldown = new Date(Date.now() + 2000).toISOString();
      return json({
        deliveryMode: 'MOCK',
        serverTime: new Date().toISOString(),
        expiresAt: emailExpiry,
        resendAvailableAt: emailCooldown,
      });
    }
    if (url.endsWith('/email/verify')) {
      if (!mobileVerified) return json({ detail: 'Verify mobile first.' }, 403);
      if (route.request().postDataJSON().code !== '654321') {
        return json({ detail: 'The verification code is incorrect.', code: 'INVALID_OTP' }, 400);
      }
      emailVerified = true;
      return json({ mobileVerified: true, emailVerified: true });
    }
    return json({ detail: 'Unexpected fixture request' }, 400);
  });

  await page.goto('http://localhost:5180/membership');
  await page.locator('.member-hero').getByRole('link', { name: 'Get Started' }).click();
  await page.getByRole('heading', { name: 'Create your account.' }).waitFor();
  await page.locator('input[name=full_name]').fill('UI Test Applicant');
  await page.locator('input[name=mobile_number]').fill('9876543210');
  await page.locator('input[name=email]').fill('ui-fixture@example.test');
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.getByRole('heading', { name: 'Verify your account.' }).waitFor();

  const mobile = page.locator('.verification-channel').first();
  assert.equal(await page.getByRole('textbox', { name: 'Mobile code digit 1', exact: true }).count(), 0);
  assert.equal(await page.getByRole('textbox', { name: 'Email code digit 1', exact: true }).count(), 0);
  assert.equal(await page.locator('.verification-channel.is-locked').count(), 1);
  assert.equal(await page.getByRole('button', { name: 'Continue', exact: true }).count(), 0);
  const send = mobile.getByRole('button', { name: 'Send code', exact: true });
  outage = true;
  await send.click();
  await mobile.getByRole('alert').waitFor();
  outage = false;
  await send.evaluate(element => { element.click(); element.click(); });
  await page.getByRole('textbox', { name: 'Mobile code digit 1', exact: true }).waitFor();
  await page.waitForTimeout(300);
  assert.equal(mobileSends, 2);
  assert.ok(await mobile.getByRole('button', { name: /Resend in/ }).count());

  const first = page.getByRole('textbox', { name: 'Mobile code digit 1', exact: true });
  await first.fill('1');
  assert.equal(await page.evaluate(() => document.activeElement.getAttribute('aria-label')), 'Mobile code digit 2');
  await page.keyboard.press('Backspace');
  assert.equal(await first.inputValue(), '');
  assert.equal(await page.evaluate(() => document.activeElement.getAttribute('aria-label')), 'Mobile code digit 1');
  await first.evaluate(element => {
    const transfer = new DataTransfer();
    transfer.setData('text', '999999');
    element.dispatchEvent(new ClipboardEvent('paste', { bubbles: true, clipboardData: transfer }));
  });
  assert.equal(await page.getByRole('textbox', { name: 'Mobile code digit 6', exact: true }).inputValue(), '9');
  await mobile.getByRole('button', { name: 'Verify mobile', exact: true }).click();
  await mobile.getByRole('alert').waitFor();

  await context.setOffline(true);
  await page.getByText('You are offline. Reconnect to send or verify a code.').waitFor();
  assert.equal(await mobile.getByRole('button', { name: 'Verify mobile', exact: true }).isDisabled(), true);
  await context.setOffline(false);
  await page.waitForTimeout(300);

  mobileExpiry = new Date(Date.now() - 1000).toISOString();
  mobileCooldown = new Date(Date.now() - 1000).toISOString();
  await page.reload();
  await mobile.getByText('This code has expired. Request a new code to continue.').waitFor();
  await mobile.getByRole('button', { name: 'Resend code', exact: true }).click();
  await mobile.getByRole('button', { name: 'Verify mobile', exact: true }).waitFor();
  assert.equal(mobileSends, 3);
  assert.equal(await mobile.getByRole('button', { name: 'Get local test code' }).count(), 0);
  const refreshedFirst = page.getByRole('textbox', { name: 'Mobile code digit 1', exact: true });
  await refreshedFirst.fill('012345');
  await mobile.getByRole('button', { name: 'Verify mobile', exact: true }).click();
  await page.getByRole('region', { name: 'Email verification locked' }).waitFor({ state: 'detached' });
  assert.equal(await page.locator('.verification-channel.is-verified').count(), 1);

  const email = page.locator('.verification-channel').nth(1);
  assert.equal(await page.getByRole('textbox', { name: 'Email code digit 1', exact: true }).count(), 0);
  await email.getByRole('button', { name: 'Send code', exact: true }).click();
  await page.getByRole('textbox', { name: 'Email code digit 1', exact: true }).waitFor();
  assert.equal(emailSends, 1);
  assert.equal(await email.getByRole('button', { name: 'Get local test code' }).count(), 0);
  await page.getByRole('textbox', { name: 'Email code digit 1', exact: true }).fill('654321');
  await email.getByRole('button', { name: 'Verify email', exact: true }).click();
  await page.waitForFunction(() => document.querySelectorAll('.verification-channel.is-verified').length === 2);
  assert.equal(await page.getByRole('button', { name: 'Continue', exact: true }).isEnabled(), true);

  const widths = [];
  for (const width of [320, 375, 768, 1024, 1280, 1440]) {
    await page.setViewportSize({ width, height: 1100 });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
    assert.equal(overflow, false);
    widths.push({ width, overflow });
    await page.locator('.register-container').screenshot({
      path: artifactPath(`otp-inputs-${width}.png`),
      style: '.navbar{visibility:hidden!important}',
    });
  }
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.getByRole('heading', { name: 'Upload Documents', exact: true }).waitFor();
  assert.equal(await page.evaluate(() => localStorage.length), 0);
  assert.deepEqual(errors, []);
  fs.writeFileSync(artifactPath('otp-ui-results.json'), JSON.stringify({
    fixtureTests: true,
    registrationToVerification: true,
    emailLockedUntilMobileVerified: true,
    noOtpInputsBeforeIssuance: true,
    mobileAndEmailVerification: true,
    duplicateSubmissionPrevented: true,
    paste: true,
    focus: true,
    backspace: true,
    countdown: true,
    providerError: true,
    invalidCode: true,
    offline: true,
    expired: true,
    refreshRestore: true,
    documentsBlocked: true,
    widths,
    errors,
  }, null, 2));
  await browser.close();
})().catch(error => {
  console.error(error.message);
  process.exit(1);
});
