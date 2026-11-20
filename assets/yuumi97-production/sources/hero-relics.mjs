/** New hero relics only. Run directly, or call buildHeroRelics().
 * Model origin [8,8,8], front -Z. No shared registry, Java, or build side effects.
 * Every source component remains a named editable vanilla cuboid.
 */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Model,SOURCE,shade} from '../tools/asset-kit.mjs';

const q=n=>Math.round(n*1000000)/1000000;
const rotation=(axis,angle,origin)=>({axis,angle,origin:origin.map(q),rescale:false});
const box=(m,name,from,to,mat,rot,more={})=>m.box(name,from.map(q),to.map(q),mat,{...more,...(rot?{rotation:rot}:{})});
function setup(id,size,palette){
  const m=new Model(id,size);
  for(const [name,color]of Object.entries(palette)){
    m.swatch(name,color,{noise:false,kind:/leather|cloth|cover/.test(name)?'cloth':undefined});
    const {x,y}=m.swatches[name],c=m.canvas;
    if(/gold|silver|quartz/.test(name)){
      c.line(x+1,y+1,x+13,y+1,shade(color,14));c.line(x+2,y+2,x+2,y+11,shade(color,9));
      c.line(x+12,y+7,x+12,y+14,shade(color,-13));
    }
    if(/obsidian/.test(name)){
      c.line(x+1,y+12,x+5,y+5,shade(color,8)).line(x+5,y+5,x+11,y+2,shade(color,6));
      c.line(x+7,y+14,x+12,y+8,shade(color,-7));
    }
    if(name==='leather'||name==='wrap')for(let yy=2;yy<16;yy+=4)c.line(x,y+yy,x+15,y+yy-2,shade(color,-12));
    if(name==='paper')for(let yy=3;yy<15;yy+=4)c.line(x+2,y+yy,x+13,y+yy,shade(color,-9));
    if(name.startsWith('nebula')){
      c.rect(x,y,16,16,color);for(let yy=3;yy<14;yy+=4)c.line(x+1,y+yy,x+14,y+Math.min(15,yy+3),shade(color,5));
    }
  }
  return m;
}
// Rasterize the authored XY silhouette. Front/rear UVs are continuous over the
// entire profile, rather than repeating a full material tile on every thin row.
function profile(m,name,points,z0,z1,mat,step=.5,rot){
  const minX=Math.min(...points.map(p=>p[0])),maxX=Math.max(...points.map(p=>p[0]));
  const minY=Math.min(...points.map(p=>p[1])),maxY=Math.max(...points.map(p=>p[1]));
  const n=Math.ceil((maxY-minY)/step),dy=(maxY-minY)/n,uv=m.swatches[mat].uv,runs=[];
  for(let i=0;i<n;i++){
    const ya=minY+i*dy,yb=minY+(i+1)*dy,yc=(ya+yb)/2,hit=[];
    for(let j=0,k=points.length-1;j<points.length;k=j++){
      const a=points[k],b=points[j];if((a[1]>yc)!==(b[1]>yc))hit.push(a[0]+(yc-a[1])*(b[0]-a[0])/(b[1]-a[1]));
    }
    hit.sort((a,b)=>a-b);
    for(let j=0;j+1<hit.length;j+=2){
      if(hit[j+1]-hit[j]<.0001)continue;
      const last=runs.at(-1);
      if(last&&Math.abs(last[0]-hit[j])<.000001&&Math.abs(last[2]-hit[j+1])<.000001&&Math.abs(last[3]-ya)<.000001)last[3]=yb;
      else runs.push([hit[j],ya,hit[j+1],yb]);
    }
  }
  runs.forEach(([xa,ya,xb,yb],i)=>{
    const fuv=[uv[0]+(xa-minX)/(maxX-minX)*(uv[2]-uv[0]),uv[1]+(maxY-yb)/(maxY-minY)*(uv[3]-uv[1]),uv[0]+(xb-minX)/(maxX-minX)*(uv[2]-uv[0]),uv[1]+(maxY-ya)/(maxY-minY)*(uv[3]-uv[1])].map(q);
    box(m,`${name}_${i}`,[xa,ya,z0],[xb,yb,z1],mat,rot,{faces:{north:{uv:fuv},south:{uv:fuv}}});
  });
}
function octY(m,name,x,y,z,w,h,d,mat,rot){
  const cut=Math.min(w,d)*.22;
  box(m,name+'_center',[x-w/2+cut,y,z-d/2],[x+w/2-cut,y+h,z+d/2],mat,rot);
  box(m,name+'_left',[x-w/2,y,z-d/2+cut],[x-w/2+cut,y+h,z+d/2-cut],mat,rot);
  box(m,name+'_right',[x+w/2-cut,y,z-d/2+cut],[x+w/2,y+h,z+d/2-cut],mat,rot);
}
function ringXY(m,name,x,y,z,rx,ry,t,depth,mat,step=.4,rot){
  const n=Math.ceil(ry*2/step),dy=ry*2/n;
  for(let i=0;i<n;i++){
    const ya=y-ry+i*dy,yb=ya+dy,v=(ya+yb)/2-y,outer=rx*Math.sqrt(Math.max(0,1-v*v/(ry*ry)));
    const inner=Math.abs(v)<ry-t?(rx-t)*Math.sqrt(Math.max(0,1-v*v/((ry-t)*(ry-t)))):0;
    if(inner){box(m,`${name}_${i}_left`,[x-outer,ya,z],[x-inner,yb,z+depth],mat,rot);box(m,`${name}_${i}_right`,[x+inner,ya,z],[x+outer,yb,z+depth],mat,rot);}
    else box(m,`${name}_${i}_cap`,[x-outer,ya,z],[x+outer,yb,z+depth],mat,rot);
  }
}
function ringXZ(m,name,x,y,z,r,t,h,mat){
  const n=Math.ceil(r*2/.6),dz=r*2/n;
  for(let i=0;i<n;i++){
    const za=z-r+i*dz,zb=za+dz,v=(za+zb)/2-z,outer=Math.sqrt(Math.max(0,r*r-v*v)),inner=Math.abs(v)<r-t?Math.sqrt((r-t)*(r-t)-v*v):0;
    if(inner){box(m,`${name}_${i}_left`,[x-outer,y,za],[x-inner,y+h,zb],mat);box(m,`${name}_${i}_right`,[x+inner,y,za],[x+outer,y+h,zb],mat);}
    else box(m,`${name}_${i}_cap`,[x-outer,y,za],[x+outer,y+h,zb],mat);
  }
}
function diamond(m,name,x,y,z,w,h,mat,frame='gold',rot){
  profile(m,name+'_setting',[[x,y-h/2],[x+w/2,y],[x,y+h/2],[x-w/2,y]],z,z+.55,frame,.3,rot);
  profile(m,name+'_stone',[[x,y-h*.37],[x+w*.34,y],[x,y+h*.37],[x-w*.34,y]],z-.27,z+.06,mat,.25,rot);
}
function rune(m,name,x,y,z,s,mat,rot){
  box(m,name+'_stem',[x-.12*s,y-.9*s,z],[x+.12*s,y+.9*s,z+.12],mat,rot);
  box(m,name+'_upper',[x-.65*s,y+.40*s,z],[x+.65*s,y+.60*s,z+.12],mat,rot);
  box(m,name+'_lower',[x-.4*s,y-.55*s,z],[x+.4*s,y-.35*s,z+.12],mat,rot);
}
function star(m,name,x,y,z,s,mat){
  box(m,name+'_vertical',[x-.13*s,y-s,z],[x+.13*s,y+s,z+.13],mat);
  box(m,name+'_horizontal',[x-s,y-.13*s,z],[x+s,y+.13*s,z+.13],mat);
  box(m,name+'_center',[x-.24*s,y-.24*s,z-.04],[x+.24*s,y+.24*s,z+.14],mat);
}
function pathVoxels(m,name,points,width,mat){
  let index=0;
  for(let j=0;j<points.length-1;j++){
    const a=points[j],b=points[j+1],n=Math.ceil(Math.hypot(...a.map((v,i)=>v-b[i]))/(width*.75));
    for(let i=0;i<=n;i++){
      const p=a.map((v,k)=>v+(b[k]-v)*i/n);
      box(m,name+'_'+index++,p.map(v=>v-width/2),p.map(v=>v+width/2),mat);
    }
  }
}
function pawXY(m,name,x,y,z,s,mat,rot){
  profile(m,name+'_palm',[[x-s,y-.65*s],[x-.65*s,y+.5*s],[x,y+.75*s],[x+.65*s,y+.5*s],[x+s,y-.65*s],[x+.4*s,y-.9*s],[x,y-.65*s],[x-.4*s,y-.9*s]],z,z+.35,mat,.25,rot);
  for(const [i,dx,dy]of [[0,-1.25,.75],[1,-.47,1.35],[2,.47,1.35],[3,1.25,.75]])
    box(m,`${name}_toe_${i}`,[x+(dx-.27)*s,y+(dy-.33)*s,z],[x+(dx+.27)*s,y+(dy+.33)*s,z+.35],mat,rot);
}

