import { reactive, ref } from 'vue'
export type Asset = { name:string; loader:string; minecraft:string; size:number }
export type Release = { tag:string; label:string; assets:Asset[] }
export type Probe = { id:string; name:string; available:boolean; speed:number; milliseconds:number; detail:string; ranges:boolean }
export type Source = { id:string; name:string; url:string }
export interface State {
  releases:Release[]; busy:boolean; operation:string; status:string; detail:string; folder:string;
  selection:{ release:string; minecraft:string; loader:string }; sourceId:string; connections:number;
  catalogInfo:string; sources:Source[]; probes:Probe[]; progress:{ percent:number; received:number; total:number; speed:number; connections:number; source:string } | null;
  completed:{ path:string; sha256:string; verified:boolean } | null;
}
declare global { interface Window { chrome?:{ webview?:{ postMessage:(value:unknown)=>void; addEventListener:(name:string, callback:(e:MessageEvent)=>void)=>void } }; __DOWNLOADER_TEST_STATE__?:State } }
export const state = reactive<State>({releases:[],busy:false,operation:'',status:'正在连接下载服务…',detail:'',folder:'',selection:{release:'',minecraft:'',loader:''},sourceId:'auto',connections:4,catalogInfo:'',sources:[],probes:[],progress:null,completed:null})
const host = window.chrome?.webview
export const native = Boolean(host)
export const interfaceVisible = ref(!native)
export function send(action:string, payload:unknown = {}) { host?.postMessage({action,payload}) }
if (host) {
  host.addEventListener('message', e => {
    if(e.data?.type === 'state') Object.assign(state,e.data.state)
    if(e.data?.type === 'reveal') {
      // Paint the initial transparent frame after the native browser becomes visible.
      requestAnimationFrame(()=>requestAnimationFrame(()=>{interfaceVisible.value=true}))
    }
  })
  send('ready')
} else if (import.meta.env.DEV && window.__DOWNLOADER_TEST_STATE__) Object.assign(state,window.__DOWNLOADER_TEST_STATE__)
else state.status = '浏览器预览 · 请使用桌面程序下载'
