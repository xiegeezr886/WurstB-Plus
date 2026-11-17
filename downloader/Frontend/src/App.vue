<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import MusicProgress from './MusicProgress.vue'
import Button from './vendor/axolotl/buttons/Button.vue'
import IconButton from './vendor/axolotl/buttons/IconButton.vue'
import HomeLaunchCard from './vendor/axolotl/HomeLaunchCard.vue'
import DownloadIcon from './vendor/axolotl/icons/download.svg'
import SettingsIcon from './vendor/axolotl/icons/settings.svg'
import BackIcon from './vendor/axolotl/icons/arrow-left.svg'
import CheckIcon from './vendor/axolotl/icons/check.svg'
import FolderIcon from './vendor/axolotl/icons/folder.svg'
import MinusIcon from './vendor/axolotl/icons/minus.svg'
import MaximizeIcon from './vendor/axolotl/icons/maximize.svg'
import CloseIcon from './vendor/axolotl/icons/x.svg'
import FabricIcon from './assets/loaders/fabric.png'
import ForgeIcon from './assets/loaders/forge.png'
import NeoForgeIcon from './assets/loaders/neoforge.png'
import RefreshIcon from './vendor/axolotl/icons/refresh-cw.svg'
import projectLogoInk from './assets/project-logo-ink.png'
import projectLogoIsland from './assets/project-logo-island.png'
import { state, send, native, interfaceVisible } from './bridge'

