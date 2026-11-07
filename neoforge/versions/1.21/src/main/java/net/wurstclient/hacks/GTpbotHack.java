/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun v1.9.0 (GPL-3.0).
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.ServerEquivTp;

@SearchTags({"tp bot", "tpbot", "teleport bot", "tp机械人", "传送机器人"})
public final class GTpbotHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance to search for a target.", 100, 1, 300, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting delay = new SliderSetting("Delay",
		"Milliseconds between two teleports.", 40, 1, 2000, 1,
		ValueDisplay.INTEGER.withSuffix("ms"));

	private final SliderSetting keepY = new SliderSetting("Y offset",
		"Height offset relative to the target.", 0, 0, 150, 0.1,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting onlyXZ = new CheckboxSetting(
		"Only horizontal", "Keep your current Y instead of the target's Y.",
		false);

	private final CheckboxSetting randomTarget = new CheckboxSetting(
		"Random target", "Pick a random target instead of the closest one.",
		false);

	private final CheckboxSetting always = new CheckboxSetting("Always",
		"Teleport on every interval without pressing a key.", true);

	private final CheckboxSetting leftClick = new CheckboxSetting(
		"Left click", "Also teleport while the attack key is held.", false);

	private final CheckboxSetting rightClick = new CheckboxSetting(
		"Right click", "Also teleport while the use key is held.", true);

	private final SliderSetting packetDistance = new SliderSetting(
		"Packet distance",
		"Distance covered by each movement packet when the"
			+ " TPServerEquivalent gate is active.",
		10, 1, 100, 0.5, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();

	private long lastTeleport;

	public GTpbotHack()
	{
		super("GTpbot");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(delay);
		addSetting(keepY);
		addSetting(onlyXZ);
		addSetting(randomTarget);
		addSetting(always);
		addSetting(leftClick);
		addSetting(rightClick);
		addSetting(packetDistance);
		entityFilters.forEach(this::addSetting);
	}

	@Override
	protected void onEnable()
	{
		lastTeleport = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		boolean triggered = leftClick.isChecked()
			&& MC.options.keyAttack.isDown()
			|| rightClick.isChecked() && MC.options.keyUse.isDown()
			|| always.isChecked();
		if(!triggered)
			return;

		long now = System.currentTimeMillis();
		if(now - lastTeleport < delay.getValueI())
			return;
		lastTeleport = now;

		List<Entity> targets = findTargets();
		if(targets.isEmpty())
			return;

		Entity target = randomTarget.isChecked()
			? targets.get((int)Math.floor(targets.size() * Math.random()))
			: targets.get(0);

		Vec3 targetPos = target.position();
		Vec3 destination = new Vec3(targetPos.x,
			onlyXZ.isChecked() ? MC.player.getY() : targetPos.y + keepY.getValue(),
			targetPos.z);

		if(!ServerEquivTp.canStand(destination))
			destination = targetPos;

		if(target.getBoundingBox().contains(MC.player.getX(), targetPos.y,
			MC.player.getZ()))
			return;

		ServerEquivTp.teleport("GTpbot", destination,
			packetDistance.getValue());
	}

	private List<Entity> findTargets()
	{
		double rangeSq = range.getValueSq();
		Stream<Entity> stream = EntityUtils.getAttackableEntities();
		stream = stream
			.filter(e -> e != MC.player && MC.player.distanceToSqr(e) <= rangeSq);
		stream = entityFilters.applyTo(stream);
		return stream
			.sorted(Comparator
				.comparingDouble(e -> MC.player.distanceToSqr(e)))
			.toList();
	}
}
