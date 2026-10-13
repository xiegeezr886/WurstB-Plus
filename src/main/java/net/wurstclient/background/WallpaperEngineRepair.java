/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonObject;

import net.wurstclient.background.WallpaperEngineImporter.Candidate;

/**
 * 把「当初导入成了预览动图」的库条目**自动修回场景**。
 *
 * <p>
 * <b>为什么需要它</b>：早期导入依赖 {@code project.json} 的类型声明，而实测有作品
 * （「Where Dream Has Been」「Perlica &amp; Talos II」「明日方舟-阿米娅-王冠」）的
 * {@code project.json} 只有 2KB、缺类型标记，于是 {@code resolveMedia} 返回 null、
 * 最后退回 {@code preview.gif} —— 导入进来的是一张 1MB 都不到的预览动图，类型还记成
 * {@code IMAGE}，界面上就是「加载不了」。</p>
 *
 * <p>
 * {@link WallpaperEngineImporter} 已经改成「目录里有 {@code scene.pkg} 就当场景」，
 * 但那只救**新导入**；已经躺在库里的坏条目，媒体文件本身就是那份 GIF，**按文件名重新
 * 推断类型永远推不出 SCENE**，只能拿原始工坊目录重新导一次。这个类就是那条路径。</p>
 *
 * <p>
 * 判定与执行分开：{@link #plan} 是纯函数（能单测、不碰磁盘），{@link #apply} 才动文件。
 * 匹配用**标题全等** —— 库里的目录 id 是标题 slug 出来的，工坊目录名是数字 id，
 * 两者之间只有标题能对上。</p>
 */
public final class WallpaperEngineRepair
{
	/** 只有这个来源的条目才可能是"导入成预览图"的受害者。 */
	private static final String ORIGIN = "Wallpaper Engine";

	/** 一次性开关：修完就不再扫全库；失败则允许下次再试。 */
	private static final AtomicBoolean STARTED = new AtomicBoolean();

	private WallpaperEngineRepair()
	{
	}

	/**
	 * 纯函数：哪些条目能升级、各自该换成哪个候选。
	 *
	 * @return 条目 id → 可播放的场景候选
	 */
	public static Map<String, Candidate> plan(List<BackgroundEntry> entries,
		List<Candidate> candidates)
	{
		Map<String, Candidate> upgrades = new HashMap<>();

		if(entries == null || candidates == null)
			return upgrades;

		// 标题 → 可播放的场景候选；同标题有多份时取第一个
		Map<String, Candidate> scenes = new HashMap<>();

		for(Candidate candidate : candidates)
			if(candidate.playable() && candidate.kind() == BackgroundKind.SCENE
				&& candidate.title() != null)
				scenes.putIfAbsent(candidate.title(), candidate);

		for(BackgroundEntry entry : entries)
		{
			if(entry == null || entry.isDefault())
				continue;

			// 只碰"从预览图导进来的"：图片/GIF 的真身本来就是图片或动图
			if(entry.kind() != BackgroundKind.IMAGE
				&& entry.kind() != BackgroundKind.GIF)
				continue;

			if(!ORIGIN.equals(entry.origin()))
				continue;

			Candidate scene = scenes.get(entry.title());

			if(scene != null)
				upgrades.put(entry.id(), scene);
		}

		return upgrades;
	}

	/**
	 * 按 {@link #plan} 重导：换掉媒体文件、把类型改回 {@code SCENE}、删掉旧的预览动图。
	 *
	 * <p>
	 * <b>不动缩略图</b>：它本来就取自同一张作品的预览图，画面是对的，重导不影响它。
	 * </p>
	 *
	 * @return 真的修好的条数
	 */
	public static int apply(BackgroundStorage storage,
		Map<String, Candidate> upgrades)
	{
		int repaired = 0;

		for(Map.Entry<String, Candidate> upgrade : upgrades.entrySet())
		{
			String id = upgrade.getKey();
			Candidate scene = upgrade.getValue();

			try
			{
				Path folder = storage.folder(id);
				Path old = storage.mediaPath(id);
				String name = "media." + suffix(scene.media());
				Path target = folder.resolve(name);

				Files.createDirectories(folder);

				if(old != null && !old.equals(target))
					Files.deleteIfExists(old);

				Files.copy(scene.media(), target,
					StandardCopyOption.REPLACE_EXISTING);
				Files.writeString(storage.metaPath(id), meta(scene.title(), name),
					StandardCharsets.UTF_8);

				System.out.println("[Background] 自动修好「" + scene.title()
					+ "」：预览图 → 场景（" + Files.size(target) / 1_000_000
					+ "MB）");
				repaired++;

			}catch(IOException | RuntimeException e)
			{
				System.out.println("[Background] 修不了「" + scene.title() + "："
					+ e.getMessage());
			}
		}

		return repaired;
	}

	/**
	 * 后台跑一次修复（列库时调用）。
	 *
	 * <p>
	 * 工坊目录在**工作线程里**才去解析：{@link SteamLocator#wallpaperFolders()}
	 * 要读 Steam 的库配置，不该压在渲染线程上。重导一个 30MB 的场景包同理。</p>
	 */
	public static void repairOnceAsync(BackgroundStorage storage)
	{
		if(storage == null || !STARTED.compareAndSet(false, true))
			return;

		Thread worker = new Thread(() -> {
			try
			{
				List<Path> folders = SteamLocator.wallpaperFolders();

				if(folders == null || folders.isEmpty())
				{
					STARTED.set(false);
					return;
				}

				int repaired = apply(storage,
					plan(storage.list(), WallpaperEngineImporter.scan(folders)));

				if(repaired > 0)
					System.out.println("[Background] 自动修好了 " + repaired
						+ " 个原本导入成预览图的场景壁纸");
				else
					STARTED.set(false);

			}catch(RuntimeException e)
			{
				STARTED.set(false);
				System.out.println("[Background] 自动修复失败：" + e.getMessage());
			}
		}, "wurst-background-repair");

		worker.setDaemon(true);
		worker.start();
	}

	private static String suffix(Path media)
	{
		String name = media.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "pkg" : name.substring(dot + 1);
	}

	/** 与 {@link BackgroundStorage} 写出来的形状一致：少一个字段就读不回来。 */
	static String meta(String title, String file)
	{
		JsonObject meta = new JsonObject();
		meta.addProperty("kind", BackgroundKind.SCENE.name());
		meta.addProperty("file", file);
		meta.addProperty("title", title);
		meta.addProperty("origin", ORIGIN);
		meta.addProperty("importedAt", System.currentTimeMillis());
		return meta.toString();
	}
}
