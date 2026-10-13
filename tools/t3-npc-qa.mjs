// Exercise NPC controls in a disposable game paired only to synthetic T3 fixtures.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';
const port = Number(process.argv[2] ?? 27879), fixturePort = Number(process.argv[3] ?? 25680);
assert.ok(fixturePort >= 25680 && fixturePort <= 25688, 'Use a supported synthetic fixture port');
const base = `http://127.0.0.1:${fixturePort}`;
const dev = await DevClient.connect({ port, timeoutMs: 45000 });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const evidence = [];
const menu = () => dev.call('dev.t3.menu');
const click = label => dev.call('dev.t3.menu', { click: label });
async function fixture(path) { const r = await fetch(base + path); assert.ok(r.ok); return r.json(); }
async function until(read, check, label) {
  for (let i = 0; i < 100; i++) { const value = await read(); if (check(value)) return value; await sleep(150); }
  assert.fail(`Timed out: ${label}`);
}
async function npc() {
  await until(() => dev.call('dev.agents'), s => s.agents.some(a => a.id === 'marlow'), 'resident visible');
  await dev.call('dev.agents.card', { agent: 'marlow' });
  return until(menu, m => m.npc?.bound, 'NPC binding');
}
function bounds(m, state) {
  const width = Math.ceil(state.window.width / state.window.guiScale), height = Math.ceil(state.window.height / state.window.guiScale);
  for (const a of m.widgets) assert.ok(a.x >= 0 && a.y >= 0 && a.x + a.width <= width && a.y + a.height <= height, `Outside screen: ${a.label}`);
  for (let i = 0; i < m.widgets.length; i++) for (let j = i + 1; j < m.widgets.length; j++) {
    const a = m.widgets[i], b = m.widgets[j];
    assert.ok(!(a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y), `Overlapping ${a.label} / ${b.label}`);
  }
}
try {
  await fixture('/_qa/reset');
  await until(() => dev.call('dev.t3'), s => s.connected && s.threads === 26, 'fixture connection');
  const initial = await npc(); const id = initial.npc.threadId, owner = initial.npc.owner;
  assert.equal(id, 'A-waiting');
  await click('Desk'); if ((await menu()).widgets.some(w => w.label === 'Pin desk')) await click('Pin desk');
  await npc();
  const before = await fixture('/_qa/dispatches');
  for (const scale of [2, 3, 4]) {
    await dev.call('dev.hud.guiScale', { scale }); const state = await dev.call('dev.state');
    await npc();
    for (const tab of ['Chat', 'Runs', 'Workspace', 'Desk']) {
      if (tab !== 'Chat') await click(tab);
      const m = await menu(); bounds(m, state); assert.equal(m.npc.threadId, id); assert.equal(m.npc.owner, owner);
      evidence.push({ scale, tab, actions: m.widgets.map(w => w.label) });
    }
  }
  assert.deepEqual(await fixture('/_qa/dispatches'), before, 'Opening menus cannot execute work');
  await dev.call('dev.hud.guiScale', { scale: 3 });
  for (const [tab, action, screen] of [
    ['Chat', 'Saved history', 'T3HistoryScreen'], ['Workspace', 'Review changes', 'T3ReviewScreen'],
    ['Runs', 'Run queue', 'T3QueueScreen'], ['Runs', 'Delegated tasks', 'T3TasksScreen'],
    ['Runs', 'Schedules', 'T3SchedulesScreen'], ['Workspace', 'Worktrees & handoff', 'T3WorkspaceScreen'],
    ['Workspace', 'Git & pull requests', 'T3GitScreen'], ['Workspace', 'Model options', 'T3OptionsScreen']
  ]) {
    await npc(); if (tab !== 'Chat') await click(tab); await click(action); const m = await menu();
    assert.equal(m.screen, screen); if (m.threadId) { assert.equal(m.threadId, id); assert.equal(m.owner, owner); }
    evidence.push({ action, screen });
  }
  // An NPC approval answers its exact request, not the other machine's same-ID request.
  await npc(); await click('Answer request');
  await until(menu, m => m.chat?.focusedThread === id && m.widgets.some(w => w.label === 'No (N)' && w.active), 'approval screen');
  await click('No (N)');
  const approval = await until(() => fixture('/_qa/dispatches'), list => list.some(c => c.type === 'runtime-request.respond'), 'approval receipt');
  assert.equal(approval.at(-1).threadId, id); assert.equal(approval.at(-1).requestId, 'shared-request');
  await fixture('/_qa/npc?thread=' + id + '&activity=editing');
  await npc(); await until(menu, m => m.widgets.some(w => w.label === 'Steer run' && w.active), 'active run');
  for (const [action, delivery, text] of [['Reply', 'queue', 'NPC fixture queued reply'], ['Steer run', 'steer', 'NPC fixture steering']]) {
    await npc(); await click(action); let m = await menu(); assert.equal(m.screen, 'T3DraftEditorScreen');
    assert.equal(m.draft.threadId, id); assert.equal(m.draft.owner, owner); assert.equal(m.draft.delivery, delivery);
    await dev.call('dev.key', { key: 'a', modifiers: 64 }); await dev.call('dev.type', { text });
    const sendLabel = delivery === 'steer' ? 'Steer (Ctrl+Enter)' : 'Send (Ctrl+Enter)';
    await until(menu, m => m.widgets.some(w => w.label === sendLabel && w.active), 'composer ready');
    await click(sendLabel);
    const commands = await until(() => fixture('/_qa/dispatches'), list => list.some(c => c.type === 'message.dispatch' && c.text === text), 'message receipt');
    const command = commands.find(c => c.type === 'message.dispatch' && c.text === text); assert.equal(command.threadId, id);
    assert.equal(command.dispatchMode.type, delivery === 'steer' ? 'steer_active' : 'queue_after_active');
    evidence.push({ action, threadId: command.threadId, delivery: command.dispatchMode.type });
  }
  await npc(); await click('Runs'); await click('Schedules');
  await until(() => dev.call('dev.t3.form'), m => !m.busy, 'schedule list');
  await dev.call('dev.t3.form', { fields: { Title: 'NPC schedule fixture', Prompt: 'Fixture reminder only' }, action: 'Save' });
  await until(() => dev.call('dev.t3.form'), m => !m.busy && m.output.includes('NPC schedule fixture'), 'saved schedule');
  await npc(); await click('Runs');
  const scheduled = await until(menu, m => m.npc.schedules.includes('NPC schedule fixture'), 'NPC upcoming schedule');
  evidence.push({ schedules: scheduled.npc.schedules });
  // Current receipts specialize both the world label and the actual body posture.
  for (const [activity, posture] of [['reading', 'READ'], ['editing', 'SIT_TYPE'], ['testing', 'LEAN_BENCH'], ['reviewing', 'REVIEW'], ['thinking', 'SIT_THINK']]) {
    await fixture('/_qa/npc?thread=' + id + '&activity=' + activity);
    const state = await until(() => dev.call('dev.agents'), s => s.agents.some(a => a.id === 'marlow' && a.state === activity), `NPC ${activity}`);
    const pose = await until(() => dev.call('dev.agents.look', { agent: 'marlow' }), s => s.agents?.some(a => a.posture === posture), `posture ${posture}`);
    evidence.push({ activity, posture, state: state.agents.find(a => a.id === 'marlow').state, pose });
  }
  await npc(); await click('Runs'); const children = await menu(); assert.match(children.npc.delegation, /Fixture delegated task.*Reading fixture sources/);
  await dev.call('dev.screenshot', { name: 'npc-controls-runs', hideHud: false });
  await npc(); await click('Stop run');
  await until(() => fixture('/_qa/dispatches'), list => list.some(c => c.type === 'run.interrupt' && c.threadId === id), 'stop receipt');
  await npc(); await click('Desk'); await click('Reassign chat'); const assignment = await menu(); assert.equal(assignment.screen, 'T3AssignmentScreen');
  // Search through the actual text widget; choose a visible existing conversation.
  await dev.call('dev.type', { text: 'Greeting' }); await sleep(200);
  const choices = await menu(); const chosen = choices.widgets.find(w => w.height === 34 && w.active); assert.ok(chosen);
  await click(chosen.label); const reassigned = await until(menu, m => m.screen === 'T3TaskScreen' && m.npc.bound && m.npc.threadId !== id, 'reassigned NPC');
  evidence.push({ reassigned: reassigned.npc.threadId });
  await click('Desk'); await click('Unpin desk'); const unpinned = await menu(); assert.ok(unpinned.widgets.some(w => w.label === 'Pin desk'));
  const all = await fixture('/_qa/dispatches'); assert.equal(all.filter(c => c.type === 'thread.create').length, 0);
  await mkdir('artifacts/qa', { recursive: true }); await writeFile('artifacts/qa/t3-npc.json', JSON.stringify(evidence, null, 2));
  console.log('PASS NPC tabs/scales, exact-owner workflows, approvals, queued reply, steering, schedules, activity postures, review, assignment and unpin; synthetic fixtures only');
} finally { await dev.call('dev.hud.guiScale', { scale: 3 }).catch(() => {}); await dev.call('dev.screen', { open: null }).catch(() => {}); dev.close(); }