function applyHands(m,records,anchor,kind,scale){
  const contexts=['thirdperson_righthand','thirdperson_lefthand','firstperson_righthand','firstperson_lefthand'];
  const effective=kind==='open_book'?[[32,0,0],[32,0,0],[23,-18,5],[23,18,-5]]:
    kind==='closed_book'?[[5,-35,12],[5,35,-12],[8,-22,8],[8,22,-8]]:
    kind==='staff'?[[0,-90,8],[0,90,-8],[0,-78,18],[0,78,-18]]:
    [[0,-90,15],[0,90,-15],[0,-90,24],[0,90,-24]];
  const rotate=(v,angles)=>{
    let[x,y,z]=v;const[a,b,c]=angles.map(v=>v*Math.PI/180);
    [x,y]=[x*Math.cos(c)-y*Math.sin(c),x*Math.sin(c)+y*Math.cos(c)];
    [x,z]=[x*Math.cos(b)+z*Math.sin(b),-x*Math.sin(b)+z*Math.cos(b)];
    [y,z]=[y*Math.cos(a)-z*Math.sin(a),y*Math.sin(a)+z*Math.cos(a)];return[x,y,z];
  };
  const record={anchor,kind,forward:kind==='closed_book'?[0,0,-1]:[0,1,0],contexts:{}};
  contexts.forEach((context,i)=>{
    const s=scale*(i>=2?1.02:1),r=effective[i],target=[0,1.5,0],left=context.endsWith('lefthand');
    const offset=rotate(anchor.map(v=>(v-8)*s),r);
    const translation=target.map((v,k)=>q((v-offset[k])*(left&&k===0?-1:1)));
    m.display[context]={rotation:left?[r[0],-r[1],-r[2]]:r,translation,scale:[s,s,s]};
    record.contexts[context]={target,effectiveRotation:r,mirroredByVanilla:left};
  });
  m.display.gui={rotation:[20,-35,0],translation:[0,-2,0],scale:[.6,.6,.6]};
  m.display.fixed={rotation:[0,180,0],translation:[0,-2,0],scale:[.65,.65,.65]};
  m.display.ground={rotation:[0,0,0],translation:[0,5,0],scale:[.34,.34,.34]};
  records[m.id]=record;
}

