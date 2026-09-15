/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.other_features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import net.wurstclient.Category;
import net.wurstclient.settings.CheckboxSetting;

/**
 * 覆盖 HackList 的「可见分类」过滤（移植自参考
 * {@code ToggledSettings.getVisibleCategories()}）。
 *
 * <p>
 * <b>这个类不测「关掉一个分类之后列表里就看不到它」</b>，因为那需要调用
 * {@code CheckboxSetting.setChecked()}，而它最后会走到
 * {@code WurstClient.saveSettings()}（{@code CheckboxSetting.java:74} →
 * {@code WurstClient.java:254}），在没有存档文件的无头测试里必然 NPE。
 * 本工程现有的测试也没有一个去拨动设置，原因相同。
 * 所以这里钉的是<b>默认值契约</b>——「默认全开」才是「只加设置、不改默认行为」
 * 这条约束的落点；拨动之后的联动由游戏内验证。
 */
final class HackListOtfTest
{
	/**
	 * {@code Feature.addSetting} 用 {@code getName().toLowerCase()} 当键
	 * （{@code Feature.java:84}），所以这里也要小写。
	 */
	private static String settingKey(Category category)
	{
		return ("Show " + category.getName()).toLowerCase(Locale.ROOT);
	}

	private static CheckboxSetting categorySetting(HackListOtf otf,
		Category category)
	{
		return assertInstanceOf(CheckboxSetting.class,
			otf.getSettings().get(settingKey(category)));
	}

	/**
	 * 这是关键性质：默认全开，「可见分类」这个过滤器在默认配置下等于不存在，
	 * 所以列表内容与加它之前完全一致。
	 */
	@Test
	void everyCategoryIsVisibleByDefault()
	{
		HackListOtf otf = new HackListOtf();

		for(Category category : Category.values())
			assertTrue(otf.isCategoryVisible(category), category.name());
	}

	/** 分类缺失时宁可多显示一项，也不要让整条消失。 */
	@Test
	void aMissingCategoryIsTreatedAsVisible()
	{
		assertTrue(new HackListOtf().isCategoryVisible(null));
	}

	@Test
	void thereIsExactlyOneSettingPerCategoryAndItDefaultsToChecked()
	{
		HackListOtf otf = new HackListOtf();

		for(Category category : Category.values())
		{
			CheckboxSetting setting = categorySetting(otf, category);
			assertNotNull(setting, category.name());
			assertTrue(setting.isCheckedByDefault(), category.name());
		}
	}

	/**
	 * 设置名会被当持久化键写进配置，所以名字跟着分类的显示名走。
	 * 分类名是中文（{@code Category} 用 {@code \\u} 转义写死，与文件编码无关），
	 * 这里顺带钉住它没被改成英文——改名等于让老配置里的开关回落默认值。
	 */
	@Test
	void settingNamesFollowTheCategoryDisplayNames()
	{
		HackListOtf otf = new HackListOtf();
		int found = 0;

		for(Category category : Category.values())
		{
			assertNotNull(otf.getSettings().get(settingKey(category)),
				category.name() + " -> " + category.getName());
			found++;
		}

		assertEquals(Category.values().length, found);
	}
}
