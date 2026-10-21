import unittest, pathlib, tempfile
from report import aggregate, resource_rows, validate_output

class ReportTest(unittest.TestCase):
    def test_output_guard_leaves_input_directories_unchanged(self):
        with tempfile.TemporaryDirectory() as temp:
            root=pathlib.Path(temp); suite=root/'raw'; analysis=root/'analysis'
            suite.mkdir(); analysis.mkdir()
            for output in (suite/'new', analysis/'new'):
                with self.assertRaisesRegex(ValueError,'OUTPUT_INSIDE_INPUT'):
                    validate_output(suite,analysis,output)
                self.assertFalse(output.exists())
            validate_output(suite,analysis,root/'report')

    def test_unknown_bounds_and_detected_only_pooled_quantiles(self):
        rows=[dict(pollMs='250',targetMillis='200',eligible='2',detected='1',missed='0',inconclusive='1',censored='0',missRate=''),
              dict(pollMs='250',targetMillis='200',eligible='2',detected='1',missed='1',inconclusive='0',censored='1',missRate='.5')]
        events=[dict(pollMs='250',targetMillis='200',eligible='True',outcome='OBSERVED',parentLaunchToScanEndMs=str(x)) for x in (100,300)]
        events.append(dict(pollMs='250',targetMillis='200',eligible='False',outcome='OBSERVED',parentLaunchToScanEndMs='900'))
        value=aggregate(rows,events)[0]
        self.assertEqual((value['eligible'],value['detected'],value['inconclusive'],value['censored']),(4,2,1,1))
        self.assertEqual((value['missLower'],value['missUpper']),(.25,.5)); self.assertIsNone(value['pooledMissRate'])
        self.assertEqual((value['pooledP50ProxyMs'],value['pooledP95ProxyMs']),(100,300))
        self.assertEqual(value['definedMissRateRepeats'],1); self.assertIsNone(value['definedRunMissSampleVariance'])
        with self.assertRaisesRegex(ValueError,'LATENCY_MISMATCH'): aggregate(rows,events[:1])

    def test_partial_resources_visible_and_not_used_as_ok_repeat(self):
        def run(status,cpu):
            return dict(pollMs='250',resourceStatus=status,collectorCpuOneCoreMean=str(cpu),parentCpuOneCoreMean=str(cpu*2),
                parentHeapMeanBytes='10',parentHeapPeakBytes='20',resourceCoveredNanos='119000000000',
                collectorCpuCoveredNanos='119000000000',parentCpuCoveredNanos='119000000000')
        value=resource_rows([run('OK',10),run('PARTIAL',90)])[0]
        self.assertEqual((value['repeats'],value['resourceOkRepeats'],value['resourcePartialRepeats']),(2,1,1))
        self.assertEqual(value['collectorCpuOneCoreMeanAcrossRunMean'],10)
        self.assertIsNone(value['collectorCpuOneCoreMeanAcrossRunSampleVariance'])

if __name__=='__main__': unittest.main()
