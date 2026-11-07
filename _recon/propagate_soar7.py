# -*- coding: utf-8 -*-
"""Directly replace the Wurst HUD system with Soar Client v7.1.2's HUDMod /
SimpleHUDMod / GuiEditHUD architecture in every v1.5.1 project."""
import re
import subprocess
import sys
from pathlib import Path

REPO = Path('.')
TEMPLATE = Path('fabric/versions/1.21.11/src/main/java/net/wurstclient')
PROJ_IMPORT = 'import net.wurstclient.util.render.GuiGraphicsExtractor;'
NATIVE_IMPORT = 'import net.minecraft.client.gui.GuiGraphicsExtractor;'
PROJ_FQ = 'net.wurstclient.util.render.GuiGraphicsExtractor'
NATIVE_FQ = 'net.minecraft.client.gui.GuiGraphicsExtractor'
LEGACY_FQ = 'net.minecraft.client.gui.GuiGraphics'

NATIVE_VERSIONS = {'26.1', '26.1.1', '26.1.2', '26.2', '26.3'}
WRAPPER_OLD = {'1.21.6', '1.21.7', '1.21.8'}
WRAPPER_EVENT = {'1.21.9', '1.21.10', '1.21.11'}


def read_template(rel):
    return (TEMPLATE / rel).read_text(encoding='utf-8')


def add_pose_args(text):
    for method, extra in (('translate', '0'), ('scale', '1')):
        marker = 'graphics.pose().' + method + '('
        pos = 0
        while True:
            start = text.find(marker, pos)
            if start < 0:
                break
            open_paren = start + len(marker) - 1
            depth = 0
            commas = 0
            index = open_paren
            while index < len(text):
                char = text[index]
                if char == '(':
                    depth += 1
                elif char == ')':
                    depth -= 1
                    if depth == 0:
                        break
                elif char == ',' and depth == 1:
                    commas += 1
                index += 1
            if commas == 1:
                text = text[:index] + ', ' + extra + text[index:]
            pos = index + 10
    return text


def to_legacy(text):
    text = text.replace(PROJ_IMPORT,
                        'import net.minecraft.client.gui.GuiGraphics;')
    text = text.replace('GuiGraphicsExtractor', 'GuiGraphics')
    text = text.replace('.pushMatrix()', '.pushPose()')
    text = text.replace('.popMatrix()', '.popPose()')
    text = add_pose_args(text)
    text = re.sub(r'\bgraphics\.text\(', 'graphics.drawString(', text)
    text = re.sub(r'\bcontext\.text\(', 'context.drawString(', text)
    text = text.replace('graphics.centeredText(', 'graphics.drawCenteredString(')
    text = text.replace('context.centeredText(',
                        'context.drawCenteredString(')
    return text


def old_input(text):
    text = text.replace(
        'public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)\n'
        '\t{\n\t\tdouble mouseX = event.x();\n\t\tdouble mouseY = event.y();\n'
        '\t\tint button = event.button();\n',
        'public boolean mouseClicked(double mouseX, double mouseY, int button)\n'
        '\t{\n')
    text = text.replace('return super.mouseClicked(event, doubleClick);',
                        'return super.mouseClicked(mouseX, mouseY, button);')
    text = text.replace(
        'public boolean mouseReleased(MouseButtonEvent event)\n\t{\n',
        'public boolean mouseReleased(double mouseX, double mouseY, int button)\n\t{\n')
    text = text.replace('event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT',
                        'button == GLFW.GLFW_MOUSE_BUTTON_LEFT')
    text = text.replace('return super.mouseReleased(event);',
                        'return super.mouseReleased(mouseX, mouseY, button);')
    text = text.replace(
        'public boolean mouseDragged(MouseButtonEvent event, double deltaX,\n'
        '\t\tdouble deltaY)\n\t{\n',
        'public boolean mouseDragged(double mouseX, double mouseY, int button,\n'
        '\t\tdouble deltaX, double deltaY)\n\t{\n')
    text = text.replace(
        'return super.mouseDragged(event, deltaX, deltaY);',
        'return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);')
    text = text.replace('event.x()', 'mouseX').replace('event.y()', 'mouseY')
    text = text.replace(
        'public boolean keyPressed(KeyEvent event)\n\t{\n'
        '\t\tif(event.key() == GLFW.GLFW_KEY_ESCAPE)',
        'public boolean keyPressed(int keyCode, int scanCode, int modifiers)\n'
        '\t{\n\t\tif(keyCode == GLFW.GLFW_KEY_ESCAPE)')
    text = text.replace('event.key() == GLFW.GLFW_KEY_TAB',
                        'keyCode == GLFW.GLFW_KEY_TAB')
    text = text.replace('event.key() == GLFW.GLFW_KEY_T',
                        'keyCode == GLFW.GLFW_KEY_T')
    text = text.replace('return super.keyPressed(event);',
                        'return super.keyPressed(keyCode, scanCode, modifiers);')
    text = text.replace('import net.minecraft.client.input.KeyEvent;\n', '')
    text = text.replace('import net.minecraft.client.input.MouseButtonEvent;\n',
                        '')
    return text


