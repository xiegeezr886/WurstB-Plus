package net.wurstclient.render.skia;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import net.minecraft.client.Minecraft;
import net.wurstclient.util.BundledNativeFiles;

/**
 * Skiko natives 引导器。
 *
 * <p>natives 随 mod 资源打包（assets/wurst/skiko/），避免 jarJar 重定位资源
 * 路径导致 Skiko 无法在嵌套 jar 中定位 DLL。首次使用前把
 * {@code skiko-windows-x64.dll} 与 {@code icudtl.dat} 解压到
 * {@code gameDir/skiko/}，再通过 {@code skiko.library.path} /
 * {@code skiko.data.path} 系统属性显式指定加载位置（Skiko 官方加载机制）。</p>
 *
 * <p>因为 {@code gameDir/skiko/} 是玩家可写的目录、Skiko 又会对它
 * {@code System.load()}，所以这里<b>不信任目标目录里已有的文件</b>：只有当它的
 * SHA-256 与编译进模组的可信校验值一致时才跳过复制；复制完还会再校验一次落盘的文件，
 * 不一致就直接报错，不会把一个来路不明的 DLL 交给 {@code System.load()}。同时也
 * 因此不必每次启动都重写 26.5 MB。</p>
 *
 * <p>失败契约：失败原因只记录一次（{@link Failure#reason}），之后每一次
 * {@link #ensure()} 都抛出<b>同一个</b>异常，不再出现"第一次抛、后面静默返回
 * false"两种行为。{@link #ensure()} 由此变成"要么 true、要么抛"：原来的三种调用
 * 方式（{@link SkiaRegionRenderer#beginRegion} 的 javadoc 本来就写了会抛、
 * {@link EspSkia} 与 {@code TwilightSurface} 都自己 catch Throwable 兜底）都仍然
 * 成立。平台不匹配也在上报错，而不是等到第一次触碰 Skia 类才
 * {@code UnsatisfiedLinkError}。</p>
 */
public final class SkikoNatives
{
	private static final String DLL_RESOURCE = "skiko/skiko-windows-x64.dll";
	private static final String ICU_RESOURCE = "skiko/icudtl.dat";

	private static volatile boolean ready;
	private static volatile Failure failure;

	public static boolean ensure()
	{
		if(ready)
			return true;

		Failure previous = failure;
		if(previous != null)
			throw previous.toException();

		synchronized(SkikoNatives.class)
		{
			if(ready)
				return true;
			if(failure != null)
				throw failure.toException();

			try
			{
				prepare();
				ready = true;
				return true;

			}catch(IOException | NoSuchAlgorithmException | RuntimeException e)
			{
				// 记下原因后，后续调用抛的是同一个异常，行为保持一致
				Failure recorded = new Failure(e);
				failure = recorded;
				throw recorded.toException();
			}
		}
	}

	private static void prepare()
		throws IOException, NoSuchAlgorithmException
	{
		checkPlatform();

		File dir = new File(Minecraft.getInstance().gameDirectory, "skiko");
		if(!dir.isDirectory() && !dir.mkdirs())
			throw new IOException("Cannot create " + dir);

		extract(DLL_RESOURCE, new File(dir, "skiko-windows-x64.dll"));
		extract(ICU_RESOURCE, new File(dir, "icudtl.dat"));

		// skiko.library.path 是目录：skiko 内部以
		// File(dir, "skiko-windows-x64.dll") 解析后再 System.load
		System.setProperty("skiko.library.path", dir.getAbsolutePath());
		System.setProperty("skiko.data.path", dir.getAbsolutePath());
	}

	/**
	 * 本工程只打包了 {@code skiko-windows-x64.dll}，其它平台必须在这里明确
	 * 失败。这样 {@code EspSkia} 的兜底路径能一次性拿到结论，而不是等真去碰
	 * Skia 类时再抛 {@code UnsatisfiedLinkError}。
	 */
	private static void checkPlatform() throws IOException
	{
		String os = System.getProperty("os.name", "");
		String arch = System.getProperty("os.arch", "").toLowerCase();

		if(!os.toLowerCase().contains("windows"))
			throw new IOException(
				"Skiko natives are only bundled for Windows, but the OS is \""
					+ os + "\"");

		if(!"x86_64".equals(arch) && !"amd64".equals(arch))
			throw new IOException(
				"Skiko natives are only bundled for x86_64, but the CPU "
					+ "architecture is \"" + arch + "\"");
	}

	private static File extract(String resourcePath, File target)
		throws IOException, NoSuchAlgorithmException
	{
		String expected = switch(resourcePath)
		{
			case DLL_RESOURCE -> "3f2a093691c567faa5e0d9d71e39d528136b8bcf85b130504d5782cc04e77142";
			case ICU_RESOURCE -> "9ae98c06cbb0ea43c5cd6b5725310c008c65e46072421a1118cb88e1de9a8b92";
			default -> throw new IOException("Unknown bundled native: " + resourcePath);
		};
		BundledNativeFiles.extract(SkikoNatives.class,
			"assets/wurst/" + resourcePath, expected, target.toPath());
		return target;
	}

	/** 第一次失败时记下的原因；之后每次调用都复用它。 */
	private static final class Failure
	{
		private final Throwable reason;

		private Failure(Throwable reason)
		{
			this.reason = reason;
		}

		private IllegalStateException toException()
		{
			return new IllegalStateException(
				"Failed to prepare Skiko natives", reason);
		}
	}
}
