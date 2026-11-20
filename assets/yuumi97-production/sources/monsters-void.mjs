/** Chapter III: six original socket-assembled cathedral/void monsters, 14 parts.
 * Every body/core is floor-rooted at [8,0,8]. Moving part hinge is [8,8,8].
 * Pitch is semantic negative-up; the shared rig importer applies Rx(-pitch).
 */
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Model,shade} from '../tools/asset-kit.mjs';

const C={white:'#F8F9FA',ivory:'#DBE5E9',silver:'#B8CFDC',steel:'#71879D',shadow:'#37415D',
  void:'#10002B',black:'#21162E',violet:'#9D4EDD',purple:'#7209B7',magenta:'#F72585',pink:'#F3B7E8',
  ether:'#CAF0F8',cyan:'#7ED9EA',gold:'#EBC77F',goldLight:'#FFF0BF',goldDark:'#95704E',
  robe:'#483169',robeDark:'#291B40',velvet:'#71406F',velvetLight:'#A66B9E',sun:'#FFE398',moon:'#C5ACED'};
const rnd=v=>Math.round(v*10000)/10000;
const r=(axis,angle,origin=[8,8,8])=>({axis,angle,origin,rescale:false});
function b(m,n,a,z,t,rotation,faces){return m.box(n,a.map(rnd),z.map(rnd),t,{...(rotation?{rotation}:{}),...(faces?{faces}:{})});}
function model(id){
  const m=new Model(id,128);
  for(const [name,c]of Object.entries(C)){m.swatch(name,c,{noise:false,kind:name.includes('robe')||name.includes('velvet')?'cloth':undefined});const{x,y}=m.swatches[name];
    if(['white','ivory','silver','steel','gold','goldDark'].includes(name)){m.canvas.line(x+1,y+1,x+13,y+1,shade(c,18));m.canvas.line(x+1,y+2,x+1,y+13,shade(c,12));m.canvas.line(x+13,y+7,x+13,y+14,shade(c,-20));}
    if(['violet','purple','magenta','ether'].includes(name)){m.canvas.line(x+2,y+13,x+10,y+3,shade(c,24));m.canvas.line(x+10,y+3,x+13,y+7,shade(c,10));}
  }
  m.display.gui={rotation:[18,-30,0],translation:[0,-1,0],scale:[.50,.50,.50]};return m;
}
function oct(m,n,x,y,z,w,h,d,t,faces){const c=Math.min(w,d)*.19;
  b(m,n+'_middle',[x-w/2+c,y,z-d/2],[x+w/2-c,y+h,z+d/2],t,undefined,faces);
  b(m,n+'_left',[x-w/2,y,z-d/2+c],[x-w/2+c,y+h,z+d/2-c],t,undefined,faces);
  b(m,n+'_right',[x+w/2-c,y,z-d/2+c],[x+w/2,y+h,z+d/2-c],t,undefined,faces);
}
function crystal(m,n,x,y,z,w,h,main='violet',rotation){
  for(const[i,lo,hi,k]of[[0,0,.13,.22],[1,.13,.28,.6],[2,.28,.78,1],[3,.78,.93,.62],[4,.93,1,.18]])
    b(m,n+'_facet'+i,[x-w*k/2,y+h*lo,z-w*k/2],[x+w*k/2,y+h*hi,z+w*k/2],main,rotation,{north:main==='ether'?'white':'pink',west:main==='ether'?'cyan':'violet',east:main,south:'purple',up:main==='ether'?'white':'pink',down:'void'});
}
function line(m,n,a,z,w,t,step=.7){const d=Math.hypot(...a.map((v,i)=>v-z[i])),k=Math.max(1,Math.ceil(d/step));for(let i=0;i<=k;i++){const p=a.map((v,j)=>v+(z[j]-v)*i/k);b(m,n+'_'+i,p.map((v,j)=>v-(Array.isArray(w)?w[j]:w)/2),p.map((v,j)=>v+(Array.isArray(w)?w[j]:w)/2),t);}}
function ringXY(m,n,cx,cy,rad,width,z,depth,t,cut=()=>true){let i=0;
  for(let y=Math.floor(cy-rad-width);y<cy+rad+width;y+=.65){let start=null;for(let x=Math.floor(cx-rad-width);x<=cx+rad+width+.65;x+=.65){const d=Math.hypot(x+.325-cx,y+.325-cy),ok=d>=rad-width/2&&d<=rad+width/2&&cut(x+.325,y+.325);if(ok&&start===null)start=x;if(!ok&&start!==null){b(m,n+'_'+i++,[start,y,z],[x,y+.65,z+depth],t);start=null;}}}
}
function stud(m,n,x,y,z,t='gold'){b(m,n,[x-.22,y-.22,z-.16],[x+.22,y+.22,z+.16],t,r('z',45,[x,y,z]));}
function hinge(m,n,x=8){oct(m,n+'_sleeve',x,7.2,8,2.2,1.6,2.2,'steel');b(m,n+'_front_disc',[x-.66,7.34,6.82],[x+.66,8.66,7.22],'silver');stud(m,n+'_pin',x,8,6.72,'violet');}
function plate(m,n,a,z,t='white'){b(m,n,a,z,t);const h=z[1]-a[1];if(h>1)b(m,n+'_top_edge',[a[0]+.12,z[1]-.32,a[2]-.09],[z[0]-.12,z[1],a[2]+.08],t==='void'?'purple':'silver');}
function shiftNamed(m,match,delta){for(const e of m.elements)if(match.test(e.name)){for(const key of ['from','to'])e[key]=e[key].map((v,i)=>rnd(v+delta[i]));if(e.rotation)e.rotation.origin=e.rotation.origin.map((v,i)=>rnd(v+delta[i]));}}
function maskXY(m,n,polygon,z,depth,t,step=1.0,skip=()=>false){
  const minX=Math.min(...polygon.map(p=>p[0])),maxX=Math.max(...polygon.map(p=>p[0])),minY=Math.min(...polygon.map(p=>p[1])),maxY=Math.max(...polygon.map(p=>p[1]));
  const inside=(x,y)=>{let q=false;for(let i=0,j=polygon.length-1;i<polygon.length;j=i++){const a=polygon[i],c=polygon[j];if((a[1]>y)!==(c[1]>y)&&x<(c[0]-a[0])*(y-a[1])/(c[1]-a[1])+a[0])q=!q;}return q;};
  let k=0;for(let y=minY;y<maxY;y+=step)for(let x=minX;x<maxX;x+=step){if(!inside(x+step/2,y+step/2)||skip(x,y))continue;b(m,n+'_'+k++,[x,y,z],[Math.min(x+step*.94,maxX),Math.min(y+step*.94,maxY),z+depth],typeof t==='function'?t(x,y):t);}
}
function prismWing(m,n,points,z,colors=['magenta','violet','gold'],cell=1.4){
  maskXY(m,n+'_stained_glass',points,z,.55,(x,y)=>colors[Math.abs(Math.floor((x+y)/3))%colors.length],cell,(x,y)=>Math.abs((x-y)%11)<.48);
  for(let i=0;i<points.length;i++)line(m,n+'_lead_edge'+i,[...points[i],z+.28],[...points[(i+1)%points.length],z+.28],[.43,.43,.85],'shadow',.5);
  const base=points[0];for(let i=1;i<points.length-1;i+=2)line(m,n+'_platinum_rib'+i,[...base,z-.12],[...points[i],z-.12],[.29,.29,.20],'silver',.6);
}