def strip_event_input_imports(text):
    text = text.replace('import net.minecraft.client.input.KeyEvent;\n', '')
    text = text.replace('import net.minecraft.client.input.MouseButtonEvent;\n',
                        '')
    return text


# ------------------------------------------------------------------ templates
HUDMOD = read_template('hud2/HUDMod.java')
SIMPLE = read_template('hud2/SimpleHUDMod.java')
THEME = read_template('hud2/HudTheme.java')
HACK = read_template('hud2/HackHudElement.java')
MANAGER = read_template('hud2/HudManager.java')
NOTIF = read_template('hud2/HudNotificationRenderer.java')
NOTIF_ELEMENT = read_template('hud2/elements/NotificationHudElement.java')
EDITOR = read_template('hud2/GuiEditHUD.java')

TEXT = read_template('hud2/elements/TextHudElement.java')
POTION = read_template('hud2/elements/PotionHudElement.java')
INVENTORY = read_template('hud2/elements/InventoryHudElement.java')
ARMOR = read_template('hud2/elements/ArmorHudElement.java')
KEYSTROKES = read_template('hud2/elements/KeystrokesHudElement.java')
TARGET = read_template('hud2/elements/TargetHudElement.java')
SIMPLE_ELEMENTS = {}
for name in ['FpsHudElement', 'CoordsHudElement', 'PingHudElement',
             'TpsHudElement', 'SpeedHudElement', 'ServerHudElement',
             'ClockHudElement', 'ComboHudElement']:
    SIMPLE_ELEMENTS[name] = read_template(f'hud2/elements/{name}.java')

# editor variants
EDITOR_EVENT = EDITOR
EDITOR_OLD = old_input(strip_event_input_imports(EDITOR))
EDITOR_LEGACY = to_legacy(EDITOR_OLD)
EDITOR_LEGACY = EDITOR_LEGACY.replace(
    'renderContents(new GuiGraphics(graphics), mouseX, mouseY,\n'
    '\t\t\tpartialTicks);',
    'renderBackground(graphics, mouseX, mouseY, partialTicks);\n'
    '\t\trenderContents(graphics, mouseX, mouseY, partialTicks);')
EDITOR_NATIVE = EDITOR.replace(PROJ_IMPORT, NATIVE_IMPORT)
EDITOR_NATIVE = EDITOR_NATIVE.replace(
    'import net.minecraft.client.gui.GuiGraphics;\n', '')
EDITOR_NATIVE = EDITOR_NATIVE.replace(
    '\t@Override\n\tpublic void render(GuiGraphics graphics, int mouseX, int mouseY,\n'
    '\t\tfloat partialTicks)\n\t{\n'
    '\t\trenderContents(new GuiGraphicsExtractor(graphics), mouseX, mouseY,\n'
    '\t\t\tpartialTicks);\n\t}',
    '\t@Override\n\tpublic void extractRenderState(GuiGraphicsExtractor graphics,\n'
    '\t\tint mouseX, int mouseY, float partialTicks)\n\t{\n'
    '\t\trenderContents(graphics, mouseX, mouseY, partialTicks);\n\t}')

# target variants
TARGET_WRAPPER_ID = TARGET
TARGET_WRAPPER_RL = TARGET.replace(
    'import net.minecraft.resources.Identifier;',
    'import net.minecraft.resources.ResourceLocation;').replace(
    'Identifier', 'ResourceLocation').replace(
    'player.getSkin().body().texturePath()', 'player.getSkin().texture()')
