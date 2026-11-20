/** Foundry creatures: fourteen vanilla cuboid parts; import has no write effects.
 * Root soles [8,0,8]; all child hinges [8,8,8]; native front -Z.
 * Run directly or call buildFoundryMonsters(). No shared registry/build writes.
 */
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Model,shade} from '../tools/asset-kit.mjs';

export const FOUNDRY_RIGS={
  mob_cinder_imp:{baseScale:1.05,rootModel:'yuumi97:mob_cinder_imp_body',sockets:{arm:{model:'yuumi97:mob_cinder_imp_arm',offset:[0,19,0]}}},
  mob_slag_hound:{baseScale:1.30,rootModel:'yuumi97:mob_slag_hound_body',sockets:{arm:{model:'yuumi97:mob_slag_hound_arm',offset:[0,10,-11]}}},
  mob_obsidian_bombardier:{baseScale:1.45,rootModel:'yuumi97:mob_obsidian_bombardier_body',sockets:{arm:{model:'yuumi97:mob_obsidian_bombardier_arm',offset:[0,21,-1]}}},
  mob_chained_juggernaut:{baseScale:1.80,rootModel:'yuumi97:mob_chained_juggernaut_body',sockets:{arm:{model:'yuumi97:mob_chained_juggernaut_arm',offset:[0,23,0]}}},
  boss_warden_vulcantus:{baseScale:1.85,rootModel:'yuumi97:boss_warden_vulcantus_core',sockets:{head:{model:'yuumi97:boss_warden_vulcantus_head',offset:[0,24,0]},weapon:{model:'yuumi97:boss_warden_vulcantus_weapon',offset:[0,23,0]}}},
  boss_ignion:{baseScale:2.65,rootModel:'yuumi97:boss_ignion_core',sockets:{head:{model:'yuumi97:boss_ignion_head',offset:[0,27,0]},weapon:{model:'yuumi97:boss_ignion_weapon',offset:[0,25,0]}}}
};
const q=n=>Math.round(n*1000000)/1000000;
const R=(axis,angle,origin)=>({axis,angle,origin:origin.map(q),rescale:false});
const B=(m,n,x,y,z,w,h,d,t,r)=>m.box(n,[x-w/2,y-h/2,z-d/2].map(q),[x+w/2,y+h/2,z+d/2].map(q),t,r?{rotation:r}:{});
function shiftParts(m,match,delta,zScale=1,zOrigin=0){
  const move=v=>v.map((n,k)=>q((k===2?zOrigin+(n-zOrigin)*zScale:n)+delta[k]));
  for(const e of m.elements.filter(e=>match.test(e.name))){e.from=move(e.from);e.to=move(e.to);if(e.rotation)e.rotation={...e.rotation,origin:move(e.rotation.origin)};}
}
const C={ink:'#17141D',iron:'#343641',ironLight:'#62616B',ironDark:'#24232D',obsidian:'#1A181B',obsidianLight:'#39303E',brass:'#B88A4F',brassLight:'#F1CB7B',brassDark:'#785638',copper:'#B77750',copperDark:'#724D3C',verdigris:'#4E9994',lava:'#FF691D',lavaShade:'#B63A16',lavaLight:'#FFB703',heat:'#FFF0A4',skin:'#B65439',skinLight:'#E98748',skinShade:'#793625',leather:'#614036',leatherLight:'#956345',bone:'#DBC9A5',white:'#FFF3D9',blueSteel:'#3D405B',blueLight:'#6D7994',glass:'#7CB3C1',soot:'#2A2527',chain:'#777581',chainShade:'#44424D',redLens:'#F55F39',orange:'#DC8A42'};
function make(id){
  const m=new Model(id,128);
  for(const[n,c]of Object.entries(C)){
    m.swatch(n,c,{noise:false,kind:n.startsWith('leather')?'cloth':undefined});const{x,y}=m.swatches[n],p=m.canvas;
    if(/iron|Steel|brass|copper|chain/.test(n)){
      p.line(x+1,y+1,x+14,y+1,shade(c,13));p.line(x+1,y+2,x+1,y+12,shade(c,8));p.line(x+13,y+7,x+13,y+14,shade(c,-16));
      for(const[xx,yy,w]of[[3,5,4],[8,11,5]])p.line(x+xx,y+yy,x+xx+w,y+yy,shade(c,-9));
    }
    if(n.startsWith('obsidian')){p.line(x+1,y+11,x+6,y+3,shade(c,9));p.line(x+6,y+3,x+14,y+6,shade(c,5));p.line(x+7,y+13,x+12,y+9,shade(c,-7));}
    if(n==='leather'){for(let yy=2;yy<15;yy+=3){p.pixel(x+1,y+yy,C.leatherLight);p.pixel(x+14,y+yy,C.leatherLight);}}
    if(n==='lava'||n==='lavaShade'){p.line(x+2,y+15,x+6,y+8,C.lavaLight).line(x+6,y+8,x+4,y+2,C.lavaLight);p.line(x+12,y+14,x+9,y+7,C.lava);}
    if(n==='glass'){p.line(x+2,y+4,x+9,y+1,C.white);p.line(x+5,y+6,x+13,y+2,'#C3E4DF');}
  }
  m.display.gui={rotation:[20,-32,0],translation:[0,-1,0],scale:[.45,.45,.45]};return m;
}
function octY(m,n,x,y,z,w,h,d,t,r){
  const cut=Math.min(w,d)*.22;
  B(m,n+'_center',x,y,z,w-cut*2,h,d,t,r);
  B(m,n+'_left',x-w/2+cut/2,y,z,cut,h,d-cut*2,t,r);B(m,n+'_right',x+w/2-cut/2,y,z,cut,h,d-cut*2,t,r);
}
function octZ(m,n,x,y,z,w,h,d,t,r){
  const cut=Math.min(w,h)*.22;
  B(m,n+'_center',x,y,z,w-cut*2,h,d,t,r);
  B(m,n+'_left',x-w/2+cut/2,y,z,cut,h-cut*2,d,t,r);B(m,n+'_right',x+w/2-cut/2,y,z,cut,h-cut*2,d,t,r);
}
function profile(m,n,points,z0,z1,t,step=.5){
  const low=Math.min(...points.map(p=>p[1])),high=Math.max(...points.map(p=>p[1])),count=Math.ceil((high-low)/step),dy=(high-low)/count;
  for(let i=0;i<count;i++){
    const ya=low+i*dy,yb=ya+dy,y=(ya+yb)/2,h=[];
    for(let j=0,k=points.length-1;j<points.length;k=j++){const a=points[k],b=points[j];if((a[1]>y)!==(b[1]>y))h.push(a[0]+(y-a[1])*(b[0]-a[0])/(b[1]-a[1]));}
    h.sort((a,b)=>a-b);for(let j=0;j+1<h.length;j+=2)if(h[j+1]-h[j]>.001)B(m,n+'_'+i+'_'+j,(h[j]+h[j+1])/2,(ya+yb)/2,(z0+z1)/2,h[j+1]-h[j],dy,z1-z0,t);
  }
}
function ringXY(m,n,x,y,z,rx,ry,t,depth,mat,r){
  const steps=Math.ceil(ry*2/.55),dy=ry*2/steps;
  for(let i=0;i<steps;i++){
    const ya=y-ry+i*dy,yb=ya+dy,v=(ya+yb)/2-y,outer=rx*Math.sqrt(Math.max(.001,1-v*v/(ry*ry))),inner=Math.abs(v)<ry-t?(rx-t)*Math.sqrt(Math.max(0,1-v*v/((ry-t)*(ry-t)))):0;
    if(inner){B(m,n+'_'+i+'_l',x-(outer+inner)/2,(ya+yb)/2,z,outer-inner,dy,depth,mat,r);B(m,n+'_'+i+'_r',x+(outer+inner)/2,(ya+yb)/2,z,outer-inner,dy,depth,mat,r);}
    else B(m,n+'_'+i+'_cap',x,(ya+yb)/2,z,outer*2,dy,depth,mat,r);
  }
}
function ringXZ(m,n,x,y,z,r,t,h,mat){
  const count=Math.ceil(r*2/.6),d=r*2/count;
  for(let i=0;i<count;i++){
    const za=z-r+i*d,zb=za+d,v=(za+zb)/2-z,o=Math.sqrt(Math.max(.001,r*r-v*v)),inner=Math.abs(v)<r-t?Math.sqrt((r-t)**2-v*v):0;
    if(inner){B(m,n+'_'+i+'_l',x-(o+inner)/2,y,(za+zb)/2,o-inner,h,d,mat);B(m,n+'_'+i+'_r',x+(o+inner)/2,y,(za+zb)/2,o-inner,h,d,mat);}else B(m,n+'_'+i+'_cap',x,y,(za+zb)/2,o*2,h,d,mat);
  }
}
function hinge(m,n,x=8,y=8,z=8,width=20){
  B(m,n+'_coaxial_shoulder_spindle',x,y,z,width,1.1,1.1,'chainShade');
  for(const s of[-1,1]){B(m,n+'_socket_'+s,x+s*(width/2-.75),y,z,2.6,3.4,3.4,'ironDark');B(m,n+'_pin_'+s,x+s*(width/2-.75),y,z-1.85,1.25,1.25,.5,'brass');}
}
function studs(m,n,x0,x1,y,z,count=5){for(let i=0;i<count;i++)B(m,n+'_'+i,x0+(x1-x0)*i/(count-1),y,z,.42,.42,.28,'brassLight');}
function boot(m,n,x,z,w=4.5,y=0){
  B(m,n+'_sole',x,y+.65,z-1.0,w+1.0,1.3,7.5,'ironDark');
  octY(m,n+'_toe',x,y+1.8,z-2.0,w+1.0,2.1,5.5,'iron');
  B(m,n+'_ankle',x,y+3.0,z+1.0,w,4.1,4.0,'iron');
  for(let i=0;i<3;i++)B(m,n+'_toe_ridge_'+i,x-w*.3+i*w*.3,y+2.8,z-3,.45,.55,2.4,'ironLight');
  B(m,n+'_front_brass_lip',x,y+1.6,z-4.83,w*.8,.45,.3,'brassDark');
}
function clawFoot(m,n,x,z,y=0,w=3.5){
  B(m,n+'_ankle',x,y+2.3,z+1,w,4.6,3.4,'ironDark');B(m,n+'_paw',x,y+1.3,z-1,w+1,2.6,5,'iron');
  for(let i=0;i<3;i++)B(m,n+'_front_claw_'+i,x-w*.32+i*w*.32,y+.65,z-3.7,.65,1.3,2.3,'brassLight');
}
function armorPanel(m,n,x,y,z,w,h,mat='iron'){B(m,n+'_plate',x,y,z,w,h,1.0,mat);B(m,n+'_upper_bead',x,y+h/2-.3,z-.6,w-.3,.5,.4,'brassDark');for(const sx of[-1,1])for(const sy of[-1,1])B(m,n+'_rivet_'+sx+'_'+sy,x+sx*(w/2-.6),y+sy*(h/2-.6),z-.65,.42,.42,.4,'brassLight');}
function chainLink(m,n,x,y,z,w=1.4,h=1.8,alternate=false,material='chain'){
  const d=.42,depth=alternate?1.1:.45,ww=alternate?.62:w;
  B(m,n+'_top',x,y+h/2-d/2,z,ww,d,depth,material);B(m,n+'_bottom',x,y-h/2+d/2,z,ww,d,depth,material);
  B(m,n+'_left',x-ww/2+d/2,y,z,d,h-d*2,depth,material);B(m,n+'_right',x+ww/2-d/2,y,z,d,h-d*2,depth,material);
}
function linkedPath(m,n,a,b,count,width=1.4){for(let i=0;i<count;i++){const f=i/(count-1);chainLink(m,n+'_'+i,...a.map((v,k)=>v+(b[k]-v)*f),width,1.7,i%2===1);}}
function flame(m,n,x,y,z,s=1){
  profile(m,n+'_outer',[[x-2.3*s,y],[x-2.5*s,y+3*s],[x-1*s,y+5*s],[x-.5*s,y+8*s],[x+1.2*s,y+5.3*s],[x+2.4*s,y+6.5*s],[x+2.1*s,y+2*s],[x+1.5*s,y]],z-1*s,z+1*s,'lava',.4);
  profile(m,n+'_inner',[[x-1.1*s,y+.5*s],[x-.8*s,y+3.3*s],[x+.1*s,y+5.8*s],[x+.7*s,y+3*s],[x+1.3*s,y+3.8*s],[x+.8*s,y+.5*s]],z-1.12*s,z-.94*s,'lavaLight',.35);
}

