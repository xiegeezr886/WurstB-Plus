package net.wurstclient.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class MixinPackageIsolationTest
{
	@Test
	void mixinPackageContainsOnlyConfiguredMixinsAndPlugin() throws IOException
	{
		// 配置文件不写死文件名：本工程用 wurstpenguin.mixins.json，
		// 而部分工程叫 wurst.mixins.json。此前写死读 /wurst.mixins.json，
		// 在改用其它名字的工程上会得到 null 流并抛 NullPointerException。
		String configName = resolveMixinConfigName();

		JsonObject config;
		try(var stream = getClass()
			.getResourceAsStream("/" + configName))
		{
			assertTrue(stream != null,
				() -> "找不到 mixin 配置: " + configName);
			config = JsonParser.parseReader(new InputStreamReader(stream,
				StandardCharsets.UTF_8)).getAsJsonObject();
		}

		Set<String> allowedClasses = new HashSet<>();
		addEntries(allowedClasses, config.getAsJsonArray("mixins"));
		addEntries(allowedClasses, config.getAsJsonArray("client"));
		addEntries(allowedClasses, config.getAsJsonArray("server"));
		if(config.has("plugin"))
		{
			String plugin = config.get("plugin").getAsString();
			allowedClasses.add(plugin.substring(plugin.lastIndexOf('.') + 1));
		}

		Path mixinSources = Path.of("src", "main", "java", "net",
			"wurstclient", "mixin");
		try(var files = Files.list(mixinSources))
		{
			files.filter(path -> path.getFileName().toString().endsWith(".java"))
				.map(path -> path.getFileName().toString().replace(".java", ""))
				.forEach(className -> assertTrue(
					allowedClasses.contains(className),
					() -> className + " is not declared in " + configName));
		}
	}

	/**
	 * 从 fabric.mod.json 的 mixins 数组取真实配置名；没有该文件（Forge /
	 * NeoForge 工程）时，退化为在资源目录里找唯一的 *.mixins.json。
	 */
	private String resolveMixinConfigName() throws IOException
	{
		Path fabricModJson = Path.of("src", "main", "resources",
			"fabric.mod.json");
		if(Files.exists(fabricModJson))
		{
			JsonObject mod = JsonParser
				.parseReader(Files.newBufferedReader(fabricModJson,
					StandardCharsets.UTF_8))
				.getAsJsonObject();
			JsonArray mixins = mod.getAsJsonArray("mixins");
			if(mixins != null && mixins.size() > 0)
				return mixins.get(0).getAsString();
		}

		Path resources = Path.of("src", "main", "resources");
		try(var files = Files.list(resources))
		{
			return files.map(path -> path.getFileName().toString())
				.filter(name -> name.endsWith(".mixins.json")).findFirst()
				.orElseThrow(() -> new IOException(
					"在 src/main/resources 下找不到 *.mixins.json"));
		}
	}

	private void addEntries(Set<String> target, JsonArray entries)
	{
		if(entries == null)
			return;
		entries.forEach(entry -> target.add(entry.getAsString()));
	}
}