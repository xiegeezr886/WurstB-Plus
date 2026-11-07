# -*- coding: utf-8 -*-
"""Plain (non-HUD) hack sources for the remaining Soar modules.

Each value is a full Java source file.  HUD modules live in
soar_remaining_hud.py.
"""

HEADER = """/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;
"""

PLAIN = {}

PLAIN['EntityCullingHack'] = HEADER + r'''
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Distance and invisibility based entity culling.
 *
 * <p>Soar used GPU occlusion queries. This lite port culls entities beyond a
 * configurable distance and optionally invisible players/armor stands, which
 * needs only the version-independent {@code EntityRenderDispatcher} hook.
 */
@SearchTags({"entity culling", "culling", "performance", "occlusion",
	"hide entities"})
public final class EntityCullingHack extends Hack
{
	private final CheckboxSetting players = new CheckboxSetting("Players",
		"Culls other players beyond the configured distance.", true);

	private final CheckboxSetting otherEntities = new CheckboxSetting(
		"Other entities", "Culls non-player entities beyond the distance.",
		true);

	private final CheckboxSetting invisible = new CheckboxSetting(
		"Invisible", "Culls invisible players and armor stands.", true);

	private final SliderSetting distance = new SliderSetting("Distance",
		"Entities farther away than this are not rendered.", 45, 10, 150, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	public EntityCullingHack()
	{
		super("EntityCulling");
		setCategory(Category.RENDER);
		addSetting(players);
		addSetting(otherEntities);
		addSetting(invisible);
		addSetting(distance);
	}

	public boolean shouldCull(Entity entity)
	{
		if(!isEnabled() || MC.player == null || entity == MC.player
			|| entity == MC.getCameraEntity())
			return false;

		if(entity instanceof Player)
		{
			if(!players.isChecked())
				return false;
		}else if(!otherEntities.isChecked())
			return false;

		if(invisible.isChecked() && entity.isInvisible()
			&& (entity instanceof Player || entity instanceof ArmorStand))
			return true;

		double max = distance.getValue();
		return entity.distanceToSqr(MC.player) > max * max;
	}
}
'''

