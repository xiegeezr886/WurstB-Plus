<!-- Adapted from Axolotl HomeLaunchCard.vue, revision dae3f4c. GPL-3.0-only. See NOTICE.md. -->
<script setup lang="ts">
import './home-widget.css'
</script>
<template>
  <div class="home-launch" data-variant="stage" data-shape="hero">
    <section class="home-launch-card home-surface">
      <div class="home-launch-glow" aria-hidden="true" />
      <div class="home-launch-icon"><slot name="icon" /></div>
      <div class="home-launch-copy"><slot /></div>
      <div class="home-launch-footer"><slot name="footer" /></div>
    </section>
  </div>
</template>
<style scoped>
/* Original stage grid and glow from apps/app-frontend/src/components/home/HomeLaunchCard.vue. */
.home-launch { display:flex; min-width:0; flex-direction:column; gap:1rem; container-type:inline-size; }
.home-launch-card { position:relative; isolation:isolate; display:grid; min-width:0; align-items:center; overflow:hidden; }
.home-launch-card > :not(.home-launch-glow) { position:relative; z-index:1; }
.home-launch-glow {
  --home-launch-tint:var(--color-brand);
  --home-launch-glow-mix:calc(var(--home-widget-bg-alpha, 1) * 12% + 8%);
  position:absolute; z-index:0; inset:0;
  background:radial-gradient(55% 90% at 100% 35%, color-mix(in srgb, var(--home-launch-tint) var(--home-launch-glow-mix), transparent), transparent 72%),
    radial-gradient(45% 70% at 0% 100%, color-mix(in srgb, var(--color-brand) calc(var(--home-launch-glow-mix) * .5), transparent), transparent 70%);
  pointer-events:none;
}
.home-launch[data-shape='hero'] .home-launch-card {
  grid-template-columns:auto minmax(0, 1fr); grid-template-rows:1fr auto;
  grid-template-areas:'icon copy' 'footer footer'; row-gap:1.5rem; column-gap:1.75rem;
  padding:2rem 2.25rem 1.75rem;
}
.home-launch-icon { grid-area:icon; display:flex; border-radius:var(--radius-lg); transition:transform 160ms ease; }
.home-launch-icon:hover { transform:translateY(-1px) scale(1.02); }
.home-launch-copy { grid-area:copy; min-width:0; display:flex; flex-direction:column; gap:.375rem; }
.home-launch-footer { grid-area:footer; display:flex; min-width:0; align-items:center; justify-content:space-between; gap:.75rem; }
/* Downloader adaptation: white window, client palette, slightly larger stage. */
.home-launch-card { min-height:300px; border:1px solid #e7e9f6; border-radius:26px; background:linear-gradient(115deg, #e2e5fa, #f8f9ff 55%, #fff); }
@container (max-width:540px) {
  .home-launch[data-shape='hero'] .home-launch-card { padding:1.75rem; column-gap:1rem; }
}
@media (prefers-reduced-motion:reduce) { .home-launch-icon { transition:none; } }
</style>
