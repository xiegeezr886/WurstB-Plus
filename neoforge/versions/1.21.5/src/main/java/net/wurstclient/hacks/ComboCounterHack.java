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

import net.minecraft.world.entity.Entity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD counter of consecutive hits on the same entity.
 *
 * <p>Simplification: Soar counted a hit only after the server confirmed damage
 * to the attacked entity. The local API has no damage-confirmation event, so
 * this port counts each attack on the same target within the timeout window.
 */
@SearchTags({"combo counter", "combo", "hit counter", "hits", "killstreak"})
public final class ComboCounterHack extends Hack
	implements PlayerAttacksEntityListener, UpdateListener, GUIRenderListener
{
	private static final long COMBO_TIMEOUT_NANOS = 2_000_000_000L;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the combo text.", 1.5, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the combo text.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the combo text.", 250, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the combo text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private Entity lastTarget;
	private int combo;
	private long lastHitNanos;

	public ComboCounterHack()
	{
		super("ComboCounter");
		setCategory(Category.COMBAT);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
		addSetting(bgColor);
		bgColor.visibleWhen(background::isChecked);
	}

	@Override
	protected void onEnable()
	{
		resetCombo();
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		resetCombo();
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		long now = System.nanoTime();
		if(target == lastTarget && now - lastHitNanos <= COMBO_TIMEOUT_NANOS)
			combo++;
		else
			combo = 1;

		lastTarget = target;
		lastHitNanos = now;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		// Being hit or dying breaks the combo.
		if(MC.player.hurtTime > 0 || MC.player.getHealth() <= 0)
		{
			resetCombo();
			return;
		}

		if(lastHitNanos != 0
			&& System.nanoTime() - lastHitNanos > COMBO_TIMEOUT_NANOS)
			resetCombo();
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(MC.player == null)
			return;

		String text = combo == 0 ? "No Combo" : combo + " Combo";
		double textScale = scale.getValue();
		double width = MC.font.width(text) * textScale;
		double height = MC.font.lineHeight * textScale;
		double x = posX.getValue();
		double y = posY.getValue();

		if(background.isChecked())
			context.fill((int)(x - 4), (int)(y - 4), (int)(x + width + 4),
				(int)(y + height + 4), bgColor.getColor().getRGB());

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)x, (float)y, 0F);
		pose.scale((float)textScale, (float)textScale, 1F);
		context.drawString(MC.font, text, 0, 0, 0xFFFFFFFF, true);
		pose.popPose();
	}

	private void resetCombo()
	{
		combo = 0;
		lastTarget = null;
		lastHitNanos = 0;
	}
}
