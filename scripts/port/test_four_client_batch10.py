"""Behavior probes for movement ownership, naming restoration and rotation."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / '.planning/2026-11-07-four-client-v151-port'
OUT = PLAN / 'batch10/tests'
SRC = OUT / 'src'


def source(name, body):
    file = SRC / (name.replace('.', '/') + '.java')
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')


def main():
    fixtures = PLAN / 'batch9/tests/src'
    for file in fixtures.rglob('*.java'):
        relative = file.relative_to(fixtures)
        if relative.parts[0] == 'audit' or relative.as_posix().startswith(('net/wurstclient/hacks/', 'net/wurstclient/mixin/')):
            continue
        dest = SRC / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(file, dest)
    provenance = {}
    for name in ['hacks/AutoWalkHack.java', 'hacks/AutoNametagHack.java', 'hacks/RotationHack.java', 'mixin/AutoWalkMovementMixin.java', 'util/RegistryIdFilter.java']:
        original = ROOT / 'fabric/src/main/java/net/wurstclient' / name
        dest = SRC / 'net/wurstclient' / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, dest)
        provenance[name] = hashlib.sha256(original.read_bytes()).hexdigest()
    for name, body in {
        'net.wurstclient.Category': 'public enum Category {MOVEMENT,OTHER}',
        'com.mojang.blaze3d.platform.InputConstants': 'public class InputConstants {public static final int PRESS=1;}',
        'net.wurstclient.events.WorldChangeListener': 'public interface WorldChangeListener {void onWorldChange(net.minecraft.client.multiplayer.ClientLevel w);}',
        'net.wurstclient.events.KeyPressListener': 'public interface KeyPressListener {void onKeyPress(KeyPressEvent e);public record KeyPressEvent(int getKeyCode,int getScanCode,int getAction,int getModifiers){}}',
        'net.wurstclient.events.MouseButtonListener': 'public interface MouseButtonListener {void onMouseButton(int b,int a);}',
        'net.wurstclient.mixinterface.IKeyBinding': 'public interface IKeyBinding {void resetPressedState();public static IKeyBinding get(net.minecraft.client.KeyMapping k){return k;}}',
        'net.wurstclient.mixin.MinecraftAccessor': 'public interface MinecraftAccessor {void setRightClickDelay(int n);}',
        'org.spongepowered.asm.mixin.injection.ModifyVariable': 'public @interface ModifyVariable {String method();At at();boolean argsOnly();int ordinal();}',
        'net.minecraft.util.Mth': 'public class Mth {public static int floor(double v){return (int)Math.floor(v);}public static float wrapDegrees(float v){v%=360;return v>=180?v-360:v< -180?v+360:v;}public static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}}',
        'net.wurstclient.util.ChatUtils': 'public class ChatUtils {public static int errors;public static void error(String s){errors++;}}',
        'net.wurstclient.settings.EnumSetting': 'public class EnumSetting<T> {public T value;public EnumSetting(String n,String d,T[] a,T v){value=v;}public EnumSetting(String n,T[] a,T v){value=v;}public T getSelected(){return value;}}',
        'net.wurstclient.settings.SliderSetting': 'public class SliderSetting {public double value;public SliderSetting(String n,String d,double v,double a,double b,double s,ValueDisplay f){value=v;}public double getValueSq(){return value*value;}public int getValueI(){return (int)value;}public float getValueF(){return (float)value;}public enum ValueDisplay{DECIMAL,INTEGER,DEGREES;public ValueDisplay withSuffix(String s){return this;}}}',
        'net.minecraft.network.chat.Component': 'public record Component(String text) {public String getString(){return text;}}',
        'net.minecraft.world.item.Items': 'public class Items {public static final Item NAME_TAG=new Item();}',
        'net.minecraft.world.entity.Mob': 'public class Mob extends LivingEntity {public Mob(String id){super(id);}}',
        'net.minecraft.world.entity.animal.Animal': 'public class Animal extends net.minecraft.world.entity.Mob {public Animal(String id){super(id);}}',
        'net.minecraft.world.entity.npc.Villager': 'public class Villager extends net.minecraft.world.entity.Mob {public Villager(){super("minecraft:villager");}}',
        'net.minecraft.world.entity.player.Player': 'public class Player extends net.minecraft.world.entity.LivingEntity {public Player(){super("minecraft:player");}}',
    }.items():
        source(name, body)
    source('net.minecraft.client.KeyMapping', '''public class KeyMapping implements net.wurstclient.mixinterface.IKeyBinding {
public final int code;public boolean down,physical;public KeyMapping(int c){code=c;}public void setDown(boolean v){down=v;}public boolean isDown(){return down;}
public void resetPressedState(){down=physical;}public boolean matches(int k,int s){return code==k;}public boolean matchesMouse(int b){return code==1000+b;}}''')
    source('net.wurstclient.WurstClient', '''public class WurstClient {
public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();public static final WurstClient INSTANCE=new WurstClient();
public final Hacks hax=new Hacks();public Hacks getHax(){return hax;}
public static class Hacks {public net.wurstclient.hacks.AutoWalkHack autoWalkHack;}}''')
    source('net.wurstclient.util.BaritoneUtils', '''public class BaritoneUtils {
public static boolean IS_AVAILABLE=true,pathing;public static int walks,stops;public static float yaw;
public static boolean isPathing(){return pathing;}public static void walkDirection(float y,double d){yaw=y;walks++;}public static void stop(){stops++;}}''')
    source('net.wurstclient.util.Rotation', '''public class Rotation {
public static int sends;private final float yaw,pitch;public Rotation(float y,float p){yaw=y;pitch=p;}public static Rotation wrapped(float y,float p){return new Rotation(y,p);}
public void applyToClientPlayer(){var p=net.wurstclient.WurstClient.MC.player;p.setYRot(yaw);p.setXRot(pitch);}public void sendPlayerLookPacket(){sends++;}}''')
    source('net.wurstclient.util.RotationUtils', '''public class RotationUtils {
public static Rotation getNeededRotations(net.minecraft.world.phys.Vec3 v){return new Rotation(42,12);}
public static double getAngleToLookVec(net.minecraft.world.phys.Vec3 v){return v.x;}}''')
    source('net.minecraft.world.entity.LivingEntity', '''public class LivingEntity extends Entity {
public float health=20;public net.minecraft.network.chat.Component name;public LivingEntity(String id){super(id);}
public float getHealth(){return health;}public boolean hasCustomName(){return name!=null;}public net.minecraft.network.chat.Component getCustomName(){return name;}}''')
    source('net.minecraft.world.item.ItemStack', '''public class ItemStack {
public Item item=new Item();public net.minecraft.network.chat.Component name;public Item getItem(){return item;}public boolean is(Item i){return item==i;}
public boolean hasCustomHoverName(){return name!=null;}public net.minecraft.network.chat.Component getHoverName(){return name;}}''')
    source('net.minecraft.world.entity.player.Inventory', '''public class Inventory {
public int selected;public final net.minecraft.world.item.ItemStack[] slots=new net.minecraft.world.item.ItemStack[9];
public Inventory(){for(int i=0;i<9;i++)slots[i]=new net.minecraft.world.item.ItemStack();}public net.minecraft.world.item.ItemStack getItem(int i){return slots[i];}}''')
    source('net.minecraft.client.player.LocalPlayer', '''public class LocalPlayer extends net.minecraft.world.entity.player.Player {
public double x,y=64,z;public float yaw,pitch,yHeadRot,yBodyRot;public boolean shift,passenger,spectator;public int swings;
public final net.minecraft.world.entity.player.Inventory inventory=new net.minecraft.world.entity.player.Inventory();public net.minecraft.world.item.ItemStack off=new net.minecraft.world.item.ItemStack();
public net.minecraft.world.entity.player.Inventory getInventory(){return inventory;}public net.minecraft.world.item.ItemStack getMainHandItem(){return inventory.getItem(inventory.selected);}public net.minecraft.world.item.ItemStack getOffhandItem(){return off;}
public net.minecraft.world.item.ItemStack getItemInHand(net.minecraft.world.InteractionHand h){return h==net.minecraft.world.InteractionHand.MAIN_HAND?getMainHandItem():off;}
public double getX(){return x;}public double getY(){return y;}public double getZ(){return z;}public float getYRot(){return yaw;}public float getXRot(){return pitch;}
public void setYRot(float v){yaw=v;}public void setXRot(float v){pitch=v;}public double distanceToSqr(net.minecraft.world.entity.Entity e){return e.distance*e.distance;}
public void swing(net.minecraft.world.InteractionHand h){swings++;}}''')
    source('net.minecraft.client.multiplayer.ClientLevel', '''public class ClientLevel {
public final java.util.List<net.minecraft.world.entity.Entity> entities=new java.util.ArrayList<>();public Iterable<net.minecraft.world.entity.Entity> entitiesForRendering(){return entities;}
public final Chunks chunks=new Chunks();public Chunks getChunkSource(){return chunks;}
public static class Chunks {public boolean loaded=true;public int x,z;public boolean hasChunk(int a,int b){x=a;z=b;return loaded;}}}''')
    source('net.minecraft.client.multiplayer.MultiPlayerGameMode', '''public class MultiPlayerGameMode {
public net.minecraft.world.entity.Entity target;public int slot,calls,rotations;public boolean fail;public net.minecraft.world.InteractionHand hand;
public net.minecraft.world.InteractionResult interact(net.minecraft.client.player.LocalPlayer p,net.minecraft.world.entity.Entity e,net.minecraft.world.InteractionHand h){target=e;hand=h;slot=p.inventory.selected;calls++;rotations=net.wurstclient.util.Rotation.sends;if(fail)throw new IllegalStateException("test interaction");return net.minecraft.world.InteractionResult.SUCCESS;}}''')
    source('net.minecraft.client.Minecraft', '''public class Minecraft implements net.wurstclient.mixin.MinecraftAccessor {
public net.minecraft.client.player.LocalPlayer player=new net.minecraft.client.player.LocalPlayer();public net.minecraft.client.multiplayer.ClientLevel level=new net.minecraft.client.multiplayer.ClientLevel();
public net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode=new net.minecraft.client.multiplayer.MultiPlayerGameMode();public Object screen;public final Options options=new Options();
public Object getConnection(){return player==null?null:this;}public void setRightClickDelay(int n){}
public static class Options {public final net.minecraft.client.KeyMapping keyUp=new net.minecraft.client.KeyMapping(87),keyDown=new net.minecraft.client.KeyMapping(83),keyLeft=new net.minecraft.client.KeyMapping(65),keyRight=new net.minecraft.client.KeyMapping(1001),keyShift=new net.minecraft.client.KeyMapping(340),keyJump=new net.minecraft.client.KeyMapping(32);}}''')
    source('audit.Batch10Probe', r'''import java.lang.reflect.*;import net.wurstclient.*;import net.wurstclient.hacks.*;import net.wurstclient.settings.*;
import net.minecraft.world.phys.*;import net.minecraft.world.entity.*;import net.wurstclient.util.BaritoneUtils;
public class Batch10Probe {
static final net.minecraft.client.Minecraft mc=WurstClient.MC;
static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
static void bool(Object o,String n,boolean v)throws Exception{((CheckboxSetting)get(o,n)).value=v;}
static void text(Object o,String n,String v)throws Exception{((TextFieldSetting)get(o,n)).value=v;}
@SuppressWarnings({"rawtypes","unchecked"}) static void mode(Object o,String n,String v)throws Exception{EnumSetting s=(EnumSetting)get(o,n);s.value=Enum.valueOf((Class)s.value.getClass(),v);}
static void reset(){mc.player=new net.minecraft.client.player.LocalPlayer();mc.level=new net.minecraft.client.multiplayer.ClientLevel();mc.gameMode=new net.minecraft.client.multiplayer.MultiPlayerGameMode();mc.screen=null;net.wurstclient.util.Rotation.sends=0;BaritoneUtils.walks=BaritoneUtils.stops=0;BaritoneUtils.pathing=false;BaritoneUtils.IS_AVAILABLE=true;}
static void walk()throws Exception{
reset();AutoWalkHack h=new AutoWalkHack();WurstClient.INSTANCE.hax.autoWalkHack=h;h.setEnabled(true);h.onUpdate();check(mc.options.keyUp.down,"forward default");
mode(h,"direction","LEFT");h.onUpdate();check(!mc.options.keyUp.down&&mc.options.keyLeft.down,"direction change releases old key");mc.options.keyLeft.physical=true;h.setEnabled(false);check(mc.options.keyLeft.down,"disable preserves physical input");mc.options.keyLeft.physical=false;
h.setEnabled(true);h.onUpdate();bool(h,"disableOnInput",true);h.onKeyPress(new net.wurstclient.events.KeyPressListener.KeyPressEvent(32,0,1,0));check(!h.isEnabled(),"manual jump stops");
h.setEnabled(true);h.onUpdate();h.onMouseButton(1,1);check(!h.isEnabled(),"mouse-bound movement stops");
h.setEnabled(true);h.onUpdate();bool(h,"disableOnY",true);mc.player.y++;h.onUpdate();check(!h.isEnabled(),"Y movement stops");
bool(h,"disableOnY",false);h.setEnabled(true);h.onUpdate();mc.player.x=-.1;mc.player.z=-16.1;mc.level.chunks.loaded=false;
Vec3 v=h.limitMovement(new Vec3(0,.3,0));check(v.y==.3&&v.x==0&&mc.level.chunks.x== -1&&mc.level.chunks.z== -2,"negative chunk floor and preserve vertical");
var mixin=new net.wurstclient.mixin.AutoWalkMovementMixin(){};var callback=mixin.getClass().getSuperclass().getDeclaredMethod("wurstWaitForChunks",Vec3.class);callback.setAccessible(true);v=(Vec3)callback.invoke(mixin,new Vec3(.2,.4,.1));check(v.x==0&&v.y==.4&&v.z==0,"actual movement callback");
mode(h,"mode","SMART");mc.player.yaw=73;h.onUpdate();check(BaritoneUtils.walks==1&&BaritoneUtils.yaw==73&&!mc.options.keyLeft.down,"smart starts and releases key");h.onUpdate();check(BaritoneUtils.walks==1,"path goal throttled");h.setEnabled(false);check(BaritoneUtils.stops==1,"only owned path stopped");
BaritoneUtils.IS_AVAILABLE=false;h.setEnabled(true);h.onUpdate();check(!h.isEnabled(),"missing Baritone disables smart");}
static AutoNametagHack setupName(){reset();var h=new AutoNametagHack();mc.player.inventory.selected=0;var stack=mc.player.inventory.slots[4];stack.item=net.minecraft.world.item.Items.NAME_TAG;stack.name=new net.minecraft.network.chat.Component("Test");h.setEnabled(true);return h;}
static void nametag()throws Exception{
var h=setupName();Mob cow=new Mob("minecraft:cow");mc.level.entities.add(cow);h.onUpdate();check(mc.gameMode.slot==4&&mc.player.inventory.selected==0,"uses tag then restores slot");check(mc.gameMode.rotations>0,"look packet before interaction");
h=setupName();mc.gameMode.fail=true;mc.level.entities.add(cow);try{h.onUpdate();throw new AssertionError("missing exception");}catch(IllegalStateException e){}check(mc.player.inventory.selected==0,"failure restores slot");
h=setupName();mc.player.inventory.slots[4].name=null;h.onUpdate();check(!h.isEnabled()&&mc.player.inventory.selected==0,"unnamed tag rejected without selecting it");
h=setupName();Mob sheep=new Mob("minecraft:sheep");sheep.distance=3;sheep.health=2;mc.level.entities.add(cow);mc.level.entities.add(sheep);text(h,"entities","sheep");h.onUpdate();check(mc.gameMode.target==sheep,"exact entity filter");
h=setupName();mc.level.entities.add(cow);mc.level.entities.add(sheep);mode(h,"priority","FARTHEST");h.onUpdate();check(mc.gameMode.target==sheep,"farthest priority");
h=setupName();mc.level.entities.add(cow);mc.level.entities.add(sheep);mode(h,"priority","LOWEST_HEALTH");bool(h,"perEntityCooldown",true);bool(h,"rotate",false);h.onUpdate();check(mc.gameMode.target==sheep&&mc.gameMode.rotations==0,"health priority and optional rotation");h.onUpdate();check(mc.gameMode.target==cow&&mc.gameMode.calls==2,"per-entity cooldown permits next target");h.onUpdate();check(mc.gameMode.calls==2,"cooling entities skipped");h.onWorldChange(mc.level);h.onUpdate();check(mc.gameMode.calls==3,"world change clears cooldown");
h=setupName();mc.player.off=mc.player.inventory.slots[4];mc.player.inventory.slots[4]=new net.minecraft.world.item.ItemStack();mc.level.entities.add(cow);h.onUpdate();check(mc.gameMode.hand==net.minecraft.world.InteractionHand.OFF_HAND&&mc.player.inventory.selected==0,"offhand naming");}
static void rotation()throws Exception{
reset();RotationHack h=new RotationHack();((SliderSetting)get(h,"yawAngle")).value=90;((SliderSetting)get(h,"pitchAngle")).value=30;h.setEnabled(true);check(mc.player.yaw==90&&mc.player.yHeadRot==90&&mc.player.yBodyRot==90&&mc.player.pitch==30,"immediate synchronized rotation");
bool(h,"silent",true);((SliderSetting)get(h,"yawAngle")).value=180;h.onUpdate();check(mc.player.yaw==90&&mc.player.yHeadRot==90,"silent retains visible body");bool(h,"silent",false);mode(h,"yawMode","OFF");mc.player.yHeadRot=42;h.onUpdate();check(mc.player.yHeadRot==42,"yaw off retains head");}
public static void main(String[] a)throws Exception{walk();nametag();rotation();System.out.println("PASS: directional movement, input/height/chunk guards, naming slot recovery and rotation state");}}
''')
    java = Path('C:/Users/ui863/.jdks/jdk25.0.4_10/bin')
    args = OUT / 'javac.args'
    args.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in SRC.rglob('*.java')), encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(OUT / 'classes'), '@' + str(args)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(OUT / 'classes'), 'audit.Batch10Probe'], check=True)
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
