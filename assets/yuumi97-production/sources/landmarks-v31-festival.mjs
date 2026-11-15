/** Yuumi97 v3.1 festival landmarks; vanilla named cuboids, authored toward -Z.
 * Dummy is grounded at Y=0. Crest hangs from [8,20,8]; it is not a gate shell.
 * Importing this module is read-only; buildFestival() performs the two exports.
 */
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Model,Canvas} from '../tools/asset-kit.mjs';

const rot=(axis,angle,origin)=>({axis,angle,origin,rescale:false});
function tile(m,name,color,draw){m.swatch(name,color,{noise:false});const s=m.swatches[name],c=new Canvas(16,16);c.rect(0,0,16,16,color);draw(c);m.canvas.blit(c,s.x,s.y);}
function palette(m){
  m.swatch('gold','#DDA64C',{border:true});m.swatch('gold_light','#FFE1A0',{border:true});m.swatch('gold_dark','#966538',{border:true});
  m.swatch('ivory','#F7E6CC',{border:true});m.swatch('ink','#343044',{border:true});
  m.swatch('red','#BD4653',{kind:'cloth',border:true});m.swatch('red_light','#EB7780',{kind:'cloth',border:true});m.swatch('red_dark','#813849',{kind:'cloth',border:true});
  m.swatch('rose','#EA94B8',{border:true});m.swatch('rose_light','#FFD3DE',{border:true});m.swatch('rose_dark','#BB608E',{border:true});
  m.swatch('teal','#57BFB7',{border:true});m.swatch('mint','#B7E6D1',{border:true});
  m.swatch('wood','#AC7849',{kind:'wood',border:true});m.swatch('wood_light','#CF9E69',{kind:'wood',border:true});m.swatch('wood_dark','#74513D',{kind:'wood',border:true});
}
function octXZ(m,n,x,z,rx,rz,y0,y1,t,o={}){
  m.box(n+'_mid',[x-rx,y0,z-rz*.58],[x+rx,y1,z+rz*.58],t,o);
  m.box(n+'_front',[x-rx*.58,y0,z-rz],[x+rx*.58,y1,z-rz*.58],t,o);
  m.box(n+'_rear',[x-rx*.58,y0,z+rz*.58],[x+rx*.58,y1,z+rz],t,o);
}
function octXY(m,n,x,y,rx,ry,z0,z1,t,o={}){
  m.box(n+'_mid',[x-rx,y-ry*.58,z0],[x+rx,y+ry*.58,z1],t,o);
  m.box(n+'_top',[x-rx*.58,y+ry*.58,z0],[x+rx*.58,y+ry,z1],t,o);
  m.box(n+'_bottom',[x-rx*.58,y-ry,z0],[x+rx*.58,y-ry*.58,z1],t,o);
}
function circleXY(m,n,x,y,r,z0,z1,t,step=.45){
  const bands=[];
  for(let dy=-r;dy<r-1e-7;dy+=step){const end=Math.min(r,dy+step),edge=Math.max(Math.abs(dy),Math.abs(end));const width=Math.max(.18,Math.sqrt(Math.max(0,r*r-edge*edge)));const old=bands.at(-1);if(old&&Math.abs(old.width-width)<1e-6)old.end=end;else bands.push({start:dy,end,width});}
  bands.forEach((b,i)=>m.box(n+'_'+i,[x-b.width,y+b.start,z0],[x+b.width,y+b.end,z1],t));
}
function ringXZ(m,n,x,z,r,depth,y0,y1,t){
  const width=2*r*Math.tan(Math.PI/8)+.035;
  for(let i=0;i<8;i++){
    const angle=i*45,q=Math.round(angle/90),residual=angle-q*90,k=((q%4)+4)%4;let a,b;
    if(k===0){a=[x-width/2,y0,z-r-depth/2];b=[x+width/2,y1,z-r+depth/2];}
    if(k===1){a=[x-r-depth/2,y0,z-width/2];b=[x-r+depth/2,y1,z+width/2];}
    if(k===2){a=[x-width/2,y0,z+r-depth/2];b=[x+width/2,y1,z+r+depth/2];}
    if(k===3){a=[x+r-depth/2,y0,z-width/2];b=[x+r+depth/2,y1,z+width/2];}
    m.box(n+'_'+i,a,b,t,residual?{rotation:rot('y',residual,[x,y0,z])}:{});
  }
}
function paw(m,n,x,y,z,s,material){
  octXY(m,n+'_pad',x,y,s*1.3,s,z,z+.18,material);
  for(const[i,dx,dy]of[[0,-1.8,1.25],[1,-.68,2.15],[2,.68,2.15],[3,1.8,1.25]])octXY(m,n+'_toe_'+i,x+dx*s,y+dy*s,s*.48,s*.58,z,z+.2,material);
}
function display(m,scale){
  m.display.gui={rotation:[22,-30,0],translation:[0,0,0],scale:[scale,scale,scale]};
  m.display.ground={rotation:[0,0,0],translation:[0,4,0],scale:[.35,.35,.35]};
  m.display.fixed={rotation:[0,180,0],translation:[0,0,0],scale:[.7,.7,.7]};
}

