package net.wurstclient.clickgui2;

import java.awt.Color;
import java.util.List;

/** Rise 6.9.5's palette data and time/position-dependent accent interpolation. */
enum NavigatorTheme
{    AUBERGINE("Aubergine", new Color(170, 7, 107), new Color(97, 4, 95), KeyColor.PURPLE, KeyColor.RED),
    AQUA("Aqua", new Color(185, 250, 255), new Color(79, 199, 200), KeyColor.AQUA),
    BANANA("Banana", new Color(253, 236, 177), new Color(255, 255, 255), KeyColor.YELLOW),
    BLEND("Blend", new Color(71, 148, 253), new Color(71, 253, 160), KeyColor.AQUA, KeyColor.LIME),
    BLOSSOM("Blossom", new Color(226, 208, 249), new Color(49, 119, 115), KeyColor.PINK, KeyColor.GRAY),
    BUBBLEGUM("Bubblegum", new Color(243, 145, 216), new Color(152, 165, 243), KeyColor.PINK, KeyColor.PURPLE),
    CANDY_CANE("Candy Cane", new Color(255, 0, 0), new Color(255, 255, 255), KeyColor.RED),
    CHERRY("Cherry", new Color(187, 55, 125), new Color(251, 211, 233), KeyColor.RED, KeyColor.PURPLE, KeyColor.PINK),
    CHRISTMAS("Christmas", new Color(255, 64, 64), new Color(255, 255, 255), new Color(64, 255, 64), KeyColor.RED, KeyColor.LIME),
    CORAL("Coral", new Color(244, 168, 150), new Color(52, 133, 151), KeyColor.PINK, KeyColor.ORANGE, KeyColor.DARK_BLUE),
    DIGITAL_HORIZON("Digital Horizon", new Color(95, 195, 228), new Color(229, 93, 135), KeyColor.AQUA, KeyColor.RED, KeyColor.PINK),
    EXPRESS("Express", new Color(173, 83, 137), new Color(60, 16, 83), KeyColor.PURPLE, KeyColor.PINK),
    LIME_WATER("Lime Water", new Color(18, 255, 247), new Color(179, 255, 171), KeyColor.AQUA, KeyColor.LIME),
    LUSH("Lush", new Color(168, 224, 99), new Color(86, 171, 47), KeyColor.LIME, KeyColor.DARK_GREEN),
    HALOGEN("Halogen", new Color(255, 65, 108), new Color(255, 75, 43), KeyColor.RED, KeyColor.ORANGE),
    HYPER("Hyper", new Color(236, 110, 173), new Color(52, 148, 230), KeyColor.PINK, KeyColor.DARK_BLUE, KeyColor.AQUA),
    MAGIC("Magic", new Color(74, 0, 224), new Color(142, 45, 226), KeyColor.DARK_BLUE, KeyColor.PURPLE),
    MAY("May", new Color(238, 79, 238), new Color(253, 219, 245), KeyColor.PINK, KeyColor.PURPLE),
    ORANGE_JUICE("Orange Juice", new Color(252, 74, 26), new Color(247, 183, 51), KeyColor.ORANGE, KeyColor.YELLOW),
    PASTEL("Pastel", new Color(243, 155, 178), new Color(207, 196, 243), KeyColor.PINK),
    PUMPKIN("Pumpkin", new Color(241, 166, 98), new Color(255, 216, 169), new Color(227, 139, 42), KeyColor.ORANGE),
    SATIN("Satin", new Color(215, 60, 67), new Color(140, 23, 39), KeyColor.RED),
    SNOWY_SKY("Snowy Sky", new Color(1, 171, 179), new Color(234, 234, 234), new Color(18, 232, 232), KeyColor.AQUA, KeyColor.GRAY),
    STEEL_FADE("Steel Fade", new Color(66, 134, 244), new Color(55, 59, 68), KeyColor.DARK_BLUE, KeyColor.GRAY),
    SUNDAE("Sundae", new Color(206, 74, 126), new Color(122, 44, 77), KeyColor.PINK, KeyColor.PURPLE, KeyColor.RED),
    SUNKIST("Sunkist", new Color(242, 201, 76), new Color(242, 153, 74), KeyColor.YELLOW, KeyColor.ORANGE),
    WATER("Water", new Color(12, 232, 199), new Color(12, 163, 232), KeyColor.AQUA, KeyColor.DARK_BLUE),
    LEGACY("Legacy", new Color(7393023), new Color(7393023), KeyColor.AQUA, KeyColor.DARK_BLUE),
    WINTER("Winter", Color.WHITE, Color.WHITE, KeyColor.GRAY, KeyColor.GRAY),
    PEONY("Peony", new Color(226, 208, 249), new Color(207, 171, 255), KeyColor.PINK, KeyColor.GRAY),
    SHADOW("Shadow", new Color(97, 131, 255), new Color(206, 212, 255), KeyColor.AQUA),
    WOOD("Wood", new Color(79, 109, 81), new Color(170, 139, 87), new Color(240, 235, 206), KeyColor.DARK_GREEN),
    CREIDA("Creida", new Color(-11644304).brighter().brighter(), new Color(-11644304).darker(), KeyColor.GRAY),
    CREIDA_TWO("Creida Two", new Color(-6632725), new Color(-8406042).darker(), KeyColor.GRAY),
    GOTHIC("Gothic", new Color(31, 30, 30), new Color(196, 190, 190), KeyColor.GRAY),
    SEN("Rue", new Color(234, 118, 176), new Color(31, 30, 30), KeyColor.PINK),
    PURPLE("Purple", new Color(5391249), new Color(5391249).brighter()),
    TEST("Rainbow", Color.WHITE, Color.WHITE),
    NORD("Nord", new Color(143, 188, 187), new Color(163, 190, 140), new Color(236, 239, 244), KeyColor.AQUA, KeyColor.GRAY);
    final String title;
    final Color first;
    final Color second;
    final Color third;
    final boolean triColor;
    final List<KeyColor> keys;

