/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Draws a wave-like particle cape behind moving players.
 *
 * <p>Soar replaced the cape model with a physics-driven mesh. This port emits
 * a cape-shaped particle trail, which needs no render-state hooks and works on
 * every supported version.
 */
@SearchTags({"wavey capes", "wavey capes", "cape", "cape trail"})
public final class WaveyCapesHack extends Hack implements UpdateListener
{
	private final SliderSetting height = new SliderSetting("Height",
		"Height of the particle cape.", 6, 2, 10, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting gravity = new SliderSetting("Gravity",
		"Downward drift of the cape particles.", 15, 2, 30, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting density = new SliderSetting("Density",
		"Particles spawned per player per tick.", 1, 1, 4, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting onlySprinting = new CheckboxSetting(
		"Only sprinting", "Only shows the cape while sprinting.", false);

	public WaveyCapesHack()
	{
		super("WaveyCapes");
		setCategory(Category.RENDER);
		addSetting(height);
		addSetting(gravity);
		addSetting(density);
		addSetting(onlySprinting);
	}

	@Override
	protected void onEnable()
	{
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
		if(MC.level == null)
			return;

		for(AbstractClientPlayer player : MC.level.players())
		{
			if(onlySprinting.isChecked() && !player.isSprinting())
				continue;

			Vec3 motion = player.getDeltaMovement();
			if(motion.x * motion.x + motion.z * motion.z < 0.002)
				continue;

			Vec3 look = player.getLookAngle();
			Vec3 back = new Vec3(-look.x, 0, -look.z);
			if(back.lengthSqr() < 1.0E-4)
				continue;
			back = back.normalize();

			double x = player.getX() + back.x * 0.3;
			double y = player.getY() + 1.1 + height.getValue() * 0.05;
			double z = player.getZ() + back.z * 0.3;
			double vy = -gravity.getValue() * 0.001;

			for(int i = 0; i < density.getValueI(); i++)
				MC.level.addParticle(ParticleTypes.CLOUD, x, y, z,
					back.x * 0.01, vy, back.z * 0.01);
		}
	}
}
