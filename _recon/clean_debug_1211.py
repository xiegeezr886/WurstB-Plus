# -*- coding: utf-8 -*-
"""Remove the temporary NanoVG debug logging from Fabric 1.21.11."""
import re
from pathlib import Path

root = Path('fabric/versions/1.21.11/src/main/java/net/wurstclient')

p = root / 'hud2/nanovg/NanoVGContext.java'
t = p.read_text(encoding='utf-8')
t = t.replace('\tprivate static int debugTextCount;\n', '')
t = re.sub(r'\t\t\tSystem\.out\.println\("\[HUD\] font ".*?'
           r'\n\t\t\t\t\+ handle\);\n', '', t, flags=re.S)
t = re.sub(r'\t\tif\(debugTextCount\+\+ < 8\)\n'
           r'\t\t\tSystem\.out\.println\("\[HUD\] text.*?'
           r'\n\t\t\t\t\+ size\);\n', '', t, flags=re.S)
t = re.sub(r'\tpublic void debugArial.*?\n\t\}\n\n(?=\tpublic void drawText)',
           '', t, flags=re.S)
p.write_text(t, encoding='utf-8', newline='\n')

p = root / 'hud2/HudManager.java'
t = p.read_text(encoding='utf-8')
t = t.replace('\tprivate static boolean debugFbo;\n', '')
t = t.replace('\tprivate static boolean debugDraw;\n', '')
t = re.sub(r'\t\t\tif\(!debugDraw\)\n\t\t\t\{.*?\n\t\t\t\}\n\n', '', t,
           flags=re.S)
t = re.sub(r'\t\t\tif\(!debugFbo\)\n\t\t\t\{.*?\n\t\t\t\}\n'
           r'(?=\t\t\tNanoVgBlitter)', '', t, flags=re.S)
for imp in ['import java.nio.ByteBuffer;\n', 'import org.lwjgl.opengl.GL11;\n',
            'import org.lwjgl.opengl.GL30;\n',
            'import org.lwjgl.system.MemoryUtil;\n',
            'import net.wurstclient.hud2.nanovg.HudFonts;\n']:
    t = t.replace(imp, '')
p.write_text(t, encoding='utf-8', newline='\n')
print('cleaned')