PLAIN['ChunkAnimatorHack'] = HEADER + r'''
import java.awt.Color;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

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

@SearchTags({"chunk animator", "chunk animation", "chunk fade", "smooth chunks"})
public final class ChunkAnimatorHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting duration = new SliderSetting("Duration",
		"How long newly loaded chunks stay highlighted, in seconds.",
		1, 0, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting radius = new SliderSetting("Radius",
		"Chunk radius to watch for newly loaded chunks.", 8, 2, 16, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting highlight = new CheckboxSetting("Highlight",
		"Draws a fading box around newly loaded chunks.", true);

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the chunk load highlight.", new Color(80, 200, 255));

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Draws the highlight through solid blocks.", true);

	private final Map<Long, Long> highlights = new HashMap<>();
	private final Set<Long> seen = new HashSet<>();
	private long lastScan;

	private Object fadeOption;
	private Object oldFadeValue;
	private Method fadeSet;
	private Method fadeGet;

	public ChunkAnimatorHack()
	{
		super("ChunkAnimator");
		setCategory(Category.RENDER);
		addSetting(duration);
		addSetting(radius);
		addSetting(highlight);
		addSetting(color);
		addSetting(throughWalls);
	}

	@Override
	protected void onEnable()
	{
		highlights.clear();
		seen.clear();
		lastScan = 0;

		// 1.21.11+ has a built-in chunk section fade option. Use it when
		// present; older versions fall back to the highlight below.
		try
		{
			Method getter = MC.options.getClass()
				.getMethod("chunkSectionFadeInTime");
			fadeOption = getter.invoke(MC.options);
			fadeGet = fadeOption.getClass().getMethod("get");
			fadeSet = fadeOption.getClass().getMethod("set", Object.class);
			oldFadeValue = fadeGet.invoke(fadeOption);
			fadeSet.invoke(fadeOption, duration.getValue());
		}catch(Exception ignored)
		{
			fadeOption = null;
			fadeSet = null;
			fadeGet = null;
		}

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);

		if(fadeOption != null && fadeSet != null && oldFadeValue != null)
		{
			try
			{
				fadeSet.invoke(fadeOption, oldFadeValue);
			}catch(Exception ignored)
			{}
		}

		fadeOption = null;
		fadeSet = null;
		fadeGet = null;
		oldFadeValue = null;
		highlights.clear();
		seen.clear();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(fadeOption != null && fadeSet != null)
		{
			try
			{
				fadeSet.invoke(fadeOption, duration.getValue());
			}catch(Exception ignored)
			{}
		}

		long now = System.currentTimeMillis();
		if(now - lastScan < 200)
			return;
		lastScan = now;

		int cx = MC.player.getBlockX() >> 4;
		int cz = MC.player.getBlockZ() >> 4;
		int r = radius.getValueI();

		Set<Long> present = new HashSet<>();
		for(int dx = -r; dx <= r; dx++)
			for(int dz = -r; dz <= r; dz++)
			{
				int x = cx + dx;
				int z = cz + dz;
				if(!MC.level.getChunkSource().hasChunk(x, z))
					continue;

				long pos = chunkKey(x, z);
				present.add(pos);
				if(seen.add(pos))
					highlights.put(pos, now);
			}

		seen.retainAll(present);
		long keep = (long)(duration.getValue() * 1000.0) + 250L;
		highlights.entrySet()
			.removeIf(entry -> now - entry.getValue() > keep);
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks)
	{
		if(!highlight.isChecked() || MC.level == null || MC.player == null
			|| highlights.isEmpty() || duration.getValue() <= 0)
			return;

		long now = System.currentTimeMillis();
		long total = (long)(duration.getValue() * 1000.0);
		int minY = getLevelMinY();
		int maxY = getLevelMaxY();
		boolean depthTest = !throughWalls.isChecked();
		Color base = color.getColor();

		for(Map.Entry<Long, Long> entry : highlights.entrySet())
		{
			long elapsed = now - entry.getValue();
			if(elapsed < 0 || elapsed >= total)
				continue;

			float fade = 1F - elapsed / (float)total;
			int alpha = Math.max(0, Math.min(255, (int)(fade * 200)));
			int argb = alpha << 24 | base.getRGB() & 0xFFFFFF;

			long pos = entry.getKey();
			int x = keyX(pos) << 4;
			int z = keyZ(pos) << 4;
			AABB box = new AABB(x, minY, z, x + 16, maxY, z + 16);
			RenderUtils.drawOutlinedBox(poseStack, box, argb, depthTest);
		}
	}

	private static long chunkKey(int x, int z)
	{
		return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private static int keyX(long key)
	{
		return (int)(key >> 32);
	}

	private static int keyZ(long key)
	{
		return (int)key;
	}

	private int getLevelMinY()
	{
		try
		{
			return ((Number)MC.level.getClass().getMethod("getMinBuildHeight")
				.invoke(MC.level)).intValue();
		}catch(Exception ignored)
		{}

		try
		{
			return ((Number)MC.level.getClass().getMethod("getMinY")
				.invoke(MC.level)).intValue();
		}catch(Exception ignored)
		{}

		return -64;
	}

	private int getLevelMaxY()
	{
		try
		{
			return ((Number)MC.level.getClass().getMethod("getMaxBuildHeight")
				.invoke(MC.level)).intValue();
		}catch(Exception ignored)
		{}

		try
		{
			return ((Number)MC.level.getClass().getMethod("getMaxY")
				.invoke(MC.level)).intValue();
		}catch(Exception ignored)
		{}

		return 320;
	}
}
'''

PLAIN['ClearGlassHack'] = HEADER + r'''
import java.awt.Color;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ShouldDrawSideListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.BlockUtils;

/**
 * Hides glass block faces so that players can see through them.
 *
 * <p>Soar replaced the glass render type with a translucent one. This port
 * simply skips glass faces, which produces the same practical "clear glass"
 * result without version-specific renderer hooks.
 */
@SearchTags({"clear glass", "clearglass", "invisible glass", "transparent glass"})
public final class ClearGlassHack extends Hack
	implements ShouldDrawSideListener, UpdateListener
{
	private final CheckboxSetting normal = new CheckboxSetting("Normal glass",
		"Hides normal glass and glass panes.", true);

	private final CheckboxSetting stained = new CheckboxSetting(
		"Stained glass", "Hides stained glass and stained glass panes.", true);

	private boolean prevNormal;
	private boolean prevStained;

	public ClearGlassHack()
	{
		super("ClearGlass");
		setCategory(Category.RENDER);
		addSetting(normal);
		addSetting(stained);
	}

	@Override
	protected void onEnable()
	{
		prevNormal = normal.isChecked();
		prevStained = stained.isChecked();
		EVENTS.add(ShouldDrawSideListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		reloadChunks();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ShouldDrawSideListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		reloadChunks();
	}

	@Override
	public void onUpdate()
	{
		if(prevNormal == normal.isChecked()
			&& prevStained == stained.isChecked())
			return;

		prevNormal = normal.isChecked();
		prevStained = stained.isChecked();
		reloadChunks();
	}

	@Override
	public void onShouldDrawSide(ShouldDrawSideEvent event)
	{
		if(MC.level == null)
			return;

		String name = BlockUtils.getName(event.getState().getBlock());
		boolean isNormal =
			name.equals("minecraft:glass") || name.equals("minecraft:glass_pane");
		boolean isStained = name.contains("stained_glass");

		if(isNormal && normal.isChecked()
			|| isStained && stained.isChecked())
			event.setRendered(false);
	}

	private void reloadChunks()
	{
		try
		{
			Object renderer = MC.getClass().getField("levelRenderer").get(MC);
			renderer.getClass().getMethod("allChanged").invoke(renderer);
			return;
		}catch(Exception ignored)
		{}

		try
		{
			Object renderer = MC.getClass().getField("levelExtractor").get(MC);
			renderer.getClass().getMethod("allChanged").invoke(renderer);
		}catch(Exception ignored)
		{}
	}
}
'''

