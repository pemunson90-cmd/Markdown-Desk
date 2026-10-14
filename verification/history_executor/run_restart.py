#!/usr/bin/env python3
"""Real SIGKILL plus OS RLIMIT_FSIZE; two independent JVM reopens per fixture."""
import hashlib, pathlib, resource, select, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'verification/build/history-executor'
JAVA=['java','-XX:-UsePerfData','-Xmx128m','-cp',str(OUT/'classes'),'com.example.markdowndesk.ExecutorRestartProbe']
def run(*args):
    return subprocess.run(JAVA+list(map(str,args)),check=True,capture_output=True,text=True,timeout=45).stdout.strip()
def inventory(root):
    return {str(p.relative_to(root)):(p.stat().st_ino,hashlib.sha256(p.read_bytes()).hexdigest()) for p in root.rglob('*') if p.is_file()}
def crash(root,event):
    child=subprocess.Popen(JAVA+['crash',str(root),event],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    try:
        ready,_,_=select.select([child.stdout],[],[],30)
        if not ready: raise AssertionError('no boundary: '+event)
        line=child.stdout.readline().strip()
        if line!='BOUNDARY '+event: raise AssertionError((event,line,child.stderr.read()))
        child.kill();child.wait(timeout=10)
        assert child.returncode==-9
    finally:
        if child.poll() is None: child.kill();child.wait()
        child.stdin.close();child.stdout.close();child.stderr.close()
events=['intent-'+event for event in ('file-before','file-after','move-before','move-after','dir-before','dir-after')]
for kind in ('checkpoint','index','complete','transaction','snapshot'):
    events += ['member-'+event+'-'+kind for event in ('before','after','dir-before','dir-after')]
events += ['retire-data-'+event+'-'+role for role in ('store','catalog','identity','index') for event in ('dir-before','dir-after')]
events += ['intent-unlink-before','intent-unlink-after','retire-dir-before','retire-dir-after']
cases=0;reopens=0
with tempfile.TemporaryDirectory(prefix='executor-restart-',dir=OUT/'tmp') as scratch:
    for event in events+['os-write-limit']:
        root=pathlib.Path(scratch)/str(cases)/'bundle';root.parent.mkdir()
        description=run('init',root).splitlines();removed={line[7:] for line in description if line.startswith('REMOVE ')}
        reserve=next(line[8:] for line in description if line.startswith('RESERVE '));before=inventory(root)
        if event=='os-write-limit':
            def limit():resource.setrlimit(resource.RLIMIT_FSIZE,(512,512))
            child=subprocess.run(JAVA+['crash',str(root),'unreachable'],preexec_fn=limit,capture_output=True,text=True,timeout=30)
            assert child.returncode!=0,'OS write limit unexpectedly accepted publication'
            assert (root/'history_control/prune.intent.stage').exists(),'missing failed publication evidence'
            assert (root/'history_control/prune.intent.stage').stat().st_size<=512
        else:crash(root,event)
        interrupted=inventory(root);blocked='history_control/prune.intent.stage' in interrupted
        expected='BLOCKED' if blocked else 'READY '+reserve
        for _ in range(2):
            assert run('reopen',root)==expected,(event,expected);reopens+=1
        after=inventory(root)
        if blocked:
            assert interrupted==after,(event,'blocked replay changed evidence')
            for name,value in before.items():assert after[name]==value,(event,name,'native unlink before committed intent')
        else:
            assert set(after)==set(before)-removed,(event,'unexpected published/deleted paths')
            for name,value in before.items():
                if name not in removed:assert after[name]==value,(event,name,'survivor bytes/inode changed')
        cases+=1
    for fault in ('member','survivor','policy','unknown','stage'):
        root=pathlib.Path(scratch)/('invalid-'+fault)/'bundle';root.parent.mkdir();description=run('init',root).splitlines();removed={line[7:] for line in description if line.startswith('REMOVE ')}
        before=inventory(root);crash(root,'intent-dir-after')
        if fault=='unknown':(root/'store/unknown').write_bytes(b'unknown')
        elif fault=='stage':(root/'history_control/prune.intent.stage').write_bytes(b'partial')
        else:
            name=next(iter(removed)) if fault=='member' else next(name for name in before if ((name.startswith('store/') and name.endswith('.snapshot') and name not in removed) if fault=='survivor' else name=='history_control/registration.control'))
            path=root/name;data=bytearray(path.read_bytes());data[-1]^=1;path.write_bytes(data)
        retained=inventory(root)
        for _ in range(2):assert run('blocked',root)=='BLOCKED';reopens+=1
        assert retained==inventory(root),(fault,'invalid replay mutated evidence');cases+=1
print(f'Journaled executor SIGKILL/OS limit: {cases} fixtures / {reopens} fresh JVM reopens PASS')
