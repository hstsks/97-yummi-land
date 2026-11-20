/** Astral archive creatures. Import is read-only; construction writes only these fourteen parts.
 * Native axes: +Y up, -Z face. Root soles [8,0,8], child hinges [8,8,8].
 * Socket offsets are unscaled model units measured from the root sole.
 */
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Model,Canvas} from '../tools/asset-kit.mjs';

export const ASTRAL_RIGS = {
  mob_grimoire_mimic:{baseScale:1.15,rootModel:'yuumi97:mob_grimoire_mimic_body',sockets:{arm:{model:'yuumi97:mob_grimoire_mimic_arm',offset:[0,15,6]}}},
  mob_clockwork_sentinel:{baseScale:1.35,rootModel:'yuumi97:mob_clockwork_sentinel_body',sockets:{arm:{model:'yuumi97:mob_clockwork_sentinel_arm',offset:[-10,21,0]}}},
  mob_ink_librarian:{baseScale:1.30,rootModel:'yuumi97:mob_ink_librarian_body',sockets:{arm:{model:'yuumi97:mob_ink_librarian_arm',offset:[0,24,-2]}}},
  mob_nebula_golem:{baseScale:1.75,rootModel:'yuumi97:mob_nebula_golem_body',sockets:{arm:{model:'yuumi97:mob_nebula_golem_arm',offset:[0,22,0]}}},
  boss_archivist_astro:{baseScale:1.65,rootModel:'yuumi97:boss_archivist_astro_core',sockets:{head:{model:'yuumi97:boss_archivist_astro_head',offset:[0,23,0]},weapon:{model:'yuumi97:boss_archivist_astro_weapon',offset:[0,23,7]}}},
  boss_novarion:{baseScale:2.40,rootModel:'yuumi97:boss_novarion_core',sockets:{head:{model:'yuumi97:boss_novarion_head',offset:[0,25,0]},weapon:{model:'yuumi97:boss_novarion_weapon',offset:[0,24,14]}}},
};

