"""Check real AutoTrash filtering and slot actions for legacy and modern adapters."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from sync_four_client_batch import ROOT, PLAN


def run(staged, target, label):
    out = PLAN / ('batch12/tests-' + label)
    src = out / 'src'

    def source(name, body):
        file = src / (name.replace('.', '/') + '.java')
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text('package ' + name.rpartition('.')[0] + ';\n' + body, encoding='utf-8')

    java_root = (PLAN / 'batch12/generated' if staged else ROOT) / target / 'src/main/java/net/wurstclient'
    provenance = {}
    for relative in ['hacks/AutoTrashHack.java', 'util/EnchantmentRules.java']:
        original = java_root / relative
        dest = src / 'net/wurstclient' / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, dest)
        provenance[relative] = hashlib.sha256(original.read_bytes()).hexdigest()
    bodies = {
        'net.wurstclient.Category': 'public enum Category {ITEMS}',
        'net.wurstclient.SearchTags': 'public @interface SearchTags {String[] value();}',
        'net.wurstclient.events.UpdateListener': 'public interface UpdateListener {void onUpdate();}',
        'net.wurstclient.settings.CheckboxSetting': 'public class CheckboxSetting {public boolean value;public CheckboxSetting(String n,String d,boolean v){value=v;}public boolean isChecked(){return value;}}',
        'net.wurstclient.settings.EnumSetting': 'public class EnumSetting<T> {public T value;public EnumSetting(String n,String d,T[] all,T v){value=v;}public T getSelected(){return value;}}',
        'net.wurstclient.settings.TextFieldSetting': 'public class TextFieldSetting {public String value;public TextFieldSetting(String n,String d,String v){value=v;}public String getValue(){return value;}}',
        'net.wurstclient.settings.ItemListSetting': 'public class ItemListSetting {public java.util.List<String> values=new java.util.ArrayList<>();public ItemListSetting(String n,String d,String... v){values.addAll(java.util.List.of(v));}public java.util.List<String> getItemNames(){return values;}}',
        'net.wurstclient.settings.SliderSetting': 'public class SliderSetting {public double value;public SliderSetting(String n,String d,double v,double min,double max,double step,ValueDisplay display){value=v;}public int getValueI(){return (int)value;}public enum ValueDisplay {INTEGER;public ValueDisplay withSuffix(String s){return this;}}}',
        'net.minecraft.client.gui.screens.inventory.AbstractContainerScreen': 'public class AbstractContainerScreen {}',
        'net.minecraft.client.gui.screens.inventory.InventoryScreen': 'public class InventoryScreen extends AbstractContainerScreen {}',
        'net.minecraft.world.item.Item': 'public record Item(String id) {}',
        'net.minecraft.world.item.Items': 'public class Items {public static final Item ENCHANTED_BOOK=new Item("minecraft:enchanted_book");}',
        'net.minecraft.world.item.enchantment.Enchantment': 'public record Enchantment(String id) {}',
        'net.minecraft.world.inventory.ClickType': 'public enum ClickType {THROW}',
        'net.minecraft.world.inventory.ContainerInput': 'public enum ContainerInput {THROW}',
        'net.wurstclient.util.InventoryUtils': 'public class InventoryUtils {public static int toNetworkSlot(int slot){return slot<9?slot+36:slot;}}',
        'net.wurstclient.util.ChatUtils': 'public class ChatUtils {public static int errors;public static void error(String text){errors++;}}',
        'net.wurstclient.hacks.autolibrarian.BookOffer': 'public record BookOffer(String id,int level,int price) {public Object getEnchantment(){return java.util.Set.of("minecraft:mending","minecraft:swift_sneak","minecraft:sharpness").contains(id)?this:null;}}',
        'net.minecraft.resources.ResourceKey': 'public record ResourceKey(String id) {public String identifier(){return id;}public String location(){return id;}}',
        'net.minecraft.core.Holder': 'public record Holder<T>(net.minecraft.resources.ResourceKey key) {public java.util.Optional<net.minecraft.resources.ResourceKey> unwrapKey(){return java.util.Optional.ofNullable(key);}}',
    }
    for name, body in bodies.items():
        source(name, body)
    source('net.minecraft.world.item.ItemStack', '''public class ItemStack {
public Item item;public int count;public final java.util.Map<net.minecraft.world.item.enchantment.Enchantment,Integer> enchants=new java.util.HashMap<>();
public ItemStack(Item i,int n){item=i;count=n;}public Item getItem(){return item;}public boolean isEmpty(){return count==0;}public boolean is(Item i){return item.equals(i);}}''')
    source('net.minecraft.core.registries.BuiltInRegistries', '''public class BuiltInRegistries {
public static final Registry ITEM=new Registry(),ENCHANTMENT=new Registry();public static class Registry {public String getKey(Object o){return o instanceof net.minecraft.world.item.Item i?i.id():((net.minecraft.world.item.enchantment.Enchantment)o).id();}}}''')
    source('net.minecraft.world.item.enchantment.EnchantmentHelper', '''public class EnchantmentHelper {
public static java.util.Map<Enchantment,Integer> getEnchantments(net.minecraft.world.item.ItemStack stack){return stack.enchants;}
public static ItemEnchantments getEnchantmentsForCrafting(net.minecraft.world.item.ItemStack stack){return new ItemEnchantments(stack.enchants);}}''')
    source('net.minecraft.world.item.enchantment.ItemEnchantments', '''public class ItemEnchantments {
private final java.util.Map<Enchantment,Integer> map;public ItemEnchantments(java.util.Map<Enchantment,Integer> values){map=values;}
public java.util.Set<Entry> entrySet(){java.util.Set<Entry> result=new java.util.HashSet<>();for(var e:map.entrySet())result.add(new Entry(new net.minecraft.core.Holder<>(new net.minecraft.resources.ResourceKey(e.getKey().id())),e.getValue()));return result;}
public record Entry(net.minecraft.core.Holder<Enchantment> getKey,int getIntValue) {}}''')
    source('net.minecraft.client.player.LocalPlayer', '''public class LocalPlayer {
public final Inventory inventory=new Inventory();public final Menu inventoryMenu=new Menu();public Menu containerMenu=inventoryMenu;public Inventory getInventory(){return inventory;}
public static class Inventory {public int selected;public final net.minecraft.world.item.ItemStack[] slots=new net.minecraft.world.item.ItemStack[36];public Inventory(){for(int i=0;i<36;i++)slots[i]=new net.minecraft.world.item.ItemStack(new net.minecraft.world.item.Item("minecraft:air"),0);}public int getSelectedSlot(){return selected;}public net.minecraft.world.item.ItemStack getItem(int i){return slots[i];}}
public static class Menu {public int containerId=0;}}''')
    source('net.minecraft.client.multiplayer.MultiPlayerGameMode', '''public class MultiPlayerGameMode {
public final java.util.List<String> actions=new java.util.ArrayList<>();private void click(int id,int slot,int button,net.minecraft.client.player.LocalPlayer player){actions.add(id+":"+slot+":"+button);var stack=player.inventory.getItem(slot>=36?slot-36:slot);stack.count=button==1?0:stack.count-1;}
public void handleInventoryMouseClick(int id,int slot,int button,net.minecraft.world.inventory.ClickType type,net.minecraft.client.player.LocalPlayer player){click(id,slot,button,player);}
public void handleContainerInput(int id,int slot,int button,net.minecraft.world.inventory.ContainerInput type,net.minecraft.client.player.LocalPlayer player){click(id,slot,button,player);}}''')
    source('net.minecraft.client.Minecraft', '''public class Minecraft {
public net.minecraft.client.player.LocalPlayer player=new net.minecraft.client.player.LocalPlayer();public Object level=new Object(),screen;public net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode=new net.minecraft.client.multiplayer.MultiPlayerGameMode();
public final Gui gui=new Gui();public class Gui {public Object screen(){return screen;}}}''')
    source('net.wurstclient.WurstClient', 'public class WurstClient {public static final net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft();}')
    source('net.wurstclient.hack.Hack', '''public class Hack {
protected static final net.minecraft.client.Minecraft MC=net.wurstclient.WurstClient.MC;protected static final Events EVENTS=new Events();private boolean enabled;
public Hack(String n){}protected void setCategory(Object o){}protected void addSetting(Object o){}protected void onEnable(){}protected void onDisable(){}
public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;if(v)onEnable();else onDisable();}
public static class Events {public void add(Object a,Object b){}public void remove(Object a,Object b){}}}''')
    source('audit.Batch12Probe', '''
import java.lang.reflect.*;import java.util.*;import net.wurstclient.hacks.AutoTrashHack;import net.wurstclient.settings.*;import net.wurstclient.util.*;
import net.minecraft.world.item.*;import net.minecraft.world.item.enchantment.*;
public class Batch12Probe {
static final net.minecraft.client.Minecraft mc=net.wurstclient.WurstClient.MC;
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
static void bool(Object o,String n,boolean v)throws Exception{((CheckboxSetting)field(o,n)).value=v;}
@SuppressWarnings({"unchecked","rawtypes"}) static void mode(Object o,String n,String v)throws Exception{EnumSetting e=(EnumSetting)field(o,n);e.value=Enum.valueOf(((Enum)e.value).getDeclaringClass(),v);}
static void list(Object o,String n,String... ids)throws Exception{ItemListSetting s=(ItemListSetting)field(o,n);s.values.clear();s.values.addAll(List.of(ids));}
static void rules(Object o,String s)throws Exception{((TextFieldSetting)field(o,"bookRules")).value=s;}
static AutoTrashHack reset()throws Exception{mc.player=new net.minecraft.client.player.LocalPlayer();mc.level=new Object();mc.gameMode=new net.minecraft.client.multiplayer.MultiPlayerGameMode();mc.screen=null;ChatUtils.errors=0;AutoTrashHack h=new AutoTrashHack();((SliderSetting)field(h,"delay")).value=0;h.setEnabled(true);return h;}
static ItemStack item(int slot,String id,int count){ItemStack s=new ItemStack(new Item("minecraft:"+id),count);mc.player.inventory.slots[slot]=s;return s;}
static ItemStack book(int slot,String id,int level){ItemStack s=item(slot,"enchanted_book",1);s.enchants.put(new Enchantment("minecraft:"+id),level);return s;}
static void basic()throws Exception{
AutoTrashHack h=reset();ItemStack selected=item(0,"rotten_flesh",3),trash=item(1,"rotten_flesh",3),keep=item(10,"stick",2);list(h,"keepItems","minecraft:stick");h.onUpdate();check(selected.count==3&&trash.count==0&&keep.count==2&&mc.gameMode.actions.equals(List.of("0:37:1")),"native blacklist, keep precedence, selected slot and network slot");
h=reset();mode(h,"mode","WHITELIST");list(h,"items");item(10,"diamond",3);h.onUpdate();check(mc.gameMode.actions.isEmpty(),"empty whitelist protection");list(h,"items","minecraft:diamond");ItemStack waste=item(11,"dirt",4);bool(h,"dropAll",false);h.onUpdate();check(waste.count==3&&mc.player.inventory.getItem(10).count==3&&mc.gameMode.actions.equals(List.of("0:11:0")),"whitelist and single item action");
h=reset();bool(h,"excludeHotbar",true);ItemStack hot=item(1,"rotten_flesh",2);item(9,"rotten_flesh",2);h.onUpdate();check(hot.count==2&&mc.gameMode.actions.equals(List.of("0:9:1")),"exclude hotbar");
h=reset();item(10,"rotten_flesh",2);mc.player.containerMenu=new net.minecraft.client.player.LocalPlayer.Menu();h.onUpdate();check(mc.gameMode.actions.isEmpty(),"hidden foreign container protected");mc.player.containerMenu=mc.player.inventoryMenu;mc.screen=new net.minecraft.client.gui.screens.inventory.AbstractContainerScreen();h.onUpdate();check(mc.gameMode.actions.isEmpty(),"foreign screen protected");mc.screen=new net.minecraft.client.gui.screens.inventory.InventoryScreen();h.onUpdate();check(mc.gameMode.actions.size()==1,"native own inventory allowed");
h=reset();item(10,"rotten_flesh",2);bool(h,"pauseInScreens",true);mc.screen=new Object();h.onUpdate();check(mc.gameMode.actions.isEmpty(),"all screen pause");mc.screen=null;h.setEnabled(false);h.onUpdate();check(mc.gameMode.actions.isEmpty(),"stale disabled callback cannot drop");
h=reset();bool(h,"toggleOffOnClear",true);h.onUpdate();check(!h.isEnabled(),"clear disables");h=reset();list(h,"items");bool(h,"toggleOffOnClear",true);h.onUpdate();check(h.isEnabled(),"unconfigured does not auto-disable");
h=reset();((SliderSetting)field(h,"delay")).value=2;item(10,"rotten_flesh",2);h.onUpdate();item(11,"rotten_flesh",2);h.onUpdate();h.onUpdate();check(mc.gameMode.actions.size()==1,"scan delay");h.onUpdate();check(mc.gameMode.actions.size()==2,"scan resumes");mc.player=null;h.onUpdate();}
static void books()throws Exception{
AutoTrashHack h=reset();list(h,"items");mode(h,"mode","WHITELIST");rules(h,"swift_sneak=3,mending=1");ItemStack exact=book(10,"swift_sneak",3),lower=book(11,"swift_sneak",2),mixed=book(12,"sharpness",5);mixed.enchants.put(new Enchantment("minecraft:mending"),1);ItemStack ordinary=item(13,"dirt",4);h.onUpdate();check(exact.count==1&&lower.count==0&&mixed.count==1&&ordinary.count==4,"exact levels, any matching enchant, book-only rules protect normal items");
h=reset();list(h,"items");rules(h,"swift_sneak=1");ItemStack high=book(10,"swift_sneak",3);ItemStack low=book(11,"swift_sneak",1);h.onUpdate();check(high.count==1&&low.count==0,"blacklist exact not minimum");
h=reset();mode(h,"mode","WHITELIST");list(h,"items","minecraft:enchanted_book");rules(h,"mending=1");ItemStack listed=book(10,"sharpness",2);h.onUpdate();check(listed.count==1,"book item list also counts as match");
h=reset();mode(h,"mode","WHITELIST");rules(h,"mending=1,broken");item(10,"diamond",2);h.onUpdate();h.onUpdate();check(mc.gameMode.actions.isEmpty()&&ChatUtils.errors==1,"partial malformed rules pause all dropping and warn once");rules(h,"missing_enchantment=1");h.onUpdate();check(mc.gameMode.actions.isEmpty()&&ChatUtils.errors==2,"unknown enchantment pauses all dropping");
for(String invalid:List.of("mending=0","mending=256","mending=1,mending=2","mending=1,","MENDING=1","mending=1=2")){try{EnchantmentRules.parse(invalid);throw new AssertionError("accepted "+invalid);}catch(IllegalArgumentException expected){}}
check(EnchantmentRules.matches(EnchantmentRules.parse("mod:effect=128"),Map.of("mod:effect",128)),"levels use value equality beyond Integer cache");}
public static void main(String[] args)throws Exception{basic();books();System.out.println("PASS: real AutoTrash empty/invalid rules, exact enchantments, container and selected-slot protection, whitelist, delay and drop actions");}}
''')
    java = Path.home() / '.jdks/jdk25.0.4_10/bin'
    argfile = out / 'javac.args'
    argfile.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in src.rglob('*.java')), encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out / 'classes'), '@' + str(argfile)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(out / 'classes'), 'audit.Batch12Probe'], check=True)
    (out / 'provenance.json').write_text(json.dumps({'staged': staged, 'target': target, 'sha256': provenance}, indent=2), encoding='utf-8')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--staged', action='store_true')
    args = parser.parse_args()
    run(args.staged, 'fabric', 'legacy')
    run(args.staged, 'fabric/versions/26.3', 'modern')


if __name__ == '__main__':
    main()
