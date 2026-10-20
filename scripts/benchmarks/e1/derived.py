"""Explicit, immutable E1 rejoin artifacts. Hash binding is not an algorithm oracle."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess

FILES = ('ground-truth.jsonl', 'observations.jsonl', 'metadata.json', 'join.json',
         'manifest.json', 'resources.csv', 'e1-manifest.json')
ENTRY = 'vn.edu.toeic.client.monitoring.harness.HarnessCli'
SCHEMA = 'e1-derived-join-v1'
REPRODUCE = ['<java>', '-cp', '<join-tool.jar>', ENTRY, 'join',
             '--input-dir', '<original-run>', '--output', '<new-run>/join.json']
ROW_FIELDS = ('trialId', 'targetMillis', 'pid', 'startInstant', 'groundTruthStatus',
              'conclusion', 'reason', 'pidObserved', 'firstScanRecord',
              'firstScanBeginElapsedNanos', 'firstScanEndElapsedNanos', 'metadataQuality',
              'observedLifetimeNanos', 'observedHoldNanos')
DIFF_FIELDS = tuple(k for k in ROW_FIELDS if k not in ('trialId', 'pid', 'startInstant'))
REASONS = {
    'OBSERVED': {'PID_START_COLLECTOR_AND_SCAN_WINDOW_MATCH'},
    'NOT_OBSERVED_IN_TRACE': {'NO_IDENTITY_MATCH_IN_COMPLETE_BRACKETED_TRACE'},
    'INCONCLUSIVE': {'TRIAL_NOT_NORMAL_EXIT', 'PID_ONLY_MISSING_START_INCONCLUSIVE',
                     'PID_START_CONFLICT_INCONCLUSIVE', 'GROUND_TRUTH_START_MISSING',
                     'TRACE_DOES_NOT_BRACKET_TRIAL', 'SOURCE_FAILURE_CANNOT_ASSERT_MISSING'},
}


def digest(path):
    with pathlib.Path(path).open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def read_json(path):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('DUPLICATE_JSON_KEY')
            result[key] = value
        return result
    path = pathlib.Path(path)
    if path.stat().st_size > 1024 * 1024:
        raise ValueError('JSON_SIZE_LIMIT')
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=pairs,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError('NONFINITE_JSON')))


def child(root, name):
    root = pathlib.Path(root)
    path = root / name
    if (not re.fullmatch(r'[A-Za-z0-9_.-]+', name) or name in ('.', '..')
            or path.is_symlink() or not path.resolve().is_relative_to(root.resolve())):
        raise ValueError('ARTIFACT_PATH')
    if not path.exists():
        raise ValueError('ARTIFACT_MISSING')
    return path


def inputs(directory):
    return {name: digest(child(directory, name)) for name in FILES}


def identity(directory, metadata):
    return dict(directory=directory.name, **{key: metadata[key] for key in
                ('runId', 'clockDomain', 'collectorSessionId', 'traceStartElapsedNanos')})


def source_version(path, revision='HEAD'):
    """Only assert Git source if the bytes match a tracked file at HEAD (including CRLF checkout)."""
    path = pathlib.Path(path).resolve()
    try:
        root = pathlib.Path(subprocess.check_output(
            ['git', '-C', str(path.parent), 'rev-parse', '--show-toplevel'], stderr=subprocess.DEVNULL,
            timeout=5).decode().strip())
        relative = path.relative_to(root).as_posix()
        tracked = subprocess.check_output(['git', '-C', str(root), 'show', revision + ':' + relative],
                                         stderr=subprocess.DEVNULL, timeout=5)
        if tracked.replace(b'\r\n', b'\n') != path.read_bytes().replace(b'\r\n', b'\n'):
            return None
        return subprocess.check_output(['git', '-C', str(root), 'rev-parse', revision],
                                       stderr=subprocess.DEVNULL, timeout=5).decode().strip()
    except (OSError, ValueError, subprocess.SubprocessError):
        return None


def validate_join(join, metadata, truth, directory):
    fields = {'schemaVersion', 'runId', 'status', 'clockDomain', 'policyVersion',
              'traceChecksum', 'latency', 'rows', 'observed', 'notObserved', 'inconclusive'}
    if (not isinstance(join, dict) or set(join) != fields
            or join['schemaVersion'] != 'controlled-observation-join-v1'
            or join['status'] != 'COMPLETE' or join['policyVersion'] != 'test-owned-process-v1'
            or join['latency'] != 'NOT_MEASURED; scan bounds and parent-observed lifetime/hold only'):
        raise ValueError('DERIVED_JOIN_SCHEMA')
    if any(join[key] != metadata[key] for key in ('runId', 'clockDomain')):
        raise ValueError('DERIVED_RUN_CLOCK')
    # Joiner uses the trace's internal checksum, not the hash of its FINAL record.
    trace_lines = child(directory, 'observations.jsonl').read_bytes().splitlines(keepends=True)
    footer = json.loads(trace_lines[-1])
    if (footer.get('kind') != 'FINAL' or footer.get('status') != 'COMPLETE'
            or footer.get('sha256') != hashlib.sha256(b''.join(trace_lines[:-1])).hexdigest()
            or join['traceChecksum'] != footer['sha256']):
        raise ValueError('DERIVED_TRACE_CHECKSUM')
    rows = join['rows']
    trials = {trial['planned']['trialId']: trial for trial in truth[1:-1]}
    if (not isinstance(rows, list) or not 1 <= len(rows) <= 90
            or any(not isinstance(row, dict) or set(row) != set(ROW_FIELDS) for row in rows)
            or len(rows) != len(trials) or len({row['trialId'] for row in rows}) != len(rows)
            or {row['trialId'] for row in rows} != set(trials)):
        raise ValueError('DERIVED_TRIAL_SET')
    for row in rows:
        trial = trials[row['trialId']]
        for key in ('pid', 'targetMillis', 'observedLifetimeNanos', 'observedHoldNanos'):
            if row[key] is not None and (type(row[key]) is not int or not 0 <= row[key] <= 2**63-1):
                raise ValueError('DERIVED_ROW_NUMBER')
        for key in ('pid', 'startInstant', 'observedLifetimeNanos', 'observedHoldNanos'):
            if row[key] != trial[key]:
                raise ValueError('DERIVED_TRIAL_BINDING')
        if (row['targetMillis'] != trial['planned']['targetMillis']
                or row['groundTruthStatus'] != trial['status']
                or not re.fullmatch(r'trial-\d{3}', row['trialId'])
                or row['conclusion'] not in REASONS
                or row['reason'] not in REASONS[row['conclusion']]
                or row['metadataQuality'] not in ('COMPLETE', 'UNREADABLE', 'NOT_CONFIRMED')
                or type(row['pidObserved']) is not bool):
            raise ValueError('DERIVED_ROW_SCHEMA')
        scan = [row[k] for k in ('firstScanRecord', 'firstScanBeginElapsedNanos', 'firstScanEndElapsedNanos')]
        if row['conclusion'] == 'OBSERVED':
            if (not row['pidObserved'] or row['startInstant'] is None
                    or row['metadataQuality'] == 'NOT_CONFIRMED'
                    or any(type(n) is not int or n < 0 for n in scan) or scan[0] < 1
                    or scan[1] > scan[2] or scan[1] > trial['exitObservedElapsedNanos']
                    or scan[2] < trial['registeredElapsedNanos']):
                raise ValueError('DERIVED_SCAN_BOUNDS')
        elif any(n is not None for n in scan) or row['metadataQuality'] != 'NOT_CONFIRMED':
            raise ValueError('DERIVED_UNOBSERVED_SCAN')
        if row['conclusion'] == 'NOT_OBSERVED_IN_TRACE' and (row['pidObserved'] or row['startInstant'] is None):
            raise ValueError('DERIVED_MISSING_IDENTITY')
    for key, conclusion in (('observed', 'OBSERVED'), ('notObserved', 'NOT_OBSERVED_IN_TRACE'), ('inconclusive', 'INCONCLUSIVE')):
        if type(join[key]) is not int or join[key] != sum(r['conclusion'] == conclusion for r in rows):
            raise ValueError('DERIVED_COUNTS')


def provenance(directory, joined, join_tool):
    metadata = read_json(child(directory, 'metadata.json'))
    analyzer = pathlib.Path(__file__).with_name('analyze.py')
    version = source_version(__file__)
    if version != source_version(analyzer):
        version = None
    return dict(schemaVersion=SCHEMA, run=identity(directory, metadata),
                rawSourceSha=metadata.get('sourceSha'), inputs=inputs(directory),
                join=dict(file='join.json', sha256=digest(joined)),
                tool=dict(entryPoint=ENTRY, jarSha256=digest(join_tool), sourceSha=None,
                          version='sha256:' + digest(join_tool)),
                creator=dict(scriptSha256=digest(__file__), analyzerSha256=digest(analyzer), sourceSha=version),
                reproduce=REPRODUCE)


def load_derived(directory, root, join_tool, provenance_script=None):
    folder = child(root, directory.name)
    if not folder.is_dir() or {p.name for p in folder.iterdir()} != {'join.json', 'provenance.json'}:
        raise ValueError('DERIVED_FILE_SET')
    prov = read_json(child(folder, 'provenance.json'))
    metadata = read_json(child(directory, 'metadata.json'))
    if (set(prov) != {'schemaVersion', 'run', 'rawSourceSha', 'inputs', 'join', 'tool', 'creator', 'reproduce'}
            or prov['schemaVersion'] != SCHEMA):
        raise ValueError('PROVENANCE_SCHEMA')
    if prov['run'] != identity(directory, metadata) or prov['rawSourceSha'] != metadata.get('sourceSha'):
        raise ValueError('PROVENANCE_RUN_CLOCK')
    if prov['inputs'] != inputs(directory):
        raise ValueError('PROVENANCE_INPUTS')
    joined = child(folder, 'join.json')
    if prov['join'] != dict(file='join.json', sha256=digest(joined)):
        raise ValueError('DERIVED_JOIN_HASH')
    tool = prov['tool']
    if tool != dict(entryPoint=ENTRY, jarSha256=digest(join_tool), sourceSha=None, version='sha256:' + digest(join_tool)):
        raise ValueError('PROVENANCE_TOOL')
    creator = prov['creator']
    script = pathlib.Path(provenance_script or __file__)
    analyzer = script.with_name('analyze.py')
    if (set(creator) != {'scriptSha256', 'analyzerSha256', 'sourceSha'}
            or creator['scriptSha256'] != digest(provenance_script or __file__)
            or creator['analyzerSha256'] != digest(analyzer)
            or creator['sourceSha'] is not None and (not re.fullmatch(r'[a-f0-9]{40}', creator['sourceSha'])
                or source_version(script, creator['sourceSha']) != creator['sourceSha']
                or source_version(analyzer, creator['sourceSha']) != creator['sourceSha'])
            or prov['reproduce'] != REPRODUCE):
        raise ValueError('PROVENANCE_CREATOR')
    join = read_json(joined)
    truth = [json.loads(line) for line in child(directory, 'ground-truth.jsonl').read_text(encoding='utf-8').splitlines()]
    validate_join(join, metadata, truth, directory)
    return join


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('suite', type=pathlib.Path)
    parser.add_argument('output', type=pathlib.Path)
    parser.add_argument('--join-tool', type=pathlib.Path, required=True)
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    from analyze import analyze
    if args.output.resolve().is_relative_to(args.suite.resolve()):
        raise ValueError('OUTPUT_INSIDE_RAW')
    runs = sorted(args.suite.glob('run-*'))
    if not runs:
        raise ValueError('NO_RUNS')
    # Verify the whole suite before creating output or launching any join process.
    before = {}
    for run in runs:
        analyze(run)
        before[run.name] = inputs(run)
    tool_hash = digest(args.join_tool)
    args.output.mkdir(parents=True, exist_ok=False)
    for run in runs:
        folder = args.output / run.name
        folder.mkdir()
        joined = folder / 'join.json'
        result = subprocess.run([args.java, '-cp', str(args.join_tool.resolve()), ENTRY, 'join',
                                 '--input-dir', str(run.resolve()), '--output', str(joined.resolve())],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)
        if result.returncode or inputs(run) != before[run.name] or digest(args.join_tool) != tool_hash:
            raise ValueError('REJOIN_FAILED_OR_INPUT_CHANGED')
        prov = provenance(run, joined, args.join_tool)
        (folder / 'provenance.json').write_text(json.dumps(prov, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        load_derived(run, args.output, args.join_tool)
    if any(inputs(run) != before[run.name] for run in runs):
        raise ValueError('RAW_CHANGED_DURING_REJOIN')


if __name__ == '__main__':
    main()
