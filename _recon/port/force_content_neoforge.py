# -*- coding: utf-8 -*-
"""Bring the lagging neoforge root to Fabric v1.5.1 content.

Unlike the same-version siblings, the neoforge 1.20.1 root was forked before
most of the v1.5.1 rewrite, so identical-at-HEAD Fabric files still lag. Copy
every divergent file except the genuinely loader-specific ones.
"""
import os
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SRC = REPO / 'fabric/src/main'
DST = REPO / 'neoforge/src/main'

KEEP = {
    'java/net/wurstclient/util/PlatformUtils.java',
    'java/net/wurstclient/util/BaritoneUtils.java',
    'java/net/wurstclient/hud2/nanovg/NanoVGContext.java',
    'java/net/wurstclient/other_features/NoChatReportsOtf.java',
    'resources/wurst.mixins.json',
    'resources/META-INF/accesstransformer.cfg',
}


def norm(data):
    return (data.replace(b'\r\n', b'\n').replace(b'\r', b'\n')
            .lstrip(b'\xef\xbb\xbf'))


def main():
    copied = kept = 0
    for root, _, files in os.walk(SRC):
        for name in files:
            sp = Path(root) / name
            rel = sp.relative_to(SRC).as_posix()
            dp = DST / rel
            if not dp.exists():
                continue
            if rel in KEEP:
                kept += 1
                continue
            if norm(sp.read_bytes()) == norm(dp.read_bytes()):
                continue
            dp.write_bytes(sp.read_bytes())
            copied += 1
            print('copy', rel)
    print('copied=%d kept-loader-specific=%d' % (copied, kept))


if __name__ == '__main__':
    main()
