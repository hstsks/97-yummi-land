import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Model, shade} from '../tools/asset-kit.mjs';

// Editable wall landmarks. One model unit = 1/16 block. The mounting back is Z=8.
// The -Z review camera sees +X to its LEFT. We author in viewer coordinates (u,y,z)
// and mirror u to model X=16-u, including the sign of rotations about Z.
// Shape hierarchy: structural back > deep moulding > recessed field > focal relief
// > attached secondary ornaments. No generated GUI assets or Java/build side effects.
const C={
  bronze:'#77502C',goldShade:'#AD7330',gold:'#DBAB43',goldLight:'#F8D76D',goldGlint:'#FFF0B2',
  velvetDark:'#390E2B',velvet:'#770E37',velvetLight:'#A52649',velvetSeam:'#4B102D',
  ink:'#17172E',navy:'#111C3D',navyLight:'#243765',navyLine:'#3B4C79',
  pearl:'#E9E9EA',pearlWarm:'#FFF0CE',pearlCool:'#BCCBDB',white:'#FFFAE7',
  cyanShade:'#187C92',cyan:'#25CFDF',cyanLight:'#B3FFF2',
  pinkShade:'#9D255F',pink:'#F54F9A',pinkLight:'#FFD0E4',
  yellowShade:'#A8771C',yellow:'#F9D94B',yellowLight:'#FFF3A1',
  emeraldShade:'#185949',emerald:'#32A77E',emeraldLight:'#9CECB2',
  ruby:'#B42450',rubyLight:'#F27B91',iron:'#393B4D'
};
const round=n=>Math.round(n*1000000)/1000000;
const rot=(angle,u,y,z)=>({axis:'z',angle:-angle,origin:[round(16-u),round(y),round(z)],rescale:false});
function box(m,name,from,to,material,rotation,extra={}){
  return m.box(name,[round(16-to[0]),round(from[1]),round(from[2])],
    [round(16-from[0]),round(to[1]),round(to[2])],material,{...extra,...(rotation?{rotation}:{})});
}
function setup(id){
  const m=new Model(id,128);
  for(const [name,color] of Object.entries(C)){
    m.swatch(name,color,{noise:false,kind:name.startsWith('velvet')?'cloth':undefined});
    const {x,y}=m.swatches[name];
    if(name.startsWith('gold')||name==='bronze'){
      // Brushed cast metal: restrained highlights, a protected dark lower edge.
      m.canvas.line(x+1,y+1,x+14,y+1,shade(color,12));
      m.canvas.line(x+2,y+3,x+13,y+3,shade(color,5));
      m.canvas.line(x+1,y+14,x+14,y+14,shade(color,-12));
      for(let xx=3;xx<14;xx+=4)m.canvas.line(x+xx,y+5,x+xx,y+11,shade(color,3));
    }
    if(name.startsWith('velvet')){
      for(let xx=2;xx<16;xx+=5)m.canvas.line(x+xx,y,x+xx,y+15,shade(color,-7));
    }
    if(name==='pearl'){
      for(let yy=1;yy<16;yy+=5){
        m.canvas.line(x,y+yy,x+15,y+Math.min(15,yy+3),'#D5D9E7');
        m.canvas.line(x,y+Math.min(15,yy+1),x+15,y+Math.min(15,yy+4),'#EEE0E5');
      }
      m.canvas.line(x+2,y+2,x+7,y+2,'#FBF9E6');
    }
    if(name==='navy'){
      m.canvas.rect(x,y,16,16,color);
      m.canvas.line(x,y+4,x+15,y+11,'#142143');
      m.canvas.line(x,y+5,x+15,y+12,'#172547');
    }
  }
  m.display.gui={rotation:[8,180,0],translation:[0,-1,0],scale:[.36,.36,.36]};
  m.display.fixed={rotation:[0,180,0],translation:[0,0,0],scale:[.5,.5,.5]};
  return m;
}

