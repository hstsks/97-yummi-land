/** Fixed-scope Chapter III clearance audit: six rigs at five semantic X pitches.
 * Checks every moving cuboid against every root/head cuboid with 15-axis OBB SAT.
 * Importing this module is read-only; direct execution writes review/void-clearance.json.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {VOID_RIGS} from '../sources/monsters-void.mjs';

const production=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const assetRoot=path.resolve(production,'../../resourcepack-gen/overrides/assets/yuumi97');
const filename=ref=>path.join(assetRoot,'models/item',ref.split(':')[1]+'.json');
export const pitches=[-70,-65,-60,0,55];
const tolerance=1e-5;
const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0);
const sub=(a,b)=>a.map((v,i)=>v-b[i]);
const add=(a,b)=>a.map((v,i)=>v+b[i]);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const identity=[[1,0,0],[0,1,0],[0,0,1]];
const apply=(r,v)=>r.map(row=>dot(row,v));
function rotation(axis,angle){
 const a=angle*Math.PI/180,c=Math.cos(a),s=Math.sin(a);
 return axis==='x'?[[1,0,0],[0,c,-s],[0,s,c]]:axis==='y'?[[c,0,s],[0,1,0],[-s,0,c]]:[[c,-s,0],[s,c,0],[0,0,1]];
}
function box(e,offset=[0,8,0],pitch=0){
 if(e.rotation?.rescale)throw Error('Unsupported rescale in '+e.name);
 const r=e.rotation?rotation(e.rotation.axis,e.rotation.angle):identity,q=rotation('x',-pitch),o=e.rotation?.origin??[0,0,0];
 let center=e.from.map((v,i)=>(v+e.to[i])/2);
 center=add(o,apply(r,sub(center,o)));
 center=add(offset,apply(q,sub(center,[8,8,8])));
 const axes=[0,1,2].map(i=>apply(q,r.map(row=>row[i]))),half=e.from.map((v,i)=>(e.to[i]-v)/2);
 const extent=[0,1,2].map(k=>half.reduce((n,h,i)=>n+h*Math.abs(axes[i][k]),0));
 return {name:e.name,center,axes,half,extent,minY:center[1]-extent[1]};
}
function depth(a,b){
 const delta=sub(b.center,a.center);
 for(let k=0;k<3;k++)if(Math.abs(delta[k])>=a.extent[k]+b.extent[k]-tolerance)return 0;
 let minimum=Infinity;
 for(let axis of [...a.axes,...b.axes,...a.axes.flatMap(u=>b.axes.map(v=>cross(u,v)))]){
  const n=Math.hypot(...axis);if(n<1e-8)continue;axis=axis.map(v=>v/n);
  const radius=b=>b.half.reduce((sum,h,i)=>sum+h*Math.abs(dot(b.axes[i],axis)),0);
  const overlap=radius(a)+radius(b)-Math.abs(dot(delta,axis));
  if(overlap<=tolerance)return 0;minimum=Math.min(minimum,overlap);
 }
 return minimum;
}

// Bounded named attachment regions only. No blade, book, cup, orb, free tentacle,
// wing panel, hand or distal forearm is exempt. All matches are preserved in the report.
const joins={
 mob_mirror_stalker:[
  ['shoulder-bearing',/^(mantis_hinge_|upper_exoskeleton_-?1_[0-4]$|white_upper_plate_-?1_[0-3]$)/,/^(fixed_shoulder_socket_|overlapping_shoulder_)/]
 ],
 mob_void_choir:[
  ['sleeve-bearing',/^(sleeve_hinge_|ivory_bell_sleeve_root_-?1_[0-3]$)/,/^(body_choir_socket_|white_outer_stole_)/],
  ['hinge-inner-flange',/^sleeve_hinge_-?1_sleeve_(left|right)$/,/^high_priest_torso_(left|right)$/],
  ['halo-shoulder-mount',/^halo_back_support_-?1_[0-6]$/,/^(body_choir_socket_|white_outer_stole_)/]
 ],
 mob_abyssal_oculus:[
  ['central-rear-axle',/^six_tentacle_anchor_beam$/,/^(vertical_void_eyeball_|eye_side_clamp_|rear_tentacle_socket_beam$|violet_rear_shell_)/]
 ],
 mob_eclipse_templar:[
  ['pauldron-bearing',/^(knight_shoulder_hinge_|armored_upper_arm_-?1_[0-6]$)/,/^(knight_fixed_shoulder_|pauldron_outer_|right_corruption_spire_)/],
  ['shoulder-inner-flange',/^(knight_shoulder_hinge_-?1_sleeve_(left|right)|armored_upper_arm_-?1_[01])$/,/^(marble_left_chest|corrupted_right_chest)$/]
 ],
 boss_twin_archons:[
  ['royal-pauldron-bearing',/^(archon_shoulder_hinge_|archon_forearm_-?1_[0-8]$|wing_shoulder_strut_(root_)?-?1_[0-4]$)/,/^(twin_pauldron_|layered_pauldron_fillet_)/],
  ['shoulder-inner-flange',/^(archon_shoulder_hinge_-?1_sleeve_(left|right)|archon_forearm_-?1_0)$/,/^twin_breastplate_/]
 ],
 boss_eclipsia:[
  ['cathedral-pauldron-bearing',/^(eclipsia_shoulder_hinge_|ivory_arm_lateral_joint_-?1_[0-5]$|ivory_upper_arm_-?1_[0-4]$|stained_glass_wing_[1-6]_mount_(root|strut)_[0-5]$)/,/^royal_shoulder_socket_/],
  ['finial-bearing-base',/^eclipsia_shoulder_hinge_-?1_(sleeve_middle|front_disc)$/,/^shoulder_cathedral_finial_-?1_facet[01]$/]
 ]
};
export function intendedJoin(id,moving,fixed){
 return joins[id].find(([,a,b])=>a.test(moving)&&b.test(fixed))?.[0]??null;
}
export function auditVoid(rigs=VOID_RIGS,read=ref=>JSON.parse(fs.readFileSync(filename(ref))).elements){
 const records=[];
 for(const [id,rig]of Object.entries(rigs)){
  const targets=read(rig.rootModel).map(e=>({...box(e),part:'root'}));
  if(rig.sockets.head)targets.push(...read(rig.sockets.head.model).map(e=>({...box(e,rig.sockets.head.offset),part:'head'})));
  const socket=rig.sockets.arm??rig.sockets.weapon;
  for(const pitch of pitches){
   const moving=read(socket.model).map(e=>box(e,socket.offset,pitch)),intersections=[],intentionalJointIntersections=[];
   for(const a of moving)for(const b of targets){
    const penetration=depth(a,b);if(!penetration)continue;
    const hit={moving:a.name,fixed:b.name,fixedPart:b.part,penetrationModelUnits:+penetration.toFixed(6)};
    const reason=b.part==='root'?intendedJoin(id,a.name,b.name):null;
    if(reason)intentionalJointIntersections.push({...hit,reason});else intersections.push(hit);
   }
   records.push({id,pitch,physicalLocalX:-pitch,minimumYModelUnits:+Math.min(...moving.map(a=>a.minY)).toFixed(6),testedPairs:moving.length*targets.length,intersections,intentionalJointIntersections});
  }
 }
 const failures=records.filter(r=>r.intersections.length||r.minimumYModelUnits< -tolerance).length;
 return {status:failures?'FAIL':'PASS',method:'All moving cuboids vs all root/head cuboids, exact 15-axis OBB SAT with element rotations then Rx(-semanticPitch); tolerance 0.00001 model unit. Fixed five-pose audit; no interpolated sweep, Minecraft rendering or entity collision simulation.',pitches,joinExceptions:Object.fromEntries(Object.entries(joins).map(([id,rules])=>[id,rules.map(([reason,moving,fixed])=>({reason,moving:moving.source,fixed:fixed.source}))])),records,failures};
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const manifestPath=path.join(assetRoot,'monster_rigs.json'),manifest=JSON.parse(fs.readFileSync(manifestPath));
 if(manifest.unitsPerBlock!==16||manifest.posePitchToLocalX!==-1||JSON.stringify(manifest.modelOrigin)!=='[8,8,8]'||JSON.stringify(manifest.rootAnchor)!=='[8,0,8]')throw Error('Manifest transform convention changed.');
 for(const [id,rig]of Object.entries(VOID_RIGS))if(JSON.stringify(rig)!==JSON.stringify(manifest.rigs[id]))throw Error('Generator/consumer rig mismatch: '+id);
 const report=auditVoid();
 const refs=Object.values(VOID_RIGS).flatMap(r=>[r.rootModel,...Object.values(r.sockets).map(s=>s.model)]);
 const evidence=[manifestPath,path.join(production,'sources/monsters-void.mjs'),fileURLToPath(import.meta.url),...refs.map(filename),...refs.map(ref=>path.join(assetRoot,'textures/prop',ref.split(':')[1]+'.png'))];
 report.sourceSha256=Object.fromEntries(evidence.map(f=>[f,crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex')]));
 fs.writeFileSync(path.join(production,'review/void-clearance.json'),JSON.stringify(report,null,2)+'\n');
 console.log(JSON.stringify({status:report.status,checks:report.records.length,failures:report.failures,unexpectedIntersections:report.records.reduce((n,r)=>n+r.intersections.length,0),minimumYModelUnits:Math.min(...report.records.map(r=>r.minimumYModelUnits))}));
 process.exitCode=report.failures?1:0;
}
