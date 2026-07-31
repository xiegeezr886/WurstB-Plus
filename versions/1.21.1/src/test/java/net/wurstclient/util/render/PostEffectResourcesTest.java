package net.wurstclient.util.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class PostEffectResourcesTest
{
	private static final Path ROOT =
		Path.of("src/main/resources/assets/wurst/shaders");

	@Test
	void everyEffectHasAValidPostChain() throws IOException
	{
		for(PostEffectQueue.Effect effect : PostEffectQueue.Effect.values())
		{
			String name = effect.toString().toLowerCase();
			JsonObject json = JsonParser.parseString(Files.readString(
				ROOT.resolve("post/target_" + name + ".json")))
				.getAsJsonObject();
			assertEquals(2, json.getAsJsonArray("passes").size());
			assertTrue(json.getAsJsonArray("targets").contains(
				JsonParser.parseString("\"swap\"")));
		}
	}

	@Test
	void targetProgramDeclaresAllRuntimeUniforms() throws IOException
	{
		String json = Files.readString(
			ROOT.resolve("program/target_effect.json"));
		assertTrue(json.contains("\"InSize\""));
		assertTrue(json.contains("\"OutSize\""));
		assertTrue(json.contains("\"Time\""));
		assertTrue(json.contains("\"Mode\""));
	}
}