// Horizontal voxel runs of a closed 2D profile, extruded into real depth. Adjacent
// rows share exact boundaries. This expresses arbitrary silhouettes using legal
// axis-aligned vanilla cuboids, without pretending arbitrary element angles work.
function profile(m,name,points,z0,z1,material,step=.5,extra={}){
  const low=Math.min(...points.map(p=>p[1])),high=Math.max(...points.map(p=>p[1]));
  const rows=Math.ceil((high-low)/step),dy=(high-low)/rows,runs=[];
  for(let i=0;i<rows;i++){
    const y0=low+i*dy,y1=low+(i+1)*dy,ym=(y0+y1)/2,xs=[];
    for(let j=0,k=points.length-1;j<points.length;k=j++){
      const [xa,ya]=points[k],[xb,yb]=points[j];
      if((ya>ym)!==(yb>ym))xs.push(xa+(ym-ya)*(xb-xa)/(yb-ya));
    }
    xs.sort((a,b)=>a-b);
    for(let j=0;j+1<xs.length;j+=2){
      if(xs[j+1]-xs[j]<=.00001)continue;
      const last=runs.at(-1);
      if(last&&Math.abs(last.x0-xs[j])<.000001&&Math.abs(last.x1-xs[j+1])<.000001&&Math.abs(last.y1-y0)<.000001)last.y1=y1;
      else runs.push({x0:xs[j],x1:xs[j+1],y0,y1,row:i,span:j/2});
    }
  }
  for(const r of runs)box(m,`${name}_row_${r.row}_${r.span}`,[r.x0,r.y0,z0],[r.x1,r.y1,z1],material,undefined,extra);
}
function octPoints(u,y,r){const q=r*.41421356237;return[[u-q,y-r],[u+q,y-r],[u+r,y-q],[u+r,y+q],[u+q,y+r],[u-q,y+r],[u-r,y+q],[u-r,y-q]];}
function octPlate(m,name,u,y,r,z0,z1,material,step=.65){profile(m,name,octPoints(u,y,r),z0,z1,material,step);}
function disc(m,name,u,y,r,z0,z1,material,step=.45){
  const pts=Array.from({length:32},(_,i)=>[u+Math.cos(i*Math.PI/16)*r,y+Math.sin(i*Math.PI/16)*r]);
  profile(m,name,pts,z0,z1,material,step);
}
function arch(m,name,u,bottom,spring,rx,ry,z0,z1,material){
  const pts=[[u-rx,bottom],[u+rx,bottom],[u+rx,spring]];
  for(let i=1;i<=24;i++){const a=i*Math.PI/24;pts.push([u+Math.cos(a)*rx,spring+Math.sin(a)*ry]);}
  profile(m,name,pts,z0,z1,material,.65);
}
function star(m,name,u,y,z,r,material){
  box(m,name+'_vertical',[u-r*.17,y-r,z],[u+r*.17,y+r,z+.20],material);
  box(m,name+'_horizontal',[u-r,y-r*.17,z],[u+r,y+r*.17,z+.20],material);
  box(m,name+'_diamond',[u-r*.30,y-r*.30,z-.08],[u+r*.30,y+r*.30,z+.20],material,rot(45,u,y,z));
}
function jewel(m,name,u,y,z,w,h,color='emerald',angle=0){
  const R=angle?rot(angle,u,y,z):undefined;
  box(m,name+'_gold_setting',[u-w*.62,y-h*.62,z],[u+w*.62,y+h*.62,z+.65],'goldShade',R);
  box(m,name+'_bevel',[u-w*.53,y-h*.53,z-.25],[u+w*.53,y+h*.53,z+.1],'goldLight',R);
  box(m,name+'_stone',[u-w*.39,y-h*.39,z-.55],[u+w*.39,y+h*.39,z-.22],color,R);
  box(m,name+'_facet',[u-w*.27,y+h*.13,z-.58],[u+w*.18,y+h*.28,z-.53],color+'Light',R);
}
function paw(m,name,u,y,z,s,material='goldLight'){
  box(m,name+'_palm',[u-.64*s,y-.64*s,z],[u+.64*s,y+.35*s,z+.16],material);
  for(const [i,dx,dy] of [[0,-.9,.4],[1,-.34,.85],[2,.34,.85],[3,.9,.4]])
    box(m,`${name}_toe_${i}`,[u+(dx-.23)*s,y+(dy-.23)*s,z],[u+(dx+.23)*s,y+(dy+.23)*s,z+.16],material);
}
function curvedLine(m,name,points,z,w,d,material){
  // Deliberate block steps follow a silhouette or inlay; no illegal diagonal angle.
  for(let i=0;i<points.length;i++){
    const [u,y]=points[i];box(m,`${name}_${i}`,[u-w/2,y-w/2,z],[u+w/2,y+w/2,z+d],material);
  }
}

