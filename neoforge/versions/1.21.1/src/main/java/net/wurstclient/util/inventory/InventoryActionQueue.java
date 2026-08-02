package net.wurstclient.util.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.wurstclient.WurstClient;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;

public final class InventoryActionQueue
	implements UpdateListener, WorldChangeListener
{
	private final ArrayList<ActionChain> pending = new ArrayList<>();
	private long sequence;
	private boolean started;

	public void start()
	{
		if(started)
			return;

		WurstClient.INSTANCE.getEventManager().add(UpdateListener.class, this);
		WurstClient.INSTANCE.getEventManager().add(WorldChangeListener.class,
			this);
		started = true;
	}

	public void stop()
	{
		if(!started)
			return;

		WurstClient.INSTANCE.getEventManager().remove(UpdateListener.class, this);
		WurstClient.INSTANCE.getEventManager().remove(WorldChangeListener.class,
			this);
		clear();
		started = false;
	}

	public synchronized boolean submit(Object owner, int priority,
		BooleanSupplier validator, Runnable... actions)
	{
		if(owner == null || actions.length == 0 || hasPending(owner)
			|| WurstClient.MC.player == null)
			return false;

		int menuId = WurstClient.MC.player.containerMenu.containerId;
		pending.add(new ActionChain(owner, priority, sequence++, menuId,
			validator, List.copyOf(Arrays.asList(actions))));
		return true;
	}

	public synchronized boolean hasPending(Object owner)
	{
		return pending.stream().anyMatch(chain -> chain.owner == owner);
	}

	public synchronized void cancel(Object owner)
	{
		pending.removeIf(chain -> chain.owner == owner);
	}

	public synchronized void clear()
	{
		pending.clear();
	}

	@Override
	public void onUpdate()
	{
		ActionChain chain;
		synchronized(this)
		{
			chain = selectNext();
			if(chain != null)
				pending.remove(chain);
		}

		if(chain == null || WurstClient.MC.player == null
			|| WurstClient.MC.player.containerMenu.containerId != chain.menuId
			|| !chain.validator.getAsBoolean())
			return;

		for(Runnable action : chain.actions)
			action.run();
	}

	private ActionChain selectNext()
	{
		ActionChain best = null;
		for(ActionChain chain : pending)
			if(best == null || chain.priority > best.priority
				|| chain.priority == best.priority
					&& chain.sequence < best.sequence)
				best = chain;
		return best;
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		clear();
	}

	private record ActionChain(Object owner, int priority, long sequence,
		int menuId, BooleanSupplier validator, List<Runnable> actions)
	{
	}
}
