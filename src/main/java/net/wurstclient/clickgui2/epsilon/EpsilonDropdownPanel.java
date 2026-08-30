package net.wurstclient.clickgui2.epsilon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme;
import net.wurstclient.clickgui2.supersoft.UiTween;

/**
 * Epsilon 26.1.2 AbstractDropdownPanel 的直接移植。
 *
 * <p>面板框架：圆角面板 + 阴影 + 头部（图标、标题、展开箭头）；左键头部拖动、
 * 右键折叠/展开（openAnim 高度动画）；内容区滚动（平滑趋近）。</p>
 */
public abstract class EpsilonDropdownPanel
{
	protected final String id;
	protected final UiTween openAnim = new UiTween(0,
		EpsilonDropdownTheme.ANIM_OPEN);
	protected float x;
	protected float y;
	protected float width = EpsilonDropdownTheme.PANEL_WIDTH;
	protected boolean opened;
	protected boolean visible;
	protected boolean dragging;
	protected float dragOffsetX;
	protected float dragOffsetY;
	protected float scroll;
	protected float targetScroll;
	protected float maxScroll;
	protected float maxPanelHeight = 300F;

	private static final float SCROLL_SMOOTHING = 0.16F;
	private static final float SCROLL_EPSILON = 0.05F;

	protected EpsilonDropdownPanel(String id)
	{
		this.id = id;
	}

	public String getId()
	{
		return id;
	}

	public void startIntro()
	{
		openAnim.snap(opened ? 1 : 0);
	}

	public abstract String getTitle();

	public abstract net.wurstclient.clickgui2.GuiIcon getIcon();

	protected abstract float computeContentHeight();

	protected abstract void drawPanelContent(GuiGraphics graphics,
		int mouseX, int mouseY, float visibleHeight);

	protected boolean mouseClickedContent(double mouseX, double mouseY,
		int button)
	{
		return false;
	}

	protected boolean mouseReleasedContent(double mouseX, double mouseY,
		int button)
	{
		return false;
	}

	protected boolean mouseDraggedContent(double mouseX, double mouseY,
		int button)
	{
		return false;
	}

	protected void tickContent()
	{}

	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		if(!visible)
			return;
		float expand = openAnim.update(opened ? 1 : 0);
		float contentHeight = computeContentHeight();
		float visibleHeight = computeVisibleContentHeight(contentHeight);
		updateScroll(contentHeight, visibleHeight, true);
		float panelHeight = EpsilonDropdownTheme.PANEL_HEADER_HEIGHT
			+ (visibleHeight + EpsilonDropdownTheme.PANEL_BOTTOM_PADDING)
				* expand;

		drawBackground(graphics, panelHeight, expand);
		if(expand < 0.01F)
			return;

