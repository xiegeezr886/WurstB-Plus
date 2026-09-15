package net.wurstclient.hud2;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.settings.Setting;

public abstract class HudElement
{
	private final String id;
	private final String name;
	private final Map<String, Setting> settings = new LinkedHashMap<>();

	protected HudElement(String id, String name)
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

	/**
	 * 注册一个「逐元素设置」。
	 *
	 * <p>
	 * 键的算法与 {@link net.wurstclient.Feature#addSetting} 一致（名字转小写），
	 * 这样「重复即报错」的语义在整个工程里是同一条。差别只用
	 * {@link Locale#ROOT} 而不是默认区域——默认区域在土耳其语环境下会把
	 * {@code I} 折成无点的 {@code ı}，键就不是你以为的那个了。
	 */
	protected final void addSetting(Setting setting)
	{
		String key = setting.getName().toLowerCase(Locale.ROOT);
		if(settings.containsKey(key))
			throw new IllegalArgumentException(
				"Duplicate HUD element setting: " + id + " " + key);

		settings.put(key, setting);
	}

	public final Map<String, Setting> getSettings()
	{
		return Collections.unmodifiableMap(settings);
	}

	public boolean isSingleton()
	{
		return true;
	}

	public boolean renderEditorPreview()
	{
		return false;
	}

	public void onEnable(HudManager manager) {}

	public void onDisable(HudManager manager) {}

	public abstract int getWidth();

	public abstract int getHeight();

	public abstract void render(GuiGraphics graphics, int x, int y,
		float partialTicks);

	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_TOP, 0, 0);
	}
}
