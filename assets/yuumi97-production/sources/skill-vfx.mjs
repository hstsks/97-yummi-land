/** Stream 01: fourteen distinct, editable Minecraft 1.21.4 skill meshes.
 * World display origin [8,8,8], authored forward -Z. No atlas/definition/build writes.
 */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Model, SOURCE, shade} from '../tools/asset-kit.mjs';

const C={white:'#FFFDF0',cream:'#FFF3B0',gold:'#FFD666',goldDark:'#B87816',goldPale:'#FFEBA5',
  orange:'#FF6B1A',ember:'#FFAA29',ash:'#2B1B17',black:'#10002B',obsidian:'#292137',
  violet:'#9D4EDD',purple:'#7209B7',lavender:'#E0AAFF',pink:'#F3D9FA',magenta:'#F72585',
  cyan:'#00BBF9',cyanPale:'#E0FBFC',blue:'#337BBF',teal:'#36CFC9',mint:'#9FF5DE',
  ice:'#CAF0F8',water:'#00B4D8',waterDark:'#116D9A',foam:'#90E0EF',stew:'#F3D27B',stewDark:'#BF873C'};
const round=x=>Math.round(x*10000)/10000;
const rot=(axis,angle,origin=[8,8,8])=>({axis,angle,origin,rescale:false});
function box(m,n,a,b,t,r,faces){return m.box(n,a.map(round),b.map(round),t,{...(r?{rotation:r}:{}),...(faces?{faces}:{})});}
function make(id){
  const m=new Model(id,128);
  for(const [name,color] of Object.entries(C)){
    m.swatch(name,color,{noise:false});const {x,y}=m.swatches[name];
    // Crisp directed facets, not noisy dots or a baked glow/billboard.
    m.canvas.line(x+2,y+2,x+12,y+2,shade(color,18));
    m.canvas.line(x+2,y+3,x+2,y+11,shade(color,12));
    m.canvas.line(x+12,y+8,x+12,y+13,shade(color,-18));
    if(['obsidian','ash','black'].includes(name))m.canvas.line(x+4,y+12,x+9,y+5,shade(color,9));
    if(['gold','goldDark','goldPale'].includes(name))m.canvas.line(x+4,y+7,x+10,y+7,shade(color,-10));
  }
  m.display.gui={rotation:[30,-35,0],translation:[0,0,0],scale:[.45,.45,.45]};
  return m;
}

// Voxelize a two-dimensional silhouette into contiguous, closed cuboid strips.
// Cross-section remains real geometry. Interior gaps remain transparent space.
function mask(m,name,plane,low,high,step,predicate,depth,texture,rotation){
  const axes=plane==='xz'?[0,2,1]:[0,1,2];let k=0;
  for(let v=low;v<high-1e-6;v+=step){let start=null;
    for(let u=low;u<=high+1e-6;u+=step){const inside=u<high-1e-6&&predicate(u+step/2,v+step/2);
      if(inside&&start===null)start=u;
      if(!inside&&start!==null){const a=[0,0,0],b=[0,0,0];a[axes[0]]=start;b[axes[0]]=u;a[axes[1]]=v;b[axes[1]]=Math.min(v+step,high);a[axes[2]]=depth[0];b[axes[2]]=depth[1];box(m,name+'_'+k++,a,b,texture,rotation);start=null;}
    }
  }
}
function annulus(m,name,r,w,y,h,texture,opt={}){
  const cx=opt.cx??8,cz=opt.cz??8,plane=opt.plane??'xz',step=opt.step??.7;
  mask(m,name,plane,-14,30,step,(x,z)=>{const rr=Math.hypot(x-cx,z-cz);return rr>=r-w/2&&rr<=r+w/2&&(!opt.cut||opt.cut(x,z));},[y,y+h],texture,opt.rotation);
}
function arc(m,name,r,w,y,h,texture,extent=105){
  const max=extent*Math.PI/180;
  mask(m,name,'xz',-14,30,.65,(x,z)=>{const dx=x-8,dz=z-8,a=Math.abs(Math.atan2(dx,-dz));return a<max&&Math.abs(Math.hypot(dx,dz)-r)<w*.5*Math.pow(Math.cos(a/max*Math.PI/2),.55);},[y,y+h],texture);
}
function line(m,name,a,b,width,texture,step=.70){
  const len=Math.hypot(...a.map((v,i)=>v-b[i])),n=Math.max(1,Math.ceil(len/step));
  for(let i=0;i<=n;i++){const p=a.map((v,k)=>v+(b[k]-v)*i/n);box(m,name+'_'+i,p.map((v,k)=>v-(Array.isArray(width)?width[k]:width)/2),p.map((v,k)=>v+(Array.isArray(width)?width[k]:width)/2),texture);}
}
function spark(m,name,x,y,z,s,texture='white'){
  box(m,name+'_vertical',[x-s*.17,y-s,z-s*.17],[x+s*.17,y+s,z+s*.17],texture);
  box(m,name+'_horizontal',[x-s,y-s*.17,z-s*.17],[x+s,y+s*.17,z+s*.17],texture);
  box(m,name+'_depth',[x-s*.17,y-s*.17,z-s*.65],[x+s*.17,y+s*.17,z+s*.65],texture);
}
function floorStar(m,name,x,z,r,y,texture){
  mask(m,name,'xz',-14,30,.55,(a,b)=>Math.abs(a-x)+Math.abs(b-z)<r&&Math.min(Math.abs(a-x),Math.abs(b-z))<r*.27,[y,y+.20],texture);
}
function paw(m,name,x,y,z,s,texture){
  box(m,name+'_pad',[x-.7*s,y-.5*s,z],[x+.7*s,y+.5*s,z+.24*s],texture);
  for(const [i,dx,dy]of[[0,-1,.65],[1,-.35,1.1],[2,.35,1.1],[3,1,.65]])box(m,name+'_toe'+i,[x+(dx-.23)*s,y+dy*s,z],[x+(dx+.23)*s,y+(dy+.48)*s,z+.24*s],texture);
}
function oct(m,name,x,y,z,w,h,d,texture,faces){
  const c=Math.min(w,d)*.2;
  box(m,name+'_center',[x-w/2+c,y,z-d/2],[x+w/2-c,y+h,z+d/2],texture,undefined,faces);
  box(m,name+'_left',[x-w/2,y,z-d/2+c],[x-w/2+c,y+h,z+d/2-c],texture,undefined,faces);
  box(m,name+'_right',[x+w/2-c,y,z-d/2+c],[x+w/2,y+h,z+d/2-c],texture,undefined,faces);
}
function crystalY(m,name,x,y,z,w,h,texture,light,dark,rotation){
  for(const [i,t0,t1,k]of[[0,0,.15,.18],[1,.15,.30,.57],[2,.30,.78,1],[3,.78,.92,.62],[4,.92,1,.18]]){
    box(m,name+'_facet'+i,[x-w*k/2,y+h*t0,z-w*k/2],[x+w*k/2,y+h*t1,z+w*k/2],texture,rotation,{north:light,west:light,south:dark,east:texture,up:light,down:dark});
  }
}
function circleGlyphs(m,name,r,y,texture,count=12){
  for(let i=0;i<count;i++){const a=i*Math.PI*2/count,x=8+Math.sin(a)*r,z=8-Math.cos(a)*r;
    box(m,name+'_stem'+i,[x-.18,y,z-.85],[x+.18,y+.18,z+.85],texture);
    box(m,name+'_cap'+i,[x-.75,y,z-.85],[x+.75,y+.18,z-.51],texture);
    if(i%3===0)box(m,name+'_foot'+i,[x-.75,y,z+.51],[x+.75,y+.18,z+.85],texture);
    else box(m,name+'_dot'+i,[x+.42,y,z+.2],[x+.78,y+.18,z+.56],texture);
  }
}