function cinderImp(){
  const m=make('mob_cinder_imp_body');
  for(const[x,n]of[[4.6,'right'],[11.4,'left']]){
    boot(m,n+'_smith_boot',x,8,3.2);B(m,n+'_bare_shin',x,5.5,8,2.8,5,2.9,'skinShade');B(m,n+'_thigh',x,9.2,8.7,3.5,4.0,3.8,'skin');
  }
  octY(m,'imp_stocky_torso',8,14.5,8,10,9.2,7.2,'skin');B(m,'imp_belly_warm_plane',8,13,4.1,7.8,6.5,.9,'skinLight');
  B(m,'leather_apron_skirt',8,9.0,3.7,8.2,6.6,.85,'leather');B(m,'leather_apron_bib',8,15,3.6,6.8,5.8,.85,'leather');
  for(const s of[-1,1])B(m,'apron_shoulder_strap_'+s,8+s*3.15,17.8,6.0,1.0,2.5,5.4,'leatherLight');
  B(m,'apron_front_pocket',8,11.1,3.10,4.6,2.8,.45,'leatherLight');studs(m,'apron_pocket_stitches',6.1,9.9,12.2,2.83,4);
  B(m,'apron_belt',8,12.8,7.3,10.5,1.1,8.4,'leather');
  B(m,'apron_brass_buckle',8,12.8,2.8,2.0,1.5,.6,'brass');B(m,'apron_buckle_hole',8,12.8,2.45,1.0,.65,.15,'leather');
  B(m,'imp_neck',8,19.7,8,4.5,2.2,4.5,'skinShade');octY(m,'imp_cheeky_head',8,23.1,7.3,11.0,7.2,8.0,'skin');
  B(m,'imp_muzzle',8,21.7,2.7,7.1,2.5,2.5,'skinLight');B(m,'imp_smile_dark',8,21.0,1.32,5.2,.9,.35,'ink');
  for(const s of[-1,1]){B(m,'imp_fang_'+s,8+s*1.45,21.0,1.05,.6,1.1,.4,'bone');B(m,'imp_eye_socket_'+s,8+s*2.5,24.05,2.87,2.25,1.65,.8,'skinShade');B(m,'imp_bright_eye_'+s,8+s*2.5,24.05,2.40,1.45,.95,.30,'lavaLight');B(m,'imp_pupil_'+s,8+s*2.35,24.05,2.19,.42,.85,.18,'ink');}
  B(m,'imp_nose',8,22.8,1.56,1.8,1.2,1.1,'skinShade');
  for(const s of[-1,1]){
    profile(m,'imp_pointed_ear_'+s,[[8+s*4.2,22.5],[8+s*7.4,25.8],[8+s*6.1,21.5]],5.1,7.0,'skinShade',.4);
    profile(m,'imp_horn_'+s,[[8+s*2.5,26],[8+s*4.7,29.1],[8+s*3.6,29.6],[8+s*1.6,26]],6.2,8.7,'obsidian',.4);
    // Short bat wings remain separate lobes, visibly rooted at the shoulder back.
    const p=[[8+s*3.7,18],[8+s*5.8,23.0],[8+s*10.2,25.1],[8+s*11.2,20.4],[8+s*8.0,21.3],[8+s*7.8,18.8],[8+s*5.6,19.5]];
    profile(m,'bat_wing_membrane_'+s,p,11.2,12.15,'skinShade',.5);
    B(m,'bat_wing_root_'+s,8+s*4.3,19.6,11.1,2.2,4.7,2.1,'skin');
    B(m,'bat_wing_upper_bone_'+s,8+s*7.7,23.1,11.0,5.8,.5,.55,'skinLight',R('z',s*22.5,[8+s*7.7,23.1,11]));
  }
  B(m,'goggle_forehead_leather_strap',8,26.5,7.2,11.7,1.25,8.5,'leather');
  for(const x of[5.2,10.8]){ringXY(m,'goggle_brass_rim_'+x,x,26.4,2.4,2.0,1.7,.5,1.0,'brass');octZ(m,'goggle_smoked_lens_'+x,x,26.4,2.35,2.9,2.4,.4,'glass');}
  B(m,'goggle_bridge',8,26.4,2.1,1.5,.6,.7,'brassDark');
  const tail=[[8,8.4,11.8],[8,7.2,15],[9,7.8,18.2],[10,10,21],[10,13,22.8]];
  tail.forEach((p,i)=>B(m,'tail_joint_'+i,...p,1.5,i===4?3.6:2.5,3.2,i%2?'skinShade':'skin'));
  flame(m,'tail_flame',10,13.1,23,.7);
  // Root shoulder sockets line up with the child [8,8,8] axle after offset.
  for(const s of[-1,1])B(m,'shoulder_socket_'+s,8+s*6.2,19,8,3,3.4,3.4,'skinShade');
  const a=make('mob_cinder_imp_arm');hinge(a,'shoulder',8,8,8,13.5);
  for(const[s,n]of[[-1,'hammer'],[1,'tongs']]){
    const x=8+s*11.2;
    B(a,n+'_upper_arm',8+s*8.6,5.6,7.4,2.8,8.0,2.9,'skin',R('z',s*22.5,[8+s*8.6,5.6,7.4]));
    B(a,n+'_elbow_bridge',8+s*10.3,2.8,6.6,3.4,2.8,3.3,'skin');
    B(a,n+'_forearm',x,2.0,4.9,2.9,3.4,4.2,'skinLight');octY(a,n+'_leather_glove',x,.25,3.0,3.6,3.2,3.6,'leather');
    for(let i=0;i<3;i++)B(a,n+'_finger_'+i,x-.9+i*.9,-.5,1.0,.65,1.35,.75,'leatherLight');
  }
  B(a,'hammer_wooden_haft',-1.2,2.6,1.7,1.25,11.0,1.25,'leatherLight');octY(a,'hammer_head_casting',-1.2,8.4,1.7,7.2,3.3,4.6,'iron');B(a,'hammer_hot_striking_face',-5.0,8.4,1.7,.5,3.5,4.9,'lavaShade');B(a,'hammer_brass_collar',-1.2,6.4,1.7,2.1,.8,2.1,'brass');
  B(a,'tongs_pivot',17.2,2.0,1.8,2.4,1.5,1.7,'brassDark');
  for(const s of[-1,1]){B(a,'tongs_handle_'+s,17.2+s*.65,-1.0,1.8,.55,6.2,.7,'ironLight',R('z',s*22.5,[17.2,2,1.8]));B(a,'tongs_hot_jaw_'+s,17.2+s*.72,5.25,1.8,.6,5.5,.8,'lavaShade',R('z',s*-22.5,[17.2,2,1.8]));B(a,'tongs_gripping_tip_'+s,17.2+s*.45,7.4,1.75,.7,1.25,1.2,'lavaLight',R('z',s*-22.5,[17.2,2,1.8]));}
  shiftParts(a,/^hammer_(wooden|head|hot|brass)/,[-2,0,0]);
  shiftParts(a,/^tongs_(pivot|handle|hot|gripping)/,[2,0,0]);
  return[m,a];
}

