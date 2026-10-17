import unittest
from analyze import percentile,cpu_percent,eligible,mean_variance,campaign_quality
class MetricsTest(unittest.TestCase):
    def test_detected_only_nearest_rank(self):
        self.assertIsNone(percentile([], .95));self.assertEqual(percentile([4,1,3,2],.5),2);self.assertEqual(percentile([4,1,3,2],.95),4)
    def test_cpu_one_core(self):
        self.assertEqual(cpu_percent(500,1000),50);self.assertEqual(cpu_percent(2000,1000),200);self.assertIsNone(cpu_percent(-1,1000));self.assertIsNone(cpu_percent(1,0))
    def test_window_censoring(self):
        self.assertTrue(eligible(10,20,10,20));self.assertFalse(eligible(None,20,10,20));self.assertFalse(eligible(9,20,10,20));self.assertFalse(eligible(10,21,10,20))
    def test_sample_variance_missing_and_single_run(self):
        self.assertEqual(mean_variance([1,None,2,3]),(3,2,1))
        self.assertEqual(mean_variance([7]),(1,7,None))
        self.assertEqual(mean_variance([None]),(0,None,None))
    def test_campaign_requires_explicit_machine_alias_and_detects_mixing(self):
        base=dict(sourceSha='a'*40,binarySha256='b'*64,planSha256='c'*64,policyVersion='test-owned-process-v1',
                  javaVersion='21',javaVendor='MOCK',machine='UNSPECIFIED',os='Windows 11',osVersion='10',architecture='amd64',
                  measurementStartElapsedNanos=15_000_000_000,measurementEndElapsedNanos=135_000_000_000,
                  config=dict(pollMillis=250,perDuration=10,seed=1234))
        rows=[]
        for period in (250,500,1000,2000):
            for _ in range(3):
                row=base.copy();row['config']=base['config'].copy();row['config']['pollMillis']=period;rows.append(row)
        reasons,_=campaign_quality(rows)
        self.assertIn('MACHINE_ALIAS_REQUIRED',reasons)
        for row in rows: row['machine']='LAB-A'
        self.assertEqual([],campaign_quality(rows)[0])
        rows[-1]['machine']='LAB-B'
        self.assertIn('MIXED_SOURCE_PLAN_CONFIG_OR_ENVIRONMENT',campaign_quality(rows)[0])
if __name__=='__main__':unittest.main()
