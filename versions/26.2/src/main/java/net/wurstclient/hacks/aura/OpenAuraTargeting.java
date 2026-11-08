/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package net.wurstclient.hacks.aura;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.WurstClient;

/** Separates attack selection, rotation-only selection and attack history. */
public final class OpenAuraTargeting
{
	private final Map<LivingEntity, OpenAura.AttackRecord> history =
		new IdentityHashMap<>();
	private Object player;
	private Object world;

	public Selection update(OpenAura.Config attackConfig,
		OpenAura.Config rotationConfig, boolean switchMode)
	{
		if(player != WurstClient.MC.player || world != WurstClient.MC.level)
			reset();
		player = WurstClient.MC.player;
		world = WurstClient.MC.level;
		if(player == null || world == null)
			return new Selection(null, null);

		history.keySet().removeIf(entity -> !entity.isAlive()
			|| WurstClient.MC.level.getEntity(entity.getId()) != entity);
		OpenAura.Target attack = OpenAura.select(OpenAura.collect(attackConfig),
			switchMode, id -> history.get(WurstClient.MC.level.getEntity(id)));
		OpenAura.Target rotation = attack != null ? attack
			: OpenAura.selectNearest(OpenAura.collect(rotationConfig));
		return new Selection(attack, rotation);
	}

	public OpenAura.Target revalidate(OpenAura.Target planned,
		OpenAura.Config config)
	{
		if(planned == null || player != WurstClient.MC.player
			|| world != WurstClient.MC.level)
			return null;
		OpenAura.Target current = OpenAura.target(planned.entity, config);
		if(current != null)
			current.restoreAttackState(history.get(current.entity));
		return current;
	}

	public void onAttack(OpenAura.Target target, boolean reset)
	{
		target.onAttack(reset);
		history.put(target.entity, target.attackRecord());
	}

	public void reset()
	{
		history.clear();
		player = null;
		world = null;
	}

	public record Selection(OpenAura.Target attack, OpenAura.Target rotation)
	{}
}
