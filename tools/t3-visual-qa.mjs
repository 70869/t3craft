// Repeatable visual review of the current native T3 campus, using synthetic conversations only.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';
import { loadScene, runScene } from './lib/scene.mjs';

const port = Number(process.argv[2] ?? 27899);
const fixturePort = Number(process.argv[3] ?? 25686);
const prefix = process.argv[4] ?? 'visual-final/';
assert.ok(fixturePort >= 25680 && fixturePort <= 25688, 'Use an isolated fixture port');
assert.match(prefix, /^\w[\w/-]*\/$/, 'Provide a relative screenshot prefix ending in /');
const dev = await DevClient.connect({ port, timeoutMs: 45000 });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function fixture(path, offset = 0) {
  const response = await fetch(`http://127.0.0.1:${fixturePort + offset}${path}`);
  assert.ok(response.ok, `Fixture request failed: ${path}`);
  return response.json();
}
async function reviewReady() {
  for (let n = 0; n < 100; n++) {
    const review = await dev.call('dev.t3.review');
    assert.equal(review.error, null);
    if (!review.loading && review.files === 2) return review;
    await sleep(100);
  }
  assert.fail('Checkpoint review did not load');
}
function bounds(menu, state) {
  const w = Math.ceil(state.window.width / state.window.guiScale), h = Math.ceil(state.window.height / state.window.guiScale);
  for (const widget of menu.widgets) assert.ok(widget.x >= 0 && widget.y >= 0 && widget.x + widget.width <= w && widget.y + widget.height <= h, `Outside screen: ${widget.label}`);
}
try {
  await dev.waitInWorld({ timeoutMs: 45000 });
  await dev.call('dev.screen', { open: 't3' });
  await dev.call('dev.t3.menu'); // Refuses any real paired environment before fixture writes.
  const before = [await fixture('/_qa/dispatches'), await fixture('/_qa/dispatches', 1)];
  await dev.call('dev.hud.guiScale', { scale: 3 });
  await dev.call('dev.screen', { open: 't3-displays' });
  const overview = (await dev.call('dev.t3.menu')).widgets.find(w => w.label.replace(/^• /, '') === 'Chat overview');
  assert.ok(overview, 'Atrium overview choice must be available');
  await dev.call('dev.t3.menu', { click: overview.label });
  await dev.call('dev.screen', { open: 't3' });
  await dev.call('dev.t3.menu', { focusThread: 'A-done-0' });
  for (const [agent, activity] of [['kit', 'editing'], ['wren', 'reading'], ['rowan', 'running']]) {
    await dev.call('dev.agents.card', { agent });
    const menu = await dev.call('dev.t3.menu');
    assert.ok(menu.npc?.threadId, `${agent} needs an assigned synthetic conversation`);
    assert.match(menu.npc.threadId, /^[AB]-(done-\d+|waiting)$/);
    await fixture(`/_qa/npc?thread=${encodeURIComponent(menu.npc.threadId)}&activity=${activity}`, menu.npc.threadId.startsWith('B-') ? 1 : 0);
  }
  await dev.call('dev.screen', { open: null });
  await sleep(2000);
  await dev.call('dev.agents', { settle: true });
  const screenChecks = [];
  for (const scale of [2, 3, 4]) {
    await dev.call('dev.hud.guiScale', { scale });
    const state = await dev.call('dev.state');
    await dev.call('dev.agents.card', { agent: 'kit' });
    await dev.call('dev.t3.menu', { click: 'Workspace' });
    await dev.call('dev.t3.menu', { click: 'Review changes' });
    await reviewReady();
    bounds(await dev.call('dev.t3.menu'), state);
    const patch = await dev.call('dev.t3.review', { selectFile: 'src/greeting.ts' });
    assert.equal(patch.selectedFile, 'src/greeting.ts'); assert.equal(patch.hunks, 2);
    await dev.call('dev.key', { key: 'key.keyboard.page.down' });
    assert.ok((await dev.call('dev.t3.review')).scroll > patch.scroll, 'Next change must advance the patch');
    await dev.call('dev.t3.review', { selectFile: 'src/greeting.ts' });
    const diffShot = await dev.call('dev.screenshot', { name: `${prefix}review-scale-${scale}`, hideHud: false });
    const deleted = await dev.call('dev.t3.review', { selectFile: 'obsolete.txt' });
    assert.equal(deleted.selectedFile, 'obsolete.txt'); assert.equal(deleted.hunks, 1);
    await dev.call('dev.screen', { open: 't3-library' });
    bounds(await dev.call('dev.t3.menu'), state);
    const libraryShot = await dev.call('dev.screenshot', { name: `${prefix}library-scale-${scale}`, hideHud: false });
    screenChecks.push({ scale, patch, deleted, diff: diffShot.path, library: libraryShot.path });
  }
  await dev.call('dev.screen', { open: null });
  await dev.call('dev.hud.guiScale', { scale: 3 });
  await dev.call('dev.screen', { open: 't3' });
  await dev.call('dev.t3.menu', { focusThread: 'A-done-0' });
  await dev.call('dev.screen', { open: null });
  const summary = await runScene({ scene: loadScene('tools/scenes/qa.json'), dev, prefix });
  assert.deepEqual([await fixture('/_qa/dispatches'), await fixture('/_qa/dispatches', 1)], before, 'Visual review must not execute T3 commands');
  await mkdir('artifacts/qa/visual-pass', { recursive: true });
  await writeFile(`artifacts/qa/visual-pass/${prefix.replaceAll('/', '-')}manifest.json`, JSON.stringify({ ...summary, screenChecks }, null, 2));
  assert.ok(summary.ok && summary.counts.skipped === 0 && summary.shots.length === 10, 'All ten views must capture');
  console.log(JSON.stringify({ ok: summary.ok, counts: summary.counts, shots: summary.shots.map(s => ({ name: s.name, path: s.path, warnings: s.warnings })) }, null, 2));
} finally {
  await dev.call('dev.screen', { open: null }).catch(() => {});
  dev.close();
}
