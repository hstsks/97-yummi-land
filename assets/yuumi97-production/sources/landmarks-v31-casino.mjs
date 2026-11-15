/**
 * V3.1 imperial casino chandelier and dealer backdrop throne.
 * Minecraft 1.21.4 named cuboids, front = -Z; no Java or shared pipeline writes.
 */
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Model, shade} from '../tools/asset-kit.mjs';

const C={
  ink:'#272339',gold:'#DDAA49',goldLight:'#FFE2A0',goldDark:'#926032',goldDeep:'#6C4328',
  cream:'#F4E5C3',wax:'#F2D99F',flame:'#FFE8AD',flameCore:'#FFF8DF',orange:'#E9A04E',
  cyan:'#4DBFCB',cyanLight:'#B4ECE7',cyanDark:'#267D96',violet:'#8E68BD',violetLight:'#D4B6EC',violetDark:'#563F83',
  emerald:'#4CAF8A',emeraldLight:'#B8E7BC',emeraldDark:'#206751',
  velvet:'#9E3154',velvetLight:'#BE5572',velvetDark:'#65263F',wine:'#452138',wood:'#513148'
};
const rot=(axis,angle,origin)=>({axis,angle,origin,rescale:false});
const b=(m,name,from,to,texture,rotation,faces)=>m.box(name,from,to,texture,{...(rotation?{rotation}:{}),...(faces?{faces}: {})});

function setup(id){
  const m=new Model(id,128);
  for(const[name,color]of Object.entries(C)){
    m.swatch(name,color,{noise:false,kind:name.startsWith('velvet')?'cloth':undefined});
    const {x,y}=m.swatches[name];
    if(name.startsWith('gold')){
      m.canvas.line(x+2,y+2,x+12,y+2,shade(color,28)).line(x+2,y+3,x+2,y+12,shade(color,20));
      m.canvas.line(x+12,y+7,x+12,y+13,shade(color,-24));
    }
    if(name==='wood'){
      for(let i=2;i<16;i+=4)m.canvas.line(x+1,y+i,x+14,y+i+1,shade(color,-12));
    }
  }
  m.display.gui={rotation:[20,-30,0],translation:[0,-1,0],scale:[.42,.42,.42]};
  m.display.fixed={rotation:[0,180,0],translation:[0,-1,0],scale:[.55,.55,.55]};
  m.display.ground={rotation:[0,0,0],translation:[0,4,0],scale:[.32,.32,.32]};
  return m;
}

