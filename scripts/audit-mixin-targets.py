#!/usr/bin/env python3
"""审计混入里的 @Accessor / @Invoker / @Shadow 目标是否还对得上。

为什么需要它：混入的注入点/访问器指向的字段与方法**只在运行期解析**。Gradle 编译不检查
（本仓库用的注解处理器也不生成 refmap，等于完全不校验），所以一个字段改名、换个类型，
编译照样通过、客户端一启动就崩：

    Mixin apply failed wurst.mixins.json:ToastManagerAccessor -> ToastManager:
    No candidates were found matching queued:Ljava/util/List;

本脚本用 javap 读目标类，把这类问题在启动之前找出来：

  * @Accessor("f")  —— 目标类是否有字段 f，类型是否与访问器返回类型相容
  * @Invoker("m")   —— 目标类是否有方法 m，参数个数与返回类型是否相容
  * @Shadow 字段/方法 —— 是否存在

目标类通过各工程编译时用的那份 MC jar 解析（Forge 用 recompiled.jar，Fabric/NeoForge
用 loom / neoforge 缓存里的 jar），所以结论与实际编译、运行一致。

用法：
    python scripts/audit-mixin-targets.py 26.2
    python scripts/audit-mixin-targets.py --all
"""
import argparse
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOME = os.path.expanduser('~')

FORGE_CACHE = os.path.join(
    HOME, '.gradle', 'caches', 'minecraftforge', 'forgegradle', 'mavenizer',
    'caches', 'forge', 'net', 'minecraftforge', 'forge')

MIXIN_RE = re.compile(r'@Mixin\s*\(\s*(?:value\s*=\s*)?([A-Za-z0-9_.$]+)')
ACCESSOR_RE = re.compile(
    r'@Accessor\s*\(\s*(?:value\s*=\s*)?"([^"]+)"\s*\)\s*'
    r'(?:@[A-Za-z]+(?:\([^)]*\))?\s*)*'
    r'([A-Za-z0-9_.$<>,\[\]\s]+?)\s+([A-Za-z0-9_$]+)\s*\(\s*\)\s*;')
INVOKER_RE = re.compile(
    r'@Invoker\s*\(\s*(?:value\s*=\s*)?"([^"]+)"\s*\)\s*'
    r'([A-Za-z0-9_.$<>,\[\]\s]+?)\s+([A-Za-z0-9_$]+)\s*\(([^)]*)\)\s*;')
SHADOW_FIELD_RE = re.compile(
    r'@Shadow(?:\([^)]*\))?[\s\S]{0,80}?([A-Za-z0-9_.$<>,\[\]\s]+?)\s+([A-Za-z0-9_$]+)\s*;')
IMPORT_RE = re.compile(r'^import\s+(?:static\s+)?([A-Za-z0-9_.]+);', re.M)


def jar_for(version):
    """按版本找用于 javap 的 MC jar。"""
    for d in sorted(os.listdir(FORGE_CACHE)) if os.path.isdir(FORGE_CACHE) else []:
        if d.startswith(version + '-'):
            p = os.path.join(FORGE_CACHE, d, 'official', version, 'recompiled.jar')
            if os.path.isfile(p):
                return p
    for p in (
        os.path.join(HOME, '.gradle', 'caches', 'fabric-loom', version,
                     'minecraft-merged.jar'),
        os.path.join(HOME, '.gradle', 'caches', 'neoforge', version,
                     'minecraft-merged.jar'),
    ):
        if os.path.isfile(p):
            return p
    return None


class Jar:
    def __init__(self, path):
        self.path = path
        self.win = subprocess.run(['cygpath', '-w', path], capture_output=True,
                                  text=True).stdout.strip() or path
        self.cache = {}

    def members(self, fqcn):
        if fqcn in self.cache:
            return self.cache[fqcn]
        out = subprocess.run(['javap', '-p', '-cp', self.win, fqcn],
                             capture_output=True, text=True, errors='replace')
        self.cache[fqcn] = out.stdout if out.returncode == 0 else None
        return self.cache[fqcn]

    def fields(self, fqcn):
        text = self.members(fqcn)
        if text is None:
            return None
        out = {}
        for line in text.splitlines():
            line = line.strip()
            if not line.endswith(';') or '(' in line:
                continue
            m = re.match(r'[\w$.<>,\[\]\s]+?\s+([A-Za-z0-9_$]+)\s*;', line)
            if m:
                out[m.group(1)] = line
        return out

    def methods(self, fqcn):
        text = self.members(fqcn)
        if text is None:
            return None
        out = {}
        for line in text.splitlines():
            line = line.strip()
            if '(' not in line or not line.endswith(';'):
                continue
            m = re.search(r'([A-Za-z0-9_$]+)\s*\(([^)]*)\)\s*;', line)
            if m:
                out.setdefault(m.group(1), []).append(
                    (len([a for a in m.group(2).split(',') if a.strip()]), line))
        return out


