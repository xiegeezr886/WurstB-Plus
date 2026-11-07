"""Execute unchanged Meteor merges with controlled host state and real helper code."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
PLAN=ROOT/'.planning/2026-11-07-four-client-v151-port'
OUT=PLAN/'batch3/tests'
SRC=OUT/'src'

def source(name,body):
    p=SRC/(name.replace('.','/')+'.java');p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text('package '+name.rpartition('.')[0]+';\n'+body,encoding='utf-8')

def main():
    provenance={}
    for name in ['hacks/AntiHungerHack.java','hacks/NoLevitationHack.java','hacks/FastBreakHack.java','hacks/AutoRespawnHack.java','util/RegistryIdFilter.java','util/SoundIdFilter.java']:
        p=ROOT/'fabric/src/main/java/net/wurstclient'/name;dest=SRC/'net/wurstclient'/name
        dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest)
        provenance[name]=hashlib.sha256(p.read_bytes()).hexdigest()
    # Reuse the previously verified host fixtures for common settings/events.
    prior=PLAN/'batch2/autoleave-tests/src'
    for name in ['net.wurstclient.SearchTags','net.wurstclient.settings.CheckboxSetting','net.wurstclient.settings.SliderSetting','net.wurstclient.events.WorldChangeListener','net.minecraft.client.multiplayer.ClientLevel']:
        rel=Path(name.replace('.','/')+'.java');dest=SRC/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(prior/rel,dest)
    p=SRC/'net/wurstclient/settings/SliderSetting.java'
    p.write_text(p.read_text(encoding='utf-8').replace('public int getValueI()', 'public String getValueString(){return ""+value;}public int getValueI()'),encoding='utf-8')
    source('net.wurstclient.Category','public enum Category {MOVEMENT,BLOCKS,COMBAT}')
    source('net.wurstclient.hack.DontSaveState','public @interface DontSaveState {}')
    source('net.wurstclient.settings.TextFieldSetting','''public class TextFieldSetting {public String value;
public TextFieldSetting(String n,String d,String v){value=v;}public String getValue(){return value;}}''')
    source('net.wurstclient.events.PreMotionListener','public interface PreMotionListener {void onPreMotion();}')
    source('net.wurstclient.events.DeathListener','public interface DeathListener {void onDeath();}')
    source('net.wurstclient.events.LeftClickListener','public interface LeftClickListener {void onLeftClick(LeftClickEvent e);public static class LeftClickEvent {}}')
    source('net.wurstclient.events.PacketOutputListener','''public interface PacketOutputListener {void onSentPacket(PacketOutputEvent e);
public static class PacketOutputEvent {private Object packet;public boolean cancelled;
public PacketOutputEvent(Object p){packet=p;}public Object getPacket(){return packet;}public void setPacket(Object p){packet=p;}public void cancel(){cancelled=true;}}}''')
    source('net.wurstclient.events.BlockBreakingProgressListener','''public interface BlockBreakingProgressListener {void onBlockBreakingProgress(BlockBreakingProgressEvent e);
public static class BlockBreakingProgressEvent {public net.minecraft.core.BlockPos getBlockPos(){return new net.minecraft.core.BlockPos(0,0,0);}
public net.minecraft.core.Direction getDirection(){return net.minecraft.core.Direction.UP;}}}''')
    source('net.minecraft.core.BlockPos','public record BlockPos(int x,int y,int z) {}')
    source('net.minecraft.core.Direction','public enum Direction {UP}')
    source('net.minecraft.world.effect.MobEffect','public record MobEffect(String id) {}')
    source('net.minecraft.core.registries.BuiltInRegistries','''public class BuiltInRegistries {
public static final Effects MOB_EFFECT=new Effects();public static class Effects {public String getKey(net.minecraft.world.effect.MobEffect e){return e.id();}}}''')
    source('net.minecraft.network.protocol.game.ServerboundMovePlayerPacket','public record ServerboundMovePlayerPacket(boolean ground,int metadata) {}')
    source('net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket','''public record ServerboundPlayerCommandPacket(Action action) {
public Action getAction(){return action;}public enum Action {START_SPRINTING,STOP_SPRINTING,PRESS_SHIFT_KEY}}''')
    source('net.minecraft.network.protocol.game.ServerboundPlayerActionPacket','public class ServerboundPlayerActionPacket {public enum Action {STOP_DESTROY_BLOCK}}')
    source('net.wurstclient.mixin.MultiPlayerGameModeAccessor','public interface MultiPlayerGameModeAccessor {boolean getIsDestroying();float getDestroyProgress();}')
    source('net.minecraft.client.multiplayer.ClientLevel','''public class ClientLevel {
public String id="minecraft:the_nether";public Dimension dimension(){return new Dimension(id);}public record Dimension(String id){public String location(){return id;}}}''')
    source('net.minecraft.client.Minecraft','''public class Minecraft {
public Player player=new Player();public GameMode gameMode=new GameMode();public net.minecraft.client.multiplayer.ClientLevel level=new net.minecraft.client.multiplayer.ClientLevel();public Object screen=new Object();
public void setScreen(Object s){screen=s;}public static class Player {public boolean ground=true,passenger,water,underwater;public float fallDistance;public int respawns;
public boolean onGround(){return ground;}public boolean isPassenger(){return passenger;}public boolean isInWater(){return water;}public boolean isUnderWater(){return underwater;}
public net.minecraft.core.BlockPos blockPosition(){return new net.minecraft.core.BlockPos(5,70,-9);}public void respawn(){respawns++;}}
public static class GameMode implements net.wurstclient.mixin.MultiPlayerGameModeAccessor {public boolean destroying;public float progress;
public boolean getIsDestroying(){return destroying;}public float getDestroyProgress(){return progress;}}}''')
    source('net.wurstclient.waypoints.Waypoint','public record Waypoint(String name,String dimension,net.minecraft.core.BlockPos pos,int color) {}')
    source('net.wurstclient.WurstClient','''public class WurstClient {public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();
public static final WurstClient INSTANCE=new WurstClient();public final Waypoints waypoints=new Waypoints();public boolean isEnabled(){return true;}
public Hax getHax(){return new Hax();}public Waypoints getWaypointsManager(){return waypoints;}
public static class Hax {public final net.wurstclient.hack.Hack noFallHack=new net.wurstclient.hack.Hack("NoFall");}
public static class Waypoints {public net.wurstclient.waypoints.Waypoint last;public void add(net.wurstclient.waypoints.Waypoint p){last=p;}}}''')
    source('net.wurstclient.hack.Hack','''public class Hack {
public static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;protected static final net.wurstclient.WurstClient WURST=net.wurstclient.WurstClient.INSTANCE;
public static final Events EVENTS=new Events();public static final Interaction IMC=new Interaction();private boolean enabled;private final String name;
public Hack(String n){name=n;}public String getName(){return name;}public String getRenderName(){return name;}protected void setCategory(Object c){}protected void addSetting(Object s){}
protected void onEnable(){}protected void onDisable(){}public boolean isEnabled(){return enabled;}
public void setEnabled(boolean v){if(v==enabled)return;enabled=v;if(v)onEnable();else onDisable();}
public static class Events {public final java.util.Map<Class<?>,java.util.Set<Object>> listeners=new java.util.HashMap<>();
public void add(Class<?> c,Object o){listeners.computeIfAbsent(c,k->new java.util.HashSet<>()).add(o);}public void remove(Class<?> c,Object o){listeners.get(c).remove(o);}}
public static class Interaction {public int packets;public Interaction getInteractionManager(){return this;}public void sendPlayerActionC2SPacket(Object a,Object b,Object c){packets++;}}}''')
    source('net.wurstclient.util.PacketUtils','''public class PacketUtils {public static Object modifyOnGround(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket p,boolean b){
return new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket(b,p.metadata());}}''')
    source('net.wurstclient.util.BlockUtils','public class BlockUtils {public static boolean unbreakable;public static boolean isUnbreakable(Object p){return unbreakable;}}')
    source('audit.MeteorMergeProbe','''import java.lang.reflect.*;import net.wurstclient.*;import net.wurstclient.hacks.*;import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;import net.wurstclient.events.*;import net.wurstclient.events.PacketOutputListener.PacketOutputEvent;
import net.minecraft.network.protocol.game.*;import net.minecraft.world.effect.MobEffect;
public class MeteorMergeProbe {
static final net.minecraft.client.Minecraft mc=WurstClient.MC;
static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
static Object field(Object x,String n)throws Exception{Field f=x.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(x);}
static void toggle(Object a,String n,boolean v)throws Exception{((CheckboxSetting)field(a,n)).value=v;}
static PacketOutputEvent move(AntiHungerHack a){var e=new PacketOutputEvent(new ServerboundMovePlayerPacket(true,19));a.onSentPacket(e);return e;}
static PacketOutputEvent sprint(AntiHungerHack a,ServerboundPlayerCommandPacket.Action action){var e=new PacketOutputEvent(new ServerboundPlayerCommandPacket(action));a.onSentPacket(e);return e;}
public static void main(String[] args)throws Exception {
AntiHungerHack a=new AntiHungerHack();a.setEnabled(true);
check(sprint(a,ServerboundPlayerCommandPacket.Action.START_SPRINTING).cancelled,"start sprint filtered");check(!sprint(a,ServerboundPlayerCommandPacket.Action.STOP_SPRINTING).cancelled,"stop sprint preserved");
var changed=(ServerboundMovePlayerPacket)move(a).getPacket();check(!changed.ground()&&changed.metadata()==19,"ground modified without losing metadata");
mc.player.ground=false;a.onPreMotion();mc.player.ground=true;a.onPreMotion();sprint(a,ServerboundPlayerCommandPacket.Action.START_SPRINTING);
check(((ServerboundMovePlayerPacket)move(a).getPacket()).ground(),"first landing preserved across unrelated packets");check(!((ServerboundMovePlayerPacket)move(a).getPacket()).ground(),"next ground may spoof");
mc.player.water=true;check(!sprint(a,ServerboundPlayerCommandPacket.Action.START_SPRINTING).cancelled&&((ServerboundMovePlayerPacket)move(a).getPacket()).ground(),"water bypass");mc.player.water=false;
mc.player.passenger=true;check(!sprint(a,ServerboundPlayerCommandPacket.Action.START_SPRINTING).cancelled,"passenger bypass");mc.player.passenger=false;
mc.gameMode.destroying=true;check(((ServerboundMovePlayerPacket)move(a).getPacket()).ground(),"mining bypass");mc.gameMode.destroying=false;
mc.player.fallDistance=.1f;check(((ServerboundMovePlayerPacket)move(a).getPacket()).ground(),"falling bypass");mc.player.fallDistance=0;
toggle(a,"onGround",false);check(((ServerboundMovePlayerPacket)move(a).getPacket()).ground(),"ground setting");toggle(a,"sprint",false);check(!sprint(a,ServerboundPlayerCommandPacket.Action.START_SPRINTING).cancelled,"sprint setting");
a.setEnabled(false);for(var listeners:Hack.EVENTS.listeners.values())check(!listeners.contains(a),"anti hunger unregisters");
NoLevitationHack n=new NoLevitationHack();MobEffect lev=new MobEffect("minecraft:levitation"),jump=new MobEffect("minecraft:jump_boost");check(!n.shouldBlock(lev),"disabled effects");n.setEnabled(true);check(n.shouldBlock(lev)&&!n.shouldBlock(jump),"native default");
((TextFieldSetting)field(n,"blockedEffects")).value="jump_boost,custom:weight";check(!n.shouldBlock(lev)&&n.shouldBlock(jump)&&n.shouldBlock(new MobEffect("custom:weight")),"effect list editing and namespaces");
((TextFieldSetting)field(n,"blockedEffects")).value="";check(!n.shouldBlock(jump)&&!n.shouldBlock(null),"empty list and null");
FastBreakHack f=new FastBreakHack();check(f.getBreakCooldown(5)==5&&!f.shouldPreventInstantBreak(),"disabled break cooldown");f.setEnabled(true);check(f.getBreakCooldown(5)==0,"default delay removal");
((SliderSetting)field(f,"cooldown")).value=8;check(f.getBreakCooldown(5)==8,"custom delay");toggle(f,"noInstaBreak",true);f.onLeftClick(new LeftClickListener.LeftClickEvent());check(f.getBreakCooldown(5)==5&&f.getBreakCooldown(5)==8,"one initial click grace");check(f.shouldPreventInstantBreak(),"instant prevention active");
f.onLeftClick(new LeftClickListener.LeftClickEvent());f.setEnabled(false);f.setEnabled(true);check(f.getBreakCooldown(5)==8,"stale click cleared");
toggle(f,"legitMode",true);f.onBlockBreakingProgress(new BlockBreakingProgressListener.BlockBreakingProgressEvent());check(Hack.IMC.packets==0,"legit behavior retained");toggle(f,"legitMode",false);f.onBlockBreakingProgress(new BlockBreakingProgressListener.BlockBreakingProgressEvent());check(Hack.IMC.packets==2,"acceleration retained");
AutoRespawnHack r=new AutoRespawnHack();r.onDeath();check(mc.player.respawns==1&&mc.screen==null,"respawn and screen");var wp=WurstClient.INSTANCE.waypoints.last;check(wp!=null&&wp.dimension().equals("minecraft:the_nether")&&wp.pos().z()==-9,"death waypoint before respawn");
toggle(r,"deathWaypoint",false);WurstClient.INSTANCE.waypoints.last=null;r.onDeath();check(WurstClient.INSTANCE.waypoints.last==null,"waypoint opt out");mc.player=null;r.onDeath();
System.out.println("PASS Meteor merges: sprint/landing/liquid/mining guards, effect ID list, cooldown/instant break lifecycle, existing acceleration, death waypoint and opt out");
}}
''')
    classes=OUT/'classes';classes.mkdir(parents=True,exist_ok=True)
    args=OUT/'sources.txt';args.write_text('\n'.join('"'+p.as_posix()+'"' for p in SRC.rglob('*.java')),encoding='utf-8')
    results=[]
    for cmd in [['javac','-encoding','UTF-8','-d',str(classes),'@'+str(args)],['java','-cp',str(classes),'audit.MeteorMergeProbe']]:
        run=subprocess.run(cmd,capture_output=True,timeout=60)
        result=f'{cmd[0]} exit={run.returncode}\n'+run.stdout.decode('utf-8',errors='replace')+run.stderr.decode('utf-8',errors='replace')
        results.append(result);(OUT/'result.txt').write_text('\n'.join(results),encoding='utf-8');print(result)
        assert run.returncode==0
    (OUT/'provenance.json').write_text(json.dumps(provenance,indent=2),encoding='utf-8')

if __name__=='__main__':main()
