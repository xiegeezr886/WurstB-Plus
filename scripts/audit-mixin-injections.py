#!/usr/bin/env python3
"""审计混入的 @Inject / @WrapOperation / @Redirect 注入点是否还对得上。

为什么需要它：本仓库的注解处理器不生成 refmap，混入的 `method = "..."` 与
`@At(target = "...")` 只在**运行期**解析。编译能过，客户端一启动就崩：

    InvalidInjectionException: Critical injection failure: @WrapOperation annotation
    on onBobView could not find any targets matching
    'renderLevel(Lnet/minecraft/client/DeltaTracker;)V' in GameRenderer.

scripts/audit-mixin-targets.py 只查 @Accessor 字段，查不到这一类。本脚本补上：

  * `method = "name(desc)ret"` —— 目标类里是否真有这个方法（按描述符比对）
  * `method = "name"`        —— 目标类里是否真有同名方法
  * `@At(value = "INVOKE", target = "Lowner;name(desc)ret")` —— 该调用是否真的
    出现在上面那个目标方法的字节码里（26.3 的 Mth.lerp 就是「方法还在、但已经
    不在 renderLevel 里被调用了」，只比对方法是否存在是查不出来的）
  * `method = "/name"`、`"name*"` 之类的 Mixin 通配写法会被跳过并标注

目标类一律用编译/运行期同一份 MC jar 解析（默认 fabric-loom 的
minecraft-merged-deobf-<版本>.jar，可用 --jar 覆盖）。

`require = 0` 的注入即使失配也不致命，结果里会标注 [require=0]。

用法：
    python scripts/audit-mixin-injections.py 26.3
    python scripts/audit-mixin-injections.py 26.3 --tree neoforge
    python scripts/audit-mixin-injections.py 26.3 --jar /path/to/minecraft.jar
"""
import argparse
import glob
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOME = os.path.expanduser('~')

TREES = {'forge': 'versions', 'fabric': 'fabric/versions',
         'neoforge': 'neoforge/versions'}

ANNOTATIONS = ('Inject', 'WrapOperation', 'Redirect', 'WrapWithCondition',
               'ModifyVariable', 'ModifyArg', 'ModifyArgs', 'ModifyConstant',
               'ModifyExpressionValue', 'ModifyReturnValue', 'ModifyReceiver',
               'Overwrite')

MIXIN_RE = re.compile(r'@Mixin\s*\(\s*(?:value\s*=\s*)?([A-Za-z0-9_.$]+)')
MIXIN_TARGETS_RE = re.compile(r'@Mixin\s*\([^)]*targets\s*=\s*"([^"]+)"')
ANNOT_RE = re.compile(r'@(' + '|'.join(ANNOTATIONS) + r')\b')
IMPORT_RE = re.compile(r'^import\s+(?:static\s+)?([A-Za-z0-9_.]+);', re.M)
MEMBER_REF_RE = re.compile(
    r'//\s+(?:Interface)?Method\s+([\w/$]+)\.([\w$<>]+):(\S+)')
FIELD_REF_RE = re.compile(r'//\s+Field\s+([\w/$]+)\.([\w$]+):(\S+)')


def load_mixin_configs(proj_dir):
    """读取资源目录下所有 *.mixins.json，返回被注册的类名集合。

    不在配置里的混入源文件在运行期根本不会被应用（jar 清单的 MixinConfigs
    只列了这几个 json），所以不该报进来。
    """
    import glob
    import json
    registered = set()
    for path in glob.glob(os.path.join(proj_dir, 'src/main/resources',
                                       '*.mixins.json')):
        try:
            data = json.load(open(path, encoding='utf-8'))
        except (OSError, ValueError):
            continue
        for key in ('mixins', 'client', 'server'):
            for name in data.get(key, []):
                registered.add(name)
    return registered


