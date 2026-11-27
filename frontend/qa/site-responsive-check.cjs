const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require('playwright-core');

const sizes = [[320,568], [375,667], [390,844], [600,800], [701,800], [768,1024],
  [844,390], [959,600], [960,600], [1023,768], [1024,768], [1280,720], [1440,900], [1920,1080]];
const routes = [
  ['/', '.zp-hero'], ['/about', '.about-hero'], ['/how-it-works', '.how-hero'],
  ['/membership', '.member-hero'], ['/register', '#registration-full-name'],
  ['/login', '#login-email'], ['/dashboard', '.z-topbar'], ['/missing-page', 'main h1'],
];

async function checkFit(page, label) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  assert.ok(overflow <= 1, `${label}: overflow of ${overflow}px at ${page.viewportSize().width}px`);
}

async function checkWidths(page, label, checkLayout) {
  for (const [width, height] of sizes) {
    await page.setViewportSize({ width, height });
    await checkFit(page, label);
    if (checkLayout) await checkLayout(width);
  }
  console.log(`Passed ${label} at ${sizes.length} viewport sizes`);
}

async function checkHeader(page, width) {
  const toggle = page.locator('.nav-toggle');
  assert.equal(await toggle.isVisible(), width < 960);
  if (width >= 960) {
    const fits = await page.locator('.navbar-inner').evaluate(inner => {
      const links = [...inner.querySelectorAll('a')].map(a => a.getBoundingClientRect());
      return links.every((r, i) => r.left >= 0 && r.right <= innerWidth &&
        r.height >= 44 && (!i || r.left >= links[i - 1].right - 1));
    });
    assert.ok(fits, `Inline navigation overlaps at ${width}px`);
    return;
  }
  const before = await toggle.boundingBox();
  await toggle.press('Enter');
  await page.waitForFunction(() => document.activeElement.matches('.nav-links a'));
  const after = await toggle.boundingBox();
  assert.equal(after.y, before.y, 'Opening the menu moved the toggle');
  await page.locator('.login-nav-btn').scrollIntoViewIfNeeded();
  const login = await page.locator('.login-nav-btn').boundingBox();
  assert.ok(login.y >= 0 && login.y + login.height <= page.viewportSize().height + 1);
  await page.keyboard.press('Escape');
  assert.equal(await toggle.getAttribute('aria-expanded'), 'false');
}

async function checkDashboard(page, width) {
  const toggle = page.locator('.z-sidebar-toggle');
  assert.equal(await toggle.isVisible(), width < 1024);
  if (width < 1024) await toggle.press('Enter');
  await checkFit(page, 'Open dashboard menu');
  const fits = await page.locator('.z-side-menu button').evaluateAll(buttons => buttons.every(b => {
    const rect = b.getBoundingClientRect();
    return rect.height >= 44 && rect.left >= 0 && rect.right <= innerWidth;
  }));
  assert.ok(fits, `Dashboard menu at ${width}px`);
  await page.getByRole('button', { name: 'My Account', exact: true }).press('Enter');
  await page.locator('.z-other-page h2').getByText('My Account').waitFor();
  if (width < 1024) {
    assert.equal(await toggle.getAttribute('aria-expanded'), 'false');
    assert.ok(await toggle.evaluate(el => document.activeElement === el));
    await toggle.press('Enter');
  }
  await page.getByRole('button', { name: 'Home', exact: true }).press('Enter');
  await page.getByRole('button', { name: 'Next banner' }).press('Enter');
  assert.equal(await page.getByRole('button', { name: 'Show banner 2' }).getAttribute('aria-pressed'), 'true');
  await page.getByRole('button', { name: 'Previous banner' }).press('Enter');
  const controlFits = await page.locator('.z-top-actions button:visible, .z-banner-section button').evaluateAll(buttons =>
    buttons.every(b => { const r = b.getBoundingClientRect(); return r.width >= 44 && r.height >= 44 && r.left >= 0 && r.right <= innerWidth; }));
  assert.ok(controlFits, `Dashboard controls at ${width}px`);
}

(async () => {
  const { preview } = await import('vite');
  const server = await preview({ root: path.resolve(__dirname, '..'), preview: { host: '127.0.0.1', port: 0 } });
  const base = 'http://127.0.0.1:' + server.httpServer.address().port;
  let browser;
  try {
    browser = await chromium.launch({ executablePath: process.env.QA_CHROMIUM });
    const page = await browser.newPage({ reducedMotion: 'reduce', hasTouch: true });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    let stage = 'details';
    await page.route('**/api/v1/**', async route => {
      if (!route.request().url().endsWith('/verification/status') || stage === 'details') {
        return route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED' } });
      }
      const verified = stage === 'documents';
      const channel = { verified, enabled: true, challengeStatus: verified ? 'NOT_ACTIVE' : 'ACTIVE',
        expiresAt: new Date(Date.now() + 300000).toISOString(), maskedDestination: '••••' };
      return route.fulfill({ json: { serverTime: new Date().toISOString(), deliveryMode: 'MOCK', mobile: channel, email: channel } });
    });
    await page.route('**/api/auth/send-otp', route => route.fulfill({ json: {} }));
    await page.addInitScript(() => sessionStorage.setItem('zaylopeUserName', 'LongDisplayName'.repeat(5)));
    for (const [route, selector] of routes) {
      await page.goto(base + route);
      await page.locator(selector).first().waitFor();
      await page.evaluate(() => document.fonts.ready);
      let checkLayout;
      if (route === '/') checkLayout = width => checkHeader(page, width);
      if (route === '/dashboard') checkLayout = width => checkDashboard(page, width);
      await checkWidths(page, route, checkLayout);
    }
    for (const [screen, expected] of [['verification', '.verification-panel'], ['documents', '.document-upload-section'], ['bank', '.bank-details-section']]) {
      if (screen === 'bank') {
        await page.locator('.register-progress button').nth(3).click();
      } else {
        stage = screen;
        await page.goto(base + '/register');
      }
      const form = page.locator(expected);
      await form.waitFor();
      await checkWidths(page, expected, async width => {
        const fits = await form.locator('input:visible, button:visible, select:visible').evaluateAll(controls => controls.every(el => {
          const r = el.getBoundingClientRect(), card = el.closest('.onboarding-card').getBoundingClientRect();
          return r.left >= card.left && r.right <= card.right + 1;
        }));
        assert.ok(fits, `${expected} fields at ${width}px`);
      });
    }
    await page.goto(base + '/login');
    await page.locator('#login-email').fill('layout@example.test');
    await page.getByRole('button', { name: 'Continue with Email' }).click();
    await page.locator('#login-code').waitFor();
    await checkWidths(page, 'login code');
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(base + '/dashboard');
    for (const [selector, title] of [['.z-flash-section', 'Flash Deals'], ['.z-most-shared-section', 'Most Shared Deals']]) {
      await page.locator(selector).getByRole('button', { name: /View All/ }).tap();
      assert.equal(await page.locator('.z-other-page h2').textContent(), title);
      await page.locator('.z-sidebar-toggle').tap();
      await page.getByRole('button', { name: 'Home', exact: true }).tap();
    }
    await page.locator('.z-sidebar-toggle').tap();
    await page.getByRole('button', { name: /Logout/ }).tap();
    await page.waitForURL(base + '/login');
    assert.deepEqual(errors, []);
    console.log('Passed keyboard navigation, dashboard touch actions, logout routing and mocked onboarding screens.');
  } finally {
    await browser?.close();
    await server.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
