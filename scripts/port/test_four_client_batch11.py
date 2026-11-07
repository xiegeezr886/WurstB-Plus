"""Execute unchanged module and mixin callbacks against deterministic game fixtures."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
from sync_four_client_batch import ROOT, PLAN


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--staged', action='store_true')
    args = parser.parse_args()
    out = PLAN / 'batch11/tests'
    src = out / 'src'

    def source(name, body):
        file = src / (name.replace('.', '/') + '.java')
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')

    java_root = (PLAN / 'batch11/generated/fabric' if args.staged else ROOT / 'fabric') / 'src/main/java/net/wurstclient'
    provenance = {}
    for relative in ['hacks/AutoBreedHack.java', 'hacks/CollisionsHack.java', 'mixin/CollisionsShapeMixin.java',
                     'mixin/CollisionsMovementMixin.java', 'mixin/CollisionsBorderMixin.java', 'util/RegistryIdFilter.java']:
        original = java_root / relative
        if relative.startswith('util/'):
            original = ROOT / 'fabric/src/main/java/net/wurstclient' / relative
        dest = src / 'net/wurstclient' / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, dest)
        provenance[relative] = hashlib.sha256(original.read_bytes()).hexdigest()
    simple = {
        'net.wurstclient.Category': 'public enum Category {OTHER,MOVEMENT}',
        'net.wurstclient.SearchTags': 'public @interface SearchTags {String[] value();}',
        'net.wurstclient.events.UpdateListener': 'public interface UpdateListener {void onUpdate();}',
        'net.wurstclient.events.WorldChangeListener': 'public interface WorldChangeListener {void onWorldChange(net.minecraft.client.multiplayer.ClientLevel level);}',
        'net.wurstclient.settings.CheckboxSetting': 'public class CheckboxSetting {public boolean value;public CheckboxSetting(String n,String d,boolean v){value=v;}public boolean isChecked(){return value;}}',
        'net.wurstclient.settings.EnumSetting': 'public class EnumSetting<T> {public T value;public EnumSetting(String n,String d,T[] all,T v){value=v;}public T getSelected(){return value;}}',
        'net.wurstclient.settings.TextFieldSetting': 'public class TextFieldSetting {public String value;public TextFieldSetting(String n,String d,String v){value=v;}public String getValue(){return value;}}',
        'net.wurstclient.settings.SliderSetting': 'public class SliderSetting {public double value;public SliderSetting(String n,String d,double v,double min,double max,double step,ValueDisplay display){value=v;}public int getValueI(){return (int)value;}public double getValueSq(){return value*value;}public enum ValueDisplay {DECIMAL,INTEGER;public ValueDisplay withSuffix(String suffix){return this;}}}',
        'net.wurstclient.settings.BlockListSetting': 'public class BlockListSetting {public final java.util.Set<net.minecraft.world.level.block.Block> selected=new java.util.HashSet<>();public BlockListSetting(String n,String d){}public boolean contains(net.minecraft.world.level.block.Block b){return selected.contains(b);}}',
        'net.minecraft.world.InteractionHand': 'public enum InteractionHand {MAIN_HAND,OFF_HAND}',
        'net.minecraft.world.InteractionResult': 'public enum InteractionResult {SUCCESS,PASS;public boolean consumesAction(){return this==SUCCESS;}}',
        'net.minecraft.world.item.ItemStack': 'public class ItemStack {public boolean empty;public boolean isEmpty(){return empty;}}',
        'net.minecraft.world.phys.Vec3': 'public class Vec3 {public final double x,y,z;public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}}',
        'net.minecraft.world.phys.AABB': 'public class AABB {public Vec3 getCenter(){return new Vec3(0,0,0);}}',
        'net.minecraft.world.phys.shapes.VoxelShape': 'public class VoxelShape {}',
        'net.minecraft.world.phys.shapes.Shapes': 'public class Shapes {public static final VoxelShape BLOCK=new VoxelShape(),EMPTY=new VoxelShape();public static VoxelShape block(){return BLOCK;}public static VoxelShape empty(){return EMPTY;}}',
        'net.minecraft.world.phys.shapes.CollisionContext': 'public class CollisionContext {}',
        'net.minecraft.world.level.BlockCollisions': 'public class BlockCollisions {}',
        'net.minecraft.world.level.BlockGetter': 'public interface BlockGetter {net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos);}',
        'net.minecraft.world.level.CollisionGetter': 'public interface CollisionGetter extends BlockGetter {}',
        'net.minecraft.world.level.block.Block': 'public class Block {}',
        'net.minecraft.world.level.block.Blocks': 'public class Blocks {public static final Block MAGMA_BLOCK=new Block(),AIR=new Block(),FIRE=new Block();}',
        'net.minecraft.world.level.material.FluidState': 'public record FluidState(boolean isEmpty) {}',
        'net.minecraft.world.level.border.WorldBorder': 'public class WorldBorder extends net.wurstclient.mixin.CollisionsBorderMixin {}',
        'net.minecraft.core.BlockPos': 'public record BlockPos(int x,int y,int z) {public BlockPos below(){return new BlockPos(x,y-1,z);}}',
        'net.minecraft.util.Mth': 'public class Mth {public static int floor(double value){return (int)Math.floor(value);}}',
        'net.minecraft.network.protocol.game.ServerboundMovePlayerPacket': 'public record ServerboundMovePlayerPacket(Double x,Double z) {public double getX(double fallback){return x==null?fallback:x;}public double getZ(double fallback){return z==null?fallback:z;}}',
        'net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket': 'public record ServerboundMoveVehiclePacket(double getX,double getZ) {}',
        'org.spongepowered.asm.mixin.Mixin': 'public @interface Mixin {Class<?> value();}',
        'org.spongepowered.asm.mixin.Unique': 'public @interface Unique {}',
        'org.spongepowered.asm.mixin.injection.At': 'public @interface At {String value();String target() default "";}',
        'org.spongepowered.asm.mixin.injection.ModifyVariable': 'public @interface ModifyVariable {String method();At at();boolean argsOnly();int ordinal();}',
        'org.spongepowered.asm.mixin.injection.Inject': 'public @interface Inject {String method();At at();boolean cancellable();}',
        'org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable': 'public class CallbackInfoReturnable<T> {public T value;public CallbackInfoReturnable(T v){value=v;}public void setReturnValue(T v){value=v;}}',
        'com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation': 'public @interface WrapOperation {String method();org.spongepowered.asm.mixin.injection.At at();}',
        'com.llamalad7.mixinextras.injector.wrapoperation.Operation': 'public interface Operation<T> {T call(Object... args);}',
    }
    for name, body in simple.items():
        source(name, body)
    source('net.minecraft.world.entity.EntityType', '''public record EntityType<T>(String id) {
public static final EntityType<?> COW=new EntityType<>("minecraft:cow"),MOOSHROOM=new EntityType<>("minecraft:mooshroom"),SHEEP=new EntityType<>("minecraft:sheep"),PIG=new EntityType<>("minecraft:pig"),CHICKEN=new EntityType<>("minecraft:chicken"),HORSE=new EntityType<>("minecraft:horse"),DONKEY=new EntityType<>("minecraft:donkey"),MULE=new EntityType<>("minecraft:mule"),LLAMA=new EntityType<>("minecraft:llama"),TRADER_LLAMA=new EntityType<>("minecraft:trader_llama"),WOLF=new EntityType<>("minecraft:wolf"),CAT=new EntityType<>("minecraft:cat"),OCELOT=new EntityType<>("minecraft:ocelot");}''')
    source('net.minecraft.core.registries.BuiltInRegistries', '''public class BuiltInRegistries {public static final Registry ENTITY_TYPE=new Registry();
public static class Registry {public String getKey(net.minecraft.world.entity.EntityType<?> type){return type.id();}}}''')
    source('net.minecraft.world.entity.Entity', '''public class Entity extends net.wurstclient.mixin.CollisionsMovementMixin {
public double x,y,z,xo,yo,zo,distance=1;public boolean removed;public Entity vehicle,controller;
public Entity getVehicle(){return vehicle;}public Entity getControllingPassenger(){return controller;}public boolean isRemoved(){return removed;}
public double getX(){return x;}public double getZ(){return z;}public void setPos(double a,double b,double c){x=a;y=b;z=c;}
public net.minecraft.world.phys.AABB getBoundingBox(){return new net.minecraft.world.phys.AABB();}}''')
    source('net.minecraft.world.entity.animal.Animal', '''public class Animal extends net.minecraft.world.entity.Entity {
public boolean baby,love,canLove=true,food=true;public float health=20;public net.minecraft.world.entity.EntityType<?> type=net.minecraft.world.entity.EntityType.COW;
public net.minecraft.world.item.ItemStack checkedFood;public net.minecraft.world.entity.EntityType<?> getType(){return type;}
public boolean isBaby(){return baby;}public boolean isInLove(){return love;}public boolean canFallInLove(){return canLove;}public float getHealth(){return health;}
public boolean isFood(net.minecraft.world.item.ItemStack stack){checkedFood=stack;return food&&!stack.isEmpty();}}''')
    source('net.minecraft.client.player.LocalPlayer', '''public class LocalPlayer extends net.minecraft.world.entity.Entity {
public boolean shift;public int swings;public net.minecraft.world.item.ItemStack main=new net.minecraft.world.item.ItemStack(),off=new net.minecraft.world.item.ItemStack();
public boolean isShiftKeyDown(){return shift;}public net.minecraft.world.item.ItemStack getMainHandItem(){return main;}public net.minecraft.world.item.ItemStack getOffhandItem(){return off;}
public double distanceToSqr(net.minecraft.world.entity.Entity e){return e.distance*e.distance;}public float getYRot(){return 20;}public float getXRot(){return 10;}
public void swing(net.minecraft.world.InteractionHand h){swings++;}}''')
    source('net.minecraft.world.level.block.state.BlockState', '''public class BlockState {
public net.minecraft.world.level.block.Block block;public boolean fluid;public BlockState(net.minecraft.world.level.block.Block b){block=b;}
public net.minecraft.world.level.block.Block getBlock(){return block;}public boolean is(net.minecraft.world.level.block.Block b){return b==block;}
public boolean isAir(){return is(net.minecraft.world.level.block.Blocks.AIR);}public net.minecraft.world.level.material.FluidState getFluidState(){return new net.minecraft.world.level.material.FluidState(!fluid);}}''')
    source('net.minecraft.client.multiplayer.ClientLevel', '''public class ClientLevel implements net.minecraft.world.level.CollisionGetter {
public final java.util.List<net.minecraft.world.entity.Entity> entities=new java.util.ArrayList<>();public Iterable<net.minecraft.world.entity.Entity> entitiesForRendering(){return entities;}
public final net.minecraft.world.level.border.WorldBorder border=new net.minecraft.world.level.border.WorldBorder();public net.minecraft.world.level.border.WorldBorder getWorldBorder(){return border;}
public net.minecraft.world.level.block.state.BlockState below=new net.minecraft.world.level.block.state.BlockState(net.minecraft.world.level.block.Blocks.AIR);
public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos p){return below;}
public final Chunks chunks=new Chunks();public Chunks getChunkSource(){return chunks;}public static class Chunks {public int x,z;public boolean loaded=true;public boolean hasChunk(int a,int b){x=a;z=b;return loaded;}}}''')
    source('net.minecraft.client.multiplayer.MultiPlayerGameMode', '''public class MultiPlayerGameMode {
public int calls,rotations;public boolean fail;public net.minecraft.world.entity.Entity target;public net.minecraft.world.InteractionHand hand;
public net.minecraft.world.InteractionResult result=net.minecraft.world.InteractionResult.SUCCESS;
public net.minecraft.world.InteractionResult interact(net.minecraft.client.player.LocalPlayer p,net.minecraft.world.entity.Entity e,net.minecraft.world.InteractionHand h){calls++;target=e;hand=h;rotations=net.wurstclient.util.Rotation.sends;if(fail)throw new IllegalStateException("fixture");return result;}}''')
    source('net.minecraft.client.Minecraft', '''public class Minecraft {
public net.minecraft.client.player.LocalPlayer player=new net.minecraft.client.player.LocalPlayer();public net.minecraft.client.multiplayer.ClientLevel level=new net.minecraft.client.multiplayer.ClientLevel();
public net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode=new net.minecraft.client.multiplayer.MultiPlayerGameMode();public Object getConnection(){return player==null?null:this;}}''')
    source('net.wurstclient.WurstClient', '''public class WurstClient {
public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();public static final WurstClient INSTANCE=new WurstClient();
public final Hacks hax=new Hacks();public Hacks getHax(){return hax;}public static class Hacks {public net.wurstclient.hacks.CollisionsHack collisionsHack;}}''')
    source('net.wurstclient.hack.Hack', '''public class Hack {
protected static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;protected static final Events EVENTS=new Events();private boolean enabled;
public Hack(String n){}protected void setCategory(Object o){}protected void addSetting(Object o){}protected void onEnable(){}protected void onDisable(){}
public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;if(v)onEnable();else onDisable();}
public static class Events {public void add(Object a,Object b){}public void remove(Object a,Object b){}}}''')
    source('net.wurstclient.events.PacketOutputListener', '''public interface PacketOutputListener {
void onSentPacket(PacketOutputEvent event);public static class PacketOutputEvent {public final Object packet;public boolean cancelled;
public PacketOutputEvent(Object p){packet=p;}public Object getPacket(){return packet;}public void cancel(){cancelled=true;}}}''')
    source('net.wurstclient.util.Rotation', '''public class Rotation {
public static int sends;public static float lastYaw,lastPitch;private final float yaw,pitch;public Rotation(float y,float p){yaw=y;pitch=p;}
public void sendPlayerLookPacket(){sends++;lastYaw=yaw;lastPitch=pitch;}}''')
    source('net.wurstclient.util.RotationUtils', 'public class RotationUtils {public static Rotation getNeededRotations(net.minecraft.world.phys.Vec3 v){return new Rotation(42,32);}}')
    source('audit.Batch11Probe', '''
import java.lang.reflect.*;import net.wurstclient.WurstClient;import net.wurstclient.hacks.*;import net.wurstclient.settings.*;
import net.minecraft.world.entity.*;import net.minecraft.world.entity.animal.Animal;import net.minecraft.world.phys.*;import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;import net.minecraft.core.BlockPos;
import net.wurstclient.events.PacketOutputListener.PacketOutputEvent;import net.minecraft.network.protocol.game.*;
import net.wurstclient.mixin.*;import net.wurstclient.util.Rotation;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
public class Batch11Probe {
static final net.minecraft.client.Minecraft mc=WurstClient.MC;
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
static void bool(Object o,String n,boolean v)throws Exception{((CheckboxSetting)field(o,n)).value=v;}
@SuppressWarnings({"unchecked","rawtypes"}) static void mode(Object o,String n,String v)throws Exception{EnumSetting e=(EnumSetting)field(o,n);e.value=Enum.valueOf(((Enum)e.value).getDeclaringClass(),v);}
static Object call(Object o,Class<?> type,String name,Object... args)throws Exception{Method m=java.util.Arrays.stream(type.getDeclaredMethods()).filter(x->x.getName().equals(name)).findFirst().orElseThrow();m.setAccessible(true);return m.invoke(o,args);}
static void reset(){mc.player=new net.minecraft.client.player.LocalPlayer();mc.level=new net.minecraft.client.multiplayer.ClientLevel();mc.gameMode=new net.minecraft.client.multiplayer.MultiPlayerGameMode();Rotation.sends=0;}
static Animal animal(){Animal a=new Animal();mc.level.entities.add(a);return a;}
static void feeding()throws Exception{
reset();AutoBreedHack h=new AutoBreedHack();h.setEnabled(true);Animal a=animal();a.baby=true;a.canLove=false;h.onUpdate();check(mc.gameMode.calls==0,"native adult filter");
mode(h,"ageFilter","BABY");mode(h,"feedMode","ONCE");mode(h,"hand","OFF_HAND");h.onUpdate();check(mc.gameMode.calls==1&&mc.gameMode.hand==net.minecraft.world.InteractionHand.OFF_HAND&&a.checkedFood==mc.player.off,"baby offhand feeding");
check(mc.gameMode.rotations==1&&Rotation.sends==2&&Rotation.lastYaw==20&&Rotation.lastPitch==10,"look packet precedes interaction and is restored");h.onUpdate();check(mc.gameMode.calls==1,"once prevents repeated feeding");
Animal b=animal();b.baby=true;b.type=EntityType.SHEEP;((TextFieldSetting)field(h,"entities")).value="minecraft:sheep";h.onUpdate();check(mc.gameMode.calls==2&&mc.gameMode.target==b,"exact selected animal and no global success delay");
h.onWorldChange(mc.level);((TextFieldSetting)field(h,"entities")).value="";h.onUpdate();check(mc.gameMode.calls==3,"world change clears feed history");
h.setEnabled(false);h.setEnabled(true);mode(h,"feedMode","REPEAT");((SliderSetting)field(h,"breedingInterval")).value=3;mc.level.entities.remove(b);int n=mc.gameMode.calls;h.onUpdate();h.onUpdate();h.onUpdate();check(mc.gameMode.calls==n+1,"repeat interval retained");h.onUpdate();check(mc.gameMode.calls==n+2,"repeat interval boundary");
h.onWorldChange(mc.level);mc.gameMode.result=net.minecraft.world.InteractionResult.PASS;n=mc.gameMode.calls;h.onUpdate();check(((java.util.Map<?,?>)field(h,"animalsFed")).isEmpty(),"rejected interaction not remembered");h.onUpdate();check(mc.gameMode.calls==n+1,"rejection throttled");
h.onWorldChange(mc.level);mc.gameMode.fail=true;int rotations=Rotation.sends;try{h.onUpdate();throw new AssertionError("expected interaction exception");}catch(IllegalStateException expected){}check(Rotation.sends==rotations+2&&Rotation.lastYaw==20,"rotation restored on interaction exception");
mc.gameMode.fail=false;mc.player=null;h.onUpdate();h.setEnabled(false);check(((java.util.Map<?,?>)field(h,"animalsFed")).isEmpty(),"disconnect safe cleanup");}
static void collisions()throws Exception{
reset();CollisionsHack h=new CollisionsHack();WurstClient.INSTANCE.hax.collisionsHack=h;h.setEnabled(true);BlockState fire=new BlockState(Blocks.FIRE);BlockPos pos=new BlockPos(0,64,0);VoxelShape original=new VoxelShape();
check(h.collisionShape(fire,mc.level,pos,original)==original,"default shape preserved");((BlockListSetting)field(h,"blocks")).selected.add(Blocks.FIRE);check(h.collisionShape(fire,mc.level,pos,original)==Shapes.block(),"selected shape");
fire.fluid=true;check(h.collisionShape(fire,mc.level,pos,original)==original,"fluid exclusion");fire.fluid=false;check(h.collisionShape(fire,new net.minecraft.client.multiplayer.ClientLevel(),pos,original)==original,"foreign world unchanged");
bool(h,"magma",true);mc.level.below=new BlockState(Blocks.MAGMA_BLOCK);check(h.collisionShape(new BlockState(Blocks.AIR),mc.level,pos,original)==Shapes.block(),"air above magma blocked");mc.player.shift=true;check(h.collisionShape(new BlockState(Blocks.AIR),mc.level,pos,original)==original,"sneak bypass");
bool(h,"unloadedChunks",true);mc.level.chunks.loaded=false;mc.player.x=.01;Vec3 movement=new Vec3(-.02,-.3,0);Vec3 limited=(Vec3)call(mc.player,CollisionsMovementMixin.class,"wurstLimitUnloadedMovement",movement);check(limited.x==0&&limited.y==-.3&&mc.level.chunks.x==-1,"actual movement callback negative fractional chunk");
Entity other=new Entity();check(h.limitMovement(other,movement)==movement,"remote entities unaffected");Entity vehicle=new Entity();vehicle.controller=mc.player;mc.player.vehicle=vehicle;check(h.limitMovement(vehicle,movement).x==0,"controlled vehicle blocked");vehicle.controller=other;check(h.limitMovement(vehicle,movement)==movement,"uncontrolled passenger unaffected");
PacketOutputEvent packet=new PacketOutputEvent(new ServerboundMovePlayerPacket(-.01,-16.01));h.onSentPacket(packet);check(packet.cancelled&&mc.level.chunks.x==-1&&mc.level.chunks.z==-2,"player packet negative chunk floors");
vehicle.xo=4;vehicle.yo=65;vehicle.zo=2;packet=new PacketOutputEvent(new ServerboundMoveVehiclePacket(-.01,0));h.onSentPacket(packet);check(packet.cancelled&&vehicle.x==4&&vehicle.y==65&&vehicle.z==2,"vehicle packet recovery");mc.player.vehicle=null;packet=new PacketOutputEvent(new ServerboundMoveVehiclePacket(-.01,0));h.onSentPacket(packet);check(!packet.cancelled,"dismount race is safe");
mc.level.chunks.loaded=true;check(h.limitMovement(mc.player,movement)==movement,"loaded movement identity");bool(h,"ignoreBorder",true);check(h.ignoresBorder(mc.level.border)&&!h.ignoresBorder(new net.minecraft.world.level.border.WorldBorder()),"client border only");
CallbackInfoReturnable<Boolean> inside=new CallbackInfoReturnable<>(true);call(mc.level.border,CollisionsBorderMixin.class,"wurstCanCollide",inside);check(!inside.value,"actual border collision callback");CallbackInfoReturnable<Boolean> contains=new CallbackInfoReturnable<>(false);call(mc.level.border,CollisionsBorderMixin.class,"wurstContains",contains);check(contains.value,"actual border bounds callback");CallbackInfoReturnable<VoxelShape> shape=new CallbackInfoReturnable<>(original);call(mc.level.border,CollisionsBorderMixin.class,"wurstBorderShape",shape);check(shape.value==Shapes.empty(),"actual border shape callback");
CollisionsShapeMixin hook=new CollisionsShapeMixin(){};com.llamalad7.mixinextras.injector.wrapoperation.Operation<VoxelShape> op=(Object... values)->original;
check(call(hook,CollisionsShapeMixin.class,"wurstCollisionShape",fire,mc.level,pos,new CollisionContext(),op)==Shapes.block(),"actual shape callback");h.setEnabled(false);check(h.collisionShape(fire,mc.level,pos,original)==original&&!h.ignoresBorder(mc.level.border)&&h.limitMovement(mc.player,movement)==movement,"disabled behavior preserved");
mc.player=null;h.onSentPacket(packet);check(h.collisionShape(fire,mc.level,pos,original)==original,"null player safe");}
public static void main(String[] args)throws Exception{feeding();collisions();System.out.println("PASS: feeding selection/history/interval/rotation and collision shapes/chunks/vehicles/borders with actual callbacks");}}
''')
    java = Path.home() / '.jdks/jdk25.0.4_10/bin'
    argfile = out / 'javac.args'
    argfile.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in src.rglob('*.java')), encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out / 'classes'), '@' + str(argfile)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(out / 'classes'), 'audit.Batch11Probe'], check=True)
    (out / 'provenance.json').write_text(json.dumps({'staged': args.staged, 'sha256': provenance}, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
