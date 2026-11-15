/**
 * v3.0 profession relics: ten distinct, editable vanilla cuboid constructions.
 * Front is -Z. Pixel atlases and model JSON are the only pack outputs here.
 * Display anchors are recorded separately for the neutral hand review fixture.
 */
import fs from 'node:fs';
import path from 'node:path';
import {Model, SOURCE} from '../tools/asset-kit.mjs';

const anchors = {};
const rotation=(axis,angle,origin)=>({axis,angle,origin,rescale:false});
const b=(m,n,f,t,tex,r)=>m.box(n,f,t,tex,r?{rotation:r}:{});

function setup(id, extra) {
  const m=new Model(id,64);
  const colors={ink:'#262535',steel:'#444B5A',gold:'#E9B94F',goldDark:'#A86B35',glint:'#FFF1B3',ivory:'#FFF0D2',silver:'#CFDFE6',silverDark:'#7994A9',...extra};
  for(const [name,color] of Object.entries(colors)) {
    m.swatch(name,color,{noise:false,kind:name.toLowerCase().includes('wood')?'wood':undefined});
    const {x,y}=m.swatches[name],c=m.canvas;
    if(['gold','silver','steel'].includes(name)) {
      c.line(x+2,y+2,x+10,y+2,name==='steel'?'#768192':'#FFF8D9');
      c.line(x+2,y+3,x+2,y+11,name==='steel'?'#626C80':'#FFF1B3');
      c.line(x+12,y+6,x+12,y+13,name==='gold'?'#A86B35':'#6D849B');
    }
    if(name==='wrap')for(let yy=2;yy<16;yy+=4)c.line(x,y+yy,x+15,y+yy-2,colors.steel);
    if(name==='paper')for(let yy=3;yy<15;yy+=3)c.line(x+2,y+yy,x+13,y+yy,colors.goldDark);
    if(name==='lava') {
      c.line(x+2,y,x+5,y+5,'#FFE083').line(x+5,y+5,x+3,y+10,'#FFD263').line(x+3,y+10,x+8,y+15,'#FFE083');
      c.line(x+12,y+2,x+9,y+8,'#F4833E').line(x+9,y+8,x+13,y+13,'#F4833E');
    }
    if(name==='leather') {
      for(let yy=1;yy<16;yy+=3)c.pixel(x+1,y+yy,colors.goldDark).pixel(x+14,y+yy,colors.goldDark);
      c.line(x+3,y+3,x+12,y+3,colors.ink).line(x+3,y+12,x+12,y+12,colors.ink);
    }
  }
  return m;
}

