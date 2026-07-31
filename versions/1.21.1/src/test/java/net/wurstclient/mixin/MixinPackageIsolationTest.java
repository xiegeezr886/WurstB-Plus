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
		JsonObject config;
		try(var stream = getClass().getResourceAsStream("/wurst.mixins.json"))
		{
			config = JsonParser.parseReader(new InputStreamReader(stream,
				StandardCharsets.UTF_8)).getAsJsonObject();
		}

		Set<String> allowedClasses = new HashSet<>();
		addEntries(allowedClasses, config.getAsJsonArray("mixins"));
		addEntries(allowedClasses, config.getAsJsonArray("client"));
		String plugin = config.get("plugin").getAsString();
		allowedClasses.add(plugin.substring(plugin.lastIndexOf('.') + 1));

		Path mixinSources = Path.of("src", "main", "java", "net",
			"wurstclient", "mixin");
		try(var files = Files.list(mixinSources))
		{
			files.filter(path -> path.getFileName().toString().endsWith(".java"))
				.map(path -> path.getFileName().toString().replace(".java", ""))
				.forEach(className -> assertTrue(
					allowedClasses.contains(className),
					() -> className + " is not declared in wurst.mixins.json"));
		}
	}

	private void addEntries(Set<String> target, JsonArray entries)
	{
		if(entries == null)
			return;
		entries.forEach(entry -> target.add(entry.getAsString()));
	}
}
