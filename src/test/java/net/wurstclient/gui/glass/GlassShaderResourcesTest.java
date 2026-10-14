package net.wurstclient.gui.glass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * 玻璃着色器的资源对账。
 *
 * <p>
 * 这一层出错的后果特别隐蔽：shader JSON 里少写一个 uniform，{@code safeGetUniform()}
 * 会返回 null，绘制时抛 NPE，而异常又被 {@link GlassButtons} 吞掉转成"永久回退扁平
 * 样式"——按钮看起来只是"没变玻璃"，不会有任何报错。所以名字必须在编译期之外也对上账：
 * Java 里 set 的每个名字都要在 JSON 里声明，JSON 里声明的每个都要在 GLSL 里真的存在。</p>
 */
class GlassShaderResourcesTest
{
	private static final Path SHADERS =
		Path.of("src/main/resources/assets/wurst/shaders/core");
	private static final Path RENDERER = Path
		.of("src/main/java/net/wurstclient/gui/glass/GlassRenderer.java");

	private static final Pattern UNIFORM_SET = Pattern
		.compile("(glassShader|blurShader)\\.safeGetUniform\\(\"([^\"]+)\"\\)");
	private static final Pattern SAMPLER_SET = Pattern
		.compile("(glassShader|blurShader)\\.setSampler\\(\"([^\"]+)\"");
	private static final Pattern GLSL_UNIFORM =
		Pattern.compile("uniform\\s+\\w+\\s+(\\w+)\\s*;");

	@Test
	void everyShaderFileExists() throws IOException
	{
		for(String name : new String[]{"glass.json", "glass.vsh", "glass.fsh",
			"blur.json", "blur.vsh", "blur.fsh"})
			assertTrue(Files.exists(SHADERS.resolve(name)), name + " 不存在");
	}

	@Test
	void shadersUseTheWurstNamespace() throws IOException
	{
		// 资源命名空间是 wurst，不是模组 ID（wurstpenguin）；写错就是静默回退扁平样式
		JsonObject glass = json("glass.json");
		assertEquals("wurst:glass", glass.get("vertex").getAsString());
		assertEquals("wurst:glass", glass.get("fragment").getAsString());
		JsonObject blur = json("blur.json");
		assertEquals("wurst:blur", blur.get("vertex").getAsString());
		assertEquals("wurst:blur", blur.get("fragment").getAsString());
	}

	@Test
	void glassDeclaresEveryNameTheRendererWrites() throws IOException
	{
		assertCovers("glassShader", json("glass.json"));
	}

	@Test
	void blurDeclaresEveryNameTheRendererWrites() throws IOException
	{
		assertCovers("blurShader", json("blur.json"));
	}

	@Test
	void everyDeclaredUniformExistsInTheGlsl() throws IOException
	{
		assertDeclaredInGlsl("glass.json", "glass");
		assertDeclaredInGlsl("blur.json", "blur");
	}

	@Test
	void thePortedFilesStillMatchUpstreamShapes() throws IOException
	{
		// 上游 ButtonMixin 走的就是这两个采样器：背景快照 + 模糊链
		JsonObject glass = json("glass.json");
		assertTrue(samplerNames(glass).containsAll(Set.of("Scene", "Blurred")));
		assertTrue(uniformNames(json("blur.json")).contains("Downsample"));
	}

	private static void assertCovers(String shaderField, JsonObject shader)
		throws IOException
	{
		Set<String> uniforms = new LinkedHashSet<>();
		Set<String> samplers = new LinkedHashSet<>();
		for(String line : Files.readAllLines(RENDERER))
		{
			Matcher uniform = UNIFORM_SET.matcher(line);
			if(uniform.find() && uniform.group(1).equals(shaderField))
				uniforms.add(uniform.group(2));
			Matcher sampler = SAMPLER_SET.matcher(line);
			if(sampler.find() && sampler.group(1).equals(shaderField))
				samplers.add(sampler.group(2));
		}

		assertTrue(uniforms.size() >= 3,
			"没从 " + RENDERER + " 里解析出 " + shaderField + " 的 uniform");
		assertTrue(uniformNames(shader).containsAll(uniforms), shaderField
			+ " 在 JSON 里缺少: " + missing(uniformNames(shader), uniforms));
		assertTrue(samplerNames(shader).containsAll(samplers), shaderField
			+ " 在 JSON 里缺少采样器: "
			+ missing(samplerNames(shader), samplers));
	}

	private static void assertDeclaredInGlsl(String jsonName, String glslName)
		throws IOException
	{
		Set<String> declared = new LinkedHashSet<>();
		for(String extension : new String[]{".vsh", ".fsh"})
		{
			Matcher matcher = GLSL_UNIFORM
				.matcher(Files.readString(SHADERS.resolve(glslName + extension)));
			while(matcher.find())
				declared.add(matcher.group(1));
		}
		// ModelViewMat / ProjMat 是引擎按帧写入的默认 uniform，声明在顶点着色器里
		Set<String> uniforms = uniformNames(json(jsonName));
		assertTrue(declared.containsAll(uniforms), jsonName
			+ " 声明了 GLSL 里没有的 uniform: "
			+ missing(declared, uniforms));
	}

	/** {@code required} 里 {@code available} 没有的名字——断言失败时把差集打出来。 */
	private static Set<String> missing(Set<String> available,
		Set<String> required)
	{
		Set<String> missing = new LinkedHashSet<>(required);
		missing.removeAll(available);
		return missing;
	}

	private static JsonObject json(String name) throws IOException
	{
		return JsonParser.parseString(Files.readString(SHADERS.resolve(name)))
			.getAsJsonObject();
	}

	private static Set<String> uniformNames(JsonObject shader)
	{
		return names(shader.getAsJsonArray("uniforms"));
	}

	private static Set<String> samplerNames(JsonObject shader)
	{
		return names(shader.getAsJsonArray("samplers"));
	}

	private static Set<String> names(JsonArray array)
	{
		Set<String> names = new LinkedHashSet<>();
		for(int i = 0; i < array.size(); i++)
			names.add(array.get(i).getAsJsonObject().get("name").getAsString());
		return names;
	}
}
