package net.wurstclient.clickgui2;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.settings.Setting;

/** Inline values belonging to one module card; the screen owns scrolling. */
final class NavigatorSettingsPanel extends Window
{
	private final List<Setting> roots;
	private final Map<Setting, Component> components = new IdentityHashMap<>();
	private List<Setting> visible = List.of();
	private int bodyX;
	private int bodyTop;
	private int viewportTop;
	private int visibleBottom;

	NavigatorSettingsPanel(Feature feature)
	{
		super("");
		roots = new ArrayList<>(feature.getSettings().values());
		setFixedWidth(true);
		setMinimizable(false);
		setPinnable(false);
		setClosable(false);
		prepareForRender();
	}

	@Override
	public void prepareForRender()
	{
		List<Setting> next = SettingTreeLayout.flatten(roots);
		if(visible.equals(next))
			return;
		for(Setting setting : visible)
			if(!next.contains(setting) && components.get(setting) instanceof NavigatorValueComponent value)
				value.blur();
		WurstClient.INSTANCE.getGui().closePopupsOwnedBy(this);
		clear();
		for(Setting setting : next)
			add(components.computeIfAbsent(setting, NavigatorValueComponent::create));
		visible = next;
	}

	int naturalHeight()
	{
		prepareForRender();
		int height = 0;
		for(int i = 0; i < countChildren(); i++)
			height += getChild(i).getHeight();
		return height;
	}

	void layout(int x, int y, int width, int top, int bottom, int cardBottom)
	{
		prepareForRender();
		bodyX = x;
		bodyTop = y;
		viewportTop = top;
		visibleBottom = Math.min(bottom, cardBottom);
		// Popup owners stay anchored to the viewport. Child positions include
		// the card's animated scroll offset, avoiding Window position clamping.
		setX(x);
		setY(top - 13);
		setFixedWidth(false);
		setWidth(width);
		setFixedWidth(true);
		setHeight(bottom - top + 13);
		int offset = y - top;
		for(int i = 0; i < countChildren(); i++)
		{
			Component child = getChild(i);
			int indent = visible.get(i).getDepth() * 10;
			child.setX(indent);
			child.setY(offset);
			child.setWidth(Math.max(12, width - indent));
			if(child instanceof NavigatorValueComponent value)
				value.retainFocusWithin(viewportTop, Math.max(bodyTop, viewportTop), visibleBottom);
			offset += child.getHeight();
		}
	}

	void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
	{
		if(visibleBottom <= Math.max(viewportTop, bodyTop))
			return;
		NanoVGUI.pushScissor(bodyX, Math.max(viewportTop, bodyTop), getWidth(),
			visibleBottom - Math.max(viewportTop, bodyTop));
		NanoVGUI.push();
		NanoVGUI.translate(bodyX, viewportTop);
		for(int i = 0; i < countChildren(); i++)
		{
			Component child = getChild(i);
			int y = viewportTop + child.getY();
			if(y + child.getHeight() > viewportTop && y < visibleBottom)
				child.render(graphics, mouseX - bodyX, mouseY - viewportTop, partialTicks);
		}
		NanoVGUI.pop();
		NanoVGUI.popScissor();
	}


	boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(mouseX < bodyX || mouseX >= bodyX + getWidth()
			|| mouseY < Math.max(bodyTop, viewportTop) || mouseY >= visibleBottom)
			return false;
		double x = mouseX - bodyX;
		double y = mouseY - viewportTop;
		for(int i = countChildren() - 1; i >= 0; i--)
		{
			Component child = getChild(i);
			if(x < child.getX() || x >= child.getX() + child.getWidth()
				|| y < child.getY() || y >= child.getY() + child.getHeight())
				continue;
			for(Component component : components.values())
				if(component != child && component instanceof NavigatorValueComponent value)
					value.blur();
			child.handleMouseClick(x, y, button);
			return true;
		}
		return false;
	}

	boolean keyPressed(int key, int scan, int modifiers)
	{
		for(Component child : components.values())
			if(child instanceof NavigatorValueComponent value && value.isEditing())
				return value.keyPressed(key, scan, modifiers);
		return false;
	}

	boolean charTyped(char character, int modifiers)
	{
		for(Component child : components.values())
			if(child instanceof NavigatorValueComponent value && value.isEditing())
				return value.charTyped(character, modifiers);
		return false;
	}

	boolean isEditing()
	{
		return components.values().stream().anyMatch(child ->
			child instanceof NavigatorValueComponent value && value.isEditing());
	}

	void release()
	{
		for(Component child : components.values())
			if(child instanceof NavigatorValueComponent value)
				value.release();
	}

	void blur()
	{
		for(Component child : components.values())
			if(child instanceof NavigatorValueComponent value)
				value.blur();
		WurstClient.INSTANCE.getGui().closePopupsOwnedBy(this);
	}
}
