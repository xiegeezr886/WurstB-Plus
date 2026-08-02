package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import net.wurstclient.WurstClient;
import net.wurstclient.proxy.ProxyManager;

@Mixin(targets = "net.minecraft.network.Connection$1")
public abstract class ConnectionChannelInitializerMixin
{
	@Inject(at = @At("HEAD"), method = "initChannel(Lio/netty/channel/Channel;)V")
	private void addProxyHandler(Channel channel, CallbackInfo ci)
	{
		ProxyManager manager = WurstClient.INSTANCE.getProxyManager();
		if(manager == null)
			return;

		ChannelHandler handler = manager.createProxyHandler();
		if(handler != null)
			channel.pipeline().addLast("wurst_proxy", handler);
	}
}
