# -*- coding: utf-8 -*-
"""Fill missing setting.name.* translations across all projects."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

MANUAL = {
    'setting.name.hypixel_only': '仅 Hypixel',
    'setting.name.max_tick': '最大 tick 数',
    'setting.name.invisible': '隐身实体',
    'setting.name.pushing_particles': '推动粒子',
}


def insert_entries(path, entries):
    text = path.read_text(encoding='utf-8')
    lines = text.splitlines(keepends=True)
    anchor = next((i for i, l in enumerate(lines)
                   if '"setting.name.' in l), None)
    if anchor is None:
        anchor = next((i for i, l in enumerate(lines)
                       if '"description.wurst.hack.' in l), None)
    if anchor is None:
        return -1
    if not lines[anchor].rstrip().endswith(','):
        lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
    lines[anchor + 1:anchor + 1] = [
        '%s: %s,\n' % (json.dumps(k, ensure_ascii=False),
                       json.dumps(v, ensure_ascii=False))
        for k, v in entries]
    new = ''.join(lines)
    json.loads(new)
    path.write_text(new, encoding='utf-8', newline='\n')
    return len(entries)


def main():
    root = REPO.resolve()
    src_path = (root / 'fabric/src/main/resources/assets/wurst/'
                'translations/zh_cn.json')
    src = json.loads(src_path.read_text(encoding='utf-8'))
    source = {k: v for k, v in src.items()
              if k.startswith('setting.name.')}
    source.update(MANUAL)

    total = 0
    for project in project_dirs():
        zh_path = (project / 'src/main/resources/assets/wurst/translations/'
                   'zh_cn.json')
        if not zh_path.exists():
            continue
        zh = json.loads(zh_path.read_text(encoding='utf-8'))
        missing = [(k, v) for k, v in source.items() if k not in zh]
        if missing:
            n = insert_entries(zh_path, missing)
            if n > 0:
                total += n
    print('inserted setting translations', total)


if __name__ == '__main__':
    main()
