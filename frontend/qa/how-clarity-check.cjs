const { artifactPath, browserExecutable } = require('./support/runtime.cjs');
const {chromium} = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');
(async () => {
 const browser = await chromium.launch({headless:true,executablePath:browserExecutable()});
 const page = await browser.newPage();
 const errors=[]; page.on('pageerror',e=>errors.push(e.message));
 const layouts=[];
 for(const [width,height] of [[1440,900],[1024,768],[768,1024],[375,844]]) {
  await page.setViewportSize({width,height}); await page.emulateMedia({reducedMotion:'reduce'});
  await page.goto('http://127.0.0.1:5181/how-it-works',{waitUntil:'networkidle'});
  await page.locator('.how-hero img').evaluate(e=>e.decode());
  const layout=await page.evaluate(()=>{
   const cards=[...document.querySelectorAll('.how-step-grid button')].map(e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,height:r.height};});
   const copy=document.querySelector('.how-hero-copy').getBoundingClientRect(),art=document.querySelector('.how-hero-visual').getBoundingClientRect();
   return {width:innerWidth,height:innerHeight,pageHeight:document.documentElement.scrollHeight,overflow:document.documentElement.scrollWidth>innerWidth,cards,overlap:copy.x<art.right&&copy.right>art.x&&copy.y<art.bottom&&copy.bottom>art.y,clipped:[...document.querySelectorAll('.how-guide :is(h1,h2,h3,p,.how-step-title,.how-step-summary)')].filter(e=>e.checkVisibility()&&e.clientWidth>1&&e.scrollWidth>e.clientWidth+2).map(e=>e.textContent),image:document.querySelector('.how-hero img').naturalWidth,sections:document.querySelectorAll('.how-guide > section').length};
  });
  assert.equal(layout.overflow,false); assert.equal(layout.overlap,false); assert.deepEqual(layout.clipped,[]); assert.equal(layout.image,1200);assert.equal(layout.sections,6);
  if(width>=1024){assert.ok(layout.cards.every(c=>c.y===layout.cards[0].y));assert.ok(layout.cards.every(c=>c.height===layout.cards[0].height));}else assert.ok(layout.cards.every((c,i)=>i===0||c.y>layout.cards[i-1].y));
  assert.equal(await page.locator('.how-preview').count(),0);
  layouts.push(layout);
  await page.screenshot({path:artifactPath(`how-clarity-hero-${width}.png`)});
  await page.locator('.how-hero .how-primary').click();await page.waitForURL('**/how-it-works#how-process');
  await page.waitForFunction(()=>document.querySelector('#how-process-title').getBoundingClientRect().top>=document.querySelector('.navbar').getBoundingClientRect().bottom);
  await page.evaluate(()=>window.dispatchEvent(new Event('zaylope:cancel-restoration')));
  const steps=page.locator('.how-step-grid button');
  for(let i=0;i<5;i++){await steps.nth(i).click();assert.equal(await steps.nth(i).getAttribute('aria-pressed'),'true');assert.equal(await page.locator('.how-step-grid [aria-pressed=true]').count(),1);assert.equal(await page.locator('.how-detail-copy strong').textContent(),await steps.nth(i).locator('.how-step-title').textContent());}
  assert.equal(await page.locator('.how-journey-controls button').last().isDisabled(),true);
  for(let i=3;i>=0;i--)await page.locator('.how-journey-controls button').first().click();
  assert.equal(await page.locator('.how-journey-controls button').first().isDisabled(),true);
  await page.waitForFunction(()=>document.activeElement===document.querySelector('.how-step-detail'));
  await page.locator('#how-process').screenshot({path:artifactPath(`how-clarity-process-${width}.png`),style:'.navbar,.public-skip,.public-back-top{visibility:hidden!important}'});
  for(let i=0;i<3;i++){await page.locator('.how-faq-list summary').nth(i).click();assert.equal(await page.locator('.how-faq-list details[open]').count(),1);}
  await page.locator('.how-faq-list summary').last().click();assert.equal(await page.locator('.how-faq-list details[open]').count(),0);
  if(width===375||width===1440)await page.screenshot({path:artifactPath(`how-clarity-full-${width}.png`),fullPage:true,style:'.navbar,.public-skip,.public-back-top{visibility:hidden!important}'});
 }
 await page.setViewportSize({width:1440,height:900});await page.goto('http://127.0.0.1:5181/how-it-works');
 const steps=page.locator('.how-step-grid button');await steps.first().focus();await page.keyboard.press('ArrowLeft');assert.equal(await steps.last().getAttribute('aria-pressed'),'true');await page.keyboard.press('Home');await page.keyboard.press('ArrowRight');assert.equal(await steps.nth(1).getAttribute('aria-pressed'),'true');assert.equal(await steps.nth(1).evaluate(e=>getComputedStyle(e).outlineStyle),'solid');await page.keyboard.press('End');assert.equal(await steps.last().getAttribute('aria-pressed'),'true');
 const faq=page.locator('.how-faq-list summary').first();await faq.focus();await page.keyboard.press('Enter');assert.equal(await page.locator('.how-faq-list details').first().evaluate(e=>e.open),true);await page.keyboard.press('Space');assert.equal(await page.locator('.how-faq-list details').first().evaluate(e=>e.open),false);
 await page.emulateMedia({reducedMotion:'no-preference'});await steps.first().click();assert.equal(await page.locator('.how-detail-copy').evaluate(e=>getComputedStyle(e).animationName),'how-arrive');await page.emulateMedia({reducedMotion:'reduce'});assert.equal(await page.locator('.how-detail-copy').evaluate(e=>getComputedStyle(e).animationName),'none');assert.equal(await page.locator('.how-step-grid button').first().evaluate(e=>getComputedStyle(e).transitionDuration),'0s');
 for(const [selector,path]of [['.how-hero .how-secondary','/membership'],['.how-final .how-lime','/membership'],['.how-final .how-on-dark','/register'],['.how-login','/login']]){
  const token=await page.evaluate(()=>window.__clarityProbe=Math.random());await page.locator(selector).click();await page.waitForURL(u=>u.pathname===path);assert.equal(await page.evaluate(()=>window.__clarityProbe),token);await page.goBack();await page.waitForURL(u=>u.pathname==='/how-it-works');await page.locator('.how-hero').waitFor();
 }
 await page.locator('.how-faq-layout .how-text-link').click();await page.waitForURL('**/membership#membership-faq');await page.waitForFunction(()=>{const e=document.querySelector('#membership-faq h2');return e&&e.getBoundingClientRect().top>=document.querySelector('.navbar').getBoundingClientRect().bottom});
 await page.goto('http://127.0.0.1:5181/how-it-works#how-process');await page.waitForFunction(()=>{const e=document.querySelector('#how-process-title');return e&&e.getBoundingClientRect().top>=document.querySelector('.navbar').getBoundingClientRect().bottom});
 await page.evaluate(()=>window.dispatchEvent(new Event('zaylope:cancel-restoration'))); for(let y=1000;y<5000;y+=160){await page.evaluate(y=>scrollTo(0,y),y);await page.waitForTimeout(40);if(await page.locator('.public-back-top').isVisible())break;} await page.locator('.public-back-top').click();await page.waitForFunction(()=>scrollY<5);assert.equal(await page.locator('h1').evaluate(e=>document.activeElement===e),true);
 await page.setViewportSize({width:375,height:844});await page.locator('.nav-toggle').click();assert.equal(await page.locator('.nav-toggle').getAttribute('aria-expanded'),'true');await page.keyboard.press('Escape');assert.equal(await page.locator('.nav-toggle').getAttribute('aria-expanded'),'false');await page.locator('.nav-toggle').click();await page.locator('.nav-links a[href="/membership"]').click();await page.waitForURL(u=>u.pathname==='/membership');assert.equal(await page.locator('.nav-toggle').getAttribute('aria-expanded'),'false');await page.goBack();await page.waitForURL(u=>u.pathname==='/how-it-works');await page.goForward();await page.waitForURL(u=>u.pathname==='/membership');
 const context=await browser.newContext({viewport:{width:375,height:844},hasTouch:true,isMobile:true,reducedMotion:'reduce'});const mobile=await context.newPage();await mobile.goto('http://127.0.0.1:5181/how-it-works');await mobile.locator('.how-step-grid button').last().tap();assert.equal(await mobile.locator('.how-step-grid button').last().getAttribute('aria-pressed'),'true');
 assert.deepEqual(errors,[]);
 fs.writeFileSync(artifactPath('how-clarity-results.json'),JSON.stringify({layouts,allStepSelection:true,previousNext:true,disabledBoundaries:true,keyboard:true,faq:true,anchor:true,directAnchor:true,spaRoutes:true,backForward:true,mobileNavigation:true,touch:true,reducedMotion:true,backToTop:true,errors},null,2));
 await browser.close();console.log('PASS: four widths, five steps, boundaries, keyboard/touch, FAQ, anchors, CTA routes, mobile menu, Back/Forward, Back to Top and reduced motion.');
})().catch(e=>{console.error(e);process.exit(1)});