// Extrude a hand-designed silhouette as half-pixel runs. Face UVs remain
// continuous over the whole silhouette instead of restarting every strip.
function profile(m,name,points,z0,z1,tex,step=.5) {
  const minY=Math.min(...points.map(p=>p[1])), maxY=Math.max(...points.map(p=>p[1]));
  const minX=Math.min(...points.map(p=>p[0])), maxX=Math.max(...points.map(p=>p[0]));
  const sw=m.swatches[tex].uv;
  for(let y=minY,i=0;y<maxY-1e-6;y+=step,i++) {
    const top=Math.min(maxY,y+step),cy=(y+top)/2, hits=[];
    for(let j=0,k=points.length-1;j<points.length;k=j++) {
      const a=points[j],d=points[k];
      if((a[1]>cy)!==(d[1]>cy))hits.push(a[0]+(cy-a[1])*(d[0]-a[0])/(d[1]-a[1]));
    }
    hits.sort((a,d)=>a-d);
    for(let j=0;j+1<hits.length;j+=2) {
      const x0=Math.floor(hits[j]/step)*step,x1=Math.ceil(hits[j+1]/step)*step;
      if(x1<=x0)continue;
      const uv=[sw[0]+(x0-minX)/(maxX-minX)*(sw[2]-sw[0]),sw[1]+(maxY-top)/(maxY-minY)*(sw[3]-sw[1]),sw[0]+(x1-minX)/(maxX-minX)*(sw[2]-sw[0]),sw[1]+(maxY-y)/(maxY-minY)*(sw[3]-sw[1])].map((v,k)=>Math.max(sw[k%2],Math.min(sw[2+k%2],v)));
      m.box(`${name}_${i}_${j}`,[x0,y,z0],[x1,top,z1],tex,{faces:{north:{uv},south:{uv}}});
    }
  }
}
function octagon(m,name,cx,y,cz,w,h,d,tex) {
  const inset=Math.min(w,d)*.22;
  b(m,name+'_middle',[cx-w/2+inset,y,cz-d/2],[cx+w/2-inset,y+h,cz+d/2],tex);
  b(m,name+'_left',[cx-w/2,y,cz-d/2+inset],[cx-w/2+inset,y+h,cz+d/2-inset],tex);
  b(m,name+'_right',[cx+w/2-inset,y,cz-d/2+inset],[cx+w/2,y+h,cz+d/2-inset],tex);
}
function diamond(m,name,x,y,z,w,h,tex='accent',frame='gold') {
  const pts=[[x,y-h/2],[x+w/2,y],[x,y+h/2],[x-w/2,y]];
  profile(m,name+'_setting',pts,z,z+.45,frame,.5);
  profile(m,name+'_cut',[[x,y-h*.37],[x+w*.34,y],[x,y+h*.37],[x-w*.34,y]],z-.3,z+.05,tex,.25);
  b(m,name+'_glint',[x-w*.16,y+h*.08,z-.38],[x+w*.03,y+h*.26,z-.28],'glint');
}
function floatingCrystal(m,name,x,y,z,w,h,depth,tex,light) {
  // Volumetric pointed prism: colored on both sides, with a real front/back
  // thickness. Separate horizontal octagons form the faceted taper.
  const sections=[[-.5,-.36,.22],[-.36,-.2,.58],[-.2,.18,1],[.18,.34,.68],[.34,.45,.36],[.45,.5,.12]];
  sections.forEach(([lo,hi,factor],i)=>{
    octagon(m,`${name}_faceted_core_${i}`,x,y+h*lo,z,w*factor,h*(hi-lo),depth*factor,tex);
    if(i>0&&i<5){
      const fw=w*factor,fd=depth*factor;
      b(m,`${name}_front_facet_${i}`,[x-fw*.19,y+h*lo+.03,z-fd*.5-.04],[x-fw*.02,y+h*hi-.03,z-fd*.5+.035],light);
      b(m,`${name}_rear_facet_${i}`,[x+fw*.02,y+h*lo+.03,z+fd*.5-.035],[x+fw*.19,y+h*hi-.03,z+fd*.5+.04],light);
    }
  });
  // A narrow floating binding keeps the relic's gold accent without turning
  // the back of the crystal into an opaque medallion.
  const collarY=y-h*.21;
  octagon(m,name+'_gold_collar',x,collarY,z,w*.63,.18,depth*.63,'gold');
}
function ring(m,name,x,y,z,r,thick,tex='gold') {
  b(m,name+'_left',[x-r,y-r+thick,z],[x-r+thick,y+r-thick,z+thick],tex);
  b(m,name+'_right',[x+r-thick,y-r+thick,z],[x+r,y+r-thick,z+thick],tex);
  b(m,name+'_top',[x-r+thick,y+r-thick,z],[x+r-thick,y+r,z+thick],tex);
  b(m,name+'_bottom',[x-r+thick,y-r,z],[x+r-thick,y-r+thick,z+thick],tex);
  for(const sx of [-1,1])for(const sy of [-1,1])b(m,`${name}_bevel_${sx}_${sy}`,[x+sx*(r-thick)-thick*.5,y+sy*(r-thick)-thick*.5,z],[x+sx*(r-thick)+thick*.5,y+sy*(r-thick)+thick*.5,z+thick],tex);
}
function haft(m,name,lo,hi,tex='wrap',width=1.65,x=8,z=8) {
  b(m,name+'_core',[x-width/2,lo,z-width/2],[x+width/2,hi,z+width/2],tex);
  for(let y=lo+.35,i=0;y<hi-.25;y+=1.2,i++)b(m,`${name}_binding_${i}`,[x-width/2-.12,y,z-width/2-.12],[x+width/2+.12,y+.28,z+width/2+.12],i%3===0?'goldDark':tex);
  for(const y of [lo,hi-.6])octagon(m,name+'_ferrule_'+y,x,y,z,width+1,.6,width+1,'gold');
}
function rune(m,name,x,y,z,color='glint',scale=1) {
  b(m,name+'_stem',[x-.15*scale,y-.9*scale,z],[x+.15*scale,y+.9*scale,z+.15],color);
  b(m,name+'_upper',[x-.7*scale,y+.45*scale,z],[x+.7*scale,y+.7*scale,z+.15],color);
  b(m,name+'_lower',[x-.5*scale,y-.5*scale,z],[x+.5*scale,y-.25*scale,z+.15],color);
}
function catCrest(m,name,x,y,z,tex='gold',size=1) {
  profile(m,name,[[x-1.4*size,y-.6*size],[x-1.4*size,y+1.5*size],[x-.4*size,y+.7*size],[x+.4*size,y+.7*size],[x+1.4*size,y+1.5*size],[x+1.4*size,y-.6*size],[x,y-1.2*size]],z,z+.4,tex,.25);
  for(const s of [-1,1])b(m,`${name}_eye_${s}`,[x+s*.7*size-.16,y+.05*size,z-.12],[x+s*.7*size+.16,y+.32*size,z+.02],'ink');
}
function applyDisplay(m,anchor,kind='upright',scale=.68) {
  // Minecraft 1.21.4 ItemTransform.apply mirrors rotation Y/Z and translation
  // X for the left hand before applying rotationXYZ and scale. Desired angles
  // below describe the effective transform; JSON stores the inverse mirror.
  // Verified against the locally installed official client (mapped class gnp).
  function rotateXYZ(v,r) {
    let [x,y,z]=v;const [a,d,c]=r.map(v=>v*Math.PI/180);
    [x,y]=[x*Math.cos(c)-y*Math.sin(c),x*Math.sin(c)+y*Math.cos(c)];
    [x,z]=[x*Math.cos(d)+z*Math.sin(d),-x*Math.sin(d)+z*Math.cos(d)];
    [y,z]=[y*Math.cos(a)-z*Math.sin(a),y*Math.sin(a)+z*Math.cos(a)];
    return [x,y,z];
  }
  const angles=kind==='bow'?[[0,-90,0],[0,90,0],[0,-90,12],[0,90,-12]]:
    kind==='book'?[[35,0,0],[35,0,0],[25,-18,5],[25,18,-5]]:
    kind==='drill'?[[0,-90,0],[0,90,0],[0,-70,8],[0,70,-8]]:
    [[0,-90,15],[0,90,-15],[0,-90,25],[0,90,-25]];
  const contexts=['thirdperson_righthand','thirdperson_lefthand','firstperson_righthand','firstperson_lefthand'];
  const record={anchor,kind,forward:kind==='drill'?[0,0,-1]:[0,1,0],contexts:{}};
  contexts.forEach((context,i)=>{
    const s=scale*(i>=2?1.03:1),r=angles[i],offset=rotateXYZ(anchor.map(v=>(v-8)*s),r);
    // Offset is compensated before vanilla's enclosing hand transform.
    const target=[0,1.5,0],left=context.endsWith('_lefthand');
    const translation=target.map((v,j)=>Number(((v-offset[j])*(left&&j===0?-1:1)).toFixed(4)));
    const storedRotation=left?[r[0],-r[1],-r[2]]:r;
    m.display[context]={rotation:storedRotation,translation,scale:[s,s,s]};
    record.contexts[context]={target,effectiveRotation:r,mirroredByVanilla:left};
  });
  m.display.gui={rotation:[20,-35,0],translation:[0,-2,0],scale:[.65,.65,.65]};
  m.display.fixed={rotation:[0,180,0],translation:[0,-2,0],scale:[.7,.7,.7]};
  m.display.ground={rotation:[0,0,0],translation:[0,3,0],scale:[.42,.42,.42]};
  anchors[m.id]=record;
}

