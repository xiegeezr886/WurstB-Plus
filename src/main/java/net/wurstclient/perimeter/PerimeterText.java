/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.wurstclient.WurstClient;

/**
 * The English and Simplified Chinese texts of the perimeter feature, following
 * the game language exactly like the reference mod does.
 *
 * <p>
 * The reference keeps its strings in the Minecraft language resources and
 * resolves them with translatable components. This port keeps the same two
 * languages in one table instead, which guarantees that both languages cover
 * every message and lets a unit test verify it. The mechanism differs, the
 * result for the player does not.
 */
public final class PerimeterText
{
	private static final Map<String, String> CHINESE = new LinkedHashMap<>();
	private static final Map<String, String> ENGLISH = new LinkedHashMap<>();
	
	private PerimeterText()
	{}
	
	private static void put(String key, String english, String chinese)
	{
		ENGLISH.put(key, english);
		CHINESE.put(key, chinese);
	}
	
	/**
	 * @return the text in the language the client is set to.
	 */
	public static String get(String key, Object... args)
	{
		return get(currentLocale(), key, args);
	}
	
	public static String get(Locale locale, String key, Object... args)
	{
		Map<String, String> table = isChinese(locale) ? CHINESE : ENGLISH;
		String template = table.getOrDefault(key,
			ENGLISH.getOrDefault(key, key));
		
		if(args == null || args.length == 0)
			return template;
		
		try
		{
			return String.format(locale, template, args);
		}catch(java.util.IllegalFormatException e)
		{
			return template;
		}
	}
	
	public static boolean isChinese(Locale locale)
	{
		return locale != null
			&& locale.getLanguage().toLowerCase(Locale.ROOT).equals("zh");
	}
	
	/**
	 * @return whether both languages define the key.
	 */
	public static boolean has(String key)
	{
		return ENGLISH.containsKey(key) && CHINESE.containsKey(key);
	}
	
	public static Set<String> keys()
	{
		return Collections.unmodifiableSet(ENGLISH.keySet());
	}
	
	public static String stateName(PerimeterAutomationState state)
	{
		return get("perimeterdigger.state." + state.id());
	}
	
	private static Locale currentLocale()
	{
		try
		{
			if(WurstClient.MC != null
				&& WurstClient.MC.getLanguageManager() != null)
			{
				String selected = WurstClient.MC.getLanguageManager()
					.getSelected();
				
				if(selected != null && !selected.isBlank())
					return Locale.forLanguageTag(selected.replace('_', '-'));
			}
		}catch(Exception | LinkageError e)
		{
			// no client yet, fall back to the system language
		}
		
		return Locale.getDefault();
	}
	
