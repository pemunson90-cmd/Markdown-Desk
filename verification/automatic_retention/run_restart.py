#!/usr/bin/env python3
"""Actual SIGKILL, bounded child waits and two independent fresh JVM reopens."""
import hashlib, pathlib, select, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'verification/build/automatic-retention'
JAVA=['java','-XX:-UsePerfData','-Xmx128m','-cp',str(OUT/'classes'),'com.example.markdowndesk.AutomaticRestartProbe']
def run(*args):
    return subprocess.run(JAVA+list(map(str,args)),check=True,capture_output=True,text=True,timeout=30).stdout.strip()
def inventory(root):
    return {str(p.relative_to(root)):(p.stat().st_ino,hashlib.sha256(p.read_bytes()).hexdigest()) for p in root.rglob('*') if p.is_file()}
def crash(root,event):
    child=subprocess.Popen(JAVA+['crash',str(root),event],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    try:
        ready,_,_=select.select([child.stdout],[],[],30)
        if not ready:raise AssertionError('no boundary: '+event)
        line=child.stdout.readline().strip()
        if line!='BOUNDARY '+event:
            child.kill();child.wait(timeout=10)
            raise AssertionError((event,line,child.stderr.read()))
        child.kill();child.wait(timeout=10)
        assert child.returncode==-9
    finally:
        if child.poll() is None:child.kill();child.wait(timeout=10)
        child.stdin.close();child.stdout.close();child.stderr.close()
events=['intent-'+e for e in ('file-before','file-after','move-before','move-after','dir-before','dir-after')]
events+=['member-'+str(i)+'-'+e for i in range(5) for e in ('before','after','dir-before','dir-after')]
events+=['retire-data-'+r+'-'+e for r in ('store','catalog','identity','index') for e in ('dir-before','dir-after')]
events+=['intent-unlink-before','intent-unlink-after','retire-dir-before','retire-dir-after']
with tempfile.TemporaryDirectory(prefix='automatic-restart-',dir=OUT/'tmp') as temp:
    for i,event in enumerate(events):
        root=pathlib.Path(temp)/str(i)/'bundle';root.parent.mkdir()
        lines=run('init',root).splitlines();removed={s[7:] for s in lines if s.startswith('REMOVE ')}
        reserve=next(s[8:] for s in lines if s.startswith('RESERVE '));assert len(removed)==5
        before=inventory(root);crash(root,event);interrupted=inventory(root)
        blocked='history_control/prune.intent.stage' in interrupted
        expected='BLOCKED' if blocked else 'READY '+reserve
        for _ in range(2):assert run('reopen',root)==expected,(event,expected)
        after=inventory(root)
        if blocked:
            assert after==interrupted,(event,'stage evidence changed')
            for key,value in before.items():assert after[key]==value,(event,'native unlink before commit')
        else:
            assert set(after)==set(before)-removed,(event,'extra paths deleted/created')
            for key,value in before.items():
                if key not in removed:assert after[key]==value,(event,'survivor bytes/inode changed',key)
print(f'PASS automatic retention SIGKILL: {len(events)} boundaries / {len(events)*2} fresh JVM reopens')
