package net.wurstclient.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class SodiumMixinCompatibilityTest
{
	@Test
	void sodiumMixinsAreDisabledToAvoidEarlyLoadingInLargeModpacks()
		throws Exception
	{
		JsonObject config;
		try(var stream = getClass().getResourceAsStream("/wurst.mixins.json"))
		{
			config = JsonParser.parseReader(new InputStreamReader(stream,
				StandardCharsets.UTF_8)).getAsJsonObject();
		}

		var clientMixins = config.getAsJsonArray("client");
		assertFalse(clientMixins.asList().stream()
			.anyMatch(entry -> entry.getAsString().startsWith("Sodium")));
	}
}
