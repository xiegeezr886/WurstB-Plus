package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class EntitySnapshotManagerTest
{
	@Test
	void snapshotDefensivelyCopiesAllLists()
	{
		EntitySnapshotManager.Snapshot snapshot =
			new EntitySnapshotManager.Snapshot(1, new ArrayList<>(),
				new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

		assertThrows(UnsupportedOperationException.class,
			() -> snapshot.entities().add(null));
		assertThrows(UnsupportedOperationException.class,
			() -> snapshot.players().add(null));
		assertThrows(UnsupportedOperationException.class,
			() -> snapshot.livingEntities().add(null));
		assertThrows(UnsupportedOperationException.class,
			() -> snapshot.items().add(null));
	}
}
