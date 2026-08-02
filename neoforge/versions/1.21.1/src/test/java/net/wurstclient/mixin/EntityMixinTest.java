package net.wurstclient.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import net.wurstclient.util.HitboxExpansionPolicy;

final class EntityMixinTest
{
	@Test
	void expandsClientTargetHitboxes()
	{
		assertTrue(HitboxExpansionPolicy.shouldExpand(true, true, false, 0.5F));
	}

	@Test
	void neverExpandsIntegratedServerHitboxes()
	{
		assertFalse(HitboxExpansionPolicy.shouldExpand(false, true, false,
			0.5F));
	}

	@Test
	void neverExpandsLocalPlayerHitbox()
	{
		assertFalse(HitboxExpansionPolicy.shouldExpand(true, true, true, 0.5F));
	}

	@Test
	void keepsStaticMixinMethodsPrivate()
	{
		for(Method method : EntityMixin.class.getDeclaredMethods())
			if(Modifier.isStatic(method.getModifiers()))
				assertTrue(Modifier.isPrivate(method.getModifiers()),
					method::toString);
	}
}
