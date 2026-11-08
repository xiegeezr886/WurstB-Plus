"""Execute NoRotate and its real HEAD/RETURN callbacks without rewriting them."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from sync_four_client_batch import ROOT, PLAN


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--staged', action='store_true')
    parser.add_argument('--target', default='fabric')
    args = parser.parse_args()
    out = PLAN / 'batch13' / ('tests' if args.target == 'fabric' else 'tests-' + args.target.replace('/', '-'))
    src = out / 'src'

    def source(name, body):
        file = src / (name.replace('.', '/') + '.java')
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')

    root = (PLAN / 'batch13/generated' if args.staged else ROOT) / args.target / 'src/main/java/net/wurstclient'
    provenance = {}
    for relative in ['hacks/NoRotateHack.java', 'mixin/NoRotatePacketMixin.java', 'hacks/PortalGodModeHack.java', 'hacks/XCarryHack.java']:
        original = root / relative
        if relative == 'hacks/XCarryHack.java':
            original = ROOT / args.target / 'src/main/java/net/wurstclient' / relative
        dest = src / 'net/wurstclient' / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, dest)
        provenance[relative] = hashlib.sha256(original.read_bytes()).hexdigest()
    bodies = {
        'net.wurstclient.Category': 'public enum Category {MOVEMENT,OTHER,ITEMS}',
        'net.wurstclient.SearchTags': 'public @interface SearchTags {String[] value();}',
        'net.wurstclient.settings.CheckboxSetting': 'public class CheckboxSetting {public boolean value;public CheckboxSetting(String n,String d,boolean v){value=v;}public boolean isChecked(){return value;}}',
        'org.spongepowered.asm.mixin.Mixin': 'public @interface Mixin {Class<?> value();}',
        'org.spongepowered.asm.mixin.injection.At': 'public @interface At {String value();}',
        'org.spongepowered.asm.mixin.injection.Inject': 'public @interface Inject {String[] method();At at();}',
        'org.spongepowered.asm.mixin.injection.callback.CallbackInfo': 'public class CallbackInfo {}',
        'com.llamalad7.mixinextras.sugar.Share': 'public @interface Share {String value();}',
        'com.llamalad7.mixinextras.sugar.ref.LocalRef': 'public class LocalRef<T> {private T value;public T get(){return value;}public void set(T v){value=v;}}',
        'net.minecraft.client.multiplayer.ClientPacketListener': 'public class ClientPacketListener extends net.wurstclient.mixin.NoRotatePacketMixin {public final java.util.List<Object> sent=new java.util.ArrayList<>();public boolean fail;public void send(Object packet){if(fail)throw new IllegalStateException("fixture");sent.add(packet);}}',
        'net.minecraft.client.Minecraft': 'public class Minecraft {public net.minecraft.client.player.LocalPlayer player=new net.minecraft.client.player.LocalPlayer();public net.minecraft.client.multiplayer.ClientPacketListener connection=new net.minecraft.client.multiplayer.ClientPacketListener();public net.minecraft.client.multiplayer.ClientPacketListener getConnection(){return connection;}}',
        'net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket': 'public record ServerboundAcceptTeleportationPacket(int id) {}',
        'net.minecraft.network.protocol.game.ServerboundContainerClosePacket': 'public record ServerboundContainerClosePacket(int getContainerId) {public int containerId(){return getContainerId;}}',
        'net.wurstclient.events.PacketOutputListener': 'public interface PacketOutputListener {void onSentPacket(PacketOutputEvent event);public static class PacketOutputEvent {public Object packet;public boolean cancelled;public PacketOutputEvent(Object p){packet=p;}public Object getPacket(){return packet;}public void cancel(){cancelled=true;}}}',
    }
    for name, body in bodies.items():
        source(name, body)
    source('net.minecraft.client.player.LocalPlayer', '''public class LocalPlayer {
public float yaw=40,pitch=20,yRotO,xRotO,yHeadRot,yHeadRotO,yBodyRot,yBodyRotO;public double x,y,z,xo,yo,zo;
public float getYRot(){return yaw;}public float getXRot(){return pitch;}public void setYRot(float v){yaw=v;}public void setXRot(float v){pitch=v;}}''')
    source('net.wurstclient.WurstClient', '''public class WurstClient {
public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();public static final WurstClient INSTANCE=new WurstClient();
public final Hacks hax=new Hacks();public Hacks getHax(){return hax;}public static class Hacks {public net.wurstclient.hacks.NoRotateHack noRotateHack;}}''')
    source('net.wurstclient.hack.Hack', '''public class Hack {
protected static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;protected static final Events EVENTS=new Events();private boolean enabled;
public Hack(String n){}protected void setCategory(Object o){}protected void addSetting(Object o){}protected void onEnable(){}protected void onDisable(){}public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;if(v)onEnable();else onDisable();}
public static class Events {public void add(Object a,Object b){}public void remove(Object a,Object b){}}}''')
    source('audit.Batch13Probe', '''
import java.lang.reflect.*;import net.wurstclient.WurstClient;import net.wurstclient.hacks.NoRotateHack;import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.mixin.NoRotatePacketMixin;import com.llamalad7.mixinextras.sugar.ref.LocalRef;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
public class Batch13Probe {
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
static void bool(Object o,String n,boolean v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);((CheckboxSetting)f.get(o)).value=v;}
static void callback(NoRotatePacketMixin hook,String name,LocalRef<NoRotateHack.SavedRotation> state)throws Exception{Method m=NoRotatePacketMixin.class.getDeclaredMethod(name,CallbackInfo.class,LocalRef.class);m.setAccessible(true);m.invoke(hook,new CallbackInfo(),state);}
public static void main(String[] args)throws Exception{
var mc=WurstClient.MC;var p=mc.player;NoRotateHack h=new NoRotateHack();WurstClient.INSTANCE.hax.noRotateHack=h;check(h.capture()==null,"disabled captures nothing");h.setEnabled(true);
p.yRotO=35;p.xRotO=15;p.yHeadRot=32;p.yHeadRotO=31;p.yBodyRot=28;p.yBodyRotO=27;
NoRotatePacketMixin hook=new net.minecraft.client.multiplayer.ClientPacketListener();LocalRef<NoRotateHack.SavedRotation> state=new LocalRef<>();callback(hook,"wurstCaptureView",state);p.yaw=120;p.pitch=80;p.yRotO=120;p.xRotO=80;p.yHeadRot=120;p.yHeadRotO=120;p.yBodyRot=120;p.yBodyRotO=120;p.x=500;p.xo=499;callback(hook,"wurstRestoreView",state);
check(p.yaw==40&&p.pitch==20&&p.yRotO==35&&p.xRotO==15&&p.yHeadRot==32&&p.yHeadRotO==31&&p.yBodyRot==28&&p.yBodyRotO==27,"actual callbacks preserve independent view, head, body and interpolation angles");check(p.x==500&&p.xo==499,"teleport and position history preserved");
p.yaw=65;state=new LocalRef<>();callback(hook,"wurstCaptureView",state);p.yaw=-90;callback(hook,"wurstRestoreView",state);check(p.yaw==65,"next packet uses current mouse input");
bool(h,"preservePitch",false);var saved=h.capture();p.yaw=120;p.pitch=75;p.xRotO=73;h.restore(saved);check(p.yaw==65&&p.pitch==75&&p.xRotO==73,"yaw-only keeps server pitch and its interpolation");bool(h,"preserveYaw",false);bool(h,"preservePitch",true);saved=h.capture();p.yaw=170;p.yHeadRot=168;p.yHeadRotO=165;p.pitch=-60;p.xRotO=-60;h.restore(saved);check(p.yaw==170&&p.yHeadRot==168&&p.yHeadRotO==165&&p.pitch==75&&p.xRotO==73,"pitch-only keeps server yaw and head interpolation");
saved=h.capture();mc.player=new net.minecraft.client.player.LocalPlayer();mc.player.pitch=33;h.restore(saved);check(mc.player.pitch==33,"old player cannot restore after respawn");mc.player=null;check(h.capture()==null,"disconnect captures nothing");h.restore(saved);
mc.player=p;bool(h,"preserveYaw",true);bool(h,"preservePitch",true);p.yaw=10;LocalRef<NoRotateHack.SavedRotation> outer=new LocalRef<>(),inner=new LocalRef<>();callback(hook,"wurstCaptureView",outer);p.yaw=20;callback(hook,"wurstCaptureView",inner);p.yaw=30;callback(hook,"wurstRestoreView",inner);check(p.yaw==20,"nested invocation restores its own capture");callback(hook,"wurstRestoreView",outer);check(p.yaw==10,"outer capture survives nested invocation");
saved=h.capture();h.setEnabled(false);p.yaw=90;h.restore(saved);check(p.yaw==90,"disable during handler does not restore");h.setEnabled(true);bool(h,"preserveYaw",false);bool(h,"preservePitch",false);check(h.capture()==null,"both settings disabled preserve vanilla");
var portal=new net.wurstclient.hacks.PortalGodModeHack();portal.setEnabled(true);var connection=mc.connection;
for(int i=0;i<10000;i++){var packet=new net.wurstclient.events.PacketOutputListener.PacketOutputEvent(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(i));portal.onSentPacket(packet);check(packet.cancelled,"teleport confirmations intercepted");}
portal.setEnabled(false);check(connection.sent.size()==1&&((net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket)connection.sent.get(0)).id()==9999,"only latest confirmation released");
portal.setEnabled(true);portal.onSentPacket(new net.wurstclient.events.PacketOutputListener.PacketOutputEvent(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(4)));mc.connection=new net.minecraft.client.multiplayer.ClientPacketListener();portal.setEnabled(false);check(mc.connection.sent.isEmpty(),"old connection confirmation never replayed on another server");
portal.setEnabled(true);portal.onSentPacket(new net.wurstclient.events.PacketOutputListener.PacketOutputEvent(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(5)));mc.connection.fail=true;try{portal.setEnabled(false);throw new AssertionError("send should fail");}catch(IllegalStateException expected){}mc.connection.fail=false;portal.setEnabled(false);check(mc.connection.sent.isEmpty(),"failed release clears pending confirmation");
portal.setEnabled(true);mc.connection=null;var disconnected=new net.wurstclient.events.PacketOutputListener.PacketOutputEvent(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(6));portal.onSentPacket(disconnected);portal.setEnabled(false);check(!disconnected.cancelled,"disconnected callback safe");
var carry=new net.wurstclient.hacks.XCarryHack();carry.setEnabled(true);var own=new net.wurstclient.events.PacketOutputListener.PacketOutputEvent(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(0));var foreign=new net.wurstclient.events.PacketOutputListener.PacketOutputEvent(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(2));carry.onSentPacket(own);carry.onSentPacket(foreign);check(own.cancelled&&!foreign.cancelled,"XCarry matches source only own inventory");
System.out.println("PASS: immediate rotation callbacks, per-axis restoration, teleport preservation, connection-scoped confirmation release and XCarry filtering");}}
''')
    java = Path.home() / '.jdks/jdk25.0.4_10/bin'
    argfile = out / 'javac.args'
    argfile.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in src.rglob('*.java')), encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out / 'classes'), '@' + str(argfile)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(out / 'classes'), 'audit.Batch13Probe'], check=True)
    (out / 'provenance.json').write_text(json.dumps({'target': args.target, 'staged': args.staged, 'sha256': provenance}, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