function paladin(){
  const m=setup('weapon_paladin_sword',{accent:'#3976CA',accentDark:'#243B7A',accentLight:'#A6DBF0',wrap:'#294D8C'});
  haft(m,'royal_blue_grip',1.4,8.7,'wrap',1.9);
  octagon(m,'pommel_locket',8,.1,8,3.5,1.8,3.2,'gold');diamond(m,'pommel_sapphire',8,1,6.24,2.2,2.3,'accent');
  profile(m,'broadsword_outline',[[5.1,10.4],[10.9,10.4],[11.2,23.5],[8,30.5],[4.8,23.5]],7.18,8.82,'silverDark');
  profile(m,'blade_left_plane',[[5.7,11.5],[8,11.5],[8,29.4],[5.4,23.3]],6.96,7.22,'silver');
  profile(m,'blade_right_plane',[[8,11.5],[10.3,11.5],[10.6,23.3],[8,29.4]],6.96,7.22,'ivory');
  profile(m,'back_blade_facet',[[5.7,11.5],[10.3,11.5],[10.6,23.3],[8,29.4],[5.4,23.3]],8.78,9.0,'silver');
  b(m,'royal_fuller',[7.55,13.5,6.75],[8.45,24.4,7.0],'accentDark');
  for(let y=15;y<24;y+=2.4)rune(m,'blade_blessing_'+y,8,y,6.57,'accentLight',.43);
  b(m,'central_ridge',[7.9,24.1,6.78],[8.1,27.2,7.03],'glint');
  b(m,'guard_crossbar',[1.4,9.1,6.4],[14.6,10.8,9.6],'goldDark');
  b(m,'guard_front_moulding',[1.8,9.4,6.12],[14.2,10.5,6.48],'gold');
  for(const s of [-1,1]){
    const wing=[[8+s*1.4,10],[8+s*3,13],[8+s*7.8,14.8],[8+s*6.5,11.2],[8+s*3.4,9.5]];
    profile(m,'guardian_wing_'+s,wing,6.9,8.8,'gold');
    for(let i=0;i<3;i++)b(m,`wing_feather_${s}_${i}`,[8+s*(3.4+i*1.3)-.35,10.5+i*.65,6.67],[8+s*(3.4+i*1.3)+.35,12.2+i*.65,6.92],'glint',rotation('z',s*-22.5,[8+s*(3.4+i*1.3),11.4+i*.65,6.8]));
  }
  catCrest(m,'winged_cat_guard',8,10.7,5.85,'gold',1.3);
  diamond(m,'oath_sapphire',8,10.2,5.28,3.0,4.1,'accent');
  applyDisplay(m,[8,4.7,8],'upright',.64);return m;
}
function shadow(){
  const m=setup('weapon_shadow_daggers',{accent:'#A768CF',accentDark:'#4E2E72',accentLight:'#E7A2F0',magenta:'#E75DAE',wrap:'#242331'});
  haft(m,'black_bandage_grip',3.2,10.4,'wrap',1.7);
  // Deep concave edge and hooked tip leave a genuine crescent-shaped opening.
  const outline=[[6.4,10.8],[9.3,10.8],[13,14],[15.5,20],[14,25],[10,29],[10.8,24.2],[10.2,20.5],[7.8,17.9],[5.2,16.7],[3.5,14]];
  profile(m,'crescent_shadow_back',outline,7.2,8.8,'accentDark');
  profile(m,'crescent_amethyst_face',[[6.8,11.5],[8.8,11.5],[12.2,14.7],[14.5,20.1],[13.3,24.4],[11.3,26.6],[11.7,23.8],[10.9,20.1],[8.2,17.1],[5.8,16],[4.7,14.1]],6.99,7.22,'accent');
  profile(m,'magenta_cutting_edge',[[9.4,12.5],[12.5,14.5],[15,20],[13.7,24.7],[10.8,27.7],[12.7,23.8],[13.6,20],[11.4,15.3]],6.8,7.03,'magenta');
  profile(m,'back_edge_gloss',[[9.4,12.5],[12.5,14.5],[15,20],[13.7,24.7],[10.8,27.7],[12.7,23.8],[13.6,20],[11.4,15.3]],8.76,8.96,'accent');
  for(let i=0;i<3;i++)rune(m,'assassin_sigil_'+i,8.5+i*1.4,14.3+i*1.7,6.67,'accentLight',.45);
  profile(m,'curved_quillon',[[3.1,10],[5,9.1],[8,9.6],[11,9.1],[12.9,10.8],[10.9,10.4],[8,11],[5.2,10.5]],6.6,9.4,'goldDark');
  diamond(m,'guard_eye',8,10.4,6.24,2,2.5,'magenta');
  ring(m,'pommel_bell_ring',8,2.4,7.65,1.15,.42);
  octagon(m,'silent_gold_bell',8,.0,8,1.9,1.6,1.9,'gold');
  b(m,'bell_sound_slit',[7.3,.4,6.98],[8.7,.6,7.13],'ink');
  b(m,'red_tassel',[8.5,.7,8.7],[9.1,3.5,9.02],'magenta',rotation('z',-22.5,[8.8,3.4,8.9]));
  applyDisplay(m,[8,6.3,8],'upright',.69);return m;
}
function ranger(){
  const m=setup('weapon_ranger_bow',{accent:'#4CD4CB',accentDark:'#247A91',accentLight:'#C6FFF0',wrap:'#36596E'});
  haft(m,'central_riser_grip',5.4,10.6,'wrap',1.65,8,8);
  // Bow lies in XY. The string is the -X side; the stave bows toward +X.
  for(const s of [-1,1]){
    const points=[[7.4,8+s*2.2],[10.1,8+s*3.2],[11.7,8+s*7],[9.1,8+s*11.7],[2.6,8+s*15],[3.6,8+s*12],[7.3,8+s*9],[8.8,8+s*5.5],[6.9,8+s*3.3]];
    profile(m,'silver_wing_limb_'+s,points,7.15,8.85,'silver');
    profile(m,'gilded_limb_spine_'+s,[[9.0,8+s*3.5],[10.1,8+s*5],[11,8+s*7],[8.5,8+s*11.3],[2.9,8+s*14.1],[6.8,8+s*10.5],[9.2,8+s*7],[8,8+s*4.6]],6.95,7.2,'gold');
    for(let i=0;i<3;i++){
      const y=8+s*(4.8+i*2.2),x=9.7-i*.15;
      profile(m,`wing_feather_${s}_${i}`,[[x,y],[x+3.3-i*.45,y+s*2.8],[x+3-i*.45,y+s*.4],[x+1,y-s*.7]],7.7,8.7,'ivory');
      b(m,`feather_root_${s}_${i}`,[x-.25,y-.35,7.4],[x+.5,y+.35,9.0],'goldDark');
    }
    b(m,'nock_'+s,[1.9,8+s*14.6-.55,7.35],[3.3,8+s*14.6+.55,8.65],'gold');
  }
  // The 5.4-unit grip/string separation clears the neutral fist fixture.
  b(m,'taut_magic_string',[2.475,-6.2,7.82],[2.725,22.2,8.08],'accent');
  b(m,'string_center_shimmer',[2.38,6.6,7.7],[2.82,9.4,8.17],'accentLight');
  b(m,'arrow_rest',[6.5,9.5,6.3],[9.2,9.85,8.3],'gold');
  ring(m,'starlight_sight',10.5,10.2,6.85,1.35,.32,'gold');
  diamond(m,'aiming_starlight',10.5,10.2,6.28,1.3,1.7,'accent');
  b(m,'sight_arm',[8.3,10,7.0],[10.5,10.5,7.6],'goldDark');
  for(const s of [-1,1])diamond(m,'limb_star_'+s,9.2,8+s*9.5,6.45,1.8,2.7,'accent');
  applyDisplay(m,[8,8,8],'bow',.64);anchors[m.id].forward=[1,0,0];anchors[m.id].stringSide=[-1,0,0];return m;
}
function grimoire(){
  const m=setup('weapon_arcane_grimoire',{accent:'#53D6CF',accentDark:'#255677',accentLight:'#BFFFF0',violet:'#AC78DB',leather:'#392853',paper:'#EBD8B9'});
  const pivot=[8,7,8];
  b(m,'rounded_binding_spine',[7.25,4.9,2.0],[8.75,7.3,15.7],'leather');
  for(const s of [-1,1]){
    const r=rotation('z',s*22.5,pivot),x0=s<0?-1:8,x1=s<0?8:17;
    b(m,'open_cover_'+s,[x0,5.7,1.4],[x1,6.65,16.4],'leather',r);
    b(m,'fore_edge_gilding_'+s,[s<0?x0:x1-.6,6.5,1.5],[s<0?x0+.6:x1,7.1,16.3],'gold',r);
    b(m,'page_block_'+s,[x0+.65,6.7,2.15],[x1-.65,7.6,15.65],'paper',r);
    b(m,'illuminated_folio_'+s,[x0+.8,7.6,2.4],[x1-.8,7.78,15.3],'ivory',r);
    for(const z of [1.5,15.7])b(m,`gold_cover_binding_${s}_${z}`,[x0,6.5,z],[x1,6.85,z+.6],'gold',r);
    for(let i=0;i<4;i++)b(m,`page_lamination_${s}_${i}`,[x0+.5,6.85+i*.18,2.15],[x1-.5,6.93+i*.18,15.65],i%2?'ivory':'goldDark',r);
    for(let z=3.7,j=0;z<14;z+=1.4,j++)b(m,`written_rune_line_${s}_${j}`,[x0+1.6,7.82,z],[x1-1.5-(j%3)*.65,7.94,z+.21],j%3===0?'accentDark':'goldDark',r);
    for(const xx of [x0+.5,x1-1.3])for(const zz of [2,14.4]){
      b(m,`corner_guard_${s}_${xx}_${zz}`,[xx,5.45,zz],[xx+.8,6.8,zz+1.1],'gold',r);
      b(m,`corner_rivet_${s}_${xx}_${zz}`,[xx+.2,6.8,zz+.3],[xx+.6,7.08,zz+.7],'glint',r);
    }
    // Underside cover constellation remains visible when held above the hand.
    b(m,'cover_constellation_border_'+s,[x0+1.5,5.45,4],[x1-1.5,5.65,13.5],'goldDark',r);
    b(m,'cover_night_field_'+s,[x0+1.8,5.29,4.3],[x1-1.8,5.49,13.2],'leather',r);
    const constellation=[[-2,2.5],[-2,-2.5],[-.85,-1.0],[.85,-1.0],[2,-2.5],[2,2.5],[0,4.0]];
    const cx=(x0+x1)/2,cz=8.0;
    for(let k=0;k<constellation.length;k++){
      const a=constellation[k],d=constellation[(k+1)%constellation.length];
      b(m,`cat_constellation_star_${s}_${k}`,[cx+a[0]-.22,4.97,cz+a[1]-.22],[cx+a[0]+.22,5.31,cz+a[1]+.22],'glint',r);
      const segments=Math.ceil(Math.max(Math.abs(a[0]-d[0]),Math.abs(a[1]-d[1]))/.35);
      for(let j=0;j<segments;j++){
        const t=(j+.5)/segments,x=cx+a[0]+(d[0]-a[0])*t,z=cz+a[1]+(d[1]-a[1])*t;
        b(m,`cat_constellation_link_${s}_${k}_${j}`,[x-.13,5.1,z-.13],[x+.13,5.3,z+.13],'gold',r);
      }
    }
    for(const xx of [-.85,.85])b(m,`constellation_eye_${s}_${xx}`,[cx+xx-.18,5.03,cz+.45],[cx+xx+.18,5.3,cz+.8],'accent',r);
  }
  for(let z=3;z<15;z+=2)b(m,'spine_binding_'+z,[7.1,4.75,z],[8.9,7.5,z+.5],'gold');
  b(m,'silk_bookmark',[7.6,6.8,15.2],[8.35,7.05,19.1],'accent');
  b(m,'bookmark_tail',[7.6,5.6,18.6],[8.35,7.0,19.1],'accent');
  // Floating suspended crystals are separately editable cuboids and facets.
  floatingCrystal(m,'levitating_arcane_core',8,13.2,7.3,4.3,7.0,3.4,'accent','accentLight');
  floatingCrystal(m,'satellite_amethyst_left',2.5,11.4,9.4,2.1,3.7,1.5,'violet','glint');
  floatingCrystal(m,'satellite_amethyst_right',13.4,12.2,10.0,2.3,4.2,1.5,'violet','glint');
  for(const [i,x,y,z] of [[0,3.5,15.8,5.8],[1,12.5,16.1,7.4],[2,8.5,18.1,9.4]]){
    rune(m,'floating_rune_'+i,x,y,z,'accentLight',.7);
  }
  catCrest(m,'spine_cat_constellation',8,7.1,1.35,'gold',.8);
  applyDisplay(m,[8,5.4,8],'book',.6);anchors[m.id].forward=[0,1,0];return m;
}
function drill(){
  const m=setup('tool_miner_drill',{accent:'#4AD9CB',accentDark:'#1F747F',accentLight:'#BEFFF0',copper:'#C78D51',wrap:'#3B414D'});
  // Open cuff: four rails enclose the wrist without filling its center.
  for(const [name,f,t] of [['left',[3.8,3.8,8.4],[5.3,12.2,16]],['right',[10.7,3.8,8.4],[12.2,12.2,16]],['bottom',[5.3,3.8,8.4],[10.7,5.3,16]],['top',[5.3,10.7,8.4],[10.7,12.2,16]]])b(m,'wrist_cuff_'+name,f,t,'steel');
  for(const z of [9.2,14.7]){
    for(const x of [3.6,11.3])b(m,`cuff_gold_rail_${z}_${x}`,[x,3.6,z],[x+1.1,12.4,z+.6],'gold');
    for(const y of [3.6,11.3])b(m,`cuff_gold_cross_${z}_${y}`,[4.7,y,z],[11.3,y+1.1,z+.6],'gold');
  }
  b(m,'knuckle_engine',[4.1,4.1,3.8],[11.9,11.9,8.6],'copper');
  b(m,'top_armor_plate',[4.8,11.8,4.4],[11.2,12.5,8.0],'steel');
  for(let i=0;i<4;i++)b(m,'heat_sink_'+i,[5.1,12.4,4.7+i*.8],[10.9,12.85,5.05+i*.8],'goldDark');
  for(const x of [3.9,11.75])for(const y of [5.2,10.2])b(m,`fastener_${x}_${y}`,[x,y,5.0],[x+.35,y+.55,5.7],'glint');
  ring(m,'eight_tooth_drive_ring',8,8,3.1,4.1,.9,'goldDark');
  for(const [i,x,y] of [[0,8,12],[1,11.1,11.1],[2,12,8],[3,11.1,4.9],[4,8,4],[5,4.9,4.9],[6,4,8],[7,4.9,11.1]])b(m,'drive_tooth_'+i,[x-.7,y-.7,2.8],[x+.7,y+.7,4.3],'gold');
  b(m,'rotor_hub',[5.1,5.1,1.6],[10.9,10.9,3.5],'steel');
  // Tapered square/diamond sections with a four-phase spiral rib. Every rotation
  // remains one legal vanilla axis; no arbitrary mesh or cone primitive.
  for(let i=0;i<9;i++){
    const z=1.6-i*1.25,r=3.1-i*.30,rot=i%2?rotation('z',45,[8,8,z]):undefined;
    b(m,'tapered_drill_core_'+i,[8-r,8-r,z-1.25],[8+r,8+r,z],i%2?'accentDark':'steel',rot);
    const phase=i%4,rr=r+.1;
    const strips=[[[8-rr,8+rr-.35,z-1.0],[8+rr,8+rr+.25,z-.45]],[[8+rr-.35,8-rr,z-1],[8+rr+.25,8+rr,z-.45]],[[8-rr,8-rr-.25,z-1],[8+rr,8-rr+.35,z-.45]],[[8-rr-.25,8-rr,z-1],[8-rr+.35,8+rr,z-.45]]];
    b(m,'starlight_spiral_flute_'+i,...strips[phase],'accent');
    if(i<6)b(m,'helix_glint_'+i,[8-r*.6,8+r-.3,z-1.15],[8-r*.6+.4,8+r+.05,z-.45],'accentLight');
  }
  b(m,'drill_point',[7.55,7.55,-11.1],[8.45,8.45,-9.6],'glint',rotation('z',45,[8,8,-10]));
  diamond(m,'gauntlet_power_cell',8,8,3.03,2.1,3.2,'accent');
  b(m,'side_power_rail_left',[3.55,7.2,5.6],[3.95,8.8,12.8],'accentDark');
  b(m,'side_power_rail_right',[12.05,7.2,5.6],[12.45,8.8,12.8],'accent');
  applyDisplay(m,[8,8,12.2],'drill',.63);return m;
}
function hammer(){
  const m=setup('tool_smith_hammer',{accent:'#FF9A45',accentDark:'#A64034',accentLight:'#FFE89B',wrap:'#343846',lava:'#B8472C'});
  haft(m,'ironbound_haft',.7,17.5,'wrap',1.9);
  b(m,'hammer_neck',[6.3,16.2,6.3],[9.7,20,9.7],'goldDark');
  b(m,'double_forge_body',[.5,18,4.3],[15.5,24.2,11.7],'steel');
  b(m,'left_striking_plate',[-1,17.5,3.8],[1.4,24.7,12.2],'silverDark');
  b(m,'right_striking_plate',[14.6,17.5,3.8],[17,24.7,12.2],'silverDark');
  for(const x of [-1.15,16.7]){
    b(m,'striking_surface_'+x,[x,18.4,4.8],[x+.45,23.8,11.2],'steel');
    for(const z of [4.7,10.8])for(const y of [18.3,23.1])b(m,`strike_stud_${x}_${y}_${z}`,[x-.05,y,z],[x+.55,y+.65,z+.55],'gold');
  }
  b(m,'front_lava_window',[2.1,18.9,4.0],[13.9,23.3,4.4],'lava');
  b(m,'rear_lava_window',[2.1,18.9,11.6],[13.9,23.3,12.0],'lava');
  for(const x of [1.5,13.8])b(m,'gold_end_band_'+x,[x,17.8,4.05],[x+.7,24.4,11.95],'gold');
  for(const y of [18.5,23.3])b(m,'front_window_frame_'+y,[2, y,3.8],[14,y+.4,4.2],'goldDark');
  // Anvil emblem: broad shoulder, narrow waist and split cat-ear horn tips.
  profile(m,'cat_anvil_medallion',[[5.2,20.2],[10.8,20.2],[9.7,21.1],[10,22.1],[12,22.5],[12,23.8],[10.8,23],[5.2,23],[4,23.8],[4,22.5],[6,22.1],[6.3,21.1]],3.37,3.81,'gold');
  diamond(m,'forge_heart',8,21.8,3.03,1.8,2.4,'accent');
  for(const x of [3,12.5])rune(m,'forge_rune_'+x,x,21.2,3.55,'accentLight',.7);
  for(let i=0;i<5;i++)b(m,'top_heat_vent_'+i,[4+i*1.7,24.15,5.4],[4.6+i*1.7,24.5,10.6],i%2?'goldDark':'ink');
  octagon(m,'weighted_pommel',8,-.4,8,3.3,1.6,3.3,'goldDark');
  applyDisplay(m,[8,5.8,8],'upright',.65);return m;
}
function scythe(){
  const m=setup('tool_farmer_scythe',{accent:'#83C85C',accentDark:'#3B795A',accentLight:'#DAEF99',pink:'#EA8FB8',wood:'#BC8859',woodDark:'#755039'});
  haft(m,'long_ashwood_staff',-7.5,24.5,'wood',1.5);
  for(let y=-5;y<23;y+=4)b(m,'bark_ridge_'+y,[7.82,y,7.15],[8.07,y+2.7,7.3],'woodDark');
  b(m,'upper_ferrule',[6.8,22.1,6.8],[9.2,25.7,9.2],'gold');
  const blade=[[8.1,25.2],[3.5,29],[-3.5,28],[-10,24],[-13,19],[-13.4,14.5],[-11.5,18.1],[-6.5,22.3],[-1,23.8],[4.5,23.5],[8.1,22.9]];
  profile(m,'harvest_moon_blade',blade,7.2,8.8,'goldDark');
  profile(m,'golden_cutting_plane',[[7.3,25.1],[3.3,28.1],[-3.2,27.2],[-9.2,23.4],[-12,18.9],[-11.8,18.7],[-6.1,23],[-1,24.5],[4.2,24.3],[7.3,23.7]],6.94,7.22,'gold');
  profile(m,'blade_bright_edge',[[3,28.3],[-3.2,27.5],[-9.7,23.4],[-12.3,18.6],[-13,15.8],[-11.6,19.5],[-9,23],[-3.1,27],[3,27.8]],6.77,7.02,'glint');
  for(const s of [-1,1]){
    const yy=17+s*2.1;
    profile(m,'sprouting_leaf_'+s,[[8,yy],[8+s*4,yy+3],[8+s*3.4,yy+.5],[8+s*.7,yy-.4]],6.8,7.25,'accent');
    b(m,'leaf_vein_'+s,[8+s*1.5-.1,yy+.2,6.65],[8+s*1.5+.1,yy+1.7,6.9],'accentLight',rotation('z',s*-45,[8+s*1.5,yy+1,6.8]));
  }
  for(let i=0;i<4;i++)b(m,'vine_wrap_'+i,[6.9,16.4+i*1.2,7.0],[9.1,16.7+i*1.2,9.0],'accentDark',rotation('z',22.5,[8,16.55+i*1.2,8]));
  for(const [i,dx,dy] of [[0,0,0],[1,-.9,.65],[2,.9,.65],[3,-.5,-.8],[4,.5,-.8]])b(m,'cherry_petal_'+i,[7.3+dx,21+dy,6.1],[8.7+dx,22.2+dy,6.7],'pink',i?rotation('z',i%2?22.5:-22.5,[8+dx,21.6+dy,6.4]):undefined);
  octagon(m,'flower_gold_heart',8,21.3,6.0,.8,.8,.6,'glint');
  b(m,'side_grip',[8.3,7.7,7.2],[11.8,8.9,8.8],'wood',rotation('z',-22.5,[8.3,8.3,8]));
  applyDisplay(m,[8,3.5,8],'upright',.58);return m;
}
function harpoon(){
  const m=setup('tool_angler_harpoon',{accent:'#42B7BA',accentDark:'#246277',accentLight:'#BDF0E3',coral:'#EA8C88',wrap:'#315569',pearl:'#FFF4DF'});
  haft(m,'abyssal_rod',-4.2,23.4,'accentDark',1.45);
  for(const y of [-2,3,8,13,18])b(m,'gold_rod_band_'+y,[7.1,y,7.1],[8.9,y+.55,8.9],'gold');
  b(m,'harpoon_socket',[6.6,20.8,6.5],[9.4,24.8,9.5],'goldDark');
  profile(m,'central_trident_tine',[[7.15,23],[8.85,23],[9.2,27.8],[8,31],[6.8,27.8]],7.1,8.9,'silver');
  for(const s of [-1,1]){
    profile(m,'barbed_outer_tine_'+s,[[8+s*1,22],[8+s*4.8,24.6],[8+s*4.9,29],[8+s*6.2,26.2],[8+s*5.9,23.4],[8+s*1,20.8]],7.3,8.7,'accent');
    b(m,'tine_gilt_'+s,[8+s*4.2-.24,24.4,7.05],[8+s*4.2+.24,27.2,7.33],'gold');
    ring(m,'line_guide_'+s,8+s*1.4,15+s*4,7.2,.75,.25,'gold');
  }
  // Reel assembly is four model units above the prototype position. The
  // unchanged lower grip now clears the entire spool and crank in both hands.
  b(m,'reel_mount',[8.5,10.8,7.2],[11.6,12,8.8],'goldDark');
  ring(m,'coral_reel_drum',11.8,10.5,6.35,2.5,.75,'coral');
  b(m,'reel_center',[10.5,9.2,6.7],[13.1,11.8,9.0],'gold');
  b(m,'reel_axle',[11.45,10.1,5.6],[12.15,10.9,9.9],'silver');
  b(m,'reel_crank',[11.7,10.3,5.25],[14.6,10.75,5.72],'goldDark');
  b(m,'crank_handle',[14.1,9.6,4.75],[15.1,11.5,5.7],'coral');
  b(m,'fishing_line_reel_to_upper_guide',[11.72,12.85,7.35],[11.88,19.4,7.5],'ivory',rotation('z',22.5,[11.8,13,7.42]));
  b(m,'fishing_line_upper_guide_to_tip',[9.33,18.8,7.35],[9.48,20.6,7.5],'ivory');
  b(m,'pearl_pendant_chain',[6.8,17.9,6.8],[7.05,20.8,7.05],'gold');
  octagon(m,'deep_sea_pearl',6.9,16.4,6.9,2.1,1.8,2.1,'pearl');
  b(m,'pearl_reflection',[6.4,17.6,5.77],[7.0,18.05,5.94],'glint');
  diamond(m,'ocean_crest',8,23.1,6.32,2.7,3.6,'accent');
  applyDisplay(m,[8,3.9,8],'upright',.6);return m;
}
function axe(){
  const m=setup('tool_carpenter_axe',{accent:'#57C48F',accentDark:'#2C735C',accentLight:'#C1EEAF',wood:'#B98354',woodDark:'#75503B'});
  haft(m,'heartwood_handle',-1.4,21,'wood',1.8);
  for(let i=0;i<4;i++)b(m,'woodgrain_'+i,[7.3+i*.3,1,7.03],[7.4+i*.3,17-i,7.18],'woodDark');
  b(m,'heavy_head_socket',[6.2,17.8,6.1],[9.8,24.9,9.9],'goldDark');
  for(const s of [-1,1]){
    const pts=[[8+s*1.3,19.5],[8+s*4,20.2],[8+s*6.9,25.7],[8+s*10,24.6],[8+s*10.4,17.8],[8+s*7.6,14.7],[8+s*5.6,18],[8+s*1.3,17.8]];
    profile(m,'broad_axe_lobe_'+s,pts,6.7,9.3,'silverDark');
    profile(m,'polished_axe_face_'+s,[[8+s*1.8,19.6],[8+s*4.8,20.3],[8+s*7.2,24.5],[8+s*8.8,23.9],[8+s*9.2,18.2],[8+s*7.7,16.3],[8+s*5.8,18.8],[8+s*1.8,18.7]],6.44,6.75,'silver');
    profile(m,'golden_etched_border_'+s,[[8+s*4.3,20],[8+s*6.8,24.8],[8+s*7.4,24.3],[8+s*5,19.8]],6.25,6.5,'gold');
    for(let i=0;i<3;i++)rune(m,`joinery_rune_${s}_${i}`,8+s*(5.8+i*.85),20.3-i*.65,6.23,'goldDark',.35);
  }
  for(const y of [17.7,23.8])b(m,'head_binding_'+y,[5.9,y,5.8],[10.1,y+.65,10.2],'gold');
  profile(m,'emerald_leaf_setting',[[8,17.2],[11.1,20.5],[10,23.7],[8,26.0],[6,23.7],[4.9,20.5]],5.7,6.23,'gold');
  profile(m,'soul_leaf',[[8,18.2],[10.1,20.7],[9.2,23.1],[8,24.9],[6.8,23.1],[5.9,20.7]],5.38,5.72,'accent');
  b(m,'leaf_mid_vein',[7.86,19,5.19],[8.14,24.1,5.42],'accentLight');
  for(const s of [-1,1])for(let i=0;i<2;i++)b(m,`leaf_vein_${s}_${i}`,[8+s*.7-.12,20.2+i*1.4,5.2],[8+s*.7+.12,21.4+i*1.4,5.42],'accentDark',rotation('z',s*-45,[8+s*.7,20.8+i*1.4,5.3]));
  octagon(m,'handle_end_cap',8,-2.1,8,2.6,1,2.6,'goldDark');
  applyDisplay(m,[8,4.5,8],'upright',.63);return m;
}
function wok(){
  const m=setup('tool_chef_wok',{accent:'#EF9B44',accentDark:'#A85232',accentLight:'#FFE39D',red:'#C9576E',herb:'#72B978',wrap:'#743C46'});
  // Tool axis is +Y; the concave pan faces -Z so the food is visible in hand.
  haft(m,'red_lacquer_handle',-2.4,8.8,'wrap',1.8);
  b(m,'handle_tang',[6.9,7.3,7.0],[9.1,11.2,9.0],'goldDark');
  // Concentric stepped shell is a real bowl: back disk, two sloping rings,
  // then rim rails, with no solid block obscuring the food on its front side.
  profile(m,'pan_back',[[3,10.2],[13,10.2],[16,13.2],[16,20.8],[13,24],[3,24],[0,20.8],[0,13.2]],8.5,10.0,'steel');
  profile(m,'inner_heated_basin',[[3.5,11],[12.5,11],[15.1,13.6],[15.1,20.4],[12.5,23.1],[3.5,23.1],[.9,20.4],[.9,13.6]],8.1,8.55,'ink');
  const rimPieces=[['bottom',[3,9.6,5.8],[13,11.1,9.5]],['top',[3,23.1,5.8],[13,24.6,9.5]],['left',[-.6,13,5.8],[1.1,21.1,9.5]],['right',[14.9,13,5.8],[16.6,21.1,9.5]]];
  for(const [name,f,t] of rimPieces)b(m,'cast_iron_rim_'+name,f,t,'steel');
  for(const [name,x,y,angle] of [['ll',1.3,11.5,-45],['lr',14.7,11.5,45],['ul',1.3,22.6,45],['ur',14.7,22.6,-45]])b(m,'rim_corner_'+name,[x-1.8,y-.8,5.8],[x+1.8,y+.8,9.3],'steel',rotation('z',angle,[x,y,7.5]));
  for(const [name,f,t] of [['top',[3.3,23.7,5.6],[12.7,24,6.0]],['left',[-.25,13.4,5.6],[.1,20.7,6]],['right',[15.8,13.4,5.6],[16.15,20.7,6]]])b(m,'worn_rim_glint_'+name,f,t,'silverDark');
  // Large rice clusters, fish fillets and herb leaves remain separate toppings.
  for(const [i,x,y] of [[0,4.4,14],[1,7.2,13],[2,10.4,14],[3,12.2,17],[4,9.6,19.5],[5,6.3,20.2],[6,3.7,17]]){
    b(m,'golden_rice_cluster_'+i,[x-1.2,y-.8,6.95],[x+1.2,y+.8,8.2],i%2?'accent':'gold');
    b(m,'rice_glint_'+i,[x-.8,y-.35,6.78],[x+.5,y+.15,7.0],'accentLight');
  }
  profile(m,'fish_fillet',[[5.5,15.7],[8.4,14.8],[11.1,16.2],[10.8,18.1],[8,19],[5.1,17.8]],5.95,7.4,'ivory');
  for(let i=0;i<3;i++)b(m,'fish_sear_'+i,[6.1+i*1.5,15.9,5.78],[6.5+i*1.5,17.9,6.06],'accentDark',rotation('z',22.5,[6.3+i*1.5,16.9,6]));
  profile(m,'fish_tail',[[10.4,16.4],[12.6,15.7],[12.4,18.4],[10.4,17.7]],6.1,7.3,'gold');
  for(const [i,x,y] of [[0,3,18.8],[1,10.8,21],[2,4.5,12.5]])profile(m,'herb_leaf_'+i,[[x,y],[x+1.4,y+.4],[x+2,y+1.8],[x+.3,y+1.4]],6.2,6.7,'herb',.25);
  for(const [i,x,y,h] of [[0,5.3,24.5,4],[1,9.1,25,5.2],[2,12,24.8,3]])profile(m,'golden_flare_'+i,[[x-1,y],[x+1,y],[x+1.3,y+h*.5],[x+.4,y+h],[x-.2,y+h*.4],[x-1.3,y+h*.7]],7,7.6,i%2?'accent':'gold');
  b(m,'ribbon_knot',[6.55,5.3,6.25],[9.45,7.25,7.65],'red');
  for(const s of [-1,1])b(m,'ribbon_loop_'+s,[8+s*2.2-1.2,5.7,6.5],[8+s*2.2+1.2,7.4,7.45],'red',rotation('z',s*22.5,[8+s*1.3,6.6,7]));
  b(m,'ribbon_tail',[9.4,2.4,7.4],[10.3,5.9,7.8],'red',rotation('z',22.5,[9.8,5.5,7.6]));
  ring(m,'handle_hanging_loop',8,-3.2,7.5,1.0,.35,'gold');
  applyDisplay(m,[8,2.5,8],'upright',.61);return m;
}

