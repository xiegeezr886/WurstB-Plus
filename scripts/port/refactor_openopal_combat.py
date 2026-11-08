"""Apply the OpenOpal pipeline refactor to the in-repository v1.5.1 matrix.

All replacements are checked before writing. Existing per-version APIs and
unrelated edits are preserved; the task directory keeps the pre-edit sources.
"""
from pathlib import Path
import argparse
import hashlib
import json

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / '.planning/2026-11-08-openopal-combat-v151'
PACKAGE = Path('src/main/java/net/wurstclient')


def replace(text, old, new):
    assert text.count(old) == 1, (old[:100], text.count(old))
    return text.replace(old, new)


def section(text, start, end, new):
    a = text.index(start)
    b = text.index(end, a)
    return text[:a] + new + text[b:]


def aura(text):
    text = text.replace('import java.util.HashMap;\n', '').replace('import java.util.Map;\n', '').replace('import java.util.List;\n', '')
    text = replace(text, 'import net.wurstclient.events.HandleInputListener;', 'import net.wurstclient.events.PreMotionListener;\nimport net.wurstclient.events.PostMotionListener;')
    text = replace(text, 'import net.wurstclient.hacks.aura.OpenAura;', 'import net.wurstclient.hacks.aura.OpenAura;\nimport net.wurstclient.hacks.aura.OpenAuraTargeting;\nimport net.wurstclient.util.MotionActionQueue;')
    text = replace(text, 'implements UpdateListener, HandleInputListener, RenderListener', 'implements UpdateListener, PreMotionListener, PostMotionListener, RenderListener')
    text = section(text, '\t/**\n\t * Entity id ->', '\tprivate final CheckboxSetting targetPlayers', '''	private final OpenAuraTargeting targeting = new OpenAuraTargeting();
	private final MotionActionQueue<OpenAuraTargeting.Selection> actions =
		new MotionActionQueue<>();

''')
    text = text.replace('\tprivate boolean pendingAttack;\n', '')
    text = text.replace('\t\tpendingAttack = false;', '\t\tactions.clear();')
    text = text.replace('\t\tattackRecords.clear();', '\t\ttargeting.reset();\n\t\tgrimKeepTicks = 0;')
    text = replace(text, 'EVENTS.add(HandleInputListener.class, this);', 'EVENTS.add(PreMotionListener.class, this);\n\t\tEVENTS.add(PostMotionListener.class, this);')
    text = replace(text, 'EVENTS.remove(HandleInputListener.class, this);', 'EVENTS.remove(PreMotionListener.class, this);\n\t\tEVENTS.remove(PostMotionListener.class, this);')
    text = section(text, '\t@Override\n\tpublic void onUpdate()', '\t@Override\n\tpublic void onRender(', '''	@Override
	public void onUpdate()
	{
		actions.clear();
		if(!requirementsMet())
		{
			clearTarget();
			return;
		}
		if(grimKeepTicks > 0)
		{
			grimKeepTicks--;
			MC.player.setSprinting(true);
		}
		actions.begin(MC.player, MC.level, MC.player.tickCount);
		planTargets();
	}

	private void planTargets()
	{
		OpenAuraTargeting.Selection selection = targeting.update(attackConfig(),
			rotationConfig(), mode.getSelected() == Mode.SWITCH);
		currentTarget = selection.attack();
		renderTarget = currentTarget == null ? null : currentTarget.entity;
		actions.schedule(selection);
		if(selection.rotation() == null)
		{
			rotationQueue.clear();
			return;
		}
		Rotation planned = selection.rotation().rotation;
		if(heypixelBypass.isChecked())
		{
			float jitter = (float)((Math.random() - 0.5D) * 0.1D);
			planned = Rotation.wrapped(planned.yaw() + jitter,
				planned.pitch() + jitter);
		}
		rotationQueue.setRotation(planned);
	}

	@Override
	public void onPreMotion()
	{
		if(!requirementsMet()
			|| !actions.isCurrent(MC.player, MC.level, MC.player.tickCount))
		{
			clearTarget();
			return;
		}
		// Refresh after movement, before the shared queue chooses the packet aim.
		if(tickLookahead.isChecked())
			planTargets();
	}

	@Override
	public void onPostMotion()
	{
		if(!requirementsMet())
		{
			clearTarget();
			return;
		}
		OpenAuraTargeting.Selection selection = actions.consume(MC.player,
			MC.level, MC.player.tickCount);
		if(selection == null)
			return;
		if(selection.attack() == null)
		{
			stopBlocking();
			tryFakeSwing();
			return;
		}

		OpenAura.Target target = targeting.revalidate(selection.attack(),
			attackConfig());
		if(target == null)
		{
			clearTarget();
			return;
		}
		currentTarget = target;
		tryAttack(target);
		updateAutoblock();
	}

	private void tryAttack(OpenAura.Target target)
	{
		if(!rotationQueue.wasApplied() || !isClickReady()
			|| !target.isAttackAvailable()
			|| hitSelect.isChecked() && MC.player.hurtTime > 0)
			return;
		var rotations = WURST.getRotationFaker();
		if(!rotations.hasServerRotation())
			return;
		Rotation sent = new Rotation(rotations.getServerYaw(),
			rotations.getServerPitch());
		EntityHitResult hit = OpenAura.raycastEntity(target.entity,
			MC.player.getEyePosition(), sent, getRange());
		if(hit == null || !throughWalls.isChecked() && !OpenAura.hasLineOfSight(
			MC.player.getEyePosition(), hit.getLocation()))
			return;
		if(!overrideRaycast.isChecked()
			&& (!(MC.hitResult instanceof EntityHitResult crosshair)
				|| crosshair.getEntity() != target.entity))
			return;

		if(smartWeapon.isChecked())
			switchToAxe(target.entity);
		// Switching weapons can change/reset the effective attack cooldown.
		if(!isClickReady() || !target.isAttackAvailable())
			return;
		if(MC.player.isUsingItem())
			MC.gameMode.releaseUsingItem(MC.player);
		boolean wasSprinting = MC.player.isSprinting();
		MC.gameMode.attack(MC.player, target.entity);
		SWING_MAIN_HAND
		if(keepSprintFov.isChecked() || grimKeepSprint.isChecked())
			keepSprint(wasSprinting);
		if(grimKeepSprint.isChecked() && wasSprinting)
			grimKeepTicks = 2;
		targeting.onAttack(target);
		nextAttackTime = System.currentTimeMillis() + clickDelay();
	}

'''.replace('SWING_MAIN_HAND', 'MC.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);' if 'import net.minecraft.world.item.component.SwingAnimation;' in text else 'MC.player.swing(InteractionHand.MAIN_HAND);'))
    text = replace(text, '\t\tif(isConsumingFoodOrPotion())\n', '\t\tif(MC.gameMode == null || !MC.player.isAlive()\n\t\t\t|| MC.player.isSpectator() || MC.player.isPassenger()\n\t\t\t|| WURST.getHax().scaffoldWalkHack.isEnabled())\n\t\t\treturn false;\n\t\tif(isConsumingFoodOrPotion())\n')
    text = replace(text, '\t\tif(MC.player != null && MC.player.isUsingItem()\n', '\t\tif(MC.player != null && MC.gameMode != null && MC.player.isUsingItem()\n')
    text = section(text, '\t/** Drops attack history', '\tpublic enum CpsMode', '')
    text = replace(text, '\tprivate void clearTarget()\n\t{', '\tprivate void clearTarget()\n\t{\n\t\ttargeting.reset();\n\t\tgrimKeepTicks = 0;')
    return text


