import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {buildHub} from './designs/hub.mjs';
import {buildDungeons} from './designs/dungeons.mjs';
import {encodeStructure} from './lib/nbt.mjs';
const base=path.dirname(fileURLToPath(import.meta.url));
const output=path.resolve(base,'../../yuumi-core/src/main/resources/structures/overhaul');
fs.mkdirSync(output,{recursive:true});
const voxels=[...buildHub(),...buildDungeons()];
if(voxels.length!==20||new Set(voxels.map(v=>v.id)).size!==20)throw new Error('Expected exactly 20 unique structures');
const structures=[];
for(const v of voxels){
  const bytes=encodeStructure(v),file=`${v.id}.nbt`,counts={};
  for(const cell of v.cells)counts[cell.Name]=(counts[cell.Name]??0)+1;
  const anchors=(v.meta.anchors??[]).map(a=>({...a,world:a.local.map((n,i)=>n+v.origin[i])}));
  const seams=v.id.startsWith('ch')?[
    ...(!v.id.endsWith('zone6')?[{face:'north',z:0,x:[(v.W-1)/2-4,(v.W-1)/2+4],floorY:6,airY:[7,14]}]:[]),
    ...(!v.id.endsWith('zone1')?[{face:'south',z:v.L-1,x:[(v.W-1)/2-4,(v.W-1)/2+4],floorY:6,airY:[7,14]}]:[])
  ]:[];
  fs.writeFileSync(path.join(output,file),bytes);
  structures.push({...v.meta,id:v.id,file,size:v.size,origin:v.origin,originMode:v.id.startsWith('ch')?'slot-relative-x':'absolute',world:v.id.startsWith('ch')?'yuumi_dungeon':'yuumi_hub',sha256:createHash('sha256').update(bytes).digest('hex'),bytes:bytes.length,blockCount:v.cells.length,counts,anchors,seams});
  console.log(`${file} ${v.size.join('x')} ${bytes.length} bytes`);
}
const manifest={minecraftVersion:'1.21.4',dataVersion:4189,slotStride:1024,coordinateConvention:'Integer positions are block cells. entityFeet anchors give standing feet; X/Z may use +0.5 cell centers. Dungeon origin X is relative to cx=slot*1024. No rotation or mirroring.',decisions:{roseWindow:{diameter:19,localY:[11,29],basis:'User-approved circular window'},balcony:{floorWorldY:74,feetWorldY:75},sanctuary:{floorWorldY:67,feetWorldY:68},runtime:'NBT contains no entities or closed dynamic gates. Stream03 attaches interactions and gates.'},structures};
fs.writeFileSync(path.join(output,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(`Compiled ${structures.length} structures / ${structures.reduce((sum,s)=>sum+s.blockCount,0)} explicit cells`);
