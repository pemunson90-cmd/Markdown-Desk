#!/usr/bin/env python3
"""Real SIGKILL at bounded pin callbacks; each fixture reopened in two fresh JVMs."""
import hashlib, os, pathlib, select, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'verification/build/history-pins'
JAVA=['java','-Xmx128m','-cp',str(OUT/'classes'),'com.example.markdowndesk.PinRestartProbe']
def run(*args):
    return subprocess.run(JAVA+list(map(str,args)),check=True,capture_output=True,text=True,timeout=30).stdout.strip()
def inventory(root):
    return {str(p.relative_to(root)):(p.stat().st_ino,hashlib.sha256(p.read_bytes()).hexdigest()) for p in root.rglob('*') if p.is_file()}
def crash(mode,root,event):
    child=subprocess.Popen(JAVA+[mode,str(root),event],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    try:
        ready,_,_=select.select([child.stdout],[],[],20)
        if not ready: raise AssertionError('no boundary: '+event)
        line=child.stdout.readline().strip()
        if line!='BOUNDARY '+event: raise AssertionError((line,child.stderr.read()))
        child.kill();child.wait(timeout=10)
        assert child.returncode==-9
    finally:
        if child.poll() is None: child.kill();child.wait()
cases=0;reopens=0
with tempfile.TemporaryDirectory(prefix='pin-restart-',dir=OUT/'tmp') as scratch:
    for closing in (False,True):
        for event in ('file-before','file-after','move-before','move-after','dir-before','dir-after','partial-0','partial-60','partial-125','unknown'):
            root=pathlib.Path(scratch)/str(cases);run('init',root)
            crash('crash-close' if closing else 'crash-begin',root,event)
            before=inventory(root);blocked=event.startswith('partial-') or event=='unknown'
            expected='blocked' if blocked else 'closed' if closing else 'active'
            for _ in range(2):
                assert run('reopen',root,expected)==expected.upper();reopens+=1
            after=inventory(root)
            if blocked: assert before==after,'blocked reconciliation changed retained evidence'
            else:
                for name,evidence in before.items():
                    promoted=name[:-6] if name.endswith('.stage') else name
                    assert after[promoted]==evidence,'reconciliation rewrote/replaced existing bytes'
            cases+=1
    root=pathlib.Path(scratch)/'closed-maintenance';maintenance=JAVA[:-1]+['com.example.markdowndesk.PinMaintenanceProbe']
    subprocess.run(maintenance+['init',str(root)],check=True,capture_output=True,text=True,timeout=30)
    before=inventory(root)
    for _ in range(2):
        result=subprocess.run(maintenance+['reopen',str(root)],check=True,capture_output=True,text=True,timeout=30)
        assert result.stdout.strip()=='CLOSED-MAINTENANCE';reopens+=1
    assert before==inventory(root),'audit-only maintenance reconciliation changed bytes'
print(f'History pin SIGKILL restart: {cases} boundaries / {reopens} fresh JVM reopens PASS')