PLAIN['ClearWaterHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

@SearchTags({"clear water", "clearwater", "no water overlay", "transparent water"})
public final class ClearWaterHack extends Hack
{
	public ClearWaterHack()
	{
		super("ClearWater");
		setCategory(Category.RENDER);
	}

	// See CameraMixin.getFluidInCamera() and
	// ScreenEffectRendererMixin.renderWater()/submitWater().
}
'''

PLAIN['CustomHeldItemsHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"custom held items", "held item", "hand item", "item position",
	"item scale"})
public final class CustomHeldItemsHack extends Hack
{
	private final SliderSetting x = new SliderSetting("X",
		"Horizontal offset of the held item.", 0, -1, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting y = new SliderSetting("Y",
		"Vertical offset of the held item.", 0, -1, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting z = new SliderSetting("Z",
		"Depth offset of the held item.", 0, -1, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting scaleX = new SliderSetting("X scale",
		"Horizontal scale of the held item.", 1, 0.1, 2, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting scaleY = new SliderSetting("Y scale",
		"Vertical scale of the held item.", 1, 0.1, 2, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting scaleZ = new SliderSetting("Z scale",
		"Depth scale of the held item.", 1, 0.1, 2, 0.05,
		ValueDisplay.DECIMAL);

	public CustomHeldItemsHack()
	{
		super("CustomHeldItems");
		setCategory(Category.RENDER);
		addSetting(x);
		addSetting(y);
		addSetting(z);
		addSetting(scaleX);
		addSetting(scaleY);
		addSetting(scaleZ);
	}

	public float getX()
	{
		return x.getValueF();
	}

	public float getY()
	{
		return y.getValueF();
	}

	public float getZ()
	{
		return z.getValueF();
	}

	public float getScaleX()
	{
		return scaleX.getValueF();
	}

	public float getScaleY()
	{
		return scaleY.getValueF();
	}

	public float getScaleZ()
	{
		return scaleZ.getValueF();
	}

	// See HeldItemRendererMixin.applyCustomHeldItemTransform().
}
'''

PLAIN['DamageTiltHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

@SearchTags({"damage tilt", "hurt direction", "damage direction", "hurt tilt"})
public final class DamageTiltHack extends Hack
{
	public DamageTiltHack()
	{
		super("DamageTilt");
		setCategory(Category.RENDER);
	}

	// See EntityMixin.updateDamageTilt().
}
'''

PLAIN['FPSSpooferHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"fps spoofer", "fake fps", "fps", "spoof fps"})
public final class FPSSpooferHack extends Hack
{
	private final SliderSetting multiplier = new SliderSetting("Multiplier",
		"Multiplies the displayed FPS by this factor.", 2, 1, 30, 1,
		ValueDisplay.INTEGER);

	public FPSSpooferHack()
	{
		super("FPSSpoofer");
		setCategory(Category.OTHER);
		addSetting(multiplier);
	}

	public int getMultiplier()
	{
		return multiplier.getValueI();
	}

	// See MinecraftClientMixin.onGetFps().
}
'''

PLAIN['HitDelayFixHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

@SearchTags({"hit delay fix", "hitdelayfix", "attack delay", "miss delay"})
public final class HitDelayFixHack extends Hack
{
	public HitDelayFixHack()
	{
		super("HitDelayFix");
		setCategory(Category.COMBAT);
	}

	// See MinecraftClientMixin.onTickHitDelayFix().
}
'''

PLAIN['ItemPhysicsHack'] = HEADER + r'''
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Spins dropped items around the Y and X axes on the client.
 *
 * <p>Soar rendered dropped items with a custom physics model. This port keeps
 * the same visual idea by rotating the item entities directly, which works
 * without version-specific renderer hooks.
 */
@SearchTags({"item physics", "itemphysics", "dropped items", "item rotation"})
public final class ItemPhysicsHack extends Hack implements UpdateListener
{
	private final SliderSetting speed = new SliderSetting("Speed",
		"Rotation speed of dropped items.", 1, 0.5, 4, 0.05,
		ValueDisplay.DECIMAL);

	public ItemPhysicsHack()
	{
		super("ItemPhysics");
		setCategory(Category.RENDER);
		addSetting(speed);
	}

	@Override
	protected void onEnable()
	{
		if(WURST.getHax().items2DHack.isEnabled())
			WURST.getHax().items2DHack.setEnabled(false);

		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null || MC.player == null)
			return;

		float amount = speed.getValueF();
		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof ItemEntity item)
				|| MC.player.distanceToSqr(item) > 4096)
				continue;

			item.setYRot(item.getYRot() + amount * 6F);
			item.setXRot(item.getXRot() + amount * 3F);
			item.yRotO = item.getYRot();
			item.xRotO = item.getXRot();
		}
	}
}
'''

PLAIN['Items2DHack'] = HEADER + r'''
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;

