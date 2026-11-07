# -*- coding: utf-8 -*-
"""Propagate the Soar HUD restyle + HUD editor integration to all v1.5.1
projects (never the repository root / v1.6.0)."""
import re
import sys
from pathlib import Path

REPO = Path('.')
VAR = Path('_recon/hud_variants')
HACK_GUARD = ('\t\tif(net.wurstclient.hud2.HackHudElement.isPreviewRendering())\n'
              '\t\t\treturn false;\n\n')

NATIVE_VERSIONS = {'26.1', '26.1.1', '26.1.2', '26.2', '26.3'}
WRAPPER_OLD = {'1.21.6', '1.21.7', '1.21.8'}
WRAPPER_EVENT = {'1.21.9', '1.21.10', '1.21.11'}


def projects():
    out = []
    for base in ['fabric/versions', 'neoforge/versions', 'versions']:
        for path in sorted(Path(base).iterdir()):
            if not path.is_dir():
                continue
            if (path / 'src/main/java/net/wurstclient/hud2/HudManager.java') \
                    .exists():
                out.append(path)
    for path in [Path('fabric'), Path('neoforge')]:
        if (path / 'src/main/java/net/wurstclient/hud2/HudManager.java') \
                .exists():
            out.append(path)
    return out


def version_of(project):
    if project.name in ('fabric', 'neoforge'):
        return '1.20.1'
    return project.name


def family(project):
    version = version_of(project)
    if version in NATIVE_VERSIONS:
        return 'native'
    if version in WRAPPER_OLD:
        return 'wrapper_old'
    if version in WRAPPER_EVENT:
        return 'wrapper_event'
    return 'legacy'


def variant(name):
    return (VAR / name).read_text(encoding='utf-8')


def write(project, rel, text):
    path = project / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8', newline='\n')


def read(project, rel):
    path = project / rel
    return path.read_text(encoding='utf-8')


def ensure_import(text, line):
    if line in text:
        return text
    package = text.split('\n', 1)[0]
    return text.replace(package + '\n',
                        package + '\n\n' + line + '\n', 1)


def graphics_type(text):
    if 'GuiGraphicsExtractor' in text:
        return 'GuiGraphicsExtractor'
    return 'GuiGraphics'


def patch_managed_listener(project):
    rel = 'src/main/java/net/wurstclient/events/GUIRenderListener.java'
    text = read(project, rel)
    if 'HackHudElement' in text:
        return 1
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
        write(project, rel, new)
    return count


def patch_preview_guard(project):
    root = project / 'src/main/java/net/wurstclient/hacks'
    if not root.is_dir():
        return 0
    count = 0
    pattern = re.compile(r'(private boolean uiHidden\(\)\s*\{\s*\n)')
    for src in root.glob('*.java'):
        text = src.read_text(encoding='utf-8')
        if 'isPreviewRendering()' in text:
            continue
        new, n = pattern.subn(lambda m: m.group(1) + HACK_GUARD, text)
        if n:
            src.write_text(new, encoding='utf-8', newline='\n')
            count += n
    return count


# --------------------------------------------------------------- elements
def patch_text_element(project):
    rel = 'src/main/java/net/wurstclient/hud2/elements/TextHudElement.java'
    text = read(project, rel)
    if 'SoarHudStyle' in text:
        return
    type_ = graphics_type(text)
    text = ensure_import(text, 'import net.wurstclient.hud2.SoarHudStyle;')
    start = text.index('\tprotected abstract String getText();')
    end = text.rindex('}')
    body = '''\tprotected abstract String getText();

\t@Override
\tpublic boolean renderEditorPreview()
\t{
\t\treturn true;
\t}

\t@Override
\tpublic int getWidth()
\t{
\t\treturn WurstClient.MC.font.width(getText())
\t\t\t+ SoarHudStyle.PADDING * 2;
\t}

\t@Override
\tpublic int getHeight()
\t{
\t\treturn SoarHudStyle.singleLineHeight();
\t}

\t@Override
\tpublic void render(TYPE graphics, int x, int y, float partialTicks)
\t{
\t\tFont font = WurstClient.MC.font;
\t\tString text = getText();
\t\tint width = font.width(text) + SoarHudStyle.PADDING * 2;
\t\tint height = getHeight();
\t\tSoarHudStyle.panel(graphics, x, y, x + width, y + height);
\t\tSoarHudStyle.text(graphics, font, text, x + SoarHudStyle.PADDING,
\t\t\tSoarHudStyle.textY(y));
\t}
'''.replace('TYPE', type_)
    write(project, rel, text[:start] + body + text[end:])