TARGET_NATIVE = TARGET.replace(PROJ_IMPORT, NATIVE_IMPORT) \
    if PROJ_IMPORT in TARGET else TARGET
TARGET_NATIVE = re.sub(r'\t\tint tint = alpha << 24 \| 0xFFFFFF;\n', '',
                       TARGET_NATIVE)
TARGET_NATIVE = re.sub(r', tint\);', ');', TARGET_NATIVE)
TARGET_LEGACY = TARGET
for line in ['import net.minecraft.client.player.AbstractClientPlayer;\n',
             'import net.minecraft.client.renderer.RenderPipelines;\n',
             'import net.minecraft.resources.Identifier;\n']:
    TARGET_LEGACY = TARGET_LEGACY.replace(line, '')
TARGET_LEGACY = re.sub(
    r'\tprivate void drawFace\(Player target, float x, float y, float size,\n'
    r'\t\tfloat opacity\)\n\t\{.*?\n\t\}\n',
    '\tprivate void drawFace(Player target, float x, float y, float size,\n'
    '\t\tfloat opacity)\n\t{\n\t}\n', TARGET_LEGACY, flags=re.S)


# ------------------------------------------------------------------- project
def projects():
    out = []
    for base in ['fabric/versions', 'neoforge/versions', 'versions']:
        for path in sorted(Path(base).iterdir()):
            if path.is_dir() and (path / 'src/main/java/net/wurstclient/hud2/HudManager.java').exists():
                out.append(path)
    for path in [Path('fabric'), Path('neoforge')]:
        if (path / 'src/main/java/net/wurstclient/hud2/HudManager.java').exists():
            out.append(path)
    return out


def version_of(project):
    return '1.20.1' if project.name in ('fabric', 'neoforge') else project.name


def family(project):
    v = version_of(project)
    if v in NATIVE_VERSIONS:
        return 'native'
    if v in WRAPPER_OLD:
        return 'wrapper_old'
    if v in WRAPPER_EVENT:
        return 'wrapper_event'
    return 'legacy'


def graphics_family(project):
    v = version_of(project)
    if v in NATIVE_VERSIONS:
        return 'native'
    if v in WRAPPER_OLD or v in WRAPPER_EVENT:
        return 'wrapper'
    return 'legacy'


def read(project, rel):
    return (project / rel).read_text(encoding='utf-8')


def write(project, rel, text):
    path = project / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8', newline='\n')


def ensure_import(text, line):
    if line in text:
        return text
    first = text.split('\n', 1)[0]
    return text.replace(first + '\n', first + '\n\n' + line + '\n', 1)


def matching_brace(text, open_index):
    depth = 0
    for i in range(open_index, len(text)):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return i
    return -1


