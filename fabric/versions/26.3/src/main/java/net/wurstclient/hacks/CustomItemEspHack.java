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

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;

@SearchTags({"custom item esp", "item esp", "item nametags", "item filter"})
public final class CustomItemEspHack extends Hack
	implements UpdateListener, RenderListener
{
	private final ItemListSetting items = new ItemListSetting("Items",
		"Only these dropped items are highlighted.");

	private final CheckboxSetting renderBox = new CheckboxSetting(
		"Render box", "Draws a box around matching dropped items.", true);

	private final EnumSetting<ShapeMode> shapeMode = new EnumSetting<>(
		"Shape mode", ShapeMode.values(), ShapeMode.BOTH)
			.visibleWhen(renderBox::isChecked);

	private final ColorSetting sideColor =
		new ColorSetting("Fill color", new Color(255, 215, 0, 40))
			.visibleWhen(renderBox::isChecked);

	private final ColorSetting lineColor =
		new ColorSetting("Line color", new Color(255, 215, 0, 200))
			.visibleWhen(renderBox::isChecked);

	private final CheckboxSetting renderName = new CheckboxSetting(
		"Render name", "Shows the item name above matching items.", true);

	private final SliderSetting scale = new SliderSetting("Text scale",
		"How large the item name is.", 1, 0.25, 3, 0.05, ValueDisplay.DECIMAL)
			.visibleWhen(renderName::isChecked);

	private final CheckboxSetting showCount = new CheckboxSetting(
		"Show count", "Appends the stack size to the item name.", true)
			.visibleWhen(renderName::isChecked);

	private final ColorSetting nameColor = new ColorSetting("Text color",
		new Color(255, 255, 255)).visibleWhen(renderName::isChecked);

	private final CheckboxSetting background = new CheckboxSetting("Background",
		"Draws a background behind the item name.", true)
			.visibleWhen(renderName::isChecked);

	private final ColorSetting backgroundColor = new ColorSetting(
		"Background color", new Color(0, 0, 0, 75))
			.visibleWhen(() -> renderName.isChecked() && background.isChecked());

	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Items farther away than this are not rendered.", 128, 16, 512, 8,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders boxes through solid blocks.", true);

	private final ArrayList<ItemEntity> matches = new ArrayList<>();

	public CustomItemEspHack()
	{
		super("CustomItemESP");
		setCategory(Category.RENDER);
		addSetting(items);
		addSetting(renderBox);
		addSetting(shapeMode);
		addSetting(sideColor);
		addSetting(lineColor);
		addSetting(renderName);
		addSetting(scale);
		addSetting(showCount);
		addSetting(nameColor);
		addSetting(background);
		addSetting(backgroundColor);
		addSetting(maxDistance);
		addSetting(throughWalls);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		matches.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		matches.clear();
	}

	@Override
	public void onUpdate()
	{
		matches.clear();
		if(MC.player == null)
			return;

		Set<Item> selected = getSelectedItems();
		double maxDistanceSq = maxDistance.getValueSq();
		for(ItemEntity item : WURST.getEntitySnapshotManager().getCurrent()
			.items())
			if(!item.isRemoved()
				&& MC.player.distanceToSqr(item) <= maxDistanceSq
				&& selected.contains(item.getItem().getItem()))
				matches.add(item);
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(matches.isEmpty())
			return;

		boolean depthTest = !throughWalls.isChecked();
		boolean sides = shapeMode.getSelected().sides;
		boolean lines = shapeMode.getSelected().lines;

		if(renderBox.isChecked())
		{
			ArrayList<RenderUtils.ColoredBox> fillBoxes = new ArrayList<>();
			ArrayList<RenderUtils.ColoredBox> lineBoxes = new ArrayList<>();
			int fill = sideColor.getColor().getRGB();
			int line = lineColor.getColor().getRGB();

			for(ItemEntity item : matches)
			{
				AABB box = EntityUtils.getLerpedBox(item, partialTicks)
					.inflate(0.1);
				if(sides)
					fillBoxes
						.add(new RenderUtils.ColoredBox(box, fill));
				if(lines)
					lineBoxes
						.add(new RenderUtils.ColoredBox(box, line));
			}

			if(!fillBoxes.isEmpty())
				RenderUtils.drawSolidBoxes(matrixStack, fillBoxes, depthTest);
			if(!lineBoxes.isEmpty())
				RenderUtils.drawOutlinedBoxes(matrixStack, lineBoxes,
					depthTest);
		}

		if(!renderName.isChecked())
			return;

		Vec3 camPos = MC.gameRenderer.mainCamera().position();
		Quaternionf cameraRotation =
			new Quaternionf(MC.gameRenderer.mainCamera().rotation());
		float textScale = 0.025F * scale.getValueF();
		int textColor = nameColor.getColorI();
		int bgColor =
			background.isChecked() ? backgroundColor.getColor().getRGB() : 0;

		for(ItemEntity item : matches)
		{
			ItemStack stack = item.getItem();
			String text = stack.getHoverName().getString();
			if(showCount.isChecked() && stack.getCount() > 1)
				text += " x" + stack.getCount();

			Vec3 pos = new Vec3(item.getX(),
				item.getY() + item.getBbHeight() + 0.75, item.getZ());

			matrixStack.pushPose();
			matrixStack.translate(pos.x - camPos.x, pos.y - camPos.y,
				pos.z - camPos.z);
			matrixStack.rotate(cameraRotation);
			matrixStack.scale(-textScale, -textScale, textScale);
			RenderUtils.submitText(matrixStack, -MC.font.width(text) / 2F, 0,
				Component.literal(text).getVisualOrderText(), false,
				Font.DisplayMode.SEE_THROUGH, 0xF000F0, textColor, bgColor, 0);
			matrixStack.popPose();
		}
	}

	private Set<Item> getSelectedItems()
	{
		Set<Item> selected = new HashSet<>();
		for(String name : items.getItemNames())
		{
			Item item = BuiltInRegistries.ITEM
				.getValue(Identifier.parse(name));
			if(item != null)
				selected.add(item);
		}
		return selected;
	}

	private enum ShapeMode
	{
		BOTH("Both", true, true),
		LINES("Lines only", false, true),
		SIDES("Sides only", true, false);

		private final String name;
		private final boolean sides;
		private final boolean lines;

		private ShapeMode(String name, boolean sides, boolean lines)
		{
			this.name = name;
			this.sides = sides;
			this.lines = lines;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
