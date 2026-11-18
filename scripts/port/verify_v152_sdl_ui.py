"""Click real 26.3 menu entries and exercise SDL key/button events in local QA.

Requires the opt-in localhost TestControlServer. Does not enter worlds, log in,
connect to servers or start music. Screenshots still require visual inspection.
"""
import argparse
import json
from pathlib import Path
import time
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--port', type=int, required=True)
    args = parser.parse_args()
    run = args.run.resolve()
    token = (run / 'wurst/test-token.txt').read_text().strip()
    evidence = {'observations': [], 'visual_inspection_required': True}

    def call(path='/screen', **params):
        request = urllib.request.Request(f'http://127.0.0.1:{args.port}' + path + '?' + urllib.parse.urlencode(params),
                                         headers={'X-Wurst-Token': token}, method='POST')
        with urllib.request.urlopen(request, timeout=20) as response:
            result = json.load(response)
        evidence['observations'].append({'path': path, 'params': params, 'result': result})
        assert result.get('ok'), result
        return result

    def expect(name):
        deadline = time.monotonic() + 7
        while True:
            result = call(name='current')
            if result.get('screen') == name:
                return result
            if time.monotonic() > deadline:
                raise AssertionError(f'Expected {name}, got {result}')
            time.sleep(.2)

    def frame(label):
        name = 'native-input-' + label
        before = {p: p.stat().st_mtime_ns for p in (run / 'screenshots').rglob(name + '*') if p.is_file()}
        call('/screenshot', name=name)
        deadline = time.monotonic() + 8
        while True:
            for path in (run / 'screenshots').rglob(name + '*'):
                if path.is_file() and before.get(path) != path.stat().st_mtime_ns and path.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):
                    evidence['observations'].append({'screenshot': str(path.relative_to(run))})
                    return
            if time.monotonic() > deadline:
                raise TimeoutError('Screenshot was not captured: ' + name)
            time.sleep(.2)

    def title():
        result = call(name='title')
        width, height = result['width'], result['height']
        margin = max(8, min(20, width // 30))
        gap = max(4, min(12, width // 60))
        size = max(12, min(64, (width - margin * 2 - gap * 5) // 6, height // 4))
        time.sleep(2)
        return width, height, margin, gap, size

    def center(layout, index, count):
        width, height, margin, gap, size = layout
        capacity = max(1, (width - 2 * margin + gap) // (size + gap))
        rows = (count + capacity - 1) // capacity
        columns = (count + rows - 1) // rows
        first = count - (rows - 1) * columns
        row_count = first if index < first else columns
        column = index if index < first else (index - first) % columns
        row = 0 if index < first else 1 + (index - first) // columns
        return ((width - row_count * size - (row_count - 1) * gap) // 2 + column * (size + gap) + size / 2,
                max(0, height - margin - 24 - size) - (rows - 1 - row) * (size + gap) + size / 2)

    def click(point, button='left'):
        return call(name='click', x=point[0], y=point[1], button=button)

    try:
        for index, name in ((0, 'SelectWorldScreen'), (1, 'JoinMultiplayerScreen')):
            layout = title()
            point = center(layout, index, 3)
            click(point, 'right')
            time.sleep(.5)
            expect('TitleScreen')
            click(point)
            expect(name)
            time.sleep(1)
            frame(name)
            call(name='key', key='escape')
            expect('TitleScreen')
        layout = title()
        width, height, margin, gap, size = layout
        click((width - margin - min(40, size) / 2, margin + min(40, size) / 2))
        expect('AltManagerScreen')
        time.sleep(1)
        frame('accounts')
        call(name='key', key='escape')
        expect('TitleScreen')
        for index, name in ((3, 'ClickGuiScreen'), (4, 'NavigatorScreen')):
            layout = title()
            click(center(layout, 2, 3))
            time.sleep(2)
            frame('tools')
            click(center(layout, index, 7))
            expect(name)
            time.sleep(1)
            frame(name)
            call(name='key', key='escape')
            time.sleep(1)
            expect('TitleScreen')
        title()
        call(name='music')
        expect('MusicScreen')
        time.sleep(1)
        frame('music')
        call(name='key', key='escape')
        expect('TitleScreen')
        evidence['passed'] = True
    except Exception as exc:
        evidence.update(passed=False, error=str(exc))
        raise
    finally:
        (run / 'native-input-verification.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(json.dumps({'passed': evidence.get('passed'), 'error': evidence.get('error')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