// Closed stepped solids. Adjacent sections meet; no unintended open cap corners.
function oct(m,name,cx,y,cz,w,h,d,texture,faces){
  const cut=Math.min(w,d)*.19;
  b(m,name+'_middle',[cx-w/2+cut,y,cz-d/2],[cx+w/2-cut,y+h,cz+d/2],texture,undefined,faces);
  b(m,name+'_west',[cx-w/2,y,cz-d/2+cut],[cx-w/2+cut,y+h,cz+d/2-cut],texture,undefined,faces);
  b(m,name+'_east',[cx+w/2-cut,y,cz-d/2+cut],[cx+w/2,y+h,cz+d/2-cut],texture,undefined,faces);
}
function ring8(m,name,apothem,y,h,t,texture){
  const half=apothem*Math.tan(Math.PI/8),length=half*2+t*.65;
  b(m,name+'_north',[8-length/2,y,8-apothem-t/2],[8+length/2,y+h,8-apothem+t/2],texture);
  b(m,name+'_south',[8-length/2,y,8+apothem-t/2],[8+length/2,y+h,8+apothem+t/2],texture);
  b(m,name+'_west',[8-apothem-t/2,y,8-length/2],[8-apothem+t/2,y+h,8+length/2],texture);
  b(m,name+'_east',[8+apothem-t/2,y,8-length/2],[8+apothem+t/2,y+h,8+length/2],texture);
  for(const sx of [-1,1])for(const sz of [-1,1]){
    const x=8+sx*apothem/Math.sqrt(2),z=8+sz*apothem/Math.sqrt(2);
    b(m,name+'_diagonal_'+sx+'_'+sz,[x-length/2,y,z-t/2],[x+length/2,y+h,z+t/2],texture,rot('y',sx*sz*45,[x,y,z]));
  }
}
function octPoint(a,angle){
  const c=Math.cos(angle),s=Math.sin(angle);
  const r=a/Math.max(Math.abs(c),Math.abs(s),(Math.abs(c)+Math.abs(s))/Math.sqrt(2));
  return [8+r*c,8+r*s];
}
function crystal(m,name,x,bottom,z,width,height,violet=false){
  const main=violet?'violet':'cyan',light=violet?'violetLight':'cyanLight',dark=violet?'violetDark':'cyanDark';
  const facets={north:light,south:dark,east:main,west:light,up:light,down:dark};
  // Different widths and per-face color distinguish facets from a solid glass stick.
  b(m,name+'_point',[x-width*.12,bottom,z-width*.12],[x+width*.12,bottom+height*.13,z+width*.12],dark);
  b(m,name+'_lower_cut',[x-width*.30,bottom+height*.13,z-width*.30],[x+width*.30,bottom+height*.32,z+width*.30],main,undefined,facets);
  oct(m,name+'_broad_facets',x,bottom+height*.32,z,width,height*.40,width,main,facets);
  b(m,name+'_upper_cut',[x-width*.32,bottom+height*.72,z-width*.32],[x+width*.32,bottom+height*.89,z+width*.32],main,undefined,facets);
  b(m,name+'_neck',[x-width*.17,bottom+height*.89,z-width*.17],[x+width*.17,bottom+height,z+width*.17],light);
  b(m,name+'_long_glint',[x-width*.18,bottom+height*.43,z-width*.514],[x-width*.02,bottom+height*.67,z-width*.49],light);
}
function candle(m,name,x,y,z,s=1){
  oct(m,name+'_drip_cup',x,y,z,1.85*s,.24*s,1.85*s,'goldDark');
  oct(m,name+'_cup_lip',x,y+.24*s,z,1.95*s,.15*s,1.95*s,'goldLight');
  b(m,name+'_wax',[x-.39*s,y+.39*s,z-.39*s],[x+.39*s,y+1.62*s,z+.39*s],'wax');
  b(m,name+'_wax_drip',[x+.18*s,y+.75*s,z-.44*s],[x+.39*s,y+1.51*s,z-.36*s],'cream');
  b(m,name+'_wick',[x-.10*s,y+1.62*s,z-.10*s],[x+.10*s,y+1.87*s,z+.10*s],'goldDeep');
  b(m,name+'_flame_base',[x-.25*s,y+1.80*s,z-.22*s],[x+.25*s,y+2.39*s,z+.22*s],'flame');
  b(m,name+'_flame_heart',[x-.12*s,y+1.96*s,z-.25*s],[x+.12*s,y+2.32*s,z-.18*s],'flameCore');
  b(m,name+'_flame_tip',[x-.11*s,y+2.39*s,z-.10*s],[x+.11*s,y+2.70*s,z+.10*s],'orange');
}
function frontPaw(m,name,x,y,z,s=1,base='gold'){
  b(m,name+'_palm',[x-.8*s,y,z],[x+.8*s,y+1.05*s,z+.35*s],base);
  b(m,name+'_palm_upper',[x-.56*s,y+1.05*s,z],[x+.56*s,y+1.35*s,z+.35*s],base);
  for(const [i,dx,dy]of[[0,-1.10,.85],[1,-.4,1.6],[2,.4,1.6],[3,1.10,.85]])
    b(m,name+'_toe_'+i,[x+(dx-.28)*s,y+dy*s,z],[x+(dx+.28)*s,y+(dy+.65)*s,z+.35*s],base);
}
function chainLink(m,name,x,y,z,edge=false){
  if(!edge){
    b(m,name+'_left',[x-.55,y,z-.18],[x-.25,y+1.6,z+.18],'gold');
    b(m,name+'_right',[x+.25,y,z-.18],[x+.55,y+1.6,z+.18],'gold');
    b(m,name+'_top',[x-.25,y+1.3,z-.18],[x+.25,y+1.6,z+.18],'goldLight');
    b(m,name+'_bottom',[x-.25,y,z-.18],[x+.25,y+.3,z+.18],'goldDark');
  }else{
    b(m,name+'_front',[x-.18,y,z-.55],[x+.18,y+1.6,z-.25],'gold');
    b(m,name+'_back',[x-.18,y,z+.25],[x+.18,y+1.6,z+.55],'goldDark');
    b(m,name+'_top',[x-.18,y+1.3,z-.25],[x+.18,y+1.6,z+.25],'goldLight');
    b(m,name+'_bottom',[x-.18,y,z-.25],[x+.18,y+.3,z+.25],'gold');
  }
}
function slopingArm(m,name,axis,sign,r0,y0,r1,texture='gold'){
  const dr=r1-r0,dy=-dr,length=Math.hypot(dr,dy),radius=(r0+r1)/2,y=y0+dy/2;
  if(axis==='x'){
    const x=8+sign*radius;
    b(m,name,[x-length/2,y-.29,7.71],[x+length/2,y+.29,8.29],texture,rot('z',sign===1?-45:45,[x,y,8]));
  }else{
    const z=8+sign*radius;
    b(m,name,[7.71,y-.29,z-length/2],[8.29,y+.29,z+length/2],texture,rot('x',sign===1?45:-45,[8,y,z]));
  }
}

