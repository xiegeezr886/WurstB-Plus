"""Record reviewed port outputs; verification never refreshes these hashes."""
import hashlib
import json
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects


def main():
    files=set()
    for stage in PLAN.glob('before*'):
        if not stage.is_dir():continue
        for f in stage.rglob('*'):
            if f.is_file() and (ROOT/f.relative_to(stage)).is_file():files.add(ROOT/f.relative_to(stage))
    for p in projects():
        for folder in ['opal','hacks/scaffold','hacks/aura']:
            files.update((p/PACKAGE/folder).rglob('*.java'))
        files.update((p/PACKAGE/'mixin').glob('OpenOpal*.java'))
        files.add(p/PACKAGE/'util/MotionActionQueue.java')
        files.add(p/'src/main/resources/META-INF/licenses/openopal/LICENSE')
    files.add(ROOT/'docs/OPENOPAL_V151_PORT.md')
    files.update((ROOT/'scripts/port').glob('*openopal*.py'))
    outputs={f.relative_to(ROOT).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(files)}
    for name in ['final-outputs.json','outputs.json']:
        (PLAN/name).write_text(json.dumps(outputs,indent=2),encoding='utf-8')
    reference=ROOT/'reference/clients/OpenOpal/src/client/java/wtf/opal'
    originals={f.relative_to(reference).as_posix():hashlib.sha256(f.read_bytes()).hexdigest()
        for f in reference.rglob('*.java')
        if any(part in f.as_posix() for part in ['/scaffold/','/killaura/','/player/rotation/','/player/mouse/','/player/slot/','/player/swing/'])
        or f.name in ['RandomUtility.java','Stopwatch.java','SkipTickUtility.java','Animation.java','Easing.java',
                      'RotationUtility.java','PlayerSimulation.java','MouseUpdateEvent.java','VelocityMode.java',
                      'InventoryScreenMixin.java','EntityMixin.java','MouseMixin.java','GameRendererMixin.java']}
    (PLAN/'reference-sha256.json').write_text(json.dumps(originals,indent=2),encoding='utf-8')
    print(f'Recorded {len(outputs)} outputs and {len(originals)} reference hashes.')


if __name__=='__main__':main()
