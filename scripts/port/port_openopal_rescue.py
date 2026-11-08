"""Direct-source SkipTick recovery port and its Minecraft tick hook."""
import json
import re
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects, replace, section
from port_openopal_sources import REFERENCE


def method(text, name):
    match = re.search(r'    (?:public|private|protected) [^\n]+\b' + name + r'\([^\n]*\) \{', text)
    assert match, name
    end = match.end()
    depth = 1
    while depth:
        if text[end] == '{': depth += 1
        if text[end] == '}': depth -= 1
        end += 1
    return text[match.start():end]


def adapt(text):
    replacements = {
        'mc.player': 'WurstClient.MC.player', 'mc.world': 'WurstClient.MC.level',
        'mc.options.jumpKey.isPressed()': 'WurstClient.MC.options.keyJump.isDown()',
        'Vec2f': 'Rotation', 'Vec3d': 'Vec3',
        'BlockPos.ofFloored': 'BlockPos.containing', '.getBlockPos()': '.blockPosition()',
        '.getEyePos()': '.getEyePosition()', '.getVelocity()': '.getDeltaMovement()',
        '.isOnGround()': '.onGround()', '.isReplaceable()': '.canBeReplaced()',
        '.getMainHandStack()': '.getMainHandItem()',
        '.getOpposite()': '.getOpposite()', 'target.offset(dir)': 'target.relative(dir)',
        'targetPos.offset(direction)': 'targetPos.relative(direction)',
        'playerPos.add(x, y, z)': 'playerPos.offset(x, y, z)',
        'eyePos.getX()': 'eyePos.x', 'eyePos.getY()': 'eyePos.y', 'eyePos.getZ()': 'eyePos.z',
        'ScaffoldSettings.Mode': 'ScaffoldWalkHack.Mode',
        'ScaffoldSettings.SelfRescueMode': 'SelfRescueMode',
        'this.settings.getSelfRescueMode()': 'hack.selfRescueMode.getSelected()',
        'this.settings.getMode().is(ScaffoldWalkHack.Mode.HEYPIXEL)': 'hack.mode.getSelected() == ScaffoldWalkHack.Mode.HEYPIXEL',
        'this.settings.isTelly()': 'hack.uitemsTelly.isChecked()',
        'this.getEffectiveMode()': 'hack.getEffectiveMode()',
        'getPlaceableBlock() != -1': 'hack.hasHotbarPlaceableBlock()',
        'RotationUtility.getRotationFromBlock(supportPos, face)': 'rotationFromBlock(supportPos, face)',
        'RotationUtility.getRotationDifference(currentRotation, rotation)': '(float)currentRotation.getAngleTo(rotation)',
        'RotationHelper.getClientHandler().getRotation()': 'ScaffoldUtils.getRotation()',
        'this.getActiveMode()': 'hack.getScaffoldMode()',
        'HeypixelScaffold heypixelScaffold': 'HeypixelScaffoldMode heypixelScaffold',
    }
    for old,new in replacements.items(): text=text.replace(old,new)
    return text


