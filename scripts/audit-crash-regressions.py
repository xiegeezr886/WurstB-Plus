#!/usr/bin/env python3
"""Check all independent builds and optionally release jars for known crashes.

Uses only Python's standard library. Run from any directory:
    python scripts/audit-crash-regressions.py
    python scripts/audit-crash-regressions.py --jar build/release/mod.jar
"""
import argparse
import hashlib
import io
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = 'mixins.baritone.json'
STUB = 'baritone/launch/BaritoneForgeModXD.class'
FLAT_NEO = {'1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6', '1.21', '1.21.1'}


def projects(root=ROOT):
    result = [root, root / 'fabric', root / 'neoforge']
    for base in ('versions', 'fabric/versions', 'neoforge/versions'):
        result.extend(sorted(p for p in (root / base).iterdir()
                             if (p / 'build.gradle').is_file()))
    return result


def annotations(text, name):
    """Read nested annotation arguments, including quoted method descriptors."""
    for match in re.finditer(r'@' + name + r'\s*\(', text):
        depth, quote, escaped, i = 1, None, False, match.end()
        while depth and i < len(text):
            char = text[i]
            if quote:
                if escaped:
                    escaped = False
                elif char == '\\':
                    escaped = True
                elif char == quote:
                    quote = None
            elif char in ('"', "'"):
                quote = char
            elif char == '(':
                depth += 1
            elif char == ')':
                depth -= 1
            i += 1
        if depth:
            raise ValueError('Unterminated @' + name)
        yield text[match.end():i - 1]


def compare_flat_config(own, bundled):
    """Only the Java level may be raised; all mixins/settings must match."""
    own, bundled = dict(own), dict(bundled)
    own_level = int(own.pop('compatibilityLevel').split('_')[1])
    bundled_level = int(bundled.pop('compatibilityLevel').split('_')[1])
    return own == bundled and own_level >= bundled_level


def used_accessor_gaps(sources, registered):
    """Find runtime references to unregistered accessor/invoker interfaces.

    Keys are Java paths relative to net/wurstclient. Ignore imports alone,
    comments and same-named helpers outside the mixin package.
    """
    code = {name: re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"', '',
                         text, flags=re.S) for name, text in sources.items()}
    registered = {name.replace('/', '.') for name in registered}
    gaps = []
    for path, text in code.items():
        if not path.startswith('mixin/') or not re.search(r'@(Accessor|Invoker)\b', text):
            continue
        name = path[len('mixin/'):-len('.java')].replace('/', '.')
        if name in registered:
            continue
        simple = name.rsplit('.', 1)[-1]
        fqcn = 'net.wurstclient.mixin.' + name
        package = fqcn.rsplit('.', 1)[0]
        for other, body in code.items():
            if other == path or simple not in body:
                continue
            imported = re.search(r'\bimport\s+' + re.escape(fqcn) + r'\s*;', body)
            same_package = re.search(r'\bpackage\s+' + re.escape(package) + r'\s*;', body)
            body = re.sub(r'\b(?:import|package)\s+[^;]*;', '', body)
            if re.search(r'\b' + re.escape(fqcn) + r'\b', body) or (
                    (imported or same_package) and re.search(r'\b' + re.escape(simple) + r'\b', body)):
                gaps.append(name)
                break
    return sorted(gaps)


