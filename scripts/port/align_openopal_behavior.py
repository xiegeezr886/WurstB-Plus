"""Restore reference behavior where the previous host port had changed it."""
import re
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects, replace, section


def aura_common(s,version):
    s=replace(s,'boolean throughWalls)\n\t{}','''boolean throughWalls, boolean yawFov) {
        public Config(double range, double fov, int flags, boolean throughWalls) {
            this(range, fov, flags, throughWalls, false);
        }
    }''')
    s=s.replace('if(!isInFov(entity, eyes, config.fov()))','if(!(config.yawFov() ? isInYawFov(entity, eyes, config.fov()) : isInFov(entity, eyes, config.fov())))')
    s=replace(s,'\tprivate static boolean isInFov(','''    private static boolean isInYawFov(LivingEntity entity, Vec3 eyes, double fov) {
        if(fov >= 180) return true;
        float yaw = net.wurstclient.opal.rotation.RotationHelper.getClientHandler()
            .getYawOr(WurstClient.MC.player.getYRot());
        return Math.abs(Mth.wrapDegrees(yaw - rotationTo(eyes, entity.position()).yaw())) < fov;
    }

	private static boolean isInFov(''')
    if version < (1,20,5):
        damage='''double damage = 0;
        for(var modifier : player.getMainHandItem().getAttributeModifiers(
            net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE))
            damage += modifier.getAmount();'''
    else:
        damage='''double damage = 0;
        var modifiers = player.getMainHandItem().getOrDefault(
            net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        for(var entry : modifiers.modifiers())
            if(entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                && entry.slot() == net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                damage += entry.modifier().amount();'''
    s=replace(s,'double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);',damage)
    return s


def scaffold(s):
    s=section(s,'\t\t// Manual jumps tower only', '\n\t\tif(updateY)', '''        boolean updateY = !sameY.isChecked()
            || autoJump.isChecked() && manualJump && !isRecentAutoJump()
            || MC.player.onGround() && !autoJumpActive
            || Math.abs(Math.floor(MC.player.getY() - sameYPos)) > 3
            || WURST.getHax().longJumpHack.isEnabled()
            || WURST.getHax().flightHack.isEnabled();''')
    s=s.replace('\t\t\tqueueSharedPlacement(true);\n','').replace('\t\tqueueSharedPlacement(false);\n','')
    s=section(s,'    public void onOpalMouseInput()', '\n\t@Override\n\tpublic void onMoveInput', '''    public void onOpalMouseInput() {
        if(!canRunMotion()) { clearMotionPlan(); return; }
        activeMode().onMouseInput();
        Mode effective = getEffectiveMode();
        if(effective == Mode.SILENCE_TELLY || effective == Mode.HEYPIXEL || effective == Mode.HYPIXEL) return;
        queueSharedPlacement(isTellyMode());
        if(pendingPlacement != null) {
            BlockHitResult hit = pendingPlacement;
            pendingPlacement = null;
            placeBlock(hit);
        }
        if(pendingSimulatedClick) {
            pendingSimulatedClick = false;
            simulateClick();
        }
        net.wurstclient.opal.MouseHelper.getRightButton().setDisabled();
    }
''')
    s=section(s,'\t\tBlockHitResult hitResult = pendingPlacement;', '\n\t@Override\n\tpublic void onRender', '\t}\n')
    s=section(s,'\t\t\t// Only simulate a click while', '\n\t\t\treturn;', '\t\t\tpendingSimulatedClick = isBlock;')
    s=section(s,'\t\telse if(useOverride)\n', '\n\t\telse\n', '''        else if(useOverride) {
            hitResult = blockCache.hit();
            MC.hitResult = hitResult;
        }''')
    s=s.replace('\tprivate long nextSimulatedClick;', '''    private final net.wurstclient.opal.CPSProperty simulationTiming =
        new net.wurstclient.opal.CPSProperty(interactCps::getValueI, () -> false);''')
    s=s.replace('\t\tlong now = System.currentTimeMillis();\n\t\tif(now < nextSimulatedClick)', '\t\tif(!net.wurstclient.opal.SwingDelay.isSwingAvailable(simulationTiming, false))')
    s=s.replace('\t\tnextSimulatedClick =\n\t\t\tnow + 1000L / Math.max(1, interactCps.getValueI());', '\t\tsimulationTiming.resetClick();\n\t\tnet.wurstclient.opal.SwingDelay.reset();')
    s=s.replace('\t\tif(!isFacingPlacement(hitResult))\n\t\t\treturn false;\n','')
    # Original's bypass state belongs to the main module and updates even in effective Vanilla mode.
    s=s.replace('\t\tScaffoldMode impl = activeMode();\n\t\tMode effective', '\t\tScaffoldMode impl = activeMode();\n\t\tif(impl instanceof HeypixelScaffoldMode heypixel) heypixel.updateUpTellyBypassState();\n\t\tMode effective')
    return s


