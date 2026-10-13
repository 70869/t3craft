// Actual UI and resident behavior against an isolated synthetic pairing.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';
const port = Number(process.argv[2] ?? 27889), fixturePort = Number(process.argv[3] ?? 25686);
assert.ok(fixturePort >= 25680 && fixturePort <= 25688, 'Use a synthetic fixture port');
const base = `http://127.0.0.1:${fixturePort}`, dev = await DevClient.connect({ port, timeoutMs: 45000 });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms)), evidence = [];
const menu = () => dev.call('dev.t3.menu');
const click = label => dev.call('dev.t3.menu', { click: label });
async function fixture(path) { const r = await fetch(base + path); assert.ok(r.ok); return r.json(); }
async function until(read, predicate, label) {
  for (let n = 0; n < 100; n++) { const value = await read(); if (predicate(value)) return value; await sleep(150); }
  assert.fail(`Timed out: ${label}`);
}
function bounds(menu, state) {
  const w = Math.ceil(state.window.width / state.window.guiScale), h = Math.ceil(state.window.height / state.window.guiScale);
  for (const a of menu.widgets) assert.ok(a.x >= 0 && a.y >= 0 && a.x + a.width <= w && a.y + a.height <= h, `Outside screen: ${a.label}`);
  for (let i = 0; i < menu.widgets.length; i++) for (let j = i + 1; j < menu.widgets.length; j++) {
    const a = menu.widgets[i], b = menu.widgets[j];
    assert.ok(!(a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y), `Overlap: ${a.label} / ${b.label}`);
  }
}
async function residentShot(name) {
  await dev.call('dev.screen', { open: null });
  const agent = (await dev.call('dev.agents')).agents.find(a => a.id === 'marlow');
  await dev.call('dev.camera', { x: agent.x + 2.5, y: agent.y + 1.7, z: agent.z + 3.5, lookAt: { x: agent.x, y: agent.y + 1, z: agent.z }, mode: 'spectator', fov: 55 });
  await dev.call('dev.screenshot', { name, hideHud: false });
  await dev.call('dev.agents.card', { agent: 'marlow' });
}
try {
  await until(() => dev.call('dev.state'), s => s.inWorld && s.ready && !s.loading, 'isolated world ready');
  await dev.call('dev.screen', { open: 't3-work' }); // Menu hook refuses real pairings before any fixture mutation.
  await menu(); await fixture('/_qa/reset');
  await until(() => dev.call('dev.t3'), s => s.connected && s.threads === 26, 'fixture connection');
  const before = await fixture('/_qa/dispatches');
  for (const scale of [2, 3, 4]) {
    await dev.call('dev.hud.guiScale', { scale }); const state = await dev.call('dev.state');
    for (const screen of ['t3-guide', 't3-work', 't3', 't3-tools', 't3-mod-settings']) {
      await dev.call('dev.screen', { open: screen }); await sleep(120); const m = await menu(); bounds(m, state);
      evidence.push({ scale, screen, widgets: m.widgets });
      if (['t3-guide', 't3-work'].includes(screen)) await dev.call('dev.screenshot', { name: `flow-${screen.slice(3)}-${scale}`, hideHud: false });
    }
  }
  assert.deepEqual(await fixture('/_qa/dispatches'), before, 'Opening flow menus executes no work');
  await dev.call('dev.hud.guiScale', { scale: 3 });
  await dev.call('dev.screen', { open: 't3-work' });
  await click('Answer request');
  const request = await menu(); assert.ok(['T3Screen', 'T3QuestionScreen'].includes(request.screen)); evidence.push({ request: request.screen });
  await dev.call('dev.screen', { open: 't3-work' }); await click('Needs you >');
  assert.ok((await menu()).widgets.some(w => w.label === 'Active runs >'));
  await click('Active runs >'); await click('At desks >'); await click('Recent chats >'); await click('Failed runs >');
  assert.ok((await menu()).widgets.some(w => w.label === 'All chats >'));
  // Type one character per tick to ensure filtering keeps keyboard focus through rebuilds.
  let m = await menu();
  await click('Next'); await click('Search work');
  for (const c of 'Greeting') { await dev.call('dev.type', { text: c }); await sleep(100); }
  assert.ok((await menu()).widgets.some(w => w.label === 'Run greeting tests'), 'Search resets pagination even when all rows still match');
  await dev.call('dev.key', { key: 'a', modifiers: 64 }); await dev.call('dev.key', { key: 'backspace' });
  await click('Search work');
  for (const c of 'Export') { await dev.call('dev.type', { text: c }); await sleep(100); }
  m = await menu(); assert.ok(m.widgets.some(w => w.label === 'Export format')); assert.ok(!m.widgets.some(w => w.label === 'Run greeting tests')); evidence.push({ search: m.widgets });
  assert.deepEqual(await fixture('/_qa/dispatches'), before, 'Navigation and search execute no work');
  await dev.call('dev.screen', { open: 't3' });
  await dev.call('dev.key', { key: 'f', modifiers: 64 }); await dev.call('dev.type', { text: 'Run greeting tests' });
  const navigation = (await menu()).chat; await click('Your work'); await click('Chat');
  assert.equal((await menu()).chat.query, navigation.query, 'Returning to the same chat preserves search');
  await dev.call('dev.key', { key: 'f', modifiers: 64 }); await dev.call('dev.key', { key: 'a', modifiers: 64 }); await dev.call('dev.key', { key: 'backspace' });
  await dev.call('dev.key', { key: 'enter' });
  const current = (await menu()).chat.focusedThread;
  await dev.call('dev.key', { key: 'tab', modifiers: 64 });
  assert.notEqual((await menu()).chat.focusedThread, current, 'Ctrl+Tab advances to another chat');
  await dev.call('dev.key', { key: 'tab', modifiers: 65 });
  assert.equal((await menu()).chat.focusedThread, current, 'Ctrl+Shift+Tab returns to the previous chat');
  // Bind an existing conversation explicitly to Marlow, using its local assignment screen.
  await dev.call('dev.agents.card', { agent: 'marlow' }); await click('Desk'); await click('Reassign chat');
  await dev.call('dev.type', { text: 'Run greeting tests' }); await sleep(300);
  m = await menu(); const assign = m.widgets.find(w => w.label === 'Run greeting tests');
  assert.ok(assign, 'Approval chat available for assignment'); await click(assign.label);
  await dev.call('dev.agents.card', { agent: 'marlow' }); m = await menu(); const id = m.npc.threadId;
  await fixture('/_qa/npc?thread=' + encodeURIComponent(id) + '&activity=running');
  await until(() => dev.call('dev.agents'), s => s.agents.some(a => a.id === 'marlow' && a.state === 'running'), 'command receipt');
  await dev.call('dev.agents', { settle: true });
  const watch = await until(() => dev.call('dev.agents.look', { agent: 'marlow' }), s => s.agents?.some(a => a.posture === 'WATCH'), 'command watching posture');
  evidence.push({ commandWatching: watch });
  await residentShot('flow-command-watching');
  await fixture('/_qa/studio?thread=' + encodeURIComponent(id) + '&state=completed');
  await dev.call('dev.agents.card', { agent: 'marlow' });
  await until(menu, s => s.widgets.some(w => w.label === 'Review changes'), 'checkpoint next action');
  await until(() => dev.call('dev.agents'), s => s.agents.some(a => a.id === 'marlow' && a.activity.startsWith('Review ready')), 'review-ready label');
  await dev.call('dev.agents', { settle: true });
  const reviewPose = await until(() => dev.call('dev.agents.look', { agent: 'marlow' }), s => s.agents?.some(a => a.posture === 'REVIEW'), 'settled review pose');
  await residentShot('flow-review-ready');
  await click('Review changes'); assert.equal((await menu()).screen, 'T3ReviewScreen'); evidence.push({ reviewReady: reviewPose });
  await fixture('/_qa/studio?thread=' + encodeURIComponent(id) + '&state=failed');
  await dev.call('dev.agents.card', { agent: 'marlow' });
  await until(menu, s => s.widgets.some(w => w.label === 'Inspect failure'), 'failure next action outranks old checkpoint');
  await until(() => dev.call('dev.agents'), s => s.agents.some(a => a.id === 'marlow' && a.state === 'error'), 'failed resident');
  await dev.call('dev.agents', { settle: true });
  await until(() => dev.call('dev.agents.look', { agent: 'marlow' }), s => s.agents?.some(a => a.posture === 'SIT_SCRATCH'), 'failure pose outranks old checkpoint');
  for (const ident of ['A-done-0', 'A-done-1', 'A-done-2', 'A-waiting']) await fixture('/_qa/question?thread=' + ident + '&mode=free');
  await dev.call('dev.screen', { open: null }); await dev.call('dev.command', { cmd: 'gamemode creative' });
  await dev.call('dev.command', { cmd: 'tp @s 0.5 66 10.5' });
  const waiting = await until(() => dev.call('dev.agents'), s => s.agents.filter(a => a.state === 'waiting_user').length >= 4, 'waiting roster');
  await sleep(1200); const crowd = await dev.call('dev.agents');
  const approaching = crowd.agents.filter(a => a.state === 'waiting_user' && a.station === 'user');
  assert.ok(approaching.length <= 2, 'No more than two request owners approach');
  assert.ok(crowd.agents.some(a => a.state === 'waiting_user' && a.station === 'desk'), 'Other requests remain visible at desks');
  evidence.push({ waiting: waiting.agents.filter(a => a.state === 'waiting_user'), approaching: approaching.length, crowd });
  await dev.call('dev.screenshot', { name: 'flow-waiting-residents', hideHud: false });
  await fixture('/_qa/offline');
  await until(() => dev.call('dev.agents'), s => s.agents.some(a => a.id === 'marlow' && a.stale), 'offline owning machine');
  await dev.call('dev.agents.card', { agent: 'marlow' });
  await until(menu, s => s.widgets.some(w => w.label === 'Reconnect machine'), 'offline next action');
  await click('Reconnect machine'); assert.equal((await menu()).screen, 'T3ConnectionsScreen');
  await fixture('/_qa/reset');
  await mkdir('artifacts/qa', { recursive: true });
  await writeFile('artifacts/qa/t3-flow.json', JSON.stringify({ evidence }, null, 2));
  console.log('PASS flow layouts at scales 2/3/4, exact request/review/offline actions, live search and pagination, retained chat navigation, Ctrl+Tab, no implicit writes, command watching, review poses and bounded request approach');
} finally { dev.close(); }
