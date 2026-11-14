import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import zlib from 'node:zlib';
import {ROOT,OUT,REVIEW} from './asset-kit.mjs';
const baseline=[...fs.readFileSync(path.join(ROOT,'resourcepack-gen/PackBuilder.java'),'utf8').matchAll(/register\(m, "([a-z0-9_]+)"/g)].map(x=>x[1]);
const models=['prop_yuumi_statue','prop_slot_cabinet','prop_roulette_wheel','prop_derby_trophy','prowling_wand','book_of_thresholds','you_and_me_bell','hat_yuumi_ears','plushie_yuumi'];
const relics=['prowling_wand','book_of_thresholds','you_and_me_bell'];
const contexts=['gui','ground','fixed','head','firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand'];
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
function texturePath(ref){const [ns,id]=ref.split(':');assert.equal(ns,'yuumi97');assert(!id.includes('..'));return path.join(OUT,'textures',id+'.png');}
function validateModel(m,id,exists=fs.existsSync){
 assert(m.textures?.particle,`${id}: particle missing`);
 for(const ref of Object.values(m.textures))assert(exists(texturePath(ref)),`${id}: missing texture ${ref}`);
 assert(m.elements?.length>0,`${id}: empty model`);
 for(const context of contexts){const d=m.display?.[context];assert(d,`${id}: ${context} missing`);for(const key of ['rotation','translation','scale'])assert(d[key]?.length===3&&d[key].every(Number.isFinite),`${id}: bad display ${context}/${key}`);assert(d.scale.every(v=>v>0&&v<=4),`${id}: bad display scale`);}
 for(const e of m.elements){
  assert(e.name,`${id}: unnamed element`);
  for(const p of ['from','to'])assert(e[p]?.length===3&&e[p].every(v=>Number.isFinite(v)&&v>=-16&&v<=32),`${id}/${e.name}: bounds`);
  assert(e.to.every((v,i)=>v>e.from[i]),`${id}/${e.name}: zero/negative volume`);
  if(e.rotation){assert(['x','y','z'].includes(e.rotation.axis));assert([-45,-22.5,0,22.5,45].includes(e.rotation.angle),`${id}/${e.name}: rotation`);assert(e.rotation.origin?.length===3&&e.rotation.origin.every(Number.isFinite));}
  assert(Object.keys(e.faces).length>0);
  for(const [face,f]of Object.entries(e.faces)){assert(['north','south','east','west','up','down'].includes(face));assert(f.uv?.length===4&&f.uv.every(v=>Number.isFinite(v)&&v>=0&&v<=16),`${id}/${e.name}: UV`);assert(f.uv[2]>f.uv[0]&&f.uv[3]>f.uv[1],`${id}/${e.name}: UV area`);assert([0,90,180,270].includes(f.rotation??0));assert(f.texture?.startsWith('#')&&m.textures[f.texture.slice(1)],`${id}: texture variable`);}
 }
}
function validateSelect(m,id,exists=fs.existsSync){
 const select=m.model;assert.equal(select.type,'minecraft:select');assert.equal(select.property,'minecraft:display_context');
 assert.equal(select.cases.length,1);assert.equal(select.cases[0].when,'gui');
 assert.equal(select.cases[0].model.model,`yuumi97:gui/${id}`);assert.equal(select.fallback.model,`yuumi97:item/${id}`);
 for(const branch of [select.cases[0].model,select.fallback]){assert.equal(branch.type,'minecraft:model');assert(exists(path.join(OUT,'models',branch.model.split(':')[1]+'.json')),`${id}: select reference`);}
}
function validatePng(file,size){
 const b=fs.readFileSync(file);assert.equal(b.subarray(0,8).toString('hex'),'89504e470d0a1a0a');assert.equal(b.readUInt32BE(16),size);assert.equal(b.readUInt32BE(20),size);assert.equal(b[24],8);assert.equal(b[25],6,'RGBA required');
 const parts=[];for(let p=8;p<b.length;){const n=b.readUInt32BE(p),type=b.toString('ascii',p+4,p+8);if(type==='IDAT')parts.push(b.subarray(p+8,p+8+n));p+=12+n;}
 const raw=zlib.inflateSync(Buffer.concat(parts));assert.equal(raw.length,(size*4+1)*size);
 let visible=0,transparent=0;const colors=new Set();for(let y=0;y<size;y++){assert.equal(raw[y*(size*4+1)],0,'Asset generator filter contract');for(let x=0;x<size;x++){const p=y*(size*4+1)+1+x*4;if(raw[p+3]>0){visible++;colors.add(raw.subarray(p,p+4).toString('hex'));}else transparent++;}}
 assert(visible>0,`${file}: blank`);assert(colors.size>=4,`${file}: insufficient palette variation`);
 if(size===32)assert(transparent>0,'Icons require transparent outside silhouette');
 return {size,visiblePixels:visible,colors:colors.size,bytes:b.length};
}
// Fault injection verifies these checks reject the failures the pack builder cannot detect.
function testValidator(){const good=read(path.join(OUT,'models/item/prop_yuumi_statue.json'));
 const badCoord=structuredClone(good);badCoord.elements[0].from[0]=-17;assert.throws(()=>validateModel(badCoord,'fault-bound',()=>true));
 assert.throws(()=>validateModel(good,'fault-texture',()=>false));
 const badAngle=structuredClone(good);badAngle.elements[0].rotation={axis:'x',angle:17,origin:[8,8,8]};assert.throws(()=>validateModel(badAngle,'fault-angle',()=>true));
 const sel={model:{type:'minecraft:select',property:'minecraft:display_context',cases:[{when:'gui',model:{type:'minecraft:model',model:'yuumi97:gui/prowling_wand'}}],fallback:{type:'minecraft:model',model:'yuumi97:item/prowling_wand'}}};
 assert.throws(()=>validateSelect(sel,'prowling_wand',()=>false));return ['invalid coordinate rejected','missing texture rejected','illegal rotation rejected','missing GUI model rejected'];}
const result={timestamp:new Date().toISOString(),target:'Minecraft Java 1.21.4 / pack_format 46',baselineCount:baseline.length,models:[],icons:[],checks:[]};
assert.equal(baseline.length,27);
const atlas=read(path.join(OUT,'../minecraft/atlases/blocks.json'));
const atlasSprites=new Set(atlas.sources.filter(s=>s.type==='minecraft:single').map(s=>s.resource));
for(const id of models)assert(atlasSprites.has(`yuumi97:prop/${id}`),`${id}: prop texture absent from blocks atlas`);
for(const id of models){const m=read(path.join(OUT,'models/item',id+'.json'));validateModel(m,id);const image=validatePng(path.join(OUT,'textures/prop',id+'.png'),id.startsWith('prop_')?128:64);result.models.push({id,elements:m.elements.length,faces:m.elements.reduce((s,e)=>s+Object.keys(e.faces).length,0),...image});}
for(const id of baseline)result.icons.push({id,...validatePng(path.join(OUT,'textures/item',id+'.png'),32)});
for(const id of relics){validateSelect(read(path.join(OUT,'items',id+'.json')),id);const gui=read(path.join(OUT,'models/gui',id+'.json'));assert.equal(gui.parent,'minecraft:item/generated');assert.equal(gui.textures.layer0,`yuumi97:item/${id}`);}
result.checks=testValidator();result.checks.push('All 9 prop textures registered in the Minecraft blocks atlas');result.itemDefinitions=33;result.status='PASS';
fs.mkdirSync(REVIEW,{recursive:true});fs.writeFileSync(path.join(REVIEW,'static-validation.json'),JSON.stringify(result,null,2)+'\n');
console.log(`PASS: ${models.length} 3D models, ${baseline.length} RGBA32 icons, ${relics.length} GUI/world selectors. 4 fault-injection cases and atlas registration passed.`);
console.log(result.models.map(m=>`${m.id}: ${m.elements} cuboids/${m.faces} faces`).join('\n'));
