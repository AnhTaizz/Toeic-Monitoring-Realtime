"""Tables/figures from existing E1 analyzer outputs; acquisition and estimators are unchanged."""
import argparse, csv, hashlib, json, math, pathlib, shutil
from analyze import mean_variance, percentile

PERIODS = (250, 500, 1000, 2000)
HOLDS = (200, 800, 3000)

def read_csv(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream))

def number(value):
    return float(value) if value not in ('', None) else None

def validate_output(suite, analysis, output):
    if any(output.resolve().is_relative_to(p.resolve()) for p in (suite, analysis)):
        raise ValueError('OUTPUT_INSIDE_INPUT')

def aggregate(rows, events):
    """Keep unknown in bounds; use only eligible OBSERVED samples for latency."""
    result = []
    for poll in sorted({int(r['pollMs']) for r in rows}):
        for hold in HOLDS:
            group = [r for r in rows if int(r['pollMs']) == poll and int(r['targetMillis']) == hold]
            if not group:
                continue
            counts = {k: sum(int(r[k]) for r in group) for k in ('eligible', 'detected', 'missed', 'inconclusive', 'censored')}
            n = counts['eligible']
            if n != counts['detected'] + counts['missed'] + counts['inconclusive']:
                raise ValueError('DENOMINATOR_MISMATCH')
            latencies = [number(e['parentLaunchToScanEndMs']) for e in events
                         if int(e['pollMs']) == poll and int(e['targetMillis']) == hold
                         and e['eligible'] == 'True' and e['outcome'] == 'OBSERVED']
            if len(latencies) != counts['detected'] or any(x is None or x < 0 for x in latencies):
                raise ValueError('ELIGIBLE_DETECTED_LATENCY_MISMATCH')
            points = [number(r['missRate']) for r in group]
            defined, mean, variance = mean_variance(points)
            result.append(dict(pollMs=poll, targetHoldMs=hold, repeats=len(group),
                definedMissRateRepeats=defined, **counts,
                pooledMissRate=None if counts['inconclusive'] or not n else counts['missed']/n,
                missLower=counts['missed']/n if n else None,
                missUpper=(counts['missed']+counts['inconclusive'])/n if n else None,
                definedRunMissMean=mean, definedRunMissSampleVariance=variance,
                detectedLatencyN=len(latencies), pooledP50ProxyMs=percentile(latencies, .5),
                pooledP95ProxyMs=percentile(latencies, .95)))
    return result

def resource_rows(runs):
    result = []
    for poll in sorted({int(r['pollMs']) for r in runs}):
        group = [r for r in runs if int(r['pollMs']) == poll]
        good = [r for r in group if r['resourceStatus'] == 'OK']
        row = dict(pollMs=poll, repeats=len(group), resourceOkRepeats=len(good), resourcePartialRepeats=len(group)-len(good))
        for field in ('collectorCpuOneCoreMean', 'parentCpuOneCoreMean', 'parentHeapMeanBytes', 'parentHeapPeakBytes'):
            n, mean, variance = mean_variance([number(r[field]) for r in good])
            row.update({field+'N': n, field+'AcrossRunMean': mean, field+'AcrossRunSampleVariance': variance})
        for field in ('resourceCoveredNanos', 'collectorCpuCoveredNanos', 'parentCpuCoveredNanos'):
            coverage = [float(r[field])/120_000_000_000 for r in group]
            row[field+'FractionMin'] = min(coverage)
            row[field+'FractionMax'] = max(coverage)
        result.append(row)
    return result

