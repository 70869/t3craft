// Drive the real Minecraft forms against the isolated fixture. Never run on a real pairing.
import assert from 'node:assert/strict';
import { DevClient } from './lib/devclient.mjs';

const port = Number(process.argv[2] ?? 27879);
const fixturePort = Number(process.argv[3] ?? 25680);
assert.ok(fixturePort >= 25680 && fixturePort <= 25688);
const base = `http://127.0.0.1:${fixturePort}`;
const dev = await DevClient.connect({ port, timeoutMs: 30_000 });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function settle() {
  for (let i = 0; i < 100; i++) {
    const form = await dev.request('dev.t3.form');
    if (!form.busy) { assert.ok(form.message === 'Updated from T3' || form.message === 'Creating a new schedule.', form.message); return form; }
    await sleep(100);
  }
  throw new Error('T3 workflow did not settle');
}
async function open(name) { await dev.request('dev.screen', { open: 't3-' + name }); return settle(); }
async function action(action, fields) { await dev.request('dev.t3.form', { action, ...(fields ? { fields } : {}) }); return settle(); }
async function json(path) { const response = await fetch(base + path); assert.ok(response.ok); return response.json(); }
try {
  await json('/_qa/reset'); await sleep(500);
  await dev.call('dev.screen', {open:'t3'}); await dev.call('dev.t3.menu', {focusThread:'A-done-0'}); await sleep(500);
  await open('worktrees');
  assert.match((await action('Create & move chat', { 'New branch': 'feature/fixture-office' })).output, /moved to/);
  await open('options'); await action('Save options', { Reasoning: 'high', Fast: 'On' });
  await open('schedules');
  await action('New schedule');
  await action('Save', { Title: 'Fixture custom weekend', Prompt: 'Fixture only', Schedule: 'Custom days', 'Minutes / HH:MM': '09:00', 'Custom days (0 Sun to 6 Sat)': '0,6' });
  await action('Load selected'); await action('Save');
  const upserts = (await json('/_qa/rpcs')).filter(r => r.method === 'scheduledTasks.upsert');
  assert.deepEqual(upserts.at(-1).payload.schedule.weekdays, [0, 6]);
  assert.equal(upserts.at(-1).payload.requireExisting, true);
  await action('Enable / disable'); await action('Run now'); await action('Delete');
  await open('git'); await action('Commit', { 'Commit message': 'Fixture commit' }); await action('Push'); await action('Create PR');
  const pr = { 'Pull request URL': 'https://github.com/fixture/repo/pull/1', 'Review / PR body': 'Fixture only' };
  await action('Save PR body', pr); await action('Submit review'); await action('Watch PR'); await action('Stop watching'); await action('Merge PR');
  const git = (await json('/_qa/rpcs')).filter(r => r.method === 'git.runStackedAction');
  assert.ok(git.every(r => r.payload.cwd === '/fixture-worktree'), 'Git uses current handoff checkout');
  await json('/_qa/workflow'); await sleep(300);
  await open('queue'); await action('Load message'); await action('Save edit', { Message: 'Edited queue fixture' });
  await action('Move first', { 'Queued message': '2' }); await action('Steer now', { 'Queued message': '1' }); await action('Cancel message'); await action('Resume queue');
  await open('tasks'); await action('Delegate task', { 'New task brief': 'Fixture child work only', 'Model number': '1' });
  await action('Read result'); await action('Cancel task');
  const commands = await json('/_qa/dispatches');
  assert.ok(commands.some(c => c.type === 'delegated_task.request' && !('threadId' in c) && c.parentThreadId === 'A-done-0'));
  assert.ok(commands.some(c => c.type === 'delegated_task.completion-delivery.dispose'));
  await dev.request('dev.screen', { open: 't3-terminal' }); await sleep(300);
  await dev.request('dev.type', { text: 'echo fixture' }); await dev.request('dev.key', { key: 'enter' }); await sleep(200);
  assert.ok((await json('/_qa/rpcs')).some(r => r.method === 'terminal.write' && r.payload.data === 'echo fixture\r'));
  await dev.request('dev.screen', { open: 't3-workspace' }); await settle();
  await dev.request('dev.t3.form', { fields: { Workspace: 'New worktree', 'New branch': 'feature/fixture-launch', 'Base ref': 'HEAD', 'First message': 'Fixture launch only' }, action: 'Create chat' });
  for (let i = 0; i < 100; i++) { if ((await json('/_qa/rpcs')).some(r => r.method === 'orchestration.launchThread')) break; await sleep(100); }
  const launch = (await json('/_qa/rpcs')).findLast(r => r.method === 'orchestration.launchThread');
  assert.equal(launch.payload.workspaceStrategy.type, 'worktree'); assert.equal(launch.payload.initialMessage.text, 'Fixture launch only');
  await dev.request('dev.screen', { open: null });
  const hq = await dev.request('dev.hq.check');
  for (const key of ['unreachable', 'climbing', 'crowded', 'misplaced']) assert.deepEqual(hq[key], [], key);
  assert.equal(hq.light.darkCells, 0);
  console.log('PASS real game forms: handoff, model options, custom schedule preservation, Git/PR writes and links, queue editing/reordering/steer/cancel, child task cancellation, terminal input, workspace launch and current HQ routes; fixture only');
} finally { dev.close(); }