def patch_potion(project):
    rel = 'src/main/java/net/wurstclient/hud2/elements/PotionHudElement.java'
    text = read(project, rel)
    if 'SoarHudStyle' in text:
        return
    type_ = graphics_type(text)
    text = ensure_import(text, 'import net.wurstclient.hud2.SoarHudStyle;')
    pattern = re.compile(
        r'\t@Override\n\tpublic int getWidth\(\).*?'
        r'\n\tprivate List<String> getLines\(\)', re.S)
    body = '''\t@Override
\tpublic int getWidth()
\t{
\t\tFont font = WurstClient.MC.font;
\t\treturn getLines().stream().mapToInt(font::width).max().orElse(80)
\t\t\t+ SoarHudStyle.PADDING * 2;
\t}

\t@Override
\tpublic int getHeight()
\t{
\t\tint lines = Math.max(1, getLines().size());
\t\treturn SoarHudStyle.panelHeight(lines * WurstClient.MC.font.lineHeight);
\t}

\t@Override
\tpublic void render(TYPE graphics, int x, int y, float partialTicks)
\t{
\t\tFont font = WurstClient.MC.font;
\t\tList<String> lines = getLines();
\t\tif(lines.isEmpty())
\t\t\treturn;
\t\tint width = getWidth();
\t\tint height = getHeight();
\t\tSoarHudStyle.panel(graphics, x, y, x + width, y + height);
\t\tfor(int index = 0; index < lines.size(); index++)
\t\t\tSoarHudStyle.text(graphics, font, lines.get(index),
\t\t\t\tx + SoarHudStyle.PADDING, y + SoarHudStyle.VERTICAL_PADDING
\t\t\t\t\t+ index * font.lineHeight);
\t}

\tprivate List<String> getLines()'''.replace('TYPE', type_)
    new, count = pattern.subn(lambda m: body, text)
    if count:
        write(project, rel, new)


def patch_inventory(project):
    rel = 'src/main/java/net/wurstclient/hud2/elements/InventoryHudElement.java'
    text = read(project, rel)
    if 'SoarHudStyle' in text:
        return
    text = ensure_import(text, 'import net.wurstclient.hud2.SoarHudStyle;')
    text = text.replace('static final int HEIGHT = 68;',
                        'static final int HEIGHT = 72;')
    text = text.replace('private static final int SLOT_START_Y = 14;',
                        'private static final int SLOT_START_Y = 18;')
    text = text.replace('private static final int SLOT_FILL = 0x78101010;',
                        'private static final int SLOT_FILL = 0x40000000;')
    for line in ['\tprivate static final int CARD_FILL = 0xAD050505;\n',
                 '\tprivate static final int CARD_OUTLINE = 0x10FFFFFF;\n',
                 '\tprivate static final int SLOT_OUTLINE = 0x0FFFFFFF;\n',
                 '\tprivate static final int TEXT = 0xFFF2F4F7;\n']:
        text = text.replace(line, '')
    pattern = re.compile(
        r'\t\tWURST\.getGui\(\)\.updateColors\(\);.*?'
        r'\t\tfor\(int row = 0; row < ROWS; row\+\+\)', re.S)
    header = '''\t\tSoarHudStyle.panel(graphics, x, y, x + WIDTH, y + HEIGHT);
\t\tSoarHudStyle.text(graphics, WurstClient.MC.font, getName(),
\t\t\tx + SoarHudStyle.PADDING, y + SoarHudStyle.VERTICAL_PADDING);

\t\tfor(int row = 0; row < ROWS; row++)'''
    text = pattern.sub(header, text, count=1)
    text = re.sub(
        r'FlatRenderer\.fillRoundedRect\(graphics, slotX, slotY,\s*'
        r'slotX \+ SLOT_SIZE, slotY \+ SLOT_SIZE, 2, SLOT_FILL\);',
        'FlatRenderer.fillRoundedRect(graphics, slotX, slotY,\n'
        '\t\t\t\t\tslotX + SLOT_SIZE, slotY + SLOT_SIZE, 3, SLOT_FILL);',
        text)
    text = re.sub(
        r'\t*FlatRenderer\.drawRoundedOutline\(graphics, slotX, slotY,\s*'
        r'slotX \+ SLOT_SIZE, slotY \+ SLOT_SIZE, 2, SLOT_OUTLINE\);\n', '',
        text)
    write(project, rel, text)


