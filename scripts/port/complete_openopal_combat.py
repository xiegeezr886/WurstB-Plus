"""Wire directly ported OpenOpal algorithms into the v1.5.1 host modules."""
import hashlib
import json
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects, replace, section
from port_openopal_sources import REFERENCE


def models(text):
    return text[:text.index('import java.util.Random;')] + '''import net.wurstclient.util.Rotation;
import net.wurstclient.opal.rotation.*;

/** OpenOpal model names backed by its original per-instance implementations. */
public enum ScaffoldRotationModel
{
	INSTANT("Instant"), HEYPIXEL("Heypixel"), LINEAR("Linear"),
	ORGANIC("Organic"), SIDEWAYS("Sideways");
	private final String name;
	ScaffoldRotationModel(String name) { this.name = name; }
	@Override public String toString() { return name; }

	public IRotationModel create(float speed, double drift, double jitter)
	{
		return switch(this) {
			case INSTANT -> InstantRotationModel.INSTANCE;
			case HEYPIXEL -> new HeypixelRotationModel(speed);
			case LINEAR -> new LinearRotationModel(speed);
			case ORGANIC -> new OrganicRotationModel(speed, drift, jitter);
			case SIDEWAYS -> new SidewaysRotationModel(speed);
		};
	}

	public Rotation tick(Rotation from, Rotation to, float speed)
	{
		return create(speed, 1.2D, 0.12D).tick(from, to, 1.0F);
	}

	public Rotation tick(Rotation from, Rotation to, float speed,
		float timeDelta, double drift, double jitter)
	{
		return create(speed, drift, jitter).tick(from, to, timeDelta);
	}

	public static Rotation tickHypixel(Rotation from, Rotation to,
		float timeDelta, boolean diagonalYaw, int airTicks)
	{
		return new HypixelRotationModel(diagonalYaw, airTicks).tick(from, to, timeDelta);
	}
}
'''


def handler(text):
    text = replace(text, '\tprivate float speed = 180.0F;', '\tprivate float speed = 180.0F;\n\tprivate double drift = 1.2D, jitter = 0.12D;\n\tprivate net.wurstclient.opal.rotation.IRotationModel implementation;')
    text = replace(text, '\t{\n\t\tif(rotation == null || WurstClient.MC.player == null)', '''	{
		rotate(rotation, model, speed, 1.2D, 0.12D);
	}

	public void rotate(Rotation rotation, ScaffoldRotationModel model,
		float speed, double drift, double jitter)
	{
		if(rotation == null || WurstClient.MC.player == null)''') if text.count('\t{\n\t\tif(rotation == null || WurstClient.MC.player == null)') == 1 else text.replace('\t{\n\t\tif(rotation == null || WurstClient.MC.player == null)', '''	{
		rotate(rotation, model, speed, 1.2D, 0.12D);
	}

	public void rotate(Rotation rotation, ScaffoldRotationModel model,
		float speed, double drift, double jitter)
	{
		if(rotation == null || WurstClient.MC.player == null)''', 1)
    text = replace(text, '\t\tthis.model = model;\n\t\tthis.speed = speed;', '''		if(implementation == null || this.model != model || this.speed != speed
			|| this.drift != drift || this.jitter != jitter)
			implementation = model.create(speed, drift, jitter);
		this.model = model;
		this.speed = speed;
		this.drift = drift;
		this.jitter = jitter;''')
    text = text.replace('model.tick(current, target, speed)', 'implementation.tick(current, target, 1.0F)')
    text = replace(text, '\t\tcurrent = null;\n', '\t\tcurrent = null;\n\t\timplementation = null;\n')
    return text


