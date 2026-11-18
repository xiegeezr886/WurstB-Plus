package net.wurstclient.neoraven.music;

import net.wurstclient.clickgui2.NavigatorStyle;

/** The player's Material roles use Navigator's palette per the user's style preference. */
final class MusicPalette {
  final int surfaceContainer = NavigatorStyle.BACKGROUND;
  final int surfaceContainerHigh = NavigatorStyle.SETTINGS;
  final int surfaceContainerHighest = NavigatorStyle.FIELD;
  final int onSurface = NavigatorStyle.TEXT, onSurfaceVariant = NavigatorStyle.MUTED;
  final int primaryContainer = NavigatorStyle.FIELD, onPrimaryContainer = NavigatorStyle.ACCENT;
  final int secondaryContainer = NavigatorStyle.OPTION, onSecondaryContainer = NavigatorStyle.INK;
  final int onPrimary = NavigatorStyle.INK, error = 0xFFFF9393;
}
