package net.wurstclient.clickgui2.component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.clickgui2.supersoft.SuperSoftRenderer;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;

final class SuperSoftRowsWindow implements SuperSoftFloatingWindow
{
	private static final int WIDTH = 100;
	private static final int HEADER_HEIGHT = 20;
	private static final int ROW_HEIGHT = 16;
	private static final float TEXT_SCALE = 0.72F;

	private final String id;
	private final String title;
	private final GuiIcon icon;
	private final Supplier<List<Row>> rows;
	private final UiTween bodyMotion = new UiTween(0, 200);
	private final UiTween headerHoverMotion = new UiTween(0, 150);
	private final UiTween arrowMotion = new UiTween(0, 200);
	private final Map<String, UiTween> hoverMotions = new HashMap<>();
	private final Map<String, UiTween> activeMotions = new HashMap<>();
	private double x;
	private double y;
	private double scrollOffset;
	private int renderedBodyHeight;
	private boolean visible = true;
	private boolean collapsed;

	SuperSoftRowsWindow(String id, String title, GuiIcon icon,
		double x, double y, Supplier<List<Row>> rows)
	{
		this.id = id;
		this.title = title;
		this.icon = icon;
		this.x = x;
		this.y = y;
		this.rows = rows;
	}

	@Override
	public String getId()
	{
		return id;
	}

	@Override
	public double getX()
	{
		return x;
	}

	@Override
	public double getY()
	{
		return y;
	}

	@Override
	public boolean isVisible()
	{
		return visible;
	}

	@Override
	public void setVisible(boolean visible)
	{
		this.visible = visible;
	}