function seven(m,name,u,y,color){
  const p=[[0,11.8],[7.1,11.8],[7.1,10.2],[2.45,0],[.15,0],[4.7,9.8],[0,9.8]];
  const map=(scale,dx=0,dy=0)=>p.map(([x,yy])=>[u+3.55+(x-3.55)*scale+dx,y+5.9+(yy-5.9)*scale+dy]);
  profile(m,name+'_recessed_black_housing',map(1.12,.07,-.06),1.35,3.4,'ink',.5);
  profile(m,name+'_cast_gold_edge',map(1.03),.35,1.65,'goldShade',.5);
  profile(m,name+'_neon_volume',map(.91),-.55,.65,color,.5,{shade:false});
  // Visible fine hot core across the top and along the diagonal, inset inside color.
  box(m,name+'_top_luminous_core',[u+.42,y+10.7,-.72],[u+6.47,y+11.12,-.52],color+'Light',undefined,{shade:false});
  const a=[u+5.56,y+10.35],b=[u+1.62,y+.82];
  curvedLine(m,name+'_diagonal_luminous_core',Array.from({length:20},(_,i)=>[a[0]+(b[0]-a[0])*i/19,a[1]+(b[1]-a[1])*i/19]),-.72,.35,.20,color+'Light');
}
function card(m,name,u,y,angle,suit){
  const R=rot(angle,u,y,1.4),B=(n,a,b,t)=>box(m,name+'_'+n,a,b,t,R);
  B('rear_cast_attachment',[u-.9,y-2.4,2.55],[u+.9,y+2.4,4.45],'bronze');
  B('cast_gold_back',[u-2.6,y-4.0,1.3],[u+2.6,y+4.0,2.65],'goldShade');
  B('gold_edge',[u-2.48,y-3.88,.85],[u+2.48,y+3.88,1.65],'goldLight');
  B('ivory_face',[u-2.1,y-3.52,.55],[u+2.1,y+3.52,.90],'white');
  // Front suit silhouette is composed in local coordinates before a legal tilt.
  const color=suit==='heart'?'ruby':'ink';
  const rows=suit==='heart'?['0110110','1111111','1111111','0111110','0011100','0001000']:['0001000','0011100','0111110','1111111','0111110','0001000','0011100'];
  rows.forEach((row,j)=>{let start=-1;for(let i=0;i<=row.length;i++){if(row[i]==='1'&&start<0)start=i;if(row[i]!=='1'&&start>=0){B(`suit_${j}_${start}`,[u+(start-3.5)*.43,y+(rows.length/2-j-1)*.43,.27],[u+(i-3.5)*.43,y+(rows.length/2-j)*.43,.57],color);start=-1;}}});
  const ar=['010','101','111','101','101'];
  ar.forEach((row,j)=>[...row].forEach((v,i)=>{if(v==='1')B(`ace_${j}_${i}`,[u-1.7+i*.27,y+2.75-j*.27,.26],[u-1.45+i*.27,y+3.0-j*.27,.56],color);}));
  B('bottom_suit_stamp',[u+1.14,y-3.1,.26],[u+1.58,y-2.66,.56],color);
}
function die(m,name,u,y,angle,value){
  const R=rot(angle,u,y,1.1),B=(n,a,b,t)=>box(m,name+'_'+n,a,b,t,R);
  B('rounded_middle',[u-1.9,y-1.65,-.8],[u+1.9,y+1.65,3.4],'gold');
  B('rounded_cross',[u-1.65,y-1.9,-.8],[u+1.65,y+1.9,3.4],'gold');
  B('front_bevel',[u-1.65,y-1.65,-1.10],[u+1.65,y+1.65,-.65],'goldLight');
  B('front_ivory',[u-1.42,y-1.42,-1.28],[u+1.42,y+1.42,-1.08],'pearlWarm');
  const pips=value===5?[[-.8,.8],[.8,.8],[0,0],[-.8,-.8],[.8,-.8]]:[[-.8,.8],[0,0],[.8,-.8]];
  pips.forEach(([x,yy],i)=>B('front_pip_'+i,[u+x-.24,y+yy-.24,-1.50],[u+x+.24,y+yy+.24,-1.25],'ruby'));
  for(const xx of [-.8,.8])B('upper_pip_'+xx,[u+xx-.24,y+1.87,.55],[u+xx+.24,y+1.94,1.15],'bronze');
  for(const yy of [-.8,.8])B('side_pip_'+yy,[u+1.87,y+yy-.22,.7],[u+1.95,y+yy+.22,1.18],'bronze');
}
function coin(m,name,u,y,z,r){
  octPlate(m,name+'_edge',u,y,r,z,z+.65,'goldShade',.45);
  octPlate(m,name+'_raised_face',u,y,r*.84,z-.17,z+.02,'goldLight',.42);
  octPlate(m,name+'_recess',u,y,r*.62,z-.22,z-.14,'gold',.42);
  paw(m,name+'_paw_stamp',u,y-.05,z-.38,r*.43,'goldGlint');
}

