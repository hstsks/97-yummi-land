// Exact oriented cuboid checks; imports and auditAstral() do not write files.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {box,depth} from './check-foundry-clearance.mjs';
import {ASTRAL_RIGS} from '../sources/monsters-astral.mjs';
const production=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const directory=path.resolve(production,'../../resourcepack-gen/overrides/assets/yuumi97/models/item');
const filename=ref=>path.join(directory,ref.split(':')[1]+'.json');
export const pitches=[-70,-65,-60,0,55];
// Only the named bearing surfaces are allowed to overlap; weapon ornaments are not joints.
export function intendedJoin(id,moving,fixed){
 if(id==='mob_grimoire_mimic')return /^(upper_jaw_hinge_axle|back_hinge_pier_left|back_hinge_pier_right)$/.test(fixed);
 if(id==='mob_clockwork_sentinel')return /^(right_shoulder_rotor_|upper_brass_arm)/.test(moving)&&/^right_shoulder_(socket|yoke)/.test(fixed);
 if(id==='mob_ink_librarian')return moving==='sleeve_shoulder_coaxial_spindle'||(/^(sleeve_shoulder_|(right|left)_upper_bone_sleeve)/.test(moving)&&/^shoulder_sleeve_collar_/.test(fixed));
 if(id==='mob_nebula_golem')return /^golem_shoulders_/.test(moving)||(/^(right|left)_stone_upper_arm_/.test(moving)&&/^(right_offset_stone_shoulder|left_book_bound_shoulder|(right|left)_shoulder_axle_collar)$/.test(fixed));
 if(id==='boss_archivist_astro')return /^(orbit_action_pivot|orbit_pivot_endcap|side_orbit_spindle_)/.test(moving)&&/^(rear_orbit_spindle|rear_spindle_crown|raised_collar_rear|high_shoulders_rear)$/.test(fixed);
 if(id==='boss_novarion')return /^(armillary_rotation_pivot|armillary_rear_spindle|lance_arm_rear_shoulder_link)$/.test(moving)&&/^armillary_rear_(axle|pylon)$/.test(fixed);
 return false;
}
export function auditAstral(rigs=ASTRAL_RIGS,read=ref=>JSON.parse(fs.readFileSync(filename(ref))).elements){
 const records=[];
 for(const[id,rig]of Object.entries(rigs)){
  const fixed=read(rig.rootModel).map(e=>({...box(e),part:'root'}));
  if(rig.sockets.head)fixed.push(...read(rig.sockets.head.model).map(e=>({...box(e,rig.sockets.head.offset,0),part:'head'})));
  const socket=rig.sockets.arm??rig.sockets.weapon;
  for(const pitch of pitches){
   const moving=read(socket.model).map(e=>box(e,socket.offset,pitch)),intersections=[],intentionalJointIntersections=[];
   for(const a of moving)for(const b of fixed){const penetration=depth(a,b);if(!penetration)continue;
    const hit={moving:a.name,fixed:b.name,fixedPart:b.part,penetrationModelUnits:+penetration.toFixed(6)};
    (intendedJoin(id,a.name,b.name)?intentionalJointIntersections:intersections).push(hit);
   }
   records.push({id,pitch,minimumYModelUnits:+Math.min(...moving.map(b=>b.minY)).toFixed(6),intersections,intentionalJointIntersections});
  }
 }
 const failures=records.filter(r=>r.intersections.length||r.minimumYModelUnits<0).length;
 return {status:failures?'FAIL':'PASS',method:'Exact 15-axis OBB SAT with authored element rotations and Rx(-semanticPitch); overlap tolerance 0.01 model unit. All moving cuboids checked against root and head. Named shoulder/neck/hinge/bearing joins explicitly listed separately. No Minecraft or physics simulation.',pitches,records,failures};
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const report=auditAstral();
 const evidence=[...Object.values(ASTRAL_RIGS).flatMap(r=>[r.rootModel,...Object.values(r.sockets).map(s=>s.model)]).map(filename),path.join(production,'sources/monsters-astral.mjs'),fileURLToPath(import.meta.url),path.join(production,'tools/check-foundry-clearance.mjs'),path.resolve(directory,'../../monster_rigs.json')];
 report.sourceSha256=Object.fromEntries(evidence.map(file=>[file,crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')]));
 fs.writeFileSync(path.join(production,'review/astral-clearance.json'),JSON.stringify(report,null,2)+'\n');
 console.log(JSON.stringify({checks:report.records.length,failures:report.failures,poses:report.records.map(r=>({id:r.id,pitch:r.pitch,minimumYModelUnits:r.minimumYModelUnits,intersections:r.intersections.length}))},null,2));
 process.exitCode=report.failures?1:0;
}
