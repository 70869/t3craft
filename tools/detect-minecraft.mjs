// Read launcher metadata only. Never reads account files, tokens, worlds or server lists.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const args = process.argv.slice(2);
const extra = [];
let output;
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--root') extra.push(args[++i]);
  else if (args[i] === '--output') output = args[++i];
  else throw new Error('Usage: node tools/detect-minecraft.mjs [--root <launcher-data>] [--output <json>]');
}
const appData = process.env.APPDATA ?? path.join(os.homedir(), 'AppData', 'Roaming');
const localData = process.env.LOCALAPPDATA ?? path.join(os.homedir(), 'AppData', 'Local');
const roots = [...new Set([
  path.join(appData, 'PrismLauncher'), path.join(localData, 'PrismLauncher'),
  path.join(appData, 'MultiMC'), path.join(appData, '.minecraft'),
  path.join(appData, 'ModrinthApp'), path.join(appData, 'com.modrinth.theseus'),
  path.join(os.homedir(), 'curseforge', 'minecraft'),
  path.join(os.homedir(), 'Documents', 'Curse', 'Minecraft'), ...extra,
].map((r) => path.resolve(r)))];
const json = (file) => { try { return JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '')); } catch { return undefined; } };
const ini = (file) => {
  try { return Object.fromEntries(fs.readFileSync(file, 'utf8').split(/\r?\n/).filter((l) => l.includes('=')).map((l) => { const i = l.indexOf('='); return [l.slice(0, i), l.slice(i + 1)]; })); } catch { return {}; }
};
const dirs = (root) => { try { return fs.readdirSync(root, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => path.join(root, e.name)); } catch { return []; } };
const modCount = (gameDir) => { try { return fs.readdirSync(path.join(gameDir, 'mods')).filter((f) => /\.jar$/i.test(f)).length; } catch { return 0; } };
const supportedFabric = (v) => {
  if (!/^\d+\.\d+\.\d+$/.test(v ?? '')) return false;
  const actual = v.split('.').map(Number), minimum = [0, 19, 5];
  for (let i = 0; i < 3; i++) { if (actual[i] > minimum[i]) return true; if (actual[i] < minimum[i]) return false; }
  return true;
};
const installations = [];
for (const root of roots.filter((r) => fs.existsSync(r))) {
  const cfg = ini(path.join(root, 'prismlauncher.cfg'));
  const instanceRoot = path.resolve(root, cfg.InstanceDir || 'instances');
  const instances = [];
  for (const instance of dirs(instanceRoot)) {
    const pack = json(path.join(instance, 'mmc-pack.json'));
    if (!pack?.components) continue;
    const settings = ini(path.join(instance, 'instance.cfg'));
    const mc = pack.components.find((c) => c.uid === 'net.minecraft')?.version;
    const loader = pack.components.find((c) => ['net.fabricmc.fabric-loader', 'net.neoforged', 'net.minecraftforge'].includes(c.uid));
    const gameDir = ['.minecraft', 'minecraft'].map((name) => path.join(instance, name)).find(fs.existsSync) ?? path.join(instance, '.minecraft');
    instances.push({ name: settings.name ?? path.basename(instance), path: instance, gameDir, minecraft: mc, loader: loader?.uid, loaderVersion: loader?.version, java: settings.JavaPath || cfg.JavaPath || null, mods: modCount(gameDir), compatible: mc === '26.3' && loader?.uid === 'net.fabricmc.fabric-loader' && supportedFabric(loader?.version) });
  }
  // Vanilla launcher profiles can point to additional game directories.
  const profiles = json(path.join(root, 'launcher_profiles.json'))?.profiles;
  const vanillaProfiles = Object.entries(profiles ?? {}).map(([id, p]) => ({ id, name: p.name || p.type, minecraft: p.lastVersionId, gameDir: p.gameDir ?? root }));
  const versions = dirs(path.join(root, 'versions')).map((dir) => path.basename(dir));
  installations.push({ root, instances, vanillaProfiles, versions });
}
const report = { detectedAt: new Date().toISOString(), required: { minecraft: '26.3', loader: 'Fabric 0.19.5+', java: '25+' }, installations, scope: 'Standard Windows launcher data directories plus --root paths; portable installations elsewhere require --root.' };
if (output) { fs.mkdirSync(path.dirname(path.resolve(output)), { recursive: true }); fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n'); }
console.log(JSON.stringify(report, null, 2));
