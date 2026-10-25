/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import java.nio.ByteBuffer;

/**
 * A font loaded into the NanoVG context from a classpath resource.
 *
 * <p>Ported from Soar Client v7.1.2
 * ({@code me.eldodebug.soar.management.nanovg.font.Font}) and changed to use
 * classpath resource paths instead of Minecraft {@code ResourceLocation}s,
 * so the same class works on every Minecraft version.
 */
public final class HudFont
{
	private final String name;
	private final String resource;
	private ByteBuffer buffer;
	private boolean loaded;

	HudFont(String name, String resource)
	{
		this.name = name;
		this.resource = resource;
	}

	public String getName()
	{
		return name;
	}

	public String getResource()
	{
		return resource;
	}

	ByteBuffer getBuffer()
	{
		return buffer;
	}

	void setBuffer(ByteBuffer buffer)
	{
		this.buffer = buffer;
	}

	public boolean isLoaded()
	{
		return loaded;
	}

	void setLoaded(boolean loaded)
	{
		this.loaded = loaded;
	}
}
