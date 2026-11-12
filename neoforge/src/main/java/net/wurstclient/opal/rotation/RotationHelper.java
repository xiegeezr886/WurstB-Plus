/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.opal.rotation;
import net.wurstclient.WurstClient;
import net.wurstclient.util.Rotation;
import net.wurstclient.mixin.OpenOpalMouseAccess;

public final class RotationHelper {
    private static final ClientRotationHandler clientHandler = new ClientRotationHandler();
    private static final RotationMouseHandler mouseHandler = new RotationMouseHandler();
    private static Object player, world;
    public static RotationMouseHandler getHandler() { return mouseHandler; }
    public static ClientRotationHandler getClientHandler() { return clientHandler; }
    public static Rotation getPlayerRotation() {
        var p = WurstClient.MC.player;
        return p == null ? new Rotation(0, 0) : new Rotation(p.getYRot(), p.getXRot());
    }
    public static double getCursorDelta(double delta, double multiplier) {
        return (float)(delta / multiplier) / 0.15F;
    }
    public static float getPartialTick() { return WurstClient.MC.getFrameTime(); }
    public static void forceMouseTick() {
        ((OpenOpalMouseAccess)WurstClient.MC.mouseHandler).opal$turnPlayer();
    }
    public static void preTick() {
        if(WurstClient.MC == null) return;
        if(player != WurstClient.MC.player || world != WurstClient.MC.level) {
            // A departed player's angles must never be copied onto a new player.
            clientHandler.setRotation(null);
            mouseHandler.discard();
            player = WurstClient.MC.player;
            world = WurstClient.MC.level;
        }
        if(!WurstClient.INSTANCE.isEnabled()) { mouseHandler.reset(); return; }
        mouseHandler.onPreTick();
        clientHandler.tickCamera();
    }
    public static void onMouseUpdate(MouseUpdateEvent event) {
        if(WurstClient.MC.player == null || !mouseHandler.isActive()) {
            clientHandler.setRotation(null);
            return;
        }
        clientHandler.onMouseUpdate(event);
        mouseHandler.onMouseUpdate(event);
    }
}
