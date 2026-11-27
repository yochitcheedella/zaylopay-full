import qaRuntime from '../qa/support/runtime.cjs';
import { createServer } from 'vite';
import { chromium } from 'playwright-core';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const screenshotsDir = qaRuntime.artifactPath('responsive-audit');
if (!fs.existsSync(screenshotsDir)) {
  fs.mkdirSync(screenshotsDir, { recursive: true });
}

const BREAKPOINTS = [320, 375, 390, 430, 768, 820, 1024, 1280, 1440];
const ROUTES = [
  { name: 'Home', path: '/' },
  { name: 'About', path: '/about' },
  { name: 'How It Works', path: '/how-it-works' },
  { name: 'Membership', path: '/membership' },
  { name: 'Login', path: '/login' },
  { name: 'Register Step 1', path: '/register?step=1' },
  { name: 'Register Step 2', path: '/register?step=2' },
  { name: 'Register Step 3', path: '/register?step=3' },
  { name: 'Register Step 4', path: '/register?step=4' },
  { name: 'Dashboard', path: '/dashboard' },
];

async function run() {
  console.log('========================================================');
  console.log('       ZAYLOPE COMPLETE RESPONSIVE AUDIT SUITE          ');
  console.log('========================================================\n');

  console.log('1. Starting local Vite preview server...');
  const server = await createServer({
    root: path.resolve(__dirname, '..'),
    server: { host: '127.0.0.1', port: 5188 },
  });
  await server.listen();
  const address = server.httpServer.address();
  const baseUrl = `http://127.0.0.1:${address.port}`;
  console.log(`✓ Local server ready at ${baseUrl}\n`);

  let browser;
  try {
    browser = await chromium.launch({ channel: 'chrome', headless: true });
  } catch {
    browser = await chromium.launch({ headless: true });
  }

  const overflowResults = [];
  const _accessibilityResults = [];
  const screenshots = [];

  // ----------------------------------------------------
  // TEST 1: HORIZONTAL OVERFLOW ACROSS ALL BREAKPOINTS
  // ----------------------------------------------------
  console.log('2. Testing horizontal overflow across all 9 breakpoints (320px to 1440px)...');

  for (const route of ROUTES) {
    process.stdout.write(`- ${route.name.padEnd(20)}: `);
    const context = await browser.newContext();
    const page = await context.newPage();

    for (const width of BREAKPOINTS) {
      await page.setViewportSize({ width, height: 850 });
      await page.goto(`${baseUrl}${route.path}`, { waitUntil: 'load' });
      await page.waitForTimeout(60);

      const audit = await page.evaluate(() => {
        const doc = document.documentElement;
        const winWidth = window.innerWidth;
        const scrollW = doc.scrollWidth;
        const clientW = doc.clientWidth;
        const hasOverflow = scrollW > clientW;

        const offenders = [];
        if (hasOverflow) {
          const allEls = Array.from(document.querySelectorAll('*'));
          for (const el of allEls) {
            const r = el.getBoundingClientRect();
            if (r.right > winWidth + 1.5) {
              offenders.push({
                tag: el.tagName.toLowerCase(),
                className: (typeof el.className === 'string' ? el.className : '').slice(0, 40),
                excess: Math.round(r.right - winWidth),
              });
            }
          }
        }

        return {
          winWidth,
          scrollW,
          clientW,
          hasOverflow,
          offenders: offenders.slice(0, 3),
        };
      });

      if (audit.hasOverflow) {
        process.stdout.write(`❌[${width}px: +${audit.scrollW - audit.clientW}px] `);
        overflowResults.push({
          route: route.name,
          path: route.path,
          width,
          scrollW: audit.scrollW,
          clientW: audit.clientW,
          offenders: audit.offenders,
        });
      } else {
        process.stdout.write(`✓${width} `);
      }

      // Capture representative screenshots for report
      if ([375, 768, 1440].includes(width) && ['Home', 'About', 'How It Works', 'Membership', 'Login', 'Register Step 1', 'Register Step 3'].includes(route.name)) {
        const safeName = route.name.toLowerCase().replace(/[^a-z0-9]/g, '-');
        const filename = `${safeName}-${width}px.png`;
        const filepath = path.join(screenshotsDir, filename);
        await page.screenshot({ path: filepath, fullPage: false });
        screenshots.push({ route: route.name, width, filename, filepath });
      }
    }
    console.log('');
    await context.close();
  }

  // ----------------------------------------------------
  // TEST 2: MOBILE NAVIGATION & KEYBOARD ACCESSIBILITY
  // ----------------------------------------------------
  console.log('\n3. Testing mobile navigation drawer, backdrop, and keyboard escape...');
  const navContext = await browser.newContext({ viewport: { width: 375, height: 667 } });
  const navPage = navContext.newPage();
  const page = await navPage;
  await page.goto(`${baseUrl}/`, { waitUntil: 'load' });

  // Check toggle button
  const toggle = page.locator('.nav-toggle');
  await toggle.waitFor({ state: 'visible' });
  const initialExpanded = await toggle.getAttribute('aria-expanded');
  console.log(`   Initial nav-toggle aria-expanded: ${initialExpanded} (expected "false")`);

  // Click toggle to open
  await toggle.click();
  await page.waitForTimeout(200);
  const afterOpenExpanded = await toggle.getAttribute('aria-expanded');
  const panelVisible = await page.locator('#primary-navigation').isVisible();
  console.log(`   After click nav-toggle aria-expanded: ${afterOpenExpanded}, panel visible: ${panelVisible}`);

  // Test escape key to close
  await page.keyboard.press('Escape');
  await page.waitForTimeout(200);
  const afterEscExpanded = await toggle.getAttribute('aria-expanded');
  console.log(`   After Escape key aria-expanded: ${afterEscExpanded} (expected "false")`);

  const navTestPassed = initialExpanded === 'false' && afterOpenExpanded === 'true' && afterEscExpanded === 'false';
  console.log(`   Mobile navigation test: ${navTestPassed ? 'PASSED ✓' : 'FAILED ❌'}`);
  await navContext.close();

  // ----------------------------------------------------
  // TEST 3: ORIENTATION (PORTRAIT & LANDSCAPE)
  // ----------------------------------------------------
  console.log('\n4. Testing orientation changes...');
  const orientContext = await browser.newContext();
  const orientPage = await orientContext.newPage();

  // Mobile landscape: iPhone 14 landscape (844 x 390)
  await orientPage.setViewportSize({ width: 844, height: 390 });
  await orientPage.goto(`${baseUrl}/`, { waitUntil: 'load' });
  const homeLandscapeOverflow = await orientPage.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);

  // Tablet portrait: iPad (768 x 1024)
  await orientPage.setViewportSize({ width: 768, height: 1024 });
  await orientPage.goto(`${baseUrl}/membership`, { waitUntil: 'load' });
  const memberPortraitOverflow = await orientPage.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);

  console.log(`   iPhone landscape (844x390) Home overflow: ${homeLandscapeOverflow ? 'FAIL ❌' : 'PASS ✓'}`);
  console.log(`   iPad portrait (768x1024) Membership overflow: ${memberPortraitOverflow ? 'FAIL ❌' : 'PASS ✓'}`);
  await orientContext.close();

  // ----------------------------------------------------
  // TEST 4: 200% ZOOM SIMULATION
  // ----------------------------------------------------
  console.log('\n5. Testing 200% text/viewport zoom (1280px / 2 = 640px equivalent with scaled content)...');
  const zoomContext = await browser.newContext({
    viewport: { width: 640, height: 450 },
    deviceScaleFactor: 2,
  });
  const zoomPage = await zoomContext.newPage();
  await zoomPage.goto(`${baseUrl}/register?step=3`, { waitUntil: 'load' });
  const zoomOverflow = await zoomPage.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  console.log(`   200% Zoom Register Step 3 overflow: ${zoomOverflow ? 'FAIL ❌' : 'PASS ✓'}`);
  await zoomContext.close();

  // ----------------------------------------------------
  // SUMMARY REPORT
  // ----------------------------------------------------
  console.log('\n========================================================');
  console.log('                   AUDIT SUMMARY REPORT                 ');
  console.log('========================================================');
  console.log(`Total Breakpoints Tested: ${BREAKPOINTS.length} (${BREAKPOINTS.join(', ')} px)`);
  console.log(`Total Routes Tested:      ${ROUTES.length}`);
  console.log(`Total Viewport Checks:    ${BREAKPOINTS.length * ROUTES.length}`);
  console.log(`Overflow Violations:      ${overflowResults.length}`);
  console.log(`Mobile Nav Accessibility: ${navTestPassed ? 'PASSED' : 'FAILED'}`);
  console.log(`Screenshots Generated:    ${screenshots.length} files in frontend/screenshots/`);

  if (overflowResults.length === 0) {
    console.log('\nSUCCESS: 100% of tested routes passed horizontal overflow tests at ALL required breakpoints.');
  } else {
    console.log('\nFAILURES DETECTED:');
    for (const fail of overflowResults) {
      console.log(`- ${fail.route} @ ${fail.width}px: +${fail.scrollW - fail.clientW}px`);
    }
  }

  await browser.close();
  await server.close();
}

run().catch(err => {
  console.error('Audit failed:', err);
  process.exit(1);
});
