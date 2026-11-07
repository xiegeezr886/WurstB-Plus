# -*- coding: utf-8 -*-
"""Disable every persisted Wurst hack in all local run/.test game dirs."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKIP = {'reference', 'build', '.git', '.gradle'}
cleared = []
total_entries = 0
for path in ROOT.rglob('enabled-hacks.json*'):
    if any(part in SKIP for part in path.parts):
        continue
    if path.suffix not in ('.json', '.bak'):
        # .json.bak has suffix .bak; anything else skipped
        continue
    try:
        data = json.loads(path.read_text(encoding='utf-8'))
    except Exception:
        continue
    if isinstance(data, list) and not data:
        continue
    n = len(data) if isinstance(data, list) else 1
    total_entries += n
    path.write_text('[]\n', encoding='utf-8', newline='\n')
    cleared.append((str(path.relative_to(ROOT)), n))

print('files_cleared', len(cleared), 'entries_disabled', total_entries)
for f, n in cleared:
    print(f, n)