const R=(axis,angle,origin)=>({axis,angle,origin,rescale:false});
const B=(m,n,x,y,z,w,h,d,t,o={})=>m.box(n,[x-w/2,y-h/2,z-d/2],[x+w/2,y+h/2,z+d/2],t,o);
function translateParts(m,select,delta){
 for(const e of m.elements.filter(select)){
  for(let i=0;i<3;i++){e.from[i]+=delta[i];e.to[i]+=delta[i];}
  if(e.rotation)e.rotation={...e.rotation,origin:e.rotation.origin.map((v,i)=>v+delta[i])};
 }
}
function tile(m,n,col,draw){m.swatch(n,col,{noise:false});const c=new Canvas(16,16);c.rect(0,0,16,16,col);draw(c);const s=m.swatches[n];m.canvas.blit(c,s.x,s.y);}
function make(id){
 const m=new Model(id,128);
 for(const[n,c,kind]of[['navy','#1B263B','cloth'],['navyLight','#334869','cloth'],['navyDeep','#111728','cloth'],['gold','#DFA756',null],['goldLight','#FFD990',null],['goldDark','#8C623C',null],['brass','#AF864D',null],['ivory','#EAE0D5',null],['white','#FFF5DE',null],['cyan','#00CFC5',null],['ice','#A4FFF0',null],['violet','#7844A8',null],['purpleLight','#BF8FE7',null],['purpleDark','#392851',null],['blueFur','#547CA4','cloth'],['blueFurLight','#82A6CB','cloth'],['stone','#353744','stone'],['stoneLight','#575966','stone'],['stoneDark','#202431','stone'],['ink','#100D21',null]])m.swatch(n,c,{kind,border:true});
 tile(m,'parchment','#E2D1AF',c=>{for(let y=1;y<16;y+=3)c.line(1,y,14,y,'#B7A182');c.rect(1,0,14,1,'#FFF0D1');for(const[x,y]of[[3,5],[11,9],[6,13]])c.pixel(x,y,'#8D735E');});
 tile(m,'runes','#EAE0D5',c=>{for(let y=3;y<15;y+=4){c.line(2,y,12,y,'#7A6683');c.pixel(4,y-1,'#7A6683');c.pixel(11,y+1,'#BBA171');}c.line(7,1,7,14,'#D6BF8E');});
 tile(m,'galaxy','#251C48',c=>{for(let y=0;y<16;y++)for(let x=0;x<16;x++){const d=Math.abs(x-y*.62-3);if(d<3)c.pixel(x,y,d<1?'#A27CDD':'#624E95');if((x*17+y*11)%41===0)c.pixel(x,y,'#D5F7F1');}c.rect(3,4,1,3,'#FFFFFF').rect(2,5,3,1,'#FFFFFF');});
 tile(m,'brassWear','#BD955B',c=>{c.rect(0,0,16,1,'#FFE0A0');c.rect(0,0,1,16,'#F3C985');for(const[x,y,w]of[[3,3,5],[8,10,6],[2,13,3]])c.rect(x,y,w,1,'#8B714E');c.rect(13,1,2,3,'#6A9D90');c.pixel(12,3,'#84BCAC');});
 tile(m,'robeStars','#263954',c=>{for(let y=1;y<16;y+=3)for(let x=1;x<16;x+=3)c.pixel(x,y,'#304660');for(const[x,y]of[[3,4],[12,8],[6,13]]){c.pixel(x,y,'#E9C982');c.pixel(x-1,y,'#A49369');}c.line(3,4,12,8,'#6F7B80').line(12,8,6,13,'#6F7B80');});
 tile(m,'inkSheen','#171222',c=>{for(const[x,y,w]of[[1,2,11],[4,4,9],[2,11,6]])c.rect(x,y,w,1,'#382B51');c.rect(2,1,6,1,'#5C4E73');});
 m.display.gui={rotation:[20,-30,0],translation:[0,-1,0],scale:[.45,.45,.45]};
 return m;
}
function octXZ(m,n,x,z,rx,rz,y0,y1,t,o={}){B(m,n+'_middle',x,(y0+y1)/2,z,rx*2,y1-y0,rz*1.16,t,o);B(m,n+'_front',x,(y0+y1)/2,z-rz*.79,rx*1.16,y1-y0,rz*.42,t,o);B(m,n+'_rear',x,(y0+y1)/2,z+rz*.79,rx*1.16,y1-y0,rz*.42,t,o);}
function octXY(m,n,x,y,rx,ry,z0,z1,t,o={}){B(m,n+'_middle',x,y,(z0+z1)/2,rx*2,ry*1.16,z1-z0,t,o);B(m,n+'_top',x,y+ry*.79,(z0+z1)/2,rx*1.16,ry*.42,z1-z0,t,o);B(m,n+'_bottom',x,y-ry*.79,(z0+z1)/2,rx*1.16,ry*.42,z1-z0,t,o);}
function ringXY(m,n,x,y,r,w,z0,z1,t,o={}){
 const tangent=2*r*Math.tan(Math.PI/8)+.035;
 for(let k=0;k<8;k++){const a=k*Math.PI/4,cx=x+Math.cos(a)*r,cy=y+Math.sin(a)*r,diagonal=k%2===1;let width,height;if(diagonal){width=w;height=tangent;}else if(k%4===0){width=w;height=tangent;}else{width=tangent;height=w;}
  B(m,n+'_'+k,cx,cy,(z0+z1)/2,width,height,z1-z0,t,diagonal?{rotation:R('z',k===1||k===5?45:-45,[cx,cy,(z0+z1)/2])}:o);
 }
}
function ringXZ(m,n,x,z,r,w,y0,y1,t){const tangent=2*r*Math.tan(Math.PI/8)+.035;for(let k=0;k<8;k++){const a=k*Math.PI/4,cx=x+Math.cos(a)*r,cz=z+Math.sin(a)*r;B(m,n+'_'+k,cx,(y0+y1)/2,cz,k%2?w:k%4===0?w:tangent,y1-y0,k%2?tangent:k%4===0?tangent:w,t,k%2?{rotation:R('y',k===1||k===5?-45:45,[cx,(y0+y1)/2,cz])}:{});}}
function tiltedRingXY(m,n,x,y,r,w,z,depth,t,angle){
 // Continuous stepped strips allow the entire inner band one legal X rotation.
 // Outer octagonal band stays in XY; their separated planes form an armillary.
 const step=.45,low=-r-w,high=r+w,rot={rotation:R('x',angle,[x,y,z])};let index=0;
 for(let yy=low;yy<high;yy+=step){let start=null;for(let xx=low;xx<=high+step;xx+=step){const inside=xx<high&&Math.abs(Math.hypot(xx+step/2,yy+step/2)-r)<=w/2;if(inside&&start===null)start=xx;if(!inside&&start!==null){m.box(n+'_'+index++,[x+start,y+yy,z-depth/2],[x+Math.min(xx,high),y+Math.min(yy+step,high),z+depth/2],t,rot);start=null;}}}
}
function gem(m,n,x,y,z,r,h,t='violet',o={}){octXZ(m,n+'_root',x,z,r*.8,r*.72,y,y+h*.2,t,o);octXZ(m,n+'_shaft',x,z,r,r*.8,y+h*.2,y+h*.67,t,o);octXZ(m,n+'_crown',x,z,r*.65,r*.5,y+h*.67,y+h*.9,'purpleLight',o);B(m,n+'_tip',x,y+h*.95,z,r*.4,h*.1,r*.35,'ice',o);B(m,n+'_facet',x-r*.43,y+h*.46,z-r*.82,r*.25,h*.45,.12,'purpleLight',o);}
function star(m,n,x,y,z,s,t='goldLight'){B(m,n+'_vertical',x,y,z,s*.46,s*2,.22,t);B(m,n+'_horizontal',x,y,z,s*2,s*.46,.23,t);B(m,n+'_heart',x,y,z-.1,s*.85,s*.85,.3,t,{rotation:R('z',45,[x,y,z])});}
function studs(m,n,x0,x1,y,z,t='goldLight'){for(let i=0;i<5;i++)B(m,n+'_'+i,x0+(x1-x0)*i/4,y,z,.34,.34,.3,t);}
function clawFoot(m,n,x,z,y=0,t='stone',w=4){B(m,n+'_heel',x,y+1.4,z+1,w,2.8,4.7,t);B(m,n+'_instep',x,y+2,z-.8,w*.84,4,4.2,t);for(let i=0;i<3;i++){B(m,n+'_toe_'+i,x-w*.32+i*w*.32,y+.8,z-3.5,w*.27,1.6,3,t);B(m,n+'_claw_'+i,x-w*.32+i*w*.32,y+.5,z-5,.55,1,1.3,'ivory');}}
function collarX(m,n,x,y,z,r=1.9,t='gold'){B(m,n+'_axle',x,y,z,2.6,r*1.3,r*1.3,'stoneDark');for(let i=0;i<3;i++){B(m,n+'_cap_'+i,x-1+i,y,z,.38,r*1.75,r*1.75,t);B(m,n+'_pin_'+i,x-1+i,y,z-r*.92,.4,.6,.25,'ice');}}
function pairedHinge(m,n,left,right){B(m,n+'_coaxial_spindle',(left+right)/2,8,8,right-left+.3,.7,.7,'goldDark');collarX(m,n+'_left',left,8,8);collarX(m,n+'_right',right,8,8);}
function book(m,n,x,y,z,w,h,d,o={}){
 B(m,n+'_pages',x,y,z,w-.6,h-.8,d-.35,'parchment',o);
 B(m,n+'_cover_front',x,y,z-d/2,w,h,.35,'navy',o);B(m,n+'_cover_back',x,y,z+d/2,w,h,.35,'navyDeep',o);
 B(m,n+'_spine',x-w/2,y,z,.55,h,d+.2,'goldDark',o);
 for(const dy of[-h*.34,h*.34])B(m,n+'_spine_band_'+dy,x-w/2-.04,y+dy,z,.65,.4,d+.35,'gold',o);
 for(const xx of[-w*.42,w*.42])for(const yy of[-h*.42,h*.42])B(m,n+'_corner_'+xx+'_'+yy,x+xx,y+yy,z-d*.5-.2,.7,.7,.22,'goldLight',o);
 B(m,n+'_front_inset',x,y,z-d*.5-.21,w*.66,h*.7,.13,'robeStars',o);
}
function openBook(m,n,x,y,z,w=6,h=6){
 for(const[side,s]of[['left',-1],['right',1]]){
  const xx=x+s*w*.26,rot={rotation:R('y',s*22.5,[x,y,z])};
  B(m,n+'_'+side+'_cover',xx,y,z,w*.53,h,.5,'gold',rot);B(m,n+'_'+side+'_pages',xx,y+.1,z-.34,w*.49,h-.6,.3,'runes',rot);
  B(m,n+'_'+side+'_rim',xx,y+h*.49,z-.32,w*.54,.22,.35,'goldLight',rot);
 }
 B(m,n+'_spine',x,y,z,.5,h+.1,.9,'navy');star(m,n+'_star',x,y,z-.65,.7,'cyan');
}

