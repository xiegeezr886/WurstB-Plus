/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.settings.CheckboxSetting;

/**
 * 覆盖 {@code HudElement} 的「逐元素设置」注册表。
 *
 * <p>
 * <b>不测的部分</b>：设置的持久化（{@code HudManager.loadLayout()} /
 * {@code saveLayout()} 里的 {@code settings} 键）与
 * {@code applyElementSettings()} 的调用时序，都依赖 {@code WurstClient} 实例与
 * 真实存档目录，无头环境下跑不起来。这里只钉注册表本身的契约。
 */
final class HudElementSettingsTest
{
	/** 最小可用元素：只需要能拿到 id 和设置表。 */
	private static final class TestElement extends HudElement
	{
		TestElement()
		{
			super("test_element", "Test Element");
		}

		@Override
		public int getWidth()
		{
			return 0;
		}

		@Override
		public int getHeight()
		{
			return 0;
		}

		@Override
		public void render(GuiGraphics graphics, int x, int y,
			float partialTicks)
		{}
	}

	@Test
	void elementsHaveNoSettingsUntilTheyDeclareOne()
	{
		assertTrue(new TestElement().getSettings().isEmpty());
	}

	/**
	 * 键是小写名字，和 {@code Feature.addSetting} 一致。
	 */
	@Test
	void settingsAreKeyedByLowercasedName()
	{
		TestElement element = new TestElement();
		CheckboxSetting setting =
			new CheckboxSetting("Show Equipment", true);
		element.addSetting(setting);

		assertEquals(1, element.getSettings().size());
		assertTrue(element.getSettings().containsKey("show equipment"));
		assertEquals(setting, element.getSettings().get("show equipment"));
	}

	/**
	 * 大小写不同的同名设置必须当场报错，而不是后者悄悄顶掉前者。这是这个注册表
	 * 存在的意义所在——`Show Equipment` 和 `Show equipment` 同时存在时，
	 * 用户看到两行、改一行没反应，是最难查的那类 bug。
	 */
	@Test
	void namesThatCollideAfterLowercasingAreRejected()
	{
		TestElement element = new TestElement();
		element.addSetting(new CheckboxSetting("Show Equipment", true));

		assertThrows(IllegalArgumentException.class,
			() -> element.addSetting(
				new CheckboxSetting("Show equipment", false)));

		// 报错之后原来的那个还得是原来的那个
		assertEquals(1, element.getSettings().size());
		assertTrue(((CheckboxSetting)element.getSettings()
			.get("show equipment")).isChecked());
	}

	@Test
	void exactlyTheSameNameIsAlsoRejected()
	{
		TestElement element = new TestElement();
		element.addSetting(new CheckboxSetting("Equipment", true));

		assertThrows(IllegalArgumentException.class,
			() -> element.addSetting(new CheckboxSetting("Equipment", true)));
	}

	@Test
	void theSettingsMapIsNotModifiableFromOutside()
	{
		TestElement element = new TestElement();
		element.addSetting(new CheckboxSetting("Equipment", true));

		assertThrows(UnsupportedOperationException.class, () -> element
			.getSettings().put("x", new CheckboxSetting("x", true)));
	}

	/**
	 * 没有子项的勾选框序列化成一个纯布尔量（{@code CheckboxSetting.toJson()}），
	 * 所以 hud-layout.json 里每个设置就是一行 {@code "Equipment": true}，
	 * 手改得动。这里钉住这个形状，因为它就是持久化格式。
	 */
	@Test
	void aPlainCheckboxSerializesAsABareBoolean()
	{
		assertTrue(new CheckboxSetting("A", true).toJson().getAsBoolean());
		assertFalse(new CheckboxSetting("B", false).toJson().getAsBoolean());
	}
}
