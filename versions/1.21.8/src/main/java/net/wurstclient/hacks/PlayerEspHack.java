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
import java.util.List;
import org.joml.Matrix4f;
import com.mojang.blaze3d.systems.RenderSystem;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.EspStyleSetting.EspStyle;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.FilterInvisibleSetting;
import net.wurstclient.settings.filters.FilterSleepingSetting;
import net.wurstclient.util.EntityEspRenderer;
import net.wurstclient.util.EntityEspRenderer.ColorMode;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.WorldToScreen;
import net.wurstclient.util.WorldToScreen.ScreenBounds;

@SearchTags({"player esp", "PlayerTracers", "player tracers"})
public final class PlayerEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener, GUIRenderListener
{
	private final EnumSetting<RenderMode> renderMode = new EnumSetting<>(
		"Render mode", "Switches between world-space and screen-space ESP.",
		RenderMode.values(), RenderMode.THREE_D);

	private final EspStyleSetting style =
		new EspStyleSetting(EspStyle.LINES_AND_BOXES);
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each player.\n"
			+ "\u00a7lFancy\u00a7r mode shows slightly larger boxes that look better.");

	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Players farther away than this are not rendered.", 256, 16, 512, 8,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final EnumSetting<ColorMode> colorMode = new EnumSetting<>(
		"Color mode", "Determines how player ESP colors are calculated.",
		ColorMode.values(), ColorMode.DISTANCE);

	private final SliderSetting colorRange = new SliderSetting("Color range",
		"Distance where the distance color reaches its farthest value.", 40, 5,
		200, 5, ValueDisplay.INTEGER.withSuffix(" blocks"))
			.visibleWhen(() -> colorMode.getSelected() == ColorMode.DISTANCE);

	private final ColorSetting customColor = new ColorSetting("Player color",
		"Color used when Color mode is set to Custom.", Color.WHITE)
			.visibleWhen(() -> colorMode.getSelected() == ColorMode.CUSTOM);

	private final ColorSetting friendColor = new ColorSetting("Friend color",
		"Friends are highlighted with this color.", Color.BLUE);

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled area inside player boxes.", 0.18, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE).visibleWhen(() ->
			renderMode.getSelected() == RenderMode.TWO_D || style.hasBoxes());

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of box outlines and tracer lines.", 0.75, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting nearFadeDistance = new SliderSetting(
		"Near fade distance",
		"Smoothly fades ESP when a player is close to the camera.", 3, 0, 12,
		0.5, ValueDisplay.DECIMAL.withSuffix(" blocks"))
			.visibleWhen(() -> renderMode.getSelected() == RenderMode.THREE_D);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders players even when blocks are in the way.",
		true);

	private final CheckboxSetting healthBar = new CheckboxSetting("Health bar",
		"Shows current health beside each 2D player box.", true)
			.visibleWhen(() -> renderMode.getSelected() == RenderMode.TWO_D);

	private final CheckboxSetting armorBar = new CheckboxSetting("Armor bar",
		"Shows average armor durability below each 2D player box.", true)
			.visibleWhen(() -> renderMode.getSelected() == RenderMode.TWO_D);
	
	private final EntityFilterList entityFilters = new EntityFilterList(
		new FilterSleepingSetting("Won't show sleeping players.", false),
		new FilterInvisibleSetting("Won't show invisible players.", false));
	
	private final ArrayList<Player> players = new ArrayList<>();
	private List<ScreenBox> screenBoxes = List.of();
	
	public PlayerEspHack()
	{
		super("PlayerESP");
		setCategory(Category.RENDER);
		addSetting(renderMode);
		addSetting(style);
		addSetting(boxSize);
		addSetting(maxDistance);
		addSetting(colorMode);
		addSetting(colorRange);
		addSetting(customColor);
		addSetting(friendColor);
		addSetting(fillOpacity);
		addSetting(lineOpacity);
		addSetting(nearFadeDistance);
		addSetting(throughWalls);
		addSetting(healthBar);
		addSetting(armorBar);
		style.visibleWhen(() -> renderMode.getSelected() == RenderMode.THREE_D);
		boxSize.visibleWhen(() -> renderMode.getSelected() == RenderMode.TWO_D
			|| style.hasBoxes());
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		players.clear();
		screenBoxes = List.of();
	}
	
	@Override
	public void onUpdate()
	{
		players.clear();
		double maxDistanceSq = maxDistance.getValueSq();
		for(Player player : WURST.getEntitySnapshotManager().getCurrent()
			.players())
		{
			if(player.isRemoved() || player.getHealth() <= 0
				|| player == MC.player || player instanceof FakePlayerEntity
				|| MC.player.distanceToSqr(player) > maxDistanceSq
				|| !entityFilters.testOne(player))
				continue;

			players.add(player);
		}
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(renderMode.getSelected() == RenderMode.THREE_D && style.hasLines())
			event.cancel();
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(renderMode.getSelected() == RenderMode.TWO_D)
		{
			updateScreenBoxes(matrixStack, partialTicks);
			return;
		}

		screenBoxes = List.of();
		EntityEspRenderer.render(matrixStack, partialTicks, players, style,
			boxSize.getExtraSize(), fillOpacity.getValue(),
			lineOpacity.getValue(), nearFadeDistance.getValue(),
			!throughWalls.isChecked(), this::getColor);
	}

	private void updateScreenBoxes(PoseStack matrixStack, float partialTicks)
	{
		// TODO: 26.1.2 - RenderSystem.getProjectionMatrix() removed
		Matrix4f view = new Matrix4f(matrixStack.last().pose());
		Matrix4f projection = new Matrix4f(); // Placeholder
		ArrayList<ScreenBox> boxes = new ArrayList<>(players.size());
		double expansion = boxSize.getExtraSize() / 2;
		for(Player player : players)
		{
			if(!throughWalls.isChecked() && !MC.player.hasLineOfSight(player))
				continue;

			AABB box = EntityUtils.getLerpedBox(player, partialTicks)
				.move(0, expansion, 0).inflate(expansion);
			ScreenBounds bounds = WorldToScreen.project(box, view, projection);
			if(bounds == null || bounds.maxX() - bounds.minX() < 1
				|| bounds.maxY() - bounds.minY() < 1)
				continue;

			float health = player.getMaxHealth() <= 0 ? 0
				: Mth.clamp((player.getHealth() + player.getAbsorptionAmount())
					/ (player.getMaxHealth() + player.getAbsorptionAmount()), 0, 1);
			boxes.add(new ScreenBox(bounds, getColor(player), health,
				getArmorDurability(player)));
		}
		screenBoxes = List.copyOf(boxes);
	}

	private float getArmorDurability(Player player)
	{
		int remaining = 0;
		int maximum = 0;
		for(var slot : new net.minecraft.world.entity.EquipmentSlot[]{
			net.minecraft.world.entity.EquipmentSlot.FEET,
			net.minecraft.world.entity.EquipmentSlot.LEGS,
			net.minecraft.world.entity.EquipmentSlot.CHEST,
			net.minecraft.world.entity.EquipmentSlot.HEAD})
		{
			ItemStack stack = player.getItemBySlot(slot);
			if(!stack.isEmpty() && stack.isDamageableItem())
			{
				remaining += stack.getMaxDamage() - stack.getDamageValue();
				maximum += stack.getMaxDamage();
			}
		}
		return maximum == 0 ? 0 : remaining / (float)maximum;
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(renderMode.getSelected() != RenderMode.TWO_D
			|| screenBoxes.isEmpty())
			return;

		for(ScreenBox box : screenBoxes)
		{
			ScreenBounds bounds = box.bounds();
			int fillColor = box.color() & 0x00FFFFFF
				| (int)(fillOpacity.getValue() * 120) << 24;
			int lineColor = box.color() & 0x00FFFFFF
				| (int)(lineOpacity.getValue() * 255) << 24;
			RenderUtils.fill2D(context, bounds.minX(), bounds.minY(),
				bounds.maxX(), bounds.maxY(), fillColor);
			RenderUtils.drawBorder2D(context, bounds.minX(), bounds.minY(),
				bounds.maxX(), bounds.maxY(), lineColor);

			if(healthBar.isChecked())
			{
				float x1 = bounds.minX() - 4;
				float y = Mth.lerp(box.health(), bounds.maxY(), bounds.minY());
				RenderUtils.fill2D(context, x1, bounds.minY(), x1 + 2,
					bounds.maxY(), 0xA0000000);
				RenderUtils.fill2D(context, x1, y, x1 + 2, bounds.maxY(),
					lineColor);
			}

			if(armorBar.isChecked() && box.armor() > 0)
			{
				float width = (bounds.maxX() - bounds.minX()) * box.armor();
				RenderUtils.fill2D(context, bounds.minX(), bounds.maxY() + 2,
					bounds.maxX(), bounds.maxY() + 4, 0xA0000000);
				RenderUtils.fill2D(context, bounds.minX(), bounds.maxY() + 2,
					bounds.minX() + width, bounds.maxY() + 4, 0xFF55AAFF);
			}
		}
	}
	
	private int getColor(Player e)
	{
		if(WURST.getFriends().contains(e.getName().getString()))
			return friendColor.getColorI();

		return EntityEspRenderer.getColor(e, colorMode.getSelected(),
			customColor.getColorI(), colorRange.getValue());
	}

	private record ScreenBox(ScreenBounds bounds, int color, float health,
		float armor)
	{
	}

	private enum RenderMode
	{
		THREE_D("3D"),
		TWO_D("2D");

		private final String name;

		RenderMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
