package net.wurstclient.hack;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.WorldChangeListener;

public final class HackLifecycleManager implements WorldChangeListener
{
	private final HackList hax;

	public HackLifecycleManager(HackList hax, EventManager events)
	{
		this.hax = hax;
		events.add(WorldChangeListener.class, this);
	}

	@Override
	public void onWorldChange(@Nullable ClientLevel world)
	{
		if(world != null)
			return;

		RuntimeException failure = null;
		for(Hack hack : hax.getAllHax())
			if(hack.isEnabled() && !hack.isStateSaved())
				try
				{
					hack.setEnabled(false);
				}catch(RuntimeException e)
				{
					if(failure == null)
						failure = e;
					else
						failure.addSuppressed(e);
				}

		if(failure != null)
			throw failure;
	}
}
