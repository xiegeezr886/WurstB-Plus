# -*- coding: utf-8 -*-
"""1.21+ API fixes for the ported 1.5.1 clickgui screens.

Enchantments are data-driven in 1.21+, so they must be looked up through the
level's registry (EnchantmentUtils) and compared with EnchantmentTags.CURSE.
"""
import re
import sys
from pathlib import Path

MAX_LEVEL_METHOD = (
    '\tprivate static int maxLevelOf(BookOffer offer)\n'
    '\t{\n'
    '\t\tHolder<Enchantment> enchantment = offer == null ? null\n'
    '\t\t\t: offer.getEnchantment();\n'
    '\t\treturn enchantment == null ? 0\n'
    '\t\t\t: enchantment.value().getMaxLevel();\n'
    '\t}\n\n')


def ensure_import(text, imp):
    if imp in text:
        return text
    anchor = 'import net.wurstclient.hacks.autolibrarian.BookOffer;'
    if anchor in text:
        return text.replace(anchor, anchor + '\n' + imp, 1)
    return text.replace('import net.wurstclient.hud2.nanovg.NanoVGUI;',
                        imp + '\nimport net.wurstclient.hud2.nanovg.NanoVGUI;',
                        1)


def fix_add_book_offer(path):
    p = Path(path)
    t = p.read_text(encoding='utf-8')
    t = ensure_import(t, 'import net.wurstclient.util.EnchantmentUtils;')
    t = t.replace(
        'BuiltInRegistries.ENCHANTMENT.holders().map(BookOffer::create)',
        'EnchantmentUtils.stream().map(BookOffer::create)')
    if 'private static int maxLevelOf' not in t:
        t = t.replace('\t@Override\n\tpublic boolean mouseClicked',
                      MAX_LEVEL_METHOD + '\t@Override\n\tpublic boolean mouseClicked',
                      1)
    # The merge can drop the local that feeds the entry renderer. Keep this
    # idempotent: the script may run more than once per project.
    decl = ('\t\t\tHolder<Enchantment> enchantment = '
            'bookOffer.getEnchantment();\n')
    needle = ('\t\t\tString name = bookOffer.getEnchantmentName();\n'
              '\t\t\tint nameColor = enchantment.is(EnchantmentTags.CURSE)')
    t = t.replace(decl + decl, decl)
    if needle in t and decl + needle not in t:
        t = t.replace(needle, decl + needle)
    t = t.replace(
        '\t\t\tEnchantment enchantment = bookOffer.getEnchantment();\n',
        '\t\t\tHolder<Enchantment> enchantment = bookOffer.getEnchantment();\n')
    t = t.replace('enchantment.isCurse()',
                  'enchantment.is(EnchantmentTags.CURSE)')
    p.write_text(t, encoding='utf-8', newline='\n')
    print('add', path)


def fix_edit_book_offer(path):
    p = Path(path)
    t = p.read_text(encoding='utf-8')
    t = t.replace('new ResourceLocation("enchanted_book")',
                  'ResourceLocation.parse("enchanted_book")')
    t = t.replace(
        'Enchantment enchantment = bookOffer.getEnchantment();',
        'Holder<Enchantment> enchantment = bookOffer.getEnchantment();')
    t = t.replace('enchantment.isCurse()',
                  'enchantment.is(EnchantmentTags.CURSE)')
    p.write_text(t, encoding='utf-8', newline='\n')
    print('edit', path)


def main():
    for project in sys.argv[1:]:
        root = Path(project) / 'src/main/java/net/wurstclient'

        p = root / 'mixin/OptionsSubScreenMixin.java'
        if p.exists():
            t = p.read_text(encoding='utf-8')
            t = t.replace(
                'import net.minecraft.client.gui.screens.OptionsSubScreen;',
                'import net.minecraft.client.gui.screens.options.'
                'OptionsSubScreen;')
            p.write_text(t, encoding='utf-8', newline='\n')

        p = root / 'hacks/scaffold/ScaffoldUtils.java'
        if p.exists():
            t = p.read_text(encoding='utf-8')
            t = t.replace('EnchantmentTableBlock', 'EnchantingTableBlock')
            p.write_text(t, encoding='utf-8', newline='\n')

        fix_add_book_offer(root / 'clickgui2/screens/AddBookOfferScreen.java')
        for name in ('EditBookOfferScreen.java', 'EditBookOffersScreen.java'):
            fix_edit_book_offer(root / 'clickgui2/screens' / name)

        p = root / 'hacks/KillauraHack.java'
        if p.exists():
            t = p.read_text(encoding='utf-8')
            t = t.replace(
                'stack.isEdible()',
                'stack.has(net.minecraft.core.component.DataComponents.FOOD)')
            p.write_text(t, encoding='utf-8', newline='\n')

        # Screen/EditBox API for 1.20.5+.
        for name in ('AddBookOfferScreen.java', 'EditBlockScreen.java',
                     'EditBookOfferScreen.java', 'EditBookOffersScreen.java',
                     'EditColorScreen.java', 'EditSliderScreen.java',
                     'EditTextFieldScreen.java', 'SelectFileScreen.java'):
            p = root / 'clickgui2/screens' / name
            if not p.exists():
                continue
            t = p.read_text(encoding='utf-8')
            t = t.replace(
                'renderDirtBackground(context);',
                'renderBackground(context, mouseX, mouseY, partialTicks);')
            t = t.replace(
                'renderBackground(context);',
                'renderBackground(context, mouseX, mouseY, partialTicks);')
            t = re.sub(r'(?m)^\s*\w+\.tick\(\);\r?\n', '', t)
            p.write_text(t, encoding='utf-8', newline='\n')

        for name in ('AbstractSelectionListMixin.java',
                     'OptionsSubScreenMixin.java'):
            p = root / 'mixin' / name
            if not p.exists():
                continue
            t = p.read_text(encoding='utf-8')
            t = t.replace(
                '\t\tAbstractSelectionList<?> list =\n'
                '\t\t\t(AbstractSelectionList<?>)(Object)this;\n'
                '\t\tlist.setRenderBackground(false);\n', '')
            t = t.replace('\t\tlist.setRenderBackground(false);\n', '')
            t = t.replace('\t\tlist.setRenderTopAndBottom(false);\n', '')
            t = t.replace(
                '\t\t// The vanilla top/bottom fade overlays read as dirt bands '
                'on top of\n'
                '\t\t// the shared menu background.\n', '')
            p.write_text(t, encoding='utf-8', newline='\n')

        print('fixed', project)


if __name__ == '__main__':
    main()
