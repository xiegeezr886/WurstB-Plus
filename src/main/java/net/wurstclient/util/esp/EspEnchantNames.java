/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.esp;

import java.util.HashMap;
import java.util.Map;

/**
 * 附魔短名表，移植自 OpenOpal 的
 * {@code utility/render/ESPUtility.java} 的 {@code ENCHANTMENT_NAMES}（GPL-3.0）。
 *
 * <p>
 * 参考用 {@code RegistryKey<Enchantment>} 作键；这里改用附魔的<b>注册名路径</b>
 * 字符串，于是本类不含任何 Minecraft 依赖，可以单测。调用方传入
 * {@code ResourceLocation} 或 {@code "minecraft:sharpness"} 这类带命名空间的
 * 写法都可以，{@link #shortNameOf(String)} 会剥掉命名空间。
 *
 * <p>
 * 表内容与参考逐条对应（39 项）。参考基于 1.21.10 的附魔常量，这 39 项在
 * 1.20.1 全部存在，唯一需要换算的是 {@code SWEEPING_EDGE}：它在 1.20.1 的
 * 注册名是 {@code sweeping}。1.21 新增的 {@code density}/{@code breach}/
 * {@code wind_burst} 参考表里本来就没有，故无需处理。
 */
public final class EspEnchantNames
{
	private static final Map<String, String> SHORT_NAMES = build();

	private EspEnchantNames()
	{
	}

	/**
	 * @param idOrPath
	 *            注册名路径或带命名空间的 id，大小写不敏感。
	 * @return 短名；表里没有则返回 {@code null}（调用方应原样跳过该附魔）。
	 */
	public static String shortNameOf(String idOrPath)
	{
		if(idOrPath == null || idOrPath.isEmpty())
			return null;

		String path = idOrPath;
		int colon = path.indexOf(':');
		if(colon >= 0)
			path = path.substring(colon + 1);

		return SHORT_NAMES.get(path.toLowerCase(java.util.Locale.ROOT));
	}

	public static int size()
	{
		return SHORT_NAMES.size();
	}

	private static Map<String, String> build()
	{
		HashMap<String, String> map = new HashMap<>();

		// 盔甲
		map.put("protection", "Pr");
		map.put("fire_protection", "Fp");
		map.put("feather_falling", "Ff");
		map.put("blast_protection", "Bp");
		map.put("projectile_protection", "Pp");
		map.put("respiration", "Re");
		map.put("aqua_affinity", "Aa");
		map.put("thorns", "Th");
		map.put("depth_strider", "Ds");
		map.put("frost_walker", "Fw");
		map.put("binding_curse", "Bc");
		map.put("soul_speed", "Ss");
		map.put("swift_sneak", "Sn");

		// 近战
		map.put("sharpness", "Sh");
		map.put("smite", "Sm");
		map.put("bane_of_arthropods", "BoA");
		map.put("knockback", "Kb");
		map.put("fire_aspect", "Fa");
		map.put("looting", "Lo");
		map.put("sweeping", "Sw");

		// 工具
		map.put("efficiency", "Ef");
		map.put("silk_touch", "St");
		map.put("unbreaking", "Un");
		map.put("fortune", "Fo");

		// 远程
		map.put("power", "Po");
		map.put("punch", "Pu");
		map.put("flame", "Fl");
		map.put("infinity", "In");

		// 钓鱼
		map.put("luck_of_the_sea", "Lu");
		map.put("lure", "Lr");

		// 三叉戟
		map.put("loyalty", "Ly");
		map.put("impaling", "Ip");
		map.put("riptide", "Ri");
		map.put("channeling", "Ch");

		// 弩
		map.put("multishot", "Mu");
		map.put("quick_charge", "Qc");
		map.put("piercing", "Pi");

		// 通用
		map.put("mending", "Me");
		map.put("vanishing_curse", "Vc");

		return Map.copyOf(map);
	}
}
