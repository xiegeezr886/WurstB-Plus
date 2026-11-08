"""Run actual ported Java state classes against small Minecraft boundary stubs.

No source-string assertions or duplicated state-machine implementations. These
tests cover deterministic behavior, not Minecraft physics or Mixin startup.
"""
from pathlib import Path
import hashlib
import json
import subprocess
import tempfile
from refactor_openopal_combat import ROOT, PLAN, PACKAGE

STUBS = {
'net/minecraft/network/protocol/Packet.java': '''package net.minecraft.network.protocol;
public interface Packet<T> {}''',
'net/wurstclient/opal/OpenOpalPacketBridge.java': '''package net.wurstclient.opal;
public class OpenOpalPacketBridge {
 public static Object observed;public static int count;
 public static void beforeSend(net.minecraft.network.protocol.Packet<?> p){observed=p;count++;}
}''',
'net/minecraft/util/Mth.java': '''package net.minecraft.util;
public class Mth {
 public static float wrapDegrees(float v){v%=360;return v>=180?v-360:v< -180?v+360:v;}
 public static double wrapDegrees(double v){v%=360;return v>=180?v-360:v< -180?v+360:v;}
 public static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
 public static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
 public static int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}
 public static float lerp(float t,float a,float b){return a+t*(b-a);}
}''',
'net/wurstclient/util/Rotation.java': '''package net.wurstclient.util;
public record Rotation(float yaw,float pitch){
 public double getAngleTo(Rotation r){return Math.hypot(net.minecraft.util.Mth.wrapDegrees(yaw-r.yaw),pitch-r.pitch);}
}''',
'net/minecraft/world/item/ItemStack.java': '''package net.minecraft.world.item;
public class ItemStack { public final int slot; public ItemStack(int s){slot=s;} }''',
'net/minecraft/world/entity/player/Inventory.java': '''package net.minecraft.world.entity.player;
import net.minecraft.world.item.ItemStack;
public class Inventory {
 public int selected; private final ItemStack[] items=new ItemStack[9];
 public Inventory(){for(int i=0;i<9;i++)items[i]=new ItemStack(i);}
 public int getSelectedSlot(){return selected;} public void setSelectedSlot(int v){selected=v;}
 public ItemStack getItem(int i){return items[i];}
}''',
'net/minecraft/client/player/LocalPlayer.java': '''package net.minecraft.client.player;
import net.minecraft.world.entity.player.Inventory; import net.minecraft.world.item.ItemStack;
public class LocalPlayer {
 public int tickCount; public float yaw,pitch,xBob,xBobO,yBob,yBobO,cooldown=1;
 private final Inventory inventory=new Inventory();
 public Inventory getInventory(){return inventory;}
 public ItemStack getMainHandItem(){return inventory.getItem(inventory.selected);}
 public float getYRot(){return yaw;} public float getXRot(){return pitch;}
 public void setYRot(float v){yaw=v;} public void setXRot(float v){pitch=v;}
 public void setYBodyRot(float v){} public void setYHeadRot(float v){}
 public float getAttackStrengthScale(float delta){return cooldown;}
}''',
'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;
import net.minecraft.client.player.LocalPlayer;
public class Minecraft {
 public LocalPlayer player=new LocalPlayer(); public Object level=new Object(),screen,overlay;
 public Object getOverlay(){return overlay;}
 public final Gui gui=new Gui(); public class Gui {public Object overlay(){return overlay;}public Object screen(){return screen;}}
 public final Options options=new Options();
 public static class Bool {public boolean value;public boolean get(){return value;}}
 public static class Options {
  public final Bool x=new Bool(),y=new Bool();
  public Bool invertYMouse(){return y;}public Bool invertMouseY(){return y;}public Bool invertMouseX(){return x;}
 }
}''',
'net/wurstclient/mixin/OpenOpalKeyAccessor.java': '''package net.wurstclient.mixin;
public interface OpenOpalKeyAccessor {int opal$getClickCount();}''',
'net/minecraft/client/KeyMapping.java': '''package net.minecraft.client;
public class KeyMapping implements net.wurstclient.mixin.OpenOpalKeyAccessor {
 public int clicks;public boolean down;
 public boolean consumeClick(){if(clicks==0)return false;clicks--;return true;}
 public boolean isDown(){return down;} public int opal$getClickCount(){return clicks;}
}''',
'net/wurstclient/WurstClient.java': '''package net.wurstclient;
import net.minecraft.client.Minecraft;import net.wurstclient.events.PreMotionListener;import java.util.ArrayList;
public class WurstClient {
 public static final Minecraft MC=new Minecraft();public static final WurstClient INSTANCE=new WurstClient();
 public final Manager manager=new Manager();public final Faker faker=new Faker();
 public Manager getEventManager(){return manager;}public Faker getRotationFaker(){return faker;}
 public static class Manager {public final ArrayList<PreMotionListener> listeners=new ArrayList<>();
  public void add(Class<?> c,PreMotionListener l){listeners.add(l);}}
 public static class Faker {public net.wurstclient.util.Rotation last;public void setRotationPacket(net.wurstclient.util.Rotation r){last=r;}}
}''',
'net/wurstclient/hacks/scaffold/ScaffoldUtils.java': '''package net.wurstclient.hacks.scaffold;
import net.wurstclient.util.Rotation;
public class ScaffoldUtils {
 public static Rotation patchConstantRotation(Rotation r,Rotation from){return r;}
 public static double getRotationDifference(Rotation a,Rotation b){return a.getAngleTo(b);}
}''',
'net/wurstclient/opal/rotation/RotationHelper.java': '''package net.wurstclient.opal.rotation;
import net.wurstclient.WurstClient;import net.wurstclient.util.Rotation;
public class RotationHelper {
 public static final ClientRotationHandler client=new ClientRotationHandler();
 public static ClientRotationHandler getClientHandler(){return client;}
 public static Rotation getPlayerRotation(){return new Rotation(WurstClient.MC.player.yaw,WurstClient.MC.player.pitch);}
 public static float getPartialTick(){return 1;}
 public static double getCursorDelta(double delta,double multiplier){return (float)(delta/multiplier)/0.15F;}
 public static void forceMouseTick(){}
}''',
}

HARNESS = r'''import net.wurstclient.WurstClient;
import net.wurstclient.opal.*;
import net.wurstclient.opal.rotation.*;
import net.wurstclient.util.*;
import net.wurstclient.events.PreMotionListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
public class PortStateTest {
 static int assertions;
 static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
 static void near(double a,double b,String m){check(Math.abs(a-b)<0.001,m+": "+a+" vs "+b);}
 static void fresh(){WurstClient.MC.player=new LocalPlayer();WurstClient.MC.level=new Object();}
 static void plans(){
  var q=new MotionActionQueue<String>();Object p=new Object(),w=new Object();
  q.begin(p,w,10);q.schedule("attack");check(q.isCurrent(p,w,11),"entity tick boundary allowed");
  check("attack".equals(q.consume(p,w,11)),"consume planned action");check(q.consume(p,w,11)==null,"consume once");
  q.begin(p,w,10);q.schedule("attack");check(q.consume(new Object(),w,10)==null,"player identity");
  q.begin(p,w,10);q.schedule("attack");check(q.consume(p,new Object(),10)==null,"world identity");
  q.begin(p,w,10);q.schedule("attack");check(q.consume(p,w,12)==null,"stale plan");
  q.begin(p,w,10);q.schedule("attack");check(q.consume(p,w,9)==null,"rewound tick");
  q.begin(p,w,Integer.MAX_VALUE);q.schedule("attack");check(q.consume(p,w,Integer.MIN_VALUE)==null,"overflow invalidates");
 }
 static void dispatch(){PreMotionListener.PreMotionEvent.INSTANCE.fire(new java.util.ArrayList<>(WurstClient.INSTANCE.manager.listeners));}
 static void queues(){
  fresh();var low=new RotationQueue(RotationQueue.Priority.BLOCK_PLACEMENT);var high=new RotationQueue(RotationQueue.Priority.COMBAT);
  low.start();high.start();low.setRotation(new Rotation(10,0));high.setRotation(new Rotation(20,0));dispatch();
  near(WurstClient.INSTANCE.faker.last.yaw(),20,"priority wins");check(high.wasApplied()&&!low.wasApplied(),"one owner");
  WurstClient.INSTANCE.faker.last=null;dispatch();check(WurstClient.INSTANCE.faker.last==null,"loser cannot replay");
  low.stop();high.stop();high.start();low.start();high.setRotation(new Rotation(20,0));low.setRotation(new Rotation(10,0));dispatch();
  near(WurstClient.INSTANCE.faker.last.yaw(),20,"registration order independent");
  high.setRotation(new Rotation(40,0));WurstClient.MC.player.tickCount+=2;WurstClient.INSTANCE.faker.last=null;dispatch();check(WurstClient.INSTANCE.faker.last==null,"stale rotation");
  high.setRotation(new Rotation(40,0));fresh();dispatch();check(WurstClient.INSTANCE.faker.last==null,"replacement player");
  low.stop();high.stop();
  var producer=new PreMotionListener(){public void onPreMotion(){high.setRotation(new Rotation(75,5));}};
  WurstClient.INSTANCE.manager.listeners.add(producer);dispatch();near(WurstClient.INSTANCE.faker.last.yaw(),75,"requests published after dispatcher still resolved");
  WurstClient.INSTANCE.manager.listeners.remove(producer);high.stop();
 }
 static void buttons(){
  var key=new KeyMapping();var b=new MouseButton(key);b.setPressed();check(b.wasPressed(),"synthetic click");check(!b.wasPressed(),"click consumed once");
  b.setPressed(true,2);b.setShowSwings(false);b.wasPressed();b.tick();check(b.isPressed()&&!b.isShowSwings(),"hold persists");b.tick();check(!b.isPressed()&&b.isShowSwings(),"hold expires");
  b.setPressed();b.setDisabled();check(!b.wasPressed()&&!b.isPressed(),"disable cancels");b.tick();key.down=true;check(b.isPressed(),"physical input restored");
  b.setShowSwings(false);key.clicks=1;check(b.isShowSwings(),"physical click visible");check(b.wasPressed(),"physical click consumed");
 }
 static void packets(){
  var original=new net.minecraft.network.protocol.Packet<Object>(){};
  var replacement=new net.minecraft.network.protocol.Packet<Object>(){};
  var listeners=new java.util.ArrayList<net.wurstclient.events.ConnectionPacketOutputListener>();
  listeners.add(e->e.setPacket(replacement));
  var event=new net.wurstclient.events.ConnectionPacketOutputListener.ConnectionPacketOutputEvent(original);
  event.fire(listeners);check(OpenOpalPacketBridge.observed==replacement,"final hook sees replaced packet");
  int before=OpenOpalPacketBridge.count;listeners.add(e->e.cancel());
  event=new net.wurstclient.events.ConnectionPacketOutputListener.ConnectionPacketOutputEvent(original);
  event.fire(listeners);check(OpenOpalPacketBridge.count==before,"cancelled packet never observed as outbound");
  var output=new net.wurstclient.events.PacketOutputListener.PacketOutputEvent(original);
  int[] calls={0};output.runAfterSend(()->calls[0]++);check(calls[0]==0,"send callbacks deferred");
  output.notifySent();output.notifySent();check(calls[0]==1,"send callbacks run once");
 }
 static void slots(){
  fresh();SlotHelper.setInstance();var h=SlotHelper.getInstance();var inv=WurstClient.MC.player.getInventory();inv.selected=1;
  h.setTargetItem(4).silence(SlotHelper.Silence.FULL);check(inv.selected==4,"actual slot switches");check(h.getSelectedSlot(inv)==1,"full visual slot retained");check(h.getMainHandStack(WurstClient.MC.player).slot==1,"visual hand retained");
  h.tick();check(h.isActive(),"lease survives first tick");h.tick();check(!h.isActive()&&inv.selected==1,"lease expires and restores");
  h.setTargetItem(5).silence(SlotHelper.Silence.DEFAULT);check(h.getSelectedSlot(inv)==5,"default hotbar actual");check(h.getMainHandStack(WurstClient.MC.player).slot==1,"default hand visual");h.onSelect(2);h.stop();check(inv.selected==2,"manual visual selection restored");
  h.setTargetItem(5).silence(SlotHelper.Silence.NONE);check(h.getMainHandStack(WurstClient.MC.player).slot==5,"none uses actual hand");h.stop();
  inv.selected=0;h.setTargetItem(5).silence(SlotHelper.Silence.FULL);h.onScroll(1);h.stop();check(inv.selected==8,"scroll wraps visual selection");
  inv.selected=1;h.setTargetItem(5).silence(SlotHelper.Silence.FULL);h.onScrollTo(6,5);h.stop();check(inv.selected==2,"modern scroll relative to visual slot");
  h.setTargetItem(5);fresh();WurstClient.MC.player.getInventory().selected=3;check(!h.onScroll(1),"old-world scroll lease discarded");h.stop();check(WurstClient.MC.player.getInventory().selected==3,"old slot never restored on new player");
  h.setTargetItem(5);WurstClient.MC.level=new Object();check(!h.isActive(),"same player new world resets lease");
 }
 static Stopwatch timer(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return (Stopwatch)f.get(target);}
 static void timing()throws Exception{
  var a=new LastAttackData(4);timer(a,"stopwatch").setTime(System.currentTimeMillis()-2000);a.reset(false,6);check(a.getTime()>=2000&&a.getDamage()==6,"burst retains stopwatch and updates damage");a.reset(true,8);check(a.getTime()<1000,"new burst resets stopwatch");
  SwingDelay.setInstance();var f=SwingDelay.class.getDeclaredField("instance");f.setAccessible(true);var t=timer(f.get(null),"swingStopwatch");var cps=new CPSProperty(()->10,()->false);cps.resetClick();t.setTime(System.currentTimeMillis()-2000);
  check(SwingDelay.isSwingAvailable(cps,false),"ready query");check(t.getTime()>=2000,"query does not consume");check(SwingDelay.isSwingAvailable(cps),"real consume");check(!SwingDelay.isSwingAvailable(cps),"fake and real share timer");
  var modern=new CPSProperty(()->10,()->true);WurstClient.MC.player.cooldown=0.7F;check(!SwingDelay.isSwingAvailable(modern),"modern cooldown pending");WurstClient.MC.player.cooldown=1;check(SwingDelay.isSwingAvailable(modern),"modern cooldown ready");
  SkipTickUtility.reset();SkipTickUtility.addSkipTicks(-1);check(!SkipTickUtility.consumeSkipTick(),"ignore negative skip");SkipTickUtility.addSkipTicks(2);check(SkipTickUtility.consumeSkipTick()&&SkipTickUtility.consumeSkipTick()&&!SkipTickUtility.consumeSkipTick(),"consume exact skips");SkipTickUtility.addSkipTicks(5);SkipTickUtility.reset();check(!SkipTickUtility.consumeSkipTick(),"reset skips");
 }
 static void models()throws Exception{
  var from=new Rotation(179,0);var to=new Rotation(-179,0);near(InstantRotationModel.INSTANCE.tick(from,to,0.5F).yaw(),180,"shortest yaw interpolation");
  var r=new LinearRotationModel(10).tick(new Rotation(0,0),new Rotation(60,80),1);near(Math.hypot(r.yaw(),r.pitch()),10,"linear speed bound");
  r=new HeypixelRotationModel(100).tick(new Rotation(0,80),new Rotation(0,150),1);near(r.pitch(),90,"pitch clamp");
  var organic=new OrganicRotationModel(90,1.2,0.12);r=organic.tick(new Rotation(0,0),new Rotation(0.2F,0.1F),1);near(r.yaw(),0.2,"no drift near target");
  var other=new OrganicRotationModel(90,1.2,0.12);organic.tick(new Rotation(0,0),new Rotation(90,40),1);var acc=OrganicRotationModel.class.getDeclaredField("timeAccumulator");acc.setAccessible(true);check(acc.getDouble(organic)>0&&acc.getDouble(other)==0,"organic state per instance");
  near(new HypixelRotationModel(true,1).tick(new Rotation(0,0),new Rotation(90,0),1).yaw(),65,"diagonal first air speed");near(new HypixelRotationModel(false,1).tick(new Rotation(0,0),new Rotation(90,0),1).yaw(),35,"straight air speed");
 }
 static void mouse(boolean invertX){
  fresh();var p=WurstClient.MC.player;p.yaw=20;p.pitch=10;
  RotationHelper.client.setRotation(new Rotation(20,10));var h=new RotationMouseHandler();
  WurstClient.MC.options.x.value=invertX;WurstClient.MC.options.y.value=true;
  h.rotate(new Rotation(60,30),InstantRotationModel.INSTANCE);var event=new MouseUpdateEvent(10,10,1,false);
  RotationHelper.client.onMouseUpdate(event);h.onMouseUpdate(event);
  check(event.isHandled(),"mouse rotation handled");near(p.yaw+event.getDeltaX()*0.15*(invertX?-1:1),60,"synthetic yaw compensates inversion");near(p.pitch-event.getDeltaY()*0.15,30,"synthetic pitch compensates inversion");
  near(RotationHelper.client.getYawOr(0),20+(invertX?-1.5:1.5),"client yaw follows real input");near(RotationHelper.client.getPitchOr(0),8.5,"client pitch follows real input");
  h.reset();near(p.yaw,20+(invertX?-1.5:1.5),"disable restores real-input yaw");near(p.pitch,8.5,"disable restores real-input pitch");
  h.rotate(new Rotation(70,30),InstantRotationModel.INSTANCE);h.discard();p.yaw=2;h.reset();near(p.yaw,2,"discard prevents old-world restoration");
  RotationHelper.client.setRotation(new Rotation(20,10));RotationHelper.client.setTicking(true);RotationHelper.client.onRotationSet();check(RotationHelper.client.getRotation()!=null,"inventory and mouse changes preserve visual view");
  RotationHelper.client.setTicking(false);RotationHelper.client.onRotationSet();check(RotationHelper.client.getRotation()==null,"external teleport rotation invalidates visual view");
 }
 public static void main(String[] args)throws Exception{plans();queues();buttons();packets();slots();timing();models();mouse(Boolean.parseBoolean(args[0]));System.out.println("PASS "+assertions+" behavioral assertions");}
}'''


def main():
    java = Path('C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin')
    rows = []
    for tree, invert_x in [('fabric', False), ('fabric/versions/26.3', True)]:
        sources = ['util/MotionActionQueue.java', 'util/RotationQueue.java', 'events/PreMotionListener.java',
                   'event/Event.java', 'event/Listener.java', 'event/CancellableEvent.java',
                   'events/ConnectionPacketOutputListener.java','events/PacketOutputListener.java',
                   'hacks/scaffold/ScaffoldRotationModel.java']
        sources += ['opal/'+c+'.java' for c in ['MouseButton','SlotHelper','SwingDelay','CPSProperty','Stopwatch','LastAttackData','SkipTickUtility','RandomUtility']]
        sources += ['opal/rotation/'+c+'.java' for c in ['IRotationModel','InstantRotationModel','LinearRotationModel','OrganicRotationModel','HeypixelRotationModel','HypixelRotationModel','SidewaysRotationModel','MouseUpdateEvent','RotationMouseHandler','ClientRotationHandler']]
        production = [ROOT/tree/PACKAGE/s for s in sources]
        with tempfile.TemporaryDirectory(prefix='openopal-state-') as tmp:
            folder = Path(tmp)
            for name, content in STUBS.items():
                f = folder/name
                f.parent.mkdir(parents=True,exist_ok=True)
                f.write_text(content,encoding='utf-8')
            (folder/'PortStateTest.java').write_text(HARNESS,encoding='utf-8')
            files = production + list(folder.rglob('*.java'))
            subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-d',str(folder/'classes'),*[str(f) for f in files]],check=True)
            result = subprocess.check_output([str(java/'java.exe'),'-cp',str(folder/'classes'),'PortStateTest',str(invert_x).lower()],text=True)
            print(tree,result.strip())
            rows.append({'project':tree,'result':result.strip(),'source_sha256':{str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in production}})
    (PLAN/'behavior-tests.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')


if __name__ == '__main__':main()
