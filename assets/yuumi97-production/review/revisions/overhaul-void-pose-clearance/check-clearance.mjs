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
for(const id of ['mob_void_choir','boss_twin_archons','boss_eclipsia']){
 const rig=VOID_RIGS[id],role=rig.sockets.arm?'arm':'weapon',part=read(rig.sockets[role].model);
 const targets=role==='arm'?read(rig.rootModel).elements.filter(e=>/hood|mask/.test(e.name)).map(e=>obb(e)):
  read(rig.sockets.head.model).elements.map(e=>obb(e,rig.sockets.head.offset));
 for(const pitch of [-70,-65,-60,0,55]){
  const parts=part.elements.map(e=>obb(e,rig.sockets[role].offset,pitch));
  const minY=Math.min(...parts.map(p=>p.c[1]-p.h.reduce((s,h,i)=>s+h*Math.abs(p.u[i][1]),0))),hits=[];
  for(const a of parts)for(const b of targets)if(overlap(a,b))hits.push([a.name,b.name]);
  records.push({id,posePitch:pitch,physicalLocalX:-pitch,minYModelUnits:+minY.toFixed(6),headIntersections:hits});
 }
}
const failures=records.filter(r=>r.headIntersections.length||r.minYModelUnits<0);
fs.writeFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)),'clearance.json'),JSON.stringify({method:'OBB SAT against actual named cuboids; element rotations + semantic pitch mapping; not a rendered/client collision test',records,failures:failures.length},null,2)+'\n');
console.log(JSON.stringify({checks:records.length,failures:failures.length,details:failures}));if(failures.length)process.exitCode=1;
