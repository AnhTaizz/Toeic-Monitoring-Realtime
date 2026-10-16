import pathlib, tempfile, unittest, json, subprocess, sys
from analyze import analyze, resource_metrics
from review_fixture import fixture, seal

class ReviewTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=pathlib.Path(self.temp.name)
        fixture(self.root)

    def test_hand_calculated_counts_latency_and_weighted_cpu(self):
        events,rows=analyze(self.root)
        row=rows[0]
        self.assertEqual([row[k] for k in ('eligible','detected','missed','inconclusive','censored')],[4,2,1,1,1])
        self.assertIsNone(row['missRate'])
        self.assertEqual((row['missRateLowerBound'],row['missRateUpperBound']),(.25,.5))
        self.assertEqual((row['p50ParentLatencyMs'],row['p95ParentLatencyMs']),(100,300))
        self.assertIsNone(events[2]['parentLaunchToScanEndMs'])
        self.assertEqual(row['collectorCpuOneCoreMean'],10)
        self.assertEqual(row['parentCpuOneCoreMean'],20)

    def mutate_join(self, change):
        path=self.root/'join.json'
        data=json.loads(path.read_text(encoding='utf-8'))
        change(data)
        path.write_text(json.dumps(data),encoding='utf-8')
        seal(self.root)

    def test_negative_latency_rejected_not_clamped(self):
        self.mutate_join(lambda data: data['rows'][0].update(firstScanEndElapsedNanos=0))
        with self.assertRaisesRegex(ValueError,'NEGATIVE_LATENCY'): analyze(self.root)

    def test_unknown_outcome_and_duplicate_ids_rejected(self):
        self.mutate_join(lambda data: data['rows'][0].update(conclusion='ALIEN'))
        with self.assertRaisesRegex(ValueError,'UNKNOWN_CONCLUSION'): analyze(self.root)
        fixture(self.root)
        self.mutate_join(lambda data: data['rows'].append(data['rows'][0]))
        with self.assertRaisesRegex(ValueError,'TRIAL_IDS_OR_COUNT'): analyze(self.root)

    def test_binding_and_checksum_rejected(self):
        self.mutate_join(lambda data: data.update(clockDomain='other-jvm'))
        with self.assertRaisesRegex(ValueError,'RUN_CLOCK_BINDING'): analyze(self.root)
        fixture(self.root)
        with (self.root/'resources.csv').open('a',encoding='utf-8') as output: output.write('broken')
        with self.assertRaisesRegex(ValueError,'RESOURCE_CHECKSUM'): analyze(self.root)

    def test_missing_counters_and_boundary_samples_report_coverage(self):
        def row(t,c,h): return dict(elapsedNanos=t,collectorThreadCpuNanos=c,parentProcessCpuNanos=c,parentHeapUsedBytes=h)
        metrics=resource_metrics([row(-1,0,99),row(0,0,10),row(5,5,20),row(10,-1,30),row(11,9,99)],0,10,5)
        self.assertEqual(metrics['collectorCpuCoveredNanos'],5)
        self.assertEqual(metrics['collectorCpuOneCoreMean'],100)
        self.assertEqual(metrics['resourceStatus'],'PARTIAL')
        self.assertEqual(metrics['parentHeapMeanBytes'],20)
        self.assertEqual(metrics['parentHeapPeakBytes'],30)
        self.assertEqual(metrics['resourceInvalidIntervals'],2)
        self.assertEqual(resource_metrics([],0,10,5)['resourceStatus'],'PARTIAL')
        with self.assertRaisesRegex(ValueError,'RESOURCE_TIME_ORDER'):
            resource_metrics([row(1,0,1),row(1,1,1)],0,10,5)

    def test_invalid_run_is_in_inventory_and_prevents_campaign_summary(self):
        suite=self.root/'suite'; suite.mkdir()
        fixture(suite/'run-good'); fixture(suite/'run-bad')
        (suite/'run-bad'/'resources.csv').write_text('corrupt',encoding='utf-8')
        out=self.root/'out'
        result=subprocess.run([sys.executable,str(pathlib.Path(__file__).with_name('analyze.py')),str(suite),str(out)],capture_output=True)
        self.assertNotEqual(result.returncode,0)
        inventory=json.loads((out/'run-status.json').read_text(encoding='utf-8'))
        self.assertEqual([r['status'] for r in inventory],['INVALID','VALID'])
        self.assertFalse((out/'summary.csv').exists())

    def test_copied_run_cannot_be_counted_as_another_repeat(self):
        suite=self.root/'duplicate'; suite.mkdir()
        fixture(suite/'run-a'); fixture(suite/'run-b')
        out=self.root/'duplicate-out'
        result=subprocess.run([sys.executable,str(pathlib.Path(__file__).with_name('analyze.py')),str(suite),str(out)],capture_output=True)
        self.assertNotEqual(result.returncode,0)
        inventory=json.loads((out/'run-status.json').read_text(encoding='utf-8'))
        self.assertIn('DUPLICATE_RUN_ID',inventory[1]['reason'])
        self.assertFalse((out/'summary.csv').exists())

if __name__=='__main__':unittest.main()
