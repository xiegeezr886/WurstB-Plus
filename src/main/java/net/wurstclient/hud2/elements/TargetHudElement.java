package net.wurstclient.hud2.elements;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.hack.HackList;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.hud2.HudManager;
import net.wurstclient.hud2.render.RiseFrostedGlass;
import net.wurstclient.hud2.render.RiseHudFont;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ScreenRegistry;
import net.wurstclient.util.esp.EspEquipmentLayout;

/** Rise 6.1.30 Modern TargetInfo adapted to the current combat events. */
public final class TargetHudElement extends HudElement
	implements PlayerAttacksEntityListener
{
	static final long TARGET_TIMEOUT_NANOS = 1_000_000_000L;
	static final long ENTER_NANOS = 850_000_000L;
	static final long EXIT_NANOS = 400_000_000L;
	static final long HEALTH_ANIMATION_NANOS = 250_000_000L;
	private static final long PARTICLE_INTERVAL_NANOS = 45_000_000L;
	private static final long PARTICLE_LIFETIME_NANOS = 650_000_000L;
	private static final int DEFAULT_WIDTH = 145;
	private static final int HEIGHT = 48;
	private static final int EDGE_OFFSET = 8;
	private static final int FACE_SIZE = 32;
	private static final int PADDING = 7;
	private static final int INDENT = 4;
	private static final int PANEL_RADIUS = 19;
	private static final int ACCENT_SECONDARY = 0xFF79A0FF;

	/** 装备行：图标缩放与格子尺寸（参考用 stackScale 0.625 + 10.5 的格子）。 */
	private static final float EQUIPMENT_SCALE = 0.5F;
	private static final float EQUIPMENT_STEP =
		EQUIPMENT_SCALE * EspEquipmentLayout.ICON_SIZE;
	private static final float EQUIPMENT_SLOT = EQUIPMENT_STEP - 1F;
	private static final WurstClient WURST = WurstClient.INSTANCE;

	private final List<HitParticle> particles = new ArrayList<>();
	private Player attackedTarget;
	private long attackedAtNanos;
	private Player displayedTarget;
	private long shownAtNanos;
	private long lastVisibleNanos;
	private long lastRenderNanos;
	private long lastParticleNanos;
	private float displayedHealth = Float.NaN;
	private float healthAnimationFrom;
	private float healthAnimationTarget;
	private long healthAnimationStarted;

	// 逐元素设置。默认值必须等于加这个开关之前的行为，否则老用户的观感会变。
	private final CheckboxSetting equipment = new CheckboxSetting("Equipment",
		"Show the target's armor and held item under the health bar.", true);

	public TargetHudElement()
	{
		super("target_hud", "目标信息");
		addSetting(equipment);
	}

	@Override
	public void onEnable(HudManager manager)
	{
		WURST.getEventManager().add(PlayerAttacksEntityListener.class, this);
	}

	@Override
	public void onDisable(HudManager manager)
	{
		WURST.getEventManager().remove(PlayerAttacksEntityListener.class, this);
		clearTarget();
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(target instanceof Player player && player != WurstClient.MC.player)
		{
			attackedTarget = player;
			attackedAtNanos = System.nanoTime();
		}
	}

	@Override
	public int getWidth()
	{
		Player target = WurstClient.MC != null
			&& ScreenRegistry.HUD_EDITOR.isOpen() ? WurstClient.MC.player
				: displayedTarget;
		if(target == null || WurstClient.MC == null
			|| WurstClient.MC.font == null)
			return DEFAULT_WIDTH;
		float health = Float.isNaN(displayedHealth) ? target.getHealth()
			: displayedHealth;
		return widthFor(target, health, WurstClient.MC.font);
	}

	@Override
	public int getHeight()
	{
		return HEIGHT;
	}

	@Override
	public boolean renderEditorPreview()
	{
		return true;
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y, float partialTicks)
	{
		long now = System.nanoTime();
		if(ScreenRegistry.HUD_EDITOR.isOpen() && WurstClient.MC.player != null)
		{
			Player player = WurstClient.MC.player;
			displayedHealth =
				player.getHealth() + player.getAbsorptionAmount();
			drawPanel(graphics, x, y, player, 1, partialTicks);
			return;
		}
		if(WurstClient.MC.player == null || WurstClient.MC.level == null)
		{
			clearTarget();
			return;
		}
		if(displayedTarget != null
			&& displayedTarget.level() != WurstClient.MC.level)
			clearTarget();

		Player candidate = resolveTarget(now);
		if(candidate != null)
		{
			if(candidate != displayedTarget)
				beginTarget(candidate, now);
			lastVisibleNanos = now;
		}
		if(displayedTarget == null)
			return;

		boolean exiting = candidate == null;
		float scale = exiting
			? exitScale((now - lastVisibleNanos) / (float)EXIT_NANOS)
			: easeOutElastic((now - shownAtNanos) / (float)ENTER_NANOS);
		if(exiting && now - lastVisibleNanos >= EXIT_NANOS)
		{
			clearTarget();
			return;
		}

		long elapsed = lastRenderNanos == 0 ? 0 : now - lastRenderNanos;
		lastRenderNanos = now;
		updateHealth(displayedTarget.getHealth()
			+ displayedTarget.getAbsorptionAmount(), now);
		updateParticles(displayedTarget, now, elapsed);
		drawPanel(graphics, x, y, displayedTarget, Math.max(0, scale),
			partialTicks);
	}

	private void beginTarget(Player target, long now)
	{
		displayedTarget = target;
		shownAtNanos = now;
		lastVisibleNanos = now;
		displayedHealth = target.getHealth();
		healthAnimationFrom = displayedHealth;
		healthAnimationTarget = displayedHealth;
		healthAnimationStarted = now;
		particles.clear();
	}

	private Player resolveTarget(long now)
	{
		if(now - attackedAtNanos <= TARGET_TIMEOUT_NANOS
			&& isUsable(attackedTarget))
			return attackedTarget;

		HackList hax = WURST.getHax();
		if(hax != null)
		{
			Player auraTarget = asPlayer(hax.killauraHack.getCurrentTarget());
			if(isUsable(auraTarget))
				return auraTarget;
			auraTarget = asPlayer(hax.multiAuraHack.getCurrentTarget());
			if(isUsable(auraTarget))
				return auraTarget;
		}

		if(WurstClient.MC.hitResult instanceof EntityHitResult hitResult)
		{
			Player crosshairTarget = asPlayer(hitResult.getEntity());
			if(isUsable(crosshairTarget))
				return crosshairTarget;
		}
		return null;
	}

	private static Player asPlayer(Entity entity)
	{
		return entity instanceof Player player ? player : null;
	}

	private static boolean isUsable(Player player)
	{
		return player != null && player != WurstClient.MC.player
			&& player.level() == WurstClient.MC.level && !player.isRemoved()
			&& player.isAlive();
	}

	private void updateHealth(float targetHealth, long now)
	{
		if(Float.isNaN(displayedHealth))
			displayedHealth = targetHealth;
		if(Math.abs(targetHealth - healthAnimationTarget) > 0.001F)
		{
			displayedHealth = interpolateHealth(healthAnimationFrom,
				healthAnimationTarget, now - healthAnimationStarted);
			healthAnimationFrom = displayedHealth;
			healthAnimationTarget = targetHealth;
			healthAnimationStarted = now;
		}
		displayedHealth = interpolateHealth(healthAnimationFrom,
			healthAnimationTarget, now - healthAnimationStarted);
	}

	private void drawPanel(GuiGraphics graphics, int x, int y, Player target,
		float scale, float partialTicks)
	{
		Font font = WurstClient.MC.font;
		int width = widthFor(target, displayedHealth, font);
		graphics.pose().pushPose();
		graphics.pose().translate((x + width / 2F) * (1 - scale),
			(y + HEIGHT / 2F) * (1 - scale), 0);
		graphics.pose().scale(scale, scale, 1);
		try
		{
			drawPanelContents(graphics, x, y, width, target, partialTicks);
		}finally
		{
			graphics.pose().popPose();
		}
	}

	private void drawPanelContents(GuiGraphics graphics, int x, int y,
		int width, Player target, float partialTicks)
	{
		Font font = WurstClient.MC.font;
		RiseFrostedGlass.draw(graphics, x, y, x + width - 1, y + HEIGHT,
			PANEL_RADIUS, 1, 0x6E000000);

		// 参考 TargetInfoElement.java:118-121 的酒量口径：吸收值同时进分子与分母，
		// 所以「满血 + 满吸收」才是满条。
		float absorption = target.getAbsorptionAmount();
		float totalMax = target.getMaxHealth() + absorption;
		float health = Math.min(displayedHealth, totalMax);
		String healthText = formatHealth(health);
		int healthTextWidth = RiseHudFont.width(font, healthText);
		int nameWidth = RiseHudFont.width(font, target.getGameProfile().getName());
		int healthBarWidth = healthBarWidth(nameWidth, healthTextWidth);
		int contentX = x + EDGE_OFFSET + FACE_SIZE + PADDING;

		RiseHudFont.draw(graphics, font, "Name:", contentX,
			y + EDGE_OFFSET + INDENT + 2, VisualTheme.TEXT, true);
		int labelWidth = RiseHudFont.width(font, "Name:");
		RiseHudFont.draw(graphics, font, target.getGameProfile().getName(),
			contentX + labelWidth + 3, y + EDGE_OFFSET + INDENT + 2,
			VisualTheme.ACCENT, true);

		int barY = y + EDGE_OFFSET + FACE_SIZE - INDENT - 7;
		FlatRenderer.fillRoundedRect(graphics, contentX, barY,
			contentX + healthBarWidth, barY + 6, 3, 0x76000000);

		// 参考是两层：下面那层是被伤害时滞后的暗色条，上面压着真实值。
		// 回血时动画值落后于真实值，暗条比亮条短，自然被盖住；掉血时反过来，
		// 于是露出一截暗色拖尾。
		int progressWidth = Math.round(healthBarWidth
			* healthRatio(health, target.getMaxHealth(), absorption));
		if(progressWidth > 0)
			drawHealthGradient(graphics, contentX, barY, progressWidth, true);

		int trueWidth = Math.round(healthBarWidth * healthRatio(
			target.getHealth() + absorption, target.getMaxHealth(), absorption));
		if(trueWidth > 0)
			drawHealthGradient(graphics, contentX, barY, trueWidth, false);

		RiseHudFont.draw(graphics, font, healthText,
			contentX + healthBarWidth + INDENT, barY - 1, VisualTheme.ACCENT,
			true);

		drawParticles(graphics, x, y);
		drawFace(graphics, target, x, y, partialTicks);
		if(equipment.isChecked())
			drawEquipment(graphics, target, x, y, width, contentX);
	}

	/**
	 * 目标身上的护甲四件与主手，排在面板下半部分的空带上。
	 *
	 * <p>
	 * 参考的 TargetInfoElement 把这一行画在面板<b>上半部分</b>、头像右边
	 * （{@code y + padding + 8.5}）。本工程的面板把名字与血条放在那里，所以
	 * 改放到头像下方的空带；行的横向口径复用
	 * {@link EspEquipmentLayout#rowStartX}，与 ESP 铭牌条保持同一套间距约定
	 * （最后一项在最左，也就是主手最左——参考是先把列表 reverse 再正序画的，
	 * 结果相同）。
	 *
	 * <p>
	 * 参考还在这行上叠了附魔短名，但本面板只有 {@value #HEIGHT}px 高、图标缩放
	 * 后不到 9px，再叠字会糊在一起，所以这里只画图标；附魔短名在 ESP 铭牌条
	 * 那边已经消费了。
	 */
	private static void drawEquipment(GuiGraphics graphics, Player target, int x,
		int y, int width, int contentX)
	{
		List<ItemStack> equipment = collectEquipment(target);
		if(equipment.isEmpty())
			return;

		int count = equipment.size();
		// 内容区（头像右侧到面板右边距之间）的中心
		float centerX = (contentX + x + width - EDGE_OFFSET) / 2F;
		float startX = EspEquipmentLayout.rowStartX(count, centerX,
			EQUIPMENT_STEP);
		int rowY = Math.round(y + EDGE_OFFSET + FACE_SIZE - INDENT + 1);

		for(int i = 0; i < count; i++)
		{
			int slotX =
				Math.round(startX + EspEquipmentLayout.rowOffsetX(count, i,
					EQUIPMENT_STEP));
			FlatRenderer.fillRoundedRect(graphics, slotX, rowY,
				slotX + Math.round(EQUIPMENT_SLOT),
				rowY + Math.round(EQUIPMENT_SLOT), 1, 0x33000000);

			ItemStack stack = equipment.get(i);
			graphics.pose().pushPose();
			graphics.pose().translate(slotX + 0.5F, rowY + 0.5F, 0);
			graphics.pose().scale(EQUIPMENT_SCALE, EQUIPMENT_SCALE, 1);
			graphics.renderItem(stack, 0, 0);
			graphics.pose().popPose();
		}
	}

	/** 护甲四件（头→胸→腿→靴，空槽跳过）+ 主手。 */
	private static List<ItemStack> collectEquipment(Player target)
	{
		List<ItemStack> equipment = new ArrayList<>(5);

		for(ItemStack stack : target.getArmorSlots())
			if(!stack.isEmpty())
				equipment.add(stack);

		ItemStack mainHand = target.getMainHandItem();
		if(!mainHand.isEmpty())
			equipment.add(mainHand);

		return equipment;
	}

	/** @param dark 画底层那根滞后的暗色条时传 true（参考 darker(theme, 0.6)）。 */
	private static void drawHealthGradient(GuiGraphics graphics, int x, int y,
		int width, boolean dark)
	{
		int from = dark ? VisualTheme.mix(ACCENT_SECONDARY, 0xFF000000, 0.4F)
			: ACCENT_SECONDARY;
		int to = dark ? VisualTheme.mix(VisualTheme.ACCENT, 0xFF000000, 0.4F)
			: VisualTheme.ACCENT;

		FlatRenderer.fillRoundedRect(graphics, x, y, x + width, y + 6, 3, from);
		if(width <= 4)
			return;
		for(int column = 2; column < width - 2; column++)
		{
			float progress = column / (float)Math.max(1, width - 1);
			graphics.fill(x + column, y, x + column + 1, y + 6,
				VisualTheme.mix(from, to, progress));
		}
	}

	private static int widthFor(Player target, float health, Font font)
	{
		String name = target.getGameProfile().getName();
		String healthText = formatHealth(health);
		return panelWidth(RiseHudFont.width(font, name),
			RiseHudFont.width(font, healthText));
	}

	static int panelWidth(int nameWidth, int healthTextWidth)
	{
		int barWidth = healthBarWidth(nameWidth, healthTextWidth);
		return EDGE_OFFSET + FACE_SIZE + EDGE_OFFSET + barWidth + INDENT
			+ healthTextWidth + EDGE_OFFSET;
	}

	static int healthBarWidth(int nameWidth, int healthTextWidth)
	{
		return Math.max(nameWidth + 35 - healthTextWidth, 65);
	}

	static String formatHealth(float health)
	{
		double rounded = Math.round(Math.max(0, health) * 10) / 10D;
		return String.format(Locale.ROOT, "%.1f", rounded);
	}

	/**
	 * 血条比例的参考口径（{@code TargetInfoElement.java:118-121}）：吸收值同时进
	 * 分子与分母，所以 20/20 血 + 4 吸收时，要 24 点才算满条。本方法收的是
	 * <b>已经加上吸收</b>的显示值 {@code combinedHealth}，分母再补一次吸收。
	 *
	 * <p>
	 * 纯算、不碰 {@code Mth}，所以能在普通 JUnit 里直接跑。分母非正时返回 0，
	 * 不产生 NaN/除零。
	 */
	static float healthRatio(float combinedHealth, float maxHealth,
		float absorption)
	{
		float totalMax = maxHealth + absorption;
		if(!(totalMax > 0))
			return 0;
		float ratio = combinedHealth / totalMax;
		if(!(ratio > 0))
			return 0;
		return ratio > 1 ? 1 : ratio;
	}

	private void drawFace(GuiGraphics graphics, Player target, int panelX,
		int panelY, float partialTicks)
	{
		float hurt = target.hurtTime == 0 ? 0
			: Math.max(0, target.hurtTime - partialTicks) * 0.5F;
		int size = Math.max(24, Math.round(FACE_SIZE - hurt));
		int offset = Math.round(hurt / 2F);
		int x = panelX + EDGE_OFFSET + offset;
		int y = panelY + EDGE_OFFSET + offset;
		FlatRenderer.fillRoundedRect(graphics, x, y, x + size, y + size, 8,
			0xAA000000);

		ResourceLocation skin = target instanceof AbstractClientPlayer player
			? player.getSkinTextureLocation() : null;
		if(skin != null)
		{
			float tint = 1 - Mth.clamp(hurt / 9F, 0, 0.75F);
			RenderSystem.setShaderColor(1, tint, tint, 1);
			try
			{
				graphics.blit(skin, x + 1, y + 1, size - 2, size - 2,
					8, 8, 8, 8, 64, 64);
				graphics.blit(skin, x + 1, y + 1, size - 2, size - 2,
					40, 8, 8, 8, 64, 64);
			}finally
			{
				RenderSystem.setShaderColor(1, 1, 1, 1);
			}
		}
		FlatRenderer.drawRoundedOutline(graphics, x, y, x + size, y + size,
			8, 0x28000000);
	}

	private void updateParticles(Player target, long now, long elapsedNanos)
	{
		float elapsedSeconds = Math.min(0.1F, elapsedNanos / 1_000_000_000F);
		for(HitParticle particle : particles)
		{
			particle.x += particle.velocityX * elapsedSeconds;
			particle.y += particle.velocityY * elapsedSeconds;
		}
		particles.removeIf(particle -> now - particle.startedAt
			>= PARTICLE_LIFETIME_NANOS);

		if(target.hurtTime <= 0 || now - lastParticleNanos
			< PARTICLE_INTERVAL_NANOS)
			return;
		lastParticleNanos = now;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for(int i = 0; i < 2 && particles.size() < 24; i++)
			particles.add(new HitParticle(20, 20,
				(float)random.nextDouble(-28, 28),
				(float)random.nextDouble(-28, 28), now,
				random.nextBoolean() ? 1 : 2));
	}

	private void drawParticles(GuiGraphics graphics, int panelX, int panelY)
	{
		long now = System.nanoTime();
		for(HitParticle particle : particles)
		{
			float life = Mth.clamp((now - particle.startedAt)
				/ (float)PARTICLE_LIFETIME_NANOS, 0, 1);
			int alpha = Math.round((1 - life) * 210);
			int x = panelX + Math.round(particle.x);
			int y = panelY + Math.round(particle.y);
			int color = VisualTheme.withAlpha(VisualTheme.ACCENT, alpha);
			FlatRenderer.fillRoundedRect(graphics, x, y,
				x + particle.size, y + particle.size, particle.size, color);
		}
	}

	static float interpolateHealth(float from, float to, long elapsedNanos)
	{
		float progress = Mth.clamp(elapsedNanos
			/ (float)HEALTH_ANIMATION_NANOS, 0, 1);
		return Mth.lerp(easeOutQuint(progress), from, to);
	}

	static float easeOutQuint(float progress)
	{
		float value = Mth.clamp(progress, 0, 1);
		return 1 - (float)Math.pow(1 - value, 5);
	}

	static float easeOutElastic(float progress)
	{
		float value = Mth.clamp(progress, 0, 1);
		if(value == 0 || value == 1)
			return value;
		double phase = 2 * Math.PI / 3;
		return (float)(Math.pow(2, -10 * value)
			* Math.sin((value * 10 - 0.75) * phase) + 1);
	}

	static float exitScale(float progress)
	{
		float value = Mth.clamp(progress, 0, 1);
		float overshoot = 1.70158F;
		float eased = (overshoot + 1) * value * value * value
			- overshoot * value * value;
		return 1 - eased;
	}

	private void clearTarget()
	{
		attackedTarget = null;
		attackedAtNanos = 0;
		displayedTarget = null;
		shownAtNanos = 0;
		lastVisibleNanos = 0;
		lastRenderNanos = 0;
		lastParticleNanos = 0;
		displayedHealth = Float.NaN;
		healthAnimationFrom = 0;
		healthAnimationTarget = 0;
		healthAnimationStarted = 0;
		particles.clear();
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_TOP, 8, 126);
	}

	private static final class HitParticle
	{
		private float x;
		private float y;
		private final float velocityX;
		private final float velocityY;
		private final long startedAt;
		private final int size;

		private HitParticle(float x, float y, float velocityX, float velocityY,
			long startedAt, int size)
		{
			this.x = x;
			this.y = y;
			this.velocityX = velocityX;
			this.velocityY = velocityY;
			this.startedAt = startedAt;
			this.size = size;
		}
	}
}
