// Real Minecraft input and rendering checks; dev.t3.menu enforces synthetic pairings.
import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';
const dev = await DevClient.connect({ port: Number(process.argv[2] ?? 27879) });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const evidence = [], issues = [];
const inspect = () => dev.call('dev.t3.menu');
async function open(screen) { await dev.call('dev.screen', { open: screen }); return inspect(); }
async function click(prefix) {
  let menu = await inspect();
  const widget = menu.widgets.find(w => w.active && w.label.replace(/^• /, '').startsWith(prefix));
  assert.ok(widget, `Missing action ${prefix}`);
  return dev.call('dev.t3.menu', { click: widget.label });
}
async function chooseTheme(label) {
  await open('t3-appearance');
  for (let i = 0; i < 6; i++) {
    const menu = await inspect();
    if (menu.widgets.some(w => w.label.replace(/^• /, '') === label)) { await click(label); return; }
    await click('Next');
  }
  assert.fail(`Theme ${label} not reachable`);
}
function bounds(menu, state) {
  const width = Math.ceil(state.window.width / state.window.guiScale), height = Math.ceil(state.window.height / state.window.guiScale);
  for (const widget of menu.widgets) {
    assert.ok(!/[Ââ]/.test(widget.label), `Invalid label encoding: ${widget.label}`);
    if (widget.x < 0 || widget.y < 0 || widget.x + widget.width > width || widget.y + widget.height > height) issues.push({ screen: menu.screen, scale: state.window.guiScale, outside: widget });
  }
  for (let i = 0; i < menu.widgets.length; i++) for (let j = i + 1; j < menu.widgets.length; j++) {
    const a = menu.widgets[i], b = menu.widgets[j];
    if (a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y) issues.push({ screen: menu.screen, scale: state.window.guiScale, overlap: [a, b] });
  }
  evidence.push({ screen: menu.screen, scale: state.window.guiScale, theme: menu.theme, widgets: menu.widgets.length });
}
try {
  await open('t3-mod-settings'); await click('Resident cast'); await dev.call('dev.screen', { open: null }); await sleep(1600);
  const agents = await dev.call('dev.agents'); assert.equal(agents.count, 18);
  for (const [id, skin] of Object.entries({ marlow: 'spartan', juniper: 'naruto', kit: 'snake', wren: 'mario', rowan: 'yoshi', tove: 'megaman' })) {
    const agent = agents.agents.find(a => a.id === id); assert.ok(agent.skin.endsWith(`/${skin}.png`), `${id} uses ${skin}`); assert.equal(agent.model, 'wide');
  }
  evidence.push({ gamingRoster: agents.agents.map(a => ({ id: a.id, skin: a.skin, model: a.model })) });
  await open('t3-look'); await click('Blue Craftsman'); await dev.call('dev.screen', { open: null }); await sleep(1200);
  assert.ok((await dev.call('dev.agents')).agents.find(a => a.id === 'marlow').skin.endsWith('/yoshi.png'));
  await open('t3-look'); await click('Shadow Scout'); await dev.call('dev.screen', { open: null }); await sleep(1200);
  assert.ok((await dev.call('dev.agents')).agents.find(a => a.id === 'marlow').skin.endsWith('/spartan.png'));
  const themes = JSON.parse(await readFile('assets-src/t3-themes.json', 'utf8')).themes;
  for (const preset of themes) {
    await chooseTheme(preset.label); await click('Default');
    for (const dark of [false, true]) {
      let menu = await inspect(); if (menu.darkTheme !== dark) { await click('Mode'); menu = await inspect(); }
      assert.equal(menu.theme, preset.id); assert.equal(menu.colors.canvas, preset[dark ? 'dark' : 'light'].canvas);
      assert.equal(menu.colors.text, preset[dark ? 'dark' : 'light'].text);
      evidence.push({ palette: preset.id, dark, colors: menu.colors });
      if (['t3-code', 'ocean', 'iris'].includes(preset.id)) await dev.call('dev.screenshot', { name: `polish-${preset.id}-${dark ? 'dark' : 'light'}`, hideHud: false });
    }
  }
  await click('Violet'); assert.equal((await inspect()).colors.accent, '#8B5CF6');
  await click('Accent hex color'); await dev.call('dev.key', { key: 'end' });
  for (let i = 0; i < 7; i++) await dev.call('dev.key', { key: 'backspace' });
  await dev.call('dev.type', { text: '#xyz123' });
  assert.equal((await inspect()).widgets.find(w => w.label === 'Save accent').active, false, 'Invalid hex cannot be saved');
  for (let i = 0; i < 7; i++) await dev.call('dev.key', { key: 'backspace' });
  await dev.call('dev.type', { text: '#12abef' }); await click('Save accent');
  assert.equal((await inspect()).accentColor, '#12ABEF');
  await open('t3-appearance'); assert.equal((await inspect()).accentColor, '#12ABEF'); await click('Violet');
  await open('t3'); assert.equal((await inspect()).colors.accent, '#8B5CF6');
  const screens = ['t3', 't3-tools', 't3-campus', 't3-connections', 't3-worktrees', 't3-git', 't3-schedules', 't3-options', 't3-queue', 't3-tasks', 't3-history', 't3-settings', 't3-library', 't3-board', 't3-reviews', 't3-terminal', 't3-mod-settings', 't3-appearance', 't3-look', 't3-displays'];
  for (const scale of [2, 3, 4]) {
    await dev.call('dev.hud.guiScale', { scale }); const state = await dev.call('dev.state');
    for (const screen of screens) bounds(await open(screen), state);
    await open('t3-mod-settings'); await click('Characters'); bounds(await inspect(), state);
    await click('Updates'); bounds(await inspect(), state);
    await open('t3-look'); const menu = await inspect(); if (menu.widgets.find(w => w.label === 'Next')?.active) { await click('Next'); bounds(await inspect(), state); }
  }
  await mkdir('artifacts/qa', { recursive: true }); await writeFile('artifacts/qa/t3-polish.json', JSON.stringify({ evidence, issues }, null, 2));
  assert.deepEqual(issues, [], JSON.stringify(issues, null, 2));
  await dev.call('dev.hud.guiScale', { scale: 3 }); await chooseTheme('Ocean'); let menu = await inspect(); if (!menu.darkTheme) await click('Mode'); await click('Default');
  await open('t3'); await dev.call('dev.screenshot', { name: 'polish-chat-ocean', hideHud: false });
  await open('t3-look'); await dev.call('dev.screenshot', { name: 'polish-character-picker', hideHud: false });
  console.log('PASS 18 visible NPCs, six initial gaming textures/models, gaming look switching, all 12 T3 palette variants, saved accent across screens, and menu bounds at GUI scales 2/3/4; fixtures only');
} finally { await dev.call('dev.hud.guiScale', { scale: 3 }); await dev.call('dev.screen', { open: null }); dev.close(); }
