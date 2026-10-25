package net.wurstclient.hud2.elements;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.HackList;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.HudManager;

/**
 * Target info panel using Soar Client v7.1.2's layout: 140x46 card, 36px
 * player head and a health bar.
 */
public final class TargetHudElement extends HUDMod
	implements PlayerAttacksEntityListener
{
	private static final long ATTACK_PRIORITY_NANOS = 1_250_000_000L;
	private static final long HOLD_NANOS = 550_000_000L;
	private static final long FADE_NANOS = 400_000_000L;
	private static final int BASE_WIDTH = 140;
	private static final int BASE_HEIGHT = 46;
	private static final WurstClient WURST = WurstClient.INSTANCE;

	private Player attackedTarget;
	private long attackedAtNanos;
	private Player displayedTarget;
	private long lastVisibleNanos;
	private long lastRenderNanos;
	private float displayedHealth = Float.NaN;

	public TargetHudElement()
	{
		super("target_hud", "Target Info");
		setDefaultPosition(8, 210);
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
	protected void draw()
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

		Player candidate = isEditing() ? WurstClient.MC.player
			: resolveTarget(now);
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

		float nameWidth = getTextWidth(displayedTarget.getName().getString(),
			10.2F);
		int width = nameWidth + 48F > BASE_WIDTH
			? (int)(BASE_WIDTH + nameWidth - 89) : BASE_WIDTH;
		drawBackground(width, BASE_HEIGHT);
		drawFace(displayedTarget, 5, 5, 36, opacity);
		drawText(displayedTarget.getName().getString(), 45.5F, 8F, 10.2F);

		float maxHealth = Math.max(1, displayedTarget.getMaxHealth());
		float ratio = Mth.clamp(displayedHealth / maxHealth, 0, 1);
		drawText("HP " + Math.round(displayedHealth), 45.5F, 24F, 9,
			getFontColor(Math.round(255 * opacity)));
		drawRect(45.5F, 35F, width - 52F, 4F,
			getFontColor(Math.round(45 * opacity)));
		int barWidth = Math.round((width - 52F) * ratio);
		if(barWidth > 0)
			drawRect(45.5F, 35F, barWidth, 4F,
				accentColor(Math.round(235 * opacity)));

		setSize(width, BASE_HEIGHT);
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

	private void drawFace(Player target, float x, float y, float size,
		float opacity)
	{
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

	private static float calculateOpacity(long now, long lastVisible)
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

	private static float smoothHealth(float current, float target,
		long elapsedNanos)
	{
		if(Float.isNaN(current) || elapsedNanos <= 0)
			return target;
		float step = Mth.clamp(elapsedNanos / 180_000_000F, 0, 1);
		return Mth.lerp(step, current, target);
	}
}
