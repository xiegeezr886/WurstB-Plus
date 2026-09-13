package net.wurstclient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class FontResourcesTest
{
	private static final String FONT_ROOT = "/assets/minecraft/font/";

	@Test
	void embeddedFontDefinitionsReferenceBundledTextures() throws IOException
	{
		for(String font : new String[]{"default", "uniform"})
		{
			JsonObject definition = readDefinition(font);
			JsonArray providers = definition.getAsJsonArray("providers");
			assertEquals(19, providers.size(), font);

			for(var providerElement : providers)
			{
				JsonObject provider = providerElement.getAsJsonObject();
				String type = provider.get("type").getAsString();

				if("ttf".equals(type))
				{
					String file = provider.get("file").getAsString();
					assertTrue(file.startsWith("wurst:font/"), file);
					String fontPath =
						"/assets/wurst/" + file.substring("wurst:".length());
					try(InputStream fontStream =
						getClass().getResourceAsStream(fontPath))
					{
						assertNotNull(fontStream, fontPath);
					}
					continue;
				}

				assertEquals("bitmap", type);

				String file = provider.get("file").getAsString();
				assertTrue(file.startsWith("minecraft:font/"), file);
				String texturePath = "/assets/minecraft/textures/"
					+ file.substring("minecraft:".length());
				try(InputStream texture =
					getClass().getResourceAsStream(texturePath))
				{
					assertNotNull(texture, texturePath);
				}
			}
		}
	}

	private JsonObject readDefinition(String font) throws IOException
	{
		String resourcePath = FONT_ROOT + font + ".json";
		try(InputStream stream = getClass().getResourceAsStream(resourcePath))
		{
			assertNotNull(stream, resourcePath);
			return JsonParser
				.parseReader(new InputStreamReader(stream, UTF_8)).getAsJsonObject();
		}
	}
}