function marquee(){
  const m=setup('prop_casino_marquee');
  // Recessed architectural chassis, gold casting, two stepped mouldings and velvet.
  arch(m,'structure_wall_backplate',8,-5.9,9.4,20.6,9.4,6.8,8,'bronze');
  arch(m,'structure_outer_casting',8,-6.1,9.4,20.7,9.55,4.7,6.85,'goldShade');
  arch(m,'moulding_outer_gold_land',8,-5.75,9.4,20.35,9.15,3.3,4.85,'gold');
  arch(m,'moulding_raised_bead',8,-5.15,9.4,19.75,8.55,2.75,3.65,'goldLight');
  // Each inset is a solid profile; its visible front must be closer to -Z than
  // the larger moulding behind it. Otherwise the solid gold casting occludes it.
  arch(m,'moulding_shadow_reveal',8,-4.55,9.4,19.1,7.9,2.45,4.35,'velvetDark');
  arch(m,'field_velvet_panel',8,-3.85,9.2,18.4,7.3,2.15,3.25,'velvet');
  // Front lower sill and slender corner flutes have their own returns and caps.
  box(m,'sill_deep_return',[-10.8,-6.0,2.85],[26.8,-4.85,6.55],'bronze');
  box(m,'sill_gilded_roll',[-11.25,-5.5,2.25],[27.25,-4.65,5.25],'goldLight');
  box(m,'sill_crimson_inset',[-8.1,-4.55,2.10],[24.1,-3.72,2.8],'velvetDark');
  for(const s of [-1,1]){
    const u=8+s*18.3;
    box(m,'pilaster_'+s+'_shaft',[u-.8,-3.8,1.95],[u+.8,9.5,4.7],'goldShade');
    box(m,'pilaster_'+s+'_flute',[u-.29,-3.2,1.60],[u+.29,8.8,2.10],'goldLight');
    box(m,'pilaster_'+s+'_capital',[u-1.3,8.7,1.55],[u+1.3,10.05,4.7],'gold');
    jewel(m,'pilaster_'+s+'_ruby',u,8.9,1.25,1.15,.9,'ruby');
  }
  // Bulb rhythm stays outside the quiet central 777 field; painted bright cores
  // are vanilla texture colors, not a claim of an unsupported emissive material.
  for(let i=0;i<23;i++){
    const a=Math.PI*(.05+.90*i/22),u=8+19.45*Math.cos(a),y=9.4+8.3*Math.sin(a);
    if(Math.abs(u-8)<6.8&&y>15)continue;
    box(m,'arch_bulb_'+i+'_cup',[u-.48,y-.45,2.10],[u+.48,y+.45,3.20],'goldShade');
    box(m,'arch_bulb_'+i+'_opal',[u-.28,y-.27,1.74],[u+.28,y+.27,2.20],i%3===0?'cyanLight':i%3===1?'yellowLight':'pinkLight',undefined,{shade:false});
  }
  for(const s of [-1,1])for(let i=0;i<4;i++){
    const u=8+s*19.7,y=-2.8+i*2.75;
    box(m,`side_bulb_${s}_${i}`,[u-.25,y-.25,2.57],[u+.25,y+.25,3.35],i%2?'yellowLight':'pinkLight',undefined,{shade:false});
  }
  for(let i=0;i<3;i++)seven(m,'focal_seven_'+(i+1),-4.0+i*8.45,.2,['cyan','yellow','pink'][i]);
  // Small velvet seam diamonds read as upholstery only in the free lower margin.
  for(let i=0;i<10;i++){
    const u=-6.8+i*3.3;
    box(m,'velvet_tuft_'+i,[u-.2,-2.85,1.95],[u+.2,-2.45,2.20],'velvetLight',rot(45,u,-2.65,2.1));
  }
  // Crowned cat relief is built from a deep cranial mass and receding cheek layers.
  profile(m,'cat_head_cast_shadow',[[1.0,18],[1.0,22],[3.1,25],[12.9,25],[15,22],[15,18],[12.2,15.7],[3.8,15.7]],.3,5.0,'goldShade',.55);
  profile(m,'cat_face_gilded_round',[[1.8,18.3],[1.8,22],[4.0,24.2],[12.0,24.2],[14.2,22],[14.2,18.3],[11.4,16.3],[4.6,16.3]],-.75,.8,'gold',.5);
  for(const [s,u] of [[-1,2.8],[1,13.2]]){
    profile(m,'cat_ear_'+s+'_outer',[[u-2.2,22],[u+2.2,22],[u+s*1.1,27.1]],.25,3.5,'goldShade',.5);
    profile(m,'cat_ear_'+s+'_gold',[[u-1.7,22.3],[u+1.7,22.3],[u+s*1.0,26.5]],-.15,.4,'goldLight',.5);
    profile(m,'cat_ear_'+s+'_ruby_inner',[[u-1.03,22.6],[u+1.03,22.6],[u+s*.84,25.35]],-.28,-.10,'velvetLight',.45);
  }
  box(m,'cat_forehead_brow',[4.15,21.55,-1.15],[11.85,23.25,-.55],'goldLight');
  box(m,'cat_left_open_eye_socket',[3.55,19.5,-1.42],[6.4,21.75,-.70],'bronze');
  box(m,'cat_left_emerald_eye',[3.9,19.78,-1.60],[6.1,21.45,-1.35],'emerald');
  box(m,'cat_left_pupil',[4.85,19.82,-1.75],[5.35,21.46,-1.55],'ink');
  box(m,'cat_left_eye_glint',[4.18,20.91,-1.80],[4.63,21.28,-1.70],'white');
  box(m,'cat_right_wink_left_stroke',[9.35,20.45,-1.50],[10.9,20.90,-.90],'ink',rot(-22.5,10.75,20.5,-1.1));
  box(m,'cat_right_wink_right_stroke',[10.75,20.45,-1.50],[12.15,20.90,-.90],'ink',rot(22.5,10.75,20.5,-1.1));
  box(m,'cat_cheek_left',[3.3,17.6,-1.55],[7.9,19.4,-.6],'goldLight');
  box(m,'cat_cheek_right',[8.1,17.6,-1.55],[12.7,19.4,-.6],'goldLight');
  box(m,'cat_muzzle_left',[5.4,17.5,-2.20],[8.0,18.9,-1.4],'goldGlint');
  box(m,'cat_muzzle_right',[8.0,17.5,-2.20],[10.6,18.9,-1.4],'goldGlint');
  box(m,'cat_rose_nose',[7.45,18.55,-2.50],[8.55,19.35,-2.1],'rubyLight',rot(45,8,18.95,-2.3));
  box(m,'cat_mouth_center',[7.83,17.4,-2.39],[8.17,18.55,-2.18],'bronze');
  for(const s of [-1,1]){
    box(m,'cat_smile_'+s,[8+s*.55-.65,17.25,-2.37],[8+s*.55+.65,17.55,-2.18],'bronze');
    for(let i=0;i<2;i++)box(m,`cat_whisker_${s}_${i}`,[8+s*5.65-1.5,18.0+i*.92,-1.8],[8+s*5.65+1.5,18.22+i*.92,-1.45],'bronze',rot(s*(i===0?22.5:-22.5),8+s*5.65,18.11+i*.92,-1.65));
  }
  box(m,'crown_erminum_lower_band',[3.6,24.0,-.90],[12.4,25.1,2.7],'velvetDark');
  box(m,'crown_gold_band',[3.3,24.65,-1.20],[12.7,25.8,2.5],'goldLight');
  profile(m,'crown_five_point_casting',[[3.7,25.5],[3.4,28.7],[5.9,27.2],[8,30.45],[10.1,27.2],[12.6,28.7],[12.3,25.5]],-.4,2.35,'gold',.45);
  for(const [i,u,y] of [[0,3.4,28.6],[1,8,30.35],[2,12.6,28.6]]){
    box(m,'crown_tip_'+i,[u-.42,y-.28,-.52],[u+.42,y+.52,.95],'goldLight');
  }
  jewel(m,'crown_central_ruby',8,26.3,-1.25,1.3,1.45,'ruby',45);
  for(const u of [4.8,11.2])jewel(m,'crown_emerald_'+u,u,25.25,-1.26,.7,.62,'emerald');
  // Side fan cards, dimensional gold dice and tiered stamped coin clusters.
  card(m,'left_spade_ace',-8.0,4.0,22.5,'spade');
  card(m,'right_heart_ace',24.0,4.0,-22.5,'heart');
  die(m,'left_gold_die',-9.3,-3.9,22.5,5);
  die(m,'right_gold_die',25.3,-3.9,-22.5,3);
  for(const s of [-1,1]){
    for(let i=0;i<3;i++){
      const u=8+s*(12.9+i*2.05),y=-6.5+(i%2)*.7;
      coin(m,`coin_stack_${s}_${i}_back`,u,y+.65,1.35,1.35);
      coin(m,`coin_stack_${s}_${i}_front`,u+.35*s,y,.60,1.35);
    }
    // Gilded flourish arms support the coins and have a clear carved scroll curl.
    const pts=Array.from({length:14},(_,i)=>{const t=i/13;return[8+s*(6.2+9.5*t),-5.7-.9*Math.sin(t*Math.PI)];});
    curvedLine(m,'bottom_acanthus_'+s,pts,1.65,.75,.8,'gold');
    star(m,'flank_star_'+s,8+s*17.2,12.2,1.0,1.0,'goldGlint');
  }
  box(m,'underside_center_medallion_mount',[6.5,-7.2,2.5],[9.5,-5.3,5.0],'goldShade');
  jewel(m,'underside_pendant',8,-6.6,2.12,2.0,1.4,'ruby',45);
  return m;
}

