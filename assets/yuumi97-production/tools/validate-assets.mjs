import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import {ROOT,OUT,REVIEW} from './asset-kit.mjs';
import {baselineModels,baselineIcons,environment,newModels,newIcons,models,icons,selectors,floorModels,atlasSizes,contexts,pureIcons,target,worldPointers,landmarksV31,landmarkMounts} from '../sources/asset-contract.mjs';

const read=file=>JSON.parse(fs.readFileSync(file,'utf8'));
const atlasFile=path.join(OUT,'../minecraft/atlases/blocks.json');
const baselineFile=path.join(ROOT,'assets/yuumi97-production/sources/v2-baseline.json');
const modelFile=id=>path.join(OUT,'models/item',id+'.json');
const guiFile=id=>path.join(OUT,'models/gui',id+'.json');
const itemFile=id=>path.join(OUT,'items',id+'.json');
const propFile=id=>path.join(OUT,'textures/prop',id+'.png');
const iconFile=id=>path.join(OUT,'textures/item',id+'.png');
function texturePath(ref){
 assert(typeof ref==='string'&&/^yuumi97:(?:prop|item)\/[a-z0-9_]+$/.test(ref),`Invalid texture reference: ${ref}`);
 return path.join(OUT,'textures',ref.slice('yuumi97:'.length)+'.png');
}
function vector(value,label){assert(Array.isArray(value)&&value.length===3&&value.every(Number.isFinite),label);}
function corners(element){
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
 const attached=model.elements.some(e=>{const p=corners(e);return mount.anchor.every((v,i)=>v>=Math.min(...p.map(a=>a[i]))-.001&&v<=Math.max(...p.map(a=>a[i]))+.001);});
 assert(attached,`${id}: attachment anchor is not supported by geometry`);
 return {...mount,front:[0,0,-1],bounds:[lo,hi],attachmentSupported:true};
}
function validateModel(m,id,exists=fs.existsSync){
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
 assert.equal(select.cases[0].model?.model,`yuumi97:gui/${id}`,`${id}: GUI model target`);assert.equal(select.fallback?.model,`yuumi97:item/${id}`,`${id}: 3D fallback target`);
 for(const branch of[select.cases[0].model,select.fallback]){assert.equal(branch.type,'minecraft:model');assert(exists(path.join(OUT,'models',branch.model.slice('yuumi97:'.length)+'.json')),`${id}: missing select reference ${branch.model}`);}
}
function validatePointer(m,id,exists=fs.existsSync){assert.equal(m.model?.type,'minecraft:model');assert.equal(m.model.model,`yuumi97:item/${id}`);assert(exists(modelFile(id)),`${id}: missing direct model reference`);}
function validateGui(m,id,exists=fs.existsSync){assert.equal(m.parent,'minecraft:item/generated');assert.equal(m.textures?.layer0,`yuumi97:item/${id}`);assert(exists(texturePath(m.textures.layer0)),`${id}: missing GUI icon`);}
function validateAtlas(atlas,exists=fs.existsSync){
 assert(Array.isArray(atlas.sources),'blocks atlas sources missing');assert.equal(atlas.sources.length,35,'blocks atlas must contain exactly 35 sources');
 const resources=[];
 for(const source of atlas.sources){assert.equal(source.type,'minecraft:single');assert(source.sprite===undefined||source.sprite===source.resource,'blocks atlas sprite name');resources.push(source.resource);assert(exists(texturePath(source.resource)),`blocks atlas missing texture: ${source.resource}`);}
 assert.equal(new Set(resources).size,35,'blocks atlas duplicate resource');assert.deepEqual([...resources].sort(),models.map(id=>`yuumi97:prop/${id}`).sort(),'blocks atlas exact ID set');return resources;
}
function validatePixels(raw,size,label){
 assert.equal(raw.length,(size*4+1)*size,`${label}: decoded PNG length`);
 let visible=0,transparent=0,partialAlpha=0,minX=size,minY=size,maxX=-1,maxY=-1;const colors=new Set();
 for(let y=0;y<size;y++){
  assert.equal(raw[y*(size*4+1)],0,`${label}: expected unfiltered generated PNG scanline`);
  for(let x=0;x<size;x++){
   const p=y*(size*4+1)+1+x*4,a=raw[p+3];if(a!==0&&a!==255)partialAlpha++;
   if(a){visible++;colors.add(raw.subarray(p,p+4).toString('hex'));minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}else transparent++;
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
function validatePng(file,size){const b=fs.readFileSync(file);return {...validatePixels(decodePng(b,size,file),size,file),bytes:b.length};}
function validatePreservation(){
 const records=new Map(read(baselineFile).map(entry=>[entry.path,entry.sha256]));
 const expected=[...baselineModels.flatMap(id=>[`assets/yuumi97/models/item/${id}.json`,`assets/yuumi97/textures/prop/${id}.png`]),...baselineIcons.map(id=>`assets/yuumi97/textures/item/${id}.png`),...selectors.slice(0,3).flatMap(id=>[`assets/yuumi97/models/gui/${id}.json`,`assets/yuumi97/items/${id}.json`])];
 assert.equal(expected.length,51,'51 original art/selector files must be preserved');
 for(const relative of expected){assert(records.has(relative),`Baseline manifest missing ${relative}`);const current=crypto.createHash('sha256').update(fs.readFileSync(path.join(ROOT,'resourcepack-gen/overrides',relative))).digest('hex');assert.equal(current,records.get(relative),`Original asset changed: ${relative}`);}
 return {manifest:path.relative(ROOT,baselineFile).replaceAll('\\','/'),preservedFiles:51,originalArtFiles:45,originalGuiAndSelectors:6,algorithm:'sha256',atlasChangeAllowed:true};
}
function validateV3Preservation(){
 const manifest=path.join(ROOT,'assets/yuumi97-production/sources/v3-baseline.json'),rows=read(manifest);
 assert.equal(rows.length,156,'v3 baseline must preserve 156 non-atlas files');assert.equal(new Set(rows.map(r=>r.path)).size,156);
 for(const row of rows){assert(row.path.startsWith('assets/')&&!row.path.includes('..')&&row.path!=='assets/minecraft/atlases/blocks.json');const hash=crypto.createHash('sha256').update(fs.readFileSync(path.join(ROOT,'resourcepack-gen/overrides',row.path))).digest('hex');assert.equal(hash,row.sha256,`v3 asset changed: ${row.path}`);}
 return {manifest:path.relative(ROOT,manifest).replaceAll('\\','/'),preservedFiles:rows.length,algorithm:'sha256',atlasChangeAllowed:true};
}
// Mutate only in-memory copies. None of these corruptions touch delivered files.
function testValidator(){
 const tests=[],rejects=(name,action)=>{assert.throws(action,undefined,`${name}: corruption accepted`);tests.push(name);};
 const id='prop_yuumi_statue',good=read(modelFile(id));let bad=structuredClone(good);
 bad.elements[0].from[0]=-17;rejects('out-of-range coordinate rejected',()=>validateModel(bad,id,()=>true));
 rejects('missing texture rejected',()=>validateModel(good,id,()=>false));
 bad=structuredClone(good);bad.elements[0].rotation={axis:'x',angle:17,origin:[8,8,8]};rejects('illegal element rotation rejected',()=>validateModel(bad,id,()=>true));
 bad=structuredClone(good);Object.values(bad.elements[0].faces)[0].uv=[1,1,1,2];rejects('degenerate UV rejected',()=>validateModel(bad,id,()=>true));
 bad=structuredClone(good);Object.values(bad.elements[0].faces)[0].uv=[0,0,17,16];rejects('out-of-range UV rejected',()=>validateModel(bad,id,()=>true));
 const selection=read(itemFile('prowling_wand'));rejects('missing GUI model rejected',()=>validateSelect(selection,'prowling_wand',()=>false));
 const wrong=structuredClone(selection);wrong.model.cases[0].model.model='yuumi97:gui/you_and_me_bell';rejects('wrong GUI selector target rejected',()=>validateSelect(wrong,'prowling_wand',()=>true));
 const atlas=read(atlasFile),missing=structuredClone(atlas);missing.sources.pop();rejects('missing atlas entry rejected',()=>validateAtlas(missing,()=>true));
 const duplicate=structuredClone(atlas);duplicate.sources[duplicate.sources.length-1]=structuredClone(duplicate.sources[0]);rejects('duplicate atlas entry rejected',()=>validateAtlas(duplicate,()=>true));
 const extra=structuredClone(atlas);extra.sources[extra.sources.length-1]={type:'minecraft:single',resource:'yuumi97:prop/uncontracted_extra'};rejects('unexpected atlas ID rejected',()=>validateAtlas(extra,()=>true));
 const png=fs.readFileSync(iconFile('churu_salmon')),wrongSize=Buffer.from(png);wrongSize.writeUInt32BE(31,16);rejects('wrong icon dimensions rejected',()=>decodePng(wrongSize,32,'fault-size'));
 const raw=decodePng(png,32,'fault-alpha'),alpha=Buffer.from(raw);alpha[10*(32*4+1)+1+10*4+3]=128;rejects('antialiased icon alpha rejected',()=>validatePixels(alpha,32,'fault-alpha'));
 const margin=Buffer.from(raw);margin[4]=255;rejects('opaque icon border rejected',()=>validatePixels(margin,32,'fault-margin'));
 const floor={elements:[{from:[0,0,0],to:[2,2,2],rotation:{axis:'z',angle:45,origin:[1,1,1]}}]};rejects('rotation below the floor rejected',()=>validateFloor(floor,'fault-floor'));
 rejects('wrong landmark direct pointer rejected',()=>validatePointer({model:{type:'minecraft:model',model:'yuumi97:item/prop_training_dummy'}},'prop_casino_marquee',()=>true));
 rejects('wall landmark classified as floor rejected',()=>validateMount(read(modelFile('prop_casino_marquee')),'prop_casino_marquee',{...landmarkMounts.prop_casino_marquee,kind:'floor'}));
 rejects('missing landmark anchor rejected',()=>validateMount(read(modelFile('prop_sakura_lantern_arch')),'prop_sakura_lantern_arch',{...landmarkMounts.prop_sakura_lantern_arch,anchor:[31,20,31]}));
 return tests;
}
function main(){
 for(const[name,list,count]of[['baseline models',baselineModels,9],['baseline icons',baselineIcons,27],['new models',newModels,20],['new icons',newIcons,30],['all models',models,35],['all icons',icons,57],['GUI selectors',selectors,18],['environment props',environment,5],['floor models',floorModels,10],['landmarks v31',landmarksV31,6],['world pointers',worldPointers,11]]){assert.equal(list.length,count,`${name}: count`);assert.equal(new Set(list).size,count,`${name}: duplicate ID`);}
 const required=[...models.flatMap(id=>[modelFile(id),propFile(id)]),...icons.map(iconFile),...selectors.flatMap(id=>[guiFile(id),itemFile(id)]),...worldPointers.map(itemFile),atlasFile];
 const missing=required.filter(file=>!fs.existsSync(file));assert.equal(missing.length,0,`Missing ${missing.length} required asset files:\n${missing.map(file=>path.relative(ROOT,file)).join('\n')}`);
 const result={timestamp:new Date().toISOString(),target,baselineCount:baselineIcons.length,models:[],icons:[],checks:[]};
 result.atlas={count:validateAtlas(read(atlasFile)).length,exactSet:true,duplicates:0};
 for(const id of models){const m=read(modelFile(id)),floor=validateModel(m,id),image=validatePng(propFile(id),atlasSizes[id]);result.models.push({id,elements:m.elements.length,faces:m.elements.reduce((sum,e)=>sum+Object.keys(e.faces).length,0),...image,...(floor?{floor}:{})});}
 for(const id of icons)result.icons.push({id,...validatePng(iconFile(id),32)});
 for(const id of selectors){validateSelect(read(itemFile(id)),id);validateGui(read(guiFile(id)),id);}
 for(const id of worldPointers)validatePointer(read(itemFile(id)),id);
 result.landmarkMounts=landmarksV31.map(id=>({id,...validateMount(read(modelFile(id)),id)}));
 result.preservation=validatePreservation();result.v3Preservation=validateV3Preservation();result.checks=testValidator();result.guiSelectors=selectors.length;result.environmentPointers=worldPointers.length;result.itemDefinitions=selectors.length+worldPointers.length;result.overrideFileCount=required.length;
 result.pure2DModelOwnership={owner:'SOL / PackBuilder.java',count:pureIcons.length,ids:pureIcons,validation:'PNG masters checked here; generated item models and pointers are a downstream packaging check'};
 result.status='PASS';return result;
}
let result;
try{result=main();console.log(`PASS: ${models.length} 3D models, ${icons.length} RGBA32 icons, ${selectors.length} GUI/world selectors, ${worldPointers.length} world pointers.`);console.log(`Exact ${result.atlas.count}-sprite atlas, ${result.v3Preservation.preservedFiles} preserved v3 files, ${result.checks.length} fault-injection cases, ${result.overrideFileCount} delivered files.`);console.log(result.models.map(m=>`${m.id}: ${m.elements} cuboids/${m.faces} faces`).join('\n'));}
catch(error){result={timestamp:new Date().toISOString(),target,status:'FAIL',error:error.message};console.error(error.stack??error);process.exitCode=1;}
fs.mkdirSync(REVIEW,{recursive:true});fs.writeFileSync(path.join(REVIEW,'static-validation-v31.json'),JSON.stringify(result,null,2)+'\n');
