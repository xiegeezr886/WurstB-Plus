# Copying

The source code of Modrinth's UI library is licensed under the GNU General Public License, Version 3 only, which is provided in the file [LICENSE](./LICENSE). However, some files listed below are licensed under a different license.

## Modrinth logo

The use of Modrinth branding elements, including but not limited to the wrench-in-labyrinth logo, the landing image, and any variations thereof, is strictly prohibited without explicit written permission from Rinth, Inc. This includes trademarks, logos, or other branding elements.

> All rights reserved. © 2020-2025 Rinth, Inc.

This includes, but may not be limited to, the following files:

- src/components/brand/\*
- src/components/servers/ModrinthServersIcon.vue

## External logos

The following files are owned by their respective copyright holders and must be used within each of their Brand Guidelines:

- src/components/servers/MedalBackgroundImage.vue

## LogShare-Web-UI log viewer

The log viewer (log highlighting engine, virtualized log viewport, and console toolbar) is adapted from [LogShare-Web-UI](https://github.com/NingZeStudio/LogShare-Web-UI), the frontend of the LogShare.CN log analysis platform, at commit `b5ae2dc`. LogShare-Web-UI is distributed under the MIT License, Copyright (c) 2024 LogShare.CN Team; a verbatim copy of that license is provided in [third-party/licenses/MIT.txt](../../third-party/licenses/MIT.txt).

The following files are adapted from LogShare-Web-UI:

- `src/layouts/shared/console/composables/log-highlight.ts` — regex rules, Trie pre-filtering, LRU caching and HTML coloring logic ported from `src/lib/logParser.worker.ts`, with colors from `src/assets/LogsAnalysis.css`
- `src/layouts/shared/console/components/LogViewport.vue` — interaction and layout modeled after `src/views/LogView.vue` (line numbers, level filtering, line wrapping, font size, search highlighting, fullscreen)
- `src/layouts/shared/console/layout.vue` — console toolbar arrangement modeled after `src/views/LogView.vue`
