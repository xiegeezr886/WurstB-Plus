package net.wurstclient.hud2.nanovg;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NanoVGNativeLoaderTest
{
	@Test
	void darwinIsMacosAndUnsupportedArchitectureIsRejected() throws Exception
	{
		String os = System.getProperty("os.name");
		String arch = System.getProperty("os.arch");
		try
		{
			Method classifier = NanoVGNativeLoader.class.getDeclaredMethod("classifier");
			Method name = NanoVGNativeLoader.class.getDeclaredMethod("nativeName");
			classifier.setAccessible(true);
			name.setAccessible(true);
			System.setProperty("os.name", "Darwin");
			System.setProperty("os.arch", "aarch64");
			assertEquals("natives-macos-arm64", classifier.invoke(null));
			assertEquals("liblwjgl_nanovg.dylib", name.invoke(null));
			System.setProperty("os.arch", "x86");
			assertNull(classifier.invoke(null));
		}finally
		{
			System.setProperty("os.name", os);
			System.setProperty("os.arch", arch);
		}
	}

	private Path write(InputStream input) throws Exception
	{
		Method method = NanoVGNativeLoader.class.getDeclaredMethod("writeNative", InputStream.class, String.class);
		method.setAccessible(true);
		return (Path)method.invoke(null, input, "native-test.dll");
	}

	@Test
	void corruptCacheIsRepairedAndChangedBundleGetsItsOwnDirectory(@TempDir Path temp) throws Exception
	{
		String previous = System.getProperty("java.io.tmpdir");
		try
		{
			System.setProperty("java.io.tmpdir", temp.toString());
			byte[] bytes = {1, 2, 3, 4};
			Path first = write(new ByteArrayInputStream(bytes));
			Files.write(first.resolve("native-test.dll"), new byte[]{9});
			assertEquals(first, write(new ByteArrayInputStream(bytes)));
			assertArrayEquals(bytes, Files.readAllBytes(first.resolve("native-test.dll")));
			assertNotEquals(first, write(new ByteArrayInputStream(new byte[]{5, 6})));
			try(var paths = Files.walk(temp))
			{
				assertFalse(paths.anyMatch(p -> p.toString().endsWith(".tmp")));
			}
		}finally { System.setProperty("java.io.tmpdir", previous); }
	}

	@Test
	void differentArchitecturesNeverShareCache(@TempDir Path temp) throws Exception
	{
		String previous = System.getProperty("java.io.tmpdir");
		String arch = System.getProperty("os.arch");
		try
		{
			System.setProperty("java.io.tmpdir", temp.toString());
			System.setProperty("os.arch", "amd64");
			Path x64 = write(new ByteArrayInputStream(new byte[]{1}));
			System.setProperty("os.arch", "aarch64");
			assertNotEquals(x64, write(new ByteArrayInputStream(new byte[]{1})));
		}finally
		{
			System.setProperty("java.io.tmpdir", previous);
			System.setProperty("os.arch", arch);
		}
	}

	@Test
	void failedInputDoesNotPublishPartialLibrary(@TempDir Path temp) throws Exception
	{
		String previous = System.getProperty("java.io.tmpdir");
		try
		{
			System.setProperty("java.io.tmpdir", temp.toString());
			InputStream failing = new InputStream() {
				@Override public int read() throws IOException { throw new IOException("interrupted extraction"); }
			};
			InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> write(failing));
			assertInstanceOf(IOException.class, exception.getCause());
			try(var paths = Files.walk(temp)) { assertEquals(0, paths.filter(Files::isRegularFile).count()); }
		}finally { System.setProperty("java.io.tmpdir", previous); }
	}
}