function slagHound(){
  const m=make('mob_slag_hound_body');
  for(const[x,side]of[[2.4,'right'],[13.6,'left']])for(const[z,end]of[[1.7,'front'],[17.8,'rear']]){
    clawFoot(m,side+'_'+end+'_paw',x,z,0,3.1);
    B(m,side+'_'+end+'_lower_leg',x,5.7,z+1.0,2.4,4.5,2.7,'ironDark');
    B(m,side+'_'+end+'_hot_tendon',x,8.3,z+.9,1.3,4.4,1.4,'lavaShade');
    octY(m,side+'_'+end+'_upper_haunch',x,10.4,z+1.2,4.5,5.3,4.6,'iron');
    B(m,side+'_'+end+'_knee_plate',x,7.2,z-1.0,3.4,2.2,1.0,'brassDark');
  }
  // Open rib cage. The molten torso is visible THROUGH rib gaps on both flanks.
  octY(m,'hound_magma_torso',8,12.5,10.4,7.2,5.6,15.7,'lavaShade');B(m,'hound_magma_spine',8,14.6,10.5,3.0,1.7,15.8,'lavaLight');
  for(let i=0;i<5;i++){
    const z=3.0+i*3.65;
    B(m,'rib_'+i+'_dorsal_arch',8,15.6,z,13.0,1.1,1.0,'iron');
    for(const s of[-1,1]){B(m,'rib_'+i+'_side_'+s,8+s*5.6,12.9,z,1.0,5.8,1.0,'ironLight');B(m,'rib_'+i+'_lower_hook_'+s,8+s*4.5,10.0,z,3.0,1.0,1.0,'iron');}
  }
  B(m,'armored_dorsal_backplate',8,16.5,11.2,6.7,1.8,17.9,'obsidian');
  for(let i=0;i<5;i++)B(m,'raised_spinal_vertebra_'+i,8,17.7,3.8+i*3.4,2.6,1.5,2.1,'brassDark');
  for(const x of[4.5,11.5]){
    octY(m,'exhaust_stack_'+x,x,18.0,14.0,2.6,5.0,2.6,'ironDark');ringXZ(m,'exhaust_open_rim_'+x,x,20.5,14,1.6,.5,.5,'brass');B(m,'exhaust_dark_bore_'+x,x,20.3,14,1.65,.12,1.65,'ink');
  }
  octY(m,'hound_angular_neck',8,14.8,1.3,8.8,8.4,6.4,'ironDark');octY(m,'hound_armored_skull',8,16.0,-2.0,8.7,6.6,8.1,'iron');
  B(m,'hound_upper_muzzle',8,15.5,-6.4,7.2,3.0,5.0,'obsidian');B(m,'hound_nose_plate',8,16.0,-9.0,5.2,2.1,.8,'ironLight');
  for(const s of[-1,1]){
    B(m,'hound_furious_eye_'+s,8+s*3.0,17.4,-5.65,2.1,.8,.5,'lavaLight',R('z',s*22.5,[8+s*3,17.4,-5.6]));
    profile(m,'hound_angular_ear_'+s,[[8+s*2.4,18.6],[8+s*5.0,23.1],[8+s*4.8,17.6]],-2.1,.2,'obsidian',.4);
    for(let i=0;i<4;i++)B(m,'hound_upper_fang_'+s+'_'+i,8+s*2.65,13.8,-4.2-i*1.3,.55,1.4,.55,'bone');
    // A fixed cheek yoke joins the raised skull to the original hinge; the
    // moving jaw clears its interior throughout the mandated pitch sweep.
    B(m,'hound_fixed_cheek_yoke_'+s,8+s*4.3,11.8,-2.5,.85,5.4,2.0,'ironDark');
  }
  B(m,'hound_jaw_socket',8,10,-3,7.4,1.8,1.8,'chainShade');
  for(const[i,y,z]of[[0,13,21.5],[1,14.1,24.1],[2,16,26.2],[3,18.4,27.1]])B(m,'hound_seg_tail_'+i,8,y,z,2.0,3.1,3.6,i%2?'brassDark':'iron');
  flame(m,'hound_tail_jet',8,19.0,27.5,.7);
  const a=make('mob_slag_hound_arm');hinge(a,'jaw_hinge',8,8,8,7.4);
  const restJaw=R('x',-45,[8,8,8]);
  B(a,'movable_lower_jaw',8,6.8,4.3,7.5,1.8,7.6,'ironDark',restJaw);B(a,'jaw_lower_plating',8,6.0,3.8,6.2,1.2,6.8,'iron',restJaw);
  B(a,'jaw_hot_throat',8,7.78,4.4,5.5,.25,6.0,'lavaShade',restJaw);B(a,'jaw_inner_fire_core',8,7.98,3.2,2.0,.18,3.4,'lavaLight',restJaw);
  for(const s of[-1,1])for(let i=0;i<4;i++){B(a,'jaw_lower_fang_'+s+'_'+i,8+s*2.8,8.4,1.25+i*1.55,.55,1.05,.6,'bone',restJaw);}
  for(const s of[-1,1]){
    B(a,'crusher_claw_support_'+s,8+s*5.0,7.0,7.0,5.2,1.3,2.0,'ironDark');B(a,'crusher_claw_wrist_'+s,8+s*7.0,5.8,5.5,2.8,3.0,3.6,'iron');
    for(let i=0;i<3;i++){const x=8+s*7.0-1.0+i;B(a,'crusher_claw_blade_'+s+'_'+i,x,4.8,3.8,.72,1.4,3.3,'brass');B(a,'crusher_claw_hot_tip_'+s+'_'+i,x,4.65,1.85,.48,1.0,1.0,'lavaLight');}
  }
  return[m,a];
}

