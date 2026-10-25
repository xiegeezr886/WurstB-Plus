/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

/**
 * Highlights potion item entities with a pulsing glint-like aura.
 *
 * <p>Simplification: Soar changed the depth function of the vanilla item glint
 * through a RenderItem mixin so that potions glinted through walls. This port
 * cannot use mixins, so it draws an animated box and enchant particles around
 * dropped potion items instead. Potions inside inventories are not affected.
 */
@SearchTags({"shiny pots", "shiny potions", "potion glint", "glint",
	"potion esp", "shiny"})
public final class ShinyPotsHack extends Hack
	implements UpdateListener, RenderListener
{
	private final ColorSetting color = new ColorSetting("Color",
		"Color of the potion aura.", new Color(170, 70, 255));

	private final SliderSetting maxDistance = new SliderSetting(
		"Max distance",
		"Dropped potions farther away than this are not highlighted.", 64, 8,
		256, 8, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the aura even when blocks are in the way.",
		true);

	private final CheckboxSetting particles = new CheckboxSetting("Particles",
		"Spawns enchant particles around highlighted potions.", true);

	private final List<ItemEntity> potionItems = new ArrayList<>();

	public ShinyPotsHack()
	{
		super("ShinyPots");
		setCategory(Category.RENDER);
		addSetting(color);
		addSetting(maxDistance);
		addSetting(throughWalls);
		addSetting(particles);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		potionItems.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		potionItems.clear();
	}

	@Override
	public void onUpdate()
	{
		potionItems.clear();
		if(MC.player == null || MC.level == null)
			return;

		double maxDistanceSq = maxDistance.getValueSq();
		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof ItemEntity item) || item.isRemoved()
				|| !isPotion(item.getItem()))
				continue;

			if(MC.player.distanceToSqr(item) > maxDistanceSq)
				continue;

			potionItems.add(item);

			if(particles.isChecked() && Math.random() < 0.35)
				spawnGlintParticle(item);
		}
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.player == null || MC.level == null)
			return;

		boolean depthTest = !throughWalls.isChecked();
		double phase = (System.currentTimeMillis() % 2000L) / 2000.0;
		float pulse = (float)((Math.sin(phase * Math.PI * 2) + 1) / 2);
		int fill = color.getColorI(0.08F + 0.12F * pulse);
		int line = color.getColorI(0.55F + 0.45F * pulse);

		for(ItemEntity item : potionItems)
		{
			if(item.isRemoved() || item.getItem().isEmpty())
				continue;

			AABB box = item.getBoundingBox().inflate(0.05);
			RenderUtils.drawSolidBox(matrixStack, box, fill, depthTest);
			RenderUtils.drawOutlinedBox(matrixStack, box, line, depthTest);
		}
	}

	private boolean isPotion(ItemStack stack)
	{
		return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION)
			|| stack.is(Items.LINGERING_POTION);
	}

	private void spawnGlintParticle(ItemEntity item)
	{
		double x = item.getX() + (Math.random() - 0.5) * 0.6;
		double y = item.getY() + Math.random() * 0.6;
		double z = item.getZ() + (Math.random() - 0.5) * 0.6;

		MC.level.addParticle(ParticleTypes.ENCHANT, x, y, z,
			(Math.random() - 0.5) * 0.4, 0.2 + Math.random() * 0.2,
			(Math.random() - 0.5) * 0.4);
	}
}