for(const build of [paladin,shadow,ranger,grimoire,drill,hammer,scythe,harpoon,axe,wok]) {
  const m=build();
  // Fail before saving an incompatible model; final cross-catalogue validation
  // remains the production validator's responsibility.
  for(const e of m.elements){
    if([...e.from,...e.to].some(v=>!Number.isFinite(v)||v< -16||v>32))throw Error(`${m.id}/${e.name}: coordinates outside vanilla bounds`);
    if(e.to.some((v,i)=>v<=e.from[i]))throw Error(`${m.id}/${e.name}: degenerate cuboid`);
    for(const f of Object.values(e.faces))if(f.uv.some(v=>v<0||v>16)||f.uv[2]<=f.uv[0]||f.uv[3]<=f.uv[1])throw Error(`${m.id}/${e.name}: invalid UV`);
  }
  m.save();console.log(`${m.id}: ${m.elements.length} named cuboids / grip ${anchors[m.id].anchor.join(',')}`);
}
fs.writeFileSync(path.join(SOURCE,'class-weapons-anchors.json'),JSON.stringify({schemaVersion:2,units:'Minecraft model units',rotationConvention:'XYZ intrinsic; column matrix Rx Ry Rz; left hand negates stored rotation Y/Z and translation X',notice:'Neutral display-space grip targets with verified vanilla left-hand mirroring; enclosing player transforms still require client validation.',models:anchors},null,2)+'\n');
