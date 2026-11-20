// Exact OBB separating-axis checks of the revised arm/wing parts against head geometry.
// This verifies solid-volume overlap only; the saved-scene visual review remains required.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {VOID_RIGS} from '../../../sources/monsters-void.mjs';
import {OUT} from '../../../tools/asset-kit.mjs';
const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0),sub=(a,b)=>a.map((v,i)=>v-b[i]),add=(a,b)=>a.map((v,i)=>v+b[i]);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const I=[[1,0,0],[0,1,0],[0,0,1]],apply=(R,v)=>R.map(row=>dot(row,v));
function rotation(axis,a){a*=Math.PI/180;let c=Math.cos(a),s=Math.sin(a);return axis==='x'?[[1,0,0],[0,c,-s],[0,s,c]]:axis==='y'?[[c,0,s],[0,1,0],[-s,0,c]]:[[c,-s,0],[s,c,0],[0,0,1]];}
function obb(e,d=[0,8,0],pitch=0){
 const R=e.rotation?rotation(e.rotation.axis,e.rotation.angle):I,Q=rotation('x',-pitch),o=e.rotation?.origin??[0,0,0];
 let c=e.from.map((v,i)=>(v+e.to[i])/2);c=add(o,apply(R,sub(c,o)));c=add(d,apply(Q,sub(c,[8,8,8])));
 return {name:e.name,c,h:e.from.map((v,i)=>(e.to[i]-v)/2),u:[0,1,2].map(i=>apply(Q,R.map(row=>row[i])))};
}
function overlap(a,b){
 const axes=[...a.u,...b.u,...a.u.flatMap(u=>b.u.map(v=>cross(u,v)))],d=sub(b.c,a.c);
 for(let axis of axes){const n=Math.hypot(...axis);if(n<1e-8)continue;axis=axis.map(v=>v/n);
  const ra=a.h.reduce((s,h,i)=>s+h*Math.abs(dot(a.u[i],axis)),0),rb=b.h.reduce((s,h,i)=>s+h*Math.abs(dot(b.u[i],axis)),0);
  if(Math.abs(dot(d,axis))>=ra+rb-1e-5)return false;
 }return true;
}
const read=ref=>JSON.parse(fs.readFileSync(path.join(OUT,'models/item',ref.split(':')[1]+'.json')));
const records=[];
for(const [id,rig] of Object.entries(VOID_RIGS)){
 const role=rig.sockets.arm?'arm':'weapon',part=read(rig.sockets[role].model);
 const targets=[...read(rig.rootModel).elements.map(e=>({...obb(e),part:'root'})),...(rig.sockets.head?read(rig.sockets.head.model).elements.map(e=>({...obb(e,rig.sockets.head.offset),part:'head'})):[])];
 for(const pitch of [-70,-65,-60,0,55]){
  const parts=part.elements.map(e=>obb(e,rig.sockets[role].offset,pitch)),hits=[];
  const minY=Math.min(...parts.map(p=>p.c[1]-p.h.reduce((s,h,i)=>s+h*Math.abs(p.u[i][1]),0)));
  for(const a of parts)for(const b of targets)if(overlap(a,b))hits.push({moving:a.name,target:b.name,targetPart:b.part});
  records.push({id,posePitch:pitch,minYModelUnits:+minY.toFixed(6),hits});
 }
}
fs.writeFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)),'raw-overlaps.json'),JSON.stringify({method:'Actual cuboid OBB SAT, element rotations then Rx(-semanticPitch); all moving cuboids vs all root/head cuboids, no exemptions yet.',records},null,2)+'\n');
const family=n=>n.replace(/[-]?\d+(\.\d+)?/g,'#');
for(const r of records){const pairs={};for(const h of r.hits){const k=family(h.moving)+' -> '+family(h.target);pairs[k]=(pairs[k]??0)+1;}console.log(JSON.stringify({id:r.id,pitch:r.posePitch,hits:r.hits.length,minY:r.minYModelUnits,pairs}));}
