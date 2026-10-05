#!/usr/bin/env node
// In-game regression check. Only mutates the two isolated loopback T3 fixtures.
// Requires the fixture config with A-done-0 pinned to desk 0 in the current HQ.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';

const port=Number(process.argv[2] || 27879);
const dev=await DevClient.connect({port,timeoutMs:10000});
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const evidence=[];
const qa=async(path,machine='A')=>{
  const response=await fetch(`http://127.0.0.1:${machine==='A'?25680:25681}/_qa/${path}`);
  assert.equal(response.ok,true,`fixture ${path}`); return response.json();
};
const inspect=async()=>{
  const agents=await dev.call('dev.agents');
  const look=await dev.call('dev.agents.look',{agent:'marlow'});
  return {agent:agents.agents.find(a=>a.id==='marlow'),life:look.agents[0],agents,look};
};
const waitFor=async(test,label,timeout=45000)=>{
  const deadline=Date.now()+timeout;
  while(Date.now()<deadline) { const current=await inspect(); if(test(current)) return current; await sleep(200); }
  throw new Error(`Timed out: ${label}`);
};
try {
  const initial=await dev.call('dev.state');
  assert.equal(initial.foreman.counts.tasks,26,'game must be connected to the isolated fixture history');
  assert.match(initial.foreman.oldestOpenDecision,/^[AB]-/,'fixture decisions required');
  assert.equal(initial.agents.count,6); assert.equal(initial.foreman.counts.memory,26);
  const fresh=await inspect(); assert.equal(fresh.life.particles,0,'loading a completed chat must not replay completion');
  await qa('reset');
  await waitFor(s=>!s.agent.stale&&!s.agent.walking&&s.agent.state==='done'&&s.life.particles===0,'clean fixture baseline');
  await dev.call('dev.screen',{open:null});
  await qa('studio?state=running');
  const working=await waitFor(s=>s.agent.state==='running'&&!s.agent.walking&&s.life.posture==='SIT_TYPE','working NPC reaches and types at its own desk');
  assert.equal(working.agent.anchor,'desk_marlow');
  evidence.push({state:'working',anchor:working.agent.anchor,posture:working.life.posture});
  await dev.call('dev.camera',{anchor:'cam_desk_marlow',hideHud:false,mode:'creative'});
  await dev.call('dev.screenshot',{name:'t3-npc-working',hideHud:false});
  await qa('studio?state=waiting');
  const waiting=await waitFor(s=>s.agent.state==='waiting_user'&&!s.agent.walking&&s.life.needsYou&&s.life.awaitingDecision==='A-done-0:shared-request','waiting NPC approaches player with exact request');
  assert.equal(waiting.agent.station,'user'); assert.equal(waiting.life.awaitingDecision,'A-done-0:shared-request');
  evidence.push({state:'waiting',anchor:waiting.agent.anchor,posture:waiting.life.posture,exactRequest:waiting.life.awaitingDecision});
  await dev.call('dev.camera',{x:waiting.agent.x+3,y:67.62,z:waiting.agent.z+3,lookAt:{x:waiting.agent.x,y:67.5,z:waiting.agent.z},mode:'creative',hideHud:false});
  await dev.call('dev.screenshot',{name:'t3-npc-waiting',hideHud:false});
  await qa('studio?state=completed');
  const done=await waitFor(s=>s.agent.state==='done'&&s.life.particles>0,'actual completion produces particles',10000);
  evidence.push({state:'done',completionParticles:done.life.particles});
  await waitFor(s=>s.agent.state==='done'&&!s.agent.walking&&s.agent.station==='lounge','completed NPC returns to lounge');
  await qa('reply');
  const spoken=await waitFor(s=>s.life.bubble==='The T3 studio reply is ready.','new T3 reply appears as NPC speech');
  evidence.push({speech:spoken.life.bubble});
  await qa('studio?state=failed');
  const failed=await waitFor(s=>s.agent.state==='error'&&s.life.family==='error','failure reaches NPC');
  evidence.push({state:'failed',family:failed.life.family});
  await qa('offline');
  const offline=await waitFor(s=>s.agent.stale===true,'offline machine dims its own NPC');
  assert(offline.agents.agents.some(a=>!a.stale),'healthy second machine stays live');
  let state;
  for(let i=0;i<30;i++) { state=await dev.call('dev.state'); if(state.hq.lamps['agent:marlow']==='off') break; await sleep(200); }
  assert.equal(state.hq.lamps['agent:marlow'],'off');
  assert.equal(state.hq.lamps['ci:#1'],'error','offline machine health lamp');
  assert.equal(state.hq.lamps['ci:#2'],'working','healthy peer machine health lamp');
  evidence.push({state:'offline',lamp:state.hq.lamps['agent:marlow'],healthyPeer:true});
  await qa('reset');
  await waitFor(s=>!s.agent.stale,'fixture reconnect recovery');
  const reachability=await dev.call('dev.hq.check');
  evidence.push({hq:reachability});
  for(const [name,screen] of [['board','t3-board'],['library','t3-library'],['reviews','t3-reviews'],['agent','agent']]) {
    const result=await dev.call('dev.screen',{open:screen});
    assert.match(result.screen,/\.t3\.T3/,'station must open a native T3 screen');
    await dev.call('dev.screenshot',{name:'t3-studio-'+name,hideHud:false});
  }
  await dev.call('dev.hud.guiScale',{scale:4});
  await dev.call('dev.screen',{open:'t3-board'});
  await dev.call('dev.screenshot',{name:'t3-studio-board-small',hideHud:false});
  await dev.call('dev.hud.guiScale',{scale:3});
  await dev.call('dev.screen',{open:null});
  await dev.call('dev.camera',{anchor:'cam_merge_station',hideHud:false,mode:'creative'});
  await dev.call('dev.screenshot',{name:'t3-studio-checkpoint-station',hideHud:false});
  const final=await dev.call('dev.state');
  assert.equal(final.agents.pathFailures,0,'NPC navigation has no failed paths');
  evidence.push({pathFailures:final.agents.pathFailures,residents:final.agents.count,tasks:final.foreman.counts.tasks});
  await mkdir('artifacts/qa',{recursive:true});
  await writeFile('artifacts/qa/t3-studio.json',JSON.stringify(evidence,null,2));
  console.log('PASS six residents; own desk typing; exact waiting request and approach; real completion particles; error; per-machine offline lamps; recovery; T3 station screens; navigation.');
} finally { dev.close(); }