		float clipY = y + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT;
		float clipH = visibleHeight * expand;
		if(clipH > 0.5F)
		{
			graphics.enableScissor(Math.round(x), Math.round(clipY),
				Math.round(width), Math.round(clipH));
			drawPanelContent(graphics, mouseX, mouseY, visibleHeight);
			graphics.disableScissor();
			drawScrollbar(graphics, contentHeight, visibleHeight, clipY,
				clipH, mouseX, mouseY);
		}
	}

	private void drawBackground(GuiGraphics graphics, float panelHeight,
		float expand)
	{
		// 阴影
		FlatRenderer.fillRoundedRect(graphics, Math.round(x) - 2,
			Math.round(y) + 3, Math.round(x + width) + 2,
			Math.round(y + panelHeight) + 6,
			Math.round(EpsilonDropdownTheme.PANEL_RADIUS + 2),
			EpsilonDropdownTheme.panelShadow());
		// 面板主体
		FlatRenderer.fillRoundedRect(graphics, Math.round(x), Math.round(y),
			Math.round(x + width), Math.round(y + panelHeight),
			Math.round(EpsilonDropdownTheme.PANEL_RADIUS),
			EpsilonDropdownTheme.panelBackground());

		// 头部：图标 + 标题 + 展开箭头
		net.wurstclient.clickgui2.GuiIcon icon = getIcon();
		float textX = icon != null ? x + 7.5F + 16F : x + 10F;
		float textY = y + (EpsilonDropdownTheme.PANEL_HEADER_HEIGHT
			- Minecraft.getInstance().font.lineHeight) * 0.5F;
		if(icon != null)
			icon.draw(graphics, Math.round(x + 7.5F), Math.round(y + 4), 8,
				EpsilonMd3Theme.PRIMARY);
		drawScaled(graphics, getTitle(), Math.round(textX), Math.round(textY),
			EpsilonMd3Theme.TEXT_PRIMARY, EpsilonDropdownTheme.HEADER_TEXT_SCALE);
		drawChevron(graphics, x + width - 10F,
			y + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT * 0.5F, expand);
	}

	private void drawChevron(GuiGraphics graphics, float centerX, float centerY,
		float expand)
	{
		// 小三角：展开向下，折叠向右
		int color = EpsilonDropdownTheme.groupChevron(0);
		int size = 3;
		if(expand > 0.5F)
			for(int row = 0; row < size; row++)
				graphics.fill(Math.round(centerX - (size - row)),
					Math.round(centerY) + row - 1, Math.round(centerX + size
						- row + 1), Math.round(centerY) + row, color);
		else
			for(int row = 0; row < size; row++)
				graphics.fill(Math.round(centerX) + row - 1,
					Math.round(centerY - (size - row)), Math.round(centerX) + row,
					Math.round(centerY + size - row + 1), color);
	}

	private void drawScrollbar(GuiGraphics graphics, float contentHeight,
		float visibleHeight, float clipY, float clipH, int mouseX, int mouseY)
	{
		if(contentHeight <= visibleHeight || clipH <= 0)
			return;
		int trackX = Math.round(x + width) - 2;
		int thumbH = Math.max(10, Math.round(clipH * clipH / contentHeight));
		int maxThumbY = Math.round(clipH - thumbH);
		int thumbY = Math.round(clipY + maxThumbY * scroll / maxScroll);
		boolean hovered = mouseX >= trackX - 2 && mouseX <= trackX + 2
			&& mouseY >= thumbY && mouseY <= thumbY + thumbH;
		graphics.fill(trackX, Math.round(clipY), trackX + 2,
			Math.round(clipY + clipH), 0x00000000);
		graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH,
			EpsilonDropdownTheme.scrollbar(hovered ? 1 : 0));
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(!visible)
			return false;
		updateScroll(computeContentHeight(),
			computeVisibleContentHeight(computeContentHeight()), false);
		if(isHeaderHovered(mouseX, mouseY))
		{
			if(button == 0)
			{
				dragging = true;
				dragOffsetX = (float)(x - mouseX);
				dragOffsetY = (float)(y - mouseY);
				return true;
			}
			if(button == 1)
			{
				opened = !opened;
				return true;
			}
		}
		if(opened && openAnim.get() > 0.5F && isContentHovered(mouseX, mouseY))
			return mouseClickedContent(mouseX, mouseY, button);
		return false;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(button == 0 && dragging)
		{
			dragging = false;
			return true;
		}
		return mouseReleasedContent(mouseX, mouseY, button);
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		if(dragging)
		{
			x = (float)(mouseX + dragOffsetX);
			y = (float)(mouseY + dragOffsetY);
			return true;
		}
		return mouseDraggedContent(mouseX, mouseY, button);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount)
	{
		if(!visible || !opened)
			return false;
		if(isPanelHovered(mouseX, mouseY))
		{
			targetScroll = Mth.clamp(targetScroll - (float)amount
				* EpsilonDropdownTheme.SCROLL_SPEED, 0, maxScroll);
			return true;
		}
		return false;
	}

	public void tick()
	{
		tickContent();
	}

	public void setPosition(float x, float y)
	{
		this.x = x;
		this.y = y;
	}

	public void setMaxPanelHeight(float maxPanelHeight)
	{
		this.maxPanelHeight = maxPanelHeight;
	}

	public float getX()
	{
		return x;
	}

	public float getY()
	{
		return y;
	}

	public float getWidth()
	{
		return width;
	}

	public boolean isOpened()
	{
		return opened;
	}

	public void setOpened(boolean opened)
	{
		this.opened = opened;
		if(!opened)
			setScrollImmediate(0);
	}

	public boolean isVisible()
	{
		return visible;
	}

	public void setVisible(boolean visible)
	{
		this.visible = visible;
		if(!visible)
			dragging = false;
	}

	public float getPanelHeight()
	{
		float expand = openAnim.get();
		float contentHeight = computeContentHeight();
		float visibleHeight = computeVisibleContentHeight(contentHeight);
		return EpsilonDropdownTheme.PANEL_HEADER_HEIGHT
			+ (visibleHeight + EpsilonDropdownTheme.PANEL_BOTTOM_PADDING)
				* expand;
	}

	protected float computeVisibleContentHeight(float contentHeight)
	{
		float maxContentHeight = Math.max(0,
			maxPanelHeight - EpsilonDropdownTheme.PANEL_HEADER_HEIGHT
				- EpsilonDropdownTheme.PANEL_BOTTOM_PADDING);
		return Math.min(contentHeight, maxContentHeight);
	}

	protected boolean isHeaderHovered(double mouseX, double mouseY)
	{
		return mouseX >= x && mouseX <= x + width && mouseY >= y
			&& mouseY <= y + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT;
	}

	protected boolean isContentHovered(double mouseX, double mouseY)
	{
		float clipY = y + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT;
		float clipH = computeVisibleContentHeight(computeContentHeight())
			* openAnim.get();
		return mouseX >= x && mouseX <= x + width && mouseY >= clipY
			&& mouseY <= clipY + clipH;
	}

	protected boolean isPanelHovered(double mouseX, double mouseY)
	{
		return mouseX >= x && mouseX <= x + width && mouseY >= y
			&& mouseY <= y + getPanelHeight();
	}

	protected void setScrollImmediate(float value)
	{
		scroll = Mth.clamp(value, 0, maxScroll);
		targetScroll = scroll;
	}

	private void updateScroll(float contentHeight, float visibleHeight,
		boolean animate)
	{
		maxScroll = Math.max(0, contentHeight - visibleHeight);
		targetScroll = Mth.clamp(targetScroll, 0, maxScroll);
		scroll = Mth.clamp(scroll, 0, maxScroll);
		if(!animate)
			return;
		if(Math.abs(scroll - targetScroll) <= SCROLL_EPSILON)
			scroll = targetScroll;
		else
			scroll = Mth.lerp(SCROLL_SMOOTHING, scroll, targetScroll);
	}

	protected void drawScaled(GuiGraphics graphics, String text, int x, int y,
		int color, float scale)
	{
		// 使用默认 CozyUI 位图字体（9px 整数渲染，不缩放避免像素感）
		graphics.drawString(Minecraft.getInstance().font,
			net.wurstclient.clickgui2.PingFangFont.text(text), x, y, color,
			false);
	}
}
