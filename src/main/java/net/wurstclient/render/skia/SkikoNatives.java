package net.wurstclient.render.skia;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

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
 * SHA-256 与 jar 内的资源完全一致时才跳过复制；复制完还会再校验一次落盘的文件，
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
		String arch = System.getProperty("os.arch", "");

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
		ResourceLocation location = new ResourceLocation("wurst",
			resourcePath);

		// 基准哈希：读的是资源流，边读边算，解压出来的 26.5 MB 只过一遍内存
		String expected;

		try(InputStream in = new BufferedInputStream(Minecraft.getInstance()
			.getResourceManager().open(location)))
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			expected = digestToHex(digest, in);
		}

		// (a) 目标文件与资源内容一致时跳过复制：既省掉每次都重写 26.5 MB，
		// 也意味着下面交给 System.load() 的 DLL 一定是随包发布的那个。
		if(target.isFile() && expected.equalsIgnoreCase(hashOf(target)))
			return target;

		// 复制用另一个资源流（上面的流要用来算哈希）；写到一半失败时文件可能
		// 不完整，所以下面的校验失败要直接报错，不能继续。
		try(InputStream copy = Minecraft.getInstance().getResourceManager()
			.open(location))
		{
			Files.copy(copy, target.toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		}

		// (b) 落盘后必须与资源一致，否则宁可让 Skia 不可用
		if(!expected.equalsIgnoreCase(hashOf(target)))
			throw new IOException("Hash mismatch after extracting " + target);

		return target;
	}

	private static String hashOf(File file) throws IOException
	{
		try(InputStream in = new BufferedInputStream(
			Files.newInputStream(file.toPath())))
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digestToHex(digest, in);

		}catch(NoSuchAlgorithmException e)
		{
			// SHA-256 是 JLS 要求每个 JVM 都必须提供的算法，走不到这里
			throw new IOException("SHA-256 is not available", e);
		}
	}

	private static String digestToHex(MessageDigest digest, InputStream in)
		throws IOException
	{
		byte[] buffer = new byte[8192];
		int read;

		while((read = in.read(buffer)) >= 0)
			digest.update(buffer, 0, read);

		StringBuilder hex = new StringBuilder(64);

		for(byte b : digest.digest())
			hex.append(Character.forDigit(b >> 4 & 0xF, 16))
				.append(Character.forDigit(b & 0xF, 16));

		return hex.toString();
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
