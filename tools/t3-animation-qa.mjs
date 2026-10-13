// Animation evidence in an isolated Minecraft world with a synthetic pairing only.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';
const port = Number(process.argv[2] ?? 27919), fixturePort = Number(process.argv[3] ?? 25686);
assert.ok(fixturePort >= 25680 && fixturePort <= 25688, 'Use a synthetic fixture port');
const dev = await DevClient.connect({ port, timeoutMs: 45000 });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const menu = () => dev.call('dev.t3.menu');
const click = label => dev.call('dev.t3.menu', { click: label });
const look = async () => (await dev.call('dev.agents.look', { agent: 'marlow' })).agents.find(a => a.id === 'marlow');
async function until(read, predicate, label) {
  for (let i = 0; i < 120; i++) { const value = await read(); if (predicate(value)) return value; await sleep(150); }
  assert.fail(`Timed out: ${label}`);
}
async function fixture(path) { const r = await fetch(`http://127.0.0.1:${fixturePort}${path}`); assert.ok(r.ok); return r.json(); }
const evidence = [];
async function shot(name) { return dev.call('dev.screenshot', { name, hideHud: true }); }
try {
  await until(() => dev.call('dev.state'), s => s.inWorld && s.ready && !s.loading, 'world ready');
  await until(() => dev.call('dev.t3'), s => s.connected && s.threads === 26, 'fixture connection');
  await dev.call('dev.screen', { open: 't3-work' });
  await menu(); // The hook refuses a real pairing before fixture mutation.
  await fixture('/_qa/reset');
  const before = await fixture('/_qa/dispatches');
  await dev.call('dev.agents.card', { agent: 'marlow' }); await click('Desk'); await click('Reassign chat');
  await dev.call('dev.type', { text: 'Greeting' }); await sleep(300);
  const choices = await menu(); const choice = choices.widgets.find(w => w.active && w.height === 34);
  assert.ok(choice, 'Existing fixture conversation available'); await click(choice.label);
  await dev.call('dev.agents.card', { agent: 'marlow' });
  const id = (await menu()).npc.threadId;
  await dev.call('dev.screen', { open: null });
  const activity = async value => {
    await fixture('/_qa/npc?thread=' + encodeURIComponent(id) + '&activity=' + value);
    await until(() => dev.call('dev.agents'), s => s.agents.some(a => a.id === 'marlow' && a.state === value), value);
    await dev.call('dev.agents', { settle: true });
  };
  await activity('editing');
  const resident = (await dev.call('dev.agents')).agents.find(a => a.id === 'marlow');
  assert.ok(resident.threadTitle && resident.threadTitle.length > 0, 'Resident world card keeps the linked thread title');
  await dev.call('dev.camera', { x: resident.x + 2.2, y: resident.y + 1.8, z: resident.z + 2.3, lookAt: { x: resident.x, y: resident.y + .9, z: resident.z }, mode: 'spectator', fov: 45 });
  await until(look, a => a.posture === 'SIT_TYPE' && a.typing > .8, 'typing posture');
  const samples = [];
  for (let i = 0; i < 120; i++) { samples.push(await look()); await sleep(100); }
  assert.ok(samples.some(a => a.typing > .8) && samples.some(a => a.typing < .2), 'Typing has bursts and pauses');
  assert.ok(samples.some(a => a.mouse > .4), 'Mouse use during a typing pause');
  for (const channel of [0, 3, 16, 17]) {
    const values = samples.map(a => a.animation[channel]);
    assert.ok(Math.max(...values) - Math.min(...values) > .015, `Hand channel ${channel} moves`);
  }
  assert.ok(samples.some(a => a.expression === 'FOCUSED'), 'Focused expression');
  await until(look, a => a.typing > .8, 'typing capture');
  evidence.push({ activity: 'editing', samples, screenshot: await shot('animation-typing') });
  await dev.call('dev.camera', { x: resident.x + 1.4, y: resident.y + 2.6, z: resident.z + 1.6, lookAt: { x: resident.x, y: resident.y + .8, z: resident.z }, mode: 'spectator', fov: 40 });
  evidence.push({ keyboard: await shot('animation-keyboard') });
  await dev.call('dev.camera', { x: resident.x + 2.7, y: resident.y + 1.8, z: resident.z + 1.8, lookAt: { x: resident.x, y: resident.y + 1.3, z: resident.z }, mode: 'spectator', fov: 60 });
  for (const [value, posture, expression] of [['thinking', 'SIT_THINK', 'THINKING'], ['testing', 'LEAN_BENCH', 'FOCUSED'], ['running', 'WATCH', 'FOCUSED']]) {
    await activity(value);
    const pose = await until(look, a => a.posture === posture && a.expression === expression && a.typing < .01 && a.mouse < .01, `${value} gesture`);
    if (value === 'testing' || value === 'running') assert.equal(pose.gesture, 'CHECK_SCREEN', 'Command/test watching has a small screen-check gesture');
    evidence.push({ activity: value, pose, screenshot: await shot('animation-' + value) });
  }
  // Wait for a real observed active run before testing the live completion reaction.
  await activity('editing'); await sleep(1800);
  await fixture('/_qa/studio?thread=' + encodeURIComponent(id) + '&state=completed');
  const pleased = await until(look, a => a.expression === 'PLEASED', 'live completion smile');
  evidence.push({ completed: pleased, screenshot: await shot('animation-completed') });
  await sleep(3200); assert.notEqual((await look()).expression, 'PLEASED', 'Completion reaction expires');
  await fixture('/_qa/leisure?thread=' + encodeURIComponent(id));
  await until(() => dev.call('dev.agents'), s => s.agents.some(a => a.id === 'marlow' && a.anchor.startsWith('lounge')), 'completed resident goes to lounge');
  await dev.call('dev.agents', { settle: true });
  const lounge = (await dev.call('dev.agents')).agents.find(a => a.id === 'marlow');
  const facing = lounge.yaw * Math.PI / 180;
  await dev.call('dev.camera', { x: lounge.x - Math.sin(facing) * 2.4, y: lounge.y + 2.1, z: lounge.z + Math.cos(facing) * 2.4, lookAt: { x: lounge.x, y: lounge.y + 1, z: lounge.z }, mode: 'spectator', fov: 55 });
  const leisure = new Set();
  for (let i = 0; i < 420 && leisure.size < 3; i++) {
    const a = await look(), channel = a.gesture === 'DEV_IDLE' ? 3 : 0;
    if (['EAT', 'PHONE', 'DEV_IDLE'].includes(a.gesture) && a.animation[channel] < -1.2 && !leisure.has(a.gesture)) {
      leisure.add(a.gesture); evidence.push({ leisure: a, screenshot: await shot('animation-leisure-' + a.gesture.toLowerCase()) });
    }
    await sleep(100);
  }
  assert.deepEqual([...leisure].sort(), ['DEV_IDLE', 'EAT', 'PHONE'], 'Completed lounge residents visibly eat, use phones and rest');
  await fixture('/_qa/studio?thread=' + encodeURIComponent(id) + '&state=failed');
  const failed = await until(look, a => a.expression === 'FRUSTRATED', 'failure expression');
  evidence.push({ failed, screenshot: await shot('animation-frustrated') });
  await fixture('/_qa/question?thread=' + encodeURIComponent(id) + '&mode=free');
  const waiting = await until(look, a => a.expression === 'WAITING', 'request expression');
  evidence.push({ waiting, screenshot: await shot('animation-waiting') });
  await fixture('/_qa/offline');
  const offline = await until(look, a => a.expression === 'OFFLINE' && a.typing < .01 && a.mouse < .01, 'offline motion stops');
  assert.equal(offline.gesture, 'NONE', 'Offline work has no social or activity gesture');
  evidence.push({ offline });
  assert.deepEqual(await fixture('/_qa/dispatches'), before, 'Animation and assignment execute no T3 work');
  await mkdir('artifacts/animations', { recursive: true });
  await writeFile('artifacts/animations/qa.json', JSON.stringify({ id, evidence }, null, 2));
  console.log('PASS varied typing, mouse pauses, hand motion, work postures, rendered snack/phone/idle gestures, state expressions, no implicit writes; isolated synthetic game');
} finally { await fixture('/_qa/reset').catch(() => {}); await dev.call('dev.screen', { open: null }).catch(() => {}); dev.close(); }
