"""Exercise Leaves paving, torch previews and sprint state using unchanged module sources."""
from pathlib import Path
import hashlib,json,shutil,subprocess
ROOT=Path(__file__).resolve().parents[2]
PLAN=ROOT/'.planning/2026-11-07-four-client-v151-port';OUT=PLAN/'batch5/tests';SRC=OUT/'src'

def source(name,body):
    p=SRC/(name.replace('.','/')+'.java');p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text('package '+name.rpartition('.')[0]+';\n'+body,encoding='utf-8')

def main():
    fixtures=PLAN/'batch2/lifecycle-tests/src'
    # Reuse common host fixtures; product classes are always copied from live source below.
    for p in fixtures.rglob('*.java'):
        rel=p.relative_to(fixtures)
        if rel.parts[0]=='audit' or rel.as_posix().startswith('net/wurstclient/hacks/'):continue
        dest=SRC/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest)
    provenance={}
    for name in ['hacks/autoplace/DirectionalPaver.java','hacks/AutoTorchHack.java','hacks/AutoSprintHack.java']:
        p=ROOT/'fabric/src/main/java/net/wurstclient'/name;dest=SRC/'net/wurstclient'/name
        dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest);provenance[name]=hashlib.sha256(p.read_bytes()).hexdigest()
    source('net.wurstclient.Category','public enum Category {BLOCKS,MOVEMENT}')
    source('net.wurstclient.events.UpdateListener','public interface UpdateListener {void onUpdate();}')
    source('net.wurstclient.events.RenderListener','public interface RenderListener {void onRender(com.mojang.blaze3d.vertex.PoseStack m,float f);}')
    source('net.wurstclient.settings.CheckboxSetting','''public class CheckboxSetting extends Setting {public boolean value;
public CheckboxSetting(String n,boolean v){value=v;}public CheckboxSetting(String n,String d,boolean v){value=v;}public boolean isChecked(){return value;}}''')
    source('net.wurstclient.settings.SliderSetting','''public class SliderSetting extends Setting {public double value;
public SliderSetting(String n,double v,double a,double b,double s,ValueDisplay d){value=v;}public SliderSetting(String n,String x,double v,double a,double b,double s,ValueDisplay d){value=v;}
public int getValueI(){return (int)value;}public double getValue(){return value;}public double getValueSq(){return value*value;}
public enum ValueDisplay {INTEGER,DECIMAL}}''')
    source('net.minecraft.core.BlockPos','''public record BlockPos(int x,int y,int z) {
public static BlockPos containing(net.minecraft.world.phys.Vec3 v){return new BlockPos((int)Math.floor(v.x()),(int)Math.floor(v.y()),(int)Math.floor(v.z()));}
public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}
public BlockPos immutable(){return this;}public BlockPos above(){return offset(0,1,0);}public BlockPos below(){return offset(0,-1,0);}
public BlockPos relative(Direction d){return offset(d.getStepX(),d.getStepY(),d.getStepZ());}
public BlockPos offset(int a,int b,int c){return new BlockPos(x+a,y+b,z+c);}}''')
    source('net.minecraft.core.Direction','''public enum Direction {UP(0,1,0),DOWN(0,-1,0),NORTH(0,0,-1),SOUTH(0,0,1),WEST(-1,0,0),EAST(1,0,0);
final int x,y,z;Direction(int a,int b,int c){x=a;y=b;z=c;}public int getStepX(){return x;}public int getStepY(){return y;}public int getStepZ(){return z;}
public Direction getOpposite(){return values()[ordinal()^1];}public Axis getAxis(){return y!=0?Axis.Y:x!=0?Axis.X:Axis.Z;}
public enum Axis {X,Y,Z;public boolean isHorizontal(){return this!=Y;}}}''')
    source('net.minecraft.world.phys.Vec3','''public record Vec3(double x,double y,double z) {
public static Vec3 atCenterOf(net.minecraft.core.BlockPos p){return new Vec3(p.x()+.5,p.y()+.5,p.z()+.5);}
public static Vec3 atBottomCenterOf(net.minecraft.core.BlockPos p){return new Vec3(p.x()+.5,p.y(),p.z()+.5);}
public Vec3 add(double a,double b,double c){return new Vec3(x+a,y+b,z+c);}public double distanceToSqr(Vec3 v){return Math.pow(x-v.x,2)+Math.pow(y-v.y,2)+Math.pow(z-v.z,2);}}''')
    source('net.minecraft.world.phys.BlockHitResult','''import net.minecraft.core.*;public class BlockHitResult extends HitResult {
public final Vec3 location;public final Direction face;public final BlockPos pos;public Type type=Type.BLOCK;
public BlockHitResult(Vec3 v,Direction f,BlockPos p,boolean b){location=v;face=f;pos=p;}
public BlockPos getBlockPos(){return pos;}public Direction getDirection(){return face;}public Type getType(){return type;}}''')
    source('net.minecraft.world.level.block.Block','''public class Block {private net.minecraft.world.item.Item item;
public net.minecraft.world.item.Item asItem(){if(item==null)item=new net.minecraft.world.item.BlockItem(this);return item;}
public static Block byItem(net.minecraft.world.item.Item i){return ((net.minecraft.world.item.BlockItem)i).getBlock();}
public net.minecraft.world.level.block.state.BlockState defaultBlockState(){return new net.minecraft.world.level.block.state.BlockState(this,false);}}''')
    source('net.minecraft.world.level.block.SlabBlock','public class SlabBlock extends Block {}')
    source('net.minecraft.world.level.block.TorchBlock','public class TorchBlock extends Block {}')
    source('net.minecraft.world.level.block.Blocks','public class Blocks {public static final TorchBlock TORCH=new TorchBlock();}')
    source('net.minecraft.world.level.block.state.BlockState','''public class BlockState {
public net.minecraft.world.level.block.Block block;public boolean replaceable;public BlockState(net.minecraft.world.level.block.Block b,boolean r){block=b;replaceable=r;}
public net.minecraft.world.level.block.Block getBlock(){return block;}public boolean canBeReplaced(){return replaceable;}public boolean isAir(){return replaceable;}
public boolean canSurvive(net.minecraft.client.multiplayer.ClientLevel l,net.minecraft.core.BlockPos p){return !l.getBlockState(p.below()).canBeReplaced();}}''')
    source('net.minecraft.world.item.ItemStack','''public class ItemStack {private final Item item;public ItemStack(Item i){item=i;}
public Item getItem(){return item;}public boolean is(Item i){return item==i;}public boolean isEmpty(){return item==null;}}''')
    source('net.minecraft.world.entity.Entity','public class Entity {}')
    source('net.minecraft.world.level.LightLayer','public enum LightLayer {BLOCK}')
    source('net.minecraft.world.effect.MobEffects','public class MobEffects {public static final Object BLINDNESS=new Object();}')
    source('net.minecraft.client.multiplayer.ClientLevel','''import net.minecraft.core.*;import net.minecraft.world.level.block.state.*;
public class ClientLevel {public final java.util.Map<BlockPos,BlockState> blocks=new java.util.HashMap<>();public boolean loaded=true,occupied;public int light;
public boolean hasChunkAt(BlockPos p){return loaded;}public boolean isInWorldBounds(BlockPos p){return true;}
public BlockState getBlockState(BlockPos p){return blocks.getOrDefault(p,new BlockState(new net.minecraft.world.level.block.Block(),true));}
public int getBrightness(Object l,BlockPos p){return light;}public java.util.List<net.minecraft.world.entity.Entity> getEntities(net.minecraft.world.entity.Entity e,net.minecraft.world.phys.AABB b){return occupied?java.util.List.of(new net.minecraft.world.entity.Entity()):java.util.List.of();}}''')
    source('net.minecraft.client.player.LocalPlayer','''import net.minecraft.world.item.*;import net.minecraft.world.phys.*;
public class LocalPlayer {
public final net.minecraft.client.Minecraft.Inventory inventory=new net.minecraft.client.Minecraft.Inventory();public Vec3 eyes=new Vec3(.5,2,.5);
public boolean sprint,passenger,flying,water,underwater,horizontalCollision,sneak,using,blind;public final Abilities abilities=new Abilities();public final Food food=new Food();public final Input input=new Input();
public net.minecraft.client.Minecraft.Inventory getInventory(){return inventory;}public ItemStack getMainHandItem(){return inventory.getItem(inventory.selected);}
public Vec3 getEyePosition(){return eyes;}public Vec3 position(){return new Vec3(.5,1,.5);}public net.minecraft.core.BlockPos blockPosition(){return new net.minecraft.core.BlockPos(0,1,0);}
public double distanceToSqr(Vec3 v){return position().distanceToSqr(v);}public void swing(Object h){}
public boolean isPassenger(){return passenger;}public boolean isFallFlying(){return flying;}public boolean isInWaterOrBubble(){return water;}public boolean isUnderWater(){return underwater;}
public boolean isShiftKeyDown(){return sneak;}public boolean isUsingItem(){return using;}public boolean hasEffect(Object o){return blind;}
public boolean isSprinting(){return sprint;}public void setSprinting(boolean v){sprint=v;}public Abilities getAbilities(){return abilities;}public Food getFoodData(){return food;}
public static class Abilities {public boolean mayfly;}public static class Food {public int hunger=20;public int getFoodLevel(){return hunger;}}
public static class Input {public float forwardImpulse;public double movement;public Vector getMoveVector(){return new Vector(movement);}}public record Vector(double length){}}
''')
    source('net.minecraft.client.Minecraft','''import net.minecraft.world.item.*;import net.minecraft.world.phys.*;
public class Minecraft {public net.minecraft.client.player.LocalPlayer player=new net.minecraft.client.player.LocalPlayer();
public net.minecraft.client.multiplayer.ClientLevel level=new net.minecraft.client.multiplayer.ClientLevel();public GameMode gameMode=new GameMode();public Object screen;public final Options options=new Options();
public static class Inventory {public int selected;public final ItemStack[] items=new ItemStack[9];public Inventory(){java.util.Arrays.fill(items,new ItemStack(new Item()));}public ItemStack getItem(int i){return items[i];}}
public static class GameMode {public final java.util.List<BlockHitResult> actions=new java.util.ArrayList<>();public boolean fail;
public void useItemOn(Object p,Object h,BlockHitResult hit){if(fail)throw new IllegalStateException("synthetic");actions.add(hit);}}
public static class Key {public boolean down;public boolean isDown(){return down;}}public static class Options {public final Key keySprint=new Key(),keyUp=new Key(),keyDown=new Key(),keyLeft=new Key(),keyRight=new Key();}}
''')
    source('net.wurstclient.WurstClient','''public class WurstClient {public static final WurstClient INSTANCE=new WurstClient();public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();
public RotationFaker getRotationFaker(){return new RotationFaker();}public static class RotationFaker {public void faceVectorPacket(Object v){}}}''')
    source('net.wurstclient.hack.Hack','''public class Hack {protected static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;
protected static final net.wurstclient.WurstClient WURST=net.wurstclient.WurstClient.INSTANCE;protected static final Events EVENTS=new Events();protected static final Interaction IMC=new Interaction();private boolean enabled;
public Hack(String s){}protected void setCategory(Object s){}protected void addSetting(Object s){}protected void onEnable(){}protected void onDisable(){}
public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){if(v==enabled)return;enabled=v;if(v)onEnable();else onDisable();}
public static class Events {public void add(Object a,Object b){}public void remove(Object a,Object b){}}
public static class Interaction {public Interaction getInteractionManager(){return this;}public void rightClickBlock(net.minecraft.core.BlockPos p,net.minecraft.core.Direction d,net.minecraft.world.phys.Vec3 v){MC.gameMode.useItemOn(MC.player,null,new net.minecraft.world.phys.BlockHitResult(v,d,p,false));}}}''')
    source('net.wurstclient.util.InventoryUtils','''public class InventoryUtils {public static int indexOf(net.minecraft.world.item.Item item,int max){for(int i=0;i<max;i++)if(net.wurstclient.WurstClient.MC.player.inventory.getItem(i).is(item))return i;return -1;}}''')
    source('net.wurstclient.util.BlockUtils','''import net.minecraft.core.*;import net.minecraft.world.phys.*;import net.wurstclient.WurstClient;
public class BlockUtils {public static boolean obstructed;
public static net.minecraft.world.level.block.state.BlockState getState(BlockPos p){return WurstClient.MC.level.getBlockState(p);}
public static boolean canBeClicked(BlockPos p){return !getState(p).canBeReplaced();}
public static java.util.stream.Stream<BlockPos> getAllInBoxStream(BlockPos center,int r){var list=new java.util.ArrayList<BlockPos>();for(int x=-r;x<=r;x++)for(int y=-r;y<=r;y++)for(int z=-r;z<=r;z++)list.add(center.offset(x,y,z));return list.stream();}
public static BlockHitResult raycast(Vec3 a,Vec3 b){var hit=new BlockHitResult(b,Direction.UP,new BlockPos(99,99,99),false);hit.type=obstructed?HitResult.Type.BLOCK:HitResult.Type.MISS;return hit;}}''')
    source('net.wurstclient.util.RenderUtils','''public class RenderUtils {public static int lines,boxes;
public static void drawLine(Object m,Object a,Object b,int c,boolean d){lines++;}public static void drawSolidBox(Object m,Object b,int c,boolean d){boxes++;}public static void drawOutlinedBox(Object m,Object b,int c,boolean d){boxes++;}}''')
    source('audit.LeavesProbe','''import java.lang.reflect.*;import net.wurstclient.*;import net.wurstclient.hacks.*;import net.wurstclient.hacks.autoplace.*;import net.wurstclient.settings.*;
import net.minecraft.core.*;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;import net.minecraft.world.item.*;import net.minecraft.world.phys.*;
public class LeavesProbe {
static final net.minecraft.client.Minecraft mc=WurstClient.MC;
static void check(boolean v,String s){if(!v)throw new AssertionError(s);}static Object field(Object x,String n)throws Exception{var f=x.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(x);}
static void mode(Object x,String n,String v)throws Exception{EnumSetting e=(EnumSetting)field(x,n);e.value=Enum.valueOf((Class)e.value.getClass(),v);}
static void toggle(Object x,String n,boolean v)throws Exception{((CheckboxSetting)field(x,n)).value=v;}
public static void main(String[] args)throws Exception {
var block=new SlabBlock();var support=new Block();var base=new BlockPos(0,0,0);mc.level.blocks.put(base,new BlockState(support,false));mc.player.inventory.items[1]=new ItemStack(block.asItem());
var p=new DirectionalPaver();p.reset();((SliderSetting)field(p,"delay")).value=0;mode(p,"support","DOWN");p.tick(block,5);check(mc.gameMode.actions.size()==1&&mc.player.inventory.selected==0,"pave cap and restore");
var hit=mc.gameMode.actions.get(0);check(hit.pos.equals(base)&&hit.face==Direction.UP,"support direction");
mc.gameMode.actions.clear();mode(p,"support","UP");p.tick(block,5);check(mc.gameMode.actions.get(0).face==Direction.DOWN,"upper support");
var upper=DirectionalPaver.hitPoint(base,Direction.EAST,true,true);var lower=DirectionalPaver.hitPoint(base,Direction.EAST,true,false);check(upper.x()==1&&upper.y()==.6&&lower.y()==.4,"slab halves on face");
mc.gameMode.actions.clear();mc.level.occupied=true;p.tick(block,5);check(mc.gameMode.actions.isEmpty(),"entity obstruction");mc.level.occupied=false;
mc.level.loaded=false;p.tick(block,5);check(mc.gameMode.actions.isEmpty(),"unloaded guard");mc.level.loaded=true;
mc.screen=new Object();p.tick(block,5);check(mc.gameMode.actions.isEmpty(),"menu guard");mc.screen=null;
mc.gameMode.fail=true;try{p.tick(block,5);throw new AssertionError("expected failure");}catch(IllegalStateException expected){}check(mc.player.inventory.selected==0,"exception slot restoration");mc.gameMode.fail=false;
mc.level.blocks.put(base,new BlockState(block,false));p.tick(block,5);check(mc.gameMode.actions.isEmpty(),"same block support ignored");mc.level.blocks.put(base,new BlockState(support,false));
var torch=new AutoTorchHack();torch.setEnabled(true);mc.player.inventory.items[1]=new ItemStack(Blocks.TORCH.asItem());toggle(torch,"onlyRender",true);torch.onUpdate();torch.onRender(new com.mojang.blaze3d.vertex.PoseStack(),0);check(mc.gameMode.actions.isEmpty()&&net.wurstclient.util.RenderUtils.lines>0,"render-only preview");
toggle(torch,"onlyRender",false);((SliderSetting)field(torch,"delay")).value=0;net.wurstclient.util.BlockUtils.obstructed=true;torch.onUpdate();check(mc.gameMode.actions.isEmpty(),"wall guard");toggle(torch,"throughWalls",true);torch.onUpdate();check(mc.gameMode.actions.size()==1&&mc.player.inventory.selected==0,"through-wall option and slot restore");
mc.gameMode.actions.clear();mc.level.light=15;torch.onUpdate();check(mc.gameMode.actions.isEmpty(),"bright spot excluded");mc.level.light=0;mc.gameMode.fail=true;try{torch.onUpdate();throw new AssertionError("expected torch fault");}catch(IllegalStateException expected){}check(mc.player.inventory.selected==0,"torch failed interaction restores slot");mc.gameMode.fail=false;
var sprint=new AutoSprintHack();sprint.setEnabled(true);mode(sprint,"sprintMode","RAGE");mc.options.keyLeft.down=true;mc.player.food.hunger=0;sprint.applySprint();check(mc.player.sprint,"rage sideways/low hunger");mc.options.keyLeft.down=false;sprint.applySprint();check(!mc.player.sprint,"stopped input releases owned sprint");toggle(sprint,"stationary",true);sprint.applySprint();check(mc.player.sprint,"stationary option");mc.player.water=true;sprint.applySprint();check(!mc.player.sprint,"water stop");mc.player.water=false;
mode(sprint,"sprintMode","STRICT");mc.options.keyUp.down=true;sprint.applySprint();check(!mc.player.sprint,"strict hunger");mc.player.food.hunger=20;sprint.applySprint();check(mc.player.sprint,"strict forward");mc.options.keySprint.down=true;sprint.setEnabled(false);check(mc.player.sprint,"manual sprint ownership retained");
System.out.println("PASS Leaves: directional/slab placement, caps/chunks/entities, exception slot restoration, torch preview/light/walls, sprint modes and manual ownership");
}}
''')
    classes=OUT/'classes';classes.mkdir(parents=True,exist_ok=True);args=OUT/'sources.txt'
    args.write_text('\n'.join('"'+p.as_posix()+'"' for p in SRC.rglob('*.java')),encoding='utf-8');results=[]
    for cmd in [['javac','-encoding','UTF-8','-d',str(classes),'@'+str(args)],['java','-cp',str(classes),'audit.LeavesProbe']]:
        run=subprocess.run(cmd,capture_output=True,timeout=60);result=f'{cmd[0]} exit={run.returncode}\n'+run.stdout.decode('utf-8',errors='replace')+run.stderr.decode('utf-8',errors='replace')
        results.append(result);(OUT/'result.txt').write_text('\n'.join(results),encoding='utf-8');print(result);assert run.returncode==0
    (OUT/'provenance.json').write_text(json.dumps(provenance,indent=2),encoding='utf-8')

if __name__=='__main__':main()