function greatsword(anchors){
  const m=setup('weapon_berserker_greatsword',64,{obsidian:'#1F1B24',obsidianLight:'#383040',edge:'#514551',groove:'#100E18',lavaDark:'#9E360D',lava:'#FF5400',hot:'#FFB800',white:'#FFF3B0',goldDark:'#9B6225',gold:'#F5B82E',goldLight:'#FFDE7A',rubyDark:'#631A3D',ruby:'#AB2952',leather:'#4B2731',wrap:'#8C4A38',copper:'#B97142'});
  box(m,'grip_leather_core',[6.95,-.7,6.95],[9.05,7.9,9.05],'leather');
  for(let i=0;i<7;i++){
    const y=-.3+i*1.1;box(m,'grip_cross_wrap_'+i,[6.82,y,6.82],[9.18,y+.34,9.18],i%3===0?'copper':'wrap');
    box(m,'grip_front_stitch_'+i,[7.63,y+.35,6.72],[8.37,y+.55,6.95],'goldDark');
  }
  octY(m,'grip_upper_ferrule',8,7.45,8,3.15,.9,3.15,'goldDark');
  octY(m,'pommel_neck',8,-1.3,8,2.8,.9,2.8,'gold');
  ringXY(m,'pommel_open_golden_ring',8,-2.5,7.3,1.75,1.65,.6,1.4,'gold',.35);
  ringXY(m,'pommel_front_bead',8,-2.5,7.13,1.62,1.52,.23,.22,'goldLight',.35);
  // The black backing stops behind the recess. Separate front cheek plates leave
  // an actual channel where the hot core sits deeper than the obsidian surface.
  const outline=[[2.6,10.3],[13.4,10.3],[14.5,15.1],[13.2,17.4],[15.4,20.6],[14.1,23.3],[15.1,27.8],[12.8,31.4],[7.9,30.8],[3.0,28.5],[3.7,25.3],[1.9,22.8],[3.15,19.9],[1.6,16.7]];
  profile(m,'blade_mass_backing',outline,7.15,8.85,'obsidian',.55);
  const left=[[2.6,10.3],[7.1,10.3],[7.1,28.7],[7.9,30.8],[3.0,28.5],[3.7,25.3],[1.9,22.8],[3.15,19.9],[1.6,16.7]];
  const right=[[8.9,10.3],[13.4,10.3],[14.5,15.1],[13.2,17.4],[15.4,20.6],[14.1,23.3],[15.1,27.8],[12.8,31.4],[8.9,29.4]];
  profile(m,'blade_front_left_facet',left,5.85,7.25,'obsidianLight',.55);
  profile(m,'blade_front_right_facet',right,5.85,7.25,'obsidian',.55);
  profile(m,'blade_back_left_facet',left,8.75,10.15,'obsidian',.55);
  profile(m,'blade_back_right_facet',right,8.75,10.15,'obsidianLight',.55);
  profile(m,'fuller_dark_channel',[[6.7,10.1],[9.3,10.1],[9.3,28.6],[12.1,30.2],[8.2,29.5],[6.7,28.8]],6.65,9.35,'groove',.5);
  const lava=[[7.25,10.15],[8.8,10.15],[8.7,15.5],[9.0,18.2],[8.5,22.8],[8.85,27.65],[11.9,29.5],[11.35,30.25],[7.5,28.55],[7.4,23.0],[7.75,18.6],[7.1,15.6]];
  profile(m,'fuller_front_molten_edge',lava,6.34,6.72,'lava',.4);
  profile(m,'fuller_rear_molten_edge',lava,9.28,9.66,'lava',.4);
  const hot=[[7.72,10.4],[8.30,10.4],[8.2,15.7],[8.53,18.3],[8.05,23],[8.38,27.9],[11.5,29.65],[11.28,29.95],[7.95,28.15],[7.85,23.1],[8.1,18.5],[7.65,15.7]];
  profile(m,'fuller_front_hot_core',hot,6.20,6.40,'hot',.35);
  profile(m,'fuller_rear_hot_core',hot,9.60,9.80,'hot',.35);
  for(const [i,y]of [12.4,17.7,23.2,27.6].entries())box(m,'fuller_white_heat_'+i,[7.85,y,6.09],[8.12,y+.7,6.24],'white');
  // A few molten fissures branch into forged chips; the main uninterrupted vein
  // remains dominant. Edge teeth are solid obsidian, with distinct chipped facets.
  for(const [i,x,y,s]of [[0,4.1,13.9,-1],[1,12.7,18.4,1],[2,3.8,24.0,-1]]){
    const a=rotation('z',s*22.5,[x,y,8]);
    box(m,'blade_chipped_tooth_'+i,[x-1.3,y-1.7,6.45],[x+1.3,y+1.7,9.55],'obsidian',a);
    box(m,'blade_chip_highlight_'+i,[x-.95,y+.8,6.30],[x+.75,y+1.2,6.48],'edge',a);
  }
  for(const [i,x,y,s]of [[0,6.2,14.5,-1],[1,9.8,21.6,1],[2,6.0,26.4,-1]]){
    box(m,'branch_fissure_'+i,[x-.15,y-1.2,5.70],[x+.15,y+1.2,5.95],'lavaDark',rotation('z',s*45,[x,y,6]));
    box(m,'branch_ember_'+i,[x-.09,y-.8,5.61],[x+.09,y+.8,5.76],'lava',rotation('z',s*45,[x,y,6]));
  }
  box(m,'guard_cast_crossbar',[-.3,8.1,6.1],[16.3,10.4,9.9],'goldDark');
  box(m,'guard_front_gilded_land',[.2,8.45,5.73],[15.8,10.07,6.25],'gold');
  for(const s of [-1,1]){
    const p=[[8+s*3.3,8.8],[8+s*6.7,8.9],[8+s*8.5,11.6],[8+s*8.8,14.4],[8+s*7.25,13.2],[8+s*6.7,11.0],[8+s*4.3,10.5]];
    profile(m,'guard_roaring_claw_'+s,p,6.25,9.75,'gold',.45);
    for(let i=0;i<3;i++){
      const x=8+s*(5.4+i*.95),y=10.6+i*.75;
      box(m,`guard_talon_${s}_${i}`,[x-.27,y-.4,5.95],[x+.27,y+1.2,6.38],'goldLight',rotation('z',s*-22.5,[x,y,6.2]));
    }
  }
  profile(m,'guard_cat_mask',[[5.65,8.3],[5.3,11.3],[6.7,10.8],[8,11.55],[9.3,10.8],[10.7,11.3],[10.35,8.3],[8,7.55]],5.48,6.38,'goldDark',.35);
  for(const s of [-1,1])box(m,'guard_cat_fierce_eye_'+s,[8+s*1.05-.43,9.8,5.29],[8+s*1.05+.43,10.15,5.51],'white',rotation('z',s*-22.5,[8+s*1.05,10,5.4]));
  diamond(m,'guard_ruby_roar',8,8.95,5.03,2.3,2.5,'ruby','goldLight');
  box(m,'guard_ruby_glint',[7.65,9.15,4.67],[7.96,9.6,4.82],'white');
  applyHands(m,anchors,[8,3.2,8],'greatsword',.63);
  return m;
}

