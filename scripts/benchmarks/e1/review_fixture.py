import json, pathlib, tempfile
from analyze import analyze

def fixture(root):
    """MOCK: 4 eligible 200ms trials (2 detected, 1 missed, 1 unknown), 1 censored."""
    root.mkdir(exist_ok=True)
    meta=dict(runId='MOCK-run', status='COMPLETE', e1e2='SMOKE MOCK', sourceSha='a'*40,
              clockDomain='parent-jvm:MOCK-run', collectorSessionId='MOCK-collector', traceStartElapsedNanos=0,
              config=dict(pollMillis=500,perDuration=1), measurementStartElapsedNanos=0,
              measurementEndElapsedNanos=10_000_000_000, resourceSamplePeriodMillis=500)
    truth=[dict(kind='HEADER',runId=meta['runId'],clockDomain=meta['clockDomain'],collectorSessionId=meta['collectorSessionId'],traceStartElapsedNanos=0)]
    rows=[]
    for i,outcome in enumerate(['OBSERVED','OBSERVED','NOT_OBSERVED_IN_TRACE','INCONCLUSIVE','OBSERVED']):
        start=(i+1)*1_000_000_000; end=start+500_000_000 if i<4 else 11_000_000_000
        truth.append(dict(kind='TRIAL',planned=dict(trialId=str(i),targetMillis=200),launchBeginElapsedNanos=start,
                          exitObservedElapsedNanos=end,observedLifetimeNanos=end-start,observedHoldNanos=200_000_000))
        rows.append(dict(trialId=str(i),targetMillis=200,conclusion=outcome,reason='MOCK',metadataQuality='COMPLETE',
                         firstScanEndElapsedNanos=start+(100_000_000 if i==0 else 300_000_000) if outcome=='OBSERVED' else None))
    truth.append(dict(kind='FINAL',status='COMPLETE',count=5))
    (root/'metadata.json').write_text(json.dumps(meta),encoding='utf-8')
    (root/'ground-truth.jsonl').write_text(''.join(json.dumps(t)+'\n' for t in truth),encoding='utf-8')
    (root/'join.json').write_text(json.dumps(dict(runId=meta['runId'],status='COMPLETE',clockDomain=meta['clockDomain'],rows=rows)),encoding='utf-8')
    (root/'observations.jsonl').write_text('MOCK checksum fixture; Java trace reader tested separately\n',encoding='utf-8')
    # Unequal intervals: 100% for 1s, 0% for 9s => 10%, NOT (100+0)/2=50%.
    (root/'resources.csv').write_text('elapsedNanos,collectorThreadCpuNanos,parentProcessCpuNanos,parentHeapUsedBytes\n0,0,0,100\n1000000000,1000000000,2000000000,200\n10000000000,1000000000,2000000000,300\n',encoding='utf-8')
    seal(root)

def seal(root):
    from analyze import digest
    files={n:digest(root/n) for n in ['ground-truth.jsonl','observations.jsonl','metadata.json','join.json']}
    (root/'manifest.json').write_text(json.dumps(dict(status='COMPLETE',files=files)),encoding='utf-8')
    (root/'e1-manifest.json').write_text(json.dumps(dict(resourcesSha256=digest(root/'resources.csv'),runManifestSha256=digest(root/'manifest.json'))),encoding='utf-8')
