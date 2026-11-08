"""Exercise production weather callbacks and vanilla rain/thunder composition."""
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
    out = PLAN / 'batch18' / ('tests-' + args.target.replace('/', '-'))
    src = out / 'src'
    base = PLAN / 'batch18/generated' if args.staged else ROOT
    root = base / args.target / 'src/main/java/net/wurstclient'
    hashes = {}

    def source(name, body):
        file = src / (name.replace('.', '/') + '.java')
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')

    for relative in ['hacks/WeatherChangerHack.java', 'mixin/WeatherChangerLevelMixin.java']:
        file = root / relative
        target = src / 'net/wurstclient' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(file, target)
        hashes[relative] = hashlib.sha256(file.read_bytes()).hexdigest()
    stubs = {
        'net.wurstclient.Category': 'public enum Category {RENDER}',
        'net.wurstclient.SearchTags': 'public @interface SearchTags {String[] value();}',
        'org.spongepowered.asm.mixin.Mixin': 'public @interface Mixin {Class<?>[] value();}',
        'org.spongepowered.asm.mixin.injection.At': 'public @interface At {String value();}',
        'com.llamalad7.mixinextras.injector.ModifyReturnValue': 'public @interface ModifyReturnValue {String method();org.spongepowered.asm.mixin.injection.At at();}',
        'net.wurstclient.settings.SliderSetting': 'public class SliderSetting {public float value;public SliderSetting(String n,String d,double v,double min,double max,double step,ValueDisplay display){value=(float)v;}public float getValueF(){return value;}public enum ValueDisplay{PERCENTAGE}}',
        'net.wurstclient.hack.Hack': 'public class Hack {private boolean enabled;public Hack(String n){}public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;}protected void addSetting(Object s){}protected void setCategory(Object c){}}',
        'net.wurstclient.hacks.NoWeatherHack': 'public class NoWeatherHack {public boolean disabled;public boolean isRainDisabled(){return disabled;}}',
        'net.wurstclient.WurstClient': 'public class WurstClient {public static final WurstClient INSTANCE=new WurstClient();public Hacks hax=new Hacks();public Hacks getHax(){return hax;}public static class Hacks{public net.wurstclient.hacks.WeatherChangerHack weatherChangerHack=new net.wurstclient.hacks.WeatherChangerHack();public net.wurstclient.hacks.NoWeatherHack noWeatherHack=new net.wurstclient.hacks.NoWeatherHack();}}',
        'net.minecraft.client.Minecraft': 'public class Minecraft {private static final Minecraft INSTANCE=new Minecraft();public net.minecraft.world.level.Level level;public static Minecraft getInstance(){return INSTANCE;}}',
        'net.minecraft.world.level.Level': '''public class Level extends net.wurstclient.mixin.WeatherChangerLevelMixin {
 public float rain=.5f,thunder=.75f;public boolean raining=true;
 public float getRainLevel(float delta){return audit.Batch18Probe.read(this,"wurstRainLevel",rain);}
 public float getThunderLevel(float delta){return audit.Batch18Probe.read(this,"wurstThunderLevel",thunder*getRainLevel(delta));}
}''',
    }
    for name, body in stubs.items():
        source(name, body)
    source('audit.Batch18Probe', '''
import java.lang.reflect.*;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.SliderSetting;
public class Batch18Probe {
static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
static void number(Object o,String name,float value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);((SliderSetting)f.get(o)).value=value;}
public static float read(Object receiver,String name,float original){try{Method m=net.wurstclient.mixin.WeatherChangerLevelMixin.class.getDeclaredMethod(name,float.class);m.setAccessible(true);return (float)m.invoke(receiver,original);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
public static void main(String[] args)throws Exception{
 var mc=net.minecraft.client.Minecraft.getInstance();var client=WurstClient.INSTANCE;var hax=client.hax;var weather=hax.weatherChangerHack;
 var level=new net.minecraft.world.level.Level();var other=new net.minecraft.world.level.Level();mc.level=level;
 check(level.getRainLevel(1)==.5f&&level.getThunderLevel(1)==.375f,"disabled preserves vanilla intensity multiplication");
 weather.setEnabled(true);check(level.getRainLevel(1)==0&&level.getThunderLevel(1)==0,"native clear defaults");
 number(weather,"rainLevel",.25f);number(weather,"thunderLevel",.5f);
 check(level.getRainLevel(1)==.25f&&level.getThunderLevel(1)==.125f,"thunder multiplied by chosen rain exactly once");
 check(level.rain==.5f&&level.thunder==.75f&&level.raining,"underlying server state never overwritten");
 check(other.getRainLevel(1)==.5f&&other.getThunderLevel(1)==.375f,"other worlds untouched");
 level.rain=0;level.thunder=0;level.raining=false;
 check(level.getRainLevel(1)==.25f&&level.getThunderLevel(1)==.125f,"server stop-rain update does not change override");
 weather.setEnabled(false);check(level.getRainLevel(1)==0&&level.getThunderLevel(1)==0&&!level.raining,"disable reveals latest stop-rain state");
 weather.setEnabled(true);level.rain=.75f;level.thunder=.5f;level.raining=true;
 hax.noWeatherHack.disabled=true;check(level.getRainLevel(1)==0&&level.getThunderLevel(1)==0,"NoWeather has priority");
 hax.noWeatherHack.disabled=false;check(level.getThunderLevel(1)==.125f,"NoWeather disable resumes weather settings");
 number(weather,"rainLevel",0);number(weather,"thunderLevel",1);check(level.getThunderLevel(1)==0,"no rain means no effective thunder");
 weather.setEnabled(false);check(level.getRainLevel(1)==.75f&&level.getThunderLevel(1)==.375f,"disable restores current intensity without multiplying saved effective thunder again");
 weather.setEnabled(true);mc.level=other;check(level.getRainLevel(1)==.75f&&other.getRainLevel(1)==0,"world transition only affects current level");
 mc.level=null;check(other.getRainLevel(1)==.5f,"disconnect bypass");
 mc.level=other;client.hax=null;check(other.getThunderLevel(1)==.375f,"uninitialized modules bypass");client.hax=hax;
 System.out.println("PASS: unchanged weather module/callbacks; live server state, stop-rain, thunder multiplication, world isolation and NoWeather priority");
}}
''')
    java = Path.home() / '.jdks/jdk25.0.4_10/bin'
    argfile = out / 'javac.args'
    argfile.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in src.rglob('*.java')), encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out / 'classes'), '@' + str(argfile)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(out / 'classes'), 'audit.Batch18Probe'], check=True)
    (out / 'provenance.json').write_text(json.dumps({'target': args.target, 'staged': args.staged, 'sha256': hashes}, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