def open_aura(text):
    text = section(text, '\t\tfor(LivingEntity entity : entities)', '\n\t/**\n\t * OpenOpal', '''		for(LivingEntity entity : entities)
		{
			Target target = target(entity, config);
			if(target != null)
				targets.add(target);
		}
		return targets;
	}

	/** Rebuilds the hit from current world state at both planning and execution. */
	public static Target target(LivingEntity entity, Config config)
	{
		if(WurstClient.MC.player == null || WurstClient.MC.level == null
			|| entity == null || !isValidTarget(entity, config.targetFlags())
			|| WurstClient.MC.level.getEntity(entity.getId()) != entity)
			return null;
		Vec3 eyes = WurstClient.MC.player.getEyePosition();
		if(!isInFov(entity, eyes, config.fov()))
			return null;
		Vec3 closest = closestPoint(eyes, entity.getBoundingBox()
			.inflate(entity.getPickRadius()));
		if(eyes.distanceTo(closest) > config.range())
			return null;
		return raycastTarget(entity, eyes, closest, config.range(),
			config.throughWalls());
	}
''')
    text = section(text, '\t\ttargets.sort(Comparator.comparingDouble(t ->', '\n\t\treturn targets.get(0);', '''		Comparator<Target> order = Comparator.comparingDouble((Target t) ->
			t.entity.getHealth() * (t.entity instanceof Player ? 1.0D : 2.0D));
		if(switchMode)
			order = Comparator.comparingLong(Target::getLastAttackTime)
				.thenComparing(order);
		targets.sort(order.thenComparingDouble(t -> t.distance)
			.thenComparingInt(t -> t.entity.getId()));
''')
    text = replace(text, '.min(Comparator.comparingDouble(t -> t.distance)).orElse(null);', '.min(Comparator.comparingDouble((Target t) -> t.distance)\n\t\t\t\t.thenComparingInt(t -> t.entity.getId())).orElse(null);')
    return text