function stalkerBody(){
  const m=model('mob_mirror_stalker_body');
  for(const s of[-1,1]){const x=8+s*4.2;
    oct(m,'long_split_foot_'+s,x,0,5.3,3.1,1.2,7.6,'void');
    for(let i=0;i<3;i++)b(m,'platinum_toe_'+s+'_'+i,[x-1.28+i*.87,.25,1.3],[x-.73+i*.87,.8,3.2],'silver');
    b(m,'slanted_shin_'+s,[x-1.1,1.1,7],[x+1.1,7.3,9.1],'white',r('x',-22.5,[x,1.1,8]));
    oct(m,'angular_knee_'+s,x,6.4,8,3.3,2.2,3.8,'steel');
    b(m,'hunched_thigh_'+s,[x-1.4,7.3,8],[x+1.4,11.7,11.0],'void',r('x',22.5,[x,8,8]));
  }
  oct(m,'lean_pelvis',8,9.3,9.8,8.6,3.2,6,'shadow');
  b(m,'forward_hunched_spine',[4.9,10.4,5.7],[11.1,16.7,11.5],'void',r('x',-22.5,[8,11,8]));
  for(let i=0;i<4;i++){const y=11.0+i*1.25,z=5.3-i*.35;plate(m,'white_rib'+i,[3.7,y,z],[12.3,y+.80,z+1.6],'white');}
  for(const s of[-1,1]){
    oct(m,'fixed_shoulder_socket_'+s,8+s*6.2,12.5,6.4,3.3,2.0,3.8,'steel');
    plate(m,'overlapping_shoulder_'+s,[8+s*6.2-1.8,14.1,4.6],[8+s*6.2+1.8,15.3,8.4],'white');
  }
  oct(m,'predatory_head',8,16,2.8,7.2,3.0,6.8,'void');
  plate(m,'beaked_mirror_mask',[5.0,17.0,-.8],[11.0,19.5,1.0],'silver');
  for(const s of[-1,1]){b(m,'sharp_eye_'+s,[8+s*1.8-.65,17.5,-1.05],[8+s*1.8+.65,18.1,-.79],'magenta');b(m,'swept_temporal_horn_'+s,[8+s*3.4-.40,18.5,1.5],[8+s*3.4+.40,22,2.6],'white',r('z',s*-22.5,[8+s*3.4,18.5,2]));}
  for(const[i,x,y,z,w,h]of[[0,8,12.2,12.2,3.1,8],[1,4.3,11.8,11.6,2.4,6.5],[2,11.7,11.8,11.6,2.4,6.5],[3,8,10.8,14.4,2.1,6.4]])crystal(m,'reflective_back_mirror'+i,x,y,z,w,h,'ether',r('x',22.5,[x,y,z]));
  for(let i=0;i<4;i++)stud(m,'spine_amethyst_'+i,8,11+i*1.3,11.9,'violet');return m;
}
function stalkerArm(){
  const m=model('mob_mirror_stalker_arm');
  // Local prism-only translucency. The joint, exoskeleton and cutting rail stay opaque.
  for(const [name,rgba]of Object.entries({glassMagenta:[247,37,133,208],glassPink:[243,183,232,208],glassViolet:[157,78,221,208],glassPurple:[114,9,183,208]}))m.swatch(name,rgba,{noise:false});
  for(const s of[-1,1]){const x=8+s*6.2;hinge(m,'mantis_hinge_'+s,x);
    line(m,'upper_exoskeleton_'+s,[x,8,8],[8+s*8,4,3.5],[1.45,1.45,1.45],'void');
    line(m,'white_upper_plate_'+s,[x,7.7,7.3],[8+s*8,4.1,3],[.7,.8,.8],'white');
    oct(m,'sharp_elbow_'+s,8+s*8,3.3,3.4,2.2,1.5,2.6,'steel');
    for(let i=0;i<10;i++){const t=i/9,z=3-t*7.4,y=3.8+t*.4,xp=8+s*(8+2.4*Math.sin(t*Math.PI));const w=1.4*(1-t)+.16;
      b(m,'mantis_prism_blade_'+s+'_'+i,[xp-w,y-.45,z-1.0],[xp+w,y+.45,z+.65],'glassMagenta',undefined,{north:'glassPink',up:'glassPink',east:'glassViolet',west:'glassMagenta',down:'glassPurple'});
      b(m,'mantis_white_cutting_edge_'+s+'_'+i,[xp-w*.25,y+.45,z-.94],[xp+w*.25,y+.66,z+.60],'ether');
    }
    for(let i=0;i<3;i++)crystal(m,'blade_socket_shard_'+s+'_'+i,x+s*(1+i*.6),4.5+i*.7,4.2,.7,2.7,'violet',r('z',s*22.5,[x,5,4.2]));
  }return m;
}
function choirBody(){
  const m=model('mob_void_choir_body');
  // Floating hem's reference shard reaches Y=0; no fake pedestal.
  for(let i=0;i<9;i++){const x=1.7+i*1.55,h=3.0+(i%3)*.8,y=i===4?0:.7+(i%2)*.5;crystal(m,'broken_stained_hem_'+i,x,y,8+(i%2?1.3:-.5),1.3,h,'violet');}
  oct(m,'purple_inner_robe',8,3.0,8,11.5,9.6,8.6,'robeDark');
  for(let i=0;i<7;i++){const x=2.9+i*1.7;plate(m,'ivory_robe_fold_'+i,[x,4.1+(i%2)*.6,3.45],[x+1.15,12.4,4.3],i%2?'ivory':'white');}
  for(const s of[-1,1]){b(m,'white_outer_stole_'+s,[8+s*4.3-1.2,7,4.0],[8+s*4.3+1.2,18.7,11.4],'white');b(m,'stole_gold_braid_'+s,[8+s*4.3-.15,8,3.8],[8+s*4.3+.15,18.1,4.02],'gold');}
  oct(m,'high_priest_torso',8,12.3,8,8.3,6.2,6.9,'velvet');
  for(const s of[-1,1])oct(m,'body_choir_socket_'+s,8+s*5,14.1,8,2.8,1.8,3.0,'silver');
  b(m,'silver_chest_fan',[5.0,15.4,4.1],[11.0,17.2,4.7],'silver');
  oct(m,'white_choir_hood',8,18.1,8.5,8.2,7.0,7.0,'white');
  b(m,'hood_deep_face_aperture',[5.2,19,4.7],[10.8,24,5.4],'void');
  b(m,'silver_silent_mask',[5.65,19.5,4.2],[10.35,23.6,4.73],'silver');
  for(const s of[-1,1])b(m,'mask_closed_eye_'+s,[8+s*1.1-.65,22.3,4.04],[8+s*1.1+.65,22.52,4.23],'void');
  b(m,'mask_vertical_tear',[7.82,19.7,4.0],[8.18,21.65,4.23],'violet');
  for(const s of[-1,1])line(m,'hood_gold_arch_'+s,[8,25.0,5.0],[8+s*3.5,21.5,5.0],[.36,.36,.4],'gold',.45);
  for(let i=0;i<5;i++)stud(m,'vestment_diamond_'+i,8,7+i*1.5,3.38,i%2?'silver':'violet');return m;
}
function choirArm(){
  const m=model('mob_void_choir_arm');
  for(const s of[-1,1]){hinge(m,'sleeve_hinge_'+s,8+s*5);
    line(m,'ivory_bell_sleeve_root_'+s,[8+s*5,7.8,7.4],[8+s*7.8,7.8,7.4],[1.2,1.4,1.4],'white');
    line(m,'ivory_bell_sleeve_upper_'+s,[8+s*7.8,7.8,7.4],[8+s*7.8,10,-1],[1.6,2.1,2.2],'white');
    line(m,'ivory_bell_sleeve_forearm_'+s,[8+s*7.8,10,-1],[8+s*3.6,7,-4.1],[1.8,2.1,2.2],'white');
    oct(m,'purple_cuff_'+s,8+s*3.6,3.6,-2.7,2.1,1.8,2.4,'purple');
    b(m,'silver_support_hand_'+s,[8+s*3.5-.65,4.1,-4.5],[8+s*3.5+.65,5.0,-1.9],'silver');
  }
  // Raised rear halo stays behind the torso through the wind-up sweep.
  ringXY(m,'broken_glass_halo',8,22.4,6.5,1.2,15.9,.8,'violet',(x,y)=>!(x<5&&y>22.4)&&!(x>11&&y<20.4));
  ringXY(m,'halo_platinum_rim',8,22.4,7.2,.40,15.7,.35,'silver',(x,y)=>!(x<4&&y>23.4)&&!(x>12&&y<19.4));
  for(const s of[-1,1])line(m,'halo_back_support_'+s,[8+s*5,8,8],[8+s*6.5,20.6,16.3],[.55,.55,.55],'shadow');
  for(let i=0;i<5;i++)crystal(m,'halo_broken_shard_'+i,2.8+i*2.6,27.5+(i%2)*.4,16.2,.75,2.3,'magenta');
  // Small choir book supports the resonating chalice; page halves are real solids.
  b(m,'choir_book_cover',[2.8,3.4,-6.7],[13.2,4.0,-2.1],'robe');
  for(const s of[-1,1])b(m,'open_choir_pages_'+s,[s<0?3.1:8.05,4.0,-6.5],[s<0?7.95:12.9,4.55,-2.3],'ivory',r('z',s*22.5,[8,4,-4.4]));
  oct(m,'chalice_foot',8,4.7,-5.2,3.1,.55,3.1,'gold');b(m,'chalice_stem',[7.55,5.25,-5.65],[8.45,8.2,-4.75],'silver');
  oct(m,'chalice_void_cup',8,8.1,-5.2,4.7,2.3,4.7,'purple');
  oct(m,'chalice_inner_resonance',8,10.4,-5.2,3.7,.25,3.7,'magenta');
  crystal(m,'chalice_suspended_note',8,10.9,-5.2,1.35,3.6,'violet');
  for(const s of[-1,1])line(m,'chalice_gold_handle_'+s,[8+s*2.2,9.8,-5.2],[8+s*3.0,8.7,-5.2],[.35,.35,.35],'gold');
  // Held ensemble clears the front of the robe even at the full +55-degree strike.
  shiftNamed(m,/^(choir_book|open_choir|chalice|purple_cuff|silver_support_hand)/,[0,2.5,-1.5]);return m;
}
function oculusBody(){
  const m=model('mob_abyssal_oculus_body');
  oct(m,'vertical_void_eyeball',8,5.2,8,17.0,15.6,9.0,'void');
  oct(m,'faceted_upper_lid',8,20.8,8,11.4,3.5,7.2,'purple');
  oct(m,'faceted_lower_lid',8,2.2,8,11.4,3.1,7.2,'purple');
  ringXY(m,'gothic_platinum_eyelid',8,13.2,10.7,1.55,3.2,1.5,'silver');
  ringXY(m,'inner_amethyst_iris_ring',8,13.2,7.7,1.2,2.75,.6,'violet');
  for(let y=6.5;y<19.4;y+=1.2){const w=Math.sqrt(Math.max(0,7.1**2-(y+.6-13.2)**2));b(m,'violet_iris_'+y,[8-w,y,2.6],[8+w,y+1.2,3.15],'purple');}
  for(let i=0;i<12;i++){const a=i*Math.PI/6;line(m,'iris_radial_fiber_'+i,[8+Math.sin(a)*3.2,13.2+Math.cos(a)*3.2,2.42],[8+Math.sin(a)*6.5,13.2+Math.cos(a)*6.5,2.42],[.4,.4,.2],i%2?'violet':'magenta',.5);}
  b(m,'vertical_black_pupil',[6.9,6.9,2.05],[9.1,19.5,2.6],'void');
  b(m,'pupil_lower_point',[7.5,5.9,2.08],[8.5,6.9,2.6],'void');b(m,'pupil_upper_point',[7.5,19.5,2.08],[8.5,20.5,2.6],'void');
  b(m,'eye_reflection_tall',[4.7,15.2,1.91],[5.5,18.2,2.22],'ether');b(m,'eye_reflection_small',[10.7,10.1,1.91],[11.6,10.9,2.22],'pink');
  for(const s of[-1,1]){
    crystal(m,'outer_gothic_spike_'+s,8+s*10.7,9.0,7,2.3,8.4,'ether');
    b(m,'eye_side_clamp_'+s,[8+s*9.5-1.3,11.8,6.0],[8+s*9.5+1.3,15.5,10.0],'silver');
  }
  crystal(m,'lower_gothic_keystone',8,0,7.5,3.1,4.7,'ether');crystal(m,'upper_gothic_keystone',8,23.0,7.5,3.0,4.6,'ether');
  b(m,'rear_tentacle_socket_beam',[-.3,13.2,9.3],[16.3,14.8,11.3],'shadow');
  for(let i=0;i<5;i++)crystal(m,'violet_rear_shell_'+i,2+i*3,6+(i%2)*3,11.6,2.5,9,'violet');return m;
}
function oculusArm(){
  const m=model('mob_abyssal_oculus_arm');
  b(m,'six_tentacle_anchor_beam',[-10.1,7.3,7.2],[26.1,8.7,8.8],'shadow');
  for(let i=0;i<6;i++){
    const s=i<3?-1:1,k=i%3,startX=8+s*(13.2+k*1.8);hinge(m,'tentacle_root_'+i,startX);
    const pts=[];
    for(let j=0;j<=13;j++){const t=j/13,x=startX+s*(4.8-k*.5)*Math.sin(t*Math.PI*.75),y=8-(5.4+k*1.15)*t+.6*Math.sin(t*Math.PI*2),z=8-(5.6+k*1.7)*t+1.0*Math.sin(t*Math.PI);pts.push([x,y,z]);}
    for(let j=0;j<pts.length-1;j++){const w=1.4-j*.068;line(m,'tentacle_'+i+'_segment_'+j,pts[j],pts[j+1],[w,w,w],j%3===0?'silver':j%2?'purple':'void',.65);if(j%3===1){const p=pts[j];b(m,'tentacle_'+i+'_arcane_vent_'+j,[p[0]-.25,p[1]-.20,p[2]-.85],[p[0]+.25,p[1]+.20,p[2]-.63],'magenta');}}
    const p=pts.at(-1);crystal(m,'tentacle_'+i+'_crystal_tip',p[0],p[1]-.5,p[2],.75,2.0,'violet',r('x',-45,p));
  }return m;
}
function templarBody(){
  const m=model('mob_eclipse_templar_body');
  for(const s of[-1,1]){const x=8+s*3.5;oct(m,'knight_sabatons_'+s,x,0,6,4.8,2.0,7.4,s<0?'white':'void');
    plate(m,'knight_shin_'+s,[x-1.65,2,6.0],[x+1.65,8.4,10],s<0?'white':'shadow');oct(m,'knight_knee_'+s,x,7.7,7.5,4.3,2.1,4.3,'silver');
    b(m,'knight_thigh_'+s,[x-1.65,9.4,6.4],[x+1.65,13.6,10.4],'shadow');}
  oct(m,'eclipse_waist',8,12.5,8,10.9,2.4,7,'steel');
  b(m,'marble_left_chest',[1.9,14.9,4.1],[8,21.8,11.8],'white');b(m,'corrupted_right_chest',[8,14.9,4.1],[14.1,21.8,11.8],'void');
  for(let i=0;i<5;i++){const y=15.4+i*1.22;b(m,'layered_breast_fillet_'+i,[2.3,y,3.89],[13.7,y+.28,4.14],i%2?'silver':'steel');}
  for(const s of[-1,1]){oct(m,'knight_fixed_shoulder_'+s,8+s*7,19.0,7,4.4,2.0,5.5,s<0?'ivory':'purple');b(m,'pauldron_outer_'+s,[8+s*7-2.4,21.0,4.5],[8+s*7+2.4,22.0,9.5],'white');}
  for(let i=0;i<5;i++)crystal(m,'right_corruption_spire_'+i,11.0+i*1.6,17.3+(i%2)*1.2,8.2+(i%2)*2.4,1.5,6.0,'violet',r('z',-22.5,[11+i*1.6,17,8]));
  oct(m,'high_gorget',8,21.8,8,7.0,1.9,6.2,'silver');oct(m,'knight_helm',8,23.7,8,7.4,5.7,7.0,'white');
  b(m,'black_T_visor',[5.0,26.5,4.32],[11.0,27.4,4.61],'void');b(m,'visor_vertical',[7.5,24.3,4.30],[8.5,27.4,4.62],'void');
  b(m,'violet_eye_line',[5.4,26.7,4.16],[10.6,27.05,4.33],'magenta');b(m,'helmet_center_crest',[7.7,29.4,5.8],[8.3,31.1,9.7],'silver');
  for(let i=0;i<7;i++){const x=1.6+i*1.9,base=2.8+(i%3)*1.25;b(m,'torn_purple_cape_'+i,[x,base,12.15],[x+1.65,20.2,13.0],i%2?'velvet':'robeDark');b(m,'cape_fold_'+i,[x+.5,base+.8,13],[x+.77,19.7,13.24],'velvetLight');}
  ringXY(m,'chest_eclipse_rim',8,18.2,1.4,.45,3.45,.35,'silver');b(m,'chest_black_eclipse',[7.2,17.4,3.3],[8.8,19.0,3.47],'void');return m;
}
function templarArm(){
  const m=model('mob_eclipse_templar_arm');
  for(const s of[-1,1]){hinge(m,'knight_shoulder_hinge_'+s,8+s*7);
    line(m,'armored_upper_arm_'+s,[8+s*7,8,8],[8+s*8.4,8,1.0],[2.1,2.2,2.1],s<0?'white':'purple');
    line(m,'platinum_forearm_'+s,[8+s*8.4,8,1.0],[8+s*2,8.5,-4.1],[1.65,1.65,1.65],'silver');
    b(m,'twohand_gauntlet_'+s,[8+s*1.8-.75,5.35,-2.65],[8+s*1.8+.75,7.1,-.6],'white');
  }
  b(m,'eclipse_grip',[7.5,4.5,-2.55],[8.5,10.2,-1.45],'void');
  for(let y=5;y<10;y+=1)b(m,'grip_silver_wrap_'+y,[7.4,y,-2.67],[8.6,y+.28,-1.33],'silver');
  b(m,'platinum_crossguard',[1.8,3.65,-3.1],[14.2,5.1,-.9],'silver');
  for(const s of[-1,1])b(m,'crossguard_upturned_'+s,[8+s*5.2-.55,4.3,-3],[8+s*5.2+.55,6.1,-1],'white',r('z',s*22.5,[8+s*5.2,4.3,-2]));
  for(const[i,y0,y1,w]of[[0,-6,-4,.2],[1,-4,-1,1.5],[2,-1,3.65,2.8]]){
    b(m,'void_greatsword_blade_'+i,[8-w,y0,-3],[8+w,y1,-1],'void');
    b(m,'blade_amethyst_fuller_'+i,[8-w*.24,y0,-3.2],[8+w*.24,y1,-2.99],'violet');
    b(m,'platinum_blade_edge_'+i,[8-w,y0,-3.1],[8-w+.22,y1,-2.9],'silver');
  }
  ringXY(m,'weapon_eclipse_ring',8,5.2,1.8,.45,-3.2,.35,'violet');b(m,'weapon_black_sun',[7.0,4.2,-3.38],[9.0,6.2,-3.19],'void');
  shiftNamed(m,/^(eclipse_grip|grip_|platinum_crossguard|crossguard_|void_greatsword|blade_|platinum_blade|weapon_|twohand_gauntlet)/,[0,2.5,-2.5]);return m;
}

