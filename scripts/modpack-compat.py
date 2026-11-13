#!/usr/bin/env python3
"""Prepare a pinned Modrinth pack in an isolated folder for compatibility tests.

Downloads are hash verified and resumable. This does not modify a user's launcher
instances or silently disable pack mods. Use inventory.json to retain exact inputs.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import ssl
import sys
import tempfile
import time
import subprocess
import urllib.request
import zipfile


def tls_context():
    context = ssl.create_default_context()
    if sys.platform == 'win32':
        # The bundled Python cert bundle does not include the Windows trust store.
        for store in ('ROOT', 'CA'):
            for cert, encoding, _trust in ssl.enum_certificates(store):
                if encoding == 'x509_asn':
                    try:
                        context.load_verify_locations(cadata=ssl.DER_cert_to_PEM_cert(cert))
                    except ssl.SSLError:
                        pass
    return context


def safe_path(root, name):
    path = PurePosixPath(name)
    if path.is_absolute() or '..' in path.parts or '\\' in name or ':' in name:
        raise ValueError('Unsafe pack path: ' + name)
    target = root.joinpath(*path.parts)
    if not target.resolve().is_relative_to(root.resolve()):
        raise ValueError('Pack path leaves instance: ' + name)
    return target


def digest(path, algorithm='sha512'):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def download(urls, destination, hashes, context):
    algorithm = 'sha512' if 'sha512' in hashes else 'sha1'
    expected = hashes[algorithm]
    if destination.is_file() and digest(destination, algorithm) == expected:
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    error = None
    for attempt in range(3):
        for url in urls:
            if not url.startswith('https://'):
                raise ValueError('Pack download must use HTTPS: ' + url)
            fd, name = tempfile.mkstemp(prefix=destination.name + '.', suffix='.part', dir=destination.parent)
            temporary = Path(name)
            try:
                request = urllib.request.Request(url, headers={'User-Agent': 'WurstBPlus-Compatibility-Tests/1'})
                with os.fdopen(fd, 'wb') as out, urllib.request.urlopen(request, timeout=45, context=context) as response:
                    shutil.copyfileobj(response, out)
                if digest(temporary, algorithm) != expected:
                    raise ValueError('Hash mismatch: ' + destination.name)
                temporary.replace(destination)
                return
            except (OSError, ValueError) as exc:
                error = exc
            finally:
                temporary.unlink(missing_ok=True)
        time.sleep(attempt + 1)
    raise RuntimeError(str(destination) + ': ' + str(error))


def inventory_mods(instance):
    result = []

    def inspect(data, source):
        with zipfile.ZipFile(data) as jar:
            if 'fabric.mod.json' not in jar.namelist():
                return
            # Fabric's metadata reader accepts raw control characters in strings.
            metadata = json.loads(jar.read('fabric.mod.json'), strict=False)
            result.append({'source': source, 'id': metadata['id'], 'version': metadata['version'],
                           'depends': metadata.get('depends', {}), 'breaks': metadata.get('breaks', {}),
                           'mixins': metadata.get('mixins', [])})
            for nested in metadata.get('jars', []):
                name = nested['file']
                if name in jar.namelist():
                    inspect(io.BytesIO(jar.read(name)), source + '!/' + name)

    for path in sorted((instance / 'mods').glob('*.jar')):
        inspect(path, path.name)
    return result


def prepare(pack, output, expected):
    if digest(pack) != expected:
        raise ValueError('Pack SHA-512 does not match pinned release')
    output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(pack) as archive:
        index = json.loads(archive.read('modrinth.index.json'))
        (output / 'modrinth.index.json').write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding='utf-8')
        for entry in archive.infolist():
            for prefix in ('overrides/', 'client-overrides/'):
                if entry.filename.startswith(prefix) and not entry.is_dir():
                    target = safe_path(output, entry.filename[len(prefix):])
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with archive.open(entry) as source, target.open('wb') as out:
                        shutil.copyfileobj(source, out)
    files = [f for f in index['files'] if f.get('env', {}).get('client') != 'unsupported']
    context = tls_context()
    errors = []
    with ThreadPoolExecutor(max_workers=8) as executor:
        futures = {executor.submit(download, f['downloads'], safe_path(output, f['path']),
                                   f['hashes'], context): f['path'] for f in files}
        for count, future in enumerate(as_completed(futures), 1):
            try:
                future.result()
            except Exception as error:
                errors.append(futures[future] + ': ' + str(error))
            if count % 25 == 0 or count == len(files):
                print(f'Downloaded/verified {count}/{len(files)}; errors={len(errors)}', flush=True)
    if errors:
        raise RuntimeError('\n'.join(errors))
    mods = inventory_mods(output)
    inventory = {'name': index['name'], 'version': index['versionId'], 'dependencies': index['dependencies'],
                 'pack_sha512': expected, 'client_files': len(files), 'mods': mods}
    (output / 'inventory.json').write_text(json.dumps(inventory, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'Prepared {len(list((output / "mods").glob("*.jar")))} top-level mod jars; {len(mods)} total Fabric metadata entries')


def windows_x64_library(library):
    # Mojang lists LWJGL ARM64/x86 classifiers with the same Windows OS rule.
    # Including all of them would overwrite the extracted x64 DLLs.
    classifier = library['name'].split(':')[3:]
    if classifier and classifier[0].startswith('natives-') and classifier[0] != 'natives-windows':
        return False
    result = not library.get('rules')
    for rule in library.get('rules', []):
        os_rule = rule.get('os', {})
        if os_rule.get('name', 'windows') != 'windows' or os_rule.get('arch', 'x86_64') not in ('x86_64', 'amd64'):
            continue
        if rule.get('features'):
            continue
        result = rule['action'] == 'allow'
    return result


def prepare_runtime(instance):
    """Resolve the official client, assets and pack-pinned Fabric loader."""
    context = tls_context()
    runtime = instance.parent / 'runtime'
    runtime.mkdir(exist_ok=True)
    index = json.loads((instance / 'modrinth.index.json').read_text('utf-8'))
    mc, loader = (index['dependencies'][k] for k in ('minecraft', 'fabric-loader'))

    def get_json(url):
        request = urllib.request.Request(url, headers={'User-Agent': 'WurstBPlus-Compatibility-Tests/1'})
        with urllib.request.urlopen(request, context=context, timeout=45) as response:
            return json.load(response)

    def fetch(url, path, sha1=None):
        if sha1:
            download([url], path, {'sha1': sha1}, context)
            return
        # Fabric Maven profiles omit checksums. TLS authenticates the source;
        # record the acquired artifact hash and verify it on subsequent runs.
        sidecar = path.with_suffix(path.suffix + '.sha512')
        if path.is_file() and sidecar.is_file() and digest(path) == sidecar.read_text():
            return
        path.parent.mkdir(parents=True, exist_ok=True)
        request = urllib.request.Request(url, headers={'User-Agent': 'WurstBPlus-Compatibility-Tests/1'})
        with urllib.request.urlopen(request, context=context, timeout=45) as response, path.open('wb') as out:
            shutil.copyfileobj(response, out)
        sidecar.write_text(digest(path), encoding='ascii')

    manifest = get_json('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')
    version = get_json(next(v['url'] for v in manifest['versions'] if v['id'] == mc))
    fabric = get_json(f'https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/profile/json')
    (runtime / 'vanilla.json').write_text(json.dumps(version, indent=2), encoding='utf-8')
    (runtime / 'fabric.json').write_text(json.dumps(fabric, indent=2), encoding='utf-8')

    artifacts = {}
    for lib in version['libraries'] + fabric['libraries']:
        if not windows_x64_library(lib):
            continue
        downloads = lib.get('downloads', {})
        if downloads.get('artifact'):
            artifact = downloads['artifact']
        else:
            group, name, release, *classifier = lib['name'].split(':')
            suffix = '-' + classifier[0] if classifier else ''
            relative = f'{group.replace(".", "/")}/{name}/{release}/{name}-{release}{suffix}.jar'
            artifact = {'path': relative, 'url': lib.get('url', 'https://libraries.minecraft.net/').rstrip('/') + '/' + relative}
        artifacts[artifact['path']] = artifact
        if 'windows' in lib.get('natives', {}):
            classifier = lib['natives']['windows'].replace('${arch}', '64')
            artifact = downloads['classifiers'][classifier]
            artifacts[artifact['path']] = artifact
    with ThreadPoolExecutor(max_workers=8) as executor:
        futures = [executor.submit(fetch, a['url'], safe_path(runtime / 'libraries', a['path']), a.get('sha1'))
                   for a in artifacts.values()]
        for future in as_completed(futures):
            future.result()
    client = runtime / f'minecraft-{mc}.jar'
    fetch(version['downloads']['client']['url'], client, version['downloads']['client']['sha1'])
    natives = runtime / 'natives'
    natives.mkdir(exist_ok=True)
    for artifact in artifacts.values():
        with zipfile.ZipFile(safe_path(runtime / 'libraries', artifact['path'])) as archive:
            for name in archive.namelist():
                if name.endswith('.dll') and (name.startswith('windows/x64/') or '/' not in name):
                    (natives / PurePosixPath(name).name).write_bytes(archive.read(name))
    assets = runtime / 'assets'
    asset_index = version['assetIndex']
    asset_file = assets / 'indexes' / (asset_index['id'] + '.json')
    fetch(asset_index['url'], asset_file, asset_index['sha1'])
    objects = json.loads(asset_file.read_text())['objects']
    with ThreadPoolExecutor(max_workers=8) as executor:
        futures = [executor.submit(fetch, f'https://resources.download.minecraft.net/{h[:2]}/{h}',
                                   assets / 'objects' / h[:2] / h, h)
                   for h in {o['hash'] for o in objects.values()}]
        for count, future in enumerate(as_completed(futures), 1):
            future.result()
            if count % 500 == 0 or count == len(futures):
                print(f'Runtime assets {count}/{len(futures)}', flush=True)
    profile = {'minecraft': mc, 'loader': loader, 'main': fabric['mainClass'],
               'classpath': [str(safe_path(runtime / 'libraries', p)) for p in artifacts] + [str(client)],
               'assets': str(assets), 'asset_index': asset_index['id'], 'natives': str(natives)}
    (runtime / 'launch.json').write_text(json.dumps(profile, indent=2), encoding='utf-8')
    print('Prepared official runtime:', mc, 'Fabric', loader, flush=True)


def launch(instance, java_home, log):
    """Start an offline local test client; no multiplayer credentials are read."""
    runtime = instance.parent / 'runtime'
    profile = json.loads((runtime / 'launch.json').read_text('utf-8'))
    java = java_home / 'bin' / ('java.exe' if sys.platform == 'win32' else 'java')
    temporary = runtime / 'java-temp'
    temporary.mkdir(exist_ok=True)
    arguments = ['-Xms1G', '-Xmx8G', '-Djava.io.tmpdir=' + str(temporary),
                 '-Djna.tmpdir=' + str(temporary), '-Dorg.lwjgl.system.SharedLibraryExtractPath=' + str(temporary / 'lwjgl'),
                 '-Djava.library.path=' + profile['natives'], '-Dminecraft.launcher.brand=WurstCompatibilityTest',
                 '-cp', os.pathsep.join(profile['classpath']), profile['main'],
                 '--username', 'WurstCompat', '--version', 'Fabric-' + profile['minecraft'],
                 '--gameDir', str(instance), '--assetsDir', profile['assets'], '--assetIndex', profile['asset_index'],
                 '--uuid', 'b37ef14d7b2a4af18efb37d9bdfe0fac', '--accessToken', '0', '--userType', 'legacy',
                 '--versionType', 'release', '--width', '1280', '--height', '720']
    args_file = runtime / 'launch.args'
    args_file.write_text('\n'.join('"' + arg.replace('\\', '\\\\').replace('"', '\\"') + '"' for arg in arguments), encoding='utf-8')
    log.parent.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    # Keep mod-created per-user caches in this disposable test runtime too.
    for key in ('APPDATA', 'LOCALAPPDATA'):
        directory = runtime / key.lower()
        directory.mkdir(exist_ok=True)
        environment[key] = str(directory)
    # Child JVMs (e.g. Crash Assistant) must use the writable temporary directory.
    environment['JAVA_TOOL_OPTIONS'] = environment.get('JAVA_TOOL_OPTIONS', '') + ''.join(
        ' "-D' + key + '=' + str(temporary) + '"' for key in ('java.io.tmpdir', 'jna.tmpdir'))
    with log.open('wb') as out:
        process = subprocess.Popen([str(java), '@' + str(args_file)], cwd=instance, stdin=subprocess.DEVNULL,
                                   stdout=out, stderr=subprocess.STDOUT,
                                   env=environment,
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        (runtime / 'client.pid').write_text(str(process.pid), encoding='ascii')
        print('Test client PID:', process.pid, 'Log:', log, flush=True)
        return process.wait()


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group()
    source.add_argument('--pack', type=Path)
    source.add_argument('--profile', type=Path, help='Pinned pack profile, e.g. scripts/modpack-profiles/*.json')
    parser.add_argument('--sha512')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--runtime', action='store_true')
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--log', type=Path)
    args = parser.parse_args()
    instance = args.output.resolve()
    if args.profile:
        profile = json.loads(args.profile.read_text('utf-8'))
        args.pack = instance.parent / 'pack.mrpack'
        args.sha512 = profile['pack_sha512']
        download([profile['pack_url']], args.pack, {'sha512': args.sha512}, tls_context())
    if args.pack:
        if not args.sha512:
            parser.error('--pack requires --sha512')
        prepare(args.pack.resolve(), instance, args.sha512)
    if args.profile:
        # These are required by the pack's own installation screen, but its
        # Modrinth distribution cannot redistribute them. Fetch from the author.
        for item in profile.get('manual_pack_requirements', []):
            download([item['url']], safe_path(instance / 'mods', item['file']),
                     {'sha512': item['sha512']}, tls_context())
    if args.runtime:
        prepare_runtime(instance)
    if args.java_home:
        return launch(instance, args.java_home.resolve(), (args.log or instance.parent / 'console.log').resolve())
    return 0


if __name__ == '__main__':
    sys.exit(main())
