// Exercise the real question screen against an isolated, loopback T3 fixture.
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';

const dev = await DevClient.connect({ port: Number(process.argv[2] ?? 27879) });
const base = 'http://127.0.0.1:' + Number(process.argv[3] ?? 25680);
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const evidence = [];
async function json(path) {
  const response = await fetch(base + path); assert.ok(response.ok); return response.json();
}
async function inspect() { return dev.request('dev.t3.question'); }
async function until(check) {
  for (let i = 0; i < 150; i++) {
    const state = await inspect(); if (check(state)) return state;
    await sleep(100);
  }
  throw new Error('Question screen did not settle');
}
async function open(mode) {
  await dev.request('dev.screen', { open: null });
  await json('/_qa/question?thread=A-waiting&mode=' + mode);
  await sleep(1000);
  await dev.request('dev.screen', { open: 't3' });
  await dev.request('dev.t3.menu', { focusThread: 'A-waiting' });
  await sleep(400);
  await dev.request('dev.t3.menu', { click: 'Answer' });
  const state = await until(s => s.canAnswer);
  assert.equal(state.threadId, 'A-waiting'); assert.equal(state.requestId, 'shared-request');
  await sleep(400); return inspect();
}
async function key(key, modifiers) { return dev.request('dev.key', { key, ...(modifiers ? { modifiers } : {}) }); }
async function commands() { return (await json('/_qa/dispatches')).filter(c => c.type === 'runtime-request.respond'); }
async function sent(expected) {
  const state = await until(s => s.sent && !s.busy);
  assert.equal(state.canAnswer, false);
  const command = (await commands()).at(-1);
  assert.equal(command.threadId, 'A-waiting'); assert.equal(command.requestId, 'shared-request');
  assert.deepEqual(command.answers, expected); evidence.push({ state, command });
}
try {
  await dev.request('dev.screen', { open: 't3' }); await dev.request('dev.t3.menu'); // rejects real pairings before fixture mutation
  // Mouse choice uses the same hit testing as a player's click.
  let state = await open('simple');
  await dev.request('dev.screenshot', { name: 'agent-question-polished', hideHud: false });
  const choice = state.choices[1];
  await dev.request('dev.t3.question', { click: { x: choice.x + 40, y: choice.y + 12 } });
  await sent({ format: 'markdown' });

  state = await open('multi'); assert.equal(state.questionCount, 2);
  const before = (await commands()).length;
  await key('1'); await key('2');
  assert.equal((await inspect()).selectedCount, 2);
  assert.equal((await commands()).length, before, 'Multi-choice selection must not send early');
  await dev.request('dev.screenshot', { name: 'agent-question-multi', hideHud: false });
  await key('enter');
  state = await until(s => s.questionIndex === 1); assert.equal(state.textFocused, true);
  assert.equal((await commands()).length, before, 'All questions must be answered together');
  await dev.request('dev.type', { text: 'The design and engineering teams' });
  await sleep(400); await key('enter');
  await sent({ format: ['json', 'markdown'], audience: 'The design and engineering teams' });

  await open('free');
  await dev.request('dev.type', { text: 'A custom export for our team' });
  await json('/_qa/fail-next'); await key('enter');
  state = await until(s => !s.busy && !!s.error);
  assert.equal(state.sent, false); assert.equal(state.canAnswer, true);
  assert.equal(state.answer, 'A custom export for our team');
  evidence.push({ failedSend: state });
  await key('enter'); await sent({ format: 'A custom export for our team' });

  await dev.request('dev.hud.guiScale', { scale: 4 });
  state = await open('long'); assert.equal(state.choices.length, 12);
  assert.ok(state.scrollMax > 0); assert.ok(state.y + state.height <= 270);
  assert.ok(state.actionY + 20 <= state.y + state.height);
  await dev.request('dev.screenshot', { name: 'agent-question-compact', hideHud: false });
  await dev.request('dev.t3.question', { scroll: -100 }); await sleep(200);
  state = await inspect(); assert.equal(state.scroll, state.scrollMax);
  const last = state.choices.at(-1);
  assert.ok(last.y >= state.bodyY && last.y + last.height <= state.bodyY + state.bodyHeight);
  await dev.request('dev.t3.question', { click: { x: last.x + 40, y: last.y + 10 } });
  await sent({ format: '11' });

  await dev.request('dev.hud.guiScale', { scale: 3 });
  await open('simple');
  const count = (await commands()).length;
  await json('/_qa/question?thread=A-waiting&mode=withdraw');
  state = await until(s => !s.canAnswer);
  await key('1'); assert.equal((await commands()).length, count, 'Withdrawn requests cannot receive answers');
  evidence.push({ withdrawn: state });
  await writeFile('artifacts/qa/t3-questions.json', JSON.stringify(evidence, null, 2));
  console.log('PASS real question dialog: exact chat request ownership, mouse choices, multi-select, multi-question free text, failed-send retry, compact scrolling and withdrawn requests; isolated fixture only');
} finally {
  await dev.request('dev.hud.guiScale', { scale: 3 });
  await dev.request('dev.screen', { open: null });
  dev.close();
}
