// Production acceptance only. No packaging, Java/plugin build, deployment or game execution.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {ROOT,OUT,SOURCE,REVIEW} from './asset-kit.mjs';
import {models,icons,selectors,worldPointers,newModels,singleReviewModels,heroItems,monsterCatalog,expectedOverrideFiles,target} from '../sources/asset-contract.mjs';
const production=path.join(ROOT,'assets/yuumi97-production'),overrides=path.join(ROOT,'resourcepack-gen/overrides');
const failures=[],check=(ok,message)=>{if(!ok)failures.push(message);return ok;};
const sha=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const rel=(root,file)=>path.relative(root,file).replaceAll('\\','/');
const stamp=file=>({path:rel(production,file),bytes:fs.statSync(file).size,sha256:sha(file)});
const read=file=>JSON.parse(fs.readFileSync(file,'utf8'));
const walk=dir=>fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);
const modelViews=['front','side','rear','threequarter','top'];
const rigPitches={front:0,side:0,rear:0,threequarter:0,'windup-minus60':-60,'windup-minus60-side':-60,'strike-plus55':55};
const handViews=['thirdperson_righthand','thirdperson_lefthand','firstperson_righthand','firstperson_lefthand'];
const equal=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
const exactNames=(actual,expected)=>Array.isArray(actual)&&equal([...actual].sort(),[...expected].sort());
const vector=value=>Array.isArray(value)&&value.length===3&&value.every(Number.isFinite);
const nearVector=(actual,expected,tolerance=.00001)=>vector(actual)&&vector(expected)&&actual.every((v,i)=>Math.abs(v-expected[i])<tolerance);
const validSha=value=>typeof value==='string'&&/^[a-f0-9]{64}$/.test(value);
export function validSavedScene(record,blendHash){
 return record.freshProcessReopen===true&&record.packedTextures===true&&
  Number.isSafeInteger(record.preparedPid)&&record.preparedPid>0&&Number.isSafeInteger(record.renderPid)&&record.renderPid>0&&
  record.preparedPid!==record.renderPid&&validSha(record.blendSha256)&&record.blendSha256===blendHash;
}
export function validEvaluatedPoses(poses,scale){
 return Number.isFinite(scale)&&scale>0&&Array.isArray(poses)&&exactNames(poses.map(p=>p?.view),Object.keys(rigPitches))&&poses.every(p=>{
  const pitch=rigPitches[p.view],angle=-pitch*Math.PI/180;
  return p.semanticPitch===pitch&&Number.isFinite(p.actualLocalXDegrees)&&Math.abs(p.actualLocalXDegrees+pitch)<.001&&
   nearVector(p.forwardWorld,[0,scale*Math.cos(angle),scale*Math.sin(angle)]);
 });
}
export function validRigMetrics(record){
 const finiteError=(value,limit)=>Number.isFinite(value)&&value>=0&&value<=limit;
 return Number.isFinite(record.rootMinimumYBlocks)&&Math.abs(record.rootMinimumYBlocks)<.0001&&
  finiteError(record.math?.maximumSocketErrorBlocks,.01)&&finiteError(record.math?.maximumPosedEndpointErrorBlocks,.00001)&&
  record.math?.goldenFixture?.pass===true&&record.math?.cases>=40;
}
export function validPlacementContexts(id,records,contract){
 const kind=contract?.kind;
 if(typeof kind!=='string'||!Array.isArray(records))return false;
 const names=id==='prop_wayfinder_arrow'?['direction-yaw180','direction-yaw0']:
  kind==='floor'?['floor-contact']:['floor_disc','water_funnel','horizontal_ring'].includes(kind)?['orientation-plane']:['direction-yaw180'];
 return exactNames(records.map(c=>c?.context),names)&&records.every(c=>c.kind===kind&&(kind!=='floor'||c.touchesFloor===true));
}
export function validHeroContexts(records,model,anchor){
 return !!anchor&&Array.isArray(records)&&exactNames(records.map(c=>c?.context),handViews)&&records.every(c=>{
  const target=anchor.contexts[c.context]?.target;
  if(!vector(c.actualAnchorDisplayUnits)||!vector(target))return false;
  const error=Math.hypot(...c.actualAnchorDisplayUnits.map((v,i)=>v-target[i]));
  return equal(c.display,model.display[c.context])&&c.kind===anchor.kind&&equal(c.anchorModelUnits,anchor.anchor)&&
   equal(c.targetDisplayUnits,target)&&Number.isFinite(c.anchorErrorModelUnits)&&c.anchorErrorModelUnits>=0&&c.anchorErrorModelUnits<.01&&
   error<.01&&Math.abs(error-c.anchorErrorModelUnits)<.00001&&vector(c.forwardBlender)&&Math.abs(Math.hypot(...c.forwardBlender)-1)<.00001&&
   c.leftHandMirroringApplied===c.context.endsWith('lefthand');
 });
}
// Reviewers record hashes after inspecting these exact images; a later rerender invalidates the observation.
export function validVisualEvidence(entry,reportHash,expectedImages){
 return !!entry&&entry.status==='PASS'&&typeof entry.notes==='string'&&entry.notes.length>0&&validSha(entry.reportSha256)&&entry.reportSha256===reportHash&&Array.isArray(entry.images)&&
  exactNames(entry.images.map(i=>i?.path),expectedImages.map(i=>i.path))&&entry.images.every(i=>
   validSha(i.sha256)&&i.sha256===expectedImages.find(expected=>expected.path===i.path)?.sha256);
}
export function validHistoricalAnchor(baseline,reportHash){
 return baseline?.path==='review/v31-delivery.json'&&validSha(baseline.sha256)&&baseline.sha256===reportHash;
}
function image(file){
 if(!check(fs.existsSync(file),'Missing image '+file))return null;
 const b=fs.readFileSync(file);check(b.length>45&&b.subarray(0,8).toString('hex')==='89504e470d0a1a0a'&&b.subarray(-8,-4).toString()==='IEND','Invalid PNG '+file);
 return {...stamp(file),width:b.readUInt32BE(16),height:b.readUInt32BE(20)};
}
function blend(file){
 const handle=fs.openSync(file,'r'),header=Buffer.alloc(12);try{fs.readSync(handle,header,0,12,0);}finally{fs.closeSync(handle);}
 check(header.subarray(0,7).toString()==='BLENDER','Editable blend header '+file);return stamp(file);
}
function sources(record,required,id){
 for(const [p,hash]of Object.entries(record.sourceSha256??{}))check(fs.existsSync(p)&&sha(p)===hash,`${id}: stale rendered source ${p}`);
 for(const file of required)check(Object.keys(record.sourceSha256??{}).some(p=>path.normalize(p).toLowerCase()===path.normalize(file).toLowerCase()),`${id}: missing recorded source ${file}`);
}
export function runDeliveryVerification(){
failures.length=0;
try{execFileSync(process.execPath,[path.join(production,'tools/validate-assets.mjs')],{stdio:'pipe'});}catch(error){check(false,'Static validation failed: '+error.stdout?.toString()+' '+error.stderr?.toString());}
const validationFile=path.join(REVIEW,'validation.json'),validation=fs.existsSync(validationFile)?read(validationFile):{};
check(validation.status==='PASS','Static validation report must pass');
const actual=walk(path.join(overrides,'assets')).map(f=>rel(overrides,f)).sort();
check(expectedOverrideFiles.length===367&&new Set(expectedOverrideFiles).size===367&&JSON.stringify(actual)===JSON.stringify(expectedOverrideFiles),'Exact 367-file override set');
const manifest=actual.map(p=>({path:p,bytes:fs.statSync(path.join(overrides,p)).size,sha256:sha(path.join(overrides,p))}));
const preserved=read(path.join(SOURCE,'preservation-baseline.json'));
check(preserved.length===174,'Expected 174 preserved non-atlas overrides');
for(const row of preserved)check(sha(path.join(overrides,row.path))===row.sha256,'Prior override changed: '+row.path);
const historical=new Map();
const historicalBaselineFile=path.join(SOURCE,'historical-evidence-baseline.json');
check(validHistoricalAnchor(read(historicalBaselineFile),sha(path.join(REVIEW,'v31-delivery.json'))),'Historical delivery anchor changed');
function collect(value){if(!value||typeof value!=='object')return;if(value.path&&value.sha256&&/^(sources|review)\//.test(value.path))historical.set(value.path,value.sha256);for(const child of Object.values(value))if(typeof child==='object')collect(child);}
for(const filename of ['v3-delivery.json','v31-delivery.json']){const file=path.join(REVIEW,filename);collect(read(file));historical.set(rel(production,file),sha(file));}
for(const[p,hash]of historical)check(fs.existsSync(path.join(production,p))&&sha(path.join(production,p))===hash,'Historical evidence changed: '+p);
const standalone=[],rigs=[];
const gripAnchors=read(path.join(SOURCE,'hero-grip-anchors.json')).models;
const placementContracts=read(path.join(SOURCE,'model-review-contract.json')).models;
for(const id of singleReviewModels){
 try{
  const dir=path.join(REVIEW,'models',id),g=read(path.join(dir,'geometry.json')),model=path.join(OUT,'models/item',id+'.json'),texture=path.join(OUT,'textures/prop',id+'.png');
  const source=path.join(SOURCE,'blend',id+'.blend'),modelData=read(model);
  check(g.id===id&&g.elements===modelData.elements.length&&validSavedScene(g,sha(source)),'Saved scene/hash/process proof '+id);
  sources(g,[model,texture,...['model-review-contract.json','hero-grip-anchors.json','skill-vfx-contract.json'].map(n=>path.join(SOURCE,n))],id);
  const views=modelViews;check(equal(g.views,views),'Five model views '+id);
  const images=views.map(v=>image(path.join(dir,v+'.png'))),contextFile=path.join(REVIEW,'contexts',id,'contexts.json'),contexts=read(contextFile);
  check(contexts.id===id&&contexts.contexts.length>0,'Required placement/grip context '+id);
  check(equal(g.contextImages,contexts.contexts.map(c=>c.context)),'Rendered context evidence '+id);
  if(heroItems.includes(id))check(validHeroContexts(contexts.contexts,modelData,gripAnchors[id]),'Four exact grip contexts and finite anchors '+id);
  else check(validPlacementContexts(id,contexts.contexts,placementContracts[id]),'Exact placement contexts and required floor contact '+id);
  standalone.push({id,blend:blend(source),geometry:stamp(path.join(dir,'geometry.json')),images,contextReport:stamp(contextFile),contextImages:contexts.contexts.map(c=>image(path.join(REVIEW,'contexts',id,'context-'+c.context+'.png'))),sheet:image(path.join(dir,'contact-sheet.png'))});
 }catch(error){check(false,id+': '+error.message);}
}
const rigFile=path.join(OUT,'monster_rigs.json'),rigManifest=fs.existsSync(rigFile)?read(rigFile):{rigs:{}};
const clearance=[];
for(const [chapter,name]of [[1,'astral'],[2,'foundry'],[3,'void']]){
 try{
  const file=path.join(REVIEW,name+'-clearance.json'),report=read(file);
  const records=report.records??report.rigs?.flatMap(r=>r.poses.map(p=>({...p,id:r.id})));
  const expected=monsterCatalog.filter(m=>m.chapter===chapter).flatMap(m=>[-70,-65,-60,0,55].map(p=>m.id+':'+p));
  check(report.status==='PASS'&&exactNames(records?.map(r=>r.id+':'+r.pitch),expected),'Thirty exact clearance poses '+name);
  for(const r of records??[]){
   const minimum=r.minimumYModelUnits??r.movingMinimumYModelUnits,collisions=r.intersections??r.unwantedCollisions;
   check(Number.isFinite(minimum)&&minimum>=0&&Array.isArray(collisions)&&collisions.length===0,`${name}/${r.id}/${r.pitch}: collision/floor clearance`);
  }
  const sourceSha256=Object.assign({},report.sourceSha256,...(report.rigs??[]).map(r=>r.sourceSha256));
  const required=monsterCatalog.filter(m=>m.chapter===chapter).flatMap(m=>m.parts.map(p=>path.join(OUT,'models/item',m.id+'_'+p+'.json')));
  sources({sourceSha256},[...required,rigFile,path.join(SOURCE,'monsters-'+name+'.mjs'),path.join(production,'tools/check-'+name+'-clearance.mjs')],name+' clearance');
  clearance.push({chapter,...stamp(file),poses:records.length,unwantedIntersections:0,minimumYModelUnits:Math.min(...records.map(r=>r.minimumYModelUnits??r.movingMinimumYModelUnits))});
 }catch(error){check(false,name+' clearance: '+error.message);}
}
for(const entry of monsterCatalog){
 const {id}=entry;
 try{
  const dir=path.join(REVIEW,'rigs',id),a=read(path.join(dir,'assembly.json')),contract=rigManifest.rigs[id];
  const expectedParts={root:contract.rootModel,...Object.fromEntries(Object.entries(contract.sockets).map(([k,v])=>[k,v.model]))};
  check(a.id===id&&a.status==='PASS'&&a.baseScale===entry.baseScale&&JSON.stringify(a.parts)===JSON.stringify(expectedParts),'Assembled part contract '+id);
  const source=path.join(SOURCE,'blend/rigs',id+'.blend');check(validSavedScene(a,sha(source)),'Fresh rig process, packed texture and blend hash proof '+id);
  const partFiles=entry.parts.flatMap(p=>[path.join(OUT,'models/item',id+'_'+p+'.json'),path.join(OUT,'textures/prop',id+'_'+p+'.png')]);
  sources(a,[rigFile,...partFiles],id);
  check(validRigMetrics(a),'Finite rig floor/socket/posed endpoint fixture '+id);
  const views=Object.keys(rigPitches);
  check(JSON.stringify(a.views)===JSON.stringify(views),'Seven assembled views '+id);
  check(validEvaluatedPoses(a.evaluatedPoses,entry.baseScale),'Exact render-evaluated views, pitches and finite forward vectors '+id);
  check(sha(path.join(dir,'threequarter.png'))!==sha(path.join(dir,'windup-minus60.png'))&&sha(path.join(dir,'threequarter.png'))!==sha(path.join(dir,'strike-plus55.png')),'Attack images must differ from neutral '+id);
  rigs.push({id,bones:entry.parts.length,blend:blend(source),assembly:stamp(path.join(dir,'assembly.json')),images:views.map(v=>image(path.join(dir,v+'.png'))),sheet:image(path.join(dir,'contact-sheet.png'))});
 }catch(error){check(false,id+': '+error.message);}
}
const boards=['overhaul-relics-and-vfx.png','overhaul-monsters-ch1-ch2-ch3.png','relics-and-vfx-overview.png','monsters-overview.png','monsters-chapter-1.png','monsters-chapter-2.png','monsters-chapter-3.png','relic-grips.png','effect-placement.png','hero-icons.png'].map(n=>image(path.join(REVIEW,n)));
check(boards[0]?.width===1260&&boards[0]?.height===8668,'19 x 3 relic/effect board dimensions');
check(boards[1]?.width===1260&&boards[1]?.height===8216,'18 x 3 assembled monster board dimensions');
const boardProofFile=path.join(REVIEW,'boards-all.json');
if(fs.existsSync(boardProofFile)){
 const proof=read(boardProofFile),inputs=[...standalone.flatMap(r=>[...r.images,...r.contextImages]),...rigs.flatMap(r=>r.images)].filter(Boolean);
 check(proof.mode==='all'&&exactNames(proof.inputs?.map(i=>i.path),inputs.map(i=>i.path.replace(/^review\//,''))),'Exact contact-sheet source set');
 for(const item of [...(proof.inputs??[]),...(proof.outputs??[])])check(validSha(item.sha256)&&fs.existsSync(path.join(REVIEW,item.path))&&sha(path.join(REVIEW,item.path))===item.sha256,'Stale contact sheet/source '+item.path);
 const requiredBoards=boards.slice(0,-1).filter(Boolean).map(i=>i.path.replace(/^review\//,''));
 check(requiredBoards.every(p=>proof.outputs?.some(i=>i.path===p)),'Required composed sheets declared');
}else check(false,'Run compose-review.mjs all to prove current sheet inputs');
const visualFile=path.join(REVIEW,'visual-review.json');let visual={};
if(fs.existsSync(visualFile)){
 visual=read(visualFile);check(visual.status==='PASS','Visual review status');
 check(JSON.stringify((visual.models??[]).map(x=>x.id).sort())===JSON.stringify([...singleReviewModels].sort()),'All 19 models visually reviewed');
 check(JSON.stringify((visual.rigs??[]).map(x=>x.id).sort())===JSON.stringify(monsterCatalog.map(x=>x.id).sort()),'All 18 assembled rigs visually reviewed');
 for(const row of standalone)check(validVisualEvidence(visual.models?.find(v=>v.id===row.id),row.geometry.sha256,[...row.images,...row.contextImages]),'Visual observation hashes for model '+row.id);
 for(const row of rigs)check(validVisualEvidence(visual.rigs?.find(v=>v.id===row.id),row.assembly.sha256,row.images),'Visual observation hashes for rig '+row.id);
 check(visual.minecraftClient==='NOT_RUN','Blender and Minecraft verification must be distinguished');
}else check(false,'Visual review not recorded');
const report={schemaVersion:1,timestamp:new Date().toISOString(),target,status:failures.length?'FAIL':'PASS',
 counts:{overrideFiles:actual.length,models3d:models.length,propAtlases:models.length,icons32:icons.length,guiSelectors:selectors.length,itemDefinitions:selectors.length+worldPointers.length,atlasSources:models.length,newModels3d:newModels.length,preservedOverrideFiles:preserved.length,preservedHistoricalEvidence:historical.size,standaloneBlends:standalone.length,assembledBlends:rigs.length,assembledBones:rigs.reduce((n,r)=>n+r.bones,0),multiangleImages:standalone.reduce((n,r)=>n+r.images.length,0)+rigs.reduce((n,r)=>n+r.images.length,0),contextImages:standalone.reduce((n,r)=>n+r.contextImages.length,0)},
 staticValidation:fs.existsSync(validationFile)?stamp(validationFile):null,baseline:stamp(path.join(SOURCE,'preservation-baseline.json')),historicalBaseline:stamp(historicalBaselineFile),clearance,standalone,rigs,boards,visualReview:fs.existsSync(visualFile)?stamp(visualFile):null,
 build:{status:'NOT_RUN',reason:'No -Build, PackBuilder, plugin compilation, packaging or server changes.'},minecraftClient:{status:'NOT_RUN',reason:'SOL integration must verify real resource loading, rendering, AI, animation, hitboxes and gameplay.'},manifest,failures};
const output=path.join(REVIEW,'delivery.json');fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({status:report.status,counts:report.counts,failures,report:output},null,2));process.exitCode=failures.length?1:0;
return report;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))runDeliveryVerification();