def patch_keystrokes(project):
    rel = 'src/main/java/net/wurstclient/hud2/elements/KeystrokesHudElement.java'
    text = read(project, rel)
    if 'SoarHudStyle' in text:
        return
    old = '''\tprivate static final int IDLE_BACKGROUND = 0xB0070A0F;
\tprivate static final int PRESSED_BACKGROUND = 0xE0006366;
\tprivate static final int IDLE_TEXT = 0xFFE4E8ED;
\tprivate static final int PRESSED_TEXT = 0xFFFFFFFF;
\tprivate static final int IDLE_BORDER = 0x28FFFFFF;
\tprivate static final int PRESSED_BORDER = 0xB000888C;'''
    new = '''\tprivate static final int IDLE_BACKGROUND = 0x50000000;
\tprivate static final int PRESSED_BACKGROUND = 0xB0006366;
\tprivate static final int IDLE_TEXT = 0xE6FFFFFF;
\tprivate static final int PRESSED_TEXT = 0xFFFFFFFF;
\tprivate static final int IDLE_BORDER = 0x18FFFFFF;
\tprivate static final int PRESSED_BORDER = 0x8000888C;'''
    if old in text:
        text = text.replace(old, new)
    text = re.sub(
        r'(FlatRenderer\.(?:fillRoundedRect|drawRoundedOutline)\(graphics, x, y, x \+ width,\s*y \+ height,)\s*3,',
        r'\1\n\t\t\t6,', text)
    write(project, rel, text)


def patch_armor(project):
    rel = 'src/main/java/net/wurstclient/hud2/elements/ArmorHudElement.java'
    text = read(project, rel)
    if 'SoarHudStyle' in text:
        return
    type_ = graphics_type(text)
    text = ensure_import(text, 'import net.wurstclient.hud2.SoarHudStyle;')
    if 'import net.wurstclient.util.RenderUtils;' not in text:
        text = ensure_import(text, 'import net.wurstclient.util.RenderUtils;')
    pattern = re.compile(
        r'\t@Override\n\tpublic int getWidth\(\).*?'
        r'\n\t@Override\n\tpublic HudElementConfig getDefaultLayout\(\)', re.S)
    if 'getArmorSlots()' in text:
        armor = ('\t\tList<ItemStack> armor = new ArrayList<>();\n'
                 '\t\tWurstClient.MC.player.getArmorSlots().forEach(armor::add);\n'
                 '\t\tCollections.reverse(armor);\n'
                 '\t\treturn armor;')
    else:
        armor = ('\t\tList<ItemStack> armor = new ArrayList<>();\n'
                 '\t\tfor(var slot : new net.minecraft.world.entity.EquipmentSlot[]{\n'
                 '\t\t\tnet.minecraft.world.entity.EquipmentSlot.FEET,\n'
                 '\t\t\tnet.minecraft.world.entity.EquipmentSlot.LEGS,\n'
                 '\t\t\tnet.minecraft.world.entity.EquipmentSlot.CHEST,\n'
                 '\t\t\tnet.minecraft.world.entity.EquipmentSlot.HEAD})\n'
                 '\t\t{\n'
                 '\t\t\tItemStack stack = WurstClient.MC.player.getItemBySlot(slot);\n'
                 '\t\t\tif(!stack.isEmpty())\n'
                 '\t\t\t\tarmor.add(stack);\n'
                 '\t\t}\n'
                 '\t\tCollections.reverse(armor);\n'
                 '\t\treturn armor;')
    body = '''\t@Override
\tpublic int getWidth()
\t{
\t\tint count = getArmor().size();
\t\treturn count == 0 ? 0 : count * 18 + SoarHudStyle.PADDING * 2 - 2;
\t}

\t@Override
\tpublic int getHeight()
\t{
\t\treturn 26;
\t}

\t@Override
\tpublic void render(TYPE graphics, int x, int y, float partialTicks)
\t{
\t\tif(WurstClient.MC.player == null)
\t\t\treturn;
\t\tList<ItemStack> armor = getArmor();
\t\tif(armor.isEmpty())
\t\t\treturn;
\t\tint width = getWidth();
\t\tSoarHudStyle.panel(graphics, x, y, x + width, y + getHeight());
\t\tfor(int index = 0; index < armor.size(); index++)
\t\t{
\t\t\tItemStack stack = armor.get(index);
\t\t\tint itemX = x + SoarHudStyle.PADDING + index * 18;
\t\t\tRenderUtils.drawItem(graphics, stack, itemX,
\t\t\t\ty + SoarHudStyle.PADDING, false);
\t\t}
\t}

\tprivate List<ItemStack> getArmor()
\t{
\t\tif(WurstClient.MC.player == null)
\t\t\treturn Collections.emptyList();
ARMOR
\t}

\t@Override
\tpublic HudElementConfig getDefaultLayout()'''.replace(
        'TYPE', type_).replace('ARMOR', armor)
    new, count = pattern.subn(lambda m: body, text)
    if count:
        write(project, rel, new)


