package net.wurstclient.clickgui2.component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FeatureMenuSupport;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.clickgui2.SettingTreeLayout;
import net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiMotion;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.settings.Setting;

public final class ModuleCardComponent extends GuiComponent
{
	private static final int ROW_HEIGHT = 20;
	private static final int SUPERSOFT_ROW_HEIGHT = 14;
	private static final float SUPERSOFT_MODULE_TEXT_SCALE = 0.78F;
	private static final float SUPERSOFT_SETTING_TEXT_SCALE = 0.67F;
	private final Feature feature;
	private final int accentColor;
	private final VapeGuiContext context;
	private final List<GuiComponent> valueComponents = new ArrayList<>();
	private final List<Setting> valueSettings = new ArrayList<>();
	private final UiMotion hoverMotion = new UiMotion(0, 380, 0.9F);
	private final UiMotion enabledMotion = new UiMotion(0, 300, 0.85F);
	private final UiMotion expansionMotion = new UiMotion(0, 280, 0.82F);
	private final UiMotion contentHeightMotion = new UiMotion(0, 340, 0.9F);
	private final UiTween fontWeightMotion;
	private final UiTween arrowMotion = new UiTween(0, 200);
	private final Map<Setting, UiTween> settingArrowMotions =
		new IdentityHashMap<>();
	private final UiTween clickMotion = new UiTween(0, 250);
	private final UiTween keybindHoverMotion = new UiTween(0, 150);
	private float expansionProgress;
	private boolean expanded;

	public ModuleCardComponent(Feature feature, int accentColor)
	{
		this(feature, accentColor, null);
	}

	public ModuleCardComponent(Feature feature, int accentColor,
		VapeGuiContext context)
	{
		this.feature = feature;
		this.accentColor = accentColor;
		this.context = context;
		fontWeightMotion = new UiTween(feature.isEnabled() ? 1 : 0, 300);
		height = ROW_HEIGHT;
	}

	public Feature getFeature()
	{
		return feature;
	}

	public boolean isExpanded()
	{
		return expanded;
	}

	public void setExpanded(boolean expanded)
	{
		if(this.expanded == expanded)
			return;
		this.expanded = expanded;
		if(expanded && valueComponents.isEmpty())
		{
			buildValueComponents();
			contentHeightMotion.snap((float)getFullContentHeight(
				context != null && context.usesSuperSoftTheme()));
		}
		height = getHeight();
	}

	@Override
	public double getHeight()
	{
		if(!expanded && expansionProgress <= 0.001F)
			return rowHeight();
		double contentHeight = 0;
		if(context != null && context.usesSuperSoftTheme())
			contentHeight += 15;
		for(GuiComponent component : valueComponents)
			contentHeight += component.getHeight();
		return rowHeight() + contentHeightMotion.update((float)contentHeight)
			* expansionProgress;
	}

	private void buildValueComponents()
	{
		valueComponents.clear();
		valueSettings.clear();
		List<Setting> visibleSettings = SettingTreeLayout.flatten(
			feature.getSettings().values());
		settingArrowMotions.keySet().retainAll(visibleSettings);
		for(Setting setting : visibleSettings)
		{
			GuiComponent component = ValueComponentFactory.create(setting);
			component.setSuperSoftTheme(context != null
				&& context.usesSuperSoftTheme());
			valueComponents.add(component);
			valueSettings.add(setting);
		}
	}

	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		boolean enabled = feature.isEnabled();
		boolean editingHidden = context != null
			&& context.isEditingHiddenModules();
		boolean hidden = context != null && context.isHidden(feature);
		boolean superSoft = context != null && context.usesSuperSoftTheme();
		float hoverProgress = hoverMotion.update(hovered ? 1 : 0);
		float enabledProgress = enabledMotion.update(enabled ? 1 : 0);
		int idle = superSoft ? SuperSoftTheme.HEADER : VapePalette.FRAME;
		int hover = superSoft ? SuperSoftTheme.MODULE_HOVER
			: VapePalette.ROW_HOVER;
		int background = SuperSoftTheme.mix(idle, accentColor, enabledProgress);
		background = SuperSoftTheme.mix(background, hover, hoverProgress);
		graphics.fill((int)x, (int)y, (int)(x + getWidth()),
			(int)y + rowHeight(), background);
		float click = clickMotion.update(0);
		if(click > 0.001F)
			graphics.fill((int)x, (int)y, (int)(x + getWidth()),
				(int)y + rowHeight(), withAlpha(EpsilonMd3Theme.TEXT_PRIMARY, click * 0.35F));

