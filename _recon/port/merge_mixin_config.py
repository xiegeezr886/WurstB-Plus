# -*- coding: utf-8 -*-
"""Merge new Fabric mixin entries into a Forge/NeoForge wurst.mixins.json.

Only entries whose mixin class exists in the target tree are added; mixins
that only make sense on Fabric are excluded.

Usage:
    python _recon/port/merge_mixin_config.py versions/1.21.9 [--apply]
"""
import argparse
import json
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

FABRIC_ONLY_MIXINS = {
    'IngameHudMixin',
    'xray.indigo.AbstractTerrainRenderContextMixin',
    'xray.indigo.BlockRenderInfoMixin',
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('target')
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()

    target = (REPO / args.target).resolve()
    props = (target / 'gradle.properties').read_text(encoding='utf-8')
    mc = None
    for line in props.splitlines():
        if line.strip().startswith('minecraft_version='):
            mc = line.split('=', 1)[1].strip()
            break
    rel = args.target.replace('\\', '/').strip('/')
    src_rel = 'fabric' if rel == 'neoforge' else 'fabric/versions/' + mc
    src_cfg = REPO / src_rel / 'src/main/resources/wurstpenguin.mixins.json'
    tgt_cfg = target / 'src/main/resources/wurst.mixins.json'
    if not src_cfg.exists() or not tgt_cfg.exists():
        raise SystemExit('missing mixin config: %s / %s' % (src_cfg, tgt_cfg))

    src = json.loads(src_cfg.read_text(encoding='utf-8'))
    tgt = json.loads(tgt_cfg.read_text(encoding='utf-8'))
    mixin_root = target / 'src/main/java/net/wurstclient/mixin'

    def existed_at_head(entry):
        rel = (src_rel + '/src/main/java/net/wurstclient/mixin/'
               + entry.replace('.', '/') + '.java')
        p = subprocess.run(['git', 'cat-file', '-e', 'HEAD:' + rel],
                           cwd=REPO, capture_output=True)
        return p.returncode == 0

    added = []
    missing_class = []
    old_mixin = []
    for key in ('mixins', 'client'):
        existing = list(tgt.get(key, []))
        for entry in src.get(key, []):
            if entry in existing:
                continue
            if entry in FABRIC_ONLY_MIXINS:
                continue
            # Only mixins introduced by the v1.5.1 fullport are new content.
            # Pre-existing Fabric mixins already have a loader equivalent in
            # the target tree (renamed accessor/handler) and must not be
            # double-registered.
            if existed_at_head(entry):
                old_mixin.append(entry)
                continue
            cls = mixin_root / (entry.replace('.', '/') + '.java')
            if not cls.exists():
                missing_class.append(entry)
                continue
            existing.append(entry)
            added.append(key + ':' + entry)
        tgt[key] = existing

    text = json.dumps(tgt, indent=2, ensure_ascii=False) + '\n'
    print('target=%s source=%s added=%d' % (rel, src_rel, len(added)))
    for item in added:
        print('  +', item)
    for item in old_mixin:
        print('  = pre-existing, left alone:', item)
    for item in missing_class:
        print('  ! class missing, skipped:', item)
    if args.apply:
        tgt_cfg.write_text(text, encoding='utf-8', newline='\n')


if __name__ == '__main__':
    main()