function trainingDummy(){
  const m=new Model('prop_training_dummy',128);palette(m);
  m.swatch('spring_iron','#4B5363',{border:true});m.swatch('spring_glint','#9FAAAF',{border:true});
  tile(m,'straw','#C8A15B',c=>{
    for(let x=1;x<16;x+=3){c.line(x,0,x,15,'#E8C67F');c.line(x+1,2,x+1,13,'#A98348');}
    for(const[x,y]of[[2,3],[7,8],[12,4],[14,12]])c.rect(x,y,1,2,'#F3D696');
  });
  tile(m,'straw_light','#E2BE78',c=>{
    for(let x=0;x<16;x+=3)c.line(x,0,x,15,'#F4DBA4');
    for(let y=2;y<16;y+=5)c.rect(1,y,14,1,'#CCAA66');
  });
  tile(m,'rope','#A97747',c=>{for(let x=-12;x<20;x+=5){c.line(x,0,x+14,15,'#DBB17A',2);c.line(x+2,0,x+16,15,'#815739');}});
  tile(m,'endgrain','#9C6C43',c=>{c.rect(1,1,14,14,'#C18D57').rect(3,3,10,10,'#82583A').rect(5,5,6,6,'#D2A164').rect(7,7,2,2,'#91613F');});
  tile(m,'practice_grain','#B88C5B',c=>{c.line(2,0,4,15,'#8D6445');c.line(8,0,7,15,'#E0B57B');c.line(13,0,12,15,'#A4774E');c.rect(1,10,14,1,'#CDA068');});
  // Mortised crossed timber feet: their bottoms share the exact Y=0 plane.
  m.box('base_cross_long_x',[-.6,0,6.35],[16.6,1.55,9.65],'wood',{faces:{east:'endgrain',west:'endgrain'}});
  m.box('base_cross_long_z',[6.35,0,-.6],[9.65,1.55,16.6],'wood',{faces:{north:'endgrain',south:'endgrain'}});
  for(const[x,z,n]of[[.5,8,'west'],[15.5,8,'east'],[8,.5,'north'],[8,15.5,'south']]){
    m.box(n+'_iron_foot_strap',[x-.65,1.54,z-.65],[x+.65,1.8,z+.65],'spring_iron');
    m.box(n+'_foot_bolt',[x-.23,1.8,z-.23],[x+.23,2,z+.23],'gold_dark');
  }
  octXZ(m,'post_metal_socket',8,8,2.7,2.7,1.55,3.1,'spring_iron');
  m.box('post_wood_core',[7.25,2.65,7.25],[8.75,12.7,8.75],'wood_dark');
  // Open gaps between coils let the spring remain legible in profile.
  for(let i=0;i<5;i++){
    const y=3+i*1.4;ringXZ(m,'spring_coil_'+i,8,8,2.05,.55,y,y+.5,'spring_iron');
    m.box('spring_front_highlight_'+i,[6.95,y+.32,5.65],[9.05,y+.5,5.95],'spring_glint');
    if(i<4)m.box('spring_coil_riser_'+i,[9.72,y+.38,7.6],[10.2,y+1.5,8.25],'spring_iron');
  }
  octXZ(m,'spring_top_compression_plate',8,8,2.65,2.65,9,10.25,'spring_glint');
  octXZ(m,'straw_torso_lower',8,8,3.2,2.65,10.15,12.05,'straw');
  octXZ(m,'straw_torso_round_belly',8,8,4.15,3.4,12.05,19.65,'straw');
  octXZ(m,'straw_torso_shoulders',8,8,3.75,3.2,19.65,21.7,'straw_light');
  m.box('shoulder_internal_wood_cross',[.4,19.7,7],[15.6,21.2,9],'wood_dark',{faces:{east:'endgrain',west:'endgrain'}});
  // Unequal arm gestures keep the stuffed training doll lively.
  for(const[x,label,angle]of[[1.8,'sword_arm',-22.5],[14.2,'free_arm',22.5]]){
    const r=rot('z',angle,[x,20.2,8]);
    octXZ(m,label+'_straw_bundle',x,8,2.5,1.35,19.25,21.25,'straw',{rotation:r});
    m.box(label+'_rope_binding',[x-.45,19.05,6.5],[x+.45,21.45,9.5],'rope',{rotation:r});
    for(let i=0;i<4;i++)m.box(label+'_loose_straw_'+i,[x-2.9+i*.17,19.35+i*.4,7.1],[x-2.3+i*.17,19.58+i*.4,8.7],'straw_light',{rotation:r});
  }
  // Front timber armour is a separate raised assembly, with pegs and rope ties.
  for(let i=0;i<4;i++)m.box('chest_armour_board_'+i,[3.9+i*2.05,12.75,4.03],[5.88+i*2.05,20.65,4.65],i%2?'wood':'wood_light',{faces:{north:'practice_grain'}});
  m.box('chest_armour_top_rail',[3.7,20.35,3.78],[12.3,21,4.75],'wood_dark');
  m.box('chest_armour_bottom_rail',[3.7,12.5,3.78],[12.3,13.15,4.75],'wood_dark');
  for(const x of[4.35,11.65])for(const y of[13.5,19.8])m.box('armour_wood_peg_'+x+'_'+y,[x-.2,y-.2,3.65],[x+.2,y+.2,4.08],'gold_dark');
  for(const x of[3.7,11.8])m.box('armour_shoulder_rope_'+x,[x,19.7,4.1],[x+.5,21.1,11.1],'rope');
  // Five physical target rings stay crisp under a distant view and at an angle.
  for(const[i,r,z,t]of[[0,3.35,3.58,'wood_dark'],[1,3.08,3.38,'red'],[2,2.4,3.21,'ivory'],[3,1.7,3.04,'red'],[4,.95,2.87,'ivory'],[5,.43,2.7,'red_dark']])circleXY(m,'bullseye_ring_'+i,8,16.85,r,z,z+.22,t);
  // Rope belt holds the stuffed torso; exposed short straw ends break the rim.
  ringXZ(m,'torso_rope_belt',8,8,3.1,.5,11.65,12.3,'rope');
  for(let i=0;i<9;i++){
    const x=4.3+i*.9;m.box('lower_straw_fringe_'+i,[x,10.1-(i%3)*.23,4.95],[x+.24,11.8,5.3],i%2?'straw':'straw_light',{rotation:rot('z',i%2?22.5:-22.5,[x+.12,11.4,5.12])});
  }
  octXZ(m,'cat_neck',8,8,2.35,2.25,21.4,22.6,'straw');
  octXZ(m,'cat_straw_cheeks',8,8,4.3,3.6,22.6,27.3,'straw_light');
  octXZ(m,'cat_straw_crown',8,8,3.85,3.25,27.3,28.6,'straw');
  octXZ(m,'cat_straw_chin',8,8,3.45,2.95,21.95,22.6,'straw');
  for(const[x,label,angle]of[[4.7,'left',-22.5],[11.3,'right',22.5]]){
    const r=rot('z',angle,[x,28.15,8]);
    m.box(label+'_cat_ear_lower',[x-1.5,27.75,6.5],[x+1.5,29.1,9.5],'straw',{rotation:r});
    m.box(label+'_cat_ear_middle',[x-1.05,29.1,6.85],[x+1.05,30.2,9.15],'straw',{rotation:r});
    m.box(label+'_cat_ear_tip',[x-.45,30.2,7.2],[x+.45,31.25,8.8],'straw_light',{rotation:r});
    m.box(label+'_cat_ear_pink_stitch',[x-.9,28.25,6.28],[x+.9,29.15,6.6],'rose',{rotation:r});
    m.box(label+'_cat_ear_pink_top',[x-.4,29.15,6.6],[x+.4,30.05,6.92],'rose_light',{rotation:r});
  }
  m.box('training_headband_front',[3.7,26.8,4.05],[12.3,27.9,4.65],'red');
  m.box('training_headband_back',[3.7,26.8,11.35],[12.3,27.9,11.95],'red_dark');
  for(const x of[3.45,12.15])m.box('training_headband_side_'+x,[x,26.8,4.55],[x+.4,27.9,11.45],'red');
  octXY(m,'headband_gold_star_seal',8,27.35,.7,.55,3.78,4.09,'gold_light');
  m.box('headband_tied_knot',[12.3,26.75,9.7],[13.3,27.95,10.8],'red_light');
  m.box('headband_flying_tail_upper',[13,26.5,9.85],[16.2,27.25,10.45],'red',{rotation:rot('z',22.5,[13,26.9,10.15])});
  m.box('headband_flying_tail_lower',[12.8,24,10.05],[13.6,26.8,10.65],'red_dark',{rotation:rot('z',22.5,[13.2,26.8,10.35])});
  // Bead eyes, stitched snout and twine whiskers are real front relief.
  for(const[x,n]of[[5.8,'left'],[10.2,'right']]){
    octXY(m,n+'_button_eye',x,25.25,.53,.67,3.98,4.45,'ink');
    m.box(n+'_eye_thread_glint',[x-.25,25.4,3.88],[x-.02,25.65,4.02],'ivory');
  }
  octXY(m,'cat_rosy_nose',8,24.05,.48,.3,3.76,4.32,'rose_dark');
  m.box('snout_center_stitch',[7.88,23.4,3.93],[8.12,24.05,4.25],'wood_dark');
  m.box('snout_left_smile',[7.1,23.17,4.0],[8,23.4,4.24],'wood_dark');
  m.box('snout_right_smile',[8,23.17,4.0],[8.9,23.4,4.24],'wood_dark');
  for(const[sign,label]of[[-1,'left'],[1,'right']])for(let i=0;i<2;i++){
    const x0=sign<0?1.9:11.8,x1=sign<0?4.2:14.1,y=23.55+i*.67;
    m.box(label+'_twine_whisker_'+i,[x0,y,4.2],[x1,y+.2,4.45],'gold_dark',{rotation:rot('z',sign*(i?22.5:-22.5),[sign<0?4.2:11.8,y+.1,4.32])});
  }
  // A rounded wooden practice sword is tied to the outward hand, not floating.
  const swordRot=rot('z',22.5,[.5,20.1,7.2]);
  m.box('practice_sword_grip',[.05,18.2,6.65],[.95,21.55,7.75],'rope',{rotation:swordRot});
  m.box('practice_sword_round_pommel',[-.2,17.7,6.5],[1.2,18.4,7.9],'wood_dark',{rotation:swordRot});
  m.box('practice_sword_crossguard',[-1.75,21.25,6.35],[2.75,22.05,8.05],'wood_dark',{rotation:swordRot});
  m.box('practice_sword_blade',[-.25,21.95,6.65],[1.25,28,7.75],'wood_light',{rotation:swordRot,faces:{north:'practice_grain',south:'practice_grain'}});
  m.box('practice_sword_bevel',[-.07,22.1,6.42],[.24,27.75,6.7],'ivory',{rotation:swordRot});
  m.box('practice_sword_safe_tip',[.05,28,6.8],[.95,28.65,7.6],'wood_light',{rotation:swordRot});
  m.box('practice_sword_hand_tie',[-.2,19.5,6.15],[1.2,20.65,8.25],'rope',{rotation:swordRot});
  display(m,.43);return m;
}

