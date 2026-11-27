const { artifactPath } = require('./support/runtime.cjs');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { chromium } = require('playwright-core');

async function checkWidths(page, base) {
  const layouts = [];
  for (const width of [320, 375, 390, 768, 1024, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: width < 768 ? 844 : 900 });
    await page.goto(base + '/membership', { waitUntil: 'domcontentloaded' });
    await page.locator('.member-art img').evaluate(image => image.decode());
    await page.evaluate(() => document.fonts.ready);
    const result = await page.evaluate(() => {
      const main = document.querySelector('.membership-page');
      const bounds = selector => main.querySelector(selector).getBoundingClientRect();
      const art = bounds('.member-art'), photo = bounds('.member-art img'), card = bounds('.member-pass');
      const isInside = rect => rect.x >= 0 && rect.right <= innerWidth && rect.y >= art.y && rect.bottom <= art.bottom + 1;
      const copyOffsets = selector => [...main.querySelectorAll(selector)].map(el =>
        Math.round(el.getBoundingClientRect().top - el.parentElement.getBoundingClientRect().top));
      return {
        overflow: document.documentElement.scrollWidth - innerWidth,
        clipped: [...main.querySelectorAll('h1,h2,h3,p,dt,dd,a,button')].filter(el => el.scrollWidth > el.clientWidth + 2 && el.clientWidth > 0).map(el => el.tagName + " " + el.textContent),
        imageFits: isInside(photo) && isInside(card),
        listTextFits: [...main.querySelectorAll('li')].every(item => {
          const range = document.createRange();
          range.selectNodeContents(item);
          const text = range.getBoundingClientRect(), box = item.getBoundingClientRect();
          return text.left >= box.left - 1 && text.right <= box.right + 1;
        }),
        heroColumns: art.x >= bounds('.member-hero-copy').right,
        benefitColumns: getComputedStyle(main.querySelector('.member-benefits')).gridTemplateColumns.split(' ').length,
        timelineColumns: getComputedStyle(main.querySelector('.member-timeline')).gridTemplateColumns.split(' ').length,
        sections: main.querySelectorAll(':scope > section').length,
        height: document.documentElement.scrollHeight,
        feeTop: bounds('.member-fee').top,
        image: main.querySelector('.member-art img').currentSrc.split('/').pop(),
        targets: [...main.querySelectorAll('a,button')].every(el => el.offsetWidth >= 44 && el.offsetHeight >= 44),
        faqHeights: [...main.querySelectorAll('.clarity-item button')].map(el => el.offsetHeight),
        benefitHeights: [...main.querySelectorAll('.member-benefits article')].map(el => el.offsetHeight),
        benefitCopyOffsets: copyOffsets('.member-benefits p'),
        timelineCopyOffsets: copyOffsets('.member-timeline p'),
      };
    });
    assert.equal(result.overflow, 0);
    assert.deepEqual(result.clipped, []);
    assert.ok(result.imageFits);
    assert.ok(result.listTextFits);
    assert.ok(result.targets);
    assert.ok(result.feeTop < 844);
    assert.ok(result.faqHeights.every(height => height >= 64 && height <= 72));
    assert.ok(Math.max(...result.benefitHeights) - Math.min(...result.benefitHeights) <= 1);
    for (const offsets of [result.benefitCopyOffsets, result.timelineCopyOffsets]) {
      assert.ok(Math.max(...offsets) - Math.min(...offsets) <= 1, width + 'px misaligned copy: ' + offsets);
    }
    assert.equal(result.sections, 8);
    assert.equal(result.heroColumns, width >= 768);
    assert.equal(result.benefitColumns, width > 1000 ? 4 : width > 520 ? 2 : 1);
    assert.equal(result.timelineColumns, width > 1000 ? 5 : 1);
    assert.equal(await page.locator('.navbar .nav-links [aria-current="page"]').textContent(), 'Membership');
    assert.equal(await page.locator('main :is(form,input), main a[href="#"]').count(), 0);
    if ([390, 1440].includes(width)) {
      await page.screenshot({ path: path.join(__dirname, 'membership-redesign-' + width + '.png'), fullPage: true });
      await scan(page, 'full page at ' + width);
    }
    layouts.push({ width, ...result });
    console.log(width + 'px: layout, overflow, image bounds, active route and content passed.');
  }
  fs.writeFileSync(artifactPath('membership-redesign-layout.json'), JSON.stringify(layouts, null, 2) + '\n');
}

