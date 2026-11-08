"""One-shot, preflighted corrections from source and bytecode comparison."""
import json
import re
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects, replace, section


def main():
    api = json.loads((PLAN/'mc-api.json').read_text(encoding='utf-8'))
    pending = {}
    for p in projects():
        def read(name):
            return (p/PACKAGE/name).read_text(encoding='utf-8')
        def put(name, s):
            pending[p/PACKAGE/name] = s
        props = dict(line.split('=',1) for line in (p/'gradle.properties').read_text(encoding='utf-8-sig').splitlines() if '=' in line)
        version = props['minecraft_version']
        # GUI extraction wrappers differ from the native 26.3 class.
        gui = re.search(r'import ([\w.]+\.GuiGraphics(?:Extractor)?);', read('events/GUIRenderListener.java')).group(1)
        put('hacks/scaffold/ScaffoldIsland.java', re.sub(r'import [\w.]+\.GuiGraphics(?:Extractor)?;', 'import '+gui+';', read('hacks/scaffold/ScaffoldIsland.java')))
        s = read('hacks/ScaffoldWalkHack.java')
        s = re.sub(r'onRenderGUI\([\w.]+\.GuiGraphics(?:Extractor)? context', 'onRenderGUI('+gui+' context', s)
        # Recreate per-mode state without invoking old-world cleanup packets.
        init_start = s.index('\t\tsameYPos =', s.index('protected void onEnable()'))
        init_end = s.index('\n\t\tEVENTS.add(MoveInputListener', init_start)
        state_init = s[init_start:init_end]
        mode_start = s.index('\t\tmodes.put(Mode.VANILLA')
        mode_end = s.index('\n\n\t\taddSetting(mode)', mode_start)
        mode_init = s[mode_start:mode_end]
        s = replace(s, '\tprivate Mode lastActiveMode;', '\tprivate Mode lastActiveMode;\n    private Object statePlayer, stateWorld;')
        s = replace(s, '\t\trecovery.reset();\n\t\tclearMotionPlan();\n\t\trotationQueue.start();', '\t\tstatePlayer = MC.player;\n\t\tstateWorld = MC.level;\n\t\trecovery.reset();\n\t\tclearMotionPlan();\n\t\trotationQueue.start();')
        s = replace(s, '\tpublic void onUpdate()\n\t{', '''\tpublic void onUpdate()
\t{
        if(statePlayer != MC.player || stateWorld != MC.level) {
            statePlayer = MC.player;
            stateWorld = MC.level;
            recovery.reset();
            clearMotionPlan();
            releaseSimulatedInput();
            net.wurstclient.opal.SlotHelper.getInstance().sync(true, true);
''' + state_init + '\n' + mode_init + '''
            activeMode().onEnable();
        }''')
        s = section(s, '\t/**\n\t * Computes this tick', '\t@Override\n\tpublic void onUpdate()', '\t/** Plans at the original pre-game-tick phase; clicks execute during input. */\n')
        # Heypixel owns its original smooth reverse on disable.
        s = replace(s, '\t\tlastActiveMode = null;\n\t\trotationHandler.reset();', '\t\tif(!(modes.get(lastActiveMode) instanceof HeypixelScaffoldMode))\n\t\t\trotationHandler.reset();\n\t\tlastActiveMode = null;')
        # Bloxd is a post-move action, not a post-packet action.
        s = replace(s, '    public void onOpalMouseInput()', '''    public void onOpalPostMove() {
        if(canRunMotion()) activeMode().onPostMove();
    }

    public void onOpalMouseInput()''')
        put('hacks/ScaffoldWalkHack.java', s)
        s = read('hacks/scaffold/ScaffoldMode.java').replace('\tdefault void onPostMotion()', '\tdefault void onPostMove() {}\n\n\tdefault void onPostMotion()')
        put('hacks/scaffold/ScaffoldMode.java', s)
        put('hacks/scaffold/SimpleTowerMode.java', read('hacks/scaffold/SimpleTowerMode.java').replace('public void onPostMotion()', 'public void onPostMove()'))
        put('mixin/OpenOpalPostMoveMixin.java', '''/* OpenOpal PostMoveEvent adapter; GPL-3.0. */
package net.wurstclient.mixin;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public class OpenOpalPostMoveMixin {
    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("RETURN"))
    private void opalPostMove(MoverType type, Vec3 delta, CallbackInfo ci) {
        if(!WurstClient.INSTANCE.isEnabled()) return;
        var scaffold = WurstClient.INSTANCE.getHax().scaffoldWalkHack;
        if(scaffold.isEnabled()) scaffold.onOpalPostMove();
    }
}
''')
        config = next((p/'src/main/resources').glob('wurst*.mixins.json'))
        pending[config] = replace(config.read_text(encoding='utf-8-sig'), '"client": [', '"client": [\n    "OpenOpalPostMoveMixin",')
        # Original Heypixel selection and recovery bookkeeping.
        s = read('hacks/scaffold/HeypixelScaffoldMode.java')
        s = replace(s, '\t\t\tstuckTicks = 0;\n\t\t\tlastValidPlaceRotation', '\t\t\tstuckTicks = 0;\n            skipRecoveryAttempts = skipRecoveryActiveTicks = skipRecoverySkipTicks = skipRecoveryNoPlaceTicks = 0;\n\t\t\tlastValidPlaceRotation')
        s = replace(s, '\t\thack.resetSlot();\n\t\toldSlot = -1;', '''\t\thack.resetSlot();
        var player = WurstClient.MC.player;
        if(player != null) {
            var client = net.wurstclient.opal.rotation.RotationHelper.getClientHandler();
            hack.rotationHandler.rotate(new Rotation(client.getYawOr(player.getYRot()),
                client.getPitchOr(player.getXRot())), ScaffoldRotationModel.HEYPIXEL, getRotateBackSpeed());
            hack.rotationHandler.reverse();
        } else hack.rotationHandler.reset();
        skipRecoveryActiveTicks = skipRecoverySkipTicks = skipRecoveryNoPlaceTicks = 0;
\t\toldSlot = -1;''')
        s = replace(s, '\t\tselectBlockSlot();', '''        if(hack.recovery.isSkipTickRecoveryActive()) {
            stuckTicks++;
            boolean hasBlock = false;
            for(int slot = 0; slot < 9; slot++)
                if(ScaffoldUtils.isPlaceableStack(player.getInventory().getItem(slot))) { hasBlock = true; break; }
            if(!hasBlock || stuckTicks > 10 && blockPos == null) {
                hack.recovery.setSkipTickRecoveryActive(false);
                stuckTicks = skipRecoveryAttempts = rotateCount = 0;
                skipRecoveryActiveTicks = skipRecoverySkipTicks = skipRecoveryNoPlaceTicks = 0;
            }
        } else {
            stuckTicks = skipRecoveryAttempts = rotateCount = 0;
            skipRecoveryActiveTicks = skipRecoverySkipTicks = skipRecoveryNoPlaceTicks = 0;
        }
\t\tselectBlockSlot();''')
        s = section(s, '\t\tRotation current = lastValidPlaceRotation', '\n\t\tBlockHitResult hitResult', '''        Rotation current = net.wurstclient.opal.rotation.RotationHelper.getHandler().isActive()
            ? net.wurstclient.opal.rotation.RotationHelper.getClientHandler().getRotation() : null;
        if(current == null) current = lastValidPlaceRotation != null
            ? lastValidPlaceRotation : ScaffoldUtils.getRotation();''')
        s = s.replace('float clientPitch = player.getXRot();', 'float clientPitch = net.wurstclient.opal.rotation.RotationHelper.getClientHandler().getPitchOr(player.getXRot());')
        s = s.replace('\t\tif(!hack.canPlaceAtHit(hitResult, hack.isTellyMode()))\n\t\t\treturn false;\n', '')
        s = s.replace('\t\tif(!hack.canPlaceThisTick())\n\t\t\treturn false;\n', '')
        put('hacks/scaffold/HeypixelScaffoldMode.java', s)
        # SilenceTelly's jump/cancellation/eagle runs on freshly computed input.
        s = read('hacks/scaffold/SilenceTellyScaffoldMode.java')
        s = section(s, '\t\tlastForward = player.input.', '\n\t\tif(placementRequestAge', '')
        s = replace(s, '\n\t\tif(!hack.isSilenceTellyTellySetting()\n\t\t\t|| blockSlot != null && isSlotStillValid(blockSlot))\n\t\t\ttryJump();', '')
        s = replace(s, '\tprivate void tryJump()', '''    @Override
    public void onMoveInput(net.wurstclient.events.MoveInputListener.MoveInputEvent event) {
        if(!ready()) return;
        lastForward = event.getForward();
        lastSideways = event.getSideways();
        if(movementCancelTicks > 0) {
            event.setForward(0); event.setSideways(0);
            movementCancelTicks--;
            return;
        }
        updateEagleSneak(event);
        if(hack.isSilenceTellyTellySetting() && blockSlot != null && isSlotStillValid(blockSlot))
            tryJump(event);
    }

\tprivate void tryJump(net.wurstclient.events.MoveInputListener.MoveInputEvent event)''')
        s = s.replace('if(!player.onGround() || player.isShiftKeyDown()\n\t\t\t|| !ScaffoldUtils.isMoving())', 'if(!player.onGround() || player.isShiftKeyDown() || event.isSneak()\n            || !ScaffoldUtils.isMoving() && Math.abs(event.getForward()) < 1.0E-4F && Math.abs(event.getSideways()) < 1.0E-4F)')
        s = replace(s, '\t\t\thack.requestJump();', '\t\t\t((net.wurstclient.mixin.LivingEntityAccessor)player).wurst_setNoJumpDelay(0);\n            event.setJump(true);')
        s = s.replace('private void updateEagleSneak()', 'private void updateEagleSneak(net.wurstclient.events.MoveInputListener.MoveInputEvent event)')
        s = replace(s, '\t\t\thack.requestSneak(true);', '\t\t\thack.requestSneak(true);\n            event.setSneak(true);')
        s = replace(s, '\t\tresetState();\n\t\tif(WurstClient.MC.player', '\t\tresetState();\n        blockFlyDesyncing = blockFlySwapped = false;\n        blockFlyTicks = 0;\n\t\tif(WurstClient.MC.player')
        s = replace(s, '\t\t\tclearTarget();\n\t\t\treturn;\n\t\t}\n\n\t\tupdatePosY();', '\t\t\tclearTarget();\n            hack.resetSlot();\n\t\t\treturn;\n\t\t}\n\n\t\tupdatePosY();')
        put('hacks/scaffold/SilenceTellyScaffoldMode.java', s)
        # Lookahead updates targets only. Rotation was already handled by mouse tick.
        s = read('hacks/KillauraHack.java')
        s = replace(s, '\tprivate void planTargets()\n\t{', '\tprivate void planTargets() { planTargets(true); }\n\n\tprivate void planTargets(boolean rotate)\n\t{')
        s = replace(s, '\t\tactions.schedule(selection);', '\t\tactions.schedule(selection);\n        if(!rotate) return;')
        s = replace(s, '\t\tif(tickLookahead.isChecked())\n\t\t\tplanTargets();', '\t\tif(tickLookahead.isChecked()) {\n\t\t\tplanTargets(false);\n            if(currentTarget != null) rotationQueue.setRotation(rotationHandler.getRotation());\n        }')
        s = s.replace('MC.level == null || MC.screen != null)', 'MC.level == null || MC.screen != null || MC.getOverlay() != null)')
        s = s.replace('target.entity.isBlocking()', 'isShielding(target.entity)').replace('if(!target.isBlocking())', 'if(!isShielding(target))')
        s = replace(s, '\tprivate boolean hasAxe()', '''    private boolean isShielding(LivingEntity target) {
        return target.isUsingItem() && target.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem;
    }

\tprivate boolean hasAxe()''')
        s = s.replace('1000.0D / attackCps.getValueI()', '1000.0D / attackTiming.getCPS()')
        s = s.replace('OpenAura.collect(config).isEmpty()', '!OpenAura.hasSwingTarget(config)')
        put('hacks/KillauraHack.java', s)
        s = read('hacks/aura/OpenAura.java')
        s = replace(s, '\t/** Rebuilds the hit', '''    /** Reference closest-distance test occurs before line-of-sight conversion. */
    public static boolean hasSwingTarget(Config config) {
        if(WurstClient.MC.player == null || WurstClient.MC.level == null) return false;
        var player = WurstClient.MC.player;
        Vec3 eyes = player.getEyePosition();
        return WurstClient.MC.level.getEntitiesOfClass(LivingEntity.class,
            player.getBoundingBox().inflate(config.range()), e -> isValidTarget(e, config.targetFlags()))
            .stream().anyMatch(e -> isInYawFov(e, eyes, config.fov())
                && eyes.distanceTo(closestPoint(eyes, e.getBoundingBox())) <= config.range());
    }

\t/** Rebuilds the hit''')
        put('hacks/aura/OpenAura.java', s)
        s = read('opal/SlotHelper.java').replace('    public boolean onScroll(double amount) {\n', '    public boolean onScroll(double amount) {\n        ensureContext();\n')
        for signature in ['public ItemStack getMainHandStack(LocalPlayer player)', 'public int getSelectedSlot(Inventory inventory)', 'public boolean isActive()']:
            s = s.replace(signature+' {', signature+' {\n        ensureContext();')
        put('opal/SlotHelper.java', s)
        if api[version]['invertX']:
            s = read('opal/rotation/RotationMouseHandler.java')
            s = s.replace('final double deltaX =', 'double deltaX =').replace('final double cursorDeltaX =', 'double cursorDeltaX =')
            s = replace(s, '            final double m = 0.15D;', '            if(MC.options.invertMouseX().get()) deltaX *= -1;\n            final double m = 0.15D;')
            s = replace(s, '        event.setDeltaX(cursorDeltaX);', '        if(MC.options.invertMouseX().get()) cursorDeltaX *= -1;\n        event.setDeltaX(cursorDeltaX);')
            put('opal/rotation/RotationMouseHandler.java', s)
            s = read('opal/rotation/ClientRotationHandler.java').replace('final double cursorX =', 'double cursorX =')
            s = replace(s, '            final float deltaYaw =', '            if(MC.options.invertMouseX().get()) cursorX *= -1;\n            final float deltaYaw =')
            put('opal/rotation/ClientRotationHandler.java', s)
        if version in ['1.21.2','1.21.3','1.21.4']:
            s = (ROOT/'fabric/versions/1.21.5'/PACKAGE/'mixin/OpenOpalScrollMixin.java').read_text(encoding='utf-8').replace('setSelectedSlot', 'setSelectedHotbarSlot')
            put('mixin/OpenOpalScrollMixin.java', s)
        if version == '26.2':
            put('mixin/OpenOpalHandsMixin.java', read('mixin/OpenOpalHandsMixin.java').replace('renderHandsWithItems', 'submitHandsWithItems'))
    for path, s in pending.items():
        if path.exists():
            backup = PLAN/'before-lifecycle-port'/path.relative_to(ROOT)
            assert not backup.exists(), backup
    for path, s in pending.items():
        if path.exists():
            backup = PLAN/'before-lifecycle-port'/path.relative_to(ROOT)
            backup.parent.mkdir(parents=True, exist_ok=True)
            backup.write_bytes(path.read_bytes())
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(s, encoding='utf-8', newline='\n')
    print('Applied lifecycle/source corrections:', len(pending), 'files')


if __name__ == '__main__': main()
