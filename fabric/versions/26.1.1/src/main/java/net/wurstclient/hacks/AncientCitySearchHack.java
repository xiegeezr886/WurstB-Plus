/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from FO (CC0-1.0).
 */
package net.wurstclient.hacks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.BlockPos;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.ChatUtils;

/**
 * Locates the nearest ancient city with the vanilla structure command and
 * optionally walks there with Baritone.
 *
 * <p>
 * Simplified from FO's FOAncientCitySearch: the bundled cubiomes JNI library,
 * jigsaw replay, loot-table simulation, chest highlighting and Xaero waypoint
 * integration are out of scope. This version only sends
 * {@code /locate structure minecraft:ancient_city}, parses the coordinates
 * from chat and starts a Baritone path. It therefore needs command permission
 * (cheats or operator rights) and cannot predict which chests contain
 * enchanted golden apples or Swift Sneak.
 */
@SearchTags({"ancient city", "ancient city search", "locate structure",
	"city finder", "structure search"})
public final class AncientCitySearchHack extends Hack
	implements UpdateListener, ChatInputListener
{
	private static final Pattern COORDINATES = Pattern.compile(
		"(-?\\d{1,9})\\s*[,\\uFF0C]\\s*(?:~|-?\\d{1,9})\\s*[,\\uFF0C]\\s*(-?\\d{1,9})");

	private final TextFieldSetting command = new TextFieldSetting(
		"Locate command", "Command sent without the leading slash.",
		"locate structure minecraft:ancient_city");

	private final CheckboxSetting sendOnEnable = new CheckboxSetting(
		"Send on enable", "Runs the locate command when the hack is enabled.",
		true);

	private final CheckboxSetting searchNow = new CheckboxSetting("Search now",
		"Starts another search, then switches itself off.", false);

	private final CheckboxSetting autoWalk = new CheckboxSetting("Auto walk",
		"Uses Baritone to walk to the located city.", true);

	private final SliderSetting targetY = new SliderSetting("Target Y",
		"Y coordinate used for the Baritone goal. Ancient cities are near -51.",
		-51, -64, 320, 1, ValueDisplay.INTEGER);

	private final SliderSetting retryDelay = new SliderSetting("Retry delay",
		"Ticks to wait for a reply before retrying the command.", 100, 40,
		1200, 20, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting maxAttempts = new SliderSetting("Max attempts",
		"How many times the command is sent before giving up.", 5, 1, 20, 1,
		ValueDisplay.INTEGER);

	private int tickCounter;
	private int attempts;
	private int nextActionTick;
	private boolean awaitingReply;
	private boolean finished;
	private boolean walkingStarted;

	public AncientCitySearchHack()
	{
		super("AncientCitySearch");
		setCategory(Category.OTHER);
		addSetting(command);
		addSetting(sendOnEnable);
		addSetting(searchNow);
		addSetting(autoWalk);
		addSetting(targetY);
		addSetting(retryDelay);
		addSetting(maxAttempts);

		targetY.visibleWhen(autoWalk::isChecked);
	}

	@Override
	protected void onEnable()
	{
		tickCounter = 0;
		attempts = 0;
		nextActionTick = sendOnEnable.isChecked() ? 20 : Integer.MAX_VALUE;
		awaitingReply = false;
		finished = false;
		walkingStarted = false;

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(ChatInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(ChatInputListener.class, this);

		if(walkingStarted && BaritoneUtils.IS_AVAILABLE)
			BaritoneUtils.stop();

		awaitingReply = false;
		finished = false;
		walkingStarted = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		tickCounter++;

		if(searchNow.isChecked())
		{
			searchNow.setChecked(false);
			attempts = 0;
			awaitingReply = false;
			finished = false;
			walkingStarted = false;
			nextActionTick = tickCounter + 20;
			return;
		}

		if(finished)
			return;

		if(tickCounter < nextActionTick)
			return;

		if(awaitingReply)
			awaitingReply = false;

		if(attempts >= maxAttempts.getValueI())
		{
			ChatUtils.warning(
				"AncientCitySearch: no reply after " + attempts
					+ " attempts. Check command permissions.");
			finished = true;
			return;
		}

		sendLocateCommand();
	}

	private void sendLocateCommand()
	{
		if(MC.getConnection() == null)
			return;

		String value = command.getValue().trim();
		if(value.startsWith("/"))
			value = value.substring(1);
		if(value.isEmpty())
		{
			ChatUtils.warning("AncientCitySearch: the locate command is empty.");
			finished = true;
			return;
		}

		attempts++;
		awaitingReply = true;
		nextActionTick = tickCounter + retryDelay.getValueI();

		ChatUtils.message("AncientCitySearch: running /" + value + " (attempt "
			+ attempts + "/" + maxAttempts.getValueI() + ")");
		MC.getConnection().sendCommand(value);
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		if(!awaitingReply || finished)
			return;

		String message = event.getComponent().getString();
		Matcher matcher = COORDINATES.matcher(message);
		if(!matcher.find())
			return;

		int x = Integer.parseInt(matcher.group(1));
		int z = Integer.parseInt(matcher.group(2));

		awaitingReply = false;
		finished = true;
		ChatUtils.message("AncientCitySearch: nearest ancient city at " + x
			+ ", " + z + ".");

		if(!autoWalk.isChecked())
			return;

		if(!BaritoneUtils.IS_AVAILABLE)
		{
			ChatUtils
				.warning("AncientCitySearch: Baritone is not available.");
			return;
		}

		walkingStarted = true;
		BaritoneUtils.walkTo(new BlockPos(x, targetY.getValueI(), z));
	}
}
