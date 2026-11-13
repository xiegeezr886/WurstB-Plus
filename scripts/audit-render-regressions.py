#!/usr/bin/env python3
"""Static render regression gate for the 66 v1.5.1 projects.

Checks lifecycle and GL state invariants, not visual correctness. Run the real
OpenGL tests separately with run-render263-tests.py --project <project>.
The root v1.6.0 project is intentionally excluded.
"""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def projects(root=ROOT):
    yield root / 'fabric'
    yield root / 'neoforge'
    for parent in ('fabric/versions', 'neoforge/versions', 'versions'):
        yield from sorted(p for p in (root / parent).iterdir() if (p / 'build.gradle').is_file())


def method(source, signature):
    """Extract a balanced Java body (these render methods have no brace literals)."""
    start = source.index('{', source.index(signature))
    depth = 1
    end = start + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def load(project):
    java = project / 'src/main/java/net/wurstclient'
    sources = {}
    for name, relative in {
        'context': 'hud2/nanovg/NanoVGContext.java',
        'guard': 'hud2/nanovg/GlStateGuard.java',
        'blitter': 'hud2/nanovg/NanoVgBlitter.java',
        'reset': 'mixin/NanoVgGuiRenderStateMixin.java',
        'renderer': 'mixin/GameRendererMixin.java',
        'selection': 'mixin/AbstractSelectionListMixin.java',
        'hud': 'hud2/HudManager.java',
    }.items():
        path = java / relative
        sources[name] = path.read_text(encoding='utf-8') if path.exists() else ''
    resources = project / 'src/main/resources'
    config = resources / ('wurstpenguin.mixins.json' if 'fabric' in project.parts else 'wurst.mixins.json')
    sources['registered'] = json.loads(config.read_text(encoding='utf-8'))['client']
    sources['version'] = project.name if project.name[0].isdigit() else '1.20.1'
    return sources


def audit(s):
    errors = []

    def require(condition, message):
        if not condition:
            errors.append(message)

    ctx, guard = s['context'], s['guard']
    create = method(ctx, 'public boolean create()')
    require('try(GlStateGuard ignored = GlStateGuard.capture())' in create
            and 'return createContext();' in create, 'initialization must restore caller GL state')
    init = method(ctx, 'private boolean createContext()')
    require(init.index('prepareTextureUpload();') < init.index('nvgCreate('), 'initial native texture uploads must use clean unpack state')
    require(re.search(r'if\(!ensureFramebuffer\(\)\)\s*\{\s*destroy\(\);', init), 'failed framebuffer initialization must destroy the context')
    upload = method(ctx, 'private static void prepareTextureUpload()')
    for token in ('GL_PIXEL_UNPACK_BUFFER, 0', 'GL_UNPACK_ALIGNMENT, 1',
                  'GL_UNPACK_ROW_LENGTH, 0', 'GL_UNPACK_SKIP_ROWS, 0', 'GL_UNPACK_SKIP_PIXELS, 0'):
        require(token in upload, 'unsafe upload state: ' + token)
    frame = method(ctx, 'public boolean beginFrame()')
    require('prepareTextureUpload();' in frame, 'font atlas uploads must use clean unpack state')
    for token in ('glColorMask(true, true, true, true)', 'glDepthMask(true)', 'glStencilMask(~0)', 'glClearStencil(0)'):
        require(token in frame and frame.index(token) < frame.index('GL11.glClear('), 'clear must set ' + token)
    require('GL_STENCIL_BUFFER_BIT' in frame, 'offscreen stencil must be cleared')
    close = method(guard, 'public void close()')
    for token in ('glBindSampler(0, sampler0)', 'GL_FRONT', 'GL_BACK',
                  'GL_PIXEL_UNPACK_BUFFER, unpackBuffer', 'GL_UNPACK_ROW_LENGTH, unpackRowLength',
                  'GL_UNPACK_SKIP_ROWS, unpackSkipRows', 'GL_UNPACK_SKIP_PIXELS, unpackSkipPixels',
                  'GL_UNPACK_ALIGNMENT, unpackAlignment', 'glClearColor(', 'glClearStencil('):
        require(token in close, 'GL restoration missing: ' + token)
    for token in ('GlStateManager._activeTexture(GL13.GL_TEXTURE0)', 'GlStateManager._bindTexture(texture0)',
                  'GlStateManager._activeTexture(activeTexture)', 'GlStateManager._bindTexture(activeTextureBinding)'):
        require(token in close, 'Minecraft texture cache synchronization missing: ' + token)
    require('if(!mod.getId().equals("FPSDisplay"))' not in s['hud'], 'desktop HUD must not be restricted to the FPS module')
    if s['version'] in ('1.20.3', '1.20.4'):
        require('renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V' in s['selection'], 'selection list must target the declared renderWidget method')
    expected_delayed = s['version'].startswith('26.') or s['version'] in {
        '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11'}
    require(bool(s['blitter']) == expected_delayed, 'incorrect immediate/delayed rendering path')
    if expected_delayed:
        blit = s['blitter']
        require('MAX_SLOTS' not in blit and 'new IdentityHashMap<>()' in blit,
                'pending snapshots must not wrap a fixed ring')
        require('pending.computeIfAbsent(state, key -> new ArrayList<>()).add(slot);' in blit,
                'each GUI submission must retain its own snapshot')
        copy = method(blit, 'private static Slot copySnapshot(')
        require('available.pollFirst()' in copy and 'try(GlStateGuard ignored = GlStateGuard.capture())' in copy,
                'copy must lease an available snapshot and restore GL state')
        require(copy.index('try(GlStateGuard') < copy.index('createTexture('), 'GPU allocation must be inside the state guard')
        release = method(blit, 'void releaseFrame(')
        require('pending.remove(state)' in release and 'available.addAll(consumed)' in release,
                'reset must release each state only once')
        shutdown = method(blit, 'void close()')
        require(all(t in shutdown for t in ('pending.values()', 'pending.clear()', 'available.clear()', 'slot.close()')),
                'shutdown must free pending and available textures')
        package = 'net.minecraft.client.gui.render.state' if s['version'].startswith('1.') else 'net.minecraft.client.renderer.state.gui'
        require(package + '.GuiRenderState' in s['reset'], 'reset mixin targets the wrong API package')
        require('@Mixin(GuiRenderState.class)' in s['reset'] and 'method = "reset()V", at = @At("RETURN")' in s['reset']
                and 'NanoVgBlitter.releaseFrame(' in s['reset'], 'snapshots must be released after GUI reset')
        require(s['registered'].count('NanoVgGuiRenderStateMixin') == 1, 'reset mixin must be registered exactly once')
        require('method = "close()V"' in s['renderer'] and 'NanoVgBlitter.close()' in s['renderer'], 'renderer shutdown hook missing')
    return errors


def main():
    failures, delayed, count = [], 0, 0
    for project in projects():
        count += 1
        try:
            sources = load(project)
            delayed += bool(sources['blitter'])
            failures.extend(f'{project.relative_to(ROOT)}: {e}' for e in audit(sources))
        except (ValueError, IndexError, KeyError, OSError) as e:
            failures.append(f'{project.relative_to(ROOT)}: missing or invalid render source: {e}')
    if count != 66:
        failures.append(f'expected 66 v1.5.1 projects, found {count}')
    for error in failures:
        print('FAIL: ' + error)
    if failures:
        raise SystemExit(1)
    print(f'PASS: {count} v1.5.1 projects; {delayed} delayed / {count - delayed} immediate rendering paths')


if __name__ == '__main__':
    main()
