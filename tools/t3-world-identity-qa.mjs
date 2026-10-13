import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { DevClient } from './lib/devclient.mjs';

const dev = await DevClient.connect({ port: Number(process.argv[2] ?? 27931) });
try {
  const state = await dev.call('dev.state');
  assert.equal(state.inWorld, true, 'Renamed HQ fixture must be open');
  assert.equal(state.world.name, 'T3 HQ', 'Fixture reproduces the saved, renamed display name');
  const anchors = await dev.call('dev.anchors');
  assert.equal(anchors.layout, 'modern', 'HQ identity must load its saved campus layout');
  assert.ok(anchors.count >= 100, 'The saved campus anchors must be present');
  const residents = await dev.call('dev.agents');
  assert.ok(residents.count > 0, 'The synthetic fixture must supply visible residents');
  assert.ok(residents.agents.every(agent => !agent.anchor?.includes('@spawn')),
    'Residents must use campus stations instead of the fallback spawn row');
  assert.equal((await dev.call('dev.t3')).connected, true, 'QA pairing must remain the synthetic loopback fixture');
  await dev.call('dev.screenshot', { name: 'renamed-hq-residents', waitChunks: false });
  await mkdir('artifacts/qa/world-identity', { recursive: true });
  await writeFile('artifacts/qa/world-identity/qa.json', JSON.stringify({ state, anchors, residents }, null, 2));
  console.log(`PASS renamed HQ loaded ${anchors.count} anchors; ${residents.count} synthetic residents have campus targets`);
} finally {
  await dev.call('dev.quit');
  dev.close();
}