function handPoints(u,y,length,width,degrees){
  const a=degrees*Math.PI/180,dx=Math.sin(a),dy=Math.cos(a),nx=dy,ny=-dx;
  const local=[[-width*.40,-1.6],[width*.40,-1.6],[width*.42,length*.55],[width*.85,length*.71],[0,length],[-width*.85,length*.71],[-width*.42,length*.55]];
  return local.map(([w,r])=>[u+dx*r+nx*w,y+dy*r+ny*w]);
}
function clockFace(){
  const m=setup('prop_plaza_clock_face');
  octPlate(m,'structure_rear_mounting_plate',8,8,18.75,6.65,8,'bronze',.70);
  octPlate(m,'bezel_deep_cast_outer_shell',8,8,18.8,4.3,6.75,'goldShade',.65);
  octPlate(m,'bezel_outer_bright_moulding',8,8,18.35,3.5,4.6,'gold',.6);
  octPlate(m,'bezel_engraved_shadow_reveal',8,8,17.55,3.2,3.7,'bronze',.6);
  octPlate(m,'bezel_inner_rolled_gold',8,8,16.95,2.6,3.45,'goldLight',.6);
  disc(m,'dial_pearl_chapter_ring',8,8,15.7,2.05,2.85,'pearl',.55);
  disc(m,'dial_gold_inner_filleting',8,8,13.7,1.7,2.2,'goldShade',.5);
  disc(m,'dial_midnight_enamel',8,8,13.22,1.30,1.85,'navy',.5);
  // Ring accents and bezel screws are attached to the casing, never floating.
  for(let i=0;i<8;i++){
    const a=Math.PI/4*i,u=8+Math.sin(a)*17.6,y=8+Math.cos(a)*17.6;
    box(m,'bezel_screw_'+i,[u-.35,y-.35,3.08],[u+.35,y+.35,3.6],'goldLight');
    box(m,'bezel_screw_slot_'+i,[u-.21,y-.055,3.05],[u+.21,y+.055,3.10],'bronze');
  }
  // Twelve hour stones: the physical cartouche has a warm gold border and an
  // emerald raised facet; 60 separate minute marks form the inner timing ring.
  for(let i=0;i<12;i++){
    const a=i*Math.PI/6,u=8+Math.sin(a)*14.32,y=8+Math.cos(a)*14.32;
    const cardinal=i%3===0,wide=i%6===3;
    jewel(m,'hour_'+String(i||12).padStart(2,'0'),u,y,1.65,cardinal?(wide?1.92:1.15):1.2,cardinal?(wide?1.15:1.92):1.2,'emerald',cardinal?0:45);
    const innerU=8+Math.sin(a)*11.48,innerY=8+Math.cos(a)*11.48;
    box(m,'hour_inner_gold_tick_'+i,[innerU-.22,innerY-.22,1.0],[innerU+.22,innerY+.22,1.42],'goldLight',rot(45,innerU,innerY,1.2));
  }
  for(let i=0;i<60;i++){
    const a=i*Math.PI/30,u=8+Math.sin(a)*12.47,y=8+Math.cos(a)*12.47,s=i%5===0?.29:.13;
    box(m,'minute_mark_'+String(i).padStart(2,'0'),[u-s,y-s,1.07],[u+s,y+s,1.34],i%5===0?'goldLight':'pearlCool');
  }
  // Thin star-map paths occupy the lower half and leave the hand silhouettes clear.
  for(const [i,u,y,r] of [[0,3.1,3.0,.60],[1,11.0,1.7,.8],[2,15.1,5.1,.5],[3,7.0,-.5,.35],[4,1.1,9.3,.32]])star(m,'dial_star_'+i,u,y,1.0,r,i%2?'gold':'pearlCool');
  const pathPoints=[[3.1,3],[7,1.0],[11,1.7],[15.1,5.1]];
  for(let j=0;j<pathPoints.length-1;j++){
    const [a,b]=[pathPoints[j],pathPoints[j+1]];
    curvedLine(m,'constellation_link_'+j,Array.from({length:15},(_,i)=>[a[0]+(b[0]-a[0])*i/14,a[1]+(b[1]-a[1])*i/14]),1.16,.105,.18,'navyLine');
  }
  // Machined central escapement: bronze shadow, 16 chunky teeth, a bright rim,
  // visible dark pockets between the eight spokes and a projecting shaft boss.
  octPlate(m,'movement_recessed_bearing',8,8,3.8,.48,1.45,'ink',.45);
  for(let i=0;i<16;i++){
    const a=i*Math.PI/8,u=8+Math.sin(a)*3.45,y=8+Math.cos(a)*3.45;
    const angle=[0,22.5,45,-22.5][i%4];
    box(m,'movement_gear_tooth_'+i,[u-.45,y-.5,-.05],[u+.45,y+.5,.85],'gold',rot(angle,u,y,.4));
  }
  octPlate(m,'movement_gold_gear_body',8,8,3.3,-.1,.65,'goldShade',.45);
  octPlate(m,'movement_open_dark_hub',8,8,2.65,-.16,-.06,'navy',.4);
  for(let i=0;i<4;i++){
    const angle=[0,45,0,45][i],horizontal=i<2;
    box(m,'movement_spoke_'+i,horizontal?[5.25,7.72,-.4]:[7.72,5.25,-.4],horizontal?[10.75,8.28,.12]:[8.28,10.75,.12],'goldLight',rot(angle,8,8,0));
  }
  octPlate(m,'movement_axle_collar',8,8,1.55,-.8,-.35,'gold',.35);
  // Exact 10:10: hour = -55 degrees from viewer up, minute = +60 degrees.
  // Rasterized profiles provide these directions without unsupported 55/60-degree
  // element rotations. Distinct lengths and inlays keep time readable at distance.
  profile(m,'hour_hand_dark_shadow',handPoints(8,8,8.9,1.6,-55),-1.16,-.66,'bronze',.26);
  profile(m,'hour_hand_cast_gold',handPoints(8,8,8.7,1.32,-55),-1.58,-1.05,'gold',.26);
  profile(m,'hour_hand_ivory_inlay',handPoints(8,8,8.1,.58,-55),-1.77,-1.56,'pearlWarm',.24);
  profile(m,'minute_hand_dark_shadow',handPoints(8,8,11.55,1.0,60),-1.50,-.98,'bronze',.24);
  profile(m,'minute_hand_cast_gold',handPoints(8,8,11.35,.82,60),-1.96,-1.43,'goldLight',.24);
  profile(m,'minute_hand_luminous_inlay',handPoints(8,8,10.7,.28,60),-2.13,-1.94,'white',.22);
  octPlate(m,'movement_front_gold_button',8,8,1.25,-2.4,-1.25,'goldLight',.25);
  octPlate(m,'movement_sapphire_star_core',8,8,.88,-2.62,-2.38,'cyanShade',.25);
  star(m,'movement_starlight_inlay',8,8,-2.83,.64,'cyanLight');
  // Top crown ears integrate into the octagonal upper casing with deep root shoes.
  for(const [s,u] of [[-1,-1.8],[1,17.8]]){
    box(m,'ear_'+s+'_root_shoe',[u-2.6,22.3,3.35],[u+2.6,25.1,6.75],'goldShade');
    profile(m,'ear_'+s+'_cast_outer',[[u-3.6,23.6],[u+3.6,23.6],[u+s*1.45,30.35]],3.15,6.60,'goldShade',.5);
    profile(m,'ear_'+s+'_bright_front',[[u-2.95,23.9],[u+2.95,23.9],[u+s*1.40,29.65]],2.65,3.40,'gold',.45);
    profile(m,'ear_'+s+'_enamel_inlay',[[u-1.92,24.2],[u+1.92,24.2],[u+s*1.20,28.25]],2.4,2.75,'navy',.45);
    profile(m,'ear_'+s+'_pearl_sliver',[[u-.80,24.4],[u+.80,24.4],[u+s*.95,27.3]],2.25,2.45,'pearl',.45);
    jewel(m,'ear_'+s+'_emerald_clasp',u,23.95,2.2,1.4,.85,'emerald');
  }
  box(m,'crown_center_bridge',[4.6,24.85,3.1],[11.4,25.8,6.2],'goldShade');
  jewel(m,'crown_center_star_setting',8,26.05,2.6,1.8,1.8,'emerald',45);
  star(m,'crown_star_finial',8,28.0,2.35,1.10,'goldLight');
  return m;
}