/**
 * Makes dropped items face the local player, approximating a flat billboard.
 *
 * <p>Soar rendered dropped items using a 2D item model. This port uses a
 * billboard yaw instead; it does not need render-state hooks and works on every
 * supported Minecraft version.
 */
@SearchTags({"items 2d", "items2d", "flat items", "billboard items"})
public final class Items2DHack extends Hack implements UpdateListener
{
	public Items2DHack()
	{
		super("Items2D");
		setCategory(Category.RENDER);
	}

	@Override
	protected void onEnable()
	{
		if(WURST.getHax().itemPhysicsHack.isEnabled())
			WURST.getHax().itemPhysicsHack.setEnabled(false);

		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null || MC.player == null)
			return;

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof ItemEntity item)
				|| MC.player.distanceToSqr(item) > 4096)
				continue;

			double dx = MC.player.getX() - item.getX();
			double dz = MC.player.getZ() - item.getZ();
			float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
			item.setYRot(yaw);
			item.setXRot(0);
			item.yRotO = yaw;
			item.xRotO = 0;
		}
	}
}
'''

PLAIN['MinimalDamageShakeHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"minimal damage shake", "damage shake", "hurt shake",
	"damage tilt", "no shake"})
public final class MinimalDamageShakeHack extends Hack
	implements UpdateListener
{
	private final SliderSetting intensity = new SliderSetting("Intensity",
		"How strong the damage camera shake should be.", 0, 0, 100, 1,
		ValueDisplay.INTEGER.withSuffix("%"));

	private double oldValue;
	private boolean saved;

	public MinimalDamageShakeHack()
	{
		super("MinimalDamageShake");
		setCategory(Category.RENDER);
		addSetting(intensity);
	}

	@Override
	protected void onEnable()
	{
		oldValue = MC.options.damageTiltStrength().get();
		saved = true;
		EVENTS.add(UpdateListener.class, this);
		apply();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		if(saved)
			MC.options.damageTiltStrength().set(oldValue);
		saved = false;
	}

	@Override
	public void onUpdate()
	{
		apply();
	}

	private void apply()
	{
		MC.options.damageTiltStrength().set(intensity.getValue() / 100.0);
	}
}
'''

PLAIN['MoBendsHack'] = HEADER + r'''
import net.minecraft.client.player.AbstractClientPlayer;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Amplifies the walk animation of player models.
 *
 * <p>Soar shipped a full player animation engine. This port scales the vanilla
 * walk animation speed, which affects arm and leg swing without replacing the
 * model or adding version-specific render-state hooks.
 */
@SearchTags({"mo bends", "mobends", "player animation", "limb swing"})
public final class MoBendsHack extends Hack implements UpdateListener
{
	private final SliderSetting multiplier = new SliderSetting("Multiplier",
		"Walk animation speed multiplier.", 1.6, 1, 3, 0.05,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting affectSelf = new CheckboxSetting(
		"Affect self", "Also animates your own player model.", true);

	private final CheckboxSetting onlySprinting = new CheckboxSetting(
		"Only sprinting", "Only amplifies sprinting players.", false);

	public MoBendsHack()
	{
		super("MoBends");
		setCategory(Category.RENDER);
		addSetting(multiplier);
		addSetting(affectSelf);
		addSetting(onlySprinting);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null)
			return;

		float factor = multiplier.getValueF();
		for(AbstractClientPlayer player : MC.level.players())
		{
			if(!affectSelf.isChecked() && player == MC.player)
				continue;
			if(onlySprinting.isChecked() && !player.isSprinting())
				continue;

			float speed = player.walkAnimation.speed();
			if(speed <= 0.01F)
				continue;

			player.walkAnimation.setSpeed(Math.min(2F, speed * factor));
		}
	}
}
'''

