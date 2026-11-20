import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {ROOT,OUT,REVIEW} from './asset-kit.mjs';
import {existingModels,existingIcons,existingSelectors,environment,newModels,newIcons,models,icons,selectors,floorModels,atlasSizes,contexts,pureIcons,target,worldPointers,landmarks,landmarkMounts,heroItems,guiTargets,monsterCatalog,monsterParts,vfx,expectedOverrideFiles} from '../sources/asset-contract.mjs';
import {worldPoint,goldenFixture} from './rig-math.mjs';

const read=file=>JSON.parse(fs.readFileSync(file,'utf8'));
const atlasFile=path.join(OUT,'../minecraft/atlases/blocks.json');
const production=path.join(ROOT,'assets/yuumi97-production'),overrides=path.join(ROOT,'resourcepack-gen/overrides');
const baselineFile=path.join(production,'sources/preservation-baseline.json');
const modelFile=id=>path.join(OUT,'models/item',id+'.json');
const guiFile=id=>path.join(OUT,'models',guiTargets[id]+'.json');
const itemFile=id=>path.join(OUT,'items',id+'.json');
const propFile=id=>path.join(OUT,'textures/prop',id+'.png');
const iconFile=id=>path.join(OUT,'textures/item',id+'.png');
function texturePath(ref){
 assert(typeof ref==='string'&&/^yuumi97:(?:prop|item)\/[a-z0-9_]+$/.test(ref),`Invalid texture reference: ${ref}`);
 return path.join(OUT,'textures',ref.slice('yuumi97:'.length)+'.png');
}
function vector(value,label){assert(Array.isArray(value)&&value.length===3&&value.every(Number.isFinite),label);}
export function corners(element){
 const points=[];
 for(const x of[element.from[0],element.to[0]])for(const y of[element.from[1],element.to[1]])for(const z of[element.from[2],element.to[2]]){
  let p=[x,y,z];
  if(element.rotation){
   const {axis,angle,origin,rescale}=element.rotation,v=p.map((n,i)=>n-origin[i]),a=angle*Math.PI/180,c=Math.cos(a),s=Math.sin(a);
   if(axis==='x')p=[v[0],v[1]*c-v[2]*s,v[1]*s+v[2]*c];
   if(axis==='y')p=[v[0]*c+v[2]*s,v[1],-v[0]*s+v[2]*c];
   if(axis==='z')p=[v[0]*c-v[1]*s,v[0]*s+v[1]*c,v[2]];
   if(rescale){const index={x:0,y:1,z:2}[axis];p=p.map((n,i)=>i===index?n:n/c);}
   p=p.map((n,i)=>n+origin[i]);
  }
  points.push(p);
 }
 return points;
}
function bounds(model){const points=model.elements.flatMap(corners);return {lo:[0,1,2].map(i=>Math.min(...points.map(p=>p[i]))),hi:[0,1,2].map(i=>Math.max(...points.map(p=>p[i])))};}
function near(actual,expected,label,tolerance=1e-6){assert(Math.abs(actual-expected)<=tolerance,`${label}: expected ${expected}, got ${actual}`);}
function contains(model,point,tolerance=1e-5){
 // Test local cuboid containment, rather than a rotated box's larger world AABB.
 return model.elements.some(e=>{
  let p=[...point];
  if(e.rotation){const {origin,angle,axis,rescale}=e.rotation;let v=p.map((n,i)=>n-origin[i]),a=-angle*Math.PI/180,c=Math.cos(a),s=Math.sin(a);if(rescale){const k={x:0,y:1,z:2}[axis];v=v.map((n,i)=>i===k?n:n*Math.cos(a));}
   p=axis==='x'?[v[0],v[1]*c-v[2]*s,v[1]*s+v[2]*c]:axis==='y'?[v[0]*c+v[2]*s,v[1],-v[0]*s+v[2]*c]:[v[0]*c-v[1]*s,v[0]*s+v[1]*c,v[2]];p=p.map((n,i)=>n+origin[i]);}
  return p.every((n,i)=>n>=e.from[i]-tolerance&&n<=e.to[i]+tolerance);
 });
}
function validateFloor(model,id){
 const points=model.elements.flatMap(corners),minY=Math.min(...points.map(p=>p[1]));
 assert(Math.abs(minY)<1e-6,`${id}: rotated geometry must meet Y=0; minimum=${minY}`);
 return {minimumY:minY,bounds:[0,1,2].map(i=>[Math.min(...points.map(p=>p[i])),Math.max(...points.map(p=>p[i]))])};
}
function validateMount(model,id,mount=landmarkMounts[id]){
 const expected={prop_casino_marquee:'wall',prop_casino_chandelier:'ceiling',prop_dealer_throne:'floor',prop_plaza_clock_face:'wall',prop_training_dummy:'floor',prop_sakura_lantern_arch:'gate'};
 assert.equal(mount.kind,expected[id],`${id}: wrong mount classification`);
 const points=model.elements.flatMap(corners),lo=[0,1,2].map(i=>Math.min(...points.map(p=>p[i]))),hi=[0,1,2].map(i=>Math.max(...points.map(p=>p[i])));
 vector(mount.anchor,`${id}: mount anchor`);assert(mount.referenceScale>0);
 if(mount.kind==='floor')validateFloor(model,id);
 if(mount.kind==='wall')assert(Math.abs(hi[2]-8)<.001&&lo[2]<7,`${id}: wall rear must meet Z=8 and front project towards -Z`);
 if(mount.kind==='ceiling')assert(Math.abs(hi[1]-24)<.001&&Math.abs(lo[1]+4)<.001,`${id}: chandelier attachment/tip Y`);
 if(mount.kind==='gate')assert(Math.abs(hi[1]-20)<.001,`${id}: crest attachment Y=20`);
 const attached=contains(model,mount.anchor,.001);
 assert(attached,`${id}: attachment anchor is not supported by geometry`);
 return {...mount,front:[0,0,-1],bounds:[lo,hi],attachmentSupported:true};
}
export function validateModel(m,id,exists=fs.existsSync){
 assert(m.textures?.particle,`${id}: particle missing`);
 assert.equal(m.textures.atlas,`yuumi97:prop/${id}`,`${id}: own prop atlas required`);
 assert.equal(m.textures.particle,`yuumi97:prop/${id}`,`${id}: particle atlas`);
 assert.deepEqual(m.texture_size,[atlasSizes[id],atlasSizes[id]],`${id}: declared atlas dimensions`);
 for(const ref of Object.values(m.textures))assert(exists(texturePath(ref)),`${id}: missing texture ${ref}`);
 assert(Array.isArray(m.elements)&&m.elements.length>0,`${id}: empty model`);
 for(const context of contexts){
  const d=m.display?.[context];assert(d,`${id}: ${context} missing`);
  for(const key of['rotation','translation','scale'])vector(d[key],`${id}: bad display ${context}/${key}`);
  assert(d.scale.every(v=>v>0&&v<=4),`${id}: bad display scale`);
 }
 const names=new Set();
 for(const e of m.elements){
  assert(typeof e.name==='string'&&e.name.length&&!names.has(e.name),`${id}: missing/duplicate element name ${e.name}`);names.add(e.name);
  for(const p of['from','to']){vector(e[p],`${id}/${e.name}: coordinates`);assert(e[p].every(v=>v>=-16&&v<=32),`${id}/${e.name}: bounds`);}
  assert(e.to.every((v,i)=>v>e.from[i]),`${id}/${e.name}: zero/negative volume`);
  if(e.rotation){assert(['x','y','z'].includes(e.rotation.axis));assert([-45,-22.5,0,22.5,45].includes(e.rotation.angle),`${id}/${e.name}: rotation angle`);vector(e.rotation.origin,`${id}/${e.name}: rotation origin`);assert(e.rotation.rescale===undefined||typeof e.rotation.rescale==='boolean');}
  assert(e.faces&&Object.keys(e.faces).length>0,`${id}/${e.name}: no faces`);
  for(const[face,f]of Object.entries(e.faces)){
   assert(['north','south','east','west','up','down'].includes(face));
   assert(Array.isArray(f.uv)&&f.uv.length===4&&f.uv.every(v=>Number.isFinite(v)&&v>=0&&v<=16),`${id}/${e.name}: UV range`);
   assert(f.uv[2]>f.uv[0]&&f.uv[3]>f.uv[1],`${id}/${e.name}: UV area`);
   assert([0,90,180,270].includes(f.rotation??0));
   assert(typeof f.texture==='string'&&f.texture.startsWith('#')&&m.textures[f.texture.slice(1)],`${id}/${e.name}: texture variable`);
  }
 }
 return floorModels.includes(id)?validateFloor(m,id):undefined;
}
function validateSelect(m,id,exists=fs.existsSync){
 const select=m.model;assert.equal(select?.type,'minecraft:select',`${id}: select type`);assert.equal(select.property,'minecraft:display_context');
 assert.equal(select.cases?.length,1);assert.equal(select.cases[0].when,'gui');
 assert.equal(select.cases[0].model?.model,`yuumi97:${guiTargets[id]}`,`${id}: GUI model target`);assert.equal(select.fallback?.model,`yuumi97:item/${id}`,`${id}: 3D fallback target`);
 for(const branch of[select.cases[0].model,select.fallback]){assert.equal(branch.type,'minecraft:model');assert(exists(path.join(OUT,'models',branch.model.slice('yuumi97:'.length)+'.json')),`${id}: missing select reference ${branch.model}`);}
}
function validatePointer(m,id,exists=fs.existsSync){assert.equal(m.model?.type,'minecraft:model');assert.equal(m.model.model,`yuumi97:item/${id}`);assert(exists(modelFile(id)),`${id}: missing direct model reference`);}
function validateGui(m,id,exists=fs.existsSync){assert.equal(m.parent,'minecraft:item/generated');assert.equal(m.textures?.layer0,`yuumi97:item/${id}`);assert(exists(texturePath(m.textures.layer0)),`${id}: missing GUI icon`);}
function validateAtlas(atlas,exists=fs.existsSync){
 assert(Array.isArray(atlas.sources),'blocks atlas sources missing');assert.equal(atlas.sources.length,models.length,'blocks atlas must contain exactly 96 sources');
 const resources=[];
 for(const source of atlas.sources){assert.equal(source.type,'minecraft:single');assert(source.sprite===undefined||source.sprite===source.resource,'blocks atlas sprite name');resources.push(source.resource);assert(exists(texturePath(source.resource)),`blocks atlas missing texture: ${source.resource}`);}
 assert.equal(new Set(resources).size,models.length,'blocks atlas duplicate resource');assert.deepEqual([...resources].sort(),models.map(id=>`yuumi97:prop/${id}`).sort(),'blocks atlas exact ID set');return resources;
}
function validatePixels(raw,size,label,forbidWarningColors=false){
 assert.equal(raw.length,(size*4+1)*size,`${label}: decoded PNG length`);
 let visible=0,transparent=0,partialAlpha=0,minX=size,minY=size,maxX=-1,maxY=-1;const colors=new Set();
 for(let y=0;y<size;y++){
  assert.equal(raw[y*(size*4+1)],0,`${label}: expected unfiltered generated PNG scanline`);
  for(let x=0;x<size;x++){
   const p=y*(size*4+1)+1+x*4,a=raw[p+3];if(a!==0&&a!==255)partialAlpha++;
   if(a){visible++;const rgb=raw.subarray(p,p+3).toString('hex');if(forbidWarningColors)assert(!['ff1133','ff2a2a'].includes(rgb),`${label}: reserved monster-warning color #${rgb}`);colors.add(raw.subarray(p,p+4).toString('hex'));minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}else transparent++;
  }
 }
 assert(visible>0,`${label}: blank PNG`);assert(colors.size>=4,`${label}: insufficient palette variation`);
 if(size===32){assert.equal(partialAlpha,0,`${label}: icons require binary alpha / no antialiasing`);assert(transparent>0,`${label}: no transparent background`);assert(minX>=1&&minY>=1&&maxX<=30&&maxY<=30,`${label}: 1px transparent contour margin required`);assert(visible>=50&&visible<=950,`${label}: unreadable icon occupancy ${visible}`);}
 return {size,visiblePixels:visible,transparentPixels:transparent,partialAlphaPixels:partialAlpha,colors:colors.size,bounds:[minX,minY,maxX,maxY]};
}
function decodePng(b,size,label){
 assert.equal(b.subarray(0,8).toString('hex'),'89504e470d0a1a0a',`${label}: PNG signature`);assert.equal(b.toString('ascii',12,16),'IHDR');
 assert.equal(b.readUInt32BE(16),size,`${label}: PNG width`);assert.equal(b.readUInt32BE(20),size,`${label}: PNG height`);assert.equal(b[24],8);assert.equal(b[25],6,'RGBA required');assert.equal(b[26],0);assert.equal(b[27],0);assert.equal(b[28],0,'Non-interlaced PNG required');
 const parts=[];let ended=false;
 for(let p=8;p<b.length;){assert(p+12<=b.length,`${label}: truncated PNG chunk`);const n=b.readUInt32BE(p),type=b.toString('ascii',p+4,p+8);assert(p+12+n<=b.length,`${label}: truncated ${type}`);if(type==='IDAT')parts.push(b.subarray(p+8,p+8+n));if(type==='IEND')ended=true;p+=12+n;}
 assert(ended&&parts.length,`${label}: PNG IDAT/IEND missing`);return zlib.inflateSync(Buffer.concat(parts));
}
export function validatePng(file,size,forbidWarningColors=false){const b=fs.readFileSync(file);return {...validatePixels(decodePng(b,size,file),size,file,forbidWarningColors),bytes:b.length};}
function validatePreservation(){
 const rows=read(baselineFile),records=new Map(rows.map(entry=>[entry.path,entry.sha256]));
 const expected=[...existingModels.flatMap(id=>[`assets/yuumi97/models/item/${id}.json`,`assets/yuumi97/textures/prop/${id}.png`]),...existingIcons.map(id=>`assets/yuumi97/textures/item/${id}.png`),...existingSelectors.flatMap(id=>[`assets/yuumi97/models/gui/${id}.json`,`assets/yuumi97/items/${id}.json`]),...[...environment,...landmarks].map(id=>`assets/yuumi97/items/${id}.json`)];
 assert.equal(rows.length,174,'Preserve exactly 174 prior non-atlas files');assert.equal(records.size,174,'Duplicate preservation path');assert.deepEqual([...records.keys()].sort(),expected.sort(),'Exact preservation file set');
 for(const relative of expected){const hash=records.get(relative);assert(/^[a-f0-9]{64}$/.test(hash),'Invalid baseline hash '+relative);const current=crypto.createHash('sha256').update(fs.readFileSync(path.join(overrides,relative))).digest('hex');assert.equal(current,hash,`Preserved asset changed: ${relative}`);}
 return {manifest:path.relative(production,baselineFile).replaceAll('\\','/'),preservedFiles:174,algorithm:'sha256',atlasChangeAllowed:true};
}

