package net.wurstclient.clickgui2.epsilon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.component.GuiComponent;
import net.wurstclient.clickgui2.component.VapeGuiContext;
import net.wurstclient.clickgui2.component.ValueComponentFactory;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.clickgui2.PingFangFont;
import net.wurstclient.clickgui2.SettingTreeLayout;
import net.wurstclient.keybinds.PossibleKeybind;
import net.wurstclient.settings.Setting;

/**
 * Epsilon 26.1.2 ModuleButton 的直接移植。
 *
 * <p>模块行（高 19）：左键点击切换启用（启用行以 PRIMARY_CONTAINER 紫色高亮，
 * hover 渐变）；右键展开设置区（下滑动画 EASE_IN_OUT_CUBIC 220ms）；行右侧为
 * keybind 胶囊按钮（左键监听绑定，右键切换 Toggle/Hold）与隐藏按钮。</p>
 */
public final class EpsilonModuleButton
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final float EXPAND_SETTING_HEIGHT = 18F;

	private final Feature feature;
	private final UiTween expandAnim = new UiTween(0,
		EpsilonDropdownTheme.ANIM_EXPAND);
	private final UiTween toggleAnim = new UiTween(0,
		EpsilonDropdownTheme.ANIM_TOGGLE);
	private final UiTween hoverAnim = new UiTween(0,
		EpsilonDropdownTheme.ANIM_HOVER);
	private final UiTween keybindHoverAnim = new UiTween(0,
		EpsilonDropdownTheme.ANIM_HOVER);
	private final List<GuiComponent> settings = new ArrayList<>();
	private final List<Setting> settingList = new ArrayList<>();
	private final VapeGuiContext context;
	private boolean expanded;
	private boolean listeningKeybind;
	private double x;
	private double y;
	private double width;
	private boolean settingsBuilt;

	public EpsilonModuleButton(Feature feature, VapeGuiContext context)
	{
		this.feature = feature;
		this.context = context;
		toggleAnim.snap(feature.isEnabled() ? 1 : 0);
	}

	public Feature getFeature()
	{
		return feature;
	}

	public boolean isExpanded()
	{
		return expanded;
	}

	public float getHeight()
	{
		float expand = expandAnim.update(expanded ? 1 : 0);
		return EpsilonDropdownTheme.MODULE_HEIGHT + settingsHeight() * expand;
	}

	private float settingsHeight()
	{
		float height = EpsilonDropdownTheme.SETTING_GAP
			+ EpsilonDropdownTheme.MODULE_ADDON_INFO_HEIGHT
			+ EpsilonDropdownTheme.SETTING_GAP;
		for(GuiComponent component : settings)
			height += component.getHeight() + EpsilonDropdownTheme.SETTING_GAP;
		return height;
	}

	private void buildSettings()
	{
		if(settingsBuilt)
			return;
		settingsBuilt = true;
		settings.clear();
		settingList.clear();
		for(Setting setting : SettingTreeLayout.flatten(
			feature.getSettings().values()))
		{
			GuiComponent component = ValueComponentFactory.create(setting);
			component.setSuperSoftTheme(true);
			settings.add(component);
			settingList.add(setting);
		}
	}

	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		float toggle = toggleAnim.update(feature.isEnabled() ? 1 : 0);
		boolean headerHovered = mouseX >= x && mouseX < x + width && mouseY >= y
			&& mouseY < y + EpsilonDropdownTheme.MODULE_HEIGHT;
		float hover = hoverAnim.update(headerHovered ? 1 : 0);

		// 行背景：禁用灰 lerp 启用紫
		int bg = EpsilonMd3.mix(EpsilonDropdownTheme.moduleDisabled(hover),
			EpsilonDropdownTheme.moduleEnabled(hover), toggle);
		FlatRenderer.fillRoundedRect(graphics, Math.round((float)x + 2),
			Math.round((float)y), Math.round((float)(x + width) - 2),
			Math.round((float)y + EpsilonDropdownTheme.MODULE_HEIGHT),
			3, bg);
		// 分隔线
		graphics.fill(Math.round((float)x + 3),
			Math.round((float)y + EpsilonDropdownTheme.MODULE_HEIGHT),
			Math.round((float)(x + width) - 3),
			Math.round((float)y + EpsilonDropdownTheme.MODULE_HEIGHT) + 1,
			EpsilonDropdownTheme.moduleDivider());

		// 名称
		int textColor = EpsilonMd3.mix(
			EpsilonDropdownTheme.moduleTextDisabled(hover),
			EpsilonDropdownTheme.moduleTextEnabled(), toggle);
		Font font = Minecraft.getInstance().font;
		String title = PingFangFont.trim(font, feature.getDisplayName(),
			Math.round((float)(width - EpsilonDropdownTheme.KEYBIND_WIDTH
				- 24)));
		drawScaled(graphics, title, Math.round((float)x
			+ EpsilonDropdownTheme.MODULE_PADDING_X),
			Math.round((float)y + (EpsilonDropdownTheme.MODULE_HEIGHT
				- font.lineHeight) / 2F),
			textColor, EpsilonDropdownTheme.MODULE_TEXT_SCALE);

		renderKeybindButton(graphics, mouseX, mouseY, toggle);
		renderHiddenButton(graphics, mouseX, mouseY);

		float expand = expandAnim.get();
		if(expand > 0.01F)
		{
			float settingY = (float)y + EpsilonDropdownTheme.MODULE_HEIGHT
				+ EpsilonDropdownTheme.SETTING_GAP;
			drawScaled(graphics, "wurstpenguin", Math.round((float)x
				+ EpsilonDropdownTheme.SETTING_INDENT
				+ EpsilonDropdownTheme.SETTING_PADDING_X),
				Math.round(settingY), EpsilonDropdownTheme.moduleAddonInfoText(),
				EpsilonDropdownTheme.MODULE_ADDON_INFO_TEXT_SCALE);
			settingY += EpsilonDropdownTheme.MODULE_ADDON_INFO_HEIGHT
				+ EpsilonDropdownTheme.SETTING_GAP;
			buildSettings();
			for(GuiComponent component : settings)
			{
				component.setX(x + EpsilonDropdownTheme.SETTING_INDENT);
				component.setY(settingY);
				component.setWidth(width - EpsilonDropdownTheme.SETTING_INDENT
					* 2F);
				if(expand > 0.5F)
					component.render(graphics, mouseX, mouseY, partialTicks);
				settingY += component.getHeight()
					+ EpsilonDropdownTheme.SETTING_GAP;
			}
		}
	}

	private void renderKeybindButton(GuiGraphics graphics, int mouseX,
		int mouseY, float toggle)
	{
		float btnW = EpsilonDropdownTheme.KEYBIND_WIDTH;
		float btnH = EpsilonDropdownTheme.KEYBIND_HEIGHT;
		float btnX = (float)(x + width) - EpsilonDropdownTheme.MODULE_PADDING_X
			- btnW;
		float btnY = (float)y + (EpsilonDropdownTheme.MODULE_HEIGHT - btnH)
			* 0.5F;
		boolean btnHovered = mouseX >= btnX && mouseX < btnX + btnW
			&& mouseY >= btnY && mouseY < btnY + btnH;
		float kbHover = keybindHoverAnim.update(btnHovered ? 1 : 0);

		String keyText = listeningKeybind ? "..." : compactKeybind();
		float textScale = keyText.length() >= 3 ? 0.46F : 0.52F;
		Font font = Minecraft.getInstance().font;

		int surface;
		int outline;
		int text = EpsilonDropdownTheme.keybindText(true);
		if(listeningKeybind)
		{
			surface = EpsilonDropdownTheme.keybindSurface(true);
			outline = EpsilonMd3.withAlpha(EpsilonMd3.PRIMARY, 200);
		}else
		{
			int idleSurface = EpsilonMd3.SECONDARY_CONTAINER;
			int activeSurface = EpsilonMd3.PRIMARY;
			surface = EpsilonMd3.mix(idleSurface, activeSurface, toggle);
			surface = EpsilonMd3.mix(surface, EpsilonMd3.TEXT_PRIMARY,
				kbHover * 0.08F);
			outline = EpsilonMd3.mix(
				EpsilonMd3.withAlpha(EpsilonMd3.SECONDARY, 220),
				EpsilonMd3.withAlpha(EpsilonMd3.ON_PRIMARY_CONTAINER, 235),
				toggle);
			outline = EpsilonMd3.mix(outline,
				EpsilonMd3.withAlpha(EpsilonMd3.TEXT_PRIMARY, 245),
				kbHover * 0.5F);
		}

		FlatRenderer.fillRoundedRect(graphics, Math.round(btnX),
			Math.round(btnY), Math.round(btnX + btnW), Math.round(btnY + btnH),
			Math.round(EpsilonDropdownTheme.KEYBIND_RADIUS), surface);
		FlatRenderer.drawRoundedOutline(graphics, Math.round(btnX),
			Math.round(btnY), Math.round(btnX + btnW), Math.round(btnY + btnH),
			Math.round(EpsilonDropdownTheme.KEYBIND_RADIUS), outline);

		int textW = font.width(keyText);
		drawScaled(graphics, keyText,
			Math.round(btnX + (btnW - textW) * 0.5F),
			Math.round(btnY + (btnH - font.lineHeight) * 0.5F - 0.5F),
			text, textScale);
	}

	private void renderHiddenButton(GuiGraphics graphics, int mouseX,
		int mouseY)
	{
		float btnW = 18F;
		float btnH = EpsilonDropdownTheme.KEYBIND_HEIGHT;
		float btnX = (float)(x + width) - EpsilonDropdownTheme.MODULE_PADDING_X
			- EpsilonDropdownTheme.KEYBIND_WIDTH - 4F - btnW;
		float btnY = (float)y + (EpsilonDropdownTheme.MODULE_HEIGHT - btnH)
			* 0.5F;
		boolean hovered = mouseX >= btnX && mouseX < btnX + btnW
			&& mouseY >= btnY && mouseY < btnY + btnH;
		if(!context.isHidden(feature))
			FlatRenderer.fillRoundedRect(graphics, Math.round(btnX),
				Math.round(btnY), Math.round(btnX + btnW), Math.round(btnY + btnH),
				Math.round(EpsilonDropdownTheme.KEYBIND_RADIUS), EpsilonMd3.mix(
					EpsilonMd3.SECONDARY_CONTAINER, EpsilonMd3.SECONDARY,
					hovered ? 0.12F : 0));
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(listeningKeybind)
		{
			listeningKeybind = false;
			return true;
		}
		if(mouseX >= x && mouseX < x + width && mouseY >= y
			&& mouseY < y + EpsilonDropdownTheme.MODULE_HEIGHT)
		{
			if(isHiddenButtonHovered(mouseX, mouseY))
			{
				context.toggleHidden(feature);
				return true;
			}
			if(isKeybindButtonHovered(mouseX, mouseY))
			{
				if(button == 0)
				{
					context.beginBinding(feature);
					return true;
				}
				return true;
			}
			if(button == 0)
			{
				feature.doPrimaryAction();
				return true;
			}
			if(button == 1)
			{
				expanded = !expanded;
				return true;
			}
		}
		if(expanded && expandAnim.get() > 0.5F)
		{
			float settingY = (float)y + EpsilonDropdownTheme.MODULE_HEIGHT
				+ EpsilonDropdownTheme.SETTING_GAP
				+ EpsilonDropdownTheme.MODULE_ADDON_INFO_HEIGHT
				+ EpsilonDropdownTheme.SETTING_GAP;
			buildSettings();
			for(GuiComponent component : settings)
			{
				if(mouseX >= component.getX() && mouseX < component.getX()
					+ component.getWidth() && mouseY >= settingY
					&& mouseY < settingY + component.getHeight())
				{
					component.setY(settingY);
					return component.mouseClicked(mouseX, mouseY, button);
				}
				settingY += component.getHeight()
					+ EpsilonDropdownTheme.SETTING_GAP;
			}
		}
		return false;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(expanded)
			for(GuiComponent component : settings)
				if(component.mouseReleased(mouseX, mouseY, button))
					return true;
		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button)
	{
		if(expanded)
			for(GuiComponent component : settings)
				if(component.mouseDragged(mouseX, mouseY, button))
					return true;
		return false;
	}

	public void tick()
	{
		for(GuiComponent component : settings)
			component.tick();
	}

	public boolean hasListeningKeybind()
	{
		return context.isBinding(feature) || listeningKeybind;
	}

	private boolean isKeybindButtonHovered(double mouseX, double mouseY)
	{
		float btnX = (float)(x + width) - EpsilonDropdownTheme.MODULE_PADDING_X
			- EpsilonDropdownTheme.KEYBIND_WIDTH;
		float btnY = (float)y + (EpsilonDropdownTheme.MODULE_HEIGHT
			- EpsilonDropdownTheme.KEYBIND_HEIGHT) * 0.5F;
		return mouseX >= btnX && mouseX < btnX + EpsilonDropdownTheme.KEYBIND_WIDTH
			&& mouseY >= btnY
			&& mouseY < btnY + EpsilonDropdownTheme.KEYBIND_HEIGHT;
	}

	private boolean isHiddenButtonHovered(double mouseX, double mouseY)
	{
		float btnX = (float)(x + width) - EpsilonDropdownTheme.MODULE_PADDING_X
			- EpsilonDropdownTheme.KEYBIND_WIDTH - 4F - 18F;
		float btnY = (float)y + (EpsilonDropdownTheme.MODULE_HEIGHT
			- EpsilonDropdownTheme.KEYBIND_HEIGHT) * 0.5F;
		return mouseX >= btnX && mouseX < btnX + 18 && mouseY >= btnY
			&& mouseY < btnY + EpsilonDropdownTheme.KEYBIND_HEIGHT;
	}

	public void setPosition(double x, double y)
	{
		this.x = x;
		this.y = y;
	}

	public void setWidth(double width)
	{
		this.width = width;
	}

	private String compactKeybind()
	{
		String command = feature.getPossibleKeybinds().stream().findFirst()
			.map(PossibleKeybind::getCommand).orElse(null);
		if(command == null)
			return "NONE";
		String key = WURST.getKeybinds().getKeyForCommand(command);
		if(key == null)
			return "NONE";
		String label;
		try
		{
			label = InputConstants.getKey(key).getDisplayName().getString()
				.trim();
		}catch(IllegalArgumentException exception)
		{
			return "?";
		}
		if(label.isEmpty())
			return "?";
		String compact = label.replaceAll("[^A-Za-z0-9]", "")
			.toUpperCase(Locale.ROOT);
		if(!compact.isEmpty())
			return compact.length() > 3 ? compact.substring(0, 3) : compact;
		return label.length() > 3 ? label.substring(0, 3) : label;
	}

	private void drawScaled(GuiGraphics graphics, String text, int x, int y,
		int color, float scale)
	{
		// 默认 CozyUI 位图字体，9px 整数渲染
		graphics.drawString(Minecraft.getInstance().font,
			net.wurstclient.clickgui2.PingFangFont.text(text), x, y, color,
			false);
	}

	private String trimToWidth(String text, float scale, float maxWidth)
	{
		Font font = Minecraft.getInstance().font;
		if(font.width(text) <= maxWidth)
			return text;
		int low = 0;
		int high = text.length();
		while(low < high)
		{
			int middle = (low + high + 1) >>> 1;
			if(font.width(text.substring(0, middle)) <= maxWidth)
				low = middle;
			else
				high = middle - 1;
		}
		return text.substring(0, low);
	}

	/** 就近引用 MD3 颜色常量。 */
	private static final class EpsilonMd3
	{
		static final int SURFACE_CONTAINER =
			net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.SURFACE_CONTAINER;
		static final int SURFACE_CONTAINER_HIGH =
			net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.SURFACE_CONTAINER_HIGH;
		static final int PRIMARY =
			net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.PRIMARY;
		static final int PRIMARY_CONTAINER =
			net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.PRIMARY_CONTAINER;
		static final int ON_PRIMARY_CONTAINER =
			net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.ON_PRIMARY_CONTAINER;
		static final int SECONDARY =
			net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.SECONDARY;
		static final int SECONDARY_CONTAINER =
			net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.SECONDARY_CONTAINER;
		static final int ON_SECONDARY_CONTAINER =
			net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.ON_SECONDARY_CONTAINER;
		static final int TEXT_PRIMARY =
			net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.TEXT_PRIMARY;
		static final int TEXT_MUTED =
			net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.TEXT_MUTED;

		static int mix(int from, int to, float progress)
		{
			return net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.mix(from,
				to, progress);
		}

		static int withAlpha(int color, int alpha)
		{
			return net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme.withAlpha(
				color, alpha);
		}
	}
}