function bombardier(){
  const m=make('mob_obsidian_bombardier_body');
  for(const[x,n]of[[3.2,'right'],[12.8,'left']]){
    boot(m,n+'_blast_boot',x,8.1,4.7);B(m,n+'_padded_lower_leg',x,6.0,8.6,4.4,5.3,4.8,'blueSteel');
    armorPanel(m,n+'_greave',x,6.7,5.65,4.3,4.8,'obsidianLight');B(m,n+'_thigh_pad',x,10.5,8.6,4.8,5.0,5.1,'blueSteel');
  }
  octY(m,'heavy_blast_suit_hips',8,12.0,8,13.6,4.0,10.6,'obsidian');
  octY(m,'heavy_blast_suit_belly',8,17.0,8.8,16.6,8.4,13.0,'blueSteel');
  octY(m,'blast_cuirass_gold_flange',8,20.8,8.3,18.4,5.4,12.1,'brassDark');
  armorPanel(m,'front_layered_blast_plate',8,17.6,2.1,12.3,10.2,'obsidian');
  armorPanel(m,'front_second_breastplate',8,19.9,1.45,9.1,5.1,'iron');
  B(m,'blast_plate_hot_seam',8,16.7,1.42,1.1,7.5,.22,'lavaShade');B(m,'blast_plate_center_emblem',8,20.2,.80,2.5,2.5,.5,'brass',R('z',45,[8,20.2,.8]));
  B(m,'leather_bomb_belt',8,12.0,8,16.2,1.6,12.2,'leather');
  for(const s of[-1,1]){
    const x=8+s*10.0;
    octY(m,'blast_shoulder_shell_'+s,x,22.0,8,6.5,5.4,8.6,'obsidian');
    B(m,'blast_shoulder_gilt_seam_'+s,x,24.65,8,5.4,.45,7.7,'brass');
    B(m,'blast_shoulder_socket_'+s,x,21,7,3.1,3.8,3.8,'ironDark');
    octY(m,'side_mortar_shell_'+s,8+s*8.5,14.5,12.7,2.8,7.0,2.8,'copper');
    octY(m,'shell_capped_tip_'+s,8+s*8.5,18.2,12.7,2.2,.9,2.2,'brassLight');
    B(m,'shell_retaining_strap_'+s,8+s*8.5,14.0,12.7,3.3,.7,3.3,'ironDark');
  }
  // The face is deeply recessed in a massive metal visor, with a slotted grille.
  octY(m,'bombardier_helmet_shell',8,26.0,8,11.8,7.1,10.6,'ironDark');
  octY(m,'helmet_upper_gold_casing',8,29.45,8.7,10.2,1.1,8.4,'brassDark');
  B(m,'helmet_face_recess',8,26.0,2.54,8.6,4.4,.45,'ink');
  B(m,'helmet_orange_eye_slit',8,27.15,2.25,6.3,.72,.30,'lavaLight');
  for(let i=0;i<5;i++)B(m,'helmet_respirator_grille_'+i,5.3+i*1.35,24.9,2.03,.48,2.3,.60,'ironLight');
  for(const s of[-1,1])B(m,'helmet_brass_cheek_'+s,8+s*4.7,25.4,2.5,1.25,4.9,1.2,'brass');
  for(let i=0;i<4;i++)B(m,'rear_heat_sink_'+i,8,17.5+i*1.35,15.45,9.7,.48,1.2,'ironLight');
  const a=make('mob_obsidian_bombardier_arm');hinge(a,'blast_shoulders',8,8,8,20.5);
  for(const s of[-1,1]){
    const x=8+s*10.5;B(a,'blast_arm_upper_'+s,x,7.5,6.0,4.0,4.2,4.6,'blueSteel');
    B(a,'blast_elbow_coupler_'+s,8+s*11.5,8.2,2.9,3.6,3.2,2.5,'blueSteel');
    B(a,'blast_arm_braced_forearm_'+s,8+s*11.5,8.2,-.9,4.4,3.8,5.4,'iron');B(a,'blast_gauntlet_'+s,x,9.0,-4.2,4.6,3.5,3.4,'brassDark');
    B(a,'cannon_side_grip_'+s,8+s*6.7,10.2,-4.2,6.0,1.4,1.5,'ironLight');
  }
  // The barrel is hollow from muzzle to a recessed fiery chamber. No front disc
  // occludes the bore. All pieces share ONE legal X rotation around the mount.
  const r=R('x',22.5,[8,3.6,6.3]);
  for(const [i,z,rx,ry,dep]of [[0,-8.5,4.7,4.7,2.0],[1,-5.0,4.2,4.2,5.4],[2,.1,4.7,4.7,4.9],[3,4.8,4.3,4.3,4.5]])
    ringXY(a,'mortar_hollow_barrel_'+i,8,3.6,z,rx,ry,1.0,dep,i%2?'iron':'brassDark',r);
  ringXY(a,'mortar_muzzle_gold_lip',8,3.6,-9.65,4.95,4.95,.55,.45,'brass',r);
  // A narrow heated liner just behind the muzzle makes the molten chamber
  // readable under neutral light while leaving a wide, genuinely open bore.
  ringXY(a,'mortar_visible_heated_liner',8,3.6,-8.65,3.8,3.8,.35,1.6,'lava',r);
  octZ(a,'mortar_recessed_lava_chamber',8,3.6,7.2,6.2,6.2,.8,'lavaShade',r);
  octZ(a,'mortar_chamber_hot_center',8,3.6,6.71,3.9,3.9,.16,'lavaLight',r);
  B(a,'mortar_chamber_white_core',8,3.6,6.58,1.5,1.5,.13,'heat',r);
  for(const s of[-1,1])B(a,'barrel_external_strap_'+s,8+s*3.35,6.9,-.9,.6,.6,16.8,'ironLight',r);
  B(a,'barrel_lift_sight',8,9.0,-1.8,1.0,2.3,2.0,'brassDark',r);
  // Shorten the tube 30%, then set the complete mortar forward and above the
  // two-hand cradle. Its breech clears chest/visor over all five bone pitches.
  shiftParts(a,/^(mortar_|barrel_)/,[0,7,-8.5],.7,6.3);
  return[m,a];
}

