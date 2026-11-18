"""Extract Cloth's full config-screen API for Forge versions without a release.

The retained API has no NeoForge executable calls. Only the two distribution
annotations change namespace; mod entrypoints and AutoConfig are not bundled.
The official NeoForge artifact and Gradle module must match the pinned hashes.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PINS = {
    '18.0.145': '4977fdd970fbb6be04fa137c74119b363ed9074b98c3624436a8cd8f03eaf14c',
    '19.0.147': 'af8e0a2f8519c22450527aad538fb2306600182650f481876fd14d2c59b9d340',
    '20.0.149': 'fb5a2e83f5e48887003c816d781f2fefc36986d4541dd926e14b8d9d5adac47e',
    '21.11.153': 'cf0d6218092579491c4006b98e1ee749c16a724998ba4ab8bc6f7bfa75b25bc5',
    '26.1.154': 'd9147c8549fedad478bd18e2ddeef172ef290a081f80bd4d242d54e027ecd9d8',
    '26.2.155': '650cdc79fcb3332dd08d75634443b93b012dba49b748359a4a97f0975bc55c31',
    '26.3.159': '24adcf323c6a439226aced11d2cd4f8e6e629b82de6f1c7d71a7b9ba37ebf81d',
}


def annotations(data):
    """Rewrite UTF8 constants, preserving all bytecode and constant-pool indices."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('Not a class')
    count = struct.unpack_from('>H', data, 8)[0]
    out, pos, index = bytearray(data[:10]), 10, 1
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
             11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[pos]
        out.append(tag)
        pos += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, pos)[0]
            value = data[pos + 2:pos + 2 + length]
            refs = re.findall(rb'net/neoforged/[\w/$]+', value)
            if any(r not in (b'net/neoforged/api/distmarker/Dist',
                             b'net/neoforged/api/distmarker/OnlyIn') for r in refs):
                raise ValueError('Executable NeoForge dependency in Cloth core')
            value = value.replace(b'net/neoforged/api/distmarker/',
                                  b'net/minecraftforge/api/distmarker/')
            out += struct.pack('>H', len(value)) + value
            pos += 2 + length
        else:
            length = sizes[tag]
            out += data[pos:pos + length]
            pos += length
            if tag in (5, 6):
                index += 1
        index += 1
    return bytes(out) + data[pos:]


def extract(version, artifact):
    digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
    if digest != PINS[version]:
        raise ValueError('Official Cloth artifact hash mismatch: ' + digest)
    dest = ROOT / 'native/cloth-config' / version
    dest.mkdir(parents=True, exist_ok=True)
    output = dest / ('cloth-config-core-' + version + '-forge.jar')
    with zipfile.ZipFile(artifact) as src, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as jar:
        for name in sorted(src.namelist()):
            if name.endswith('/') or not name.startswith((
                    'me/shedaniel/clothconfig2/', 'me/shedaniel/math/', 'assets/')):
                continue
            data = src.read(name)
            if name.endswith('.class'):
                data = annotations(data)
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            jar.writestr(entry, data)
    manifest = {'upstream': 'me.shedaniel.cloth:cloth-config-neoforge:' + version,
                'upstream_sha256': digest, 'file': output.name,
                'sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
                'changes': 'Full config-screen and math API; distribution annotations mapped to Forge. No loader entrypoints or AutoConfig.'}
    (dest / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(output.relative_to(ROOT))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', choices=PINS)
    parser.add_argument('artifact', type=Path)
    args = parser.parse_args()
    extract(args.version, args.artifact)