def silence(s):
    s=s.replace(' * <p>OpenOpal\'s StuckModule / SkipTick rescue and outbound packet blockage are\n * not ported because Wurst has no equivalent subsystems; everything else\n * (target search, rescue rotation, eagle, parkour jump, block slot modes,\n * duplicate rotation, sensitivity fix, block fly and rotation abuse) is.\n','')
    s=s.replace('\t\trequestCurrentPlacement();\n\t}', '\t}')
    s=replace(s,'\t\tpreparePlacementTarget();\n\n\t\tif(!hack.isSilenceTellyTellySetting()', '''        if(placementArmed && placementArmedAge != player.tickCount) clearPlacementArmed();
        if(placementArmed && isSlotStillValid(placementArmedSlot)) {
            applySlot(placementArmedSlot);
            return;
        }
        clearPlacementArmed();
		preparePlacementTarget();

		if(!hack.isSilenceTellyTellySetting()''')
    s=replace(s,'        if(tryPlaceArmed()) invalidateSearchCache();', '''        net.wurstclient.opal.MouseHelper.getRightButton().setDisabled();
        boolean armed = placementArmed;
        if(tryPlaceArmed()) invalidateSearchCache();
        if(armed) preparePlacementTarget();
        requestCurrentPlacement();''')
    s=s.replace('\t\tif(!canPlace)\n', '\t\tif(!canPlace || isOutgoingPlacementBlocked())\n')
    s=s.replace('\t\tif(!placementArmed)\n', '\t\tif(isOutgoingPlacementBlocked()) { clearPlacementArmed(); return false; }\n\t\tif(!placementArmed)\n')
    s=s.replace('Vec3 armedEyePos = placementArmedEyePos;', 'Vec3 armedEyePos = WurstClient.MC.player.getEyePosition();')
    s=replace(s,'\t@Override\n\tpublic ScaffoldWalkHack.RotationRequest getPacketRotation()', '''    private boolean isOutgoingPlacementBlocked() {
        return WurstClient.INSTANCE.getHax().blinkHack.isEnabled();
    }

    @Override
    public void onPreMotion() {
        if(!hasCurrentPlacementRequest() || isOutgoingPlacementBlocked()) {
            packetRotation = null;
            return;
        }
        Vec3 eyePos = WurstClient.MC.player.getEyePosition();
        Rotation target = placementRequestRotation;
        if(!SilenceTellyRaycast.didHitBlockFace(eyePos, target,
            placementRequestData.pos(), placementRequestData.facing(), true)) {
            Rotation stable = SilenceTellyRotation.getClosestToBlockFace(
                placementRequestData.pos(), placementRequestData.facing(),
                ScaffoldUtils.getRotation(), eyePos, hack.silenceFixRotation.isChecked());
            if(stable == null) { packetRotation = null; return; }
            target = applyDuplicateRotation(stable);
            if(!SilenceTellyRaycast.didHitBlockFace(eyePos, target,
                placementRequestData.pos(), placementRequestData.facing(), true)) target = stable;
        }
        target = avoidDuplicateYawDelta(target, eyePos, placementRequestData);
        if(!SilenceTellyRaycast.didHitBlockFace(eyePos, target,
            placementRequestData.pos(), placementRequestData.facing(), true)) {
            packetRotation = null;
            return;
        }
        packetRotation = target;
        placementRequestEyePos = eyePos;
        hack.setPacketRotation(target, 1);
    }

	@Override
	public ScaffoldWalkHack.RotationRequest getPacketRotation()''')
    return s