function moltenAnchor(m,n,x,y,z){
  ringXY(m,n+'_eye',x,y+5.6,z,1.25,1.4,.46,1.0,'chainShade');
  B(m,n+'_shank',x,y+1.8,z,1.35,6.0,1.8,'iron');
  B(m,n+'_stock',x,y+3.35,z,7.6,1.0,1.7,'ironLight');
  const pts=[[x-4.5,y+1.4],[x-3.5,y-1.1],[x,y-3.2],[x+3.5,y-1.1],[x+4.5,y+1.4],[x+2.8,y+.1],[x+2.0,y-.9],[x+.7,y-1.5],[x+.7,y+.5],[x-.7,y+.5],[x-.7,y-1.5],[x-2.0,y-.9],[x-2.8,y+.1]];
  profile(m,n+'_forged_flukes',pts,z-1.3,z+1.3,'ironDark',.4);
  profile(m,n+'_molten_front_vein',[[x-4.0,y+.85],[x-3.0,y-1.1],[x,y-2.65],[x+3.0,y-1.1],[x+4,y+.85],[x+2.7,y-.15],[x,y-2.0],[x-2.7,y-.15]],z-1.48,z-1.25,'lava',.35);
  for(const s of[-1,1])profile(m,n+'_hot_fluke_tip_'+s,[[x+s*4.5,y+1.4],[x+s*3.5,y-1.1],[x+s*3.5,y+.5]],z-1.52,z-.8,'lavaLight',.35);
}
function juggernaut(){
  const m=make('mob_chained_juggernaut_body');
  for(const[x,n]of[[2.5,'right'],[13.5,'left']]){
    boot(m,n+'_executioner_boot',x,8,4.9);B(m,n+'_shin',x,6.5,8.7,4.3,6.4,4.9,'ironDark');armorPanel(m,n+'_greave',x,6.8,5.7,4.6,5.6,'obsidian');
    B(m,n+'_knee_block',x,10.8,6.6,4.8,2.2,4.0,'iron');B(m,n+'_thigh',x,13.0,8.7,4.8,4.8,5.2,'ironDark');
  }
  octY(m,'executioner_waist',8,14.6,8.5,14,3.6,10.6,'iron');
  profile(m,'tattered_leather_tabard',[[4,14],[12,14],[12.7,5.8],[10.5,7],[8.7,5.3],[7.0,7.4],[4.2,6.0]],2.7,3.5,'leather',.5);
  octY(m,'executioner_barrel_chest',8,20.0,8.2,17.6,9.0,12.0,'ironDark');armorPanel(m,'executioner_front_armor',8,20.2,1.6,13.6,7.0,'iron');
  for(const s of[-1,1]){
    octY(m,'executioner_scorched_pauldron_'+s,8+s*10.3,23.1,8.0,6.4,5.5,8.0,'obsidian');
    B(m,'executioner_pauldron_ridge_'+s,8+s*10.3,25.85,8,5.3,.6,6.7,'ironLight');
    B(m,'executioner_shoulder_socket_'+s,8+s*9.0,23,8,3.6,3.6,3.6,'chainShade');
  }
  // Two separate raised chains cross over the cuirass. Alternating links expose
  // real holes and edge-on links instead of merely painting an X on the chest.
  linkedPath(m,'chest_chain_down_right',[2.3,23.3,.6],[13.7,15.6,.6],10,1.45);
  linkedPath(m,'chest_chain_down_left',[13.7,23.3,-.05],[2.3,15.6,-.05],10,1.45);
  B(m,'rear_neck_brace',8,25,9.6,6,3,5,'ironDark');octY(m,'barred_iron_helmet',8,27.2,7.9,10.6,7.2,9.8,'ironDark');
  B(m,'helmet_face_dark_recess',8,27.0,2.66,7.6,5.8,.45,'ink');
  for(const s of[-1,1])B(m,'helmet_eye_ember_'+s,8+s*1.75,28,2.36,1.7,.6,.24,'lavaLight');
  for(let i=0;i<5;i++)B(m,'helmet_vertical_bar_'+i,4.7+i*1.65,27,2.1,.58,5.8,.8,'ironLight');
  for(const y of[24.3,29.7])B(m,'helmet_cross_bar_'+y,8,y,2.0,8.6,.55,.9,'chainShade');
  B(m,'helmet_raised_sagittal_ridge',8,30.6,8.0,1.0,1.2,8.3,'brassDark');
  for(const x of[3.7,12.3])B(m,'helmet_side_lock_pin_'+x,x,26.8,2.0,.9,1.4,1.0,'brassDark');
  for(let i=0;i<4;i++)B(m,'rear_cuirass_segment_'+i,8,16.4+i*2.1,14.45,12.8,1.2,.8,i%2?'iron':'ironLight');
  const a=make('mob_chained_juggernaut_arm');hinge(a,'executioner_shoulders',8,8,8,20.0);
  for(const s of[-1,1]){
    const x=8+s*11.2;B(a,'iron_upper_arm_'+s,8+s*9.6,5.2,7.6,4.4,5.8,4.8,'ironDark');B(a,'iron_elbow_coupler_'+s,x,2.3,5.5,4.7,2.1,4.7,'brassDark');
    B(a,'heavy_forearm_'+s,x,.2,3.7,4.6,4.0,4.8,'iron');octY(a,'executioner_fist_'+s,x,-2.2,1.9,5.1,3.4,4.9,'ironDark');
    for(let i=0;i<3;i++)B(a,'fist_knuckle_'+s+'_'+i,x-1.5+i*1.5,-2.1,-.7,1.15,1.35,.85,'ironLight');
    linkedPath(a,'hanging_anchor_chain_'+s,[x,-3.8,1.5],[x,-6.5,1.5],4,1.45);
    moltenAnchor(a,'molten_anchor_'+s,x,-11.65,1.5);
  }
  return[m,a];
}

