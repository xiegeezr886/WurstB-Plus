# -*- coding: utf-8 -*-
"""1.20.5/1.20.6 renderLevel(float,long) no longer takes a PoseStack.

The old handler still declared PoseStack as a target parameter, so Mixin
rejected the injection at apply time. Capture the PoseStack local that is
passed to bobView() with MixinExtras' @Local sugar instead.
"""
import sys
from pathlib import Path

OLD = (
    '\tprivate void onRenderWorldViewBobbing(float tickDelta, long limitTime,\n'
    '\t\tPoseStack matrices, CallbackInfo ci)\n'
)
NEW = (
    '\tprivate void onRenderWorldViewBobbing(float tickDelta, long limitTime,\n'
    '\t\t@Local(ordinal = 0) PoseStack matrices, CallbackInfo ci)\n'
)


def patch(path):
    p = Path(path)
    t = p.read_text(encoding='utf-8')
    if '@Local(ordinal = 0) PoseStack matrices' in t:
        print('already', path)
        return
    if OLD not in t:
        print('anchor missing', path)
        return
    if 'import com.llamalad7.mixinextras.sugar.Local;' not in t:
        anchor = 'import com.llamalad7.mixinextras.injector.ModifyReturnValue;\n'
        if anchor not in t:
            print('import anchor missing', path)
            return
        t = t.replace(anchor, anchor +
                      'import com.llamalad7.mixinextras.sugar.Local;\n', 1)
    t = t.replace(OLD, NEW, 1)
    p.write_text(t, encoding='utf-8', newline='\n')
    print('patched', path)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
