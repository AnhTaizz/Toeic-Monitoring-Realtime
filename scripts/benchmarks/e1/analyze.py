"""Phân tích raw E1 bất biến, stdlib Python. Latency parent launch -> scan-end là cận trên, không kernel time."""
import csv, hashlib, json, math, pathlib, statistics, sys, collections

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

def analyze(directory):
    directory=pathlib.Path(directory)
    manifest=json.loads((directory/'manifest.json').read_text())
    if manifest['status']!='COMPLETE': raise ValueError('INCOMPLETE run retained')
    for name, expected in manifest['files'].items():
        if digest(directory/name)!=expected: raise ValueError('RAW_CHECKSUM')
    extra=json.loads((directory/'e1-manifest.json').read_text())
    if digest(directory/'resources.csv')!=extra['resourcesSha256'] or digest(directory/'manifest.json')!=extra['runManifestSha256']: raise ValueError('RESOURCE_CHECKSUM')
    metadata=json.loads((directory/'metadata.json').read_text())
    low,high=metadata['measurementStartElapsedNanos'],metadata['measurementEndElapsedNanos']
    truth=[json.loads(x) for x in (directory/'ground-truth.jsonl').read_text().splitlines()]
    rows={row['trialId']:row for row in json.loads((directory/'join.json').read_text())['rows']}
    events=[]
    for trial in truth[1:-1]:
        row=rows[trial['planned']['trialId']]
        included=eligible(trial['launchBeginElapsedNanos'],trial['exitObservedElapsedNanos'],low,high)
        outcome=row['conclusion'] if included else 'CENSORED'
        latency=(row['firstScanEndElapsedNanos']-trial['launchBeginElapsedNanos'])/1e6 if included and outcome=='OBSERVED' else None
        events.append(dict(trialId=row['trialId'],targetMillis=row['targetMillis'],launchNanos=trial['launchBeginElapsedNanos'],exitNanos=trial['exitObservedElapsedNanos'],actualLifetimeNanos=trial['observedLifetimeNanos'],actualHoldNanos=trial['observedHoldNanos'],eligible=included,outcome=outcome,reason=row['reason'] if included else 'OUTSIDE_MEASUREMENT_WINDOW',parentLaunchToScanEndMs=latency,metadataQuality=row['metadataQuality']))
    samples=list(csv.DictReader((directory/'resources.csv').open()))
    cpu=[];process=[];heap=[]
    for previous,current in zip(samples,samples[1:]):
        a,b=int(previous['elapsedNanos']),int(current['elapsedNanos'])
        if not eligible(a,b,low,high):continue
        for field,out in [('collectorThreadCpuNanos',cpu),('parentProcessCpuNanos',process)]:
            before,after=int(previous[field]),int(current[field])
            value=cpu_percent(after-before,b-a) if min(before,after)>=0 else None
            if value is not None:out.append(value)
        heap.append(int(current['parentHeapUsedBytes']))
    summary=[]
    for duration in (200,800,3000):
        group=[e for e in events if e['targetMillis']==duration and e['eligible']]
        detected=sum(e['outcome']=='OBSERVED' for e in group);missed=sum(e['outcome']=='NOT_OBSERVED_IN_TRACE' for e in group)
        unreadable=len(group)-detected-missed;latencies=[e['parentLaunchToScanEndMs'] for e in group if e['parentLaunchToScanEndMs'] is not None]
        summary.append(dict(runId=metadata['runId'],label=metadata['e1e2'],pollMs=metadata['config']['pollMillis'],targetMillis=duration,eligible=len(group),detected=detected,missed=missed,inconclusive=unreadable,censored=sum(e['targetMillis']==duration and not e['eligible'] for e in events),missRate=missed/len(group) if group and not unreadable else None,missRateLowerBound=missed/len(group) if group else None,missRateUpperBound=(missed+unreadable)/len(group) if group else None,p50ParentLatencyMs=percentile(latencies,.5),p95ParentLatencyMs=percentile(latencies,.95),collectorCpuOneCoreMean=statistics.mean(cpu) if cpu else None,parentCpuOneCoreMean=statistics.mean(process) if process else None,parentHeapMeanBytes=statistics.mean(heap) if heap else None,parentHeapPeakBytes=max(heap) if heap else None,resourceSamples=len(heap),collectorCpuReadableSamples=len(cpu)))
    return events,summary

def write_csv(path,rows):
    with path.open('x',newline='',encoding='utf-8') as output:
        writer=csv.DictWriter(output,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)

