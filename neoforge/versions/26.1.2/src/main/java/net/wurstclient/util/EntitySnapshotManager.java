package net.wurstclient.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.wurstclient.WurstClient;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;

public final class EntitySnapshotManager
	implements UpdateListener, WorldChangeListener
{
	private volatile Snapshot current = Snapshot.empty();

	public void start()
	{
		WurstClient.INSTANCE.getEventManager().add(UpdateListener.class, this);
		WurstClient.INSTANCE.getEventManager().add(WorldChangeListener.class,
			this);
	}

	public void stop()
	{
		WurstClient.INSTANCE.getEventManager().remove(UpdateListener.class,
			this);
		WurstClient.INSTANCE.getEventManager().remove(WorldChangeListener.class,
			this);
		current = Snapshot.empty();
	}

	@Override
	public void onUpdate()
	{
		ClientLevel level = WurstClient.MC.level;
		if(level == null)
		{
			current = Snapshot.empty();
			return;
		}

		ArrayList<Entity> entities = new ArrayList<>();
		ArrayList<AbstractClientPlayer> players = new ArrayList<>();
		ArrayList<LivingEntity> livingEntities = new ArrayList<>();
		ArrayList<ItemEntity> items = new ArrayList<>();
		for(Entity entity : level.entitiesForRendering())
		{
			entities.add(entity);
			if(entity instanceof AbstractClientPlayer player)
				players.add(player);
			if(entity instanceof LivingEntity livingEntity)
				livingEntities.add(livingEntity);
			if(entity instanceof ItemEntity item)
				items.add(item);
		}

		current = new Snapshot(level.getGameTime(), List.copyOf(entities),
			List.copyOf(players), List.copyOf(livingEntities),
			List.copyOf(items));
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		current = Snapshot.empty();
	}

	public Snapshot getCurrent()
	{
		return current;
	}

	public record Snapshot(long gameTime, List<Entity> entities,
		List<AbstractClientPlayer> players,
		List<LivingEntity> livingEntities, List<ItemEntity> items)
	{
		public Snapshot
		{
			entities = List.copyOf(entities);
			players = List.copyOf(players);
			livingEntities = List.copyOf(livingEntities);
			items = List.copyOf(items);
		}

		private static Snapshot empty()
		{
			return new Snapshot(0, List.of(), List.of(), List.of(), List.of());
		}
	}
}
