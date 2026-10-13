// Fixture-backed menu navigation; dev.t3.menu rejects real credentials.
import assert from 'node:assert/strict';
import {DevClient} from './lib/devclient.mjs';
const dev=await DevClient.connect({port:Number(process.argv[2] ?? 27889),timeoutMs:15000});
const fixturePort=Number(process.argv[3] ?? 25686); assert.ok(fixturePort>=25680&&fixturePort<=25688);
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const menu=()=>dev.call('dev.t3.menu');
const click=label=>dev.call('dev.t3.menu',{click:label});
try {
 for(const scale of [2,3,4]) {
  await dev.call('dev.hud.guiScale',{scale});
  await dev.call('dev.screen',{open:'t3-tools'}); await sleep(200);
  const m=await menu(); assert.equal(m.widgets.length, 18, 'all twelve distinct tools visible without paging'); const s=await dev.call('dev.state');
  for (const duplicate of ['Saved history', 'Copy latest reply', 'New workspace']) assert.ok(!m.widgets.some(w => w.label === duplicate));
  const w=Math.ceil(s.window.width/s.window.guiScale),h=Math.ceil(s.window.height/s.window.guiScale);
  for(const a of m.widgets) assert.ok(a.x>=0&&a.y>=0&&a.x+a.width<=w&&a.y+a.height<=h,`bounds ${a.label}`);
  for(let i=0;i<m.widgets.length;i++)for(let j=i+1;j<m.widgets.length;j++) {const a=m.widgets[i],b=m.widgets[j];assert.ok(!(a.x<b.x+b.width&&a.x+a.width>b.x&&a.y<b.y+b.height&&a.y+a.height>b.y),`overlap ${a.label}/${b.label}`);}
  await dev.call('dev.screenshot',{name:`navigation-tools-${scale}`,hideHud:false});
  const search=m.widgets.find(a=>a.label==='Find a tool'); assert.ok(search);
  await click('Find a tool');
  for(const text of ['t','e','r','m']) {await dev.call('dev.type',{text});await sleep(120);}
  assert.ok((await menu()).widgets.some(a=>a.label==='Terminal'));
  assert.ok(!(await menu()).widgets.some(a=>a.label==='Schedules'));
 }
 await dev.call('dev.screen',{open:'t3-tools'}); await click('Find a tool');await dev.call('dev.type',{text:'model'});await sleep(200);
 await click('Model options'); assert.equal((await menu()).screen,'T3OptionsScreen');
 await click('Back');assert.equal((await menu()).screen,'T3ToolsScreen');assert.ok(!(await menu()).widgets.some(a=>a.label==='Schedules'), 'query preserved');
 await dev.call('dev.key',{key:'f',modifiers:64}); await dev.call('dev.key',{key:'enter'}); assert.equal((await menu()).screen,'T3OptionsScreen');await dev.call('dev.key',{key:'escape'});assert.equal((await menu()).screen,'T3ToolsScreen');
 await dev.call('dev.key',{key:'f',modifiers:64}); await dev.call('dev.key',{key:'escape'}); await sleep(200); assert.equal((await menu()).widgets.length,18,'Escape clears search');
 await dev.call('dev.screen', {open:'t3'}); await click('Terminal'); assert.equal((await menu()).screen,'T3TerminalScreen'); await click('Back'); assert.equal((await menu()).screen,'T3Screen');
 await dev.call('dev.key',{key:'key.keyboard.grave.accent',modifiers:64}); assert.equal((await menu()).screen,'T3TerminalScreen'); await dev.call('dev.key',{key:'key.keyboard.grave.accent',modifiers:64}); assert.equal((await menu()).screen,'T3Screen');
 await click('Workspace'); await click('History'); assert.equal((await menu()).screen,'T3HistoryScreen'); await click('Back'); assert.equal((await menu()).screen,'T3ToolsScreen'); await click('Back');
 await click('+ New'); await sleep(100); await dev.call('dev.key',{key:'a',modifiers:64}); await dev.call('dev.type',{text:'Saved new chat draft'}); await click('+ New'); await click('+ New'); assert.equal((await menu()).chat.composer,'Saved new chat draft','new-chat draft survives toggling back to the existing conversation'); await click('+ New');
 for(const scale of [2,3,4]) {
  await dev.call('dev.hud.guiScale',{scale}); await dev.call('dev.screen',{open:'t3'}); await click('+ New'); await click('Workspace'); assert.equal((await menu()).screen,'T3WorkspaceScreen');
  for(let i=0;i<100;i++) {if(!(await dev.call('dev.t3.form')).busy)break;await sleep(100);}
  let m=await menu(); for(const unused of ['Base ref','New branch','Worktree path']) assert.ok(!m.widgets.some(w=>w.label===unused), `root hides ${unused}`);
  await click('Project root'); m=await menu(); for(const needed of ['Base ref','New branch']) assert.ok(m.widgets.some(w=>w.label===needed)); assert.ok(!m.widgets.some(w=>w.label==='Worktree path'));
  await click('New branch'); await dev.call('dev.type',{text:'feature/navigation-check'});
  await click('New worktree'); m=await menu(); assert.ok(m.widgets.some(w=>w.label==='Worktree path')); for(const unused of ['Base ref','New branch'])assert.ok(!m.widgets.some(w=>w.label===unused));
  await dev.call('dev.screenshot',{name:`navigation-workspace-${scale}`,hideHud:false});
  await click('Existing worktree'); await click('Project root'); assert.equal((await dev.call('dev.t3.form')).fields['New branch'],'feature/navigation-check','hidden branch value survives workspace choice changes');
 }
 await dev.call('dev.screen',{open:'t3'}); await dev.call('dev.t3.menu',{focusThread:'A-done-0'}); await click('Workspace'); await click('Chat settings');
 await click('Chat title'); await dev.call('dev.key',{key:'a',modifiers:64}); await dev.call('dev.type',{text:'Retained settings title'}); await click('Plan mode');
 for(let i=0;i<100;i++) {if((await menu()).widgets.some(w=>w.label==='✓ Plan mode'))break;await sleep(100);}
 await click('Rename');
 let renamed=false; for(let i=0;i<100;i++) {const calls=await (await fetch(`http://127.0.0.1:${fixturePort}/_qa/dispatches`)).json(); renamed=calls.some(c=>c.threadId==='A-done-0'&&c.title==='Retained settings title'); if(renamed)break;await sleep(100);}
 assert.ok(renamed,'changing chat mode keeps the title being edited'); await click('Back'); assert.equal((await menu()).screen,'T3ToolsScreen');
 console.log('PASS tools/scales 2/3/4, search, Back/Enter/Escape, direct terminal/Ctrl+grave, History and + New workspace choices');
} finally {dev.close();}