async function checkMotion(page) {
  const card = page.locator('.member-pass');
  const benefit = page.locator('.member-benefits article').first();
  const timeline = page.locator('.member-timeline');
  const flow = page.locator('.member-flow');
  assert.equal(await card.evaluate(el => getComputedStyle(el).animationName), 'none');
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.reload();
  assert.equal(await card.evaluate(el => getComputedStyle(el).animationName), 'member-float');
  await benefit.hover();
  await page.waitForFunction(() => {
    const style = getComputedStyle(document.querySelector('.member-benefits article:hover'));
    return new DOMMatrix(style.transform).m42 === -3;
  });
  await timeline.scrollIntoViewIfNeeded();
  await timeline.locator('li.member-entered').last().waitFor();
  await flow.scrollIntoViewIfNeeded();
  await flow.locator('li.member-entered').last().waitFor();
  assert.deepEqual(await flow.locator('li').evaluateAll(items => items.map(el => getComputedStyle(el).animationDelay)),
    ['0s', '0.035s', '0.07s', '0.105s']);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  assert.equal(await flow.locator('li').last().evaluate(el => getComputedStyle(el).animationName), 'none');
}

async function scan(page, label) {
  await page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
  const issues = await page.evaluate(async () => {
    const { violations } = await window.axe.run(document, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'] },
    });
    return violations.map(({ id, nodes }) => ({ id, elements: nodes.map(node => node.target) }));
  });
  assert.deepEqual(issues, [], label + ' accessibility results: ' + JSON.stringify(issues));
}

async function checkFaq(page) {
  const questions = page.locator('#membership-faq .clarity-item button');
  assert.equal(await questions.count(), 7);
  await questions.first().focus();
  for (let index = 0; index < 7; index++) {
    const question = questions.nth(index);
    await page.keyboard.press('Enter');
    assert.equal(await question.getAttribute('aria-expanded'), 'true');
    const answerId = await question.getAttribute('aria-controls');
    const open = await page.evaluate(id => {
      const el = document.getElementById(id);
      return el?.getAttribute('aria-hidden') === 'false' && !el.inert && el.textContent.length > 0;
    }, answerId);
    assert.ok(open);
    assert.equal(await page.locator('#membership-faq [aria-expanded="true"]').count(), 1);
    assert.equal(await question.evaluate(el => getComputedStyle(el).outlineStyle), 'solid');
    if (index < 6) await page.keyboard.press('ArrowDown');
  }
  await scan(page, 'expanded answer');
  await page.keyboard.press('Space');
  assert.equal(await questions.last().getAttribute('aria-expanded'), 'false');
  await page.keyboard.press('Home');
  assert.ok(await questions.first().evaluate(el => el === document.activeElement));
  await page.keyboard.press('End');
  assert.ok(await questions.last().evaluate(el => el === document.activeElement));
}

