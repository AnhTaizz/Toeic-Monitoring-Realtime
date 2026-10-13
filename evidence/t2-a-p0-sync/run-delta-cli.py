from pathlib import Path
import subprocess,json,sys,hashlib
r=Path(__file__).parent
out=r/'delta-cli'
out.mkdir(exist_ok=False)
java='/mnt/e/Java/Jav/jdk-21/bin/java.exe'
base=[java,'-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-cp','E:/Project/Toeic-Monitoring-Realtime-a-p0/client/target/client-0.1.0-SNAPSHOT-all.jar','vn.edu.toeic.client.monitoring.trace.TraceCli']
def win(p): return 'E:'+str(p.resolve())[6:]
results=[]
def replay(name,source,args=(),expected=0):
    p=subprocess.run(base+['replay','--input',source,'--output',win(out/(name+'.json'))]+list(args))
    assert p.returncode==expected,(name,p.returncode,expected)
    results.append({'name':name,'exit':p.returncode,'expected':expected})
    print('CLI '+name+' exit='+str(p.returncode)+' expected='+str(expected),flush=True)
def compare(prefix):
    f=json.loads((out/(prefix+'-full.json')).read_text());d=json.loads((out/(prefix+'-delta.json')).read_text())
    assert f['status']==d['status']=='PASS'
    assert f['businessEvents']==d['businessEvents'] and f['deliveredEvents']==d['deliveredEvents']
    assert len(f['observationChecks'])==len(d['observationChecks'])
    for a,b in zip(f['observationChecks'],d['observationChecks']): assert a['actualState']['processes']==b['actualState']['processes']
    print('COMPARE '+prefix+' canonical process/metadata and event IDs/content PASS',flush=True)
fixture='client/src/test/resources/monitoring-traces/delta-handwritten-v1.jsonl'
replay('fixture-full',fixture);replay('fixture-delta',fixture,['--mode','delta']);compare('fixture')
replay('reconnect-full',fixture,['--duplicate','100','--reconnect-at','3']);replay('reconnect-delta',fixture,['--mode','delta','--duplicate','100','--reconnect-at','3']);compare('reconnect')
replay('negative-delta',fixture,['--mode','delta','--mutate-delta-at','2'],1);replay('negative-full',fixture,['--mutate-full-at','2'],1)
replay('unsupported-delta-faults',fixture,['--mode','delta','--drop','10'],2)
replay('legacy-full-faults','client/src/test/resources/monitoring-traces/handwritten-v1.jsonl',['--seed','1234','--duplicate','20','--drop','10','--reorder','35','--reconnect-at','3'])
replay('test-schema-refused','evidence/t2-c4/raw/observations.jsonl',['--mode','delta'],2)
p=subprocess.run(base+['record','--output',win(out/'real-processhandle.jsonl'),'--scans','6','--poll-ms','100']);assert p.returncode==0
replay('real-full',win(out/'real-processhandle.jsonl'));replay('real-delta',win(out/'real-processhandle.jsonl'),['--mode','delta']);compare('real')
(out/'summary.json').write_text(json.dumps({'cases':results,'realScan':True,'networkAck':'SIMULATED','productionDelta':'DISABLED'},indent=2)+'\n')
print('T3-C1 CLI PASS; REAL ProcessHandle scan; replay ACK/network SIMULATED; production delta DISABLED',flush=True)