function mimic(){
 const m=make('mob_grimoire_mimic_body');
 B(m,'lower_navy_book_cover',8,.65,7,20,1.3,17,'navy');B(m,'lower_gold_edge',8,1.45,7,19.6,.3,16.6,'gold');
 octXZ(m,'lower_jaw_page_block',8,7,9.5,7.8,1.6,4.5,'parchment');B(m,'mouth_velvet_interior',8,4.6,6,16.8,.5,13.2,'purpleDark');
 for(let i=0;i<9;i++){const x=.3+i*1.93;B(m,'lower_gum_'+i,x,5.12,-.35,1.65,1,1.6,'navyLight');B(m,'lower_fang_base_'+i,x,5.8,-.5,.95,1.3,.9,'ivory');B(m,'lower_fang_tip_'+i,x,6.75,-.6,.42,.8,.4,'white');}
 for(const x of[-1.6,17.6])for(let i=0;i<4;i++){B(m,'side_fang_'+x+'_'+i,x,5.65,2+i*3,.9,2.5,.9,'ivory');}
 B(m,'back_spine_lower',8,6,14.4,19,9,2.1,'navy');B(m,'back_spine_gold_ridge',8,10.5,14.4,19.5,1.1,2.5,'gold');
 B(m,'back_hinge_pier_left',.5,12.1,14,2.2,6.5,3.4,'goldDark');B(m,'back_hinge_pier_right',15.5,12.1,14,2.2,6.5,3.4,'goldDark');B(m,'upper_jaw_hinge_axle',8,15,14,17.8,1.4,1.4,'goldLight');
 for(const[x,n,s]of[[-1.6,'left',-1],[17.6,'right',1]]){
  for(let i=0;i<5;i++){const xx=x+s*(1.1+i*1.6),yy=4.3+i*.35,zz=7+i*.7;B(m,n+'_paper_wing_quill_'+i,xx,yy,zz,2.2,1,8.6-i*.9,'parchment',{rotation:R('z',s*22.5,[xx,yy,zz])});B(m,n+'_wing_gold_vein_'+i,xx,yy+.55,zz,.3,.18,7.5-i*.8,'gold');}
 }
 // Rolled tongue exits the open mouth and curls upward at its broad tip.
 for(const[i,z,y,w]of[[0,3,5,4],[1,0,4.8,4.5],[2,-3,4.3,4.5],[3,-6,3.8,4.1],[4,-8.5,4.3,3.6],[5,-10,5.2,3],[6,-10.4,6.2,2.2]]){B(m,'curling_magic_tongue_'+i,8,y,z,w,1.15,3.2,i>3?'purpleLight':'violet');B(m,'tongue_center_groove_'+i,8,y+.6,z,.22,.12,2.9,'purpleDark');}
 for(const x of[-.8,16.8])for(const z of[-.8,14.8])B(m,'lower_cover_golden_corner_'+x+'_'+z,x,.85,z,1.8,.7,1.8,'goldLight');
 star(m,'lower_cover_front_seal',8,2,-1.6,1.4);studs(m,'spine_tacks',1,15,9,15.52);
 const a=make('mob_grimoire_mimic_arm'),rot={rotation:R('x',22.5,[8,8,8])};
 B(a,'hinge_axle',8,8,8,18,1.6,1.6,'gold');
 B(a,'upper_navy_cover',8,10,-.4,20,1.6,17,'navy',rot);B(a,'upper_gold_cover_rim',8,9.1,-.4,19.7,.35,16.8,'gold',rot);B(a,'upper_page_slab',8,8.5,-.4,18.7,.8,15.7,'parchment',rot);
 B(a,'upper_mouth_lining',8,7.96,-.2,16.7,.25,13.7,'purpleDark',rot);
 for(let i=0;i<9;i++){const x=.3+i*1.93;B(a,'upper_fang_root_'+i,x,7.3,-7.7,1.05,1.2,1.1,'ivory',rot);B(a,'upper_fang_tip_'+i,x,6.4,-7.85,.43,.8,.45,'white',rot);}
 for(const x of[-1.5,17.5])for(let i=0;i<4;i++)B(a,'upper_side_fang_'+x+'_'+i,x,7.2,-5+i*3,.8,1.7,.8,'ivory',rot);
 B(a,'upper_cover_galaxy_inset',8,10.9,-.5,13,.2,11,'galaxy',rot);
 for(const x of[-.5,16.5])B(a,'upper_cover_gold_long_rail_'+x,x,11,-.4,.55,.35,16,'gold',rot);
 for(const z of[-7.5,6.8])B(a,'upper_cover_gold_cross_rail_'+z,8,11,z,17,.35,.55,'gold',rot);
 for(let i=0;i<5;i++)B(a,'gold_chain_over_cover_'+i,8,11.15,-5+i*2.5,1.1,.5,1.6,i%2?'goldLight':'goldDark',rot);
 B(a,'chain_front_hasplate',8,9,-9.1,3.2,3.3,.7,'gold',rot);B(a,'chain_lock_dark_keyhole',8,9,-9.5,.55,1.25,.15,'navyDeep',rot);
 // Offset the two tooth rows by half a tooth pitch. A small jaw rise keeps the
 // closing teeth above the tongue, while rear risers preserve the actual hinge.
 translateParts(a,e=>e.name!=='hinge_axle',[0,2,0]);
 for(const e of a.elements.filter(e=>/^upper_fang_(root|tip)_/.test(e.name))){
  e.from[0]+=.965;e.to[0]+=.965;
  if(e.name.startsWith('upper_fang_root_')){e.from[0]+=.1;e.to[0]-=.1;}
 }
 for(const x of[.5,15.5])B(a,'upper_jaw_hinge_riser_'+x,x,9.75,8.5,1.8,4.1,1.6,'goldDark');
 return [m,a];
}

