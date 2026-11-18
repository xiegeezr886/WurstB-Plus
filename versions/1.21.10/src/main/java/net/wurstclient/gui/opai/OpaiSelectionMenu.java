package net.wurstclient.gui.opai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.gui.MainMenuBackground;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import org.lwjgl.glfw.GLFW;

/** Native presentation of Opai ListScreen; actions remain owned by vanilla. */
public final class OpaiSelectionMenu
{
 public record Row(Object key, String title, String detail, String extra,
  ResourceLocation icon, boolean selectable) {}
 private record Control(Button nativeButton, int x, int y, int width) {}
 private static final class RowHighlight {
  float hover, selection;
  void update(boolean hovered, boolean selected, float blend) {
   hover += ((hovered ? 1 : 0) - hover) * blend;
   selection += ((selected ? 1 : 0) - selection) * blend;
  }
 }
 private final Screen owner;
 private final String title;
 private final Supplier<List<Row>> rows;
 private final Consumer<Object> select;
 private final Runnable activate, back;
 private final List<Control> controls = new ArrayList<>(), extras = new ArrayList<>();
 private Object selected;
 private long lastClick;
 private final OpaiSmoothScroll scrolling = new OpaiSmoothScroll();
 private final Map<Object, RowHighlight> highlights = new IdentityHashMap<>();
 private final Set<Object> visibleKeys = Collections.newSetFromMap(new IdentityHashMap<>());
 private long lastHighlightFrame = -1;
 private int left, top = 38, bottom, listWidth;
 private boolean more, closing;
 private OpaiMenuMotion motion;
 private Supplier<String> subtitle = () -> "";

 public OpaiSelectionMenu(Screen owner, String title, Supplier<List<Row>> rows,
  Consumer<Object> select, Runnable activate, Runnable back, List<Button> buttons, int firstRow, int secondRow)
 {
  this.owner=owner; this.title=title; this.rows=rows; this.select=select; this.activate=activate; this.back=back;
  listWidth=Math.min(320,owner.width-20); left=(owner.width-listWidth)/2; bottom=owner.height-62;
  motion=OpaiMenuMotion.enter(true, Util.getMillis());
  for(int i=0;i<firstRow+secondRow && i<buttons.size();i++) {
   int count=i<firstRow?firstRow:secondRow,index=i<firstRow?i:i-firstRow;
   int w=(listWidth-(count-1)*4)/count;
   controls.add(new Control(buttons.get(i),left+index*(w+4),owner.height-(i<firstRow?52:28),w));
  }
  for(int i=firstRow+secondRow;i<buttons.size();i++)
   extras.add(new Control(buttons.get(i),owner.width-130,38+(i-firstRow-secondRow)*24,114));
  select.accept(null);
 }

