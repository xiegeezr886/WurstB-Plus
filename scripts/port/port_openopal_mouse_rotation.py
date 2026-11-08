"""Direct source port of OpenOpal's mouse and visual rotation handlers."""
import re
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects, replace, section
from port_openopal_input import HEADER, original


def adapt(s):
    s = re.sub(r'import (?:wtf.opal|com.mojang.blaze3d.systems|net.minecraft.client.MinecraftClient)[^;]+;\n', '', s)
    s = re.sub(r'    @Subscribe(?:\([^\n]*\))?\n', '', s)
    s = s.replace(' implements IEventSubscriber', '')
    s = s.replace('        EventDispatcher.subscribe(this);\n', '')
    s = re.sub(r'package wtf.opal[^;]+;', 'package net.wurstclient.opal.rotation;', s)
    s = s.replace('net.minecraft.util.math.Vec2f', 'net.wurstclient.util.Rotation').replace('Vec2f', 'Rotation')
    s = s.replace('net.minecraft.util.math.MathHelper', 'net.minecraft.util.Mth').replace('MathHelper', 'Mth')
    s = s.replace('import static wtf.opal.client.Constants.mc;', 'import static net.wurstclient.WurstClient.MC;')
    s = s.replace('mc.', 'MC.')
    s = s.replace('MC.options.getInvertMouseY().getValue()', 'MC.options.invertYMouse().get()')
    s = s.replace('RotationUtility.getRotation()', 'RotationHelper.getPlayerRotation()')
    s = s.replace('RotationUtility.getCursorDelta(', 'RotationHelper.getCursorDelta(')
    s = s.replace('RotationUtility.getRotationDifference(', 'net.wurstclient.hacks.scaffold.ScaffoldUtils.getRotationDifference(')
    s = s.replace('Math.clamp(', 'net.minecraft.util.Mth.clamp(')
    for a,b in [('getYaw','getYRot'),('getPitch','getXRot'),('setYaw','setYRot'),('setPitch','setXRot'),('setBodyYaw','setYBodyRot'),('setHeadYaw','setYHeadRot')]:
        s = s.replace('.'+a+'(', '.'+b+'(')
    for a,b in [('lastRenderYaw','yBobO'),('renderYaw','yBob'),('lastRenderPitch','xBobO'),('renderPitch','xBob')]:
        s = s.replace('MC.player.'+a, 'MC.player.'+b)
    s = re.sub(r'\b(this\.)?(originalRotation|tickRotation|targetRotation|tickedRotation|rotation)\.x\b', r'\1\2.yaw()', s)
    s = re.sub(r'\b(this\.)?(originalRotation|tickRotation|targetRotation|tickedRotation|rotation)\.y\b', r'\1\2.pitch()', s)
    return HEADER + s


def mouse_handler():
    s = adapt(original('client/feature/helper/impl/player/rotation/handler/RotationMouseHandler.java'))
    s = s.replace('onPreTick(PreGameTickEvent event)', 'onPreTick()')
    s = s.replace('MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false)', 'RotationHelper.getPartialTick()')
    s = s.replace('MC.mouse.tick();', 'RotationHelper.forceMouseTick();')
    s = s.replace('private void forceTick()', 'public void forceTick()')
    s = s.replace('    public boolean isUnlockCursor() {', '    public boolean isForcingTick() { return this.ticked; }\n\n    public boolean isUnlockCursor() {')
    s = s.replace('        this.targetRotation = clientHandler.getRotation();', '        this.targetRotation = clientHandler.getRotation();\n        if(this.targetRotation == null) this.targetRotation = RotationHelper.getPlayerRotation();')
    s = s.replace('        this.targetRotation = targetRotation;', '        if(this.tickRotation == null) this.tickRotation = RotationHelper.getPlayerRotation();\n        this.targetRotation = targetRotation;')
    return s


def client_handler():
    return adapt(original('client/feature/helper/impl/player/rotation/handler/ClientRotationHandler.java'))


HELPER = HEADER + '''package net.wurstclient.opal.rotation;
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
    public static float getPartialTick() { return PARTIAL_TICK; }
    public static void forceMouseTick() {
        ((OpenOpalMouseAccess)WurstClient.MC.mouseHandler).opal$turnPlayer(MOUSE_ARGUMENT);
    }
    public static void preTick() {
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
'''

