# -*- coding: utf-8 -*-
"""1.20.5+ API fixes for the ported 1.5.1 UI/hacks."""
import re
import sys
from pathlib import Path


def patch(project):
    root = Path(project) / 'src/main/java/net/wurstclient'

    p = root / 'hacks/scaffold/ScaffoldUtils.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('EnchantmentTableBlock', 'EnchantingTableBlock')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'hacks/KillauraHack.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('stack.isEdible()',
                      'stack.has(net.minecraft.core.component.DataComponents.FOOD)')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'clickgui2/screens/AddBookOfferScreen.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('EnchantmentUtils.stream()',
                      'BuiltInRegistries.ENCHANTMENT.holders()')
        t = t.replace(
            'BuiltInRegistries.ENCHANTMENT.stream().map(BookOffer::create)',
            'BuiltInRegistries.ENCHANTMENT.holders().map(BookOffer::create)')
        if 'private static int maxLevelOf' not in t:
            method = (
                '\tprivate static int maxLevelOf(BookOffer offer)\n'
                '\t{\n'
                '\t\tHolder<Enchantment> enchantment = offer == null ? null\n'
                '\t\t\t: offer.getEnchantment();\n'
                '\t\treturn enchantment == null ? 0\n'
                '\t\t\t: enchantment.value().getMaxLevel();\n'
                '\t}\n\n')
            t = t.replace('\t@Override\n\tpublic boolean mouseClicked',
                          method + '\t@Override\n\tpublic boolean mouseClicked',
                          1)
        t = t.replace(
            'String name = bookOffer.getEnchantmentName();\n'
            '\t\t\tint nameColor = enchantment.value().isCurse()',
            'String name = bookOffer.getEnchantmentName();\n'
            '\t\t\tHolder<Enchantment> enchantment = bookOffer.getEnchantment();\n'
            '\t\t\tint nameColor = enchantment.value().isCurse()')
        p.write_text(t, encoding='utf-8', newline='\n')

    for name in ('EditBookOfferScreen.java', 'EditBookOffersScreen.java'):
        p = root / 'clickgui2/screens' / name
        if p.exists():
            t = p.read_text(encoding='utf-8')
            t = t.replace(
                'Enchantment enchantment = bookOffer.getEnchantment();',
                'Enchantment enchantment = '
                'bookOffer.getEnchantment().value();')
            t = t.replace('renderDirtBackground(context);',
                          'renderBackground(context, mouseX, mouseY, '
                          'partialTicks);')
            p.write_text(t, encoding='utf-8', newline='\n')

    for name in ('AbstractSelectionListMixin.java',
                 'OptionsSubScreenMixin.java'):
        p = root / 'mixin' / name
        if p.exists():
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

    # Screen/EditBox API used by every 1.20.5+ clickgui screen.
    for name in ('AddBookOfferScreen.java', 'EditBlockScreen.java',
                 'EditBookOfferScreen.java', 'EditBookOffersScreen.java',
                 'EditColorScreen.java', 'EditSliderScreen.java',
                 'EditTextFieldScreen.java', 'SelectFileScreen.java'):
        p = root / 'clickgui2/screens' / name
        if not p.exists():
            continue
        t = p.read_text(encoding='utf-8')
        t = t.replace('renderBackground(context);',
                      'renderBackground(context, mouseX, mouseY, '
                      'partialTicks);')
        t = re.sub(r'(?m)^\s*\w+\.tick\(\);\r?\n', '', t)
        p.write_text(t, encoding='utf-8', newline='\n')

    print('fixed', project)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
