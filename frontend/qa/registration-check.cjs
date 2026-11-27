const { artifactPath, browserExecutable } = require('./support/runtime.cjs');
const {chromium}=require('playwright-core');const assert=require('node:assert/strict');const fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:browserExecutable()});
 const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));let mode='new';let submitCount=0;let registered=false;let statusCount=0;let bothVerified=false;
 const state=()=>({serverTime:new Date().toISOString(),deliveryMode:'MOCK',mobile:{maskedDestination:'+91 •••••• 3210',verified:bothVerified,enabled:true},email:{maskedDestination:'t••••@example.test',verified:bothVerified,enabled:true}});
 await page.route('http://localhost:8080/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname;const json=(body,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
  if(path.endsWith('/verification/status')){
   statusCount++;
   if(registered||mode==='valid')return json(state());
   if(mode==='expired')return json({code:'UNAUTHENTICATED',sessionState:'EXPIRED_OR_INVALID'},401);
   if(mode==='database')return json({code:'DATABASE_UNAVAILABLE'},503);
   if(mode==='server')return json({code:'INTERNAL_ERROR'},500);
   if(mode==='service')return route.abort('connectionrefused');
   return json({code:'UNAUTHENTICATED',sessionState:'NONE'},401);
  }
  if(path.endsWith('/applicants/register')){submitCount++;await new Promise(resolve=>setTimeout(resolve,250));assert.equal(route.request().postDataJSON().mobileNumber,'9876543210');if(mode==='duplicate')return json({code:'EMAIL_ALREADY_REGISTERED',detail:'Email already registered.'},409);registered=true;return json({applicantReference:'APP-TEST',message:'Draft saved'},201);}
  return json({code:'UNEXPECTED'},400);
 });
 const load=async()=>{await page.goto('http://localhost:5180/register');await page.waitForFunction(()=>!document.querySelector('.registration-checking'));await page.waitForTimeout(250)};
 await load();assert.equal(await page.locator('.registration-notice').count(),0);assert.equal(await page.getByRole('heading',{name:'Create your account.',exact:true}).count(),1);
 assert.equal(statusCount,1);await page.evaluate(()=>{window.dispatchEvent(new Event('online'));window.dispatchEvent(new Event('online'))});await page.waitForTimeout(150);assert.equal(statusCount,1);
 await page.getByRole('button',{name:'4. Bank Details',exact:true}).click();assert.equal(await page.getByRole('heading',{name:'Create your account.',exact:true}).count(),1);
 const results=[];
 await load();
 for(const width of [320,360,375,390,768,1024,1280,1366,1440,1920]){
  await page.setViewportSize({width,height:width<768?800:720});await page.evaluate(()=>scrollTo(0,0));
  results.push(await page.evaluate(()=>{
   const r=document.querySelector('.register-container').getBoundingClientRect();const nav=document.querySelector('.auth-header').getBoundingClientRect();const main=document.querySelector('.onboarding-main').getBoundingClientRect();const title=document.querySelector('#registration-title').getBoundingClientRect();
   return {width:innerWidth,height:innerHeight,overflow:document.documentElement.scrollWidth>innerWidth,card:{x:r.x,y:r.y,width:r.width,height:r.height},centerError:Math.abs(r.x+r.width/2-innerWidth/2),verticalCenterError:Math.abs(r.y+r.height/2-(main.y+main.height/2)),titleBelowNavbar:title.top>=nav.bottom,scrollY,inputs:[...document.querySelectorAll('.registration-fields input')].map(e=>({height:e.getBoundingClientRect().height,autocomplete:e.autocomplete}))};
  }));const r=results.at(-1);assert.equal(r.overflow,false);assert.ok(r.card.width<=520.1);assert.ok(r.centerError<1);assert.ok(r.verticalCenterError<1);assert.equal(r.titleBelowNavbar,true);if(width>=768)assert.ok(r.card.y+r.card.height<=r.height,'Normal desktop form must fit');await page.screenshot({path:artifactPath(`registration-${width}.png`)});
 }
 await page.getByRole('button',{name:'Continue',exact:true}).click();assert.equal(await page.locator('.registration-field-error').count(),3);assert.equal(await page.evaluate(()=>document.activeElement.className),'register-error');
 await page.getByLabel('Full name',{exact:true}).fill('Test Applicant');await page.getByLabel('Mobile number, India +91',{exact:true}).fill('+919876543210');await page.getByLabel('Email address',{exact:true}).fill('test@example.test');
 mode='database';await page.reload();await page.locator('.registration-notice-database').waitFor();await page.getByLabel('Full name',{exact:true}).fill('Keep my entry');mode='new';await page.getByRole('button',{name:'Retry connection',exact:true}).click();await page.waitForFunction(()=>!document.querySelector('.registration-checking'));assert.equal(await page.getByLabel('Full name',{exact:true}).inputValue(),'Keep my entry');
 mode='expired';await load();assert.equal(await page.locator('.registration-notice-session').count(),1);
 mode='service';await load();assert.equal(await page.locator('.registration-notice-service').count(),1);
 mode='server';await load();assert.equal(await page.locator('.registration-notice-service').count(),1);
 mode='valid';await load();await page.getByRole('heading',{name:'Verify your account.',exact:true}).waitFor();
 bothVerified=true;await load();await page.getByRole('heading',{name:'Upload Documents',exact:true}).waitFor();
 await page.getByRole('button',{name:'4. Bank Details',exact:true}).click();await page.getByRole('heading',{name:'Bank Account Details',exact:true}).waitFor();
 bothVerified=false;mode='new';await load();
 await page.context().setOffline(true);await page.locator('.registration-notice-offline').waitFor();mode='new';await page.context().setOffline(false);await page.waitForFunction(()=>!document.querySelector('.registration-checking'));
 await page.getByLabel('Full name',{exact:true}).fill('Test Applicant');await page.getByLabel('Mobile number, India +91',{exact:true}).fill('9876543210');await page.getByLabel('Email address',{exact:true}).fill('test@example.test');
 mode='duplicate';await page.getByRole('button',{name:'Continue',exact:true}).click();await page.locator('#registration-email-error').waitFor();assert.equal(await page.getByRole('heading',{name:'Create your account.',exact:true}).count(),1);assert.equal(await page.getByLabel('Email address',{exact:true}).inputValue(),'test@example.test');
 mode='new';submitCount=0;await page.getByRole('button',{name:'Continue',exact:true}).evaluate(el=>{el.click();el.click()});await page.getByRole('heading',{name:'Verify your account.',exact:true}).waitFor();assert.equal(submitCount,1);assert.equal(await page.getByRole('button',{name:'Continue',exact:true}).count(),0);await page.reload();await page.getByRole('heading',{name:'Verify your account.',exact:true}).waitFor();
 await page.emulateMedia({reducedMotion:'reduce'});const reduced=await page.locator('.register-container').evaluate(el=>getComputedStyle(el).animationName);assert.equal(reduced,'none');
 await page.getByRole('link',{name:'Log in',exact:true}).last().click();await page.waitForURL('**/login');
 registered=false;mode='new';
 await page.goto('http://localhost:5180/');await page.setViewportSize({width:1280,height:720});await page.evaluate(()=>scrollTo(0,document.body.scrollHeight));
 await page.locator('.navbar .register-nav-btn').click();await page.getByRole('heading',{name:'Create your account.',exact:true}).waitFor();await page.waitForFunction(()=>!document.querySelector('.registration-checking'));
 const routePosition=await page.evaluate(()=>({scrollY,belowNavbar:document.querySelector('#registration-title').getBoundingClientRect().top>=document.querySelector('.auth-header').getBoundingClientRect().bottom}));assert.equal(routePosition.scrollY,0);assert.equal(routePosition.belowNavbar,true);
 await page.getByLabel('Email address',{exact:true}).focus();await page.keyboard.press('Tab');const keyboardFocus=await page.getByRole('button',{name:'Continue',exact:true}).evaluate(el=>({focused:document.activeElement===el,outline:getComputedStyle(el).outlineStyle}));assert.equal(keyboardFocus.focused,true);assert.equal(keyboardFocus.outline,'solid');
 await page.setViewportSize({width:360,height:480});await page.evaluate(()=>scrollTo(0,0));assert.ok(await page.locator('#registration-title').evaluate(el=>el.getBoundingClientRect().top>=document.querySelector('.auth-header').getBoundingClientRect().bottom));await page.getByRole('button',{name:'Continue',exact:true}).scrollIntoViewIfNeeded();assert.ok(await page.getByRole('button',{name:'Continue',exact:true}).isVisible());
 assert.equal(await page.evaluate(()=>localStorage.length+sessionStorage.length),0);
 const report={results,routePosition,keyboardFocus,newVisitorClean:true,noUnauthorizedRetries:true,validation:true,expiredSession:true,offline:true,serviceUnavailable:true,server500:true,databaseUnavailable:true,retryPreservesInput:true,duplicateRegistration:true,duplicateSubmissionPrevented:true,otpPreserved:true,refreshRestored:true,verifiedDraftRestored:true,documentAndBankScreensPreserved:true,shortViewportScroll:true,noBrowserStorage:true,loginLink:true,reducedMotion:reduced,errors};assert.deepEqual(errors,[]);fs.writeFileSync(artifactPath('registration-results.json'),JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify(report));
})().catch(e=>{console.error(e.message);process.exit(1)});