async function checkRoutes(page, base) {
  for (const link of ['.member-hero .zp-primary', '.member-hero .zp-secondary', '.member-final .zp-lime', '.member-final .member-outline']) {
    const action = page.locator(link);
    const href = await action.getAttribute('href');
    assert.ok(['/register', '/how-it-works'].includes(href));
    await action.click();
    await page.waitForURL(url => url.pathname === href);
    await page.locator(href === '/register' ? '#registration-title' : '#how-title').waitFor();
    await page.goBack();
    await page.locator('#member-title').waitFor();
  }
  await page.locator('.member-text-link').focus();
  await page.keyboard.press('Enter');
  await page.waitForURL(base + '/membership#membership-conditions');
  assert.ok(await page.locator('#membership-conditions').evaluate(el => el === document.activeElement));
  await page.locator('footer a[href$="#membership-faq"]').click();
  await page.waitForURL(base + '/membership#membership-faq');
  await page.getByRole('button', { name: 'Back to top' }).click();
  await page.waitForFunction(() => scrollY < 3 && document.activeElement === document.querySelector('h1'));
  await page.locator('footer a[href$="#membership-faq"]').click();
  await page.locator('.navbar .brand').click();
  await page.waitForURL(base + '/');
  await page.waitForFunction(() => scrollY < 3);
  await page.goBack();
  await page.waitForURL(base + '/membership#membership-faq');
  await page.waitForFunction(() => document.querySelector('#membership-faq').getBoundingClientRect().top < innerHeight);
  await page.goForward();
  await page.waitForURL(base + '/');
  await page.waitForFunction(() => scrollY < 3);
}

async function checkShortViewportFaq(page) {
  const originalViewport = page.viewportSize();
  await page.setViewportSize({ width: 375, height: 500 });
  const first = page.locator('#membership-faq button').first();
  await first.focus();
  await first.evaluate(button => button.scrollIntoView({ block: 'center' }));
  for (const key of ['End', 'ArrowDown', 'ArrowUp', 'Home']) {
    await page.keyboard.press(key);
    assert.ok(await page.evaluate(() => {
      const rect = document.activeElement.getBoundingClientRect();
      return rect.top >= document.querySelector('.navbar').getBoundingClientRect().bottom - 1
        && rect.bottom <= innerHeight + 1;
    }), key + ' should keep the focused question visible');
  }
  await page.setViewportSize(originalViewport);
}

(async () => {
  const { preview } = await import('vite');
  const server = await preview({ root: path.resolve(__dirname, '..'), preview: { host: '127.0.0.1', port: 5198 } });
  const base = 'http://127.0.0.1:' + server.httpServer.address().port;
  let browser;
  try {
    browser = await chromium.launch({ executablePath: process.env.QA_CHROMIUM, headless: true });
    const page = await browser.newPage({ reducedMotion: 'reduce' });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await checkWidths(page, base);
    await checkFaq(page);
    await checkShortViewportFaq(page);
    await checkMotion(page);
    await checkRoutes(page, base);
    const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, hasTouch: true, reducedMotion: 'reduce' });
    await mobile.goto(base + '/membership');
    const toggle = mobile.locator('.nav-toggle');
    await toggle.tap();
    await mobile.locator('.nav-open').waitFor();
    assert.equal(await toggle.getAttribute('aria-expanded'), 'true');
    await mobile.keyboard.press('Escape');
    await mobile.locator('.nav-open').waitFor({ state: 'detached' });
    await toggle.tap();
    await mobile.locator('.nav-open a[href="/about"]').tap();
    await mobile.waitForURL(base + '/about');
    await mobile.locator('.nav-open').waitFor({ state: 'detached' });
    const aboutQuestion = mobile.locator('#about-faq .clarity-item button').first();
    await aboutQuestion.tap();
    await mobile.locator('#about-faq [aria-expanded="true"]').waitFor();
    await mobile.goBack();
    await mobile.locator('#membership-faq .clarity-item button').first().tap();
    await mobile.locator('#membership-faq [aria-expanded="true"]').waitFor();
    assert.deepEqual(errors, []);
    console.log('Passed keyboard and touch FAQ, reduced motion, CTAs, anchors, Home logo, history, About FAQ and mobile menu. No WCAG A/AA violations detected by axe in the scanned states.');
  } finally {
    await browser?.close();
    await server.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
