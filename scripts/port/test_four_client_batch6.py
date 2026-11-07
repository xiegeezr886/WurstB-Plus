"""Exercise unchanged movement modules and the actual multiplier callback.

Host fixtures model input, motion and collisions. Real mapped-jar compilation
and hook inspection remain separate from these deterministic behavior checks.
"""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / '.planning/2026-11-07-four-client-v151-port'
OUT = PLAN / 'batch6/tests'
SRC = OUT / 'src'


def source(name, body):
    path = SRC / (name.replace('.', '/') + '.java')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')


def main():
    provenance = {}
    for name in ['hacks/' + n + 'Hack.java' for n in
                 ['AirJump', 'AutoJump', 'Spider', 'ReverseStep', 'HighJump']] + ['mixin/HighJumpMultiplierMixin.java']:
        original = ROOT / 'fabric/src/main/java/net/wurstclient' / name
        dest = SRC / 'net/wurstclient' / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, dest)
        provenance[name] = hashlib.sha256(original.read_bytes()).hexdigest()
    source('net.wurstclient.Category', 'public enum Category {MOVEMENT}')
    source('net.wurstclient.SearchTags', 'public @interface SearchTags {String[] value();}')
    source('net.wurstclient.events.UpdateListener', 'public interface UpdateListener {void onUpdate();}')
    source('net.wurstclient.events.KeyPressListener', '''public interface KeyPressListener {
void onKeyPress(KeyPressEvent e);
record KeyPressEvent(int keyCode,int scanCode,int action,int modifiers) {
public int getKeyCode(){return keyCode;}public int getScanCode(){return scanCode;}
public int getAction(){return action;}public int getModifiers(){return modifiers;}}}''')
    source('com.mojang.blaze3d.platform.InputConstants', 'public class InputConstants {public static final int PRESS=1;}')
    source('net.wurstclient.settings.CheckboxSetting', '''public class CheckboxSetting {public boolean value;
public CheckboxSetting(String n,String d,boolean v){value=v;}public boolean isChecked(){return value;}}''')
    source('net.wurstclient.settings.EnumSetting', '''public class EnumSetting<T extends Enum<T>> {public T value;
public EnumSetting(String n,String d,T[] a,T v){value=v;}public EnumSetting(String n,T[] a,T v){value=v;}
public T getSelected(){return value;}}''')
    source('net.wurstclient.settings.SliderSetting', '''public class SliderSetting {public double value;
public SliderSetting(String n,String d,double v,double a,double b,double step,ValueDisplay vd){value=v;}
public double getValue(){return value;}public float getValueF(){return (float)value;}
public enum ValueDisplay {INTEGER,DECIMAL;public ValueDisplay withSuffix(String s){return this;}}}''')
    source('net.minecraft.world.phys.Vec3', '''public class Vec3 {public final double x,y,z;
public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}}''')
    source('net.minecraft.world.phys.AABB', '''public class AABB {public final double minX,minY,minZ,maxX,maxY,maxZ;
public AABB(double x,double y,double z,double a,double b,double c){minX=x;minY=y;minZ=z;maxX=a;maxY=b;maxZ=c;}
public AABB move(double x,double y,double z){return new AABB(minX+x,minY+y,minZ+z,maxX+x,maxY+y,maxZ+z);}}''')
    source('net.minecraft.core.BlockPos', '''public record BlockPos(int x,int y,int z) {public int getY(){return y;}}''')
    source('net.minecraft.world.level.block.Block', 'public class Block {}')
    source('net.minecraft.world.level.block.BedBlock', 'public class BedBlock extends Block {}')
    source('net.minecraft.world.level.block.state.BlockState', '''public record BlockState(net.minecraft.world.level.block.Block block) {
public net.minecraft.world.level.block.Block getBlock(){return block;}}''')
    source('net.minecraft.world.entity.Entity', '''import net.minecraft.world.phys.*;import net.minecraft.core.*;
public class Entity extends net.wurstclient.mixin.HighJumpMultiplierMixin {
public boolean ground,water,lava,noPhysics;public double x=.5,y=64,z=.5;
public Entity controller;public Vec3 velocity=new Vec3(1,0,2);
public boolean onGround(){return ground;}public boolean isInWater(){return water;}public boolean isInLava(){return lava;}
public Entity getControllingPassenger(){return controller;}
public Vec3 getDeltaMovement(){return velocity;}public void setDeltaMovement(double a,double b,double c){velocity=new Vec3(a,b,c);}
public BlockPos blockPosition(){return new BlockPos((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));}
public AABB getBoundingBox(){return new AABB(x-.3,y,z-.3,x+.3,y+1.8,z+.3);}}''')
    source('net.minecraft.client.player.LocalPlayer', '''public class LocalPlayer extends net.minecraft.world.entity.Entity {
public boolean horizontalCollision,spectator,fallFlying,climbable,sneaking,sprinting;public float zza,xxa;public int jumps;
public net.minecraft.world.entity.Entity vehicle;public final Abilities abilities=new Abilities();
public boolean isSpectator(){return spectator;}public boolean isPassenger(){return vehicle!=null;}
public net.minecraft.world.entity.Entity getVehicle(){return vehicle;}
public boolean isFallFlying(){return fallFlying;}public boolean onClimbable(){return climbable;}
public boolean isShiftKeyDown(){return sneaking;}public boolean isSprinting(){return sprinting;}
public Abilities getAbilities(){return abilities;}public static class Abilities {public boolean flying;}
public void jumpFromGround(){jumps++;setDeltaMovement(velocity.x,.5,velocity.z);}}''')
    source('net.minecraft.client.multiplayer.ClientLevel', '''import net.minecraft.core.*;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;
public class ClientLevel {
public boolean noGround;public net.minecraft.world.entity.Entity collisionEntity;public net.minecraft.world.phys.AABB collisionBox;
public final java.util.Map<BlockPos,Block> blocks=new java.util.HashMap<>();
public BlockState getBlockState(BlockPos p){return new BlockState(blocks.getOrDefault(p,new Block()));}
public boolean noCollision(net.minecraft.world.entity.Entity e,net.minecraft.world.phys.AABB box){collisionEntity=e;collisionBox=box;return noGround;}}''')
    source('net.minecraft.client.Minecraft', '''public class Minecraft {
public net.minecraft.client.player.LocalPlayer player;public net.minecraft.client.multiplayer.ClientLevel level;public Object screen;
public final Options options=new Options();public static class Options {public final Key keyJump=new Key(32),keyShift=new Key(340);}
public static class Key {public boolean down;public int code;public Key(int c){code=c;}
public boolean isDown(){return down;}public boolean matches(int k,int s){return k==code;}}}''')
    source('net.wurstclient.WurstClient', '''public class WurstClient {
public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();public static final WurstClient INSTANCE=new WurstClient();
public final Hacks hacks=new Hacks();public Hacks getHax(){return hacks;}
public static class Hacks {public final Toggle freecamHack=new Toggle();
public net.wurstclient.hacks.HighJumpHack highJumpHack;}
public static class Toggle {private boolean enabled;public void setEnabled(boolean v){enabled=v;}public boolean isEnabled(){return enabled;}}}''')
    source('net.wurstclient.hack.Hack', '''public class Hack {
protected static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;
protected static final net.wurstclient.WurstClient WURST=net.wurstclient.WurstClient.INSTANCE;
public static final Events EVENTS=new Events();private boolean enabled;
public Hack(String n){}protected void setCategory(Object c){}protected void addSetting(Object s){}
protected void onEnable(){}protected void onDisable(){}public boolean isEnabled(){return enabled;}
public void setEnabled(boolean v){if(v==enabled)return;enabled=v;if(v)onEnable();else onDisable();}
public static class Events {public final java.util.Set<String> registered=new java.util.HashSet<>();
public void add(Class<?> c,Object o){registered.add(c.getName()+System.identityHashCode(o));}
public void remove(Class<?> c,Object o){registered.remove(c.getName()+System.identityHashCode(o));}}}''')
    source('org.spongepowered.asm.mixin.Mixin', 'public @interface Mixin {Class<?>[] value();}')
    source('org.spongepowered.asm.mixin.injection.At', 'public @interface At {String value();}')
    source('org.spongepowered.asm.mixin.injection.Inject', 'public @interface Inject {String method();At at();boolean cancellable();}')
    source('org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable', '''public class CallbackInfoReturnable<T> {
public T value;public CallbackInfoReturnable(T v){value=v;}public float getReturnValueF(){return ((Number)value).floatValue();}
public void setReturnValue(T v){value=v;}}''')
    source('audit.MovementProbe', '''import java.lang.reflect.*;import net.wurstclient.*;import net.wurstclient.hacks.*;
import net.wurstclient.hack.Hack;import net.wurstclient.settings.*;import net.wurstclient.events.KeyPressListener.KeyPressEvent;
import net.minecraft.client.player.*;import net.minecraft.client.multiplayer.*;import net.minecraft.world.entity.*;
import net.minecraft.core.*;import net.minecraft.world.level.block.*;import org.spongepowered.asm.mixin.injection.callback.*;
public class MovementProbe {
static final net.minecraft.client.Minecraft mc=WurstClient.MC;
static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
static void close(double a,double b,String s){check(Math.abs(a-b)<1e-6,s+": "+a+" != "+b);}
static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
static void mode(Object o,String n,String v)throws Exception{EnumSetting e=(EnumSetting)field(o,n);e.value=Enum.valueOf(e.value.getClass(),v);}
static void flag(Object o,String n,boolean v)throws Exception{((CheckboxSetting)field(o,n)).value=v;}
static void value(Object o,String n,double v)throws Exception{((SliderSetting)field(o,n)).value=v;}
static void reset(){mc.player=new LocalPlayer();mc.level=new ClientLevel();mc.screen=null;mc.options.keyJump.down=false;mc.options.keyShift.down=false;WurstClient.INSTANCE.hacks.freecamHack.setEnabled(false);}
public static void main(String[] args)throws Exception {
reset();var auto=new AutoJumpHack();auto.setEnabled(true);mc.player.ground=true;auto.onUpdate();check(mc.player.jumps==0,"legacy stationary behavior");
mc.player.zza=1;mc.player.horizontalCollision=true;auto.onUpdate();check(mc.player.jumps==1,"legacy obstacle jump");
mode(auto,"jumpWhen","ALWAYS");mode(auto,"mode","LOW_HOP");mc.player.zza=0;mc.player.horizontalCollision=false;auto.onUpdate();close(mc.player.velocity.y,.25,"low hop");close(mc.player.velocity.x,1,"horizontal motion preserved");
mode(auto,"jumpWhen","SPRINTING");mc.player.setDeltaMovement(1,0,2);auto.onUpdate();close(mc.player.velocity.y,0,"sprint requires input");
mc.player.xxa=1;mc.player.sprinting=true;auto.onUpdate();close(mc.player.velocity.y,.25,"sprint strafe");
mc.player.sneaking=true;mc.player.setDeltaMovement(1,0,2);auto.onUpdate();close(mc.player.velocity.y,0,"sneak inhibits auto jump");mc.player.sneaking=false;
mc.screen=new Object();auto.onUpdate();close(mc.player.velocity.y,0,"menu guard");mc.screen=null;WurstClient.INSTANCE.hacks.freecamHack.setEnabled(true);auto.onUpdate();close(mc.player.velocity.y,0,"freecam guard");auto.setEnabled(false);
reset();var air=new AirJumpHack();air.setEnabled(true);air.onKeyPress(new KeyPressEvent(32,0,1,0));close(mc.player.velocity.y,.42,"press between ticks");
flag(air,"nativeJump",true);air.onKeyPress(new KeyPressEvent(32,0,2,0));check(mc.player.jumps==0,"repeat ignored");
air.onKeyPress(new KeyPressEvent(32,0,1,0));check(mc.player.jumps==1,"native jump");mc.options.keyJump.down=true;air.onUpdate();check(mc.player.jumps==1,"key and update do not double jump");
flag(air,"maintainLevel",true);mc.player.setDeltaMovement(1,-.3,2);air.onUpdate();check(mc.player.jumps==2,"hold restores level");air.onUpdate();check(mc.player.jumps==2,"rising motion preserved");
mc.player.setDeltaMovement(1,-.3,2);mc.options.keyShift.down=true;air.onKeyPress(new KeyPressEvent(340,0,1,0));air.onUpdate();check(mc.player.jumps==2,"lower target level");
mc.player.y=63;air.onUpdate();check(mc.player.jumps==3,"sneak press lowers once");
mc.screen=new Object();air.onKeyPress(new KeyPressEvent(32,0,1,0));check(mc.player.jumps==3,"air menu guard");mc.screen=null;
WurstClient.INSTANCE.hacks.freecamHack.setEnabled(true);air.onKeyPress(new KeyPressEvent(32,0,1,0));check(mc.player.jumps==3,"air freecam guard");WurstClient.INSTANCE.hacks.freecamHack.setEnabled(false);
flag(air,"maintainLevel",false);mc.player=new LocalPlayer();mc.player.y=10;mc.level=new ClientLevel();air.onUpdate();check(mc.player.jumps==0,"world change does not synthesize key edge");
mc.player=null;mc.level=null;air.onUpdate();air.setEnabled(false);check(Hack.EVENTS.registered.isEmpty(),"listener cleanup");
reset();var spider=new SpiderHack();mc.player.horizontalCollision=true;value(spider,"climbSpeed",.7);spider.onUpdate();close(mc.player.velocity.y,.7,"configured climb speed");
mc.player.setDeltaMovement(1,.4,2);spider.onUpdate();close(mc.player.velocity.y,.4,"existing upward jump preserved");mc.player=null;spider.onUpdate();
reset();var reverse=new ReverseStepHack();mc.player.ground=true;mc.player.zza=1;reverse.onUpdate();close(mc.player.velocity.y,-1,"default reverse step");
mc.player.setDeltaMovement(1,0,2);mc.level.noGround=true;reverse.onUpdate();close(mc.player.velocity.y,0,"fall distance limit");mc.level.noGround=false;
mc.player.x=.9;mc.level.blocks.put(new BlockPos(1,64,0),new BedBlock());reverse.onUpdate();close(mc.player.velocity.y,0,"bed edge footprint");mc.level.blocks.clear();
var vehicle=new Entity();vehicle.ground=true;vehicle.controller=mc.player;mc.player.vehicle=vehicle;reverse.onUpdate();close(vehicle.velocity.y,0,"vehicles opt in");
flag(reverse,"vehicles",true);reverse.onUpdate();close(vehicle.velocity.y,-1,"controlled vehicle snap");check(mc.level.collisionEntity==vehicle,"vehicle collision bounds");
vehicle.setDeltaMovement(1,0,2);vehicle.controller=new LocalPlayer();reverse.onUpdate();close(vehicle.velocity.y,0,"uncontrolled vehicle unchanged");vehicle.controller=mc.player;
mc.options.keyJump.down=true;reverse.onUpdate();close(vehicle.velocity.y,0,"vehicle jump guard");mc.options.keyJump.down=false;vehicle.water=true;reverse.onUpdate();close(vehicle.velocity.y,0,"vehicle water guard");
reset();var high=new HighJumpHack();WurstClient.INSTANCE.hacks.highJumpHack=high;close(high.getJumpMultiplier(),1,"disabled multiplier");high.setEnabled(true);close(high.getAdditionalJumpMotion(),.6,"legacy height");
mode(high,"mode","MULTIPLIER");value(high,"multiplier",3);close(high.getAdditionalJumpMotion(),0,"modes do not stack");
var hook=net.wurstclient.mixin.HighJumpMultiplierMixin.class.getDeclaredMethod("wurstJumpMultiplier",CallbackInfoReturnable.class);hook.setAccessible(true);
var cir=new CallbackInfoReturnable<Float>(.5f);hook.invoke(mc.player,cir);close(cir.value,1.5,"actual callback multiplies block factor");
var other=new CallbackInfoReturnable<Float>(.5f);hook.invoke(new Entity(),other);close(other.value,.5,"only local player");
high.setEnabled(false);cir=new CallbackInfoReturnable<Float>(.5f);hook.invoke(mc.player,cir);close(cir.value,.5,"disabled callback passthrough");
System.out.println("PASS movement: defaults, trigger modes, low hops, key edge deduplication, maintain/lower level, world/menu/freecam guards, listener cleanup, climb speed, beds, controlled vehicles, actual local-player multiplier callback");
}}''')
    java = Path('C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin')
    classes = OUT / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    argfile = OUT / 'sources.txt'
    argfile.write_text('\n'.join('"' + str(p.resolve()).replace('\\', '/') + '"'
                                 for p in SRC.rglob('*.java')), encoding='utf-8')
    compile_result = subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(classes), '@' + str(argfile)], capture_output=True)
    output = 'javac exit=' + str(compile_result.returncode) + '\n' + compile_result.stdout.decode('utf-8', errors='replace') + compile_result.stderr.decode('utf-8', errors='replace')
    code = compile_result.returncode
    if not code:
        run = subprocess.run([str(java / 'java.exe'), '-cp', str(classes), 'audit.MovementProbe'], capture_output=True)
        output += '\njava exit=' + str(run.returncode) + '\n' + run.stdout.decode('utf-8', errors='replace') + run.stderr.decode('utf-8', errors='replace')
        code = run.returncode
    (OUT / 'result.txt').write_text(output, encoding='utf-8')
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2), encoding='utf-8')
    print(output)
    raise SystemExit(code)


if __name__ == '__main__':
    main()
