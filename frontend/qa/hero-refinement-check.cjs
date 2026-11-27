const { artifactPath, browserExecutable } = require('./support/runtime.cjs');
const {chromium}=require('playwright-core');
const fs=require('node:fs');const assert=require('node:assert/strict');
const phase=process.env.HERO_QA_PHASE||'after';const origin=process.env.HERO_QA_ORIGIN||'http://127.0.0.1:5180';
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:browserExecutable()});
 const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text())});
 const results=[];
 const sizes=phase==='before'?[[1280,720],[1366,768],[390,844],[1280,633],[1280,544]]:[[320,568],[375,667],[390,844],[768,1024],[1024,768],[1280,720],[1366,768],[1440,900],[1920,1080],[1280,633],[1280,544]];
 for(const [width,height] of sizes){
  await page.setViewportSize({width,height});await page.goto(origin+'/about',{waitUntil:'networkidle'});await page.evaluate(()=>document.fonts.ready);await page.waitForTimeout(850);
  const measurements=await page.evaluate(()=>{
   const rect=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height,bottom:r.bottom,right:r.right}};
   const title=document.querySelector('.about-hero h1');const buttons=[...document.querySelectorAll('.about-hero .about-actions a')];const art=document.querySelector('.about-artwork');
   return {width:innerWidth,height:innerHeight,overflow:document.documentElement.scrollWidth>innerWidth,h1Count:document.querySelectorAll('h1').length,title:rect(title),fontSize:getComputedStyle(title).fontSize,headingLineHeight:getComputedStyle(title).lineHeight,buttons:buttons.map(rect),art:rect(art),hero:rect(document.querySelector('.about-hero')),navbar:rect(document.querySelector('.navbar')),artMaxWidth:getComputedStyle(art).maxWidth,desktopCtasVisible:buttons.every(e=>e.getBoundingClientRect().bottom<=innerHeight),artVisible:art.getBoundingClientRect().bottom<=innerHeight,navButtons:[...document.querySelectorAll('.nav-actions a')].map(e=>({height:e.getBoundingClientRect().height,radius:getComputedStyle(e).borderRadius,font:getComputedStyle(e).fontSize}))};
  });results.push(measurements);
  if(phase==='after') {assert.equal(measurements.overflow,false);assert.equal(measurements.h1Count,1);if(width>=1024){assert.equal(measurements.desktopCtasVisible,true,`${width} CTAs`);assert.equal(measurements.artVisible,true,`${width} art`);assert.ok(measurements.art.width<=480.5);} }
  await page.screenshot({path:artifactPath(`hero-refinement-${phase}-${width}x${height}.png`)});
  if(phase==='after'&&[390,1440].includes(width)){
   for(const section of await page.locator('[data-about-reveal]').all()){await section.scrollIntoViewIfNeeded();await page.waitForTimeout(40);}
   await page.evaluate(()=>scrollTo(0,0));await page.waitForTimeout(650);await page.screenshot({path:artifactPath(`hero-refinement-full-${width}.png`),fullPage:true});
  }
 }
 fs.writeFileSync(artifactPath(`hero-refinement-${phase}.json`),JSON.stringify({origin,results,errors},null,2));console.log(JSON.stringify({phase,results,errors}));
 if(phase==='after')assert.deepEqual(errors,[]);await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});

