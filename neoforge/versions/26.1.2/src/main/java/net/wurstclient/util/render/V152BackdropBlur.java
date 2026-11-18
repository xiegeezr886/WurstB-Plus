package net.wurstclient.util.render;

import java.io.IOException;
import java.util.Set;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Reload-aware 1.21.4 post-chain adapter. ShaderManager owns the pipeline. */
public final class V152BackdropBlur implements AutoCloseable {
  private static final Identifier EFFECT =
      Identifier.fromNamespaceAndPath("wurstpenguin", "opai_background");
  private static final Set<Identifier> TARGETS = Set.of(Identifier.withDefaultNamespace("main"));

  public void process(RenderTarget target) throws IOException {
    var chain = Minecraft.getInstance().getShaderManager().getPostChain(EFFECT, TARGETS);
    if (chain == null) throw new IOException("Missing Opai backdrop post effect");
    chain.process(target, GraphicsResourceAllocator.UNPOOLED);
  }

  @Override public void close() {
    // ShaderManager releases/reloads the shared pipeline; no owned GL resources.
  }
}