const vfxRules={
 vfx_slash_crescent_gold:['horizontal_crescent',['crescent_0','crescent_1','crescent_2']],
 vfx_slash_crimson_heavy:['horizontal_heavy_slash',['molten_wave','raging_flame_tooth','white_hot_core']],
 vfx_titan_greatsword_drop:['vertical_drop',['obsidian_blade','molten_blade_channel','spiral_flame']],
 vfx_slash_crescent_shadow:['crossed_slash',['cross_-45','cross_45','black_cat_rune']],
 vfx_shadow_mark_crest:['vertical_mark',['crescent_fang','diamond_shadow_frame','vertical_dark_eye']],
 vfx_star_arrow_volley:['forward_projectile',['starhead','comet_ribbon_central','comet_ribbon_left','comet_ribbon_right']],
 vfx_astral_bow_sigil:['horizontal_aiming_ring',['outer_astral_gold','constellation_chord','aim_marker']],
 vfx_magic_circle_arcane:['thin_floor_circle',['outer_runic_ring','hexagram_0','hexagram_1','ancient_arcane_rune']],
 vfx_gravity_singularity:['sphere_with_tilted_ring',['event_horizon','accretion_outer_shadow','accretion_white_hot_rim']],
 vfx_arcane_meteor:['diagonal_meteor',['violet_magma_body','ancient_ice_crystal','leading_impact_crystal']],
 vfx_magic_circle_sanctuary:['thin_floor_circle',['outer_cathedral_gold','inner_healing_ring','quatrefoil_petals','holy_cross']],
 vfx_holy_spear:['vertical_spear',['downward_lance','wing_-1','wing_1','holy_water_gem']],
 vfx_golden_fishing_vortex:['surface_vortex',['funnel_0','funnel_1','funnel_2','golden_fish','treasure_coin']],
 vfx_feast_cauldron_aura:['floor_cauldron',['paw_foot','cat_ear_handle','recessed_stew_surface','tilted_ladle','steam_cloud']],
};
export function validateVfxGeometry(model,id){
 const {lo,hi}=bounds(model),range=(axis,min,max)=>assert(lo[axis]>=min-1e-5&&hi[axis]<=max+1e-5,`${id}: axis ${axis} outside ${min}..${max}`);
 if(['vfx_magic_circle_arcane','vfx_magic_circle_sanctuary'].includes(id)){
  near(lo[1],7.6,id+' thin-disc lower Y');near(hi[1],8.4,id+' thin-disc upper Y');for(const axis of[0,2]){near(lo[axis],-14,id+' full-disc lower span');near(hi[axis],30,id+' full-disc upper span');}
 }
 if(id==='vfx_astral_bow_sigil')range(1,7,9);
 if(id==='vfx_titan_greatsword_drop'||id==='vfx_holy_spear'){
  near(lo[1],-14,id+' downward tip');assert(contains(model,[8,-14,8]),id+': tip must meet vertical X=8/Z=8 axis');
  if(id==='vfx_titan_greatsword_drop'){near(hi[1],30,id+' grip top');assert(contains(model,[8,30,8]),id+': grip top must meet vertical axis');}
  else {const wings=model.elements.filter(e=>/^wing_[+-]?1/.test(e.name));assert(wings.length,id+': missing wings');const wb=bounds({elements:wings});assert(wb.lo[1]>=12-1e-5&&wb.hi[1]<=24+1e-5,id+': wing Y range must be 12..24');}
 }
 if(id==='vfx_star_arrow_volley'){near(lo[2],-14,id+' forward tip');near(hi[2],30,id+' comet tail');assert(contains(model,[8,8,-14]),id+': forward tip must meet -Z axis');}
 if(id==='vfx_gravity_singularity')assert(model.elements.some(e=>e.name.startsWith('accretion_')&&e.rotation?.axis==='x'&&e.rotation.angle===22.5),id+': missing 22.5-degree accretion ring');
 if(id==='vfx_golden_fishing_vortex'){near(lo[1],0,id+' funnel bottom');near(hi[1],8,id+' water surface');for(const axis of[0,2]){near(lo[axis],-14,id+' lower surface extent');near(hi[axis],30,id+' upper surface extent');}}
 if(id==='vfx_feast_cauldron_aura'){
  validateFloor(model,id);const steam=model.elements.filter(e=>e.name.startsWith('steam_cloud'));assert(steam.length,id+': missing steam geometry');const sb=bounds({elements:steam});assert(sb.lo[1]>=18-1e-5&&sb.hi[1]<=28+1e-5,id+': steam must lie in Y18..28');
 }
 return {bounds:[lo,hi],geometryContract:true};
}
export function validateVfxContract(contract,getModel=id=>read(modelFile(id))){
 assert.equal(contract.schemaVersion,1);assert.deepEqual(contract.modelOrigin,[8,8,8]);assert.deepEqual(contract.front,[0,0,-1]);assert.deepEqual(Object.keys(contract.models).sort(),[...vfx].sort(),'Exact VFX contract IDs');
 return vfx.map(id=>{const meta=contract.models[id],model=getModel(id),[kind,names]=vfxRules[id];assert.equal(meta.kind,kind,id+': VFX kind');assert.deepEqual(meta.pivot,[8,8,8],id+': VFX pivot');assert.equal(meta.elements,model.elements.length,id+': stale VFX geometry metadata');assert.deepEqual(meta.requiredComponentNames,names,id+': required component catalog');for(const prefix of names)assert(model.elements.some(e=>e.name.startsWith(prefix)),id+': missing component '+prefix);return {id,kind,...validateVfxGeometry(model,id)};});
}
function validateRigCopies(sourceBytes,packBytes){assert(sourceBytes.equals(packBytes),'Source and packed monster rig manifests differ');return JSON.parse(sourceBytes.toString('utf8'));}
export function validateRigs(manifest,getModel=id=>read(modelFile(id))){
 assert.equal(manifest.schemaVersion,1);assert.equal(manifest.unitsPerBlock,16);assert.deepEqual(manifest.modelOrigin,[8,8,8]);assert.deepEqual(manifest.rootAnchor,[8,0,8]);assert.deepEqual(manifest.front,[0,0,-1]);assert.equal(manifest.yawConvention,'bukkit');assert.equal(manifest.posePitchToLocalX,-1);
 assert.deepEqual(Object.keys(manifest.rigs).sort(),monsterCatalog.map(m=>m.id).sort(),'Exact 18-rig catalog');const refs=[],records=[];
 for(const spec of monsterCatalog){const rig=manifest.rigs[spec.id],root=spec.id+'_'+spec.parts[0];assert(Number.isFinite(rig.baseScale)&&rig.baseScale>0,spec.id+': baseScale must be positive and finite');assert.equal(rig.baseScale,spec.baseScale,spec.id+': incorrect baseScale');assert.equal(rig.rootModel,'yuumi97:'+root,spec.id+': wrong root part');assert.deepEqual(Object.keys(rig.sockets).sort(),spec.parts.slice(1).sort(),spec.id+': incorrect socket roles');
  validateFloor(getModel(root),root);refs.push(rig.rootModel);
  for(const role of spec.parts.slice(1)){const socket=rig.sockets[role],id=spec.id+'_'+role;assert.equal(socket.model,'yuumi97:'+id,spec.id+': wrong '+role+' model');vector(socket.offset,spec.id+'/'+role+': finite 3-axis socket offset');const model=getModel(id);assert(model.elements?.length,id+': missing geometry');refs.push(socket.model);
   if(role==='head'){const b=bounds(model);assert(b.lo[1]>=8-1e-6&&b.hi[1]<=24+1e-6,id+': head geometry must stay within Y8..24');}
  }
  records.push({id:spec.id,chapter:spec.chapter,baseScale:rig.baseScale,rootModel:rig.rootModel,sockets:rig.sockets});
 }
 assert.equal(refs.length,42);assert.equal(new Set(refs).size,42,'Duplicate monster part references');assert.deepEqual(refs.map(r=>r.slice('yuumi97:'.length)).sort(),[...monsterParts].sort(),'Exact 42 rig part references');
 return {count:records.length,partCount:refs.length,unitsPerBlock:16,rigs:records};
}
function yawCardinal(p,yaw){const[x,y,z]=p;return yaw===0?[-x,y,-z]:yaw===90?[z,y,-x]:yaw===180?[x,y,z]:[-z,y,x];}
export function validateRigMath(transform=worldPoint,manifest){
 const compare=(actual,expected,label)=>{vector(actual,label);const error=Math.hypot(...actual.map((n,i)=>n-expected[i]));assert(error<.01,`${label}: ${error} blocks of error`);return error;};
 compare(transform(goldenFixture.vertex,goldenFixture),goldenFixture.expectedPoint,'Golden negative-pitch-up fixture');compare(transform([8,8,8],goldenFixture),goldenFixture.expectedPivot,'Golden pivot fixture');
 let cases=2,maxPivotError=0;const floor=[3.25,-2,7.5],offset=[3,16,-5];
 for(const yaw of[0,90,180,270])for(const scale of[1,2]){
  compare(transform([8,0,8],{floor,yaw,scale,root:true}),floor,'Root floor anchor');cases++;
  const forward=yawCardinal([0,0,-1],yaw).map((n,i)=>floor[i]+n*scale);compare(transform([8,8,-8],{floor,yaw,scale}),forward,'Bukkit cardinal forward');cases++;
  for(const pitch of[-70,-65,-60,0,55]){const expected=yawCardinal(offset,yaw).map((n,i)=>floor[i]+n*scale/16),options={floor,yaw,scale,pitch,offset};maxPivotError=Math.max(maxPivotError,compare(transform([8,8,8],options),expected,'Pitch-invariant socket pivot'));const tip=transform([8,8,-8],options);near(tip[1],expected[1]-Math.sin(pitch*Math.PI/180)*scale,'Semantic pitch height');near(Math.hypot(...tip.map((n,i)=>n-expected[i])),scale,'Rigid lever length');cases+=3;}
 }
 let actualSocketCases=0;
 if(manifest)for(const rig of Object.values(manifest.rigs))for(const socket of Object.values(rig.sockets))for(const yaw of[0,90,180,270])for(const multiplier of[1,2])for(const pitch of[-70,-65,-60,0,55]){
  const scale=rig.baseScale*multiplier,expected=yawCardinal(socket.offset,yaw).map((n,i)=>floor[i]+n*scale/16);maxPivotError=Math.max(maxPivotError,compare(transform([8,8,8],{floor,yaw,scale,pitch,offset:socket.offset}),expected,'Actual rig socket pivot'));actualSocketCases++;
 }
 return {cases,actualSocketCases,yaws:[0,90,180,270],scales:[1,2],pitches:[-70,-65,-60,0,55],maximumPivotErrorBlocks:maxPivotError,toleranceBlocks:.01,goldenFixture};
}
function validateFileSet(paths){assert.equal(expectedOverrideFiles.length,367,'Expected override count');assert.equal(new Set(expectedOverrideFiles).size,367,'Duplicate expected override path');assert.deepEqual([...paths].sort(),expectedOverrideFiles,'Exact 367-file override set');}
// Mutate only in-memory copies. None of these corruptions touch delivered files.
export function runSelfTests(){
 const tests=[],rejects=(name,action)=>{assert.throws(action,undefined,`${name}: corruption accepted`);tests.push(name);};
 const id='prop_yuumi_statue',good=read(modelFile(id));let bad=structuredClone(good);
 bad.elements[0].from[0]=-17;rejects('out-of-range coordinate rejected',()=>validateModel(bad,id,()=>true));
 rejects('missing texture rejected',()=>validateModel(good,id,()=>false));
 bad=structuredClone(good);bad.elements[0].rotation={axis:'x',angle:17,origin:[8,8,8]};rejects('illegal element rotation rejected',()=>validateModel(bad,id,()=>true));
 bad=structuredClone(good);Object.values(bad.elements[0].faces)[0].uv=[1,1,1,2];rejects('degenerate UV rejected',()=>validateModel(bad,id,()=>true));
 bad=structuredClone(good);Object.values(bad.elements[0].faces)[0].uv=[0,0,17,16];rejects('out-of-range UV rejected',()=>validateModel(bad,id,()=>true));
 const selection=read(itemFile('prowling_wand'));rejects('missing GUI model rejected',()=>validateSelect(selection,'prowling_wand',()=>false));
 const wrong=structuredClone(selection);wrong.model.cases[0].model.model='yuumi97:gui/you_and_me_bell';rejects('wrong GUI selector target rejected',()=>validateSelect(wrong,'prowling_wand',()=>true));
 const atlas={sources:models.map(id=>({type:'minecraft:single',resource:`yuumi97:prop/${id}`}))},missing=structuredClone(atlas);validateAtlas(atlas,()=>true);missing.sources.pop();rejects('missing atlas entry rejected',()=>validateAtlas(missing,()=>true));
 const duplicate=structuredClone(atlas);duplicate.sources[duplicate.sources.length-1]=structuredClone(duplicate.sources[0]);rejects('duplicate atlas entry rejected',()=>validateAtlas(duplicate,()=>true));
 const extra=structuredClone(atlas);extra.sources[extra.sources.length-1]={type:'minecraft:single',resource:'yuumi97:prop/uncontracted_extra'};rejects('unexpected atlas ID rejected',()=>validateAtlas(extra,()=>true));
 const png=fs.readFileSync(iconFile('churu_salmon')),wrongSize=Buffer.from(png);wrongSize.writeUInt32BE(31,16);rejects('wrong icon dimensions rejected',()=>decodePng(wrongSize,32,'fault-size'));
 const raw=decodePng(png,32,'fault-alpha'),alpha=Buffer.from(raw);alpha[10*(32*4+1)+1+10*4+3]=128;rejects('antialiased icon alpha rejected',()=>validatePixels(alpha,32,'fault-alpha'));
 const margin=Buffer.from(raw);margin[4]=255;rejects('opaque icon border rejected',()=>validatePixels(margin,32,'fault-margin'));
 const floor={elements:[{from:[0,0,0],to:[2,2,2],rotation:{axis:'z',angle:45,origin:[1,1,1]}}]};rejects('rotation below the floor rejected',()=>validateFloor(floor,'fault-floor'));
 rejects('wrong landmark direct pointer rejected',()=>validatePointer({model:{type:'minecraft:model',model:'yuumi97:item/prop_training_dummy'}},'prop_casino_marquee',()=>true));
 rejects('wall landmark classified as floor rejected',()=>validateMount(read(modelFile('prop_casino_marquee')),'prop_casino_marquee',{...landmarkMounts.prop_casino_marquee,kind:'floor'}));
 rejects('missing landmark anchor rejected',()=>validateMount(read(modelFile('prop_sakura_lantern_arch')),'prop_sakura_lantern_arch',{...landmarkMounts.prop_sakura_lantern_arch,anchor:[31,20,31]}));
 const modernSelect={model:{type:'minecraft:select',property:'minecraft:display_context',cases:[{when:'gui',model:{type:'minecraft:model',model:'yuumi97:item/guidebook_gui'}}],fallback:{type:'minecraft:model',model:'yuumi97:item/guidebook'}}};validateSelect(modernSelect,'guidebook',()=>true);modernSelect.model.cases[0].model.model='yuumi97:gui/guidebook';rejects('new GUI helper at wrong directory rejected',()=>validateSelect(modernSelect,'guidebook',()=>true));
 rejects('unexpected override file rejected',()=>validateFileSet([...expectedOverrideFiles,'assets/yuumi97/items/unexpected.json']));rejects('missing override file rejected',()=>validateFileSet(expectedOverrideFiles.slice(1)));
 const warning=Buffer.from(raw);warning[1]=255;warning[2]=17;warning[3]=51;warning[4]=255;rejects('reserved monster warning color rejected',()=>validatePixels(warning,32,'fault-warning',true));
 const disc={elements:[{name:'disc',from:[-14,7.5,-14],to:[30,8.4,30]}]};rejects('over-thick magic circle rejected',()=>validateVfxGeometry(disc,'vfx_magic_circle_arcane'));
 const wrongTip={elements:[{name:'tip',from:[7.5,-13,7.5],to:[8.5,30,8.5]}]};rejects('upshifted falling sword tip rejected',()=>validateVfxGeometry(wrongTip,'vfx_titan_greatsword_drop'));
 const fixture={schemaVersion:1,unitsPerBlock:16,modelOrigin:[8,8,8],rootAnchor:[8,0,8],front:[0,0,-1],yawConvention:'bukkit',posePitchToLocalX:-1,rigs:Object.fromEntries(monsterCatalog.map(m=>[m.id,{baseScale:m.baseScale,rootModel:'yuumi97:'+m.id+'_'+m.parts[0],sockets:Object.fromEntries(m.parts.slice(1).map(p=>[p,{model:'yuumi97:'+m.id+'_'+p,offset:[0,16,0]}]))}]))};
 const fixtureModel=id=>({elements:[{name:'fixture',from:[7,id.endsWith('_body')||id.endsWith('_core')?0:8,7],to:[9,id.endsWith('_body')||id.endsWith('_core')?2:10,9]}]});validateRigs(fixture,fixtureModel);
 let rigBad=structuredClone(fixture);delete rigBad.rigs.mob_grimoire_mimic;rejects('missing monster rig rejected',()=>validateRigs(rigBad,fixtureModel));
 rigBad=structuredClone(fixture);rigBad.rigs.mob_grimoire_mimic.baseScale=2;rejects('incorrect positive baseScale rejected',()=>validateRigs(rigBad,fixtureModel));
 rigBad=structuredClone(fixture);rigBad.rigs.mob_grimoire_mimic.sockets.arm.offset[1]=NaN;rejects('nonfinite socket offset rejected',()=>validateRigs(rigBad,fixtureModel));
 rigBad=structuredClone(fixture);rigBad.rigs.mob_grimoire_mimic.sockets.head=structuredClone(rigBad.rigs.mob_grimoire_mimic.sockets.arm);rejects('unexpected rig socket role rejected',()=>validateRigs(rigBad,fixtureModel));
 rigBad=structuredClone(fixture);rigBad.rigs.mob_grimoire_mimic.sockets.arm.model='yuumi97:mob_clockwork_sentinel_arm';rejects('cross-rig model reference rejected',()=>validateRigs(rigBad,fixtureModel));
 rejects('head below neck pivot rejected',()=>validateRigs(fixture,id=>id.endsWith('_head')?{elements:[{from:[7,7.9,7],to:[9,10,9]}]}:fixtureModel(id)));
 rejects('source/packed rig mismatch rejected',()=>validateRigCopies(Buffer.from('{}'),Buffer.from('{ }')));
 rejects('wrong pitch sign rejected',()=>validateRigMath((p,o={})=>worldPoint(p,{...o,pitch:-(o.pitch??0)})));
 rejects('wrong yaw convention rejected',()=>validateRigMath((p,o={})=>worldPoint(p,{...o,yaw:180-(o.yaw??180)})));
 rejects('pivot scale drift rejected',()=>validateRigMath((p,o={})=>worldPoint(p,{...o,scale:(o.scale??1)*1.02})));
 return tests;
}
export function runValidation(){
 for(const[name,list,count]of[['existing models',existingModels,35],['existing icons',existingIcons,57],['new models',newModels,61],['new icons',newIcons,4],['all models',models,96],['all icons',icons,61],['GUI selectors',selectors,22],['floor models',floorModels,29],['landmarks',landmarks,6],['world pointers',worldPointers,68],['monster parts',monsterParts,42]]){assert.equal(list.length,count,`${name}: count`);assert.equal(new Set(list).size,count,`${name}: duplicate ID`);}
 assert.deepEqual(Object.keys(atlasSizes).sort(),[...models].sort(),'Complete explicit atlas dimensions');assert.deepEqual(Object.keys(guiTargets).sort(),[...selectors].sort(),'Exact GUI target catalog');assert.equal(selectors.length+worldPointers.length,90);
 for(const id of[...vfx,...monsterParts,'weapon_arcane_orb_tome'])assert.equal(atlasSizes[id],128,id+': expected 128px atlas');for(const id of['weapon_berserker_greatsword','weapon_priest_staff','guidebook','prop_wayfinder_arrow'])assert.equal(atlasSizes[id],64,id+': expected 64px atlas');
 const required=expectedOverrideFiles.map(p=>path.join(overrides,p));
 const missing=required.filter(file=>!fs.existsSync(file));assert.equal(missing.length,0,`Missing ${missing.length} required asset files:\n${missing.map(file=>path.relative(ROOT,file)).join('\n')}`);
 const walk=dir=>fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.relative(overrides,path.join(dir,e.name)).replaceAll('\\','/')]);validateFileSet(walk(path.join(overrides,'assets')));
 const result={timestamp:new Date().toISOString(),target,models:[],icons:[],checks:[]};
 result.atlas={count:validateAtlas(read(atlasFile)).length,exactSet:true,duplicates:0};
 for(const id of models){const m=read(modelFile(id)),floor=validateModel(m,id),image=validatePng(propFile(id),atlasSizes[id],newModels.includes(id));result.models.push({id,elements:m.elements.length,faces:m.elements.reduce((sum,e)=>sum+Object.keys(e.faces).length,0),...image,...(floor?{floor}:{})});}
 for(const id of icons)result.icons.push({id,...validatePng(iconFile(id),32,newIcons.includes(id))});
 for(const id of selectors){validateSelect(read(itemFile(id)),id);validateGui(read(guiFile(id)),id);}
 for(const id of worldPointers)validatePointer(read(itemFile(id)),id);
 result.landmarkMounts=landmarks.map(id=>({id,...validateMount(read(modelFile(id)),id)}));
 result.vfx=validateVfxContract(read(path.join(production,'sources/skill-vfx-contract.json')));
 const rig=validateRigCopies(fs.readFileSync(path.join(production,'sources/monster-rig-sockets.json')),fs.readFileSync(path.join(OUT,'monster_rigs.json')));result.monsterRigs={...validateRigs(rig),identicalSourceAndPack:true};
 result.rigMath=validateRigMath(worldPoint,rig);result.preservation=validatePreservation();result.checks=runSelfTests();result.guiSelectors=selectors.length;result.worldPointers=worldPointers.length;result.itemDefinitions=selectors.length+worldPointers.length;result.overrideFileCount=required.length;
 result.pure2DModelOwnership={owner:'SOL / PackBuilder.java',count:pureIcons.length,ids:pureIcons,validation:'PNG masters checked here; generated item models and pointers are a downstream packaging check'};
 result.status='PASS';return result;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 let result;
 try{result=runValidation();console.log(`PASS: ${models.length} 3D models, ${icons.length} RGBA32 icons, ${selectors.length} selectors, ${worldPointers.length} direct pointers, ${result.monsterRigs.count} rigs.`);console.log(`Exact ${result.atlas.count}-sprite atlas, ${result.preservation.preservedFiles} preserved files, ${result.checks.length} corruption cases, ${result.overrideFileCount} delivered files.`);}
 catch(error){result={timestamp:new Date().toISOString(),target,status:'FAIL',error:error.message};console.error(error.stack??error);process.exitCode=1;}
 fs.mkdirSync(REVIEW,{recursive:true});fs.writeFileSync(path.join(REVIEW,'validation.json'),JSON.stringify(result,null,2)+'\n');
}
