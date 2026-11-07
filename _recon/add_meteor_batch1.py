# -*- coding: utf-8 -*-
"""Replicate Meteor batch-1 hacks to every v1.5 project (skip root v1.6)."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

TEMPLATE_DIR = REPO / 'fabric/versions/1.21.11/src/main/java/net/wurstclient/hacks'

ITEMS = [
    ('BowSpamHack', 'bowSpamHack', 'bowAimbotHack'),
    ('AutoEXPHack', 'autoExpHack', 'autoEatHack'),
    ('EXPThrowerHack', 'expThrowerHack', 'autoEatHack'),
]


def autoexp_source(project):
    src = (TEMPLATE_DIR / 'AutoEXPHack.java').read_text(encoding='utf-8')
    # 1.20.x projects predate EnchantmentUtils
    if not (project / 'src/main/java/net/wurstclient/util/'
            'EnchantmentUtils.java').exists():
        src = src.replace(
            'import net.wurstclient.util.EnchantmentUtils;',
            'import net.minecraft.world.item.enchantment.EnchantmentHelper;')
        src = src.replace(
            'EnchantmentUtils.getLevel(Enchantments.MENDING, stack) <= 0',
            'EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING,'
                ' stack) <= 0')
    return src


def register(list_path, field_name, cls, anchor):
    text = list_path.read_text(encoding='utf-8')
    if field_name in text:
        return 'exists'
    m = re.search(r'^([ \t]*)public final \w+ ' + anchor + r' = .*$',
                  text, re.M)
    if not m:
        return 'NO ANCHOR'
    line = '\tpublic final %s %s = new %s();' % (cls, field_name, cls)
    text = text[:m.end()] + '\n' + line + text[m.end():]
    list_path.write_text(text, encoding='utf-8', newline='\n')
    return 'added'


def main():
    root = REPO.resolve()
    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue  # v1.6.0 stays untouched
        for cls, field, anchor in ITEMS:
            dst = p / ('src/main/java/net/wurstclient/hacks/%s.java' % cls)
            text = autoexp_source(p) if cls == 'AutoEXPHack' \
                else (TEMPLATE_DIR / (cls + '.java')).read_text(
                    encoding='utf-8')
            dst.write_text(text, encoding='utf-8', newline='\n')
            r = register(p / 'src/main/java/net/wurstclient/hack/HackList.java',
                         field, cls, anchor)
            stats[r] = stats.get(r, 0) + 1

        # MiddleClickExtra is deferred: MouseButtonPressListener is missing on
        # 1.20.x-1.21.5 projects. Remove any copies from the earlier attempt.
        mce = p / 'src/main/java/net/wurstclient/hacks/MiddleClickExtraHack.java'
        if mce.exists():
            mce.unlink()
        list_path = p / 'src/main/java/net/wurstclient/hack/HackList.java'
        text = list_path.read_text(encoding='utf-8')
        text = re.sub(r'^\s*public final MiddleClickExtraHack '
                      r'middleClickExtraHack = .*\n', '', text, flags=re.M)
        list_path.write_text(text, encoding='utf-8', newline='\n')
    print('projects=', len(project_dirs()) - 1, stats)


if __name__ == '__main__':
    main()