def balanced(text, start):
    """从 text[start] == '(' 起，返回配对括号内的内容与结束位置。"""
    depth = 0
    i = start
    while i < len(text):
        c = text[i]
        if c == '"':  # 跳过字符串字面量
            i += 1
            while i < len(text) and text[i] != '"':
                i += 2 if text[i] == '\\' else 1
        elif c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return text[start + 1:i], i
        i += 1
    return text[start + 1:], len(text)


def annotation_args(text, name):
    """产出所有 @<name>(...) 的 `(括号内文本, 行号, 右括号后偏移)`。

    第三个元素给 handler 签名检查用：处理方法声明就紧跟在这个位置后面。
    """
    out = []
    for m in re.finditer(r'@' + name + r'\b', text):
        j = m.end()
        while j < len(text) and text[j] in ' \t\r\n':
            j += 1
        if j < len(text) and text[j] == '(':
            body, end = balanced(text, j)
            out.append((body, text.count('\n', 0, m.start()) + 1, end + 1))
    return out


def split_params(text):
    """按顶层逗号切开参数列表。"""
    text = text.strip()
    if not text:
        return []
    out, depth, cur = [], 0, ''
    for c in text:
        if c in '<([':
            depth += 1
        elif c in '>)]':
            depth -= 1
        if c == ',' and depth == 0:
            out.append(cur)
            cur = ''
        else:
            cur += c
    out.append(cur)
    return [p.strip() for p in out]


def simple_type(param):
    """把 `java.util.List<Foo> bar` 归一成 `List`；无法可靠判断时返回 None。"""
    p = re.sub(r'@[\w$.]+(?:\s*\([^)]*\))?\s*', '', param.strip())
    p = re.sub(r'\bfinal\s+', '', p)
    parts = p.split()
    if len(parts) >= 2:
        p = ' '.join(parts[:-1])  # 去掉参数名
    p = re.sub(r'<.*?>', '', p)
    p = p.replace('...', '').replace('[]', '').strip()
    p = p.split('.')[-1].strip()
    # 单个大写字母多半是泛型形参（`T item`），其擦除类型在源码里看不出来，
    # 直接放弃判断，避免误报。
    if re.fullmatch(r'[A-Z]', p):
        return None
    return p or None


def handler_params(text, end):
    """从注解右括号之后找到处理方法声明，返回它的参数简单类型列表。

    解析不出来（有额外注解、泛型形参、可变参数等）时返回 None，调用方应跳过
    而不是报错——这个检查只用来抓「参数个数明显对不上」这一类。
    """
    i = end
    open_at = None
    while i < len(text) and i - end < 800:
        c = text[i]
        if c == '"':
            i += 1
            while i < len(text) and text[i] != '"':
                i += 2 if text[i] == '\\' else 1
            i += 1
            continue
        if c == '(':
            if open_at is not None:
                return None
            open_at = i
        elif c == ')':
            if open_at is None:
                return None
            head = text[end:open_at]
            if '@' in head or 'enum ' in head:
                return None  # 处理方法上还挂了别的注解，位置不可靠
            out = []
            for p in split_params(text[open_at + 1:i]):
                st = simple_type(p)
                if st is None:
                    return None
                out.append(st)
            return out
        elif c == '{' or c == ';':
            return None
        i += 1
    return None


def _read_concat(text, j):
    """从 text[j] == '"' 起读一个字符串，并把 `+ "..."` 的拼接接起来。"""
    end = text.find('"', j + 1)
    if end < 0:
        return None, j
    out = text[j + 1:end]
    k = end + 1
    while True:
        m = re.compile(r'\s*\+\s*"').match(text, k)
        if not m:
            return out, k
        end = text.find('"', m.end())
        if end < 0:
            return out, k
        out += text[m.end():end]
        k = end + 1


