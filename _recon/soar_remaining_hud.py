# -*- coding: utf-8 -*-
"""HUD hack sources for the remaining Soar modules.

The templates use ``__GUI__``, ``__GUI_IMPORT__`` and ``__HELPERS__`` so the
same body can be rendered against GuiGraphics (1.20.2-1.21.5),
Wurst's GuiGraphicsExtractor shim (1.21.6-1.21.11) and the native
GuiGraphicsExtractor (26.x).
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

HUD = {}

HUD['ColorSaturationHack'] = HEADER + r'''
import java.awt.Color;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
__GUI_IMPORT__

/**
 * Applies a screen-space colour grade (hue tint, brightness and contrast).
 *
 * <p>Soar used a post-processing shader. This port uses a lightweight
 * full-screen overlay instead, so it works across every loader and Minecraft
 * version without a version-specific shader pipeline.
 */
@SearchTags({"color saturation", "saturation", "color filter", "colour grade"})
public final class ColorSaturationHack extends Hack implements GUIRenderListener
{
	private final SliderSetting hue = new SliderSetting("Hue",
		"Hue of the colour filter.", 0, 0, 1, 0.01, ValueDisplay.DECIMAL);

	private final SliderSetting saturation = new SliderSetting("Saturation",
		"Strength of the colour filter.", 0.5, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting brightness = new SliderSetting("Brightness",
		"Screen brightness adjustment.", 0.5, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting contrast = new SliderSetting("Contrast",
		"Edge darkening/contrast adjustment.", 0.5, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	public ColorSaturationHack()
	{
		super("ColorSaturation");
		setCategory(Category.RENDER);
		addSetting(hue);
		addSetting(saturation);
		addSetting(brightness);
		addSetting(contrast);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(MC.player == null || uiHidden())
			return;

		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();

		Color hueColor = Color.getHSBColor((float)hue.getValue(), 1F, 1F);
		int tintAlpha = (int)((1F - saturation.getValue()) * 46F);
		if(tintAlpha > 0)
			context.fill(0, 0, width, height, argb(hueColor, tintAlpha));

		double b = brightness.getValue();
		if(b > 0.5)
			context.fill(0, 0, width, height,
				argb(Color.WHITE, (int)((b - 0.5) * 120)));
		else if(b < 0.5)
			context.fill(0, 0, width, height,
				argb(Color.BLACK, (int)((0.5 - b) * 120)));

		int edge = (int)((1F - contrast.getValue()) * 70F);
		if(edge > 0)
		{
			context.fill(0, 0, width, 6, argb(Color.BLACK, edge));
			context.fill(0, height - 6, width, height, argb(Color.BLACK, edge));
			context.fill(0, 0, 6, height, argb(Color.BLACK, edge));
			context.fill(width - 6, 0, width, height, argb(Color.BLACK, edge));
		}
	}
__HELPERS__
}
'''

HUD['DamageTintHack'] = HEADER + r'''
import java.awt.Color;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
__GUI_IMPORT__

/**
 * Red vignette shown while the player's health is low.
 *
 * <p>Soar drew a smooth texture overlay. This port draws a lightweight tinted
 * overlay, so it works across all supported versions.
 */
@SearchTags({"damage tint", "low health", "red vignette", "health tint"})
public final class DamageTintHack extends Hack
	implements UpdateListener, GUIRenderListener
{
	private final SliderSetting threshold = new SliderSetting("Health",
		"Health threshold at which the tint appears.", 10, 1, 20, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting alpha = new SliderSetting("Alpha",
		"Maximum opacity of the tint.", 0.35, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the low-health tint.", new Color(255, 0, 0));

	private float fade;

	public DamageTintHack()
	{
		super("DamageTint");
		setCategory(Category.RENDER);
		addSetting(threshold);
		addSetting(alpha);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		fade = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		fade = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		boolean low = MC.player.getHealth() <= threshold.getValue()
			&& !MC.player.isCreative() && !MC.player.isSpectator();
		float target = low ? 1F : 0F;
		fade += (target - fade) * 0.15F;
		if(fade < 0.01F && !low)
			fade = 0;
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(fade <= 0.01F || MC.player == null || uiHidden() || MC.player.isCreative()
			|| MC.player.isSpectator())
			return;

		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();
		int a = (int)(fade * alpha.getValue() * 255F);
		context.fill(0, 0, width, height, argb(color.getColor(), a));

		int edge = Math.min(255, a + 40);
		context.fill(0, 0, width, 8, argb(color.getColor(), edge));
		context.fill(0, height - 8, width, height,
			argb(color.getColor(), edge));
		context.fill(0, 0, 8, height, argb(color.getColor(), edge));
		context.fill(width - 8, 0, width, height,
			argb(color.getColor(), edge));
	}
__HELPERS__
}
'''

HUD['GlintColorHack'] = HEADER + r'''
import java.awt.Color;

import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
__GUI_IMPORT__

/**
 * Controls vanilla glint speed/strength and shows the configured glint colour
 * as an overlay on enchanted hotbar slots.
 *
 * <p>Recolouring the world-space glint texture needs a custom shader. This
 * port keeps the vanilla glint pipeline and exposes the colour on the HUD,
 * which avoids per-version render-type changes.
 */
@SearchTags({"glint color", "glintcolour", "enchantment glint", "foil color"})
public final class GlintColorHack extends Hack
	implements UpdateListener, GUIRenderListener
{
	public enum Mode
	{
		SYNC,
		RAINBOW,
		CUSTOM
	}

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Colour source for the HUD glint overlay.", Mode.values(), Mode.SYNC);

	private final ColorSetting color = new ColorSetting("Color",
		"Custom glint colour.", new Color(255, 0, 0));

	private final SliderSetting speed = new SliderSetting("Speed",
		"Vanilla glint animation speed.", 0.5, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting strength = new SliderSetting("Strength",
		"Vanilla glint strength.", 0.5, 0, 1, 0.01, ValueDisplay.DECIMAL);

	private final CheckboxSetting hotbar = new CheckboxSetting("Hotbar overlay",
		"Draws the configured colour over enchanted hotbar items.", true);

	private double oldSpeed;
	private double oldStrength;

	public GlintColorHack()
	{
		super("GlintColor");
		setCategory(Category.RENDER);
		addSetting(mode);
		addSetting(color);
		addSetting(speed);
		addSetting(strength);
		addSetting(hotbar);
	}

	@Override
	protected void onEnable()
	{
		oldSpeed = MC.options.glintSpeed().get();
		oldStrength = MC.options.glintStrength().get();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
		apply();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		MC.options.glintSpeed().set(oldSpeed);
		MC.options.glintStrength().set(oldStrength);
	}

	@Override
	public void onUpdate()
	{
		apply();
	}

	private void apply()
	{
		MC.options.glintSpeed().set(speed.getValue());
		MC.options.glintStrength().set(strength.getValue());
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(!hotbar.isChecked() || MC.player == null || uiHidden())
			return;

		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();
		int left = width / 2 - 91;
		int top = height - 22;
		int rgb = getGlintColor().getRGB() & 0xFFFFFF;

		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty() || !stack.isEnchanted())
				continue;

			int x = left + 3 + i * 20;
			int y = top + 3;
			context.fill(x, y, x + 16, y + 16, 0x50000000 | rgb);
		}
	}

	private Color getGlintColor()
	{
		switch(mode.getSelected())
		{
			case RAINBOW:
			float hue = (System.currentTimeMillis() % 3000L) / 3000F;
			return Color.getHSBColor(hue, 1F, 1F);

			case CUSTOM:
			return color.getColor();

			case SYNC:
			default:
			float[] ac = WURST.getGui().getAcColor();
			return new Color(clamp(ac[0]), clamp(ac[1]), clamp(ac[2]));
		}
	}

	private float clamp(float value)
	{
		return Math.max(0, Math.min(1, value));
	}
__HELPERS__
}
'''

HUD['HitColorHack'] = HEADER + r'''
import java.awt.Color;

import net.minecraft.world.entity.Entity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
__GUI_IMPORT__

/**
 * Screen flash shown when the player hits an entity.
 *
 * <p>Soar recoloured the red entity hurt overlay. This port draws a configurable
 * hit flash on the HUD instead, which is independent of entity render states.
 */
@SearchTags({"hit color", "hitcolour", "hit flash", "damage flash"})
public final class HitColorHack extends Hack
	implements PlayerAttacksEntityListener, GUIRenderListener
{
	private final ColorSetting color = new ColorSetting("Color",
		"Color of the hit flash.", new Color(255, 40, 40));

	private final SliderSetting alpha = new SliderSetting("Alpha",
		"Maximum opacity of the hit flash.", 0.25, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting duration = new SliderSetting("Duration",
		"How long the hit flash lasts.", 0.25, 0.05, 2, 0.05,
		ValueDisplay.DECIMAL.withSuffix("s"));

	private long lastHit;

	public HitColorHack()
	{
		super("HitColor");
		setCategory(Category.RENDER);
		addSetting(color);
		addSetting(alpha);
		addSetting(duration);
	}

	@Override
	protected void onEnable()
	{
		lastHit = 0;
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		lastHit = System.currentTimeMillis();
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(lastHit == 0 || uiHidden())
			return;

		long elapsed = System.currentTimeMillis() - lastHit;
		long total = (long)(duration.getValue() * 1000.0);
		if(elapsed < 0 || elapsed > total)
			return;

		float fade = 1F - elapsed / (float)total;
		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();
		int a = (int)(fade * alpha.getValue() * 255F);
		context.fill(0, 0, width, height, argb(color.getColor(), a));
	}
__HELPERS__
}
'''

HUD['MotionBlurHack'] = HEADER + r'''
import java.awt.Color;

import net.minecraft.util.Mth;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
__GUI_IMPORT__

/**
 * Draws an edge smear whose intensity follows camera movement.
 *
 * <p>Soar used a framebuffer accumulation shader. This port approximates the
 * visual feedback with a movement-reactive HUD overlay, avoiding per-version
 * post-processing code.
 */
@SearchTags({"motion blur", "motionblur", "camera blur", "afterimage"})
public final class MotionBlurHack extends Hack
	implements UpdateListener, GUIRenderListener
{
	private final SliderSetting amount = new SliderSetting("Amount",
		"Strength of the movement smear.", 0.5, 0.1, 0.9, 0.05,
		ValueDisplay.DECIMAL);

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the motion smear.", new Color(255, 255, 255));

	private float lastYaw;
	private float lastPitch;
	private float intensity;
	private boolean initialized;

	public MotionBlurHack()
	{
		super("MotionBlur");
		setCategory(Category.RENDER);
		addSetting(amount);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		intensity = 0;
		initialized = false;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		intensity = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		float yaw = MC.player.getYRot();
		float pitch = MC.player.getXRot();
		if(!initialized)
		{
			lastYaw = yaw;
			lastPitch = pitch;
			initialized = true;
			return;
		}

		float dy = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
		float dp = Math.abs(pitch - lastPitch);
		lastYaw = yaw;
		lastPitch = pitch;

		float movement = Math.min(1F, (dy + dp) / 20F);
		float target = movement * (float)amount.getValue();
		intensity += (target - intensity) * 0.2F;
		if(intensity < 0.005F)
			intensity = 0;
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(intensity <= 0.005F || uiHidden())
			return;

		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();
		int maxAlpha = (int)(intensity * 90F);
		int base = color.getColor().getRGB() & 0xFFFFFF;

		for(int i = 0; i < 6; i++)
		{
			int a = maxAlpha * (6 - i) / 12;
			int strip = 4 + i * 6;
			context.fill(0, 0, strip, height, a << 24 | base);
			context.fill(width - strip, 0, width, height, a << 24 | base);
		}
	}
__HELPERS__
}
'''

HUD['RearviewHack'] = HEADER + r'''
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
__GUI_IMPORT__

/**
 * HUD radar showing entities behind the player.
 *
 * <p>Soar rendered a second camera into a texture. This port shows a compact
 * rear-view radar, which keeps the awareness benefit without a per-version
 * framebuffer/camera hook.
 */
@SearchTags({"rearview", "rear view", "rear-view", "radar", "behind"})
public final class RearviewHack extends Hack implements GUIRenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance shown in the rear-view radar.", 48, 8, 128, 4,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting width = new SliderSetting("Width",
		"Width of the rear-view radar.", 180, 80, 400, 10,
		ValueDisplay.INTEGER);

	private final SliderSetting height = new SliderSetting("Height",
		"Height of the rear-view radar.", 100, 40, 220, 10,
		ValueDisplay.INTEGER);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the radar.", 20, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the radar.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting showPlayers = new CheckboxSetting(
		"Show players", "Shows players on the radar.", true);

	private final CheckboxSetting showMobs = new CheckboxSetting("Show mobs",
		"Shows living mobs on the radar.", true);

	private final CheckboxSetting showOther = new CheckboxSetting("Show other",
		"Shows other entities on the radar.", false);

	public RearviewHack()
	{
		super("Rearview");
		setCategory(Category.RENDER);
		addSetting(range);
		addSetting(width);
		addSetting(height);
		addSetting(posX);
		addSetting(posY);
		addSetting(showPlayers);
		addSetting(showMobs);
		addSetting(showOther);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(MC.player == null || MC.level == null || uiHidden())
			return;

		int left = (int)posX.getValue();
		int top = (int)posY.getValue();
		int w = width.getValueI();
		int h = height.getValueI();
		int centerX = left + w / 2;
		int bottom = top + h - 6;
		double r = range.getValue();

		context.fill(left, top, left + w, top + h, 0xB0000000);
		context.fill(left, top, left + w, top + 1, 0xFF606060);
		context.fill(left, top + h - 1, left + w, top + h, 0xFF606060);
		context.fill(left, top, left + 1, top + h, 0xFF606060);
		context.fill(left + w - 1, top, left + w, top + h, 0xFF606060);
		text(context, "Rearview", left + 5, top + 5, 0xFFFFFFFF, true);

		Vec3 look = MC.player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if(right.lengthSqr() < 1.0E-4)
			right = new Vec3(1, 0, 0);
		else
			right = right.normalize();

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(entity == MC.player || entity.isRemoved() || !entity.isAlive())
				continue;

			boolean player = entity instanceof Player;
			boolean mob = entity instanceof LivingEntity && !player;
			if(player && !showPlayers.isChecked()
				|| mob && !showMobs.isChecked()
				|| !player && !mob && !showOther.isChecked())
				continue;

			Vec3 rel = entity.position().subtract(MC.player.position());
			if(rel.lengthSqr() > r * r)
				continue;

			double forward = look.dot(rel);
			if(forward >= 0)
				continue;

			double lateral = right.dot(rel);
			double depth = -forward;
			int px = centerX + (int)(lateral / r * (w / 2.0 - 5));
			int py = bottom - (int)(depth / r * (h - 20));
			if(px < left + 3 || px > left + w - 3 || py < top + 14
				|| py > top + h - 3)
				continue;

			int color = player ? 0xFFFF5555
				: mob ? 0xFFFFAA00 : 0xFFAAAAAA;
			context.fill(px - 2, py - 2, px + 2, py + 2, color);
		}

		context.fill(centerX, bottom + 1, centerX + 1, bottom + 5, 0x80FFFFFF);
	}
__HELPERS__
}
'''

HUD['Skin3DHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
__GUI_IMPORT__

/**
 * Rotating stylised head preview.
 *
 * <p>Soar rendered a voxel skin from the player's skin texture. Fetching and
 * drawing the skin texture differs between versions, so this port draws a
 * lightweight skin-coloured head with an animated face instead.
 */
@SearchTags({"skin 3d", "skin3d", "skin preview", "player head"})
public final class Skin3DHack extends Hack implements GUIRenderListener
{
	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the head preview.", 2, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the preview.", 30, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the preview.", 30, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting spin = new SliderSetting("Spin speed",
		"Rotation speed of the preview.", 1, 0, 4, 0.05,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting showName = new CheckboxSetting("Show name",
		"Shows the local player name below the head.", true);

	public Skin3DHack()
	{
		super("Skin3D");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(spin);
		addSetting(showName);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(MC.player == null || uiHidden())
			return;

		int size = (int)(24 * scale.getValue());
		if(size < 4)
			return;

		int x = (int)posX.getValue();
		int y = (int)posY.getValue();
		double angle = System.currentTimeMillis() / 1000.0
			* spin.getValue();
		int off = (int)(Math.sin(angle) * Math.max(1, size / 10));

		context.fill(x + off, y - off, x + size + off, y + size - off,
			0x60000000);
		context.fill(x, y, x + size, y + size, 0xFF1B1B1B);
		context.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFFC79A6B);

		int eye = Math.max(2, size / 5);
		context.fill(x + size / 4, y + size / 3, x + size / 4 + eye,
			y + size / 3 + eye, 0xFF202020);
		context.fill(x + size * 3 / 4 - eye, y + size / 3, x + size * 3 / 4,
			y + size / 3 + eye, 0xFF202020);
		context.fill(x + size / 3, y + size * 2 / 3, x + size * 2 / 3,
			y + size * 2 / 3 + Math.max(1, size / 12), 0xFF6B3E2E);

		if(showName.isChecked())
			centeredText(context, MC.player.getName().getString(),
				x + size / 2, y + size + 3, 0xFFFFFFFF);
	}
__HELPERS__
}
'''

HUD['SoundSubtitlesHack'] = HEADER + r'''
import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
__GUI_IMPORT__

/**
 * Directional sound subtitle HUD.
 *
 * <p>Uses {@code SoundManager.addListener} through a reflective proxy so the
 * same class compiles against the 2-argument (1.20.2) and 3-argument
 * (1.20.3+) listener signatures.
 */
@SearchTags({"sound subtitles", "subtitles", "sound titles", "directional sound"})
public final class SoundSubtitlesHack extends Hack
	implements GUIRenderListener
{
	private static final int WIDTH = 200;
	private static final int ROW_HEIGHT = 12;

	private final SliderSetting max = new SliderSetting("Max",
		"Maximum number of subtitle rows.", 3, 1, 10, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting duration = new SliderSetting("Duration",
		"How long each subtitle stays on screen.", 3, 1, 10, 1,
		ValueDisplay.INTEGER.withSuffix("s"));

	private final CheckboxSetting background = new CheckboxSetting(
		"Background", "Draws a background behind the subtitles.", true);

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the subtitle text.", Color.WHITE);

	private final List<Subtitle> subtitles = new ArrayList<>();
	private SoundEventListener listener;

	public SoundSubtitlesHack()
	{
		super("SoundSubtitles");
		setCategory(Category.RENDER);
		addSetting(max);
		addSetting(duration);
		addSetting(background);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		listener = (SoundEventListener)Proxy.newProxyInstance(
			SoundEventListener.class.getClassLoader(),
			new Class<?>[]{SoundEventListener.class}, (proxy, method, args) ->
			{
				if("onPlaySound".equals(method.getName()) && args != null
					&& args.length > 0
					&& args[0] instanceof SoundInstance sound)
				{
					WeighedSoundEvents events = args.length > 1
						&& args[1] instanceof WeighedSoundEvents w ? w : null;
					handleSound(sound, events);
					return null;
				}

				switch(method.getName())
				{
					case "equals":
					return args != null && args.length == 1
						&& proxy == args[0];

					case "hashCode":
					return System.identityHashCode(proxy);

					case "toString":
					return "SoundSubtitlesHack listener";

					default:
					return null;
				}
			});

		MC.getSoundManager().addListener(listener);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		if(listener != null)
			MC.getSoundManager().removeListener(listener);
		listener = null;
		subtitles.clear();
		EVENTS.remove(GUIRenderListener.class, this);
	}

	private void handleSound(SoundInstance sound, WeighedSoundEvents events)
	{
		if(subtitles.size() >= max.getValueI())
			return;

		String text = null;
		if(events != null && events.getSubtitle() != null)
			text = events.getSubtitle().getString();
		if(text == null || text.isBlank())
		{
			String id = soundId(sound);
			int slash = Math.max(id.lastIndexOf('/'), id.lastIndexOf('.'));
			text = slash >= 0 ? id.substring(slash + 1) : id;
		}
		if(text == null || text.isBlank())
			return;

		long now = System.currentTimeMillis();
		for(Subtitle subtitle : subtitles)
			if(subtitle.text.equals(text))
			{
				subtitle.x = sound.getX();
				subtitle.y = sound.getY();
				subtitle.z = sound.getZ();
				subtitle.start = now;
				return;
			}

		subtitles.add(new Subtitle(text, sound.getX(), sound.getY(),
			sound.getZ(), now));
	}

	private String soundId(SoundInstance sound)
	{
		try
		{
			return String.valueOf(sound.getClass().getMethod("getIdentifier")
				.invoke(sound));
		}catch(Exception ignored)
		{}

		try
		{
			return String.valueOf(sound.getClass().getMethod("getLocation")
				.invoke(sound));
		}catch(Exception ignored)
		{}

		return "sound";
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(MC.player == null || uiHidden())
			return;

		long now = System.currentTimeMillis();
		long life = (long)(duration.getValue() * 1000.0);
		subtitles.removeIf(subtitle -> now - subtitle.start > life);
		if(subtitles.isEmpty())
			return;

		int rows = Math.min(subtitles.size(), max.getValueI());
		int totalHeight = rows * ROW_HEIGHT;
		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();
		int left = width / 2 - WIDTH / 2;
		int top = height - 55 - totalHeight;

		if(background.isChecked())
			context.fill(left, top - 3, left + WIDTH, top + totalHeight + 3,
				0x90000000);

		Vec3 look = MC.player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if(right.lengthSqr() > 1.0E-4)
			right = right.normalize();

		for(int i = 0; i < rows; i++)
		{
			Subtitle subtitle = subtitles.get(i);
			Vec3 rel = new Vec3(subtitle.x, subtitle.y, subtitle.z)
				.subtract(MC.player.getEyePosition());
			double side = rel.lengthSqr() > 1.0E-6
				? right.dot(rel.normalize()) : 0;
			String arrow = side > 0.15 ? "> " : side < -0.15 ? "< " : "";
			int rgb = color.getColor().getRGB() & 0xFFFFFF;
			centeredText(context, arrow + subtitle.text, width / 2,
				top + i * ROW_HEIGHT + 2, 0xFF000000 | rgb);
		}
	}

	private static final class Subtitle
	{
		private final String text;
		private double x;
		private double y;
		private double z;
		private long start;

		private Subtitle(String text, double x, double y, double z,
			long start)
		{
			this.text = text;
			this.x = x;
			this.y = y;
			this.z = z;
			this.start = start;
		}
	}
__HELPERS__
}
'''

HUD['ViaVersionHack'] = HEADER + r'''
import net.minecraft.client.multiplayer.ServerData;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
__GUI_IMPORT__

/**
 * Displays the connected server's advertised version and protocol.
 *
 * <p>Soar bundled ViaVersion and translated packets. That backend is not
 * available in this multi-loader build, so this port deliberately exposes the
 * server information instead of pretending to translate protocols.
 */
@SearchTags({"viaversion", "protocol", "server version", "version info"})
public final class ViaVersionHack extends Hack implements GUIRenderListener
{
	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the server info panel.", 10, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the server info panel.", 10, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showProtocol = new CheckboxSetting(
		"Show protocol", "Shows the server protocol number.", true);

	private final CheckboxSetting showVersion = new CheckboxSetting(
		"Show version", "Shows the server version string.", true);

	public ViaVersionHack()
	{
		super("ViaVersion");
		setCategory(Category.OTHER);
		addSetting(posX);
		addSetting(posY);
		addSetting(showProtocol);
		addSetting(showVersion);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
		ChatUtils.warning(
			"ViaVersion: protocol translation is not bundled; showing server"
				+ " information only.");
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(uiHidden())
			return;

		ServerData server = MC.getCurrentServer();
		String name = server == null ? "Singleplayer" : server.name;
		String address = server == null ? "-" : server.ip;
		String version = server == null || server.version == null ? "-"
			: server.version.getString();
		int protocol = server == null ? -1 : server.protocol;

		int left = (int)posX.getValue();
		int top = (int)posY.getValue();
		context.fill(left, top, left + 190, top + 56, 0x90000000);
		text(context, "Server: " + name, left + 5, top + 5, 0xFFFFFFFF, true);
		text(context, "Address: " + address, left + 5, top + 16, 0xFFB0B0B0,
			true);
		if(showVersion.isChecked())
			text(context, "Version: " + version, left + 5, top + 27,
				0xFFB0B0B0, true);
		if(showProtocol.isChecked())
			text(context, "Protocol: " + protocol, left + 5, top + 38,
				0xFFB0B0B0, true);
	}
__HELPERS__
}
'''

HUD['JumpResetIndicatorHack'] = HEADER + r'''
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
__GUI_IMPORT__

/**
 * Shows whether the player jumped at the right tick after taking knockback.
 */
@SearchTags({"jump reset", "jumpreset", "jump reset indicator", "knockback"})
public final class JumpResetIndicatorHack extends Hack
	implements UpdateListener, GUIRenderListener
{
	private final SliderSetting maxTick = new SliderSetting("Max tick",
		"Maximum tick difference considered a jump reset.", 10, 1, 100, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the indicator.", 30, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the indicator.", 120, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Background", "Draws a background behind the indicator.", true);

	private int hurtAge;
	private int jumpAge;
	private int lastHurtTime;
	private boolean wasOnGround;
	private long lastTime;

	public JumpResetIndicatorHack()
	{
		super("JumpResetIndicator");
		setCategory(Category.RENDER);
		addSetting(maxTick);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
	}

	@Override
	protected void onEnable()
	{
		hurtAge = 0;
		jumpAge = 0;
		lastTime = 0;
		if(MC.player != null)
		{
			lastHurtTime = MC.player.hurtTime;
			wasOnGround = MC.player.onGround();
		}else
		{
			lastHurtTime = 0;
			wasOnGround = true;
		}

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		long now = System.currentTimeMillis();
		if(MC.player.hurtTime > lastHurtTime)
		{
			hurtAge = MC.player.tickCount;
			lastTime = now;
		}
		lastHurtTime = MC.player.hurtTime;

		boolean onGround = MC.player.onGround();
		if(wasOnGround && !onGround
			&& MC.player.getDeltaMovement().y > 0.05)
		{
			jumpAge = MC.player.tickCount;
			lastTime = now;
		}
		wasOnGround = onGround;
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(MC.player == null || uiHidden())
			return;

		String text = getStatus();
		int color = getStatusColor();
		int x = (int)posX.getValue();
		int y = (int)posY.getValue();
		int width = MC.font.width(text) + 8;

		if(background.isChecked())
			context.fill(x - 4, y - 3, x + width, y + 12, 0x90000000);

		text(context, text, x, y, color, true);
	}

	private String getStatus()
	{
		if(System.currentTimeMillis() - lastTime > 2500
			|| Math.abs(jumpAge - hurtAge) >= maxTick.getValueI())
			return "No Jump";

		if(jumpAge == hurtAge + 1)
			return "Perfect!";
		if(hurtAge + 1 < jumpAge)
			return "Late: " + (jumpAge - hurtAge + 1) + " Tick";
		return "Early: " + (hurtAge + 1 - jumpAge) + " Tick";
	}

	private int getStatusColor()
	{
		String status = getStatus();
		if(status.equals("Perfect!"))
			return 0xFF55FF55;
		if(status.startsWith("Early") || status.startsWith("Late"))
			return 0xFFFFAA00;
		return 0xFFFFFFFF;
	}
__HELPERS__
}
'''

HUD['PitchDisplayHack'] = HEADER + r'''
import java.text.DecimalFormat;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
__GUI_IMPORT__

@SearchTags({"pitch display", "pitch", "look angle"})
public final class PitchDisplayHack extends Hack implements GUIRenderListener
{
	private final DecimalFormat format = new DecimalFormat("0.##");

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the pitch display.", 30, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the pitch display.", 150, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Background", "Draws a background behind the text.", true);

	public PitchDisplayHack()
	{
		super("PitchDisplay");
		setCategory(Category.RENDER);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(MC.player == null || uiHidden())
			return;

		String text = "Pitch: " + format.format(MC.player.getXRot());
		int x = (int)posX.getValue();
		int y = (int)posY.getValue();
		if(background.isChecked())
			context.fill(x - 3, y - 2,
				x + MC.font.width(text) + 3, y + 10, 0x90000000);
		text(context, text, x, y, 0xFFFFFFFF, true);
	}
__HELPERS__
}
'''

HUD['YawDisplayHack'] = HEADER + r'''
import java.text.DecimalFormat;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
__GUI_IMPORT__

@SearchTags({"yaw display", "yaw", "rotation"})
public final class YawDisplayHack extends Hack implements GUIRenderListener
{
	private final DecimalFormat format = new DecimalFormat("0.##");

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the yaw display.", 30, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the yaw display.", 165, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Background", "Draws a background behind the text.", true);

	public YawDisplayHack()
	{
		super("YawDisplay");
		setCategory(Category.RENDER);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(MC.player == null || uiHidden())
			return;

		String text = "Yaw: " + format.format(
			Math.abs(MC.player.getYRot() % 90));
		int x = (int)posX.getValue();
		int y = (int)posY.getValue();
		if(background.isChecked())
			context.fill(x - 3, y - 2,
				x + MC.font.width(text) + 3, y + 10, 0x90000000);
		text(context, text, x, y, 0xFFFFFFFF, true);
	}
__HELPERS__
}
'''

HUD['WebBrowserHack'] = HEADER + r'''
import java.awt.Desktop;
import java.net.URI;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;
__GUI_IMPORT__

/**
 * Opens a URL in the operating system's browser and shows it on the HUD.
 *
 * <p>Soar embedded Chromium through MCEF. Bundling a browser engine for every
 * loader/version is not practical, so this port delegates to the system browser
 * and keeps the HUD panel.
 */
@SearchTags({"web browser", "webbrowser", "browser", "url", "website"})
public final class WebBrowserHack extends Hack implements GUIRenderListener
{
	private final TextFieldSetting url = new TextFieldSetting("URL",
		"Page opened in the system browser.", "https://www.google.com/",
		s -> s.startsWith("http://") || s.startsWith("https://"));

	private final CheckboxSetting openOnEnable = new CheckboxSetting(
		"Open on enable", "Opens the URL when the hack is enabled.", true);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the browser panel.", 10, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the browser panel.", 60, 0, 2500, 1,
		ValueDisplay.INTEGER);

	public WebBrowserHack()
	{
		super("WebBrowser");
		setCategory(Category.OTHER);
		addSetting(url);
		addSetting(openOnEnable);
		addSetting(posX);
		addSetting(posY);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
		if(openOnEnable.isChecked())
			open();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	private void open()
	{
		try
		{
			if(!Desktop.isDesktopSupported()
				|| !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
				throw new IllegalStateException("desktop browser unavailable");

			Desktop.getDesktop().browse(new URI(url.getValue()));
			ChatUtils.message("Opened in system browser: " + url.getValue());
		}catch(Exception e)
		{
			ChatUtils.error("Could not open the system browser: "
				+ e.getMessage());
		}
	}

	@Override
	public void onRenderGUI(__GUI__ context, float partialTicks)
	{
		if(uiHidden())
			return;

		int left = (int)posX.getValue();
		int top = (int)posY.getValue();
		context.fill(left, top, left + 230, top + 30, 0x90000000);
		text(context, "WebBrowser (system browser)", left + 5, top + 5,
			0xFFFFFFFF, true);
		text(context, url.getValue(), left + 5, top + 16, 0xFFB0B0B0, true);
	}
__HELPERS__
}
'''
