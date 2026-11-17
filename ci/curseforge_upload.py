"""Uploads Death Breakdown's tested jars to CurseForge (mod project + plugin project).
Reads CURSEFORGE_TOKEN from the environment (a GitHub secret; never printed).
DRY_RUN=true only resolves every tag and prints what it would upload."""
import json, os, sys, urllib.request, uuid, hashlib

TOKEN = os.environ.get('CURSEFORGE_TOKEN', '')
DRY = os.environ.get('DRY_RUN', 'true') != 'false'
MOD_ID, PLUGIN_ID = 1808919, 1808923
VERSION = '1.0.0'
CHANGELOG = open('CHANGELOG.md', encoding='utf-8').read()
PLUGIN_CHANGELOG = """## 1.0.0
First release.
- When a player dies, a short recap shows in chat: what killed them and every hit from the last 10 seconds (hover for details)
- Click **[Open recap]** for the full recap as a book (it's never added to the inventory)
- For each hit: who or what hit you, the damage type, the damage, and how much armor, enchantments and other things blocked
- `/deathbreakdown` (`/db`) shows your last recap again, `/db reload` reloads the config
- Works with vanilla clients. Paper 1.12.2 to 26.3
- **Requires LogicLib Plugin**
"""

# (file, loaders, minecraft versions, java)
MOD_FILES = [
    ('forge-1.12.2', ['Forge'], ['1.12.2'], 8),
    ('forge-1.16.5', ['Forge'], ['1.16.5'], 8),
    ('forge-1.18.2', ['Forge'], ['1.18.2'], 17),
    ('forge-1.19.2', ['Forge'], ['1.19.2'], 17),
    ('forge-1.20.1', ['Forge', 'NeoForge'], ['1.20.1'], 17),
    ('forge-1.21.1', ['Forge'], ['1.21.1'], 21),
    ('neoforge-1.21.1', ['NeoForge'], ['1.21.1'], 21),
    ('forge-1.21.11', ['Forge'], ['1.21.11'], 21),
    ('neoforge-1.21.11', ['NeoForge'], ['1.21.11'], 21),
    ('forge-26.1.2', ['Forge'], ['26.1.2'], 25),
    ('neoforge-26.1.2', ['NeoForge'], ['26.1.2'], 25),
    ('forge-26.2', ['Forge'], ['26.2'], 25),
    ('neoforge-26.2', ['NeoForge'], ['26.2'], 25),
    ('forge-26.3', ['Forge'], ['26.3'], 25),
    ('neoforge-26.3', ['NeoForge'], ['26.3'], 25),
]
PAPER_VERSIONS = ['1.12.2', '1.16.5', '1.18.2', '1.19.2', '1.20.1', '1.21.1', '1.21.11', '26.1.2', '26.2', '26.3']


def get(url):
    r = urllib.request.Request(url, headers={'X-Api-Token': TOKEN, 'User-Agent': 'deathbreakdown-publish'})
    return json.load(urllib.request.urlopen(r, timeout=60))


def versions(host):
    types = {t['id']: t for t in get(f'https://{host}/api/game/version-types')}
    out = []
    for v in get(f'https://{host}/api/game/versions'):
        t = types.get(v['gameVersionTypeID'], {})
        out.append((v['id'], v['name'], t.get('slug', ''), t.get('name', '')))
    return out


def find(all_versions, name, want_type=None):
    hits = [v for v in all_versions if v[1].lower() == name.lower() and (want_type is None or want_type(v))]
    return hits[0] if hits else None


def is_mc(v):
    return v[2].startswith('minecraft') or v[3].lower().startswith('minecraft') or v[2].startswith('bukkit') or v[3].lower().startswith('bukkit')


def upload(host, project, path, meta):
    boundary = uuid.uuid4().hex
    data = open(path, 'rb').read()
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n\r\n{json.dumps(meta)}\r\n'
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{os.path.basename(path)}"\r\n'
            f'Content-Type: application/java-archive\r\n\r\n').encode() + data + f'\r\n--{boundary}--\r\n'.encode()
    r = urllib.request.Request(f'https://{host}/api/projects/{project}/upload-file', data=body, method='POST',
                               headers={'X-Api-Token': TOKEN, 'User-Agent': 'deathbreakdown-publish',
                                        'Content-Type': f'multipart/form-data; boundary={boundary}'})
    try:
        return json.load(urllib.request.urlopen(r, timeout=300))
    except urllib.error.HTTPError as e:
        raise SystemExit(f'upload failed {e.code}: {e.read().decode(errors="replace")[:500]}')