function archonCore(){
  const m=model('boss_twin_archons_core');
  for(let i=0;i<7;i++){const x=-.8+i*2.6,t=x<8?'ivory':'robeDark',bottom=i%2?.5:0;
    b(m,'split_royal_hem_'+i,[x,bottom,3.8],[x+2.35,8.4,12.8],t);b(m,'split_hem_embroidery_'+i,[x+.35,bottom+.3,3.63],[x+2,1.2+bottom,3.81],x<8?'gold':'violet');}
  for(const s of[-1,1]){const a=s<0?[1.3,8.3,5.0]:[8,8.3,5.0],z=s<0?[8,18.8,12.0]:[14.7,18.8,12.0];plate(m,'twin_breastplate_'+s,a,z,s<0?'white':'void');
    oct(m,'twin_pauldron_'+s,8+s*7.5,17.4,8,5.2,3.2,6.8,s<0?'gold':'purple');
    b(m,'twin_waist_stole_'+s,[8+s*2.4-.8,3.5,3.7],[8+s*2.4+.8,16.3,4.4],s<0?'sun':'velvet');}
  b(m,'royal_center_spine',[7.55,7.7,3.5],[8.45,21.8,4.8],'silver');
  for(let i=0;i<5;i++){stud(m,'sun_robe_gem'+i,4.7,5.6+i*2.6,3.44,'goldLight');stud(m,'moon_robe_gem'+i,11.3,5.6+i*2.6,3.44,'violet');}
  ringXY(m,'sun_pectoral_medallion',4.8,17.8,1.60,.45,4.65,.24,'gold');
  for(let i=0;i<8;i++){const a=i*Math.PI/4;line(m,'sun_pectoral_ray_'+i,[4.8+Math.sin(a)*1.9,17.8+Math.cos(a)*1.9,4.63],[4.8+Math.sin(a)*2.6,17.8+Math.cos(a)*2.6,4.63],[.22,.22,.25],'goldLight',.45);}
  ringXY(m,'moon_pectoral_crescent',11.2,17.8,1.8,.62,4.65,.24,'violet',(x,y)=>x>10.3);
  b(m,'moon_pectoral_star',[9.7,17.3,4.5],[10.7,18.3,4.75],'moon',r('z',45,[10.2,17.8,4.6]));
  for(const s of[-1,1])for(let i=0;i<3;i++){
    const x=8+s*7.5;
    b(m,'layered_pauldron_fillet_'+s+'_'+i,[x-2.35,18.7+i*.72,4.43],[x+2.35,18.98+i*.72,4.69],s<0?'goldLight':'moon');
    b(m,'royal_collar_ridge_'+s+'_'+i,[8+s*(1.7+i*.9)-.24,20.5,4.3],[8+s*(1.7+i*.9)+.24,22.7,5.25],s<0?'gold':'silver');
  }
  oct(m,'twin_high_gorget',8,20.5,8,8.6,2.5,6.8,'silver');oct(m,'twin_neck_contact',8,23,8,4.3,.6,4.3,'shadow');
  for(let i=0;i<6;i++){
    b(m,'rear_split_cape_'+i,[.2+i*2.6,2.6+(i%2)*.8,13],[2.5+i*2.6,19.3,13.7],i<3?'ivory':'robe');
    b(m,'cape_embroidered_river_'+i,[1.1+i*2.6,3.7+(i%2)*.8,13.7],[1.40+i*2.6,18.5,13.9],i<3?'gold':'violet');
    for(let j=0;j<3;j++)b(m,'cape_brocade_'+i+'_'+j,[.8+i*2.6,5.0+j*4,13.9],[1.7+i*2.6,5.7+j*4,14.07],i<3?'silver':'velvetLight');
  }return m;
}
function archonHead(){
  const m=model('boss_twin_archons_head');
  oct(m,'neck_pivot_contact',8,8,8,4.3,1.1,4.3,'shadow');
  b(m,'sun_half_mask',[3.4,9.1,4.6],[8,17.6,11.4],'ivory');b(m,'moon_half_mask',[8,9.1,4.6],[12.6,17.6,11.4],'void');
  b(m,'mask_center_seam',[7.7,9.3,4.35],[8.3,18.3,4.65],'silver');
  b(m,'sun_eye',[4.4,14,4.20],[6.9,14.65,4.6],'goldLight');b(m,'moon_eye',[9.1,14,4.20],[11.6,14.65,4.6],'magenta');
  for(const s of[-1,1])b(m,'sharp_mask_cheek_'+s,[8+s*3.7-.7,9.7,4.15],[8+s*3.7+.7,12.2,5.4],s<0?'white':'purple',r('z',s*22.5,[8+s*3.7,10.5,5]));
  for(let i=0;i<6;i++){
    const x=2.9-i*1.05,y=16.3+i*.68;
    line(m,'sun_crown_ray_'+i,[4.0,17.2,8.1],[x,Math.min(23.5,y+3.9),8.1],[.65,.65,.85],i%2?'gold':'goldLight',.5);
  }
  for(let i=0;i<6;i++){const x=12.3+i*.45,y=16.5+i*.9,z=8.2;crystal(m,'crescent_moon_horn_'+i,x,y,z,1.8-i*.18,2.5,'violet');}
  b(m,'archon_crown_band',[3.1,17.1,4.7],[12.9,18.4,11.4],'silver');stud(m,'crown_split_gem',8,18.1,4.36,'violet');return m;
}
function archonWeapon(){
  const m=model('boss_twin_archons_weapon');
  for(const s of[-1,1]){hinge(m,'archon_shoulder_hinge_'+s,8+s*7.5);
    line(m,'archon_forearm_'+s,[8+s*7.5,8,8],[8+s*11.6,3.5,2.8],[1.8,1.9,1.9],s>0?'white':'void');
    oct(m,'archon_gauntlet_'+s,8+s*7.5,2.6,2.4,2.6,2.2,3.0,s>0?'gold':'silver');
  }
  b(m,'radiant_rapier_grip',[14.95,1.0,1.6],[16.05,6.3,2.7],'goldDark');
  b(m,'radiant_rapier_guard',[12.5,1.0,1.1],[18.5,2.0,3.2],'gold');
  for(const[i,y0,y1,w]of[[0,-8,-5,.16],[1,-5,-1,.45],[2,-1,1,.70]]){b(m,'white_light_rapier_'+i,[15.5-w,y0,1.8],[15.5+w,y1,2.5],'white');b(m,'rapier_gold_edge_'+i,[15.5-w,y0,1.65],[15.5-w+.12,y1,1.82],'gold');}
  oct(m,'left_void_orb',.5,1.7,0.0,4.4,4.4,4.4,'void');ringXY(m,'orb_fracture_rim',.5,3.9,2.55,.52,-.55,.48,'magenta');
  b(m,'orb_vertical_rift',[.25,1.9,-2.4],[.75,5.9,-2.0],'violet');
  for(const s of[-1,1]){
    const pts=[[8+s*8.3,12],[8+s*11.6,26.8],[8+s*18.8,23.8],[8+s*14.5,19.2],[8+s*17.2,15.7],[8+s*10.3,13.0]];
    prismWing(m,'paired_prism_wing_'+s,pts,14.3,s<0?['sun','white','gold']:['violet','magenta','moon']);
    line(m,'wing_shoulder_strut_root_'+s,[8+s*7.5,8,8],[8+s*9,8,9],[.75,.75,.75],'silver');
    line(m,'wing_shoulder_strut_'+s,[8+s*9,8,9],[8+s*8.3,12,14.3],[.75,.75,.75],'silver');
  }
  shiftNamed(m,/^(radiant_rapier|white_light_rapier|rapier_gold|archon_gauntlet_1_)/,[4.1,0,0]);
  shiftNamed(m,/^(left_void_orb|orb_|archon_gauntlet_-1_)/,[-4.1,0,0]);return m;
}
function eclipsiaCore(){
  const m=model('boss_eclipsia_core');
  // Fluted cathedral skirt has an irregular real silhouette and a continuous floor edge.
  for(let i=0;i<11;i++){const x=-3.7+i*2.15,y=i%3===0?0:.3,front=2.2+Math.abs(i-5)*.28;
    b(m,'abyssal_dress_pleat_'+i,[x,y,front],[x+1.9,10.2,14.4],i%2?'robeDark':'void');
    b(m,'star_river_in_pleat_'+i,[x+.80,1.1,front-.13],[x+1.07,9.3,front+.02],i%2?'violet':'magenta');
    if(i%2===0)stud(m,'dress_star_'+i,x+.9,3.1+(i%3)*1.6,front-.2,'ether');
  }
  oct(m,'waist_cincher',8,9.8,8,10.2,3.0,7.5,'silver');
  plate(m,'cathedral_breastplate',[1.8,12.8,3.1],[14.2,22.4,12.0],'white');
  for(const s of[-1,1]){b(m,'marble_side_rib_'+s,[8+s*5.3-.75,13.7,2.9],[8+s*5.3+.75,22.2,4.3],'ivory');
    oct(m,'royal_shoulder_socket_'+s,8+s*8.2,18.7,8,5.4,2.6,6.4,'silver');
    crystal(m,'shoulder_cathedral_finial_'+s,8+s*8.6,21.15,8,2.2,6.3,'ether');
    b(m,'hanging_royal_stole_'+s,[8+s*4.2-.9,3.6,2.0],[8+s*4.2+.9,16.2,2.8],'white');
    for(let i=0;i<4;i++)stud(m,'stole_jewel_'+s+'_'+i,8+s*4.2,5.5+i*2.6,1.8,'violet');
  }
  ringXY(m,'chest_eclipse_corona',8,18.0,3.5,.6,2.25,.35,'gold');
  oct(m,'black_sun_chest',8,15.2,2.8,5.5,5.5,1.0,'void');
  for(let i=0;i<8;i++){const a=i*Math.PI/4;line(m,'coronal_ray_'+i,[8+Math.sin(a)*3.7,18+Math.cos(a)*3.7,2.15],[8+Math.sin(a)*4.4,18+Math.cos(a)*4.4,2.15],[.30,.30,.25],'magenta',.4);}
  oct(m,'high_cathedral_collar',8,22.4,8,8.5,1.6,7.0,'white');oct(m,'eclipsia_neck_contact',8,24,8,4.6,.6,4.6,'shadow');
  for(let i=0;i<7;i++)b(m,'rear_celestial_train_'+i,[-.2+i*2.4,1.2+(i%2),14.4],[1.9+i*2.4,19.0,15.3],i%2?'velvet':'robe');return m;
}
function eclipsiaHead(){
  const m=model('boss_eclipsia_head');
  oct(m,'neck_rotation_contact',8,8,8,4.6,1.1,4.6,'shadow');
  oct(m,'pale_saint_mask',8,9.1,7.2,8.3,8.0,6.8,'ivory');
  b(m,'platinum_blindfold',[3.55,13.5,3.43],[12.45,15.4,4.10],'silver');
  b(m,'blindfold_silk_highlight',[3.8,14.5,3.25],[12.2,14.95,3.47],'white');
  b(m,'mouth_silent_slit',[7.1,10.6,3.60],[8.9,10.88,3.84],'shadow');
  for(const s of[-1,1]){b(m,'long_temple_veil_'+s,[8+s*4.3-.55,8.2,6.4],[8+s*4.3+.55,17.5,11.8],'white');b(m,'veil_amethyst_edge_'+s,[8+s*4.3-.18,8.3,6.18],[8+s*4.3+.18,17.2,6.43],'violet');}
  // Entire head including eclipse halo and crown remains inside Y=8..24.
  ringXY(m,'black_eclipse_halo',8,16,7.1,1.3,12.1,.8,'void');
  ringXY(m,'eclipse_halo_purple_corona',8,16,7.65,.35,11.9,.3,'violet');
  for(let i=0;i<7;i++){const x=3.8+i*1.4,h=3.2+(3-Math.abs(i-3))*.75;crystal(m,'amethyst_crown_spire_'+i,x,17.2,7.7,1.35,h,'violet');}
  b(m,'crown_platinum_band',[3.3,16.6,4.9],[12.7,17.7,10.5],'silver');stud(m,'crown_eclipse_gem',8,17.3,4.57,'magenta');
  return m;
}
function eclipsiaWeapon(){
  const m=model('boss_eclipsia_weapon');
  for(const s of[-1,1]){hinge(m,'eclipsia_shoulder_hinge_'+s,8+s*8.2);
    line(m,'ivory_arm_lateral_joint_'+s,[8+s*8.2,8,8],[8+s*11,8,8],[.8,.8,.8],'silver');
    line(m,'ivory_upper_arm_'+s,[8+s*11,8,8],[8+s*11,8,0],[1.8,1.9,1.9],'white');
    line(m,'scythe_forearm_'+s,[8+s*11,8,0],[8+s*5.5,9.3,-8.7],[1.5,1.5,1.5],'silver');
    b(m,'scythe_holding_hand_'+s,[8+s*5.5-.8,2.0,-4.6],[8+s*5.5+.8,3.6,-2.8],'white');
  }
  b(m,'void_scythe_cross_shaft',[-12.0,2.1,-4.3],[27.5,3.2,-3.2],'void');b(m,'scythe_shaft_silver_spine',[-11.8,3.2,-4.3],[27.3,3.45,-3.2],'silver');
  for(let x=-11;x<27;x+=2.1)b(m,'scythe_grip_wrap_'+x,[x,2,-4.44],[x+.45,3.36,-3.08],'goldDark');
  ringXY(m,'huge_crescent_scythe_border',20,7.8,10.5,2.4,-4.45,1.65,'void',(x,y)=>x>19.2);
  ringXY(m,'huge_crescent_scythe_amethyst',20,7.8,10.5,1.25,-4.7,.26,'violet',(x,y)=>x>19.2);
  ringXY(m,'huge_crescent_scythe_light_edge',20,7.8,11.0,.40,-4.86,.17,'pink',(x,y)=>x>19.6);
  crystal(m,'scythe_pommel_crystal',-11.3,1.6,-3.8,1.7,2.4,'violet');
  shiftNamed(m,/^(void_scythe|scythe_shaft|scythe_grip|huge_crescent|scythe_pommel|scythe_holding_hand)/,[0,6.5,-5]);
  const half=[[[-1.1,13.0],[-4.0,29.0],[-9.7,27.0],[-4.8,21.2],[-10.0,20.0],[-2.3,14.0]],
    [[-1.3,12.7],[-11.8,23.0],[-14.8,19.7],[-10.5,17.6],[-13.7,15.0],[-3.6,12.6]],
    [[-1.5,12.2],[-11.2,15.3],[-12.8,12.8],[-6.4,11.7],[-8.5,10.4],[-2.1,10.3]]];
  for(const s of[-1,1])for(let j=0;j<3;j++){
    const pts=half[j].map(([x,y])=>[s<0?x:16-x,y]),n='stained_glass_wing_'+(s<0?j+1:j+4),z=14.0+j*.8;
    prismWing(m,n,pts,z,['magenta','violet','gold'],1.2);
    line(m,n+'_mount_root',[8+s*8.2,8,8],[8+s*11,8,8],[.66,.66,.66],'shadow');
    line(m,n+'_mount_strut',[8+s*11,8,8],[8+s*11,pts[0][1],z],[.66,.66,.66],'shadow');
    line(m,n+'_mount_return',[8+s*11,pts[0][1],z],[pts[0][0],pts[0][1],z],[.66,.66,.66],'shadow');
    const tip=pts[1];b(m,n+'_detached_glass_tip',[tip[0]-.4,tip[1]-.3,z-.2],[tip[0]+.4,tip[1]+.65,z+.55],j%2?'gold':'magenta',r('z',s*22.5,[tip[0],tip[1],z]));
  }
  return m;
}

