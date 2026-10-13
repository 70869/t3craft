// Actual native widget interactions; the game must be paired to isolated fixtures only.
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';
const dev = await DevClient.connect({ port: Number(process.argv[2] ?? 27879) });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const evidence = [], issues = [];
const screens = ['agent-menu', 't3', 't3-tools', 't3-campus', 't3-connections', 't3-worktrees', 't3-git', 't3-schedules', 't3-options', 't3-queue', 't3-tasks', 't3-history', 't3-settings', 't3-library', 't3-board', 't3-reviews', 't3-terminal'];
try {
  await dev.call('dev.screen', { open: 't3' });
  await dev.call('dev.key', { key: 'f', modifiers: 64 });
  await dev.call('dev.type', { text: 'Export format' });
  let menu = await dev.call('dev.t3.menu');
  assert.equal(menu.chat.query, 'Export format'); assert.equal(menu.chat.matches, 1); assert.equal(menu.chat.composer, '');
  await dev.call('dev.key', { key: 'enter' });
  menu = await dev.call('dev.t3.menu'); assert.equal(menu.chat.focusedThread, 'B-waiting'); evidence.push({ search: menu.chat });
  // Searching must not trigger the waiting thread's numbered answers.
  await dev.call('dev.key', { key: 'f', modifiers: 64 });
  await dev.call('dev.key', { key: 'escape' });
  await dev.call('dev.type', { text: '1nonexistent' });
  menu = await dev.call('dev.t3.menu'); assert.equal(menu.chat.query, '1nonexistent'); assert.equal(menu.chat.matches, 0);
  await dev.call('dev.key', { key: 'escape' });
  await dev.call('dev.screen', { open: null });
  await dev.call('dev.agents.card', { agent: 'marlow' });
  menu = await dev.call('dev.t3.menu'); assert.equal(menu.screen, 'T3TaskScreen');
  await dev.call('dev.t3.menu', { click: 'Saved history' });
  assert.match((await dev.call('dev.state')).screen.class, /\.T3HistoryScreen$/);
  for (const scale of [2, 3, 4]) {
    await dev.call('dev.hud.guiScale', { scale });
    for (const screen of screens) {
      if (screen === 'agent-menu') await dev.call('dev.agents.card', { agent: 'marlow' });
      else await dev.call('dev.screen', { open: screen });
      await sleep(80);
      const state = await dev.call('dev.state'), width = Math.ceil(state.window.width / state.window.guiScale), height = Math.ceil(state.window.height / state.window.guiScale);
      menu = await dev.call('dev.t3.menu');
      evidence.push({ screen, scale, widgets: menu.widgets });
      for (const w of menu.widgets) if (w.x < 0 || w.y < 0 || w.x + w.width > width || w.y + w.height > height) issues.push({ screen, scale, outside: w });
      for (let i = 0; i < menu.widgets.length; i++) for (let j = i + 1; j < menu.widgets.length; j++) {
        const a = menu.widgets[i], b = menu.widgets[j];
        if (a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y) issues.push({ screen, scale, overlap: [a, b] });
      }
      if (['t3', 't3-tools', 't3-campus', 't3-git'].includes(screen) && scale !== 2) await dev.call('dev.screenshot', { name: 'menus-' + screen.slice(3) + '-scale-' + scale, hideHud: false });
    }
  }
  await writeFile('artifacts/qa/t3-menus.json', JSON.stringify({ evidence, issues }, null, 2));
  assert.deepEqual(issues, [], JSON.stringify(issues, null, 2));
  await dev.call('dev.hud.guiScale', { scale: 3 });
  await dev.call('dev.screen', { open: null }); await dev.call('dev.command', { cmd: 'gamemode creative' });
  // Put the player away from the obstruction; adding a block under them moves them physically.
  await dev.call('dev.command', { cmd: 'tp @s 0.5 66 24.5' }); await sleep(500);
  await dev.call('dev.key', { mapping: 'key.agentcraft.campus' }); await sleep(200);
  assert.equal((await dev.call('dev.t3.menu')).screen, 'T3CampusScreen');
  const beforeVisit = (await dev.call('dev.state')).player;
  await dev.call('dev.command', { cmd: 'setblock 0 66 -22 minecraft:stone' });
  try {
    await dev.call('dev.t3.menu', { click: 'Garden studio' }); await sleep(250);
    const blocked = await dev.call('dev.state'); assert.match(blocked.screen.class, /\.T3CampusScreen$/);
    assert.equal(blocked.player.x, beforeVisit.x); assert.equal(blocked.player.z, beforeVisit.z);
  } finally { await dev.call('dev.command', { cmd: 'setblock 0 66 -22 minecraft:air' }); }
  for (const [label, x, y, z] of [['Garden studio', .5, 66, -21.5], ['Focus garden', 37.5, 65, 24.5], ['Reception', .5, 66, 24.5]]) {
    await dev.call('dev.screen', { open: 't3-campus' });
    for (let page = 0; page < 5; page++) {
      const menu = await dev.call('dev.t3.menu');
      if (menu.widgets.some(w => w.label === label)) break;
      await dev.call('dev.t3.menu', { click: 'Next' });
    }
    await dev.call('dev.t3.menu', { click: label });
    let state;
    for (let attempt = 0; attempt < 30; attempt++) {
      await sleep(100); state = await dev.call('dev.state');
      if (state.screen === null && Math.abs(state.player.x - x) < .01 && Math.abs(state.player.y - y) < .01 && Math.abs(state.player.z - z) < .01) break;
    }
    assert.equal(state.screen, null, label);
    assert.ok(Math.abs(state.player.x - x) < .01 && Math.abs(state.player.y - y) < .01 && Math.abs(state.player.z - z) < .01, `${label}: ${JSON.stringify(state.player)}`);
  }
  console.log('PASS native menu clicks, chat search, waiting-question typing isolation, 17 menu layouts at GUI scales 2/3/4, H shortcut, obstructed-visit rejection and local HQ visits; fixtures only');
} finally { await dev.call('dev.hud.guiScale', { scale: 3 }); await dev.call('dev.screen', { open: null }); dev.close(); }
