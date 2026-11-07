# -*- coding: utf-8 -*-
"""Add description keys for the ported hacks to en_us / zh_cn."""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import project_dirs, mc_version, version_tuple

EN = {
    'attributeswap': 'Swaps main-hand item attributes with the target slot.',
    'macedmg': 'Increases your damage when attacking with a mace.',
    'antiknockback': 'Prevents you from taking knockback from players and mobs.',
}
ZH = {
    'attributeswap': '攻击时自动切换到更合适的武器（盾破、破甲或耐久保护）。',
    'macedmg': '使用重锤攻击时提高伤害。',
    'antiknockback': '保护你不被其他生物或玩家推动和用剑击退。',
}


def add_keys(path, wanted, table):
    text = path.read_text(encoding='utf-8')
    missing = [k for k in wanted if f'"description.wurst.hack.{k}"' not in text]
    if not missing:
        return 0
    lines = text.splitlines(keepends=True)
    anchor = None
    for i, line in enumerate(lines):
        if '"description.wurst.hack.antiknockback"' in line:
            anchor = i
            break
    if anchor is None:
        return -1
    a = lines[anchor]
    if not a.rstrip().endswith(','):
        lines[anchor] = a.rstrip('\r\n') + ',\n'
    ins = []
    for k in missing:
        ins.append('"description.wurst.hack.%s": "%s",\n' % (k, table[k]))
    lines[anchor + 1:anchor + 1] = ins
    new = ''.join(lines)
    json.loads(new)  # keep the file valid
    path.write_text(new, encoding='utf-8', newline='\n')
    return len(missing)


def main():
    stats = {}
    for p in project_dirs():
        vt = version_tuple(mc_version(p))
        keys = []
        if vt >= (1, 21, 0):
            keys.append('macedmg')
        if vt >= (1, 21, 5):
            keys.append('attributeswap')
        if not keys:
            continue
        base = p / 'src/main/resources/assets/wurst/translations'
        for name, table in (('en_us.json', EN), ('zh_cn.json', ZH)):
            f = base / name
            if f.exists():
                n = add_keys(f, keys, table)
                stats[n] = stats.get(n, 0) + 1
    print('translations', stats)


if __name__ == '__main__':
    main()