function priestStaff(anchors){
  const m=setup('weapon_priest_staff',64,{ink:'#143544',quartz:'#F4F7FA',quartzShade:'#A9C4D3',goldDark:'#9D813C',gold:'#E8C56B',goldLight:'#FFF0B3',moonDeep:'#56B4D3',moon:'#91E5F6',moonLight:'#E0FBFC',waterDeep:'#008E91',water:'#00F5D4',waterLight:'#D2FFF1',cloth:'#DAE8F3',silver:'#C8DBEB',mint:'#72E8D0',silverDark:'#55839C'});
  octY(m,'staff_quartz_haft',8,-7,8,1.8,22.4,1.8,'quartz');
  box(m,'staff_front_quartz_flute',[7.72,-6.2,6.98],[8.28,14.8,7.17],'moonLight');
  for(let i=0;i<8;i++){
    const y=-6.7+i*2.85;
    octY(m,'staff_gold_joint_'+i,8,y,8,2.25,.55,2.25,'gold');
    box(m,'staff_joint_front_glint_'+i,[7.48,y+.3,6.77],[8.52,y+.46,6.94],'goldLight');
  }
  octY(m,'staff_lower_foot',8,-8.3,8,2.6,1.3,2.6,'goldDark');
  octY(m,'staff_lower_crystal',8,-9.0,8,1.7,.85,1.7,'moon');
  diamond(m,'staff_lower_teardrop',8,-8.0,6.62,1.1,1.6,'water','gold');
  octY(m,'crescent_neck_gold',8,14.8,8,3.8,1.2,3.6,'goldDark');
  octY(m,'crescent_neck_cap',8,15.85,8,4.6,.65,4.3,'gold');
  // Upright open crescent: large negative space stays visible around the chalice.
  // +X is the viewer's left. The opening is toward model -X / viewer right.
  const moon=[[2.6,30.7],[6.3,31.3],[10.3,30.1],[13.1,27.2],[14.2,23.6],[13.5,19.4],[10.5,16.2],[6.6,15.5],[3.0,16.5],[1.5,18.2],[5.6,17.6],[8.8,18.8],[11.0,21.5],[11.3,24.7],[9.8,27.5],[6.7,29.2],[3.3,29.1]];
  profile(m,'moon_back_silver_depth',moon,6.25,9.75,'moonDeep',.4);
  profile(m,'moon_front_blue_facet',moon,5.95,6.4,'moon',.4);
  const inner=[[2.8,30.25],[6.25,30.85],[10.0,29.65],[12.65,27.0],[13.7,23.6],[13.05,19.65],[10.15,16.6],[6.65,15.95],[3.3,16.85],[2.9,17.3],[5.7,17.08],[9.15,18.4],[11.5,21.3],[11.85,24.8],[10.15,27.95],[6.8,29.75],[3.25,29.65]];
  profile(m,'moon_luminous_front_edge',inner,5.79,6.03,'moonLight',.35);
  profile(m,'moon_luminous_rear_edge',inner,9.72,9.91,'moonLight',.35);
  // Gold chalice has a genuinely open bowl: stepped annuli, not a closed block
  // with a turquoise sticker. The magically suspended cup intentionally floats.
  const cx=6.4,cz=8;
  octY(m,'chalice_foot',cx,18.0,cz,4.1,.6,3.9,'goldDark');
  octY(m,'chalice_foot_bright',cx,18.45,cz,3.65,.45,3.5,'gold');
  octY(m,'chalice_stem',cx,18.9,cz,1.15,2.3,1.15,'goldLight');
  octY(m,'chalice_lower_bowl',cx,20.8,cz,2.8,.7,2.8,'goldDark');
  ringXZ(m,'chalice_lower_wall',cx,21.35,cz,2.0,.65,.75,'gold');
  ringXZ(m,'chalice_middle_wall',cx,22.05,cz,2.65,.58,.78,'gold');
  ringXZ(m,'chalice_open_lip',cx,22.75,cz,2.98,.5,.55,'goldLight');
  octY(m,'chalice_holy_water_body',cx,21.65,cz,3.8,1.0,3.8,'waterDeep');
  octY(m,'chalice_holy_water_surface',cx,22.65,cz,4.6,.13,4.6,'water');
  box(m,'chalice_water_reflection',[cx-1.35,22.79,cz-.8],[cx+.35,22.84,cz-.5],'waterLight');
  box(m,'chalice_water_reflection_small',[cx+.55,22.79,cz+.6],[cx+1.3,22.84,cz+.9],'waterLight');
  diamond(m,'chalice_front_aqua_jewel',cx,21.55,5.45,1.05,1.25,'water','goldLight');
  for(const [i,x,y]of [[0,4.4,25.2],[1,8.5,26.2],[2,2.4,22.8]])star(m,'holy_starlight_'+i,x,y,7.8,.38,'moonLight');
  for(const s of [-1,1]){
    const r=rotation('z',s*22.5,[8,15.6,8]);
    box(m,'neck_acanthus_'+s,[8+s*1.5-.35,14.5,6.1],[8+s*1.5+.35,17.0,7.0],'gold',r);
  }
  applyHands(m,anchors,[8,2.8,8],'staff',.61);
  return m;
}