def aura_target(text):
    text = replace(text, 'import java.util.ArrayList;', 'import net.wurstclient.opal.LastAttackData;\nimport net.wurstclient.opal.RandomUtility;\nimport net.wurstclient.hacks.scaffold.ScaffoldUtils;\nimport java.util.ArrayList;')
    text = replace(text, '\t\tprivate long lastAttackTime;\n\t\tprivate double lastAttackDamage;', '\t\tprivate LastAttackData lastAttackData;')
    text = section(text, '\t\tpublic boolean isAttackAvailable()', '\n\tpublic static List<Target> collect', '''		public boolean isAttackAvailable()
		{
			if(lastAttackData == null)
				return true;
			double damage = attackDamage();
			if(entity.getHealth() + entity.getAbsorptionAmount() <= damage)
				return true;
			return lastAttackData.getTime() >= ATTACK_WINDOW_MS
				|| damage > lastAttackData.getDamage();
		}

		public void onAttack(boolean reset)
		{
			double damage = attackDamage();
			if(lastAttackData == null)
				lastAttackData = new LastAttackData(damage);
			else
				lastAttackData.reset(reset, damage);
		}

		public void restoreAttackState(AttackRecord record)
		{
			lastAttackData = record == null ? null : record.data();
		}

		public AttackRecord attackRecord()
		{
			return new AttackRecord(lastAttackData);
		}

		public long getLastAttackTime()
		{
			return lastAttackData == null ? 0 : lastAttackData.getTime();
		}
	}

	public record AttackRecord(LastAttackData data)
	{}
''')
    text = replace(text, 't.entity.getHealth() * (t.entity instanceof Player ? 1.0D : 2.0D)', '(t.entity.getHealth() + t.entity.getAbsorptionAmount())\n\t\t\t\t* (t.entity instanceof Player ? 1.0D : 2.0D)')
    text = replace(text, 'order = Comparator.comparingLong(Target::getLastAttackTime)\n\t\t\t\t.thenComparing(order);', '''order = Comparator.comparingDouble((Target t) ->
				-((t.entity.getHealth() + t.entity.getAbsorptionAmount()) * 25.0D
					+ t.getLastAttackTime())).thenComparing(order);''')
    text = section(text, '\tpublic static Target raycastTarget(', '\n\tpublic static EntityHitResult raycastEntity', '''	/** Direct port of RotationUtility.getRotationFromRaycastedEntity. */
	public static Target raycastTarget(LivingEntity entity, Vec3 eyes,
		Vec3 closest, double range, boolean throughWalls)
	{
		float spread = (float)RandomUtility.getJoinRandomDouble(0.01D, 0.05D);
		Rotation base = rotationTo(eyes, closest);
		Rotation closestRotation = ScaffoldUtils.getVanillaRotation(new Rotation(
			base.yaw() + RandomUtility.getRandomFloat(-spread, spread),
			base.pitch() + RandomUtility.getRandomFloat(-spread, spread)));
		EntityHitResult closestHit = raycastEntity(entity, eyes, closestRotation, range);
		if(closestHit != null)
			return throughWalls || hasLineOfSight(eyes, closestHit.getLocation())
				? new Target(entity, closestRotation, closestHit.getLocation(), eyes.distanceTo(closest)) : null;

		AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
		Vec3 center = box.getCenter();
		double widthX = box.getXsize(), height = box.getYsize(), widthZ = box.getZsize();
		if(widthX <= 0 || height <= 0 || widthZ <= 0)
			return null;
		float step = 8.F - RandomUtility.RANDOM.nextFloat() * 0.25F;
		Target best = null;
		double bestAngle = Double.MAX_VALUE;
		for(double x = -widthX; x < widthX; x += widthX / step)
			for(double y = -height; y < height; y += height / step)
				for(double z = -widthZ; z < widthZ; z += widthZ / step)
				{
					Rotation rotation = ScaffoldUtils.getVanillaRotation(
						rotationTo(eyes, center.add(x, y, z)));
					EntityHitResult hit = raycastEntity(entity, eyes, rotation, range);
					if(hit == null)
						continue;
					double angle = rotation.getAngleTo(closestRotation);
					if(angle < bestAngle)
					{
						bestAngle = angle;
						best = new Target(entity, rotation, hit.getLocation(), eyes.distanceTo(closest));
					}
				}
		return best != null && (throughWalls || hasLineOfSight(eyes, best.hitVec)) ? best : null;
	}
''')
    return text