def main():
    root=pathlib.Path(sys.argv[1]);out=pathlib.Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=False)
    summary=[];all_events=[]
    for run in sorted(root.glob('run-*')):
        events,rows=analyze(run);write_csv(out/(run.name+'-events.csv'),events);summary.extend(rows);all_events.extend(events)
    if not summary: raise ValueError('NO_RUNS; no synthetic data emitted')
    write_csv(out/'summary-by-duration.csv',summary)
    runs=[]
    for run_id in dict.fromkeys(row['runId'] for row in summary):
        group=[row for row in summary if row['runId']==run_id];first=group[0]
        item={key:first[key] for key in ('runId','label','pollMs','collectorCpuOneCoreMean','parentCpuOneCoreMean','parentHeapMeanBytes','parentHeapPeakBytes','resourceSamples','collectorCpuReadableSamples')}
        for row in group:
            for key in ('eligible','detected','missed','inconclusive','censored','missRate','missRateLowerBound','missRateUpperBound','p50ParentLatencyMs','p95ParentLatencyMs'):item[f"{key}_{row['targetMillis']}ms"]=row[key]
        runs.append(item)
    write_csv(out/'summary.csv',runs)
    text=['# Phân tích E1','', 'Sinh từ raw/checksum; latency chỉ detected, parent launch API → scan-end (cận trên), không clock kernel. Memory=heap JVM parent; CPU một core, scope collector thread và parent process riêng.',
          '', 'Analyzer SHA256: `'+digest(pathlib.Path(__file__))+'`. Các bảng dùng trung bình và phương sai mẫu giữa các run; không dùng ba dòng duration của một run như ba lần lặp độc lập.',
          '', f"Dữ liệu: {len(runs)} run; {len(all_events)} trials; eligible={sum(e['eligible'] for e in all_events)}; censored={sum(not e['eligible'] for e in all_events)}; inconclusive={sum(e['outcome']=='INCONCLUSIVE' for e in all_events)}.",
          '', '| Poll ms | Collector CPU % một core: mean / variance | Parent CPU %: mean / variance | Heap mean MiB: mean / variance |',
          '|---|---|---|---|']
    for poll in sorted(set(row['pollMs'] for row in runs)):
        group=[row for row in runs if row['pollMs']==poll]
        text.append(f"| {poll} | {stats_text([row['collectorCpuOneCoreMean'] for row in group])} | {stats_text([row['parentCpuOneCoreMean'] for row in group])} | {stats_text([row['parentHeapMeanBytes']/1048576 if row['parentHeapMeanBytes'] is not None else None for row in group])} |")
    text+=['', 'Variance CPU có đơn vị %², heap có đơn vị MiB²; n là số run đọc được metric, None nghĩa thiếu dữ liệu/không đủ2run để tính variance. Peak heap và latency từng run ở summary.csv. Không dùng missed latency=0.', '', '| Target hold ms | Parent-observed lifetime ms: min / mean / max |', '|---|---|']
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
    if {row['pollMs'] for row in runs} == {250,500,1000,2000}:
        fast=[row for row in runs if row['pollMs']==250];slow=[row for row in runs if row['pollMs']==2000]
        _,fast_cpu,_=mean_variance([row['collectorCpuOneCoreMean'] for row in fast]);_,slow_cpu,_=mean_variance([row['collectorCpuOneCoreMean'] for row in slow])
        _,fast_miss,_=mean_variance([row['missRate_200ms'] for row in fast]);_,slow_miss,_=mean_variance([row['missRate_200ms'] for row in slow])
        text+=['', f"Đối chiếu hai đầu dải đo: collector CPU trung bình250ms={fast_cpu}% một core,2000ms={slow_cpu}%; miss rate targethold200ms tương ứng={fast_miss} và {slow_miss} (tỷ lệ0..1)."]
        if None not in (fast_cpu,slow_cpu,fast_miss,slow_miss):
            if fast_cpu>slow_cpu and fast_miss<slow_miss:text.append('Trong bộ dữ liệu này, polling nhanh giảm bỏ lỡ nhóm targethold200ms nhưng tăng chi phí CPU. Đây là đánh đổi đo được, không chứng minh mọi process/máy đều được lợi hoặc latency kernel chính xác.')
            else:text.append('Bộ dữ liệu không cho quan hệ giảm-miss/tăng-CPU đồng thời rõ ở hai đầu dải; giữ nguyên kết quả, không kết luận polling nhanh luôn tốt hơn.')
    text+=['','SMOKE không là E1 final. E1 final cần đủ12run/3repeats, C review; chỉ đại diện máy/fixture/policy TEST này. Suite chạy tuần tự trong một parent JVM với collector/window mới mỗi run: heap/GC/JIT có thể carryover, các run không chứng minh độc lập hoàn toàn. Ứng dụng nền của workstation không bị đóng băng; rotated order giảm nhưng không loại mọi nhiễu. Không suy hành vi người dùng; không E2/delta/load.']
    (out/'analysis.md').write_text('\n'.join(text),encoding='utf-8')

if __name__=='__main__':main()
