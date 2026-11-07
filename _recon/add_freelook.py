# -*- coding: utf-8 -*-
"""Replicate FreelookHack + GameRendererMixin hook to all 66 v1.5 projects."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

SRC = REPO / 'fabric/versions/1.21.11/src/main/java/net/wurstclient/hacks/FreelookHack.java'
SOURCE = SRC.read_text(encoding='utf-8')


def main():
    root = REPO.resolve()
    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        hack_dir = p / 'src/main/java/net/wurstclient/hacks'
        (hack_dir / 'FreelookHack.java').write_text(
            SOURCE, encoding='utf-8', newline='\n')

        list_path = p / 'src/main/java/net/wurstclient/hack/HackList.java'
        text = list_path.read_text(encoding='utf-8')
        if 'freelookHack' not in text:
            m = re.search(
                r'^([ \t]*)public final \w+ notifierHack\s*=.*?;',
                text, re.M | re.S)
            if not m:
                stats['NO LIST ANCHOR'] = stats.get('NO LIST ANCHOR', 0) + 1
            else:
                text = text[:m.end()] + \
                    '\n\tpublic final FreelookHack freelookHack = ' \
                    'new FreelookHack();' + text[m.end():]
                list_path.write_text(text, encoding='utf-8', newline='\n')
                stats['registered'] = stats.get('registered', 0) + 1

        mixin = p / 'src/main/java/net/wurstclient/mixin/GameRendererMixin.java'
        if not mixin.exists():
            stats['NO MIXIN'] = stats.get('NO MIXIN', 0) + 1
            continue
        mt = mixin.read_text(encoding='utf-8')
        if 'freelookHack' in mt:
            stats['mixin-exists'] = stats.get('mixin-exists', 0) + 1
            continue
        m = re.search(r'^([ \t]*)CameraTransformViewBobbingEvent event\s*=',
                      mt, re.M)
        if not m:
            stats['NO MIXIN HOOK'] = stats.get('NO MIXIN HOOK', 0) + 1
            continue
        ins = ('\t\tWurstClient.INSTANCE.getHax().freelookHack\n'
               '\t\t\t.applyCameraRotation(matrices);\n')
        mt = mt[:m.start()] + ins + mt[m.start():]
        mixin.write_text(mt, encoding='utf-8', newline='\n')
        stats['mixin-added'] = stats.get('mixin-added', 0) + 1
    print('freelook', stats)


if __name__ == '__main__':
    main()