function goldSlash(){
  const m=make('vfx_slash_crescent_gold');
  for(const [i,r]of[20,14.6,9.4].entries()){
    const y=7.1+i*.48;arc(m,'crescent_'+i+'_rune_outline',r,2.8,y,.65,'goldDark');
    arc(m,'crescent_'+i+'_gold_body',r-.20,2.12,y+.65,.32,'gold');
    arc(m,'crescent_'+i+'_platinum_core',r-.48,.75,y+.97,.19,'white',101);
    for(let j=0;j<5;j++){const a=(-.77+j*.385),x=8+Math.sin(a)*(r+1.28),z=8-Math.cos(a)*(r+1.28);box(m,'crescent_'+i+'_afterimage_rune'+j,[x-.30,y+.18,z-.30],[x+.30,y+.51,z+.30],'goldPale');}
  }
  for(const [i,p]of[[0,[-10,8.7,9]],[1,[26,8.2,9]],[2,[-7,9.6,15]],[3,[23,9.1,15]]])spark(m,'gold_end_spark'+i,...p,.55,'goldPale');
  return m;
}
function heavySlash(){
  const m=make('vfx_slash_crimson_heavy');
  arc(m,'obsidian_crescent_back',17.9,5.5,5.95,1.35,'ash',109);
  arc(m,'molten_wave',17.7,4.5,7.30,1.2,'orange',107);
  arc(m,'ember_highlight',17.15,2.8,8.5,.55,'ember',104);
  arc(m,'white_hot_core',16.8,1.10,9.05,.30,'cream',102);
  for(let i=0;i<19;i++){
    const a=(-99+i*11)*Math.PI/180,r=20.1+(i%3)*.4,x=8+Math.sin(a)*r,z=8-Math.cos(a)*r,h=1.4+(i%4)*.65;
    crystalY(m,'raging_flame_tooth'+i,x,7.7,z,.85+(i%3)*.22,h,'orange','ember','ash');
    if(i%2===0)box(m,'ejected_charcoal'+i,[x-.38,5.8,z-.38],[x+.38,6.55,z+.38],'ash',rot('y',i%4?22.5:-22.5,[x,6.2,z]));
  }
  return m;
}
function titanDrop(){
  const m=make('vfx_titan_greatsword_drop');
  // Blade profile descends continuously to the exact Y=-14 endpoint.
  const slices=[[-14,-11,.30],[-11,-7,1.45],[-7,-2,2.65],[-2,12.5,3.55]];
  for(const[i,y0,y1,w]of slices.map((s,i)=>[i,...s])){
    box(m,'obsidian_blade_'+i,[8-w,y0,6.45],[8+w,y1,9.55],'obsidian',undefined,{north:'ash',south:'obsidian',east:'black',west:'obsidian'});
    const k=Math.max(.11,w*.25);box(m,'molten_blade_channel_'+i,[8-k,y0,6.28],[8+k,y1,6.48],'orange');
    box(m,'hot_blade_seam_'+i,[8-k*.35,y0,6.18],[8+k*.35,y1,6.29],'cream');
    box(m,'rear_blade_lava_'+i,[8-k,y0,9.52],[8+k,y1,9.72],'ember');
  }
  for(let i=0;i<7;i++)for(const s of[-1,1]){
    const y=-1+i*1.9,x=8+s*3.55;
    box(m,'sawtooth_'+s+'_'+i,[x-.65,y,6.5],[x+.65,y+.95,9.5],'ash',rot('z',s*22.5,[x,y,8]));
    line(m,'lava_fracture_'+s+'_'+i,[8+s*.8,y+.7,6.15],[8+s*2.8,y-.2,6.15],[.22,.25,.16],i%2?'ember':'orange',.4);
  }
  box(m,'crossguard',[0.4,12.5,6.1],[15.6,14.6,9.9],'goldDark');
  box(m,'crossguard_front',[.8,13.8,5.9],[15.2,14.6,6.15],'gold');
  for(const s of[-1,1]){const x=8+s*6.1;crystalY(m,'golden_claw_guard'+s,x,12.7,8,1.6,4.0,'gold','goldPale','goldDark');}
  box(m,'grip',[7.18,14.6,7.18],[8.82,27.8,8.82],'ash');
  for(let y=15;y<28;y+=1.35)oct(m,'grip_binding_'+y,8,y,8,1.85,.33,1.85,'goldDark');
  oct(m,'pommel',8,27.8,8,3.4,1.3,3.4,'gold');oct(m,'pommel_top_y30',8,29.1,8,2.1,.9,2.1,'goldPale');
  // A pitched helix surrounds the sword without hiding its falling silhouette.
  for(let i=0;i<74;i++){const a=i/73*Math.PI*3.4,r=6.8+(i%13)*.025,x=8+Math.cos(a)*r,z=8+Math.sin(a)*r,y=-7+i/73*23;
    box(m,'spiral_flame_'+i,[x-.42,y-.30,z-.42],[x+.42,y+.30,z+.42],i%7<2?'cream':i%3?'orange':'ember');}
  return m;
}
function shadowSlash(){
  const m=make('vfx_slash_crescent_shadow');
  for(const angle of[-45,45])for(let i=0;i<22;i++){
    const y=-13+i*1.9,w=.35+1.4*Math.pow(Math.sin((i+.5)/22*Math.PI),.6),r=rot('z',angle);
    box(m,'cross_'+angle+'_shadow_'+i,[8-w,y,7.1],[8+w,y+1.9,9.0],'black',r);
    box(m,'cross_'+angle+'_amethyst_'+i,[8-w*.7,y,6.84],[8+w*.7,y+1.9,7.12],'violet',r);
    box(m,'cross_'+angle+'_light_'+i,[7.72,y,6.65],[8.28,y+1.9,6.85],'pink',r);
    box(m,'cross_'+angle+'_rear_amethyst_'+i,[8-w*.7,y,8.98],[8+w*.7,y+1.9,9.22],'purple',r);
    box(m,'cross_'+angle+'_rear_low_glow_'+i,[7.72,y,9.20],[8.28,y+1.9,9.40],'violet',r);
  }
  for(const [i,x,y]of[[0,-8,9],[1,25,8],[2,8,28],[3,8,-10]]){
    paw(m,'black_cat_rune'+i,x,y,7.8,.92,'black');
    for(let k=0;k<3;k++)box(m,'claw_fragment'+i+'_'+k,[x-1.3+k*.8,y-3,7.4],[x-.96+k*.8,y-1.4,7.8],'violet',rot('z',22.5,[x,y,8]));
  }
  return m;
}
function shadowMark(){
  const m=make('vfx_shadow_mark_crest');
  mask(m,'crescent_fang_border','xy',-7,23,.6,(x,y)=>{const r=Math.hypot(x-8,y-9);return r>10.3&&r<13.0&&y<14.5;},[7.2,8.8],'black');
  mask(m,'crescent_fang_light','xy',-7,23,.6,(x,y)=>{const r=Math.hypot(x-8,y-9);return r>10.9&&r<12.1&&y<14.5;},[6.9,7.21],'violet');
  mask(m,'crescent_fang_rear_glow','xy',-7,23,.6,(x,y)=>{const r=Math.hypot(x-8,y-9);return r>10.9&&r<12.1&&y<14.5;},[8.78,9.06],'purple');
  mask(m,'crescent_fang_rear_core','xy',-7,23,.6,(x,y)=>{const r=Math.hypot(x-8,y-9);return r>11.32&&r<11.83&&y<14.5;},[9.04,9.20],'violet');
  for(const s of[-1,1])crystalY(m,'crescent_tapered_fang'+s,8+s*11.1,12.7,8,1.5,6.0,'violet','lavender','purple');
  box(m,'diamond_shadow_frame',[3.8,3.8,7.0],[12.2,12.2,9.0],'black',rot('z',45));
  box(m,'diamond_amethyst_inset',[4.8,4.8,6.7],[11.2,11.2,7.1],'violet',rot('z',45));
  box(m,'diamond_highlight',[6.0,6.0,6.4],[10.0,10.0,6.7],'lavender',rot('z',45));
  box(m,'vertical_dark_eye',[7.54,4.7,6.2],[8.46,11.3,6.41],'black');
  box(m,'diamond_rear_amethyst',[4.8,4.8,8.98],[11.2,11.2,9.24],'purple',rot('z',45));
  box(m,'diamond_rear_low_glow',[6.0,6.0,9.22],[10.0,10.0,9.42],'violet',rot('z',45));
  box(m,'vertical_rear_dark_eye',[7.54,4.7,9.40],[8.46,11.3,9.55],'black');
  for(const s of[-1,1])box(m,'ear_rune_'+s,[8+s*4-.65,16.0,7.0],[8+s*4+.65,20.2,8.0],'purple',rot('z',s*22.5,[8+s*4,16,8]));
  spark(m,'upper_shadow_star',8,22.0,7.7,1.2,'pink');
  return m;
}
function starArrow(){
  const m=make('vfx_star_arrow_volley');
  for(const[i,z0,z1,w]of[[0,-14,-11,.25],[1,-11,-7,1.3],[2,-7,-3,3.0],[3,-3,1,5.2],[4,1,4,3.0]]){
    box(m,'starhead_vertical_'+i,[7.1,8-w,z0],[8.9,8+w,z1],'cyan',undefined,{north:'cyanPale',up:'ice',down:'blue'});
    box(m,'starhead_horizontal_'+i,[8-w,7.1,z0],[8+w,8.9,z1],'teal',undefined,{north:'cyanPale',up:'cyanPale',down:'blue'});
    box(m,'starhead_hot_center_'+i,[7.57,7.57,z0],[8.43,8.43,z1],'white');
  }
  const tails=[{name:'central',dx:0,dy:0,end:30,mat:'cyanPale'},{name:'left',dx:-4.8,dy:2.4,end:27,mat:'teal'},{name:'right',dx:4.8,dy:-2.4,end:28.5,mat:'cyan'}];
  for(const t of tails)for(let i=0;i<28;i++){
    const q=i/27,z=3+q*(t.end-3),x=8+t.dx*Math.sin(q*Math.PI*.70),y=8+t.dy*Math.sin(q*Math.PI*.85),w=.75*(1-q)+.18;
    box(m,'comet_ribbon_'+t.name+'_'+i,[x-w,y-w*.6,z-.48],[x+w,y+w*.6,Math.min(30,z+.48)],t.mat);
  }
  for(let i=0;i<9;i++)spark(m,'star_dust_'+i,3+(i*7)%11,4+(i*5)%9,7+i*2.2,.32+(i%3)*.12,i%2?'white':'cyan');
  return m;
}
function bowSigil(){
  const m=make('vfx_astral_bow_sigil');
  annulus(m,'outer_astral_gold',17.8,.8,7.1,.45,'gold');annulus(m,'inner_aiming_cyan',15.5,.6,7.55,.40,'teal');
  for(let i=0;i<8;i++){const a=i*Math.PI/4,b=(i+1)*Math.PI/4,p=[8+Math.sin(a)*14.2,8.0,8-Math.cos(a)*14.2],q=[8+Math.sin(b)*14.2,8.0,8-Math.cos(b)*14.2];
    line(m,'constellation_chord_'+i,p,q,[.34,.25,.34],'cyanPale');floorStar(m,'constellation_node_'+i,p[0],p[2],1.15,8.15,'goldPale');}
  for(let i=0;i<4;i++){
    const a=i*Math.PI/2,radial=r=>[8+Math.sin(a)*r,8.35,8-Math.cos(a)*r];
    line(m,'aim_marker_stem_'+i,radial(18.8),radial(21.7),[.6,.35,.6],'cyanPale');
    const p=radial(18.5);for(const s of[-1,1])line(m,'aim_marker_wing_'+i+'_'+s,p,[p[0]+Math.sin(a)*2+Math.cos(a)*s*1.7,8.35,p[2]-Math.cos(a)*2+Math.sin(a)*s*1.7],[.55,.35,.55],'gold');
  }
  circleGlyphs(m,'range_ticks',16.65,8.5,'teal',16);return m;
}
function arcaneCircle(){
  const m=make('vfx_magic_circle_arcane');
  // Cardinal endcaps guarantee the requested exact -14..30 span.
  annulus(m,'outer_runic_ring',21.55,.85,7.6,.30,'purple',{step:.5});
  annulus(m,'outer_hot_trace',20.95,.32,7.90,.20,'lavender',{step:.5});
  annulus(m,'inner_sigil_ring',16.8,.65,7.7,.36,'magenta',{step:.5});
  for(const r of[0,1]){const vertices=[0,1,2].map(i=>{const a=(i*120+r*60)*Math.PI/180;return[8+Math.sin(a)*15.9,8.15,8-Math.cos(a)*15.9];});
    for(let i=0;i<3;i++)line(m,'hexagram_'+r+'_edge_'+i,vertices[i],vertices[(i+1)%3],[.55,.30,.55],r?'lavender':'magenta',.50);}
  annulus(m,'central_open_seal',4.2,.55,7.95,.35,'pink',{step:.5});circleGlyphs(m,'ancient_arcane_rune',18.8,8.1,'lavender',16);
  for(const [i,x,z]of[[0,8,-13.7],[1,8,29.7],[2,-13.7,8],[3,29.7,8]])box(m,'cardinal_extent_'+i,[x-.3,7.6,z-.3],[x+.3,8.4,z+.3],'magenta');
  return m;
}
function singularity(){
  const m=make('vfx_gravity_singularity');
  // Closed stepped event horizon; exposed octagonal bands provide genuine volume.
  for(let i=0;i<14;i++){const y=1+i,rr=Math.sqrt(Math.max(0,49-(y+.5-8)**2))*2;oct(m,'event_horizon_slice'+i,8,y,8,rr,1,rr,'black',{north:i%3?'black':'obsidian',south:'black',up:'obsidian',down:'black'});}
  const rotation=rot('x',22.5);
  annulus(m,'accretion_outer_shadow',18,2.6,7.5,.55,'purple',{rotation,step:.60});
  annulus(m,'accretion_ionized_stream',16.8,1.5,8.05,.35,'violet',{rotation,step:.6});
  annulus(m,'accretion_white_hot_rim',15.8,.65,8.4,.26,'ice',{rotation,step:.6});
  annulus(m,'accretion_inner_glow',11.8,.8,7.85,.35,'lavender',{rotation,step:.6});
  for(let i=0;i<17;i++){const a=i*Math.PI*2/17,r=18.5+(i%3)*.45,x=8+Math.cos(a)*r,z=8+Math.sin(a)*r;box(m,'orbital_energy_fragment'+i,[x-.28,8.3,z-.28],[x+.28,8.8,z+.28],i%3?'violet':'pink',rotation);}
  annulus(m,'event_horizon_lensing_rim',7.05,.65,6.8,.45,'lavender',{plane:'xy',step:.5});
  return m;
}
function meteor(){
  const m=make('vfx_arcane_meteor');
  // Asymmetric cut-rock strata replace the previous broad box-shaped core.
  for(const [i,x,y,z,w,h,d]of[[0,8.7,-.2,7.0,6.6,2.7,7.2],[1,8.0,2.0,7.7,11.0,3.1,10.5],[2,7.6,4.8,8.6,13.6,3.5,11.0],[3,7.2,8.0,8.2,10.8,3.1,9.8],[4,8.1,10.8,8.7,7.1,2.2,6.1],[5,8.5,12.7,8.8,3.6,1.3,3.2]])
    oct(m,'violet_magma_body_stratum_'+i,x,y,z,w,h,d,i%2?'obsidian':'purple',{north:i%2?'purple':'violet',south:'black',west:'obsidian',east:'purple',up:i%2?'violet':'purple',down:'black'});
  const rocks=[[2.8,4.1,4.1,4.1,4.7,3.0,'z',22.5],[11.9,6.6,3.4,3.5,4.6,3.0,'z',-22.5],
    [3.0,9.4,7.4,3.8,3.8,3.3,'x',22.5],[11.4,10.0,8.7,3.7,4.2,3.1,'y',22.5],
    [7.4,11.8,5.1,4.3,3.1,2.7,'z',-22.5],[7.5,3.2,12.0,4.5,4.4,3.0,'x',-22.5],
    [2.0,6.0,10.9,3.1,4.3,3.0,'z',22.5],[13.6,5.6,9.8,3.0,3.7,3.4,'z',-22.5],
    [8.8,1.0,4.8,3.1,3.5,3.3,'x',22.5],[5.9,9.4,12.4,3.6,3.9,3.2,'y',-22.5]];
  for(const [i,rock]of rocks.entries()){
    const [x,y,z,w,h,d,axis,angle]=rock,R=rot(axis,angle,[x,y,z]);
    box(m,'broken_crust_boulder_'+i,[x-w/2,y-h/2,z-d/2],[x+w/2,y+h/2,z+d/2],i%3?'obsidian':'purple',R,{north:'purple',south:'black',west:'obsidian',east:'violet',up:'violet',down:'black'});
    const start=m.elements.length;
    line(m,'boulder_magma_crack_'+i,[x-w*.27,y-h*.26,z-d/2-.06],[x+w*.12,y+h*.29,z-d/2-.06],[.19,.22,.14],i%2?'magenta':'lavender',.40);
    line(m,'boulder_crack_branch_'+i,[x-w*.02,y+h*.05,z-d/2-.07],[x+w*.32,y-h*.04,z-d/2-.07],[.14,.16,.15],'violet',.4);
    for(const e of m.elements.slice(start))e.rotation=R;
  }
  line(m,'upper_cut_magma_fault',[5.0,11.13,7.8],[10.8,11.13,9.9],[.28,.16,.28],'magenta',.45);
  line(m,'upper_cut_fault_branch',[8.0,11.13,8.9],[7.4,11.13,11.0],[.18,.16,.18],'lavender',.45);
  for(const [i,x,y,z,w,h]of[[0,2,-.8,3,3.8,12],[1,13,.2,5,4.0,13],[2,4,9,8,3.2,14],[3,11,11,10,4.0,14],[4,8,14,12,2.7,13],[5,16,7,8,2.6,10],[6,.5,5,9,2.7,11]])
    crystalY(m,'ancient_ice_crystal_'+i,x,y,z,w,h,'cyan','ice','blue',rot('x',45,[x,8,8]));
  crystalY(m,'leading_impact_crystal',8,-9,8,4.8,13,'violet','pink','purple',rot('x',45));
  for(let i=0;i<11;i++){const y=15+i*.9,z=13+i*1.05,x=8+Math.sin(i*.9)*3;box(m,'diagonal_comet_fragment'+i,[x-.45,y-.55,z-.45],[x+.45,y+.55,z+.45],i%2?'cyanPale':'violet',rot('x',22.5,[x,y,z]));}
  return m;
}
function sanctuary(){
  const m=make('vfx_magic_circle_sanctuary');
  annulus(m,'outer_cathedral_gold',21.55,.85,7.6,.30,'goldDark',{step:.5});
  annulus(m,'outer_white_gold_edge',21.0,.38,7.9,.25,'goldPale',{step:.5});
  annulus(m,'inner_healing_ring',16.5,.85,7.7,.4,'teal',{step:.5});
  annulus(m,'inner_gold_fillet',15.75,.35,8.1,.25,'gold',{step:.5});
  annulus(m,'central_open_chalice_seal',4.8,.55,7.9,.35,'white',{step:.5});
  for(let i=0;i<4;i++){
    const a=i*Math.PI/2,cx=8+Math.sin(a)*7.0,cz=8-Math.cos(a)*7.0;
    annulus(m,'quatrefoil_petals_'+i,4.4,.50,8.0,.30,'mint',{cx,cz,step:.5});
    const x=8+Math.sin(a)*19,z=8-Math.cos(a)*19;
    box(m,'holy_cross_long_'+i,[x-.25,8.05,z-1.6],[x+.25,8.4,z+1.6],'white');
    box(m,'holy_cross_arm_'+i,[x-1.25,8.05,z-.35],[x+1.25,8.4,z+.15],'white');
  }
  for(let i=0;i<16;i++){const a=(i+.5)*Math.PI/8;line(m,'cathedral_light_ray'+i,[8+Math.sin(a)*17.5,8.2,8-Math.cos(a)*17.5],[8+Math.sin(a)*19.4,8.2,8-Math.cos(a)*19.4],[.4,.30,.4],i%2?'gold':'mint');}
  for(const [i,x,z]of[[0,8,-13.7],[1,8,29.7],[2,-13.7,8],[3,29.7,8]])box(m,'cardinal_extent_'+i,[x-.3,7.6,z-.3],[x+.3,8.4,z+.3],'gold');
  return m;
}
function holySpear(){
  const m=make('vfx_holy_spear');
  for(const[i,y0,y1,w]of[[0,-14,-11,.18],[1,-11,-7,.65],[2,-7,-2,1.25],[3,-2,10,1.8]]){
    box(m,'downward_lance_gold_'+i,[8-w,y0,7.15],[8+w,y1,8.85],'gold');
    box(m,'downward_lance_white_'+i,[8-w*.36,y0,6.94],[8+w*.36,y1,7.16],'white');
  }
  box(m,'holy_crossbar',[-.4,10.9,7.0],[16.4,12.6,9.0],'goldDark');box(m,'holy_crossbar_glint',[.1,12.0,6.8],[15.9,12.6,7.1],'goldPale');
  box(m,'long_champagne_staff',[7.35,10,7.35],[8.65,29,8.65],'gold');box(m,'staff_front_white',[7.70,13,7.13],[8.30,28.8,7.37],'white');
  crystalY(m,'staff_crown',8,28.5,8,2.2,2.6,'gold','cream','goldDark');
  for(const s of[-1,1]){
    // A broad, scalloped feather fan is the primary silhouette. Building only
    // almost-parallel shafts previously collapsed into a thin line at distance.
    const outline=[[2,12.4],[5.5,12.55],[7.2,12.9],[7.0,13.6],[9.7,13.25],[9.3,14.3],[12,14.1],[11.6,15.2],[14.3,15.2],[14,16.4],[16.1,17.2],[15.7,18.3],[18.3,23.65],[16.4,22.5],[14.8,21.1],[12,19],[9.5,17.8],[6.8,16.8],[4.6,15.5],[2,14.0]];
    const inside=(x,y)=>{let ok=false;for(let i=0,j=outline.length-1;i<outline.length;j=i++){const a=outline[i],b=outline[j];if((a[1]>y)!==(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])ok=!ok;}return ok;};
    const outer=(x,y)=>inside((x-8)*s,y);
    const inner=(x,y)=>{const u=(x-8)*s;return inside(u,y)&&inside(u-.28,y)&&inside(u+.28,y)&&inside(u,y-.28)&&inside(u,y+.28);};
    mask(m,'wing_'+s+'_broad_gold_fan','xy',-14,30,.4,outer,[7.6,8.45],'gold');
    mask(m,'wing_'+s+'_broad_ivory_fan','xy',-14,30,.4,inner,[7.12,7.62],'white');
    mask(m,'wing_'+s+'_broad_rear_fan','xy',-14,30,.4,inner,[8.43,8.70],'goldPale');
    const seams=[[[4.8,15.0],[7.1,13.4]],[[6.4,16.2],[9.3,13.8]],[[8.1,17.0],[11.6,14.7]],[[10,18],[13.9,15.9]],[[12,19],[15.5,17.8]],[[14.2,20.5],[17.0,20.0]]];
    for(const [i,[a,b]]of seams.entries()){
      line(m,'wing_'+s+'_feather_groove_'+i,[8+s*a[0],a[1],7.04],[8+s*b[0],b[1],7.04],[.23,.23,.18],'goldDark',.32);
      line(m,'wing_'+s+'_rear_feather_groove_'+i,[8+s*a[0],a[1],8.77],[8+s*b[0],b[1],8.77],[.19,.19,.16],'gold',.32);
    }
    const ridge=[[2,13.7],[5,15.7],[9.5,17.8],[13,19.8],[18.1,23.55]];
    for(let i=0;i<ridge.length-1;i++)line(m,'wing_'+s+'_curved_gold_ridge_'+i,[8+s*ridge[i][0],ridge[i][1],7.0],[8+s*ridge[i+1][0],ridge[i+1][1],7.0],[.38,.38,.36],'goldPale',.35);
    for(let i=0;i<4;i++)line(m,'wing_'+s+'_root_covert_'+i,[8+s*(2.4+i*.65),13.1+i*.38,6.84],[8+s*(5.0+i*.65),14.4+i*.48,6.84],[.48,.50,.22],i%2?'white':'cream',.35);
  }
  box(m,'holy_water_gem_frame',[5.7,9.7,6.4],[10.3,14.3,7.2],'goldDark',rot('z',45,[8,12,8]));
  box(m,'holy_water_gem',[6.3,10.3,6.1],[9.7,13.7,6.5],'cyan',rot('z',45,[8,12,8]));
  box(m,'holy_water_gem_glint',[7.1,11.1,5.9],[8.9,12.9,6.15],'ice',rot('z',45,[8,12,8]));
  return m;
}
function fishingVortex(){
  const m=make('vfx_golden_fishing_vortex');
  // Open tapered helical ribbons: bottom radius 2, surface radius 21.5 at Y=8.
  for(let strand=0;strand<3;strand++)for(let i=0;i<128;i++){
    const t=i/127,a=t*Math.PI*5+strand*Math.PI*2/3,r=1.8+t*19.3,x=8+Math.cos(a)*r,z=8+Math.sin(a)*r,y=.20+t*7.55,w=.36+t*.22;
    box(m,'funnel_'+strand+'_ribbon_'+i,[x-w,Math.max(0,y-.20),z-w],[x+w,Math.min(8,y+.20),z+w],strand===0?'gold':strand===1?'water':'foam');
  }
  annulus(m,'surface_tidal_ring',21.45,.9,7.6,.4,'water',{step:.5});annulus(m,'surface_golden_ripple',18.4,.6,7.65,.32,'gold',{step:.6});
  for(let i=0;i<12;i++){const a=i*Math.PI/6,r=19.5,x=8+Math.sin(a)*r,z=8-Math.cos(a)*r;
    box(m,'seafoam_bubble_'+i,[x-.44,7.4,z-.44],[x+.44,8,z+.44],i%2?'foam':'cyanPale');}
  for(let i=0;i<5;i++){const a=(i+.25)*Math.PI*2/5,r=13.8,x=8+Math.sin(a)*r,z=8-Math.cos(a)*r;
    box(m,'golden_fish_body'+i,[x-1.5,7.45,z-.52],[x+1.5,7.95,z+.52],'gold');
    box(m,'golden_fish_head'+i,[x-1.9,7.5,z-.32],[x-1.5,7.9,z+.32],'goldPale');
    box(m,'golden_fish_tail_a'+i,[x+1.5,7.45,z-.86],[x+2.3,7.95,z-.14],'gold');
    box(m,'golden_fish_tail_b'+i,[x+1.5,7.45,z+.14],[x+2.3,7.95,z+.86],'gold');
    box(m,'golden_fish_eye'+i,[x-1.25,7.96,z-.20],[x-.95,8,z+.10],'waterDark');
  }
  for(let i=0;i<9;i++){const a=i*Math.PI*2/9,r=7.8+(i%2)*2.4,x=8+Math.cos(a)*r,z=8+Math.sin(a)*r;oct(m,'treasure_coin_'+i,x,4.7+(i%3)*.65,z,1.4,.32,1.4,'gold');box(m,'coin_stamp_'+i,[x-.25,5.03+(i%3)*.65,z-.25],[x+.25,5.14+(i%3)*.65,z+.25],'goldPale');}
  return m;
}
function cauldron(){
  const m=make('vfx_feast_cauldron_aura');
  for(const sx of[-1,1])for(const sz of[-1,1]){
    const x=8+sx*6,z=8+sz*5;
    oct(m,'paw_foot_'+sx+'_'+sz,x,0,z,3.2,1.1,3.8,'goldDark');
    for(let k=0;k<3;k++)box(m,'paw_toe_'+sx+'_'+sz+'_'+k,[x-1.35+k*.9,.1,z-1.95],[x-.65+k*.9,.9,z-1.45],'goldPale');
    box(m,'gold_leg_'+sx+'_'+sz,[x-.65,1.1,z-.65],[x+.65,4.2,z+.65],'gold');
  }
  oct(m,'rounded_golden_belly',8,3.5,8,14.5,6.2,12.5,'goldDark',{north:'gold',south:'goldDark',east:'gold',west:'goldDark',up:'gold',down:'goldDark'});
  oct(m,'upper_body',8,9.7,8,17.0,2.2,15,'gold');
  // Hollow stepped lip surrounding an actual recessed stew surface.
  for(const [i,x0,z0,x1,z1]of[[0,-1,-.1,17,1.2],[1,-1,14.8,17,16.1],[2,-1,1.2,.3,14.8],[3,15.7,1.2,17,14.8]])box(m,'thick_pot_lip_'+i,[x0,11.9,z0],[x1,13.0,z1],'goldPale');
  box(m,'recessed_stew_surface',[.3,11.9,1.2],[15.7,12.45,14.8],'stew');
  for(let i=0;i<15;i++){const x=2.2+(i*3.6)%12,z=3+(i*4.7)%10;oct(m,'stew_bubble_'+i,x,12.45,z,.65+(i%3)*.25,.32+(i%2)*.32,.65+(i%3)*.25,i%3?'cream':'stewDark');}
  for(const s of[-1,1]){
    const x=s===1?19.0:-3.0;
    box(m,'cat_ear_handle_base'+s,[x-1.6,10.0,6.1],[x+1.6,11.0,9.9],'goldDark');
    box(m,'cat_ear_handle_left'+s,[x-1.6,11,6.8],[x-.7,15.3,9.2],'gold');
    box(m,'cat_ear_handle_right'+s,[x+.7,11,6.8],[x+1.6,15.3,9.2],'gold');
    box(m,'cat_ear_handle_peak'+s,[x-.7,14.4,6.8],[x+.7,16.0,9.2],'goldPale');
    box(m,'handle_bridge'+s,[Math.min(x,8),10.2,7.5],[Math.max(x,8),11.2,8.5],'gold');
  }
  paw(m,'front_chef_paw',8,7.3,1.62,1.6,'goldPale');
  // The ladle is a separate gold stem tilted on one legal axis only.
  box(m,'tilted_ladle_stem',[12.25,11.5,9.25],[13.0,23.0,10.0],'goldDark',rot('z',-22.5,[12.6,12,9.6]));
  oct(m,'ladle_bowl',12.4,11.9,9.7,3.3,.6,2.9,'goldDark');oct(m,'ladle_bowl_stew',12.4,12.5,9.7,2.5,.18,2.1,'cream');
  for(const [i,x,y,z,w]of[[0,3.3,18.0,8.3,2.4],[1,8.6,21.0,6.3,3.3],[2,4.7,25.0,8.1,2.7],[3,12.3,25.3,7.8,3.0]]){
    oct(m,'steam_cloud_'+i,x,y,z,w,w*.60,w*.8,'white');oct(m,'steam_cloud_lobe_'+i,x-w*.3,y+w*.5,z,w*.72,w*.38,w*.65,'cream');
  }
  spark(m,'michelin_star_one',1.7,23.0,7,.75,'goldPale');spark(m,'michelin_star_two',12.9,19.0,7,.65,'white');
  return m;
}

