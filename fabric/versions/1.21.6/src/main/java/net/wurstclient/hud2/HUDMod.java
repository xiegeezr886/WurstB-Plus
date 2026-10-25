package net.wurstclient.hud2;

import java.util.Locale;

import net.minecraft.client.gui.Font;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.util.render.GuiGraphicsExtractor;

/**
 * HUD base class ported from Soar Client v7.1.2
 * ({@code me.eldodebug.soar.management.mods.HUDMod}).
 *
 * <p>
 * HUD mods own an absolute position and a scale factor, draw a themed rounded
 * background, and expose the same save/restore/scale/draw helper surface as
 * Soar so the ported HUD modules can keep their original drawing logic.
 */
public abstract class HUDMod
{
	protected static final WurstClient WURST = WurstClient.INSTANCE;

	private static HudTheme theme = HudTheme.NORMAL;
	private static final float DEFAULT_SCALE = 1.0F;
	private static final int DEFAULT_SIZE = 100;

	private final String id;
	private final String name;
	private int x = 100;
	private int y = 100;
	private int width = DEFAULT_SIZE;
	private int height = DEFAULT_SIZE;
	private int defaultX = 100;
	private int defaultY = 100;
	private float scale = DEFAULT_SCALE;
	private float defaultScale = DEFAULT_SCALE;
	private boolean enabled = true;
	private boolean defaultEnabled = true;
	private boolean dragging;
	private boolean draggable = true;
	private int draggingX;
	private int draggingY;

	protected GuiGraphicsExtractor graphics;
	protected float partialTicks;

	protected HUDMod(String id, String name)
	{
		this.id = id;
		this.name = name;
	}

	public final String getId()
	{
		return id;
	}

	public final String getName()
	{
		return name;
	}

	public final String getTranslatedName()
	{
		String key = "hud.name." + name.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		String translated = WURST.translate(key);
		return translated.equals(key) ? name : translated;
	}

	/** Draws this HUD mod. Called once per frame while the mod is enabled. */
	protected abstract void draw();

	public void onEnable(HudManager manager) {}

	public void onDisable(HudManager manager) {}

	public final void render(GuiGraphicsExtractor graphics, float partialTicks)
	{
		this.graphics = graphics;
		this.partialTicks = partialTicks;
		try
		{
			draw();
		}finally
		{
			this.graphics = null;
		}
	}

	public static HudTheme getTheme()
	{
		return theme;
	}

	public static void setTheme(HudTheme newTheme)
	{
		theme = newTheme == null ? HudTheme.NORMAL : newTheme;
	}

	public void save()
	{
		graphics.pose().pushMatrix();
	}

	public void restore()
	{
		graphics.pose().popMatrix();
	}

	public void scale(float addX, float addY, float width, float height,
		float extraScale)
	{
		float centerX = x + (addX + width / 2F) * scale;
		float centerY = y + (addY + height / 2F) * scale;
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(extraScale, extraScale);
		graphics.pose().translate(-centerX, -centerY);
	}

	// ------------------------------------------------------------ backgrounds
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

		float x = this.x + addX * scale;
		float y = this.y + addY * scale;
		float w = width * scale;
		float h = height * scale;

		int color1 = accentColor(220);
		int color2 = accentColor2(220);
		int shadow = 0x3C000000;
		int black = 0x64000000;

