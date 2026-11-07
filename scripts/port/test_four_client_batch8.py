"""Validate recovery ownership, dynamic timer override and item interactions."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / '.planning/2026-11-07-four-client-v151-port'
OUT = PLAN / 'batch8/tests'
SRC = OUT / 'src'


def source(name, body):
    path = SRC / (name.replace('.', '/') + '.java')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')


def main():
    fixtures = PLAN / 'batch6/tests/src'
    for file in fixtures.rglob('*.java'):
        rel = file.relative_to(fixtures)
        if rel.parts[0] == 'audit' or rel.as_posix().startswith(('net/wurstclient/hacks/', 'net/wurstclient/mixin/')):
            continue
        dest = SRC / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(file, dest)
    provenance = {}
    for name in ['hacks/' + n + 'Hack.java' for n in ['AntiVoid', 'FastClimb', 'Timer', 'AutoShearer', 'FastUse']] + ['mixin/FastUseCooldownMixin.java', 'util/RegistryIdFilter.java']:
        original = ROOT / 'fabric/src/main/java/net/wurstclient' / name
        dest = SRC / 'net/wurstclient' / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, dest)
        provenance[name] = hashlib.sha256(original.read_bytes()).hexdigest()
    source('net.wurstclient.Category', 'public enum Category {MOVEMENT,OTHER,ITEMS}')
    source('net.wurstclient.events.WorldChangeListener', 'public interface WorldChangeListener {void onWorldChange(net.minecraft.client.multiplayer.ClientLevel world);}')
    source('net.wurstclient.settings.TextFieldSetting', '''public class TextFieldSetting {public String value;
public TextFieldSetting(String n,String d,String v){value=v;}public String getValue(){return value;}}''')
    source('net.wurstclient.settings.SliderSetting', '''public class SliderSetting {public double value;
public SliderSetting(String n,String d,double v,double a,double b,double s,ValueDisplay vd){value=v;}
public SliderSetting(String n,double v,double a,double b,double s,ValueDisplay vd){value=v;}
public double getValue(){return value;}public float getValueF(){return (float)value;}public int getValueI(){return (int)value;}
public double getValueSq(){return value*value;}public String getValueString(){return Double.toString(value);}
public enum ValueDisplay {INTEGER,DECIMAL;public ValueDisplay withSuffix(String s){return this;}}}''')
    source('net.wurstclient.hack.Hack', '''public class Hack {
protected static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;
protected static final net.wurstclient.WurstClient WURST=net.wurstclient.WurstClient.INSTANCE;
public static final Events EVENTS=new Events();private boolean enabled;private String name;
public Hack(String n){name=n;}public String getName(){return name;}public String getRenderName(){return name;}
protected void setCategory(Object c){}protected void addSetting(Object s){}protected void onEnable(){}protected void onDisable(){}
public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){if(v==enabled)return;enabled=v;if(v)onEnable();else onDisable();}
public static class Events {public void add(Object c,Object o){}public void remove(Object c,Object o){}}}''')
    source('net.wurstclient.WurstClient', '''public class WurstClient {
public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();public static final WurstClient INSTANCE=new WurstClient();
public final Hacks hacks=new Hacks();public Hacks getHax(){return hacks;}
public static class Hacks {public final Toggle freecamHack=new Toggle(),flightHack=new Toggle();public net.wurstclient.hacks.FastClimbHack fastClimbHack;public net.wurstclient.hacks.FastUseHack fastUseHack;}
public static class Toggle {private boolean enabled;public void setEnabled(boolean v){enabled=v;}public boolean isEnabled(){return enabled;}}}''')
    source('net.minecraft.world.entity.Entity', '''import net.minecraft.world.phys.*;public class Entity {
public boolean removed;public double x,y=64,z;public boolean isRemoved(){return removed;}
public AABB getBoundingBox(){return new AABB(x-.3,y,z-.3,x+.3,y+1,z+.3);}}''')
    source('net.minecraft.world.phys.AABB', '''public class AABB {public final double minX,minY,minZ,maxX,maxY,maxZ;
public AABB(double x,double y,double z,double a,double b,double c){minX=x;minY=y;minZ=z;maxX=a;maxY=b;maxZ=c;}
public Vec3 getCenter(){return new Vec3((minX+maxX)/2,(minY+maxY)/2,(minZ+maxZ)/2);}}''')
    source('net.minecraft.world.item.Item', 'public class Item {public final String id;public Item(String s){id=s;}}')
    source('net.minecraft.world.item.BlockItem', 'public class BlockItem extends Item {public BlockItem(String s){super(s);}}')
    source('net.minecraft.world.item.Items', '''public class Items {
public static final Item SNOWBALL=new Item("minecraft:snowball"),EGG=new Item("minecraft:egg"),EXPERIENCE_BOTTLE=new Item("minecraft:experience_bottle"),ENDER_EYE=new Item("minecraft:ender_eye"),ENDER_PEARL=new Item("minecraft:ender_pearl"),SPLASH_POTION=new Item("minecraft:splash_potion"),LINGERING_POTION=new Item("minecraft:lingering_potion"),SHEARS=new Item("minecraft:shears");}''')
    source('net.minecraft.world.item.ItemStack', '''public class ItemStack {public final Item item;public int damage,max=100;
public ItemStack(Item i){item=i;}public Item getItem(){return item;}public boolean is(Item i){return item==i;}
public boolean isDamageableItem(){return item==Items.SHEARS;}public int getDamageValue(){return damage;}public int getMaxDamage(){return max;}}''')
    source('net.minecraft.world.item.DyeColor', 'public enum DyeColor {WHITE,ORANGE,MAGENTA,LIGHT_BLUE,YELLOW,LIME,PINK,GRAY,LIGHT_GRAY,CYAN,PURPLE,BLUE,BROWN,GREEN,RED,BLACK}')
    source('net.minecraft.world.entity.animal.Sheep', '''public class Sheep extends net.minecraft.world.entity.Entity {
public boolean baby,sheared;public float health=10;public net.minecraft.world.item.DyeColor color=net.minecraft.world.item.DyeColor.WHITE;
public boolean isBaby(){return baby;}public boolean isSheared(){return sheared;}public float getHealth(){return health;}
public net.minecraft.world.item.DyeColor getColor(){return color;}}''')
    source('net.minecraft.core.registries.BuiltInRegistries', '''public class BuiltInRegistries {public static final Registry ITEM=new Registry();
public static class Registry {public String getKey(net.minecraft.world.item.Item i){return i.id;}}}''')
    source('net.minecraft.client.player.LocalPlayer', '''import net.minecraft.world.item.*;import net.minecraft.world.phys.*;
public class LocalPlayer extends net.minecraft.world.entity.Entity {
public boolean spectator,passenger,climbable,horizontalCollision;public int jumps;public float yaw=12,pitch=34;
public Vec3 velocity=new Vec3(1,0,2);public Inventory inventory=new Inventory();public ItemStack offhand=new ItemStack(new Item("minecraft:air"));
public boolean isSpectator(){return spectator;}public boolean isPassenger(){return passenger;}public boolean onClimbable(){return climbable;}
public double getY(){return y;}public int getBlockY(){return (int)Math.floor(y);}public net.minecraft.core.BlockPos blockPosition(){return new net.minecraft.core.BlockPos((int)x,getBlockY(),(int)z);}
public Vec3 getDeltaMovement(){return velocity;}public void setDeltaMovement(double a,double b,double c){velocity=new Vec3(a,b,c);}public void jumpFromGround(){jumps++;}
public Inventory getInventory(){return inventory;}public ItemStack getMainHandItem(){return inventory.items[inventory.selected];}public ItemStack getOffhandItem(){return offhand;}
public double distanceToSqr(net.minecraft.world.entity.Entity e){return Math.pow(x-e.x,2)+Math.pow(y-e.y,2)+Math.pow(z-e.z,2);}
public void swing(Object hand){}public float getYRot(){return yaw;}public float getXRot(){return pitch;}
public static class Inventory {public int selected;public ItemStack[] items=new ItemStack[9];public Inventory(){java.util.Arrays.setAll(items,i->new ItemStack(new Item("minecraft:air")));}public ItemStack getItem(int i){return items[i];}}}''')
    source('net.minecraft.world.level.block.Blocks', 'public class Blocks {public static final Block POWDER_SNOW=new Block();}')
    source('net.minecraft.world.level.block.PowderSnowBlock', '''public class PowderSnowBlock {public static boolean walk=true;public static boolean canEntityWalkOnPowderSnow(Object p){return walk;}}''')
    source('net.minecraft.world.level.block.state.BlockState', '''public record BlockState(net.minecraft.world.level.block.Block block) {
public boolean is(net.minecraft.world.level.block.Block b){return block==b;}}''')
    source('net.minecraft.client.multiplayer.ClientLevel', '''public class ClientLevel {
public int minY=-64;public int getMinBuildHeight(){return minY;}
public final java.util.List<net.minecraft.world.entity.Entity> entities=new java.util.ArrayList<>();
public Iterable<net.minecraft.world.entity.Entity> entitiesForRendering(){return entities;}}''')
    source('net.wurstclient.util.BlockUtils', '''public class BlockUtils {public static net.minecraft.world.level.block.Block block=new net.minecraft.world.level.block.Block();
public static net.minecraft.world.level.block.state.BlockState getState(Object p){return new net.minecraft.world.level.block.state.BlockState(block);}}''')
    source('net.minecraft.world.InteractionHand', 'public enum InteractionHand {MAIN_HAND,OFF_HAND}')
    source('net.minecraft.world.InteractionResult', 'public class InteractionResult {public boolean consumesAction(){return true;}}')
    source('net.wurstclient.util.Rotation', '''public record Rotation(float yaw,float pitch) {
public static final java.util.List<String> actions=new java.util.ArrayList<>();public void sendPlayerLookPacket(){actions.add("rotate:"+pitch);}}''')
    source('net.wurstclient.util.RotationUtils', 'public class RotationUtils {public static Rotation getNeededRotations(Object v){return new Rotation(55,66);}}')
    source('net.wurstclient.mixin.MinecraftAccessor', '''public interface MinecraftAccessor {int getRightClickDelay();void invokeStartUseItem();}''')
    source('net.minecraft.client.Minecraft', '''public class Minecraft extends net.wurstclient.mixin.FastUseCooldownMixin implements net.wurstclient.mixin.MinecraftAccessor {
public net.minecraft.client.player.LocalPlayer player;public net.minecraft.client.multiplayer.ClientLevel level;public Object screen;
public final Options options=new Options();public GameMode gameMode=new GameMode();public int delay,uses;
public Object getConnection(){return player==null?null:this;}public int getRightClickDelay(){return delay;}
public void invokeStartUseItem(){uses++;delay=net.wurstclient.WurstClient.INSTANCE.hacks.fastUseHack.getItemUseCooldown(player.getMainHandItem());}
public static class Options {public final Key keyJump=new Key(),keyUse=new Key();}public static class Key {public boolean down;public boolean isDown(){return down;}public void setDown(boolean v){down=v;}}
public static class GameMode {public int uses,slot;public boolean fail;public net.minecraft.world.InteractionHand hand;
public net.minecraft.world.InteractionResult interact(net.minecraft.client.player.LocalPlayer p,net.minecraft.world.entity.Entity e,net.minecraft.world.InteractionHand h){uses++;slot=p.inventory.selected;hand=h;net.wurstclient.util.Rotation.actions.add("interact");if(fail)throw new IllegalStateException("synthetic");return new net.minecraft.world.InteractionResult();}}}''')
    source('net.minecraft.world.flag.FeatureFlagSet', 'public class FeatureFlagSet {}')
    source('org.spongepowered.asm.mixin.Shadow', 'public @interface Shadow {}')
    source('org.spongepowered.asm.mixin.injection.At', 'public @interface At {String value();String target() default "";}')
    source('com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation', 'public @interface WrapOperation {String method();org.spongepowered.asm.mixin.injection.At at();}')
    source('com.llamalad7.mixinextras.injector.wrapoperation.Operation', 'public interface Operation<T> {T call(Object... args);}')
    source('audit.RecoveryProbe', '''import java.lang.reflect.*;import net.wurstclient.*;import net.wurstclient.hacks.*;import net.wurstclient.settings.*;
import net.minecraft.client.player.*;import net.minecraft.client.multiplayer.*;import net.minecraft.world.item.*;import net.minecraft.world.entity.animal.*;
import net.minecraft.world.InteractionHand;import net.wurstclient.util.*;
public class RecoveryProbe {
static final net.minecraft.client.Minecraft mc=WurstClient.MC;static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
static void close(double a,double b,String s){check(Math.abs(a-b)<1e-5,s);}
static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
static void flag(Object o,String n,boolean v)throws Exception{((CheckboxSetting)field(o,n)).value=v;}
static void mode(Object o,String n,String v)throws Exception{EnumSetting e=(EnumSetting)field(o,n);e.value=Enum.valueOf(e.value.getClass(),v);}
static void reset(){mc.player=new LocalPlayer();mc.level=new ClientLevel();mc.gameMode=new net.minecraft.client.Minecraft.GameMode();mc.screen=null;mc.delay=0;mc.uses=0;mc.options.keyJump.down=false;mc.options.keyUse.down=false;WurstClient.INSTANCE.hacks.flightHack.setEnabled(false);WurstClient.INSTANCE.hacks.freecamHack.setEnabled(false);Rotation.actions.clear();}
public static void main(String[] args)throws Exception {
reset();var anti=new AntiVoidHack();anti.setEnabled(true);mc.player.y=-63;anti.onUpdate();close(mc.player.velocity.y,.5,"default bounce");close(mc.player.velocity.x,1,"horizontal motion retained");check(!mc.options.keyJump.down,"no stuck jump key");
mode(anti,"mode","FLIGHT");mc.player.y=-65;anti.onUpdate();check(WurstClient.INSTANCE.hacks.flightHack.isEnabled(),"temporary flight");mc.player.y=-60;anti.onUpdate();check(!WurstClient.INSTANCE.hacks.flightHack.isEnabled(),"recovery releases owned flight");
WurstClient.INSTANCE.hacks.flightHack.setEnabled(true);mc.player.y=-65;anti.onUpdate();anti.setEnabled(false);check(WurstClient.INSTANCE.hacks.flightHack.isEnabled(),"preexisting manual flight preserved");
WurstClient.INSTANCE.hacks.flightHack.setEnabled(false);anti.setEnabled(true);anti.onUpdate();anti.onWorldChange(null);check(!WurstClient.INSTANCE.hacks.flightHack.isEnabled(),"world releases temporary flight");mode(anti,"mode","JUMP");anti.onUpdate();check(mc.player.jumps==1,"jump mode");mc.player.y=-80;anti.onUpdate();check(mc.player.jumps==1,"void lower bound");mc.player=null;anti.onUpdate();anti.setEnabled(false);
reset();var climb=new FastClimbHack();WurstClient.INSTANCE.hacks.fastClimbHack=climb;var timer=new TimerHack();timer.setEnabled(true);climb.setEnabled(true);mc.player.climbable=true;mc.player.horizontalCollision=true;climb.onUpdate();close(mc.player.velocity.y,.2872,"velocity climb");flag(climb,"timerMode",true);close(timer.getTimerSpeed(),1.436,"climb timer priority");mc.player.climbable=false;close(timer.getTimerSpeed(),2,"timer setting restored off ladder");mc.player.climbable=true;climb.setEnabled(false);close(timer.getTimerSpeed(),2,"disable removes override");climb.setEnabled(true);mc.level=null;close(timer.getTimerSpeed(),2,"disconnect removes override");reset();mc.player.climbable=false;mc.player.horizontalCollision=true;BlockUtils.block=net.minecraft.world.level.block.Blocks.POWDER_SNOW;close(climb.getTimerMultiplier(),1.436,"powder snow boots path");net.minecraft.world.level.block.PowderSnowBlock.walk=false;close(climb.getTimerMultiplier(),1,"powder snow footwear guard");climb.setEnabled(false);
reset();var shear=new AutoShearerHack();shear.setEnabled(true);var sheep=new Sheep();mc.level.entities.add(sheep);mc.player.inventory.items[4]=new ItemStack(Items.SHEARS);shear.onUpdate();check(mc.gameMode.slot==4&&mc.player.inventory.selected==0,"shears slot restored");check(Rotation.actions.equals(java.util.List.of("rotate:66.0","interact","rotate:34.0")),"rotation surrounds interaction");
shear.setEnabled(false);shear.setEnabled(true);mc.gameMode.fail=true;try{shear.onUpdate();throw new AssertionError("fault expected");}catch(IllegalStateException expected){}check(mc.player.inventory.selected==0,"failed shearing restores slot");mc.gameMode.fail=false;
flag(shear,"antiBreak",true);mc.player.inventory.items[4].damage=99;mc.player.offhand=new ItemStack(Items.SHEARS);shear.onUpdate();check(mc.gameMode.hand==InteractionHand.OFF_HAND,"usable offhand wins over broken main shears");shear.setEnabled(false);shear.setEnabled(true);mc.screen=new Object();int count=mc.gameMode.uses;shear.onUpdate();check(mc.gameMode.uses==count,"inventory guard");shear.setEnabled(false);
reset();var use=new FastUseHack();WurstClient.INSTANCE.hacks.fastUseHack=use;use.setEnabled(true);check(use.getItemUseCooldown(new ItemStack(Items.EGG))==0,"native throwables");check(use.getItemUseCooldown(new ItemStack(new Item("minecraft:stick")))==4,"native unrelated item");
mode(use,"scope","SELECTED");((TextFieldSetting)field(use,"selectedItems")).value="minecraft:stick";((SliderSetting)field(use,"cooldown")).value=2;var stick=new ItemStack(new Item("minecraft:stick"));check(use.getItemUseCooldown(stick)==2,"selected cooldown");check(use.getItemUseCooldown(new ItemStack(Items.EGG))==4,"excluded stack");flag(use,"blocks",true);check(use.getItemUseCooldown(new ItemStack(new BlockItem("minecraft:stone")))==2,"all block items");
var hook=net.wurstclient.mixin.FastUseCooldownMixin.class.getDeclaredMethod("wurstUseCooldown",ItemStack.class,net.minecraft.world.flag.FeatureFlagSet.class,com.llamalad7.mixinextras.injector.wrapoperation.Operation.class);hook.setAccessible(true);var delay=net.wurstclient.mixin.FastUseCooldownMixin.class.getDeclaredField("rightClickDelay");delay.setAccessible(true);
com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original=(a)->false;boolean enabled=(boolean)hook.invoke(mc,stick,new net.minecraft.world.flag.FeatureFlagSet(),original);check(!enabled&&delay.getInt(mc)==2,"actual per-stack hook preserves feature flag result");hook.invoke(mc,new ItemStack(Items.EGG),new net.minecraft.world.flag.FeatureFlagSet(),original);check(delay.getInt(mc)==4,"offhand/nonselected stack resets vanilla delay");
mode(use,"mode","MULTI");mode(use,"scope","ALL");mc.options.keyUse.down=true;mc.player.inventory.items[0]=stick;use.onUpdate();check(mc.uses==1&&mc.delay==2,"nonzero cooldown stops burst");mc.delay=0;((SliderSetting)field(use,"cooldown")).value=0;((SliderSetting)field(use,"multiCount")).value=3;use.onUpdate();check(mc.uses==4,"zero cooldown burst count");use.setEnabled(false);check(use.getItemUseCooldown(stick)==4,"disabled passthrough");
System.out.println("PASS recovery: flight ownership/world changes, no forced jump key, timer priority/restoration, powder snow, shears/offhand/exception restoration, exact item filters, actual per-stack cooldown callback and burst limits");
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
        result = subprocess.run([str(java / 'java.exe'), '-cp', str(classes), 'audit.RecoveryProbe'], capture_output=True)
        code = result.returncode
        output += '\njava exit=' + str(code) + '\n' + result.stdout.decode('utf-8', errors='replace') + result.stderr.decode('utf-8', errors='replace')
    (OUT / 'result.txt').write_text(output, encoding='utf-8')
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2), encoding='utf-8')
    print(output)
    raise SystemExit(code)


if __name__ == '__main__':
    main()
