/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.server.level.ParticleStatus;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.ISimpleOption;
import net.wurstclient.settings.CheckboxSetting;

/**
 * Applies performance-friendly vanilla options while enabled and restores the
 * exact previous values when disabled.
 *
 * <p>
 * Soar's original module also cancelled chunk rendering delays, hid armor
 * stands and removed invisible non-tablist entities through its event system.
 * Those required renderer hooks, so this port only handles options.
 */
@SearchTags({"fps boost", "fpsboost", "performance", "lag", "frames", "fps",
	"graphics", "particles"})
public final class FpsBoostHack extends Hack implements UpdateListener
{
	private final CheckboxSetting fastGraphics = new CheckboxSetting(
		"Fast graphics",
		"Turns clouds off and disables ambient occlusion and entity shadows.",
		true);

	private final CheckboxSetting minimalParticles = new CheckboxSetting(
		"Minimal particles", "Reduces the particle setting to Minimal.", true);

	private final CheckboxSetting reduceEntityDistance = new CheckboxSetting(
		"Reduce entity distance",
		"Lowers the entity distance scaling to the minimum of 50%.", true);

	private GraphicsPreset oldGraphicsPreset;
	private CloudStatus oldClouds;
	private boolean oldAmbientOcclusion;
	private boolean oldEntityShadows;
	private ParticleStatus oldParticles;
	private double oldEntityDistance;

	private boolean graphicsApplied;
	private boolean particlesApplied;
	private boolean entityDistanceApplied;

	public FpsBoostHack()
	{
		super("FpsBoost");
		setCategory(Category.OTHER);
		addSetting(fastGraphics);
		addSetting(minimalParticles);
		addSetting(reduceEntityDistance);
	}

	@Override
	protected void onEnable()
	{
		oldGraphicsPreset = MC.options.graphicsPreset().get();
		oldClouds = MC.options.cloudStatus().get();
		oldAmbientOcclusion = MC.options.ambientOcclusion().get();
		oldEntityShadows = MC.options.entityShadows().get();
		oldParticles = MC.options.particles().get();
		oldEntityDistance = MC.options.entityDistanceScaling().get();

		graphicsApplied = false;
		particlesApplied = false;
		entityDistanceApplied = false;

		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);

		restoreGraphics();
		restoreParticles();
		restoreEntityDistance();
	}

	@Override
	public void onUpdate()
	{
		if(fastGraphics.isChecked())
		{
			if(!graphicsApplied)
			{
				MC.options.cloudStatus().set(CloudStatus.OFF);
				MC.options.ambientOcclusion().set(false);
				MC.options.entityShadows().set(false);
				graphicsApplied = true;
			}
		}else
			restoreGraphics();

		if(minimalParticles.isChecked())
		{
			if(!particlesApplied)
			{
				MC.options.particles().set(ParticleStatus.MINIMAL);
				particlesApplied = true;
			}
		}else
			restoreParticles();

		if(reduceEntityDistance.isChecked())
		{
			if(!entityDistanceApplied)
			{
				MC.options.entityDistanceScaling().set(0.5);
				entityDistanceApplied = true;
			}
		}else
			restoreEntityDistance();
	}

	private void restoreGraphics()
	{
		if(!graphicsApplied)
			return;

		MC.options.cloudStatus().set(oldClouds);
		MC.options.ambientOcclusion().set(oldAmbientOcclusion);
		MC.options.entityShadows().set(oldEntityShadows);

		// Changing the options above marks the preset as Custom. Restore the
		// label without re-applying the whole preset and clobbering unrelated
		// settings such as render distance.
		ISimpleOption.get(MC.options.graphicsPreset())
			.forceSetValue(oldGraphicsPreset);

		graphicsApplied = false;
	}

	private void restoreParticles()
	{
		if(!particlesApplied)
			return;

		MC.options.particles().set(oldParticles);
		particlesApplied = false;
	}

	private void restoreEntityDistance()
	{
		if(!entityDistanceApplied)
			return;

		MC.options.entityDistanceScaling().set(oldEntityDistance);
		entityDistanceApplied = false;
	}
}
