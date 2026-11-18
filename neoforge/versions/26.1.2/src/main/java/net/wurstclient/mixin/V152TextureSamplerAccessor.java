package net.wurstclient.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import com.mojang.blaze3d.textures.GpuSampler;
@Mixin(AbstractTexture.class)
public interface V152TextureSamplerAccessor {
  @Accessor("sampler") void wurst$setSampler(GpuSampler sampler);
}
