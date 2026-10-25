/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2;

import java.awt.Color;
import java.util.Locale;

import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.hud2.nanovg.HudFont;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.hud2.nanovg.NanoVGContext;

/**
 * Base class for every HUD module.
 *
 * <p>Ported from Soar Client v7.1.2
 * ({@code me.eldodebug.soar.management.mods.HUDMod}) and extended so the
 * module is also a Wurst {@link Feature}, which makes it visible in the
 * existing ClickGUI and gives it the standard settings persistence.
 */
public abstract class HUDMod extends Feature
{
	private static HudTheme theme = HudTheme.NORMAL;

	private final String id;
	private final String name;
	private final String description;
	private String translationKey;
	private Category category = Category.HUD;

	private int x = 100;
	private int y = 100;
	private int width = 100;
	private int height = 100;
	private int defaultX = 100;
	private int defaultY = 100;
	private float scale = 1F;
	private float defaultScale = 1F;
	private boolean enabled;
	private boolean defaultEnabled;
	private boolean dragging;
	private boolean draggable = true;
	private int draggingX;
	private int draggingY;
	private int failures;
	private boolean drewContent;

	private HudManager manager;
	private final HudAnimation animation = new HudAnimation();

	protected NanoVGContext nvg;
	protected float partialTicks;

	protected HUDMod(String id, String name, String description)
	{
		this.id = id;
		this.name = name;
		this.description = description;
		this.translationKey = id.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	public final String getId()
	{
		return id;
	}

	public final String getTranslationKey()
	{
		return translationKey;
	}

	protected final void setTranslationKey(String key)
	{
		translationKey = key;
	}

	public final String getRawName()
	{
		return name;
	}

	public final String getTranslatedName()
	{
		String key = "hud.name." + translationKey;
		String translated = WURST.translate(key);
		return translated.equals(key) ? name : translated;
	}

	@Override
	public final String getName()
	{
		return id;
	}

	@Override
	public String getDisplayName()
	{
		return getTranslatedName();
	}

	@Override
	public String getRenderName()
	{
		return getTranslatedName();
	}

	@Override
	public String getDescription()
	{
		String key = "hud.description." + translationKey;
		String translated = WURST.translate(key);
		return translated.equals(key) ? description : translated;
	}

	@Override
	public String getPrimaryAction()
	{
		return "Toggle";
	}

	@Override
	public void doPrimaryAction()
	{
		setEnabled(!enabled);
	}

	@Override
	public Category getCategory()
	{
		return category;
	}

	public final void setCategory(Category category)
	{
		this.category = category == null ? Category.HUD : category;
	}

	@Override
	public boolean isEnabled()
	{
		return enabled;
	}

	public void setEnabled(boolean enabled)
	{
		if(this.enabled == enabled)
			return;

		if(enabled && WURST.getHax() != null
			&& WURST.getHax().tooManyHaxHack.isEnabled()
			&& WURST.getHax().tooManyHaxHack.isBlocked(this))
			return;

		this.enabled = enabled;
		if(enabled)
			onEnable();
		else
			onDisable();

		if(manager != null)
		{
			manager.onModuleEnabledChanged(this);
			manager.addNotification(this);
		}
	}

	public final void setEnabledSilently(boolean enabled)
	{
		this.enabled = enabled;
	}

	protected void onEnable() {}

	protected void onDisable() {}

	final void bindManager(HudManager manager)
	{
		this.manager = manager;
	}

	public final HudManager getManager()
	{
		return manager;
	}

	/** Draws the module. Called once per frame while it is enabled. */
	protected abstract void draw();

	public final void render(NanoVGContext nvg, float partialTicks)
	{
		this.nvg = nvg;
		this.partialTicks = partialTicks;
		drewContent = false;
		nvg.save();
		try
		{
			draw();
		}catch(Throwable t)
		{
			if(++failures <= 3)
			{
				System.err.println("[HUD] Module " + id + " failed to draw");
				t.printStackTrace();
			}
			if(failures == 4)
				System.err.println("[HUD] Module " + id
					+ " keeps failing; disabling it");
			if(failures >= 4)
				setEnabledSilently(false);
		}finally
		{
			nvg.restore();
			this.nvg = null;
		}
	}

	/**
	 * Whether the module actually drew a card in the last frame. Modules that
	 * need a world or a player return without drawing, so the editor must not
	 * draw a selection ring around empty space or let those invisible modules
	 * steal mouse drags.
	 */
	public final boolean hasContent()
	{
		return drewContent;
	}

	protected final void markDrawn()
	{
		drewContent = true;
	}

	public final HudAnimation getAnimation()
	{
		return animation;
	}

	// ---------------------------------------------------------------- theme
	public static HudTheme getTheme()
	{
		return theme;
	}

	public static void setTheme(HudTheme newTheme)
	{
		theme = newTheme == null ? HudTheme.NORMAL : newTheme;
	}

	public static int accentColor(int alpha)
	{
		WURST.getGui().updateColors();
		float a = Math.max(0, Math.min(255, alpha)) / 255F;
		return WURST.getGui().getTheme().accent(a);
	}

	public static int accentColor2(int alpha)
	{
		int first = accentColor(alpha);
		int r = first >> 16 & 0xFF;
		int g = first >> 8 & 0xFF;
		int b = first & 0xFF;
		r = Math.round(r + (255 - r) * 0.35F);
		g = Math.round(g + (255 - g) * 0.35F);
		b = Math.round(b + (255 - b) * 0.35F);
		return alpha << 24 | r << 16 | g << 8 | b;
	}

	private static Color color(int argb)
	{
		return new Color(argb, true);
	}

	public void drawBackground(float width, float height)
	{
		drawBackground(0, 0, width, height, 6 * scale);
	}

	public void drawBackground(float addX, float addY, float width,
		float height)
	{
		drawBackground(addX, addY, width, height, 6 * scale);
	}

	public void drawBackground(float width, float height, float radius)
	{
		drawBackground(0, 0, width, height, radius);
	}

	public void drawBackground(float addX, float addY, float width,
		float height, float radius)
	{
		if(width < 0 || height < 0)
			return;

		drewContent = true;
		float x = this.x + addX * scale;
		float y = this.y + addY * scale;
		float w = width * scale;
		float h = height * scale;

		int color1 = accentColor(220);
		int color2 = accentColor2(220);
		int black = 0x64000000;

		switch(theme)
		{
			case NORMAL:
			drawShadowScreen(x, y, w, h, radius);
			roundedRect(x, y, w, h, radius, 0xA8000000);
			drawRoundedOutline(x, y, w, h, radius, 1,
				new Color(0x14FFFFFF, true));
			break;

			case GLOW:
			gradientShadow(x, y, w, h, radius);
			gradientRoundedRect(x, y, w, h, radius, color1, color2);
			break;

			case OUTLINE:
			drawShadowScreen(x - 2, y - 2, w + 4, h + 4, radius + 2);
			gradientOutline(x - 1, y - 1, w + 2, h + 2, radius + 1,
				1.5F * scale, color1, color2);
			roundedRect(x, y, w, h, radius, black);
			break;

			case OUTLINE_GLOW:
			gradientShadow(x - 2, y - 2, w + 4, h + 4, radius + 2);
			gradientOutline(x - 1, y - 1, w + 2, h + 2, radius + 1,
				1.5F * scale, color1, color2);
			roundedRect(x, y, w, h, radius, black);
			break;

			case VANILLA:
			drawShadowScreen(x, y, w, h, radius);
			roundedRect(x, y, w, h, radius, black);
			break;

			case VANILLA_GLOW:
			gradientShadow(x, y, w, h, radius);
			roundedRect(x, y, w, h, radius, black);
			break;

			case SHADOW:
			drawShadowScreen(x, y, w, h, radius);
			roundedRect(x, y, w, h, radius, black);
			break;

			case DARK:
			drawShadowScreen(x, y, w, h, radius);
			roundedRect(x, y, w, h, radius, 0xDC141414);
			break;

			case LIGHT:
			drawShadowScreen(x, y, w, h, radius);
			roundedRect(x, y, w, h, radius, 0xDCF0F0F0);
			break;
		}
	}

	private void drawShadowScreen(float x, float y, float width,
		float height, float radius)
	{
		if(nvg == null || width <= 0 || height <= 0)
			return;
		nvg.drawShadow(x, y, width, height, radius);
	}

	private void gradientShadow(float x, float y, float width, float height,
		float radius)
	{
		if(nvg == null)
			return;
		nvg.drawGradientShadow(x - 4 * scale, y - 3 * scale,
			width + 8 * scale, height + 6 * scale, radius + 4 * scale,
			color(accentColor(40)), color(accentColor2(40)));
		nvg.drawGradientShadow(x - 2 * scale, y - 2 * scale,
			width + 4 * scale, height + 4 * scale, radius + 2 * scale,
			color(accentColor(55)), color(accentColor2(55)));
	}

	private void roundedRect(float x, float y, float width, float height,
		float radius, int argb)
	{
		if(nvg != null)
			nvg.drawRoundedRect(x, y, width, height, radius, color(argb));
	}

	private void gradientRoundedRect(float x, float y, float width,
		float height, float radius, int top, int bottom)
	{
		if(nvg != null)
			nvg.drawGradientRoundedRect(x, y, width, height, radius,
				color(top), color(bottom));
	}

	private void gradientOutline(float x, float y, float width, float height,
		float radius, float strokeWidth, int color1, int color2)
	{
		if(nvg != null)
			nvg.drawGradientOutlineRoundedRect(x, y, width, height, radius,
				strokeWidth, color(color1), color(color2));
	}

	// --------------------------------------------------------------- drawing
	public void save()
	{
		if(nvg != null)
			nvg.save();
	}

	public void restore()
	{
		if(nvg != null)
			nvg.restore();
	}

	public void scissor(float addX, float addY, float width, float height)
	{
		if(nvg != null)
			nvg.scissor(x + addX * scale, y + addY * scale, width * scale,
				height * scale);
	}

	public void scale(float addX, float addY, float width, float height,
		float extraScale)
	{
		if(nvg != null)
			nvg.scale(x + addX * scale, y + addY * scale, width * scale,
				height * scale, extraScale);
	}

	public void drawRect(float addX, float addY, float width, float height,
		Color color)
	{
		if(nvg != null)
			nvg.drawRect(x + addX * scale, y + addY * scale, width * scale,
				height * scale, color);
	}

	public void drawRoundedRect(float addX, float addY, float width,
		float height, float radius, Color color)
	{
		if(nvg != null && width >= 0 && height >= 0)
			nvg.drawRoundedRect(x + addX * scale, y + addY * scale,
				width * scale, height * scale, radius * scale, color);
	}

	public void drawRoundedOutline(float addX, float addY, float width,
		float height, float radius, float strokeWidth, Color color)
	{
		if(nvg != null)
			nvg.drawOutlineRoundedRect(x + addX * scale, y + addY * scale,
				width * scale, height * scale, radius * scale,
				strokeWidth * scale, color);
	}

	public void drawImage(int image, float addX, float addY, float width,
		float height)
	{
		if(nvg != null)
			nvg.drawImage(image, x + addX * scale, y + addY * scale,
				width * scale, height * scale);
	}

	public void drawCircle(float addX, float addY, float radius, Color color)
	{
		if(nvg != null)
			nvg.drawCircle(x + addX * scale, y + addY * scale,
				radius * scale, color);
	}

	public void drawArc(float addX, float addY, float radius, float startAngle,
		float endAngle, float strokeWidth, Color color)
	{
		if(nvg != null)
			nvg.drawArc(x + addX * scale, y + addY * scale, radius * scale,
				startAngle, endAngle, strokeWidth * scale, color);
	}

	public void drawText(String text, float addX, float addY, float size,
		HudFont font, Color color)
	{
		if(nvg != null)
			nvg.drawText(text, x + addX * scale, y + addY * scale, color,
				size * scale, font);
	}

	public void drawText(String text, float addX, float addY, float size,
		HudFont font)
	{
		drawText(text, addX, addY, size, font, getFontColor());
	}

	public void drawCenteredText(String text, float addX, float addY,
		float size, HudFont font, Color color)
	{
		if(nvg != null)
			nvg.drawCenteredText(text, x + addX * scale,
				y + addY * scale, color, size * scale, font);
	}

	public void drawCenteredText(String text, float addX, float addY,
		float size, HudFont font)
	{
		drawCenteredText(text, addX, addY, size, font, getFontColor());
	}

	/**
	 * Measures text in the module's own (unscaled) coordinate space.
	 *
	 * <p>Callers feed the result straight back into {@link #drawBackground} and
	 * {@link #drawText}, which apply {@link #getScale()} themselves. Scaling the
	 * size here as well would double-scale every generated card.
	 */
	public float getTextWidth(String text, float size, HudFont font)
	{
		return nvg == null ? 0 : nvg.getTextWidth(text, size, font);
	}

	public float getTextHeight(String text, float size, HudFont font)
	{
		return nvg == null ? 0 : nvg.getTextHeight(text, size, font);
	}

	public void drawRoundedImage(String path, float addX, float addY,
		float width, float height, float radius)
	{
		if(nvg != null)
			nvg.drawRoundedImage(path, x + addX * scale, y + addY * scale,
				width * scale, height * scale, radius * scale);
	}

	public void drawRoundedImage(java.io.File file, float addX, float addY,
		float width, float height, float radius)
	{
		if(nvg != null)
			nvg.drawRoundedImage(file, x + addX * scale, y + addY * scale,
				width * scale, height * scale, radius * scale);
	}

	public void drawRoundedImage(int texture, float addX, float addY,
		float width, float height, float radius)
	{
		if(nvg != null)
			nvg.drawRoundedImage(texture, x + addX * scale,
				y + addY * scale, width * scale, height * scale,
				radius * scale);
	}

	public void drawPlayerHead(String texturePath, float addX, float addY,
		float width, float height, float radius)
	{
		if(nvg != null)
			nvg.drawPlayerHead(texturePath, x + addX * scale,
				y + addY * scale, width * scale, height * scale,
				radius * scale);
	}

	public Color getFontColor(int alpha)
	{
		if(theme == HudTheme.DARK || theme == HudTheme.LIGHT)
			return color(accentColor(alpha));
		return new Color(255, 255, 255, Math.max(0, Math.min(255, alpha)));
	}

	public Color getFontColor()
	{
		return getFontColor(255);
	}

	// ----------------------------------------------------------------- state
	public boolean isEditing()
	{
		return MC.screen instanceof GuiEditHUD;
	}

	public int getX()
	{
		return x;
	}

	public void setX(int x)
	{
		this.x = x;
	}

	public int getY()
	{
		return y;
	}

	public void setY(int y)
	{
		this.y = y;
	}

	public int getWidth()
	{
		return Math.round(width * scale);
	}

	public int getUnscaledWidth()
	{
		return width;
	}

	public void setWidth(int width)
	{
		this.width = width;
	}

	public int getHeight()
	{
		return Math.round(height * scale);
	}

	public int getUnscaledHeight()
	{
		return height;
	}

	public void setHeight(int height)
	{
		this.height = height;
	}

	public void setSize(int width, int height)
	{
		this.width = width;
		this.height = height;
	}

	public float getScale()
	{
		return scale;
	}

	public void setScale(float scale)
	{
		this.scale = Math.max(0.2F, Math.min(5F, scale));
	}

	public float getDefaultScale()
	{
		return defaultScale;
	}

	public void setDefaultScale(float scale)
	{
		this.defaultScale = Math.max(0.2F, Math.min(5F, scale));
		this.scale = this.defaultScale;
	}

	public boolean isDefaultEnabled()
	{
		return defaultEnabled;
	}

	public void setDefaultEnabled(boolean enabled)
	{
		this.defaultEnabled = enabled;
		this.enabled = enabled;
	}

	public int getDefaultX()
	{
		return defaultX;
	}

	public int getDefaultY()
	{
		return defaultY;
	}

	public void setDefaultPosition(int x, int y)
	{
		defaultX = x;
		defaultY = y;
		this.x = x;
		this.y = y;
	}

	public boolean isDragging()
	{
		return dragging;
	}

	public void setDragging(boolean dragging)
	{
		this.dragging = dragging;
	}

	public boolean isDraggable()
	{
		return draggable;
	}

	public void setDraggable(boolean draggable)
	{
		this.draggable = draggable;
	}

	public int getDraggingX()
	{
		return draggingX;
	}

	public void setDraggingX(int draggingX)
	{
		this.draggingX = draggingX;
	}

	public int getDraggingY()
	{
		return draggingY;
	}

	public void setDraggingY(int draggingY)
	{
		this.draggingY = draggingY;
	}

	public void clampToScreen(int screenWidth, int screenHeight)
	{
		x = Math.max(0, Math.min(x, screenWidth - getWidth()));
		y = Math.max(0, Math.min(y, screenHeight - getHeight()));
	}

	public static HudFont regularFont()
	{
		return HudFonts.REGULAR;
	}

	public static HudFont mediumFont()
	{
		return HudFonts.MEDIUM;
	}

	public static HudFont demiboldFont()
	{
		return HudFonts.DEMIBOLD;
	}

	public static HudFont iconFont()
	{
		return HudFonts.ICON;
	}
}
