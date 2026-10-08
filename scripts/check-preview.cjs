// Exercise the design preview in an isolated headless Chrome session via CDP.
const fs = require('node:fs');
const path = require('node:path');
async function main() {
  const pages = await (await fetch('http://127.0.0.1:9335/json/list')).json();
  const page = pages.find(p => p.type === 'page' && p.url.includes('daylight-browser-preview'));
  if (!page) throw new Error('Preview page not found');
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve,reject) => { ws.onopen=resolve; ws.onerror=reject; });
  let next=0;
  const pending=new Map();
  const contexts=[];
  ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.method==='Runtime.executionContextCreated')contexts.push({id:m.params.context.id,sessionId:m.sessionId});if(pending.has(m.id)){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}};
  function send(method,params={},sessionId){return new Promise((resolve,reject)=>{const id=++next;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params,sessionId}));});}
  await send('Page.reload',{ignoreCache:true});
  await new Promise(resolve=>setTimeout(resolve,500));
  await send('Runtime.enable');
  const targets=await send('Target.getTargets');
  for(const target of targets.targetInfos.filter(t=>t.type==='iframe')){
    const attached=await send('Target.attachToTarget',{targetId:target.targetId,flatten:true});
    await send('Runtime.enable',{},attached.sessionId);
  }
  let contextId;
  let frameSession;
  for(const ctx of contexts){const result=await send('Runtime.evaluate',{contextId:ctx.id,expression:'Boolean(document.getElementById("daylight-preview"))',returnByValue:true},ctx.sessionId);if(result.result.value){contextId=ctx.id;frameSession=ctx.sessionId;}}
  if(!contextId)throw new Error('Preview frame not found');
  async function evaluate(expression){const r=await send('Runtime.evaluate',{contextId,expression,returnByValue:true},frameSession);if(r.exceptionDetails)throw new Error(r.exceptionDetails.exception?.description||'Browser exception');return r.result.value;}
  const results=await evaluate(`(()=>{
    const root=document.getElementById('daylight-preview');
    const results=[];
    const click=selector=>{const el=root.querySelector(selector);if(!el)throw Error('Missing '+selector);el.click();};
    const check=(condition,label)=>{if(!condition)throw Error(label);results.push(label);};
    for(const [tab,title] of [['library','Words to return to'],['forms','Rituals, made personal'],['schedule','Gentle nudges'],['settings','Make yourself at home']]){click('[data-go="'+tab+'"]');check(root.textContent.includes(title),'Navigation: '+tab);}
    click('[data-go="forms"]');click('[data-go="review"]');
    const before=Number(root.querySelector('[role="progressbar"]').getAttribute('aria-valuenow'));
    const target=root.querySelector('[data-check="2"]');const delta=target.checked?-20:20;target.click();
    check(Number(root.querySelector('[role="progressbar"]').getAttribute('aria-valuenow'))===before+delta,'Checklist updates progress');
    root.querySelector('#day-reflection').value='A useful preview';click('[data-action="save-reflection"]');
    check(root.querySelector('[role="status"]').textContent.includes('saved'),'Reflection feedback');
    click('[data-go="library"]');click('[data-go="add"]');
    root.querySelector('#day-title').value='Preview test';root.querySelector('#day-body').value='<b>Literal text</b>';
    click('button[type="submit"]');
    check(root.textContent.includes('Preview test')&&root.textContent.includes('<b>Literal text</b>'),'Add entry preserves literal text');
    click('[data-go="settings"]');click('[data-dark]');
    check(['dark','light'].includes(root.style.colorScheme),'Theme switch');
    if(root.querySelector('[data-dark]').checked)click('[data-dark]');
    click('[data-go="home"]');return results;
  })()`);
  await send('Emulation.setDeviceMetricsOverride',{width:360,height:1500,deviceScaleFactor:1,mobile:false});
  await new Promise(resolve=>setTimeout(resolve,700));
  const fits=await evaluate('document.documentElement.scrollWidth <= document.documentElement.clientWidth');
  if(!fits)throw new Error('Preview overflows at narrow width');
  results.push('Narrow layout has no horizontal overflow');
  const shot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});
  fs.writeFileSync(path.resolve('.tools/daylight-preview-narrow.png'),Buffer.from(shot.data,'base64'));
  console.log(results.map(x=>'PASS: '+x).join('\n'));
  await send('Browser.close');
}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>setTimeout(()=>process.exit(process.exitCode||0),250));