type Page='home'|'versions'|'loader'|'download'|'settings'
const page=ref<Page>('home'), settingsReturn=ref<Page>('home')
const titles={home:'首页',versions:'选择版本',loader:'选择加载器',download:'下载',settings:'设置'}
const release=ref(''), minecraft=ref(''), loader=ref('')
const selectedRelease=computed(()=>state.releases.find(r=>r.tag===release.value))
const compareVersions=(a:string,b:string)=>{
  const aa=a.split('.').map(Number),bb=b.split('.').map(Number)
  for(let i=0;i<Math.max(aa.length,bb.length);i++){const delta=(bb[i]??0)-(aa[i]??0);if(delta)return delta} return 0
}
const versions=computed(()=>[...new Set(selectedRelease.value?.assets.map(a=>a.minecraft)??[])].sort(compareVersions))
const loaders=computed(()=>[...new Set(selectedRelease.value?.assets.filter(a=>a.minecraft===minecraft.value).map(a=>a.loader)??[])])
const asset=computed(()=>selectedRelease.value?.assets.find(a=>a.minecraft===minecraft.value&&a.loader===loader.value))
const summary=computed(()=>asset.value?`${release.value} · Minecraft ${minecraft.value} · ${loader.value}`:'Forge · NeoForge · Fabric')
const canBack=computed(()=>page.value==='settings'||(!state.busy&&page.value!=='home'))
const sources=computed(()=>[['auto','自动选择线路'],...state.sources.map(s=>[s.id,s.name])])
const mirrors=computed(()=>state.sources.filter(s=>s.id!=='direct').map(s=>({...s,probe:state.probes.find(p=>p.id===s.id)})).sort((a,b)=>Number(b.probe?.available??false)-Number(a.probe?.available??false)||(b.probe?.speed??0)-(a.probe?.speed??0)))
const availableMirrors=computed(()=>mirrors.value.filter(s=>s.probe?.available).length)
const loaderIcons={Fabric:FabricIcon,Forge:ForgeIcon,NeoForge:NeoForgeIcon}
const loaderIcon=(name:string)=>loaderIcons[name as keyof typeof loaderIcons]
const mb=(n:number)=>`${(n/1048576).toFixed(1)} MB`
const speed=(n:number)=>n>=1048576?`${(n/1048576).toFixed(1)} MB/s`:`${Math.round(n/1024)} KB/s`
watch(()=>state.releases,()=>{
  if(!selectedRelease.value) release.value=state.releases.find(r=>r.tag===state.selection.release)?.tag??state.releases[0]?.tag??''
},{immediate:true})
watch(versions,()=>{if(!versions.value.includes(minecraft.value))minecraft.value=versions.value.includes(state.selection.minecraft)?state.selection.minecraft:versions.value[0]??''},{immediate:true})
watch(loaders,()=>{if(!loaders.value.includes(loader.value))loader.value=loaders.value.includes(state.selection.loader)?state.selection.loader:loaders.value.includes('Fabric')?'Fabric':loaders.value[0]??''},{immediate:true})
watch(page,async()=>{await nextTick();document.querySelector<HTMLElement>('main h1, main h2')?.focus({preventScroll:true})})
function configure(){send('configure',{release:release.value,minecraft:minecraft.value,loader:loader.value})}
function next(){if(state.busy||!asset.value)return;configure();page.value=page.value==='home'?'versions':page.value==='versions'?'loader':'download'}
function settings(){if(page.value==='settings')page.value=settingsReturn.value;else{settingsReturn.value=page.value;page.value='settings'}}
function back(){if(!canBack.value)return;page.value=page.value==='settings'?settingsReturn.value:page.value==='download'?'loader':page.value==='loader'?'versions':'home'}
function download(){configure();send('download',{release:release.value,minecraft:minecraft.value,loader:loader.value})}
function probe(){configure();send('probe')}
function changeOption(event:Event,key:string){send('configure',{[key]:(event.target as HTMLSelectElement).value})}
function titleDown(event:MouseEvent){if(event.button===0 && !(event.target as Element).closest('button'))send('drag')}
</script>
<template>
 <div class="app-shell" :class="{'startup-hidden':!interfaceVisible}" :inert="!interfaceVisible">
  <header class="titlebar" @mousedown="titleDown" @dblclick.self="send('maximize')">
   <div class="brand"><img class="brand-mark" :src="projectLogoIsland" alt="WDownloader" width="34" height="34" draggable="false"/></div>
   <IconButton label="返回上一页" type="quiet" :disabled="!canBack" @click="back"><BackIcon/></IconButton>
   <span class="page-title">{{titles[page]}}</span>
   <div class="window-actions"><IconButton label="最小化" type="quiet" @click="send('minimize')"><MinusIcon/></IconButton><IconButton label="最大化或还原" type="quiet" @click="send('maximize')"><MaximizeIcon/></IconButton><IconButton label="关闭" type="quiet" class="window-close" @click="send('close')"><CloseIcon/></IconButton></div>
  </header>
  <main :class="{'settings-main':page==='settings'}">
   <Transition name="page" mode="out-in">
    <div v-if="page==='home'" key="home" class="page-content home-page">
     <HomeLaunchCard>
      <template #icon><div class="download-art"><DownloadIcon/></div></template>
      <h1 class="project-wordmark" tabindex="-1"><img :src="projectLogoInk" alt="WurstB+ Plus" width="1463" height="430" draggable="false"/></h1>
      <template #footer><div class="card-actions"><Button type="colored" color="brand" size="xl" class="download-primary" :disabled="state.busy||!asset" @click="next"><DownloadIcon/>下载客户端</Button><p v-if="state.detail" class="muted" role="alert">{{state.detail}}</p></div></template>
     </HomeLaunchCard>
    </div>
    <section v-else-if="page==='versions'" key="versions" class="page-content flow-card">
     <h2 tabindex="-1">选择版本</h2>
     <div class="version-fields"><label>发布版本<select v-model="release" :disabled="state.busy" aria-label="发布版本"><option v-for="r in state.releases" :key="r.tag" :value="r.tag">{{r.label}}</option></select></label><label>Minecraft 游戏版本<select v-model="minecraft" :disabled="state.busy" aria-label="Minecraft 游戏版本"><option v-for="v in versions" :key="v">{{v}}</option></select></label></div>
     <Button type="colored" color="brand" size="xl" :disabled="state.busy||!asset" @click="next">下一步：选择加载器</Button>
    </section>
    <section v-else-if="page==='loader'" key="loader" class="page-content flow-card">
     <h2 tabindex="-1">选择加载器</h2><p class="muted">{{release}} · Minecraft {{minecraft}}</p>
     <div class="loader-options" role="radiogroup" aria-label="模组加载器"><label v-for="name in loaders" :key="name" class="loader-tile" :class="{selected:loader===name}"><input type="radio" name="loader" v-model="loader" :value="name" :disabled="state.busy"/><img :src="loaderIcon(name)" class="loader-logo" alt="" width="48" height="48" draggable="false"/><strong>{{name}}</strong><span>{{loader===name?'已选择':'点击选择'}}<CheckIcon v-if="loader===name"/></span></label></div>
     <Button type="colored" color="brand" size="xl" :disabled="state.busy||!asset" @click="next">下一步：下载</Button>
    </section>
    <section v-else-if="page==='download'" key="download" class="page-content flow-card">
     <div class="download-heading"><h2 tabindex="-1">WurstB+ Plus</h2><span class="size-pill">{{asset?mb(asset.size):''}}</span></div>
     <p class="subheading">{{summary}}</p><p class="filename muted">{{asset?.name}}</p>
     <div class="save-location"><FolderIcon/><div><span>保存到</span><p :title="state.folder">{{state.folder}}</p></div></div>
     <Button type="colored" color="brand" size="xl" class="download-primary" :disabled="state.busy||!asset||!native" @click="download"><DownloadIcon/>{{state.busy?'正在下载…':state.completed?'再次下载':'下载客户端'}}</Button>
     <MusicProgress v-if="state.busy||state.progress" :percent="state.progress?.percent" :active="state.busy"/>
     <p v-if="state.busy||state.progress||state.completed||state.detail" class="download-status" role="status">{{state.status}}</p>
     <p v-if="state.progress&&state.busy" class="muted transfer-detail">{{mb(state.progress.received)}} / {{mb(state.progress.total)}} · {{speed(state.progress.speed)}}</p>
     <p v-else-if="!state.completed&&state.detail" class="muted transfer-detail">{{state.detail}}</p>
     <div v-if="state.completed" class="completion-actions"><Button type="quiet" @click="send('openFolder')"><FolderIcon/>打开下载文件夹</Button><Button type="quiet" @click="send('copyHash')">复制 SHA-256</Button></div>
    </section>
    <section v-else key="settings" class="page-content settings-page">
     <h2 tabindex="-1">下载设置</h2>
     <div class="settings-card"><div class="setting-heading"><h3>保存位置</h3><div><Button type="quiet" :disabled="state.busy" @click="send('browse')">更改</Button></div></div><p class="folder-text">{{state.folder}}</p></div>
     <div class="settings-card"><div class="network-fields"><label>下载线路<select aria-label="下载线路" :value="state.sourceId" :disabled="state.busy" @change="changeOption($event,'sourceId')"><option v-for="[id,name] in sources" :key="id" :value="id">{{name}}</option></select></label><label>下载线程数<select :value="state.connections" :disabled="state.busy" @change="changeOption($event,'connections')"><option v-for="n in [1,2,4,8]" :value="n" :key="n">{{n}} 线程</option></select></label></div><p class="muted">不支持分段的线路会自动使用单线程。</p><div class="mirror-heading"><h3>镜像节点列表</h3><Button type="quiet" :disabled="state.busy||!asset" @click="probe"><RefreshIcon/>重新测速</Button></div><div class="mirror-counts"><span>总节点数 <strong>{{mirrors.length}}</strong></span><span>可用节点 <strong>{{availableMirrors}}</strong></span></div><div class="mirror-list" aria-label="镜像节点列表"><button v-for="source in mirrors" :key="source.id" class="mirror-row" :class="{selected:state.sourceId===source.id}" :disabled="state.busy" :aria-pressed="state.sourceId===source.id" @click="send('configure',{sourceId:source.id})"><span class="mirror-url">{{source.url}}</span><span class="mirror-speed">{{source.probe?(source.probe.available?`${speed(source.probe.speed)} · ${source.probe.milliseconds} ms`:'不可用'):'—'}}</span><CheckIcon v-if="state.sourceId===source.id"/></button></div></div>
     <div class="settings-card"><h3>发布列表</h3><div class="settings-links"><Button type="quiet" :disabled="state.busy" @click="send('refresh')">刷新发布</Button><Button type="quiet" @click="send('releaseNotes')">发布说明</Button><Button type="quiet" @click="send('repository')">GitHub</Button><Button type="quiet" @click="send('licenses')">开源许可</Button></div></div>
     <p v-if="state.detail" class="muted" role="alert">{{state.detail}}</p>
    </section>
   </Transition>
  </main>
  <footer class="app-footer"><IconButton label="设置" type="quiet" size="xl" class="settings-icon" :class="{active:page==='settings'}" @click="settings"><SettingsIcon/></IconButton><Button v-if="state.busy" type="quiet" @click="send('cancel')">取消当前任务</Button></footer>
 </div>
</template>
