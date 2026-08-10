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
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
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
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.*;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.FakePlayerEntity;

@DontSaveState
public final class ProtectHack extends Hack
	implements UpdateListener, RenderListener
{
	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();
	
	private final SwingHandSetting swingHand = new SwingHandSetting(
		SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);
	
	private final CheckboxSetting useAi =
		new CheckboxSetting("Use AI (experimental)", false);
	
	private final PauseAttackOnContainersSetting pauseOnContainers =
		new PauseAttackOnContainersSetting(true);
	
	private final EntityFilterList entityFilters =
		new EntityFilterList(FilterPlayersSetting.genericCombat(false),
			FilterSleepingSetting.genericCombat(false),
			FilterFlyingSetting.genericCombat(0),
			FilterHostileSetting.genericCombat(false),
			FilterNeutralSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF),
			FilterPassiveSetting.genericCombat(false),
			FilterPassiveWaterSetting.genericCombat(false),
			FilterBabiesSetting.genericCombat(false),
			FilterBatsSetting.genericCombat(false),
			FilterSlimesSetting.genericCombat(false),
			FilterPetsSetting.genericCombat(false),
			FilterVillagersSetting.genericCombat(false),
			FilterZombieVillagersSetting.genericCombat(false),
			FilterGolemsSetting.genericCombat(false),
			FilterPiglinsSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF),
			FilterZombiePiglinsSetting
				.genericCombat(FilterZombiePiglinsSetting.Mode.OFF),
			FilterEndermenSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF),
			FilterShulkersSetting.genericCombat(false),
			FilterAllaysSetting.genericCombat(false),
			FilterInvisibleSetting.genericCombat(false),
			FilterNamedSetting.genericCombat(false),
			FilterShulkerBulletSetting.genericCombat(false),
			FilterArmorStandsSetting.genericCombat(false),
			FilterCrystalsSetting.genericCombat(true));
	
	private EntityPathFinder pathFinder;
	private PathProcessor processor;
	private int ticksProcessing;
	
	private Entity friend;
	private Entity enemy;
	private Entity attackTarget;
	
	private double distanceF = 2;
	private double distanceE = 3;
	
	public ProtectHack()
	{
		super("Protect");
		
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(speed);
		addSetting(swingHand);
		addSetting(useAi);
		addSetting(pauseOnContainers);
		
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	public String getRenderName()
	{
		if(friend != null)
			return getTranslatedName() + " [" + friend.getName().getString()
				+ "]";
		return getTranslatedName();
	}
	
	@Override
	protected void onEnable()
	{
		if(MC.player == null || MC.level == null)
		{
			setEnabled(false);
			return;
		}

		WURST.getHax().followHack.setEnabled(false);
		WURST.getHax().tunnellerHack.setEnabled(false);
		
		// set friend
		if(friend == null)
		{
			Stream<Entity> stream = StreamSupport
				.stream(MC.level.entitiesForRendering().spliterator(), true)
				.filter(LivingEntity.class::isInstance)
				.filter(
					e -> !e.isRemoved() && ((LivingEntity)e).getHealth() > 0)
				.filter(e -> e != MC.player)
				.filter(e -> !(e instanceof FakePlayerEntity));
			friend = stream
				.min(Comparator
					.comparingDouble(e -> MC.player.distanceToSqr(e)))
				.orElse(null);
		}
		if(friend == null)
		{
			setEnabled(false);
			return;
		}

		pathFinder = null;
		processor = null;
		attackTarget = null;
		
		speed.resetTimer();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		pathFinder = null;
		processor = null;
		ticksProcessing = 0;
		PathProcessor.releaseControls();
		
		enemy = null;
		attackTarget = null;
		
		if(friend != null)
		{
			MC.options.keyUp.setDown(false);
			friend = null;
		}
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
			suspendAutomation();
			return;
		}
		
		// check if player died, friend died or disappeared
		if(friend == null || friend.isRemoved()
			|| !(friend instanceof LivingEntity)
			|| ((LivingEntity)friend).getHealth() <= 0
			|| MC.player.getHealth() <= 0)
		{
			friend = null;
			enemy = null;
			setEnabled(false);
			return;
		}
		
		// set enemy
		Stream<Entity> stream = EntityUtils.getAttackableEntities()
			.filter(e -> MC.player.distanceToSqr(e) <= 36)
			.filter(e -> e != friend);
		
		stream = entityFilters.applyTo(stream);
		
		enemy = stream
			.min(
				Comparator.comparingDouble(e -> MC.player.distanceToSqr(e)))
			.orElse(null);
		
		Entity target =
			enemy == null || MC.player.distanceToSqr(friend) >= 24 * 24
				? friend : enemy;
		Entity nextAttackTarget = target == enemy ? enemy : null;
		if(nextAttackTarget != attackTarget)
		{
			attackTarget = nextAttackTarget;
			speed.resetTimer();
		}
		
		double distance = target == enemy ? distanceE : distanceF;
		
		if(useAi.isChecked())
		{
			// reset pathfinder
			if(pathFinder == null || pathFinder.entity != target
				|| (processor == null || processor.isDone() || ticksProcessing >= 10
				|| !pathFinder.isPathStillValid(processor.getIndex()))
					&& (pathFinder.isDone() || pathFinder.isFailed()))
			{
				pathFinder = new EntityPathFinder(target, distance);
				processor = null;
				ticksProcessing = 0;
			}
			
			// find path
			if(!pathFinder.isDone() && !pathFinder.isFailed())
			{
				PathProcessor.lockControls();
				WURST.getRotationFaker()
					.faceVectorClient(target.getBoundingBox().getCenter());
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
			if(MC.player.isInWater() && MC.player.getY() < target.getY())
				MC.player.push(0, 0.04, 0);
			
			// control height if flying
			if(!MC.player.onGround()
				&& (MC.player.getAbilities().flying
					|| WURST.getHax().flightHack.isEnabled())
				&& MC.player.distanceToSqr(target.getX(), MC.player.getY(),
					target.getZ()) <= MC.player.distanceToSqr(
						MC.player.getX(), target.getY(), MC.player.getZ()))
			{
				if(MC.player.getY() > target.getY() + 1D)
					MC.options.keyShift.setDown(true);
				else if(MC.player.getY() < target.getY() - 1D)
					MC.options.keyJump.setDown(true);
			}else
			{
				MC.options.keyShift.setDown(false);
				MC.options.keyJump.setDown(false);
			}
			
			// follow target
			WURST.getRotationFaker()
				.faceVectorClient(target.getBoundingBox().getCenter());
			MC.options.keyUp.setDown(MC.player.distanceTo(
				target) > (target == friend ? distanceF : distanceE));
		}
		
		if(target == enemy)
		{
			WURST.getHax().autoSwordHack.setSlot(enemy);
			
			// check cooldown
			if(!speed.isTimeToAttack())
				return;
			if(MC.player.distanceToSqr(enemy) > distanceE * distanceE)
				return;
			
			// attack enemy
			MC.gameMode.attack(MC.player, enemy);
			swingHand.swing(InteractionHand.MAIN_HAND);
			speed.resetTimer();
		}
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(!useAi.isChecked() || pathFinder == null)
			return;
		
		PathCmd pathCmd = WURST.getCmds().pathCmd;
		pathFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
			pathCmd.isDepthTest());
	}
	
	public void setFriend(Entity friend)
	{
		this.friend = friend;
	}

	private void suspendAutomation()
	{
		if(attackTarget != null)
			speed.resetTimer();
		attackTarget = null;
		enemy = null;
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
		private double distanceSq;
		
		public EntityPathFinder(Entity entity, double distance)
		{
			super(BlockPos.containing(entity.position()));
			this.entity = entity;
			distanceSq = distance * distance;
			setThinkTime(1);
		}
		
		@Override
		protected boolean checkDone()
		{
			return done =
				entity.distanceToSqr(Vec3.atCenterOf(current)) <= distanceSq;
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
