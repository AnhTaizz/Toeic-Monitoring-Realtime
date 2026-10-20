"""Phân tích raw E1 bất biến, stdlib Python. Latency parent launch -> scan-end là cận trên, không kernel time."""
import csv, hashlib, json, math, pathlib, statistics, sys, collections, re, argparse

def percentile(values, percent):
    return sorted(values)[max(0, math.ceil(len(values)*percent)-1)] if values else None

def cpu_percent(cpu_delta, wall_delta):
    return 100*cpu_delta/wall_delta if cpu_delta >= 0 and wall_delta > 0 else None

def mean_variance(values):
    readable=[value for value in values if value is not None]
    return (len(readable), statistics.mean(readable) if readable else None,
            statistics.variance(readable) if len(readable)>1 else None)

def stats_text(values):
    count,mean,variance=mean_variance(values)
    return f"{mean} / {variance} (n={count})"

def eligible(start, end, low, high):
    return start is not None and end is not None and low <= start <= end <= high

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def resource_metrics(samples, low, high, period):
    """Time-weighted CPU over readable, wholly contained intervals; no interpolation."""
    if high <= low or period <= 0:
        raise ValueError('RESOURCE_WINDOW')
    totals = {key: [0, 0, 0] for key in ('collectorThreadCpuNanos', 'parentProcessCpuNanos')}
    ticks = [int(row['elapsedNanos']) for row in samples]
    if any(b <= a for a, b in zip(ticks, ticks[1:])):
        raise ValueError('RESOURCE_TIME_ORDER')
    in_window = [row for row in samples if low <= int(row['elapsedNanos']) <= high]
    heap = [int(row['parentHeapUsedBytes']) for row in in_window if int(row['parentHeapUsedBytes']) >= 0]
    covered = 0
    max_gap = 0
    invalid_intervals = 0
    for previous, current in zip(samples, samples[1:]):
        a, b = int(previous['elapsedNanos']), int(current['elapsedNanos'])
        if not eligible(a, b, low, high):
            continue
        covered += b-a
        max_gap = max(max_gap, b-a)
        for field, total in totals.items():
            before, after = int(previous[field]), int(current[field])
            if before < 0 or after < before:
                invalid_intervals += 1
                continue
            total[0] += after-before
            total[1] += b-a
            total[2] += 1
    collector, parent = totals.values()
    good = (covered >= .95*(high-low) and max_gap <= 2*period and
            invalid_intervals == 0 and len(heap) == len(in_window) and len(heap) >= 2)
    return dict(collectorCpuOneCoreMean=cpu_percent(collector[0], collector[1]),
                parentCpuOneCoreMean=cpu_percent(parent[0], parent[1]),
                parentHeapMeanBytes=statistics.mean(heap) if heap else None,
                parentHeapPeakBytes=max(heap) if heap else None,
                resourceSamples=len(heap), collectorCpuReadableSamples=collector[2],
                parentCpuReadableSamples=parent[2], resourceCoveredNanos=covered,
                collectorCpuCoveredNanos=collector[1], parentCpuCoveredNanos=parent[1],
                resourceMaxGapNanos=max_gap, resourceInvalidIntervals=invalid_intervals,
                resourceStatus='OK' if good else 'PARTIAL')


