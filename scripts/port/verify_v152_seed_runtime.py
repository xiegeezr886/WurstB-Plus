"""Exercise a running opt-in native QA client against an existing local test save.

Without --seed this tests real collection and logout only. --seed explicitly
injects a test result to exercise validation and mining handoff; it must never
be reported as successful automatic cracking. Authentication stays local.
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
    parser.add_argument('--world', default='seed-mining-qa')
    parser.add_argument('--seed', type=int, help='Explicit known-seed test injection, not cracking')
    parser.add_argument('--require-structures', action='store_true')
    args = parser.parse_args()
    run = args.run.resolve()
    if not (run / 'saves' / args.world / 'level.dat').is_file():
        raise ValueError('Existing local QA save required')
    token = (run / 'wurst/test-token.txt').read_text().strip()
    evidence = {'test_result_injected': args.seed is not None, 'observations': []}

    def call(action, path, params=None, method='POST'):
        url = f'http://127.0.0.1:{args.port}' + path
        if params:
            url += '?' + urllib.parse.urlencode(params)
        request = urllib.request.Request(url, method=method, headers={'X-Wurst-Token': token})
        with urllib.request.urlopen(request, timeout=20) as response:
            result = json.loads(response.read())
        evidence['observations'].append({'action': action, 'result': result})
        return result

    try:
        assert call('enter', '/world/enter', {'name': args.world}).get('ok')
        deadline = time.monotonic() + 45
        while not call('wait-player', '/player', method='GET').get('ok'):
            if time.monotonic() >= deadline:
                raise TimeoutError('Client did not enter the QA world')
            time.sleep(1)
        call('resume', '/screen', {'name': 'none'})
        assert call('enable-collector', '/hacks/enable', {'name': 'SeedCracker'}).get('enabled')
        deadline = time.monotonic() + 25
        while True:
            status = call('collection', '/screen', {'name': 'seed-status'})
            assert status['collector'] and status['hashedSeed'], status
            if not args.require_structures or status['structureData'] > 0:
                break
            if time.monotonic() >= deadline:
                raise TimeoutError('No real structure data collected in the QA save')
            time.sleep(1)
        if args.seed is not None:
            before = status['seed']
            call('wrong-result', '/screen', {'name': 'seed-test-result', 'seed': args.seed ^ 1})
            time.sleep(.3)
            assert call('reject-result', '/screen', {'name': 'seed-status'})['seed'] == before
            call('preview-only', '/setting', {'hack': 'SeedMine', 'name': 'Preview only', 'value': 'true'})
            call('auto-handoff', '/setting', {'hack': 'SeedCracker', 'name': 'Mine when cracked', 'value': 'true'})
            call('explicit-test-result', '/screen', {'name': 'seed-test-result', 'seed': args.seed})
            time.sleep(3)
            status = call('preview', '/screen', {'name': 'seed-status'})
            assert status['seed'] == str(args.seed) and status['predictions'] > 0, status
        call('save-and-exit', '/screen', {'name': 'disconnect'})
        status = call('cleared-session', '/screen', {'name': 'seed-status'})
        assert status['seed'] is None and status['predictions'] == 0, status
        assert not any(status[k] for k in ('pathing', 'collector', 'structureData', 'hashedSeed')), status
        assert not call('no-player', '/player', method='GET').get('ok')
        evidence['passed'] = True
    except Exception as error:
        evidence['passed'] = False
        evidence['error'] = str(error)
        raise
    finally:
        (run / 'native-seed-verification.json').write_text(
            json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('PASS native collection, session validation and saved exit; injected test result=' + str(args.seed is not None))


if __name__ == '__main__':
    main()