function furnaceLegs(m,n,spread=5.5,heavy=false){
  for(const s of[-1,1]){
    const x=8+s*spread;boot(m,n+'_boot_'+s,x,8.5,heavy?5.9:5.0);
    B(m,n+'_calf_piston_'+s,x,6.7,9.2,heavy?4.7:4.1,6.6,4.6,'ironDark');
    for(const dx of[-1.2,1.2])B(m,n+'_shin_chrome_rod_'+s+'_'+dx,x+dx,6.5,6.55,.65,6.4,.7,'chain');
    armorPanel(m,n+'_knee_'+s,x,10.5,5.8,heavy?5.5:4.8,2.6,'brassDark');
    B(m,n+'_upper_leg_'+s,x,12.1,9.2,heavy?5.2:4.5,5.0,5.4,'iron');
    B(m,n+'_outer_thigh_casing_'+s,x+s*1.8,12,8.7,1.2,5.5,5.8,'blueSteel');
  }
}
function warden(){
  const m=make('boss_warden_vulcantus_core');furnaceLegs(m,'warden',5.0);
  octY(m,'warden_hip_carapace',8,14.0,8.7,14.5,4.1,11.5,'brassDark');
  octY(m,'warden_breast_armored_vessel',8,19.8,8,16.6,10.3,12.7,'iron');
  armorPanel(m,'warden_front_chest',8,20.1,1.35,12.1,7.9,'blueSteel');
  B(m,'warden_chest_center_vent_recess',8,20.1,.70,5.4,4.5,.35,'ink');
  for(let i=0;i<4;i++)B(m,'warden_chest_louvre_'+i,8,18.5+i*1.05,.43,5.2,.4,.6,'brass');
  B(m,'warden_waist_belt',8,14.7,8,17.0,1.8,13.4,'ironDark');
  profile(m,'warden_protective_apron',[[4,15],[12,15],[13,8.2],[10,8.7],[8,7.6],[5.1,8.5]],1.0,2.0,'leather',.5);
  studs(m,'warden_apron_rivets',4.8,11.2,14.3,.77,6);
  for(const s of[-1,1]){
    octY(m,'warden_shoulders_'+s,8+s*9.3,23.2,8,6.3,4.5,8.2,'brassDark');
    B(m,'warden_shoulder_front_plate_'+s,8+s*9.3,23.0,3.5,5.0,3.2,.6,'copper');
    B(m,'warden_action_socket_'+s,8+s*10.2,23,8,3.2,3.3,3.3,'chainShade');
  }
  octY(m,'warden_neck_collar',8,24.3,8,5.7,2.6,5.7,'brassDark');
  // Rear boiler is a separate pressure vessel with open chimney mouths, a valve
  // wheel and bands. Its two pipes remain visible around the assembled head.
  octY(m,'back_steam_boiler_tank',8,20.6,16.3,10.3,12.4,8.0,'copperDark');
  for(const y of[15.1,20.5,26.2])octY(m,'boiler_binding_band_'+y,8,y,16.3,10.8,.8,8.4,'brass');
  for(const s of[-1,1]){
    const x=8+s*3.5;octY(m,'boiler_twin_chimney_'+s,x,26.5,16.0,2.8,8.1,2.8,'ironDark');
    ringXZ(m,'chimney_open_rim_'+s,x,30.6,16.0,1.75,.45,.6,'brass');
    B(m,'chimney_dark_throat_'+s,x,30.3,16,1.9,.1,1.9,'ink');
    B(m,'boiler_side_feed_'+s,8+s*5.4,18.4,14.8,1.6,6.7,1.6,'brassDark');
    B(m,'boiler_elbow_feed_'+s,8+s*5.0,15.6,11.6,1.7,1.7,7.0,'brassDark');
  }
  ringXY(m,'boiler_rear_valve_wheel',8,21.2,20.9,2.65,2.65,.48,.55,'ironLight');
  B(m,'valve_crossbar_x',8,21.2,21.2,5.0,.45,.4,'brass');B(m,'valve_crossbar_y',8,21.2,21.2,.45,5.0,.4,'brass');B(m,'valve_center_spindle',8,21.2,20.45,1.1,1.1,2.4,'brassLight');
  const h=make('boss_warden_vulcantus_head');octY(h,'neck_pivot_sleeve',8,9.0,8,4.6,2.0,4.6,'ironDark');
  octY(h,'warden_horned_helmet',8,15.2,8,12.5,10.4,11.0,'brassDark');
  octY(h,'warden_crown_iron_cap',8,20.5,8.5,10.3,1.4,8.8,'iron');
  B(h,'warden_sunken_faceplate',8,15.0,2.08,9.2,7.4,.7,'ironDark');
  B(h,'warden_scorching_visor',8,16.5,1.58,7.9,.95,.40,'redLens');B(h,'warden_visor_hot_strip',8,16.6,1.34,5.6,.25,.18,'lavaLight');
  for(let i=0;i<4;i++)B(h,'warden_respiration_grille_'+i,5.6+i*1.6,12.9,1.32,.65,2.7,.7,'ironLight');
  for(const s of[-1,1]){
    profile(h,'warden_brass_horn_'+s,[[8+s*4.1,19],[8+s*7.8,21.3],[8+s*8.1,23.5],[8+s*6.8,24.0],[8+s*6.1,21.7],[8+s*3.2,20.6]],6.0,9.5,'brass',.4);
    B(h,'warden_temple_bolt_'+s,8+s*5.8,16.1,3.4,1.1,2.0,1.1,'brassLight');
  }
  B(h,'warden_center_helmet_seam',8,18.8,1.75,.7,3.8,.65,'brass');
  const w=make('boss_warden_vulcantus_weapon');hinge(w,'warden_action_shoulders',8,8,8,22.0);
  for(const s of[-1,1]){
    const x=8+s*10.0;B(w,'warden_upper_arm_'+s,x,5.7,7.7,4.1,5.1,4.4,'ironDark');B(w,'warden_elbow_'+s,8+s*11.3,3.5,3.0,5.2,2.8,5.2,'brassDark');B(w,'warden_forearm_'+s,8+s*12,2.8,-1.5,6.4,4.0,5.0,'blueSteel');
  }
  // Left arm: three independent, deep hollow nozzles in a triangular cluster.
  octZ(w,'triple_flamer_rear_manifold',18.0,1.6,5.4,10.5,9.6,4.2,'ironDark');
  const barrels=[[14.9,3.8],[21.1,3.8],[18.0,-1.6]];
  for(const[i,[x,y]]of barrels.entries()){
    for(const [j,z,rad,dep]of [[0,-6.8,1.9,5.8],[1,-1.4,2.1,5.1],[2,3.2,1.85,4.2]])ringXY(w,`flamer_nozzle_${i}_tube_${j}`,x,y,z,rad,rad,.6,dep,j===1?'copperDark':'iron');
    ringXY(w,'flamer_nozzle_'+i+'_open_muzzle',x,y,-9.95,2.2,2.2,.55,.55,'brass');
    octZ(w,'flamer_nozzle_'+i+'_inner_ember',x,y,5.2,2.6,2.6,.35,'lavaShade');octZ(w,'flamer_nozzle_'+i+'_inner_heat',x,y,4.96,1.7,1.7,.16,'lavaLight');
    B(w,'flamer_nozzle_'+i+'_top_brace',x,y+1.95,-1.7,.48,.5,15.6,'ironLight');
  }
  octY(w,'flamer_fuel_tank',19.2,5.0,11.0,5.4,6.8,4.5,'copper');B(w,'flamer_fuel_tank_band',19.2,5,11.0,5.4,.7,4.9,'ironDark');
  B(w,'flamer_hose_drop',21.1,1.7,11.0,1.25,6.3,1.25,'chainShade');B(w,'flamer_hose_elbow',20.3,-1.2,8.9,2.9,1.25,5.1,'chainShade');
  // Right arm: the gripped haft, piston rods and broad hammer anvil are distinct.
  octY(w,'hammer_gauntlet',-2.0,-.5,2.7,5.0,3.8,4.8,'brassDark');
  B(w,'steam_hammer_haft',-2.0,1.6,1.6,1.65,15.0,1.65,'ironDark');
  for(let i=0;i<5;i++)B(w,'hammer_grip_ring_'+i,-2,-3.5+i*.9,1.6,2.0,.35,2.0,'copper');
  octY(w,'steam_hammer_pressure_head',-2,10.4,1.6,10.7,5.4,7.6,'iron');
  B(w,'hammer_striking_anvil',-2,10.4,-2.55,11.2,5.9,.9,'brassDark');B(w,'hammer_anvil_worn_face',-2,10.4,-3.15,9.5,4.4,.35,'ironLight');
  for(const x of[-5.5,1.5]){B(w,'hammer_exposed_piston_rod_'+x,x,6.5,1.6,.8,4.6,.8,'chain');B(w,'hammer_piston_sleeve_'+x,x,8.4,1.6,1.45,2.1,1.45,'brass');}
  B(w,'hammer_center_pressure_chamber',-2,7.2,1.6,3.7,4.3,3.7,'copperDark');B(w,'hammer_glowing_pressure_slot',-2,10.6,-3.37,5.4,.55,.17,'lavaShade');
  B(w,'hammer_haft_endcap',-2,-6.3,1.6,2.8,1.1,2.8,'brass');
  shiftParts(w,/^(flamer_|triple_flamer_)/,[4,3,-3]);
  shiftParts(w,/^triple_flamer_/,[0,0,-1.5]);
  shiftParts(w,/^(steam_hammer_|hammer_)/,[-4,2,-2]);
  return[m,h,w];
}

