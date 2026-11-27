const { artifactPath, browserExecutable } = require('./support/runtime.cjs');
const {chromium} = require('playwright-core');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const origin = process.env.ABOUT_QA_ORIGIN || 'http://127.0.0.1:5180';
(async () => {
 const browser = await chromium.launch({headless:true, executablePath:browserExecutable()});
 const page = await browser.newPage();
 page.setDefaultTimeout(10000);
 const errors = [];
 page.on('pageerror', error => errors.push(error.message));
 const layouts = process.env.ABOUT_QA_INTERACTIONS_ONLY ? JSON.parse(fs.readFileSync(artifactPath('about-layout-results.json'), 'utf8')) : [];
 for (const width of (process.env.ABOUT_QA_INTERACTIONS_ONLY ? [] : [320,375,390,768,1024,1280,1440,1920])) {
  await page.setViewportSize({width,height:1000});
  await page.goto(origin+'/about', {waitUntil:'networkidle'});
  await page.evaluate(() => document.fonts.ready);
  // Exercise the complete scroll flow so every observer has fired before capture.
  for (const section of await page.locator('[data-about-reveal]').all()) { await section.scrollIntoViewIfNeeded(); await page.waitForTimeout(40); }
  const layout = await page.evaluate(() => ({width:innerWidth,overflow:document.documentElement.scrollWidth > innerWidth,h1:document.querySelectorAll('h1').length,main:document.querySelectorAll('main').length,footer:document.querySelectorAll('footer').length,workflow:getComputedStyle(document.querySelector('.about-workflow')).gridTemplateColumns,tooWide:[...document.querySelectorAll('main *')].filter(e=>e.getBoundingClientRect().right>innerWidth+1).map(e=>e.className).filter(Boolean),smallTargets:[...document.querySelectorAll('main a,footer a')].filter(e=>e.getBoundingClientRect().height<44).map(e=>e.textContent.trim())}));
  assert.equal(layout.overflow,false, 'overflow at '+width);
  assert.equal(layout.h1,1);assert.equal(layout.main,1);assert.equal(layout.footer,1);
  assert.deepEqual(layout.smallTargets,[]);
  layouts.push(layout);
  await page.evaluate(() => scrollTo(0,0));await page.waitForTimeout(650);
  await page.screenshot({path:artifactPath(`about-${width}.png`),fullPage:true});
 }
 fs.writeFileSync(artifactPath('about-layout-results.json'),JSON.stringify(layouts,null,2));
 await page.goto(origin+'/about');
 assert.match(await page.title(),/^About ZayloPe/);
 assert.ok(await page.locator('meta[property="og:description"]').getAttribute('content'));
 assert.equal(await page.locator('.nav-links a[aria-current="page"]').getAttribute('href'),'/about');
 const links = await page.locator('main a,footer a').evaluateAll(elements=>elements.map(e=>({label:e.textContent.trim(),href:e.getAttribute('href')})));
 for (const href of [...new Set(links.map(e=>e.href))].filter(href=>href.startsWith('/'))) {
  await page.goto(origin+'/about');
  await page.locator(`main a[href="${href}"], footer a[href="${href}"]`).first().click();
  await page.waitForURL(origin+href);
  await page.locator('h1, h2').first().waitFor({state:'visible'});
  if(href.includes('#')) assert.equal(await page.locator(href.slice(href.indexOf('#'))).count(),1);
 }
 await page.setViewportSize({width:390,height:844});
 await page.goto(origin+'/about');
 await page.getByRole('button',{name:'Open navigation'}).click();
 await page.waitForTimeout(300);
 assert.equal(await page.locator('.nav-toggle').getAttribute('aria-expanded'),'true');
 assert.equal(await page.locator('.nav-links a').first().evaluate(e=>e===document.activeElement),true);
 await page.keyboard.press('Escape');
 assert.equal(await page.locator('.nav-toggle').getAttribute('aria-expanded'),'false');
 assert.equal(await page.locator('.nav-toggle').evaluate(e=>e===document.activeElement),true);
 await page.getByRole('button',{name:'Open navigation'}).click();
 await page.locator('.nav-links a[href="/membership"]').click();await page.waitForURL('**/membership');
 assert.equal(await page.locator('.nav-toggle').getAttribute('aria-expanded'),'false');
 await page.goto(origin+'/about');await page.keyboard.press('Tab');
 assert.equal(await page.locator('.about-skip').evaluate(e=>e===document.activeElement),true);
 await page.keyboard.press('Enter');assert.equal(await page.locator('#about-main').evaluate(e=>e===document.activeElement),true);
 await page.emulateMedia({reducedMotion:'reduce'});await page.reload();
 const reduced = await page.locator('.commerce-node').first().evaluate(e=>({animation:getComputedStyle(e).animationName,transition:getComputedStyle(e).transitionDuration}));
 assert.equal(reduced.animation,'none');assert.equal(reduced.transition,'0s');
 const preserved=[];
 for (const route of ['/','/how-it-works','/membership','/register','/login']) {
  await page.goto(origin+route,{waitUntil:'domcontentloaded'});
  const data=await page.evaluate(()=>({route:location.pathname,heading:document.querySelector('h1, h2')?.textContent,footer:document.querySelectorAll('.zaylope-footer').length,overflow:document.documentElement.scrollWidth>innerWidth,title:document.title}));
  assert.ok(data.heading,route);assert.equal(data.overflow,false,route);assert.ok(!data.title.startsWith('About ZayloPe'),route);
  preserved.push(data);
  if(route==='/register') {
   assert.equal(await page.locator('input').count()>0,true);
   await page.screenshot({path:artifactPath('about-regression-register.png'),fullPage:true});
  }
 }
 assert.deepEqual(errors,[]);
 await page.goto(origin+'/about');
 await page.evaluate(()=>{for(const el of document.querySelectorAll('[data-about-reveal]')) el.classList.remove('about-entered');});
 assert.equal(await page.locator('.about-hero h1').evaluate(e=>getComputedStyle(e).opacity),'1');
 fs.writeFileSync(artifactPath('about-results.json'),JSON.stringify({origin,layouts,links,reduced,preserved,errors,checks:{mobileMenu:true,escapeFocus:true,skipLink:true,metadata:true,activeNavigation:true,contentVisibleWithoutReveal:true}},null,2));
 console.log(JSON.stringify({viewports:layouts.map(x=>x.width),links:links.length,reduced,preserved,errors}));
 await browser.close();
})().catch(error=>{console.error(error);process.exit(1)});