		switch(theme)
		{
			case NORMAL:
			drawShadowScreen(x, y, w, h, radius);
			gradientRoundedRect(x, y, w, h, radius, color1, color2);
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

	public void drawShadow(float addX, float addY, float width, float height,
		float radius)
	{
		float x = this.x + addX * scale;
		float y = this.y + addY * scale;
		float w = width * scale;
		float h = height * scale;
		drawShadowScreen(x, y, w, h, radius * scale);
	}

	private void drawShadowScreen(float x, float y, float width, float height,
		float radius)
	{
		if(width <= 0 || height <= 0)
			return;

		float expand = Math.max(1, scale);
		FlatRenderer.fillRoundedRect(graphics, (int)(x - expand * 4),
			(int)(y - expand * 2), (int)(x + width + expand * 4),
			(int)(y + height + expand * 2), (int)(radius + expand * 4),
			0x14000000);
		FlatRenderer.fillRoundedRect(graphics, (int)(x - expand * 3),
			(int)(y - expand), (int)(x + width + expand * 3),
			(int)(y + height + expand), (int)(radius + expand * 3),
			0x1E000000);
		FlatRenderer.fillRoundedRect(graphics, (int)(x - expand * 2),
			(int)(y - expand), (int)(x + width + expand * 2),
			(int)(y + height + expand), (int)(radius + expand * 2),
			0x2A000000);
	}

	private void gradientShadow(float x, float y, float width, float height,
		float radius)
	{
		gradientRoundedRect(x - 4 * scale, y - 3 * scale, width + 8 * scale,
			height + 6 * scale, radius + 4 * scale, accentColor(40),
			accentColor2(40));
		gradientRoundedRect(x - 2 * scale, y - 2 * scale, width + 4 * scale,
			height + 4 * scale, radius + 2 * scale, accentColor(55),
			accentColor2(55));
	}

	// ---------------------------------------------------------------- drawing
	public void drawRect(float addX, float addY, float width, float height,
		int color)
	{
		graphics.fill((int)(x + addX * scale), (int)(y + addY * scale),
			(int)(x + (addX + width) * scale),
			(int)(y + (addY + height) * scale), color);
	}

	public void drawRoundedRect(float addX, float addY, float width,
		float height, float radius, int color)
	{
		if(width < 0 || height < 0)
			return;
		FlatRenderer.fillRoundedRect(graphics, (int)(x + addX * scale),
			(int)(y + addY * scale), (int)(x + (addX + width) * scale),
			(int)(y + (addY + height) * scale), Math.round(radius * scale),
			color);
	}

	public void drawRoundedOutline(float addX, float addY, float width,
		float height, float radius, float strokeWidth, int color)
	{
		FlatRenderer.drawRoundedOutline(graphics,
			(int)(x + addX * scale), (int)(y + addY * scale),
			(int)(x + (addX + width) * scale),
			(int)(y + (addY + height) * scale), Math.round(radius * scale),
			color);
	}

	private void roundedRect(float x, float y, float width, float height,
		float radius, int color)
	{
		FlatRenderer.fillRoundedRect(graphics, (int)x, (int)y,
			(int)(x + width), (int)(y + height), Math.max(0, (int)radius),
			color);
	}

	private void gradientRoundedRect(float x, float y, float width,
		float height, float radius, int top, int bottom)
	{
		if(width <= 0 || height <= 0)
			return;

		int topY = (int)y;
		int bottomY = (int)(y + height);
		int left = (int)x;
		int right = (int)(x + width);
		float r = Math.max(0, Math.min(radius, Math.min(width, height) / 2F));
		int rows = Math.max(1, bottomY - topY);
		for(int row = 0; row < rows; row++)
		{
			float progress = rows <= 1 ? 0 : row / (float)(rows - 1);
			double dy = 0;
			if(row < r)
				dy = r - row - 0.5;
			else if(row >= rows - r)
				dy = row - (rows - r) + 0.5;
			int inset = 0;
			if(dy > 0)
				inset = (int)Math.ceil(r - Math.sqrt(Math.max(0, r * r
					- dy * dy)));
			graphics.fill(left + inset, topY + row, right - inset,
				topY + row + 1, lerpColor(top, bottom, progress));
		}
	}

	private void gradientOutline(float x, float y, float width, float height,
		float radius, float strokeWidth, int color1, int color2)
	{
		FlatRenderer.drawRoundedOutline(graphics, (int)x, (int)y,
			(int)(x + width), (int)(y + height), Math.max(0, (int)radius),
			color1);
		int inset = Math.max(1, Math.round(strokeWidth));
		FlatRenderer.drawRoundedOutline(graphics, (int)x + inset,
			(int)y + inset, (int)(x + width) - inset,
			(int)(y + height) - inset, Math.max(0, (int)radius - inset),
			color2);
	}

	// ------------------------------------------------------------------- text
	public void drawText(String text, float addX, float addY, float size,
		int color)
	{
		if(text == null || text.isEmpty())
			return;

		Font font = WurstClient.MC.font;
		float fontScale = size * scale / 9F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + addX * scale, y + addY * scale);
		graphics.pose().scale(fontScale, fontScale);
		graphics.text(font, text, 0, 0, color, false);
		graphics.pose().popMatrix();
	}

	public void drawText(String text, float addX, float addY, float size)
	{
		drawText(text, addX, addY, size, getFontColor());
	}

	public void drawCenteredText(String text, float addX, float addY,
		float size, int color)
	{
		if(text == null || text.isEmpty())
			return;

		Font font = WurstClient.MC.font;
		float fontScale = size * scale / 9F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + addX * scale, y + addY * scale);
		graphics.pose().scale(fontScale, fontScale);
		graphics.text(font, text, -font.width(text) / 2, 0, color, false);
		graphics.pose().popMatrix();
	}

	public void drawCenteredText(String text, float addX, float addY,
		float size)
	{
		drawCenteredText(text, addX, addY, size, getFontColor());
	}

	public float getTextWidth(String text, float size)
	{
		return WurstClient.MC.font.width(text) * size / 9F;
	}

	public float getTextHeight(float size)
	{
		return size;
	}

	// ----------------------------------------------------------------- colors
	public int getFontColor(int alpha)
	{
		if(theme == HudTheme.DARK || theme == HudTheme.LIGHT)
			return accentColor(alpha);
		return alpha << 24 | 0xFFFFFF;
	}

	public int getFontColor()
	{
		return getFontColor(255);
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

	public static int lerpColor(int from, int to, float progress)
	{
		float p = Math.max(0, Math.min(1, progress));
		int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * p);
		int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF)
			- (from >> 16 & 0xFF)) * p);
		int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF)
			- (from >> 8 & 0xFF)) * p);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF))
			* p);
		return a << 24 | r << 16 | g << 8 | b;
	}

	// ------------------------------------------------------------------ state
	public boolean isEditing()
	{
		return WurstClient.MC.screen instanceof GuiEditHUD;
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
		this.scale = Math.max(0.2F, Math.min(5.0F, scale));
	}

	public float getDefaultScale()
	{
		return defaultScale;
	}

	public void setDefaultScale(float defaultScale)
	{
		this.defaultScale = defaultScale;
		this.scale = defaultScale;
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public void setEnabled(boolean enabled)
	{
		if(this.enabled == enabled)
			return;
		this.enabled = enabled;
		onEnabledChanged(enabled);
	}

	public void setEnabledSilently(boolean enabled)
	{
		this.enabled = enabled;
	}

	protected void onEnabledChanged(boolean enabled)
	{
	}

	public boolean isDefaultEnabled()
	{
		return defaultEnabled;
	}

	public void setDefaultEnabled(boolean defaultEnabled)
	{
		this.defaultEnabled = defaultEnabled;
		this.enabled = defaultEnabled;
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
}
