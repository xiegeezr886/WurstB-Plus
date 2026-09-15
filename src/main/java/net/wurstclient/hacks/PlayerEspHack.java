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
import java.util.Map;
import org.joml.Matrix4f;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.render.skia.EspSkia;
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
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.EntityEspRenderer;
import net.wurstclient.util.EntityEspRenderer.ColorMode;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.WorldToScreen;
import net.wurstclient.util.WorldToScreen.ScreenBounds;
import net.wurstclient.util.esp.EspEnchantNames;
import net.wurstclient.util.esp.EspEquipmentLayout;
import net.wurstclient.util.esp.EspNameTagElement;
import net.wurstclient.util.esp.EspNameTagLayout;
import net.wurstclient.util.esp.EspNameTagPolicy;

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

	private final CheckboxSetting boxStroke = new CheckboxSetting("Box stroke",
		"Draws the box outline as a thin colored line wrapped in a dark casing,"
			+ " the way the reference client draws it. Turn this off for the"
			+ " old single-pixel border.",
		true).visibleWhen(() -> renderMode.getSelected() == RenderMode.TWO_D);

	private final CheckboxSetting roundedBox = new CheckboxSetting(
		"Rounded box", "Rounds the corners of the 2D box.", false)
			.visibleWhen(() -> renderMode.getSelected() == RenderMode.TWO_D);

	private final SliderSetting cornerRadius = new SliderSetting(
		"Corner radius", "Corner radius of the rounded 2D box.", 1.5, 0, 6, 0.5,
		ValueDisplay.DECIMAL)
			.visibleWhen(() -> renderMode.getSelected() == RenderMode.TWO_D
				&& roundedBox.isChecked());

	private final CheckboxSetting nameTags = new CheckboxSetting("Name tags",
		"Draws an element strip above each 2D box: status indicators plus the"
			+ " player's name, health and distance.",
		true).visibleWhen(() -> renderMode.getSelected() == RenderMode.TWO_D);

	private final CheckboxSetting tagDistance = new CheckboxSetting(
		"Tag distance", "Adds the distance to the name tag strip.", true)
			.visibleWhen(() -> renderMode.getSelected() == RenderMode.TWO_D
				&& nameTags.isChecked());

	private final CheckboxSetting tagHealth = new CheckboxSetting("Tag health",
		"Adds health to the name tag strip; absorption is appended"
			+ " automatically whenever the player has some.",
		true).visibleWhen(() -> renderMode.getSelected() == RenderMode.TWO_D
			&& nameTags.isChecked());

	private final CheckboxSetting statusIndicators = new CheckboxSetting(
		"Status indicators",
		"Marks sneaking, invisible and blocking players on the name tag strip.",
		true).visibleWhen(() -> renderMode.getSelected() == RenderMode.TWO_D
			&& nameTags.isChecked());

	private final CheckboxSetting tagEquipment = new CheckboxSetting(
		"Tag equipment",
		"Draws each player's armour and main-hand item above their 2D box,"
			+ " labelled with shortened enchantment names.",
		true).visibleWhen(() -> renderMode.getSelected() == RenderMode.TWO_D
			&& nameTags.isChecked());
	
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
		addSetting(boxStroke);
		addSetting(roundedBox);
		addSetting(cornerRadius);
		addSetting(nameTags);
		addSetting(tagDistance);
		addSetting(tagHealth);
		addSetting(statusIndicators);
		addSetting(tagEquipment);
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
		Matrix4f view = new Matrix4f(matrixStack.last().pose());
		Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
		ArrayList<ScreenBox> boxes = new ArrayList<>(players.size());
		double expansion = boxSize.getExtraSize() / 2;
		for(Player player : players)
		{
			// 这里不能用原版 MC.player.hasLineOfSight(player)：它在 128 格外一律
			// 返回 false（1.20.2 真源 world/entity/LivingEntity.java:147 的
			// MAX_LINE_OF_SIGHT_TEST_RANGE，判定在 :2878），而 Max distance 默认
			// 是 256。结果是「Through walls 关」时，150 格外、中间毫无遮挡的玩家
			// 在 2D 模式不画、在 3D 模式照画——同一个开关两种模式不一致。
			// BlockUtils.hasLineOfSight 用的是同一条 ClipContext.Block.COLLIDER
			// 射线（util/BlockUtils.java:156-159），但没有距离上限。
			if(!throughWalls.isChecked() && !BlockUtils.hasLineOfSight(
				MC.player.getEyePosition(), player.getEyePosition()))
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
				getArmorDurability(player), player));
		}
		screenBoxes = List.copyOf(boxes);
	}

	private float getArmorDurability(Player player)
	{
		int remaining = 0;
		int maximum = 0;
		for(ItemStack stack : player.getArmorSlots())
			if(!stack.isEmpty() && stack.isDamageableItem())
			{
				remaining += stack.getMaxDamage() - stack.getDamageValue();
				maximum += stack.getMaxDamage();
			}
		return maximum == 0 ? 0 : remaining / (float)maximum;
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(renderMode.getSelected() != RenderMode.TWO_D
			|| screenBoxes.isEmpty())
			return;

		// 参考的 2D ESP 全部走矢量绘制：带深色描边的细线、圆角、抗锯齿文字。
		// 本工程没有 NanoVG，改走既有的 Skia 区域管线（Twilight 界面同一条），
		// 原生库不可用时逐像素退回原来的原版四边形路径。
		//
		// 先预排版：区域只用「这一帧真会画到的并集」，而不是整屏——区域越大，
		// 每帧 peekPixels + glTexSubImage2D 要上传的像素就越多。
		// 先定路径、再排版：标尺必须和实际画的那条路径同源。
		//
		// 这一点原先搞反了——排版无条件用 EspSkia.textWidth，于是原生库不可用时
		// 会在排版阶段就抛 UnsatisfiedLinkError，异常穿出渲染事件，
		// renderScreenBoxesVanilla 那条兜底路径永远执行不到。
		boolean skia = EspSkia.isUsable();
		EspNameTagLayout.GlyphMeasurer measurer =
			skia ? text -> EspSkia.textWidth(text, EspNameTagLayout.FONT_SIZE)
				: text -> MC.font.width(text)
					* EspNameTagLayout.vanillaScale(MC.font.lineHeight);

		ArrayList<DrawnBox> drawn = new ArrayList<>(screenBoxes.size());
		for(ScreenBox box : screenBoxes)
			drawn.add(new DrawnBox(box,
				nameTags.isChecked() ? layoutNameTag(box, measurer) : null));

		float[] content = contentBounds(drawn);
		if(content == null)
			return;

		int guiWidth = context.guiWidth();
		int guiHeight = context.guiHeight();
		int x = Mth.clamp((int)Math.floor(content[0]), 0, guiWidth);
		int y = Mth.clamp((int)Math.floor(content[1]), 0, guiHeight);
		int x2 = Mth.clamp((int)Math.ceil(content[2]), 0, guiWidth);
		int y2 = Mth.clamp((int)Math.ceil(content[3]), 0, guiHeight);
		if(x2 - x < 1 || y2 - y < 1)
			return;

		if(!skia || !EspSkia.begin(context, x, y, x2 - x, y2 - y))
		{
			renderScreenBoxesVanilla(context);
			renderNameTagsVanilla(context, drawn);
			renderEquipment(context, drawn);
			return;
		}

		try
		{
			for(DrawnBox entry : drawn)
				renderScreenBox(entry);
		}finally
		{
			EspSkia.end(context);
		}

		// 物品图标与附魔短名必须走原版绘制：Skia 是 CPU 光栅画布，画不了物品
		// 模型的 GL 渲染。参考自己也是把物品丢进原版队列、和 NanoVG 分开画的。
		// 这里放在区域 blit 之后，所以不会被区域覆盖。
		renderEquipment(context, drawn);
	}

	/** 护甲四件 + 主手，空槽跳过；护甲顺序是头→胸→腿→靴。 */
	private static List<ItemStack> collectEquipment(Player player)
	{
		List<ItemStack> equipment = new ArrayList<>(5);

		for(ItemStack stack : player.getArmorSlots())
			if(!stack.isEmpty())
				equipment.add(stack);

		ItemStack mainHand = player.getMainHandItem();
		if(!mainHand.isEmpty())
			equipment.add(mainHand);

		return equipment;
	}

	/**
	 * 参考 ESPModule.renderEquipment()。列表最后一项画在最左边（见
	 * {@link EspEquipmentLayout}），所以主手在最左、头盔在最右。
	 */
	private void renderEquipment(GuiGraphics context, List<DrawnBox> drawn)
	{
		if(!tagEquipment.isChecked() || MC.player == null)
			return;

		for(DrawnBox entry : drawn)
		{
			Player player = entry.box().player();
			if(player == null)
				continue;

			List<ItemStack> equipment = collectEquipment(player);
			if(equipment.isEmpty())
				continue;

			ScreenBounds bounds = entry.box().bounds();
			boolean hasTags = entry.tag() != null && !entry.tag().isEmpty();
			List<EspEquipmentLayout.Slot> slots = EspEquipmentLayout.layout(
				equipment.size(), (bounds.minX() + bounds.maxX()) / 2F,
				bounds.minY(), hasTags);

			for(EspEquipmentLayout.Slot slot : slots)
			{
				ItemStack stack = equipment.get(slot.index());
				PoseStack pose = context.pose();
				pose.pushPose();
				pose.translate(slot.x(), slot.y(), 0);
				pose.scale(EspEquipmentLayout.ICON_SCALE,
					EspEquipmentLayout.ICON_SCALE, 1F);
				context.renderItem(stack, 0, 0);
				pose.popPose();

				renderEnchantNames(context, stack, slot.x(), slot.y());
			}
		}
	}

	/**
	 * 附魔短名。参考把每个附魔各画一行、叠在图标右下角；图标本身只有 10.4px
	 * 宽，那样会和图标以及右边的相邻图标糊在一起，所以这里拼成一行放在图标
	 * 正上方。短名表见 {@link EspEnchantNames}。
	 */
	private void renderEnchantNames(GuiGraphics context, ItemStack stack,
		float x, float y)
	{
		StringBuilder text = new StringBuilder();

		for(Map.Entry<Enchantment, Integer> entry : EnchantmentHelper
			.getEnchantments(stack).entrySet())
		{
			ResourceLocation id =
				BuiltInRegistries.ENCHANTMENT.getKey(entry.getKey());
			if(id == null)
				continue;

			String shortName = EspEnchantNames.shortNameOf(id.getPath());
			if(shortName == null)
				continue;

			if(text.length() > 0)
				text.append(' ');
			text.append(shortName).append(entry.getValue());
		}

		if(text.length() == 0)
			return;

		context.drawString(MC.font, text.toString(), (int)x, (int)(y - 9),
			0xFFFFFFFF, true);
	}

	/**
	 * 这一帧所有内容的并集。方框四周留出健康条（左侧）、护甲条（下方）与
	 * 描边的余量；铭牌条按真实排版结果算，避免长名字被区域裁掉。
	 */
	private float[] contentBounds(List<DrawnBox> drawn)
	{
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;

		for(DrawnBox entry : drawn)
		{
			ScreenBounds bounds = entry.box().bounds();
			minX = Math.min(minX, bounds.minX() - 8);
			minY = Math.min(minY, bounds.minY() - 4);
			maxX = Math.max(maxX, bounds.maxX() + 8);
			maxY = Math.max(maxY, bounds.maxY() + 8);

			EspNameTagLayout.Layout tag = entry.tag();
			if(tag == null || tag.isEmpty())
				continue;

			float bgY = tag.placed().get(0).bgY();
			float bgHeight = tag.placed().get(0).bgHeight();
			minX = Math.min(minX, tag.startX() - 2);
			minY = Math.min(minY, bgY - 1);
			maxX = Math.max(maxX, tag.startX() + tag.totalWidth() + 2);
			maxY = Math.max(maxY, bgY + bgHeight + 1);
		}

		if(minX > maxX || minY > maxY)
			return null;
		return new float[]{minX, minY, maxX, maxY};
	}

	private void renderScreenBox(DrawnBox entry)
	{
		ScreenBox box = entry.box();
		ScreenBounds bounds = box.bounds();
		float x1 = bounds.minX();
		float y1 = bounds.minY();
		float x2 = bounds.maxX();
		float y2 = bounds.maxY();
		float width = x2 - x1;
		float height = y2 - y1;

		int lineColor = box.color() & 0x00FFFFFF
			| (int)(lineOpacity.getValue() * 255) << 24;
		float radius = roundedBox.isChecked() ? cornerRadius.getValueF() : 0;

		double fillAlpha = fillOpacity.getValue();
		if(fillAlpha > 0)
			EspSkia.fillRoundRect(x1, y1, width, height, radius,
				box.color() & 0x00FFFFFF | (int)(fillAlpha * 120) << 24);

		if(boxStroke.isChecked())
			// 参考 ESPModule.renderFullBox() 的 boxStroke 分支：
			// rectOutlineStroke(x, y, w, h, 0.5, 0.5 * 3, color, 0xff000000)
			EspSkia.outlineRectCased(x1, y1, width, height, 0.5F, 1.5F,
				lineColor, 0xFF000000);
		else if(radius > 0)
			EspSkia.strokeRoundRect(x1, y1, width, height, radius, 1F,
				lineColor);
		else
			EspSkia.outlineRect(x1, y1, width, height, 1F, lineColor);

		if(healthBar.isChecked())
		{
			float barX = x1 - 4;
			float top = Mth.lerp(box.health(), y2, y1);
			EspSkia.fillRect(barX, y1, 2, height, 0xA0000000);
			EspSkia.fillRect(barX, top, 2, y2 - top, lineColor);
		}

		if(armorBar.isChecked() && box.armor() > 0)
		{
			float filled = width * box.armor();
			EspSkia.fillRect(x1, y2 + 2, width, 2, 0xA0000000);
			EspSkia.fillRect(x1, y2 + 2, filled, 2, 0xFF55AAFF);
		}

		if(entry.tag() != null && !entry.tag().isEmpty())
			renderNameTag(entry.tag());
	}

	/** 按参考的元素顺序与排版算好这一帧的铭牌条。 */
	private EspNameTagLayout.Layout layoutNameTag(ScreenBox box,
		EspNameTagLayout.GlyphMeasurer measurer)
	{
		Player player = box.player();
		if(player == null || MC.player == null)
			return null;

		boolean indicators = statusIndicators.isChecked();
		EspNameTagPolicy.Options options = new EspNameTagPolicy.Options(
			indicators, indicators, indicators, tagDistance.isChecked(), true,
			tagHealth.isChecked());

		EspNameTagPolicy.State state = new EspNameTagPolicy.State(
			player.isCrouching(), player.isInvisible(), player.isBlocking(),
			(int)Math.floor(MC.player.distanceTo(player)),
			player.getDisplayName().getString(), player.getHealth(),
			player.getAbsorptionAmount());

		List<EspNameTagElement> elements =
			EspNameTagPolicy.build(options, state);
		if(elements.isEmpty())
			return null;

		ScreenBounds bounds = box.bounds();
		return EspNameTagLayout.layout(elements,
			(bounds.minX() + bounds.maxX()) / 2F, bounds.minY(), measurer);
	}

	/**
	 * Skia 不可用时的铭牌兜底。
	 *
	 * <p>
	 * 排版结果与 Skia 路径<b>是同一份</b>（同一个
	 * {@link EspNameTagLayout.Layout}），只是换成原版图元：背景用
	 * {@code RenderUtils.fill2D}，文字用 {@code drawString}。所以两条路径给出的
	 * 元素顺序、间距、居中位置都一致，差别只在字形的抗锯齿与栅格化。
	 *
	 * <p>
	 * 原版没有「按字号绘制」的入口，整串用 pose 缩放到铭牌字号，再把
	 * Skia/NanoVG 的<b>基线</b>口径换算成原版的<b>顶边</b>口径
	 * （{@link EspNameTagLayout#vanillaTextTop}）。
	 *
	 * <p>
	 * 图标相对正文低 1px，与参考的 {@code position.y + 1} 一致。
	 */
	private void renderNameTagsVanilla(GuiGraphics context,
		List<DrawnBox> drawn)
	{
		Font font = MC.font;
		float scale = EspNameTagLayout.vanillaScale(font.lineHeight);
		int lineHeight = font.lineHeight;

		// 先把所有背景铺完再画字：背景条之间不重叠（间距 5、内边距 2），
		// 所以两趟不会互相遮挡，而 fill2D 的批次要先结束才能交给
		// drawString 的另一种渲染类型。
		for(DrawnBox entry : drawn)
		{
			EspNameTagLayout.Layout layout = entry.tag();
			if(layout == null || layout.isEmpty())
				continue;

			for(EspNameTagLayout.Placed placed : layout.placed())
				RenderUtils.fill2D(context, placed.bgX(), placed.bgY(),
					placed.bgX() + placed.bgWidth(),
					placed.bgY() + placed.bgHeight(), 0x80000000);
		}
		RenderUtils.getVCP().endBatch();

		for(DrawnBox entry : drawn)
		{
			EspNameTagLayout.Layout layout = entry.tag();
			if(layout == null || layout.isEmpty())
				continue;

			for(EspNameTagLayout.Placed placed : layout.placed())
			{
				EspNameTagElement element = placed.element();
				float textTop = EspNameTagLayout.vanillaTextTop(placed.bgY(),
					placed.bgHeight(), scale, lineHeight);

				if(element.hasIcon())
					drawVanillaGlyph(context, font, element.icon().glyph(),
						placed.iconX(), textTop + scale, scale,
						element.color());
				if(element.hasText())
					drawVanillaGlyph(context, font, element.text(),
						placed.textX(), textTop, scale, element.color());
			}
		}
	}

	private static void drawVanillaGlyph(GuiGraphics context, Font font,
		String text, float x, float topY, float scale, int color)
	{
		if(text == null || text.isEmpty() || Float.isNaN(x))
			return;

		context.pose().pushPose();
		context.pose().translate(x, topY, 0);
		context.pose().scale(scale, scale, 1);
		context.drawString(font, text, 0, 0, color, false);
		context.pose().popPose();
	}

	private void renderNameTag(EspNameTagLayout.Layout layout)
	{
		float fontSize = EspNameTagLayout.FONT_SIZE;

		for(EspNameTagLayout.Placed placed : layout.placed())
		{
			// 参考在此处先铺 NVGRenderer.BLUR_PAINT 再叠 50% 黑。CPU 光栅
			// 画布拿不到游戏帧缓冲，做不了真正的背景模糊，这里只保留那层
			// 半透明黑底（见 docs/openaopal-port.md「不搬的部分」）。
			EspSkia.fillRoundRect(placed.bgX(), placed.bgY(),
				placed.bgWidth(), placed.bgHeight(),
				EspNameTagLayout.BG_RADIUS, 0x80000000);

			EspNameTagElement element = placed.element();
			if(element.hasIcon())
				// 参考把图标画在 position.y + 1，正文画在 position.y
				EspSkia.textBaseline(element.icon().glyph(), placed.iconX(),
					layout.baselineY() + 1, fontSize, element.color());
			if(element.hasText())
				EspSkia.textBaseline(element.text(), placed.textX(),
					layout.baselineY(), fontSize, element.color());
		}
	}

	/**
	 * Skia 不可用时的原版兜底：方框/血条/护甲条的画法与加入 Skia 路径之前
	 * 逐像素一致；铭牌条另见 {@link #renderNameTagsVanilla}。
	 */
	private void renderScreenBoxesVanilla(GuiGraphics context)
	{
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
		RenderUtils.getVCP().endBatch();
	}
	
	private int getColor(Player e)
	{
		if(WURST.getFriends().contains(e.getName().getString()))
			return friendColor.getColorI();

		return EntityEspRenderer.getColor(e, colorMode.getSelected(),
			customColor.getColorI(), colorRange.getValue());
	}

	private record ScreenBox(ScreenBounds bounds, int color, float health,
		float armor, Player player)
	{
	}

	/** 一个已经预算好铭牌排版的方框。 */
	private record DrawnBox(ScreenBox box, EspNameTagLayout.Layout tag)
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