function chandelier(){
  const m=setup('prop_casino_chandelier');
  // The crown hanger terminates exactly at [8,24,8]; no supporting floor geometry.
  oct(m,'ceiling_mount_cap',8,23.50,8,3.8,.50,3.8,'goldDark');
  oct(m,'mount_cap_inset',8,23.25,8,2.8,.25,2.8,'gold');
  for(let i=0;i<4;i++)chainLink(m,'top_chain_'+i,8,19.16+i*1.08,8,i%2===1);
  oct(m,'upper_central_fleur',8,18.55,8,3.5,1.1,3.5,'gold');
  oct(m,'upper_emerald_collar',8,18.25,8,2.8,.3,2.8,'emerald');
  b(m,'central_suspension_stem',[7.60,-.50,7.60],[8.40,19.55,8.40],'goldDark');
  b(m,'central_stem_front_glint',[7.78,1.2,7.55],[7.99,18.3,7.65],'goldLight');
  for(const [i,y,w]of[[0,15.8,2.4],[1,10.2,3.3],[2,3.7,2.5],[3,-.55,1.8]]){
    oct(m,'stem_collar_'+i,8,y,8,w,.48,w,'gold');
    oct(m,'stem_jewel_'+i,8,y+.48,8,w*.73,.24,w*.73,i%2?'violet':'cyan');
  }
  // Four diagonal golden arms join the stem hub, upper ring, and wide middle ring.
  b(m,'top_arm_cross_x',[4.9,18.65,7.65],[11.1,19.25,8.35],'gold');
  b(m,'top_arm_cross_z',[7.65,18.65,4.9],[8.35,19.25,11.1],'gold');
  for(const axis of ['x','z'])for(const sign of [-1,1]){
    slopingArm(m,'upper_swept_arm_'+axis+'_'+sign,axis,sign,2.8,19.4,12.0);
    slopingArm(m,'lower_swept_arm_'+axis+'_'+sign,axis,sign,0,11.6,7.6,'goldDark');
  }
  const tiers=[{a:6,y:16,h:.8,n:8,drop:11.65,dropH:2.8,w:.95},{a:12,y:10,h:.9,n:16,drop:5.2,dropH:3.25,w:1.28},{a:7.6,y:3.8,h:.8,n:8,drop:-1.25,dropH:3.2,w:1.10}];
  for(const [tier,t]of tiers.entries()){
    ring8(m,'tier_'+tier+'_lower_molding',t.a,t.y-.22,.25,.93,'goldDark');
    ring8(m,'tier_'+tier+'_gold_ring',t.a,t.y,t.h,.76,'gold');
    ring8(m,'tier_'+tier+'_top_glint',t.a,t.y+t.h,.14,.84,'goldLight');
    for(let i=0;i<t.n;i++){
      const angle=i*2*Math.PI/t.n,[x,z]=octPoint(t.a,angle),bottom=t.drop+(i%2?.30:0),top=bottom+t.dropH;
      b(m,'tier_'+tier+'_pendant_chain_'+i,[x-.12,top,z-.12],[x+.12,t.y+.1,z+.12],'goldDark');
      oct(m,'tier_'+tier+'_pendant_cap_'+i,x,top-.08,z,t.w*.65,.28,t.w*.65,'gold');
      crystal(m,'tier_'+tier+'_crystal_'+i,x,bottom,z,t.w,t.dropH,i%2===1);
      // Raised mounting rivets catch light above the ring's lower edge.
      oct(m,'tier_'+tier+'_ring_rivet_'+i,x,t.y+.15,z,.38,.32,.38,'goldLight');
    }
  }
  // Sixteen luminous candle points fill different levels without blocking their open centers.
  for(let i=0;i<4;i++){const[x,z]=octPoint(6,i*Math.PI/2);candle(m,'upper_candle_'+i,x,16.94,z,.78);}
  for(let i=0;i<8;i++){const[x,z]=octPoint(12,(i+.5)*Math.PI/4);candle(m,'middle_candle_'+i,x,11.04,z,.91);}
  for(let i=0;i<4;i++){const[x,z]=octPoint(7.6,(i+.5)*Math.PI/2);candle(m,'lower_candle_'+i,x,4.74,z,.73);}
  // Cat-eared jewel crest at the presentation face; paw embossing sits outside the ring.
  for(const sx of [-1,1]){
    const x=8+sx*2.0,z=1.75;
    b(m,'crest_ear_base_'+sx,[x-.58,16.75,z-.23],[x+.58,17.55,z+.23],'gold');
    b(m,'crest_ear_tip_'+sx,[x-.35,17.55,z-.23],[x+.35,18.22,z+.23],'gold');
    b(m,'crest_ear_inlay_'+sx,[x-.20,17.15,z-.29],[x+.20,17.65,z-.20],'violetLight');
  }
  b(m,'crest_front_medallion',[6.7,15.45,1.42],[9.3,17.1,1.8],'goldDark');
  frontPaw(m,'crest_paw',8,15.63,1.23,.56,'goldLight');
  // Four lower paw medallions are visibly backed by real ring metal.
  for(const [i,x,z]of[[0,8,-4.48],[1,8,19.95]]){
    b(m,'middle_paw_plate_'+i,[x-1.2,9.65,z],[x+1.2,11.15,z+.32],'goldDark');
    frontPaw(m,'middle_paw_'+i,x,9.76,z-.18,.54,'goldLight');
  }
  // A single large central pear drop sets the explicit lower anchor at Y=-4.
  oct(m,'central_drop_gold_cap',8,-.58,8,1.15,.25,1.15,'gold');
  crystal(m,'central_terminal_crystal',8,-4,8,2.20,3.5,false);
  m.display.gui={rotation:[16,-30,0],translation:[0,0,0],scale:[.41,.41,.41]};
  return m;
}