	static
	{
		// states
		put("perimeterdigger.state.idle", "Idle", "空闲");
		put("perimeterdigger.state.validating", "Validating", "校验中");
		put("perimeterdigger.state.ready", "Ready", "就绪");
		put("perimeterdigger.state.navigating_to_mine", "Going to the mine",
			"前往矿区");
		put("perimeterdigger.state.mining", "Mining", "挖掘中");
		put("perimeterdigger.state.collecting_drops", "Collecting drops",
			"拾取掉落物");
		put("perimeterdigger.state.eating", "Eating", "进食中");
		put("perimeterdigger.state.complete", "Complete", "已完成");
		put("perimeterdigger.state.navigating_to_unload", "Going to unload",
			"前往卸货点");
		put("perimeterdigger.state.approaching_unload", "Approaching the shaft",
			"接近竖井");
		put("perimeterdigger.state.positioning_for_unload",
			"Positioning for unloading", "卸货就位");
		put("perimeterdigger.state.unloading", "Unloading", "卸货中");
		put("perimeterdigger.state.navigating_to_resupply",
			"Going to resupply", "前往补给点");
		put("perimeterdigger.state.resupplying", "Resupplying", "补给中");
		put("perimeterdigger.state.navigating_to_durability_supply",
			"Going to the durability supply", "前往耐久补给点");
		put("perimeterdigger.state.swapping_durability_at_supply",
			"Swapping equipment", "更换装备");
		put("perimeterdigger.state.navigating_to_bed", "Going to bed",
			"前往床铺");
		put("perimeterdigger.state.sleeping", "Sleeping", "睡觉中");
		put("perimeterdigger.state.navigating_to_perimeter_portal",
			"Going to the perimeter portal", "前往周界传送门");
		put("perimeterdigger.state.entering_perimeter_portal",
			"Entering the perimeter portal", "进入周界传送门");
		put("perimeterdigger.state.navigating_to_repair_portal",
			"Going to the repair portal", "前往维修传送门");
		put("perimeterdigger.state.entering_repair_portal",
			"Entering the repair portal", "进入维修传送门");
		put("perimeterdigger.state.clearing_repair_portal",
			"Clearing the repair portal", "清理维修传送门");
		put("perimeterdigger.state.navigating_to_repair_machine",
			"Going to the repair furnaces", "前往维修熔炉");
		put("perimeterdigger.state.repairing", "Repairing", "维修中");
		put("perimeterdigger.state.returning_to_mine", "Returning to the mine",
			"返回矿区");
		put("perimeterdigger.state.paused", "Paused", "已暂停");
		put("perimeterdigger.state.error", "Error", "错误");
		
		// details
		put("stopped", "stopped", "已停止");
		put("paused", "paused", "已暂停");
		put("resumed", "resumed", "已继续");
		put("world changed", "world changed", "世界已切换");
		put("mining batch of %d blocks", "mining batch of %d blocks",
			"本批次挖掘 %d 个方块");
		put("mined %d/%d this batch", "mined %d/%d this batch",
			"本批次已挖 %d/%d");
		put("collecting drops within %d blocks",
			"collecting drops within %d blocks", "拾取 %d 格内的掉落物");
		put("waiting for the inventory to settle (%d items)",
			"waiting for the inventory to settle (%d items)",
			"等待背包稳定（%d 件物品）");
		put("walking to %d drop(s)", "walking to %d drop(s)", "走向 %d 个掉落物");
		put("travelling to unloading point %s",
			"travelling to unloading point %s", "前往卸货点 %s");
		put("approaching the edge of %s", "approaching the edge of %s",
			"接近 %s 边缘");
		put("facing the shaft at %s", "facing the shaft at %s", "面向 %s 的竖井");
		put("positioning at %s", "positioning at %s", "正在 %s 就位");
		put("dropping products into %s", "dropping products into %s",
			"将产物丢入 %s");
		put("mining complete", "mining complete", "挖掘完成");
		put("mining and unloading complete", "mining and unloading complete",
			"挖掘与卸货完成");
		put("returning to the mining area", "returning to the mining area",
			"返回挖掘区域");
		put("eating", "eating", "进食中");
		put("finished eating", "finished eating", "进食结束");
		put("finished eating, mining on", "finished eating, mining on",
			"进食结束，继续挖掘");
		put("replaced a worn item with a healthy one",
			"replaced a worn item with a healthy one", "已用完好物品替换磨损物品");
		put("travelling to the supply point", "travelling to the supply point",
			"前往补给点");
		put("using the supply point", "using the supply point", "正在使用补给点");
		put("looking for a place to reach the supply",
			"looking for a place to reach the supply", "寻找可够到补给点的站位");
		put("looking for the supply container",
			"looking for the supply container", "寻找补给容器");
		put("no supply point is configured", "no supply point is configured",
			"未配置补给点");
		put("travelling to the bed", "travelling to the bed", "前往床铺");
		put("sleeping", "sleeping", "睡觉中");
		put("awake, mining on", "awake, mining on", "已起床，继续挖掘");
		put("travelling to the perimeter portal",
			"travelling to the perimeter portal", "前往周界传送门");
		put("travelling to the portal", "travelling to the portal",
			"前往传送门");
		put("entering the portal", "entering the portal", "进入传送门");
		put("travelling to the repair furnaces",
			"travelling to the repair furnaces", "前往维修熔炉");
		put("taking furnace output", "taking furnace output", "取出熔炉产物");
		put("mining on", "mining on", "继续挖掘");
		put("facing the shaft at %s", "facing the shaft at %s", "面向 %s 的竖井");
		put("error: %s", "error: %s", "错误：%s");
		put("no mining area: plan a rectangle or detect a boundary first",
			"no mining area: plan a rectangle or detect a boundary first",
			"没有挖掘区域：请先规划矩形或检测边界");
		put("no safe standing position near unloading point %s",
			"no safe standing position near unloading point %s",
			"卸货点 %s 附近没有安全站位");
		put("could not reach unloading point %s",
			"could not reach unloading point %s", "无法到达卸货点 %s");
		put("could not start the mining batch",
			"could not start the mining batch", "无法开始挖掘批次");
		
		// commands
		put("perimeterdigger.command.started", "Perimeter automation started.",
			"周界自动化已启动。");
		put("perimeterdigger.command.stopped", "Perimeter automation stopped.",
			"周界自动化已停止。");
		put("perimeterdigger.command.paused", "Perimeter automation paused.",
			"周界自动化已暂停。");
		put("perimeterdigger.command.resumed", "Perimeter automation resumed.",
			"周界自动化已继续。");
		put("perimeterdigger.command.not_running",
			"Perimeter automation is not running.",
			"周界自动化未在运行。");
		put("perimeterdigger.command.problems",
			"The automation cannot start yet:", "自动化暂时无法启动：");
		put("perimeterdigger.command.saved", "Configuration saved to %s.",
			"配置已保存到 %s。");
		put("perimeterdigger.command.loaded", "Configuration loaded from %s.",
			"已从 %s 载入配置。");
		put("perimeterdigger.command.unknown_value", "Unknown value: %s",
			"未知取值：%s");
		put("perimeterdigger.command.set", "%s is now %s.",
			"%s 现在为 %s。");
		put("perimeterdigger.command.added", "Added %s.", "已添加 %s。");
		put("perimeterdigger.command.removed", "Removed %s.", "已移除 %s。");
		put("perimeterdigger.command.unknown_option", "Unknown option: %s",
			"未知选项：%s");
		put("perimeterdigger.command.baritone_missing",
			"Baritone is not installed, the automation cannot move.",
			"未安装 Baritone，自动化无法移动。");
		put("perimeterdigger.command.area_cleared", "Area cleared.", "区域已清除。");
		put("perimeterdigger.command.config_reloaded", "Configuration reloaded.", "配置已重新载入。");
		put("perimeterdigger.command.config_reset", "Configuration reset to defaults.", "配置已重置为默认值。");
		put("perimeterdigger.command.no_area", "No area stored yet.", "尚未存储区域。");
		put("perimeterdigger.command.no_unloading_points", "No unloading points configured.", "未配置卸货点。");
		put("perimeterdigger.command.no_world", "No world is loaded.", "未加载世界。");
		put("perimeterdigger.command.bad_y_range", "minY must not be larger than maxY.", "minY 不能大于 maxY。");
		put("perimeterdigger.command.planned", "Planned %s.", "已规划 %s。");
		put("perimeterdigger.command.detected", "Detected %d columns in %d scanlines.", "检测到 %d 列，共 %d 条扫描线。");
		put("perimeterdigger.command.walking_to", "Walking to %s.", "正前往 %s。");
	}
}
