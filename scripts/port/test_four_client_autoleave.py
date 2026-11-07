"""Execute unchanged AutoLeave/AutoReconnect classes against a controllable host."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.planning/2026-11-07-four-client-v151-port/batch2/autoleave-tests'
SRC = OUT / 'src'

def source(name, body):
    path = SRC / (name.replace('.', '/') + '.java')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')

def main():
    provenance = {}
    for name in ['hacks/AutoLeaveHack.java', 'hacks/AutoReconnectHack.java', 'util/AutoLeaveTriggers.java']:
        original = ROOT / 'fabric/src/main/java/net/wurstclient' / name
        dest = SRC / 'net/wurstclient' / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, dest)
        provenance[name] = hashlib.sha256(original.read_bytes()).hexdigest()
    source('net.wurstclient.Category', 'public enum Category {COMBAT,OTHER}')
    source('net.wurstclient.SearchTags', 'public @interface SearchTags {String[] value();}')
    source('net.wurstclient.DontBlock', 'public @interface DontBlock {}')
    source('net.wurstclient.settings.CheckboxSetting', '''public class CheckboxSetting {
public boolean value;public CheckboxSetting(String n,boolean v){value=v;}public CheckboxSetting(String n,String d,boolean v){value=v;}
public boolean isChecked(){return value;}}''')
    source('net.wurstclient.settings.SliderSetting', '''public class SliderSetting {
public double value;public SliderSetting(String n,String d,double v,double a,double b,double s,ValueDisplay f){value=v;}
public SliderSetting(String n,double v,double a,double b,double s,ValueDisplay f){value=v;}
public int getValueI(){return (int)value;}public float getValueF(){return (float)value;}public double getValue(){return value;}
public enum ValueDisplay {INTEGER,DECIMAL;public ValueDisplay withSuffix(String s){return this;}public ValueDisplay withLabel(int n,String s){return this;}}}''')
    source('net.wurstclient.settings.EnumSetting', '''public class EnumSetting<T> {
public T value;public EnumSetting(String n,String d,T[] vs,T v){value=v;}public T getSelected(){return value;}}''')
    source('net.wurstclient.events.UpdateListener', 'public interface UpdateListener {void onUpdate();}')
    source('net.wurstclient.events.WorldChangeListener', 'public interface WorldChangeListener {void onWorldChange(net.minecraft.client.multiplayer.ClientLevel world);}')
    source('net.wurstclient.events.PacketInputListener', '''public interface PacketInputListener {
void onReceivedPacket(PacketInputEvent event);public record PacketInputEvent(Object packet){public Object getPacket(){return packet;}}}''')
    source('net.wurstclient.mixin.AutoLeavePortalAccessor', 'public interface AutoLeavePortalAccessor {boolean wurstIsInsidePortal();}')
    source('net.minecraft.world.entity.EquipmentSlot', 'public enum EquipmentSlot {HEAD,CHEST,LEGS,FEET}')
    source('net.minecraft.world.item.Item', 'public class Item {}')
    source('net.minecraft.world.item.Items', 'public class Items {public static final Item ELYTRA=new Item(),TOTEM_OF_UNDYING=new Item();}')
    source('net.minecraft.world.item.ItemStack', '''public class ItemStack {
public Item item=new Item();public int max,damage;public boolean is(Item i){return item==i;}
public int getMaxDamage(){return max;}public int getDamageValue(){return damage;}public boolean isDamageableItem(){return max>0;}}''')
    source('net.minecraft.network.protocol.game.ClientboundSetTimePacket', 'public class ClientboundSetTimePacket {}')
    source('net.minecraft.network.protocol.game.ServerboundInteractPacket', 'public class ServerboundInteractPacket {public static Object createAttackPacket(Object p,boolean s){return new Object();}}')
    source('net.minecraft.client.multiplayer.ClientLevel', 'public class ClientLevel {public int disconnects;public void disconnect(){disconnects++;}}')
    source('net.minecraft.client.Minecraft', '''import net.minecraft.world.item.*;import net.minecraft.world.entity.*;
public class Minecraft {
public Player player=new Player();public net.minecraft.client.multiplayer.ClientLevel level=new net.minecraft.client.multiplayer.ClientLevel();
public Connection connection=new Connection();public Connection getConnection(){return connection;}
public static class Player implements net.wurstclient.mixin.AutoLeavePortalAccessor {
public boolean portal;public float health=20;public double x,y=64,z;
public Abilities abilities=new Abilities();public final ItemStack[] armor={new ItemStack(),new ItemStack(),new ItemStack(),new ItemStack()};
public boolean wurstIsInsidePortal(){return portal;}public Abilities getAbilities(){return abilities;}
public double getX(){return x;}public double getY(){return y;}public double getZ(){return z;}
public float getHealth(){return health;}public ItemStack getItemBySlot(EquipmentSlot s){return armor[s.ordinal()];}public boolean isShiftKeyDown(){return false;}}
public static class Abilities {public boolean instabuild;}
public static class Connection {public int sent;public void sendChat(String s){sent++;}public void send(Object p){sent++;}}}''')
    source('net.wurstclient.WurstClient', '''public class WurstClient {
public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();public static final WurstClient INSTANCE=new WurstClient();
private Hax hax;public Hax getHax(){if(hax==null)hax=new Hax();return hax;}
public static class Hax {public final net.wurstclient.hacks.AutoReconnectHack autoReconnectHack=new net.wurstclient.hacks.AutoReconnectHack();}}''')
    source('net.wurstclient.hack.Hack', '''public class Hack {
protected static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;
protected static final net.wurstclient.WurstClient WURST=net.wurstclient.WurstClient.INSTANCE;
public static final Events EVENTS=new Events();private boolean enabled;private final String name;
public Hack(String n){name=n;}public String getName(){return name;}public String getRenderName(){return name;}
protected void setCategory(Object c){}protected void addSetting(Object s){}protected void onEnable(){}protected void onDisable(){}
public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){if(enabled==v)return;enabled=v;if(v)onEnable();else onDisable();}
public static class Events {public final java.util.Map<Class<?>,java.util.Set<Object>> listeners=new java.util.HashMap<>();
public void add(Class<?> c,Object o){listeners.computeIfAbsent(c,k->new java.util.HashSet<>()).add(o);}
public void remove(Class<?> c,Object o){listeners.getOrDefault(c,new java.util.HashSet<>()).remove(o);}}}''')
    source('net.wurstclient.util.ChatUtils', 'public class ChatUtils {public static void message(String s){}}')
    source('net.wurstclient.util.InventoryUtils', 'public class InventoryUtils {public static int totems;public static int count(Object i,int n,boolean o){return totems;}}')
    source('audit.AutoLeaveProbe', '''import java.lang.reflect.*;import net.wurstclient.*;import net.wurstclient.hacks.*;
import net.wurstclient.settings.*;import net.wurstclient.events.*;import net.wurstclient.util.InventoryUtils;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;import net.minecraft.client.multiplayer.ClientLevel;
public class AutoLeaveProbe {
static final net.minecraft.client.Minecraft mc=WurstClient.MC;
static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
static Object field(Object x,String n)throws Exception{Field f=x.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(x);}
static void toggle(Object a,String n,boolean v)throws Exception{((CheckboxSetting)field(a,n)).value=v;}
static void number(Object a,String n,double v)throws Exception{((SliderSetting)field(a,n)).value=v;}
static void stale(AutoLeaveHack a)throws Exception{Field f=a.getClass().getDeclaredField("lastServerTick");f.setAccessible(true);f.setLong(a,System.nanoTime()-20_000_000_000L);}
static AutoLeaveHack fresh()throws Exception{mc.level=new ClientLevel();mc.player=new net.minecraft.client.Minecraft.Player();mc.connection=new net.minecraft.client.Minecraft.Connection();InventoryUtils.totems=0;AutoLeaveHack a=new AutoLeaveHack();a.setEnabled(true);toggle(a,"lowHealth",false);return a;}
public static void main(String[] args)throws Exception {
AutoLeaveHack a=fresh();toggle(a,"lowHealth",true);mc.player.health=8;number(a,"totems",0);InventoryUtils.totems=1;a.onUpdate();check(mc.level.disconnects==0,"legacy health gate preserves totems");
InventoryUtils.totems=0;a.onUpdate();check(mc.level.disconnects==1&&!a.isEnabled(),"health threshold equality and disable");
a=fresh();toggle(a,"lowHealth",true);mc.player.health=0;a.onUpdate();check(mc.level.disconnects==0,"dead player ignored");
a=fresh();toggle(a,"portalTimeout",true);number(a,"portalTicks",2);mc.player.portal=true;a.onUpdate();a.onUpdate();check(mc.level.disconnects==0,"portal equality");
mc.player.portal=false;a.onUpdate();mc.player.portal=true;a.onUpdate();a.onWorldChange(mc.level);a.onUpdate();a.onUpdate();check(mc.level.disconnects==0,"portal interrupted and world reset");a.onUpdate();check(mc.level.disconnects==1,"portal continuous timeout");
a=fresh();toggle(a,"serverTimeout",true);toggle(a,"timeoutReconnect",true);stale(a);a.onReceivedPacket(new PacketInputListener.PacketInputEvent(new ClientboundSetTimePacket()));a.onUpdate();check(mc.level.disconnects==0,"time packet refresh");
stale(a);a.onWorldChange(mc.level);a.onUpdate();check(mc.level.disconnects==0,"timeout reset on join");stale(a);a.onUpdate();
AutoReconnectHack reconnect=WurstClient.INSTANCE.getHax().autoReconnectHack;check(mc.level.disconnects==1&&reconnect.isEnabled()&&reconnect.getWaitTicks()==1200,"timeout requests reconnect before leaving");
reconnect.onWorldChange(null);check(reconnect.isEnabled(),"disconnect retains reconnect");reconnect.onWorldChange(new ClientLevel());check(!reconnect.isEnabled()&&reconnect.getWaitTicks()==100,"reconnect restores settings");
a=fresh();toggle(a,"lowTotems",true);InventoryUtils.totems=3;a.onUpdate();check(mc.level.disconnects==0,"totem equality");InventoryUtils.totems=2;a.onUpdate();check(mc.level.disconnects==1,"independent totems while healthy");
a=fresh();toggle(a,"armorDamage",true);mc.player.armor[1].max=100;mc.player.armor[1].damage=96;mc.player.armor[1].item=net.minecraft.world.item.Items.ELYTRA;toggle(a,"ignoreElytra",true);a.onUpdate();check(mc.level.disconnects==0,"ignore elytra");toggle(a,"ignoreElytra",false);a.onUpdate();check(mc.level.disconnects==1,"elytra armor threshold");
a=fresh();toggle(a,"nearPosition",true);number(a,"positionRange",2);mc.player.x=2.5;mc.player.z=.5;a.onUpdate();check(mc.level.disconnects==0,"position boundary");mc.player.x=2.49;a.onUpdate();check(mc.level.disconnects==1,"position center");
a=fresh();toggle(a,"lowY",true);number(a,"yLevel",64);a.onUpdate();check(mc.level.disconnects==0,"height equality");mc.player.y=63;mc.player.abilities.instabuild=true;a.onUpdate();check(mc.level.disconnects==0,"creative guard");mc.player.abilities.instabuild=false;toggle(a,"disableAfterLeave",false);a.onUpdate();check(mc.level.disconnects==1&&a.isEnabled(),"height and retained enable");
a=fresh();toggle(a,"lowY",true);mc.player=null;a.onUpdate();mc.player=new net.minecraft.client.Minecraft.Player();mc.connection=null;a.onUpdate();check(mc.level.disconnects==0,"missing player or connection");
a.setEnabled(false);for(var listeners:net.wurstclient.hack.Hack.EVENTS.listeners.values())check(!listeners.contains(a),"event cleanup");
System.out.println("PASS AutoLeave: health/totem gates, portal timeout/reset, server timing/reconnect, armor, position, height, creative/null guards, event cleanup");
}}
''')
    classes = OUT / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    args = OUT / 'sources.txt'
    args.write_text('\n'.join('"'+p.as_posix()+'"' for p in SRC.rglob('*.java')), encoding='utf-8')
    results=[]
    for cmd in [['javac','-encoding','UTF-8','-d',str(classes),'@'+str(args)],['java','-cp',str(classes),'audit.AutoLeaveProbe']]:
        run=subprocess.run(cmd,capture_output=True,timeout=60)
        result=f'{cmd[0]} exit={run.returncode}\n'+run.stdout.decode('utf-8',errors='replace')+run.stderr.decode('utf-8',errors='replace')
        results.append(result)
        (OUT/'result.txt').write_text('\n'.join(results),encoding='utf-8')
        print(result)
        assert run.returncode==0
    (OUT/'provenance.json').write_text(json.dumps(provenance,indent=2),encoding='utf-8')

if __name__=='__main__':
    main()