def string_values(text, key):
    """取出 `key = "..."` 或 `key = {"...", "..."}` 里的字符串。

    源码里长描述符常写成相邻字面量相加（`"a(" + "b)V"`），这里会拼回去。
    """
    out = []
    for m in re.finditer(r'\b' + key + r'\s*=\s*', text):
        j = m.end()
        if j < len(text) and text[j] == '{':
            end = text.find('}', j)
            if end < 0:
                continue
            k = j + 1
            while k < end:
                if text[k] == '"':
                    val, k = _read_concat(text, k)
                    if val is not None:
                        out.append(val)
                    continue
                k += 1
        elif j < len(text) and text[j] == '"':
            val, _ = _read_concat(text, j)
            if val is not None:
                out.append(val)
    return out


class Jar:
    def __init__(self, path, javap):
        self.path = path
        self.win = subprocess.run(['cygpath', '-w', path],
                                  capture_output=True, text=True).stdout.strip() \
            or path
        self.javap = javap
        self._sig = {}
        self._code = {}
        self._params = {}

    @staticmethod
    def _failed(out):
        # 大类的字节码里常常出现字面量 "Error:"（javap 会把它当注释打出来），
        # 所以只能看退出码和输出开头，不能全文搜 "Error:"。
        if out.returncode != 0:
            return True
        return out.stdout.lstrip().startswith('Error:') \
            or out.stderr.lstrip().startswith('Error:')

    def _run(self, flags, fqcn):
        return subprocess.run([self.javap] + flags + ['-cp', self.win, fqcn],
                              capture_output=True, text=True,
                              errors='replace')

    def signatures(self, fqcn):
        """返回 (方法名集合, {名字+描述符})；类不存在返回 None。

        描述符必须跟方法名配对比较：只比描述符的话，`renderLevel(DeltaTracker)V`
        会撞上恰好同描述符的 `update(DeltaTracker)V`，把失效的目标放过去。
        """
        if fqcn in self._sig:
            return self._sig[fqcn]
        out = self._run(['-p', '-s'], fqcn)
        if self._failed(out):
            self._sig[fqcn] = None
            return None
        names, pairs = set(), set()
        pending = None
        for line in out.stdout.splitlines():
            line = line.strip()
            m = re.match(r'descriptor:\s*(\S+)', line)
            if m:
                if pending is not None:
                    pairs.add(pending + m.group(1))
                continue
            m = re.search(r'([A-Za-z0-9_$<>]+)\s*\([^)]*\)\s*;', line)
            if m:
                pending = m.group(1)
                names.add(pending)
            elif line.endswith(';'):
                pending = None
        self._sig[fqcn] = (names, pairs)
        return self._sig[fqcn]

    def param_lists(self, fqcn):
        """返回 {方法名: [[参数简单类型, ...], ...]}；类不存在返回 None。

        只用来核对 `@Inject` 处理方法的参数个数。用 javap 的普通签名输出（不带
        -s）是因为它给的是源码级类型名，正好能跟混入源码里的写法对齐；描述符
        形式还要另外做类型映射，没必要。
        """
        if fqcn in self._params:
            return self._params[fqcn]
        out = self._run(['-p'], fqcn)
        if self._failed(out):
            self._params[fqcn] = None
            return None
        table = {}
        for line in out.stdout.splitlines():
            m = re.match(r'\s{2}(?!.*\bthrows\b).*?([A-Za-z0-9_$<>]+)'
                         r'\s*\(([^)]*)\);\s*$', line)
            if not m:
                continue
            name, params = m.group(1), m.group(2).strip()
            if params in ('', 'void'):
                table.setdefault(name, []).append([])
                continue
            parsed = []
            for p in split_params(params):
                p = re.sub(r'<.*?>', '', p)
                p = p.replace('...', '').replace('[]', '').strip()
                parsed.append(p.split('.')[-1].strip())
            table.setdefault(name, []).append(parsed)
        self._params[fqcn] = table
        return table

    def bytecode(self, fqcn):
        """返回 {方法名: 该方法的指令文本}；类不存在返回 None。"""
        if fqcn in self._code:
            return self._code[fqcn]
        out = self._run(['-p', '-c', '-s'], fqcn)
        if self._failed(out):
            self._code[fqcn] = None
            return None
        blocks = {}
        cur = None
        for line in out.stdout.splitlines():
            if re.match(r'\s{2}\S.*\(.*\)\s*;\s*$', line) \
                    and 'descriptor:' not in line:
                m = re.search(r'([A-Za-z0-9_$<>]+)\s*\(', line)
                cur = m.group(1) if m else None
                blocks.setdefault(cur, [])
                continue
            if cur:
                blocks[cur].append(line)
        self._code[fqcn] = blocks
        return blocks