def heypixel(s):
    s=s.replace('\t\tupdateUpTellyBypassState();\n','')
    s=s.replace('private void updateUpTellyBypassState()', 'public void updateUpTellyBypassState()')
    s=s.replace('player.getYRot()', 'net.wurstclient.opal.rotation.RotationHelper.getClientHandler().getYawOr(player.getYRot())')
    # Avoid duplicated qualified receiver in the return path.
    s=s.replace('WurstClient.MC.net.wurstclient.opal.rotation.RotationHelper.getClientHandler().getYawOr(player.getYRot())', 'net.wurstclient.opal.rotation.RotationHelper.getClientHandler().getYawOr(WurstClient.MC.player.getYRot())')
    s=re.sub(r'pendingPlace = ([^;]+);\n\t\t\treturn true;',r'return performPlace(\1);',s)
    s=s.replace('if(!hack.isFacingPlacement(hitResult)\n\t\t\t|| !hack.canPlaceAtHit(hitResult, hack.isTellyMode()))', 'if(!hack.canPlaceAtHit(hitResult, hack.isTellyMode()))')
    return s


def main():
    pending={}
    for p in projects():
        props=dict(line.split('=',1) for line in (p/'gradle.properties').read_text(encoding='utf-8-sig').splitlines() if '=' in line)
        version=tuple(map(int,props['minecraft_version'].split('.')))
        for name,fn in [('hacks/aura/OpenAura.java',lambda s:aura_common(s,version)),('hacks/ScaffoldWalkHack.java',scaffold),
            ('hacks/scaffold/SilenceTellyScaffoldMode.java',silence),('hacks/scaffold/HeypixelScaffoldMode.java',heypixel)]:
            path=p/PACKAGE/name
            pending[path]=fn(path.read_text(encoding='utf-8'))
        path=p/PACKAGE/'hacks/ScaffoldWalkHack.java'
        pending[path]=replace(pending[path],'\t\tRotation out = requestedRotation;', '\t\tactiveMode().onPreMotion();\n\t\tRotation out = requestedRotation;')
        path=p/PACKAGE/'hacks/scaffold/ScaffoldMode.java'
        pending[path]=replace(path.read_text(encoding='utf-8'),'\tdefault void onMouseInput() {}','\tdefault void onPreMotion() {}\n\n\tdefault void onMouseInput() {}')
        path=p/PACKAGE/'hacks/KillauraHack.java'
        s=path.read_text(encoding='utf-8').replace('fov.getValue() * 2','fov.getValue()')
        s=s.replace('targetFlags(), throughWalls.isChecked());','targetFlags(), throughWalls.isChecked(), true);')
        pending[path]=s
        path=p/PACKAGE/'hacks/scaffold/SimpleTowerMode.java'
        pending[path]=path.read_text(encoding='utf-8').replace('hitResult.getLocation().add(0, 1, 0)','hitResult.getLocation()')
        path=p/PACKAGE/'hacks/scaffold/OpenOpalScaffoldRecovery.java'
        s=path.read_text(encoding='utf-8')
        s=s.replace('    public void tryArmSkipTickRecovery() {', '''    private Object trackedPlayer, trackedWorld;
    public void tryArmSkipTickRecovery() {
        if(trackedPlayer != WurstClient.MC.player || trackedWorld != WurstClient.MC.level
            || hack.selfRescueMode.getSelected() == SelfRescueMode.DISABLED) {
            reset();
            trackedPlayer = WurstClient.MC.player;
            trackedWorld = WurstClient.MC.level;
        }''')
        pending[path]=s
    for path,s in pending.items():
        backup=PLAN/'before-behavior-alignment'/path.relative_to(ROOT)
        backup.parent.mkdir(parents=True,exist_ok=True)
        assert not backup.exists(),backup
        backup.write_bytes(path.read_bytes())
        path.write_text(s,encoding='utf-8',newline='\n')
    print('Aligned reference behavior:',len(pending),'files')


if __name__=='__main__':main()
