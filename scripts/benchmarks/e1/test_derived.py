"""MOCK artifacts with hand-computed expectations; no E1 measurements collected."""
import copy
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from analyze import analyze
from derived import inputs, provenance, digest
from review_fixture import seal


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def mock_run(root):
    root.mkdir(parents=True)
    config = dict(pollMillis=500, seed=1234, perDuration=2, maxConcurrent=2,
                  readyTimeoutMillis=3000, exitSlackMillis=1500, maxRunMillis=180000, sourceSha='MOCK')
    header = dict(kind='HEADER', schemaVersion='controlled-ground-truth-v1', runId='MOCK-join-run',
                  sourceLabel='MOCK', clockDomain='parent-jvm:MOCK-join-run',
                  collectorSessionId='MOCK-collector', traceStartElapsedNanos=0, config=config)
    meta = dict(header, status='COMPLETE', sourceSha='MOCK', e1e2='MOCK',
                measurementStartElapsedNanos=0, measurementEndElapsedNanos=10_000_000_000,
                resourceSamplePeriodMillis=5000)
    meta.pop('kind')
    write_json(root/'metadata.json', meta)
    trials = []
    rows = []
    for i, (start, target, conclusion) in enumerate([
            (1, 200, 'OBSERVED'), (2, 200, 'INCONCLUSIVE'), (3, 800, 'OBSERVED'),
            (5, 800, 'NOT_OBSERVED_IN_TRACE'), (6, 3000, 'INCONCLUSIVE'), (9, 3000, 'NOT_OBSERVED_IN_TRACE')], 1):
        begin = start*1_000_000_000
        end = begin + (100+target)*1_000_000
        instant = None if i == 5 else '2026-10-20T00:00:00Z'
        trial = dict(kind='TRIAL', planned=dict(trialId=f'trial-{i:03}', targetMillis=target,
                      phaseMillis=0, plannedLaunchElapsedNanos=begin), queuedElapsedNanos=begin,
                     launchBeginElapsedNanos=begin, startReturnedElapsedNanos=begin+10_000_000,
                     registeredElapsedNanos=begin+20_000_000, readyReceivedElapsedNanos=begin+30_000_000,
                     goSentElapsedNanos=begin+100_000_000, exitObservedElapsedNanos=end,
                     pid=100+i, processName='java.exe', startInstant=instant,
                     metadataQuality='UNREADABLE' if instant is None else 'COMPLETE', status='EXITED',
                     exitCode=0, forcedCleanup=False, observedLifetimeNanos=end-begin-10_000_000,
                     observedHoldNanos=target*1_000_000)
        trials.append(trial)
        observed = conclusion == 'OBSERVED'
        rows.append(dict(trialId=trial['planned']['trialId'], targetMillis=target, pid=trial['pid'],
                         startInstant=instant, groundTruthStatus='EXITED', conclusion=conclusion,
                         reason='PID_START_COLLECTOR_AND_SCAN_WINDOW_MATCH' if observed else
                         'NO_IDENTITY_MATCH_IN_COMPLETE_BRACKETED_TRACE' if conclusion == 'NOT_OBSERVED_IN_TRACE' else
                         'GROUND_TRUTH_START_MISSING' if i == 5 else 'SOURCE_FAILURE_CANNOT_ASSERT_MISSING',
                         pidObserved=observed, firstScanRecord=3 if i == 1 else 7 if observed else None,
                         firstScanBeginElapsedNanos=begin+(99_000_000 if i == 1 else 299_000_000) if observed else None,
                         firstScanEndElapsedNanos=begin+(100_000_000 if i == 1 else 300_000_000) if observed else None,
                         metadataQuality='COMPLETE' if observed else 'NOT_CONFIRMED',
                         observedLifetimeNanos=trial['observedLifetimeNanos'], observedHoldNanos=trial['observedHoldNanos']))
    def jsonl(path, body, footer):
        data=''.join(json.dumps(row)+'\n' for row in body).encode()
        footer=dict(footer, sha256=hashlib.sha256(data).hexdigest())
        path.write_bytes(data+(json.dumps(footer)+'\n').encode())
        return footer['sha256']
    jsonl(root/'ground-truth.jsonl', [header]+trials, dict(kind='FINAL', status='COMPLETE', count=6))
    trace=[dict(kind='HEADER', schemaVersion='monitoring-observation-trace-test-v1', sourceLabel='MOCK',
                pollMillis=500, policyVersion='test-owned-process-v1', wallOrigin='2026-10-20T00:00:00Z')]
    for i,(tick,kind,pid) in enumerate([(0,'START',None),(.5,'OBSERVATION',None),(1.1,'OBSERVATION',101),
            (1.6,'SOURCE_FAILURE',None),(1.9,'OBSERVATION',None),(2.4,'OBSERVATION',None),
            (3.3,'OBSERVATION',103),(4.8,'OBSERVATION',None),(5.95,'OBSERVATION',None),
            (9.2,'OBSERVATION',None),(12.3,'OBSERVATION',None),(12.4,'STOP',None)],1):
        trace.append(dict(kind='RECORD', index=i, type=kind, elapsedNanos=round(tick*1e9),
                          collectorSessionId='MOCK-collector', policyVersion='test-owned-process-v1',
                          scanDurationNanos=1_000_000 if kind=='OBSERVATION' else 0,
                          processes=[] if pid is None else [dict(pid=pid, processName='java.exe',
                              startInstant='2026-10-20T00:00:00Z', metadataQuality='COMPLETE')]))
    checksum=jsonl(root/'observations.jsonl', trace, dict(kind='FINAL', status='COMPLETE', attempted=12, written=12, dropped=0))
    join=dict(schemaVersion='controlled-observation-join-v1', runId=header['runId'], status='COMPLETE',
              clockDomain=header['clockDomain'], policyVersion='test-owned-process-v1', traceChecksum=checksum,
              latency='NOT_MEASURED; scan bounds and parent-observed lifetime/hold only', rows=rows,
              observed=2, notObserved=2, inconclusive=2)
    write_json(root/'join.json',join)
    (root/'resources.csv').write_text('elapsedNanos,collectorThreadCpuNanos,parentProcessCpuNanos,parentHeapUsedBytes\n'
        '0,0,0,100\n1000000000,1000000000,2000000000,200\n10000000000,1000000000,2000000000,300\n',encoding='utf-8')
    seal(root)
    return join


class DerivedTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=pathlib.Path(self.tmp.name)
        self.run=self.root/'suite'/'run-MOCK'
        self.old=mock_run(self.run)
        self.tool=self.root/'MOCK-tool.jar';self.tool.write_bytes(b'MOCK artifact hash fixture, not runnable Java')
        self.derived=self.root/'derived';self.folder=self.derived/self.run.name;self.folder.mkdir(parents=True)
        self.save(self.old)

    def save(self, join):
        write_json(self.folder/'join.json',join)
        write_json(self.folder/'provenance.json',provenance(self.run,self.folder/'join.json',self.tool))

    def changed(self):
        join=copy.deepcopy(self.old)
        join['rows'][1].update(conclusion='NOT_OBSERVED_IN_TRACE',reason='NO_IDENTITY_MATCH_IN_COMPLETE_BRACKETED_TRACE')
        join['notObserved']=3;join['inconclusive']=1
        return join

    def cli(self, out, *extra):
        return subprocess.run([sys.executable,str(pathlib.Path(__file__).with_name('analyze.py')),
            str(self.run.parent),str(out),*map(str,extra)],capture_output=True)

    def test_default_and_identical_derived_metrics_are_equal(self):
        original=analyze(self.run)
        self.assertEqual(original,analyze(self.run,self.derived,self.tool))
        self.assertEqual([original[1][0][key] for key in ('eligible','detected','missed','inconclusive')],[2,1,0,1])
        self.assertEqual(original[1][0]['collectorCpuOneCoreMean'],10)
        self.assertEqual(original[1][0]['parentCpuOneCoreMean'],20)

    def test_new_join_changes_only_expected_counts_and_bounds(self):
        before=inputs(self.run);self.save(self.changed())
        events,summary=analyze(self.run,self.derived,self.tool)
        row=summary[0]
        self.assertEqual([row[k] for k in ('eligible','detected','missed','inconclusive','censored')],[2,1,1,0,0])
        self.assertEqual((row['missRate'],row['missRateLowerBound'],row['missRateUpperBound']),(.5,.5,.5))
        self.assertEqual((row['p50ParentLatencyMs'],row['p95ParentLatencyMs']),(100,100))
        self.assertEqual(row['collectorCpuOneCoreMean'],10)
        self.assertEqual(events[4]['outcome'],'INCONCLUSIVE')
        self.assertEqual(events[5]['outcome'],'CENSORED')
        self.assertEqual(inputs(self.run),before)

    def test_raw_original_join_and_both_manifests_cannot_be_bypassed(self):
        for name in ('observations.jsonl','ground-truth.jsonl','metadata.json','join.json','manifest.json','resources.csv','e1-manifest.json'):
            with self.subTest(name=name):
                path=self.run/name;data=path.read_bytes();path.write_bytes(data+b' ')
                with self.assertRaises((ValueError,KeyError)):analyze(self.run,self.derived,self.tool)
                path.write_bytes(data)

    def test_join_hash_and_missing_wrong_provenance_rejected(self):
        path=self.folder/'join.json';path.write_bytes(path.read_bytes()+b' ')
        with self.assertRaisesRegex(ValueError,'DERIVED_JOIN_HASH'):analyze(self.run,self.derived,self.tool)
        self.save(self.old)
        path=self.folder/'provenance.json';path.unlink()
        with self.assertRaises(ValueError):analyze(self.run,self.derived,self.tool)
        self.save(self.old);p=json.loads(path.read_text());p['schemaVersion']='alien';write_json(path,p)
        with self.assertRaisesRegex(ValueError,'PROVENANCE_SCHEMA'):analyze(self.run,self.derived,self.tool)

    def test_provenance_binding_tool_and_creator_rejected(self):
        for key in ('runId','clockDomain','collectorSessionId','traceStartElapsedNanos'):
            self.save(self.old);p=json.loads((self.folder/'provenance.json').read_text());p['run'][key]='OTHER'
            write_json(self.folder/'provenance.json',p)
            with self.subTest(key=key),self.assertRaisesRegex(ValueError,'PROVENANCE_RUN_CLOCK'):analyze(self.run,self.derived,self.tool)
        for field in ('inputs','tool','creator'):
            self.save(self.old);p=json.loads((self.folder/'provenance.json').read_text());p[field][next(iter(p[field]))]='OTHER'
            write_json(self.folder/'provenance.json',p)
            with self.subTest(field=field),self.assertRaises(ValueError):analyze(self.run,self.derived,self.tool)

    def test_trial_missing_duplicate_extra_and_bad_conclusion_rejected(self):
        for mutation in (lambda j:j['rows'].pop(),lambda j:j['rows'].append(j['rows'][0]),
                         lambda j:j['rows'][0].update(trialId='trial-999'),
                         lambda j:j['rows'][0].update(conclusion='ALIEN'),
                         lambda j:j['rows'][0].update(reason='MOCK WRONG REASON'),
                         lambda j:j['rows'][0].update(firstScanEndElapsedNanos=float('nan')),
                         lambda j:j['rows'][0].update(pid=999),
                         lambda j:j.update(runId='OTHER'),
                         lambda j:j.update(clockDomain='OTHER'),
                         lambda j:j['rows'][0].update(firstScanBeginElapsedNanos=9_000_000_000),
                         lambda j:j['rows'][0].update(firstScanRecord=True),
                         lambda j:j.update(observed=3)):
            j=copy.deepcopy(self.old);mutation(j);self.save(j)
            with self.assertRaises(ValueError):analyze(self.run,self.derived,self.tool)

    def test_changed_join_cannot_be_installed_in_original_without_checksum_failure(self):
        # The old default contract correctly refuses replacing historical join bytes.
        write_json(self.run/'join.json',self.changed())
        with self.assertRaisesRegex(ValueError,'RAW_CHECKSUM'):analyze(self.run)

    def test_cli_diff_receipt_and_determinism(self):
        self.save(self.changed())
        for name in ('out1','out2'):
            result=self.cli(self.root/name,'--derived-joins',self.derived,'--join-tool',self.tool)
            self.assertEqual(result.returncode,0,result.stderr.decode())
        one,two=self.root/'out1',self.root/'out2'
        self.assertEqual({p.name:p.read_bytes() for p in one.iterdir()},{p.name:p.read_bytes() for p in two.iterdir()})
        import csv
        diff=list(csv.DictReader((one/'join-differences.csv').open(encoding='utf-8')))
        self.assertEqual([r['trialId'] for r in diff if r['changed']=='True'],['trial-002'])
        self.assertEqual(diff[1]['old_conclusion'],'INCONCLUSIVE')
        self.assertEqual(diff[1]['new_conclusion'],'NOT_OBSERVED_IN_TRACE')
        receipt=json.loads((one/'analysis-receipt.json').read_text())
        self.assertEqual(receipt['joinMode'],'DERIVED')
        self.assertEqual(receipt['runs'][0]['rawInputs'],inputs(self.run))
        self.assertEqual(receipt['tools']['joinToolJarSha256'],digest(self.tool))
        self.assertEqual(json.loads((one/'campaign-status.json').read_text())['status'],'PARTIAL')
        before=(one/'summary.csv').read_bytes()
        self.assertNotEqual(self.cli(one).returncode,0)
        self.assertEqual((one/'summary.csv').read_bytes(),before)

    def test_invalid_derived_run_stays_invalid_without_campaign_summary(self):
        self.save(self.changed());(self.folder/'join.json').write_text('{}',encoding='utf-8')
        out=self.root/'invalid'
        self.assertNotEqual(self.cli(out,'--derived-joins',self.derived,'--join-tool',self.tool).returncode,0)
        self.assertEqual(json.loads((out/'run-status.json').read_text())[0]['status'],'INVALID')
        self.assertFalse((out/'summary.csv').exists())

    def test_no_implicit_tool_selection_and_no_path_fallback(self):
        with self.assertRaisesRegex(ValueError,'EXPLICIT_JOIN_TOOL_REQUIRED'):analyze(self.run,self.derived)
        p=self.folder/'provenance.json';j=json.loads(p.read_text());j['join']['file']='../join.json';write_json(p,j)
        with self.assertRaisesRegex(ValueError,'DERIVED_JOIN_HASH'):analyze(self.run,self.derived,self.tool)
        self.save(self.old);(self.folder/'other.json').write_text('{}')
        with self.assertRaisesRegex(ValueError,'DERIVED_FILE_SET'):analyze(self.run,self.derived,self.tool)

    def test_extra_or_missing_run_never_falls_back_to_original(self):
        for extra in (False,True):
            root=self.root/('selection-'+str(extra));root.mkdir()
            if extra:(root/'run-EXTRA').mkdir()
            result=self.cli(self.root/('out-'+str(extra)),'--derived-joins',root,'--join-tool',self.tool)
            self.assertNotEqual(result.returncode,0)
            inventory=json.loads((self.root/('out-'+str(extra))/'run-status.json').read_text())
            self.assertEqual(inventory[0]['status'],'INVALID')
            self.assertIn('DERIVED_RUN_SET',inventory[0]['reason'])

    def test_derive_output_guard_and_all_inputs_prechecked_before_java(self):
        from derived import main
        out=self.root/'exists';out.mkdir();(out/'keep').write_text('unchanged')
        with patch.object(sys,'argv',['derived.py',str(self.run.parent),str(out),'--join-tool',str(self.tool)]),\
                patch('derived.subprocess.run') as java, self.assertRaises(FileExistsError):main()
        java.assert_not_called();self.assertEqual((out/'keep').read_text(),'unchanged')
        (self.run/'observations.jsonl').write_text('corrupt')
        fresh=self.root/'fresh'
        with patch.object(sys,'argv',['derived.py',str(self.run.parent),str(fresh),'--join-tool',str(self.tool)]),\
                patch('derived.subprocess.run') as java, self.assertRaisesRegex(ValueError,'RAW_CHECKSUM'):main()
        java.assert_not_called();self.assertFalse(fresh.exists())

    def test_provenance_unknown_fields_duplicate_json_and_fake_source_rejected(self):
        for mutation in (lambda p:p.update(extra='not in v1'),lambda p:p['creator'].update(sourceSha='0'*40),
                         lambda p:p['inputs'].update({'resources.csv':'f'*64}),
                         lambda p:p['tool'].update(jarSha256='f'*64)):
            self.save(self.old);p=json.loads((self.folder/'provenance.json').read_text());mutation(p)
            write_json(self.folder/'provenance.json',p)
            with self.assertRaises(ValueError):analyze(self.run,self.derived,self.tool)
        self.save(self.old);p=self.folder/'provenance.json'
        p.write_text('{"schemaVersion":"e1-derived-join-v1",'+p.read_text()[1:])
        with self.assertRaisesRegex(ValueError,'DUPLICATE_JSON_KEY'):analyze(self.run,self.derived,self.tool)

    def test_missing_tool_is_invalid_with_receipt_not_fallback(self):
        out=self.root/'missing-tool'
        result=self.cli(out,'--derived-joins',self.derived,'--join-tool',self.root/'absent.jar')
        self.assertNotEqual(result.returncode,0)
        self.assertEqual(json.loads((out/'run-status.json').read_text())[0]['status'],'INVALID')
        receipt=json.loads((out/'analysis-receipt.json').read_text())
        self.assertEqual(receipt['runs'][0]['rawInputs'],inputs(self.run))
        self.assertIsNone(receipt['tools']['joinToolJarSha256'])


if __name__=='__main__':unittest.main()
