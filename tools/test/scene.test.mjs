// node --test tools/test   (or: npm test --prefix tools)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { fetchAnchors, resolveCamera, resolveTemplates, templateContext, runScene, loadScene } from '../lib/scene.mjs';

const TOOLS = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

class FakeDev {
  constructor({ anchors = null, anchorsShape = 'nested', screens = ['chat', 'pause'], foremanState = null } = {}) {
    this.anchors = anchors;
    this.anchorsShape = anchorsShape;
    this.screens = screens;
    this.foremanState = foremanState;
    this.calls = [];
  }
  async request(type, payload = {}) {
    this.calls.push({ type, ...payload });
    switch (type) {
      case 'dev.anchors':
        if (!this.anchors) return { ok: false, error: "unknown type 'dev.anchors' (try dev.help)" };
        return this.anchorsShape === 'nested' ? { id: '1', type, ok: true, anchors: this.anchors } : { id: '1', type, ok: true, ...this.anchors };
      case 'dev.screen':
        if (payload.open && !this.screens.includes(payload.open)) return { ok: false, error: `unknown screen '${payload.open}'; known: chat, pause` };
        return { ok: true, screen: payload.open ? { class: 'X' } : null };
      case 'dev.screenshot':
        return { ok: true, path: `/shots/${payload.name}.png`, width: 1920, height: 1080, stats: { meanLuma: 100, stdLuma: 30, darkFraction: 0 } };
      case 'dev.state':
        return { ok: true, foreman: this.foremanState, world: { time: 12000 } };
      default:
        return { ok: true };
    }
  }
  async health() { return { ok: true, stalled: false, msSinceLastFrame: 5 }; }
  types() { return this.calls.map((c) => c.type); }
}

function fakeForeman() {
  const decisions = new Map([
    ['d3', { id: 'd3', kind: 'merge', status: 'open', agentId: 'marlow', repoId: 'sim-demo-showcase', worktree: 'wren-t4', createdAt: 1 }],
    ['d4', { id: 'd4', kind: 'question', status: 'open', agentId: 'marlow', createdAt: 2 }],
  ]);
  const sent = [];
  return {
    sent,
    state: {
      foreman: { backend: 'sim', showcase: true },
      agents: new Map([['juniper', { id: 'juniper', name: 'Juniper' }]]),
      tasks: new Map([['t1', { id: 't1', title: 'x', status: 'done' }]]),
      decisions,
      repos: new Map([['sim-demo-showcase', { id: 'sim-demo-showcase' }]]),
      memory: new Map(),
      goal: { id: 'g1' },
    },
    openDecisions(kind) { return [...decisions.values()].filter((d) => d.status === 'open' && (!kind || d.kind === kind)); },
    async diff(repoId, worktree) {
      sent.push({ type: 'diff.request', repoId, worktree });
      return { repoId, worktree, branch: 'b', stats: { files: 2, additions: 3, deletions: 1 }, files: [{ path: 'a', status: 'modified', additions: 2, deletions: 1 }, { path: 'b', status: 'added', additions: 1, deletions: 0 }] };
    },
    async send(type, payload) { sent.push({ type, ...payload }); return { ok: true, result: {} }; },
  };
}

const quiet = () => {};

test('fetchAnchors: unknown command, nested and top-level shapes', async () => {
  assert.deepEqual((await fetchAnchors(new FakeDev())).available, false);
  const a = { cam_a: { x: 1, y: 2, z: 3, yaw: 0, pitch: 10 }, bad: { x: 'no' } };
  const nested = await fetchAnchors(new FakeDev({ anchors: a }));
  assert.equal(nested.available, true);
  assert.deepEqual(Object.keys(nested.anchors), ['cam_a']);
  assert.deepEqual(nested.invalid, ['bad']);
  const flat = await fetchAnchors(new FakeDev({ anchors: { cam_b: { x: 0, y: 70, z: 0, lookAt: { x: 1, y: 70, z: 1 } } }, anchorsShape: 'flat' }));
  assert.deepEqual(Object.keys(flat.anchors), ['cam_b']);
});

test('resolveCamera: override > mod > scene table > raw camera; skip info when nothing', () => {
  const cam = (x) => ({ x, y: 70, z: 0, yaw: 0, pitch: 0 });
  const s = { anchor: ['a', 'b'], camera: cam(9) };
  assert.equal(resolveCamera(s, { b: cam(2) }, {}, { a: cam(1) }).source, 'override');
  assert.deepEqual(resolveCamera(s, { b: cam(2) }, {}, null), { camera: cam(2), source: 'mod', anchor: 'b', tried: ['a'] });
  assert.equal(resolveCamera(s, {}, { a: cam(3) }).source, 'scene-anchor');
  assert.equal(resolveCamera(s, {}, {}).source, 'fallback-camera');
  assert.deepEqual(resolveCamera({ anchor: 'x' }, {}, {}), { camera: null, missing: ['x'] });
  assert.equal(resolveCamera({ camera: cam(0) }, {}, {}).source, 'camera');
});

