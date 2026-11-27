const { qaUrl, artifactPath, browserExecutable } = require('./support/runtime.cjs');
const { chromium } = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const browser = await chromium.launch({ headless: true,
    executablePath: process.env.CHROME_PATH || browserExecutable() });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } });
  const origin = process.env.IDENTITY_QA_ORIGIN || qaUrl('');
  const policy = {
    allowlistEnabled: true, disposableBlockingEnabled: true, dnsEnabled: true,
    allowedDomains: ['gmail.com', 'yahoo.com', 'yahoo.co.in', 'outlook.com', 'hotmail.com', 'live.com', 'icloud.com', 'proton.me', 'protonmail.com', 'rediffmail.com'],
    disposableDomains: fs.readFileSync(path.join(__dirname, '../../backend/src/main/resources/validation/disposable-email-domains.conf'), 'utf8').split(/\r?\n/).filter(Boolean),
  };
  let policyUnavailable = false;
  const policyResponses = [];
  let domainUnavailable = false;
  let registered = false;
  let restored = false;
  let googleProfileEmail = null;
  let registerRequests = 0;
  let mutations = 0;
  let email = 'sai18@gmail.com';
  let failedDelivery = false;
  const checks = [];
  const errors = [];
  const status = () => ({ serverTime: new Date().toISOString(), deliveryMode: 'MOCK', contactVerified: false,
    mobile: { enabled: true, verified: false, maskedDestination: '+91 •••••• 3210', challengeStatus: 'NOT_SENT' },
    email: { enabled: true, verified: false, maskedDestination: 's••••@gmail.com', challengeStatus: failedDelivery ? 'DELIVERY_FAILED' : 'NOT_SENT' },
  });
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url()).pathname;
    const json = (body, status = 200) => route.fulfill({ status, json: body });
    if (url.endsWith('/validation/email-policy')) {
      policyResponses.push(policyUnavailable ? 503 : 200);
      return policyUnavailable ? json({ detail: 'Service unavailable' }, 503) : json(policy);
    }
    if (url.endsWith('/auth/session') && googleProfileEmail) return json({ authenticated: false, sessionState: 'NONE',
      onboarding: { active: true, flowType: 'GOOGLE_COMPLETE_PROFILE' } });
    if (url.endsWith('/auth/google/onboarding')) return json({ status: 'NEEDS_PROFILE_COMPLETION',
      email: googleProfileEmail, fullName: 'QA Google Applicant', authoritativeEmail: true, emailVerified: true });
    if (url.endsWith('/auth/session')) return json(registered && restored ? {
      authenticated: true, sessionState: 'ACTIVE', sessionScope: 'ONBOARDING', eligibleStep: 'VERIFY',
      fullName: 'QA Applicant', mobileVerified: false, emailVerified: false, emailVerificationMethod: 'NONE',
      applicantReference: 'APP-QA', maskedEmailAddress: 's••••@gmail.com', maskedMobileNumber: '+91 •••••• 3210',
    } : { authenticated: false, sessionState: 'NONE' });
    if (url.endsWith('/applicants/register')) {
      registerRequests++;
      await new Promise(resolve => setTimeout(resolve, 250));
      if (domainUnavailable) return json({ code: 'EMAIL_DOMAIN_CHECK_UNAVAILABLE', detail: 'Email domain checks are temporarily unavailable. Your details are unchanged; please retry.' }, 503);
      registered = true;
      email = request.postDataJSON().email;
      return json({ applicantReference: 'APP-QA', message: 'Draft saved', status: 'DRAFT' }, 201);
    }
    if (url.endsWith('/verification/status')) return json(status());
    if (url.endsWith('/verification/email/send')) {
      failedDelivery = true;
      return json({ deliveryMode: 'MOCK', serverTime: new Date().toISOString(),
        expiresAt: new Date(Date.now() + 300000).toISOString(), resendAvailableAt: new Date(Date.now() + 60000).toISOString() });
    }
    if (url.endsWith('/applicants/me/registration')) return json({ applicantReference: 'APP-QA', fullName: 'QA Applicant', maskedEmailAddress: 's••••@gmail.com', maskedMobileNumber: '+91 •••••• 3210' });
    if (url.endsWith('/verification/email') && request.method() === 'PUT') {
      mutations++; email = request.postDataJSON().email;
      return json({ mobileVerified: false, emailVerified: false });
    }
    if (url.endsWith('/auth/logout')) { registered = false; return route.fulfill({ status: 204 }); }
    return json({});
  });
  try {
    await page.goto(`${origin}/register`);
    await page.locator('#email-address').waitFor();
    await page.locator('#full-name').fill('QA Applicant');
    await page.locator('#mobile-number').fill('9876543210');
    for (const value of ['sai@gmi.com', 'sai@kin.com', 'sai@12.12.im', 'sai18@gmial.ocm', 'sai@gmail.com.evil.dev', 'sai@mailinator.com']) {
      await page.locator('#email-address').fill(value);
      await page.locator('#email-address').blur();
      assert.equal(await page.locator('#email-address').getAttribute('aria-invalid'), 'true');
      assert.equal(await page.locator('#email-address').locator('..').locator('..').locator('.zp-dot-valid.is-visible').count(), 0);
      assert.equal(await page.getByRole('button', { name: 'Continue', exact: true }).isDisabled(), true);
    }
    assert.equal(registerRequests, 0);
    await page.locator('#email-address').fill('sai@gmi.com');
    await page.locator('#email-address').blur();
    assert.equal(await page.locator('#email-address').inputValue(), 'sai@gmi.com');
    const feedbackStyle = await page.locator('.zp-field-error').first().evaluate(el => ({size:getComputedStyle(el).fontSize,color:getComputedStyle(el).color}));
    assert.equal(feedbackStyle.size, '14px');
    assert.equal(feedbackStyle.color, 'rgb(180, 35, 24)');
    await page.screenshot({ path: artifactPath('identity-validation-after.png'), fullPage: true });
    checks.push('Unsupported, typo, disposable and spoofed domains blocked before submission with no green validation');
    await page.getByRole('button', { name: 'Use suggested address' }).click();
    assert.equal(await page.locator('#email-address').inputValue(), 'sai@gmail.com');
    await page.getByText('Email provider accepted. Ownership has not been verified.').waitFor();
    checks.push('Correction requires explicit confirmation; accepted provider remains distinct from verified ownership');
    const mobile = page.locator('#mobile-number');
    assert.equal(await mobile.getAttribute('inputmode'), 'numeric');
    await mobile.fill('98765abc10');
    assert.equal(await mobile.inputValue(), '9876543210');
    await mobile.fill('+91 98765 43210');
    assert.equal(await mobile.inputValue(), '9876543210');
    checks.push('Fixed-prefix mobile input rejects letters and accepts canonical Indian paste');
    domainUnavailable = true;
    await page.getByRole('button', { name: 'Continue', exact: true }).click();
    await page.getByText('Checking your details and email domain…').waitFor();
    await page.getByText('We cannot check this email address right now. Please try again shortly.').waitFor();
    assert.equal(await page.locator('#email-address').inputValue(), 'sai@gmail.com');
    domainUnavailable = false;
    await page.getByRole('button', { name: 'Continue', exact: true }).click();
    await page.getByRole('heading', { name: 'Verify your contact details' }).waitFor();
    checks.push('Temporary domain failure preserves entries and permits retry; valid registration reaches verification');
    const emailChoice = page.getByRole('radio', { name: /Email address/ });
    await emailChoice.click();
    await page.getByRole('button', { name: 'Change email address', exact: true }).click();
    const save = page.getByRole('button', { name: 'Save email', exact: true });
    assert.equal(await save.isDisabled(), true);
    await page.locator('#edit-email-address').fill('sai@kin.com');
    await page.locator('#edit-email-address').blur();
    assert.equal(await save.isDisabled(), true);
    assert.equal(await page.locator('#edit-email-address').getAttribute('aria-invalid'), 'true');
    await page.locator('#edit-email-address').fill('corrected@yahoo.com');
    assert.equal(await save.isDisabled(), false);
    await save.click();
    await page.getByText('Email address updated. Request a verification code below.').waitFor();
    assert.equal(mutations, 1);
    assert.equal(email, 'corrected@yahoo.com');
    checks.push('Email correction disables empty/invalid Save, submits valid correction and returns to unverified state');
    await page.getByRole('button', { name: 'Send email code', exact: true }).click();
    await page.getByText('Code delivery failed. Check your contact details and retry after the resend timer.').waitFor();
    checks.push('Protected registration status exposes provider failure without treating contact as verified');
    registered = false;
    policyUnavailable = true;
    await page.goto(`${origin}/register`);
    await page.getByRole('button', { name: 'Retry email validation' }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Continue', exact: true }).isDisabled(), true);
    await page.locator('#email-address').fill('kept@gmail.com');
    await page.locator('#email-address').blur();
    assert.equal(await page.locator('#email-address').getAttribute('aria-invalid'), 'false');
    policyUnavailable = false;
    await page.getByRole('button', { name: 'Retry email validation' }).click();
    await page.getByText('Email provider accepted. Ownership has not been verified.').waitFor();
    assert.equal(await page.locator('#email-address').inputValue(), 'kept@gmail.com');
    checks.push('Policy service failure prevents submission and retry preserves entered address');
    for (const width of [375, 768]) {
      await page.setViewportSize({ width, height: 950 });
      await page.locator('#email-address').fill('sai@kin.com');
      await page.locator('#email-address').blur();
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      await page.screenshot({ path: artifactPath(`identity-validation-after-${width}.png`), fullPage: true });
    }
    checks.push('Mobile/tablet validation feedback has no horizontal overflow');
    googleProfileEmail = 'qa@kin.com';
    await page.goto(`${origin}/register`);
    await page.locator('#google-mobile-number').waitFor();
    await page.getByText('Please use Gmail, Yahoo, Outlook or another supported email address.').waitFor();
    assert.equal(await page.getByRole('button', { name: 'Continue', exact: true }).isDisabled(), true);
    googleProfileEmail = 'qa.google@gmail.com';
    await page.goto(`${origin}/register`);
    await page.locator('#google-mobile-number').fill('+91 98765 43210');
    assert.equal(await page.locator('#google-mobile-number').inputValue(), '9876543210');
    assert.equal(await page.getByRole('button', { name: 'Continue', exact: true }).isDisabled(), false);
    checks.push('Google profile completion shares provider policy, clearly rejects unsupported email and reuses mobile paste validation');
    assert.deepEqual(errors, []);
  } catch (failure) {
    await page.screenshot({ path: artifactPath('identity-validation-failure.png'), fullPage: true });
    console.error((await page.locator('body').innerText()).slice(-3500));
    throw failure;
  } finally {
    fs.writeFileSync(artifactPath('identity-validation-results.json'), JSON.stringify({ mode: 'Browser with intercepted API and test contacts; no real OTP delivery', checks, pageErrors: errors, policyResponses }, null, 2));
    await browser.close();
  }
  console.log(`Passed ${checks.length} identity browser checks.`);
})().catch(error => { console.error(error); process.exitCode = 1; });




