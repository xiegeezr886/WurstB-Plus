package net.wurstclient.test;

import net.minecraft.server.Bootstrap;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.network.NetworkEvent;

public final class ForgeTestBootstrap {
  private static boolean prepared;

  public static synchronized void bootstrap() {
    if (!prepared) {
      try {
        var method = EventListenerHelper.class.getDeclaredMethod(
            "getListenerListInternal", Class.class, boolean.class);
        method.setAccessible(true);
        prepare(method, NetworkEvent.class);
        for (var type : NetworkEvent.class.getDeclaredClasses())
          if (Event.class.isAssignableFrom(type)) prepare(method, type);
        prepared = true;
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Forge unit-test bootstrap failed", e);
      }
    }
    Bootstrap.bootStrap();
  }

  private static void prepare(java.lang.reflect.Method method, Class<?> type)
      throws ReflectiveOperationException {
    if (type == Event.class) return;
    prepare(method, type.getSuperclass());
    method.invoke(null, type, true);
  }
}
