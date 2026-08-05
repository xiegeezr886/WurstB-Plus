package net.wurstclient.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class WurstMixinConfigPlugin implements IMixinConfigPlugin
{
	@Override
	public void onLoad(String mixinPackage)
	{
	}

	@Override
	public String getRefMapperConfig()
	{
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName,
		String mixinClassName)
	{
		if(!mixinClassName.endsWith("SodiumBlockOcclusionCacheMixin")
			&& !mixinClassName.endsWith("SodiumFluidRendererMixin"))
			return true;

		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		String classResource = targetClassName.replace('.', '/') + ".class";
		return classLoader != null && classLoader.getResource(classResource) != null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets,
		Set<String> otherTargets)
	{
	}

	@Override
	public List<String> getMixins()
	{
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass,
		String mixinClassName, IMixinInfo mixinInfo)
	{
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass,
		String mixinClassName, IMixinInfo mixinInfo)
	{
	}
}
