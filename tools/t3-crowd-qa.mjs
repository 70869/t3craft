// Exercise actual resident tick/route recovery in a copy of the authored release campus.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';

const dev = await DevClient.connect({ port: Number(process.argv[2] ?? 27939), timeoutMs: 60000 });
const fixturePort = Number(process.argv[3] ?? 25686);
assert.ok(fixturePort >= 25680 && fixturePort <= 25688);
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const evidence = [];
const fixture = async path => { const r = await fetch(`http://127.0.0.1:${fixturePort}${path}`); assert.ok(r.ok); return r.json(); };
const read = () => dev.call('dev.agents');
function clear(agents) {
  for (let i=0;i<agents.length;i++) for (let j=i+1;j<agents.length;j++) {
    const a=agents[i],b=agents[j];
    if (Math.abs(a.y-b.y)<1.65) assert.ok(Math.hypot(a.x-b.x,a.z-b.z)>=.75, `${a.id} overlaps ${b.id}`);
  }
}
async function route(id,from,to) { await dev.call('dev.t3.residentWalk',{agent:id,from,to}); }
async function finish(ids,label) {
  const samples=[];
  for(let n=0;n<500;n++) {
    const state=await read(); clear(state.agents); samples.push(state);
    const walkers=state.agents.filter(a=>ids.includes(a.id));
    if (n>5 && walkers.every(a=>!a.walking && Math.hypot(a.x-a.target.x,a.z-a.target.z)<.05)) {
      evidence.push({label,samples}); return;
    }
    await pause(100);
  }
  evidence.push({label,samples}); assert.fail(`${label}: residents did not arrive`);
}
try {
  for (let n=0;n<120;n++) {
    const ready=await dev.call('dev.state');
    if (ready.inWorld && ready.ready && !ready.loading && (await dev.call('dev.t3')).connected) break;
    assert.ok(n<119,'Authored fixture world must be ready'); await pause(300);
  }
  await dev.call('dev.screen',{open:'t3-mod-settings'});
  const menu=await dev.call('dev.t3.menu'); // Reject actual credentials before all mutations.
  const cast=menu.widgets.find(w=>w.label.startsWith('Resident cast'));
  assert.ok(cast); await dev.call('dev.t3.menu',{click:cast.label});
  await dev.call('dev.screen',{open:null});
  await pause(1800);
  const anchors=await dev.call('dev.anchors');
  assert.equal(anchors.layout,'modern'); assert.equal(anchors.revision,9);
  const state=await dev.call('dev.state'); assert.equal(state.world.name,'AgentCraft HQ');
  assert.equal(state.hq.lastChanged,0,'Saved campus must load without regeneration');
  const before=await fixture('/_qa/dispatches');
  const roster=await read(); assert.equal(roster.count,18); clear(roster.agents);
  evidence.push({authoredWorld:state.world,anchors,roster});
  const left={x:-3.5,y:66,z:10.5},right={x:3.5,y:66,z:10.5};
  await route('kit',left,right); await route('wren',right,left);
  await dev.call('dev.camera',{x:5.5,y:68.4,z:8.5,lookAt:{x:.5,y:67,z:10.5},mode:'spectator'});
  await finish(['kit','wren'],'Head-on crossing');
  assert.ok(evidence.at(-1).samples.some(s=>s.agents.some(a=>['kit','wren'].includes(a.id) && a.crowdWait>0)),'Crossing must exercise yielding');
  await dev.call('dev.screenshot',{name:'crowd-crossing-arrived',hideHud:true});
  await route('kit',left,right); await route('wren',right,left);
  await route('rowan',{x:.5,y:66,z:7.5},{x:.5,y:66,z:13.5});
  await route('tove',{x:.5,y:66,z:13.5},{x:.5,y:66,z:7.5});
  await finish(['kit','wren','rowan','tove'],'Four-way crossing');
  // A standing resident stays put while the walker goes around it.
  const middle={x:.5,y:66,z:10.5};
  await route('wren',middle,middle); await route('kit',left,right);
  await finish(['kit','wren'],'Standing resident bypass');
  // Concurrent arrivals at one destination must reserve different body positions.
  await route('kit',left,middle); await route('wren',right,middle);
  await finish(['kit','wren'],'Shared destination spacing');
  await dev.call('dev.screenshot',{name:'crowd-shared-destination',hideHud:true});
  await route('kit',middle,left); await route('wren',middle,right);
  await pause(1600); // Initial overlap is deliberate; subsequent samples require separation.
  await finish(['kit','wren'],'Recovery from coincident positions');
  await dev.call('dev.t3.residentWalk',{clear:true});
  await finish(['kit','wren','rowan','tove'],'Return to assigned desks');
  assert.deepEqual(await fixture('/_qa/dispatches'),before,'Movement previews execute no T3 work');
  console.log('PASS authored revision-9 campus, 18 distinct residents, head-on/four-way yielding, stationary bypass, reserved shared destinations, coincident recovery, return to desks, no T3 writes');
} finally {
  await mkdir('artifacts/npc-behavior',{recursive:true});
  await writeFile('artifacts/npc-behavior/crowd-qa.json',JSON.stringify(evidence,null,2));
  await dev.call('dev.t3.residentWalk',{clear:true}).catch(()=>{});
  await dev.call('dev.screen',{open:null}).catch(()=>{}); dev.close();
}
