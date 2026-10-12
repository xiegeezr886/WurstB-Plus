/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 调试日志：默认写到 {@code <游戏目录>/wurst/logs/} 下。
 *
 * <p>
 * 目标目录由 {@link #init(Path)} 传入 —— 调用方传的是 WurstClient 的
 * {@code wurstFolder}，也就是 {@code MC.gameDirectory/wurst}。所以每个游戏实例
 * （每个版本、每个整合包各一份）有**自己的** logs 目录，互不覆盖。
 * </p>
 *
 * <h2>三条设计约束</h2>
 *
 * <ol>
 * <li><b>不阻塞游戏线程</b>：写盘在一条 daemon 线程上做，调用方只入队。
 * 渲染线程每帧都可能记录东西，同步写盘会造成卡顿。</li>
 *
 * <li><b>绝不把异常抛回游戏代码</b>：这是调试设施，磁盘满、目录只读、路径非法
 * 都不该让客户端崩。所有入口都吞掉异常并置一个内部标记，只在控制台提示一次。</li>
 *
 * <li><b>不无限增长</b>：每次启动开一个新文件（按时间命名），启动时按数量裁剪
 * 旧文件；单次会话内超过上限后停止写入并记一条截断说明，而不是把磁盘写满。</li>
 * </ol>
 *
 * <p>
 * 关闭方式：设系统属性 {@code -Dwurst.debuglog=off}。未初始化时所有方法都是
 * 空操作，所以任何地方调用都安全。
 * </p>
 */
public final class WurstDebugLog
{
	private static final DateTimeFormatter FILE_STAMP =
		DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final DateTimeFormatter LINE_STAMP =
		DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	/** 单次会话写入上限。超过就停止写正文，只补一条截断说明。 */
	private static final long MAX_BYTES = 4L * 1024 * 1024;

	/** 保留的文件数，按修改时间从新到旧。更旧的启动时删掉。 */
	private static final int KEEP_FILES = 10;

	private static final String DISABLED_PROPERTY = "wurst.debuglog";

	private static volatile WurstDebugLog instance;

	private final Path logFile;
	private final ConcurrentLinkedQueue<String> queue =
		new ConcurrentLinkedQueue<>();
	private final AtomicLong writtenBytes = new AtomicLong();
	private final AtomicBoolean stopped = new AtomicBoolean();
	private final AtomicBoolean warned = new AtomicBoolean();
	private final Thread writer;

	private WurstDebugLog(Path logFolder, String fileName)
	{
		logFile = logFolder.resolve(fileName);

		writer = new Thread(this::drainLoop, "Wurst-DebugLog");
		// daemon：客户端退出时不该被这条线程拖住
		writer.setDaemon(true);
		writer.start();

		// 退出钩子要排空的是**本实例**的队列，所以绑 drainNow 而不是静态的 flush
		Runtime.getRuntime().addShutdownHook(new Thread(this::drainNow));
	}

	/**
	 * 建 logs 目录、裁掉过旧的文件、开一个本次会话的日志文件。
	 *
	 * <p>
	 * 可重复调用：第二次起直接返回，不新建文件。传 null 或初始化失败都只是
	 * 让日志变成空操作，不影响游戏。
	 * </p>
	 */
	public static synchronized void init(Path wurstFolder)
	{
		if(instance != null)
			return;

		if("off".equalsIgnoreCase(System.getProperty(DISABLED_PROPERTY, "on")))
			return;

		if(wurstFolder == null)
			return;

		try
		{
			Path logFolder = wurstFolder.resolve("logs");
			Files.createDirectories(logFolder);
			prune(logFolder);

			String name = "debug-" + LocalDateTime.now().format(FILE_STAMP)
				+ ".log";
			instance = new WurstDebugLog(logFolder, name);

		}catch(IOException | RuntimeException e)
		{
			System.err.println("[Wurst] 调试日志不可用，已禁用: " + e);
		}
	}

	/** 日志目录，未初始化时返回 null。 */
	public static Path getLogFolder()
	{
		WurstDebugLog log = instance;
		return log == null ? null : log.logFile.getParent();
	}

	/** 本次会话的日志文件，未初始化时返回 null。 */
	public static Path getLogFile()
	{
		WurstDebugLog log = instance;
		return log == null ? null : log.logFile;
	}

	public static boolean isActive()
	{
		return instance != null;
	}

	public static void debug(String tag, String message)
	{
		debug(tag, message, null);
	}

	/**
	 * 记一行。{@code tag} 用来区分来源（例如 {@code "AutoSteal"}），便于事后过滤。
	 */
	public static void debug(String tag, String message, Throwable error)
	{
		WurstDebugLog log = instance;
		if(log == null || log.stopped.get())
			return;

		StringBuilder sb = new StringBuilder(96);
		sb.append(LocalDateTime.now().format(LINE_STAMP)).append(" [")
			.append(Thread.currentThread().getName()).append("] [")
			.append(tag).append("] ").append(message);

		if(error != null)
		{
			sb.append("  -- ").append(error.getClass().getName());

			String msg = error.getMessage();
			if(msg != null)
				sb.append(": ").append(msg);

			for(StackTraceElement frame : error.getStackTrace())
			{
				sb.append(System.lineSeparator()).append("\tat ")
					.append(frame);
			}
		}

		log.queue.add(sb.toString());
	}

	/** 把队列里剩下的写完。退出钩子与测试用；正常路径靠后台线程。 */
	public static void flush()
	{
		WurstDebugLog log = instance;
		if(log != null)
			log.drainNow();
	}

	private void drainLoop()
	{
		while(!stopped.get())
		{
			drainNow();

			try
			{
				Thread.sleep(250);
			}catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
		}

		drainNow();
	}

	private void drainNow()
	{
		if(queue.isEmpty())
			return;

		List<String> batch = new ArrayList<>();
		for(String line = queue.poll(); line != null; line = queue.poll())
			batch.add(line);

		if(batch.isEmpty())
			return;

		try(BufferedWriter out = Files.newBufferedWriter(logFile,
			StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
			java.nio.file.StandardOpenOption.APPEND))
		{
			for(String line : batch)
			{
				out.write(line);
				out.newLine();
				writtenBytes.addAndGet(line.length() + 1);
			}

			if(writtenBytes.get() >= MAX_BYTES)
			{
				out.write("--- 已达单次会话上限 " + MAX_BYTES
					+ " 字节，后续内容不再写入 ---");
				out.newLine();
				stopped.set(true);
			}

		}catch(IOException | RuntimeException e)
		{
			// 记不下来不是游戏的问题：停掉并只在控制台提示一次
			stopped.set(true);
			if(warned.compareAndSet(false, true))
				System.err.println("[Wurst] 调试日志写入失败，已停止: " + e);
		}
	}

	/** 只保留最近 KEEP_FILES 个日志文件，按修改时间从新到旧。 */
	private static void prune(Path logFolder)
	{
		List<Path> files = new ArrayList<>();

		try(DirectoryStream<Path> stream =
			Files.newDirectoryStream(logFolder, "debug-*.log"))
		{
			for(Path file : stream)
				files.add(file);

		}catch(IOException e)
		{
			return;
		}

		if(files.size() <= KEEP_FILES)
			return;

		files.sort(Comparator.comparingLong(WurstDebugLog::lastModified)
			.reversed());

		for(Path file : files.subList(KEEP_FILES, files.size()))
		{
			try
			{
				Files.deleteIfExists(file);
			}catch(IOException e)
			{
				// 删不掉就算了，不影响本次会话
			}
		}
	}

	private static long lastModified(Path file)
	{
		try
		{
			return Files.getLastModifiedTime(file).toMillis();
		}catch(IOException e)
		{
			return 0;
		}
	}
}
