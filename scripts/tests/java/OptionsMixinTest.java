package net.wurstclient.mixin;

import static org.junit.jupiter.api.Assertions.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.client.Options;
import net.wurstclient.WurstClient;
import org.junit.jupiter.api.Test;

/** Exercises the actual wrapper against a minimal Options boundary, not Mixin weaving. */
final class OptionsMixinTest
{
	private void save(Options options, Operation<Void> original) throws Exception
	{
		var method = OptionsMixin.class.getDeclaredMethod("wurstSaveGamma", Operation.class);
		method.setAccessible(true);
		method.invoke(options, original);
	}

	@Test void activeFullbrightIsNeverPersistedAndIsRestored() throws Exception
	{
		Options options = new Options(16);
		save(options, args -> { assertEquals(0.5, options.gamma().get()); return null; });
		assertEquals(16, options.gamma().get());
	}

	@Test void renderingStateSurvivesSaveFailure() throws Exception
	{
		Options options = new Options(16);
		assertThrows(InvocationTargetException.class,
			() -> save(options, args -> { throw new IllegalStateException("I/O failure"); }));
		assertEquals(16, options.gamma().get());
	}

	@Test void legitimateGammaPassesThroughUnchanged() throws Exception
	{
		Options options = new Options(0.25);
		save(options, args -> { assertEquals(0.25, options.gamma().get()); return null; });
		assertEquals(0.25, options.gamma().get());
	}

	@Test void invalidConfiguredDefaultIsClampedOnlyForSaving() throws Exception
	{
		Options options = new Options(16);
		WurstClient.Hax hax = new WurstClient.Hax();
		hax.fullbrightHack.value = 3;
		WurstClient.INSTANCE.hax = hax;
		try
		{
			save(options, args -> { assertEquals(1, options.gamma().get()); return null; });
			assertEquals(16, options.gamma().get());
		}finally { WurstClient.INSTANCE.hax = null; }
	}
}
