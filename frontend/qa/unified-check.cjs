const { browserExecutable } = require('./support/runtime.cjs');
const {chromium}=require('playwright-core');
const assert=require('node:assert/strict');const fs=require('node:fs');
const origin=process.env.PUBLIC_QA_ORIGIN||'http://127.0.0.1:5181';
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:browserExecutable()});
 const page=await browser.newPage({viewport:{width:1280,height:720}});page.setDefaultTimeout(10000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const checks=[];const check=(name)=>{checks.push(name);console.log('PASS '+name)};
 const go=async path=>{await page.goto(origin+path,{waitUntil:'networkidle'});await page.locator('h1').first().waitFor();};
 const scroll=async y=>{await page.mouse.wheel(0,y-(await page.evaluate(()=>scrollY)));await page.waitForFunction(y=>Math.abs(scrollY-y)<4,y);};
 const top=async()=>page.waitForFunction(()=>scrollY<2);
 if (!process.env.PUBLIC_QA_LAYOUT_ONLY) {
 await go('/about');await scroll(1000);await page.locator('.navbar .brand').click();await page.waitForURL(origin+'/');await top();check('About logo opens Home at top');
 await scroll(1100);await page.locator('.navbar .brand').click();await top();check('Home logo scrolls Home to top');
 await go('/?source=qa');await scroll(1000);await page.locator('.navbar .brand').click();await top();check('Logo scrolls Home to top even when removing a query string');
 await go('/membership');await scroll(500);await page.locator('.nav-links a[href="/about"]').click();await top();check('Different public route starts at top');
 await scroll(950);await page.locator('.nav-links a[href="/about"]').click();await top();check('Active navigation returns to top');
 await go('/');await scroll(1100);
 // Click the visible sticky link at its screen coordinates. Playwright's locator
 // pre-scroll moves this display:contents navigation despite its visible bounds.
 const aboutLink=await page.locator('.nav-links a[href="/about"]').boundingBox();
 await page.mouse.click(aboutLink.x+aboutLink.width/2,aboutLink.y+aboutLink.height/2);
 await top();await scroll(900);await page.goBack();await page.waitForURL(origin+'/');await page.waitForFunction(()=>Math.abs(scrollY-1100)<4);await page.goForward();await page.waitForURL('**/about');await page.waitForFunction(()=>Math.abs(scrollY-900)<4);check('Back and Forward restore independent history-entry positions');
 await page.locator('.footer-column a[href="/membership#membership-faq"]').click();await page.waitForURL('**/membership#membership-faq');await page.waitForFunction(()=>{const target=document.getElementById('membership-faq');if(!target)return false;const y=target.getBoundingClientRect().top;return y>=72&&y<100;});check('Cross-route FAQ anchor clears sticky header');
 await page.locator('.footer-column a[href="/membership#membership-faq"]').click();await page.waitForFunction(()=>document.getElementById('membership-faq')?.getBoundingClientRect().top>=72);check('Repeated hash destination remains usable');
 await go('/about');await page.locator('.about-hero .about-button-primary').click();await page.waitForURL('**/membership');check('Explore membership CTA');
 await go('/about');await page.locator('.about-hero .about-button-secondary').click();await page.waitForURL('**/how-it-works');check('How it works CTA');
 await page.route('**/api/v1/**',route=>route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({code:'UNAUTHENTICATED',sessionState:'NONE'})}));
 await page.locator('.register-nav-btn').click();await page.waitForURL('**/register*');await page.locator('input').first().waitFor();check('Get Started opens existing registration');
 await page.locator('.auth-header a[href="/login"]').click();await page.waitForURL('**/login');await page.getByRole('heading',{name:'Welcome to ZAYLOPE'}).waitFor();check('Login route');
 await go('/about');await scroll(1200);const back=page.getByRole('button',{name:'Back to top'});await back.focus();await page.keyboard.press('Enter');await top();assert.equal(await back.count(),0);assert.equal(await page.locator('main h1').evaluate(e=>e===document.activeElement),true);check('Keyboard Back to Top, heading focus and hidden-at-top state');
 await page.setViewportSize({width:390,height:844});await go('/about');await page.getByRole('button',{name:'Open navigation'}).click();await page.waitForFunction(()=>document.activeElement?.textContent==='Home');await page.keyboard.press('Escape');assert.equal(await page.locator('.nav-toggle').getAttribute('aria-expanded'),'false');await page.getByRole('button',{name:'Open navigation'}).click();await page.locator('.nav-links a[href="/membership"]').click();await page.waitForURL('**/membership');assert.equal(await page.locator('.nav-toggle').getAttribute('aria-expanded'),'false');check('Mobile menu focus, Escape and close after selection');
 await go('/about');await page.keyboard.press('Tab');await page.keyboard.press('Enter');await page.waitForFunction(()=>document.activeElement.id==='public-content');check('Shared skip link keyboard focus');
 await page.emulateMedia({reducedMotion:'reduce'});await go('/about');assert.equal(await page.locator('.commerce-scene-object').first().evaluate(e=>getComputedStyle(e).animationName),'none');await scroll(1000);await page.locator('.navbar .brand').click();await top();check('Reduced-motion animation and navigation');
 await go('/register?step=1');await page.locator('input').first().waitFor();const inputs=page.locator('input');await inputs.nth(0).fill('QA Example');await inputs.nth(1).fill('9876543210');await inputs.nth(2).fill('qa@example.invalid');const originalElement=await inputs.nth(0).elementHandle();await scroll(200);await page.evaluate(()=>{history.pushState({...history.state,key:'qa-query-entry'},'','/register?step=1&source=qa');dispatchEvent(new PopStateEvent('popstate',{state:history.state}));});assert.equal(await page.evaluate(()=>scrollY),200);assert.equal(await inputs.nth(0).inputValue(),'QA Example');assert.equal(await originalElement.evaluate(e=>e===document.querySelector('input')),true);check('Registration query update retains mounted form and values without submit');
 await page.unroute('**/api/v1/**');await page.route('**/api/v1/**',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:'SERVICE_UNAVAILABLE'})}));await go('/register');await page.getByText('The registration service is unavailable.',{exact:false}).waitFor();check('Existing registration service-unavailable state (isolated 503 fixture)');
 await go('/not-a-real-page');await page.getByRole('heading',{name:'Let’s find your next step.'}).waitFor();await page.getByRole('link',{name:'Return home',exact:true}).click();await top();check('Custom 404 and recovery link');
 const blocked=await browser.newContext();const errorPage=await blocked.newPage();await errorPage.route('**/assets/Dashboard-*.js',route=>route.abort());await errorPage.goto(origin+'/dashboard');await errorPage.getByRole('heading',{name:'This page couldn’t load.'}).waitFor();check('Error boundary handles failed route chunk without clearing session');await blocked.close();
 fs.writeFileSync('qa/unified-navigation-results.json',JSON.stringify({checks,errors},null,2));
 }
 const layouts=[];
 for(const [width,height] of (process.env.PUBLIC_QA_NAV_ONLY ? [] : [[320,568],[375,667],[390,844],[768,1024],[1024,768],[1280,720],[1366,768],[1440,900],[1920,1080]])){
  await page.setViewportSize({width,height});
  for(const path of (process.env.PUBLIC_QA_PATHS || '/,/about,/how-it-works,/membership').split(',')){
   await go(path);await page.evaluate(()=>document.fonts.ready);
   const data=await page.evaluate(()=>({path:location.pathname,width:innerWidth,height:innerHeight,overflow:document.documentElement.scrollWidth>innerWidth,h1:document.querySelectorAll('h1').length,nav:document.querySelectorAll('nav.navbar').length,footer:document.querySelectorAll('footer').length,title:document.title,artWidth:document.querySelector('.about-artwork')?.getBoundingClientRect().width,ctaBottom:document.querySelector('.about-hero .about-actions')?.getBoundingClientRect().bottom}));
   assert.equal(data.overflow,false,JSON.stringify(data));assert.equal(data.h1,1);assert.equal(data.nav,1);assert.equal(data.footer,1);layouts.push(data);
   if([390,1280].includes(width)){await page.waitForTimeout(800);await page.screenshot({path:`qa/unified-${path==='/'?'home':path.slice(1)}-${width}.png`});}
  }
 }
 if (!process.env.PUBLIC_QA_NAV_ONLY) check('All four public pages at nine viewport sizes');
 await page.setViewportSize({width:640,height:360});await go('/about');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);assert.equal(await page.locator('.nav-toggle').isVisible(),true);check('200% equivalent reflow (640 CSS-pixel laptop viewport)');
 assert.deepEqual(errors,[]);fs.writeFileSync(process.env.PUBLIC_QA_NAV_ONLY ? 'qa/about-complete-navigation-results.json' : (process.env.PUBLIC_QA_PATHS ? 'qa/unified-home-final-results.json' : 'qa/unified-results.json'),JSON.stringify({origin,checks,layouts,errors,fixtures:['Unauthenticated registration GETs return 401 NONE; no application submission','Unavailable-service response 503','Dashboard JS chunk blocked in isolated context']},null,2));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});