def resolve_target(text):
    """从混入源码里解析出目标类的全限定名。"""
    m = MIXIN_TARGETS_RE.search(text)
    if m:
        return m.group(1)
    m = MIXIN_RE.search(text)
    if not m:
        return None
    raw = m.group(1)
    if raw.endswith('.class'):
        raw = raw[:-len('.class')]
    imports = {i.split('.')[-1]: i for i in IMPORT_RE.findall(text)}
    parts = raw.split('.')
    base = imports.get(parts[0])
    if base is None:
        return None
    return '$'.join([base] + parts[1:])


def fqcn_of(slash_form):
    return slash_form.replace('/', '.').replace('$', '.')


def split_owner(ref):
    """拆开 Mixin `method` 的 owner 前缀。

    `method` 允许写成 `"Lnet/minecraft/.../Entity;push(...)V"`（带 owner）或
    只写 `"push(...)V"`，也可以加 `/` 前缀表示「只用本类的，不查父类」。
    返回 `(owner, name)`；无 owner 前缀时 owner 为 None。
    """
    if ref.startswith('L') and ';' in ref:
        owner, rest = ref[1:].split(';', 1)
        return owner, rest.lstrip('/')
    return None, ref.lstrip('/')


def check_file(path, jar, proj, rel):
    text = open(path, encoding='utf-8', errors='replace').read()
    fqcn = resolve_target(text)
    if not fqcn:
        return []
    sig = jar.signatures(fqcn)
    if sig is None:
        return [(proj, rel, os.path.basename(path), 0, '-', '-',
                 '目标类不在 jar 中（loader 特有？）')]
    names, pairs = sig
    params = jar.param_lists(fqcn) or {}
    code = None
    findings = []

    def report(line, kind, member, why):
        findings.append((proj, rel, os.path.basename(path), line, kind,
                         member, why))

    for ann in ANNOTATIONS:
        for body, line, end in annotation_args(text, ann):
            require0 = 'require' in body and re.search(
                r'require\s*=\s*0', body)
            tag = ' [require=0]' if require0 else ''
            methods = string_values(body, 'method')
            for meth in methods:
                if re.search(r'[*]', meth):
                    continue  # Mixin 通配写法，跳过
                if '(' in meth:
                    head, desc = meth.split('(', 1)
                    owner, name = split_owner(head)
                    # owner 前缀只有在该 owner 就是本混入的目标类时才能本地校验，
                    # 否则要额外加载那个类，直接跳过（不报、不算漏，只是不查）。
                    if owner and owner.replace('/', '.') != fqcn:
                        continue
                    desc = '(' + desc.lstrip('/')
                    # javap 按类名列出构造器与静态初始化块，不写 <init>/<clinit>。
                    if name == '<init>':
                        name = fqcn.split('.')[-1].split('$')[-1]
                    elif name == '<clinit>':
                        name = 'static'
                    if name + desc not in pairs:
                        report(line, '@' + ann, meth,
                               '目标类无此描述符的方法' + tag)
                else:
                    owner, name = split_owner(meth)
                    if owner and owner.replace('/', '.') != fqcn:
                        continue
                    if name not in names and name not in ('<init>', '<clinit>'):
                        report(line, '@' + ann, meth, '目标类无此方法' + tag)

                # @At 里的 INVOKE 目标必须在目标方法的字节码里真的出现
                if '(' not in meth:
                    continue
                owner, name = split_owner(meth.split('(')[0])
                # javap 把构造器按类名列出，不写 <init>。
                lookup = name
                if name == '<init>':
                    lookup = fqcn.split('.')[-1]
                if code is None:
                    code = jar.bytecode(fqcn) or {}
                body_text = '\n'.join(code.get(lookup, []))
                for at in annotation_args(body, 'At'):
                    at_body = at[0]
                    if 'INVOKE' not in at_body and 'FIELD' not in at_body:
                        continue
                    for tgt in string_values(at_body, 'target'):
                        if not tgt.startswith('L') or ';' not in tgt:
                            continue
                        owner, rest = tgt[1:].split(';', 1)
                        if '(' not in rest:
                            continue
                        tname = rest.split('(')[0]
                        tdesc = rest[len(tname):]
                        key = '%s.%s:%s' % (owner, tname, tdesc)
                        # javap 对同类内部的调用会省略 owner，写成
                        # `// Method bobView:(...)V`，所以要额外按 名:描述符 找一次。
                        same_class = owner.replace('/', '.') == fqcn and \
                            ('%s:%s' % (tname, tdesc)) in body_text
                        if key not in body_text and not same_class:
                            report(line, '@' + ann + '/@At', key,
                                   '调用不在 %s 的字节码里' % name + tag)

            # @Inject 的处理方法签名必须跟目标方法的参数表对得上。这一类比目标失配
            # 更隐蔽：目标方法真实存在、名字也对，只是参数个数变了（1.21.2 的
            # Screen.renderBlurredBackground 从 (F)V 变成 ()V 就是），上面所有检查
            # 全部通过，直到运行期才抛
            # InvalidInjectionException: Invalid descriptor ... Expected (CallbackInfo;)V
            if ann != 'Inject':
                continue
            hp = handler_params(text, end)
            if hp is None or not hp:
                continue
            if hp[-1] not in ('CallbackInfo', 'CallbackInfoReturnable'):
                continue  # 不是回调式 @Inject，不判断
            args = hp[:-1]
            if not args:
                # 只收一个 CallbackInfo 的处理器永远合法：Mixin 的
                # Callback.checkDescriptor() 里有专门一条
                # `handler.desc.equals(target.getSimpleCallbackDescriptor())`
                # 的分支，命中后把 captureArgs 置 false，一个目标参数都不传。
                # 1.20.1 根工程（已实机启动验证）里有 44 处这种写法。
                continue
            cands = []
            skipped = False
            for meth in string_values(body, 'method'):
                owner, name = split_owner(meth.split('(')[0])
                if '*' in meth or (owner and owner.replace('/', '.') != fqcn):
                    skipped = True
                    break
                if name == '<init>':
                    name = fqcn.split('.')[-1].split('$')[-1]
                elif name == '<clinit>':
                    continue
                lst = params.get(name)
                if lst:
                    cands.extend(lst)
            if skipped or not cands:
                continue
            # 只比参数**个数**。类型名不能比：目标类里的 `T` 在 javap 里是
            # 擦除后的声明，而处理方法写的是具体子类（26.1.1 的
            # `shouldShowName(T, double)` 与处理方法里的 `LivingEntity`
            # 就是这么一对，Mixin 实际能接受），比类型名会全是误报。
            # Mixin 要求处理方法要么取全目标参数，要么一个都不取，不接受真前缀。
            if not any(len(args) == len(c) for c in cands):
                shapes = ' / '.join('(' + ', '.join(c) + ')' for c in cands)
                report(line, '@Inject', 'handler(%s)' % ', '.join(hp),
                       '处理方法参数个数对不上目标 %s' % shapes)
    return findings