def recovery_class():
    reference = (REFERENCE / 'client/feature/module/impl/world/scaffold/ScaffoldModule.java').read_text(encoding='utf-8')
    names = ['updateSkipTickRecoveryGroundState','findRecoveryBlock','findReadySkipTickRecoveryTarget',
             'getCurrentClientRotation','isFallingIntoVoid','shouldTriggerSkipTickRecovery','tryArmSkipTickRecovery',
             'getSkipTickRecoveryBlockPos','getSkipTickRecoveryFace','isSkipTickRecoveryActive',
             'setSkipTickRecoveryActive','markSkipTickRecoveryFailed']
    methods = '\n\n'.join(adapt(method(reference, name)) for name in names)
    methods = re.sub(r'        final StuckModule stuckModule = [^\n]+;\n', '', methods)
    methods = methods.replace('!stuckModule.isEnabled() && !this.skipTickRecoveryActive', '!this.skipTickRecoveryActive')
    methods = re.sub(r'\s*if \(stuckModule.isEnabled\(\)\) \{\s*stuckModule.setEnabled\(false\);\s*}', '', methods)
    methods = methods.replace('private void updateSkipTickRecoveryGroundState()', 'public void updateSkipTickRecoveryGroundState()')
    methods = methods.replace('private void tryArmSkipTickRecovery()', 'public void tryArmSkipTickRecovery()')
    return '''/*
 * Direct port of OpenOpal ScaffoldModule's SkipTick recovery methods (GPL-3.0).
 * Source: reference/clients/OpenOpal; see META-INF/licenses/openopal/LICENSE.
 */
package net.wurstclient.hacks.scaffold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.ScaffoldWalkHack;
import net.wurstclient.opal.SkipTickUtility;
import net.wurstclient.util.Rotation;

public final class OpenOpalScaffoldRecovery {
    private static final Direction[] DIRECTIONS = Direction.values();
    private final ScaffoldWalkHack hack;
    private boolean skipTickRecoveryActive, skipTickRecoveryFailed;
    private int skipTickRecoveryCandidateTicks;
    private BlockPos skipTickRecoveryBlockPos;
    private Direction skipTickRecoveryFace;
    public OpenOpalScaffoldRecovery(ScaffoldWalkHack hack) { this.hack = hack; }

    public void reset() {
        setSkipTickRecoveryActive(false);
        skipTickRecoveryFailed = false;
    }

    private boolean isSolidAndNonInteractive(BlockState state, BlockPos pos) {
        return WurstClient.MC.level != null
            && !state.getCollisionShape(WurstClient.MC.level, pos).isEmpty()
            && state.getMenuProvider(WurstClient.MC.level, pos) == null;
    }

    private Rotation rotationFromBlock(BlockPos pos, Direction face) {
        float xDiff = (float)(pos.getX() + 0.5 - WurstClient.MC.player.getX() + face.getStepX() * 0.5);
        float yDiff = (float)(WurstClient.MC.player.getEyeY() - pos.getY() - face.getStepY() * 0.5);
        float zDiff = (float)(pos.getZ() + 0.5 - WurstClient.MC.player.getZ() + face.getStepZ() * 0.5);
        double distance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
        return new Rotation((float)Math.toDegrees(-Math.atan2(xDiff, zDiff)),
            (float)Math.toDegrees(Math.atan(yDiff / distance)));
    }

''' + methods + '''
    private record SkipTickRecoveryTarget(BlockPos targetPos, BlockPos supportPos,
        Direction face, Rotation rotation, float rotationDifference, double distanceSq) {}

    public enum SelfRescueMode {
        DISABLED("Disabled"), SKIP_TICK("SkipTick");
        private final String name;
        SelfRescueMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }
}
'''


def heypixel(text):
    ref = (REFERENCE / 'client/feature/module/impl/world/scaffold/mode/HeypixelScaffold.java').read_text(encoding='utf-8')
    names = ['handleSkipTickRecovery','applyPlacementRotation','runSkidRescueRecursive',
             'computeReachable','predictYAfterTicks','sendRescueRotationPacket','shouldSuppressSkipTickRecoveryTrigger']
    methods = '\n\n'.join(adapt(method(ref, name)) for name in names)
    methods = methods.replace('module.', 'hack.recovery.')
    methods = methods.replace('this.packetRotation = rotation;\n        this.packetRotationTicks = 2;', 'hack.setPacketRotation(rotation, 2);')
    methods = methods.replace('RotationHelper.getHandler().rotate(rotation, new HeypixelRotationModel(rotateSpeed));', 'rotateTo(rotation, rotateSpeed, false);')
    methods = methods.replace('sendPacketSilent(new PlayerMoveC2SPacket.LookAndOnGround(rotation.x, rotation.y, WurstClient.MC.player.onGround(), false));', 'rotation.sendPlayerLookPacket();')
    methods = methods.replace('final boolean placed = place(false);', 'sendRescueRotationPacket(rotation);\n        final boolean placed = performPlace(new BlockHitResult(getVec3(blockPos, enumFacing), enumFacing, blockPos, false));')
    methods = methods.replace('            place(false);', '            performPlace(new BlockHitResult(getVec3(blockPos, enumFacing), enumFacing, blockPos, false));')
    methods = methods.replace('MoveUtility.isMoving()', 'ScaffoldUtils.isMoving()')
    methods = methods.replace('SkipTickUtility.', 'net.wurstclient.opal.SkipTickUtility.')
    text = section(text, '\t// Kept for parity with the reference;', '\n}', methods)
    text = replace(text, '\tprivate int rotateCount;', '\tprivate int rotateCount;\n\tprivate int skipRecoveryAttempts, skipRecoveryActiveTicks, skipRecoverySkipTicks, skipRecoveryNoPlaceTicks;')
    text = replace(text, '\t\tgetBlockInfo();\n\n\t\tif(isSafeWalkEnabled()', '\t\tgetBlockInfo();\n\t\tif(handleSkipTickRecovery())\n\t\t\treturn;\n\n\t\tif(isSafeWalkEnabled()')
    text = replace(text, '\t\tstuckTicks = 0;\n\t\thack.notePlaced', '\t\tstuckTicks = 0;\n\t\tskipRecoveryAttempts = 0;\n\t\thack.notePlaced')
    return text