function orbTome(anchors){
  const m=setup('weapon_arcane_orb_tome',128,{ink:'#21122E',cover:'#3A245B',coverLight:'#624282',coverDark:'#241837',paper:'#F3E8EE',paperShade:'#CEBDD2',paperLight:'#FFF7F6',inkRune:'#8E598F',goldDark:'#947045',gold:'#D8B46B',goldLight:'#FFE4A4',nebulaDark:'#3D087A',nebula:'#5A189A',nebulaMid:'#843ACA',nebulaLight:'#C77DFF',nebulaPale:'#E6B8FF',star:'#FFFFFF',silver:'#D9D2E8',ribbon:'#AA62B2',ribbonDark:'#593766',cyan:'#7DE8EF',cyanDark:'#3A8CBA',obsidian:'#161127',violet:'#A653D8'});
  octY(m,'book_spine_binding',8,.75,8,2.2,2.5,17.2,'coverDark');
  box(m,'book_spine_gilt', [7.45,.55,-.55],[8.55,1.0,16.55],'goldDark');
  for(const s of [-1,1]){
    const side=s<0?'left':'right',r=rotation('z',s*22.5,[8,1.8,8]);
    const x0=s<0?-4:8.1,x1=s<0?7.9:20;
    box(m,'book_'+side+'_hardcover',[x0,1.0,-.35],[x1,1.9,16.35],'cover',r);
    box(m,'book_'+side+'_gilt_page_edge',[x0+.2,1.92,.05],[x1-.2,2.35,15.95],'goldDark',r);
    box(m,'book_'+side+'_paper_block',[x0+.45,2.35,.3],[x1-.35,2.85,15.7],'paperShade',r);
    box(m,'book_'+side+'_open_page',[x0+.6,2.85,.45],[x1-.45,3.05,15.55],'paper',r);
    for(let row=0;row<5;row++){
      const z=2.0+row*2.55;
      box(m,`book_${side}_constellation_rule_${row}`,[x0+1.45,3.075,z],[x1-1.3,3.14,z+.16],'inkRune',r);
      for(const dx of [.0,2.0,4.7])box(m,`book_${side}_star_point_${row}_${dx}`,[x0+2.0+dx,3.09,z-.45],[x0+2.25+dx,3.19,z-.2],row%2?'goldDark':'violet',r);
    }
    for(const xx of [x0+.2,x1-1.0])for(const zz of [-.3,15.25]){
      box(m,`book_${side}_corner_${xx}_${zz}`,[xx,.88,zz],[xx+.8,2.2,zz+1.05],'gold',r);
    }
    for(let i=0;i<4;i++)box(m,`book_${side}_foreedge_leaf_${i}`,[x0+.5,2.38+i*.105,.12],[x1-.45,2.42+i*.105,.28],'paperLight',r);
  }
  box(m,'book_center_silk_marker',[7.72,2.85,-1.0],[8.28,3.11,16.9],'ribbon');
  box(m,'book_marker_hanging_tail',[7.72,-1.7,16.6],[8.28,2.91,17.05],'ribbonDark');
  diamond(m,'book_marker_star_weight',8,-1.3,16.42,1.3,1.7,'cyan','gold');
  // Rounded sphere from successive disc sections. The sphere owns genuine front,
  // back and side volume; only its designed nebula and stars use small appliques.
  const center=[8,17.3,8],radius=6.2,step=.85,layers=Math.ceil(radius*2/step),dy=radius*2/layers;
  for(let j=0;j<layers;j++){
    const ya=center[1]-radius+j*dy,yb=ya+dy,ym=(ya+yb)/2,r=Math.sqrt(radius*radius-(ym-center[1])**2),rows=Math.ceil(r*2/.85),dz=r*2/rows;
    for(let k=0;k<rows;k++){
      const za=center[2]-r+k*dz,zb=za+dz,zm=(za+zb)/2,rx=Math.sqrt(Math.max(.01,r*r-(zm-center[2])**2));
      const material=j>layers*.70?'nebulaLight':j>layers*.42?'nebulaMid':j>layers*.17?'nebula':'nebulaDark';
      box(m,`orb_spherical_layer_${j}_${k}`,[center[0]-rx,ya,za],[center[0]+rx,yb,zb],material);
    }
  }
  const surface=(x,y,front=true)=>8+(front?-1:1)*Math.sqrt(Math.max(.1,radius*radius-(x-8)**2-(y-17.3)**2));
  for(const [i,x,y,s]of [[0,6.0,20.5,.72],[1,10.3,18.8,.48],[2,7.5,15.2,.48],[3,10.6,14.5,.27],[4,4.2,16.4,.28],[5,9.5,21.7,.25]])star(m,'orb_front_star_'+i,x,y,surface(x,y)-.18,s,'star');
  for(const [i,x,y,s]of [[0,8.8,20.0,.55],[1,5.4,17.0,.45],[2,10.6,15.4,.32]])star(m,'orb_rear_star_'+i,x,y,surface(x,y,false)+.02,s,'nebulaPale');
  for(const [i,x,y]of [[0,4.9,19.8],[1,5.8,19.0],[2,6.6,18.4],[3,7.6,17.7],[4,8.7,17.0],[5,9.8,16.4]]){
    const z=surface(x,y)-.07;box(m,'orb_nebula_stream_'+i,[x-.32,y-.19,z-.06],[x+.32,y+.19,z+.10],'nebulaPale');
  }
  // Exactly four independent orbital rune cards. Rear cards sit higher so all
  // four count as distinct silhouette features in the front/three-quarter views.
  const cards=[{x:.0,y:17.2,z:1.0,a:-22.5},{x:16,y:17.2,z:1.0,a:22.5},{x:.0,y:25.4,z:15.0,a:22.5},{x:16,y:25.4,z:15.0,a:-22.5}];
  for(const [i,c]of cards.entries()){
    const r=rotation('y',c.a,[c.x,c.y,c.z]),n='orbit_page_'+i;
    box(m,n+'_gilded_body',[c.x-2.1,c.y-3.2,c.z-.48],[c.x+2.1,c.y+3.2,c.z+.48],'gold',r);
    box(m,n+'_parchment_front',[c.x-1.77,c.y-2.87,c.z-.60],[c.x+1.77,c.y+2.87,c.z-.45],'paper',r);
    box(m,n+'_violet_back',[c.x-1.77,c.y-2.87,c.z+.45],[c.x+1.77,c.y+2.87,c.z+.60],'coverLight',r);
    rune(m,n+'_main_rune',c.x,c.y,c.z-.75,1.05,'inkRune',r);
    for(const sy of [-1,1]){
      box(m,n+'_front_rule_'+sy,[c.x-1.3,c.y+sy*2.14,c.z-.76],[c.x+1.3,c.y+sy*2.14+.17,c.z-.57],'goldDark',r);
      box(m,n+'_rear_gold_star_'+sy,[c.x-.24,c.y+sy*1.6-.24,c.z+.58],[c.x+.24,c.y+sy*1.6+.24,c.z+.72],'goldLight',r);
    }
  }
  for(const [i,x,y,z]of [[0,-.5,10.8,7],[1,16.5,11.3,9],[2,8,27.0,10]])star(m,'orbit_starlight_'+i,x,y,z,.5,'goldLight');
  applyHands(m,anchors,[8,1.55,8],'open_book',.55);
  return m;
}