 public boolean ready() { return !closing && motion.finished(Util.getMillis()); }
 public void subtitle(Supplier<String> text) { subtitle = text; }
 public void selectItem(Object item) {
  selected = item; select.accept(item);
 }
 public void close() {
  if(closing) return;
  long now=Util.getMillis(); closing=true; more=false;
  motion=new OpaiMenuMotion(false,motion.rotation(now),motion.phase(now),now);
 }
 public void render(GuiGraphicsExtractor graphics,int mx,int my) {
  long now=Util.getMillis();
  if(closing && motion.crossed(now)) {
   NanoVGUI.pass(graphics,()->MainMenuBackground.render(owner.width,owner.height,255));
   motion.handoff(now);back.run();return;
  }
  List<Row> data=rows.get();
  if(selected!=null && data.stream().noneMatch(r->r.key()==selected)) {selected=null;select.accept(null);}
  clamp(data.size());
  scrolling.step(now);
  double scroll = scrolling.position();
  float highlightBlend = lastHighlightFrame < 0 ? 0
   : (float)-Math.expm1(-Math.max(0, now-lastHighlightFrame)/65D);
  lastHighlightFrame = now;
  visibleKeys.clear();
  NanoVGUI.pass(graphics,()->{
   MainMenuBackground.render(owner.width,owner.height,255);
   NanoVGUI.push(); NanoVGUI.context().setAlpha(motion.fade(now));
   NanoVGUI.centeredText(NanoVGUI.plainSubstrByWidth(subtitle.get(),listWidth,8),owner.width/2F,28,OpaiMenuStyle.ACCENT,8);
   for(Control c:controls) draw(c,mx,my);
   if(!extras.isEmpty()) OpaiMenuStyle.button(owner.width-72,8,64,"更多",true,mx>=owner.width-72&&my>=8&&my<28);
   NanoVGUI.pop();
   NanoVGUI.push(); OpaiMenuStyle.flip(motion.rotation(now)+180,motion.phase(now),owner.width/2F,(top+bottom)/2F);
   NanoVGUI.fillRounded(left,top,left+listWidth,bottom,8,0x64000000);
   NanoVGUI.pushScissor(left+4,top+5,listWidth-8,Math.max(0,bottom-top-10));
   for(int i=0;i<data.size();i++){
    float y=(float)(top+5+i*41-scroll);
    if(y+36<top+5||y>bottom-5)continue;
    Row r=data.get(i);
    visibleKeys.add(r.key());
    RowHighlight highlight=highlights.computeIfAbsent(r.key(),key->new RowHighlight());
    boolean hover=r.selectable()&&ready()&&!more&&mx>=left+4&&mx<left+listWidth-4
     &&my>=top+5&&my<bottom-5&&my>=y&&my<y+36;
    highlight.update(hover,r.key()==selected,highlightBlend);
    OpaiMenuStyle.row(left+4,y,listWidth-8,36,highlight.hover,highlight.selection);
    if(r.icon()!=null)NanoVGUI.image(r.icon(),left+8,y+3,30,30,6,0xFFFFFFFF);
    else {NanoVGUI.fillRounded(left+8,y+3,left+38,y+33,6,0x203B3654);NanoVGUI.centeredText("W",left+23,y+10,OpaiMenuStyle.ACCENT,12);}
    int textX=left+47;
    NanoVGUI.text(NanoVGUI.substrByWidth(r.title(),listWidth-55,10),textX,y+2,r.selectable()?0xFFFFFFFF:0xFF999999,10);
    NanoVGUI.text(NanoVGUI.substrByWidth(r.detail(),listWidth-55,8),textX,y+16,0xFFB9B9B9,8);
    NanoVGUI.text(NanoVGUI.substrByWidth(r.extra(),listWidth-55,8),textX,y+26,0xFFB9B9B9,8);
   }
   if(data.isEmpty())NanoVGUI.centeredText("列表为空",owner.width/2F,top+28,0xFFB9B9B9,9);
   NanoVGUI.popScissor();
   double available=bottom-top-10,total=data.size()*41;
   if(total>available){float h=(float)Math.max(12,available*available/total),y=(float)(top+5+scroll/(total-available)*(available-h));
    NanoVGUI.fillRounded(left+listWidth-3,y,left+listWidth-1,y+h,1,OpaiMenuStyle.ACCENT);}
   NanoVGUI.pop();
   if(more){NanoVGUI.fillRounded(owner.width-138,32,owner.width-8,42+extras.size()*24,8,0xF0222030);for(Control c:extras)draw(c,mx,my);}
  });
  // Keep animation state only for visible entries, even in very long lists.
  highlights.keySet().retainAll(visibleKeys);
 }
 private void draw(Control c,int mx,int my){OpaiMenuStyle.button(c.x(),c.y(),c.width(),c.nativeButton().getMessage().getString(),c.nativeButton().active,ready()&&hit(c,mx,my));}
 private boolean hit(Control c,double x,double y){return x>=c.x()&&x<c.x()+c.width()&&y>=c.y()&&y<c.y()+20;}
 private void press(Control c){
  if(!c.nativeButton().active)return;
  c.nativeButton().playDownSound(Minecraft.getInstance().getSoundManager());
  if(controls.indexOf(c)==controls.size()-1)close();else c.nativeButton().onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
 }
 public boolean click(double x,double y,int button){
  if(!ready())return true;
  if(button==GLFW.GLFW_MOUSE_BUTTON_4){close();return true;}
  if(button!=0)return true;
  if(!extras.isEmpty()&&x>=owner.width-72&&y>=8&&y<28){more=!more;return true;}
  if(more){for(Control c:extras)if(hit(c,x,y)){press(c);return true;}more=false;return true;}
  for(Control c:controls)if(hit(c,x,y)){press(c);return true;}
  if(x>=left+4&&x<left+listWidth-4&&y>=top+5&&y<bottom-5){
   double pos=y-top-5+scrolling.position(); int i=(int)(pos/41);
   List<Row> data=rows.get();
   if(i>=0&&i<data.size()&&pos%41<36){
    Row row=data.get(i); if(!row.selectable())return true;
    long now=Util.getMillis(); boolean twice=row.key()==selected&&now-lastClick<250;
    selected=row.key(); select.accept(selected);lastClick=now;
    if(twice)activate.run();
   }
  }
  return true;
 }
 public boolean scroll(double x,double y,double delta){
  if(ready()&&!more&&x>=left&&x<left+listWidth&&y>=top&&y<bottom){clamp(rows.get().size());scrolling.by(-delta*41);lastClick=0;}
  return true;
 }
 private void clamp(int count){scrolling.limit(Math.max(0,count*41-(bottom-top-10)));}
 public boolean key(int key){
  if(key==GLFW.GLFW_KEY_ESCAPE){if(more)more=false;else close();return true;}
  if(!ready())return true;
  List<Row> data=rows.get();
  if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){if(selected!=null)activate.run();return true;}
  if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){
   int i=-1;for(int j=0;j<data.size();j++)if(data.get(j).key()==selected)i=j;
   int direction=key==GLFW.GLFW_KEY_DOWN?1:-1;
   for(i=i<0?0:i+direction;i>=0&&i<data.size();i+=direction)if(data.get(i).selectable()){
    selected=data.get(i).key();select.accept(selected);
    clamp(data.size());scrolling.reveal(i*41,i*41+36,bottom-top-10);
    lastClick=0;break;
   }
  }
  return true;
 }
}