def targeting(text):
    return text.replace('public void onAttack(OpenAura.Target target)', 'public void onAttack(OpenAura.Target target, boolean reset)').replace('target.onAttack();', 'target.onAttack(reset);')


def killaura(text):
    text = replace(text, 'import net.wurstclient.hacks.aura.OpenAura;', '''import net.wurstclient.hacks.scaffold.ScaffoldRotationModel;
import net.wurstclient.hacks.scaffold.ScaffoldRotationHandler;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.hacks.aura.OpenAura;''')
    text = replace(text, '\tprivate RotationQueue rotationQueue;', '''	private final EnumSetting<ScaffoldRotationModel> rotationModel = new EnumSetting<>(
		"Rotation model", ScaffoldRotationModel.values(), ScaffoldRotationModel.INSTANT);
	private final SliderSetting maxAngle = new SliderSetting("Max angle", 90, 5, 360, 5, ValueDisplay.INTEGER);
	private final SliderSetting drift = new SliderSetting("Drift intensity", 1.2, 0.5, 2, 0.1, ValueDisplay.DECIMAL);
	private final SliderSetting jitter = new SliderSetting("Jitter intensity", 0.12, 0, 0.3, 0.01, ValueDisplay.DECIMAL);
	private final CheckboxSetting attackCooldown19 = new CheckboxSetting("1.9+ Attack Cooldown", false);
	private final ScaffoldRotationHandler rotationHandler = new ScaffoldRotationHandler();
	private int attacks;
	private long lastAttackTime;
	private RotationQueue rotationQueue;''')
    text = replace(text, '\t\taddSetting(mode);', '''		addSetting(rotationModel);
		addSetting(maxAngle);
		addSetting(drift);
		addSetting(jitter);
		addSetting(attackCooldown19);
		addSetting(mode);''')
    text = text.replace('"How far the aura can attack.", 6, 3, 10,', '"How far the aura can attack.", 3, 3, 6,')
    text = text.replace('CpsMode.values(), CpsMode.AUTO', 'CpsMode.values(), CpsMode.MANUAL')
    text = text.replace('new CheckboxSetting("Hide fake swings", false)', 'new CheckboxSetting("Hide fake swings", true)')
    text = text.replace('\t\tgrimKeepTicks = 0;', '\t\tgrimKeepTicks = 0;\n\t\tattacks = 0;\n\t\trotationHandler.reset();')
    text = section(text, '\t\tRotation planned = selection.rotation().rotation;', '\n\t}\n\n\t@Override\n\tpublic void onPreMotion()', '''		Rotation planned = selection.rotation().rotation;
		rotationHandler.rotate(planned, heypixelBypass.isChecked()
			? ScaffoldRotationModel.HEYPIXEL : rotationModel.getSelected(),
			maxAngle.getValueF(), drift.getValue(), jitter.getValue());
		rotationQueue.setRotation(rotationHandler.getRotation());''')
    text = replace(text, '\t\tif(!rotationQueue.wasApplied() || !isClickReady()\n\t\t\t|| !target.isAttackAvailable()', '\t\tif(!rotationQueue.wasApplied() || !isAttackSwingAvailable(target)')
    text = replace(text, '\t\tif(!overrideRaycast.isChecked()\n', '\t\tif((!overrideRaycast.isChecked() || heypixelBypass.isChecked())\n')
    text = replace(text, '\t\t// Switching weapons can change/reset the effective attack cooldown.\n\t\tif(!isClickReady() || !target.isAttackAvailable())\n\t\t\treturn;\n', '')
    text = replace(text, '\t\ttargeting.onAttack(target);', '''		targeting.onAttack(target, attacks == 0);
		attacks = attacks > 0 ? attacks - 1 : 2;
		lastAttackTime = System.currentTimeMillis();''')
    text = section(text, '\tprivate boolean isClickReady()', '\n\tprivate long clickDelay()', '''	/** KillAuraModule.isAttackSwingAvailable, using the original burst window. */
	private boolean isAttackSwingAvailable(OpenAura.Target target)
	{
		boolean smartAttack = smartWeapon.isChecked() && target.entity.isBlocking()
			&& hasAxe();
		if((attackCooldown19.isChecked() || cpsMode.getSelected() == CpsMode.AUTO)
			&& !smartAttack)
			return MC.player.getAttackStrengthScale(0.5F) >= 1.0F;
		if(heypixelBypass.isChecked())
		{
			double baseDelay = 1000.0D / attackCps.getValueI();
			long delay = (long)(baseDelay + (Math.random() - 0.5D) * baseDelay * 0.4D);
			return System.currentTimeMillis() - lastAttackTime >= delay;
		}
		return target.isAttackAvailable() || attacks > 0
			|| System.currentTimeMillis() >= nextAttackTime;
	}

	private boolean hasAxe()
	{
		for(int slot = 0; slot < 9; slot++)
			if(MC.player.getInventory().getItem(slot).is(ItemTags.AXES))
				return true;
		return false;
	}
''')
    text = replace(text, '\t\tif(hideFakeSwings.isChecked()\n\t\t\t|| System.currentTimeMillis() < nextSwingTime)', '\t\tif(System.currentTimeMillis() < nextSwingTime\n\t\t\t|| MC.hitResult instanceof net.minecraft.world.phys.BlockHitResult\n\t\t\t\t&& MC.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)')
    start = text.index('\tprivate void tryFakeSwing()')
    end = text.index('\tprivate void switchToAxe', start)
    fake = text[start:end]
    import re
    fake = re.sub(r'MC.player.swing\(InteractionHand.MAIN_HAND(?:, SwingAnimation.DEFAULT,\s*false)?\);', '(hideFakeSwings.isChecked() ? SwingHand.SERVER : SwingHand.CLIENT)\n\t\t\t.swing(InteractionHand.MAIN_HAND);', fake)
    text = text[:start] + fake + text[end:]
    return text


