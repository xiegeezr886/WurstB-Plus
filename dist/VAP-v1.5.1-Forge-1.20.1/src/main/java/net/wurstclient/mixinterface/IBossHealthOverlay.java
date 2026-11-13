package net.wurstclient.mixinterface;

import java.util.Map;
import java.util.UUID;

import net.minecraft.world.BossEvent;

public interface IBossHealthOverlay
{
	public Map<UUID, ? extends BossEvent> wurst_getEvents();
}
