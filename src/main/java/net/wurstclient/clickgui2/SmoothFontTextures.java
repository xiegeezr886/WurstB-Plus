/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * Tracks which {@link AbstractTexture}s belong to the bundled CJK custom
 * fonts (PingFang SC, Rise SF Pro). These font glyphs are rasterized at
 * oversample and drawn with fractional matrix scales; under Minecraft's
 * default GL_NEAREST sampling that produces hard "pixel dots" on dense CJK
 * strokes. We route those textures through linear filtering instead.
 *
 * <p>普通运行类，必须放在 Mixin 专用包（net.wurstclient.mixin）之外，
 * 否则 Mixin 0.8.5 会在被 Mixin 直接引用时报包隔离错误。</p>
 */
public final class SmoothFontTextures
{
	private static final Set<AbstractTexture> SMOOTH =
		Collections.newSetFromMap(new ConcurrentHashMap<>());

	private SmoothFontTextures()
	{
	}

	public static void register(AbstractTexture texture)
	{
		SMOOTH.add(texture);
	}

	public static boolean isSmooth(AbstractTexture texture)
	{
		return SMOOTH.contains(texture);
	}
}
