"""Exercise unchanged FOV/chat modules and verbatim FOV callback methods."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from sync_four_client_batch import ROOT, PLAN


def fov_callbacks(text):
    methods = []
    for name in ['onGetFov', 'onCalculateFov', 'onCalculateHudFov']:
        index = text.find(' ' + name + '(')
        if index < 0:
            continue
        start = text.rfind('\t@ModifyReturnValue', 0, index)
        end = text.index('\n\t}', index) + len('\n\t}')
        assert start >= 0, name
        methods.append(text[start:end])
    assert len(methods) in (1, 2)
    return '\n\n'.join(methods)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', default='fabric')
    parser.add_argument('--staged', action='store_true')
    args = parser.parse_args()
    out = PLAN / 'batch14' / ('tests-' + args.target.replace('/', '-'))
    src = out / 'src'
    base = PLAN / 'batch14/generated' if args.staged else ROOT
    root = base / args.target / 'src/main/java/net/wurstclient'
    hashes = {}

    def source(name, body):
        file = src / (name.replace('.', '/') + '.java')
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')

    for relative in ['hacks/CustomFovHack.java', 'hacks/ChatHiderHack.java']:
        file = root / relative
        target = src / 'net/wurstclient' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(file, target)
        hashes[relative] = hashlib.sha256(file.read_bytes()).hexdigest()
    camera = root / 'mixin/CameraMixin.java'
    hook = camera if camera.exists() and 'onCalculateFov' in camera.read_text(encoding='utf-8') else root / 'mixin/GameRendererMixin.java'
    methods = fov_callbacks(hook.read_text(encoding='utf-8'))
    hashes[hook.relative_to(root).as_posix()] = hashlib.sha256(hook.read_bytes()).hexdigest()
    source('audit.FovCallbacks', '''
import net.wurstclient.WurstClient;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;
public class FovCallbacks {
''' + methods + '\n}\n')
    stubs = {
        'net.wurstclient.Category': 'public enum Category { RENDER, CHAT }',
        'net.wurstclient.SearchTags': 'public @interface SearchTags {String[] value();}',
        'net.wurstclient.events.UpdateListener': 'public interface UpdateListener {void onUpdate();}',
        'net.minecraft.client.Camera': 'public class Camera {}',
        'net.minecraft.client.gui.screens.ChatScreen': 'public class ChatScreen {}',
        'com.llamalad7.mixinextras.injector.ModifyReturnValue': 'public @interface ModifyReturnValue {String method(); org.spongepowered.asm.mixin.injection.At at();}',
        'org.spongepowered.asm.mixin.injection.At': 'public @interface At {String value();}',
        'net.wurstclient.settings.SliderSetting': '''public class SliderSetting {public double value;public SliderSetting(String n,String d,double v,double min,double max,double step,ValueDisplay display){value=v;}public double getValue(){return value;}public int getValueI(){return (int)value;}public enum ValueDisplay {INTEGER,DECIMAL;public ValueDisplay withSuffix(String s){return this;}}}''',
        'net.minecraft.client.Minecraft': '''public class Minecraft {public Object player=new Object(),screen;public final Options options=new Options();public final Gui gui=new Gui();public class Gui{public Object screen(){return screen;}}public static class Option{double value=.4;public Double get(){return value;}public void set(Double v){value=v;}}public static class Options{public Option opacity=new Option();public Option chatOpacity(){return opacity;}}}''',
        'net.wurstclient.WurstClient': '''public class WurstClient {public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();public static final WurstClient INSTANCE=new WurstClient();public final Hacks hax=new Hacks();public final Otfs otfs=new Otfs();public Hacks getHax(){return hax;}public Otfs getOtfs(){return otfs;}public static class Hacks{public net.wurstclient.hacks.CustomFovHack customFovHack;}public static class Otfs{public Zoom zoomOtf=new Zoom();}public static class Zoom{public double factor=1;public int calls;public double changeFovBasedOnZoom(double f){calls++;return f/factor;}}}''',
        'net.wurstclient.hack.Hack': '''public class Hack {protected static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;protected static final net.wurstclient.WurstClient WURST=net.wurstclient.WurstClient.INSTANCE;protected static final Events EVENTS=new Events();private boolean enabled;public Hack(String n){}public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){if(enabled==v)return;enabled=v;if(v)onEnable();else onDisable();}protected void onEnable(){}protected void onDisable(){}protected void addSetting(Object s){}protected void setCategory(Object c){}public static class Events{public void add(Object a,Object b){}public void remove(Object a,Object b){}}}''',
    }
    for name, body in stubs.items():
        source(name, body)
    source('audit.Batch14Probe', '''
import java.lang.reflect.*;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.*;
import net.wurstclient.settings.SliderSetting;
public class Batch14Probe {
static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
static void number(Object o,String name,double value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);((SliderSetting)f.get(o)).value=value;}
static double callback(boolean world,double original)throws Exception{
 for(Method m:FovCallbacks.class.getDeclaredMethods()){
  if(m.getName().equals("onGetFov")){m.setAccessible(true);Object v;if(m.getParameterTypes()[0]==double.class)v=Double.valueOf(original);else v=Float.valueOf((float)original);return ((Number)m.invoke(new FovCallbacks(),v,new net.minecraft.client.Camera(),0f,world)).doubleValue();}
  if(m.getName().equals(world?"onCalculateFov":"onCalculateHudFov")){m.setAccessible(true);return ((Number)m.invoke(new FovCallbacks(),(float)original)).doubleValue();}
 }throw new AssertionError("missing world/hand callback");
}
public static void main(String[] args)throws Exception{
 var client=WurstClient.INSTANCE;var mc=WurstClient.MC;var zoom=client.otfs.zoomOtf;
 var fov=new CustomFovHack();client.hax.customFovHack=fov;
 check(callback(true,90)==90&&callback(false,70)==70,"disabled FOV keeps vanilla world and hand");
 zoom.factor=2;check(callback(true,90)==45&&callback(false,70)==35,"disabled CustomFov keeps native zoom for world and hand");
 fov.setEnabled(true);check(callback(true,90)==55&&callback(false,90)==70,"world FOV composes with zoom; item FOV stays independent");
 int calls=zoom.calls;callback(false,90);check(zoom.calls==calls,"custom hand bypasses zoom");
 number(fov,"fov",170);number(fov,"itemFov",30);zoom.factor=1;check(callback(true,80)==170&&callback(false,80)==30,"settings apply without a tick or option mutation");
 fov.setEnabled(false);check(callback(true,80)==80&&callback(false,65)==65,"disable restores renderer's current FOV");
 var chat=new ChatHiderHack();mc.options.chatOpacity().set(.4);chat.setEnabled(true);chat.onUpdate();check(mc.options.chatOpacity().get()==0,"closed chat hidden");
 number(chat,"openOpacity",.8);mc.screen=new net.minecraft.client.gui.screens.ChatScreen();chat.onUpdate();check(mc.options.chatOpacity().get()==.8,"open chat configured opacity");
 chat.setEnabled(false);mc.screen=null;chat.onUpdate();check(mc.options.chatOpacity().get()==.4,"late update cannot undo opacity restoration");
 mc.options.chatOpacity().set(0.0);chat.setEnabled(true);chat.setEnabled(false);check(mc.options.chatOpacity().get()==.8,"source fallback recovers zero opacity");
 chat.setEnabled(true);mc.player=null;chat.onUpdate();check(mc.options.chatOpacity().get()==.8,"disconnected update leaves settings alone");chat.setEnabled(false);
 System.out.println("PASS: actual FOV callbacks, world/hand/zoom combinations, live settings, chat visibility and disable/disconnect cleanup");
}}
''')
    java = Path.home() / '.jdks/jdk25.0.4_10/bin'
    argfile = out / 'javac.args'
    argfile.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in src.rglob('*.java')), encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out / 'classes'), '@' + str(argfile)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(out / 'classes'), 'audit.Batch14Probe'], check=True)
    (out / 'provenance.json').write_text(json.dumps({'target': args.target, 'staged': args.staged, 'sha256': hashes}, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
