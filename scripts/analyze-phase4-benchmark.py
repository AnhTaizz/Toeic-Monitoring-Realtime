#!/usr/bin/env python3
"""Summarize real Phase 4 raw CSV; never impute missing observations as zero."""
import argparse,csv,json,math,statistics
from pathlib import Path

def quantile(values,p):
    return sorted(values)[max(0,math.ceil(len(values)*p)-1)] if values else None

def analyze(root):
    root=Path(root)
    with (root/'raw.csv').open(newline='',encoding='utf-8-sig') as f: rows=list(csv.DictReader(f))
    with (root/'resources.csv').open(newline='',encoding='utf-8-sig') as f: resources=list(csv.DictReader(f))
    result=[]
    for n in (1,2,5,10,20):
        group=[r for r in rows if int(r['clients'])==n]
        res=[r for r in resources if int(r['clients'])==n]
        item={'clients':n,'rounds':sorted({int(r['round']) for r in res}),'http_count':0,'http_error_rate':None}
        for kind,op,label in [('http',None,'http'),('monitoring','event_delivery','event'),('ws','connect','ws_connect'),('presence','connected_detection','connected'),('presence','disconnected_detection','disconnected')]:
            selected=[r for r in group if r['kind']==kind and (op is None or r['operation']==op)]
            good=[float(r['elapsed_ms']) for r in selected if r['success']=='true']
            item[label+'_count']=len(selected);item[label+'_success']=len(good)
            item[label+'_p50_ms']=quantile(good,.5);item[label+'_p95_ms']=quantile(good,.95)
            if kind=='http' and selected:item['http_error_rate']=(len(selected)-len(good))/len(selected)
        for op in ('join','start','submit'):
            selected=[r for r in group if r['kind']=='http' and r['operation']==op]
            item[op]={'success':sum(r['success']=='true' for r in selected),'total':len(selected)}
        for process in ('server','generator'):
            cpu=[100*float(r[process+'_cpu_ms'])/float(r['elapsed_ms']) for r in res]
            ram=[int(r[process+'_working_set_bytes'])/1024/1024 for r in res]
            item[process+'_cpu_one_core_percent_mean']=statistics.mean(cpu) if cpu else None
            item[process+'_working_set_snapshot_mib_max']=max(ram) if ram else None
        item['complete']=item['rounds']==[1,2,3] and item['join']['total']==n*3 and item['submit']['total']==n*3 and item['start']['total']==3 and item['event_count']==n*9 and item['disconnected_count']==n*3 and all(r['success']=='true' for r in group)
        result.append(item)
    operation_rows=[]
    for n in (1,2,5,10,20):
        for op in sorted({r['operation'] for r in rows if r['kind']=='http' and int(r['clients'])==n}):
            selected=[r for r in rows if r['kind']=='http' and int(r['clients'])==n and r['operation']==op]
            good=[float(r['elapsed_ms']) for r in selected if r['success']=='true']
            operation_rows.append(dict(clients=n,operation=op,count=len(selected),success=len(good),p50_ms=quantile(good,.5),p95_ms=quantile(good,.95)))
    with (root/'operations.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=['clients','operation','count','success','p50_ms','p95_ms']);w.writeheader();w.writerows(operation_rows)
    report={'method':'nearest-rank p50/p95 over actual successful samples; failed samples retained in counts/raw; per-level pooled three repetitions; CPU 100%=one logical core; RAM endpoint working-set snapshots, not peak','network':'loopback on one physical host; simulated protocol candidates; not physical LAN or desktop client sizing','levels':result,'complete':all(x['complete'] for x in result)}
    (root/'summary.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    fields=['clients','http_count','http_p50_ms','http_p95_ms','event_p50_ms','event_p95_ms','ws_connect_p95_ms','connected_p95_ms','disconnected_p95_ms','http_error_rate','server_cpu_one_core_percent_mean','generator_cpu_one_core_percent_mean','server_working_set_snapshot_mib_max','generator_working_set_snapshot_mib_max','complete']
    with (root/'summary.csv').open('w',newline='',encoding='utf-8') as f:
        writer=csv.DictWriter(f,fieldnames=fields,extrasaction='ignore');writer.writeheader();writer.writerows(result)
    print(json.dumps(report,indent=2))
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('directory');args=parser.parse_args()
    raise SystemExit(0 if analyze(args.directory)['complete'] else 1)