def scaffold(text):
    text = replace(text, 'import net.wurstclient.hacks.scaffold.HeypixelScaffoldMode;', '''import net.wurstclient.hacks.scaffold.HeypixelScaffoldMode;
import net.wurstclient.hacks.scaffold.OpenOpalScaffoldRecovery;
import net.wurstclient.hacks.scaffold.OpenOpalScaffoldRecovery.SelfRescueMode;''')
    text = replace(text, '\tpublic final CheckboxSetting safeWalk =', '''	public final OpenOpalScaffoldRecovery recovery = new OpenOpalScaffoldRecovery(this);
	public final EnumSetting<SelfRescueMode> selfRescueMode = new EnumSetting<>(
		"Self rescue mode", SelfRescueMode.values(), SelfRescueMode.DISABLED);
	public ScaffoldMode getScaffoldMode() { return activeMode(); }
	public final CheckboxSetting safeWalk =''')
    text = replace(text, '\t\taddSetting(safeWalk);', '\t\taddSetting(safeWalk);\n\t\taddSetting(selfRescueMode);')
    text = replace(text, '\tprotected void onEnable()\n\t{', '\tprotected void onEnable()\n\t{\n\t\trecovery.reset();')
    text = replace(text, '\tprotected void onDisable()\n\t{', '\tprotected void onDisable()\n\t{\n\t\trecovery.reset();')
    text = replace(text, '\t\tpruneRecentPlacedBlocks();', '\t\tpruneRecentPlacedBlocks();\n\t\trecovery.tryArmSkipTickRecovery();\n\t\trecovery.updateSkipTickRecoveryGroundState();')
    return text


MIXIN = '''/* Direct adaptation of OpenOpal ClientWorldMixin.hookSkipTicks (GPL-3.0). */
package net.wurstclient.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.SkipTickUtility;

@Mixin(ClientLevel.class)
public class OpenOpalSkipTickMixin {
    @WrapOperation(method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void opalSkipTick(Entity entity, Operation<Void> original) {
        if(entity == WurstClient.MC.player && WurstClient.INSTANCE.isEnabled()
            && WurstClient.INSTANCE.getHax().scaffoldWalkHack.isEnabled()
            && SkipTickUtility.consumeSkipTick())
            return;
        original.call(entity);
    }
}
'''


def main():
    pending = {}
    recovery = recovery_class()
    for project in projects():
        for name, transform in [('hacks/ScaffoldWalkHack.java', scaffold), ('hacks/scaffold/HeypixelScaffoldMode.java', heypixel)]:
            path = project / PACKAGE / name
            pending[path] = transform(path.read_text(encoding='utf-8'))
        pending[project / PACKAGE / 'hacks/scaffold/OpenOpalScaffoldRecovery.java'] = recovery
        pending[project / PACKAGE / 'mixin/OpenOpalSkipTickMixin.java'] = MIXIN
        configs = list((project / 'src/main/resources').glob('wurst*.mixins.json'))
        assert len(configs) == 1, configs
        config = configs[0]
        original = config.read_text(encoding='utf-8-sig')
        assert 'OpenOpalSkipTickMixin' not in original
        pending[config] = replace(original, '"client": [', '"client": [\n    "OpenOpalSkipTickMixin",')
    for path, content in pending.items():
        if path.exists():
            backup = PLAN / 'before-rescue-port' / path.relative_to(ROOT)
            backup.parent.mkdir(parents=True, exist_ok=True)
            assert not backup.exists()
            backup.write_bytes(path.read_bytes())
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8', newline='\n')
    print('Applied recovery source and tick integration:', len(pending), 'files.')


if __name__ == '__main__':
    main()