def rotation_queue(text):
    text = replace(text, '\tprivate Rotation nextRotation;', '\tprivate Rotation nextRotation;\n\tprivate Object requestPlayer;\n\tprivate int requestTick;\n\tprivate boolean applied;')
    text = replace(text, '\tpublic void setRotation(Rotation target)\n\t{\n\t\tif(target == null)\n\t\t\treturn;', '\tpublic void setRotation(Rotation target)\n\t{\n\t\tif(target == null || MC.player == null\n\t\t\t|| !Float.isFinite(target.yaw()) || !Float.isFinite(target.pitch()))\n\t\t\treturn;')
    text = replace(text, '\t\t\tnextRotation = target;', '\t\t\tnextRotation = target;\n\t\t\trequestPlayer = MC.player;\n\t\t\trequestTick = MC.player.tickCount;')
    text = replace(text, '\t\t\trequestOrder = 0;', '\t\t\trequestOrder = 0;\n\t\t\trequestPlayer = null;\n\t\t\tapplied = false;')
    text = replace(text, '\tprivate boolean hasWork()', '''	/** True only for the winner of the latest movement phase. */
	public boolean wasApplied()
	{
		synchronized(LOCK)
		{
			return applied;
		}
	}

	private boolean hasWork()''')
    text = replace(text, '\t\tpublic void onPreMotion()\n\t\t{\n\t\t\tif(MC.player == null)\n\t\t\t\treturn;', '''		public void onPreMotion()
		{}

		@Override
		public void afterPreMotion()
		{''')
    text = replace(text, '\t\t\t\tRotationQueue owner = ACTIVE.stream()', '''				for(RotationQueue queue : ACTIVE)
				{
					queue.applied = false;
					if(MC.player == null)
						queue.clear();
					else if(queue.nextRotation != null
						&& (queue.requestPlayer != MC.player
							|| MC.player.tickCount < queue.requestTick
							|| (long)MC.player.tickCount - queue.requestTick > 1))
						queue.nextRotation = null;
				}
				RotationQueue owner = ACTIVE.stream()''')
    text = replace(text, '\t\t\t\tnext = owner == null ? null : owner.pollRotation();', '''				next = owner == null ? null : owner.pollRotation();
				if(owner != null)
					owner.applied = next != null;
				// A one-shot request that lost arbitration must never replay later.
				for(RotationQueue queue : ACTIVE)
					queue.nextRotation = null;''')
    return text


def pre_motion(text):
    text = replace(text, '\tpublic void onPreMotion();', '''	public void onPreMotion();

	/** Resolves shared requests after every module has published its plan. */
	default void afterPreMotion()
	{}''')
    return replace(text, '\t\t\t\tlistener.onPreMotion();', '\t\t\t\tlistener.onPreMotion();\n\t\t\tfor(PreMotionListener listener : listeners)\n\t\t\t\tlistener.afterPreMotion();')


