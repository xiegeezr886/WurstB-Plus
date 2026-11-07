"""Execute unchanged native module bodies with deterministic game boundaries."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.planning/2026-11-07-four-client-v151-port/batch1-tests'
SRC = OUT / 'src'

def source(name, body):
    path = SRC / (name.replace('.', '/') + '.java')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')

def main():
    provenance = {}
    for name in ['hacks/SlippyHack.java', 'hacks/EndermanLookHack.java',
                 'hacks/BuildHeightHack.java', 'util/SoundIdFilter.java', 'util/RegistryIdFilter.java']:
        original = ROOT / 'fabric/src/main/java/net/wurstclient' / name
        copied = SRC / 'net/wurstclient' / name
        copied.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, copied)
        provenance[name] = hashlib.sha256(original.read_bytes()).hexdigest()
    source('net.wurstclient.Category', 'public enum Category {MOVEMENT,COMBAT,BLOCKS}')
    source('net.wurstclient.SearchTags', 'public @interface SearchTags {String[] value();}')
    source('net.wurstclient.events.UpdateListener', 'public interface UpdateListener {void onUpdate();}')
    source('net.wurstclient.events.PacketOutputListener', '''public interface PacketOutputListener {
void onSentPacket(PacketOutputEvent e);
class PacketOutputEvent {Object packet; public PacketOutputEvent(Object p){packet=p;}
public Object getPacket(){return packet;} public void setPacket(Object p){packet=p;}}
}''')
    source('net.wurstclient.settings.EnumSetting', '''public class EnumSetting<T> {
public T value; public final T[] values;
public EnumSetting(String n,String d,T[] v,T x){values=v;value=x;} public T getSelected(){return value;}
public void setIndex(int i){value=values[i];}}''')
    source('net.wurstclient.settings.CheckboxSetting', '''public class CheckboxSetting {
public boolean value; public CheckboxSetting(String n,String d,boolean v){value=v;} public boolean isChecked(){return value;}}''')
    source('net.wurstclient.settings.SliderSetting', '''public class SliderSetting {
public double value; public SliderSetting(String n,String d,double v,double min,double max,double step,ValueDisplay f){value=v;}
public float getValueF(){return (float)value;} public enum ValueDisplay {DECIMAL}}''')
    source('net.wurstclient.settings.BlockListSetting', '''import net.minecraft.world.level.block.Block;
public class BlockListSetting {public final java.util.Set<Block> blocks=new java.util.HashSet<>();
public BlockListSetting(String n,String d,String... names){} public boolean contains(Block b){return blocks.contains(b);}}''')
    source('net.minecraft.world.level.block.Block', 'public class Block {}')
    source('net.minecraft.core.Direction', 'public enum Direction {UP,DOWN,NORTH}')
    source('net.minecraft.world.entity.EquipmentSlot', 'public enum EquipmentSlot {HEAD}')
    source('net.minecraft.world.item.Items', 'public class Items {public static final Object CARVED_PUMPKIN=new Object();}')
    source('net.minecraft.world.item.ItemStack', '''public class ItemStack {
public Object item; public boolean is(Object x){return item==x;}}''')
    source('net.minecraft.world.phys.Vec3', '''public class Vec3 {
public final double x,y,z; public Vec3(double a,double b,double c){x=a;y=b;z=c;}
public double length(){return Math.sqrt(x*x+y*y+z*z);} public Vec3 normalize(){double n=length();return n==0?this:new Vec3(x/n,y/n,z/n);}
public double dot(Vec3 p){return x*p.x+y*p.y+z*p.z;} public Vec3 subtract(Vec3 p){return new Vec3(x-p.x,y-p.y,z-p.z);}}''')
    source('net.minecraft.world.phys.BlockHitResult', '''import net.minecraft.core.Direction;
public record BlockHitResult(Vec3 location,Direction direction) {
public Vec3 getLocation(){return location;} public Direction getDirection(){return direction;}
public BlockHitResult withDirection(Direction d){return new BlockHitResult(location,d);}}''')
    source('net.minecraft.network.protocol.game.ServerboundUseItemOnPacket', '''import net.minecraft.world.phys.BlockHitResult;
public record ServerboundUseItemOnPacket(Object hand,BlockHitResult hit,int sequence) {
public Object getHand(){return hand;} public BlockHitResult getHitResult(){return hit;} public int getSequence(){return sequence;}}''')
    source('net.minecraft.world.entity.Entity', '''import net.minecraft.world.phys.Vec3;
public class Entity {public Vec3 eyes=new Vec3(0,0,0);public boolean alive=true;
public boolean isAlive(){return alive;} public Vec3 getEyePosition(){return eyes;}
public double getX(){return eyes.x;} public double getEyeY(){return eyes.y;} public double getZ(){return eyes.z;}}''')
    source('net.minecraft.world.entity.monster.EnderMan', '''public class EnderMan extends net.minecraft.world.entity.Entity {
public boolean angry;public boolean isCreepy(){return angry;}}''')
    source('net.wurstclient.util.Rotation', 'public record Rotation(float yaw,float pitch) {}')
    source('net.wurstclient.util.RotationQueue', '''public class RotationQueue {
public Rotation last; public int requests;public RotationQueue(Priority p){}
public void clear(){last=null;requests=0;} public void stop(){clear();}
public void setRotation(Rotation r){last=r;requests++;}public enum Priority {BACKGROUND}}''')
    source('net.wurstclient.util.RotationUtils', '''public class RotationUtils {
public static Rotation getNeededRotations(net.minecraft.world.phys.Vec3 p){return new Rotation(12,34);}}''')
    source('net.wurstclient.hack.Hack', '''import net.minecraft.world.entity.*;
import net.minecraft.world.item.*; import net.minecraft.world.phys.Vec3;
public class Hack {
public static final Host MC=new Host();public static final Events EVENTS=new Events();
public boolean enabled;public Hack(String n){} public boolean isEnabled(){return enabled;}
protected void onEnable(){}protected void onDisable(){} protected void addSetting(Object s){} protected void setCategory(Object c){}
public static class Events {public void add(Class<?> c,Object x){}public void remove(Class<?> c,Object x){}}
public static class Host {public Player player=new Player();public Level level=new Level();}
public static class Level {public int min=-64,height=384;public final java.util.List<Entity> entities=new java.util.ArrayList<>();
public Iterable<Entity> entitiesForRendering(){return entities;}public int getMinBuildHeight(){return min;}public int getHeight(){return height;}}
public static class Player extends Entity {public final Abilities abilities=new Abilities();public final ItemStack helmet=new ItemStack();
public Vec3 view=new Vec3(0,0,1);public boolean visible=true;
public Abilities getAbilities(){return abilities;} public ItemStack getItemBySlot(EquipmentSlot s){return helmet;}
public boolean hasLineOfSight(Entity e){return visible;}public Vec3 getViewVector(float f){return view;} public float getYRot(){return 0;}}
public static class Abilities {public boolean instabuild;}
}''')
    source('audit.BatchProbe', '''import java.lang.reflect.*;
import net.wurstclient.hacks.*;import net.wurstclient.hack.Hack;import net.wurstclient.settings.*;
import net.wurstclient.util.*;import net.wurstclient.events.PacketOutputListener.PacketOutputEvent;
import net.minecraft.world.level.block.Block;import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.*;import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
public class BatchProbe {
static void check(boolean x,String why){if(!x)throw new AssertionError(why);}
static Object field(Object x,String n)throws Exception{Field f=x.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(x);}
public static void main(String[] args)throws Exception {
 SoundIdFilter sounds=new SoundIdFilter();
 check(sounds.contains("entity.cat.ambient, mod:bell", "minecraft:entity.cat.ambient"),"default namespace");
 check(sounds.contains("entity.cat.ambient, mod:bell", "mod:bell"),"custom namespace");
 check(!sounds.contains("entity.cat.ambient, mod:bell", "minecraft:bell"),"namespace isolation");
 check(!sounds.contains("", "mod:bell"),"cleared settings refresh cache");
 check(!sounds.contains("entity.cat.ambient", "minecraft:entity.cat.ambient.extra"),"no prefix match");
 System.out.println("PASS sound IDs: namespace, exact match, edited/cleared settings");
 SlippyHack slippy=new SlippyHack();Block stone=new Block(),ice=new Block();
 check(slippy.getFriction(stone,.6f)==.6f,"disabled original");slippy.enabled=true;
 check(slippy.getFriction(stone,.6f)==1,"blacklist empty");
 ((BlockListSetting)field(slippy,"ignoredBlocks")).blocks.add(ice);
 check(slippy.getFriction(ice,.98f)==.98f,"blacklisted ice");
 ((EnumSetting<?>)field(slippy,"listMode")).setIndex(0);
 check(slippy.getFriction(stone,.6f)==.6f,"empty whitelist");
 ((BlockListSetting)field(slippy,"allowedBlocks")).blocks.add(stone);
 ((SliderSetting)field(slippy,"friction")).value=.25;
 check(slippy.getFriction(stone,.6f)==.25f,"whitelist custom friction");
 System.out.println("PASS Slippy: disabled, both list modes, original and custom coefficients");
 BuildHeightHack height=new BuildHeightHack();Object hand=new Object();
 for(int min:new int[]{-64,0,96}) {
  Hack.MC.level.min=min;Hack.MC.level.height=384;int top=min+384;
  for(Direction side:Direction.values())for(int y:new int[]{top-1,top,top+1}) {
   Vec3 location=new Vec3(1,y,2);BlockHitResult hit=new BlockHitResult(location,side);
   var packet=new ServerboundUseItemOnPacket(hand,hit,123);var event=new PacketOutputEvent(packet);
   height.onSentPacket(event);var out=(ServerboundUseItemOnPacket)event.getPacket();
   boolean changed=y>=top&&side==Direction.UP;
   check((out!=packet)==changed,"height and face boundary");
   check(out.getHand()==hand&&out.getSequence()==123&&out.getHitResult().getLocation()==location,"packet metadata");
   check(hit.getDirection()==side,"original hit unchanged");
  }
 }
 var saved=Hack.MC.level;Hack.MC.level=null;var unrelated=new PacketOutputEvent("other");height.onSentPacket(unrelated);
 check(unrelated.getPacket().equals("other"),"no-world safe");Hack.MC.level=saved;
 System.out.println("PASS BuildHeight: dimension minima, face/height boundaries, packet metadata, no world");
 EndermanLookHack look=new EndermanLookHack();EnderMan ender=new EnderMan();ender.eyes=new Vec3(0,0,10);
 Hack.MC.level.entities.add(ender);RotationQueue q=(RotationQueue)field(look,"rotations");
 look.onUpdate();check(q.last!=null&&q.last.pitch()==90,"look away");
 Hack.MC.player.view=new Vec3(1,0,0);look.onUpdate();check(q.last==null,"not looking");
 ender.angry=true;look.onUpdate();check(q.last!=null&&q.last.pitch()==34,"stun hostile");
 ((CheckboxSetting)field(look,"stun")).value=false;look.onUpdate();check(q.last==null,"stun disabled");
 ((EnumSetting<?>)field(look,"lookMode")).setIndex(0);ender.angry=false;look.onUpdate();check(q.last!=null,"at passive");
 ender.angry=true;look.onUpdate();check(q.last==null,"at skips hostile");ender.angry=false;
 Hack.MC.player.visible=false;look.onUpdate();check(q.last==null,"occlusion");Hack.MC.player.visible=true;
 Hack.MC.player.abilities.instabuild=true;look.onUpdate();check(q.last==null,"creative immunity");Hack.MC.player.abilities.instabuild=false;
 Hack.MC.player.helmet.item=net.minecraft.world.item.Items.CARVED_PUMPKIN;look.onUpdate();check(q.last==null,"pumpkin immunity");
 Hack.MC.player.helmet.item=null;ender.alive=false;look.onUpdate();check(q.last==null,"dead target");
 Hack.MC.player=null;look.onUpdate();check(q.last==null,"no player clears pending rotation");
 System.out.println("PASS EndermanLook: at/away, hostility/stun, sight, immunity, invalid targets");
}
}''')
    classes = OUT / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    argsfile = OUT / 'sources.txt'
    argsfile.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in SRC.rglob('*.java')), encoding='utf-8')
    results = []
    for command in [['javac', '-encoding', 'UTF-8', '-d', str(classes), '@' + str(argsfile)],
                    ['java', '-cp', str(classes), 'audit.BatchProbe']]:
        run = subprocess.run(command, capture_output=True, timeout=60)
        result = f'{command[0]} exit={run.returncode}\n' + run.stdout.decode('utf-8', errors='replace') + run.stderr.decode('utf-8', errors='replace')
        results.append(result)
        print(result)
        (OUT / 'result.txt').write_text('\n'.join(results), encoding='utf-8')
        assert run.returncode == 0
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2), encoding='utf-8')

if __name__ == '__main__':
    main()
