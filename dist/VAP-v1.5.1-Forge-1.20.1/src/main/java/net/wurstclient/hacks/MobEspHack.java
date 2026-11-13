/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.*;
import net.wurstclient.util.EntityEspRenderer;
import net.wurstclient.util.EntityEspRenderer.ColorMode;

@SearchTags({"mob esp", "MobTracers", "mob tracers"})
public final class MobEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each mob.\n"
			+ "\u00a7lFancy\u00a7r mode shows slightly larger boxes that look better.");

	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Mobs farther away than this are not rendered.", 256, 16, 512, 8,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final EnumSetting<ColorMode> colorMode = new EnumSetting<>(
		"Color mode", "Determines how mob ESP colors are calculated.",
		ColorMode.values(), ColorMode.DISTANCE);

	private final SliderSetting colorRange = new SliderSetting("Color range",
		"Distance where the distance color reaches its farthest value.", 40, 5,
		200, 5, ValueDisplay.INTEGER.withSuffix(" blocks"))
			.visibleWhen(() -> colorMode.getSelected() == ColorMode.DISTANCE);

	private final ColorSetting customColor = new ColorSetting("Mob color",
		"Color used when Color mode is set to Custom.", Color.RED)
			.visibleWhen(() -> colorMode.getSelected() == ColorMode.CUSTOM);

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled area inside mob boxes.", 0.18, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE).visibleWhen(style::hasBoxes);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of box outlines and tracer lines.", 0.75, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting nearFadeDistance = new SliderSetting(
		"Near fade distance",
		"Smoothly fades ESP when a mob is close to the camera.", 3, 0, 12,
		0.5, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders mobs even when blocks are in the way.", true);
	
	private final EntityFilterList entityFilters =
		new EntityFilterList(FilterHostileSetting.genericVision(false),
			FilterNeutralSetting
				.genericVision(AttackDetectingEntityFilter.Mode.OFF),
			FilterPassiveSetting.genericVision(false),
			FilterPassiveWaterSetting.genericVision(false),
			FilterBatsSetting.genericVision(false),
			FilterSlimesSetting.genericVision(false),
			FilterPetsSetting.genericVision(false),
			FilterVillagersSetting.genericVision(false),
			FilterZombieVillagersSetting.genericVision(false),
			FilterGolemsSetting.genericVision(false),
			FilterPiglinsSetting
				.genericVision(AttackDetectingEntityFilter.Mode.OFF),
			FilterZombiePiglinsSetting
				.genericVision(AttackDetectingEntityFilter.Mode.OFF),
			FilterEndermenSetting
				.genericVision(AttackDetectingEntityFilter.Mode.OFF),
			FilterShulkersSetting.genericVision(false),
			FilterAllaysSetting.genericVision(false),
			FilterInvisibleSetting.genericVision(false),
			FilterNamedSetting.genericVision(false),
			FilterArmorStandsSetting.genericVision(true));
	
	private final ArrayList<LivingEntity> mobs = new ArrayList<>();
	
	public MobEspHack()
	{
		super("MobESP");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(boxSize);
		addSetting(maxDistance);
		addSetting(colorMode);
		addSetting(colorRange);
		addSetting(customColor);
		addSetting(fillOpacity);
		addSetting(lineOpacity);
		addSetting(nearFadeDistance);
		addSetting(throughWalls);
		boxSize.visibleWhen(style::hasBoxes);
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		mobs.clear();
	}
	
	@Override
	public void onUpdate()
	{
		mobs.clear();
		double maxDistanceSq = maxDistance.getValueSq();
		for(LivingEntity mob : WURST.getEntitySnapshotManager().getCurrent()
			.livingEntities())
		{
			if(mob instanceof Player || mob.isRemoved()
				|| mob.getHealth() <= 0
				|| MC.player.distanceToSqr(mob) > maxDistanceSq
				|| !entityFilters.testOne(mob))
				continue;

			mobs.add(mob);
		}
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(style.hasLines())
			event.cancel();
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		EntityEspRenderer.render(matrixStack, partialTicks, mobs, style,
			boxSize.getExtraSize(), fillOpacity.getValue(),
			lineOpacity.getValue(), nearFadeDistance.getValue(),
			!throughWalls.isChecked(), this::getColor);
	}
	
	private int getColor(LivingEntity e)
	{
		return EntityEspRenderer.getColor(e, colorMode.getSelected(),
			customColor.getColorI(), colorRange.getValue());
	}
}