def find_jar(version):
    """按版本找一份 **Mojang 官方映射** 的 MC jar。

    26.1 起 fabric-loom 把产物放在 minecraft-merged-deobf/<v>/；更早的版本放在
    minecraft-merged/<v>-loom.mappings.../（带 layered 映射散列后缀），两者都是
    mojmap。三个加载器都用官方映射，所以同一份 jar 对三棵树都适用。
    """
    base = os.path.join(HOME, '.gradle', 'caches', 'fabric-loom',
                        'minecraftMaven', 'net', 'minecraft')
    for pat in (
        os.path.join(base, 'minecraft-merged-deobf', version, '*.jar'),
        os.path.join(base, 'minecraft-merged', version + '-*', '*.jar'),
        os.path.join(base, 'minecraft-merged', version, '*.jar'),
    ):
        for p in sorted(glob.glob(pat)):
            if 'intermediary' not in p and 'sources' not in p:
                return p
    # Forge 侧的 recompiled.jar（同样是 mojmap）作为兜底
    for pat in (
        os.path.join(HOME, '.gradle', 'caches', 'minecraftforge',
                     'forgegradle', 'mavenizer', 'caches', 'forge', 'net',
                     'minecraftforge', 'forge', version + '-*', 'official',
                     version, 'recompiled.jar'),
        os.path.join(HOME, '.gradle', 'caches', 'minecraftforge',
                     'forgegradle', 'mavenizer', 'caches', 'maven', 'forge',
                     'net', 'minecraftforge', 'forge', version + '-*',
                     'forge-*-userdev.jar'),
    ):
        for p in sorted(glob.glob(pat)):
            if os.path.isfile(p):
                return p
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('version')
    ap.add_argument('--tree', default='all',
                    choices=['all'] + list(TREES))
    ap.add_argument('--jar')
    ap.add_argument('--javap', default=None)
    args = ap.parse_args()

    javap = args.javap
    if not javap:
        for base in (r'C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot',
                     r'C:\Program Files\Java\jdk-21.0.11',
                     r'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'):
            cand = os.path.join(base, 'bin', 'javap.exe')
            if os.path.isfile(cand):
                javap = cand
                break
    if not javap:
        raise SystemExit('找不到 javap')

    jar_path = args.jar or find_jar(args.version)
    if not jar_path:
        raise SystemExit('找不到 %s 的 MC jar（可用 --jar 指定）' % args.version)
    jar = Jar(jar_path, javap)
    print('jar: %s\n' % jar_path)

    trees = list(TREES) if args.tree == 'all' else [args.tree]
    total = []
    skipped = 0
    for tree in trees:
        # 1.20.1 的三个工程不在 versions/ 下，而是仓库根 / fabric/ / neoforge/
        # 本身（它们是这一代的参考实现）。
        if args.version == '1.20.1':
            proj_dir = {'forge': ROOT, 'fabric': os.path.join(ROOT, 'fabric'),
                        'neoforge': os.path.join(ROOT, 'neoforge')}[tree]
            tree_root = proj_dir
        else:
            tree_root = os.path.join(ROOT, TREES[tree])
            proj_dir = os.path.join(tree_root, args.version)
        mixin_dir = os.path.join(proj_dir,
                                 'src/main/java/net/wurstclient/mixin')
        if not os.path.isdir(mixin_dir):
            continue
        registered = load_mixin_configs(proj_dir)
        if not registered:
            print('警告：%s 没读到混入配置，不做注册过滤' % proj_dir)
        for dirpath, _, files in os.walk(mixin_dir):
            for f in sorted(files):
                if not f.endswith('.java'):
                    continue
                p = os.path.join(dirpath, f)
                rel = os.path.relpath(p, tree_root)
                config_name = os.path.relpath(p, mixin_dir)[:-len('.java')] \
                    .replace(os.sep, '.')
                if registered and config_name not in registered:
                    skipped += 1
                    continue
                total += check_file(p, jar, tree, rel)

    for proj, rel, fname, line, kind, member, why in sorted(total):
        print('%-9s %-58s:%-4d %-18s %-46s %s'
              % (proj, rel, line, kind, member, why))
    print('\n发现 %d 处可疑注入点（另有 %d 个未注册的混入文件已跳过）'
          % (len(total), skipped))


if __name__ == '__main__':
    main()