export const VOID_RIGS={
  mob_mirror_stalker:{baseScale:1.30,rootModel:'yuumi97:mob_mirror_stalker_body',sockets:{arm:{model:'yuumi97:mob_mirror_stalker_arm',offset:[0,13.5,-1.6]}}},
  mob_void_choir:{baseScale:1.35,rootModel:'yuumi97:mob_void_choir_body',sockets:{arm:{model:'yuumi97:mob_void_choir_arm',offset:[0,15,0]}}},
  mob_abyssal_oculus:{baseScale:1.50,rootModel:'yuumi97:mob_abyssal_oculus_body',sockets:{arm:{model:'yuumi97:mob_abyssal_oculus_arm',offset:[0,14,2]}}},
  mob_eclipse_templar:{baseScale:1.55,rootModel:'yuumi97:mob_eclipse_templar_body',sockets:{arm:{model:'yuumi97:mob_eclipse_templar_arm',offset:[0,20,-1]}}},
  boss_twin_archons:{baseScale:1.80,rootModel:'yuumi97:boss_twin_archons_core',sockets:{head:{model:'yuumi97:boss_twin_archons_head',offset:[0,23,0]},weapon:{model:'yuumi97:boss_twin_archons_weapon',offset:[0,19,0]}}},
  boss_eclipsia:{baseScale:2.55,rootModel:'yuumi97:boss_eclipsia_core',sockets:{head:{model:'yuumi97:boss_eclipsia_head',offset:[0,24,0]},weapon:{model:'yuumi97:boss_eclipsia_weapon',offset:[0,20,0]}}}
};
const builders=[stalkerBody,stalkerArm,choirBody,choirArm,oculusBody,oculusArm,templarBody,templarArm,archonCore,archonHead,archonWeapon,eclipsiaCore,eclipsiaHead,eclipsiaWeapon];
function validate(m){const names=new Set();for(const e of m.elements){if(names.has(e.name))throw Error('Duplicate '+m.id+'/'+e.name);names.add(e.name);for(let k=0;k<3;k++)if(!Number.isFinite(e.from[k])||!Number.isFinite(e.to[k])||e.from[k]>=e.to[k]||e.from[k]<-16||e.to[k]>32)throw Error('Illegal bounds '+m.id+'/'+e.name+' '+JSON.stringify([e.from,e.to]));if(e.rotation&&![-45,-22.5,0,22.5,45].includes(e.rotation.angle))throw Error('Illegal rotation '+e.name);}}
export function buildVoidMonsters(){const models=builders.map(f=>f());for(const m of models)validate(m);for(const m of models)m.save();return models.map(m=>m.id);}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))console.log(JSON.stringify({models:buildVoidMonsters(),rigs:VOID_RIGS}));