function sentinel(){
 const m=make('mob_clockwork_sentinel_body');
 for(const[x,n]of[[4.5,'right'],[11.5,'left']]){clawFoot(m,n+'_sabatons',x,7,0,'navy',3.7);B(m,n+'_greave',x,6.7,8,3.3,7.4,3.7,'brassWear');B(m,n+'_knee',x,10.4,6.3,3.8,2.3,2.3,'gold');B(m,n+'_thigh',x,12.4,8,3.1,3.7,3.2,'navy');B(m,n+'_greave_inlay',x,6.2,5.92,.5,4.3,.16,'cyan');}
 octXZ(m,'hip_cuirass',8,8,5.2,3.5,12.3,15,'navy');octXZ(m,'waist_gear',8,8,3.4,2.6,15,16.7,'goldDark');octXZ(m,'brass_rib_cage',8,8,5.1,3.6,16.4,22.5,'brassWear');
 B(m,'navy_chest_enamel',8,19,4.2,7.3,4.1,.65,'navy');star(m,'chest_compass_badge',8,19.1,3.78,1.35,'cyan');studs(m,'chest_top_rivets',4.6,11.4,21.2,4.1);
 B(m,'right_shoulder_yoke',1.4,21,8,4.4,2.4,4,'brass');collarX(m,'right_shoulder_socket',-1.8,21,8,2.2);
 B(m,'left_fixed_upper_arm',14.3,19.4,8,3.2,5.2,3.5,'navy');B(m,'left_fixed_gauntlet',15.8,16,5,3.8,3.8,3.5,'brassWear');
 octXY(m,'left_constellation_shield',16,17,4.4,6,1.6,2.65,'navy');ringXY(m,'shield_brass_frame',16,17,4.2,.6,1.22,1.7,'gold');star(m,'shield_polar_star',16,17,1.02,1.8,'cyan');
 for(let i=0;i<4;i++)star(m,'shield_minor_star_'+i,13.5+(i%2)*5,14.5+Math.floor(i/2)*5,1.02,.43,'goldLight');
 octXZ(m,'neck_gears',8,8,2.1,2,22.5,23.6,'gold');octXZ(m,'monoeye_helmet',8,8,4.3,3.8,23.4,28.6,'navy');octXZ(m,'helmet_crown',8,8,3.1,3.1,28.6,29.7,'brassWear');
 B(m,'visor_horizontal_slot',8,26.5,4.05,7.7,1.5,.5,'ink');octXY(m,'monoeye_gold_housing',8,26.5,1.65,1.35,3.53,4.05,'gold');octXY(m,'monoeye_cyan_lens',8,26.5,1.15,.88,3.32,3.55,'cyan');B(m,'monoeye_white_glint',7.6,26.8,3.2,.55,.45,.15,'ice');
 B(m,'helmet_sagittal_crest',8,30,8,.85,2,5.5,'gold');
 // Rear winding key is actual open geometry with two lobes and a central axle.
 B(m,'rear_key_shaft',8,20,14,1.4,1.4,6,'goldDark');B(m,'winding_key_bridge',8,20,17.4,10.4,1.2,1.2,'gold');
 for(const x of[3.8,12.2])ringXY(m,'key_open_lobe_'+x,x,20,2.6,.75,16.9,18,'goldLight');
 for(let i=0;i<4;i++)B(m,'back_clockwork_vent_'+i,8,17+i*.85,11.72,5.8,.25,.4,'stoneDark');
 const a=make('mob_clockwork_sentinel_arm');collarX(a,'right_shoulder_rotor',8,8,8,2.1);
 B(a,'upper_brass_arm',8,4.3,7,3.3,6.1,3.3,'brassWear');B(a,'upper_arm_enamel_strip',8,4.4,5.25,1.6,3.9,.3,'navy');octXZ(a,'elbow_gear',8,5.3,2,2,0,2.5,'goldDark');
 B(a,'forward_forearm',8,1.2,1,3.6,3.4,8.4,'navy');B(a,'gauntlet_cuff',8,1.2,-3.2,4.2,4,1.6,'gold');B(a,'halberd_grip_fist',8,1.2,-5,3.3,3.3,3.1,'brassWear');
 B(a,'halberd_long_shaft',8,1.2,-3.5,1,1,20.5,'goldDark');for(let i=0;i<4;i++)B(a,'halberd_grip_wrap_'+i,8,1.2,-2+i*1.05,1.4,1.4,.45,'gold');
 B(a,'halberd_crystal_socket',8,1.2,-11.6,2.2,2.2,2,'gold');
 for(let i=0;i<5;i++){const w=4.2-i*.7;B(a,'halberd_forward_crystal_'+i,8,1.2,-11.7-i*.72,w,w*.65,.9,i<3?'cyan':'ice');}
 for(const s of[-1,1]){B(a,'halberd_axe_hook_'+s,8+s*3.3,1.2,-10.3,2,1.6,4.6,'gold');B(a,'halberd_crescent_blade_'+s,8+s*4.05,1.2,-12,1.6,1.05,4,'cyan');B(a,'halberd_hook_tip_'+s,8+s*3.6,1.2,-13.5,1.8,.55,1,'ice');}
 // A raised rest grip keeps the cutting edge above ground through the +55 degree strike.
 for(const e of a.elements)if(e.name.startsWith('halberd_'))e.rotation=R('x',22.5,[8,1.2,-5]);
 return [m,a];
}