def scaffold(text):
    text = replace(text, 'private static final int NORMAL_SEARCH_DEPTH = 0;', 'private static final int NORMAL_SEARCH_DEPTH = 4;')
    text = replace(text, '\tpublic final SliderSetting interactCps =', '''	public final SliderSetting driftIntensity = new SliderSetting("Drift intensity", 1.2, 0.5, 2, 0.1, ValueDisplay.DECIMAL);
	public final SliderSetting jitterIntensity = new SliderSetting("Jitter intensity", 0.12, 0, 0.3, 0.01, ValueDisplay.DECIMAL);
	public final SliderSetting interactCps =''')
    text = replace(text, '\t\taddSetting(maxAngle);', '\t\taddSetting(maxAngle);\n\t\taddSetting(driftIntensity);\n\t\taddSetting(jitterIntensity);')
    return replace(text, 'rotationHandler.rotate(target, model, maxAngle.getValueF());', 'rotationHandler.rotate(target, model, maxAngle.getValueF(),\n\t\t\t\tdriftIntensity.getValue(), jitterIntensity.getValue());')


TRANSFORMS = {
    'hacks/scaffold/ScaffoldRotationModel.java': models,
    'hacks/scaffold/ScaffoldRotationHandler.java': handler,
    'hacks/aura/OpenAura.java': aura_target,
    'hacks/aura/OpenAuraTargeting.java': targeting,
    'hacks/KillauraHack.java': killaura,
    'hacks/ScaffoldWalkHack.java': scaffold,
}


def main():
    pending = {}
    for project in projects():
        for name, transform in TRANSFORMS.items():
            path = project / PACKAGE / name
            try:
                pending[path] = transform(path.read_text(encoding='utf-8'))
            except Exception as error:
                raise RuntimeError(str(path)) from error
    for path, text in pending.items():
        backup = PLAN / 'before-source-port' / path.relative_to(ROOT)
        backup.parent.mkdir(parents=True, exist_ok=True)
        assert not backup.exists(), backup
        backup.write_bytes(path.read_bytes())
        path.write_text(text, encoding='utf-8', newline='\n')
    print('Applied source algorithms to', len(pending), 'files.')


if __name__ == '__main__':
    main()
