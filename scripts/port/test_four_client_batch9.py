"""Exercise unchanged native targeting and mount code plus beacon callbacks."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / '.planning/2026-11-07-four-client-v151-port'
OUT = PLAN / 'batch9/tests'
SRC = OUT / 'src'


def source(name, body):
    file = SRC / (name.replace('.', '/') + '.java')
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')


def main():
    provenance = {}
    for name in ['hacks/AutoMountHack.java', 'hacks/NoMiningTraceHack.java', 'hacks/GhostHandHack.java', 'mixin/BetterBeaconsScreenMixin.java', 'mixin/NoMiningTraceMixin.java', 'mixinterface/IBeaconPowerButton.java', 'util/RegistryIdFilter.java']:
        original = ROOT / 'fabric/src/main/java/net/wurstclient' / name
        dest = SRC / 'net/wurstclient' / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, dest)
        provenance[name] = hashlib.sha256(original.read_bytes()).hexdigest()
    for name, body in {
        'org.spongepowered.asm.mixin.Mixin': 'public @interface Mixin {Class<?>[] value() default {};}',
        'org.spongepowered.asm.mixin.Shadow': 'public @interface Shadow {}',
        'org.spongepowered.asm.mixin.Final': 'public @interface Final {}',
        'org.spongepowered.asm.mixin.Unique': 'public @interface Unique {}',
        'org.spongepowered.asm.mixin.injection.At': 'public @interface At {String value();}',
        'org.spongepowered.asm.mixin.injection.Inject': 'public @interface Inject {String method();At at();}',
        'org.spongepowered.asm.mixin.injection.callback.CallbackInfo': 'public class CallbackInfo {}',
        'net.wurstclient.SearchTags': 'public @interface SearchTags {String[] value();}',
        'net.wurstclient.Category': 'public enum Category {BLOCKS,OTHER}',
        'net.wurstclient.settings.CheckboxSetting': 'public class CheckboxSetting {public boolean value;public CheckboxSetting(String n,String d,boolean v){value=v;}public boolean isChecked(){return value;}}',
        'net.wurstclient.settings.TextFieldSetting': 'public class TextFieldSetting {public String value;public TextFieldSetting(String n,String d,String v){value=v;}public String getValue(){return value;}}',
        'net.wurstclient.settings.EnumSetting': 'public class EnumSetting<T> {public T value;public EnumSetting(String n,String d,T[] a,T v){value=v;}public T getSelected(){return value;}}',
        'net.wurstclient.settings.SliderSetting': 'public class SliderSetting {double value;public SliderSetting(String n,String d,double v,double a,double b,double s,ValueDisplay f){value=v;}public double getValueSq(){return value*value;}public enum ValueDisplay{DECIMAL;public ValueDisplay withSuffix(String s){return this;}}}',
        'net.wurstclient.events.UpdateListener': 'public interface UpdateListener {void onUpdate();}',
        'net.wurstclient.events.RightClickListener': 'public interface RightClickListener {void onRightClick(RightClickEvent e);public static class RightClickEvent {public boolean cancelled;public void cancel(){cancelled=true;}public boolean isCancelled(){return cancelled;}}}',
        'net.wurstclient.mixin.MinecraftAccessor': 'public interface MinecraftAccessor {void setRightClickDelay(int n);}',
        'net.wurstclient.util.Rotation': 'public class Rotation {public void applyToClientPlayer(){}public void sendPlayerLookPacket(){}}',
        'net.wurstclient.util.RotationUtils': 'public class RotationUtils {public static Rotation getNeededRotations(net.minecraft.world.phys.Vec3 v){return new Rotation();}}',
        'net.minecraft.world.InteractionHand': 'public enum InteractionHand {MAIN_HAND,OFF_HAND}',
        'net.minecraft.world.InteractionResult': 'public enum InteractionResult {SUCCESS,CONSUME,PASS,FAIL;public boolean consumesAction(){return this==SUCCESS||this==CONSUME;}}',
        'net.minecraft.core.Direction': 'public enum Direction {UP}',
        'net.minecraft.world.item.Item': 'public class Item {}',
        'net.minecraft.world.item.SpawnEggItem': 'public class SpawnEggItem extends Item {}',
        'net.minecraft.tags.ItemTags': 'public class ItemTags {public static final Object PICKAXES=new Object();}',
        'net.minecraft.world.item.ItemStack': 'public class ItemStack {public boolean pickaxe;public Item item=new Item();public boolean is(Object tag){return pickaxe;}public Item getItem(){return item;}}',
        'net.minecraft.world.entity.player.Inventory': 'public class Inventory {}',
        'net.minecraft.world.entity.Saddleable': 'public interface Saddleable {boolean isSaddled();}',
        'net.minecraft.world.entity.animal.horse.AbstractHorse': 'public class AbstractHorse extends net.minecraft.world.entity.Entity implements net.minecraft.world.entity.Saddleable {public boolean saddled;public AbstractHorse(String id){super(id);}public boolean isSaddled(){return saddled;}}',
        'net.minecraft.world.entity.vehicle.Boat': 'public class Boat extends net.minecraft.world.entity.Entity {public Boat(){super("minecraft:boat");}}',
        'net.minecraft.world.entity.vehicle.AbstractMinecart': 'public class AbstractMinecart extends net.minecraft.world.entity.Entity {public AbstractMinecart(){super("minecraft:minecart");}}',
        'net.minecraft.core.registries.BuiltInRegistries': 'public class BuiltInRegistries {public static final Registry ENTITY_TYPE=new Registry();public static class Registry {public String getKey(String type){return type;}}}',
        'net.minecraft.client.renderer.GameRenderer': 'public class GameRenderer {}',
        'net.minecraft.client.gui.screens.inventory.BeaconScreen': 'public class BeaconScreen {}',
        'net.minecraft.network.chat.Component': 'public record Component(String text) {}',
        'net.minecraft.client.gui.components.Tooltip': 'public class Tooltip {public static Tooltip create(net.minecraft.network.chat.Component c){return new Tooltip();}}',
        'net.minecraft.client.gui.components.AbstractWidget': 'public class AbstractWidget {public boolean active=true;public void setTooltip(Tooltip t){}}',
        'net.minecraft.world.effect.MobEffect': 'public class MobEffect {public net.minecraft.network.chat.Component getDisplayName(){return new net.minecraft.network.chat.Component("effect");}}',
        'net.minecraft.world.effect.MobEffects': 'public class MobEffects {public static final MobEffect MOVEMENT_SPEED=new MobEffect(),DIG_SPEED=new MobEffect(),DAMAGE_RESISTANCE=new MobEffect(),JUMP=new MobEffect(),DAMAGE_BOOST=new MobEffect(),REGENERATION=new MobEffect();}',
        'net.minecraft.world.inventory.BeaconMenu': 'public class BeaconMenu {public int levels;public int getLevels(){return levels;}}',
    }.items():
        source(name, body)
    source('net.wurstclient.hack.Hack', '''public class Hack {
protected static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;
protected static final net.wurstclient.WurstClient WURST=net.wurstclient.WurstClient.INSTANCE;
protected static final Events EVENTS=new Events();private boolean enabled;
public Hack(String n){}protected void setCategory(Object c){}protected void addSetting(Object s){}
protected void onEnable(){}protected void onDisable(){}public boolean isEnabled(){return enabled;}
public void setEnabled(boolean v){enabled=v;if(v)onEnable();else onDisable();}
public static class Events {public void add(Object a,Object b){}public void remove(Object a,Object b){}}}''')
    source('net.wurstclient.WurstClient', '''public class WurstClient {
public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();public static final WurstClient INSTANCE=new WurstClient();
public final Hacks hax=new Hacks();public Hacks getHax(){return hax;}
public static class Hacks {public net.wurstclient.hacks.NoMiningTraceHack noMiningTraceHack;public final Reach reachHack=new Reach();public final Beacon betterBeaconsHack=new Beacon();}
public static class Reach {public boolean isEnabled(){return false;}public float getBlockRange(){return 4;}}
public static class Beacon {public boolean expanded=true;public boolean hasExpandedSelection(){return expanded;}}}''')
    source('net.minecraft.core.BlockPos', '''public record BlockPos(int x,int y,int z) {
public static BlockPos containing(net.minecraft.world.phys.Vec3 v){return new BlockPos((int)Math.floor(v.x),(int)Math.floor(v.y),(int)Math.floor(v.z));}}''')
    source('net.minecraft.world.phys.Vec3', '''public record Vec3(double x,double y,double z) {
public Vec3 scale(double k){return new Vec3(x*k,y*k,z*k);}public Vec3 add(Vec3 o){return new Vec3(x+o.x,y+o.y,z+o.z);}
public static Vec3 atCenterOf(net.minecraft.core.BlockPos p){return new Vec3(p.x()+.5,p.y()+.5,p.z()+.5);}}'''.replace('record Vec3(double x,double y,double z)', 'class Vec3').replace('{\npublic Vec3 scale', '{public final double x,y,z;public Vec3(double a,double b,double c){x=a;y=b;z=c;}\npublic Vec3 scale'))
    source('net.minecraft.world.phys.AABB', 'public class AABB {public Vec3 getCenter(){return new Vec3(0,0,0);}}')
    source('net.minecraft.world.phys.HitResult', '''public class HitResult {
public enum Type {MISS,BLOCK,ENTITY}public final Type type;public HitResult(Type t){type=t;}public Type getType(){return type;}}''')
    source('net.minecraft.world.phys.BlockHitResult', '''public class BlockHitResult extends HitResult {
private final net.minecraft.core.BlockPos pos;public BlockHitResult(Vec3 v,net.minecraft.core.Direction d,net.minecraft.core.BlockPos p,boolean inside){super(Type.BLOCK);pos=p;}public net.minecraft.core.BlockPos getBlockPos(){return pos;}}''')
    source('net.minecraft.world.phys.EntityHitResult', '''public class EntityHitResult extends HitResult {
private final net.minecraft.world.entity.Entity entity;public EntityHitResult(net.minecraft.world.entity.Entity e){super(Type.ENTITY);entity=e;}public net.minecraft.world.entity.Entity getEntity(){return entity;}}''')
    source('net.minecraft.world.entity.Entity', '''public class Entity {
public String id;public double distance=1;public boolean removed;public net.minecraft.world.phys.HitResult picked=new net.minecraft.world.phys.HitResult(net.minecraft.world.phys.HitResult.Type.MISS);
public Entity(String id){this.id=id;}public String getType(){return id;}public boolean isRemoved(){return removed;}
public net.minecraft.world.phys.HitResult pick(double r,float t,boolean liquids){return picked;}public net.minecraft.world.phys.AABB getBoundingBox(){return new net.minecraft.world.phys.AABB();}}''')
    source('net.minecraft.client.player.LocalPlayer', '''public class LocalPlayer extends net.minecraft.world.entity.Entity {
public LocalPlayer(){super("minecraft:player");}public boolean shift,passenger,spectator;public int swings;
public net.minecraft.world.item.ItemStack main=new net.minecraft.world.item.ItemStack(),off=new net.minecraft.world.item.ItemStack();
public net.minecraft.world.item.ItemStack getMainHandItem(){return main;}public net.minecraft.world.item.ItemStack getOffhandItem(){return off;}
public boolean isShiftKeyDown(){return shift;}public boolean isPassenger(){return passenger;}public boolean isSpectator(){return spectator;}
public double distanceToSqr(net.minecraft.world.entity.Entity e){return e.distance*e.distance;}
public net.minecraft.world.phys.Vec3 getEyePosition(float t){return new net.minecraft.world.phys.Vec3(.5,.5,.5);}
public net.minecraft.world.phys.Vec3 getViewVector(float t){return new net.minecraft.world.phys.Vec3(1,0,0);}public void swing(net.minecraft.world.InteractionHand h){swings++;}}''')
    source('net.minecraft.client.multiplayer.ClientLevel', '''public class ClientLevel {
public final java.util.List<net.minecraft.world.entity.Entity> entities=new java.util.ArrayList<>();
public final java.util.Set<net.minecraft.core.BlockPos> containers=new java.util.HashSet<>();
public Iterable<net.minecraft.world.entity.Entity> entitiesForRendering(){return entities;}
public State getBlockState(net.minecraft.core.BlockPos p){return new State(containers.contains(p));}
public record State(boolean hasBlockEntity) {}}''')
    source('net.minecraft.client.multiplayer.MultiPlayerGameMode', '''import net.minecraft.world.*;public class MultiPlayerGameMode {
public int mounts;public net.minecraft.world.entity.Entity mounted;public final java.util.List<InteractionHand> hands=new java.util.ArrayList<>();
public final java.util.ArrayDeque<InteractionResult> results=new java.util.ArrayDeque<>();
public float getPickRange(){return 4;}public void interact(net.minecraft.client.player.LocalPlayer p,net.minecraft.world.entity.Entity e,InteractionHand h){mounts++;mounted=e;}
public InteractionResult useItemOn(net.minecraft.client.player.LocalPlayer p,InteractionHand h,net.minecraft.world.phys.BlockHitResult hit){hands.add(h);return results.isEmpty()?InteractionResult.PASS:results.remove();}}''')
    source('net.minecraft.client.Minecraft', '''public class Minecraft implements net.wurstclient.mixin.MinecraftAccessor {
public net.minecraft.client.player.LocalPlayer player=new net.minecraft.client.player.LocalPlayer();
public net.minecraft.client.multiplayer.ClientLevel level=new net.minecraft.client.multiplayer.ClientLevel();
public net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode=new net.minecraft.client.multiplayer.MultiPlayerGameMode();
public Object screen;public int delay;public final Options options=new Options();public net.minecraft.world.phys.HitResult hitResult;public net.minecraft.world.entity.Entity crosshairPickEntity;
public Object getConnection(){return this;}public void setRightClickDelay(int n){delay=n;}public net.minecraft.world.entity.Entity getCameraEntity(){return player;}
public static Minecraft getInstance(){return net.wurstclient.WurstClient.MC;}
public static class Options {public final Key keyUse=new Key();}public static class Key {public boolean down=true;public boolean isDown(){return down;}}}''')
    source('net.minecraft.client.gui.components.Button', '''public class Button extends AbstractWidget {
private final java.util.function.Consumer<Button> action;Button(java.util.function.Consumer<Button> a){action=a;}public void press(){if(active)action.accept(this);}
public static Builder builder(net.minecraft.network.chat.Component n,java.util.function.Consumer<Button> a){return new Builder(a);}
public record Builder(java.util.function.Consumer<Button> action) {public Builder bounds(int x,int y,int w,int h){return this;}public Button build(){return new Button(action);}}}''')
    source('net.minecraft.client.gui.screens.inventory.AbstractContainerScreen', '''public class AbstractContainerScreen<T> {
protected final T menu;protected int leftPos,topPos;public int rebuilds;public final java.util.List<Object> widgets=new java.util.ArrayList<>();
protected AbstractContainerScreen(T m,net.minecraft.world.entity.player.Inventory i,net.minecraft.network.chat.Component c){menu=m;}
protected <W> W addRenderableWidget(W w){widgets.add(w);return w;}protected void removeWidget(Object w){widgets.remove(w);}protected void rebuildWidgets(){rebuilds++;}}''')
    source('audit.Batch9Probe', r'''import java.lang.reflect.*;import java.util.*;
import net.wurstclient.*;import net.wurstclient.hacks.*;import net.wurstclient.settings.*;
import net.minecraft.world.*;import net.minecraft.world.entity.*;import net.minecraft.world.phys.*;
import net.minecraft.core.*;import net.minecraft.client.gui.components.*;import net.minecraft.world.effect.*;
public class Batch9Probe {
static final net.minecraft.client.Minecraft mc=WurstClient.MC;
static void check(boolean c,String m){if(!c)throw new AssertionError(m);}
static Field field(Object o,String n)throws Exception{Class<?> c=o.getClass();while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
static Object get(Object o,String n)throws Exception{return field(o,n).get(o);}static void set(Object o,String n,Object v)throws Exception{field(o,n).set(o,v);}
static void bool(Object o,String n,boolean v)throws Exception{((CheckboxSetting)get(o,n)).value=v;}
static void text(Object o,String n,String v)throws Exception{((TextFieldSetting)get(o,n)).value=v;}
@SuppressWarnings({"unchecked","rawtypes"}) static void mode(Object o,String n,String v)throws Exception{EnumSetting s=(EnumSetting)get(o,n);s.value=Enum.valueOf((Class)s.value.getClass(),v);}
static Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{Class<?> c=o.getClass();while(c!=null){try{Method m=c.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}catch(NoSuchMethodException e){c=c.getSuperclass();}}throw new NoSuchMethodException(n);}
static void reset(){mc.player=new net.minecraft.client.player.LocalPlayer();mc.level=new net.minecraft.client.multiplayer.ClientLevel();mc.gameMode=new net.minecraft.client.multiplayer.MultiPlayerGameMode();mc.options.keyUse.down=true;mc.screen=null;mc.delay=0;}
static void trace()throws Exception{
reset();NoMiningTraceHack h=new NoMiningTraceHack();WurstClient.INSTANCE.hax.noMiningTraceHack=h;
HitResult entity=new EntityHitResult(new Entity("minecraft:zombie"));HitResult block=new BlockHitResult(new Vec3(2,0,0),Direction.UP,new BlockPos(2,0,0),false);mc.player.picked=block;
check(h.selectBlock(entity,mc.player,4,1)==entity,"disabled trace");h.setEnabled(true);
check(h.selectBlock(entity,mc.player,4,1)==entity,"pickaxe gate");mc.player.off.pickaxe=true;
check(h.selectBlock(entity,mc.player,4,1)==block,"offhand pickaxe");text(h,"excludedEntities","zombie");
check(h.selectBlock(entity,mc.player,4,1)==entity,"excluded entity");text(h,"excludedEntities","");
mc.player.picked=new HitResult(HitResult.Type.MISS);check(h.selectBlock(entity,mc.player,4,1)==entity,"keep entity if no block");
mc.player.picked=block;mc.player.off.pickaxe=false;bool(h,"onlyPickaxe",false);check(h.selectBlock(entity,mc.player,4,1)==block,"any item setting");
check(h.selectBlock(block,mc.player,4,1)==block,"already selected block");
mc.hitResult=entity;mc.crosshairPickEntity=((EntityHitResult)entity).getEntity();
net.wurstclient.mixin.NoMiningTraceMixin hook=new net.wurstclient.mixin.NoMiningTraceMixin(){};
call(hook,"wurstSelectBlock",new Class<?>[]{float.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class},1F,null);
check(mc.hitResult==block&&mc.crosshairPickEntity==null,"actual target callback clears entity");
mc.player=null;check(h.selectBlock(entity,null,4,1)==entity,"disconnected trace");}
static class Pig extends Entity implements Saddleable {boolean saddle;Pig(){super("minecraft:pig");}public boolean isSaddled(){return saddle;}}
static boolean accepts(AutoMountHack h,Entity e)throws Exception{return (boolean)call(h,"isConfigured",new Class<?>[]{Entity.class},e);}
static void mount()throws Exception{
reset();AutoMountHack h=new AutoMountHack();h.setEnabled(true);bool(h,"checkSaddle",true);
var llama=new net.minecraft.world.entity.animal.horse.AbstractHorse("minecraft:llama");check(accepts(h,llama),"llama saddle exemption");
var horse=new net.minecraft.world.entity.animal.horse.AbstractHorse("minecraft:horse");check(!accepts(h,horse),"horse requires configured saddle");horse.saddled=true;check(accepts(h,horse),"saddled horse");
bool(h,"checkSaddle",false);var skeleton=new net.minecraft.world.entity.animal.horse.AbstractHorse("minecraft:skeleton_horse");check(!accepts(h,skeleton),"mandatory undead saddle");skeleton.saddled=true;check(accepts(h,skeleton),"saddled undead horse");
Pig pig=new Pig();bool(h,"pigs",true);check(!accepts(h,pig),"mandatory pig saddle");pig.saddle=true;check(accepts(h,pig),"saddled pig");
mode(h,"selection","SELECTED_TYPES");text(h,"entities","pig");check(accepts(h,pig)&&!accepts(h,horse),"entity list overrides groups");
text(h,"entities","zombie");check(!accepts(h,new Entity("minecraft:zombie")),"nonrideable rejected");
for(String id:List.of("happy_ghast","nautilus","zombie_nautilus")){text(h,"entities",id);check(accepts(h,new Entity("minecraft:"+id)),"newer rideable in explicit list: "+id);}
text(h,"entities","pig");mc.level.entities.add(pig);
mc.player.shift=true;h.onUpdate();check(mc.gameMode.mounts==0,"sneaking does not mount");mc.player.shift=false;h.onUpdate();check(mc.gameMode.mounted==pig,"selected entity mounted");h.onUpdate();check(mc.gameMode.mounts==1,"cooldown stops repeat");}
static void ghost()throws Exception{
reset();GhostHandHack h=new GhostHandHack();h.setEnabled(true);mc.level.containers.add(new BlockPos(2,0,0));
mc.gameMode.results.add(InteractionResult.PASS);mc.gameMode.results.add(InteractionResult.SUCCESS);
var event=new net.wurstclient.events.RightClickListener.RightClickEvent();h.onRightClick(event);
check(event.isCancelled()&&mc.player.swings==1&&mc.delay==4,"offhand successful interaction cancels vanilla and sets cooldown");
check(mc.gameMode.hands.equals(List.of(InteractionHand.MAIN_HAND,InteractionHand.OFF_HAND)),"hand order and no duplicate block");
reset();mc.level.containers.add(new BlockPos(2,0,0));mc.player.picked=new BlockHitResult(new Vec3(2,0,0),Direction.UP,new BlockPos(2,0,0),false);h.onRightClick(new net.wurstclient.events.RightClickListener.RightClickEvent());check(mc.gameMode.hands.isEmpty(),"visible container retains vanilla");
mc.player.picked=new HitResult(HitResult.Type.MISS);mc.player.shift=true;h.onRightClick(new net.wurstclient.events.RightClickListener.RightClickEvent());check(mc.gameMode.hands.isEmpty(),"sneak bypass");mc.player.shift=false;
event=new net.wurstclient.events.RightClickListener.RightClickEvent();h.onRightClick(event);check(!event.isCancelled()&&mc.gameMode.hands.size()==2,"PASS preserves vanilla and only visits block once");
mc.gameMode.hands.clear();mc.level.containers.clear();mc.level.containers.add(new BlockPos(5,0,0));h.onRightClick(event);check(mc.gameMode.hands.isEmpty(),"outside reach");}
static class Beacon extends net.wurstclient.mixin.BetterBeaconsScreenMixin {Beacon(net.minecraft.world.inventory.BeaconMenu m){super(m,new net.minecraft.world.entity.player.Inventory(),new net.minecraft.network.chat.Component("beacon"));}}
static class Power extends AbstractWidget implements net.wurstclient.mixinterface.IBeaconPowerButton {}
@SuppressWarnings("unchecked") static void beacons()throws Exception{
var menu=new net.minecraft.world.inventory.BeaconMenu();Beacon b=new Beacon(menu);Object confirm=new AbstractWidget(),cancel=new AbstractWidget();Power power=new Power();
List<Object> vanilla=new ArrayList<>(List.of(confirm,cancel,power));set(b,"beaconButtons",vanilla);b.widgets.addAll(vanilla);
Class<?>[] ci={org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class};call(b,"wurstExpandSelection",ci,new Object[]{null});
check(vanilla.equals(List.of(confirm,cancel)),"retain native confirm/cancel");check(!b.widgets.contains(power)&&b.widgets.size()==14,"replace powers with twelve selectors");
List<Button> primary=(List<Button>)get(b,"wurstPrimaryButtons"),secondary=(List<Button>)get(b,"wurstSecondaryButtons");
primary.get(5).press();check(get(b,"primary")==MobEffects.REGENERATION,"primary regeneration at level zero");secondary.get(1).press();check(get(b,"secondary")==null,"secondary level gate");
menu.levels=4;call(b,"wurstRefreshButtons",ci,new Object[]{null});secondary.get(1).press();check(get(b,"secondary")==MobEffects.DIG_SPEED,"secondary haste at level four");
WurstClient.INSTANCE.hax.betterBeaconsHack.expanded=false;call(b,"wurstRefreshButtons",ci,new Object[]{null});check(b.rebuilds==1,"disabled module rebuilds native widgets");}
public static void main(String[] a)throws Exception{trace();mount();ghost();beacons();System.out.println("PASS: targeting callback, mount filters, through-wall use and beacon selection");}}
''')
    java = Path('C:/Users/ui863/.jdks/jdk25.0.4_10/bin')
    OUT.mkdir(parents=True, exist_ok=True)
    args = OUT / 'javac.args'
    args.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in SRC.rglob('*.java')), encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(OUT / 'classes'), '@' + str(args)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(OUT / 'classes'), 'audit.Batch9Probe'], check=True)
    # Verify the modern static callback without changing its production source.
    modern = OUT / 'modern-src'
    hook = ROOT / 'fabric/versions/26.3/src/main/java/net/wurstclient/mixin/NoMiningTraceMixin.java'
    dest = modern / 'net/wurstclient/mixin/NoMiningTraceMixin.java'
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(hook, dest)
    provenance['modern/NoMiningTraceMixin.java'] = hashlib.sha256(hook.read_bytes()).hexdigest()
    annotation = modern / 'com/llamalad7/mixinextras/injector/ModifyReturnValue.java'
    annotation.parent.mkdir(parents=True, exist_ok=True)
    annotation.write_text('package com.llamalad7.mixinextras.injector;public @interface ModifyReturnValue {String method();org.spongepowered.asm.mixin.injection.At at();}', encoding='utf-8')
    probe = modern / 'audit/ModernTraceProbe.java'
    probe.parent.mkdir(parents=True, exist_ok=True)
    probe.write_text('''package audit;
import net.minecraft.world.phys.*;import net.minecraft.world.entity.Entity;
public class ModernTraceProbe {public static void main(String[] args)throws Exception {
var mc=net.wurstclient.WurstClient.MC;var h=new net.wurstclient.hacks.NoMiningTraceHack();h.setEnabled(true);
net.wurstclient.WurstClient.INSTANCE.hax.noMiningTraceHack=h;mc.player.main.pickaxe=true;
HitResult original=new EntityHitResult(new Entity("minecraft:pig"));
HitResult block=new BlockHitResult(new Vec3(2,0,0),net.minecraft.core.Direction.UP,new net.minecraft.core.BlockPos(2,0,0),false);mc.player.picked=block;
var method=net.wurstclient.mixin.NoMiningTraceMixin.class.getDeclaredMethod("wurstSelectBlock",HitResult.class,Entity.class,double.class,double.class,float.class);method.setAccessible(true);
if(method.invoke(null,original,mc.player,4D,3D,1F)!=block)throw new AssertionError("modern static target callback");
System.out.println("PASS: unchanged modern static NoMiningTrace callback");}}''', encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-cp', str(OUT / 'classes'), '-d', str(OUT / 'modern-classes'), str(dest), str(annotation), str(probe)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(OUT / 'modern-classes') + ';' + str(OUT / 'classes'), 'audit.ModernTraceProbe'], check=True)
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
