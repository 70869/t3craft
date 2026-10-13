import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';

const dev = await DevClient.connect({ port: Number(process.argv[2] ?? 27879) });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const evidence = [];
async function click(prefix) {
  const menu = await dev.call('dev.t3.menu'); // refuses real credentials
  const widget = menu.widgets.find(w => w.active && w.label.replace(/^• /, '').startsWith(prefix));
  assert.ok(widget, `Missing ${prefix}`);
  return dev.call('dev.t3.menu', { click: widget.label });
}
async function poll(test, label, timeout = 15000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    const agents = await dev.call('dev.agents');
    const look = await dev.call('dev.agents.look');
    if (test(agents, look)) { evidence.push({ label, agents, look }); return { agents, look }; }
    await pause(200);
  }
  assert.fail(label);
}
try {
  await dev.call('dev.screen', { open: 't3-mod-settings' });
  await click('Resident cast');
  await dev.call('dev.screen', { open: null });
  await pause(1800);
  const agents = await dev.call('dev.agents', { settle: true });
  evidence.push(agents);
  assert.equal(agents.count, 18, 'Every campus desk can have a resident');
  assert.equal(new Set(agents.agents.map(a => a.skin)).size, 18, 'All residents have distinct gaming looks');
  assert.ok(agents.agents.some(a => a.skin.endsWith('/yoshi.png')));
  assert.ok(agents.agents.some(a => a.skin.endsWith('/megaman.png')));
  const sitter = agents.agents.find(a => a.anchor === 'lounge');
  assert.ok(sitter, 'Use a clean campus fixture');
  const sitterId = sitter.id;
  await dev.call('dev.command', { cmd: 'setblock -15 66 0 minecraft:oak_stairs[facing=north]' });
  await dev.call('dev.command', { cmd: 'setblock -14 66 0 minecraft:air' });
  await poll((a, l) => l.agents.find(n => n.id === sitterId)?.seated && a.agents.find(n => n.id === sitterId)?.x === -14.5, 'Resident follows moved chair');
  await dev.call('dev.command', { cmd: 'setblock -15 66 0 minecraft:oak_stairs[facing=south]' });
  await poll((a, l) => l.agents.find(n => n.id === sitterId)?.seated && Math.abs(a.agents.find(n => n.id === sitterId)?.yaw) === 180, 'Resident follows rotated chair');
  await dev.call('dev.command', { cmd: 'setblock -15 67 0 minecraft:stone' });
  await poll((a, l) => !l.agents.find(n => n.id === sitterId)?.seated && !a.agents.find(n => n.id === sitterId)?.walking, 'Obstructed chair cannot keep a seated pose');
  await dev.call('dev.command', { cmd: 'setblock -15 67 0 minecraft:air' });
  await dev.call('dev.command', { cmd: 'setblock -15 66 0 minecraft:air' });
  await dev.call('dev.command', { cmd: 'setblock -14 66 0 minecraft:oak_stairs[facing=north]' });
  await poll((a, l) => l.agents.find(n => n.id === sitterId)?.seated && a.agents.find(n => n.id === sitterId)?.x === -13.5, 'Resident recovers when chair is restored');
  // Camera is 1.1 blocks from the sitter: this caught the previous 1.3-block hiding rule.
  await dev.call('dev.camera', { x: -13.5, y: 67.35, z: 1.73, lookAt: { x: -13.5, y: 67.35, z: .63 }, mode: 'spectator' });
  await pause(400);
  await poll(a => a.agents.find(n => n.id === sitterId)?.plate, 'Close nameplate stays visible');
  await dev.call('dev.screenshot', { name: 'residents-close-label', waitChunks: false });
  for (const scale of [2, 3, 4]) {
    await dev.call('dev.hud.guiScale', { scale });
    await dev.call('dev.screen', { open: 't3-displays' });
    const menu = await dev.call('dev.t3.menu'), state = await dev.call('dev.state');
    const width = Math.ceil(state.window.width/state.window.guiScale), height = Math.ceil(state.window.height/state.window.guiScale);
    for (const widget of menu.widgets) assert.ok(widget.x >= 0 && widget.y >= 0 && widget.x+widget.width <= width && widget.y+widget.height <= height, `Display bounds at scale ${scale}`);
    for (let i=0; i<menu.widgets.length; i++) for (let j=i+1; j<menu.widgets.length; j++) {
      const a=menu.widgets[i], b=menu.widgets[j];
      assert.ok(!(a.x<b.x+b.width && a.x+a.width>b.x && a.y<b.y+b.height && a.y+a.height>b.y), `Display overlap at scale ${scale}`);
    }
    await click('Status graph');
    await dev.call('dev.screen', { open: 't3-displays' });
    assert.ok((await dev.call('dev.t3.menu')).widgets.some(w => w.label === '• Status graph'), 'View survives screen reopen');
    evidence.push({ displayScale: scale, menu });
  }
  await dev.call('dev.hud.guiScale', { scale: 3 });
  await dev.call('dev.screen', { open: null });
  await dev.call('dev.camera', { x: .5, y: 69.5, z: 23, lookAt: { x: .5, y: 69.55, z: 15.5 }, mode: 'spectator' });
  await dev.call('dev.screenshot', { name: 'residents-status-graph' });
  await dev.call('dev.camera', { x: -5.5, y: 69, z: 15.5, lookAt: { x: -9, y: 69, z: 15.5 }, mode: 'spectator' });
  await dev.call('dev.screenshot', { name: 'residents-themed-wall' });
  console.log('PASS 18 gaming residents; moved/rotated/obstructed/restored chairs; close nameplate; saved display graph; display layouts at GUI scales 2/3/4');
} finally {
  await mkdir('artifacts/qa', { recursive: true });
  await writeFile('artifacts/qa/t3-residents.json', JSON.stringify(evidence, null, 2));
  await dev.call('dev.screen', { open: null });
  await dev.call('dev.hud.guiScale', { scale: 3 });
  dev.close();
}