MOUSE_MIXIN = HEADER + '''package net.wurstclient.mixin;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.rotation.RotationHelper;
import net.wurstclient.opal.rotation.MouseUpdateEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(MouseHandler.class)
public class OpenOpalMouseMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Unique private boolean opalHandled;
    @Inject(method = "TURN_METHOD", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private void opalUpdate(MOUSE_PARAM CallbackInfo ci) {
        double sensitivity = WurstClient.MC.options.sensitivity().get() * 0.6F + 0.2F;
        double multiplier = sensitivity * sensitivity * sensitivity * 8.0D;
        MouseUpdateEvent event = new MouseUpdateEvent(accumulatedDX, accumulatedDY,
            multiplier, RotationHelper.getHandler().isUnlockCursor());
        RotationHelper.onMouseUpdate(event);
        accumulatedDX = event.getDeltaX();
        accumulatedDY = event.getDeltaY();
        opalHandled = event.isHandled();
    }
    @ModifyExpressionValue(method = "TURN_METHOD", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private boolean opalSmooth(boolean original) { return original && !opalHandled; }
    @ModifyExpressionValue(method = "TURN_METHOD", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isScoping()Z"))
    private boolean opalScope(boolean original) { return original && !opalHandled; }
    @Inject(method = "TURN_METHOD", at = @At("RETURN"))
    private void opalTail(MOUSE_PARAM CallbackInfo ci) {
        RotationHelper.getClientHandler().onPostMouseUpdate();
        opalHandled = false;
    }
OLD_CURSOR
}
'''

WRAPPER = HEADER + '''package net.wurstclient.hacks.scaffold;
import net.wurstclient.util.Rotation;
import net.wurstclient.opal.rotation.RotationHelper;
import net.wurstclient.opal.rotation.HypixelRotationModel;

/** Host setting adapter; all mouse state lives in the original OpenOpal handler. */
public final class ScaffoldRotationHandler {
    public void rotate(Rotation target, ScaffoldRotationModel model, float speed) {
        rotate(target, model, speed, 1.2D, 0.12D);
    }
    public void rotate(Rotation target, ScaffoldRotationModel model, float speed,
        double drift, double jitter) {
        RotationHelper.getHandler().rotate(target, model.create(speed, drift, jitter));
    }
    public void rotateHypixel(Rotation target, boolean diagonalYaw, int airTicks) {
        RotationHelper.getHandler().rotate(target, new HypixelRotationModel(diagonalYaw, airTicks));
    }
    public void reverse() { RotationHelper.getHandler().reverse(); }
    public void tickReverse() { /* RotationMouseHandler.onPreTick owns this phase. */ }
    public void reset() { RotationHelper.getHandler().reset(); }
    public boolean isActive() { return RotationHelper.getHandler().isActive(); }
    public Rotation getRotation() { return RotationHelper.getPlayerRotation(); }
}
'''

CAMERA = HEADER + '''package net.wurstclient.mixin;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.rotation.RotationHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(Camera.class)
public class OpenOpalCameraMixin {
    @Redirect(method = "CAMERA_METHOD", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float opalYaw(Entity entity, float delta) {
        return entity == WurstClient.MC.player
            ? RotationHelper.getClientHandler().getYawOr(entity.getViewYRot(delta)) : entity.getViewYRot(delta);
    }
    @Redirect(method = "CAMERA_METHOD", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float opalPitch(Entity entity, float delta) {
        return entity == WurstClient.MC.player
            ? RotationHelper.getClientHandler().getPitchOr(entity.getViewXRot(delta)) : entity.getViewXRot(delta);
    }
}
'''