test('resolveTemplates keeps types, interpolates, and skips on missing values', () => {
  const ctx = templateContext(fakeForeman());
  assert.deepEqual(resolveTemplates({ r: '{{merge.repoId}}', n: '{{merge.createdAt}}', t: 'x {{merge.id}} y' }, ctx), { r: 'sim-demo-showcase', n: 1, t: 'x d3 y' });
  assert.throws(() => resolveTemplates('{{permission.id}}', ctx, { foreman: {} }), /no open permission decision/);
  assert.throws(() => resolveTemplates('{{merge.id}}', templateContext(null)), /no Foreman is connected/);
});

test('current campus scene skips unknown cameras without inventing fallback views', async () => {
  const dev = new FakeDev();
  const fm = fakeForeman();
  const scene = loadScene(path.join(TOOLS, 'scenes', 'qa.json'));
  const sum = await runScene({ scene, dev, foreman: fm, prefix: 'qa/t/', log: quiet });
  const st = Object.fromEntries(sum.shots.map((r) => [r.name, r.status]));
  assert.equal(sum.shots.length, 10);
  for (const shot of scene.shots) { assert.equal(st[shot.name], 'skipped'); assert.match(sum.shots.find(r => r.name === shot.name).reason, /no camera/); }
  assert.equal(sum.ok, true);
  assert.equal(sum.counts.failed, 0);
  assert.ok(!dev.calls.some(c => c.type === 'dev.screenshot'));
  assert.deepEqual(fm.sent, []);
});

test('qa.json on a full mod: every shot uses its anchor, screens get resolved args and are closed', async () => {
  const names = ['cam_exterior_hero', 'cam_entrance_atrium', 'cam_task_wall', 'cam_desk_kit', 'cam_wide_interior', 'cam_decision_podium', 'cam_console', 'cam_merge_station', 'cam_library', 'cam_night'];
  const anchors = Object.fromEntries(names.map((n, i) => [n, { x: i, y: 70, z: i, yaw: 10 * i, pitch: 5 }]));
  const dev = new FakeDev({ anchors, screens: ['t3', 't3-library'], foremanState: { connected: true } });
  const fm = fakeForeman();
  const sum = await runScene({ scene: loadScene(path.join(TOOLS, 'scenes', 'qa.json')), dev, foreman: fm, log: quiet });
  assert.equal(sum.counts.ok, 10, JSON.stringify(sum.shots.filter((r) => r.status !== 'ok')));
  for (const r of sum.shots) assert.equal(r.camera.source, 'mod', r.name);
  assert.equal(sum.shots.find(r => r.name === 'qa04_agent_desk').camera.anchor, 'cam_desk_kit');
  assert.ok(dev.calls.some(c => c.type === 'dev.t3.review' && c.selectFile === 'src/greeting.ts'));
  assert.deepEqual(dev.calls.filter(c => c.type === 'dev.t3.menu').map(c => c.click), ['Workspace', 'Review changes']);
  assert.ok(!dev.calls.some(c => c.type === 'dev.type'));
  assert.deepEqual(fm.sent, []);
});

test('a dev.state path the mod never reports does not stall the scene', async () => {
  const dev = new FakeDev();
  const t0 = Date.now();
  const sum = await runScene({ scene: { file: 'x', waitFor: { path: 'foreman.connected', equals: true, unreportedMs: 300, timeoutMs: 30000 }, shots: [{ name: 'a', camera: { x: 0, y: 70, z: 0, yaw: 0, pitch: 0 } }] }, dev, log: quiet });
  assert.equal(sum.counts.ok, 1);
  assert.ok(Date.now() - t0 < 5000);
});

test('a failing dev request fails the shot (not skip) and the scene continues', async () => {
  const dev = new FakeDev();
  const orig = dev.request.bind(dev);
  dev.request = async (type, p) => (type === 'dev.camera' && p.x === 1 ? { ok: false, error: "field 'pitch' must be in [-90, 90]" } : orig(type, p));
  const sum = await runScene({ scene: { file: 'x', shots: [{ name: 'bad', camera: { x: 1, y: 70, z: 0, yaw: 0, pitch: 0 } }, { name: 'good', camera: { x: 2, y: 70, z: 0, yaw: 0, pitch: 0 } }] }, dev, log: quiet });
  assert.deepEqual(sum.shots.map((r) => r.status), ['failed', 'ok']);
  assert.equal(sum.ok, false);
});
