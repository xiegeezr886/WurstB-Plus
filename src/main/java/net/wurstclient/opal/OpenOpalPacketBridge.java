/* OpenOpal InstantaneousSendPacketEvent adapter; GPL-3.0. */
package net.wurstclient.opal;
import net.minecraft.network.protocol.Packet;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.scaffold.SilenceTellyScaffoldMode;
public final class OpenOpalPacketBridge {
    private OpenOpalPacketBridge() {}
    public static void beforeSend(Packet<?> packet) {
        if(!WurstClient.INSTANCE.isEnabled() || WurstClient.INSTANCE.getHax() == null) return;
        var scaffold = WurstClient.INSTANCE.getHax().scaffoldWalkHack;
        if(scaffold.isEnabled() && scaffold.getScaffoldMode() instanceof SilenceTellyScaffoldMode mode)
            mode.onImmediateSend(packet);
    }
}