def main():
    pending = {}
    for project in projects():
        props = dict(line.split('=',1) for line in (project/'gradle.properties').read_text(encoding='utf-8-sig').splitlines() if '=' in line)
        version = tuple(map(int, props['minecraft_version'].split('.')))
        old_mouse = version < (1,20,5)
        partial = 'WurstClient.MC.getFrameTime()' if version < (1,21) else 'WurstClient.MC.getDeltaTracker().getGameTimeDeltaPartialTick(false)'
        handler = mouse_handler()
        handler = handler.replace('    public void reset() {', '''    public void discard() {
        this.active = false;
        this.forward = false;
        this.targetRotation = null;
        this.rotationModel = null;
        this.originalRotation = null;
        this.tickRotation = null;
        this.unlockCursor = false;
    }

    public void reset() {''')
        helper = HELPER.replace('PARTIAL_TICK', partial).replace('MOUSE_ARGUMENT', '' if old_mouse else '0.0D')
        event = HEADER + original('event/impl/game/input/MouseUpdateEvent.java').replace('wtf.opal.event.impl.game.input', 'net.wurstclient.opal.rotation')
        mixin = MOUSE_MIXIN.replace('TURN_METHOD', 'turnPlayer()V' if old_mouse else 'turnPlayer(D)V').replace('MOUSE_PARAM', '' if old_mouse else 'double timeDelta,')
        cursor = '''    @ModifyExpressionValue(method = "turnPlayer()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean opalCursor(boolean original) {
        return original || RotationHelper.getHandler().isForcingTick();
    }'''
        mixin = mixin.replace('OLD_CURSOR', cursor if old_mouse else '')
        accessor = HEADER + '''package net.wurstclient.mixin;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(MouseHandler.class)
public interface OpenOpalMouseAccess {
    @Invoker("turnPlayer") void opal$turnPlayer(PARAM);
}
'''
        accessor = accessor.replace('PARAM', '' if old_mouse else 'double timeDelta')
        camera = CAMERA.replace('CAMERA_METHOD', 'alignWithEntity' if version >= (26,2) else 'setup')
        for name, text in {'opal/rotation/RotationMouseHandler.java':handler,
            'opal/rotation/ClientRotationHandler.java':client_handler(),
            'opal/rotation/RotationHelper.java':helper,'opal/rotation/MouseUpdateEvent.java':event,
            'mixin/OpenOpalMouseAccess.java':accessor,'mixin/OpenOpalMouseMixin.java':mixin,
            'mixin/OpenOpalCameraMixin.java':camera,'hacks/scaffold/ScaffoldRotationHandler.java':WRAPPER}.items():
            pending[project/PACKAGE/name] = text
        path = project/PACKAGE/'mixin/OpenOpalInputMixin.java'
        text = path.read_text(encoding='utf-8')
        text = text.replace('        if (!WurstClient.INSTANCE.isEnabled()', '        net.wurstclient.opal.rotation.RotationHelper.preTick();\n        if (!WurstClient.INSTANCE.isEnabled()',1)
        text = text.replace('import net.minecraft.network.protocol.game.ServerboundSwingPacket;', 'import net.wurstclient.settings.SwingHandSetting.SwingHand;')
        text = text.replace('player.connection.send(new ServerboundSwingPacket(hand))', 'SwingHand.SERVER.swing(hand)')
        if version >= (26,3):
            text = text[:text.index('    @Redirect(method = "startAttack()Z"')] + '}\n'
        pending[path] = text
        path = project/PACKAGE/'hacks/scaffold/ScaffoldUtils.java'
        text = path.read_text(encoding='utf-8')
        text = section(text, '\tpublic static Rotation getRotation()', '\n\t/**', '''    public static Rotation getRotation() {
        return net.wurstclient.opal.rotation.RotationHelper.getPlayerRotation();
    }
''')
        pending[path] = text
        config = next((project/'src/main/resources').glob('wurst*.mixins.json'))
        pending[config] = replace(config.read_text(encoding='utf-8-sig'), '"client": [', '"client": [\n    "OpenOpalMouseAccess",\n    "OpenOpalMouseMixin",\n    "OpenOpalCameraMixin",')
    for path, text in pending.items():
        if path.exists():
            backup = PLAN/'before-mouse-port'/path.relative_to(ROOT)
            backup.parent.mkdir(parents=True,exist_ok=True)
            assert not backup.exists(), backup
            backup.write_bytes(path.read_bytes())
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(text,encoding='utf-8',newline='\n')
    print('Mouse rotation source port:',len(pending),'files')


if __name__ == '__main__': main()