PLAIN['OldAnimationsHack'] = HEADER + r'''
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.LeftClickListener;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

/**
 * Applies a practical subset of Soar's 1.8-style animation toggles.
 *
 * <p>Implemented: block-hit particles, sprint hit particles, hiding the attack
 * indicator and disabling vanilla damage tilt. Rotating the first-person hand
 * model itself would need a render-state hook and is not attempted here.
 */
@SearchTags({"old animations", "1.8 animations", "oldanimations", "legacy animations"})
public final class OldAnimationsHack extends Hack
	implements UpdateListener, LeftClickListener, PlayerAttacksEntityListener
{
	private final CheckboxSetting blockHit = new CheckboxSetting("Block hit",
		"Spawns extra particles when hitting a block.", true);

	private final CheckboxSetting pushingParticles = new CheckboxSetting(
		"Pushing particles", "Spawns sweep particles when sprint-hitting.", true);

	private final CheckboxSetting hideAttackIndicator = new CheckboxSetting(
		"Hide attack indicator", "Hides the 1.9 attack cooldown indicator.", true);

	private final CheckboxSetting oldDamageTilt = new CheckboxSetting(
		"Old damage tilt", "Disables the vanilla damage camera tilt.", false);

	private AttackIndicatorStatus oldIndicator;
	private double oldTilt;
	private boolean indicatorApplied;
	private boolean tiltApplied;

	public OldAnimationsHack()
	{
		super("OldAnimations");
		setCategory(Category.RENDER);
		addSetting(blockHit);
		addSetting(pushingParticles);
		addSetting(hideAttackIndicator);
		addSetting(oldDamageTilt);
	}

	@Override
	protected void onEnable()
	{
		oldIndicator = MC.options.attackIndicator().get();
		oldTilt = MC.options.damageTiltStrength().get();
		indicatorApplied = false;
		tiltApplied = false;

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(LeftClickListener.class, this);
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		applyOptions();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(LeftClickListener.class, this);
		EVENTS.remove(PlayerAttacksEntityListener.class, this);

		if(indicatorApplied)
			MC.options.attackIndicator().set(oldIndicator);
		if(tiltApplied)
			MC.options.damageTiltStrength().set(oldTilt);

		indicatorApplied = false;
		tiltApplied = false;
	}

	@Override
	public void onUpdate()
	{
		applyOptions();
	}

	private void applyOptions()
	{
		if(hideAttackIndicator.isChecked())
		{
			if(!indicatorApplied)
			{
				MC.options.attackIndicator().set(AttackIndicatorStatus.OFF);
				indicatorApplied = true;
			}
		}else if(indicatorApplied)
		{
			MC.options.attackIndicator().set(oldIndicator);
			indicatorApplied = false;
		}

		if(oldDamageTilt.isChecked())
		{
			if(!tiltApplied)
			{
				MC.options.damageTiltStrength().set(0.0);
				tiltApplied = true;
			}
		}else if(tiltApplied)
		{
			MC.options.damageTiltStrength().set(oldTilt);
			tiltApplied = false;
		}
	}

	@Override
	public void onLeftClick(LeftClickEvent event)
	{
		if(!blockHit.isChecked() || MC.level == null
			|| !(MC.hitResult instanceof BlockHitResult hit))
			return;

		for(int i = 0; i < 4; i++)
			MC.level.addParticle(ParticleTypes.CRIT, hit.getLocation().x,
				hit.getLocation().y, hit.getLocation().z, 0, 0.05, 0);
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(!pushingParticles.isChecked() || MC.level == null
			|| MC.player == null || !MC.player.isSprinting()
			|| !(target instanceof LivingEntity))
			return;

		MC.level.addParticle(ParticleTypes.SWEEP_ATTACK, target.getX(),
			target.getY() + target.getBbHeight() * 0.5, target.getZ(), 0, 0, 0);
	}
}
'''

PLAIN['OverlayEditorHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"overlay editor", "overlay", "pumpkin overlay", "fire overlay"})
public final class OverlayEditorHack extends Hack
{
	private final CheckboxSetting hidePumpkin = new CheckboxSetting(
		"Hide pumpkin", "Hides the pumpkin blur overlay.", false);

	private final CheckboxSetting hideFire = new CheckboxSetting("Hide fire",
		"Hides the first-person fire overlay.", false);

	public OverlayEditorHack()
	{
		super("OverlayEditor");
		setCategory(Category.RENDER);
		addSetting(hidePumpkin);
		addSetting(hideFire);
	}

	public boolean isHidePumpkin()
	{
		return hidePumpkin.isChecked();
	}

	public boolean isHideFire()
	{
		return hideFire.isChecked();
	}

	// See GuiMixin/IngameHudMixin for pumpkin and
	// ScreenEffectRendererMixin/InGameOverlayRendererMixin for fire.
}
'''

PLAIN['ParticleCustomizerHack'] = HEADER + r'''
import java.util.Random;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"particle customizer", "particles", "crit particles",
	"sharpness particles"})
