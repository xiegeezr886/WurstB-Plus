"""Validate added OpenOpal mixins against cached, named Minecraft bytecode."""
import importlib.util
import json
import re
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects

spec=importlib.util.spec_from_file_location('mixin_audit',ROOT/'scripts/audit-mixin-injections.py')
audit=importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)
JAVAP='C:/Users/ui863/.jdks/jdk25.0.4_10/bin/javap.exe'


def check(project,info):
    jar=audit.Jar(info['jar'],JAVAP)
    failures=[]
    checks=0
    for path in (project/PACKAGE/'mixin').glob('OpenOpal*.java'):
        text=path.read_text(encoding='utf-8')
        fqcn=audit.resolve_target(text)
        names,pairs=jar.signatures(fqcn) or (set(),set())
        code=jar.bytecode(fqcn) or {}
        for item in audit.check_file(str(path),jar,str(project.relative_to(ROOT)),path.name):
            failures.append(item)
        for ann in audit.ANNOTATIONS:
            for body,line,end in audit.annotation_args(text,ann):
                methods=audit.string_values(body,'method')
                for meth in methods:
                    name=meth.split('(')[0]
                    selected=code if name=='*' else {name:code.get(name,[])}
                    body_text='\n'.join(line for lines in selected.values() for line in lines)
                    for at,_,_ in audit.annotation_args(body,'At'):
                        if 'INVOKE' not in at and 'FIELD' not in at:continue
                        for target in audit.string_values(at,'target'):
                            if not target.startswith('L') or ';' not in target:continue
                            owner,member=target[1:].split(';',1)
                            if '(' in member:
                                method,desc=member.split('(',1)
                                key=method+':('+desc
                            else:key=member
                            checks+=1
                            if owner+'.'+key not in body_text and not (owner.replace('/','.')==fqcn and key in body_text):
                                failures.append([str(project.relative_to(ROOT)),path.name,line,ann,meth,target,'reference absent from bytecode'])
        members=jar._run(['-p','-s'],fqcn).stdout
        for ann in ['Invoker','Accessor']:
            for body,line,end in audit.annotation_args(text,ann):
                target=re.search(r'"([^"]+)"',body).group(1)
                checks+=1
                if ann=='Invoker' and target not in names:
                    failures.append([path.name,line,ann,target,'method absent'])
                if ann=='Accessor' and not re.search(r'\b'+re.escape(target)+r';',members):
                    failures.append([path.name,line,ann,target,'field absent'])
    return {'project':str(project.relative_to(ROOT)),'checks':checks,'failures':failures}


def main():
    data=json.loads((PLAN/'mc-api.json').read_text(encoding='utf-8'))
    rows=[]
    for p in projects():
        if not p.relative_to(ROOT).as_posix().startswith('fabric'):continue
        props=dict(line.split('=',1) for line in (p/'gradle.properties').read_text(encoding='utf-8-sig').splitlines() if '=' in line)
        rows.append((p,data[props['minecraft_version']]))
    results=[]
    with ThreadPoolExecutor(max_workers=4) as pool:
        for future in as_completed([pool.submit(check,p,info) for p,info in rows]):
            row=future.result()
            results.append(row)
            print(row['project'],row['checks'],'checks',len(row['failures']),'failures',flush=True)
    (PLAN/'mixin-audit.json').write_text(json.dumps(results,indent=2,ensure_ascii=False),encoding='utf-8')
    raise SystemExit(any(r['failures'] for r in results))


if __name__=='__main__':main()
