"""Transplant OpenOpal source bodies, preserving their provenance and algorithms."""
from pathlib import Path
import hashlib
import json
import re

from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects

REFERENCE = ROOT / 'reference/clients/OpenOpal/src/client/java/wtf/opal'


def source(relative, package):
    path = REFERENCE / relative
    text = path.read_text(encoding='utf-8')
    text = re.sub(r'^package [^;]+;', f'package {package};', text, count=1)
    header = ('/*\n * Ported directly from OpenOpal: ' + relative + '\n'
              ' * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.\n'
              ' * Minecraft names and host integration adapted for WurstB+ Plus.\n */\n')
    return header + text


def generate():
    files = {}
    for name, original in [('RandomUtility', 'utility/misc/math/RandomUtility.java'),
                           ('Stopwatch', 'utility/misc/time/Stopwatch.java'),
                           ('LastAttackData', 'client/feature/module/impl/combat/killaura/target/LastAttackData.java'),
                           ('SkipTickUtility', 'utility/player/SkipTickUtility.java')]:
        files[f'opal/{name}.java'] = source(original, 'net.wurstclient.opal').replace(
            'import wtf.opal.utility.misc.time.Stopwatch;\n', '')
    model_path = 'client/feature/helper/impl/player/rotation/model/'
    interface = source(model_path + 'IRotationModel.java', 'net.wurstclient.opal.rotation')
    interface = interface.replace('import net.minecraft.util.math.Vec2f;', 'import net.wurstclient.util.Rotation;\nimport net.wurstclient.hacks.scaffold.ScaffoldRotationModel;')
    files['opal/rotation/IRotationModel.java'] = interface.replace('Vec2f', 'Rotation').replace('EnumRotationModel', 'ScaffoldRotationModel')
    for name in ['Instant', 'Linear', 'Heypixel', 'Organic', 'Sideways', 'Hypixel']:
        text = source(model_path + f'impl/{name}RotationModel.java', 'net.wurstclient.opal.rotation')
        imports = {
            'net.minecraft.util.math.MathHelper': 'net.minecraft.util.Mth',
            'net.minecraft.util.math.Vec2f': 'net.wurstclient.util.Rotation',
            'wtf.opal.client.feature.helper.impl.player.rotation.model.EnumRotationModel': 'net.wurstclient.hacks.scaffold.ScaffoldRotationModel',
            'wtf.opal.client.feature.helper.impl.player.rotation.model.IRotationModel': 'net.wurstclient.opal.rotation.IRotationModel',
            'wtf.opal.utility.player.RotationUtility': 'net.wurstclient.hacks.scaffold.ScaffoldUtils',
            'wtf.opal.utility.misc.math.RandomUtility': 'net.wurstclient.opal.RandomUtility',
        }
        for old, new in imports.items():
            text = text.replace(old, new)
        text = re.sub(r'import (?:wtf\.opal\.[^;]+|static wtf\.opal\.[^;]+);\n', '', text)
        for old, new in [('Vec2f', 'Rotation'), ('MathHelper', 'Mth'), ('EnumRotationModel', 'ScaffoldRotationModel'), ('RotationUtility', 'ScaffoldUtils')]:
            text = text.replace(old, new)
        text = re.sub(r'\b(from|to|rotation|prevRotation)\.x\b', r'\1.yaw()', text)
        text = re.sub(r'\b(from|to|rotation|prevRotation)\.y\b', r'\1.pitch()', text)
        if name == 'Hypixel':
            text = text.replace('public class HypixelRotationModel implements IRotationModel {', '''public class HypixelRotationModel implements IRotationModel {
    private final boolean diagonalYaw;
    private final int airTicks;

    public HypixelRotationModel(boolean diagonalYaw, int airTicks) {
        this.diagonalYaw = diagonalYaw;
        this.airTicks = airTicks;
    }''')
            text = text.replace('LocalDataWatch.get().airTicks', 'airTicks')
            text = re.sub(r'private boolean isYawDiagonal\(\) \{[\s\S]*?\n    }', 'private boolean isYawDiagonal() {\n        return diagonalYaw;\n    }', text)
        files[f'opal/rotation/{name}RotationModel.java'] = text
    return files


def main():
    files = generate()
    origins = {str(p.relative_to(REFERENCE)): hashlib.sha256(p.read_bytes()).hexdigest()
               for p in REFERENCE.rglob('*.java')
               if '/scaffold/' in p.as_posix() or '/killaura/' in p.as_posix()
               or '/rotation/' in p.as_posix() or p.name in ['RandomUtility.java', 'Stopwatch.java', 'SkipTickUtility.java']}
    for project in projects():
        for name, content in files.items():
            target = project / PACKAGE / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding='utf-8', newline='\n')
        license_path = project / 'src/main/resources/META-INF/licenses/openopal/LICENSE'
        license_path.parent.mkdir(parents=True, exist_ok=True)
        license_path.write_bytes((ROOT / 'reference/clients/OpenOpal/LICENSE').read_bytes())
    (PLAN / 'reference-sha256.json').write_text(json.dumps(origins, indent=2), encoding='utf-8')
    print(f'Copied {len(files)} source ports + GPL license into {len(projects())} builds.')


if __name__ == '__main__':
    main()