def patch_target(project):
    rel = 'src/main/java/net/wurstclient/hud2/elements/TargetHudElement.java'
    text = read(project, rel)
    if 'SoarHudStyle' in text:
        return
    text = ensure_import(text, 'import net.wurstclient.hud2.SoarHudStyle;')
    replacements = {
        'private static final int CARD_RADIUS = 4;':
            'private static final int CARD_RADIUS = SoarHudStyle.RADIUS;',
        'private static final int CARD_FILL = 0xAD050505;':
            'private static final int CARD_FILL = SoarHudStyle.PANEL_COLOR;\n'
            '\tprivate static final int CARD_SHADOW = SoarHudStyle.SHADOW_COLOR;',
        'private static final int FACE_FILL = 0x68070B10;':
            'private static final int FACE_FILL = 0x50000000;',
        'private static final int TEXT_PRIMARY = 0xFFF2F4F7;':
            'private static final int TEXT_PRIMARY = SoarHudStyle.TEXT_COLOR;',
        'private static final int HEALTH_TRACK = 0xA020252A;':
            'private static final int HEALTH_TRACK = 0x5020252A;',
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    pattern = re.compile(
        r'\t\tFlatRenderer\.fillRoundedRect\(graphics, x, y, x \+ WIDTH, y \+ panelHeight,\s*'
        r'CARD_RADIUS, withOpacity\(CARD_FILL, opacity\)\);\s*'
        r'FlatRenderer\.drawRoundedOutline\(graphics, x, y, x \+ WIDTH,\s*'
        r'y \+ panelHeight,\s*CARD_RADIUS, withOpacity\(CARD_OUTLINE, opacity\)\);\s*'
        r'int themeColor = getThemeAccentColor\(\);\s*'
        r'FlatRenderer\.fillRoundedRect\(graphics, x \+ 1, y \+ 2, x \+ 3,\s*'
        r'y \+ panelHeight - 2, 1,\s*'
        r'withOpacity\(themeColor, opacity \* 0\.94F\)\);')
    block = '''\t\tFlatRenderer.fillRoundedRect(graphics, x, y + 1, x + WIDTH,
\t\t\ty + panelHeight + 2, CARD_RADIUS,
\t\t\twithOpacity(CARD_SHADOW, opacity));
\t\tFlatRenderer.fillRoundedRect(graphics, x, y, x + WIDTH, y + panelHeight,
\t\t\tCARD_RADIUS, withOpacity(CARD_FILL, opacity));

\t\tint themeColor = getThemeAccentColor();'''
    text, count = pattern.subn(block, text, count=1)
    if count:
        write(project, rel, text)


def patch_minimap(project):
    rel = 'src/main/java/net/wurstclient/hud2/elements/MinimapHudElement.java'
    text = read(project, rel)
    if 'SoarHudStyle' in text:
        return
    type_ = graphics_type(text)
    text = ensure_import(text, 'import net.wurstclient.hud2.SoarHudStyle;')
    pattern = re.compile(
        r'\tprivate void drawFrame\([^)]*\)\n\t\{\n.*?\n\t\}\n', re.S)
    block = '''\tprivate void drawFrame(TYPE graphics, int x, int y)
\t{
\t\tSoarHudStyle.panel(graphics, x, y, x + WIDTH, y + HEIGHT);
\t\tFlatRenderer.fillRoundedRect(graphics, x + PADDING - 1,
\t\t\ty + PADDING - 1, x + PADDING + MAP_SIZE + 1,
\t\t\ty + PADDING + MAP_SIZE + 1, MAP_RADIUS + 1, 0xF0080A0D);
\t}
'''.replace('TYPE', type_)
    new, count = pattern.subn(block, text, count=1)
    if count:
        write(project, rel, new)


def patch_hacklist(project):
    rel = 'src/main/java/net/wurstclient/hud/HackListHUD.java'
    text = read(project, rel)
    if 'SoarHudStyle' in text:
        return
    text = ensure_import(text, 'import net.wurstclient.hud2.SoarHudStyle;')
    text = text.replace('private static final int ENTRY_HEIGHT = 12;',
                        'private static final int ENTRY_HEIGHT = 18;\n'
                        '\tprivate static final int PANEL_HEIGHT = 15;')
    text = text.replace('+ " hacks enabled") + 11);',
                        '+ " hacks enabled") + 12);')
    text = text.replace('max().orElse(font.width("HackList")) + 11);',
                        'max().orElse(font.width("HackList")) + 12);')
    text = text.replace('''\t\tif(otf.getMode() == Mode.COUNT)
\t\t\treturn ENTRY_HEIGHT;
\t\treturn Math.max(ENTRY_HEIGHT, activeHax.size() * ENTRY_HEIGHT);''',
                        '''\t\tif(otf.getMode() == Mode.COUNT)
\t\t\treturn PANEL_HEIGHT;
\t\treturn Math.max(PANEL_HEIGHT,
\t\t\tactiveHax.size() * ENTRY_HEIGHT - 3);''')
    pattern = re.compile(
        r'\t\tFont font = WurstClient\.MC\.font;\n'
        r'\t\tint textWidth = font\.width\(text\);.*?'
        r'\t\tposY \+= ENTRY_HEIGHT \* progress;', re.S)
    block = '''\t\tFont font = WurstClient.MC.font;
\t\tint textWidth = font.width(text);
\t\tfloat slide = (textWidth + 12) * (1 - progress);
\t\tfloat x1;
\t\tfloat x2;
\t\tif(!alignRight)
\t\t{
\t\t\tx1 = baseX - slide;
\t\t\tx2 = x1 + textWidth + 12;
\t\t}
\t\telse
\t\t{
\t\t\tx2 = baseX + containerWidth + slide;
\t\t\tx1 = x2 - textWidth - 12;
\t\t}

\t\tint top = Math.round(posY);
\t\tint bottom = top + PANEL_HEIGHT;
\t\tint left = Math.round(x1);
\t\tint right = Math.round(x2);
\t\tSoarHudStyle.panel(context, left, top, right, bottom);
\t\tSoarHudStyle.text(context, font, text, left + 6, top + 3,
\t\t\twithAlpha(0xFFFFFF, Math.round(255 * progress)));
\t\tposY += ENTRY_HEIGHT * progress;'''
    text, count = pattern.subn(block, text, count=1)
    write(project, rel, text)


def patch_logo(project):
    rel = 'src/main/java/net/wurstclient/hud/WurstLogo.java'
    text = read(project, rel)
    if 'SoarHudStyle' in text:
        return
    text = ensure_import(text, 'import net.wurstclient.hud2.SoarHudStyle;')
    pattern = re.compile(
        r'\t\tint right = x \+ getWidth\(\);.*?'
        r'\t\tcontext\.(?:text|drawString)\(font, version,.*?false\);\n',
        re.S)
    block = '''\t\tint right = x + getWidth();
\t\tint bottom = y + PANEL_HEIGHT;
\t\tSoarHudStyle.panel(context, x, y, right, bottom);

\t\tint textY = y + (PANEL_HEIGHT - font.lineHeight) / 2;
\t\tSoarHudStyle.text(context, font, brand, x + SoarHudStyle.PADDING,
\t\t\ttextY, SoarHudStyle.TEXT_COLOR);
\t\tSoarHudStyle.text(context, font, version,
\t\t\tx + SoarHudStyle.PADDING + 6 + font.width(brand), textY,
\t\t\tSoarHudStyle.MUTED_TEXT_COLOR);
'''
    new, count = pattern.subn(block, text, count=1)
    if count:
        write(project, rel, new)


# --------------------------------------------------------------- extractor
TRACK_METHODS = {
    'fill': ("x1, y1, x2, y2", 'track(x1, y1, x2, y2);'),
    'fillGradient': ("x1, y1, x2, y2", 'track(x1, y1, x2, y2);'),
    'outline': ("x1, y1, x2, y2", 'track(x1, y1, x2, y2);'),
    'horizontalLine': ("x1, x2, y", 'track(x1, y, x2, y + 1);'),
    'verticalLine': ("x, y1, y2", 'track(x, y1, x + 1, y2);'),
    'text': ("font, text, x, y", 'track(x, y, x + font.width(text), '
             'y + font.lineHeight);'),
    'centeredText': ("font, text, x, y", 'int half = font.width(text) / 2;\n'
                     '\t\ttrack(x - half, y, x + half, y + font.lineHeight);'),
    'textWithWordWrap': ("font, text, x, y, width",
                         'track(x, y, x + width, y + font.lineHeight);'),
    'textWithBackdrop': ("font, text, x, y, width",
                         'track(x, y, x + width, y + font.lineHeight);'),
    'blit': ("x, y, width, height", 'track(x, y, x + width, y + height);'),
    'blitSprite': ("x, y, width, height",
                   'track(x, y, x + width, y + height);'),
    'item': ("x, y", 'track(x, y, x + 16, y + 16);'),
    'fakeItem': ("x, y", 'track(x, y, x + 16, y + 16);'),
    'itemDecorations': ("x, y", 'track(x, y, x + 16, y + 16);'),
}

FIELDS = '''\n\tprivate boolean boundsTracking;
\tprivate int boundsMinX = Integer.MAX_VALUE;
\tprivate int boundsMinY = Integer.MAX_VALUE;
\tprivate int boundsMaxX = Integer.MIN_VALUE;
\tprivate int boundsMaxY = Integer.MIN_VALUE;
'''

TRACKING = '''\n\t/**
\t * Starts recording the bounding box of subsequent draw calls. Used by the
\t * HUD editor to measure ported HUD modules while they render.
\t */
\tpublic void beginBoundsTracking()
\t{
\t\tboundsTracking = true;
\t\tboundsMinX = Integer.MAX_VALUE;
\t\tboundsMinY = Integer.MAX_VALUE;
\t\tboundsMaxX = Integer.MIN_VALUE;
\t\tboundsMaxY = Integer.MIN_VALUE;
\t}

\tpublic void endBoundsTracking()
\t{
\t\tboundsTracking = false;
\t}

\tpublic int getTrackedWidth()
\t{
\t\treturn boundsMaxX >= boundsMinX ? boundsMaxX - boundsMinX : 0;
\t}

\tpublic int getTrackedHeight()
\t{
\t\treturn boundsMaxY >= boundsMinY ? boundsMaxY - boundsMinY : 0;
\t}

\tpublic int getTrackedMinX()
\t{
\t\treturn boundsMaxX >= boundsMinX ? boundsMinX : 0;
\t}

\tpublic int getTrackedMinY()
\t{
\t\treturn boundsMaxY >= boundsMinY ? boundsMinY : 0;
\t}

\tprivate void track(int x1, int y1, int x2, int y2)
\t{
\t\tif(!boundsTracking)
\t\t\treturn;
\t\tboundsMinX = Math.min(boundsMinX, Math.min(x1, x2));
\t\tboundsMinY = Math.min(boundsMinY, Math.min(y1, y2));
\t\tboundsMaxX = Math.max(boundsMaxX, Math.max(x1, x2));
\t\tboundsMaxY = Math.max(boundsMaxY, Math.max(y1, y2));
\t}
'''


def patch_extractor(project):
    rel = 'src/main/java/net/wurstclient/util/render/GuiGraphicsExtractor.java'
    path = project / rel
    if not path.exists():
        return 0
    text = path.read_text(encoding='utf-8')
    if 'beginBoundsTracking' in text:
        return 0
    text = text.replace('\tprivate boolean scissorEnabled;\n',
                        '\tprivate boolean scissorEnabled;\n' + FIELDS, 1)
    anchor = ('\tpublic GuiGraphics getInner()\n\t{\n\t\treturn inner;\n\t}\n')
    if anchor not in text:
        return 0
    text = text.replace(anchor, anchor + TRACKING, 1)
    pattern = re.compile(
        r'(public void (\w+)\(([^)]*)\)\s*\n\t\{\n)')
    changed = [0]

    def add_track(match):
        name = match.group(2)
        params = match.group(3)
        info = TRACK_METHODS.get(name)
        if not info:
            return match.group(0)
        required = info[0]
        if required not in params:
            return match.group(0)
        changed[0] += 1
        return match.group(1) + '\t\t' + info[1] + '\n'

    text = pattern.sub(add_track, text)
    path.write_text(text, encoding='utf-8', newline='\n')
    return changed[0]


# ------------------------------------------------------------------ main
def main():
    only = [Path(p).as_posix() for p in sys.argv[1:]] or None
    for project in projects():
        if only and project.as_posix() not in only:
            continue
        fam = family(project)
        # variant files
        write(project, 'src/main/java/net/wurstclient/hud2/SoarHudStyle.java',
              variant(f'SoarHudStyle_{"legacy" if fam == "legacy" else ("native" if fam == "native" else "extractor")}.java'))
        write(project, 'src/main/java/net/wurstclient/hud2/HackHudElement.java',
              variant(f'HackHudElement_{"legacy" if fam == "legacy" else ("native" if fam == "native" else "extractor")}.java'))
        write(project, 'src/main/java/net/wurstclient/hud2/HudManager.java',
              variant(f'HudManager_{"legacy" if fam == "legacy" else ("native" if fam == "native" else "extractor")}.java'))
        write(project,
              'src/main/java/net/wurstclient/hud2/HudNotificationRenderer.java',
              variant(f'HudNotificationRenderer_{"legacy" if fam == "legacy" else ("native" if fam == "native" else "extractor")}.java'))
        # editor variant with per-project adjustments
        baseline_editor = read(project,
                               'src/main/java/net/wurstclient/hud2/HudEditorScreen.java')
        if fam == 'native':
            editor = variant('HudEditorScreen_native.java')
            if 'setScreenAndShow' in baseline_editor:
                editor = editor.replace('minecraft.setScreen(null);',
                                        'minecraft.setScreenAndShow(null);')
            if 'InputConstants' in baseline_editor:
                editor = editor.replace('import org.lwjgl.glfw.GLFW;',
                                        'import com.mojang.blaze3d.platform.InputConstants;')
                editor = editor.replace('GLFW.GLFW_KEY_ESCAPE',
                                        'InputConstants.KEY_ESCAPE')
                editor = editor.replace('GLFW.GLFW_MOUSE_BUTTON_LEFT',
                                        'InputConstants.MOUSE_BUTTON_LEFT')
                editor = editor.replace('GLFW.GLFW_MOUSE_BUTTON_RIGHT',
                                        'InputConstants.MOUSE_BUTTON_RIGHT')
        elif fam == 'wrapper_old':
            editor = variant('HudEditorScreen_wrapper_old.java')
        elif fam == 'wrapper_event':
            editor = variant('HudEditorScreen_wrapper_event.java')
        else:
            editor = variant('HudEditorScreen_legacy.java')
        write(project,
              'src/main/java/net/wurstclient/hud2/HudEditorScreen.java',
              editor)
        # shared patches
        patch_managed_listener(project)
        patch_preview_guard(project)
        patch_text_element(project)
        patch_potion(project)
        patch_inventory(project)
        patch_keystrokes(project)
        patch_armor(project)
        patch_target(project)
        patch_minimap(project)
        patch_hacklist(project)
        patch_logo(project)
        if fam in ('wrapper_old', 'wrapper_event'):
            patch_extractor(project)
        print('done', project, fam)


if __name__ == '__main__':
    main()
