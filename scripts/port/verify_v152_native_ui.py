"""Exercise native menu/configuration/render lifecycles in an opt-in QA client.

Uses an existing local test save. Captures real rendered frames for separate
visual inspection; screenshot capture alone does not prove visual correctness.
Does not inject seeds, connect to servers or quit the client.
"""
import argparse
import json
from pathlib import Path
import shutil
import time
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--port', type=int, required=True)
    parser.add_argument('--world', default='seed-mining-qa')
    parser.add_argument('--viewpoint', type=float, nargs=5, metavar=('X', 'Y', 'Z', 'YAW', 'PITCH'),
                        help='Optional teleport in the local QA save for structure screenshots')
    args = parser.parse_args()
    run = args.run.resolve()
    if not (run / 'saves' / args.world / 'level.dat').is_file():
        raise ValueError('Existing local QA save required')
    token = (run / 'wurst/test-token.txt').read_text().strip()
    evidence = {'observations': [], 'visual_inspection_required': True}

    def call(action, path, params=None, method='POST'):
        url = f'http://127.0.0.1:{args.port}' + path
        if params:
            url += '?' + urllib.parse.urlencode(params)
        request = urllib.request.Request(url, method=method, headers={'X-Wurst-Token': token})
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.loads(response.read())
        evidence['observations'].append({'action': action, 'result': result})
        return result

    def screenshot(name):
        # Older Screenshot.grab adds screenshots/ itself and keeps the supplied
        # extensionless name. Accept both that form and modern asynchronous PNGs.
        before = {p: p.stat().st_mtime_ns for p in (run / 'screenshots').rglob(name + '*')
                  if p.is_file()}
        call(name, '/screenshot', {'name': name})
        deadline = time.monotonic() + 8
        while True:
            files = [p for p in (run / 'screenshots').rglob(name + '*') if p.is_file()]
            for path in sorted(files, key=lambda p: p.stat().st_mtime, reverse=True):
                if before.get(path) == path.stat().st_mtime_ns:
                    continue
                if path.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):
                    if path.suffix != '.png':
                        target = path.with_name(path.name + '.png')
                        shutil.copyfile(path, target)
                        path = target
                    evidence['observations'].append({'screenshot': str(path.relative_to(run))})
                    return
            if time.monotonic() >= deadline:
                raise TimeoutError('No PNG frame captured for ' + name)
            time.sleep(.2)

    try:
        assert not call('require-main-menu', '/player', method='GET').get('ok')
        for name, expected in (('singleplayer', 'SelectWorldScreen'),
                               ('multiplayer', 'JoinMultiplayerScreen'),
                               ('accounts', 'AltManagerScreen')):
            result = call('open-' + name, '/screen', {'name': name})
            assert result.get('screen') == expected, result
            time.sleep(1)
            screenshot('native-ui-' + name)
            call('close-' + name, '/screen', {'name': 'close'})
            time.sleep(1)
            result = call('return-' + name, '/screen', {'name': 'current'})
            assert result.get('screen') == 'TitleScreen', result

        assert call('enter', '/world/enter', {'name': args.world}).get('ok')
        deadline = time.monotonic() + 45
        while not call('wait-player', '/player', method='GET').get('ok'):
            if time.monotonic() >= deadline:
                raise TimeoutError('Client did not enter the QA world')
            time.sleep(1)
        call('resume', '/screen', {'name': 'none'})
        assert call('collector', '/hacks/enable', {'name': 'SeedCracker'}).get('enabled')
        if args.viewpoint:
            command = 'tp @s ' + ' '.join(format(n, '.8g') for n in args.viewpoint)
            assert call('local-viewpoint', '/command', {'cmd': command}).get('ok')
            time.sleep(5)
        for visible in (False, True):
            call('structure-visibility', '/setting',
                 {'hack': 'SeedCracker', 'name': 'Show structures', 'value': str(visible).lower()})
            time.sleep(1)
            screenshot('native-ui-structures-' + ('on' if visible else 'off'))
        call('open-config-command', '/command', {'cmd': 'seedcracker gui'})
        time.sleep(1)
        result = call('configuration-page', '/screen', {'name': 'current'})
        assert result.get('screen') == 'ClothConfigScreen', result
        screenshot('native-ui-configuration')
        call('close-config', '/screen', {'name': 'close'})
        call('resume-after-config', '/screen', {'name': 'none'})
        assert call('lsd-on', '/hacks/enable', {'name': 'LSD'}).get('enabled')
        time.sleep(1)
        screenshot('native-ui-lsd')
        assert not call('lsd-off', '/hacks/disable', {'name': 'LSD'}).get('enabled')
        call('save-and-exit', '/screen', {'name': 'disconnect'})
        status = call('cleared-session', '/screen', {'name': 'seed-status'})
        assert status['seed'] is None and not any(status[k] for k in
            ('predictions', 'pathing', 'collector', 'structureData', 'hashedSeed')), status
        assert not call('no-player', '/player', method='GET').get('ok')
        time.sleep(2)
        screenshot('native-ui-title')
        evidence['passed'] = True
    except Exception as error:
        evidence.update(passed=False, error=str(error))
        raise
    finally:
        (run / 'native-ui-verification.json').write_text(
            json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('PASS menu/configuration/render lifecycle; inspect captured PNGs separately')


if __name__ == '__main__':
    main()
