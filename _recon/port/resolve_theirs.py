# -*- coding: utf-8 -*-
"""Resolve diff3 conflict markers by keeping the 'theirs' side."""
import re
import sys
from pathlib import Path

PATTERN = re.compile(
    r'<<<<<<<[^\n]*\n.*?=======\n(.*?)>>>>>>>[^\n]*\n', re.S)


def resolve(path):
    p = Path(path)
    text = p.read_text(encoding='utf-8')
    new = PATTERN.sub(r'\1', text)
    if new == text:
        print('no conflict', path)
        return
    p.write_text(new, encoding='utf-8', newline='\n')
    print('resolved', path)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        resolve(arg)
