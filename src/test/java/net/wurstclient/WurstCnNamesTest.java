/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.junit.jupiter.api.Test;

public final class WurstCnNamesTest
{
	@Test
	void loadsAllWurstCnHackNames() throws IOException
	{
		String resourcePath =
			"/assets/wurst/translations/zh_cn_names.json";
		try(InputStream stream = getClass().getResourceAsStream(resourcePath))
		{
			assertNotNull(stream);
			JsonObject names = JsonParser
				.parseReader(new InputStreamReader(stream, UTF_8)).getAsJsonObject();

			assertEquals(192, names.size());
			assertEquals("空中跳跃",
				names.get("hack.name.airjump").getAsString());
			assertEquals("杀戮光环",
				names.get("hack.name.killaura").getAsString());
			assertEquals("玩家透视",
				names.get("hack.name.playeresp").getAsString());
			assertEquals("玩家光环",
				names.get("hack.name.playerhalo").getAsString());
			assertEquals("无未命中冷却",
				names.get("hack.name.nomisscooldown").getAsString());
			assertEquals("弹射物反击",
				names.get("hack.name.projectilepuncher").getAsString());
			assertEquals("快速下台阶",
				names.get("hack.name.reversestep").getAsString());
			assertEquals("右键连点",
				names.get("hack.name.rightclicker").getAsString());
			assertEquals("疾跑重置",
				names.get("hack.name.wtap").getAsString());
			assertEquals("防强制旋转",
				names.get("hack.name.norotate").getAsString());
			assertEquals("增强击退",
				names.get("hack.name.superknockback").getAsString());
			assertEquals("载具弹射",
				names.get("hack.name.vehicleboost").getAsString());
			assertTrue(names.keySet().stream()
				.allMatch(key -> key.startsWith("hack.name.")));
			assertFalse(names.entrySet().stream()
				.anyMatch(entry -> entry.getValue().getAsString().isBlank()));
		}
	}
}