def scaffold(text):
    text = replace(text, 'import net.wurstclient.util.Rotation;', 'import net.wurstclient.util.Rotation;\nimport net.wurstclient.util.RotationQueue;\nimport net.wurstclient.util.MotionActionQueue;')
    text = replace(text, '\tprotected void onEnable()\n\t{', '\tprotected void onEnable()\n\t{\n\t\tclearMotionPlan();\n\t\trotationQueue.start();')
    text = replace(text, '\tprotected void onDisable()\n\t{', '\tprotected void onDisable()\n\t{\n\t\tclearMotionPlan();\n\t\trotationQueue.stop();')
    text = section(text, '\tpublic void onUpdate()\n\t{', '\n\t\tif(mode.getSelected() != lastActiveMode)', '''	public void onUpdate()
	{
		motionActions.clear();
		pendingPlacement = null;
		pendingSimulatedClick = false;
		rotationQueue.clear();
		if(!canRunMotion())
		{
			clearMotionPlan();
			releaseSimulatedInput();
			return;
		}
''')
    text = replace(text, '\t\treleaseSimulatedJump();\n\t\tupdateGroundTicks();', '\t\tmotionActions.begin(MC.player, MC.level, MC.player.tickCount);\n\t\tmotionActions.schedule(lastActiveMode);\n\t\treleaseSimulatedJump();\n\t\tupdateGroundTicks();')
    text = replace(text, '\tpublic void onPreMotion()\n\t{\n\t\tif(MC.player == null)\n\t\t\treturn;', '''	public void onPreMotion()
	{
		if(!canRunMotion() || mode.getSelected() != lastActiveMode
			|| !motionActions.isCurrent(MC.player, MC.level, MC.player.tickCount))
		{
			clearMotionPlan();
			return;
		}''')
    text = replace(text, '\t\t\tWURST.getRotationFaker().setRotationPacket(out);', '\t\t\trotationQueue.setRotation(out);\n\t\tmotionRotationRequested = out != null;')
    text = replace(text, '\tpublic void onPostMotion()\n\t{\n\t\tactiveMode().onPostMotion();', '''	public void onPostMotion()
	{
		if(!canRunMotion())
		{
			clearMotionPlan();
			return;
		}
		Mode plannedMode = motionActions.consume(MC.player, MC.level,
			MC.player.tickCount);
		if(plannedMode == null || plannedMode != mode.getSelected()
			|| motionRotationRequested && !rotationQueue.wasApplied())
		{
			clearMotionPlan();
			return;
		}
		activeMode().onPostMotion();''')
    screen = 'MC.gui.screen()' if 'MC.gui.screen()' in text else 'MC.screen'
    text = replace(text, '\t// ------------------------------------------------------------- shared tick', '''	private final RotationQueue rotationQueue =
		new RotationQueue(RotationQueue.Priority.BLOCK_PLACEMENT);
	private final MotionActionQueue<Mode> motionActions = new MotionActionQueue<>();
	private boolean motionRotationRequested;

	private boolean canRunMotion()
	{
		return MC.player != null && MC.level != null && MC.gameMode != null
			&& SCREEN == null && MC.player.isAlive()
			&& !MC.player.isSpectator() && !MC.player.isFallFlying()
			&& !MC.player.isPassenger();
	}

	private void clearMotionPlan()
	{
		if(lastActiveMode != null)
			modes.get(lastActiveMode).cancelPendingPlacement();
		motionActions.clear();
		pendingPlacement = null;
		pendingSimulatedClick = false;
		motionRotationRequested = false;
		rotationQueue.clear();
		requestedRotation = null;
		packetRotation = null;
		packetRotationTicks = 0;
		rotation = null;
		blockCache = null;
		rotationHandler.reset();
		invalidateSearchCache();
	}

	/** Verifies the sent aim against the face immediately before interaction. */
	public boolean isFacingPlacement(BlockHitResult planned)
	{
		if(!canRunMotion() || planned == null)
			return false;
		var faker = WURST.getRotationFaker();
		if(!faker.hasServerRotation())
			return false;
		BlockHitResult hit = ScaffoldUtils.raycastBlock(
			new Rotation(faker.getServerYaw(), faker.getServerPitch()), getRange());
		return hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
			&& hit.getBlockPos().equals(planned.getBlockPos())
			&& hit.getDirection() == planned.getDirection();
	}

	// ------------------------------------------------------------- shared tick'''.replace('SCREEN', screen))
    text = replace(text, '\tpublic boolean placeBlock(BlockHitResult hitResult)\n\t{', '\tpublic boolean placeBlock(BlockHitResult hitResult)\n\t{\n\t\tif(!isFacingPlacement(hitResult))\n\t\t\treturn false;')
    return text