function librarian(){
 const m=make('mob_ink_librarian_body');
 octXZ(m,'ink_pool_contact',8,8,5.8,4.3,0,.55,'inkSheen');
 for(const[i,x,y,z,r]of[[0,8,.5,8,3.1],[1,9,2,8,2.7],[2,9.4,4,8.2,2.2],[3,8.7,6,8.5,1.8],[4,8,8,8.5,2.3]])octXZ(m,'twisting_ink_tail_'+i,x,z,r,r*.78,y,y+2.2,'inkSheen');
 for(const[i,x,z]of[[0,2,6],[1,14,9],[2,6,12.3]]){B(m,'spreading_ink_rivulet_'+i,x,.18,z,3.6,.36,1.1,'inkSheen');B(m,'ink_bead_'+i,x+1.4,.5,z,.65,.65,.65,'purpleDark');}
 for(let i=0;i<7;i++){const x=1.8+i*2.05,y=8+(i%3)*.85,left=i===0?2:x-1.025,right=i===6?14:x+1.025;B(m,'ragged_front_robe_panel_'+i,(left+right)/2,(y+20)/2,4.3,right-left,20-y,1.3,i%2?'navy':'robeStars');B(m,'torn_gold_hem_'+i,x,y+.15,3.57,1.75,.35,.22,'goldDark');}
 octXZ(m,'robe_upper_torso',8,8,5.7,3.9,17.8,24.5,'navy');
 for(const s of[-1,1]){B(m,'robe_fold_'+s,8+s*5.2,15.5,8,1.5,14,7.4,'navyDeep');B(m,'shoulder_sleeve_collar_'+s,8+s*7.7,24,6,3.4,3.5,3.7,'navyLight');}
 for(let i=0;i<5;i++)B(m,'rear_tattered_cape_'+i,3+i*2.5,14-(i%2),11.8,2.35,16-(i%2)*2,1,'robeStars');
 B(m,'robe_central_gold_stole',8,18,3.5,1.4,11.5,.45,'goldDark');star(m,'robe_collar_star',8,22.5,3.15,1,'gold');
 octXZ(m,'hood_lower_ring',8,8,4.7,3.8,24,26,'navyLight');B(m,'hood_shadow_face',8,26.7,4.17,7.2,4.8,.6,'ink');
 B(m,'hood_left_volume',3.5,26.8,8,2,5.6,7.3,'navy');B(m,'hood_right_volume',12.5,26.8,8,2,5.6,7.3,'navy');B(m,'hood_crown',8,29.3,8,8.9,1,7.4,'navy');B(m,'hood_back',8,26.9,11.4,8.7,5.8,1.1,'navyDeep');
 for(const s of[-1,1]){B(m,'cat_hood_ear_base_'+s,8+s*4.3,29.65,7,2.6,1.7,3.1,'navy');B(m,'cat_hood_ear_middle_'+s,8+s*4.55,30.8,7,1.75,1.2,2.2,'navyLight');B(m,'cat_hood_ear_tip_'+s,8+s*4.8,31.6,7,.7,.8,1.1,'navyLight');B(m,'cat_hood_ear_inner_'+s,8+s*4.3,29.7,5.38,1.15,1,.18,'violet');B(m,'cat_hood_ear_inner_tip_'+s,8+s*4.55,30.6,5.82,.6,.8,.18,'purpleLight');B(m,'cyan_eye_'+s,8+s*2.0,27.2,3.75,1.4,.7,.3,'cyan');B(m,'eye_core_'+s,8+s*1.9,27.25,3.56,.55,.3,.14,'ice');}
 const a=make('mob_ink_librarian_arm');pairedHinge(a,'sleeve_shoulder',0,16);
 for(const[s,n]of[[-1,'right'],[1,'left']]){const x=8+s*8;B(a,n+'_upper_bone_sleeve',x,7,3.7,3.2,3.1,8,'navy');B(a,n+'_elbow_binding',x,6.5,.0,3.6,3.7,1.4,'goldDark');B(a,n+'_outstretched_forearm',x-s*1.8,6.3,-3.6,5.2,2.5,7,'navyLight');for(let i=0;i<3;i++)B(a,n+'_finger_bone_'+i,x-s*3.5+s*i*.55,6.3,-7.3,.42,.65,2.5,'ivory');B(a,n+'_sleeve_rag',x+s*.5,4.5,1.1,1,2.2,4.5,'navyDeep');}
 // A short transverse handle holds both chains. The lantern stays in front of the torso.
 B(a,'lantern_carrier_crossbar',8,6.2,-8,8.7,.65,.65,'gold');
 for(const x of[5,11]){
  B(a,'lantern_carrier_hanger_'+x,x,5.8,-8.85,.5,.65,1.9,'gold');
  for(let i=0;i<3;i++)B(a,'lantern_chain_'+x+'_'+i,x,5.5-i*.75,-9.2-i*.65,.5,.95,.8,i%2?'goldLight':'goldDark');
 }
 octXZ(a,'lantern_lower_foot',8,-10.6,3,2.8,-1,.1,'goldDark');octXZ(a,'lantern_glass_chamber',8,-10.6,2.4,2.2,.1,4.2,'purpleDark');
 for(const x of[5.55,10.45])for(const z of[-12.65,-8.55])B(a,'lantern_brass_mullion_'+x+'_'+z,x,2.2,z,.45,4.5,.45,'gold');
 octXZ(a,'lantern_upper_roof',8,-10.6,3.15,2.9,4.1,5.1,'gold');B(a,'lantern_top_crest',8,5.5,-10.6,2,1.2,2,'goldLight');
 for(const[i,y,w]of[[0,.5,2.8],[1,1.3,2.2],[2,2.2,1.6],[3,3.15,.8]])B(a,'cyan_soul_flame_'+i,8+(i%2)*.25,y,-12.95,w,1.2,.6,i<2?'cyan':'ice');
 star(a,'lantern_front_constellation',8,2,-13.35,.55,'ice');
 // The narrowed outer robe panels and this 0.3-unit forward clearance prevent
 // sleeve/elbow clipping at +55 without moving the shoulder hinge or lantern grip.
 translateParts(a,e=>!e.name.startsWith('sleeve_shoulder_'),[0,0,-.3]);
 return [m,a];
}

