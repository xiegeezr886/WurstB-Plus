#!/usr/bin/env python3
"""Print a Minecraft level.dat's spawn point and saved player position.

Used by the runtime harness to pick a Baritone `#goto` target near spawn and,
when the client exits cleanly, to check that the player actually moved.

Usage:
    python scripts/read-level.py <level.dat>
Output (one line, machine readable):
    spawn <x> <y> <z> player <x> <y> <z> | spawn <x> <y> <z> player missing
"""
import gzip
import struct
import sys
from pathlib import Path

TAGS = {1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd', 7: 'B', 8: 's',
        9: 'list', 10: 'compound', 11: 'i[]', 12: 'q[]'}


class Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, fmt):
        size = struct.calcsize('>' + fmt)
        value = struct.unpack_from('>' + fmt, self.data, self.pos)
        self.pos += size
        return value[0] if len(value) == 1 else value

    def string(self):
        size = self.take('H')
        value = self.data[self.pos:self.pos + size].decode('utf-8', 'replace')
        self.pos += size
        return value

    def payload(self, tag, name):
        if tag == 1:
            return self.take('b')
        if tag == 2:
            return self.take('h')
        if tag == 3:
            return self.take('i')
        if tag == 4:
            return self.take('q')
        if tag == 5:
            return self.take('f')
        if tag == 6:
            return self.take('d')
        if tag == 7:
            count = self.take('i')
            self.pos += count
            return None
        if tag == 8:
            return self.string()
        if tag == 9:
            item = self.take('b')
            count = self.take('i')
            return [self.payload(item, None) for _ in range(count)]
        if tag == 10:
            result = {}
            while True:
                child = self.take('b')
                if child == 0:
                    return result
                key = self.string()
                result[key] = self.payload(child, key)
        if tag == 11:
            count = self.take('i')
            return list(self.take('i') for _ in range(count))
        if tag == 12:
            count = self.take('i')
            return list(self.take('q') for _ in range(count))
        raise ValueError('unknown NBT tag %d at %d' % (tag, self.pos))


def read_level(path):
    with gzip.open(path, 'rb') as stream:
        data = stream.read()
    reader = Reader(data)
    tag = reader.take('b')
    if tag != 10:
        raise ValueError('level.dat root is not a compound')
    reader.string()  # root name
    return reader.payload(10, '')


def triple(value):
    return tuple(round(float(v), 2) for v in value)


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    root = read_level(sys.argv[1])
    data = root.get('Data', root)
    spawn = (data.get('SpawnX'), data.get('SpawnY'), data.get('SpawnZ'))
    if None in spawn:
        # 26.x stores the spawn as Data.spawn.pos (int array) instead of the
        # legacy Data.SpawnX/SpawnY/SpawnZ fields.
        legacy = (data.get('spawn') or {}).get('pos')
        if legacy and len(legacy) == 3:
            spawn = tuple(int(v) for v in legacy)
    if None in spawn:
        spawn = (0, 64, 0)
    player = data.get('Player') or {}
    pos = player.get('Pos')
    parts = ['spawn', *(str(v) for v in spawn)]
    if pos and len(pos) == 3:
        parts += ['player', *(str(v) for v in triple(pos))]
    else:
        parts += ['player', 'missing']
    print(' '.join(parts))


if __name__ == '__main__':
    main()
