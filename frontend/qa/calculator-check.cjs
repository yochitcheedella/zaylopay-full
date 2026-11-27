const { qaUrl, artifactPath, browserExecutable } = require('./support/runtime.cjs');
const { chromium } = require('playwright-core');
const fs = require('node:fs');
const assert = require('node:assert/strict');
(async () => {
 const browser = await chromium.launch({headless:true,executablePath:browserExecutable()});
 const page = await browser.newPage(); const errors=[]; page.on('pageerror', e=>errors.push(e.message));
 await page.goto(qaUrl('/')); await page.evaluate(()=>document.fonts.ready);
 const section=page.locator('.zp-calculator'); const output=section.locator('output');
 const amount=async()=>{await page.waitForTimeout(400);return output.innerText()};
 const edit=async(key,raw)=>{await page.locator(`#calc-${key}`).fill(raw);await page.locator(`#calc-${key}`).blur()};
 const reset=async()=>{await section.getByRole('button',{name:'Reset',exact:true}).click()};
 const calculations=[];
 calculations.push({case:'default',actual:await amount()}); assert.equal(calculations.at(-1).actual,'₹1,000');
 await edit('orders','0'); calculations.push({case:'zero orders',actual:await amount()});assert.equal(calculations.at(-1).actual,'₹0');
 await edit('orders','40');await edit('value','1250');await edit('rate','7.5');calculations.push({case:'40 × 1250 × 7.5%',actual:await amount()});assert.equal(calculations.at(-1).actual,'₹3,750');
 for(const key of ['orders','value','rate']){
  const input=page.locator(`#calc-${key}`);await input.fill('');assert.equal(await input.inputValue(),'');await input.blur();assert.equal(await input.inputValue(),'0');
  await edit(key,'-50');assert.equal(await input.inputValue(),'0');
  await edit(key,'999999');assert.equal(await input.inputValue(),await input.getAttribute('max'));
 }
 calculations.push({case:'maximum',actual:await amount()});assert.equal(calculations.at(-1).actual,'₹4,00,000');
 await reset();
 const slider=section.getByRole('slider',{name:'Monthly qualifying orders slider'});
 await slider.focus();await page.keyboard.press('ArrowRight');assert.equal(await page.locator('#calc-orders').inputValue(),'21');
 await page.keyboard.press('End');assert.equal(await page.locator('#calc-orders').inputValue(),'200');
 await page.keyboard.press('Home');assert.equal(await page.locator('#calc-orders').inputValue(),'0');
 await edit('orders','36');assert.equal(await slider.inputValue(),'36');
 await edit('rate','7.3');assert.equal(await page.locator('#calc-rate').inputValue(),'7.5');
 await reset();assert.equal(await page.locator('#calc-orders').inputValue(),'20');assert.equal(await page.locator('#calc-value').inputValue(),'1000');assert.equal(await page.locator('#calc-rate').inputValue(),'5');
 const results=[];
 for(const width of [320,375,768,1024,1280,1440]){
  await page.setViewportSize({width,height:1000}); await section.scrollIntoViewIfNeeded();await page.waitForTimeout(1100);
  results.push(await section.evaluate(el=>{
   const rect=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height}};
   return {width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth,grid:rect(el.querySelector('.zp-calc-grid')),controls:rect(el.querySelector('.zp-calc-controls')),result:rect(el.querySelector('.zp-calc-result')),inputs:[...el.querySelectorAll('input[type=number]')].map(rect),amount:rect(el.querySelector('output')),amountOverflow:el.querySelector('output').scrollWidth>el.querySelector('output').clientWidth};
  }));
  assert.equal(results.at(-1).overflow,false);assert.equal(results.at(-1).amountOverflow,false);
  await section.screenshot({path:artifactPath(`calculator-${width}.png`),style:'.navbar{visibility:hidden!important}'});
 }
 await page.setViewportSize({width:320,height:1000});
 await edit('orders','200');await edit('value','10000');await edit('rate','20');await amount();
 const maxOverflow=await output.evaluate(el=>el.scrollWidth>el.clientWidth);assert.equal(maxOverflow,false);
 await section.screenshot({path:artifactPath('calculator-max-320.png'),style:'.navbar{visibility:hidden!important}'});
 await reset();await slider.focus();await page.keyboard.press('ArrowRight');await page.waitForTimeout(250);const tooltip=await section.locator('.zp-range-tooltip').first().evaluate(el=>getComputedStyle(el).opacity);const focus=await slider.evaluate(el=>getComputedStyle(el).outlineStyle);
 assert.equal(tooltip,'1');assert.equal(focus,'solid');await page.emulateMedia({reducedMotion:'reduce'});await edit('orders','30');await page.waitForTimeout(40);const reducedAmount=await output.innerText();assert.equal(reducedAmount,'₹1,500');
 const reducedMotion=await section.evaluate(el=>({coinAnimation:getComputedStyle(el.querySelector('.zp-calc-coin')).animationName,reveal:getComputedStyle(el.querySelector('.zp-calc-reveal')).animationName}));
 const preserved={navbar:await page.locator('.navbar').count(),hero:await page.locator('.zp-hero').count(),benefits:await page.locator('.zp-benefit').count(),journey:await page.locator('.zp-journey-step').count(),earn:await page.locator('.zp-earn').count(),footer:await page.locator('.zaylope-footer').count()};
 const report={calculations,results,maxOverflow,sliderSynchronization:true,emptyEditing:true,clamping:true,reset:true,tooltip,focus,reducedMotion,reducedAmount,preserved,errors};
 fs.writeFileSync(artifactPath('calculator-results.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});

