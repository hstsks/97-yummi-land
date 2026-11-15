// Asset-only v3.1 acceptance. Does not package, compile Java, deploy or run Minecraft.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {ROOT,OUT,SOURCE,REVIEW} from './asset-kit.mjs';
import {models,icons,selectors,worldPointers,landmarksV31,landmarkMounts,target} from '../sources/asset-contract.mjs';
const production=path.join(ROOT,'assets/yuumi97-production'),overrides=path.join(ROOT,'resourcepack-gen/overrides');
const failures=[],check=(ok,message)=>{if(!ok)failures.push(message);return ok;};
const sha=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const rel=(root,file)=>path.relative(root,file).replaceAll('\\','/');
const stamp=file=>({path:rel(production,file),bytes:fs.statSync(file).size,sha256:sha(file)});
const read=file=>JSON.parse(fs.readFileSync(file,'utf8'));
const walk=dir=>fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);
function image(file){
 if(!check(fs.existsSync(file),'Missing image '+file))return null;
 const b=fs.readFileSync(file);check(b.length>45&&b.subarray(0,8).toString('hex')==='89504e470d0a1a0a'&&b.subarray(-8,-4).toString()==='IEND','Invalid PNG '+file);
 return {...stamp(file),width:b.readUInt32BE(16),height:b.readUInt32BE(20)};
}
try{execFileSync(process.execPath,[path.join(production,'tools/validate-assets.mjs')],{stdio:'pipe'});}catch(error){check(false,'Static validation failed: '+error.stdout?.toString()+' '+error.stderr?.toString());}
const staticFile=path.join(REVIEW,'static-validation-v31.json'),validation=read(staticFile);
check(validation.status==='PASS'&&validation.overrideFileCount===175&&validation.itemDefinitions===29,'Static report counts/status');
const expected=['assets/minecraft/atlases/blocks.json',...models.flatMap(id=>[`assets/yuumi97/models/item/${id}.json`,`assets/yuumi97/textures/prop/${id}.png`]),...icons.map(id=>`assets/yuumi97/textures/item/${id}.png`),...selectors.map(id=>`assets/yuumi97/models/gui/${id}.json`),...[...selectors,...worldPointers].map(id=>`assets/yuumi97/items/${id}.json`)].sort();
const actual=walk(path.join(overrides,'assets')).map(f=>rel(overrides,f)).sort();
check(expected.length===175&&new Set(expected).size===175&&JSON.stringify(actual)===JSON.stringify(expected),'Exact 175-file override set');
const manifest=actual.map(p=>({path:p,bytes:fs.statSync(path.join(overrides,p)).size,sha256:sha(path.join(overrides,p))}));
const previousFile=path.join(REVIEW,'v3-delivery.json'),previous=read(previousFile),historical=new Map();
function collect(value){if(!value||typeof value!=='object')return;if(value.path&&value.sha256&&/^(sources|review)\//.test(value.path))historical.set(value.path,value.sha256);for(const child of Object.values(value))if(typeof child==='object')collect(child);}
collect(previous);
for(const [p,hash] of historical)check(fs.existsSync(path.join(production,p))&&sha(path.join(production,p))===hash,'Historical v3 evidence changed: '+p);
check(historical.size===200,'Expected 200 preserved historical evidence files');
const preserved=read(path.join(SOURCE,'v3-baseline.json'));
check(preserved.length===156,'Expected 156 prior overrides');
for(const row of preserved)check(sha(path.join(overrides,row.path))===row.sha256,'Prior override changed: '+row.path);
const evidence=[];
for(const id of landmarksV31){
 try{
  const model=path.join(OUT,'models/item',id+'.json'),texture=path.join(OUT,'textures/prop',id+'.png'),blend=path.join(SOURCE,'blend',id+'.blend');
  const geometryFile=path.join(REVIEW,'models',id,'geometry.json'),g=read(geometryFile),data=read(model);
  const handle=fs.openSync(blend,'r'),header=Buffer.alloc(12);try{fs.readSync(handle,header,0,12,0);}finally{fs.closeSync(handle);}
  check(header.subarray(0,7).toString()==='BLENDER','Editable blend header '+id);
  check(g.id===id&&g.elements===data.elements.length&&g.freshProcessReopen===true&&g.packedTextures===true,'Saved scene proof '+id);
  for(const file of [model,texture,path.join(SOURCE,'landmarks-v31-mounts.json')]){
   const recorded=Object.entries(g.sourceSha256??{}).find(([p])=>path.normalize(p).toLowerCase()===path.normalize(file).toLowerCase())?.[1];
   check(recorded===sha(file),'Stale source render '+file);
  }
  const views=['front','side','rear','threequarter'];check(JSON.stringify(g.views)===JSON.stringify(views),'Four model views '+id);
  const images=views.map(v=>image(path.join(REVIEW,'models',id,v+'.png')));
  const contextFile=path.join(REVIEW,'contexts',id,'contexts.json'),contexts=read(contextFile),mount=landmarkMounts[id],name=mount.kind+'_mount',record=contexts.contexts.find(c=>c.context===name);
  check(contexts.id===id&&JSON.stringify(contexts.mount)===JSON.stringify(mount),'Mount metadata '+id);
  check(record?.touchesMount===true&&record?.penetratesMount===false&&Math.abs(record.attachmentGapModelUnits)<.001,'Attachment contact '+id);
  check(g.contextImages.includes(name),'Declared mounting view '+id);
  if(mount.kind==='floor')check(record?.touchesFloor===true&&!record.penetratesFloor,'Floor contact '+id);
  if(id==='prop_dealer_throne')check(contexts.contexts.some(c=>c.context==='dealer_clearance'),'Dealer clearance view');
  const contextImages=contexts.contexts.map(c=>image(path.join(REVIEW,'contexts',id,'context-'+c.context+'.png')));
  let distance=[];
  if(mount.kind==='wall'){
   const d=read(path.join(REVIEW,'models',id,'distance.json'));check(d.id===id&&d.views.length===2,'Two distance views '+id);
   for(const meters of [16,32]){const v=d.views.find(v=>v.distanceMeters===meters);check(v?.fovDegrees===70&&v?.referenceScale===mount.referenceScale&&JSON.stringify(v?.resolution)==='[640,640]','Calibrated distance view '+id+'/'+meters);}
   distance=d.views.map(v=>({...v,image:image(path.join(REVIEW,'models',id,v.name+'.png'))}));
  }
  evidence.push({id,elements:g.elements,blend:stamp(blend),geometry:stamp(geometryFile),device:g.device,images,contextReport:stamp(contextFile),contextImages,distance});
 }catch(error){check(false,id+': '+error.message);}
}
const boards=['landmarks-v31.png','landmarks-v31-contexts.png','landmarks-v31-distance.png'].map(n=>image(path.join(REVIEW,n)));
check(boards[0]?.width===1440&&boards[0]?.height===3152,'Required 6x3 contact sheet dimensions');
const visualFile=path.join(REVIEW,'v31-visual-review.md'),hasVisual=fs.existsSync(visualFile);
const status=failures.length?'FAIL':hasVisual?'PASS':'REVIEW_REQUIRED';
const report={schemaVersion:1,timestamp:new Date().toISOString(),target,status,
 counts:{overrideFiles:actual.length,models3d:models.length,propAtlases:models.length,icons32:icons.length,guiSelectors:selectors.length,itemDefinitions:selectors.length+worldPointers.length,atlasSources:validation.atlas?.count,newModels3d:landmarksV31.length,preservedOverrideFiles:preserved.length,preservedHistoricalEvidence:historical.size,blends:evidence.length,multiangleImages:evidence.reduce((n,e)=>n+e.images.length,0),contextImages:evidence.reduce((n,e)=>n+e.contextImages.length,0),distanceImages:evidence.reduce((n,e)=>n+e.distance.length,0)},
 staticValidation:stamp(staticFile),previousDelivery:stamp(previousFile),baseline:stamp(path.join(SOURCE,'v3-baseline.json')),mounts:stamp(path.join(SOURCE,'landmarks-v31-mounts.json')),blender:evidence,boards,
 visualReview:hasVisual?{...stamp(visualFile),record:fs.readFileSync(visualFile,'utf8'),notice:'Observed images documented by reviewer; this script does not grade aesthetics.'}:{status:'NOT_RECORDED'},
 build:{status:'NOT_RUN',reason:'No -Build, PackBuilder, Java compilation or server changes.'},minecraftClient:{status:'NOT_RUN',reason:'SOL integration must verify actual placement/yaw, resource loading and nighttime lighting.'},manifest,failures};
const output=path.join(REVIEW,'v31-delivery.json');fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({status,counts:report.counts,failures,report:output},null,2));
process.exitCode=failures.length?1:hasVisual?0:2;
