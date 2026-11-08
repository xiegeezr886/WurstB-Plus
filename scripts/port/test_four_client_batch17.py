"""Execute unmodified Reach source and callbacks; no game runtime claims."""
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
    tree = next(t for t in json.loads((PLAN / 'target-inventory.json').read_text(encoding='utf-8')) if t['root'] == args.target)
    legacy = tree['mc'] in ['1.20.1', '1.20.2', '1.20.3', '1.20.4']
    out = PLAN / 'batch17' / ('tests-' + args.target.replace('/', '-'))
    src = out / 'src'
    base = PLAN / 'batch17/generated' if args.staged else ROOT
    root = base / args.target / 'src/main/java/net/wurstclient'
    hashes = {}

    def source(name, body):
        file = src / (name.replace('.', '/') + '.java')
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')

    hook = 'ReachLegacyRendererMixin' if legacy else 'ReachPlayerMixin'
    for relative in ['hacks/ReachHack.java', 'mixin/' + hook + '.java']:
        file = root / relative
        target = src / 'net/wurstclient' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(file, target)
        hashes[relative] = hashlib.sha256(file.read_bytes()).hexdigest()
    stubs = {
        'net.wurstclient.Category': 'public enum Category {OTHER}',
        'net.wurstclient.SearchTags': 'public @interface SearchTags {String[] value();}',
        'org.spongepowered.asm.mixin.Mixin': 'public @interface Mixin {Class<?>[] value();}',
        'org.spongepowered.asm.mixin.injection.At': 'public @interface At {String value();}',
        'org.spongepowered.asm.mixin.injection.Constant': 'public @interface Constant {double doubleValue();}',
        'org.spongepowered.asm.mixin.injection.ModifyConstant': 'public @interface ModifyConstant {String method(); Constant constant();}',
        'com.llamalad7.mixinextras.injector.ModifyReturnValue': 'public @interface ModifyReturnValue {String method();org.spongepowered.asm.mixin.injection.At at();}',
        'net.wurstclient.settings.SliderSetting': 'public class SliderSetting {public double value;public SliderSetting(String n,String d,double v,double min,double max,double step,ValueDisplay display){value=v;}public double getValue(){return value;}public SliderSetting aliases(String a){return this;}public enum ValueDisplay{DECIMAL}}',
        'net.wurstclient.settings.CheckboxSetting': 'public class CheckboxSetting {public boolean value;public CheckboxSetting(String n,String d,boolean v){value=v;}public boolean isChecked(){return value;}}',
        'net.wurstclient.hack.Hack': 'public class Hack {protected static final net.minecraft.client.Minecraft MC=net.minecraft.client.Minecraft.getInstance();private boolean enabled;public Hack(String n){}public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;}protected void addSetting(Object s){}protected void setCategory(Object c){}}',
        'net.wurstclient.WurstClient': 'public class WurstClient {public static final WurstClient INSTANCE=new WurstClient();public Hacks hax=new Hacks();public Hacks getHax(){return hax;}public static class Hacks{public net.wurstclient.hacks.ReachHack reachHack=new net.wurstclient.hacks.ReachHack();}}',
        'net.minecraft.client.Minecraft': 'public class Minecraft {private static final Minecraft INSTANCE=new Minecraft();public net.minecraft.world.entity.player.Player player;public final GameMode gameMode=new GameMode();public static Minecraft getInstance(){return INSTANCE;}public static class GameMode {public boolean creative;public boolean hasInfiniteItems(){return creative;}}}',
        'net.minecraft.client.renderer.GameRenderer': 'public class GameRenderer '+('extends net.wurstclient.mixin.ReachLegacyRendererMixin ' if legacy else '')+'{}',
        'net.minecraft.world.entity.player.Player': 'public class Player '+('' if legacy else 'extends net.wurstclient.mixin.ReachPlayerMixin ')+'{public double entity=3,block=4.5;public double entityInteractionRange(){return '+('entity' if legacy else 'audit.Batch17Probe.read(this,"wurstEntityReach",entity)')+';}public double blockInteractionRange(){return '+('block' if legacy else 'audit.Batch17Probe.read(this,"wurstBlockReach",block)')+';}}',
    }
    for name, body in stubs.items():
        source(name, body)
    extra = '''
 var renderer=new net.minecraft.client.renderer.GameRenderer();
 check(read(renderer,"wurstEntitySearchDistance",6)==3.75,"legacy search uses survival entity base plus extra");
 mc.gameMode.creative=true;check(reach.getEntityRange()==6.75&&reach.getBlockRange()==7.25,"legacy creative bases");
 number(reach,"extraEntityRange",8);check(read(renderer,"wurstEntitySearchDistance",6)==14,"legacy entity ray no longer capped at six");
''' if legacy else '''
 player.entity=7.25;player.block=11.5;
 check(reach.getEntityRange()==8&&reach.getBlockRange()==13.75,"attribute and creative bonuses are preserved before adding exactly once");
 var other=new net.minecraft.world.entity.player.Player();other.entity=12;other.block=15;
 check(other.entityInteractionRange()==12&&other.blockInteractionRange()==15,"other players retain their ranges");
 mc.player=other;check(player.entityInteractionRange()==7.25,"old local player no longer receives range override");
 mc.player=null;check(reach.getEntityRange()==0&&reach.getBlockRange()==0,"disconnected range helpers are safe");
 mc.player=player;var hax=client.hax;client.hax=null;check(player.entityInteractionRange()==7.25,"initialization guard");client.hax=hax;
'''
    source('audit.Batch17Probe', '''
import java.lang.reflect.*;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.*;
public class Batch17Probe {
static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
static void number(Object o,String name,double value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);((SliderSetting)f.get(o)).value=value;}
static void mode(Object o,boolean value)throws Exception{Field f=o.getClass().getDeclaredField("extraMode");f.setAccessible(true);((CheckboxSetting)f.get(o)).value=value;}
public static double read(Object receiver,String name,double original){try{Method m=net.wurstclient.mixin.HOOK.class.getDeclaredMethod(name,double.class);m.setAccessible(true);return (double)m.invoke(receiver,original);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
public static void main(String[] args)throws Exception{
 var mc=net.minecraft.client.Minecraft.getInstance();var client=WurstClient.INSTANCE;var reach=client.hax.reachHack;
 var player=new net.minecraft.world.entity.player.Player();mc.player=player;
 check(reach.changeEntityRange(4.75)==4.75&&reach.changeBlockRange(9)==9,"disabled uses original distances");
 reach.setEnabled(true);check(reach.getEntityRange()==6&&reach.getBlockRange()==5,"native absolute defaults stay independent");
 number(reach,"entityRange",2);number(reach,"blockRange",9);
 check(reach.getEntityRange()==2&&reach.getBlockRange()==9&&reach.getReachDistance()==9,"different configured distances must not collapse to the maximum");
 mode(reach,true);check(reach.getEntityRange()==3&&reach.getBlockRange()==4.5,"zero extra keeps original ranges");
 number(reach,"extraEntityRange",.75);number(reach,"extraBlockRange",2.25);
 check(reach.getEntityRange()==3.75&&reach.getBlockRange()==6.75,"fractional extras apply independently");
 EXTRA
 reach.setEnabled(false);check(reach.changeEntityRange(3.25)==3.25&&reach.changeBlockRange(12.5)==12.5,"disable immediately returns current originals");
 System.out.println("PASS: unchanged Reach module/callbacks; absolute defaults, independent ranges, additive mode, native bonuses and cleanup");
}}
'''.replace('HOOK', hook).replace('EXTRA', extra))
    java = Path.home() / '.jdks/jdk25.0.4_10/bin'
    argfile = out / 'javac.args'
    argfile.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in src.rglob('*.java')), encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out / 'classes'), '@' + str(argfile)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(out / 'classes'), 'audit.Batch17Probe'], check=True)
    (out / 'provenance.json').write_text(json.dumps({'target': args.target, 'staged': args.staged, 'sha256': hashes}, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
