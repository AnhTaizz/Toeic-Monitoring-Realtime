"""Counts JUnit XML per run label and records MSI provenance for one final pipeline output.

Usage: python -I postprocess.py <outDir> <expectedSourceSha>
Derived from target/integration-final-20261017/control/postprocess.py (RC 0.3.9), Windows paths.
"""
import hashlib, json, shutil, sys, zipfile, xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path(sys.argv[1]); SOURCE = sys.argv[2]
WT = Path(r'E:\Project\Toeic-Monitoring-Realtime\target\integration-final-20261017\worktree')
BASE = OUT.parent

def digest(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def load(p): return json.loads(Path(p).read_text(encoding='utf-8-sig'))
def save(p, x): Path(p).write_text(json.dumps(x, ensure_ascii=False, indent=2), encoding='utf-8')

for label in ['clean', 'package', 'installed', 'installed-attempt']:
    d = OUT / f'{label}-reports'
    if not d.exists(): continue
    modules = {}; skipped = []; failed = []
    for m in ['protocol', 'client', 'server', 'monitoring-spike']:
        v = {k: 0 for k in ['tests', 'failures', 'errors', 'skipped']}; v['suites'] = 0
        for f in (d / m).glob('TEST-*.xml'):
            r = ET.parse(f).getroot(); v['suites'] += 1
            for k in ['tests', 'failures', 'errors', 'skipped']: v[k] += int(r.get(k, '0'))
            for t in r.findall('testcase'):
                name = t.get('classname') + '#' + t.get('name')
                if t.find('skipped') is not None:
                    reason = (t.find('skipped').get('message') or (t.find('skipped').text or '')).strip().splitlines()
                    skipped.append({'test': name, 'reason': reason[0][:160] if reason else ''})
                if t.find('failure') is not None or t.find('error') is not None: failed.append(name)
        v['passed'] = v['tests'] - v['failures'] - v['errors'] - v['skipped']; modules[m] = v
    totals = {k: sum(v[k] for v in modules.values()) for k in ['tests', 'passed', 'failures', 'errors', 'skipped', 'suites']}
    save(OUT / f'{label}-counts.json', {'source': SOURCE, 'label': label, 'totals': totals, 'modules': modules,
                                        'skippedTests': skipped, 'failedTests': failed})
    print(label, totals)

msi_path = OUT / 'msi-path.txt'
if msi_path.exists():
    installer = Path(msi_path.read_text(encoding='utf-8-sig').strip())
    metadata = load(installer.parent / 'build-info.json')
    assert metadata['sourceCommit'] == SOURCE and not metadata['dirty'], 'MSI metadata source/dirty mismatch'
    assert digest(installer) == metadata['installerSha256']
    images = list((WT / 'jpackage-out').glob('*/build/ToeicMonitor'))
    image = max(images, key=lambda p: p.stat().st_mtime)
    jar = image / 'app/client-0.1.0-SNAPSHOT-all.jar'; built = WT / 'client/target/client-0.1.0-SNAPSHOT-all.jar'
    with zipfile.ZipFile(jar) as packaged, zipfile.ZipFile(built) as rebuilt:
        names = {n for n in packaged.namelist() if n.endswith('.class')}
        built_names = {n for n in rebuilt.namelist() if n.endswith('.class')}
        mismatches = [n for n in sorted(names & built_names) if packaged.read(n) != rebuilt.read(n)]
        natives = all(n in packaged.namelist() for n in ['jfxmedia.dll', 'glass.dll', 'javafx/scene/media/Media.class', 'javafx/scene/control/Control.class'])
        forbidden = [n for n in packaged.namelist() if n.endswith('/.env') or 'InstalledClientAgent' in n
                     or (n.startswith('vn/edu/toeic/') and (n.endswith('Smoke.class') or n.endswith('Test.class')))]
    old = Path(r'E:\Project\Toeic-Monitoring-Realtime\target\integration-final-20261017\worktree\jpackage-out')
    result = {'source': metadata['sourceCommit'], 'version': metadata['version'], 'dirty': metadata['dirty'],
              'msiSha256': digest(installer), 'imageJarSha256': digest(jar), 'rebuiltClientJarSha256': digest(built),
              'serverJarSha256': digest(WT / 'server/target/server-0.1.0-SNAPSHOT.jar'), 'classCount': len(names),
              'mismatches': mismatches, 'sameClassSet': names == built_names, 'windowsNatives': natives,
              'forbiddenEntries': forbidden, 'fullDefault': metadata.get('monitoringDefault'), 'jdk': metadata.get('java'),
              'runtimeRelease': (image / 'runtime/release').read_text(encoding='utf-8'),
              'installedRun': load(OUT / 'installed-run.json') if (OUT / 'installed-run.json').exists() else None}
    save(OUT / 'MSI_PROVENANCE.json', result)
    release = BASE / f"release-{metadata['version']}"; release.mkdir(exist_ok=True)
    for f in [installer, installer.parent / 'build-info.json', Path(str(installer) + '.sha256')]:
        dest = release / f.name
        if dest.exists(): assert digest(dest) == digest(f), f'refusing to overwrite {dest.name}'
        else: shutil.copyfile(f, dest)
    (release / 'SHA256SUMS.txt').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in sorted(release.iterdir()) if p.name != 'SHA256SUMS.txt'), encoding='utf-8')
    print('MSI', metadata['version'], digest(installer), 'mismatches', len(mismatches), 'natives', natives, 'forbidden', len(forbidden))
print('pg stopped:', not (OUT / 'pgdata/postmaster.pid').exists())
