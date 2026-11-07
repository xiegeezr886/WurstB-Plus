"""Exercise native modules and unchanged mixin callbacks with a controlled host."""
from pathlib import Path
import hashlib,json,shutil,subprocess

ROOT=Path(__file__).resolve().parents[2]
PLAN=ROOT/'.planning/2026-11-07-four-client-v151-port'
OUT=PLAN/'batch4/tests';SRC=OUT/'src'

def source(name,body):
    p=SRC/(name.replace('.','/')+'.java');p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text('package '+name.rpartition('.')[0]+';\n'+body,encoding='utf-8')

def main():
    provenance={}
    for kind,names in [('hacks',['TridentBoostHack','MultitaskHack','NoInteractHack']),('mixin',['TridentBoostMixin','MultitaskMixin','NoInteractMixin']),('util',['RegistryIdFilter'])]:
        for name in names:
            rel=Path(kind)/(name+'.java');p=ROOT/'fabric/src/main/java/net/wurstclient'/rel;dest=SRC/'net/wurstclient'/rel
            dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest);provenance[rel.as_posix()]=hashlib.sha256(p.read_bytes()).hexdigest()
    common=['net.wurstclient.SearchTags','net.wurstclient.settings.CheckboxSetting','net.wurstclient.settings.SliderSetting','net.wurstclient.settings.TextFieldSetting','net.minecraft.core.BlockPos','net.minecraft.core.Direction']
    for name in common:
        rel=Path(name.replace('.','/')+'.java');dest=SRC/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(PLAN/'batch3/tests/src'/rel,dest)
    source('net.wurstclient.settings.EnumSetting','''public class EnumSetting<T> {public T value;
public EnumSetting(String n,String d,T[] vs,T v){value=v;}public T getSelected(){return value;}}''')
    source('net.wurstclient.settings.BlockListSetting','''public class BlockListSetting {
public final java.util.Set<net.minecraft.world.level.block.Block> blocks=new java.util.HashSet<>();
public BlockListSetting(String n,String d){}public boolean contains(net.minecraft.world.level.block.Block b){return blocks.contains(b);}}''')
    source('net.wurstclient.Category','public enum Category {MOVEMENT,OTHER}')
    source('net.wurstclient.hack.Hack','''public class Hack {
protected static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;
protected static final net.wurstclient.WurstClient WURST=net.wurstclient.WurstClient.INSTANCE;
private boolean enabled;public Hack(String n){}protected void setCategory(Object c){}protected void addSetting(Object s){}
public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;}}''')
    source('net.minecraft.world.InteractionHand','public enum InteractionHand {MAIN_HAND,OFF_HAND}')
    source('net.minecraft.world.InteractionResult','public enum InteractionResult {FAIL,SUCCESS,PASS}')
    source('net.minecraft.world.level.block.Block','public record Block(String id) {}')
    source('net.minecraft.world.entity.Entity','''public class Entity {public String type="minecraft:pig",name="entity";public boolean named;
public String getType(){return type;}public String getScoreboardName(){return name;}public boolean hasCustomName(){return named;}}''')
    source('net.minecraft.world.entity.animal.Animal','public class Animal extends net.minecraft.world.entity.Entity {public boolean baby;public boolean isBaby(){return baby;}}')
    source('net.minecraft.world.entity.player.Player','''public class Player extends net.minecraft.world.entity.Entity {
public boolean wet,using;public double x,y,z;public Player(){type="minecraft:player";}
public void push(double x,double y,double z){this.x=x;this.y=y;this.z=z;}public boolean isInWaterOrRain(){return wet;}public boolean isUsingItem(){return using;}}''')
    source('net.minecraft.client.player.LocalPlayer','public class LocalPlayer extends net.minecraft.world.entity.player.Player {}')
    source('net.minecraft.world.item.TridentItem','public class TridentItem {}')
    source('net.minecraft.core.registries.BuiltInRegistries','''public class BuiltInRegistries {public static final Registry ENTITY_TYPE=new Registry();
public static class Registry {public String getKey(String s){return s;}}}''')
    source('net.minecraft.world.phys.BlockHitResult','public record BlockHitResult(net.minecraft.core.BlockPos pos){public net.minecraft.core.BlockPos getBlockPos(){return pos;}}')
    source('net.minecraft.world.phys.EntityHitResult','public class EntityHitResult {}')
    source('net.minecraft.client.multiplayer.MultiPlayerGameMode','''public class MultiPlayerGameMode {public int releases;
public void releaseUsingItem(net.minecraft.client.player.LocalPlayer p){releases++;p.using=false;}}''')
    source('net.minecraft.client.Minecraft','''public class Minecraft extends net.wurstclient.mixin.MultitaskMixin {
public net.minecraft.client.player.LocalPlayer player=new net.minecraft.client.player.LocalPlayer();
public net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode=new net.minecraft.client.multiplayer.MultiPlayerGameMode();
public Level level=new Level();public Options options=new Options();
public static class Level {public final java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.Block> blocks=new java.util.HashMap<>();
public State getBlockState(net.minecraft.core.BlockPos p){return new State(blocks.getOrDefault(p,new net.minecraft.world.level.block.Block("air")));}}
public record State(net.minecraft.world.level.block.Block block){public net.minecraft.world.level.block.Block getBlock(){return block;}}
public static class Options {public final Key keyUse=new Key();}public static class Key {public boolean down;public int clicks;
public boolean isDown(){return down;}public boolean consumeClick(){if(clicks<=0)return false;clicks--;return true;}}}''')
    source('net.wurstclient.WurstClient','''public class WurstClient {
public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();public static final WurstClient INSTANCE=new WurstClient();
private Hax hax;public final java.util.Set<String> friends=new java.util.HashSet<>();public java.util.Set<String> getFriends(){return friends;}
public Hax getHax(){if(hax==null)hax=new Hax();return hax;}
public static class Hax {public final net.wurstclient.hacks.TridentBoostHack tridentBoostHack=new net.wurstclient.hacks.TridentBoostHack();
public final net.wurstclient.hacks.MultitaskHack multitaskHack=new net.wurstclient.hacks.MultitaskHack();
public final net.wurstclient.hacks.NoInteractHack noInteractHack=new net.wurstclient.hacks.NoInteractHack();}}''')
    source('org.spongepowered.asm.mixin.Mixin','public @interface Mixin {Class<?>[] value();int priority() default 1000;}')
    source('org.spongepowered.asm.mixin.injection.At','public @interface At {String value();String target() default "";int ordinal() default -1;}')
    for name in ['Inject','Redirect']:
        source('org.spongepowered.asm.mixin.injection.'+name,'public @interface '+name+' {String[] method();At at();boolean cancellable() default false;int require() default -1;}')
    source('com.llamalad7.mixinextras.injector.ModifyExpressionValue','public @interface ModifyExpressionValue {String[] method();org.spongepowered.asm.mixin.injection.At at();}')
    source('org.spongepowered.asm.mixin.injection.callback.CallbackInfo','public class CallbackInfo {public boolean cancelled;public void cancel(){cancelled=true;}}')
    source('org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable','public class CallbackInfoReturnable<T> extends CallbackInfo {public T value;public void setReturnValue(T v){value=v;cancel();}}')
    source('audit.InteractionProbe','''import java.lang.reflect.*;import net.wurstclient.*;import net.wurstclient.settings.*;import net.wurstclient.mixin.*;
import net.minecraft.world.entity.*;import net.minecraft.world.entity.player.*;import net.minecraft.world.entity.animal.*;
import net.minecraft.world.InteractionHand;import net.minecraft.world.InteractionResult;import net.minecraft.core.*;
import org.spongepowered.asm.mixin.injection.callback.*;
public class InteractionProbe {
static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
static Object field(Object x,String n)throws Exception{Field f=x.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(x);}
static void mode(Object x,String n,String v)throws Exception {EnumSetting e=(EnumSetting)field(x,n);e.value=Enum.valueOf((Class)e.value.getClass(),v);}
static Object call(Class<?> type,Object x,String name,Object...args)throws Exception {for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)){m.setAccessible(true);return m.invoke(x,args);}throw new AssertionError(name);}
public static void main(String[] args)throws Exception {
var mc=WurstClient.MC;var h=WurstClient.INSTANCE.getHax();var t=h.tridentBoostHack;TridentBoostMixin tm=new TridentBoostMixin(){};
call(TridentBoostMixin.class,tm,"wurstRiptideVelocity",mc.player,1d,-2d,3d);check(mc.player.z==3,"disabled velocity");t.setEnabled(true);
call(TridentBoostMixin.class,tm,"wurstRiptideVelocity",mc.player,1d,-2d,3d);check(mc.player.x==2&&mc.player.y==-4&&mc.player.z==6,"local three-axis boost");
Player other=new Player();call(TridentBoostMixin.class,tm,"wurstRiptideVelocity",other,1d,-2d,3d);check(other.z==3,"other entity unchanged");
check((boolean)call(TridentBoostMixin.class,tm,"wurstRiptideWater",mc.player)&&!(boolean)call(TridentBoostMixin.class,tm,"wurstRiptideWater",other),"water bypass local only");
((CheckboxSetting)field(t,"outOfWater")).value=false;check(!(boolean)call(TridentBoostMixin.class,tm,"wurstRiptideWater",mc.player),"water option");mc.player.wet=true;check((boolean)call(TridentBoostMixin.class,tm,"wurstRiptideWater",mc.player),"natural water");
var m=h.multitaskHack;check((boolean)call(MultitaskMixin.class,mc,"wurstUseWhileMining",true),"disabled multitask");m.setEnabled(true);
check(!(boolean)call(MultitaskMixin.class,mc,"wurstUseWhileMining",true)&&!(boolean)call(MultitaskMixin.class,mc,"wurstMineWhileUsing",true),"use and mine");
mc.player.using=true;mc.options.keyUse.clicks=3;call(MultitaskMixin.class,mc,"wurstPreserveUseRelease",new CallbackInfo());check(mc.gameMode.releases==1&&mc.options.keyUse.clicks==0,"release stops use and consumes pending clicks");
mc.player.using=true;mc.options.keyUse.down=true;call(MultitaskMixin.class,mc,"wurstPreserveUseRelease",new CallbackInfo());check(mc.gameMode.releases==1,"held use retained");
((CheckboxSetting)field(m,"attackEntities")).value=false;check((boolean)call(MultitaskMixin.class,mc,"wurstAttackWhileUsing",true)&&!(boolean)call(MultitaskMixin.class,mc,"wurstMineWhileUsing",true),"entity attack toggle independent");
var n=h.noInteractHack;Entity e=new Entity();BlockPos pos=new BlockPos(0,0,0);var block=new net.minecraft.world.level.block.Block("chest");mc.level.blocks.put(pos,block);
check(n.shouldAttackEntity(e)&&n.shouldAttackBlock(pos),"disabled no interact");n.setEnabled(true);check(n.shouldAttackEntity(e)&&n.shouldAttackBlock(pos),"empty blacklist permits");
((BlockListSetting)field(n,"blockMine")).blocks.add(block);check(!n.shouldAttackBlock(pos),"mining blacklist");mode(n,"blockMineMode","WHITELIST");check(n.shouldAttackBlock(pos),"mining whitelist");
mode(n,"blockHand","OFF_HAND");check(!n.shouldInteractBlock(pos,InteractionHand.OFF_HAND)&&n.shouldInteractBlock(pos,InteractionHand.MAIN_HAND),"block hand filter");
((TextFieldSetting)field(n,"entityHit")).value="pig";check(!n.shouldAttackEntity(e),"entity ID blacklist");mode(n,"entityHitMode","WHITELIST");check(n.shouldAttackEntity(e)&&!n.shouldAttackEntity(other),"entity ID whitelist");mode(n,"entityHitMode","BLACKLIST");((TextFieldSetting)field(n,"entityHit")).value="";
other.name="friend";WurstClient.INSTANCE.friends.add("friend");mode(n,"friends","HIT");check(!n.shouldAttackEntity(other)&&n.shouldInteractEntity(other,InteractionHand.MAIN_HAND),"friend attack-only");
Animal baby=new Animal();baby.baby=true;mode(n,"babies","INTERACT");check(n.shouldAttackEntity(baby)&&!n.shouldInteractEntity(baby,InteractionHand.MAIN_HAND),"baby interact-only");
e.named=true;mode(n,"named","BOTH");check(!n.shouldAttackEntity(e)&&!n.shouldInteractEntity(e,InteractionHand.MAIN_HAND),"named both");
NoInteractMixin nm=new NoInteractMixin(){};CallbackInfo attack=new CallbackInfo();call(NoInteractMixin.class,nm,"wurstEntityAttack",mc.player,e,attack);check(attack.cancelled,"attack callback cancellation");
CallbackInfoReturnable<InteractionResult> interact=new CallbackInfoReturnable<>();call(NoInteractMixin.class,nm,"wurstEntityUse",mc.player,e,InteractionHand.MAIN_HAND,interact);check(interact.value==InteractionResult.FAIL,"interaction callback fails without fallback");
mode(n,"blockMineMode","BLACKLIST");CallbackInfoReturnable<Boolean> mining=new CallbackInfoReturnable<>();call(NoInteractMixin.class,nm,"wurstBlockMining",pos,Direction.UP,mining);check(Boolean.FALSE.equals(mining.value),"mining callback cancellation");
n.setEnabled(false);check(n.shouldAttackEntity(e)&&n.shouldInteractBlock(pos,InteractionHand.OFF_HAND),"disabled preserves vanilla");
System.out.println("PASS TridentBoost local-only velocity/water hooks; Multitask key release/held state; NoInteract list/hand/friend/baby/name modes and cancellable callbacks");
}}
''')
    classes=OUT/'classes';classes.mkdir(parents=True,exist_ok=True);args=OUT/'sources.txt'
    args.write_text('\n'.join('"'+p.as_posix()+'"' for p in SRC.rglob('*.java')),encoding='utf-8');results=[]
    for cmd in [['javac','-encoding','UTF-8','-d',str(classes),'@'+str(args)],['java','-cp',str(classes),'audit.InteractionProbe']]:
        run=subprocess.run(cmd,capture_output=True,timeout=60);result=f'{cmd[0]} exit={run.returncode}\n'+run.stdout.decode('utf-8',errors='replace')+run.stderr.decode('utf-8',errors='replace')
        results.append(result);(OUT/'result.txt').write_text('\n'.join(results),encoding='utf-8');print(result);assert run.returncode==0
    (OUT/'provenance.json').write_text(json.dumps(provenance,indent=2),encoding='utf-8')

if __name__=='__main__':main()
