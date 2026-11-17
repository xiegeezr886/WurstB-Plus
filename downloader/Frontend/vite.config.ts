import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import svgLoader from 'vite-svg-loader'
import { viteSingleFile } from 'vite-plugin-singlefile'
// Preserve upstream viewBox attributes so CSS-sized icons scale instead of clipping.
export default defineConfig({ plugins: [vue(), svgLoader({ svgo: false }), viteSingleFile()], build: { target: 'es2022' } })
