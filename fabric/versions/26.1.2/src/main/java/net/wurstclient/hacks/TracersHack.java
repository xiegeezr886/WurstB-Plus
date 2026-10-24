/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
/* Ported from Meteor Client (GPL-3.0). */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.CameraTransformViewBobbingListener.CameraTransformViewBobbingEvent;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityEspRenderer;
import net.wurstclient.util.EntityEspRenderer.ColorMode;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RenderUtils.ColoredPoint;

@SearchTags({"tracers", "Tracer", "lines to entities"})
public final class TracersHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Entities farther away than this are not traced.", 256, 16, 1024, 8,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final CheckboxSetting players = new CheckboxSetting("Players",
		"Draw tracers to other players.", true);

	private final CheckboxSetting mobs = new CheckboxSetting("Mobs",
		"Draw tracers to living entities other than players.", true);

	private final CheckboxSetting items = new CheckboxSetting("Items",
		"Draw tracers to dropped items.", true);

	private final CheckboxSetting others = new CheckboxSetting("Other entities",
		"Draw tracers to entities that are neither living nor items.",
		false);

	private final CheckboxSetting ignoreSelf = new CheckboxSetting("Ignore self",
		"Doesn't draw a tracer to yourself in third person.", true);

	private final CheckboxSetting ignoreFriends = new CheckboxSetting(
		"Ignore friends", "Doesn't draw tracers to friends.", true);

	private final CheckboxSetting showInvisible = new CheckboxSetting(
		"Show invisible", "Draws tracers to invisible entities.", true);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders tracers even when blocks are in the way.",
		true);

	private final CheckboxSetting stem = new CheckboxSetting("Stem",
		"Draws a vertical line through the center of each traced entity.",
		true);

	private final EnumSetting<Target> target = new EnumSetting<>("Target",
		"Which part of the entity the tracer should point at.", Target.values(),
		Target.BODY);

	private final EnumSetting<ColorMode> colorMode = new EnumSetting<>(
		"Color mode", "Determines how tracer colors are calculated.",
		ColorMode.values(), ColorMode.CUSTOM);

	private final SliderSetting colorRange = new SliderSetting("Color range",
		"Distance where the distance color reaches its farthest value.", 40, 5,
		200, 5, ValueDisplay.INTEGER.withSuffix(" blocks"))
			.visibleWhen(() -> colorMode.getSelected() == ColorMode.DISTANCE);

	private final ColorSetting customColor = new ColorSetting("Tracer color",
		"Color used when Color mode is set to Custom.",
		new Color(205, 205, 205));

	private final ColorSetting friendColor = new ColorSetting("Friend color",
		"Friends are traced with this color.", Color.BLUE);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of the tracer lines.", 0.75, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final ArrayList<Entity> targets = new ArrayList<>();

	public TracersHack()
	{
		super("Tracers");
		setCategory(Category.RENDER);
		addSetting(maxDistance);
		addSetting(players);
		addSetting(mobs);
		addSetting(items);
		addSetting(others);
		addSetting(ignoreSelf);
		addSetting(ignoreFriends);
		addSetting(showInvisible);
		addSetting(throughWalls);
		addSetting(stem);
		addSetting(target);
		addSetting(colorMode);
		addSetting(colorRange);
		addSetting(customColor);
		addSetting(friendColor);
		addSetting(lineOpacity);
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
		targets.clear();
	}

	@Override
	public void onUpdate()
	{
		targets.clear();
		if(MC.player == null || MC.level == null)
			return;

		double maxDistanceSq = maxDistance.getValueSq();
		for(Entity entity : WURST.getEntitySnapshotManager().getCurrent()
			.entities())
		{
			if(entity == null || entity.isRemoved()
				|| entity instanceof FakePlayerEntity
				|| MC.player.distanceToSqr(entity) > maxDistanceSq)
				continue;

			if(entity == MC.player && ignoreSelf.isChecked())
				continue;

			if(!showInvisible.isChecked() && entity.isInvisible())
				continue;

			if(entity instanceof LivingEntity living
				&& living.getHealth() <= 0)
				continue;

			if(entity instanceof Player)
			{
				if(!players.isChecked())
					continue;
				if(ignoreFriends.isChecked()
					&& WURST.getFriends().isFriend(entity))
					continue;
			}else if(entity instanceof ItemEntity)
			{
				if(!items.isChecked())
					continue;
			}else if(entity instanceof LivingEntity)
			{
				if(!mobs.isChecked())
					continue;
			}else if(!others.isChecked())
				continue;

			targets.add(entity);
		}
	}

	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		event.cancel();
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.player == null || MC.level == null || targets.isEmpty())
			return;

		boolean depthTest = !throughWalls.isChecked();
		List<ColoredPoint> points = new ArrayList<>(targets.size());
		List<Stem> stems =
			new ArrayList<>(stem.isChecked() ? targets.size() : 0);

		for(Entity entity : targets)
		{
			AABB box = EntityUtils.getLerpedBox(entity, partialTicks);
			Vec3 pos = EntityUtils.getLerpedPos(entity, partialTicks);
			double y = switch(target.getSelected())
			{
				case FEET -> box.minY;
				case BODY -> box.getCenter().y;
				case HEAD -> box.maxY;
			};

			int color = getColor(entity);
			points.add(new ColoredPoint(new Vec3(pos.x, y, pos.z), color));

			if(stem.isChecked())
				stems.add(new Stem(new Vec3(pos.x, box.minY, pos.z),
					new Vec3(pos.x, box.maxY, pos.z), color));
		}

		RenderUtils.drawTracers(matrixStack, partialTicks, points, depthTest);

		for(Stem s : stems)
			RenderUtils.drawLine(matrixStack, s.start(), s.end(), s.color(),
				depthTest);
	}

	private int getColor(Entity entity)
	{
		int alpha = (int)(Mth.clamp(lineOpacity.getValue(), 0, 1) * 255);

		if(entity instanceof Player
			&& WURST.getFriends().isFriend(entity))
			return withAlpha(friendColor.getColorI(), alpha);

		int color = EntityEspRenderer.getColor(entity, colorMode.getSelected(),
			customColor.getColorI(), colorRange.getValue());
		return withAlpha(color, alpha);
	}

	private static int withAlpha(int color, int alpha)
	{
		return color & 0x00FFFFFF | alpha << 24;
	}

	private enum Target
	{
		FEET("Feet"),
		BODY("Body"),
		HEAD("Head");

		private final String name;

		Target(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private record Stem(Vec3 start, Vec3 end, int color)
	{}
}
