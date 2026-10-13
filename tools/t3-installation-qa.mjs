// First-launch setup in an isolated game starting with an empty pairing config.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';
const port = Number(process.argv[2] ?? 27909), fixturePort = Number(process.argv[3] ?? 25688);
assert.ok(fixturePort >= 25680 && fixturePort <= 25688, 'Use only an isolated fixture');
const base = `http://127.0.0.1:${fixturePort}`;
const dev = await DevClient.connect({ port, timeoutMs: 45000 });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const evidence = [];
function bounds(menu, state) {
  const width = Math.ceil(state.window.width / state.window.guiScale), height = Math.ceil(state.window.height / state.window.guiScale);
  for (const widget of menu.widgets) assert.ok(widget.x >= 0 && widget.y >= 0 && widget.x + widget.width <= width && widget.y + widget.height <= height, `Outside screen: ${widget.label}`);
}
try {
  await dev.waitInWorld({ timeoutMs: 180000 });
  assert.equal((await dev.call('dev.t3')).paired, false, 'Start with a new unpaired QA config');
  assert.equal((await dev.call('dev.state')).screen?.class.split('.').at(-1), 'T3ConnectionsScreen', 'First launch must offer pairing without a key press');
  const before = await (await fetch(base + '/_qa/dispatches')).json();
  for (const scale of [2, 3, 4]) {
    await dev.call('dev.hud.guiScale', { scale });
    const state = await dev.call('dev.state'), menu = await dev.call('dev.t3.menu');
    bounds(menu, state);
    assert.ok(menu.widgets.some(w => w.label === 'Pair machine' && !w.active));
    await dev.call('dev.screenshot', { name: `pairing-${scale}`, hideHud: false });
    evidence.push({ scale, screen: menu.screen, widgets: menu.widgets });
  }
  await dev.call('dev.screen', { open: null }); await sleep(400);
  assert.equal((await dev.call('dev.state')).screen, null, 'Dismissed setup should not reopen each tick');
  await dev.call('dev.hud.guiScale', { scale: 3 });
  await dev.call('dev.screen', { open: 't3-connections' });
  await dev.call('dev.type', { text: base + '/pair#token=fixture-pairing' });
  await dev.call('dev.key', { key: 'enter' });
  for (let n = 0; n < 80 && !(await dev.call('dev.t3')).connected; n++) await sleep(150);
  assert.equal((await dev.call('dev.t3')).connected, true, 'Synthetic pairing must connect');
  assert.equal((await dev.call('dev.state')).screen?.class.split('.').at(-1), 'T3GuideScreen', 'Successful first pairing should continue to the guide');
  for (const scale of [2, 3, 4]) {
    await dev.call('dev.hud.guiScale', { scale });
    const menu = await dev.call('dev.t3.menu'); bounds(menu, await dev.call('dev.state'));
    evidence.push({ scale, screen: menu.screen, widgets: menu.widgets });
    await dev.call('dev.screenshot', { name: `paired-guide-${scale}`, hideHud: false });
  }
  assert.deepEqual(await (await fetch(base + '/_qa/dispatches')).json(), before, 'Pairing and onboarding must execute no T3 commands');
  await mkdir('artifacts/qa/installation', { recursive: true });
  await writeFile('artifacts/qa/installation/onboarding.json', JSON.stringify(evidence, null, 2));
  console.log('PASS automatic unpaired setup, dismiss/reopen, synthetic pairing, guide layout at scales 2/3/4, and zero T3 commands');
} finally { dev.close(); }
