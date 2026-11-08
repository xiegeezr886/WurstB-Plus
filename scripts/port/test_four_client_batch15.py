"""Probe Parkour support geometry using unchanged source and Minecraft's AABB."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from sync_four_client_batch import ROOT, PLAN


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--staged', action='store_true')
    parser.add_argument('--target', default='fabric')
    args = parser.parse_args()
    rows = json.loads((PLAN / 'minecraft-api.json').read_text(encoding='utf-8'))
    row = next(row for row in rows if row['root'] == args.target)
    out = PLAN / 'batch15' / ('tests-' + args.target.replace('/', '-'))
    src = out / 'src'

    def source(name, body):
        file = src / (name.replace('.', '/') + '.java')
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')

    root = PLAN / 'batch15/src/net/wurstclient' if args.staged else ROOT / args.target / 'src/main/java/net/wurstclient'
    file = root / 'hacks/ParkourHack.java'
    dest = src / 'net/wurstclient/hacks/ParkourHack.java'
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(file, dest)
    modern = row['mc'].startswith('26.') or tuple(map(int, row['mc'].split('.'))) >= (1, 21, 6)
    sneak_root = PLAN / 'batch15' / ('modern' if modern else 'src') / 'net/wurstclient' if args.staged else root
    sneak_file = sneak_root / 'hacks/SneakHack.java'
    shutil.copyfile(sneak_file, src / 'net/wurstclient/hacks/SneakHack.java')
    stubs = {
        'net.wurstclient.Category': 'public enum Category {MOVEMENT}',
        'net.wurstclient.events.PreMotionListener': 'public interface PreMotionListener {void onPreMotion();}',
        'net.wurstclient.events.PostMotionListener': 'public interface PostMotionListener {void onPostMotion();}',
        'net.wurstclient.SearchTags': 'public @interface SearchTags {String[] value();}',
        'net.wurstclient.settings.EnumSetting': 'public class EnumSetting<T extends Enum<T>> {public T selected;public EnumSetting(String n,String d,T[] values,T v){selected=v;}public T getSelected(){return selected;}}',
        'net.wurstclient.mixinterface.IKeyBinding': 'public interface IKeyBinding {void setPressed(boolean v);void resetPressedState();static IKeyBinding get(net.minecraft.client.Minecraft.Key k){return k;}}',
        'net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket': 'public record ServerboundPlayerCommandPacket(Object player,Action action){public enum Action{PRESS_SHIFT_KEY,RELEASE_SHIFT_KEY}}',
        'net.minecraft.network.protocol.game.ServerboundPlayerInputPacket': 'public record ServerboundPlayerInputPacket(net.minecraft.world.entity.player.Input input){}',
        'net.minecraft.world.entity.player.Input': 'public record Input(boolean forward,boolean backward,boolean left,boolean right,boolean jump,boolean shift,boolean sprint){}',
        'net.wurstclient.events.UpdateListener': 'public interface UpdateListener {void onUpdate();}',
        'net.wurstclient.settings.CheckboxSetting': 'public class CheckboxSetting {public boolean value;public CheckboxSetting(String n,String d,boolean v){value=v;}public boolean isChecked(){return value;}}',
        'net.wurstclient.settings.SliderSetting': 'public class SliderSetting {public double value;public SliderSetting(String n,String d,double v,double min,double max,double step,ValueDisplay display){value=v;}public double getValue(){return value;}public enum ValueDisplay{DECIMAL;public ValueDisplay withSuffix(String s){return this;}}}',
        'net.wurstclient.WurstClient': 'public class WurstClient {public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();public static final WurstClient INSTANCE=new WurstClient();public final Hacks hax=new Hacks();public Hacks getHax(){return hax;}public static class Hacks{public Toggle safeWalkHack=new Toggle(),flightHack=new Toggle(),freecamHack=new Toggle();public Hacks(){safeWalkHack.enabled=true;}}public static class Toggle{public boolean enabled;public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;}}}',
        'net.wurstclient.hack.Hack': 'public class Hack {protected static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;protected static final net.wurstclient.WurstClient WURST=net.wurstclient.WurstClient.INSTANCE;protected static final Events EVENTS=new Events();private boolean enabled;public Hack(String n){}public String getName(){return "fixture";}public String getRenderName(){return getName();}public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){if(enabled==v)return;enabled=v;if(v)onEnable();else onDisable();}protected void onEnable(){}protected void onDisable(){}protected void addSetting(Object s){}protected void setCategory(Object c){}public static class Events{public void add(Object a,Object b){}public void remove(Object a,Object b){}}}',
        'net.minecraft.client.Minecraft': 'public class Minecraft {public net.minecraft.client.player.LocalPlayer player=new net.minecraft.client.player.LocalPlayer();public net.minecraft.client.multiplayer.ClientLevel level=new net.minecraft.client.multiplayer.ClientLevel();public Options options=new Options();public static class Options{public Key keyJump=new Key(),keyShift=new Key();}public static class Key implements net.wurstclient.mixinterface.IKeyBinding{public boolean pressed,physical;public boolean isDown(){return pressed;}public void setPressed(boolean v){pressed=v;}public void resetPressedState(){pressed=physical;}}}',
        'net.minecraft.client.player.LocalPlayer': 'public class LocalPlayer {public boolean grounded=true,sneaking;public int jumps;public net.minecraft.world.phys.AABB box=new net.minecraft.world.phys.AABB(.2,64,.2,.8,65.8,.8);public final Abilities abilities=new Abilities();public Abilities getAbilities(){return abilities;}public static class Abilities{public boolean flying;}public Connection connection=new Connection();public static class Connection{public final java.util.List<Object> sent=new java.util.ArrayList<>();public void send(Object packet){sent.add(packet);}}public InputState input=new InputState();public static class InputState{public net.minecraft.world.entity.player.Input keyPresses=new net.minecraft.world.entity.player.Input(true,false,false,true,true,false,true);}public boolean onGround(){return grounded;}public boolean isShiftKeyDown(){return sneaking;}public net.minecraft.world.phys.AABB getBoundingBox(){return box;}public void jumpFromGround(){jumps++;}}',
        'net.minecraft.client.multiplayer.ClientLevel': '''public class ClientLevel {
public java.util.List<net.minecraft.world.phys.AABB> blocks=new java.util.ArrayList<>(),entities=new java.util.ArrayList<>();
public boolean noCollision(Object entity,net.minecraft.world.phys.AABB box){return blocks.stream().noneMatch(box::intersects)&&entities.stream().noneMatch(box::intersects);}
public Iterable<net.minecraft.world.phys.AABB> getBlockCollisions(Object entity,net.minecraft.world.phys.AABB box){return blocks.stream().filter(box::intersects).toList();}}
''',
    }
    for name, body in stubs.items():
        source(name, body)
    source('audit.Batch15Probe', '''
import java.lang.reflect.*;import net.wurstclient.WurstClient;import net.wurstclient.hacks.ParkourHack;import net.minecraft.world.phys.AABB;
public class Batch15Probe {
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
static void bool(Object h,String name,boolean value)throws Exception{Field f=h.getClass().getDeclaredField(name);f.setAccessible(true);((net.wurstclient.settings.CheckboxSetting)f.get(h)).value=value;}
public static void main(String[] args)throws Exception{
 var mc=WurstClient.MC;var player=mc.player;var level=mc.level;var h=new ParkourHack();h.onUpdate();check(player.jumps==0,"disabled does not jump");h.setEnabled(true);check(!WurstClient.INSTANCE.hax.safeWalkHack.enabled,"native SafeWalk exclusion retained");
 level.blocks.add(new AABB(0,63,0,1,64,1));h.onUpdate();check(player.jumps==0,"full block support prevents jumping");
 player.box=new AABB(.9995,64,.2,1.5995,65.8,.8);h.onUpdate();check(player.jumps==1,"native inset edge threshold");
 player.jumps=0;level.entities.add(new AABB(.9,63.9,.1,1.5,65.7,.9));h.onUpdate();check(player.jumps==0,"native mode retains entity collisions");
 bool(h,"blockCollisionsOnly",true);h.onUpdate();check(player.jumps==1,"Meteor mode ignores entity support at edge");
 player.jumps=0;player.box=new AABB(.998,64,.2,1.598,65.8,.8);h.onUpdate();check(player.jumps==0,"block shape support still prevents jump");
 level.blocks.clear();mc.options.keyJump.pressed=true;h.onUpdate();check(player.jumps==0,"manual jump bypass");mc.options.keyJump.pressed=false;
 mc.options.keyShift.pressed=true;h.onUpdate();check(player.jumps==0,"physical sneak bypass");mc.options.keyShift.pressed=false;player.sneaking=true;h.onUpdate();check(player.jumps==0,"player sneak bypass");
 bool(h,"sneak",true);h.onUpdate();check(player.jumps==1,"existing jump while sneaking option retained");
 player.jumps=0;player.grounded=false;h.onUpdate();check(player.jumps==0,"no midair jump");player.grounded=true;h.setEnabled(false);h.onUpdate();check(player.jumps==0,"late update after disable harmless");
 h.setEnabled(true);mc.player=null;h.onUpdate();mc.player=player;mc.level=null;h.onUpdate();check(player.jumps==0,"disconnect transitions harmless");
 System.out.println("PASS: actual Minecraft AABB edge geometry, block/entity support modes, existing controls and lifecycle guards");
}}
''')
    source('audit.SneakProbe', '''
import java.lang.reflect.*;import net.wurstclient.WurstClient;import net.wurstclient.hacks.SneakHack;
public class SneakProbe {
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
static void bool(Object h,String n,boolean v)throws Exception{Field f=h.getClass().getDeclaredField(n);f.setAccessible(true);((net.wurstclient.settings.CheckboxSetting)f.get(h)).value=v;}
@SuppressWarnings({"rawtypes","unchecked"}) static void mode(Object h,String n)throws Exception{Field f=h.getClass().getDeclaredField("mode");f.setAccessible(true);var setting=(net.wurstclient.settings.EnumSetting)f.get(h);setting.selected=Enum.valueOf(setting.selected.getDeclaringClass(),n);}
static boolean shift(Object packet){if(packet instanceof net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket p)return p.action()==net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY;return ((net.minecraft.network.protocol.game.ServerboundPlayerInputPacket)packet).input().shift();}
public static void main(String[] args)throws Exception{
 var mc=WurstClient.MC;var p=mc.player;var h=new SneakHack();h.onPreMotion();check(!mc.options.keyShift.pressed,"disabled does not force key");
 h.setEnabled(true);h.onPreMotion();check(mc.options.keyShift.pressed,"native legit mode forces sneak");h.setEnabled(false);check(!mc.options.keyShift.pressed,"legit cleanup restores physical key");
 h.setEnabled(true);mode(h,"PACKET");h.onPreMotion();h.onPostMotion();check(p.connection.sent.size()==4&&shift(p.connection.sent.get(3)),"native packet sequence retained");
 for(Object sent:p.connection.sent)if(sent instanceof net.minecraft.network.protocol.game.ServerboundPlayerInputPacket packet){var i=packet.input();check(i.forward()&&!i.backward()&&!i.left()&&i.right()&&i.jump()&&i.sprint(),"modern packet preserves other input flags");}
 bool(h,"offWhileFlying",true);p.abilities.flying=true;h.onPreMotion();h.onPostMotion();check(p.connection.sent.size()==5&&!shift(p.connection.sent.get(4)),"flight releases owned packet sneak and does not reapply");
 h.onPreMotion();h.onPostMotion();check(p.connection.sent.size()==5,"flight pause emits no repeated release");p.abilities.flying=false;h.onPreMotion();h.onPostMotion();check(shift(p.connection.sent.get(p.connection.sent.size()-1)),"packet sneak resumes after flight");
 mode(h,"LEGIT");h.onPreMotion();check(!shift(p.connection.sent.get(p.connection.sent.size()-1))&&mc.options.keyShift.pressed,"mode change releases old packet state before legit input");
 WurstClient.INSTANCE.hax.freecamHack.enabled=true;h.onPreMotion();check(!mc.options.keyShift.pressed,"freecam pauses legit mode");WurstClient.INSTANCE.hax.freecamHack.enabled=false;
 mode(h,"PACKET");h.onPreMotion();h.onPostMotion();mc.player=new net.minecraft.client.player.LocalPlayer();h.setEnabled(false);check(mc.player.connection.sent.isEmpty(),"old player's packet state not released onto new connection");
 h.setEnabled(true);mc.player=null;h.onPreMotion();h.onPostMotion();h.setEnabled(false);check(!mc.options.keyShift.pressed,"disconnect cleanup safe");
 mc.player=p;mc.options.keyShift.physical=true;h.setEnabled(true);mode(h,"LEGIT");h.onPreMotion();mode(h,"PACKET");h.setEnabled(false);check(mc.options.keyShift.pressed,"disable respects physical key even after mode change");
 System.out.println("PASS: native Sneak modes, both-mode flight pause, packet/key ownership cleanup, modern input flags and disconnect handling");
}}
''')
    java = Path.home() / '.jdks/jdk25.0.4_10/bin'
    argfile = out / 'javac.args'
    argfile.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in src.rglob('*.java')), encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-cp', row['jar'], '-d', str(out / 'classes'), '@' + str(argfile)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(out / 'classes') + os.pathsep + row['jar'], 'audit.Batch15Probe'], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(out / 'classes') + os.pathsep + row['jar'], 'audit.SneakProbe'], check=True)
    (out / 'provenance.json').write_text(json.dumps({'target': args.target, 'staged': args.staged, 'minecraft_jar': row['jar'], 'sha256': {'hacks/ParkourHack.java': hashlib.sha256(file.read_bytes()).hexdigest(), 'hacks/SneakHack.java': hashlib.sha256(sneak_file.read_bytes()).hexdigest()}}, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
