// Native click routing, compact scale, and T3-owned shelf flags. Synthetic pairings only.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';
const port = Number(process.argv[2] ?? 27939), fixturePort = Number(process.argv[3] ?? 25687);
assert.ok(fixturePort >= 25680 && fixturePort <= 25688);
const dev = await DevClient.connect({ port, timeoutMs: 45000 });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const menu = () => dev.call('dev.t3.menu');
const click = label => dev.call('dev.t3.menu', { click: label });
const point = async (x, y) => { await dev.call('dev.t3.menu', { clickAt: { x, y } }); await sleep(150); return menu(); };
const fixture = async path => { const r = await fetch(`http://127.0.0.1:${fixturePort}${path}`); assert.ok(r.ok); return r.json(); };
async function until(predicate) {
  for (let i = 0; i < 100; i++) { const m = await menu(); if (predicate(m)) return m; await sleep(100); }
  assert.fail('Sidebar did not reach the expected native state');
}
const evidence = [];
try {
  await dev.call('dev.screen', { open: 't3' }); await menu(); // Enforce fixture credentials before any mutation.
  await fixture('/_qa/reset'); const before = await fixture('/_qa/dispatches');
  await dev.call('dev.key', { key: 'f', modifiers: 64 });
  await dev.call('dev.key', { key: 'a', modifiers: 64 }); await dev.call('dev.key', { key: 'backspace' });
  await dev.call('dev.screen', { open: null }); await dev.call('dev.hud.guiScale', { scale: 4 });
  assert.equal((await dev.call('dev.state')).window.guiScale, 4);
  await dev.call('dev.screen', { open: 't3' }); assert.equal((await dev.call('dev.state')).window.guiScale, 2);
  await dev.call('dev.t3.menu', { focusThread: 'A-done-2' });
  await fixture('/_qa/shelves');
  let m = await until(m => m.chat.workingCount === 1 && m.chat.settledCount === 1 && m.chat.workingHeaderY >= 0 && m.chat.settledHeaderY >= 0 && m.chat.focusedVisible);
  evidence.push(m.chat);
  await point(28, m.chat.workingHeaderY + 5); m = await until(m => !m.chat.workingExpanded);
  await point(28, m.chat.workingHeaderY + 5); m = await until(m => m.chat.workingExpanded);
  await point(28, m.chat.workingHeaderY + 14 + 17); await until(m => m.chat.focusedThread === 'A-done-1' && m.chat.focusedVisible);
  m = await menu(); await point(28, m.chat.settledHeaderY + 5); m = await until(m => m.chat.settledExpanded);
  await point(28, m.chat.settledHeaderY + 14 + 17); await until(m => m.chat.focusedThread === 'A-done-0' && m.chat.focusedVisible);
  await dev.call('dev.screenshot', { name: 'release-1.0.1-sidebar', hideHud: false });
  await click('Filter'); assert.equal((await menu()).chat.filtersOpen, true);
  await click('Workspace'); assert.equal((await menu()).screen, 'T3ToolsScreen');
  await click('Back'); assert.equal((await menu()).chat.filtersOpen, true, 'Back retains sidebar scope presentation');
  await click('Filter');
  for (const scale of [2, 3, 4]) {
    await dev.call('dev.hud.guiScale', { scale });
    await dev.call('dev.screen', { open: 't3-tools' }); assert.equal((await dev.call('dev.state')).window.guiScale, 2);
    await dev.call('dev.screen', { open: null }); assert.equal((await dev.call('dev.state')).window.guiScale, scale, 'World GUI preference is restored');
  }
  assert.deepEqual(await fixture('/_qa/dispatches'), before, 'Sidebar navigation creates no chats or runs');
  await mkdir('artifacts/qa', { recursive: true }); await writeFile('artifacts/qa/t3-sidebar.json', JSON.stringify(evidence, null, 2));
  console.log('PASS compact scale, world-scale restoration, exact shelf clicks, collapse/expand, visible focus, Back state, no T3 writes');
} finally { await fixture('/_qa/reset').catch(() => {}); await dev.call('dev.screen', { open: null }).catch(() => {}); dev.close(); }
