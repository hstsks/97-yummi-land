/**
 * Usage (after regenerate.ps1 WITHOUT -Build, Blender review and visual review):
 *   node E:\MinGem\assets\yuumi97-production\tools\verify-v3-delivery.mjs
 *
 * Reads only the art contract, final overrides and production evidence. Does
 * not read Java, start Minecraft, package the resource pack or invoke Gradle.
 * Writes review/v3-delivery.json, including SHA-256 delivery manifests.
 * Exit 0 = technical checks pass and a human-readable visual record exists;
 * exit 1 = technical failure; exit 2 = visual record is still missing.
 * A visual record is attached, never treated as an automatic aesthetic test.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {ROOT,OUT,SOURCE,REVIEW} from './asset-kit.mjs';
import {models,newModels,icons,selectors,environment,furniture,hats,weapons,atlasSizes,target} from '../sources/asset-contract.mjs';

const overrideRoot=path.join(ROOT,'resourcepack-gen/overrides');
const production=path.join(ROOT,'assets/yuumi97-production');
const failures=[];
const recordFailure=(section,message)=>failures.push({section,message});
const sha=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const relative=(root,file)=>path.relative(root,file).replaceAll('\\','/');
const stamp=file=>({path:relative(production,file),bytes:fs.statSync(file).size,sha256:sha(file)});
function readJson(file,section){
  try{return JSON.parse(fs.readFileSync(file,'utf8').replace(/^\uFEFF/,''));}
  catch(error){recordFailure(section,`${relative(production,file)}: ${error.message}`);return null;}
}
function check(condition,section,message){if(!condition)recordFailure(section,message);return condition;}
function png(file,section,expectedSize){
  if(!check(fs.existsSync(file),section,`Missing image: ${relative(production,file)}`))return null;
  const buffer=fs.readFileSync(file);
  if(!check(buffer.length>=45&&buffer.subarray(0,8).toString('hex')==='89504e470d0a1a0a',section,`Invalid PNG: ${file}`))return null;
  const width=buffer.readUInt32BE(16),height=buffer.readUInt32BE(20);
  check(width>0&&height>0,section,`Empty image dimensions: ${file}`);
  check(buffer.subarray(-8,-4).toString('ascii')==='IEND',section,`PNG has no final IEND: ${file}`);
  if(expectedSize){
    check(width===expectedSize&&height===expectedSize,section,`Wrong image dimensions: ${file}`);
    check(buffer[24]===8&&buffer[25]===6,section,`Expected RGBA8 asset: ${file}`);
  }
  return {...stamp(file),width,height,bitDepth:buffer[24],colorType:buffer[25]};
}
function walk(dir){
  if(!fs.existsSync(dir))return [];
  return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):e.isFile()?[path.join(dir,e.name)]:[]);
}

const expected=[
  'assets/minecraft/atlases/blocks.json',
  ...models.map(id=>`assets/yuumi97/models/item/${id}.json`),
  ...models.map(id=>`assets/yuumi97/textures/prop/${id}.png`),
  ...icons.map(id=>`assets/yuumi97/textures/item/${id}.png`),
  ...selectors.map(id=>`assets/yuumi97/models/gui/${id}.json`),
  ...[...selectors,...environment].map(id=>`assets/yuumi97/items/${id}.json`),
].sort();
check(expected.length===157&&new Set(expected).size===157,'static','Contract must produce exactly 157 unique override files.');
const actual=walk(path.join(overrideRoot,'assets')).map(file=>relative(overrideRoot,file)).sort();
check(JSON.stringify(actual)===JSON.stringify(expected),'static',`Override file set mismatch: expected 157, found ${actual.length}; missing=${expected.filter(p=>!actual.includes(p)).join(',')}; unexpected=${actual.filter(p=>!expected.includes(p)).join(',')}`);
const manifest=actual.map(p=>({path:p,bytes:fs.statSync(path.join(overrideRoot,p)).size,sha256:sha(path.join(overrideRoot,p))}));

const baselineFile=path.join(SOURCE,'v2-baseline.json');
const baseline=readJson(baselineFile,'preservation');
const retained=(baseline??[]).filter(row=>row.path!=='assets/minecraft/atlases/blocks.json');
check(retained.length===51,'preservation',`Expected 51 preserved baseline files, found ${retained.length}.`);
let preserved=0;
for(const row of retained){
  const file=path.resolve(overrideRoot,row.path);
  if(!check(file.startsWith(overrideRoot+path.sep),'preservation',`Invalid baseline path: ${row.path}`))continue;
  if(check(fs.existsSync(file),'preservation',`Missing baseline asset: ${row.path}`)&&check(sha(file)===row.sha256,'preservation',`Baseline content changed: ${row.path}`))preserved++;
}

const staticFile=path.join(REVIEW,'static-validation.json');
const staticReport=readJson(staticFile,'static');
check(staticReport?.status==='PASS','static','Current static-validation.json must report PASS.');
check(staticReport?.models?.length===29&&staticReport?.icons?.length===57,'static','Static report must cover all 29 models and 57 icons.');
const atlas=readJson(path.join(overrideRoot,'assets/minecraft/atlases/blocks.json'),'static');
const entries=atlas?.sources??[];
const atlasIds=entries.map(e=>e.resource).sort();
check(entries.length===29&&entries.every(e=>e.type==='minecraft:single')&&new Set(atlasIds).size===29&&JSON.stringify(atlasIds)===JSON.stringify(models.map(id=>`yuumi97:prop/${id}`).sort()),'static','Atlas must contain exactly the 29 unique prop textures.');
for(const id of models)png(path.join(OUT,'textures/prop',`${id}.png`),'static',atlasSizes[id]);
for(const id of icons)png(path.join(OUT,'textures/item',`${id}.png`),'static',32);

const views=['front','side','rear','threequarter'];
const handContexts=['thirdperson_righthand','thirdperson_lefthand','firstperson_righthand','firstperson_lefthand'];
const handAnchors=readJson(path.join(SOURCE,'class-weapons-anchors.json'),'blender')?.models??{};
const sourceHash=(evidence,file)=>Object.entries(evidence?.sourceSha256??{}).find(([filename])=>path.normalize(filename).toLowerCase()===path.normalize(file).toLowerCase())?.[1];
const modelEvidence=[];
for(const id of newModels){
  const blend=path.join(SOURCE,'blend',`${id}.blend`);
  let blendEntry=null;
  if(check(fs.existsSync(blend),'blender',`Missing editable scene: ${id}.blend`)){
    const descriptor=fs.openSync(blend,'r'),header=Buffer.alloc(12);
    try{fs.readSync(descriptor,header,0,12,0);}finally{fs.closeSync(descriptor);}
    check(header.subarray(0,7).toString('ascii')==='BLENDER','blender',`Expected uncompressed BLENDER header: ${id}.blend`);
    blendEntry={...stamp(blend),header:header.toString('ascii')};
  }
  const modelFile=path.join(OUT,'models/item',`${id}.json`),textureFile=path.join(OUT,'textures/prop',`${id}.png`);
  const data=readJson(modelFile,'blender');
  const evidenceFile=path.join(REVIEW,'models',id,'geometry.json');
  const evidence=readJson(evidenceFile,'blender');
  check(evidence?.id===id,'blender',`Model evidence ID mismatch: ${id}`);
  check(evidence?.elements===data?.elements?.length,'blender',`Rendered element count differs: ${id}`);
  check(evidence?.freshProcessReopen===true,'blender',`No fresh-process reopening proof: ${id}`);
  check(evidence?.packedTextures===true,'blender',`No packed-texture reopening proof: ${id}`);
  if(fs.existsSync(modelFile)&&fs.existsSync(textureFile)){
    check(sourceHash(evidence,modelFile)===sha(modelFile),'blender',`Render evidence does not match final model: ${id}`);
    check(sourceHash(evidence,textureFile)===sha(textureFile),'blender',`Render evidence does not match final texture: ${id}`);
  }
  check(data?.textures?.atlas===`yuumi97:prop/${id}`&&data?.textures?.particle===`yuumi97:prop/${id}`,'blender',`Final model texture reference mismatch: ${id}`);
  check(views.every(v=>evidence?.views?.includes(v)),'blender',`Missing declared model views: ${id}`);
  const images=views.map(v=>png(path.join(REVIEW,'models',id,`${v}.png`),'blender')).filter(Boolean);
  const needed=weapons.includes(id)?handContexts:hats.includes(id)?['head']:furniture.includes(id)?['floor_00','floor_45']:['floor_00'];
  const contextFile=path.join(REVIEW,'contexts',id,'contexts.json');
  const contextReport=readJson(contextFile,'blender');
  check(contextReport?.id===id,'blender',`Context evidence ID mismatch: ${id}`);
  const reported=contextReport?.contexts??[];
  for(const name of needed){
    const item=reported.find(c=>c.context===name);
    check(Boolean(item),'blender',`Missing context ${id}/${name}`);
    check(evidence?.contextImages?.includes(name),'blender',`Context absent from saved-scene evidence: ${id}/${name}`);
    if(weapons.includes(id)){
      check(Number.isFinite(item?.anchorErrorModelUnits)&&item.anchorErrorModelUnits<=.001,'blender',`Hand grip error exceeds tolerance: ${id}/${name}`);
      check(JSON.stringify(item?.display)===JSON.stringify(data?.display?.[name]),'blender',`Context does not use final display transform: ${id}/${name}`);
      check(JSON.stringify(item?.anchorModelUnits)===JSON.stringify(handAnchors[id]?.anchor),'blender',`Context does not use final grip anchor: ${id}/${name}`);
      check(item?.leftHandMirroringApplied===name.endsWith('_lefthand'),'blender',`Incorrect handedness rule: ${id}/${name}`);
    }
    if(name.startsWith('floor_'))check(item?.touchesFloor===true&&item?.penetratesFloor===false&&Math.abs(item?.lowestYModelUnits)<=.001,'blender',`Floor contact was not verified: ${id}/${name}`);
  }
  const contextImages=needed.map(name=>png(path.join(REVIEW,'contexts',id,`context-${name}.png`),'blender')).filter(Boolean);
  modelEvidence.push({id,elements:data?.elements?.length,blend:blendEntry,geometryReport:fs.existsSync(evidenceFile)?stamp(evidenceFile):null,sourceSha256:evidence?.sourceSha256,images,contexts:reported,contextReport:fs.existsSync(contextFile)?stamp(contextFile):null,contextImages});
}

const iconBoardNames=['icons-native-32.png','icons-contact-8x.png','icons-stress-16.png','icons-v3-contact.png'];
const iconBoards=iconBoardNames.map(name=>png(path.join(REVIEW,'icons',name),'icons')).filter(Boolean);
const iconReport=readJson(path.join(REVIEW,'icons/validation.json'),'icons');
check(iconReport?.count===57,'icons','Icon technical report must cover 57 masters.');
const visualFile=path.join(REVIEW,'v3-visual-review.md');
const visualRecord=fs.existsSync(visualFile)?{status:'RECORDED',...stamp(visualFile),record:fs.readFileSync(visualFile,'utf8'),notice:'This record is attached as observational evidence; this verifier does not assess artistic quality.'}:{status:'NOT_RECORDED',path:relative(production,visualFile)};
const sectionStatus=name=>failures.some(f=>f.section===name)?'FAIL':'PASS';
const status=failures.length?'FAIL':visualRecord.status==='RECORDED'?'PASS':'REVIEW_REQUIRED';
const report={
  schemaVersion:1,timestamp:new Date().toISOString(),target,status,
  counts:{overrideFiles:manifest.length,models3d:models.length,newModels3d:newModels.length,icons32:icons.length,newIcons32:30,guiSelectors:selectors.length,atlasSources:entries.length,preservedBaselineFiles:preserved,blends:modelEvidence.filter(e=>e.blend).length,multiangleImages:modelEvidence.reduce((n,e)=>n+e.images.length,0),contextImages:modelEvidence.reduce((n,e)=>n+e.contextImages.length,0)},
  staticValidation:{status:sectionStatus('static'),report:fs.existsSync(staticFile)?stamp(staticFile):null},
  baselinePreservation:{status:sectionStatus('preservation'),manifest:fs.existsSync(baselineFile)?stamp(baselineFile):null,checked:retained.length,preserved},
  blenderTechnicalValidation:{status:sectionStatus('blender'),notice:'Saved-scene and neutral-fixture evidence, not a Minecraft client test.',models:modelEvidence},
  iconTechnicalValidation:{status:sectionStatus('icons'),boards:iconBoards},
  visualReview:visualRecord,
  minecraftClient:{status:'NOT_RUN',reason:'Client resource loading and live player pose validation are separate integration work.'},
  build:{status:'NOT_RUN',reason:'Asset-only delivery; -Build, PackBuilder packaging and server deployment were intentionally not invoked.'},
  manifest,failures,
};
fs.mkdirSync(REVIEW,{recursive:true});
const output=path.join(REVIEW,'v3-delivery.json');
fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({status,counts:report.counts,failures,report:output},null,2));
if(failures.length)process.exitCode=1;else if(visualRecord.status!=='RECORDED')process.exitCode=2;
