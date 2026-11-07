# -*- coding: utf-8 -*-
"""Install the 1.21.5+ KeyboardInput/MoveInput wiring."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from fix_1215_plus import ACCESSOR, KEYBOARD_MIXIN  # noqa: E402


def patch(project):
    root = Path(project) / 'src/main/java/net/wurstclient'
    (root / 'mixin/KeyboardInputMixin.java').write_text(
        KEYBOARD_MIXIN, encoding='utf-8', newline='\n')
    (root / 'mixin/ClientInputAccessor.java').write_text(
        ACCESSOR, encoding='utf-8', newline='\n')

    p = Path(project) / 'src/main/resources/wurstpenguin.mixins.json'
    t = p.read_text(encoding='utf-8')
    if '"ClientInputAccessor"' not in t:
        for anchor in ('    "ClientConnectionMixin",\n',
                       '    "ClientPlayerEntityMixin",\n'):
            if anchor in t:
                t = t.replace(anchor, anchor
                              + '    "ClientInputAccessor",\n', 1)
                break
        else:
            t = t.replace('  "client": [\n',
                          '  "client": [\n    "ClientInputAccessor",\n', 1)
        p.write_text(t, encoding='utf-8', newline='\n')
    print('input', project)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