def write_csv(path, rows):
    with path.open('x', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader(); writer.writerows(rows)

def figures(out, buckets, resources, runs):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    matplotlib.rcParams.update({'svg.hashsalt': 'qa05-e1-v1', 'svg.fonttype': 'none',
        'font.size': 10, 'axes.spines.top': False, 'axes.spines.right': False})
    def save(fig, name):
        fig.savefig(out/(name+'.png'), dpi=170, metadata={'Software': 'QA05 E1 CSV report'})
        fig.savefig(out/(name+'.svg'), metadata={'Date': None, 'Creator': 'QA05 E1 CSV report'})
        plt.close(fig)
    fig, axes = plt.subplots(1, 3, figsize=(13, 4.5), sharey=True, layout='constrained')
    for ax, hold in zip(axes, HOLDS):
        group = [r for r in buckets if r['targetHoldMs'] == hold]
        bottom = [0.0]*len(group)
        for key, label, color in [('detected','Observed','#237a9b'),('missed','Missed','#df7848'),('inconclusive','Inconclusive','#c5a147')]:
            values = [100*r[key]/r['eligible'] if r['eligible'] else 0 for r in group]
            ax.bar(range(len(group)), values, bottom=bottom, label=label, color=color, width=.65)
            bottom = [a+b for a,b in zip(bottom,values)]
        ax.set_xticks(range(len(group)), [str(r['pollMs']) for r in group]); ax.set_ylim(0, 108)
        for i,r in enumerate(group): ax.text(i, 102, 'N='+str(r['eligible']), ha='center', fontsize=9)
        ax.set_title(f'Target hold {hold} ms'); ax.set_xlabel('Configured poll (ms)')
    axes[0].set_ylabel('Eligible trial share (%)'); axes[-1].legend(loc='lower right')
    fig.suptitle('E1: observed / missed / inconclusive (3 repeats per period; hold is not exact lifetime)')
    save(fig, 'outcomes')
    fig, axes = plt.subplots(1, 3, figsize=(13, 4.4), layout='constrained')
    for ax, hold in zip(axes, HOLDS):
        group = [r for r in buckets if r['targetHoldMs'] == hold]; x = list(range(len(group)))
        ax.plot(x, [r['pooledP50ProxyMs'] for r in group], 'o-', label='Pooled p50')
        ax.plot(x, [r['pooledP95ProxyMs'] for r in group], 's-', label='Pooled p95')
        ax.set_xticks(x, [f"{r['pollMs']}\nn={r['detectedLatencyN']}" for r in group])
        ax.set_title(f'Target hold {hold} ms'); ax.set_xlabel('Configured poll (ms); detected sample n')
        ax.set_ylabel('Parent launch-to-scan-end proxy (ms)'); ax.grid(axis='y', alpha=.2); ax.set_ylim(bottom=0)
    axes[-1].legend(); fig.suptitle('Detected eligible trials only; nearest-rank quantiles; not kernel latency')
    save(fig, 'latency-proxy')
    fig, axes = plt.subplots(1, 2, figsize=(10, 4.4), layout='constrained')
    for ax, field, title in zip(axes, ('collectorCpuOneCoreMean','parentCpuOneCoreMean'), ('Collector thread','Parent JVM (excludes children)')):
        for i,r in enumerate(resources):
            points = [float(x[field]) for x in runs if int(x['pollMs']) == r['pollMs'] and x['resourceStatus'] == 'OK']
            mean=r[field+'AcrossRunMean']; variance=r[field+'AcrossRunSampleVariance']
            ax.scatter([i]*len(points), points, color='#73808c', s=25, zorder=3)
            if mean is not None: ax.errorbar(i, mean, yerr=math.sqrt(variance) if variance is not None else None, fmt='D', color='#237a9b', capsize=5)
        ax.set_xticks(range(len(resources)), [f"{r['pollMs']}\nOK n={r['resourceOkRepeats']}" for r in resources])
        ax.set_title(title); ax.set_xlabel('Configured poll (ms)'); ax.set_ylabel('CPU (% of one core)')
        ax.set_ylim(bottom=0); ax.grid(axis='y', alpha=.2)
    fig.suptitle('Time-weighted CPU per run; dots=repeats, diamond=mean, error=sample SD (not CI)')
    save(fig, 'cpu')
    fig, ax = plt.subplots(figsize=(11, 4.4), layout='constrained'); x=list(range(1,len(runs)+1))
    ax.plot(x, [float(r['parentHeapMeanBytes'])/1048576 for r in runs], 'o-', label='Mean of heap-used samples')
    ax.plot(x, [float(r['parentHeapPeakBytes'])/1048576 for r in runs], 's--', label='Maximum sampled heap used')
    ax.set_xticks(x, [f"{i}\n{r['pollMs']}" for i,r in zip(x,runs)]); ax.set_xlabel('Run order / configured poll (ms)')
    ax.set_ylabel('Parent JVM heap used (MiB), not RSS'); ax.set_ylim(bottom=0); ax.legend(); ax.grid(axis='y', alpha=.2)
    ax.set_title('One shared parent JVM: heap / GC / JIT carryover remains possible')
    save(fig, 'heap')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('suite', type=pathlib.Path); parser.add_argument('analysis', type=pathlib.Path); parser.add_argument('output', type=pathlib.Path)
    args=parser.parse_args(); validate_output(args.suite,args.analysis,args.output)
    args.output.mkdir(parents=True, exist_ok=False)
    receipt=json.loads((args.analysis/'analysis-receipt.json').read_text(encoding='utf-8'))
    for name, expected in receipt['outputs'].items():
        if hashlib.sha256((args.analysis/name).read_bytes()).hexdigest() != expected: raise ValueError('ANALYSIS_CHECKSUM')
    runs=read_csv(args.analysis/'summary.csv'); rows=read_csv(args.analysis/'summary-by-duration.csv'); events=[]; inventory=[]
    raw_dirs=sorted(args.suite.glob('run-*'))
    if len(raw_dirs)!=len(runs): raise ValueError('RUN_COUNT')
    for directory, run in zip(raw_dirs,runs):
        metadata=json.loads((directory/'metadata.json').read_text(encoding='utf-8'))
        if metadata['runId']!=run['runId']: raise ValueError('RUN_ID')
        for event in read_csv(args.analysis/(directory.name+'-events.csv')): events.append(dict(event,pollMs=run['pollMs']))
        n=lambda field: sum(int(run[f'{field}_{hold}ms']) for hold in HOLDS)
        inventory.append(dict(order=len(inventory)+1, run=directory.name, pollMs=run['pollMs'], status=metadata['status'],
            resourcesCleaned=metadata['resourcesCleaned'], resourceStatus=run['resourceStatus'],
            eligible=n('eligible'), detected=n('detected'), missed=n('missed'), inconclusive=n('inconclusive'), censored=n('censored'),
            collectorCpuOneCoreMean=run['collectorCpuOneCoreMean'], parentCpuOneCoreMean=run['parentCpuOneCoreMean'],
            parentHeapMeanMiB=float(run['parentHeapMeanBytes'])/1048576, sampledHeapPeakMiB=float(run['parentHeapPeakBytes'])/1048576,
            collectorCpuCoverage=float(run['collectorCpuCoveredNanos'])/120_000_000_000,
            parentCpuCoverage=float(run['parentCpuCoveredNanos'])/120_000_000_000, resourceMaxGapMs=float(run['resourceMaxGapNanos'])/1e6))
    buckets=aggregate(rows,events); resources=resource_rows(runs)
    write_csv(args.output/'run-results.csv',inventory); write_csv(args.output/'outcomes-by-period-hold.csv',buckets); write_csv(args.output/'resources-by-period.csv',resources)
    for name in ('summary.csv','summary-by-duration.csv','analysis.md','campaign-status.json','run-status.json','analysis-receipt.json'):
        shutil.copyfile(args.analysis/name,args.output/name)
    figures(args.output,buckets,resources,runs)
    import matplotlib
    manifest=dict(schemaVersion='e1-report-artifacts-v1', scriptSha256=hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest(),
        matplotlibVersion=matplotlib.__version__, analysisReceiptSha256=hashlib.sha256((args.analysis/'analysis-receipt.json').read_bytes()).hexdigest(),
        files={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(args.output.iterdir())})
    (args.output/'figures-receipt.json').write_text(json.dumps(manifest,indent=2,sort_keys=True)+'\n',encoding='utf-8')

if __name__=='__main__': main()
