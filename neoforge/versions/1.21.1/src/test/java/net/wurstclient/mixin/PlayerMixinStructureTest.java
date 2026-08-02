package net.wurstclient.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

final class PlayerMixinStructureTest
{
	@Test
	void staticMixinMethodsArePrivate()
	{
		for(var method : PlayerMixin.class.getDeclaredMethods())
			if(Modifier.isStatic(method.getModifiers()))
				assertTrue(Modifier.isPrivate(method.getModifiers()),
					() -> method.getName() + " must be private");
	}

	@Test
	void doesNotUseRuntimeGeneratedModifyArgsClasses()
	{
		for(var method : PlayerMixin.class.getDeclaredMethods())
			for(var annotation : method.getDeclaredAnnotations())
				assertTrue(!annotation.annotationType().getSimpleName()
					.equals("ModifyArgs"),
					"PlayerMixin must not use @ModifyArgs on Forge");
	}
}
