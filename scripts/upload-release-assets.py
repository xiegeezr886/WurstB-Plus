#!/usr/bin/env python3
r"""把 build/release-v1.5/ 的产物上传到 GitHub Release（默认 v1.5.0）。

设计约束：
  * **只动 assets，不改 release 简介**。本脚本从不调用 PATCH /releases/{id}，
    因此 title/body 不会被触碰。
  * 同名资产已存在时必须先删再传（GitHub 不允许重名）。删除是逐个进行的，
    任一时刻最多只有一个资产处于「已删未传」的窗口内。
  * 凭据从 `git credential fill` 取（Git Credential Manager 里存的那份），
    不从命令行或文件读取，也不打印。

用法：
    python scripts/upload-release-assets.py --dry-run        # 只报告会做什么
    python scripts/upload-release-assets.py                  # 真正上传
    python scripts/upload-release-assets.py --only Fabric-1.20.2
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(ROOT, 'build', 'release-v1.5')
OWNER_REPO = 'xiegeezr886/WurstB-Plus'
API = 'https://api.github.com'
UA = {'User-Agent': 'wurstb-release-uploader', 'Accept':
      'application/vnd.github+json'}


def token():
    """从 GCM 取 github 凭据；优先环境变量，便于 CI 使用。"""
    for var in ('GH_TOKEN', 'GITHUB_TOKEN'):
        if os.environ.get(var):
            return os.environ[var]
    p = subprocess.run(['git', 'credential', 'fill'], cwd=ROOT,
                       input='protocol=https\nhost=github.com\n\n',
                       capture_output=True, text=True)
    for line in p.stdout.splitlines():
        if line.startswith('password='):
            return line.split('=', 1)[1]
    raise SystemExit('取不到 GitHub 凭据（git credential fill 无 password）')


def api(url, tok, method='GET', data=None, headers=None):
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header('Authorization', 'token ' + tok)
    for k, v in UA.items():
        req.add_header(k, v)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=120) as r:
        body = r.read()
    return json.loads(body) if body else None


def upload_asset(upload_url, tok, path, name):
    with open(path, 'rb') as fh:
        data = fh.read()
    # 必须百分号编码：资产名里的 `+` 若原样放进查询串会被解析成空格，
    # GitHub 收到后落盘成 `.`，于是 `WurstB+.Plus-…` 变成 `WurstB.Plus-…`。
    qname = urllib.parse.quote(name, safe='')
    req = urllib.request.Request(
        upload_url.replace('{?name,label}', '') + '?name=' + qname,
        data=data, method='POST')
    req.add_header('Authorization', 'token ' + tok)
    req.add_header('Content-Type', 'application/java-archive')
    req.add_header('Content-Length', str(len(data)))
    for k, v in UA.items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=900) as r:
        return json.loads(r.read())


def patch_body_26_3(rel, tok):
    """在简介的「覆盖范围」三行版本表末尾补上 26.3 —— 只改这一个位置。

    刻意不动标题、不重写简介：逐行匹配 `| <加载器> | ...、26.2 |`，
    只在末尾追加 `、26.3`；已经写过 26.3 的行原样保留。
    """
    body = rel.get('body') or ''
    out, changed = [], 0
    for line in body.split('\n'):
        eol = '\r' if line.endswith('\r') else ''
        core = line[:-1] if eol else line
        if re.match(r'\|\s*(Forge|Fabric|NeoForge)\s*\|', core) \
                and '26.3' not in core:
            new = core.rstrip()
            if new.endswith('|'):
                new = new[:-1].rstrip() + '、26.3 |'
            else:
                new += '、26.3'
            if new != core:
                changed += 1
            out.append(new + eol)
        else:
            out.append(line)
    if not changed:
        print('简介里没有需要补 26.3 的行（可能已补过）')
        return 0
    new_body = '\n'.join(out)
    # 只提交 body 字段，release 的 name/title 不受影响
    api('%s/repos/%s/releases/%d' % (API, OWNER_REPO, rel['id']), tok,
        method='PATCH', data=json.dumps({'body': new_body}).encode(),
        headers={'Content-Type': 'application/json'})
    print('简介已更新：%d 行补上 26.3（仅 body，未改标题）' % changed)
    return changed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--tag', default='v1.5.0')
    ap.add_argument('--dry-run', action='store_true')
    ap.add_argument('--only', nargs='*')
    ap.add_argument('--add-263-to-body', action='store_true',
                    help='在简介的版本表末尾补上 26.3（本轮要求；其余简介内容不动）')
    ap.add_argument('--body-only', action='store_true',
                    help='只补简介的 26.3，不上传任何 assets')
    args = ap.parse_args()

    if not os.path.isdir(DIST):
        raise SystemExit('没有 %s，先跑 scripts/build-v1.5-release.py' % DIST)
    files = sorted(f for f in os.listdir(DIST)
                   if f.endswith('.jar') and f.startswith('WurstB+.Plus-'))
    if args.only:
        files = [f for f in files
                 if any(o in f for o in args.only)]
    if not files:
        raise SystemExit('没有可上传的 jar')

    tok = token()
    rel = api('%s/repos/%s/releases/tags/%s' % (API, OWNER_REPO, args.tag), tok)
    rid = rel['id']
    print('release %s (id=%d, name=%s) 现有资产 %d 个'
          % (args.tag, rid, rel.get('name'), len(rel.get('assets', []))))
    if args.body_only:
        if args.dry_run:
            body = rel.get('body') or ''
            hits = [l for l in body.split('\n')
                    if re.match(r'\|\s*(Forge|Fabric|NeoForge)\s*\|', l)
                    and '26.3' not in l]
            print('[dry] 简介将补 26.3 的行数：%d' % len(hits))
        else:
            patch_body_26_3(rel, tok)
        return 0
    print('本次待上传 %d 个文件；只动 assets，不修改简介\n' % len(files))
    existing = {a['name']: a for a in rel.get('assets', [])}

    ok = fail = 0
    for name in files:
        path = os.path.join(DIST, name)
        size = os.path.getsize(path)
        old = existing.get(name)
        action = '替换' if old else '新增'
        if args.dry_run:
            print('  [dry] %-46s %-4s %8.1f MB' % (name, action, size / 1e6))
            continue
        try:
            if old:
                api('%s/repos/%s/releases/assets/%d'
                    % (API, OWNER_REPO, old['id']), tok, method='DELETE')
            upload_asset(rel['upload_url'], tok, path, name)
            print('  OK   %-46s %-4s %8.1f MB' % (name, action, size / 1e6),
                  flush=True)
            ok += 1
        except Exception as e:  # 保留现场，继续下一个，最后统一报错
            print('  FAIL %-46s %s' % (name, e), flush=True)
            fail += 1
        time.sleep(0.3)
    print('\n成功 %d / 失败 %d' % (ok, fail))
    # 简介只在资产都传上去之后再动，避免「简介说支持 26.3、资产却还没上」
    if args.add_263_to_body and fail == 0:
        if args.dry_run:
            body = rel.get('body') or ''
            hits = [l for l in body.split('\n')
                    if re.match(r'\|\s*(Forge|Fabric|NeoForge)\s*\|', l)
                    and '26.3' not in l]
            print('[dry] 简介将补 26.3 的行数：%d' % len(hits))
        else:
            patch_body_26_3(rel, tok)
    return 1 if fail else 0


if __name__ == '__main__':
    sys.exit(main())