def tags(all_versions, mcs, loaders, java, problems, label):
    ids, names = [], []
    for mc in mcs:
        v = find(all_versions, mc, is_mc)
        if v: ids.append(v[0]); names.append(f'{v[1]} [{v[3] or v[2]}]')
        else: problems.append(f'{label}: Minecraft {mc} not found')
    for l in loaders:
        v = find(all_versions, l)
        if v: ids.append(v[0]); names.append(v[1])
        else: problems.append(f'{label}: loader {l} not found')
    for env in ['Client', 'Server']:
        v = find(all_versions, env)
        if v: ids.append(v[0]); names.append(v[1])
    if java:
        v = find(all_versions, f'Java {java}')
        if v: ids.append(v[0]); names.append(v[1])
        else: print(f'  note: {label}: "Java {java}" tag does not exist, skipped')
    return ids, names


def main():
    if not TOKEN: raise SystemExit('CURSEFORGE_TOKEN is not set')
    problems, plan = [], []
    mc = versions('minecraft.curseforge.com')
    for name, loaders, mcs, java in MOD_FILES:
        path = f'jars/deathbreakdown-{VERSION}+{name}.jar'
        if not os.path.exists(path): problems.append(f'missing {path}'); continue
        ids, names = tags(mc, mcs, loaders, java, problems, name)
        label = ' + '.join(loaders) + ' ' + ', '.join(mcs)
        meta = {'changelog': CHANGELOG, 'changelogType': 'markdown', 'displayName': f'Death Breakdown {VERSION} ({label})',
                'gameVersions': ids, 'releaseType': 'release'}
        plan.append(('minecraft.curseforge.com', MOD_ID, path, meta, names))

    ppath = 'plugin/deathbreakdown-paper.jar'
    if os.path.exists(ppath):
        host, bv = None, None
        for h in ['bukkit.curseforge.com', 'dev.bukkit.org']:
            try:
                bv = versions(h); host = h; break
            except Exception as e:
                print(f'  {h}: {e}')
        if not host: problems.append('no bukkit API host answered')
        else:
            ids, names = [], []
            for v in PAPER_VERSIONS:
                hit = find(bv, v)
                if not hit:  # Bukkit lists versions like "1.21" or "1.21.1"; fall back to the major line
                    major = '.'.join(v.split('.')[:2])
                    hit = find(bv, major)
                if hit and hit[0] not in ids: ids.append(hit[0]); names.append(hit[1])
                elif not hit: print(f'  note: plugin: no Bukkit tag for {v}')
            if not ids: problems.append('plugin: no Bukkit version tags found')
            meta = {'changelog': PLUGIN_CHANGELOG, 'changelogType': 'markdown',
                    'displayName': f'Death Breakdown Plugin {VERSION} (Paper 1.12.2-26.3)', 'gameVersions': ids,
                    'releaseType': 'release',
                    'relations': {'projects': [{'slug': 'logiclib-plugin', 'type': 'requiredDependency'}]}}
            plan.append((host, PLUGIN_ID, ppath, meta, names))
    else:
        problems.append(f'missing {ppath}')

    for host, project, path, meta, names in plan:
        sha = hashlib.sha256(open(path, 'rb').read()).hexdigest()
        print(f'{"WOULD UPLOAD" if DRY else "UPLOAD"} {os.path.basename(path)} -> {host} project {project}\n'
              f'  name: {meta["displayName"]}\n  tags: {", ".join(names)}\n  sha256 {sha}  {os.path.getsize(path)} bytes'
              + (f'\n  relations: {meta["relations"]}' if 'relations' in meta else ''))
    if problems:
        print('PROBLEMS:\n- ' + '\n- '.join(problems)); sys.exit(1)
    if DRY:
        print(f'DRY RUN OK: {len(plan)} files ready'); return
    for host, project, path, meta, names in plan:
        res = upload(host, project, path, meta)
        print(f'uploaded {os.path.basename(path)}: file id {res.get("id")}')
    print(f'DONE: {len(plan)} files uploaded')


main()