def patch_minimap(project, gfx):
    rel = 'src/main/java/net/wurstclient/hud2/elements/MinimapHudElement.java'
    path = project / rel
    if not path.exists():
        return
    text = path.read_text(encoding='utf-8')
    if 'extends HUDMod' in text:
        return
    text = re.sub(r'import net\.wurstclient\.hud2\.HudElement;\n', '', text)
    text = re.sub(
        r'import net\.wurstclient\.hud2\.HudLayout\.HudElementConfig;\n', '',
        text)
    text = re.sub(r'import net\.wurstclient\.hud2\.SoarHudStyle;\n', '', text)
    text = re.sub(r'\t*SoarHudStyle\.panel\([^;]*\);\n', '', text)
    text = ensure_import(text, 'import net.wurstclient.hud2.HUDMod;')
    text = text.replace('extends HudElement', 'extends HUDMod')
    text = re.sub(
        r'public MinimapHudElement\(\)\s*\{\s*super\("minimap", "[^"]*"\);\s*\}',
        'public MinimapHudElement()\n\t{\n\t\tsuper("minimap", "Minimap");\n'
        '\t\tsetDefaultPosition(700, 160);\n\t\tsetDefaultEnabled(false);\n\t}',
        text)
    text = re.sub(
        r'\t@Override\n\tpublic boolean renderEditorPreview\(\)\s*\{\s*return true;\s*\}\n\n?',
        '', text)
    text = re.sub(
        r'\t@Override\n\tpublic int getWidth\(\)\s*\{\s*return WIDTH;\s*\}\n\n?',
        '', text)
    text = re.sub(
        r'\t@Override\n\tpublic int getHeight\(\)\s*\{\s*return HEIGHT;\s*\}\n\n?',
        '', text)
    # remove getDefaultLayout method
    match = re.search(r'\t@Override\n\tpublic HudElementConfig getDefaultLayout\(\)\s*\{', text)
    if match:
        brace = text.index('{', match.start())
        end = matching_brace(text, brace)
        text = text[:match.start()] + text[end + 1:]
    # rename render -> draw
    match = re.search(r'\t@Override\n\tpublic void render\(([^)]*)\)\s*\n\t\{', text)
    if not match:
        path.write_text(text, encoding='utf-8', newline='\n')
        return
    open_brace = text.index('{', match.start())
    end = matching_brace(text, open_brace)
    body = text[open_brace + 1:end]
    body = body.replace('partialTicks', 'this.partialTicks')
    if 'GuiGraphics' in text and 'GuiGraphicsExtractor' not in text:
        start = '''
		int x = 0;
		int y = 0;
		drawBackground(WIDTH, HEIGHT);
		graphics.pose().pushPose();
		graphics.pose().translate(getX(), getY(), 0);
		graphics.pose().scale(getScale(), getScale(), 1);
'''
        tail = '''
		graphics.pose().popPose();
		setSize(WIDTH, HEIGHT);
'''
    else:
        start = '''
		int x = 0;
		int y = 0;
		drawBackground(WIDTH, HEIGHT);
		graphics.pose().pushMatrix();
		graphics.pose().translate(getX(), getY());
		graphics.pose().scale(getScale(), getScale());
'''
        tail = '''
		graphics.pose().popMatrix();
		setSize(WIDTH, HEIGHT);
'''
    replacement = ('\t@Override\n\tprotected void draw()\n\t{' + start + body
                   + tail + '\t}')
    text = text[:match.start()] + replacement + text[end + 1:]
    path.write_text(text, encoding='utf-8', newline='\n')


def patch_hudlist(project):
    rel = 'src/main/java/net/wurstclient/hud/HackListHUD.java'
    path = project / rel
    if not path.exists():
        return
    text = path.read_text(encoding='utf-8')
    text = re.sub(r'import net\.wurstclient\.hud2\.SoarHudStyle;\n', '', text)
    text = re.sub(r'\t*SoarHudStyle\.panel\([^;]*\);\n', '', text)
    text = text.replace('SoarHudStyle.PADDING', '5')
    text = text.replace('SoarHudStyle.TEXT_COLOR', '0xFFFFFFFF')
    text = text.replace('SoarHudStyle.MUTED_TEXT_COLOR', '0xCCFFFFFF')
    pattern = re.compile(
        r'\t\tint top = Math\.round\(posY\);.*?posY \+= ENTRY_HEIGHT \* progress;',
        re.S)
    block = '''\t\tint top = Math.round(posY);
\t\tint left = Math.round(x1);
\t\tcontext.%s(font, text, left + 6, top + 3,
\t\t\twithAlpha(0xFFFFFF, Math.round(255 * progress))%s);
\t\tposY += ENTRY_HEIGHT * progress;'''
    if 'GuiGraphics context' in text or 'GuiGraphics graphics' in text:
        block = block % ('drawString', ', false')
    else:
        block = block % ('text', ', false')
    text, count = pattern.subn(block, text, count=1)
    path.write_text(text, encoding='utf-8', newline='\n')


def patch_logo(project):
    rel = 'src/main/java/net/wurstclient/hud/WurstLogo.java'
    path = project / rel
    if not path.exists():
        return
    text = path.read_text(encoding='utf-8')
    text = re.sub(r'import net\.wurstclient\.hud2\.SoarHudStyle;\n', '', text)
    text = re.sub(r'\t*SoarHudStyle\.panel\([^;]*\);\n', '', text)
    text = text.replace('SoarHudStyle.PADDING', '5')
    text = text.replace('SoarHudStyle.TEXT_COLOR', '0xFFFFFFFF')
    text = text.replace('SoarHudStyle.MUTED_TEXT_COLOR', '0xCCFFFFFF')
    match = re.search(r'public void renderAt\(([^)]*)\)\s*\{', text)
    if not match:
        return
    params = match.group(1)
    type_name = params.split()[0]
    brace = text.index('{', match.start())
    end = matching_brace(text, brace)
    call = 'drawString' if type_name == 'GuiGraphics' else 'text'
    body = f'''public void renderAt({params})
	{{
		String brand = WurstClient.CLIENT_NAME;
		String version = getVersionString();
		Font font = WurstClient.MC.font;
		int textY = y + (PANEL_HEIGHT - font.lineHeight) / 2;
		context.{call}(font, brand, x + 5, textY, 0xFFFFFFFF, false);
		context.{call}(font, version, x + 11 + font.width(brand), textY,
			0xCCFFFFFF, false);
	}}'''
    text = text[:match.start()] + body + text[end + 1:]
    path.write_text(text, encoding='utf-8', newline='\n')