		Font font = Minecraft.getInstance().font;
		int textColor = superSoft ? SuperSoftTheme.TEXT
			: enabled || hovered || expanded ? VapePalette.TEXT_HOVER
				: VapePalette.TEXT;
		int contentOffset = editingHidden ? 20 : 0;
		int reservedWidth = 24;
		if(expanded && context != null && !superSoft)
			reservedWidth += 14;
		String displayName = font.plainSubstrByWidth(feature.getDisplayName(),
			Math.max(8, (int)getWidth() - reservedWidth - 6 - contentOffset));
		if(editingHidden)
			renderVisibilityToggle(graphics, hidden);
		int textY = (int)y + Math.max(2, (rowHeight() - 7) / 2);
		if(superSoft)
		{
			float weight = fontWeightMotion.update(enabled ? 1 : 0);
			int nameColor = hidden ? VapePalette.TEXT_HIDDEN : textColor;
			if(weight < 0.999F)
				drawScaled(graphics, Component.literal(displayName),
					(int)x + 8 + contentOffset, textY,
					withAlpha(nameColor, 1 - weight),
					SUPERSOFT_MODULE_TEXT_SCALE);
			if(weight > 0.001F)
				drawScaled(graphics,
					Component.literal(displayName).withStyle(ChatFormatting.BOLD),
					(int)x + 8 + contentOffset, textY,
					withAlpha(nameColor, weight), SUPERSOFT_MODULE_TEXT_SCALE);
		}
		else
			graphics.drawString(font, Component.literal(displayName),
				(int)x + 8 + contentOffset,
				textY, hidden ? VapePalette.TEXT_HIDDEN : textColor, false);

		if(superSoft)
		{
			if(!feature.getSettings().isEmpty())
				GuiIcon.CHEVRON.drawRotated(graphics,
					(int)(x + getWidth()) - 12, (int)y + 4, 8,
					SuperSoftTheme.TEXT_SECONDARY,
					arrowMotion.update(expanded ? 90 : 0));
		}
		else
			renderSettingsDots(graphics, enabled);
		if(expanded && context != null && !superSoft)
			GuiIcon.PIN.draw(graphics, (int)(x + getWidth()) - 28,
				(int)y + 6, 8, context.isFavorite(feature)
					? accentColor : VapePalette.TEXT);