function blossom(m,n,x,y,z,s=1,shade='rose'){
  // Five separate thick petals, with lighter raised inner facets and a gold stamen.
  for(let i=0;i<5;i++){
    const a=(90+i*72)*Math.PI/180,cx=x+Math.cos(a)*s*.93,cy=y+Math.sin(a)*s*.93;
    octXY(m,n+'_petal_'+i,cx,cy,s*.72,s*.68,z+(i%2)*.08,z+.6+(i%2)*.08,shade);
    m.box(n+'_petal_fold_'+i,[cx-s*.32,cy-s*.24,z-.07],[cx+s*.25,cy+s*.19,z+.09],shade==='rose_light'?'ivory':'rose_light');
  }
  octXY(m,n+'_gold_stamen',x,y,s*.4,s*.36,z-.22,z+.08,'gold_light');
  m.box(n+'_stamen_core',[x-s*.14,y-s*.13,z-.32],[x+s*.14,y+s*.13,z-.18],'rose_dark');
}
function sakuraLanternArch(){
  const m=new Model('prop_sakura_lantern_arch',128);palette(m);
  m.swatch('lacquer','#A23F49',{border:true});m.swatch('lacquer_light','#CA5E62',{border:true});m.swatch('lacquer_dark','#6F3344',{border:true});
  m.swatch('branch','#764C47',{kind:'wood',border:true});m.swatch('branch_light','#A36F62',{kind:'wood',border:true});
  m.swatch('paper_amber','#F1B068',{noise:false});m.swatch('paper_warm','#E99250',{noise:false});m.swatch('paper_cream','#FFD19A',{noise:false});
  tile(m,'lantern_paper','#F5B86F',c=>{
    for(let x=1;x<16;x+=3)c.line(x,0,x,15,'#F8C583');
    c.rect(0,0,16,1,'#D8944D').rect(0,15,16,1,'#D8944D');
    for(const[x,y]of[[3,4],[9,8],[13,12]])c.rect(x,y,1,3,'#FFCF93');
  });
  tile(m,'plaque_inlay','#973945',c=>{c.rect(0,0,16,1,'#E1AE5A').rect(0,15,16,1,'#E1AE5A');c.line(2,8,8,2,'#C86D62');c.line(8,2,14,8,'#C86D62');c.line(14,8,8,14,'#C86D62');c.line(8,14,2,8,'#C86D62');c.rect(6,6,4,4,'#E3B566');});
  // The upper cleat is the one exact mounting contact, centered on [8,20,8].
  m.box('gate_mount_upper_cleat',[6.4,19.25,7.25],[9.6,20,8.75],'gold_dark');
  m.box('gate_mount_lacquer_spacer',[6.75,18.25,7.35],[9.25,19.25,8.65],'lacquer_dark');
  m.box('festival_plaque_shadow',[-3.2,14.65,7.35],[19.2,18.75,9.55],'lacquer_dark');
  m.box('festival_plaque_red_face',[-2.7,15.05,6.95],[18.7,18.35,7.48],'lacquer',{faces:{north:'plaque_inlay'}});
  m.box('plaque_top_gold_moulding',[-3.55,18.35,6.7],[19.55,18.95,9.7],'gold');
  m.box('plaque_top_lit_bead',[-3.7,18.95,6.65],[19.7,19.2,9.75],'gold_light');
  m.box('plaque_bottom_gold_moulding',[-3.55,14.4,6.7],[19.55,15.05,9.7],'gold');
  for(const[x,label]of[[-3.55,'left'],[18.85,'right']]){
    m.box(label+'_plaque_end_brass',[x,14.75,6.65],[x+.7,18.55,9.65],'gold_dark');
    m.box(label+'_plaque_outer_red_cap',[x-.35,15.25,6.75],[x+1.05,18.05,9.9],'lacquer_light');
    m.box(label+'_plaque_gold_button',[x+.02,16.15,6.35],[x+.68,16.85,6.78],'gold_light');
  }
  // Central paw seal sits above the lantern's suspension point.
  octXY(m,'plaque_center_crest_frame',8,16.65,2.25,1.75,6.45,7.05,'gold_dark');
  octXY(m,'plaque_center_crest_lacquer',8,16.65,1.97,1.46,6.23,6.48,'lacquer_dark');
  paw(m,'plaque_center_paw',8,16.2,5.98,.53,'gold_light');
  m.box('lantern_hanger_left',[7.05,12.5,7.15],[7.5,14.8,8.15],'gold');
  m.box('lantern_hanger_right',[8.5,12.5,7.15],[8.95,14.8,8.15],'gold');
  m.box('lantern_hanger_bridge',[7.05,14.15,7.15],[8.95,14.8,8.15],'gold_light');
  octXZ(m,'lantern_upper_brass_cap',8,7.65,2.95,2.35,11.95,12.6,'gold_dark');
  octXZ(m,'lantern_upper_red_cap',8,7.65,2.65,2.15,12.6,13.1,'lacquer');
  // Ribbed round paper shell. Every band is volumetric, not a flat lantern card.
  const tiers=[
    [1.15,2.1,3.1,2.5],[2.1,3.25,4.25,3.4],[3.25,4.55,5.15,4.1],[4.55,5.95,5.65,4.5],
    [5.95,7.35,5.85,4.65],[7.35,8.7,5.65,4.5],[8.7,9.95,5.15,4.1],[9.95,11.05,4.25,3.4],[11.05,11.95,3.1,2.5],
  ];
  tiers.forEach(([y0,y1,rx,rz],i)=>{
    octXZ(m,'paper_lantern_band_'+i,8,7.65,rx,rz,y0,y1,'paper_amber',{faces:{north:'lantern_paper',south:'lantern_paper',east:'paper_warm',west:'paper_warm',up:'paper_cream',down:'paper_warm'},shade:false});
    if(i>0&&i<8)octXZ(m,'bamboo_lantern_rib_'+i,8,7.65,rx+.035,rz+.035,y0,y0+.105,'gold_dark');
  });
  octXZ(m,'lantern_lower_red_cap',8,7.65,2.65,2.15,.55,1.15,'lacquer');
  octXZ(m,'lantern_lower_brass_cap',8,7.65,2.95,2.35,.15,.55,'gold');
  // Dark vermilion paw is placed on a raised ivory seal, so it reads at distance.
  octXY(m,'lantern_front_seal',8,6.5,2.95,3.05,2.69,3.05,'paper_cream');
  paw(m,'lantern_front_paw_stamp',8,5.65,2.44,1.05,'red_dark');
  // Branches connect into the plaque and curve outward, with independent forks.
  const leftStart=m.elements.length;
  m.box('left_branch_main',[-10.5,15.55,7.45],[-1.9,16.45,8.45],'branch',{rotation:rot('z',22.5,[-1.9,16,7.95])});
  m.box('left_branch_upper_fork',[-7.1,16.4,7.7],[-2.5,17.05,8.35],'branch_light',{rotation:rot('z',-22.5,[-2.5,16.7,8])});
  m.box('left_branch_lower_fork',[-7.1,11.3,7.15],[-6.45,15.8,7.8],'branch',{rotation:rot('z',-22.5,[-6.8,15.8,7.45])});
  m.box('left_branch_outer_hook',[-12.3,12.7,7.5],[-8,13.3,8.1],'branch',{rotation:rot('z',-22.5,[-8,13,7.8])});
  m.box('left_branch_upper_tip',[-9.2,15.1,8.35],[-5.8,15.6,8.85],'branch_light',{rotation:rot('z',-22.5,[-5.8,15.35,8.6])});
  m.box('left_branch_budding_twig',[-4.4,16.3,6.9],[-3.9,18.7,7.4],'branch_light',{rotation:rot('z',-22.5,[-4.15,16.3,7.15])});
  const leftBranches=m.elements.slice(leftStart);
  for(const source of leftBranches){
    const e=structuredClone(source);e.name=e.name.replace('left_','right_');[e.from[0],e.to[0]]=[16-source.to[0],16-source.from[0]];
    if(e.rotation){e.rotation.origin[0]=16-e.rotation.origin[0];e.rotation.angle=-e.rotation.angle;}
    m.elements.push(e);
  }
  // The two low full blossoms have real curved-back pedicels into the branch,
  // distinct from any intentionally loose decorative petals around the bouquet.
  for(const[x,label]of[[-6.15,'left'],[22.15,'right']]){
    m.box(label+'_branch_flower_3_stalk',[x-.18,11.65,6.45],[x+.18,14.8,6.85],'branch',
      {rotation:rot('x',22.5,[x,11.8,6.65])});
  }
  // Foreground, rear and small buds have different depths, avoiding a flat flower strip.
  const blooms=[[-3.8,17.45,6.65,.95,'rose_light'],[-6.75,16.05,7.05,1.08,'rose'],[-9.8,13.35,6.75,1.18,'rose_light'],[-6.15,11.75,6.3,.98,'rose'],[-11.65,15.05,8.15,.87,'rose_dark'],[-3.6,14.15,7.2,.83,'rose_dark']];
  for(const[sign,label]of[[-1,'left'],[1,'right']]){
    blooms.forEach(([x,y,z,s,t],i)=>blossom(m,label+'_sakura_flower_'+i,sign<0?x:16-x,y,z,s,t));
    for(const[i,x,y,z]of[[0,-12.65,12,7.5],[1,-8.9,17.4,8.35],[2,-4.4,18.55,7.8]]){
      const bx=sign<0?x:16-x;octXY(m,label+'_sakura_bud_'+i,bx,y,.45,.6,z,z+.7,'rose');
      m.box(label+'_bud_tip_'+i,[bx-.18,y+.3,z-.08],[bx+.18,y+.65,z+.18],'rose_light');
    }
  }
  // Gold ribbon bows wrap around each branch; thin independent tails hang clear.
  for(const[x,label]of[[-4.5,'left'],[20.5,'right']]){
    const y=12.65,z=6.2;
    m.box(label+'_ribbon_knot',[x-.4,y-.45,z-.35],[x+.4,y+.45,z+.45],'gold_light');
    m.box(label+'_ribbon_bow_l',[x-2.1,y-.45,z],[x-.35,y+.6,z+.6],'gold',{rotation:rot('z',-22.5,[x-.35,y,z+.3])});
    m.box(label+'_ribbon_bow_r',[x+.35,y-.45,z],[x+2.1,y+.6,z+.6],'gold',{rotation:rot('z',22.5,[x+.35,y,z+.3])});
    m.box(label+'_ribbon_tail_long',[x-.8,6.9,z+.1],[x-.1,y-.35,z+.5],'gold',{rotation:rot('z',-22.5,[x-.45,y-.35,z+.3])});
    m.box(label+'_ribbon_tail_short',[x+.1,8.15,z+.15],[x+.8,y-.35,z+.55],'gold_light',{rotation:rot('z',22.5,[x+.45,y-.35,z+.35])});
    for(let i=0;i<3;i++)m.box(label+'_ribbon_gold_fringe_'+i,[x-.75+i*.3,6.3,z+.15],[x-.6+i*.3,7.15,z+.45],'gold_dark',{rotation:rot('z',-22.5,[x-.45,y-.35,z+.3])});
  }
  m.box('lantern_bottom_tassel_cord',[7.75,-1.55,7.4],[8.25,.25,7.9],'gold_dark');
  octXZ(m,'lantern_bottom_tassel_knot',8,7.65,.78,.68,-2.15,-1.35,'gold');
  for(let i=0;i<7;i++){
    const x=6.7+i*.38,extra=(i%3)*.2;
    m.box('lantern_bottom_tassel_thread_'+i,[x,-5.1+extra,7.25],[x+.24,-2.05,8.05],i%2?'gold':'gold_light');
  }
  m.box('lantern_tassel_red_binding',[6.55,-2.55,7.1],[9.45,-2.1,8.2],'lacquer_light');
  display(m,.37);return m;
}

export function buildFestival(){
  const result=[trainingDummy(),sakuraLanternArch()];
  for(const m of result){m.save();console.log(`${m.id}: ${m.elements.length} named cuboids, ${Object.keys(m.swatches).length} material tiles, 128x128 RGBA`);}
  return result;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))buildFestival();
