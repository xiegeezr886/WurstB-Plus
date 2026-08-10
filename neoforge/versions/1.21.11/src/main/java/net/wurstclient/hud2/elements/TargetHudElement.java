package net.wurstclient.hud2.elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.Font;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.client.renderer.RenderPipelines;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.HackList;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.hud2.HudManager;
import net.wurstclient.util.RenderUtils;

public final class TargetHudElement extends HudElement
	implements PlayerAttacksEntityListener
{
	static final long ATTACK_PRIORITY_NANOS = 1_250_000_000L;
	static final long HOLD_NANOS = 550_000_000L;
	static final long FADE_NANOS = 400_000_000L;
	private static final int WIDTH = 150;
	private static final int BASE_HEIGHT = 38;
	private static final int EQUIPMENT_HEIGHT = 58;
	private static final int EQUIPMENT_ITEM_SIZE = 16;
	private static final int EQUIPMENT_ITEM_STEP = 18;
	private static final int CARD_RADIUS = 4;
	private static final int CARD_FILL = 0xAD050505;
	private static final int CARD_OUTLINE = 0x10FFFFFF;
	private static final int FACE_FILL = 0x68070B10;
	private static final int FACE_OUTLINE = 0x14FFFFFF;
	private static final int TEXT_SHADOW = 0x91000000;
	private static final int TEXT_PRIMARY = 0xFFF2F4F7;
	private static final int HEALTH_TRACK = 0xA020252A;
	private static final WurstClient WURST = WurstClient.INSTANCE;

	private Player attackedTarget;
	private long attackedAtNanos;
	private Player displayedTarget;
	private long lastVisibleNanos;
	private long lastRenderNanos;
	private float displayedHealth = Float.NaN;

	public TargetHudElement()
	{
		super("target_hud", "目标信息");
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
		return WIDTH;
	}

	@Override
	public int getHeight()
	{
		return heightForEquipmentCount(getEquipment(displayedTarget).size());
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, float partialTicks)
	{
		long now = System.nanoTime();
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
			{
				displayedTarget = candidate;
				displayedHealth = candidate.getHealth();
			}
			lastVisibleNanos = now;
		}

		if(displayedTarget == null)
			return;
		float opacity = candidate != null ? 1
			: calculateOpacity(now, lastVisibleNanos);
		if(opacity <= 0)
		{
			clearTarget();
			return;
		}

		long elapsed = lastRenderNanos == 0 ? 0 : now - lastRenderNanos;
		lastRenderNanos = now;
		displayedHealth = smoothHealth(displayedHealth,
			displayedTarget.getHealth(), elapsed);
		drawPanel(graphics, x, y, displayedTarget, opacity);
	}

	private Player resolveTarget(long now)
	{
		if(now - attackedAtNanos <= ATTACK_PRIORITY_NANOS
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

	private Player asPlayer(Entity entity)
	{
		return entity instanceof Player player ? player : null;
	}

	private boolean isUsable(Player player)
	{
		return player != null && player != WurstClient.MC.player
			&& player.level() == WurstClient.MC.level && !player.isRemoved()
			&& player.isAlive();
	}

	private void drawPanel(GuiGraphicsExtractor graphics, int x, int y, Player target,
		float opacity)
	{
		Font font = WurstClient.MC.font;
		List<ItemStack> equipment = getEquipment(target);
		int panelHeight = heightForEquipmentCount(equipment.size());
		FlatRenderer.fillRoundedRect(graphics, x, y, x + WIDTH, y + panelHeight,
			CARD_RADIUS, withOpacity(CARD_FILL, opacity));
		FlatRenderer.drawRoundedOutline(graphics, x, y, x + WIDTH,
			y + panelHeight,
			CARD_RADIUS, withOpacity(CARD_OUTLINE, opacity));

		int themeColor = getThemeAccentColor();
		FlatRenderer.fillRoundedRect(graphics, x + 1, y + 2, x + 3,
			y + panelHeight - 2, 1,
			withOpacity(themeColor, opacity * 0.94F));
		drawFace(graphics, target, x + 7, y + 5, 28, opacity);

		String name = font.plainSubstrByWidth(target.getName().getString(),
			WIDTH - 49);
		graphics.text(font, name, x + 42, y + 7,
			withOpacity(TEXT_SHADOW, opacity), false);
		graphics.text(font, name, x + 41, y + 6,
			withOpacity(TEXT_PRIMARY, opacity), false);

		float maxHealth = Math.max(1, target.getMaxHealth());
		int barLeft = x + 41;
		int barRight = x + WIDTH - 7;
		int barTop = y + 23;
		FlatRenderer.fillRoundedRect(graphics, barLeft, barTop, barRight,
			barTop + 5, 2, withOpacity(HEALTH_TRACK, opacity));
		float healthRatio = Mth.clamp(displayedHealth / maxHealth, 0, 1);
		int progressRight = barLeft
			+ Math.round((barRight - barLeft) * healthRatio);
		if(progressRight > barLeft)
			FlatRenderer.fillRoundedRect(graphics, barLeft, barTop,
				progressRight, barTop + 5, 2,
				withOpacity(healthColor(themeColor, healthRatio), opacity));

		if(!equipment.isEmpty())
			drawEquipment(graphics, equipment, x, y, opacity);
	}

	private void drawEquipment(GuiGraphicsExtractor graphics, List<ItemStack> equipment,
		int x, int y, float opacity)
	{
		graphics.fill(x + 7, y + BASE_HEIGHT - 1, x + WIDTH - 7,
			y + BASE_HEIGHT, withOpacity(0x14FFFFFF, opacity));
		int rowWidth = equipmentRowWidth(equipment.size());
		int itemX = x + (WIDTH - rowWidth) / 2;
		int itemY = y + BASE_HEIGHT + 1;
		for(ItemStack stack : equipment)
		{
			RenderUtils.drawItem(graphics, stack, itemX, itemY, false);
			itemX += EQUIPMENT_ITEM_STEP;
		}
	}

	private static List<ItemStack> getEquipment(Player target)
	{
		if(target == null)
			return List.of();

		List<ItemStack> equipment = new ArrayList<>(5);
		ItemStack mainHand = target.getMainHandItem();
		if(!mainHand.isEmpty())
			equipment.add(mainHand);

		List<ItemStack> armor = new ArrayList<>(4);
		for(var slot : new net.minecraft.world.entity.EquipmentSlot[]{
			net.minecraft.world.entity.EquipmentSlot.FEET,
			net.minecraft.world.entity.EquipmentSlot.LEGS,
			net.minecraft.world.entity.EquipmentSlot.CHEST,
			net.minecraft.world.entity.EquipmentSlot.HEAD})
		{
			ItemStack stack = target.getItemBySlot(slot);
			if(!stack.isEmpty())
				armor.add(stack);
		}
		Collections.reverse(armor);
		armor.stream().filter(stack -> !stack.isEmpty()).forEach(equipment::add);
		return equipment;
	}

	static int heightForEquipmentCount(int equipmentCount)
	{
		return equipmentCount > 0 ? EQUIPMENT_HEIGHT : BASE_HEIGHT;
	}

	static int equipmentRowWidth(int equipmentCount)
	{
		if(equipmentCount <= 0)
			return 0;
		return EQUIPMENT_ITEM_SIZE
			+ (equipmentCount - 1) * EQUIPMENT_ITEM_STEP;
	}

	private void drawFace(GuiGraphicsExtractor graphics, Player target, int x, int y,
		int size, float opacity)
	{
		FlatRenderer.fillRoundedRect(graphics, x, y, x + size, y + size, 4,
			withOpacity(FACE_FILL, opacity));
		Identifier skin = target instanceof AbstractClientPlayer player
			? player.getSkin().body().texturePath() : null;
		if(skin != null)
		{
			try
			{
				graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x + 2, y + 2,
					8, 8, size - 4, size - 4, 8, 8, 64, 64);
				graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x + 2, y + 2,
					40, 8, size - 4, size - 4, 8, 8, 64, 64);
			}finally
			{
			}
		}else
			graphics.centeredText(WurstClient.MC.font, "?", x + size / 2,
				y + size / 2 - 4, withOpacity(TEXT_PRIMARY, opacity));
		FlatRenderer.drawRoundedOutline(graphics, x, y, x + size, y + size,
			4, withOpacity(FACE_OUTLINE, opacity));
	}

	private void clearTarget()
	{
		attackedTarget = null;
		attackedAtNanos = 0;
		displayedTarget = null;
		lastVisibleNanos = 0;
		lastRenderNanos = 0;
		displayedHealth = Float.NaN;
	}

	static float calculateOpacity(long now, long lastVisible)
	{
		long elapsed = Math.max(0, now - lastVisible);
		if(elapsed <= HOLD_NANOS)
			return 1;
		if(elapsed >= HOLD_NANOS + FADE_NANOS)
			return 0;
		float progress = (elapsed - HOLD_NANOS) / (float)FADE_NANOS;
		float eased = progress * progress * (3 - 2 * progress);
		return 1 - eased;
	}

	static float smoothHealth(float current, float target, long elapsedNanos)
	{
		if(Float.isNaN(current) || elapsedNanos <= 0)
			return target;
		float step = Mth.clamp(elapsedNanos / 180_000_000F, 0, 1);
		return Mth.lerp(step, current, target);
	}

	static String compactUuid(UUID uuid)
	{
		return uuid.toString().substring(0, 8);
	}

	private static int getThemeAccentColor()
	{
		WURST.getGui().updateColors();
		return WURST.getGui().getTheme().accent(1);
	}

	static int healthColor(int themeColor, float healthRatio)
	{
		float ratio = Mth.clamp(healthRatio, 0, 1);
		int red = Math.round(Mth.lerp(ratio, 255, themeColor >> 16 & 0xFF));
		int green = Math.round(Mth.lerp(ratio, 65, themeColor >> 8 & 0xFF));
		int blue = Math.round(Mth.lerp(ratio, 48, themeColor & 0xFF));
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static int withOpacity(int color, float opacity)
	{
		int alpha = color >>> 24;
		int fadedAlpha = Math.round(alpha * Mth.clamp(opacity, 0, 1));
		return color & 0x00FFFFFF | fadedAlpha << 24;
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_TOP, 8, 126);
	}
}
