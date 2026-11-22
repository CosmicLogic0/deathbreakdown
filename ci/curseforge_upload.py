"""Uploads Death Breakdown's tested jars to CurseForge (mod project + plugin project).
Reads CURSEFORGE_TOKEN from the environment (a GitHub secret; never printed).
DRY_RUN=true only resolves every tag and prints what it would upload."""
import json, os, sys, urllib.request, uuid, hashlib

TOKEN = os.environ.get('CURSEFORGE_TOKEN', '')
DRY = os.environ.get('DRY_RUN', 'true') != 'false'
MOD_ID, PLUGIN_ID = 1808919, 1808923
VERSION = '1.1.0'
_full = open('CHANGELOG.md', encoding='utf-8').read()
# Only this version's section (from '## <VERSION>' to the next '## ').
_start = _full.find('## ' + VERSION)
CHANGELOG = _full[_start:_full.find('\n## ', _start + 3)].strip() + '\n' if _start >= 0 else _full
PLUGIN_CHANGELOG = """## 1.1.0
### New
- **Death history:** each player's last 5 deaths are kept, even after leaving or a restart. `/db history` lists them; click one to open it.
- **Tip line** in the hover and the book, like "Lava did 80% of the damage."
- **[copy]** in chat and `/db copy`: the recap as text for Discord or a bug report (Minecraft 1.15+; older servers print the text).
- `/db help`, `/db show <number>`, and for staff `/db player <name>` (permission `deathbreakdown.others`).

### Improved
- The [details] hover shows the newest 6 hits, so it always fits on the screen.
- `/db reload` and the console say exactly what was wrong in config.yml.
- A config.yml with a typo that breaks the file is kept as `config.yml.broken-<date>` instead of being replaced with the defaults (LogicLib Plugin 0.2.0).
- **Now requires LogicLib Plugin 0.2.0 or newer.** With an older one the plugin doesn't start and the console says what to get.

### Fixed
- A value that isn't a number (like `window-seconds: banana`) made the time window 1 second. It now falls back to the default.

**Requires LogicLib Plugin 0.2.0 or newer.**
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
        out.append((v['id'], v['name'], t.get('slug', ''), t.get('name', '') or f"type {v['gameVersionTypeID']}"))
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
    for name, loaders, mcs, java in ([] if os.environ.get('ONLY') == 'plugin' else MOD_FILES):
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
        for h in ['dev.bukkit.org', 'bukkit.curseforge.com']:
            try:
                got = versions(h)
                print(f'  {h}: {len(got)} versions')
                if len(got) > 10: bv = got; host = h; break
            except Exception as e:
                print(f'  {h}: {e}')
        if not host: problems.append('no bukkit API host answered')
        else:
            ids, names = [], []
            for v in PAPER_VERSIONS:
                bk = lambda x: x[3] == 'Bukkit'  # only the current "Bukkit" tag type is accepted for uploads
                hit = find(bv, v, bk)
                if not hit:  # older lines may only be listed as "1.12" etc.
                    major = '.'.join(v.split('.')[:2])
                    hit = find(bv, major, bk)
                if hit and hit[0] not in ids: ids.append(hit[0]); names.append(f'{hit[1]} [{hit[3] or hit[2]}]')
                elif not hit: print(f'  note: plugin: no Bukkit tag for {v}')
            if not ids:
                problems.append('plugin: no Bukkit version tags found')
                print(f'  {host} has {len(bv)} versions, e.g.: ' + '; '.join(f'{v[1]} [{v[3] or v[2]}]' for v in bv[-60:]))
            bk_names = sorted({x[1] for x in bv if x[3] == 'Bukkit' and (x[1].startswith('1.12') or x[1].startswith('1.16'))})
            print('  Bukkit-type tags for 1.12/1.16: ' + ', '.join(bk_names))
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
