import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';
const dev = await DevClient.connect({port:Number(process.argv[2] ?? 27939)});
const fixturePort = Number(process.argv[3] ?? 25686);
assert.ok(fixturePort>=25680 && fixturePort<=25688);
const pause = ms => new Promise(resolve=>setTimeout(resolve,ms));
const evidence=[];
const read=()=>dev.call('dev.agents.look');
async function until(predicate,label) {
  for(let i=0;i<400;i++) {const state=await read(), match=state.agents.find(predicate); if(match)return match; await pause(100);}
  assert.fail(label);
}
try {
  await dev.call('dev.screen',{open:'t3-work'}); await dev.call('dev.t3.menu');
  await dev.call('dev.screen',{open:null});
  const before=await (await fetch(`http://127.0.0.1:${fixturePort}/_qa/dispatches`)).json();
  assert.ok((await fetch(`http://127.0.0.1:${fixturePort}/_qa/empty`)).ok,'Empty one synthetic machine so the fixed cast has unassigned idle residents');
  await pause(2200);
  await dev.call('dev.agents',{settle:true}); // Movement has its own crowd QA; inspect gestures at their assigned seats.
  const stretch=await until(a=>a.gesture==='SEATED_STRETCH' && a.seated,'Seated idle stretch appears');
  const resident=(await dev.call('dev.agents')).agents.find(a=>a.id===stretch.id);
  const angle=resident.yaw*Math.PI/180, fx=-Math.sin(angle),fz=Math.cos(angle);
  await dev.call('dev.camera',{x:resident.x+fx*2.4,y:resident.y+1.6,z:resident.z+fz*2.4,lookAt:{x:resident.x,y:resident.y+1.1,z:resident.z},mode:'spectator',fov:55});
  const rendered=await until(a=>a.id===stretch.id && a.gesture==='SEATED_STRETCH' && a.animation[0]<-1.4,'Rendered stretch raises the arms');
  assert.ok(rendered.seated && rendered.sit>.8,'Stretch keeps the seat');
  evidence.push({stretch:rendered,shot:await dev.call('dev.screenshot',{name:'gesture-seated-stretch',hideHud:true})});
  await dev.call('dev.camera',{x:resident.x+fx*2.4,y:resident.y+1.6,z:resident.z+fz*2.4,lookAt:{x:resident.x,y:resident.y+1.1,z:resident.z},mode:'creative',fov:55});
  const greeting=await until(a=>a.id===stretch.id && a.gesture==='GREET' && a.animation[0]<-1.5,'Nearby player receives a rendered wave');
  evidence.push({greeting,shot:await dev.call('dev.screenshot',{name:'gesture-greeting',hideHud:true})});
  await pause(3500);
  assert.notEqual((await read()).agents.find(a=>a.id===stretch.id).gesture,'GREET','Greeting expires and activity resumes');
  const after=await (await fetch(`http://127.0.0.1:${fixturePort}/_qa/dispatches`)).json();
  assert.deepEqual(after,before,'Idle gestures execute no T3 work');
  console.log('PASS actual rendered seated stretch and player greeting, seat preservation, bounded greeting, no T3 writes');
} finally {
  await fetch(`http://127.0.0.1:${fixturePort}/_qa/reset`).catch(()=>{});
  await mkdir('artifacts/npc-behavior',{recursive:true});
  await writeFile('artifacts/npc-behavior/gestures-qa.json',JSON.stringify(evidence,null,2));
  dev.close();
}
