// Real Minecraft terminal controls, using disposable protocol-2 fixtures only.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';
const port = Number(process.argv[2] ?? 27929), fixturePort = Number(process.argv[3] ?? 25686);
assert.ok(fixturePort >= 25680 && fixturePort <= 25688);
const dev = await DevClient.connect({ port, timeoutMs: 30000 });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const menu = () => dev.call('dev.t3.menu');
const click = label => dev.call('dev.t3.menu', { click: label });
const evidence = [];
async function fixture(path) { const response = await fetch(`http://127.0.0.1:${fixturePort}${path}`); assert.ok(response.ok); return response.json(); }
async function until(check) { let last; for (let i = 0; i < 150; i++) { last = await menu(); if (check(last)) return last; await sleep(100); } assert.fail('Terminal did not settle: ' + JSON.stringify(last.terminal)); }
async function open() { await dev.call('dev.screen', { open: 't3-terminal' }); return until(m => m.terminal?.message.startsWith('T3 terminal')); }
try {
  for (let i = 0; i < 150; i++) { const state = await dev.call('dev.state'), t3 = await dev.call('dev.t3'); if (state.ready && t3.connected && t3.threads === 26) break; await sleep(200); }
  await dev.call('dev.screen', { open: 't3' }); await menu(); // rejects real pairings
  await fixture('/_qa/reset'); await sleep(500);
  await dev.call('dev.t3.menu', { focusThread: 'A-done-0' }); await sleep(500);
  await open(); await until(m => m.terminal?.focused);
  await dev.call('dev.key', { key: 'a', modifiers: 64 }); await dev.call('dev.key', { key: 'backspace' });
  await dev.call('dev.type', { text: 'echo kept-across-navigation' });
  await dev.call('dev.screen', { open: 't3-tools' }); await open();
  assert.equal((await menu()).terminal.input, 'echo kept-across-navigation', 'unsent terminal input survives leaving and reopening');
  await dev.call('dev.key', { key: 'enter' });
  evidence.push(await until(m => m.terminal?.input === '' && m.terminal.output.includes('fixture-output')));
  await click('Ctrl+C'); evidence.push(await until(m => m.terminal?.output.includes('^C') && m.terminal.focused));
  await dev.call('dev.type', { text: 'echo keep-on-failure' }); await fixture('/_qa/terminal-failure');
  await dev.call('dev.key', { key: 'enter' });
  const failed = await until(m => !m.terminal?.sending && m.terminal?.message.includes('Fixture terminal delivery failed'));
  assert.equal(failed.terminal.input, 'echo keep-on-failure');
  await fixture('/_qa/terminal-recover'); await click('Reconnect');
  const recovered = await until(m => m.terminal?.message.startsWith('T3 terminal') && m.terminal.focused);
  assert.equal(recovered.terminal.input, 'echo keep-on-failure');
  assert.ok(recovered.terminal.output.includes('kept-across-navigation'), 'reconnect keeps shell history');
  await dev.call('dev.key', { key: 'enter' }); await until(m => m.terminal?.input === '' && !m.terminal.sending);
  await dev.call('dev.type', { text: 'exit' }); await dev.call('dev.key', { key: 'enter' });
  await until(m => m.terminal?.message.startsWith('Shell exited') && !m.widgets.find(w => w.label === 'Send').active && m.widgets.some(w => w.label === 'Restart shell' && w.active));
  await click('Restart shell'); await until(m => m.terminal?.message.startsWith('T3 terminal') && m.terminal.running);
  for (const scale of [2, 3, 4]) {
    await dev.call('dev.hud.guiScale', { scale }); const m = await menu(), state = await dev.call('dev.state');
    const width = Math.ceil(state.window.width / state.window.guiScale), height = Math.ceil(state.window.height / state.window.guiScale);
    for (const w of m.widgets) assert.ok(w.x >= 0 && w.y >= 0 && w.x + w.width <= width && w.y + w.height <= height, w.label);
    for (let i = 0; i < m.widgets.length; i++) for (let j = i + 1; j < m.widgets.length; j++) {
      const a = m.widgets[i], b = m.widgets[j]; assert.ok(!(a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y), `${a.label}/${b.label}`);
    }
    assert.ok(!m.widgets.some(w => w.label === 'Restart shell'), 'restart is only offered after shell exit');
    assert.equal(m.widgets.find(w => w.label === 'Reconnect').active, false);
    evidence.push(await dev.call('dev.screenshot', { name: `terminal-${scale}`, hideHud: false }));
  }
  await click('End shell'); await until(m => m.terminal?.message === 'Shell closed');
  await click('Back'); await open(); assert.ok((await menu()).terminal.running, 'explicit reopen starts a shell after close');
  const calls = await fixture('/_qa/rpcs');
  const terminal = calls.filter(r => r.method.startsWith('terminal.'));
  assert.ok(terminal.every(r => r.payload.threadId === 'A-done-0' && r.payload.terminalId === 't3craft'));
  assert.ok(terminal.some(r => r.method === 'terminal.resize'));
  assert.deepEqual(await fixture('/_qa/dispatches'), [], 'terminal QA creates no T3 runs');
  await mkdir('artifacts/release-readiness', { recursive: true });
  await writeFile('artifacts/release-readiness/terminal.json', JSON.stringify({ ok: true, terminal, evidence }, null, 2));
  console.log('PASS terminal output, exact owner, saved input, failure/reconnect, Ctrl+C, exit/restart/close, resizing and scales 2/3/4; fixture only');
} finally { await fixture('/_qa/terminal-recover').catch(() => {}); dev.close(); }