def heypixel(text):
    text = replace(text, '\t@Override\n\tpublic void onPostMotion()', '''	@Override
	public void cancelPendingPlacement()
	{
		pendingPlace = null;
	}

	@Override
	public void onPostMotion()''')
    return replace(text, '\tprivate boolean performPlace(BlockHitResult hitResult)\n\t{', '''	private boolean performPlace(BlockHitResult hitResult)
	{
		if(!hack.isFacingPlacement(hitResult)
			|| !hack.canPlaceAtHit(hitResult, hack.isTellyMode()))
			return false;''')


def scaffold_mode(text):
    return replace(text, '\tdefault void onPostMotion()\n\t{}', '''	default void onPostMotion()
	{}

	/** Drops a plan when its world, mode or rotation ownership is no longer valid. */
	default void cancelPendingPlacement()
	{}''')


def silence(text):
    return replace(text, '\t@Override\n\tpublic void onPostMotion()', '''	@Override
	public void cancelPendingPlacement()
	{
		clearPlacementRequest();
		clearPlacementArmed();
	}

	@Override
	public void onPostMotion()''')


def multi_aura(text):
    return replace(text, '\t\tif(MC.player == null || MC.level == null)\n\t\t\treturn;', '''		if(MC.player == null || MC.level == null || MC.gameMode == null
			|| !MC.player.isAlive() || MC.player.isSpectator()
			|| WURST.getHax().scaffoldWalkHack.isEnabled())
		{
			currentTarget = null;
			return;
		}''')


TRANSFORMS = {
    'hacks/KillauraHack.java': aura,
    'hacks/aura/OpenAura.java': open_aura,
    'util/RotationQueue.java': rotation_queue,
    'events/PreMotionListener.java': pre_motion,
    'hacks/ScaffoldWalkHack.java': scaffold,
    'hacks/scaffold/HeypixelScaffoldMode.java': heypixel,
    'hacks/scaffold/ScaffoldMode.java': scaffold_mode,
    'hacks/scaffold/SilenceTellyScaffoldMode.java': silence,
    'hacks/MultiAuraHack.java': multi_aura,
}
NEW_FILES = ['util/MotionActionQueue.java', 'hacks/aura/OpenAuraTargeting.java']


def projects():
    props = [ROOT / 'fabric/gradle.properties', ROOT / 'neoforge/gradle.properties']
    for folder in ['versions', 'fabric/versions', 'neoforge/versions']:
        props.extend((ROOT / folder).glob('*/gradle.properties'))
    return sorted(p.parent for p in props if '1.5.1' in p.read_text(encoding='utf-8-sig'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    manifest_path = PLAN / 'outputs.json'
    if args.check:
        manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
        changed = [name for name, digest in manifest.items()
                   if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != digest]
        assert not changed, changed
        print(f'Verified {len(manifest)} source hashes across {len(projects())} projects.')
        return
    assert not manifest_path.exists(), 'Already applied; use --check, or edit current sources directly.'
    pending = {}
    for project in projects():
        for name, transform in TRANSFORMS.items():
            path = project / PACKAGE / name
            try:
                pending[path] = transform(path.read_text(encoding='utf-8-sig'))
            except Exception as error:
                raise RuntimeError(f'Preflight failed: {path}') from error
        for name in NEW_FILES:
            pending[project / PACKAGE / name] = (ROOT / 'fabric' / PACKAGE / name).read_text(encoding='utf-8')
    print(f'Preflight passed: {len(pending)} files in {len(projects())} projects.')
    if not args.apply:
        return
    manifest = {}
    for path, content in pending.items():
        relative = path.relative_to(ROOT)
        if path.exists():
            backup = PLAN / 'before' / relative
            backup.parent.mkdir(parents=True, exist_ok=True)
            assert not backup.exists(), backup
            backup.write_bytes(path.read_bytes())
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8', newline='\n')
        manifest[relative.as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Applied. Pre-edit sources and output hashes saved under', PLAN)


if __name__ == '__main__':
    main()
