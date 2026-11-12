import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.event.EventManager;
import net.wurstclient.mixin.WurstMixinConfigPlugin;
import net.wurstclient.mixin.sodium.AbstractBlockRenderContextMixin;
import net.wurstclient.mixin.sodium.DefaultFluidRendererMixin;

class SodiumXRayMixinTest
{
	private final BlockState state = new BlockState();
	private final BlockPos pos = new BlockPos(12, 34, 56);

	private static void invoke(Object target, String name, Object... args)
		throws Exception
	{
		for(Method method : target.getClass().getDeclaredMethods())
			if(method.getName().equals(name))
			{
				method.setAccessible(true);
				method.invoke(target, args);
				return;
			}
		throw new NoSuchMethodException(name);
	}

	private static void field(Object target, String name, Object value)
		throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	@Test
	void blockDecisionInvertsCullingAndPreservesPosition() throws Exception
	{
		var mixin = new AbstractBlockRenderContextMixin();
		field(mixin, "state", state);
		field(mixin, "pos", pos);
		for(boolean visible : new boolean[]{true, false})
		{
			EventManager.decision = visible;
			var result = new CallbackInfoReturnable<Boolean>("test", true);
			// Unculled model quads with a null face must also be hidden.
			invoke(mixin, "wurst$onIsFaceCulled", null, result);
			assertTrue(result.isCancelled());
			assertEquals(!visible, result.getReturnValue());
			assertSame(pos, EventManager.last.pos);
		}
	}

	@Test
	void blockNoDecisionOrMissingContextKeepsSodiumResult() throws Exception
	{
		var mixin = new AbstractBlockRenderContextMixin();
		field(mixin, "state", state);
		field(mixin, "pos", pos);
		EventManager.decision = null;
		var result = new CallbackInfoReturnable<Boolean>("test", true, true);
		invoke(mixin, "wurst$onIsFaceCulled", Direction.UP, result);
		assertFalse(result.isCancelled());
		assertTrue(result.getReturnValue());
		field(mixin, "state", null);
		EventManager.decision = false;
		invoke(mixin, "wurst$onIsFaceCulled", Direction.UP, result);
		assertFalse(result.isCancelled());
	}

	@Test
	void hiddenFluidCancelsWholeRender() throws Exception
	{
		var mixin = new DefaultFluidRendererMixin();
		EventManager.decision = false;
		var ci = new CallbackInfo("render", true);
		invoke(mixin, "wurst$onRender", ci, state, pos);
		assertTrue(ci.isCancelled());
		assertSame(pos, EventManager.last.pos);
	}

	@Test
	void selectedFluidBypassesOldAndNewOcclusionIncludingCaveHeuristic()
		throws Exception
	{
		var mixin = new DefaultFluidRendererMixin();
		EventManager.decision = true;
		invoke(mixin, "wurst$onRender", new CallbackInfo("render", true), state, pos);
		var visible = new CallbackInfoReturnable<Boolean>("test", true, false);
		invoke(mixin, "wurst$onVisibility", visible);
		assertTrue(visible.getReturnValue());
		var occluded = new CallbackInfoReturnable<Boolean>("test", true, true);
		invoke(mixin, "wurst$onOccluded", occluded);
		assertFalse(occluded.getReturnValue());
		var exposure = new CallbackInfoReturnable<Integer>("test", true, 0);
		invoke(mixin, "wurst$onUpFaceExposure", exposure);
		assertEquals(0b11, exposure.getReturnValue());
	}

	@Test
	void fluidDecisionCannotLeakToNextRenderOrAfterReturn() throws Exception
	{
		var mixin = new DefaultFluidRendererMixin();
		EventManager.decision = true;
		invoke(mixin, "wurst$onRender", new CallbackInfo("render", true), state, pos);
		// Simulate a previous render throwing before its RETURN cleanup.
		EventManager.decision = null;
		invoke(mixin, "wurst$onRender", new CallbackInfo("render", true), state, pos);
		var result = new CallbackInfoReturnable<Boolean>("test", true, false);
		invoke(mixin, "wurst$onVisibility", result);
		assertFalse(result.isCancelled());
		EventManager.decision = true;
		invoke(mixin, "wurst$onRender", new CallbackInfo("render", true), state, pos);
		invoke(mixin, "wurst$afterRender", new CallbackInfo("render", false));
		invoke(mixin, "wurst$onVisibility", result);
		assertFalse(result.isCancelled());
	}

	@Test
	void optionalEligibilityReadsResourcesWithoutLoadingTargets() throws Exception
	{
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
				@Override
				public URL getResource(String name) {
					return name.equals("example/Present.class")
						? SodiumXRayMixinTest.class.getResource("/SodiumXRayMixinTest.class") : null;
				}
				@Override
				protected Class<?> loadClass(String name, boolean resolve) {
					throw new AssertionError("Eligibility loaded " + name);
				}
			});
			var plugin = new WurstMixinConfigPlugin();
			String mixin = "net.wurstclient.mixin.sodium.DefaultFluidRendererMixin";
			assertTrue(plugin.shouldApplyMixin("example.Present", mixin));
			assertFalse(plugin.shouldApplyMixin("example.Missing", mixin));
			assertFalse(plugin.shouldApplyMixin("example.Missing",
				"net.wurstclient.mixin.sodium/DefaultFluidRendererMixin"));
			assertTrue(plugin.shouldApplyMixin("example.Missing", "net.wurstclient.mixin.OptionsMixin"));
		}finally
		{
			Thread.currentThread().setContextClassLoader(previous);
		}
	}
}
