"""Headless execution of the actual reconnect and selected-sapling farm classes."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.planning/2026-11-07-four-client-v151-port/batch2/lifecycle-tests'
SRC = OUT / 'src'

def source(name, body):
    path = SRC / (name.replace('.', '/') + '.java')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')

def main():
    provenance = {}
    for name in ['hacks/AutoReconnectHack.java', 'hacks/treebot/SaplingFarm.java']:
        original = ROOT / 'fabric/src/main/java/net/wurstclient' / name
        copied = SRC / 'net/wurstclient' / name
        copied.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, copied)
        provenance[name] = hashlib.sha256(original.read_bytes()).hexdigest()
    source('net.wurstclient.Category', 'public enum Category {OTHER}')
    source('net.wurstclient.DontBlock', 'public @interface DontBlock {}')
    source('net.wurstclient.SearchTags', 'public @interface SearchTags {String[] value();}')
    source('net.wurstclient.events.WorldChangeListener', 'public interface WorldChangeListener {void onWorldChange(net.minecraft.client.multiplayer.ClientLevel world);}')
    source('net.wurstclient.settings.Setting', 'public class Setting {}')
    source('net.wurstclient.settings.CheckboxSetting', '''public class CheckboxSetting extends Setting {
public boolean value;public CheckboxSetting(String n,boolean v){value=v;} public boolean isChecked(){return value;}}''')
    source('net.wurstclient.settings.SliderSetting', '''public class SliderSetting extends Setting {
public double value;public SliderSetting(String n,String d,double v,double a,double b,double s,ValueDisplay f){value=v;}
public SliderSetting(String n,double v,double a,double b,double s,ValueDisplay f){value=v;}
public int getValueI(){return (int)value;}public double getValue(){return value;}
public enum ValueDisplay {INTEGER,DECIMAL;public ValueDisplay withSuffix(String s){return this;}}}''')
    source('net.wurstclient.settings.EnumSetting', '''public class EnumSetting<T> extends Setting {
public T value;public EnumSetting(String n,String d,T[] values,T v){value=v;} public T getSelected(){return value;}}''')
    source('net.wurstclient.settings.ColorSetting', '''public class ColorSetting extends Setting {
public ColorSetting(String n,java.awt.Color c){} public int getColorI(){return 0xFFFFFF;}}''')
    source('net.wurstclient.settings.SwingHandSetting', '''public class SwingHandSetting {
public enum SwingHand {CLIENT;public void swing(net.minecraft.world.InteractionHand h){}}}''')
    source('net.wurstclient.hack.Hack', '''public class Hack {
public static final Events EVENTS=new Events();private boolean enabled;
public Hack(String n){} protected void setCategory(Object c){}protected void addSetting(Object s){}
public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){if(enabled==v)return;enabled=v;if(!v)onDisable();}
protected void onDisable(){}
public static class Events {public final java.util.Set<Object> listeners=new java.util.HashSet<>();
public void add(Class<?> c,Object o){listeners.add(o);}public void remove(Class<?> c,Object o){listeners.remove(o);}}}''')
    source('com.mojang.blaze3d.vertex.PoseStack', 'public class PoseStack {}')
    source('net.minecraft.core.Direction', 'public enum Direction {UP}')
    source('net.minecraft.core.BlockPos', '''public record BlockPos(int x,int y,int z) {
public BlockPos immutable(){return this;}public BlockPos above(){return new BlockPos(x,y+1,z);}}''')
    source('net.minecraft.world.InteractionHand', 'public enum InteractionHand {MAIN_HAND}')
    source('net.minecraft.world.phys.Vec3', '''public record Vec3(double x,double y,double z) {
public static Vec3 atCenterOf(net.minecraft.core.BlockPos p){return new Vec3(p.x()+.5,p.y()+.5,p.z()+.5);}
public Vec3 add(double a,double b,double c){return new Vec3(x+a,y+b,z+c);}
public double distanceToSqr(Vec3 p){return (x-p.x)*(x-p.x)+(y-p.y)*(y-p.y)+(z-p.z)*(z-p.z);}}''')
    source('net.minecraft.world.phys.HitResult', 'public class HitResult {public enum Type {BLOCK,MISS}}')
    source('net.minecraft.world.phys.BlockHitResult', '''import net.minecraft.core.*;
public class BlockHitResult extends HitResult {private final BlockPos pos;
public BlockHitResult(Vec3 p,Direction d,BlockPos b,boolean i){pos=b;}
public BlockPos getBlockPos(){return pos;}public Type getType(){return Type.BLOCK;}}''')
    source('net.minecraft.world.phys.AABB', 'public record AABB(net.minecraft.core.BlockPos pos) {}')
    source('net.minecraft.world.level.block.Block', 'public class Block {}')
    source('net.minecraft.world.level.block.SaplingBlock', 'public class SaplingBlock extends Block {}')
    source('net.minecraft.world.level.block.state.BlockState', '''public class BlockState {
public net.minecraft.world.level.block.Block block;public boolean replaceable;public float hardness=1;
public BlockState(net.minecraft.world.level.block.Block b,boolean r){block=b;replaceable=r;}
public net.minecraft.world.level.block.Block getBlock(){return block;}public boolean canBeReplaced(){return replaceable;}
public float getDestroySpeed(Object l,Object p){return hardness;}}''')
    source('net.minecraft.world.item.Item', 'public class Item {}')
    source('net.minecraft.world.item.BlockItem', '''public class BlockItem extends Item {
private final net.minecraft.world.level.block.Block block;
public BlockItem(net.minecraft.world.level.block.Block b){block=b;}public net.minecraft.world.level.block.Block getBlock(){return block;}}''')
    source('net.minecraft.world.item.Items', 'public class Items {public static final Item BONE_MEAL=new Item();}')
    source('net.minecraft.world.item.ItemStack', '''public class ItemStack {
private final Item item;public ItemStack(Item i){item=i;} public Item getItem(){return item;}public boolean is(Item i){return item==i;}}''')
    source('net.minecraft.client.multiplayer.ClientLevel', '''import net.minecraft.core.BlockPos;import net.minecraft.world.level.block.state.BlockState;
public class ClientLevel {public final java.util.Map<BlockPos,BlockState> blocks=new java.util.HashMap<>();
public boolean loaded=true;public boolean hasChunkAt(BlockPos p){return loaded;}
public BlockState getBlockState(BlockPos p){return blocks.getOrDefault(p,new BlockState(new net.minecraft.world.level.block.Block(),true));}}''')
    source('net.minecraft.client.Minecraft', '''import net.minecraft.world.item.*;import net.minecraft.world.phys.*;
public class Minecraft {public Player player=new Player();public net.minecraft.client.multiplayer.ClientLevel level=new net.minecraft.client.multiplayer.ClientLevel();
public GameMode gameMode=new GameMode();public Object screen;public HitResult hitResult;
public static class Player {public final Inventory inventory=new Inventory();public Vec3 eyes=new Vec3(.5,2,.5);
public Inventory getInventory(){return inventory;}public ItemStack getMainHandItem(){return inventory.getItem(inventory.selected);}
public Vec3 getEyePosition(){return eyes;}}
public static class Inventory {public int selected;public ItemStack[] items=new ItemStack[9];
public Inventory(){java.util.Arrays.fill(items,new ItemStack(new Item()));}public ItemStack getItem(int i){return items[i];}}
public static class GameMode {public final java.util.List<String> actions=new java.util.ArrayList<>();public boolean fail;
public void useItemOn(Player p,Object hand,BlockHitResult hit){if(fail)throw new IllegalStateException("synthetic");
actions.add(hit.getBlockPos()+":"+p.inventory.selected);}}}''')
    source('net.wurstclient.WurstClient', '''public class WurstClient {
public static final WurstClient INSTANCE=new WurstClient();public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();
public RotationFaker getRotationFaker(){return new RotationFaker();}public static class RotationFaker {public void faceVectorPacket(Object p){}}}''')
    source('net.wurstclient.util.RenderUtils', '''public class RenderUtils {
public static int boxes;public static void drawSolidBox(Object m,Object b,int c,boolean d){boxes++;}
public static void drawOutlinedBox(Object m,Object b,int c,boolean d){boxes++;}}''')
    source('audit.LifecycleProbe', '''import java.lang.reflect.*;import net.wurstclient.*;import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.AutoReconnectHack;import net.wurstclient.hacks.treebot.SaplingFarm;
import net.wurstclient.settings.*;import net.minecraft.core.*;import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.BlockState;import net.minecraft.world.phys.*;
public class LifecycleProbe {
static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
static Object field(Object x,String n)throws Exception{Field f=x.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(x);}
static void select(SaplingFarm f,BlockPos pos){WurstClient.MC.hitResult=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);check(f.select(),"select");}
public static void main(String[] args)throws Exception {
 AutoReconnectHack a=new AutoReconnectHack();check(!a.isEnabled()&&a.getWaitTicks()==100,"initial reconnect");
 a.requestOnce(60);check(a.isEnabled()&&a.getWaitTicks()==1200,"temporary reconnect");
 a.onWorldChange(null);check(a.getWaitTicks()==1200,"disconnect preserves request");
 a.onWorldChange(new net.minecraft.client.multiplayer.ClientLevel());check(!a.isEnabled()&&a.getWaitTicks()==100,"join restores prior disabled/default delay");
 check(!Hack.EVENTS.listeners.contains(a),"listener removed");
 a.setEnabled(true);a.requestOnce(10);a.requestOnce(30);a.onWorldChange(new net.minecraft.client.multiplayer.ClientLevel());
 check(a.isEnabled()&&a.getWaitTicks()==100,"repeated request retains originally enabled state");
 a.requestOnce(30);a.setEnabled(false);check(a.getWaitTicks()==100&&!Hack.EVENTS.listeners.contains(a),"manual cancellation");
 System.out.println("PASS reconnect: temporary delay, leave/join, repeated request, manual cancel, original setting/state preserved");
 SaplingFarm farm=new SaplingFarm();farm.reset();((SliderSetting)field(farm,"delay")).value=0;
 var mc=WurstClient.MC;BlockPos base=new BlockPos(0,0,0),second=new BlockPos(1,0,0);
 var sapling=new SaplingBlock();mc.player.inventory.items[0]=new ItemStack(new BlockItem(sapling));
 mc.player.inventory.items[1]=new ItemStack(Items.BONE_MEAL);
 select(farm,base);farm.tick(5);check(mc.gameMode.actions.size()==1&&mc.gameMode.actions.get(0).endsWith(":0"),"plant with held sapling");
 mc.level.blocks.put(base.above(),new BlockState(sapling,false));farm.tick(5);
 check(mc.gameMode.actions.get(1).endsWith(":1")&&mc.player.inventory.selected==0,"bone meal and slot restore");
 select(farm,second);mc.gameMode.actions.clear();farm.tick(5);check(mc.gameMode.actions.size()==1,"per tick cap");
 ((SliderSetting)field(farm,"blocksPerTick")).value=2;mc.gameMode.actions.clear();farm.tick(5);check(mc.gameMode.actions.size()==2,"two targets");
 select(farm,second);select(farm,base);mc.gameMode.actions.clear();farm.tick(5);check(mc.gameMode.actions.isEmpty(),"selection toggles off");
 select(farm,base);mc.player.inventory.items[1]=new ItemStack(new Item());farm.tick(5);check(mc.gameMode.actions.isEmpty(),"missing bone meal");
 mc.player.inventory.items[1]=new ItemStack(Items.BONE_MEAL);mc.screen=new Object();farm.tick(5);check(mc.gameMode.actions.isEmpty(),"menu pause");mc.screen=null;
 mc.level.loaded=false;farm.tick(5);check(mc.gameMode.actions.isEmpty(),"unloaded target skipped");mc.level.loaded=true;
 mc.player.eyes=new Vec3(.5,3,.5);farm.tick(.1);check(mc.gameMode.actions.isEmpty(),"reach check");mc.player.eyes=new Vec3(.5,2,.5);
 mc.gameMode.fail=true;try{farm.tick(5);throw new AssertionError("expected fault");}catch(IllegalStateException expected){}
 check(mc.player.inventory.selected==0,"slot restored on failed interaction");mc.gameMode.fail=false;
 mc.level=new net.minecraft.client.multiplayer.ClientLevel();farm.tick(5);check(mc.gameMode.actions.isEmpty(),"world change clears selections");
 System.out.println("PASS Leaves tree farm: selection, planting, bone meal, action cap, reach/menu/chunk guards, slot restoration, world isolation");
}
}''')
    classes = OUT / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    args = OUT / 'sources.txt'
    args.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in SRC.rglob('*.java')), encoding='utf-8')
    results = []
    for command in [['javac', '-encoding', 'UTF-8', '-d', str(classes), '@' + str(args)],
                    ['java', '-cp', str(classes), 'audit.LifecycleProbe']]:
        run = subprocess.run(command, capture_output=True, timeout=60)
        result = f'{command[0]} exit={run.returncode}\n' + run.stdout.decode('utf-8', errors='replace') + run.stderr.decode('utf-8', errors='replace')
        results.append(result)
        print(result)
        (OUT / 'result.txt').write_text('\n'.join(results), encoding='utf-8')
        assert run.returncode == 0
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2), encoding='utf-8')

if __name__ == '__main__':
    main()