public final class ParticleCustomizerHack extends Hack
	implements PlayerAttacksEntityListener
{
	private final CheckboxSetting alwaysSharpness = new CheckboxSetting(
		"Always sharpness", "Always spawns sharpness particles.", false);

	private final CheckboxSetting alwaysCriticals = new CheckboxSetting(
		"Always criticals", "Always spawns critical particles.", false);

	private final CheckboxSetting sharpness = new CheckboxSetting("Sharpness",
		"Spawns sharpness particles for enchanted weapons.", true);

	private final CheckboxSetting criticals = new CheckboxSetting("Criticals",
		"Spawns critical particles when falling.", false);

	private final SliderSetting sharpnessAmount = new SliderSetting(
		"Sharpness amount", "Number of sharpness particles.", 2, 1, 10, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting criticalsAmount = new SliderSetting(
		"Criticals amount", "Number of critical particles.", 2, 1, 10, 1,
		ValueDisplay.INTEGER);

	private final Random random = new Random();

	public ParticleCustomizerHack()
	{
		super("ParticleCustomizer");
		setCategory(Category.RENDER);
		addSetting(alwaysSharpness);
		addSetting(alwaysCriticals);
		addSetting(sharpness);
		addSetting(criticals);
		addSetting(sharpnessAmount);
		addSetting(criticalsAmount);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PlayerAttacksEntityListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(MC.level == null || MC.player == null
			|| !(target instanceof LivingEntity))
			return;

		boolean critical = MC.player.fallDistance > 0
			&& !MC.player.onGround() && !MC.player.onClimbable()
			&& !MC.player.isInWater() && !MC.player.isPassenger()
			&& !MC.player.hasEffect(MobEffects.BLINDNESS);

		if(alwaysCriticals.isChecked() || criticals.isChecked() && critical)
			spawn(ParticleTypes.CRIT, target, criticalsAmount.getValueI());

		boolean sharp = MC.player.getMainHandItem().isEnchanted();
		if(alwaysSharpness.isChecked() || sharpness.isChecked() && sharp)
			spawn(ParticleTypes.ENCHANTED_HIT, target,
				sharpnessAmount.getValueI());
	}

	private void spawn(net.minecraft.core.particles.ParticleOptions type,
		Entity target, int count)
	{
		for(int i = 0; i < count; i++)
		{
			double vx = (random.nextDouble() - 0.5) * 0.2;
			double vy = random.nextDouble() * 0.2;
			double vz = (random.nextDouble() - 0.5) * 0.2;
			MC.level.addParticle(type, target.getX(),
				target.getY() + target.getBbHeight() * 0.5, target.getZ(), vx,
				vy, vz);
		}
	}
}
'''

PLAIN['RawInputHack'] = HEADER + r'''
import java.lang.reflect.Method;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

/**
 * Enables Minecraft's native raw mouse input option.
 *
 * <p>Soar used a JInput thread. Minecraft 1.20.2+ already exposes the platform
 * raw input path, so this port toggles and restores that option. The option was
 * moved/renamed in some 26.x builds, so the lookup is reflective.
 */
@SearchTags({"raw input", "rawinput", "raw mouse", "mouse input"})
public final class RawInputHack extends Hack
{
	private Object option;
	private Object oldValue;

	public RawInputHack()
	{
		super("RawInput");
		setCategory(Category.OTHER);
	}

	@Override
	protected void onEnable()
	{
		option = null;
		oldValue = null;
		try
		{
			Method getter = MC.options.getClass().getMethod("rawMouseInput");
			option = getter.invoke(MC.options);
			oldValue = option.getClass().getMethod("get").invoke(option);
			option.getClass().getMethod("set", Object.class).invoke(option,
				true);
		}catch(Exception ignored)
		{
			option = null;
			oldValue = null;
		}
	}

	@Override
	protected void onDisable()
	{
		if(option == null)
			return;

		try
		{
			option.getClass().getMethod("set", Object.class).invoke(option,
				oldValue);
		}catch(Exception ignored)
		{}

		option = null;
		oldValue = null;
	}
}
'''

PLAIN['SkinProtectHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

@SearchTags({"skin protect", "skinprotect", "hide skin", "default skin"})
public final class SkinProtectHack extends Hack
{
	public SkinProtectHack()
	{
		super("SkinProtect");
		setCategory(Category.RENDER);
	}

	// See PlayerSkinProviderMixin.
}
'''

PLAIN['SlowSwingHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"slow swing", "slowswing", "swing speed", "hand swing"})
public final class SlowSwingHack extends Hack
{
	private final SliderSetting delay = new SliderSetting("Delay",
		"Hand swing duration in ticks.", 14, 2, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	public SlowSwingHack()
	{
		super("SlowSwing");
		setCategory(Category.RENDER);
		addSetting(delay);
	}

	public int getDelay()
	{
		return delay.getValueI();
	}

	// See LivingEntityMixin.
}
'''

PLAIN['SoundModifierHack'] = HEADER + r'''
import java.util.EnumMap;

import net.minecraft.sounds.SoundSource;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Overrides vanilla sound category volumes.
 *
 * <p>Soar matched individual sound names. This port exposes the vanilla sound
 * categories instead, which is version-independent and still lets players mute
 * or boost specific groups.
 */
@SearchTags({"sound modifier", "soundmodifier", "volume", "sound volume",
	"mute sounds"})
public final class SoundModifierHack extends Hack implements UpdateListener
{
	private static final SoundSource[] SOURCES = {SoundSource.MASTER,
		SoundSource.MUSIC, SoundSource.RECORDS, SoundSource.WEATHER,
		SoundSource.BLOCKS, SoundSource.HOSTILE, SoundSource.NEUTRAL,
		SoundSource.PLAYERS, SoundSource.AMBIENT, SoundSource.VOICE};

	private final SliderSetting master = volume("Master", 100);
	private final SliderSetting music = volume("Music", 100);
	private final SliderSetting records = volume("Records", 100);
	private final SliderSetting weather = volume("Weather", 100);
	private final SliderSetting blocks = volume("Blocks", 100);
	private final SliderSetting hostile = volume("Hostile mobs", 100);
	private final SliderSetting neutral = volume("Neutral mobs", 100);
	private final SliderSetting players = volume("Players", 100);
	private final SliderSetting ambient = volume("Ambient", 100);
	private final SliderSetting voice = volume("Voice", 100);

	private final EnumMap<SoundSource, Double> oldVolumes =
		new EnumMap<>(SoundSource.class);

	public SoundModifierHack()
	{
		super("SoundModifier");
		setCategory(Category.OTHER);
		addSetting(master);
		addSetting(music);
		addSetting(records);
		addSetting(weather);
		addSetting(blocks);
		addSetting(hostile);
		addSetting(neutral);
		addSetting(players);
		addSetting(ambient);
		addSetting(voice);
	}

	private SliderSetting volume(String name, int value)
	{
		return new SliderSetting(name, "Volume of this sound category.",
			value, 0, 100, 1, ValueDisplay.INTEGER.withSuffix("%"));
	}

	@Override
	protected void onEnable()
	{
		for(SoundSource source : SOURCES)
			oldVolumes.put(source,
				MC.options.getSoundSourceOptionInstance(source).get());

		EVENTS.add(UpdateListener.class, this);
		apply();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);

		for(SoundSource source : SOURCES)
		{
			Double value = oldVolumes.get(source);
			if(value != null)
				MC.options.getSoundSourceOptionInstance(source).set(value);
		}
		oldVolumes.clear();
	}

	@Override
	public void onUpdate()
	{
		apply();
	}

	private void apply()
	{
		set(SoundSource.MASTER, master);
		set(SoundSource.MUSIC, music);
		set(SoundSource.RECORDS, records);
		set(SoundSource.WEATHER, weather);
		set(SoundSource.BLOCKS, blocks);
		set(SoundSource.HOSTILE, hostile);
		set(SoundSource.NEUTRAL, neutral);
		set(SoundSource.PLAYERS, players);
		set(SoundSource.AMBIENT, ambient);
		set(SoundSource.VOICE, voice);
	}

	private void set(SoundSource source, SliderSetting setting)
	{
		MC.options.getSoundSourceOptionInstance(source)
			.set(setting.getValue() / 100.0);
	}
}
'''

PLAIN['AutoGGHack'] = HEADER + r'''
import java.util.List;

import net.minecraft.client.multiplayer.ServerData;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.ChatInputListener.ChatInputEvent;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

@SearchTags({"auto gg", "autogg", "gg", "hypixel", "end game"})
public final class AutoGGHack extends Hack
	implements ChatInputListener, UpdateListener
{
	private static final List<String> WIN_MARKERS = List.of(
		"1st Killer -", "1st Place -", "Winner:", " - Damage Dealt -",
		"Winning Team -", "1st -", "Winners:", "Winning Team:",
		" won the game!", "Top Seeker:", "1st Place:",
		"Last team standing!", "Winner #1 (", "Top Survivors",
		"Winners -", "Sumo Duel -", "Most Wool Placed -",
		"Your Overall Winstreak:");

	private final SliderSetting delay = new SliderSetting("Delay",
		"Seconds to wait before sending the message.", 0, 0, 5, 1,
		ValueDisplay.INTEGER.withSuffix("s"));

	private final TextFieldSetting message = new TextFieldSetting("Message",
		"Message sent after a win.", "gg");

	private final CheckboxSetting hypixelOnly = new CheckboxSetting(
		"Hypixel only", "Only sends on Hypixel.", true);

	private int pendingTicks;
	private long lastSent;

	public AutoGGHack()
	{
		super("AutoGG");
		setCategory(Category.CHAT);
		addSetting(delay);
		addSetting(message);
		addSetting(hypixelOnly);
	}

	@Override
	protected void onEnable()
	{
		pendingTicks = 0;
		lastSent = 0;
		EVENTS.add(ChatInputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatInputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		pendingTicks = 0;
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		if(pendingTicks > 0 || event.getComponent() == null)
			return;

		ServerData server = MC.getCurrentServer();
		if(hypixelOnly.isChecked() && (server == null || server.ip == null
			|| !server.ip.toLowerCase().contains("hypixel")))
			return;

		String text = event.getComponent().getString();
		for(String marker : WIN_MARKERS)
			if(text.contains(marker))
			{
				pendingTicks = Math.max(1,
					delay.getValueI() * 20);
				break;
			}
	}

	@Override
	public void onUpdate()
	{
		if(pendingTicks <= 0)
			return;

		if(--pendingTicks > 0)
			return;

		if(System.currentTimeMillis() - lastSent < 3000)
			return;

		String text = message.getValue();
		if(text == null || text.isBlank() || MC.getConnection() == null)
			return;

		if(text.startsWith("/"))
			MC.getConnection().sendCommand(text.substring(1));
		else
			MC.getConnection().sendChat(text);

		lastSent = System.currentTimeMillis();
	}
}
'''

PLAIN['ForceMainHandHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"force main hand", "main hand", "left hand", "right hand"})
public final class ForceMainHandHack extends Hack
{
	public enum Hand
	{
		RIGHT,
		LEFT
	}

	private final EnumSetting<Hand> hand = new EnumSetting<>("Hand",
		"Main hand used when rendering other players.", Hand.values(),
		Hand.RIGHT);

	public ForceMainHandHack()
	{
		super("ForceMainHand");
		setCategory(Category.RENDER);
		addSetting(hand);
	}

	public boolean isRightHand()
	{
		return hand.getSelected() == Hand.RIGHT;
	}

	// See PlayerMixin.
}
'''

PLAIN['SnapTapHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

/**
 * Snap Tap / SOCD resolution for A-D and W-S.
 *
 * <p>The most recently pressed key wins while both directions are held. See
 * KeyBindingMixin / KeyMappingMixin.
 */
@SearchTags({"snap tap", "snaptap", "socd", "strafe", "keyboard"})
public final class SnapTapHack extends Hack
{
	private long leftPressTime;
	private long rightPressTime;
	private long forwardPressTime;
	private long backPressTime;

	public SnapTapHack()
	{
		super("SnapTap");
		setCategory(Category.MOVEMENT);
	}

	@Override
	protected void onDisable()
	{
		leftPressTime = 0;
		rightPressTime = 0;
		forwardPressTime = 0;
		backPressTime = 0;
	}

	public long getLeftPressTime()
	{
		return leftPressTime;
	}

	public void setLeftPressTime(long time)
	{
		leftPressTime = time;
	}

	public long getRightPressTime()
	{
		return rightPressTime;
	}

	public void setRightPressTime(long time)
	{
		rightPressTime = time;
	}

	public long getForwardPressTime()
	{
		return forwardPressTime;
	}

	public void setForwardPressTime(long time)
	{
		forwardPressTime = time;
	}

	public long getBackPressTime()
	{
		return backPressTime;
	}

	public void setBackPressTime(long time)
	{
		backPressTime = time;
	}
}
'''

PLAIN['WaveyCapesHack'] = HEADER + r'''
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Draws a wave-like particle cape behind moving players.
 *
 * <p>Soar replaced the cape model with a physics-driven mesh. This port emits
 * a cape-shaped particle trail, which needs no render-state hooks and works on
 * every supported version.
 */
@SearchTags({"wavey capes", "wavey capes", "cape", "cape trail"})
public final class WaveyCapesHack extends Hack implements UpdateListener
{
	private final SliderSetting height = new SliderSetting("Height",
		"Height of the particle cape.", 6, 2, 10, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting gravity = new SliderSetting("Gravity",
		"Downward drift of the cape particles.", 15, 2, 30, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting density = new SliderSetting("Density",
		"Particles spawned per player per tick.", 1, 1, 4, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting onlySprinting = new CheckboxSetting(
		"Only sprinting", "Only shows the cape while sprinting.", false);

	public WaveyCapesHack()
	{
		super("WaveyCapes");
		setCategory(Category.RENDER);
		addSetting(height);
		addSetting(gravity);
		addSetting(density);
		addSetting(onlySprinting);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null)
			return;

		for(AbstractClientPlayer player : MC.level.players())
		{
			if(onlySprinting.isChecked() && !player.isSprinting())
				continue;

			Vec3 motion = player.getDeltaMovement();
			if(motion.x * motion.x + motion.z * motion.z < 0.002)
				continue;

			Vec3 look = player.getLookAngle();
			Vec3 back = new Vec3(-look.x, 0, -look.z);
			if(back.lengthSqr() < 1.0E-4)
				continue;
			back = back.normalize();

			double x = player.getX() + back.x * 0.3;
			double y = player.getY() + 1.1 + height.getValue() * 0.05;
			double z = player.getZ() + back.z * 0.3;
			double vy = -gravity.getValue() * 0.001;

			for(int i = 0; i < density.getValueI(); i++)
				MC.level.addParticle(ParticleTypes.CLOUD, x, y, z,
					back.x * 0.01, vy, back.z * 0.01);
		}
	}
}
'''