const builders=[goldSlash,heavySlash,titanDrop,shadowSlash,shadowMark,starArrow,bowSigil,arcaneCircle,singularity,meteor,sanctuary,holySpear,fishingVortex,cauldron];
const specs=[
  ['horizontal_crescent',{outerFront:[8,8,-13.4]},['crescent_0','crescent_1','crescent_2']],
  ['horizontal_heavy_slash',{outerFront:[8,8,-13.0]},['molten_wave','raging_flame_tooth','white_hot_core']],
  ['vertical_drop',{tip:[8,-14,8],gripTop:[8,30,8]},['obsidian_blade','molten_blade_channel','spiral_flame']],
  ['crossed_slash',{forward:[0,0,-1]},['cross_-45','cross_45','black_cat_rune']],
  ['vertical_mark',{forward:[0,0,-1]},['crescent_fang','diamond_shadow_frame','vertical_dark_eye']],
  ['forward_projectile',{tip:[8,8,-14],tail:[8,8,30]},['starhead','comet_ribbon_central','comet_ribbon_left','comet_ribbon_right']],
  ['horizontal_aiming_ring',{normal:[0,1,0]},['outer_astral_gold','constellation_chord','aim_marker']],
  ['thin_floor_circle',{normal:[0,1,0]},['outer_runic_ring','hexagram_0','hexagram_1','ancient_arcane_rune']],
  ['sphere_with_tilted_ring',{ringTiltDegrees:22.5,ringTiltAxis:'x'},['event_horizon','accretion_outer_shadow','accretion_white_hot_rim']],
  ['diagonal_meteor',{travelDirection:[0,-1,-1]},['violet_magma_body','ancient_ice_crystal','leading_impact_crystal']],
  ['thin_floor_circle',{normal:[0,1,0]},['outer_cathedral_gold','inner_healing_ring','quatrefoil_petals','holy_cross']],
  ['vertical_spear',{tip:[8,-14,8],wingYRange:[12,24]},['downward_lance','wing_-1','wing_1','holy_water_gem']],
  ['surface_vortex',{surfaceY:8,lowestY:0},['funnel_0','funnel_1','funnel_2','golden_fish','treasure_coin']],
  ['floor_cauldron',{floorAnchor:[8,0,8],steamYRange:[18,28]},['paw_foot','cat_ear_handle','recessed_stew_surface','tilted_ladle','steam_cloud']]
];
function check(m){
  const names=new Set();
  for(const e of m.elements){
    if(names.has(e.name))throw Error('Duplicate name '+m.id+'/'+e.name);names.add(e.name);
    for(let k=0;k<3;k++)if(!Number.isFinite(e.from[k])||!Number.isFinite(e.to[k])||e.from[k]>=e.to[k]||e.from[k]<-16||e.to[k]>32)throw Error('Illegal extent '+m.id+'/'+e.name+': '+JSON.stringify([e.from,e.to]));
    if(e.rotation&&![-45,-22.5,0,22.5,45].includes(e.rotation.angle))throw Error('Illegal rotation '+e.name);
  }
}
export function buildSkillVfx(){
  const models=builders.map(fn=>fn());const contract={schemaVersion:1,modelOrigin:[8,8,8],front:[0,0,-1],notice:'Authored frame; Bukkit yaw 0 points +Z. No emissive shader is assumed. Blender review is required.',models:{}};
  for(let i=0;i<models.length;i++){
    const m=models[i];check(m);m.save();const [kind,directionalPoints,requiredComponentNames]=specs[i];
    contract.models[m.id]={kind,pivot:[8,8,8],directionalPoints,requiredComponentNames,elements:m.elements.length};
  }
  fs.writeFileSync(path.join(SOURCE,'skill-vfx-contract.json'),JSON.stringify(contract,null,2)+'\n');
  return models.map(m=>m.id);
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))console.log(JSON.stringify({models:buildSkillVfx()}));