function guidebook(anchors){
  const m=setup('guidebook',64,{ink:'#1A2444',navy:'#243F73',navyLight:'#456CB0',goldDark:'#9D763A',gold:'#E8BE61',goldLight:'#FFE8AD',cover:'#FF85C0',coverShade:'#B34F86',coverLight:'#FFC5DE',jelly:'#E95AA9',jellyLight:'#FFDAEC',paper:'#F5EADB',paperShade:'#CDB9B6',ribbon:'#75CBEA',ribbonDark:'#367EAB',white:'#FFF9F0'});
  box(m,'closed_page_block',[2.25,1.2,6.45],[13.75,18.7,9.75],'paperShade');
  box(m,'closed_front_hardcover',[1.6,.6,5.45],[14.4,19.3,6.5],'navy');
  box(m,'closed_rear_hardcover',[1.6,.6,9.7],[14.4,19.3,10.75],'navy');
  box(m,'front_coral_leather_inset',[2.35,1.45,5.12],[13.65,18.45,5.6],'cover');
  box(m,'rear_coral_leather_inset',[2.35,1.45,10.6],[13.65,18.45,10.94],'coverShade');
  box(m,'royal_blue_rounded_spine',[1.0,.85,5.9],[2.55,19.05,10.3],'navy');
  box(m,'spine_light_seam',[.87,1.2,6.42],[1.09,18.7,6.75],'navyLight');
  for(let i=0;i<5;i++)box(m,'spine_gilded_raised_band_'+i,[.82,2.0+i*3.7,5.72],[2.65,2.55+i*3.7,10.48],'goldDark');
  for(let i=0;i<11;i++)box(m,'foreedge_page_line_'+i,[13.71,1.55+i*1.54,6.63],[13.91,1.67+i*1.54,9.56],'paper');
  box(m,'upper_exposed_page_edge',[2.6,18.7,6.55],[13.65,18.88,9.6],'paper');
  for(const y of [1.85,17.65])box(m,'cover_horizontal_gold_rule_'+y,[2.9,y,4.98],[13.15,y+.25,5.17],'gold');
  for(const x of [2.9,12.9])box(m,'cover_vertical_gold_rule_'+x,[x,2.0,4.98],[x+.25,17.65,5.17],'gold');
  for(const x of [1.45,13.35])for(const y of [.45,18.45]){
    box(m,`corner_brass_wrap_${x}_${y}`,[x,y,5.25],[x+1.15,y+.95,10.95],'goldDark');
    box(m,`corner_front_glint_${x}_${y}`,[x+.08,y+.08,5.04],[x+1.05,y+.85,5.3],'goldLight');
  }
  // Raised cat-ear cartouche encloses a four-toed jelly paw. The frame's center
  // is genuinely open, leaving the contrasting navy medallion below visible.
  profile(m,'cat_emblem_navy_medallion',[[4.0,7.3],[4.0,12.8],[5.2,15.3],[7.0,13.8],[9.0,13.8],[10.8,15.3],[12.0,12.8],[12.0,7.3],[10.0,5.5],[6,5.5]],4.55,5.20,'navy',.45);
  const frameParts=[[[4,7.3],[4,12.8],[5.2,15.3],[7,13.8],[6.5,13.2],[5.25,14.1],[4.8,12.6],[4.8,7.6]],[[12,7.3],[12,12.8],[10.8,15.3],[9,13.8],[9.5,13.2],[10.75,14.1],[11.2,12.6],[11.2,7.6]],[[4,7.3],[6,5.5],[10,5.5],[12,7.3],[11.2,7.6],[9.7,6.3],[6.3,6.3],[4.8,7.6]]];
  frameParts.forEach((p,i)=>profile(m,'cat_ear_gold_cartouche_'+i,p,4.08,4.74,'gold',.3));
  pawXY(m,'front_jelly_paw',8,9.4,3.64,1.65,'jelly');
  pawXY(m,'front_jelly_paw_facets',7.87,9.59,3.48,1.37,'jellyLight');
  box(m,'jelly_paw_main_glint',[7.1,10.0,3.34],[7.6,10.35,3.53],'white');
  // The clasp wraps around the fore edge; its visible face is a tiny second paw.
  box(m,'clasp_wrap_front',[12.15,8.55,4.72],[15.05,10.9,5.85],'goldDark');
  box(m,'clasp_foreedge_hinge',[14.35,8.55,5.5],[15.05,10.9,10.5],'gold');
  box(m,'clasp_rear_hinge',[12.9,8.55,10.35],[15.05,10.9,11.12],'goldDark');
  box(m,'clasp_front_plate',[12.25,8.7,4.45],[14.9,10.75,4.9],'gold');
  pawXY(m,'clasp_paw_stamp',13.56,9.46,4.26,.54,'goldLight');
  box(m,'bookmark_blue_silk',[8.1,-3.2,7.7],[9.5,1.5,8.15],'ribbon');
  box(m,'bookmark_blue_fold',[8.1,-3.2,8.13],[8.55,1.5,8.3],'ribbonDark');
  profile(m,'bookmark_swallowtail',[[8.1,-3],[8.1,-4.3],[8.8,-3.72],[9.5,-4.3],[9.5,-3]],7.7,8.15,'ribbon',.25);
  star(m,'bookmark_starlight',8.8,-2.25,7.52,.36,'white');
  for(const [i,x,y]of [[0,4,3.5],[1,11.7,16.6]])star(m,'cover_small_star_'+i,x,y,4.8,.38,'goldLight');
  applyHands(m,anchors,[5.8,4.0,8.1],'closed_book',.64);
  return m;
}