function technicalCheck(m){
  const names=new Set(),lo=[Infinity,Infinity,Infinity],hi=[-Infinity,-Infinity,-Infinity];
  for(const e of m.elements){
    if(names.has(e.name))throw Error('Duplicate element name '+e.name);names.add(e.name);
    for(let k=0;k<3;k++)if(e.from[k]<-16||e.to[k]>32||e.from[k]>=e.to[k])throw Error('Invalid cuboid '+e.name);
    if(e.rotation&&![-45,-22.5,0,22.5,45].includes(e.rotation.angle))throw Error('Illegal rotation '+e.name);
    for(const f of Object.values(e.faces))if(f.uv.some(n=>n<0||n>16)||f.uv[0]===f.uv[2]||f.uv[1]===f.uv[3])throw Error('Invalid UV '+e.name);
    for(const x of [e.from[0],e.to[0]])for(const y of [e.from[1],e.to[1]])for(const z of [e.from[2],e.to[2]]){
      let p=[x,y,z];
      if(e.rotation){const [ox,oy]=e.rotation.origin,a=e.rotation.angle*Math.PI/180;p=[ox+(x-ox)*Math.cos(a)-(y-oy)*Math.sin(a),oy+(x-ox)*Math.sin(a)+(y-oy)*Math.cos(a),z];}
      for(let k=0;k<3;k++){lo[k]=Math.min(lo[k],p[k]);hi[k]=Math.max(hi[k],p[k]);}
    }
  }
  if(lo.some(n=>n< -16-.000001)||hi.some(n=>n>32.000001)||Math.abs(hi[2]-8)>.000001)throw Error('Invalid actual wall bounds '+m.id+': '+JSON.stringify([lo,hi]));
  const attachment=m.elements.some(e=>e.name.startsWith('structure_')&&e.from.every((v,k)=>v<=[8,8,8][k])&&e.to.every((v,k)=>v>=[8,8,8][k]));
  if(!attachment)throw Error('Rear attachment is not inside structure '+m.id);
  return{id:m.id,elements:m.elements.length,atlas:[128,128],bounds:[lo.map(round),hi.map(round)],mountAnchor:[8,8,8],front:[0,0,-1]};
}
export function buildSignage(){
  const models=[marquee(),clockFace()];
  const reports=models.map(technicalCheck);
  models.forEach(m=>m.save());
  return reports;
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1]))
  console.log(JSON.stringify(buildSignage(),null,2));