	@Override
	public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY,
		float partialTicks, int maxBodyHeight, VapeGuiContext context)
	{
		if(!visible)
			return;
		List<Row> currentRows = rows.get();
		int contentHeight = currentRows.size() * ROW_HEIGHT;
		int fullBodyHeight = Math.min(maxBodyHeight, contentHeight);
		int bodyHeight = Math.round(fullBodyHeight
			* bodyMotion.update(collapsed ? 0 : 1));
		renderedBodyHeight = bodyHeight;
		clampScroll(contentHeight, bodyHeight);
		int bottom = (int)y + HEADER_HEIGHT + bodyHeight;
		SuperSoftRenderer.window(graphics, (int)x, (int)y, (int)x + WIDTH,
			bottom, 2, SuperSoftTheme.BORDER);
		boolean headerHovered = headerContains(mouseX, mouseY);
		int headerColor = SuperSoftTheme.mix(SuperSoftTheme.HEADER,
			SuperSoftTheme.SETTING_HOVER,
			headerHoverMotion.update(headerHovered ? 0.32F : 0));
		SuperSoftRenderer.header(graphics, (int)x, (int)y, (int)x + WIDTH,
			(int)y + HEADER_HEIGHT, 2, headerColor);
		icon.draw(graphics, (int)x + 6, (int)y + 6, 8,
			SuperSoftTheme.TEXT_SECONDARY);
		drawScaled(graphics, font, title,
			(int)x + (WIDTH - Math.round(font.width(title) * TEXT_SCALE)) / 2,
			(int)y + 7, SuperSoftTheme.TEXT, TEXT_SCALE);
		GuiIcon.CHEVRON.drawRotated(graphics, (int)x + WIDTH - 14,
			(int)y + 6, 8, SuperSoftTheme.TEXT_SECONDARY,
			arrowMotion.update(collapsed ? 0 : 90));
		if(bodyHeight == 0)
			return;

		context.enableScissor(graphics, x, y + HEADER_HEIGHT, x + WIDTH, bottom);
		for(int index = 0; index < currentRows.size(); index++)
		{
			int rowY = (int)(y + HEADER_HEIGHT + index * ROW_HEIGHT
				- scrollOffset);
			if(rowY + ROW_HEIGHT <= y + HEADER_HEIGHT || rowY >= bottom)
				continue;
			Row row = currentRows.get(index);
			boolean hovered = mouseX >= x && mouseX < x + WIDTH
				&& mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
			String motionId = index + ":" + row.label;
			float hover = hoverMotions.computeIfAbsent(motionId,
				ignored -> new UiTween(0, 150)).update(hovered ? 1 : 0);
			float active = activeMotions.computeIfAbsent(motionId,
				ignored -> new UiTween(row.active.getAsBoolean() ? 1 : 0, 150))
				.update(row.active.getAsBoolean() ? 1 : 0);
			int animatedRow = SuperSoftTheme.mix(SuperSoftTheme.SETTING,
				SuperSoftTheme.SETTING_HOVER, hover);
			animatedRow = SuperSoftTheme.mix(animatedRow,
				SuperSoftTheme.ACCENT, active);
			int color = row.kind == RowKind.SECTION ? SuperSoftTheme.HEADER
				: row.kind == RowKind.DISABLED ? 0xFF151515
					: animatedRow;
			graphics.fill((int)x, rowY, (int)x + WIDTH, rowY + ROW_HEIGHT,
				color);
			drawScaled(graphics, font, row.label, (int)x + 6, rowY + 5,
				row.kind == RowKind.DISABLED ? SuperSoftTheme.MUTED
					: SuperSoftTheme.TEXT, TEXT_SCALE);
			if(row.kind == RowKind.SWITCH)
				SuperSoftRenderer.switchControl(graphics, (int)x + WIDTH - 20,
					rowY + 4, SuperSoftTheme.ACCENT, active);
		}
		graphics.disableScissor();
		renderScrollbar(graphics, contentHeight, bodyHeight);
	}

	@Override
	public boolean headerContains(double mouseX, double mouseY)
	{
		return visible && mouseX >= x && mouseX < x + WIDTH && mouseY >= y
			&& mouseY < y + HEADER_HEIGHT;
	}

	@Override
	public boolean mouseClickedHeader(double mouseX, double mouseY, int button)
	{
		if(button != 1)
			return false;
		collapsed = !collapsed;
		return true;
	}

	@Override
	public boolean mouseClickedBody(double mouseX, double mouseY, int button)
	{
		if(!visible || collapsed || mouseX < x
			|| mouseX >= x + WIDTH || mouseY < y + HEADER_HEIGHT)
			return false;
		List<Row> currentRows = rows.get();
		if(mouseY >= y + HEADER_HEIGHT + renderedBodyHeight)
			return false;
		if(button != 0)
			return true;
		int index = (int)((mouseY - y - HEADER_HEIGHT + scrollOffset)
			/ ROW_HEIGHT);
		if(index < 0 || index >= currentRows.size())
			return true;
		Row row = currentRows.get(index);
		if(row.kind != RowKind.SECTION && row.kind != RowKind.DISABLED)
			row.action.run();
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button)
	{
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		if(!visible || collapsed || mouseX < x || mouseX >= x + WIDTH
			|| mouseY < y + HEADER_HEIGHT)
			return false;
		int contentHeight = rows.get().size() * ROW_HEIGHT;
		if(mouseY >= y + HEADER_HEIGHT + renderedBodyHeight
			|| contentHeight <= renderedBodyHeight)
			return false;
		scrollOffset = Mth.clamp(scrollOffset + (delta > 0 ? -16 : 16), 0,
			contentHeight - renderedBodyHeight);
		return true;
	}

	@Override
	public void moveTo(double x, double y, int screenWidth, int screenHeight)
	{
		this.x = Mth.clamp(x, 0, Math.max(0, screenWidth - WIDTH));
		this.y = Mth.clamp(y, 0,
			Math.max(0, screenHeight - HEADER_HEIGHT));
	}

	@Override
	public int totalHeight(int maxBodyHeight)
	{
		return HEADER_HEIGHT + (collapsed ? 0
			: Math.min(maxBodyHeight, rows.get().size() * ROW_HEIGHT));
	}

	@Override
	public void tick()
	{}

	private void clampScroll(int contentHeight, int bodyHeight)
	{
		scrollOffset = Mth.clamp(scrollOffset, 0,
			Math.max(0, contentHeight - bodyHeight));
	}

	private void renderScrollbar(GuiGraphics graphics, int contentHeight,
		int bodyHeight)
	{
		if(contentHeight <= bodyHeight || bodyHeight <= 0)
			return;
		int thumbHeight = Math.max(10, bodyHeight * bodyHeight / contentHeight);
		int thumbY = (int)y + HEADER_HEIGHT + Math.round((float)scrollOffset
			/ (contentHeight - bodyHeight) * (bodyHeight - thumbHeight));
		graphics.fill((int)x + WIDTH - 2, thumbY, (int)x + WIDTH,
			thumbY + thumbHeight, 0xAAFFFFFF);
	}

	private static void drawScaled(GuiGraphics graphics, Font font, String text,
		int x, int y, int color, float scale)
	{
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font, text, 0, 0, color, false);
		graphics.pose().popPose();
	}

	static Row section(String label)
	{
		return new Row(label, RowKind.SECTION, () -> false, () -> {});
	}

	static Row disabled(String label)
	{
		return new Row(label, RowKind.DISABLED, () -> false, () -> {});
	}

	static Row action(String label, BooleanSupplier active, Runnable action)
	{
		return new Row(label, RowKind.ACTION, active, action);
	}

	static Row toggle(String label, BooleanSupplier active, Runnable action)
	{
		return new Row(label, RowKind.TOGGLE, active, action);
	}

	static Row switchRow(String label, BooleanSupplier active, Runnable action)
	{
		return new Row(label, RowKind.SWITCH, active, action);
	}

	record Row(String label, RowKind kind, BooleanSupplier active,
		Runnable action)
	{}

	private enum RowKind
	{
		SECTION,
		ACTION,
		TOGGLE,
		SWITCH,
		DISABLED
	}
}
