"""Restore source search/packet semantics after the earlier pipeline rewrite."""
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects, replace, section


def main():
    pending = {}
    for p in projects():
        def read(name): return (p/PACKAGE/name).read_text(encoding='utf-8')
        def put(name,s): pending[p/PACKAGE/name] = s
        s = read('hacks/ScaffoldWalkHack.java')
        s = section(s, '\t\t// The simulated fallback', '\n\t\t{\n\t\t\tPlayerSimulation', '\t\tif(blockCache == null)')
        s = s.replace('\t\t\t\t\tif(target.getY() > sameYPos - 1)\n\t\t\t\t\t\tcontinue;\n', '')
        s = section(s, '\t\t// A cache computed', '\n\t\tBlockHitResult hitResult', '')
        s = replace(s, '\t\tif(!isTellyMode())\n\t\t\treturn getBridgeCandidates(base, entity);\n\n', '')
        s = section(s, '\t/**\n\t * Normal (non-telly)', '\tprivate void addLastPlacedCandidate', '')
        s = section(s, '\t\t// Never place at or above', '\n\t\tboolean canUseCache', '')
        s = section(s, '\t\t\t\t// Bridging must never', '\n\t\t\t\t|| !ScaffoldUtils.isReplaceable(candidate.pos())', '')
        s = s.replace('\n\t\t\t\t\t|| !telly && next.getY() > origin.getY()', '')
        # Remove obsolete guards/methods from the discarded post-motion placement design.
        s = section(s, '\t/** Verifies the sent aim', '\n\t// ------------------------------------------------------------- shared tick', '')
        s = s.replace(' * deferred to {@link #onPostMotion()} so it runs after the movement packet,', ' * performed in the input phase, matching OpenOpal mouse handling,')
        put('hacks/ScaffoldWalkHack.java', s)
        s = read('hacks/scaffold/HeypixelScaffoldMode.java')
        s = section(s, '\t@Override\n\tpublic void onPostMotion()', '\tprivate boolean performPlace', '')
        s = s.replace('\tprivate BlockHitResult pendingPlace;\n', '').replace('\t\t\tpendingPlace = null;\n', '').replace('\t\tpendingPlace = null;\n', '')
        put('hacks/scaffold/HeypixelScaffoldMode.java', s)
        # Connection event fires after ordinary packet listeners and before send.
        s = read('events/ConnectionPacketOutputListener.java')
        s = replace(s, '\t\t\t\tif(isCancelled())\n\t\t\t\t\tbreak;\n\t\t\t}', '''\t\t\t\tif(isCancelled())
\t\t\t\t\tbreak;
\t\t\t}
            if(!isCancelled()) net.wurstclient.opal.OpenOpalPacketBridge.beforeSend(packet);''')
        put('events/ConnectionPacketOutputListener.java', s)
        put('opal/OpenOpalPacketBridge.java', '''/* OpenOpal InstantaneousSendPacketEvent adapter; GPL-3.0. */
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
''')
        put('mixin/OpenOpalMovementAccess.java', '''/* OpenOpal final movement-rotation correction; GPL-3.0. */
package net.wurstclient.mixin;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ServerboundMovePlayerPacket.class)
public interface OpenOpalMovementAccess {
    @Mutable @Accessor("yRot") void opal$setYaw(float yaw);
    @Mutable @Accessor("xRot") void opal$setPitch(float pitch);
}
''')
        config = next((p/'src/main/resources').glob('wurst*.mixins.json'))
        pending[config] = replace(config.read_text(encoding='utf-8-sig'), '"client": [', '"client": [\n    "OpenOpalMovementAccess",')
        s = read('hacks/scaffold/SilenceTellyScaffoldMode.java')
        s = replace(s, '\tprivate Rotation serverRotation;', '\tprivate Rotation serverRotation;\n    private Rotation observedMovementRotation;\n    private int observedMovementAge = -1;\n    private Vec3 observedPacketEye;')
        s = replace(s, '\t\tvar rotationFaker = WurstClient.INSTANCE.getRotationFaker();\n\t\tserverRotation = new Rotation(rotationFaker.getServerYaw(),\n\t\t\trotationFaker.getServerPitch());', '''        boolean observed = observedMovementRotation != null
            && observedMovementAge == WurstClient.MC.player.tickCount;
        if(observed) serverRotation = observedMovementRotation;
        else if(serverRotation == null) serverRotation = ScaffoldUtils.getRotation();
        Vec3 sentEye = observedPacketEye;
        observedMovementRotation = null;
        observedMovementAge = -1;
        observedPacketEye = null;''')
        s = s.replace('\t\tif(!hasCurrentPlacementRequest() || packetRotation == null)', '\t\tif(!hasCurrentPlacementRequest() || packetRotation == null || isOutgoingPlacementBlocked())')
        s = replace(s, '\t\tVec3 requestedEyePos = WurstClient.MC.player.getEyePosition();', '\t\tVec3 requestedEyePos = observed && sentEye != null ? sentEye : WurstClient.MC.player.getEyePosition();')
        s = replace(s, '    private boolean isOutgoingPlacementBlocked()', '''    /** Original SilenceTelly instantaneous packet hook, after all packet listeners. */
    public void onImmediateSend(net.minecraft.network.protocol.Packet<?> packet) {
        if(!ready() || !(packet instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket move)
            || !move.hasRotation()) return;
        var player = WurstClient.MC.player;
        Rotation outbound = new Rotation(move.getYRot(player.getYRot()), move.getXRot(player.getXRot()));
        Vec3 packetEye = new Vec3(move.getX(player.getX()), move.getY(player.getY()) + player.getEyeHeight(), move.getZ(player.getZ()));
        boolean placement = hasCurrentPlacementRequest() && packetRotation != null && placementRequestEyePos != null;
        if(placement && !SilenceTellyRaycast.didHitBlockFace(packetEye, outbound,
            placementRequestData.pos(), placementRequestData.facing(), true)) {
            var access = (net.wurstclient.mixin.OpenOpalMovementAccess)move;
            access.opal$setYaw(packetRotation.yaw());
            access.opal$setPitch(packetRotation.pitch());
            outbound = packetRotation;
        }
        if(placement) packetRotation = outbound;
        observedMovementRotation = outbound;
        observedMovementAge = player.tickCount;
        observedPacketEye = packetEye;
    }

    private boolean isOutgoingPlacementBlocked()''')
        s = replace(s, '\t\tserverRotation = null;', '\t\tserverRotation = null;\n        observedMovementRotation = null;\n        observedMovementAge = -1;\n        observedPacketEye = null;')
        put('hacks/scaffold/SilenceTellyScaffoldMode.java', s)
        # Preserve source VelocityMode's optional contract; host simple velocity
        # mode has the same defaults, without inventing knockback heuristics.
        put('opal/VelocityMode.java', '''/* OpenOpal VelocityMode KillAura integration defaults; GPL-3.0. */
package net.wurstclient.opal;
public interface VelocityMode {
    default boolean isAttacking() { return false; }
    default int getHitSelectSkips() { return 0; }
    default boolean consumeHitSelectSkip() { return false; }
}
''')
        put('hacks/AntiKnockbackHack.java', read('hacks/AntiKnockbackHack.java').replace('implements KnockbackListener', 'implements KnockbackListener, net.wurstclient.opal.VelocityMode'))
        s = read('hacks/KillauraHack.java')
        s = replace(s, '        if(!isAttackSwingAvailable(target))', '''        var velocity = WURST.getHax().antiKnockbackHack;
        if(hitSelect.isChecked() && velocity.isEnabled()) {
            if(velocity.getHitSelectSkips() > 0) { velocity.consumeHitSelectSkip(); return; }
            if(velocity.isAttacking()) return;
        }
        if(!isAttackSwingAvailable(target))''')
        put('hacks/KillauraHack.java', s)
        s = read('hacks/aura/OpenAura.java')
        # Renderer API differs; the host solid-box adapter has the same signature.
        start = s.index('\tpublic static void drawBox(')
        s = s[:start] + '''    public static void drawBox(PoseStack matrices, Vec3 pos, LivingEntity target, int color) {
        double half = target.getBbWidth() / 2.0D;
        AABB box = new AABB(pos.x - half, pos.y, pos.z - half,
            pos.x + half, pos.y + target.getBbHeight(), pos.z + half).move(RenderUtils.getCameraPos());
        RenderUtils.drawSolidBox(matrices, box, color & 0xFFFFFF | 0x40000000, true);
    }
}
'''
        put('hacks/aura/OpenAura.java', s)
    for path in pending:
        if path.exists(): assert not (PLAN/'before-fidelity-port'/path.relative_to(ROOT)).exists(),path
    for path,s in pending.items():
        if path.exists():
            b=PLAN/'before-fidelity-port'/path.relative_to(ROOT)
            b.parent.mkdir(parents=True,exist_ok=True)
            b.write_bytes(path.read_bytes())
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(s,encoding='utf-8',newline='\n')
    print('Applied source fidelity corrections:',len(pending),'files')


if __name__ == '__main__':main()
