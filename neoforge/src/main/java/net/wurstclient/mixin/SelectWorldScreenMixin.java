package net.wurstclient.mixin;

import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.wurstclient.WurstClient;
import net.wurstclient.gui.opai.OpaiSelectionMenu;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen
{
 @Shadow private WorldSelectionList list;
 @Shadow @Final protected Screen lastScreen;
 @Shadow protected EditBox searchBox;
 @Unique private OpaiSelectionMenu opai$menu;
 protected SelectWorldScreenMixin(Component title){super(title);}

 @Inject(method="init()V",at=@At("TAIL"))
 private void opai$init(CallbackInfo ci){
  if(!WurstClient.INSTANCE.isEnabled())return;
  List<Button> buttons=children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
  if(buttons.size()<6)return;
  String[] labels={"进入世界","创建世界","编辑","删除","重新创建","返回"};
  for(int i=0;i<labels.length;i++)buttons.get(i).setMessage(Component.literal(labels[i]));
  ArrayList<Button> ordered=new ArrayList<>(List.of(buttons.get(0),buttons.get(1),buttons.get(2),buttons.get(3),buttons.get(5),buttons.get(4)));
  opai$menu=new OpaiSelectionMenu(this,"选择世界",this::opai$rows,
   entry->list.setSelected((WorldSelectionList.Entry)entry),
   ()->list.getSelectedOpt().filter(WorldSelectionList.WorldListEntry::isSelectable).ifPresent(WorldSelectionList.WorldListEntry::joinWorld),
   ()->minecraft.setScreen(lastScreen),ordered,2,3);
  setFocused(null);
 }
 @Unique private List<OpaiSelectionMenu.Row> opai$rows(){
  OpaiWorldListAccessor access=(OpaiWorldListAccessor)list;
  var levels=access.opai$poll();
  if(levels!=access.opai$displayed())access.opai$update(levels);
  List<OpaiSelectionMenu.Row> rows=new ArrayList<>();
  for(var entry:list.children()){
   if(entry instanceof WorldSelectionList.WorldListEntry world){
    var data=(OpaiWorldEntryAccessor)(Object)world;var s=data.opai$summary();
    rows.add(new OpaiSelectionMenu.Row(world,s.getLevelName(),s.getInfo().getString(),s.getLevelId(),data.opai$icon().textureLocation(),world.isSelectable()));
   }else rows.add(new OpaiSelectionMenu.Row(entry,"正在加载世界…","","",null,false));
  }
  return rows;
 }
 @Inject(method="render",at=@At("HEAD"),cancellable=true)
 private void opai$render(GuiGraphics g,int x,int y,float delta,CallbackInfo ci){
  if(opai$menu!=null){opai$menu.render(g,x,y);ci.cancel();}
 }
 @Override public boolean mouseClicked(double x,double y,int b){return opai$menu!=null?opai$menu.click(x,y,b):super.mouseClicked(x,y,b);}
 @Override public boolean mouseScrolled(double x,double y,double d){return opai$menu!=null?opai$menu.scroll(x,y,d):super.mouseScrolled(x,y,d);}
 @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)
 private void opai$key(int key,int scan,int mods,CallbackInfoReturnable<Boolean> cir){if(opai$menu!=null)cir.setReturnValue(opai$menu.key(key));}
 @Inject(method="charTyped",at=@At("HEAD"),cancellable=true)
 private void opai$char(char c,int mods,CallbackInfoReturnable<Boolean> cir){if(opai$menu!=null)cir.setReturnValue(false);}
 @Inject(method="onClose",at=@At("HEAD"),cancellable=true)
 private void opai$close(CallbackInfo ci){if(opai$menu!=null){opai$menu.close();ci.cancel();}}
}