function wayfinder(){
  const m=setup('prop_wayfinder_arrow',64,{ink:'#17394B',tealDark:'#187F9A',teal:'#36CFC9',tealLight:'#9AF7EC',ice:'#E2FFF7',goldDark:'#AE8035',gold:'#FFD666',goldLight:'#FFF2BA',navy:'#244A70',sky:'#76C7E7',skyLight:'#C6F3FF',violet:'#8F75B8'});
  // Diamond cross-sections form a true crystal spear point along -Z, with four
  // differently shaded quadrants. The tip is not a flat arrow sign.
  const layers=[[-14.0,-12.3,.45],[-12.3,-10.0,1.45],[-10,-7.0,2.8],[-7,-3.5,4.5],[-3.5,0,6.0],[0,3.0,6.8],[3,5.4,5.1],[5.4,7.2,3.3]];
  for(const [i,[za,zb,r]]of layers.entries()){
    const rows=Math.ceil(r*2/.55),dy=r*2/rows;
    for(let j=0;j<rows;j++){
      const ya=8-r+j*dy,yb=ya+dy,ym=(ya+yb)/2,half=r*(1-Math.abs(ym-8)/r);
      box(m,`crystal_diamond_${i}_${j}_left`,[8-half,ya,za],[8,yb,zb],ym>=8?'tealLight':'teal');
      box(m,`crystal_diamond_${i}_${j}_right`,[8,ya,za],[8+half,yb,zb],ym>=8?'teal':'tealDark');
    }
  }
  for(const [i,dir]of [[0,[1,0]],[1,[-1,0]],[2,[0,1]],[3,[0,-1]]]){
    const pts=layers.slice(1).map(([za,zb,r])=>[8+dir[0]*(r+.10),8+dir[1]*(r+.10),(za+zb)/2]);
    pathVoxels(m,'gold_crystal_wing_rib_'+i,pts,.62,i===2?'goldLight':'gold');
  }
  box(m,'arrow_teal_shaft',[7.22,7.22,5.8],[8.78,8.78,25.8],'tealDark');
  box(m,'arrow_shaft_light_ridge',[7.58,8.72,6.0],[8.42,8.95,24.8],'tealLight');
  for(const z of [8.5,14.2,20.0])octY(m,'shaft_gold_joint_'+z,8,6.95,z,2.25,2.1,1.1,'goldDark');
  // Rear cat-ear fletching: a triangular gap separates the two upright ear fins.
  for(const s of [-1,1]){
    const p=[[8+s*.65,8.7],[8+s*6.05,9.3],[8+s*4.5,15.2]];
    profile(m,'rear_cat_ear_frame_'+s,p,22.5,25.5,'gold',.45);
    const inner=[[8+s*1.85,9.65],[8+s*5.0,10.0],[8+s*4.35,13.65]];
    profile(m,'rear_cat_ear_crystal_'+s,inner,22.28,25.7,'sky',.4);
    profile(m,'rear_cat_ear_inner_glint_'+s,[[8+s*2.55,10.05],[8+s*3.1,10.1],[8+s*4.18,12.8]],22.14,22.35,'skyLight',.4);
    box(m,'rear_lateral_fin_'+s,[8+s*3.5-.35,6.4,20.0],[8+s*3.5+.35,8.0,27.3],'goldDark',rotation('z',s*22.5,[8+s*3.5,7.2,23.5]));
  }
  box(m,'rear_tail_ferrule',[6.75,6.75,25.35],[9.25,9.25,27.2],'gold');
  profile(m,'rear_fourpoint_star',[[8,4.8],[8.7,7.3],[11.2,8],[8.7,8.7],[8,11.2],[7.3,8.7],[4.8,8],[7.3,7.3]],27.2,28.15,'goldLight',.35);
  m.display.gui={rotation:[35,45,0],translation:[0,0,0],scale:[.5,.5,.5]};
  m.display.ground={rotation:[0,0,0],translation:[0,4,0],scale:[.35,.35,.35]};
  return m;
}