def analyze(directory, derived_joins=None, join_tool=None, provenance_script=None):
    directory=pathlib.Path(directory)
    if derived_joins is not None:
        if join_tool is None: raise ValueError('EXPLICIT_JOIN_TOOL_REQUIRED')
        # Fully validate the immutable original, including its join, even when superseded.
        analyze(directory)
    manifest=json.loads((directory/'manifest.json').read_text(encoding='utf-8'))
    if manifest['status']!='COMPLETE': raise ValueError('INCOMPLETE run retained')
    if set(manifest['files']) != {'ground-truth.jsonl','observations.jsonl','metadata.json','join.json'}:
        raise ValueError('MANIFEST_FILE_SET')
    for name, expected in manifest['files'].items():
        if digest(directory/name)!=expected: raise ValueError('RAW_CHECKSUM')
    extra=json.loads((directory/'e1-manifest.json').read_text(encoding='utf-8'))
    if digest(directory/'resources.csv')!=extra['resourcesSha256'] or digest(directory/'manifest.json')!=extra['runManifestSha256']: raise ValueError('RESOURCE_CHECKSUM')
    metadata=json.loads((directory/'metadata.json').read_text(encoding='utf-8'))
    low,high=metadata['measurementStartElapsedNanos'],metadata['measurementEndElapsedNanos']
    truth=[json.loads(x) for x in (directory/'ground-truth.jsonl').read_text(encoding='utf-8').splitlines()]
    joined=json.loads((directory/'join.json').read_text(encoding='utf-8'))
    if derived_joins is not None:
        from derived import load_derived
        joined=load_derived(directory,derived_joins,join_tool,provenance_script)
    if metadata['status'] != 'COMPLETE' or joined['status'] != 'COMPLETE':
        raise ValueError('INCOMPLETE_METADATA_OR_JOIN')
    for key in ('runId','clockDomain'):
        if metadata[key] != truth[0][key] or metadata[key] != joined[key]:
            raise ValueError('RUN_CLOCK_BINDING')
    for key in ('collectorSessionId','traceStartElapsedNanos'):
        if metadata[key] != truth[0][key]:
            raise ValueError('COLLECTOR_CLOCK_BINDING')
    if truth[0]['kind'] != 'HEADER' or truth[-1]['kind'] != 'FINAL' or truth[-1]['status'] != 'COMPLETE':
        raise ValueError('GROUND_TRUTH_BOUNDARY')
    ids=[trial['planned']['trialId'] for trial in truth[1:-1]]
    rows={row['trialId']:row for row in joined['rows']}
    if len(ids)!=len(set(ids)) or set(ids)!=set(rows) or len(rows)!=len(joined['rows']) or truth[-1]['count']!=len(ids):
        raise ValueError('TRIAL_IDS_OR_COUNT')
    if low < 0 or high <= low:
        raise ValueError('MEASUREMENT_WINDOW')
    events=[]
    for trial in truth[1:-1]:
        row=rows[trial['planned']['trialId']]
        if row['targetMillis'] != trial['planned']['targetMillis'] or row['targetMillis'] not in (200,800,3000):
            raise ValueError('TARGET_MISMATCH')
        if row['conclusion'] not in ('OBSERVED','NOT_OBSERVED_IN_TRACE','INCONCLUSIVE'):
            raise ValueError('UNKNOWN_CONCLUSION')
        included=eligible(trial['launchBeginElapsedNanos'],trial['exitObservedElapsedNanos'],low,high)
        outcome=row['conclusion'] if included else 'CENSORED'
        latency=(row['firstScanEndElapsedNanos']-trial['launchBeginElapsedNanos'])/1e6 if included and outcome=='OBSERVED' else None
        if latency is not None and latency < 0:
            raise ValueError('NEGATIVE_LATENCY')
        events.append(dict(trialId=row['trialId'],targetMillis=row['targetMillis'],launchNanos=trial['launchBeginElapsedNanos'],exitNanos=trial['exitObservedElapsedNanos'],actualLifetimeNanos=trial['observedLifetimeNanos'],actualHoldNanos=trial['observedHoldNanos'],eligible=included,outcome=outcome,reason=row['reason'] if included else 'OUTSIDE_MEASUREMENT_WINDOW',parentLaunchToScanEndMs=latency,metadataQuality=row['metadataQuality']))
    with (directory/'resources.csv').open(encoding='utf-8',newline='') as source:
        samples=list(csv.DictReader(source))
    resources=resource_metrics(samples,low,high,metadata['resourceSamplePeriodMillis']*1_000_000)
    summary=[]
    for duration in (200,800,3000):
        group=[e for e in events if e['targetMillis']==duration and e['eligible']]
        detected=sum(e['outcome']=='OBSERVED' for e in group);missed=sum(e['outcome']=='NOT_OBSERVED_IN_TRACE' for e in group)
        unreadable=len(group)-detected-missed;latencies=[e['parentLaunchToScanEndMs'] for e in group if e['parentLaunchToScanEndMs'] is not None]
        summary.append(dict(runId=metadata['runId'],label=metadata['e1e2'],pollMs=metadata['config']['pollMillis'],targetMillis=duration,eligible=len(group),detected=detected,missed=missed,inconclusive=unreadable,censored=sum(e['targetMillis']==duration and not e['eligible'] for e in events),missRate=missed/len(group) if group and not unreadable else None,missRateLowerBound=missed/len(group) if group else None,missRateUpperBound=(missed+unreadable)/len(group) if group else None,p50ParentLatencyMs=percentile(latencies,.5),p95ParentLatencyMs=percentile(latencies,.95),**resources))
    return events,summary