function golem(){
 const m=make('mob_nebula_golem_body');
 for(const[x,n]of[[3,'right'],[13,'left']]){clawFoot(m,n+'_basalt_foot',x,7,0,'stone',5.4);B(m,n+'_lower_leg',x,6.3,8,4.4,6.6,4.9,'stoneLight');B(m,n+'_knee_boulder',x,9.5,6.3,5.2,3.6,4.7,'stone');B(m,n+'_upper_leg',x,11,8,4,4.2,4.6,'stoneDark');}
 octXZ(m,'irregular_lower_trunk',8,8,7,4.6,11,15.7,'stoneDark');octXZ(m,'massive_stone_ribcage',8,8,8.2,5.4,15.4,22,'stone');
 B(m,'right_offset_stone_shoulder',-.4,22,8.3,5.7,5.6,7.6,'stoneLight');B(m,'left_book_bound_shoulder',15.2,22,9,5.8,6.8,7.1,'stoneDark');
 B(m,'chest_geode_recess',8,18.3,2.45,7.9,6.3,.9,'purpleDark');octXY(m,'chest_crystal_core',8,18.5,2.9,2.6,1.3,2.2,'violet');octXY(m,'chest_core_inner_star',8,18.5,1.7,1.5,1.03,1.35,'purpleLight');star(m,'chest_nebula_spark',8,18.5,.86,1.0,'ice');
 for(const[s,n]of[[-1,'right'],[1,'left']]){B(m,n+'_shoulder_axle_collar',8+s*10.1,22,8,4,3.7,3.7,'goldDark');B(m,n+'_stone_brow',8+s*1.9,26,4.3,3.2,1.2,1.2,'stoneLight');B(m,n+'_cyan_eye',8+s*1.8,25.3,4.12,1.5,.55,.25,'cyan');}
 octXZ(m,'small_sunken_head',8,8,4,3.6,22,27.5,'stone');B(m,'head_central_crack',8,25,4.25,.45,3,.3,'purpleLight');
 for(const[i,x,z,r,h,y]of[[0,-.8,8,2.2,7.5,23],[1,3,11,1.65,6.3,24],[2,14.7,8,1.9,5.2,25],[3,11.5,11,1.4,5.4,25]])gem(m,'shoulder_amethyst_column_'+i,x,y,z,r,h);
 book(m,'back_fused_grimoire_large',6.6,18.8,14.8,8,10,2.8);book(m,'left_shoulder_fused_tome',15,21.5,6.5,4.2,8,2.3,{rotation:R('z',-22.5,[15,21.5,6.5])});book(m,'hip_fused_tome',1.4,13,5.5,4,5.4,2.2,{rotation:R('z',22.5,[1.4,13,5.5])});
 for(const x of[4.5,9.5])B(m,'rear_tome_binding_'+x,x,18.8,13.1,.55,11,4.2,'gold');
 for(let i=0;i<5;i++)B(m,'front_stone_fault_'+i,2+i*2.5,14+i%2,3.22,1.1,.4,.4,'purpleDark');
 const a=make('mob_nebula_golem_arm');pairedHinge(a,'golem_shoulders',-3,19);
 // Preserve the coaxial bearings; the wider outside arms still overlap their
 // rotor ends but clear the hip book, knees, chest bands and fixed crystals.
 for(const[x,n,s]of[[-6.5,'right',-1],[22.5,'left',1]]){octXZ(a,n+'_stone_upper_arm',x,7.6,2.5,2.8,1.8,8.9,'stone');B(a,n+'_elbow_slab',x,1.7,5.8,6.2,3.1,6.3,'stoneLight');octXZ(a,n+'_giant_hammer_fist',x,3.2,4.1,4.5,-4.8,1.5,'stone');B(a,n+'_knuckle_face',x,-1.6,-1.5,6.8,4.5,1.1,'stoneLight');for(let i=0;i<3;i++)B(a,n+'_knuckle_split_'+i,x-2.3+i*2.3,-1.6,-2.1,.35,3.7,.3,'purpleDark');
  for(let i=0;i<3;i++)gem(a,n+'_amethyst_spike_'+i,x+s*(1+i*.75),3.3+i,6.3-i*2,1.1,4.6-i*.65,'violet',{rotation:R('z',s*22.5,[x+s*(1+i*.75),3.3+i,6.3-i*2])});
  B(a,n+'_gold_binding_top',x,2.7,3.4,8.5,.7,7.6,'goldDark');B(a,n+'_gold_binding_front',x,-.7,-1.7,.65,6.5,.7,'gold');
 }
 return [m,a];
}

