/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.wurstclient.gui.opai.OpaiSelectionMenu;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IMultiplayerScreen;
import net.wurstclient.serverfinder.CleanUpScreen;
import net.wurstclient.serverfinder.ServerFinderScreen;
import net.wurstclient.util.LastServerRememberer;

@Mixin(JoinMultiplayerScreen.class)
public class MultiplayerScreenMixin extends Screen implements IMultiplayerScreen
{
	@Shadow
	protected ServerSelectionList serverSelectionList;
	
	private Button lastServerButton;
 @Shadow @Final private Screen lastScreen;
 @Unique private OpaiSelectionMenu opai$menu;
 @Unique private long opai$lastProbe;
 @Shadow public void joinSelectedServer() {}
 @Shadow public void setSelected(ServerSelectionList.Entry entry) {}
	
	private MultiplayerScreenMixin(WurstClient wurst, Component title)
	{
		super(title);
	}
	
	@Inject(at = @At("TAIL"), method = "init()V")
	private void onInit(CallbackInfo ci)
	{
		if(!WurstClient.INSTANCE.isEnabled())
			return;
		
		lastServerButton = addRenderableWidget(Button
			.builder(Component.literal("上次服务器"),
				b -> LastServerRememberer
					.joinLastServer((JoinMultiplayerScreen)(Object)this))
			.bounds(width / 2 - 154, 10, 100, 20).build());
		updateLastServerButton();
		
		addRenderableWidget(
			Button
				.builder(Component.literal("服务器搜索"),
					b -> minecraft.setScreen(new ServerFinderScreen(
						(JoinMultiplayerScreen)(Object)this)))
				.bounds(width / 2 + 154 + 4, height - 54, 100, 20).build());
		
		addRenderableWidget(Button
			.builder(Component.literal("清理"),
				b -> minecraft.setScreen(
					new CleanUpScreen((JoinMultiplayerScreen)(Object)this)))
			.bounds(width / 2 + 154 + 4, height - 30, 100, 20).build());
  List<Button> buttons=children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
  String[] labels={"加入服务器","直接连接","添加服务器","编辑","删除","刷新","返回"};
  for(int i=0;i<labels.length && i<buttons.size();i++)buttons.get(i).setMessage(Component.literal(labels[i]));
  opai$menu=new OpaiSelectionMenu(this,"服务器列表",this::opai$rows,
   entry->setSelected((ServerSelectionList.Entry)entry),this::joinSelectedServer,
   ()->minecraft.setScreen(lastScreen),buttons,3,4);
  setFocused(null);

	}
	
 @Unique private List<OpaiSelectionMenu.Row> opai$rows(){
  List<OpaiSelectionMenu.Row> rows=new ArrayList<>();
  for(var entry:serverSelectionList.children()){
   if(entry instanceof ServerSelectionList.OnlineServerEntry online){
    var server=online.getServerData();
    String detail=server.ping>=0?server.status.getString()+" players · "+server.ping+" ms":server.motd.getString();
    rows.add(new OpaiSelectionMenu.Row(entry,server.name,detail,server.ip,((OpaiServerEntryAccessor)(Object)online).opai$icon().textureLocation(),true));
   }else if(entry instanceof ServerSelectionList.NetworkServerEntry lan){
    var server=lan.getServerData();
    rows.add(new OpaiSelectionMenu.Row(entry,server.getMotd(),"局域网",server.getAddress(),null,true));
   }
  }
  return rows;
 }
 @Inject(method="render",at=@At("HEAD"),cancellable=true)
 private void opai$render(GuiGraphics g,int x,int y,float delta,CallbackInfo ci){
  if(opai$menu==null)return;
  // Vanilla owns ping tasks and favicon upload. Clip its row paint completely.
  if(Util.getMillis()-opai$lastProbe>1000){
   opai$lastProbe=Util.getMillis();
   g.enableScissor(0,0,0,0);
   for(var entry:serverSelectionList.children())
    if(entry instanceof ServerSelectionList.OnlineServerEntry)
     entry.render(g,0,0,0,300,36,-100,-100,false,delta);
   g.flush();g.disableScissor();
  }
  opai$menu.render(g,x,y);ci.cancel();
 }
 @Override public boolean mouseClicked(double x,double y,int b){return opai$menu!=null?opai$menu.click(x,y,b):super.mouseClicked(x,y,b);}
 @Override public boolean mouseScrolled(double x,double y,double d){return opai$menu!=null?opai$menu.scroll(x,y,d):super.mouseScrolled(x,y,d);}
 @Inject(method="onClose()V",at=@At("HEAD"),cancellable=true)
 private void opai$close(CallbackInfo ci){if(opai$menu!=null){opai$menu.close();ci.cancel();}}
 @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)
 private void opai$key(int key,int scan,int mods,CallbackInfoReturnable<Boolean> cir){
  if(opai$menu!=null && key!=org.lwjgl.glfw.GLFW.GLFW_KEY_F5)cir.setReturnValue(opai$menu.key(key));
 }

	@Inject(at = @At("HEAD"),
		method = "join(Lnet/minecraft/client/multiplayer/ServerData;)V")
	private void onConnect(ServerData entry, CallbackInfo ci)
	{
		LastServerRememberer.setLastServer(entry);
		updateLastServerButton();
	}
	
	@Unique
	private void updateLastServerButton()
	{
		if(lastServerButton == null)
			return;
		
		lastServerButton.active = LastServerRememberer.getLastServer() != null;
	}
	
	@Override
	public ServerSelectionList getServerListSelector()
	{
		return serverSelectionList;
	}
	
	@Override
	public void connectToServer(ServerData server)
	{
		join(server);
	}
	
	@Shadow
	private void join(ServerData entry)
	{
		
	}
}