def patch_managed_listener(project):
    rel = 'src/main/java/net/wurstclient/events/GUIRenderListener.java'
    path = project / rel
    if not path.exists():
        return
    text = path.read_text(encoding='utf-8')
    if 'HackHudElement' in text:
        return
    text = ensure_import(text, 'import net.wurstclient.hud2.HackHudElement;')
    pattern = re.compile(
        r'for\(GUIRenderListener listener : listeners\)\s*\n'
        r'\s*listener\.onRenderGUI\(([^;]+)\);')
    new, count = pattern.subn(
        'for(GUIRenderListener listener : listeners)\n'
        '\t\t\t{\n'
        '\t\t\t\tif(HackHudElement.isSuppressed(listener))\n'
        '\t\t\t\t\tcontinue;\n'
        '\t\t\t\tlistener.onRenderGUI(\\1);\n'
        '\t\t\t}', text)
    if count:
        path.write_text(new, encoding='utf-8', newline='\n')


def patch_preview_guard(project):
    root = project / 'src/main/java/net/wurstclient/hacks'
    if not root.is_dir():
        return
    pattern = re.compile(r'(private boolean uiHidden\(\)\s*\{\s*\n)')
    guard = ('\t\tif(net.wurstclient.hud2.HackHudElement.isPreviewRendering())\n'
             '\t\t\treturn false;\n\n')
    for src in root.glob('*.java'):
        text = src.read_text(encoding='utf-8')
        if 'isPreviewRendering()' in text:
            continue
        new, count = pattern.subn(lambda m: m.group(1) + guard, text)
        if count:
            src.write_text(new, encoding='utf-8', newline='\n')


def revert_extractor(project):
    rel = 'src/main/java/net/wurstclient/util/render/GuiGraphicsExtractor.java'
    path = project / rel
    if not path.exists():
        return
    if 'beginBoundsTracking' not in path.read_text(encoding='utf-8'):
        return
    data = subprocess.check_output(['git', 'show', 'HEAD:' + path.as_posix()])
    path.write_bytes(data)


def purge_legacy_files(project):
    root = project / 'src/main/java/net/wurstclient/hud2'
    for name in ['HudElement.java', 'HudLayout.java', 'SoarHudStyle.java',
                 'HudEditorScreen.java']:
        path = root / name
        if path.exists():
            path.unlink()