function astroCore(){
 const m=make('boss_archivist_astro_core');
 ringXZ(m,'levitation_contact_ring',8,8,5.8,.8,0,.7,'gold');ringXZ(m,'levitation_upper_ring',8,8,4.4,.6,1.6,2.1,'cyan');
 for(let i=0;i<8;i++){const a=i*Math.PI/4;B(m,'orbiting_ground_rune_'+i,8+Math.cos(a)*5.8,.9,8+Math.sin(a)*5.8,.7,1.3,.7,'goldLight');}
 for(const[y0,y1,rx,rz,n]of[[2.5,6,7,4.4,'flared_hem'],[6,11,6.2,4.1,'lower_robe'],[11,16.5,4.8,3.4,'waist_robe'],[16.5,21.8,6.1,3.8,'high_shoulders']])octXZ(m,n,8,8,rx,rz,y0,y1,'robeStars');
 for(const s of[-1,1]){B(m,'embroidered_long_stole_'+s,8+s*2.6,12.5,3.6,1.35,18.8,.7,'gold');B(m,'stole_purple_inlay_'+s,8+s*2.6,12.7,3.2,.55,17,.2,'purpleDark');for(let i=0;i<4;i++)star(m,'stole_star_'+s+'_'+i,8+s*2.6,5.6+i*4.2,3.03,.52,'goldLight');}
 for(let i=0;i<7;i++)B(m,'robe_bottom_pleat_'+i,2+i*2,4.3,3.65,.8,3.5,.6,i%2?'navyDeep':'navyLight');
 B(m,'waist_gold_sash',8,14.4,3.8,9.5,1.4,.6,'goldDark');star(m,'waist_astral_buckle',8,14.5,3.3,1.1,'cyan');
 for(const[x,n]of[[1,'left'],[15,'right']]){octXZ(m,n+'_folded_sleeve',x,8,2.3,3.7,14.8,21.5,'navy');B(m,n+'_gold_sleeve_cuff',x,15.1,5.8,4.2,1.3,4.8,'gold');B(m,n+'_folded_blue_paw',x,14.7,3.9,3.1,2.5,2.5,'blueFur');}
 octXZ(m,'raised_collar',8,8,4.1,3.3,21.4,22.7,'goldDark');octXZ(m,'neck_socket',8,8,2.3,2.3,22.6,23.4,'blueFur');
 B(m,'rear_orbit_spindle',8,23,13.4,2.4,2.4,6,'goldDark');B(m,'rear_spindle_crown',8,23,15,3.2,3.2,1,'gold');
 for(let i=0;i<5;i++)star(m,'back_constellation_'+i,4+i*2,8+(i%3)*3,12.2,.6,'goldLight');return m;
}
function catHead(id,lion=false){
 const m=make(id);
 octXZ(m,'neck_pivot_collar',8,8,2.3,2.3,8,10.1,lion?'goldDark':'blueFur');
 octXZ(m,'cat_lower_jaw',8,7.6,3.5,3,9.6,12,'blueFur');octXZ(m,'cat_main_cheek_volume',8,7.6,lion?5.1:4.5,3.8,11.5,17.7,'blueFur');octXZ(m,'cat_forehead',8,8,4.2,3.2,17.4,19.3,'blueFur');
 for(const[s,n]of[[-1,'left'],[1,'right']]){B(m,n+'_muzzle_lobe',8+s*1.4,12.4,3.55,2.9,2,1.8,'blueFurLight');B(m,n+'_eye_socket',8+s*2.5,15.3,3.6,2.5,1.8,.55,'navyDeep');B(m,n+'_cyan_eye',8+s*2.5,15.3,3.23,1.7,.7,.25,'cyan');B(m,n+'_eye_glint',8+s*2.5,15.4,3.04,.45,.4,.15,'ice');B(m,n+'_stern_brow',8+s*2.5,16.45,3.35,2.8,.65,.7,'navy',{rotation:R('z',s*22.5,[8+s*2.5,16.45,3.35])});
  for(let i=0;i<2;i++)B(m,n+'_whisker_'+i,8+s*4.1,12+i*.65,3.3,2.4,.2,.25,'goldLight',{rotation:R('z',s*(i?22.5:-22.5),[8+s*4.1,12+i*.65,3.3])});
  B(m,n+'_ear_lower',8+s*3.4,19.25,7,2.6,2.4,3.1,'blueFur');B(m,n+'_ear_tip',8+s*3.9,20.75,7,1.15,1,1.6,'blueFurLight');B(m,n+'_ear_inner',8+s*3.6,19.5,5.36,1.1,1.5,.22,'purpleLight');
 }
 B(m,'triangular_cat_nose',8,13.2,2.95,.95,.6,.4,'navyDeep');B(m,'mouth_center',8,11.9,2.7,.2,1.1,.18,'navyDeep');B(m,'mouth_left',7.35,11.42,2.95,1.1,.2,.2,'navyDeep');B(m,'mouth_right',8.65,11.42,2.95,1.1,.2,.2,'navyDeep');
 if(!lion){
  ringXY(m,'gold_monocle',4.9,15.3,1.55,.32,2.75,3.14,'goldLight');B(m,'monocle_lens_glint',4.35,15.8,2.7,.45,.6,.13,'ice');
  for(let i=0;i<5;i++)B(m,'monocle_chain_'+i,3.5+i*.12,14.4-i*.8,3.6+i*.5,.28,.55,.35,'gold');
  octXZ(m,'bishop_mitre_band',8,8,4.2,3,18.7,19.7,'gold');B(m,'mitre_navy_panel',8,21.3,7.4,6.3,3.3,3.2,'navy');B(m,'mitre_upper_step',8,23.15,7.4,3.7,.9,2.5,'gold');B(m,'mitre_crown_tip',8,23.8,7.4,1.3,.4,1.2,'goldLight');
  star(m,'bishop_constellation_front',8,21.4,5.61,1.2,'cyan');for(const x of[5.2,10.8])B(m,'mitre_gold_border_'+x,x,21.1,5.75,.45,2.8,.35,'goldLight');
  for(const x of[6.5,9.5])B(m,'mitre_back_ribbon_'+x,x,16,11.6,.9,6,.4,'gold');
 }else{
  for(let i=0;i<12;i++){const a=i*Math.PI/6,x=8+Math.cos(a)*6.5,y=Math.max(9.6,14.6+Math.sin(a)*5.9),w=i%2?2.6:3.2;B(m,'lion_starlight_mane_root_'+i,x,y,9.7,w,3.2,4.7,i%2?'navy':'navyLight');B(m,'lion_mane_gold_vein_'+i,x,y,7.26,.55,2.5,.35,'gold');}
  for(const x of[5.6,10.4])B(m,'lion_upper_fang_'+x,x,11.4,2.7,.65,1.45,.6,'ivory');
  ringXY(m,'sun_crown_halo',8,17,6.5,.65,11.8,12.6,'gold');
  for(let i=0;i<7;i++){const x=2+i*2,y=20.2+(3-Math.abs(i-3))*.4;B(m,'sun_crown_ray_base_'+i,x,y,8.3,1.3,2.1,1.6,'gold');B(m,'sun_crown_ray_tip_'+i,x,y+1.3,8.3,.55,.8,.7,'goldLight');}
  B(m,'sun_crown_forehead',8,19.6,4.8,8.5,.8,1,'gold');star(m,'sun_king_brow_gem',8,19.6,4.15,.9,'cyan');
 }
 return m;
}
function astroWeapon(){
 const m=make('boss_archivist_astro_weapon');B(m,'orbit_action_pivot',8,8,6,2.4,2.4,5.4,'goldDark');B(m,'orbit_pivot_endcap',8,8,8,3.1,3.1,.7,'gold');
 for(const s of[-1,1]){B(m,'side_orbit_spindle_'+s,8+s*6.5,8,8,13.1,.65,.65,'goldDark');B(m,'side_orbit_vertical_'+s,8+s*13,8,11,.55,18,.55,'gold');}
 for(const[n,x,y]of[['lower_left',-5,0],['upper_left',-5,17],['lower_right',21,0],['upper_right',21,17]]){openBook(m,'turret_'+n,x,y,11,6.4,6.7);B(m,'turret_muzzle_'+n,x,y,9.3,1.25,1.25,1.5,'cyan');star(m,'turret_charge_'+n,x,y,8.45,.65,'ice');}
 // Suspended central astronomical staff is ahead of the body throughout its pitch arc.
 B(m,'astronomical_staff_shaft',8,15.5,-12,.85,19,.85,'gold');for(let i=0;i<5;i++)B(m,'staff_navy_grip_'+i,8,8+i*1.1,-12,1.2,.55,1.2,'navy');
 ringXY(m,'staff_astrolabe',8,23.7,3,.55,-12.5,-11.5,'goldLight');gem(m,'staff_cyan_nucleus',8,21.8,-12,1.1,3.3,'cyan');star(m,'staff_north_star',8,28,-12,.8,'goldLight');
 for(const x of[-5,21])for(const y of[4.3,11.5])star(m,'side_orbit_star_'+x+'_'+y,x,y,10.7,.65,'cyan');
 translateParts(m,e=>/^astronomical_staff_|^staff_/.test(e.name),[0,2,-1]);
 return m;
}

