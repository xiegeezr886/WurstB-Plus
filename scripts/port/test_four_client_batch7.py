"""Run unchanged message/XP/effect code against deterministic host fixtures."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / '.planning/2026-11-07-four-client-v151-port'
OUT = PLAN / 'batch7/tests'
SRC = OUT / 'src'


def source(name, body):
    path = SRC / (name.replace('.', '/') + '.java')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')


def main():
    fixture_root = PLAN / 'batch6/tests/src'
    for file in fixture_root.rglob('*.java'):
        rel = file.relative_to(fixture_root)
        if rel.parts[0] == 'audit' or rel.as_posix().startswith(('net/wurstclient/hacks/', 'net/wurstclient/mixin/')):
            continue
        dest = SRC / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(file, dest)
    provenance = {}
    for name in ['hacks/' + n + 'Hack.java' for n in ['MessageAura', 'EXPThrower', 'PotionSaver']] + ['mixin/StatusEffectInstanceMixin.java', 'util/RegistryIdFilter.java']:
        original = ROOT / 'fabric/src/main/java/net/wurstclient' / name
        dest = SRC / 'net/wurstclient' / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, dest)
        provenance[name] = hashlib.sha256(original.read_bytes()).hexdigest()
    source('net.wurstclient.Category', 'public enum Category {CHAT,ITEMS,OTHER}')
    source('net.wurstclient.settings.SliderSetting', '''public class SliderSetting {public double value;
public SliderSetting(String n,String d,double v,double a,double b,double step,ValueDisplay vd){value=v;}
public int getValueI(){return (int)value;}public enum ValueDisplay {INTEGER;public ValueDisplay withSuffix(String s){return this;}}}''')
    source('net.wurstclient.settings.TextFieldSetting', '''public class TextFieldSetting {public String value;
public TextFieldSetting(String n,String d,String v){value=v;}public String getValue(){return value;}}''')
    source('net.wurstclient.events.WorldChangeListener', 'public interface WorldChangeListener {void onWorldChange(net.minecraft.client.multiplayer.ClientLevel world);}')
    source('net.wurstclient.events.PacketOutputListener', '''public interface PacketOutputListener {void onSentPacket(PacketOutputEvent event);
class PacketOutputEvent {public Object packet;public boolean cancelled;public PacketOutputEvent(Object p){packet=p;}
public Object getPacket(){return packet;}public void cancel(){cancelled=true;}}}''')
    source('net.minecraft.network.protocol.game.ServerboundMovePlayerPacket', 'public class ServerboundMovePlayerPacket {}')
    source('net.minecraft.world.entity.Entity', '''public class Entity {
public final java.util.UUID id=java.util.UUID.randomUUID();public String name="Player";
public java.util.UUID getUUID(){return id;}public String getScoreboardName(){return name;}}''')
    source('net.minecraft.world.entity.player.Player', 'public class Player extends net.minecraft.world.entity.Entity {}')
    source('net.minecraft.world.item.Item', 'public class Item {}')
    source('net.minecraft.world.item.Items', 'public class Items {public static final Item EXPERIENCE_BOTTLE=new Item();}')
    source('net.minecraft.world.item.ItemStack', '''public class ItemStack {public Item item;public ItemStack(Item i){item=i;}public boolean is(Item i){return item==i;}}''')
    source('net.minecraft.world.entity.player.Inventory', '''public class Inventory {
public int selected;public net.minecraft.world.item.ItemStack[] items=new net.minecraft.world.item.ItemStack[9];
public Inventory(){java.util.Arrays.setAll(items,i->new net.minecraft.world.item.ItemStack(null));}
public net.minecraft.world.item.ItemStack getItem(int i){return items[i];}}''')
    source('net.minecraft.world.effect.MobEffect', 'public record MobEffect(String id) {}')
    source('net.minecraft.world.effect.MobEffectInstance', '''public class MobEffectInstance extends net.wurstclient.mixin.StatusEffectInstanceMixin {
public final MobEffect effect;public MobEffectInstance(MobEffect e){effect=e;}public MobEffect getEffect(){return effect;}
public int compareTo(MobEffectInstance o){return effect.id().compareTo(o.effect.id());}
public boolean equals(Object o){return o instanceof MobEffectInstance m&&effect.equals(m.effect);}
public int hashCode(){return effect.hashCode();}}''')
    source('net.minecraft.core.registries.BuiltInRegistries', '''public class BuiltInRegistries {public static final Registry MOB_EFFECT=new Registry();
public static class Registry {public String getKey(net.minecraft.world.effect.MobEffect e){return e.id();}}}''')
    source('net.minecraft.client.player.LocalPlayer', '''import net.minecraft.world.item.*;import net.minecraft.world.effect.*;
public class LocalPlayer extends net.minecraft.world.entity.player.Player {
public boolean ground=true;public float zza,xxa,yaw=35,pitch=12;public net.minecraft.world.phys.Vec3 velocity=new net.minecraft.world.phys.Vec3(0,0,0);
public final java.util.Map<MobEffect,MobEffectInstance> effects=new java.util.HashMap<>();
public final net.minecraft.world.entity.player.Inventory inventory=new net.minecraft.world.entity.player.Inventory();public ItemStack offhand=new ItemStack(null);
public boolean onGround(){return ground;}public net.minecraft.world.phys.Vec3 getDeltaMovement(){return velocity;}
public java.util.Map<MobEffect,MobEffectInstance> getActiveEffectsMap(){return effects;}
public net.minecraft.world.entity.player.Inventory getInventory(){return inventory;}
public ItemStack getMainHandItem(){return inventory.getItem(inventory.selected);}public ItemStack getOffhandItem(){return offhand;}
public float getYRot(){return yaw;}public float getXRot(){return pitch;}public void setYRot(float v){yaw=v;}public void setXRot(float v){pitch=v;}}''')
    source('net.minecraft.client.multiplayer.ClientLevel', '''public class ClientLevel {
public final java.util.List<net.minecraft.world.entity.player.Player> players=new java.util.ArrayList<>();
public java.util.List<net.minecraft.world.entity.player.Player> players(){return players;}}''')
    source('net.minecraft.client.multiplayer.ClientPacketListener', '''public class ClientPacketListener {
public final java.util.List<String> commands=new java.util.ArrayList<>(),chat=new java.util.ArrayList<>();
public void sendCommand(String s){commands.add(s);}public void sendChat(String s){chat.add(s);}}''')
    source('net.minecraft.world.InteractionHand', 'public enum InteractionHand {MAIN_HAND,OFF_HAND}')
    source('net.minecraft.client.Minecraft', '''public class Minecraft {
public net.minecraft.client.player.LocalPlayer player;public net.minecraft.client.multiplayer.ClientLevel level;public Object screen;
public net.minecraft.client.multiplayer.ClientPacketListener connection;public GameMode gameMode=new GameMode();
public net.minecraft.client.multiplayer.ClientPacketListener getConnection(){return connection;}
public static class GameMode {public boolean fail;public int uses,slot;public float pitch;public net.minecraft.world.InteractionHand hand;
public void useItem(net.minecraft.client.player.LocalPlayer p,net.minecraft.world.InteractionHand h){uses++;slot=p.getInventory().selected;pitch=p.getXRot();hand=h;if(fail)throw new IllegalStateException("synthetic use failure");}}}''')
    source('net.wurstclient.WurstClient', '''public class WurstClient {
public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();public static final WurstClient INSTANCE=new WurstClient();
public final java.util.Set<String> friends=new java.util.HashSet<>();public java.util.Set<String> getFriends(){return friends;}
public final Hacks hacks=new Hacks();public Hacks getHax(){return hacks;}public static class Hacks {public net.wurstclient.hacks.PotionSaverHack potionSaverHack;}}''')
    source('net.wurstclient.util.InventoryUtils', '''public class InventoryUtils {
public static int indexOf(net.minecraft.world.item.Item item,int n){var p=net.wurstclient.WurstClient.MC.player;for(int i=0;i<n;i++)if(p.inventory.items[i].is(item))return i;return -1;}
public static void selectItem(int slot){net.wurstclient.WurstClient.MC.player.inventory.selected=slot;}}''')
    source('net.wurstclient.util.Rotation', '''public record Rotation(float yaw,float pitch) {
public static final java.util.List<Float> packets=new java.util.ArrayList<>();
public void applyToClientPlayer(){var p=net.wurstclient.WurstClient.MC.player;p.setYRot(yaw);p.setXRot(pitch);}
public void sendPlayerLookPacket(){packets.add(pitch);}}''')
    source('org.spongepowered.asm.mixin.Shadow', 'public @interface Shadow {}')
    source('audit.UtilityProbe', '''import java.lang.reflect.*;import net.wurstclient.*;import net.wurstclient.hacks.*;import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;import net.minecraft.client.player.*;import net.minecraft.client.multiplayer.*;
import net.minecraft.world.entity.player.*;import net.minecraft.world.effect.*;import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;import net.wurstclient.events.PacketOutputListener.PacketOutputEvent;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;import org.spongepowered.asm.mixin.injection.callback.*;
public class UtilityProbe {
static final net.minecraft.client.Minecraft mc=WurstClient.MC;
static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
static void flag(Object o,String n,boolean v)throws Exception{((CheckboxSetting)field(o,n)).value=v;}
static void mode(Object o,String n,String v)throws Exception{EnumSetting e=(EnumSetting)field(o,n);e.value=Enum.valueOf(e.value.getClass(),v);}
static void reset(){mc.player=new LocalPlayer();mc.level=new ClientLevel();mc.level.players.add(mc.player);mc.connection=new ClientPacketListener();mc.screen=null;mc.gameMode=new net.minecraft.client.Minecraft.GameMode();WurstClient.INSTANCE.friends.clear();}
static Player player(String name){var p=new Player();p.name=name;return p;}
public static void main(String[] args)throws Exception {
reset();var aura=new MessageAuraHack();aura.setEnabled(true);aura.onUpdate();check(mc.connection.chat.size()==1,"timer default retained");aura.setEnabled(false);
reset();aura=new MessageAuraHack();mode(aura,"mode","NEARBY");((SliderSetting)field(aura,"delay")).value=0;var present=player("Present");mc.level.players.add(present);aura.setEnabled(true);aura.onUpdate();check(mc.connection.commands.isEmpty(),"existing players not messaged on enable");
var newcomer=player("Newcomer");mc.level.players.add(newcomer);aura.onUpdate();check(mc.connection.commands.size()==1&&mc.connection.commands.get(0).startsWith("msg Newcomer "),"new player private message");aura.onUpdate();check(mc.connection.commands.size()==1,"visible player deduplication");
mc.level.players.remove(newcomer);aura.onUpdate();mc.level.players.add(newcomer);aura.onUpdate();check(mc.connection.commands.size()==2,"re-entry");
flag(aura,"ignoreFriends",true);WurstClient.INSTANCE.friends.add("Friend");mc.level.players.add(player("Friend"));aura.onUpdate();check(mc.connection.commands.size()==2,"friend filter");
((SliderSetting)field(aura,"delay")).value=2;mc.level.players.add(player("One"));aura.onUpdate();mc.level.players.add(player("Leaves"));aura.onUpdate();mc.level.players.removeIf(p->p.name.equals("Leaves"));aura.onUpdate();aura.onUpdate();check(mc.connection.commands.size()==3,"departed queued recipient removed");
((TextFieldSetting)field(aura,"nearbyMessage")).value="x".repeat(400);mc.level.players.add(player("Long"));aura.onUpdate();check(mc.connection.commands.get(3).length()==256,"message length cap");
aura.onWorldChange(null);check(!aura.isEnabled()&&Hack.EVENTS.registered.isEmpty(),"disconnect disables and cleans listeners");
reset();aura=new MessageAuraHack();flag(aura,"disableOnDisconnect",false);mode(aura,"mode","NEARBY");aura.setEnabled(true);var newWorld=new ClientLevel();mc.level=newWorld;aura.onWorldChange(newWorld);mc.level.players.add(player("OtherWorld"));aura.onUpdate();check(mc.connection.commands.size()==1,"new world tracks new arrivals");aura.setEnabled(false);
reset();var exp=new EXPThrowerHack();mc.player.inventory.items[5]=new ItemStack(Items.EXPERIENCE_BOTTLE);exp.onUpdate();check(mc.gameMode.slot==5&&mc.gameMode.pitch==90,"hotbar selection and downward use");check(mc.player.inventory.selected==0&&mc.player.pitch==12&&mc.player.yaw==35,"slot and rotation restore");
mc.player.offhand=new ItemStack(Items.EXPERIENCE_BOTTLE);exp.onUpdate();check(mc.gameMode.hand==InteractionHand.OFF_HAND&&mc.gameMode.slot==0,"offhand without swap");
mc.player.offhand=new ItemStack(null);mc.gameMode.fail=true;try{exp.onUpdate();throw new AssertionError("fault expected");}catch(IllegalStateException expected){}check(mc.player.inventory.selected==0&&mc.player.pitch==12,"throw failure restores state");mc.gameMode.fail=false;
flag(exp,"lookDown",false);flag(exp,"restoreSlot",false);exp.onUpdate();check(mc.player.inventory.selected==5&&mc.gameMode.pitch==12,"native selection options");mc.screen=new Object();int uses=mc.gameMode.uses;exp.onUpdate();check(mc.gameMode.uses==uses,"menu guard");
reset();var potion=new PotionSaverHack();WurstClient.INSTANCE.hacks.potionSaverHack=potion;potion.setEnabled(true);
var speed=new MobEffect("minecraft:speed");var poison=new MobEffect("minecraft:poison");var local=new MobEffectInstance(speed);var other=new MobEffectInstance(speed);mc.player.effects.put(speed,local);check(local.equals(other),"fixture exercises equality hazard");
check(potion.shouldFreeze(local)&&!potion.shouldFreeze(other),"local identity isolation");
var hook=net.wurstclient.mixin.StatusEffectInstanceMixin.class.getDeclaredMethod("onUpdateDuration",CallbackInfoReturnable.class);hook.setAccessible(true);var duration=net.wurstclient.mixin.StatusEffectInstanceMixin.class.getDeclaredField("duration");duration.setAccessible(true);duration.setInt(local,120);
var cir=new CallbackInfoReturnable<Integer>(-1);hook.invoke(local,cir);check(cir.value==120,"actual duration callback freezes local effect");cir=new CallbackInfoReturnable<Integer>(-1);hook.invoke(other,cir);check(cir.value==-1,"actual callback leaves other entities alone");
flag(potion,"allEffects",false);var bad=new MobEffectInstance(poison);mc.player.effects.put(poison,bad);check(potion.shouldFreeze(local)&&!potion.shouldFreeze(bad),"effect selection");
mc.player.zza=1;check(!potion.shouldFreeze(local),"stationary setting");flag(potion,"onlyWhenStationary",false);check(potion.shouldFreeze(local),"client-only moving timer option");var packet=new PacketOutputEvent(new ServerboundMovePlayerPacket());potion.onSentPacket(packet);check(!packet.cancelled,"moving packets preserved");
mc.player.zza=0;packet=new PacketOutputEvent(new ServerboundMovePlayerPacket());potion.onSentPacket(packet);check(packet.cancelled,"idle legacy suppression");mc.player.ground=false;packet=new PacketOutputEvent(new ServerboundMovePlayerPacket());potion.onSentPacket(packet);check(!packet.cancelled,"falling packets preserved");
mc.player.ground=true;flag(potion,"suppressMovement",false);packet=new PacketOutputEvent(new ServerboundMovePlayerPacket());potion.onSentPacket(packet);check(!packet.cancelled,"client-only mode");potion.setEnabled(false);check(!potion.shouldFreeze(local)&&Hack.EVENTS.registered.isEmpty(),"disabled effect passthrough");
System.out.println("PASS utilities: timer compatibility, arrival/re-entry/friends/world/message limits, offhand/downward XP use, exception restoration, selected local-only effects and actual callback, moving/falling packet preservation");
}}''')
    java = Path('C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin')
    classes = OUT / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    argfile = OUT / 'sources.txt'
    argfile.write_text('\n'.join('"' + str(p.resolve()).replace('\\', '/') + '"' for p in SRC.rglob('*.java')), encoding='utf-8')
    result = subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(classes), '@' + str(argfile)], capture_output=True)
    output = 'javac exit=' + str(result.returncode) + '\n' + result.stdout.decode('utf-8', errors='replace') + result.stderr.decode('utf-8', errors='replace')
    code = result.returncode
    if not code:
        result = subprocess.run([str(java / 'java.exe'), '-cp', str(classes), 'audit.UtilityProbe'], capture_output=True)
        code = result.returncode
        output += '\njava exit=' + str(code) + '\n' + result.stdout.decode('utf-8', errors='replace') + result.stderr.decode('utf-8', errors='replace')
    (OUT / 'result.txt').write_text(output, encoding='utf-8')
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2), encoding='utf-8')
    print(output)
    raise SystemExit(code)


if __name__ == '__main__':
    main()