def check_archive(archive):
    errors = []
    names = set(archive.namelist())
    nested = [n for n in names if n.startswith('META-INF/')
              and 'baritone' in n.lower() and n.endswith('.jar')]
    if nested and CONFIG in names:
        errors.append('outer mixins.baritone.json shadows nested Baritone')
    for name in nested:
        with zipfile.ZipFile(io.BytesIO(archive.read(name))) as inner:
            inner_names = set(inner.namelist())
            # The old 26.1-to-26.2 port has broken chat/GUI/entity references.
            # Checking its rewritten Minecraft metadata alone cannot catch this.
            if ('1.18.0-26.2' in name or
                    'baritone/api/utils/LegacyRenderPipelineBuilder.class' in inner_names):
                errors.append(name + ': obsolete Baritone 26.2 port; use official 1.19.0')
            configs = [CONFIG]
            if 'fabric.mod.json' in inner_names:
                metadata = json.loads(inner.read('fabric.mod.json'))
                configs = [entry if isinstance(entry, str) else entry['config']
                           for entry in metadata.get('mixins', [])]
            if not configs or any(config not in inner_names for config in configs):
                errors.append(name + ': missing Baritone mixin config')
            for config in configs:
                if config != CONFIG and config in names:
                    errors.append('outer ' + config + ' shadows nested Baritone')
    if STUB in names and 'META-INF/neoforge.mods.toml' in names:
        if not re.search(rb'modId\s*=\s*["\']baritoe["\']',
                         archive.read('META-INF/neoforge.mods.toml')):
            errors.append('dangling Baritone @Mod entrypoint')
    # Derive shader requirements from the classes that actually ship.
    if 'net/wurstclient/WurstShaderPipelines.class' in names:
        pipeline = archive.read('net/wurstclient/WurstShaderPipelines.class')
        if b'wurst:core/fogless_lines' in pipeline:
            for suffix in ('vsh', 'fsh'):
                name = 'assets/wurst/shaders/core/fogless_lines.' + suffix
                if name not in names:
                    errors.append('missing shader ' + name)
    if 'fabric.mod.json' in names:
        meta = json.loads(archive.read('fabric.mod.json'))
        if meta.get('id') == 'wurstpenguin':
            extras = [j['file'] for j in meta.get('jars', [])
                      if 'mixinextras' in j.get('file', '').lower()]
            if not extras:
                errors.append('Fabric release does not embed MixinExtras')
            for name in extras:
                if name not in names:
                    errors.append('missing nested ' + name)
                    continue
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as inner:
                    version = json.loads(inner.read('fabric.mod.json'))['version']
                if tuple(map(int, version.split('.'))) < (0, 5, 5):
                    errors.append('embedded MixinExtras is older than 0.5.5')
    return errors