		if(!expanded && expansionProgress <= 0.001F)
			return;
		double settingY = y + rowHeight();
		double fullContentHeight = getFullContentHeight(superSoft);
		if(context == null)
			graphics.enableScissor((int)x, (int)(y + rowHeight()),
				(int)(x + getWidth()), (int)(y + rowHeight()
					+ fullContentHeight * expansionProgress));
		else
			context.enableScissor(graphics, x, y + rowHeight(),
				x + getWidth(), y + rowHeight()
					+ fullContentHeight * expansionProgress);
		if(superSoft)
		{
			settingY += 1;
			renderKeybindRow(graphics, settingY, mouseX, mouseY);
			settingY += 14;
		}
		for(int index = 0; index < valueComponents.size(); index++)
		{
			GuiComponent component = valueComponents.get(index);
			Setting setting = valueSettings.get(index);
			int indent = superSoft ? setting.getDepth() * 8 : 0;
			component.setX(superSoft ? x + 8 + indent : x);
			component.setY(settingY);
			component.setWidth(
				superSoft ? getWidth() - 12 - indent : getWidth());
			graphics.fill((int)x, (int)component.getY(),
				(int)(x + getWidth()),
				(int)(component.getY() + component.getHeight()),
				superSoft ? SuperSoftTheme.SETTING : VapePalette.FRAME);
			if(superSoft && setting.hasChildren())
				GuiIcon.CHEVRON.drawRotated(graphics, (int)x + 1 + indent,
					(int)component.getY() + 4, 7,
					SuperSoftTheme.TEXT_SECONDARY,
					settingArrowMotions.computeIfAbsent(setting,
						ignored -> new UiTween(setting.isExpanded() ? 90 : 0, 180))
						.update(setting.isExpanded() ? 90 : 0));
			component.render(graphics, mouseX, mouseY, partialTicks);
			settingY += component.getHeight();
		}
		graphics.disableScissor();
	}

	private double getFullContentHeight(boolean superSoft)
	{
		double result = superSoft ? 15 : 0;
		for(GuiComponent component : valueComponents)
			result += component.getHeight();
		return result;
	}

	private void renderKeybindRow(GuiGraphics graphics, double rowY,
		int mouseX, int mouseY)
	{
		boolean rowHovered = mouseX >= x && mouseX < x + getWidth()
			&& mouseY >= rowY && mouseY < rowY + 14;
		float hover = keybindHoverMotion.update(rowHovered ? 1 : 0);
		graphics.fill((int)x, (int)rowY, (int)(x + getWidth()),
			(int)rowY + 14, SuperSoftTheme.mix(SuperSoftTheme.SETTING,
				SuperSoftTheme.SETTING_HOVER, hover));
		Font font = Minecraft.getInstance().font;
		drawScaled(graphics, Component.literal("KeyBind"), (int)x + 4,
			(int)rowY + 4, SuperSoftTheme.TEXT_SECONDARY,
			SUPERSOFT_SETTING_TEXT_SCALE);
		String key = getKeyLabel();
		int keyWidth = Math.round(font.width(key) * SUPERSOFT_SETTING_TEXT_SCALE);
		drawScaled(graphics, Component.literal(key),
			(int)(x + getWidth()) - keyWidth - 5, (int)rowY + 4,
			context.isBinding(feature) ? EpsilonMd3Theme.SECONDARY : accentColor,
			SUPERSOFT_SETTING_TEXT_SCALE);
	}

	private String getKeyLabel()
	{
		if(context != null && context.isBinding(feature))
			return "Press a key";
		String command = feature.getPossibleKeybinds().stream().findFirst()
			.map(net.wurstclient.keybinds.PossibleKeybind::getCommand)
			.orElse(null);
		if(command == null)
			return "None";
		String key = WurstClient.INSTANCE.getKeybinds().getKeyForCommand(command);
		if(key == null)
			return "None";
		try
		{
			return InputConstants.getKey(key).getDisplayName().getString();
		}catch(IllegalArgumentException exception)
		{
			return "None";
		}
	}

	private void renderSettingsDots(GuiGraphics graphics, boolean enabled)
	{
		int dotX = (int)(x + getWidth()) - 10;
		int dotY = (int)y + 6;
		int color = enabled ? VapePalette.TEXT_HOVER : VapePalette.TEXT;
		for(int offset = 0; offset < 3; offset++)
			graphics.fill(dotX, dotY + offset * 3, dotX + 2,
				dotY + offset * 3 + 2, color);
	}

	private void renderVisibilityToggle(GuiGraphics graphics, boolean hidden)
	{
		int left = (int)x + 7;
		int top = (int)y + 7;
		graphics.fill(left, top, left + 6, top + 6,
			hidden ? EpsilonMd3Theme.SURFACE_CONTAINER_HIGHEST : VapePalette.ACCENT);
		graphics.fill(left + 1, top + 1, left + 5, top + 5,
			hidden ? VapePalette.FRAME : VapePalette.ACCENT);
	}

	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button == 0 && mouseY < y + rowHeight())
			clickMotion.snap(1);
		if(expanded && mouseY >= y + rowHeight())
		{
			if(context != null && context.usesSuperSoftTheme()
				&& mouseY < y + rowHeight() + 15)
			{
				if(button == 0)
					context.beginBinding(feature);
				return true;
			}
			int settingIndex = settingIndexAt(mouseY);
			if(context != null && context.usesSuperSoftTheme()
				&& settingIndex >= 0)
			{
				Setting setting = valueSettings.get(settingIndex);
				double arrowRight = x + 13 + setting.getDepth() * 8;
				if(setting.hasChildren() && button == 0 && mouseX < arrowRight)
				{
					setting.setExpanded(!setting.isExpanded());
					buildValueComponents();
					return true;
				}
			}
			GuiComponent component = settingAt(mouseY);
			return component != null
				&& component.mouseClicked(mouseX, mouseY, button);
		}
		if(button == 0 && context != null
			&& context.isEditingHiddenModules() && mouseX < x + 20)
		{
			context.toggleHidden(feature);
			return true;
		}
		if(button == 0 && expanded && context != null
			&& mouseX >= x + getWidth() - 34
			&& mouseX < x + getWidth() - 20)
		{
			context.toggleFavorite(feature);
			return true;
		}
		if(button == 0 && !feature.getSettings().isEmpty()
			&& mouseX >= x + getWidth() - 20)
		{
			setExpanded(!expanded);
			return true;
		}
		if(button == 1 && !feature.getSettings().isEmpty())
		{
			setExpanded(!expanded);
			return true;
		}
		if(button != 0)
			return false;
		return FeatureMenuSupport.runPrimaryAction(feature);
	}

	@Override
	protected boolean onRelease(double mouseX, double mouseY, int button)
	{
		GuiComponent component = settingAt(mouseY);
		return component != null
			&& component.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	protected boolean onDrag(double mouseX, double mouseY, int button)
	{
		GuiComponent component = settingAt(mouseY);
		return component != null
			&& component.mouseDragged(mouseX, mouseY, button);
	}

	private GuiComponent settingAt(double mouseY)
	{
		if(!expanded || mouseY < y + rowHeight())
			return null;
		double componentY = y + rowHeight();
		if(context != null && context.usesSuperSoftTheme())
			componentY += 15;
		for(GuiComponent component : valueComponents)
		{
			if(mouseY >= componentY
				&& mouseY < componentY + component.getHeight())
				return component;
			componentY += component.getHeight();
		}
		return null;
	}

	private int settingIndexAt(double mouseY)
	{
		if(!expanded || mouseY < y + rowHeight())
			return -1;
		double componentY = y + rowHeight();
		if(context != null && context.usesSuperSoftTheme())
			componentY += 15;
		for(int index = 0; index < valueComponents.size(); index++)
		{
			GuiComponent component = valueComponents.get(index);
			if(mouseY >= componentY
				&& mouseY < componentY + component.getHeight())
				return index;
			componentY += component.getHeight();
		}
		return -1;
	}

	@Override
	public void tick()
	{
		if(expanded && visibleSettingsChanged())
			buildValueComponents();
		expansionProgress = expansionMotion.update(expanded ? 1 : 0);
		for(GuiComponent component : valueComponents)
			component.tick();
	}

	private boolean visibleSettingsChanged()
	{
		return !valueSettings.equals(
			SettingTreeLayout.flatten(feature.getSettings().values()));
	}

	private int rowHeight()
	{
		return context != null && context.usesSuperSoftTheme()
			? SUPERSOFT_ROW_HEIGHT : ROW_HEIGHT;
	}

	private void drawScaled(GuiGraphics graphics, Component text, int x, int y,
		int color, float scale)
	{
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color,
			false);
		graphics.pose().popPose();
	}

	private static int withAlpha(int color, float opacity)
	{
		int alpha = Math.round((color >>> 24) * Math.max(0, Math.min(1, opacity)));
		return color & 0x00FFFFFF | alpha << 24;
	}
}