function ignion(){
  const m=make('boss_ignion_core');furnaceLegs(m,'titan',6.2,true);
  octY(m,'titan_heavy_hips',8,13.8,8.7,18.4,4.1,13.1,'ironDark');
  B(m,'titan_waist_piston_bridge',8,15.0,8.7,9.2,3.4,9.1,'brassDark');
  // Furnace chest is a real chamber: four frame members surround the recessed
  // heart, while the front grille leaves it visible through five open slots.
  B(m,'furnace_rear_bulkhead',8,21.2,14.3,20.0,13.0,3.2,'ironDark');
  for(const s of[-1,1])B(m,'furnace_outer_sidewall_'+s,8+s*8.7,21.3,8.8,4.2,13.4,11.0,'iron');
  B(m,'furnace_chest_header',8,26.4,6.4,16.7,3.1,6.9,'obsidian');B(m,'furnace_chest_sill',8,15.9,6.4,16.7,2.4,6.9,'ironDark');
  octZ(m,'furnace_heart_volcanic_cradle',8,21.0,7.6,11.8,10.8,5.9,'lavaShade');
  octZ(m,'furnace_heart_lava_glow',8,21.0,4.38,9.4,8.5,.65,'lava');
  octZ(m,'furnace_heart_white_hot_center',8,21.2,3.96,5.7,6.4,.3,'lavaLight');
  B(m,'heart_ragged_heat_column',8,21.6,3.72,1.85,4.8,.3,'heat');
  for(const x of[3.2,5.6,8.0,10.4,12.8])B(m,'heart_caging_vertical_bar_'+x,x,21.1,2.8,.82,10.9,1.4,'ironDark');
  for(const y of[16.2,26.0])B(m,'heart_caging_horizontal_bar_'+y,8,y,2.6,12.3,.8,1.65,'ironLight');
  studs(m,'furnace_header_bolts',1.7,14.3,27.2,2.72,7);studs(m,'furnace_sill_bolts',1.7,14.3,15.3,2.72,7);
  for(const s of[-1,1]){
    const x=8+s*13.2;
    octY(m,'titan_cauldron_pauldron_base_'+s,x,24.7,8.5,9.2,5.5,10.5,'ironDark');
    ringXZ(m,'titan_cauldron_pauldron_open_rim_'+s,x,28.0,8.5,5.0,.8,1.0,'brassDark');
    octY(m,'pauldron_molten_reservoir_'+s,x,27.75,8.5,7.6,.35,7.6,'lava');
    B(m,'pauldron_heat_glint_'+s,x,27.98,8,4.5,.15,2.7,'lavaLight');
    B(m,'pauldron_front_drip_'+s,x+s*1.8,25.8,3.0,.9,4.2,.65,'lavaShade');
    B(m,'titan_shoulder_pivot_socket_'+s,8+s*14.0,25,8,3.7,4.6,4.6,'chainShade');
  }
  octY(m,'titan_neck_gear_collar',8,26.5,8,6.7,2.5,6.7,'ironDark');
  for(let i=0;i<5;i++)B(m,'titan_back_heat_sink_'+i,8,17.1+i*2.0,16.4,16.8,.8,1.2,'ironLight');
  for(const x of[2.1,13.9])B(m,'titan_rear_exhaust_riser_'+x,x,23.6,17.0,2.5,10.8,2.5,'obsidian');
  const h=make('boss_ignion_head');octY(h,'titan_neck_pivot',8,9.0,8,5.2,2.0,5.2,'ironDark');
  octY(h,'titan_obsidian_cranium',8,15.5,8.1,13.7,10.2,11.9,'obsidian');
  octY(h,'titan_jutting_lower_jaw',8,11.4,5.2,11.0,4.0,7.1,'ironDark');
  B(h,'titan_open_mouth_fire',8,12.7,1.31,7.8,2.4,.55,'lava');B(h,'titan_mouth_hot_center',8,12.7,.95,5.9,1.1,.22,'lavaLight');
  for(let i=0;i<5;i++){B(h,'titan_upper_fang_'+i,4.9+i*1.55,13.65,.75,.75,1.3,.6,'ironLight');B(h,'titan_lower_fang_'+i,4.9+i*1.55,11.75,.75,.75,1.2,.6,'ironLight');}
  for(const s of[-1,1]){
    B(h,'titan_glowing_eye_'+s,8+s*3.1,17.0,1.58,3.1,1.15,.52,'lavaLight',R('z',s*22.5,[8+s*3.1,17.0,1.58]));
    B(h,'titan_brow_plate_'+s,8+s*3.1,18.2,1.75,5.1,1.25,1.4,'obsidianLight',R('z',s*22.5,[8+s*3.1,18.2,1.75]));
    profile(h,'broken_obsidian_crown_horn_'+s,[[8+s*3.0,19.2],[8+s*7.6,21.0],[8+s*8.1,23.0],[8+s*6.5,23.8],[8+s*5.2,21.7],[8+s*2.0,20.2]],5.5,10.0,'obsidianLight',.4);
    B(h,'titan_side_jaw_rivet_'+s,8+s*5.5,12.2,2.1,1.2,1.1,1.0,'brassDark');
  }
  profile(h,'broken_center_crown',[[5.9,19.5],[6.3,23.2],[7.5,22.3],[8.1,24],[9.2,21.6],[10.3,22.4],[10.1,19.5]],6.2,9.9,'obsidian',.4);
  B(h,'titan_nasal_keel',8,15.5,1.9,1.65,4.4,1.5,'obsidianLight');
  const w=make('boss_ignion_weapon');hinge(w,'titan_massive_action_axle',8,8,8,29.0);
  for(const s of[-1,1]){
    const x=8+s*15.5;
    octY(w,'titan_upper_arm_casing_'+s,x,4.6,8.8,6.2,6.6,3.8,'iron');
    B(w,'titan_elbow_hydraulic_'+s,x,1.0,5.4,5.3,2.7,5.3,'brassDark');
    octY(w,'titan_massive_gauntlet_'+s,x,-3.0,2.5,8.5,6.6,8.4,'ironDark');
    armorPanel(w,'titan_gauntlet_front_'+s,x,-2.9,-2.15,7.0,5.5,'iron');
    for(let i=0;i<4;i++){
      B(w,'titan_knuckle_'+s+'_'+i,x-2.7+i*1.8,-2.15,-3.05,1.45,2.0,1.25,'ironLight');
      B(w,'titan_finger_segment_'+s+'_'+i,x-2.7+i*1.8,-4.55,-2.75,1.45,2.0,1.0,'obsidian');
      B(w,'titan_magma_finger_seam_'+s+'_'+i,x-2.7+i*1.8,-3.35,-3.25,.48,.35,.17,'lava');
    }
    B(w,'titan_thumb_'+s,x+s*4.0,-3.4,.5,2.4,3.8,4.1,'iron');
    B(w,'titan_wrist_heat_slot_'+s,x,.15,1.7,3.5,.65,.7,'lavaLight');
  }
  // Right gauntlet drags a real chain to a heavy, low slung crushing weight.
  // It clears the other fist and hangs in front of the furnace rather than in it.
  // The chain drapes in front of the knuckles before returning to the weight
  // eye, so it remains visibly separate from both solid forms.
  const chainRoute=[[23.5,-4.6,-3.25],[26.5,-4.85,-5.85],[25.8,-5.6,-1.9]],chainPoints=[];
  for(let segment=0;segment<2;segment++)for(let i=segment?1:0;i<=7;i++){
    const f=i/7;chainPoints.push(chainRoute[segment].map((v,k)=>v+(chainRoute[segment+1][k]-v)*f));
  }
  chainPoints.forEach((p,i)=>chainLink(w,'furnace_flail_chain_'+i,...p,1.55,1.7,i%2===1));
  octY(w,'furnace_crushing_weight',8.1,-11.4,-6.4,8.5,7.0,8.5,'ironDark');
  octY(w,'crusher_brass_equator',8.1,-11.4,-6.4,9.0,.85,9.0,'brassDark');
  for(const s of[-1,1]){
    B(w,'crusher_side_spike_'+s,8.1+s*5.0,-11.4,-6.4,2.0,2.1,2.1,'obsidianLight');
    B(w,'crusher_front_back_spike_'+s,8.1,-11.4,-6.4+s*5.0,2.1,2.1,2.0,'obsidianLight');
  }
  B(w,'crusher_front_magma_crack',8.1,-11.4,-10.9,1.1,5.3,.3,'lava');
  B(w,'crusher_front_hot_crack',8.1,-11.4,-11.08,.35,3.6,.15,'lavaLight');
  ringXY(w,'crusher_top_chain_eye',8.1,-7.1,-6.4,1.35,1.6,.48,1.0,'chain');
  // An outboard hanging weight stays visible during the backswing and clears
  // the calves and floor at +55; the original bone socket remains unchanged.
  shiftParts(w,/^(furnace_crushing_weight|crusher_)/,[17.7,1.5,4.5]);
  return[m,h,w];
}

