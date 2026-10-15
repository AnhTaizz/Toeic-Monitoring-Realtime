import unittest
from analyze import percentile,cpu_percent,eligible,mean_variance
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
if __name__=='__main__':unittest.main()
