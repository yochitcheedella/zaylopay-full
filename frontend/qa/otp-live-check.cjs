const { browserExecutable } = require('./support/runtime.cjs');
const {chromium}=require('playwright-core');
const readline=require('node:readline');const fs=require('node:fs');const assert=require('node:assert/strict');
const codes=new Map();const waiters=new Map();
const input=readline.createInterface({input:process.stdin});input.on('line',line=>{const data=JSON.parse(line);codes.set(data.channel,data.code);waiters.get(data.channel)?.(data.code);});
const nextCode=channel=>codes.has(channel)?Promise.resolve(codes.get(channel)):new Promise(resolve=>waiters.set(channel,resolve));
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:browserExecutable()});
 const context=await browser.newContext();if(!process.env.OTP_TEST_REGISTER_EMAIL)await context.addCookies([{name:'ZAYLOPE_APPLICANT',value:process.env.OTP_TEST_SESSION,domain:'localhost',path:'/api/v1',httpOnly:true,sameSite:'Strict'}]);
 const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 // Point the dev API module at the isolated server. Requests then use the browser's
 // actual CORS/cookie handling, rather than a route.fetch proxy that hides CORS defects.
 await page.route('**/src/Services/api.js',async route=>{
  const response=await route.fetch();const source=await response.text();
  assert.ok(source.includes('http://localhost:8080/api/v1'));
  await route.fulfill({response,body:source.replace('http://localhost:8080/api/v1',`http://localhost:${process.env.OTP_TEST_PORT}/api/v1`)});
 });
 await page.goto('http://localhost:5180/register');
 if(process.env.OTP_TEST_REGISTER_EMAIL){
  await page.waitForFunction(()=>!document.querySelector('.registration-checking'));
  assert.equal(await page.locator('.registration-notice').count(),0);
  await page.getByLabel('Full name',{exact:true}).fill('Browser Integration Applicant');
  await page.getByLabel('Mobile number, India +91',{exact:true}).fill(process.env.OTP_TEST_REGISTER_MOBILE);
  await page.getByLabel('Email address',{exact:true}).fill(process.env.OTP_TEST_REGISTER_EMAIL);
  await page.getByRole('button',{name:'Continue',exact:true}).click();
 }
 await page.getByRole('heading',{name:'Verify your account.'}).waitFor();
 const session=(await context.cookies()).find(cookie=>cookie.name==='ZAYLOPE_APPLICANT');
 assert.ok(session?.httpOnly);assert.equal(session.path,'/api/v1');assert.equal(session.sameSite,'Strict');
 assert.equal(await page.locator('.verification-panel').getByText(/Local development mode/).count(),1);
 assert.equal(await page.getByRole('button',{name:'Continue',exact:true}).count(),0);
 const channels=page.locator('.verification-channel');
 await channels.nth(0).getByRole('button',{name:'Send code',exact:true}).click();const mobile=await nextCode('mobile');
 await page.getByRole('textbox',{name:'Mobile code digit 1',exact:true}).fill(mobile);
 assert.equal(await page.getByRole('textbox',{name:'Mobile code digit 6',exact:true}).inputValue(),mobile[5]);
 await page.getByRole('button',{name:'Verify mobile',exact:true}).click();await page.getByLabel('Verified',{exact:true}).waitFor();
 await channels.nth(1).getByRole('button',{name:'Send code',exact:true}).click();const email=await nextCode('email');
 await page.getByRole('textbox',{name:'Email code digit 1',exact:true}).fill(email);
 await page.getByRole('button',{name:'Verify email',exact:true}).click();await page.waitForFunction(()=>document.querySelectorAll('.verification-channel.is-verified').length===2);
 await page.reload();await page.getByRole('heading',{name:'Upload Documents',exact:true}).waitFor();
 await page.getByRole('button',{name:'2. Verification',exact:true}).click();
 await page.waitForFunction(()=>document.querySelectorAll('.verification-channel.is-verified').length===2);
 const widths=[];for(const width of [320,375,768,1024,1280]){await page.setViewportSize({width,height:1000});const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);assert.equal(overflow,false);widths.push({width,overflow});await page.locator('.register-container').screenshot({path:`qa/otp-verified-${width}.png`,style:'.navbar{visibility:hidden!important}'});}
 await page.getByRole('button',{name:'Continue',exact:true}).click();await page.getByRole('heading',{name:'Upload Documents',exact:true}).waitFor();
 assert.equal(await page.evaluate(()=>localStorage.length),0);assert.deepEqual(errors,[]);
 fs.writeFileSync('qa/otp-live-results.json',JSON.stringify({liveMySqlVerification:true,realRegistration:Boolean(process.env.OTP_TEST_REGISTER_EMAIL),browserCors:true,httpOnlySessionCookie:true,mobile:true,email:true,refreshRestored:true,documentProgression:true,noLocalStorage:true,widths,errors},null,2));
 await browser.close();input.close();process.exit(0);
})().catch(e=>{console.error(e.message);process.exit(1)});
