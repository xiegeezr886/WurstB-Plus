# Sources and modifications

This is an unofficial Opai-to-LiquidBounce theme port.

The integration layer, Svelte settings, menus and HUD editor are derived from the user-supplied LiquidBounce Nextgen source archive, copyright CCBlueX and contributors, GPL-3.0-or-later. See LICENSE and the retained upstream file headers.

The visual references, recovered animation calculations, fonts, images, audio and sprite resources come from the user-supplied Opai-Samsara-Shell archive. Original attribution includes Opai / StylesFoundation, Samsara contributors, and Jon_awa for the dynamic island state implementation. The source archive's license is included as LICENSE-OPAI-SHELL. Individual third-party assets remain attributable to their respective creators; this notice does not relicense those assets.

Modifications include browser rendering adaptations, the Opai appearance, API error handling, category mapping, profiles, additional components, packaging and isolated browser tests. No affiliation or endorsement by the upstream projects is claimed. Source is supplied alongside the theme archive.

Additional Via protocol card artwork is Minecraft promotional release artwork © Mojang / Microsoft, including press-hosted mirrors. It is not licensed under the theme's GPL. Each added image is center-cropped and Gaussian-blurred offline; original Opai version images remain unchanged. Original URLs, article links, source and processed SHA-256 values are in `opai/version-extra/sources.json` (runtime package) and `public/opai/version-extra/sources.json` (source repository).

Music integration: NCM request protocol, native StreamingSoundPlayer and PrefetchInputStream are adapted from opai-client/tritium-music (IzumiiKonata and contributors), GPL-3.0; see music/LICENSE-TRITIUM and music/UPSTREAM.md for the pinned upstream commit. Vendored JLayer and jFLAC decoder sources retain their original notices. The service source is in `music/src/`; release packages include `music/OpaiMusicService-Source.zip`.

The theme encodes login QR images locally using node-qrcode (soldair and contributors), MIT; see music/LICENSE-QRCODE.

GD music API attribution: GD音乐台 (music.gdstudio.xyz), GD Studio. The provider states CC BY-NC 4.0, non-commercial study only. The provider's API/service terms are separate from the GPL theme code. No music recordings, API account credentials or user sessions are bundled in the distribution.

Dango 3D lyric scene and YRC/LRC parsing: adapted from Sorihiko/DangoMusic at 985d55303b9df9866c41099ee70a772b5a48c4f6, GPL-3.0. See music/LICENSE-DANGO and music/UPSTREAM.md. The original LyricsModule.kt and LyricsProvider.kt are included in the corresponding source archive. Native render submission uses LiquidBounce / Minecraft APIs; the original OpenGL glow/flash shaders and Listen Together are not bundled.

Bed progress panel geometry and chest grid / 380 ms per-slot feedback are adapted from the supplied Samsara DynamicIslandState.java, DynamicIslandPainter.java and ChestStealer.java. The pointer-drag account grouping and persistent server/world display ordering are theme-side integration changes. Original project and author notices remain in the supplied shell license and this notice.
