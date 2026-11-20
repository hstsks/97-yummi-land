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
 for(let k=0;k<3;k++){const ah=a.h.reduce((v,h,i)=>v+h*Math.abs(a.u[i][k]),0),bh=b.h.reduce((v,h,i)=>v+h*Math.abs(b.u[i][k]),0);if(Math.abs(a.c[k]-b.c[k])>=ah+bh-1e-5)return false;}
 const axes=[...a.u,...b.u,...a.u.flatMap(u=>b.u.map(v=>cross(u,v)))],d=sub(b.c,a.c);
 for(let axis of axes){const n=Math.hypot(...axis);if(n<1e-8)continue;axis=axis.map(v=>v/n);
  const ra=a.h.reduce((s,h,i)=>s+h*Math.abs(dot(a.u[i],axis)),0),rb=b.h.reduce((s,h,i)=>s+h*Math.abs(dot(b.u[i],axis)),0);
  if(Math.abs(dot(d,axis))>=ra+rb-1e-5)return false;
 }return true;
}
const read=ref=>JSON.parse(fs.readFileSync(path.join(OUT,'models/item',ref.split(':')[1]+'.json')));

const specs=[['mob_void_choir',/^(choir_book|open_choir|chalice|purple_cuff|silver_support_hand)/],['mob_eclipse_templar',/^(eclipse_grip|grip_|platinum_crossguard|crossguard_|void_greatsword|blade_|platinum_blade|weapon_|twohand_gauntlet)/],['boss_eclipsia',/^(void_scythe|scythe_shaft|scythe_grip|huge_crescent|scythe_pommel|scythe_holding_hand)/]];
for(const [id,re] of specs){const rig=VOID_RIGS[id],role=rig.sockets.arm?'arm':'weapon',part=read(rig.sockets[role].model).elements.filter(e=>re.test(e.name));const targets=[...read(rig.rootModel).elements.map(e=>obb(e)),...(rig.sockets.head?read(rig.sockets.head.model).elements.map(e=>obb(e,rig.sockets.head.offset)):[])];const candidates=[];
for(let dy=0;dy<=8;dy+=.5)for(let dz=0;dz>=-9;dz-=.5){let hits=0,minY=99,bounds=true;for(const pitch of [-70,-65,-60,0,55])for(const e of part){const t=structuredClone(e);for(const key of ['from','to']){t[key][1]+=dy;t[key][2]+=dz;}if(t.rotation){t.rotation.origin[1]+=dy;t.rotation.origin[2]+=dz;}if(t.from.some(v=>v< -16)||t.to.some(v=>v>32))bounds=false;const a=obb(t,rig.sockets[role].offset,pitch);minY=Math.min(minY,a.c[1]-a.h.reduce((s,h,i)=>s+h*Math.abs(a.u[i][1]),0));for(const b of targets)if(overlap(a,b))hits++;}if(bounds&&minY>=0)candidates.push({dy,dz,hits,minY,cost:dy*dy+dz*dz});}
candidates.sort((a,b)=>a.hits-b.hits||a.cost-b.cost);console.log(id,JSON.stringify(candidates.slice(0,8)));}