def propagate(project):
    fam = family(project)
    gfx = graphics_family(project)
    base = 'src/main/java/net/wurstclient/hud2'
    # core
    write(project, f'{base}/HUDMod.java',
          to_legacy(HUDMOD) if gfx == 'legacy'
          else HUDMOD.replace(PROJ_IMPORT, NATIVE_IMPORT)
          if gfx == 'native' else HUDMOD)
    write(project, f'{base}/SimpleHUDMod.java', SIMPLE)
    write(project, f'{base}/HudTheme.java', THEME)
    write(project, f'{base}/HackHudElement.java',
          to_legacy(HACK) if gfx == 'legacy'
          else HACK.replace(PROJ_IMPORT, NATIVE_IMPORT)
          if gfx == 'native' else HACK)
    manager = MANAGER
    if gfx == 'native':
        manager = manager.replace(PROJ_FQ, NATIVE_FQ)
    elif gfx == 'legacy':
        manager = to_legacy(manager.replace(PROJ_FQ, LEGACY_FQ))
    write(project, f'{base}/HudManager.java', manager)
    notif = NOTIF
    if gfx == 'native':
        notif = notif.replace(PROJ_IMPORT, NATIVE_IMPORT)
    elif gfx == 'legacy':
        notif = to_legacy(notif)
    write(project, f'{base}/HudNotificationRenderer.java', notif)
    write(project, f'{base}/elements/NotificationHudElement.java',
          NOTIF_ELEMENT)

    # editor
    if fam == 'native':
        editor = EDITOR_NATIVE
        baseline_path = project / base / 'HudEditorScreen.java'
        if not baseline_path.exists():
            baseline_path = project / base / 'GuiEditHUD.java'
        baseline = baseline_path.read_text(encoding='utf-8') \
            if baseline_path.exists() else ''
        if 'setScreenAndShow' in baseline:
            editor = editor.replace('minecraft.setScreen(null);',
                                    'minecraft.setScreenAndShow(null);')
        if 'InputConstants' in baseline:
            editor = editor.replace('import org.lwjgl.glfw.GLFW;',
                                    'import com.mojang.blaze3d.platform.InputConstants;')
            editor = editor.replace('GLFW.GLFW_KEY_ESCAPE',
                                    'InputConstants.KEY_ESCAPE')
            editor = editor.replace('GLFW.GLFW_KEY_TAB',
                                    'InputConstants.KEY_TAB')
            editor = editor.replace('GLFW.GLFW_KEY_T',
                                    'InputConstants.KEY_T')
            editor = editor.replace('GLFW.GLFW_MOUSE_BUTTON_LEFT',
                                    'InputConstants.MOUSE_BUTTON_LEFT')
            editor = editor.replace('GLFW.GLFW_MOUSE_BUTTON_RIGHT',
                                    'InputConstants.MOUSE_BUTTON_RIGHT')
            editor = editor.replace('GLFW.GLFW_MOUSE_BUTTON_MIDDLE',
                                    'InputConstants.MOUSE_BUTTON_MIDDLE')
    elif fam == 'wrapper_old':
        editor = EDITOR_OLD
    elif fam == 'wrapper_event':
        editor = EDITOR_EVENT
    else:
        editor = EDITOR_LEGACY
    write(project, f'{base}/GuiEditHUD.java', editor)

    # elements
    write(project, f'{base}/elements/TextHudElement.java', TEXT)
    potion = POTION.replace('.getEffect().value()', '.getEffect()') \
        if version_of(project) in {'1.20.1', '1.20.2', '1.20.3', '1.20.4'} \
        else POTION
    write(project, f'{base}/elements/PotionHudElement.java', potion)
    write(project, f'{base}/elements/InventoryHudElement.java',
          to_legacy(INVENTORY) if gfx == 'legacy' else INVENTORY)
    write(project, f'{base}/elements/ArmorHudElement.java',
          to_legacy(ARMOR) if gfx == 'legacy' else ARMOR)
    write(project, f'{base}/elements/KeystrokesHudElement.java', KEYSTROKES)
    for name, text in SIMPLE_ELEMENTS.items():
        write(project, f'{base}/elements/{name}.java', text)
    if gfx == 'native':
        target = TARGET_NATIVE
    elif gfx == 'legacy':
        target = TARGET_LEGACY
    else:
        baseline_target = read(project,
                               f'{base}/elements/TargetHudElement.java') \
            if (project / base / 'elements/TargetHudElement.java').exists() else ''
        target = TARGET_WRAPPER_RL if 'ResourceLocation' in baseline_target \
            else TARGET_WRAPPER_ID
    write(project, f'{base}/elements/TargetHudElement.java', target)
    patch_minimap(project, gfx)

    purge_legacy_files(project)
    patch_hudlist(project)
    patch_logo(project)
    patch_managed_listener(project)
    patch_preview_guard(project)
    revert_extractor(project)

    # rename editor references
    for java in (project / 'src/main/java').rglob('*.java'):
        text = java.read_text(encoding='utf-8')
        if 'HudEditorScreen' in text:
            java.write_text(text.replace('HudEditorScreen', 'GuiEditHUD'),
                            encoding='utf-8', newline='\n')
        if 'import net.wurstclient.hud2.SoarHudStyle;' in text:
            text = java.read_text(encoding='utf-8')
            text = re.sub(
                r'import net\.wurstclient\.hud2\.SoarHudStyle;\n', '', text)
            java.write_text(text, encoding='utf-8', newline='\n')

    print('replaced', project, fam, gfx)


def main():
    only = [Path(p).as_posix() for p in sys.argv[1:]] or None
    for project in projects():
        if only and project.as_posix() not in only:
            continue
        propagate(project)


if __name__ == '__main__':
    main()
