/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.util.ArrayList;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RenderUtils.ColoredBox;

@SearchTags({"chams", "through walls", "entity esp", "wireframe"})
public final class ImgChamsHack extends Hack
	implements UpdateListener, RenderListener
{
	private static final int CHAM_FILL = 0x1EFFFFFF;
	private static final int CHAM_LINE = 0x7FFFFFFF;

	private final CheckboxSetting throughWall = new CheckboxSetting(
		"Through walls", "Shows selected entities as wireframe overlays.", false);

	private final CheckboxSetting twCrystals = new CheckboxSetting("Crystals",
		"Shows end crystals through walls.", true)
			.visibleWhen(throughWall::isChecked);

	private final CheckboxSetting twPlayers = new CheckboxSetting("Players",
		"Shows players through walls.", true)
			.visibleWhen(throughWall::isChecked);

	private final CheckboxSetting twMobs = new CheckboxSetting("Mobs",
		"Shows hostile mobs through walls.", true)
			.visibleWhen(throughWall::isChecked);

	private final CheckboxSetting twAnimals = new CheckboxSetting("Animals",
		"Shows animals through walls.", true)
			.visibleWhen(throughWall::isChecked);

	private final CheckboxSetting twVillagers = new CheckboxSetting("Villagers",
		"Shows villagers and wandering traders through walls.", true)
			.visibleWhen(throughWall::isChecked);

	private final CheckboxSetting twSlimes = new CheckboxSetting("Slimes",
		"Shows slimes through walls.", true)
			.visibleWhen(throughWall::isChecked);

	public ImgChamsHack()
	{
		super("IMGChams");
		setCategory(Category.RENDER);
		addSetting(throughWall);
		addSetting(twCrystals);
		addSetting(twPlayers);
		addSetting(twMobs);
		addSetting(twAnimals);
		addSetting(twVillagers);
		addSetting(twSlimes);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		// Kept for update-driven hooks; the wireframe overlay itself is
		// rendered from the entity list every frame.
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(!throughWall.isChecked() || MC.level == null || MC.player == null)
			return;

		ArrayList<ColoredBox> fills = new ArrayList<>();
		ArrayList<ColoredBox> lines = new ArrayList<>();

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(entity == MC.player || !shouldRender(entity))
				continue;

			AABB box = entity.getBoundingBox();
			fills.add(new ColoredBox(box, CHAM_FILL));
			lines.add(new ColoredBox(box, CHAM_LINE));
		}

		if(!fills.isEmpty())
			RenderUtils.drawSolidBoxes(matrixStack, fills, true);
		if(!lines.isEmpty())
			RenderUtils.drawOutlinedBoxes(matrixStack, lines, true);
	}

	private boolean shouldRender(Entity entity)
	{
		if(entity instanceof EndCrystal)
			return twCrystals.isChecked();
		if(entity instanceof Slime)
			return twSlimes.isChecked();
		if(entity instanceof Villager || entity instanceof WanderingTrader)
			return twVillagers.isChecked();
		if(entity instanceof Player)
			return twPlayers.isChecked();
		if(entity instanceof Animal)
			return twAnimals.isChecked();
		return entity instanceof Mob && twMobs.isChecked();
	}
}