function throne(){
  const m=setup('prop_dealer_throne');
  m.swatch('velvetQuilt',C.velvet,{noise:false,kind:'cloth'});
  {
    const{x,y}=m.swatches.velvetQuilt;
    for(let yy=0;yy<16;yy++)for(let xx=0;xx<16;xx++){
      if((xx+yy)%8===0)m.canvas.pixel(x+xx,y+yy,C.velvetDark);
      else if((xx-yy+32)%8===0)m.canvas.pixel(x+xx,y+yy,C.velvetLight);
    }
    for(const xx of [4,12])for(const yy of [4,12])m.canvas.rect(x+xx,y+yy,2,2,C.goldDark);
  }
  m.swatch('rearScrollwork',C.wood,{noise:false});
  {
    const{x,y}=m.swatches.rearScrollwork;
    m.canvas.rect(x+1,y+1,14,1,C.goldDark).rect(x+1,y+14,14,1,C.goldDark);
    m.canvas.rect(x+1,y+1,1,14,C.goldDark).rect(x+14,y+1,1,14,C.goldDark);
    for(const yy of [4,8,12])m.canvas.line(x+3,y+yy,x+12,y+yy,C.wine);
    m.canvas.line(x+4,y+4,x+11,y+11,C.goldDark).line(x+11,y+4,x+4,y+11,C.goldDark);
  }
  // Floor is exactly Y=0. Each symmetric plinth is centered about X/Z=8.
  oct(m,'wide_dark_plinth',8,0,8,26,1.0,20,'wood');
  oct(m,'plinth_gold_band',8,1.0,8,25.5,.28,19.5,'goldDark');
  oct(m,'upper_dais_step',8,1.28,8,24.3,.60,18.3,'gold');
  oct(m,'velvet_dais_carpet',8,1.88,8,22.2,.17,16.2,'velvetDark');
  b(m,'front_dais_nosing',[-.4,1.75,-1.32],[16.4,2.12,-.88],'goldLight');
  for(const x of [0,3.2,6.4,9.6,12.8,16])b(m,'dais_front_stud_'+x,[x-.16,.37,-2.05],[x+.16,.68,-1.87],'gold');
  // Low seat and slender arms leave the dealer's foreground silhouette open.
  for(const x of [1.4,12.8])for(const z of [2.0,9.8]){
    b(m,'seat_leg_'+x+'_'+z,[x,2.05,z],[x+1.8,5.0,z+1.8],'wood');
    b(m,'seat_gold_foot_'+x+'_'+z,[x-.1,2.05,z-.1],[x+1.9,2.55,z+1.9],'goldDark');
  }
  b(m,'seat_wood_frame',[.2,4.65,.8],[15.8,5.45,12.4],'wood');
  b(m,'seat_gold_lower_seam',[.05,5.30,.65],[15.95,5.62,12.55],'gold');
  oct(m,'seat_rounded_velvet',8,5.62,6.6,15.2,1.05,11.2,'velvet');
  oct(m,'seat_soft_top',8,6.67,6.6,13.6,.24,9.6,'velvetLight');
  for(const x of [4.3,8,11.7])b(m,'seat_tuft_'+x,[x-.15,6.90,6.45],[x+.15,6.99,6.75],'velvetDark');
  // Structural back is behind the NPC seating volume, with a deep quilted center.
  b(m,'back_wood_structure',[-.1,4.2,12.1],[16.1,24.4,14.6],'wood',undefined,{south:'rearScrollwork'});
  b(m,'back_upper_step',[1.4,24.4,12.1],[14.6,26.5,14.6],'wood',undefined,{south:'rearScrollwork'});
  b(m,'back_top_step',[3.4,26.5,12.1],[12.6,28.3,14.6],'wood',undefined,{south:'rearScrollwork'});
  b(m,'back_inset_shadow',[.55,7.0,11.66],[15.45,24.5,12.16],'wine');
  b(m,'quilted_backrest',[1.05,7.5,11.25],[14.95,24.3,11.78],'velvetQuilt');
  b(m,'padded_upper_back',[2.1,24.3,11.25],[13.9,25.9,12.16],'velvet');
  b(m,'padded_crown_back',[4.1,25.9,11.25],[11.9,27.3,12.16],'velvet');
  // Delicate raised tuft diamonds and gold buttons create actual upholstering relief.
  for(const [row,y]of[9.4,13.1,16.8,20.5,23.5].entries()){
    for(const [col,x]of[3.0,6.35,9.7,13.05].entries()){
      const shift=row%2?.25:0;
      b(m,'quilt_diamond_'+row+'_'+col,[x-.85+shift,y-.85,11.15],[x+.85+shift,y+.85,11.31],(row+col)%2?'velvet':'velvetLight',rot('z',45,[x+shift,y,11.23]));
      b(m,'quilt_button_'+row+'_'+col,[x-.13+shift,y-.13,10.99],[x+.13+shift,y+.13,11.18],'goldDark');
    }
  }
  // Outer arch is continuous: posts, true 45-degree shoulders, then a lintel crown.
  for(const [side,x]of[[-1,-2.3],[1,18.3]]){
    b(m,'arch_pier_'+side,[x-.8,2.05,11.6],[x+.8,24.4,15.1],'goldDark');
    b(m,'arch_pier_flute_'+side,[x-.40,3.1,11.35],[x+.40,24.3,11.75],'gold');
    b(m,'arch_pier_glint_'+side,[x-.2,4.0,11.24],[x+.02,23.9,11.40],'goldLight');
    for(const [i,y,w]of[[0,2.1,2.55],[1,5.0,2.05],[2,22.8,2.25],[3,24.0,2.7]])
      oct(m,'arch_pier_collar_'+side+'_'+i,x,y,13.35,w,.40,4.0,'gold');
  }
  for(const side of [-1,1]){
    const x=8+side*7.5,y=26.65;
    b(m,'arch_sloped_shoulder_'+side,[x-4.25,y-.87,11.55],[x+4.25,y+.87,15.05],'goldDark',rot('z',-side*45,[x,y,13.3]));
    b(m,'arch_shoulder_front_molding_'+side,[x-4.19,y-.48,11.24],[x+4.19,y+.48,11.62],'gold',rot('z',-side*45,[x,y,13.3]));
    b(m,'arch_shoulder_front_glint_'+side,[x-4.10,y-.11,11.14],[x+4.10,y+.11,11.29],'goldLight',rot('z',-side*45,[x,y,13.3]));
  }
  b(m,'arch_crown_lintel',[3.4,29.15,11.55],[12.6,30.55,15.05],'goldDark');
  b(m,'arch_crown_gold_edge',[3.4,29.4,11.24],[12.6,30.25,11.62],'gold');
  b(m,'arch_crown_light_edge',[3.6,29.95,11.12],[12.4,30.17,11.30],'goldLight');
  // Inner gold frame gives a clean boundary around velvet at a distance.
  for(const x of [.55,14.9])b(m,'back_inner_border_'+x,[x,7.0,10.98],[x+.55,24.65,11.34],'gold');
  b(m,'back_lower_gold_rail',[.55,7.0,10.98],[15.45,7.5,11.34],'goldDark');
  b(m,'back_inner_brow',[2.0,25.5,10.98],[14.0,26.0,11.34],'gold');
  for(const [side,x]of[[-1,1.2],[1,14.8]]){
    b(m,'arm_front_support_'+side,[x-.52,2.05,2.0],[x+.52,8.3,3.25],'goldDark');
    oct(m,'arm_support_cap_'+side,x,8.0,2.6,1.65,.45,1.9,'gold');
    b(m,'arm_rail_'+side,[x-.72,8.4,1.7],[x+.72,9.0,11.7],'gold');
    b(m,'arm_velvet_pad_'+side,[x-.59,9.0,2.4],[x+.59,9.55,10.4],'velvet');
    b(m,'arm_pad_gloss_'+side,[x-.44,9.55,3.0],[x+.44,9.72,9.8],'velvetLight');
  }
  // Two independent side pedestals carry raised paw-shaped orb finials.
  for(const side of [-1,1]){
    const x=8+side*11.1,z=9.6;
    oct(m,'orb_pedestal_foot_'+side,x,2.05,z,3.0,.55,3.0,'goldDark');
    b(m,'orb_column_'+side,[x-.62,2.6,z-.62],[x+.62,20.8,z+.62],'gold');
    b(m,'orb_column_dark_flute_'+side,[x-.18,3.4,z-.70],[x+.18,20.2,z-.58],'goldDark');
    for(const y of [5.0,17.8,20.6])oct(m,'orb_column_collar_'+side+'_'+y,x,y,z,1.8,.43,1.8,'goldLight');
    oct(m,'paw_orb_lower_'+side,x,21.0,z,2.25,.65,2.25,'goldDark');
    oct(m,'paw_orb_body_'+side,x,21.65,z,3.0,1.50,3.0,'gold');
    oct(m,'paw_orb_cap_'+side,x,23.15,z,2.1,.65,2.1,'goldLight');
    frontPaw(m,'orb_paw_emboss_'+side,x,21.65,z-1.57,.65,'goldLight');
    b(m,'orb_green_stone_'+side,[x-.34,22.15,z-1.72],[x+.34,22.83,z-1.48],'emerald',rot('z',45,[x,22.49,z-1.60]));
  }
  // Emerald lights alternate with small gold rosettes along the upper arch.
  for(const [i,x,y]of[[0,-2.3,18.5],[1,-2.3,22.0],[2,.5,26.6],[3,4.3,29.75],[4,11.7,29.75],[5,15.5,26.6],[6,18.3,22.0],[7,18.3,18.5]]){
    b(m,'arch_jewel_socket_'+i,[x-.56,y-.56,10.90],[x+.56,y+.56,11.40],'goldDeep',rot('z',45,[x,y,11.15]));
    b(m,'arch_emerald_'+i,[x-.35,y-.35,10.73],[x+.35,y+.35,10.96],'emerald',rot('z',45,[x,y,10.86]));
    b(m,'arch_jewel_glint_'+i,[x-.17,y+.08,10.65],[x+.10,y+.29,10.78],'emeraldLight');
  }
  // Center crest crowns the arch without obstructing the central NPC silhouette.
  b(m,'crest_central_plaque',[6.2,28.35,10.70],[9.8,30.65,11.55],'gold');
  frontPaw(m,'royal_paw_crest',8,28.65,10.48,.92,'goldLight');
  b(m,'crest_small_emerald',[7.65,29.0,10.32],[8.35,29.7,10.55],'emerald',rot('z',45,[8,29.35,10.43]));
  // Gold studs articulate the back and front of the actual structural object.
  for(const x of [.35,15.65])for(const y of [7.5,11.5,15.5,19.5,23.5]){
    b(m,'back_stud_'+x+'_'+y,[x-.15,y-.15,14.52],[x+.15,y+.15,14.77],'gold');
  }
  m.display.gui={rotation:[18,-30,0],translation:[0,-3,0],scale:[.40,.40,.40]};
  return m;
}

export function buildCasino(){
  const models=[chandelier(),throne()];
  for(const model of models){
    model.save();
    const lo=[0,1,2].map(i=>Math.min(...model.elements.map(e=>e.from[i])));
    const hi=[0,1,2].map(i=>Math.max(...model.elements.map(e=>e.to[i])));
    console.log(JSON.stringify({id:model.id,elements:model.elements.length,atlas:[128,128],rawBounds:[lo,hi]}));
  }
  return models;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))buildCasino();
