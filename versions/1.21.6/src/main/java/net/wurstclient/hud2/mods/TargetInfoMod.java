/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package net.wurstclient.hud2.mods;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Vector3f;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.compat.HudTextures;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.target.TargetHudMotion;
import net.wurstclient.hud2.target.TargetHudDrawing;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.EntityUtils;
import net.minecraft.world.phys.Vec3;

/** Independent Modern-style target card; uses the existing NanoVG HUD renderer. */
public final class TargetInfoMod extends HUDMod
	implements PlayerAttacksEntityListener, UpdateListener
{
	private static final Color ACCENT = new Color(accentColor(255), true);
	private static final Color ACCENT_DARK = new Color(0x818DC8);
	private static final Color GLASS = new Color(0, 0, 0, 110);
	private static final Color TRACK_TOP = new Color(0, 0, 0, 64);
	private static final Color HEAD_OUTLINE = new Color(0, 0, 0, 40);
	private static final String SKELETON = "minecraft:textures/entity/skeleton/skeleton.png";
	private final TargetHudDrawing drawing = new TargetHudDrawing();
	private final CheckboxSetting follow = new CheckboxSetting("Follow Player",
		"Anchors the card next to the attacked target instead of the HUD editor position.", false);
	private final CheckboxSetting particles = new CheckboxSetting("Particles",
		"Shows a small burst when the target takes damage.", true);
	private TargetHudMotion motion = new TargetHudMotion();
	private LivingEntity target;
	private HudCompat.TargetInfo info;
	private long lastAttack;
	private String healthText = "0.0";
	private int previousHurt;
	private boolean wasPreview;
	private final ArrayList<HitParticle> sparks = new ArrayList<>();

	public TargetInfoMod()
	{
		super("TargetInfo", "Target Info",
			"Modern target card with animated health and damage feedback.");
		setTranslationKey("target_info");
		setDefaultPosition(180, 54);
		addSetting(follow);
		addSetting(particles);
	}

	@Override protected void onEnable()
	{
		reset();
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		reset();
		drawing.close();
	}

	private void reset()
	{
		target = null;
		info = null;
		motion = new TargetHudMotion();
		sparks.clear();
		previousHurt = 0;
		wasPreview = false;
		setSize(0, 0);
	}

	private static long now() {return System.nanoTime() / 1_000_000;}

	@Override public void onPlayerAttacksEntity(Entity entity)
	{
		if(!(entity instanceof LivingEntity living) || living == MC.player
			|| !living.isAlive() || living.isSpectator()) return;
		boolean changed = target != living;
		target = living;
		lastAttack = now();
		if(changed)
		{
			sparks.clear();
			previousHurt = 0;
		}
		sample(lastAttack, changed);
		motion.setVisible(true, lastAttack);
	}

	@Override public void onUpdate()
	{
		if(MC.player == null || MC.level == null
			|| target != null && target.level() != MC.level)
		{
			reset();
			return;
		}
		long time = now();
		if(target != null) sample(time, false);
		sparks.removeIf(p -> time - p.born() >= 650);
		if(!particles.isChecked()) sparks.clear();
		if(target != null && target.hurtTime > previousHurt
			&& particles.isChecked() && motion.scale(time) > 0)
		{
			ThreadLocalRandom random = ThreadLocalRandom.current();
			for(int i = 0; i < 6 && sparks.size() < 24; i++)
				sparks.add(new HitParticle(time, random.nextFloat() * 2 - 1,
					random.nextFloat() * 2 - 1, 2 + random.nextFloat() * 3));
		}
		previousHurt = target == null ? 0 : target.hurtTime;
	}

	private void sample(long time, boolean changed)
	{
		info = HudCompat.infoFor(target);
		float health = target.isAlive() ? target.getHealth() : 0;
		motion.setHealth(health, target.getMaxHealth(), changed, time);
		healthText = String.format(Locale.ROOT, "%.1f",
			Float.isFinite(health) ? Math.max(0, Math.min(health, target.getMaxHealth())) : 0);
	}

	@Override protected void draw()
	{
		if(MC.player == null || MC.level == null)
		{
			reset();
			return;
		}
		long time = now();
		boolean preview = isEditing() || MC.screen instanceof ChatScreen;
		if(preview)
		{
			boolean changed = target != MC.player;
			if(changed) sparks.clear();
			wasPreview = true;
			target = MC.player;
			lastAttack = time;
			sample(time, changed);
		}else if(wasPreview) wasPreview = false;
		boolean valid = target != null && target.isAlive() && !target.isRemoved()
			&& target.level() == MC.level;
		motion.setVisible(preview || TargetHudMotion.shouldShow(time, lastAttack, valid), time);
		float opening = isEditing() ? 1 : motion.scale(time);
		if(info == null || opening <= .001F)
		{
			setSize(0, 0);
			if(!valid || time - lastAttack >= 1400) {target = null; info = null;}
			return;
		}
		float nameWidth = getTextWidth(info.name(), 11, HudFonts.MEDIUM);
		float hpWidth = getTextWidth(healthText, 11, HudFonts.MEDIUM);
		float barWidth = Math.max(nameWidth + 32 - hpWidth, 65);
		float width = 48 + barWidth + 4 + hpWidth + 8;
		int oldX = getX(), oldY = getY();
		if(follow.isChecked() && !preview && target != MC.player)
		{
			float[] point = project(target);
			if(point == null) {setSize(0, 0); return;}
			setX(Math.max(0, Math.min(Math.round(point[0]) + 14,
				MC.getWindow().getGuiScaledWidth() - Math.round(width * getScale()))));
			setY(Math.max(0, Math.min(Math.round(point[1] - 24 * getScale()),
				MC.getWindow().getGuiScaledHeight() - Math.round(48 * getScale()))));
		}
		save();
		try
		{
			scale(0, 0, width, 48, opening);
			float unit = getScale();
			drawBackground(width - 1, 48);
			Color text = ACCENT;
			drawText(info.name(), 47, 14.5F, 11, HudFonts.MEDIUM, text);
			gradient(47, 29, barWidth, 6, 3, TRACK_TOP, GLASS);
			float fill = barWidth * motion.health(time);
			if(fill > .01F)
				gradient(47, 29, fill, 6, Math.min(3, fill / 2),
					ACCENT_DARK, text);
			drawText(healthText, 51 + barWidth, 28, 11, HudFonts.MEDIUM, text);
			float hurt = preview ? 0 : Math.max(0, target.hurtTime - partialTicks) * .5F;
			// Transform a constant-size head: avoids creating texture-cache entries per hurt size.
			save();
			try
			{
				scale(8, 8, 32, 32, 1 - hurt / 32);
				TargetHudDrawing.shadow(nvg, getX() + 8 * unit, getY() + 8 * unit,
					32 * unit, 32 * unit, 10 * unit, 5 * unit, HEAD_OUTLINE);
				drawPortrait(hurt);
				drawRoundedOutline(7.5F, 7.5F, 33, 33, 10, .5F, HEAD_OUTLINE);
			}finally {restore();}
			if(particles.isChecked() && !preview)
				for(HitParticle p : sparks)
				{
					float age = Math.max(0, (time - p.born()) / 650F);
					if(age >= 1) continue;
					float travel = (float)(1 - Math.exp(-age * 2)) * 45;
					float px = 20 + p.dx() * travel, py = 20 + p.dy() * travel;
					int alpha = Math.round(180 * (1 - age));
					drawCircle(px, py, p.size(), new Color(accentColor(alpha / 5), true));
					drawCircle(px, py, p.size() / 2, new Color(accentColor(alpha), true));
				}
			setSize(Math.round(width), 48);
			markDrawn();
		}finally
		{
			restore();
			setX(oldX);
			setY(oldY);
		}
	}

	private void gradient(float x, float y, float width, float height,
		float radius, Color top, Color bottom)
	{
		float unit = getScale();
		TargetHudDrawing.gradient(nvg, getX() + x * unit, getY() + y * unit,
			width * unit, height * unit, radius * unit, top, bottom);
	}

	private void drawPortrait(float hurt)
	{
		String path = info.skin();
		int textureHeight = 64;
		float faceHeight = 8;
		if(!target.isAlive() || target.isRemoved())
		{
			path = SKELETON;
			textureHeight = 32;
		}else if(path == null)
		{
			if(target instanceof net.minecraft.world.entity.monster.Zombie)
				path = "minecraft:textures/entity/zombie/zombie.png";
			else if(target instanceof net.minecraft.world.entity.monster.AbstractSkeleton)
			{
				path = SKELETON;
				textureHeight = 32;
			}else if(target instanceof net.minecraft.world.entity.monster.Creeper)
			{
				path = "minecraft:textures/entity/creeper/creeper.png";
				textureHeight = 32;
			}else if(target instanceof net.minecraft.world.entity.npc.AbstractVillager)
			{
				path = "minecraft:textures/entity/villager/villager.png";
				faceHeight = 10;
			}
		}
		float unit = getScale();
		if(path != null)
			drawing.face(nvg, HudTextures.id(path), 64, textureHeight, 8, 8,
				8, faceHeight, getX() + 8 * unit, getY() + 8 * unit, 32 * unit, 10 * unit, hurt);
		else
		{
			drawRoundedRect(8, 8, 32, 32, 10, ACCENT_DARK);
			drawCenteredText(HudIcon.HEART_FILL, 24, 16, 16, HudFonts.ICON, Color.WHITE);
		}
	}

	private record HitParticle(long born, float dx, float dy, float size) {}


	private float[] project(Entity entity)
	{
		Vec3 point = EntityUtils.getLerpedPos(entity, partialTicks)
			.add(0, entity.getBbHeight() * 0.5D, 0);
		Camera camera = MC.gameRenderer.getMainCamera();
		Vec3 relative = point.subtract(camera.getPosition());

		Vector3f look = camera.getLookVector();
		double forward = relative.x * look.x() + relative.y * look.y()
			+ relative.z * look.z();
		if(forward <= 0.05D)
			return null;

		Vector3f upVector = camera.getUpVector();
		double up = relative.x * upVector.x() + relative.y * upVector.y()
			+ relative.z * upVector.z();
		Vector3f leftVector = camera.getLeftVector();
		double right = -(relative.x * leftVector.x()
			+ relative.y * leftVector.y() + relative.z * leftVector.z());
		double tan = Math.tan(
			Math.toRadians(MC.options.fov().get()) / 2.0D);
		double aspect = (double)MC.getWindow().getWidth()
			/ MC.getWindow().getHeight();

		double ndcX = right / (forward * tan * aspect);
		double ndcY = up / (forward * tan);
		if(ndcX < -1.2D || ndcX > 1.2D || ndcY < -1.2D || ndcY > 1.2D)
			return null;

		float x = (float)((ndcX + 1.0D) / 2.0D
			* MC.getWindow().getGuiScaledWidth());
		float y = (float)((1.0D - ndcY) / 2.0D
			* MC.getWindow().getGuiScaledHeight());
		return new float[]{x, y};
	}

}
