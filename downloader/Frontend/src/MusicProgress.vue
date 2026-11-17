<script setup lang="ts">
// Port of NeoRaven MusicCanvas.wave / MusicTransportMotion; GPL-3.0.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
const props = defineProps<{ percent?:number; active:boolean }>()
const host = ref<HTMLElement>()
const width = ref(634), progress = ref(0), playing = ref(0), phase = ref(0), sweep = ref(0)
const reduced = ref(false)
const known = computed(()=>props.percent !== undefined && Number.isFinite(props.percent))
const target = computed(()=>known.value ? Math.max(0,Math.min(1,props.percent! / 100)) : 0)
const end = computed(()=>width.value * progress.value)
const wave = computed(()=>{
  const p=progress.value
  const ramp=p<=.1||p>=.95?0:p<.14?(p-.1)/.04:p>.9?(.95-p)/.05:1
  const amplitude=.42*(9-3.24)/2*ramp*playing.value
  const count=Math.min(512,Math.max(2,Math.floor(end.value/2)+1))
  return Array.from({length:count},(_,i)=>{
    const x=end.value*i/(count-1)
    const y=8+Math.sin((x-phase.value)*Math.PI/20)*amplitude
    return `${i?'L':'M'}${x.toFixed(3)},${y.toFixed(3)}`
  }).join(' ')
})
let frame=0, previous=0, observer:ResizeObserver|undefined, media:MediaQueryList
function preference(){reduced.value=media.matches; if(reduced.value){progress.value=target.value;playing.value=0}}
function tick(now:number){
  const dt=previous?Math.min(100,now-previous):0;previous=now
  progress.value=reduced.value?target.value:progress.value+(target.value-progress.value)*(1-Math.exp(-dt/90))
  if(Math.abs(progress.value-target.value)<.0001)progress.value=target.value
  playing.value=reduced.value?0:playing.value+((props.active?1:0)-playing.value)*(1-Math.exp(-dt/85))
  if(!reduced.value){phase.value=(phase.value+dt*.028*playing.value)%40;sweep.value=(sweep.value+dt/1400)%1}
  frame=requestAnimationFrame(tick)
}
function visibility(){cancelAnimationFrame(frame);previous=0;if(!document.hidden)frame=requestAnimationFrame(tick)}
watch(target,(value,old)=>{if(value<old||reduced.value)progress.value=value})
onMounted(()=>{
  media=matchMedia('(prefers-reduced-motion: reduce)');preference();media.addEventListener('change',preference)
  observer=new ResizeObserver(entries=>{width.value=Math.max(1,entries[0].contentRect.width-4)})
  observer.observe(host.value!);document.addEventListener('visibilitychange',visibility);visibility()
})
onUnmounted(()=>{cancelAnimationFrame(frame);observer?.disconnect();media?.removeEventListener('change',preference);document.removeEventListener('visibilitychange',visibility)})
</script>
<template>
 <div ref="host" class="music-progress" role="progressbar" aria-label="下载进度" :aria-valuenow="known?Math.round(target*100):undefined" aria-valuemin="0" aria-valuemax="100" :aria-busy="active">
  <svg :viewBox="`-2 0 ${width+4} 16`" aria-hidden="true">
   <path :d="`M${known?end:0},8 H${width}`" stroke="#3C435F"/>
   <path v-if="known&&end>0" class="music-wave" :d="wave" stroke="#B5BEF0"/>
   <path v-else-if="!known" :d="`M${width*(reduced ? .35 : sweep*.75)},8 h${width*.25}`" stroke="#B5BEF0"/>
  </svg>
 </div>
</template>
<style scoped>
.music-progress{height:16px;margin-top:18px;width:100%}
.music-progress svg{display:block;width:100%;height:16px;overflow:visible}
.music-progress path{fill:none;stroke-width:3.24;stroke-linecap:round;stroke-linejoin:round}
</style>
