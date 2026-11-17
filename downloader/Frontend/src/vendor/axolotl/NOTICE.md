# Axolotl / Modrinth frontend source

Source: https://github.com/Axolotl-Launcher/Axolotl
Revision: dae3f4cf9e7665e3ae8f65f729215338446e213e
Local checkout: reference/Axolotl (ignored by Git).

Copyright Rinth, Inc. and original contributors. Axolotl modifications Copyright © 2026 Garbage Human Studio, developed by Mystic Stars. GPL-3.0-only; see public/licenses/Axolotl-GPL-3.0.txt and the original COPYING files shipped alongside it.

- buttons/Button.vue, ButtonFrame.vue, IconButton.vue and types.ts are copied **unchanged** from packages/ui/src/components/base/buttons/.
- icons/*.svg are copied **unchanged** from packages/assets/icons/; no Axolotl brand logo is included. Loader artwork comes separately from the official projects, documented in src/assets/loaders/SOURCES.md.
- home-widget.css is copied **unchanged** from apps/app-frontend/src/components/home/home-widget.css.
- HomeLaunchCard.vue adapts the actual template, grid layout and gradient CSS from apps/app-frontend/src/components/home/HomeLaunchCard.vue. Game launch state, accounts and instance queries are replaced with downloader slots; source classes and the stage card structure are retained.
- The independent settings route and bottom settings control follow App.vue and components/ui/NavButton.vue.

WurstB+ changes use its existing #B5BEF0 accent and #0C185C contrast from fabric/src/main/java/net/wurstclient/clickgui2/OpaiStyle.java. Vue and Tailwind compile the upstream components; the desktop WebView2 host connects them to the .NET download engine.
