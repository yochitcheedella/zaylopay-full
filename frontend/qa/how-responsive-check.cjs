const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require('playwright-core');

let base = process.env.QA_BASE_URL;
const titles = ['Become a Member', 'Explore Products', 'Get Your Link', 'Share Products', 'Track Orders', 'View Commissions'];

async function checkLayout(browser, width) {
  const mobile = width < 768;
  const page = await browser.newPage({ viewport: { width, height: 900 }, hasTouch: mobile, reducedMotion: 'reduce' });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(base + '/how-it-works', { waitUntil: 'domcontentloaded' });
  const main = page.locator('#how-main');
  await main.locator('img').evaluate(image => image.decode());
  const slides = main.locator('.how-step:visible');
  assert.equal(await slides.count(), mobile ? 1 : titles.length);
  assert.deepEqual(await main.locator('.how-step h2').allTextContents(), titles);
  assert.equal(await main.getByRole('heading', { level: 1 }).innerText().then(text => text.replace(/\s+/g, ' ').trim()), 'Join. Share. Track. Grow.');
  const layout = await main.evaluate(main => {
    const bounds = selector => main.querySelector(selector).getBoundingClientRect();
    return {
      overflow: document.documentElement.scrollWidth - innerWidth,
      twoColumns: bounds('.how-hero-art').left >= bounds('h1').right,
      images: [...main.querySelectorAll('.how-step')].map(step => {
        const panel = step.querySelector('.how-illustration').getBoundingClientRect();
        const art = step.querySelector('.aj-art').getBoundingClientRect();
        return { first: art.left < step.querySelector('.how-step-copy').getBoundingClientRect().left,
          fits: art.top >= panel.top - 1 && art.bottom <= panel.bottom + 1 && art.left >= panel.left && art.right <= panel.right };
      }),
    };
  });
  assert.equal(layout.overflow, 0);
  assert.equal(layout.twoColumns, !mobile);
  assert.ok(layout.images.every(image => image.fits));
  if (!mobile) assert.deepEqual(layout.images.map(image => image.first), [true, false, true, false, true, false]);
  if (process.env.QA_SCREENSHOTS) {
    await page.screenshot({ path: path.join(process.env.QA_SCREENSHOTS, 'how-hero-' + width + '.png') });
  }
  assert.ok(await main.getByRole('heading', { name: 'When does an order qualify?' }).isVisible());
  await main.getByRole('link', { name: 'Explore the steps' }).click();
  assert.ok(page.url().endsWith('#how-process'));
  if (mobile) {
    const height = await main.locator('.how-slides').evaluate(element => element.offsetHeight);
    assert.equal(await main.locator('.how-indicators button').count(), titles.length);
    const start = main.locator('.how-mobile-start');
    const startTop = await start.evaluate(el => el.getBoundingClientRect().top + scrollY);
    for (let i = 0; i < titles.length; i++) {
      await main.locator('.how-indicators button').nth(i).tap();
      await main.locator('#how-step-' + (i + 1)).waitFor({ state: 'visible', timeout: 1800 });
      assert.equal(await slides.getAttribute('id'), 'how-step-' + (i + 1));
      assert.equal(await main.locator('.how-slides').evaluate(element => element.offsetHeight), height);
      assert.equal(await slides.locator('.how-kicker').textContent(), 'Step ' + (i + 1) + ' of 6');
      assert.equal(await main.locator('.how-indicators [aria-current="step"]').count(), 1);
      assert.ok(Math.abs(await start.evaluate(el => el.getBoundingClientRect().top + scrollY) - startTop) < 1);
    }
    await main.getByRole('button', { name: 'Next step' }).tap();
    await main.locator('#how-step-1').waitFor();
    assert.equal(await slides.getAttribute('id'), 'how-step-1');
    await main.getByRole('button', { name: 'Previous step' }).focus();
    await page.keyboard.press('Enter');
    await main.locator('#how-step-6').waitFor();
    assert.equal(await slides.getAttribute('id'), 'how-step-6');
    await slides.scrollIntoViewIfNeeded();
    const box = await slides.boundingBox();
    const y = Math.round(box.y + box.height / 2);
    const client = await page.context().newCDPSession(page);
    for (const [type, x] of [['touchStart', 250], ['touchMove', 190], ['touchMove', 135], ['touchEnd', 0]]) {
      await client.send('Input.dispatchTouchEvent', { type, touchPoints: type === 'touchEnd' ? [] : [{ x, y }] });
    }
    await main.locator('#how-step-1').waitFor();
    assert.equal(await slides.getAttribute('id'), 'how-step-1');
    assert.equal(await main.getByRole('button', { name: /slideshow/i }).count(), 0);
  } else {
    for (let index = 0; index < titles.length; index++) {
      const link = main.locator('.how-index a').nth(index);
      await link.focus();
      await page.keyboard.press('Enter');
      assert.ok(page.url().endsWith('#how-step-' + (index + 1)));
    }
  }
  const destinations = [['.how-hero .zp-secondary', 'membership'], ['.how-membership-link', 'membership'], ['.how-next .zp-lime', 'membership'], ['.how-register', 'register']];
  if (mobile) destinations.push(['.how-mobile-start', 'register']);
  for (const [selector, route] of destinations) {
    if (mobile) await main.locator(selector).tap();
    else await main.locator(selector).click();
    await page.waitForURL(base + '/' + route);
    await page.locator(route === 'register' ? '#registration-title' : '#member-title').waitFor();
    await page.goBack({ waitUntil: 'domcontentloaded' });
    await main.waitFor();
  }
  assert.deepEqual(errors, []);
  await page.close();
  console.log(width + 'px passed layout, illustrations, navigation and CTA routing.');
}

async function checkPlayback(browser) {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, hasTouch: true });
  await page.goto(base + '/how-it-works', { waitUntil: 'domcontentloaded' });
  await page.locator('.how-controls').scrollIntoViewIfNeeded();
  const current = () => page.locator('.how-step:visible').getAttribute('id');
  const first = await current();
  await page.waitForTimeout(4000);
  assert.equal(await current(), first);
  await page.waitForTimeout(1900);
  assert.notEqual(await current(), first);
  await page.keyboard.press('Tab');
  await page.getByRole('button', { name: 'Next step' }).focus();
  const paused = await current();
  await page.waitForTimeout(5700);
  assert.equal(await current(), paused);
  await page.getByRole('button', { name: 'Resume slideshow' }).tap();
  await page.waitForTimeout(5800);
  assert.notEqual(await current(), paused);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const reduced = await current();
  await page.waitForTimeout(5700);
  assert.equal(await current(), reduced);
  await page.getByRole('button', { name: 'Next step' }).tap();
  await page.locator('#' + reduced).waitFor({ state: 'hidden' });
  assert.notEqual(await current(), reduced);
  await page.close();
  console.log('Passed autoplay timing, keyboard focus pause, resume and reduced motion.');
}

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.QA_CHROMIUM, headless: true });
  let server;
  try {
    if (!base) {
      const { preview } = await import('vite');
      server = await preview({ root: path.resolve(__dirname, '..'), preview: { host: '127.0.0.1', port: 5198 } });
      base = 'http://127.0.0.1:' + server.httpServer.address().port;
    }
    for (const width of [320, 375, 390, 768, 1024, 1440]) await checkLayout(browser, width);
    await checkPlayback(browser);
  } finally {
    await browser.close();
    await server?.close();
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