function corners(e){
  const points=[];for(const x of[e.from[0],e.to[0]])for(const y of[e.from[1],e.to[1]])for(const z of[e.from[2],e.to[2]]){
    let v=[x,y,z];if(e.rotation){const{axis,angle,origin}=e.rotation,a=angle*Math.PI/180,c=Math.cos(a),s=Math.sin(a);v=v.map((n,k)=>n-origin[k]);const[x,y,z]=v;if(axis==='x')v=[x,y*c-z*s,y*s+z*c];if(axis==='y')v=[x*c+z*s,y,-x*s+z*c];if(axis==='z')v=[x*c-y*s,x*s+y*c,z];v=v.map((n,k)=>n+origin[k]);}points.push(v);
  }return points;
}
function check(m){
  const names=new Set();for(const e of m.elements){
    if(names.has(e.name))throw Error('Duplicate element '+m.id+'/'+e.name);names.add(e.name);
    if([...e.from,...e.to].some(v=>!Number.isFinite(v)||v< -16||v>32)||e.to.some((v,k)=>v<=e.from[k]))throw Error('Invalid cuboid '+m.id+'/'+e.name);
    if(e.rotation&&![-45,-22.5,0,22.5,45].includes(e.rotation.angle))throw Error('Illegal angle '+e.name);
    for(const f of Object.values(e.faces))if(f.uv.some(v=>!Number.isFinite(v)||v<0||v>16)||f.uv[2]<=f.uv[0]||f.uv[3]<=f.uv[1])throw Error('Illegal UV '+e.name);
  }
  const pts=m.elements.flatMap(corners),lo=[0,1,2].map(k=>Math.min(...pts.map(p=>p[k]))),hi=[0,1,2].map(k=>Math.max(...pts.map(p=>p[k])));
  if(lo.some(v=>v< -16-.00001)||hi.some(v=>v>32.00001))throw Error('Actual bounds '+m.id+JSON.stringify([lo,hi]));
  if(m.id.endsWith('_body')||m.id.endsWith('_core')){if(Math.abs(lo[1])>.00001)throw Error('Root does not meet Y=0 '+m.id+' '+lo[1]);}
  else if(!m.elements.some(e=>e.from.every((v,k)=>v<=8)&&e.to.every((v,k)=>v>=8)))throw Error('Missing child hinge support '+m.id);
  if(m.id.endsWith('_head')&&(lo[1]<8-.00001||hi[1]>24.00001))throw Error('Head outside Y8..24 '+m.id);
  return{id:m.id,elements:m.elements.length,textureSize:128,bounds:[lo.map(q),hi.map(q)]};
}
export function buildFoundryMonsters(){
  const models=[...cinderImp(),...slagHound(),...bombardier(),...juggernaut(),...warden(),...ignion()];
  const reports=models.map(check);for(const m of models)m.save();return{models:reports,rigs:FOUNDRY_RIGS};
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1]))console.log(JSON.stringify(buildFoundryMonsters(),null,2));
