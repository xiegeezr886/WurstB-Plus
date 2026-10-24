/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.awt.Color;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;

/**
 * Draws a box around players and mobs that are holding or blocking with a
 * shield.
 *
 * <p>
 * Upstream rendered a 180-degree arc on the ground. This port uses a box
 * around the entity instead, which is cheaper and works for every shield
 * holder regardless of body rotation.
 */
@SearchTags({"shield esp", "shield tracer", "blocking players", "shield",
	"blocking"})
public final class ShieldEspHack extends Hack implements RenderListener
{
	private final CheckboxSetting players = new CheckboxSetting("Players",
		"Render players that hold or block with a shield.", true);

	private final CheckboxSetting mobs = new CheckboxSetting("Mobs",
		"Render non-player living entities that hold or block with a shield.",
		false);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders shield users even when blocks are in the way.",
		true);

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the shield ESP boxes.", new Color(255, 0, 0));

	public ShieldEspHack()
	{
		super("ShieldESP");
		setCategory(Category.RENDER);
		addSetting(players);
		addSetting(mobs);
		addSetting(throughWalls);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(RenderListener.class, this);
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.player == null || MC.level == null)
			return;

		boolean depthTest = !throughWalls.isChecked();
		int lineColor = color.getColorI();
		int fillColor = color.getColorI(60);

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof LivingEntity living)
				|| living == MC.player || !living.isAlive())
				continue;

			boolean isPlayer = living instanceof Player;
			if(isPlayer ? !players.isChecked() : !mobs.isChecked())
				continue;

			if(!holdsShield(living) && !living.isBlocking())
				continue;

			AABB box =
				EntityUtils.getLerpedBox(living, partialTicks).inflate(0.1);
			RenderUtils.drawSolidBox(matrixStack, box, fillColor, depthTest);
			RenderUtils.drawOutlinedBox(matrixStack, box, lineColor,
				depthTest);
		}
	}

	private boolean holdsShield(LivingEntity entity)
	{
		return entity.getMainHandItem().is(Items.SHIELD)
			|| entity.getOffhandItem().is(Items.SHIELD);
	}
}
