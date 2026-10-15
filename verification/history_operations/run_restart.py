#!/usr/bin/env python3
"""Actual child SIGKILL and RLIMIT_FSIZE; each recovered fixture has two fresh reopens."""
import hashlib, pathlib, resource, select, signal, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'verification/build/history-operations'
JAVA=['java','-Xmx128m','-cp',str(OUT/'classes'),'com.example.markdowndesk.OperationRestartProbe']
def run(*args,**kwargs):
    return subprocess.run(JAVA+list(map(str,args)),check=True,capture_output=True,text=True,timeout=40,**kwargs).stdout.strip()
def inventory(root):
    return {str(p.relative_to(root)):(p.stat().st_ino,p.read_bytes()) for p in root.rglob('*') if p.is_file()}
def kill(root,event):
    child=subprocess.Popen(JAVA+['recover',str(root),event],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    try:
        ready,_,_=select.select([child.stdout],[],[],25)
        if not ready: raise AssertionError('missing boundary '+event)
        line=child.stdout.readline().strip()
        if line!='BOUNDARY '+event: raise AssertionError((event,line,child.stderr.read()))
        child.kill();child.wait(timeout=10);assert child.returncode==-9
    finally:
        if child.poll() is None:child.kill();child.wait()
def reopen(root,before):
    for _ in range(2):assert run('reopen',root)=='CLOSED'
    after=inventory(root)
    assert len(after)==len(before),'extra file or unlink'
    for name,(inode,data) in before.items():
        target=name[:-6] if name.endswith('.close.stage') else name
        assert target in after and after[target][0]==inode,'inode or member changed'
        assert after[target][1].startswith(data) if name.endswith('.close.stage') else after[target][1]==data,'non-prefix evidence changed'
        if name.endswith('.close.stage'):assert len(after[target][1])==126
cases=0
with tempfile.TemporaryDirectory(prefix='operations-',dir=OUT/'tmp') as temp:
    for n,outcome in ((0,'CANCELLED'),(93,'CANCELLED'),(94,'RELEASED'),(125,'RELEASED'),(126,'RELEASED')):
        events=['read-only-file-before','read-only-file-after','move-before','move-after','close-dir-before','close-dir-after']
        if n<126:events+=['BEFORE_APPEND','AFTER_APPEND','AFTER_FORCE']
        for event in events:
            root=pathlib.Path(temp)/str(cases);run('init',root,n,outcome);before=inventory(root);kill(root,event);reopen(root,before);cases+=1
    for n,limit,outcome in ((0,1,'CANCELLED'),(0,93,'CANCELLED'),(0,94,'CANCELLED'),(94,110,'RELEASED'),(94,125,'RELEASED')):
        root=pathlib.Path(temp)/str(cases);run('init',root,n,outcome);before=inventory(root)
        def restrict():
            signal.signal(signal.SIGXFSZ,signal.SIG_IGN);resource.setrlimit(resource.RLIMIT_FSIZE,(limit,limit))
        assert run('limit',root,preexec_fn=restrict)=='BLOCKED'
        stage=next((root/'history_control').glob('*.close.stage'));assert stage.stat().st_size==limit,'real OS prefix boundary not exercised'
        reopen(root,before);cases+=1
    root=pathlib.Path(temp)/'lost-manifest';run('init',root,94,'RELEASED');(root/'original-manifest').unlink();before=inventory(root)
    for _ in range(2):assert run('blocked',root)=='BLOCKED'
    assert before==inventory(root);cases+=1
print(f'Operation recovery: {cases} SIGKILL/OS-limit/manifest fixtures / {cases*2} fresh reopens PASS')
