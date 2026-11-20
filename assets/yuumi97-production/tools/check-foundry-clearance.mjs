/** Read-only geometry test of the six foundry rigs. Writes only its QA report.
 * Usage: node assets/yuumi97-production/tools/check-foundry-clearance.mjs
 * Exact 15-axis OBB SAT tests use each cuboid's legal element rotation followed
 * by the bone pitch. Joint overlaps are reported separately, not silently passed.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {FOUNDRY_RIGS} from '../sources/monsters-foundry.mjs';
const base=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const modelDir=path.resolve(base,'../../resourcepack-gen/overrides/assets/yuumi97/models/item');
const add=(a,b)=>a.map((v,i)=>v+b[i]);
const sub=(a,b)=>a.map((v,i)=>v-b[i]);
const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
function rotate(v,axis,degrees){const a=degrees*Math.PI/180,c=Math.cos(a),s=Math.sin(a),[x,y,z]=v;return axis==='x'?[x,y*c-z*s,y*s+z*c]:axis==='y'?[x*c+z*s,y,-x*s+z*c]:[x*c-y*s,x*s+y*c,z];}
export function box(e,offset,pitch){
  let center=e.from.map((v,i)=>(v+e.to[i])/2),axes=[[1,0,0],[0,1,0],[0,0,1]];
  if(e.rotation){const r=e.rotation;center=add(rotate(sub(center,r.origin),r.axis,r.angle),r.origin);axes=axes.map(v=>rotate(v,r.axis,r.angle));}
  if(offset){center=add(add(rotate(sub(center,[8,8,8]),'x',-pitch),offset),[8,0,8]);axes=axes.map(v=>rotate(v,'x',-pitch));}
  const half=e.from.map((v,i)=>(e.to[i]-v)/2);
  return {name:e.name,center,axes,half,minY:center[1]-axes.reduce((s,a,i)=>s+Math.abs(a[1])*half[i],0)};
}
export function depth(a,b){
  let minimum=Infinity;
  for(const raw of [...a.axes,...b.axes,...a.axes.flatMap(x=>b.axes.map(y=>cross(x,y)))]){
    const len=Math.sqrt(dot(raw,raw));if(len<1e-8)continue;
    const axis=raw.map(v=>v/len),r1=a.axes.reduce((s,v,i)=>s+Math.abs(dot(v,axis))*a.half[i],0),r2=b.axes.reduce((s,v,i)=>s+Math.abs(dot(v,axis))*b.half[i],0);
    const overlap=r1+r2-Math.abs(dot(sub(a.center,b.center),axis));if(overlap<=.01)return 0;minimum=Math.min(minimum,overlap);
  }return minimum;
}
// This allowlist names only load-bearing joints. Forearms, fists and tools
// receive no blanket exemption. New/renamed cuboids fail unless reviewed here.
function jointRules(id){
  const rules=[],oct=n=>['center','left','right'].map(s=>n+'_'+s);
  const allow=(moving,fixed,reason)=>rules.push({moving:[moving].flat(),fixed:[fixed].flat(),reason});
  const spindle='Coaxial internal load-bearing spindle seated in torso and shoulder bearings.';
  const bearing='Rotating bearing and fixed shoulder housing intentionally overlap at the attachment.';
  const upper='Proximal upper-arm sleeve is inserted into the fixed armored shoulder housing.';
  const elbow='Compact elbow bearing sits inside the shoulder guard; the exterior forearm is clear.';
  if(id==='mob_cinder_imp'){
    allow('shoulder_coaxial_shoulder_spindle',[...oct('imp_stocky_torso'),...oct('imp_cheeky_head'),'imp_neck','apron_shoulder_strap_-1','apron_shoulder_strap_1','shoulder_socket_-1','shoulder_socket_1'],spindle);
    for(const [s,side,tool]of[[-1,'left','hammer'],[1,'right','tongs']]){
      allow(['shoulder_socket_'+s,'shoulder_pin_'+s],['imp_stocky_torso_'+side,'imp_cheeky_head_'+side,'bat_wing_root_'+s,'shoulder_socket_'+s],bearing);
      allow(tool+'_upper_arm','shoulder_socket_'+s,upper);
    }
  }
  if(id==='mob_slag_hound'){
    allow('jaw_hinge_coaxial_shoulder_spindle','hound_jaw_socket',spindle);
    allow(['movable_lower_jaw','jaw_hot_throat'],'hound_jaw_socket','Rear of the movable jaw is physically captured by the fixed transverse jaw bearing.');
    for(const s of[-1,1]){
      allow(['jaw_hinge_socket_'+s,'jaw_hinge_pin_'+s],['hound_angular_neck_center','hound_fixed_cheek_yoke_'+s,'hound_jaw_socket'],bearing);
      allow('crusher_claw_support_'+s,['hound_fixed_cheek_yoke_'+s,'hound_jaw_socket'],'Claw support shares the central jaw-bearing housing.');
    }
  }
  if(id==='mob_obsidian_bombardier'){
    allow('blast_shoulders_coaxial_shoulder_spindle',[...oct('heavy_blast_suit_belly'),...oct('blast_cuirass_gold_flange'),...oct('blast_shoulder_shell_-1'),...oct('blast_shoulder_shell_1'),'blast_shoulder_socket_-1','blast_shoulder_socket_1'],spindle);
    for(const [s,side]of[[-1,'left'],[1,'right']]){
      const shell=[...oct('blast_shoulder_shell_'+s),'blast_shoulder_socket_'+s];
      allow(['blast_shoulders_socket_'+s,'blast_shoulders_pin_'+s],['heavy_blast_suit_belly_'+side,'blast_cuirass_gold_flange_'+side,...shell],bearing);
      allow('blast_arm_upper_'+s,['blast_cuirass_gold_flange_'+side,'blast_shoulder_gilt_seam_'+s,...shell],upper);
      allow('blast_elbow_coupler_'+s,[...oct('blast_shoulder_shell_'+s),'blast_shoulder_gilt_seam_'+s],elbow);
    }
  }
  if(id==='mob_chained_juggernaut'){
    allow('executioner_shoulders_coaxial_shoulder_spindle',[...oct('executioner_barrel_chest'),...oct('executioner_scorched_pauldron_-1'),...oct('executioner_scorched_pauldron_1'),'executioner_shoulder_socket_-1','executioner_shoulder_socket_1','rear_neck_brace',...oct('barred_iron_helmet')],spindle);
    for(const[s,side]of[[-1,'left'],[1,'right']]){
      const shell=['executioner_barrel_chest_'+side,...oct('executioner_scorched_pauldron_'+s),'executioner_shoulder_socket_'+s];
      allow(['executioner_shoulders_socket_'+s,'executioner_shoulders_pin_'+s],shell,bearing);
      allow('iron_upper_arm_'+s,[...shell,'executioner_pauldron_ridge_'+s],upper);
    }
  }
  if(id==='boss_warden_vulcantus'){
    allow('warden_action_shoulders_coaxial_shoulder_spindle',[...oct('warden_breast_armored_vessel'),...oct('warden_shoulders_-1'),...oct('warden_shoulders_1'),'warden_action_socket_-1','warden_action_socket_1',...oct('warden_neck_collar')],spindle);
    for(const[s,side]of[[-1,'left'],[1,'right']]){
      const shell=[...oct('warden_shoulders_'+s),'warden_action_socket_'+s];
      allow(['warden_action_shoulders_socket_'+s,'warden_action_shoulders_pin_'+s],shell,bearing);
      allow('warden_upper_arm_'+s,['warden_breast_armored_vessel_'+side,'warden_shoulder_front_plate_'+s,...shell],upper);
      allow('warden_elbow_'+s,[...oct('warden_shoulders_'+s),'warden_shoulder_front_plate_'+s],elbow);
    }
  }
  if(id==='boss_ignion'){
    allow('titan_massive_action_axle_coaxial_shoulder_spindle',['furnace_outer_sidewall_-1','furnace_outer_sidewall_1','furnace_chest_header','furnace_heart_volcanic_cradle_center',...oct('titan_cauldron_pauldron_base_-1'),...oct('titan_cauldron_pauldron_base_1'),'titan_shoulder_pivot_socket_-1','titan_shoulder_pivot_socket_1',...oct('titan_neck_gear_collar')],spindle);
    for(const s of[-1,1]){
      const shell=[...oct('titan_cauldron_pauldron_base_'+s),'titan_shoulder_pivot_socket_'+s];
      allow(['titan_massive_action_axle_socket_'+s,'titan_massive_action_axle_pin_'+s],shell,bearing);
      allow(oct('titan_upper_arm_casing_'+s),[...shell,'pauldron_front_drip_'+s],upper+' The drip coats that bearing cover; the open molten reservoir remains clear.');
    }
  }
  return rules;
}
const sha=filename=>crypto.createHash('sha256').update(fs.readFileSync(filename)).digest('hex');
export function checkFoundryClearance(){
const manifestPath=path.resolve(modelDir,'../../monster_rigs.json');
const manifest=JSON.parse(fs.readFileSync(manifestPath,'utf8'));
const report={schemaVersion:1,status:'PASS',notice:'All moving arm/weapon cuboids versus the fixed root and neutral head, exact 15-axis OBB SAT; contacts <=0.01 model units are treated as touching. Names of intentional load-bearing joint pairs are explicitly allowlisted and every overlap is recorded separately. Model-space geometry only; no Minecraft runtime or deforming skin certification.',pitches:[-70,-65,-60,0,55],headYawDegrees:0,sourceSha256:{[manifestPath]:sha(manifestPath),[fileURLToPath(import.meta.url)]:sha(fileURLToPath(import.meta.url)),[path.join(base,'sources/monsters-foundry.mjs')]:sha(path.join(base,'sources/monsters-foundry.mjs'))},errors:[],rigs:[]};
for(const [id,rig]of Object.entries(FOUNDRY_RIGS)){
  if(JSON.stringify(manifest.rigs[id])!==JSON.stringify(rig))report.errors.push(id+': generated rig manifest differs from source.');
  const filename=n=>path.join(modelDir,n.split(':')[1]+'.json'),load=n=>JSON.parse(fs.readFileSync(filename(n),'utf8')).elements;
  const root=load(rig.rootModel).map(e=>box(e)),fixed=[...root];
  if(rig.sockets.head)fixed.push(...load(rig.sockets.head.model).map(e=>box(e,rig.sockets.head.offset,0)));
  const socket=rig.sockets.arm??rig.sockets.weapon,child=load(socket.model),poses=[],rules=jointRules(id);
  for(const pitch of report.pitches){
    const moving=child.map(e=>box(e,socket.offset,pitch));const floor=Math.min(...moving.map(b=>b.minY)),unwantedCollisions=[],intentionalJointOverlaps=[];
    for(const a of moving)for(const b of fixed){
      const penetration=depth(a,b);if(!penetration)continue;
      const pair={moving:a.name,fixed:b.name,penetrationModelUnits:+penetration.toFixed(4)},rule=rules.find(r=>r.moving.includes(a.name)&&r.fixed.includes(b.name));
      if(rule)intentionalJointOverlaps.push({...pair,reason:rule.reason});else unwantedCollisions.push(pair);
    }
    const status=floor>=-1e-6&&unwantedCollisions.length===0?'PASS':'FAIL';
    if(status==='FAIL')report.errors.push(`${id} pitch ${pitch}: floor=${floor.toFixed(6)}, unwanted=${unwantedCollisions.length}`);
    poses.push({pitch,status,evaluatedMovingCuboids:moving.length,evaluatedFixedCuboids:fixed.length,movingMinimumYModelUnits:+floor.toFixed(6),unwantedCollisions,intentionalJointOverlaps});
  }
  report.rigs.push({id,status:poses.every(p=>p.status==='PASS')?'PASS':'FAIL',sourceSha256:Object.fromEntries([rig.rootModel,...Object.values(rig.sockets).map(s=>s.model)].map(n=>[filename(n),sha(filename(n))])),intentionalJointRules:rules,poses});
}
report.status=report.errors.length?'FAIL':'PASS';
report.evaluatedRigCount=report.rigs.length;report.evaluatedPoseCount=report.rigs.reduce((n,r)=>n+r.poses.length,0);
report.unwantedCollisionCount=report.rigs.reduce((n,r)=>n+r.poses.reduce((n,p)=>n+p.unwantedCollisions.length,0),0);
report.intentionalJointOverlapCount=report.rigs.reduce((n,r)=>n+r.poses.reduce((n,p)=>n+p.intentionalJointOverlaps.length,0),0);
fs.writeFileSync(path.join(base,'review/foundry-clearance.json'),JSON.stringify(report,null,2)+'\n');
return report;
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1])){
 const report=checkFoundryClearance();
 console.log(JSON.stringify({status:report.status,evaluatedRigCount:report.evaluatedRigCount,evaluatedPoseCount:report.evaluatedPoseCount,unwantedCollisionCount:report.unwantedCollisionCount,intentionalJointOverlapCount:report.intentionalJointOverlapCount,errors:report.errors,rigs:report.rigs.map(r=>({id:r.id,status:r.status,minY:Math.min(...r.poses.map(p=>p.movingMinimumYModelUnits))}))},null,2));
 if(report.status!=='PASS')process.exitCode=1;
}