function novaCore(){
 const m=make('boss_novarion_core');
 for(const[x,n]of[[3.5,'right'],[12.5,'left']]){clawFoot(m,n+'_lion_paw',x,6.2,0,'blueFur',5);B(m,n+'_heel_joint',x,4.5,10,3.4,4.6,3.8,'goldDark');B(m,n+'_reversed_hock',x,7.8,11.1,3.5,5.1,3.7,'navy');B(m,n+'_forward_thigh',x,11.6,8.5,4.3,5.2,5.4,'blueFur');B(m,n+'_knee_armor',x,10.1,5.5,4.4,3.4,1.7,'gold');B(m,n+'_leg_plate',x,6,7.4,3.2,3.8,1.3,'ivory');star(m,n+'_knee_star',x,10.2,4.55,.65,'cyan');}
 octXZ(m,'lion_pelvis',8,8,5.5,4.1,12,15.4,'navy');octXZ(m,'tapered_abdominal_plate',8,8,4.9,3.7,15.1,18.1,'ivory');octXZ(m,'broad_constellation_cuirass',8,8,7.5,4.7,17.7,23.3,'navy');
 for(const s of[-1,1]){B(m,'platinum_pectoral_plate_'+s,8+s*4.2,21,3.55,5.9,4.5,1.3,'ivory');B(m,'gold_chest_seam_'+s,8+s*2.2,20.5,2.75,.6,4.7,.45,'gold');octXZ(m,'giant_shoulder_crown_'+s,8+s*8.4,8,3.3,4.4,22.3,26.2,'gold');B(m,'shoulder_navy_inset_'+s,8+s*8.6,24,3.45,4.5,2.4,.55,'navy');star(m,'shoulder_star_'+s,8+s*8.6,24,3.05,.8,'cyan');}
 octXY(m,'galaxy_core_socket',8,20.2,3.6,3.2,2.05,3.6,'goldDark');octXY(m,'swirling_galaxy_core',8,20.2,2.9,2.6,1.6,2.08,'galaxy');ringXY(m,'core_platinum_orbit',8,20.2,2.8,.32,1.25,1.65,'ivory');star(m,'galaxy_core_central_spark',8,20.2,1.06,1,'ice');
 for(let i=0;i<3;i++)B(m,'waist_lamellar_plate_'+i,8,16.1-i*.75,3.4,9.8-i,.55,1.3,'goldDark');
 octXZ(m,'king_neck_socket',8,8,3.1,2.9,23.3,25.4,'blueFur');B(m,'armillary_rear_pylon',8,24,17,2.2,2.5,11,'goldDark');B(m,'armillary_rear_axle',8,24,22,3.4,3.4,1.5,'gold');
 for(const s of[-1,1]){B(m,'rear_cape_panel_'+s,8+s*4.1,17,12.85,6.2,13.5,1.1,'robeStars');B(m,'rear_cape_gold_seam_'+s,8+s*7.1,17,12.95,.4,13.5,.3,'gold');}
 return m;
}
function novaWeapon(){
 const m=make('boss_novarion_weapon');
 m.swatch('nebulaGlassCyan','#00CFC5C8',{noise:false});m.swatch('nebulaGlassViolet','#BF8FE7C8',{noise:false});
 B(m,'armillary_rotation_pivot',8,8,8,3,3,3,'goldDark');B(m,'armillary_rear_spindle',8,8,13.5,1.4,1.4,12,'gold');
 // Rearward ring plane keeps its lower arc behind the cuirass at +/- attack pitch.
 ringXY(m,'grand_brass_armillary_outer',8,8,13,.8,15.4,16.4,'gold');tiltedRingXY(m,'grand_brass_armillary_inner',8,8,10.8,.85,16.8,.65,'brassWear',22.5);
 B(m,'armillary_equatorial_bar',8,8,16.4,27,.6,.6,'goldLight');B(m,'armillary_meridian_bar',8,8,16.4,.6,27,.6,'gold');
 octXY(m,'armillary_galactic_center',8,8,2.5,2.5,16.2,17.2,'galaxy');star(m,'armillary_core_star',8,8,15.98,1.3,'cyan');
 for(let i=0;i<8;i++){const a=i*Math.PI/4;star(m,'armillary_zodiac_'+i,8+Math.cos(a)*12.8,8+Math.sin(a)*12.8,15.1,.6,'goldLight');}
 // Four actual translucent crystal wings. Opaque frame rails leave the pane
 // open behind the alpha-200 glass rather than hiding it with a solid backing.
 for(const[s,n]of[[-1,'left'],[1,'right']])for(const[upper,u]of[['upper',1],['lower',-1]]){
  for(let i=0;i<6;i++){const x=8+s*(10.1+i*1.7),y=8+u*(3+i*1.6),h=6.1-i*.55,z=14.5+(i%2)*.7;
   const wingRot={rotation:R('z',s*u*22.5,[x,y,z])};
   for(const side of[-1,1]){
    B(m,n+'_'+upper+'_wing_rib_'+i+'_'+side,x+side*.96,y,z-.5,.24,h+.24,.6,'goldDark',wingRot);
    B(m,n+'_'+upper+'_wing_cap_'+i+'_'+side,x,y+side*h*.5,z-.5,2.16,.24,.6,'gold',wingRot);
   }
   B(m,n+'_'+upper+'_nebula_feather_'+i,x,y,z-.5,1.8,h,1.25,i%2?'nebulaGlassViolet':'nebulaGlassCyan',wingRot);
   B(m,n+'_'+upper+'_crystal_glint_'+i,x-s*.28,y+u*.5,z-1.22,.3,h*.68,.15,'ice',{rotation:R('z',s*u*22.5,[x,y,z])});
  }
 }
 // Extra rearward ring clearance is required at -70/-65, beyond the rendered
 // -60 windup. The extended spindle keeps every ring and wing attached.
 translateParts(m,e=>!['armillary_rotation_pivot','armillary_rear_spindle'].includes(e.name),[0,0,3]);
 B(m,'lance_arm_rear_shoulder_link',-.2,8,8,17,.9,.9,'goldDark');collarX(m,'lance_shoulder',-7.3,8,8,2.25,'gold');B(m,'lance_upper_arm',-7.3,5.2,5.4,3.9,6.1,5.8,'navy');B(m,'lance_gauntlet',-7.3,2.6,1.3,4.6,4.5,4,'gold');
 B(m,'constellation_lance_shaft',-7.3,7.8,0.8,1,35,1,'goldDark');for(let i=0;i<5;i++)B(m,'lance_grip_binding_'+i,-7.3,.3+i*1.1,.8,1.5,.55,1.5,'goldLight');
 gem(m,'lance_starlight_spearhead',-7.3,23.8,.8,2.2,7.6,'cyan');B(m,'lance_crossguard',-7.3,23.7,.8,7,.7,1.6,'gold');
 for(const s of[-1,1])B(m,'lance_guard_hook_'+s,-7.3+s*3,24.8,.8,1,2.7,1.1,'goldLight');
 return m;
}

export function buildAstralMonsters(){
 const models=[...mimic(),...sentinel(),...librarian(),...golem(),astroCore(),catHead('boss_archivist_astro_head'),astroWeapon(),novaCore(),catHead('boss_novarion_head',true),novaWeapon()];
 for(const m of models)m.save();
 return structuredClone(ASTRAL_RIGS);
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const rigs=buildAstralMonsters();console.log(JSON.stringify({built:'astral archive',rigs},null,2));
}