function corners(e){
  const p=[];
  for(const x of[e.from[0],e.to[0]])for(const y of[e.from[1],e.to[1]])for(const z of[e.from[2],e.to[2]]){
    let v=[x,y,z];
    if(e.rotation){const{axis,angle,origin}=e.rotation,a=angle*Math.PI/180,c=Math.cos(a),s=Math.sin(a);v=v.map((n,k)=>n-origin[k]);const[x,y,z]=v;
      if(axis==='x')v=[x,y*c-z*s,y*s+z*c];if(axis==='y')v=[x*c+z*s,y,-x*s+z*c];if(axis==='z')v=[x*c-y*s,x*s+y*c,z];v=v.map((n,k)=>n+origin[k]);}
    p.push(v);
  }return p;
}
function check(m){
  const names=new Set();
  for(const e of m.elements){
    if(names.has(e.name))throw Error('Duplicate '+e.name);names.add(e.name);
    if([...e.from,...e.to].some(v=>!Number.isFinite(v)||v< -16||v>32)||e.to.some((v,k)=>v<=e.from[k]))throw Error('Bad bounds '+e.name);
    if(e.rotation&&![-45,-22.5,0,22.5,45].includes(e.rotation.angle))throw Error('Bad angle '+e.name);
    for(const f of Object.values(e.faces))if(f.uv.some(v=>!Number.isFinite(v)||v<0||v>16)||f.uv[2]<=f.uv[0]||f.uv[3]<=f.uv[1])throw Error('Bad UV '+e.name);
  }
  for(const [context,d]of Object.entries(m.display))if(d.translation.some(v=>Math.abs(v)>80)||d.scale.some(v=>v<=0||v>4))throw Error('Bad display '+context);
  const pts=m.elements.flatMap(corners),bounds=[0,1,2].map(k=>[Math.min(...pts.map(p=>p[k])),Math.max(...pts.map(p=>p[k]))]);
  if(bounds.some(([a,b])=>a< -16-.00001||b>32.00001))throw Error('Rotated bounds '+m.id+' '+JSON.stringify(bounds));
  return{id:m.id,elements:m.elements.length,textureSize:m.atlasSize,bounds:bounds.map(v=>v.map(q)),front:[0,0,-1],origin:[8,8,8]};
}
export function buildHeroRelics(){
  const records={},models=[greatsword(records),priestStaff(records),orbTome(records),guidebook(records),wayfinder()];
  const reports=models.map(check);
  const anchors={schemaVersion:2,units:'Minecraft model units',rotationConvention:'XYZ intrinsic; column matrix Rx Ry Rz; left hand negates stored rotation Y/Z and translation X',notice:'Neutral display-space grip targets. Enclosing player transforms require client verification.',models:records};
  for(const m of models)m.save();
  fs.writeFileSync(path.join(SOURCE,'hero-grip-anchors.json'),JSON.stringify(anchors,null,2)+'\n');
  return{models:reports,anchors};
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1])){
  const result=buildHeroRelics();console.log(JSON.stringify({models:result.models,grips:Object.keys(result.anchors.models)},null,2));
}
