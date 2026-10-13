// Exact-owner navigation and recovery against disposable T3 fixtures only.
import assert from 'node:assert/strict';
import { DevClient } from './lib/devclient.mjs';
const port = Number(process.argv[2] ?? 27889), fixturePort = Number(process.argv[3] ?? 25686);
assert.ok(fixturePort >= 25680 && fixturePort <= 25688);
const dev = await DevClient.connect({ port, timeoutMs: 30000 });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const menu = () => dev.call('dev.t3.menu');
const click = label => dev.call('dev.t3.menu', { click: label });
async function fixture(path) { const r = await fetch(`http://127.0.0.1:${fixturePort}${path}`); assert.ok(r.ok); return r.json(); }
async function until(read, check) { for (let n = 0; n < 100; n++) { const value = await read(); if (check(value)) return value; await sleep(100); } assert.fail('Recovery did not settle'); }
try {
  await dev.call('dev.screen', { open: 't3' }); await menu(); // refuses real credentials
  await fixture('/_qa/reset'); await sleep(400);
  await dev.call('dev.t3.menu', { focusThread: 'A-done-0' }); await sleep(400);
  for (const scale of [2, 3, 4]) {
    await dev.call('dev.hud.guiScale', { scale });
    for (const screen of ['t3-work', 't3-tools', 't3-campus']) {
      await dev.call('dev.screen', { open: screen }); const m = await menu();
      for (const label of ['Chat', 'Your work', 'Workspace', 'Campus']) assert.ok(m.widgets.some(w => w.label === label));
      const s = await dev.call('dev.state'), width = Math.ceil(s.window.width / s.window.guiScale), height = Math.ceil(s.window.height / s.window.guiScale);
      for (const a of m.widgets) assert.ok(a.x >= 0 && a.y >= 0 && a.x + a.width <= width && a.y + a.height <= height, a.label);
      for (let i = 0; i < m.widgets.length; i++) for (let j = i + 1; j < m.widgets.length; j++) {
        const a = m.widgets[i], b = m.widgets[j]; assert.ok(!(a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y), `${a.label}/${b.label}`);
      }
      await dev.call('dev.screenshot', { name: `primary-${screen.slice(3)}-${scale}`, hideHud: false });
    }
  }
  await dev.call('dev.screen', { open: 't3-workspace' });
  const workspace = await until(() => dev.call('dev.t3.form'), f => !f.busy);
  assert.match(workspace.fields.Project, /Greeting fixture/); assert.match(workspace.fields.Model, /Fixture model/);
  await dev.call('dev.screen', { open: 't3-git' }); await until(() => dev.call('dev.t3.form'), f => !f.busy);
  await dev.call('dev.t3.form', { fields: { 'Commit message': 'Keep this draft' } });
  await click('Connections'); await click('Done'); assert.equal((await menu()).screen, 'T3GitScreen');
  assert.equal((await dev.call('dev.t3.form')).fields['Commit message'], 'Keep this draft');
  await dev.call('dev.screen', { open: 't3-terminal' });
  await until(menu, m => m.terminal?.message.startsWith('T3 terminal'));
  await dev.call('dev.type', { text: 'echo keep-input' }); await fixture('/_qa/terminal-failure');
  await dev.call('dev.key', { key: 'enter' });
  const failed = await until(menu, m => !m.terminal?.sending && m.terminal?.message.includes('Fixture terminal delivery failed'));
  assert.equal(failed.terminal.input, 'echo keep-input');
  await fixture('/_qa/terminal-recover'); await click('Reconnect');
  await until(menu, m => m.terminal?.message.startsWith('T3 terminal'));
  assert.equal((await menu()).terminal.input, 'echo keep-input');
  await dev.call('dev.key', { key: 'enter' }); await until(menu, m => m.terminal?.input === '' && !m.terminal.sending);
  console.log('PASS primary navigation/scales, named project/model selection, form draft across reconnect, failed terminal input retention and acknowledged retry');
} finally { await fixture('/_qa/terminal-recover').catch(() => {}); dev.close(); }
