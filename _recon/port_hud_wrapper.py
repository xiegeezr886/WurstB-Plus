# -*- coding: utf-8 -*-
"""Port the 1.20.1 NanoVG HUD to a 1.21.6+ wrapper project."""
import re
import shutil
import sys
from pathlib import Path

ROOT = Path('.').resolve()
SRC = Path('fabric/src/main/java/net/wurstclient')


def to_wrapper(text):
    text = text.replace('import net.minecraft.client.gui.GuiGraphics;',
                        'import net.wurstclient.util.render.GuiGraphicsExtractor;')
    text = text.replace('net.minecraft.client.gui.GuiGraphics',
                        'net.wurstclient.util.render.GuiGraphicsExtractor')
    text = re.sub(r'\bGuiGraphics\b', 'GuiGraphicsExtractor', text)
    text = text.replace('.pushPose()', '.pushMatrix()')
    text = text.replace('.popPose()', '.popMatrix()')
    text = re.sub(r'\bgraphics\.drawString\(', 'graphics.text(', text)
    text = re.sub(r'\bcontext\.drawString\(', 'context.text(', text)
    text = text.replace('graphics.drawCenteredString(',
                        'graphics.centeredText(')
    text = text.replace('context.drawCenteredString(',
                        'context.centeredText(')
    # 3-arg pose calls become 2-arg for Matrix3x2fStack.
    text = re.sub(r'\.pose\(\)\.translate\(([^,()]+),([^,()]+),0\)',
                  r'.pose().translate(\1,\2)', text)
    text = re.sub(r'\.pose\(\)\.scale\(([^,()]+),([^,()]+),1\)',
                  r'.pose().scale(\1,\2)', text)
    return text


def main():
    target = Path(sys.argv[1]).resolve()
    if ROOT not in target.parents:
        raise SystemExit('outside repo')
    dst = target / 'src/main/java/net/wurstclient'
    hud = dst / 'hud2'
    if hud.exists():
        shutil.rmtree(hud)
    shutil.copytree(SRC / 'hud2', hud)
    for rel in ['hud/HackListHUD.java', 'hud/WurstLogo.java']:
        p = dst / rel
        if p.exists():
            p.unlink()
    for p in hud.rglob('*.java'):
        t = p.read_text(encoding='utf-8')
        p.write_text(to_wrapper(t), encoding='utf-8', newline='\n')
    shutil.copyfile(
        SRC / 'events/GUIRenderListener.java',
        dst / 'events/GUIRenderListener.java')
    fonts_src = ROOT / 'fabric/src/main/resources/assets/wurstpenguin/nanovg/fonts'
    fonts_dst = target / 'src/main/resources/assets/wurstpenguin/nanovg/fonts'
    fonts_dst.mkdir(parents=True, exist_ok=True)
    for font in fonts_src.glob('*.ttf'):
        shutil.copyfile(font, fonts_dst / font.name)
    print('copied wrapper HUD to', target)


if __name__ == '__main__':
    main()