def check_sources(root=ROOT):
    errors = []
    counts = dict(projects=0, colors=0, translators=0, rotations=0, reach=0,
                  constants=0, gamma=0, fabric=0, flat_baritone=0, shaders=0,
                  native_caches=0, encryption=0, accessor_projects=0, sodium=0)
    for project in projects(root):
        counts['projects'] += 1
        java = project / 'src/main/java/net/wurstclient'
        resources = project / 'src/main/resources'
        build = (project / 'build.gradle').read_text('utf-8-sig')
        props = dict(re.findall(r'^([\w.]+)=(.*)$',
                               (project / 'gradle.properties').read_text('utf-8-sig'), re.M))

        def fail(message):
            errors.append(str(project.relative_to(root)) + ': ' + message)

        if props.get('minecraft_version') == '26.2':
            if '1.19.0-26.2' not in build or '1.18.0-26.2' in build:
                fail('Minecraft 26.2 requires native Baritone 1.19.0-26.2')
            metadata = resources / 'META-INF/jarjar/metadata.json'
            if metadata.exists() and '1.18.0-26.2' in metadata.read_text('utf-8'):
                fail('JarJar metadata still references the obsolete Baritone 26.2 port')

        color = java / 'hud2/nanovg/NanoVGContext.java'
        if color.exists():
            counts['colors'] += 1
            text = color.read_text('utf-8-sig')
            if 'NVGColor.create()' in text and re.search(r'\bcolor\s*\.\s*free\s*\(', text):
                fail('invalid free of GC-managed NVGColor')
        native_loader = java / 'hud2/nanovg/NanoVGNativeLoader.java'
        if native_loader.exists():
            counts['native_caches'] += 1
            text = native_loader.read_text('utf-8-sig')
            if not all(token in text for token in (
                    'LIB_VERSION, classifier()', 'MessageDigest.isEqual',
                    'Files.createTempFile', 'StandardCopyOption.ATOMIC_MOVE',
                    'NOFOLLOW_LINKS')):
                fail('native cache lacks platform isolation, integrity or atomic extraction')
        encryption = ((root / 'common/src/main/java/net/wurstclient/altmanager/Encryption.java')
                      if project == root else java / 'altmanager/Encryption.java')
        text = encryption.read_text('utf-8-sig')
        counts['encryption'] += 1
        if not all(token in text for token in (
                'setObjectInputFilter', 'info.depth()', 'info.references()',
                'info.arrayLength()', 'info.streamBytes()', 'readLegacyBytes',
                'type == BigInteger.class', 'Status.REJECTED')):
            fail('legacy key deserialization lacks type and resource bounds')
        translator = (java / 'WurstTranslator.java').read_text('utf-8-sig')
        counts['translators'] += 1
        if 'string == null && mcEnglish != null' not in translator or 'if(mcEnglish == null)' not in translator:
            fail('missing startup translation guard')
        hud = (java / 'hud2/HudManager.java').read_text('utf-8-sig')
        if re.search(r'elementDisplayNames\.put\([^;]*getTranslatedName\(', hud):
            fail('HUD registration eagerly translates before reload')
        rotation = java / 'opal/rotation/RotationHelper.java'
        if rotation.exists():
            counts['rotations'] += 1
            text = rotation.read_text('utf-8-sig')
            if not re.search(r'void preTick\(\)\s*\{\s*if\(WurstClient.MC == null\) return;', text):
                fail('preTick accesses Minecraft before initialization')
            text = (java / 'mixin/OpenOpalInputMixin.java').read_text('utf-8-sig')
            body = text.split('void opalPreTick(CallbackInfo ci)', 1)[1].split('var hax', 1)[0]
            if not (body.index('getHax() == null') < body.index('RotationHelper.preTick()')
                    < body.index('if (!WurstClient.INSTANCE.isEnabled())')):
                fail('tick guard ordering breaks initialization or disabled cleanup')
        for source in java.rglob('*.java'):
            text = source.read_text('utf-8-sig')
            for annotation in annotations(text, 'ModifyConstant'):
                counts['constants'] += 1
                # FastBreak deliberately checks all 3 cooldown writes. It has no
                # known third-party conflict; preserve its version-drift contract.
                required = (source.name == 'FastBreakCooldownMixin.java'
                            and re.search(r'\brequire\s*=\s*3\b', annotation)
                            and 'startDestroyBlock' in annotation
                            and 'continueDestroyBlock' in annotation)
                if not required and not re.search(r'\brequire\s*=\s*0\b', annotation):
                    fail(str(source.relative_to(project)) + ': constant modifier must be optional')
            if source.name == 'ReachLegacyRendererMixin.java':
                counts['reach'] += 1
        gamma = java / 'mixin/OptionsMixin.java'
        configs = [f for f in resources.glob('*mixins*.json') if f.name != CONFIG]
        registered = {name for f in configs for key in ('client', 'mixins')
                      for name in json.loads(f.read_text('utf-8-sig')).get(key, [])}
        sources = {f.relative_to(java).as_posix(): f.read_text('utf-8-sig')
                   for f in java.rglob('*.java')}
        counts['accessor_projects'] += 1
        for name in used_accessor_gaps(sources, registered):
            fail('used accessor/invoker is not registered: ' + name)
        if project == root / 'fabric/versions/1.21.11':
            counts['sodium'] += 1
            for name, target in (
                    ('AbstractBlockRenderContextMixin', 'render.model.AbstractBlockRenderContext'),
                    ('DefaultFluidRendererMixin', 'render.chunk.compile.pipeline.DefaultFluidRenderer')):
                path = java / ('mixin/sodium/' + name + '.java')
                if ('sodium.' + name not in {n.replace('/', '.') for n in registered}
                        or not path.exists()
                        or 'net.caffeinemc.mods.sodium.client.' + target not in path.read_text('utf-8-sig')):
                    fail('missing modern Sodium X-Ray adapter: ' + name)
            plugin = (java / 'mixin/WurstMixinConfigPlugin.java').read_text('utf-8-sig')
            if 'Class.forName' in plugin or 'getResource(classResource)' not in plugin:
                fail('optional Sodium eligibility must not load target classes')
        counts['gamma'] += 1
        if not gamma.exists() or 'OptionsMixin' not in registered:
            fail('missing gamma save mixin or registration')
        elif 'finally' not in gamma.read_text('utf-8-sig'):
            fail('gamma save mixin does not restore rendering state on failure')
        cfg = resources / CONFIG
        flat = project.parent == root / 'neoforge/versions' and project.name in FLAT_NEO
        if cfg.exists() and not flat:
            fail('outer Baritone config shadows nested mod')
        if flat:
            counts['flat_baritone'] += 1
            jars = list((project / 'libs').glob('baritone*.jar'))
            if not cfg.exists() or len(jars) != 1:
                fail('missing authoritative flat Baritone input/config')
            else:
                with zipfile.ZipFile(jars[0]) as jar:
                    bundled = json.loads(jar.read(CONFIG))
                if not compare_flat_config(json.loads(cfg.read_text('utf-8-sig')), bundled):
                    fail('flat Baritone config differs from its actual dependency')
            if project.name in {'1.20.5', '1.20.6', '1.21', '1.21.1'}:
                if not re.search(r'from\(\{ zipTree\([^\n]+\}\) \{[^}]*exclude '
                                 r"'META-INF/neoforge.mods.toml', '" + re.escape(STUB) + "'", build):
                    fail('Baritone metadata/stub exclusions must be scoped to zipTree')
        fabric_meta = resources / 'fabric.mod.json'
        if fabric_meta.exists():
            counts['fabric'] += 1
            meta = json.loads(fabric_meta.read_text('utf-8-sig'))
            if meta['depends']['fabricloader'] != '>=0.17.0':
                fail('Fabric Loader minimum must be >=0.17.0')
            if not re.search(r'include\((?:modImplementation|implementation)\("io.github.llamalad7:mixinextras-fabric:0\.5\.5"\)\)', build):
                fail('MixinExtras 0.5.5 must be embedded')
        pipeline = java / 'WurstShaderPipelines.java'
        if pipeline.exists() and 'wurst:core/fogless_lines' in pipeline.read_text('utf-8-sig'):
            counts['shaders'] += 1
            for suffix in ('vsh', 'fsh'):
                if not (resources / ('assets/wurst/shaders/core/fogless_lines.' + suffix)).is_file():
                    fail('missing fogless_lines.' + suffix)
    # Native code must never flow through the resource-pack manager. The digest
    # is compiled into the owning module alongside the loader, not read from packs.
    native_sources = [root / 'src/main/java/net/wurstclient/background/FfmpegNatives.java',
                      root / 'src/main/java/net/wurstclient/render/skia/SkikoNatives.java']
    pinned = ''.join(f.read_text('utf-8-sig') for f in native_sources)
    for source in native_sources:
        text = source.read_text('utf-8-sig')
        if 'getResourceManager()' in text or 'BundledNativeFiles.extract' not in text:
            errors.append(str(source.relative_to(root)) + ': native source must be module-owned')
    for folder, pattern in [('ffmpeg', '*.dll'), ('skiko', '*')]:
        for resource in (root / 'src/main/resources/assets/wurst' / folder).glob(pattern):
            if resource.is_file() and resource.suffix in {'.dll', '.dat'}:
                if hashlib.sha256(resource.read_bytes()).hexdigest() not in pinned:
                    errors.append(str(resource.relative_to(root)) + ': missing trusted SHA256 pin')
    return errors, counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, action='append', default=[])
    args = parser.parse_args()
    errors, counts = check_sources()
    for path in args.jar:
        with zipfile.ZipFile(path) as archive:
            errors.extend(str(path) + ': ' + e for e in check_archive(archive))
    for error in errors:
        print('FAIL:', error)
    print(('FAIL' if errors else 'PASS') + ': ' + ', '.join(f'{k}={v}' for k, v in counts.items()))
    return bool(errors)


if __name__ == '__main__':
    sys.exit(main())
