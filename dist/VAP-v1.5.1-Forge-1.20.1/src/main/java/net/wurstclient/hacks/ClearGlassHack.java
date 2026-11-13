/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ShouldDrawSideListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.BlockUtils;

/**
 * Hides glass block faces so that players can see through them.
 *
 * <p>Soar replaced the glass render type with a translucent one. This port
 * simply skips glass faces, which produces the same practical "clear glass"
 * result without version-specific renderer hooks.
 */
@SearchTags({"clear glass", "clearglass", "invisible glass", "transparent glass"})
public final class ClearGlassHack extends Hack
	implements ShouldDrawSideListener, UpdateListener
{
	private final CheckboxSetting normal = new CheckboxSetting("Normal glass",
		"Hides normal glass and glass panes.", true);

	private final CheckboxSetting stained = new CheckboxSetting(
		"Stained glass", "Hides stained glass and stained glass panes.", true);

	private boolean prevNormal;
	private boolean prevStained;

	public ClearGlassHack()
	{
		super("ClearGlass");
		setCategory(Category.RENDER);
		addSetting(normal);
		addSetting(stained);
	}

	@Override
	protected void onEnable()
	{
		prevNormal = normal.isChecked();
		prevStained = stained.isChecked();
		EVENTS.add(ShouldDrawSideListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		reloadChunks();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ShouldDrawSideListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		reloadChunks();
	}

	@Override
	public void onUpdate()
	{
		if(prevNormal == normal.isChecked()
			&& prevStained == stained.isChecked())
			return;

		prevNormal = normal.isChecked();
		prevStained = stained.isChecked();
		reloadChunks();
	}

	@Override
	public void onShouldDrawSide(ShouldDrawSideEvent event)
	{
		if(MC.level == null)
			return;

		String name = BlockUtils.getName(event.getState().getBlock());
		boolean isNormal =
			name.equals("minecraft:glass") || name.equals("minecraft:glass_pane");
		boolean isStained = name.contains("stained_glass");

		if(isNormal && normal.isChecked()
			|| isStained && stained.isChecked())
			event.setRendered(false);
	}

	private void reloadChunks()
	{
		try
		{
			Object renderer = MC.getClass().getField("levelRenderer").get(MC);
			renderer.getClass().getMethod("allChanged").invoke(renderer);
			return;
		}catch(Exception ignored)
		{}

		try
		{
			Object renderer = MC.getClass().getField("levelExtractor").get(MC);
			renderer.getClass().getMethod("allChanged").invoke(renderer);
		}catch(Exception ignored)
		{}
	}
}
