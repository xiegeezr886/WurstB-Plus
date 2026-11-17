// Run after pnpm build. DOWNLOADER_PLAYWRIGHT_MODULE may point to an installed Playwright module.
import { createServer } from 'node:http'
import { readFile, mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
import assert from 'node:assert/strict'
const { chromium } = await import(process.env.DOWNLOADER_PLAYWRIGHT_MODULE || 'playwright')
const root = fileURLToPath(new URL('..', import.meta.url))
const html = await readFile(join(root, 'dist/index.html'))
const snapshot = JSON.parse(await readFile(join(root, '../ReleaseSnapshot.json'), 'utf8'))
const releases = snapshot.releases.map(r=>({tag:r.tag_name,label:`${r.tag_name} · ${r.prerelease?'预发布':'正式版'}`,assets:r.assets.map(a=>{
 const match=a.name.match(/-(NeoForge|Forge|Fabric)-(\d+(?:\.\d+){1,2})\.jar$/)
 return {name:a.name,size:a.size,loader:match[1],minecraft:match[2]}
})}))
const mirrorHosts=(await readFile(join(root,'../Core/MirrorHosts.txt'),'utf8')).trim().split(/\r?\n/)
const sources=[{id:'direct',name:'GitHub 直连',url:'https://github.com/'},...mirrorHosts.map(host=>({id:'mirror:'+new URL('https://'+host).hostname,name:host,url:'https://'+host+'/'}))]
const initial={releases,busy:false,operation:'',status:'准备就绪',detail:'',folder:'D:\\Downloads',selection:{release:'v1.5.1',minecraft:'1.20.1',loader:'Fabric'},sourceId:'auto',connections:4,catalogInfo:'实时列表 · GitHub 直连',sources,probes:[],progress:null,completed:null}
const server=createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html)})
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
const browser=await chromium.launch({headless:true, ...(process.env.DOWNLOADER_BROWSER_PATH?{executablePath:process.env.DOWNLOADER_BROWSER_PATH}:{channel:'msedge'})})
const page=await browser.newPage({viewport:{width:1040,height:700},reducedMotion:'reduce'})
const errors=[]
page.on('pageerror',e=>errors.push(e.message))
await page.addInitScript(initial=>{
 window.__commands=[];window.__hostState=initial;let listener
 window.chrome={webview:{addEventListener:(_,cb)=>{listener=cb},postMessage:message=>{
  window.__commands.push(message)
  if(message.action==='configure'){
   const p=message.payload
   if(p.release) window.__hostState.selection={release:p.release,minecraft:p.minecraft,loader:p.loader}
   if(p.connections)window.__hostState.connections=Number(p.connections)
   if(p.sourceId)window.__hostState.sourceId=p.sourceId
  }
  queueMicrotask(()=>listener({data:{type:'state',state:window.__hostState}}))
 }}}
 window.__emitState=state=>{window.__hostState={...window.__hostState,...state};listener({data:{type:'state',state:window.__hostState}})}
 window.__reveal=()=>listener({data:{type:'reveal'}})
},initial)
const output=join(root,'../artifacts/ui');await mkdir(output,{recursive:true})
async function screenshot(name){await page.screenshot({path:join(output,`${name}.png`)});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'Horizontal window overflow')}
try{
 await page.goto(`http://127.0.0.1:${server.address().port}`)
 assert.equal(await page.locator('.app-shell').evaluate(el=>getComputedStyle(el).opacity),'0','Native startup stays hidden until the loading animation finishes')
 await page.emulateMedia({reducedMotion:'no-preference'})
 await page.evaluate(()=>window.__reveal())
 await page.waitForFunction(()=>{const opacity=Number(getComputedStyle(document.querySelector('.app-shell')).opacity);return opacity>.05&&opacity<.95})
 await screenshot('00-reveal')
 await page.waitForFunction(()=>getComputedStyle(document.querySelector('.app-shell')).opacity==='1')
 await page.emulateMedia({reducedMotion:'reduce'})
 await page.getByRole('button',{name:'下载客户端',exact:true}).waitFor()
 await screenshot('01-home')
 assert.equal(await page.locator('select').count(),0)
 assert.equal(await page.getByText('下一次探索').count(),0)
 await page.getByRole('button',{name:'下载客户端',exact:true}).click()
 await page.getByRole('heading',{name:'选择版本'}).waitFor()
 await page.getByLabel('发布版本').selectOption('v1.5.2')
 assert.deepEqual(await page.getByLabel('Minecraft 游戏版本').locator('option').allTextContents(),['1.20.1'])
 await screenshot('02-versions')
 await page.getByRole('button',{name:'下一步：选择加载器'}).click()
 await page.getByRole('heading',{name:'选择加载器'}).waitFor()
 assert.equal(await page.getByRole('radio').count(),1)
 await page.getByRole('button',{name:'返回上一页'}).click()
 await page.getByLabel('发布版本').selectOption('v1.5.1')
 await page.getByLabel('Minecraft 游戏版本').selectOption('1.20.1')
 await page.getByRole('button',{name:'下一步：选择加载器'}).click()
 await page.getByRole('heading',{name:'选择加载器'}).waitFor()
 assert.equal(await page.getByRole('radio').count(),3)
 await page.getByText('NeoForge',{exact:true}).click()
 await screenshot('03-loaders')
 await page.getByRole('button',{name:'下一步：下载',exact:true}).click()
 await page.getByRole('button',{name:'下载客户端',exact:true}).waitFor()
 await screenshot('04-download')
 await page.getByRole('button',{name:'设置',exact:true}).click()
 await page.getByRole('heading',{name:'下载设置'}).waitFor()
 assert.equal(await page.locator('.mirror-row').count(),115)
 await page.locator('.mirror-row').first().click()
 assert.equal(await page.locator('.mirror-row[aria-pressed="true"]').count(),1)
 await page.getByLabel('下载线路',{exact:true}).selectOption('auto')
 await page.getByLabel('下载线程数').selectOption('8')
 assert.equal(await page.getByLabel('下载线程数').inputValue(),'8')
 await screenshot('05-settings')
 assert.equal(await page.getByText('系统下载文件夹',{exact:true}).count(),0)
 await page.getByRole('button',{name:'返回上一页'}).click()
 await page.getByRole('button',{name:'下载客户端',exact:true}).click()
 assert.ok(await page.evaluate(()=>window.__commands.some(c=>c.action==='download'&&c.payload.loader==='NeoForge')))
 await page.evaluate(()=>window.__emitState({busy:true,operation:'download',progress:null}))
 assert.equal(await page.getByRole('progressbar').getAttribute('aria-valuenow'),null,'Probing must not invent a percentage')
 await page.evaluate(()=>window.__emitState({busy:true,operation:'download',status:'正在下载',progress:{percent:42,received:23000000,total:55000000,speed:2400000,connections:4,source:'GH-Proxy'}}))
 assert.equal(await page.getByRole('button',{name:'返回上一页'}).isDisabled(),true)
 assert.equal(await page.getByRole('progressbar').getAttribute('aria-valuenow'),'42')
 const stillWave=await page.locator('.music-wave').getAttribute('d')
 assert.ok(!/[ML][^,]+,(?!8\.000)/.test(stillWave),'Reduced motion keeps a flat track')
 await page.emulateMedia({reducedMotion:'no-preference'})
 await page.waitForFunction(()=>{
  const ys=document.querySelector('.music-wave')?.getAttribute('d')?.match(/,[^ ]+/g)?.map(y=>Number(y.slice(1)))??[]
  return Math.max(...ys)-Math.min(...ys)>2
 })
 const movingWave=await page.locator('.music-wave').getAttribute('d')
 await page.waitForFunction(previous=>document.querySelector('.music-wave')?.getAttribute('d')!==previous,movingWave)
 await screenshot('06-downloading')
 assert.ok(!(await page.locator('.transfer-detail').innerText()).match(/线程|GH-Proxy/))
 await page.getByRole('button',{name:'设置',exact:true}).click()
 assert.equal(await page.getByLabel('下载线程数').isDisabled(),true)
 await page.getByRole('button',{name:'返回上一页'}).click()
 await page.getByRole('button',{name:'取消当前任务'}).click()
 assert.ok(await page.evaluate(()=>window.__commands.some(c=>c.action==='cancel')))
 await page.evaluate(()=>window.__emitState({busy:false,status:'下载已取消',progress:null}))
 await page.setViewportSize({width:800,height:600})
 await screenshot('07-small-window')
 await page.setViewportSize({width:1040,height:700})
 await page.evaluate(()=>window.__emitState({status:'下载完成',detail:'SHA-256 与发布记录一致 · JAR 校验通过',completed:{path:'D:\\Downloads\\client.jar',sha256:'0'.repeat(64),verified:true},progress:{percent:100,received:55000000,total:55000000,speed:0,connections:4,source:'GH-Proxy'}}))
 await page.getByRole('button',{name:'打开下载文件夹',exact:true}).waitFor()
 await page.waitForFunction(()=>document.querySelector('.music-wave')?.getAttribute('d')?.match(/,[^ ]+/g)?.every(y=>y===',8.000'))
 assert.equal(await page.getByRole('progressbar').getAttribute('aria-valuenow'),'100')
 assert.equal(await page.getByText('SHA-256 与发布记录一致 · JAR 校验通过',{exact:true}).count(),0)
 for(const icon of await page.locator('.save-location>svg, .completion-actions svg').all()) assert.equal(await icon.getAttribute('viewBox'),'0 0 24 24','Icons must retain viewBox when CSS changes their size')
 await screenshot('08-complete')
 assert.deepEqual(errors,[])
 console.log('PASS: UI page flow, release filtering, loader selection, settings round-trip, thread configuration, cancel and 800x600 layout')
 console.log(`Screenshots: ${output}`)
}finally{await browser.close();await new Promise(resolve=>server.close(resolve))}
