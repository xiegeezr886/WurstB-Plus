package net.wurstclient.hack;

import java.util.EnumMap;
import java.util.LinkedHashSet;

public final class HackConflictManager
{
	private final EnumMap<HackConflictGroup, Hack> owners =
		new EnumMap<>(HackConflictGroup.class);

	public void acquire(Hack hack)
	{
		LinkedHashSet<Hack> conflicts = new LinkedHashSet<>();
		for(HackConflictGroup group : hack.getConflictGroups())
		{
			Hack owner = owners.get(group);
			if(owner != null && owner != hack)
				conflicts.add(owner);
		}

		for(Hack conflict : conflicts)
			conflict.setEnabled(false);

		for(HackConflictGroup group : hack.getConflictGroups())
			owners.put(group, hack);
	}

	public void release(Hack hack)
	{
		for(HackConflictGroup group : hack.getConflictGroups())
			if(owners.get(group) == hack)
				owners.remove(group);
	}

	public Hack getOwner(HackConflictGroup group)
	{
		return owners.get(group);
	}
}
