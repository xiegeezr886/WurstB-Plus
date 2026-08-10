/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.ai.PathFinder;
import net.wurstclient.ai.PathPos;
import net.wurstclient.ai.PathProcessor;
import net.wurstclient.commands.PathCmd;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.PauseAttackOnContainersSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.CombatRotationController;
import net.wurstclient.util.CombatTargetSession;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RotationQueue;
import net.wurstclient.util.RotationUtils;

@SearchTags({"fight bot"})
@DontSaveState
public final class FightBotHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Attack range (like Killaura)", 4.25, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();
	
	private final SwingHandSetting swingHand = new SwingHandSetting(
		SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);
	
	private final SliderSetting distance = new SliderSetting("Distance",
		"How closely to follow the target.\n"
			+ "This should be set to a lower value than Range.",
		3, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting useAi =
		new CheckboxSetting("Use AI (experimental)", false);
	
	private final PauseAttackOnContainersSetting pauseOnContainers =
		new PauseAttackOnContainersSetting(true);
	
	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();
	
	private EntityPathFinder pathFinder;
	private PathProcessor processor;
	private int ticksProcessing;
	private final CombatRotationController rotationController =
		new CombatRotationController(RotationQueue.Priority.COMBAT);
	private final CombatTargetSession<Entity> targetSession =
		new CombatTargetSession<>();
	
	public FightBotHack()
	{
		super("FightBot");
		
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(range);
		addSetting(speed);
		addSetting(swingHand);
		addSetting(distance);
		addSetting(useAi);
		addSetting(pauseOnContainers);
		
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		if(MC.player == null || MC.level == null)
		{
			setEnabled(false);
			return;
		}

		WURST.getHax().tunnellerHack.setEnabled(false);
		pathFinder = null;
		processor = null;
		targetSession.clear();
		speed.resetTimer();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		rotationController.start();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		rotationController.stop();
		suspendTargeting(false);
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
		{
			setEnabled(false);
			return;
		}

		speed.updateTimer();
		
		if(pauseOnContainers.shouldPause())
		{
			suspendTargeting(true);
			return;
		}
		
		// set entity
		Stream<Entity> stream = EntityUtils.getAttackableEntities();
		stream = entityFilters.applyTo(stream);
		
		Entity entity = stream
			.min(
				Comparator.comparingDouble(e -> MC.player.distanceToSqr(e)))
			.orElse(null);
		if(entity == null)
		{
			suspendTargeting(true);
			return;
		}
		CombatTargetSession.Selection<Entity> selection =
			targetSession.track(entity);
		if(selection.changed())
		{
			speed.resetTimer();
			resetPath();
		}
		
		WURST.getHax().autoSwordHack.setSlot(entity);
		
		if(useAi.isChecked())
		{
			// reset pathfinder
			if(pathFinder == null || pathFinder.entity != entity
				|| (processor == null || processor.isDone() || ticksProcessing >= 10
				|| !pathFinder.isPathStillValid(processor.getIndex()))
					&& (pathFinder.isDone() || pathFinder.isFailed()))
			{
				pathFinder = new EntityPathFinder(entity);
				processor = null;
				ticksProcessing = 0;
			}
			
			// find path
			if(!pathFinder.isDone() && !pathFinder.isFailed())
			{
				PathProcessor.lockControls();
				rotationController.request(RotationUtils.getNeededRotations(
					entity.getBoundingBox().getCenter()));
				pathFinder.think();
				pathFinder.formatPath();
				processor = pathFinder.getProcessor();
			}
			
			// process path
			if(processor != null && !processor.isDone())
			{
				processor.process();
				ticksProcessing++;
			}
		}else
		{
			if(pathFinder != null || processor != null)
			{
				PathProcessor.releaseControls();
				resetPath();
			}

			// jump if necessary
			if(MC.player.horizontalCollision && MC.player.onGround())
				MC.player.jumpFromGround();
			
			// swim up if necessary
			if(MC.player.isInWater() && MC.player.getY() < entity.getY())
				MC.player.push(0, 0.04, 0);
			
			// control height if flying
			if(!MC.player.onGround()
				&& (MC.player.getAbilities().flying
					|| WURST.getHax().flightHack.isEnabled())
				&& MC.player.distanceToSqr(entity.getX(), MC.player.getY(),
					entity.getZ()) <= MC.player.distanceToSqr(
						MC.player.getX(), entity.getY(), MC.player.getZ()))
			{
				if(MC.player.getY() > entity.getY() + 1D)
					MC.options.keyShift.setDown(true);
				else if(MC.player.getY() < entity.getY() - 1D)
					MC.options.keyJump.setDown(true);
			}else
			{
				MC.options.keyShift.setDown(false);
				MC.options.keyJump.setDown(false);
			}
			
			// follow entity
			MC.options.keyUp.setDown(
				MC.player.distanceTo(entity) > distance.getValueF());
			rotationController.request(RotationUtils.getNeededRotations(
				entity.getBoundingBox().getCenter()));
		}
		
		// check cooldown
		if(!speed.isTimeToAttack())
			return;
		
		// check range
		if(MC.player.distanceToSqr(entity) > Math.pow(range.getValue(), 2))
			return;
		
		// attack entity
		MC.gameMode.attack(MC.player, entity);
		swingHand.swing(InteractionHand.MAIN_HAND);
		speed.resetTimer();
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(pathFinder == null)
			return;
		PathCmd pathCmd = WURST.getCmds().pathCmd;
		pathFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
			pathCmd.isDepthTest());
	}

	private void suspendTargeting(boolean resetTimer)
	{
		if(resetTimer && targetSession.getTarget() != null)
			speed.resetTimer();
		targetSession.clear();
		rotationController.clear();
		PathProcessor.releaseControls();
		resetPath();
	}

	private void resetPath()
	{
		pathFinder = null;
		processor = null;
		ticksProcessing = 0;
	}
	
	private class EntityPathFinder extends PathFinder
	{
		private final Entity entity;
		
		public EntityPathFinder(Entity entity)
		{
			super(BlockPos.containing(entity.position()));
			this.entity = entity;
			setThinkTime(1);
		}
		
		@Override
		protected boolean checkDone()
		{
			return done =
				entity.distanceToSqr(Vec3.atCenterOf(current)) <= Math
					.pow(distance.getValue(), 2);
		}
		
		@Override
		public ArrayList<PathPos> formatPath()
		{
			if(!done)
				failed = true;
			
			return super.formatPath();
		}
	}
}