    NavigatorTheme(String title, Color first, Color second, KeyColor... keys) {
        this(title, first, second, first, false, keys);
    }

    NavigatorTheme(String title, Color first, Color second, Color third, KeyColor... keys) {
        this(title, first, second, third, true, keys);
    }

    NavigatorTheme(String title, Color first, Color second, Color third, boolean triColor, KeyColor... keys) {
        this.title = title;
        this.first = first;
        this.second = second;
        this.third = third;
        this.triColor = triColor;
        this.keys = List.of(keys);
    }

    int primary() { return this == TEST ? accent(0, 0, System.currentTimeMillis()) : first.getRGB(); }
    int secondary() { return this == TEST ? accent(0, 50, System.currentTimeMillis()) : second.getRGB(); }
    int tertiary() { return this == TEST ? accent(0, 100, System.currentTimeMillis()) : third.getRGB(); }

    int accent(double x, double y, long now) {
        if(this == TEST)
            return Color.HSBtoRGB((float)(Math.ceil((now + (int)((x + y) * 10)) / 10.0) % 360 / 360), 0.6F, 1);
        double blend = Math.sin(now / 600.0 + x * 0.005 + y * 0.06) * 0.5 + 0.5;
        if(triColor)
            return blend <= 0.5 ? mix(second, first, blend * 2) : mix(third, second, (blend - 0.5) * 2);
        return mix(first, second, blend);
    }

    private static int mix(Color a, Color b, double weight) {
        return new Color((int)(a.getRed() * weight + b.getRed() * (1 - weight)),
            (int)(a.getGreen() * weight + b.getGreen() * (1 - weight)),
            (int)(a.getBlue() * weight + b.getBlue() * (1 - weight))).getRGB();
    }

    enum KeyColor {
        RED(255,50,50), ORANGE(255,128,50), YELLOW(255,255,50), LIME(128,255,50),
        DARK_GREEN(50,128,50), AQUA(50,200,255), DARK_BLUE(50,100,200),
        PURPLE(128,50,255), PINK(255,128,255), GRAY(100,100,110);
        final int argb;
        KeyColor(int r, int g, int b) { argb = new Color(r,g,b).getRGB(); }
    }
}