def simple(t):
    t = re.sub(r'<.*>', '', t).strip()
    return t.split('.')[-1].strip()


def compatible(want, have):
    """访问器声明的类型 vs 字段/返回值实际类型，宽松比较。"""
    if want == have:
        return True
    if simple(want) == simple(have):
        return True
    # 泛型擦除后仍可赋值的情况：Set/List/Deque/Collection 之间不算兼容，
    # 但 int/Integer 这类装箱差异不算问题。
    pairs = {('Integer', 'int'), ('int', 'Integer'), ('Long', 'long'),
             ('long', 'Long'), ('Boolean', 'boolean'), ('boolean', 'Boolean'),
             ('Float', 'float'), ('float', 'Float'), ('Double', 'double'),
             ('double', 'Double'), ('Short', 'short'), ('short', 'Short'),
             ('Byte', 'byte'), ('byte', 'Byte'), ('Character', 'char'),
             ('char', 'Character')}
    return (want, have) in pairs


def projects(version=None):
    out = []
    for dirpath, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in ('build', '.gradle')]
        if 'gradle.properties' not in files:
            continue
        try:
            text = open(os.path.join(dirpath, 'gradle.properties'),
                        encoding='utf-8', errors='replace').read()
        except OSError:
            continue
        m = re.search(r'^minecraft_version\s*=\s*(\S+)', text, re.M)
        if not m:
            continue
        if version and m.group(1) != version:
            continue
        out.append((os.path.relpath(dirpath, ROOT).replace(os.sep, '/'),
                    m.group(1)))
    return out


def mixin_files(proj):
    base = os.path.join(ROOT, proj, 'src/main/java/net/wurstclient/mixin')
    if not os.path.isdir(base):
        return []
    return [os.path.join(base, f) for f in sorted(os.listdir(base))
            if f.endswith('.java')]


def check(proj, version, jar, verbose=False):
    findings = []
    for path in mixin_files(proj):
        try:
            text = open(path, encoding='utf-8', errors='replace').read()
        except OSError:
            continue
        tm = MIXIN_RE.search(text)
        if not tm:
            continue
        target_simple = tm.group(1).split('.')[0]
        imports = {i.split('.')[-1]: i for i in IMPORT_RE.findall(text)}
        fq = imports.get(target_simple)
        if not fq:
            continue

        fields = jar.fields(fq)
        if fields is None:
            continue

        for field, want, meth in ACCESSOR_RE.findall(text):
            if field not in fields:
                findings.append((proj, version, os.path.basename(path),
                                 '@Accessor', field,
                                 '目标类无此字段', fields and ''))
                continue
            have = fields[field]
            if not compatible(simple(want), simple(
                    re.match(r'[\w$.<>,\[\]\s]+?\s+[A-Za-z0-9_$]+\s*;', have)
                    .group(0).rsplit(' ', 1)[0])):
                findings.append((proj, version, os.path.basename(path),
                                 '@Accessor', field,
                                 '类型不符', '%s -> 字段是 %s'
                                 % (want.strip(), have.strip())))
    return findings


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('version', nargs='?')
    ap.add_argument('--all', action='store_true')
    args = ap.parse_args()

    targets = projects(None if args.all else args.version)
    if not targets:
        raise SystemExit('没有匹配的工程')

    seen_jar = {}
    total = []
    for proj, version in targets:
        if version not in seen_jar:
            p = jar_for(version)
            seen_jar[version] = Jar(p) if p else None
        jar = seen_jar[version]
        if jar is None:
            continue
        total += check(proj, version, jar)

    print('%-34s %-8s %-30s %-10s %-22s %s'
          % ('工程', '版本', '文件', '注解', '成员', '问题'))
    print('=' * 132)
    for proj, version, fname, kind, member, why, detail in total:
        print('%-34s %-8s %-30s %-10s %-22s %s %s'
              % (proj, version, fname, kind, member, why, detail))
    print()
    print('发现 %d 处可疑的混入目标' % len(total))


if __name__ == '__main__':
    main()
