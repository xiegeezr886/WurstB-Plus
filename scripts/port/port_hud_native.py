# -*- coding: utf-8 -*-
"""Copy the working 26.3 Fabric HUD-only port to another 26.3 project.

Usage:
    python _recon/port_hud_native.py versions/26.3
    python _recon/port_hud_native.py neoforge/versions/26.3
"""
import subprocess
import sys
from pathlib import Path

ROOT = Path('.').resolve()
SRC = Path('fabric/versions/26.3')


def main():
    if len(sys.argv) != 2:
        raise SystemExit('usage: port_hud_native.py <target>')
    dst = Path(sys.argv[1])
    if not dst.exists():
        raise SystemExit('target not found: %s' % dst)
    dst = dst.resolve()
    if ROOT not in dst.parents:
        raise SystemExit('target outside repo')

    out = subprocess.run(
        ['git', 'status', '--porcelain', '--untracked-files=all', str(SRC)],
        cwd=str(ROOT), capture_output=True, text=True)
    if out.returncode != 0:
        raise SystemExit(out.stderr)

    copied = deleted = skipped = 0
    for line in out.stdout.splitlines():
        if len(line) < 4:
            continue
        status = line[:2].strip()
        path = line[3:].strip().strip('"')
        src = ROOT / path
        rel = path[len(str(SRC)) + 1:]
        if rel in ('build.gradle', 'gradle.properties'):
            skipped += 1
            continue
        target = dst / rel
        if status == 'D':
            if target.exists():
                if target.is_dir():
                    import shutil
                    shutil.rmtree(target)
                else:
                    target.unlink()
                deleted += 1
            continue
        if not src.exists():
            skipped += 1
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        if src.is_dir():
            import shutil
            if target.exists():
                shutil.rmtree(target)
            shutil.copytree(src, target)
        else:
            target.write_bytes(src.read_bytes())
        copied += 1

    # Fonts are untracked under the resources directory and may not show up
    # as individual files in older git versions; copy explicitly.
    fonts_src = SRC / 'src/main/resources/assets/wurstpenguin/nanovg/fonts'
    fonts_dst = dst / 'src/main/resources/assets/wurstpenguin/nanovg/fonts'
    fonts_dst.mkdir(parents=True, exist_ok=True)
    for font in fonts_src.glob('*.ttf'):
        (fonts_dst / font.name).write_bytes(font.read_bytes())

    print('target=%s copied=%d deleted=%d skipped=%d' %
          (dst, copied, deleted, skipped))


if __name__ == '__main__':
    main()