def write_csv(path,rows):
    with path.open('x',newline='',encoding='utf-8') as output:
        writer=csv.DictWriter(output,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)

def campaign_quality(metadata_list):
    reasons=[]
    counts=collections.Counter(m['config']['pollMillis'] for m in metadata_list)
    if counts != {250:3,500:3,1000:3,2000:3}: reasons.append('REQUIRES_4_PERIODS_X_3_UNIQUE_RUNS')
    if any(m['measurementStartElapsedNanos']!=15_000_000_000 or m['measurementEndElapsedNanos']!=135_000_000_000
           or m['config']['perDuration']!=10 for m in metadata_list): reasons.append('NOT_FULL_WINDOW_OR_WORKLOAD')
    aliases=[m.get('machine') for m in metadata_list]
    if any(not isinstance(alias,str) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,80}',alias) or alias=='UNSPECIFIED' for alias in aliases):
        reasons.append('MACHINE_ALIAS_REQUIRED')
    signatures={json.dumps({k:m.get(k) for k in ('sourceSha','binarySha256','planSha256','policyVersion','javaVersion','javaVendor','machine','os','osVersion','architecture')},sort_keys=True)
                +json.dumps({k:v for k,v in m['config'].items() if k!='pollMillis'},sort_keys=True) for m in metadata_list}
    if len(signatures)!=1: reasons.append('MIXED_SOURCE_PLAN_CONFIG_OR_ENVIRONMENT')
    return reasons,counts

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root',type=pathlib.Path);parser.add_argument('out',type=pathlib.Path)
    parser.add_argument('--derived-joins',type=pathlib.Path);parser.add_argument('--join-tool',type=pathlib.Path)
    parser.add_argument('--provenance-script',type=pathlib.Path)
    args=parser.parse_args()
    if bool(args.derived_joins) != bool(args.join_tool) or args.provenance_script and not args.derived_joins:
        parser.error('--derived-joins and --join-tool must be selected together')
    root,out=args.root,args.out
    if args.derived_joins and (out.resolve().is_relative_to(root.resolve()) or out.resolve().is_relative_to(args.derived_joins.resolve())):
        raise ValueError('OUTPUT_INSIDE_INPUT')
    out.mkdir(parents=True,exist_ok=False)
    from derived import inputs,source_version,DIFF_FIELDS
    run_dirs=sorted(root.glob('run-*'))
    selection_error=args.derived_joins and (not args.derived_joins.is_dir() or
        {p.name for p in args.derived_joins.iterdir()}!={p.name for p in run_dirs})
    receipts=[];diffs=[]
    summary=[];all_events=[];inventory=[];seen_ids=set();metadata_list=[]
    for run in run_dirs:
        record=dict(run=run.name,rawInputs={})
        receipts.append(record)
        try:
            before=inputs(run)
            record['rawInputs']=before
            if selection_error: raise ValueError('DERIVED_RUN_SET')
            events,rows=analyze(run,args.derived_joins,args.join_tool,args.provenance_script)
            record['rawSourceSha']=json.loads((run/'metadata.json').read_text(encoding='utf-8')).get('sourceSha')
            if args.derived_joins:
                folder=args.derived_joins/run.name
                record.update(derivedJoinSha256=digest(folder/'join.json'),provenanceSha256=digest(folder/'provenance.json'))
                old={r['trialId']:r for r in json.loads((run/'join.json').read_text(encoding='utf-8'))['rows']}
                new={r['trialId']:r for r in json.loads((folder/'join.json').read_text(encoding='utf-8'))['rows']}
                for trial in sorted(old):
                    item=dict(run=run.name,trialId=trial,changed=any(old[trial].get(k)!=new[trial].get(k) for k in DIFF_FIELDS))
                    for key in DIFF_FIELDS:
                        item['old_'+key]=old[trial].get(key);item['new_'+key]=new[trial].get(key)
                    diffs.append(item)
            if inputs(run)!=before: raise ValueError('RAW_CHANGED_DURING_ANALYSIS')
            if rows[0]["runId"] in seen_ids: raise ValueError("DUPLICATE_RUN_ID")
            seen_ids.add(rows[0]["runId"])
            record['status']='VALID'
        except (ValueError,KeyError,TypeError,IndexError,OSError) as error:
            record['status']='INVALID'
            inventory.append(dict(run=run.name,status='INVALID',reason=type(error).__name__+': '+str(error)))
            continue
        metadata_list.append(json.loads((run/"metadata.json").read_text(encoding="utf-8")))
        inventory.append(dict(run=run.name,status='VALID',reason=rows[0]['resourceStatus']))
        write_csv(out/(run.name+'-events.csv'),events);summary.extend(rows);all_events.extend(events)
    # A first run must remain immutable while later runs/output are being processed too.
    for record,item in zip(receipts,inventory):
        if item['status']=='VALID':
            try:
                if inputs(root/record['run'])!=record['rawInputs']: raise ValueError('RAW_CHANGED_DURING_ANALYSIS')
            except (ValueError,OSError) as error:
                record['status']='INVALID';item.update(status='INVALID',reason=str(error))
    (out/'run-status.json').write_text(json.dumps(inventory,indent=2),encoding='utf-8')
    def receipt():
        tools=dict(analyzerSha256=digest(pathlib.Path(__file__)),analyzerSourceSha=source_version(__file__),
                   validationScriptSha256=digest(pathlib.Path(__file__).with_name('derived.py')),
                   provenanceScriptSha256=digest(args.provenance_script or pathlib.Path(__file__).with_name('derived.py'))
                       if (args.provenance_script or pathlib.Path(__file__).with_name('derived.py')).is_file() else None)
        if args.join_tool: tools['joinToolJarSha256']=digest(args.join_tool) if args.join_tool.is_file() else None
        value=dict(schemaVersion='e1-analysis-receipt-v1',joinMode='DERIVED' if args.derived_joins else 'ORIGINAL',
                   tools=tools,runs=receipts,outputs={p.name:digest(p) for p in sorted(out.iterdir()) if p.name!='analysis-receipt.json'})
        (out/'analysis-receipt.json').write_text(json.dumps(value,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    if diffs: write_csv(out/'join-differences.csv',diffs)
    receipt()
    if any(row['status']=='INVALID' for row in inventory):
        raise ValueError('INVALID_RUNS: see run-status.json; no campaign summary emitted')
    if not summary: raise ValueError('NO_RUNS; no synthetic data emitted')
    reasons,counts=campaign_quality(metadata_list)
    (out/'campaign-status.json').write_text(json.dumps(dict(status='PARTIAL' if reasons else 'CANDIDATE_REVIEW_REQUIRED',
            reasons=reasons,countsByPoll=dict(counts),humanReview='NOT_ASSERTED'),indent=2),encoding='utf-8')
    write_csv(out/'summary-by-duration.csv',summary)
    runs=[]
    for run_id in dict.fromkeys(row['runId'] for row in summary):
        group=[row for row in summary if row['runId']==run_id];first=group[0]
        item={key:first[key] for key in ('runId','label','pollMs','collectorCpuOneCoreMean','parentCpuOneCoreMean','parentHeapMeanBytes','parentHeapPeakBytes','resourceSamples','collectorCpuReadableSamples','parentCpuReadableSamples','resourceCoveredNanos','collectorCpuCoveredNanos','parentCpuCoveredNanos','resourceMaxGapNanos','resourceInvalidIntervals','resourceStatus')}
        for row in group:
            for key in ('eligible','detected','missed','inconclusive','censored','missRate','missRateLowerBound','missRateUpperBound','p50ParentLatencyMs','p95ParentLatencyMs'):item[f"{key}_{row['targetMillis']}ms"]=row[key]
        runs.append(item)
    write_csv(out/'summary.csv',runs)
    text=['# Phân tích E1','', 'Thiết kế chiến dịch: xem campaign-status.json; PARTIAL không phải chiến dịch E1 đầy đủ. Không tự suy human review từ số run.', '', 'Sinh từ raw/checksum; latency chỉ detected, parent launch API → scan-end (cận trên), không clock kernel. Memory=heap JVM parent; CPU một core, scope collector thread và parent process riêng.',
          '', 'Analyzer SHA256: `'+digest(pathlib.Path(__file__))+'`. Các bảng dùng trung bình và phương sai mẫu giữa các run; không dùng ba dòng duration của một run như ba lần lặp độc lập.',
          '', f"Dữ liệu: {len(runs)} run; {len(all_events)} trials; eligible={sum(e['eligible'] for e in all_events)}; censored={sum(not e['eligible'] for e in all_events)}; inconclusive={sum(e['outcome']=='INCONCLUSIVE' for e in all_events)}.",
          '', '| Poll ms | Collector CPU % một core: mean / variance | Parent CPU %: mean / variance | Heap mean MiB: mean / variance |',
          '|---|---|---|---|']
    for poll in sorted(set(row['pollMs'] for row in runs)):
        group=[row for row in runs if row['pollMs']==poll]
        text.append(f"| {poll} | {stats_text([row['collectorCpuOneCoreMean'] for row in group])} | {stats_text([row['parentCpuOneCoreMean'] for row in group])} | {stats_text([row['parentHeapMeanBytes']/1048576 if row['parentHeapMeanBytes'] is not None else None for row in group])} |")
    text+=['', 'CPU trong run = tổng delta CPU / tổng delta wall của khoảng đọc được, không trung bình các phần trăm. resourceStatus PARTIAL nếu coverage <95%, gap >2 chu kỳ lấy mẫu, counter thiếu/giảm hoặc heap thiếu. Boundary không nội suy. Heap mean là trung bình mẫu, peak là cực đại được lấy mẫu, không peak liên tục. Các giá trị PARTIAL giữ để chẩn đoán, không nghiệm thu CPU toàn cửa sổ.', '', 'Variance CPU có đơn vị %², heap có đơn vị MiB²; n là số run đọc được metric, None nghĩa thiếu dữ liệu/không đủ2run để tính variance. Peak heap và latency từng run ở summary.csv. Không dùng missed latency=0.', '', '| Target hold ms | Parent-observed lifetime ms: min / mean / max |', '|---|---|']
    for duration in (200,800,3000):
        values=[e['actualLifetimeNanos']/1e6 for e in all_events if e['targetMillis']==duration and e['eligible'] and e['actualLifetimeNanos'] is not None]
        text.append(f"| {duration} | {min(values) if values else None} / {statistics.mean(values) if values else None} / {max(values) if values else None} |")
    text+=['', 'Target hold khác lifetime quan sát ở parent (gồm startup/READY/scheduling); không gọi target200ms là process lifetime chính xác200ms.', '']
    for poll in sorted(set(row['pollMs'] for row in summary)):
        for duration in (200,800,3000):
            values=[row['missRate'] for row in summary if row['pollMs']==poll and row['targetMillis']==duration and row['missRate'] is not None]
            text.append(f'- Poll {poll}ms, target hold {duration}ms: n={len(values)}; mean miss={statistics.mean(values) if values else None}; variance={statistics.variance(values) if len(values)>1 else None}.')
    unknown=collections.Counter(e['reason'] for e in all_events if e['outcome']=='INCONCLUSIVE')
    text+=['', f"Mẫu chưa kết luận theo reason: {dict(unknown)}.", 'Các mean miss rate có n<3 ở trên chỉ mô tả các run có toàn bộ identity xác định trong bucket; không dùng chúng như mean đầy đủ3repeats hoặc lặng lẽ loại unknown. Bảng dưới giữ toàn bộ mẫu eligible, gồm unknown.', '', '| Poll ms | Target hold ms | Eligible / detected / missed / unknown | Miss range min..max |', '|---|---|---|---|']
    for poll in sorted(set(row['pollMs'] for row in summary)):
        for duration in (200,800,3000):
            group=[row for row in summary if row['pollMs']==poll and row['targetMillis']==duration]
            n=sum(row['eligible'] for row in group);detected=sum(row['detected'] for row in group);missed=sum(row['missed'] for row in group);inconclusive=sum(row['inconclusive'] for row in group)
            text.append(f"| {poll} | {duration} | {n} / {detected} / {missed} / {inconclusive} | {missed/n if n else None} .. {(missed+inconclusive)/n if n else None} |")
    text+=['', 'Miss range là bounds trên chính tập mẫu thu được: known missed/eligible đến (known missed+unknown)/eligible; không gán một kết quả cho unknown, không phải confidence interval thống kê hoặc phép suy thời điểm process mở. PID-only thiếu start vẫn INCONCLUSIVE theo oracle gốc, không nới identity để làm kết quả đẹp.']
    text+=['','SMOKE không là E1 final. E1 final cần đủ12run/3repeats, C review; chỉ đại diện máy/fixture/policy TEST này. Suite chạy tuần tự trong một parent JVM với collector/window mới mỗi run: heap/GC/JIT có thể carryover, các run không chứng minh độc lập hoàn toàn. Ứng dụng nền của workstation không bị đóng băng; rotated order giảm nhưng không loại mọi nhiễu. Không suy hành vi người dùng; không E2/delta/load.']
    (out/'analysis.md').write_text('\n'.join(text),encoding='utf-8')
    receipt()

if __name__=='__main__':main()
