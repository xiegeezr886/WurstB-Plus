/* Adapted from Meteor Client. Copyright (c) Meteor Development. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.events.MouseButtonListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.ChatUtils;

@SearchTags({"auto walk", "smart walk", "directional walk"})
public final class AutoWalkHack extends Hack
	implements UpdateListener, KeyPressListener, MouseButtonListener, WorldChangeListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Simple holds a movement key. Smart uses Baritone to walk in your initial direction.",
		Mode.values(), Mode.SIMPLE);
	private final EnumSetting<Direction> direction = new EnumSetting<>("Direction",
		"Direction used by Simple mode.", Direction.values(), Direction.FORWARDS);
	private final CheckboxSetting disableOnInput = new CheckboxSetting(
		"Disable on input", "Stops on manual movement key or mouse button presses.", false);
	private final CheckboxSetting disableOnY = new CheckboxSetting(
		"Disable on Y change", "Stops Simple mode when your height changes.", false);
	private final CheckboxSetting waitForChunks = new CheckboxSetting(
		"Wait for chunks", "Stops horizontal movement toward unloaded chunks.", true);
	private KeyMapping pressed;
	private Mode activeMode;
	private boolean startedBaritone;
	private float goalYaw;
	private int goalCooldown;
	private ClientLevel world;
	private double previousY;

	public AutoWalkHack()
	{
		super("AutoWalk");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(direction);
		addSetting(disableOnInput);
		addSetting(disableOnY);
		addSetting(waitForChunks);
	}

	@Override
	protected void onEnable()
	{
		activeMode = null;
		world = null;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(KeyPressListener.class, this);
		EVENTS.add(MouseButtonListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(KeyPressListener.class, this);
		EVENTS.remove(MouseButtonListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		releaseKey();
		stopPath();
		world = null;
	}

	private void releaseKey()
	{
		if(pressed != null)
			IKeyBinding.get(pressed).resetPressedState();
		pressed = null;
	}

	private void stopPath()
	{
		if(startedBaritone)
			BaritoneUtils.stop();
		startedBaritone = false;
		goalCooldown = 0;
	}

	@Override
	public void onWorldChange(ClientLevel nextWorld)
	{
		releaseKey();
		stopPath();
		world = null;
		activeMode = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
		{
			releaseKey();
			return;
		}
		if(world != MC.level)
		{
			onWorldChange(MC.level);
			world = MC.level;
			previousY = MC.player.getY();
		}
		if(activeMode != mode.getSelected())
		{
			releaseKey();
			stopPath();
			activeMode = mode.getSelected();
			goalYaw = MC.player.getYRot();
		}
		if(activeMode == Mode.SMART)
		{
			if(!BaritoneUtils.IS_AVAILABLE)
			{
				ChatUtils.error("Smart AutoWalk requires Baritone.");
				setEnabled(false);
				return;
			}
			if(goalCooldown > 0)
				goalCooldown--;
			else if(!BaritoneUtils.isPathing())
			{
				BaritoneUtils.walkDirection(goalYaw, 200);
				startedBaritone = true;
				goalCooldown = 20;
			}
			return;
		}
		if(disableOnY.isChecked() && MC.player.getY() != previousY)
		{
			setEnabled(false);
			return;
		}
		previousY = MC.player.getY();
		KeyMapping next = switch(direction.getSelected())
		{
			case FORWARDS -> MC.options.keyUp;
			case BACKWARDS -> MC.options.keyDown;
			case LEFT -> MC.options.keyLeft;
			case RIGHT -> MC.options.keyRight;
		};
		if(pressed != next)
			releaseKey();
		pressed = next;
		pressed.setDown(true);
	}

	private KeyMapping[] movementKeys()
	{
		return new KeyMapping[]{MC.options.keyUp, MC.options.keyDown,
			MC.options.keyLeft, MC.options.keyRight, MC.options.keyShift, MC.options.keyJump};
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		if(!disableOnInput.isChecked() || event.getAction() != InputConstants.PRESS
			|| MC.screen != null)
			return;
		for(KeyMapping key : movementKeys())
			if(key.matches(event.getKeyCode(), event.getScanCode()))
			{
				setEnabled(false);
				return;
			}
	}

	@Override
	public void onMouseButton(int button, int action)
	{
		if(!disableOnInput.isChecked() || action != InputConstants.PRESS
			|| MC.screen != null)
			return;
		for(KeyMapping key : movementKeys())
			if(key.matchesMouse(button))
			{
				setEnabled(false);
				return;
			}
	}

	public Vec3 limitMovement(Vec3 movement)
	{
		if(!isEnabled() || mode.getSelected() != Mode.SIMPLE
			|| !waitForChunks.isChecked() || MC.player == null || MC.level == null)
			return movement;
		int x = Mth.floor(MC.player.getX() + movement.x * 2) >> 4;
		int z = Mth.floor(MC.player.getZ() + movement.z * 2) >> 4;
		return MC.level.getChunkSource().hasChunk(x, z) ? movement
			: new Vec3(0, movement.y, 0);
	}

	private enum Mode {SIMPLE, SMART}
	private enum Direction {FORWARDS, BACKWARDS, LEFT, RIGHT}
}
