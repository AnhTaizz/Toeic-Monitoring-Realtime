"""Verify retained artifact bytes; does not rerun live tests or decide the gate."""
from pathlib import Path
import hashlib
import json
import zipfile

root = Path(__file__).resolve().parent
for line in (root / 'SHA256SUMS.txt').read_text(encoding='utf-8').splitlines():
    digest, name = line.split('  ', 1)
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == digest, name
for archive in json.loads((root / 'archive-manifest.json').read_text(encoding='utf-8')):
    path = root / archive['archive']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == archive['sha256'], path.name
    with zipfile.ZipFile(path) as saved:
        assert len(saved.namelist()) == len(archive['files']), path.name
        for item in archive['files']:
            data = saved.read(item['path'])
            assert len(data) == item['bytes'], item['path']
            assert hashlib.sha256(data).hexdigest() == item['sha256'], item['path']
print('PASS retained artifact/archive member integrity; gate/LAN/human review not asserted')
