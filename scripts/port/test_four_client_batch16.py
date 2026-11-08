"""Run unchanged TimeChanger and read callbacks against controlled world state.

These are callback behavior probes, not an in-game Mixin application test.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from sync_four_client_batch import ROOT, PLAN


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', default='fabric')
    parser.add_argument('--staged', action='store_true')
    args = parser.parse_args()
    modern = '/26.' in args.target
    instance = args.target.endswith('/26.3')
    out = PLAN / 'batch16' / ('tests-' + args.target.replace('/', '-'))
    src = out / 'src'
    base = PLAN / 'batch16/generated' if args.staged else ROOT
    root = base / args.target / 'src/main/java/net/wurstclient'
    hashes = {}

    def source(name, body):
        file = src / (name.replace('.', '/') + '.java')
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')

    hook = 'ClientClockManagerMixin' if modern else 'TimeChangerLevelMixin'
    for relative in ['hacks/TimeChangerHack.java', 'mixin/' + hook + '.java']:
        file = root / relative
        target = src / 'net/wurstclient' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(file, target)
        hashes[relative] = hashlib.sha256(file.read_bytes()).hexdigest()

    stubs = {
        'net.wurstclient.Category': 'public enum Category { RENDER }',
        'net.wurstclient.SearchTags': 'public @interface SearchTags {String[] value();}',
        'org.spongepowered.asm.mixin.Mixin': 'public @interface Mixin {Class<?>[] value();}',
        'org.spongepowered.asm.mixin.Shadow': 'public @interface Shadow {}',
        'org.spongepowered.asm.mixin.injection.At': 'public @interface At {String value();}',
        'com.llamalad7.mixinextras.injector.ModifyReturnValue': 'public @interface ModifyReturnValue {String method(); org.spongepowered.asm.mixin.injection.At at();}',
        'net.wurstclient.settings.SliderSetting': 'public class SliderSetting {public double value;public SliderSetting(String n,String d,double v,double min,double max,double step,ValueDisplay display){value=v;}public int getValueI(){return (int)value;}public enum ValueDisplay {INTEGER}}',
        'net.wurstclient.hack.Hack': 'public class Hack {private boolean enabled;public Hack(String n){}public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;}protected void addSetting(Object s){}protected void setCategory(Object c){}}',
        'net.wurstclient.hacks.NoWeatherHack': 'public class NoWeatherHack {public boolean changed;public int time=6000;public boolean isTimeChanged(){return changed;}public int getChangedTime(){return time;}}',
        'net.wurstclient.WurstClient': 'public class WurstClient {public static final WurstClient INSTANCE=new WurstClient();public Hacks hax=new Hacks();public Hacks getHax(){return hax;}public static class Hacks {public net.wurstclient.hacks.TimeChangerHack timeChangerHack=new net.wurstclient.hacks.TimeChangerHack();public net.wurstclient.hacks.NoWeatherHack noWeatherHack=new net.wurstclient.hacks.NoWeatherHack();}}',
        'net.minecraft.client.Minecraft': 'public class Minecraft {private static final Minecraft INSTANCE=new Minecraft();public net.minecraft.client.multiplayer.ClientLevel level;public static Minecraft getInstance(){return INSTANCE;}}',
        'net.minecraft.world.clock.WorldClock': 'public class WorldClock {}',
        'net.minecraft.world.clock.WorldClocks': 'public class WorldClocks {public static final Object OVERWORLD=new Object();}',
        'net.minecraft.core.Holder': 'public class Holder<T> {public Object key;public Holder(Object k){key=k;}public boolean is(Object k){return key==k;}}',
        'net.minecraft.world.level.LevelAccessor': 'public interface LevelAccessor {long dayTime();}',
    }
    stubs['net.minecraft.world.level.Level'] = ('public class Level {public long rawTime;}' if modern else
        'public class Level extends net.wurstclient.mixin.TimeChangerLevelMixin {public long rawTime;public long getDayTime(){return audit.Batch16Probe.read(this,rawTime);}}')
    stubs['net.minecraft.client.multiplayer.ClientLevel'] = '''
public class ClientLevel extends net.minecraft.world.level.Level {
 public final net.minecraft.client.ClientClockManager manager=new net.minecraft.client.ClientClockManager();
 public net.minecraft.client.ClientClockManager clockManager(){return manager;}
 public Registry registryAccess(){return new Registry();}
 public static class Registry {public java.util.Optional<net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock>> get(Object k){return java.util.Optional.of(new net.minecraft.core.Holder<>(k));}}
}'''
    stubs['net.minecraft.client.ClientClockManager'] = '''
public class ClientClockManager EXTENSION {
 public final ClientClockInstance overworld=new ClientClockInstance();
 public ClientClockInstance getInstance(net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> h){return overworld;}
 public static class ClientClockInstance INSTANCE_EXTENSION {public long ticks=43210;public float rate=.5f;}
}'''.replace('INSTANCE_EXTENSION', 'extends net.wurstclient.mixin.ClientClockManagerMixin' if instance else '').replace(
        'EXTENSION', 'extends net.wurstclient.mixin.ClientClockManagerMixin' if modern and not instance else '')
    for name, body in stubs.items():
        source(name, body)

    receiver = 'world.manager.overworld' if instance else 'world.manager' if modern else 'world'
    other = 'otherWorld.manager.overworld' if instance else 'otherWorld.manager' if modern else 'otherWorld'
    parameters = ',new net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock>(net.minecraft.world.clock.WorldClocks.OVERWORLD)' if modern and not instance else ''
    extra = '''
 hax.noWeatherHack.changed=true;check(read(current,51000)==54000,"NoWeather retains time override priority and day number");
 hax.noWeatherHack.changed=false;check(read(current,51000)==-12300,"TimeChanger resumes after NoWeather override");
''' if modern else ''
    if modern and not instance:
        extra += '''
 Method m=net.wurstclient.mixin.ClientClockManagerMixin.class.getDeclaredMethod("onGetTotalTicks",long.class,net.minecraft.core.Holder.class);m.setAccessible(true);
 check((long)m.invoke(current,9876L,new net.minecraft.core.Holder<>(new Object()))==9876,"other clock definitions stay untouched");
'''
    if not modern and 'implements LevelAccessor' in (root / ('mixin/' + hook + '.java')).read_text(encoding='utf-8'):
        extra += 'check(world.dayTime()==-12300,"legacy interface reads route through the same getter");\n'
    source('audit.Batch16Probe', '''
import java.lang.reflect.*;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
public class Batch16Probe {
static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
public static long read(Object receiver,long original){try {
 for(Method m:net.wurstclient.mixin.HOOK.class.getDeclaredMethods())if(m.getName().equals("wurstDisplayTime")||m.getName().equals("onGetTotalTicks")){
  m.setAccessible(true);return (long)m.invoke(receiver,originalPARAMETERS);
 }throw new AssertionError("missing callback");
}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
public static void main(String[] args)throws Exception{
 var mc=Minecraft.getInstance();var client=WurstClient.INSTANCE;var hax=client.hax;var time=hax.timeChangerHack;
 var world=new ClientLevel();var otherWorld=new ClientLevel();mc.level=world;
 Object current=RECEIVER;Object other=OTHER;
 check(read(current,43210)==43210,"disabled keeps real time");
 time.setEnabled(true);check(read(current,43210)==0,"enabled default display time");
 Field f=time.getClass().getDeclaredField("time");f.setAccessible(true);((SliderSetting)f.get(time)).value=-12300;
 check(read(current,55555)==-12300,"negative time setting is applied immediately");
 check(read(other,98765)==98765,"other world never inherits current display time");
 EXTRA
 world.rawTime=90123;world.manager.overworld.ticks=90123;world.manager.overworld.rate=0;
 check(read(current,90123)==-12300,"new server update stays visually overridden");
 time.setEnabled(false);check(read(current,90123)==90123,"disable reveals latest server time, not activation time");
 check(world.rawTime==90123&&world.manager.overworld.ticks==90123&&world.manager.overworld.rate==0,"stored time and stopped clock remain intact");
 time.setEnabled(true);mc.level=otherWorld;check(read(current,321)==321&&read(other,987)==-12300,"world changes isolate old clock and apply to new world");
 mc.level=null;check(read(current,444)==444,"disconnect bypasses override");
 mc.level=world;client.hax=null;check(read(current,555)==555,"uninitialized module list bypasses override");client.hax=hax;
 System.out.println("PASS: unchanged TimeChanger and read callbacks; live settings, server updates, disable, clock state, world isolation and override priority");
}}
'''.replace('HOOK', hook).replace('PARAMETERS', parameters).replace('RECEIVER', receiver).replace('OTHER', other).replace('EXTRA', extra))
    java = Path.home() / '.jdks/jdk25.0.4_10/bin'
    argfile = out / 'javac.args'
    argfile.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in src.rglob('*.java')), encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out / 'classes'), '@' + str(argfile)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(out / 'classes'), 'audit.Batch16Probe'], check=True)
    (out / 'provenance.json').write_text(json.dumps({'target': args.target, 'staged': args.staged, 'sha256': hashes}, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
