/**
 * Golden Paws Casino prop source, Minecraft Java 1.21.4.
 * Rebuild with: node assets/yuumi97-production/sources/casino.mjs
 * Named, editable cuboids; no unsupported element rotations or baked mesh.
 * Slot authored around the retained 1 x 3 x 1 interactive block stack.
 * Roulette/trophy are static decorative props; game behavior stays in Java.
 */
import {Model, Canvas} from '../tools/asset-kit.mjs';

const rotation=(axis,angle,origin)=>({axis,angle,origin,rescale:false});
function tile(model,name,color,paint){
  model.swatch(name,color,{noise:false});
  const sw=model.swatches[name], c=new Canvas(16,16);
  c.rect(0,0,16,16,color); paint(c);
  model.canvas.blit(c,sw.x,sw.y);
  return name;
}
function star(c,cx,cy,color){
  c.polygon([[cx,cy-5],[cx+2,cy-2],[cx+5,cy-1],[cx+2,cy+1],[cx+3,cy+5],[cx,cy+3],[cx-3,cy+5],[cx-2,cy+1],[cx-5,cy-1],[cx-2,cy-2]],color);
}
function paw(c,cx,cy,color){
  c.ellipse(cx,cy+2,4,3,color);
  for(const [x,y] of [[-4,-2],[-1,-4],[2,-4],[5,-2]])c.ellipse(cx+x,cy+y,1.5,2,color);
}
function cat(c,cx,cy,color){
  c.polygon([[cx-5,cy+3],[cx-5,cy-5],[cx-2,cy-2],[cx+2,cy-2],[cx+5,cy-5],[cx+5,cy+3],[cx+2,cy+5],[cx-2,cy+5]],color);
  c.rect(cx-3,cy,2,1,'#27233e').rect(cx+2,cy,2,1,'#27233e');
  c.pixel(cx,cy+2,'#b75070').pixel(cx-1,cy+3,'#b75070').pixel(cx+1,cy+3,'#b75070');
}
function palette(m){
  m.swatch('gold','#e0ad49',{border:true});
  m.swatch('gold_light','#ffe2a0',{border:true});
  m.swatch('gold_shadow','#9a662d',{border:true});
  m.swatch('gold_mid','#c38b35',{border:true});
  m.swatch('ivory','#f4e8cc',{border:true});
  m.swatch('ink','#282237',{border:true});
  m.swatch('rose','#e478aa',{border:true});
  m.swatch('mint','#7bdfd5',{border:true});
  m.swatch('wine','#6e2947',{border:true});
}

export function slotCabinet(){
  const m=new Model('prop_slot_cabinet',128); palette(m);
  m.swatch('enamel','#35273e',{border:true});
  m.swatch('enamel_side','#49314f',{border:true});
  m.swatch('recess','#131a2a',{border:true});
  m.swatch('steel','#6e748b',{border:true});
  m.swatch('glass_glint','#d9faf2',{noise:false});
  m.swatch('red_button','#d6536f',{border:true});
  m.swatch('mint_neon','#b6ffed',{border:true});
  tile(m,'reel_paw','#f8eacd',c=>{
    c.rect(0,0,16,2,'#d7c8b9').rect(0,14,16,2,'#c9b2a4');
    paw(c,7,7,'#e978a4'); c.pixel(6,8,'#ffcadb');
  });
  tile(m,'reel_cat','#f8eacd',c=>{
    c.rect(0,0,16,2,'#d7c8b9').rect(0,14,16,2,'#c9b2a4');
    cat(c,8,7,'#8bdace');c.pixel(3,3,'#ffe59a').pixel(12,3,'#ffe59a');
  });
  tile(m,'reel_seven','#f8eacd',c=>{
    c.rect(0,0,16,2,'#d7c8b9').rect(0,14,16,2,'#c9b2a4');
    c.text('7',5,3,'#a62f58',2);c.rect(6,3,6,1,'#ffb4c8');
  });
  tile(m,'marquee','#32263e',c=>{
    c.rect(0,0,16,1,'#ffdc89').rect(0,15,16,1,'#c28a42');
    c.text('97',4,3,'#ffe6a1');c.text('PAW',2,9,'#9bf2d9');
  });
  tile(m,'paytable','#222437',c=>{
    c.text('777',2,2,'#ffd681');c.rect(2,8,12,1,'#bb759b');
    c.rect(3,11,2,2,'#ec98bc').rect(7,11,2,2,'#8cdecf').rect(11,11,2,2,'#f6d579');
  });
  tile(m,'side_crest','#4a2c4c',c=>{
    c.rect(1,1,14,1,'#bd8343').rect(1,14,14,1,'#bd8343');
    star(c,8,7,'#dfad55');c.pixel(8,5,'#fff2c0');
  });
  tile(m,'service_panel','#2e293e',c=>{
    c.rect(1,1,14,14,'#514057').rect(2,2,12,12,'#352b40');
    for(let y=4;y<12;y+=2)c.rect(4,y,8,1,'#161c2a');
    for(const [x,y]of[[2,2],[13,2],[2,13],[13,13]])c.pixel(x,y,'#c59759');
  });
  // A 3.0-block enclosure around gold/diamond, note and froglight blocks.
  m.box('base_shadow_foot',[-1.6,-9.8,.4],[17.6,-8.5,15.6],'ink');
  m.box('base_gold_bead',[-1.9,-8.5,.1],[17.9,-7.6,15.9],'gold');
  m.box('cabinet_cast_enclosure',[-.5,-7.6,1.2],[16.5,25.95,14.8],'enamel',{faces:{east:'enamel_side',west:'enamel_side',south:'service_panel'}});
  m.box('left_side_inset',[-.64,-5,3.6],[-.5,20.5,12.5],'enamel_side');
  m.box('right_side_inset',[16.5,-5,3.6],[16.64,20.5,12.5],'enamel_side');
  m.box('left_square_star_badge',[-.68,8,3.6],[-.64,16.9,12.5],'enamel_side',{faces:{west:'side_crest'}});
  m.box('right_square_star_badge',[16.64,8,3.6],[16.68,16.9,12.5],'enamel_side',{faces:{east:'side_crest'}});
  for(const [x,name]of[[-.85,'left'],[16.15,'right']]){
    m.box(name+'_gold_corner_rail',[x,-7.3,.75],[x+.7,25.5,1.8],'gold');
    m.box(name+'_mint_edge',[x+.19,1.7,.55],[x+.5,19.7,.83],'mint_neon',{shade:false});
  }
  // Upper marquee and paired ear silhouettes.
  m.box('marquee_gold_frame',[-1,20.7,.2],[17,26.4,15.2],'gold');
  m.box('marquee_enamel_inset',[-.2,21.3,-.1],[16.2,25.7,.3],'enamel',{faces:{north:'marquee'}});
  m.box('marquee_cream_brow',[-.7,26.4,.3],[16.7,27.1,14.6],'gold_light');
  for(const [x,sign,label]of[[2.25,-1,'left'],[13.75,1,'right']]){
    const r=rotation('z',sign*22.5,[x,26,7.4]);
    m.box(label+'_cat_ear_base',[x-2.1,25.9,5.5],[x+2.1,27.6,9.3],'gold',{rotation:r});
    m.box(label+'_cat_ear_taper',[x-1.5,27.6,5.8],[x+1.5,29.2,9],'gold',{rotation:r});
    m.box(label+'_cat_ear_tip',[x-.8,29.2,6.1],[x+.8,30.4,8.7],'gold_light',{rotation:r});
    m.box(label+'_cat_ear_pink_base',[x-1.4,26.6,5.25],[x+1.4,27.6,5.55],'rose',{rotation:r});
    m.box(label+'_cat_ear_pink_taper',[x-.85,27.6,5.55],[x+.85,28.9,5.85],'rose',{rotation:r});
    m.box(label+'_cat_ear_neon_tip',[x-.38,29,5.86],[x+.38,29.85,6.16],'mint_neon',{rotation:r,shade:false});
  }
  // Three separate reel wells, chamfer-like rails and glints.
  m.box('reel_gold_surround',[.4,5.6,.12],[15.6,19.7,1.15],'gold');
  m.box('reel_dark_window',[1,6.2,-.12],[15,19.1,.18],'recess');
  for(const [x,t,name]of[[1.5,'reel_paw','paw'],[6.1,'reel_cat','yuumi'],[10.7,'reel_seven','seven']]){
    m.box(name+'_roller_outer',[x,7.4,-.34],[x+3.8,17.4,-.1],'gold_shadow');
    m.box(name+'_roller_visible',[x+.16,7.65,-.44],[x+3.64,17.15,-.32],'ivory');
    m.box(name+'_square_reel_symbol',[x+.16,10.5,-.49],[x+3.64,13.98,-.44],'ivory',{faces:{north:t}});
    m.box(name+'_glass_reflection',[x+.29,15.5,-.5],[x+.55,16.8,-.44],'glass_glint',{shade:false});
    m.box(name+'_roller_lower_shadow',[x+.15,7.6,-.5],[x+3.65,8,-.44],'gold_shadow');
  }
  m.box('win_line_left_arrow',[.57,11.5,-.38],[1.38,12.4,-.12],'rose');
  m.box('win_line_right_arrow',[14.62,11.5,-.38],[15.43,12.4,-.12],'rose');
  m.box('reel_window_top_trim',[.8,18.7,-.45],[15.2,19.2,.05],'gold_light');
  // Sloping console overlays the interaction block; no gameplay hitbox changes.
  const consoleRotation=rotation('x',22.5,[8,4,1.3]);
  m.box('console_black_slope',[.3,2.2,-1.5],[15.7,5.6,1.6],'enamel',{rotation:consoleRotation});
  m.box('console_gold_lip',[.1,2,-1.65],[15.9,2.55,-1.1],'gold');
  m.box('play_button_socket',[10.5,3,-1.95],[13.9,4.8,-1.5],'gold_shadow',{rotation:consoleRotation});
  m.box('play_button_red',[10.9,3.35,-2.2],[13.5,4.45,-1.94],'red_button',{rotation:consoleRotation});
  m.box('bet_button_socket',[6.3,3,-1.95],[9.4,4.8,-1.5],'gold_shadow',{rotation:consoleRotation});
  m.box('bet_button_mint',[6.7,3.35,-2.2],[9,4.45,-1.94],'mint',{rotation:consoleRotation});
  m.box('coin_slot_gold_bezel',[1.8,3,-1.7],[4.8,5.5,-1.35],'gold');
  m.box('coin_slot_dark_cut',[2.9,3.4,-1.76],[3.6,5.15,-1.69],'recess');
  m.box('lower_paytable_bezel',[1,-3.9,.52],[15,.7,1.15],'gold');
  m.box('lower_paytable_glass',[1.5,-3.45,.29],[14.5,.25,.53],'ink',{faces:{north:'paytable'}});
  m.box('payout_recess',[3,-6.9,.5],[13,-4.7,1.19],'recess');
  m.box('payout_tray_base',[2.6,-7.2,-1.5],[13.4,-6.65,1.5],'steel');
  m.box('payout_tray_front_lip',[2.6,-7.15,-1.65],[13.4,-6.15,-1.2],'gold');
  m.box('payout_tray_left',[2.5,-7.1,-1.3],[3.1,-5.8,1.4],'gold');
  m.box('payout_tray_right',[12.9,-7.1,-1.3],[13.5,-5.8,1.4],'gold');
  // Lever assembly on the player's right, including pivot and segmented knob.
  m.box('lever_mount_shadow',[16.5,7.9,6],[18.1,12.2,10],'ink');
  m.box('lever_pivot_brass',[17.8,8.8,6.8],[19.1,11.3,9.2],'gold');
  m.box('lever_axle',[18.9,9.4,7.4],[20.4,10.7,8.6],'steel');
  const leverR=rotation('x',-22.5,[19.6,10,8]);
  m.box('lever_shaft',[19.1,10,7.5],[20.1,18.7,8.5],'gold_light',{rotation:leverR});
  m.box('lever_ball_core',[18.15,18,6.55],[21.05,20.5,9.45],'rose',{rotation:leverR});
  m.box('lever_ball_top',[18.65,20.4,7.05],[20.55,21,8.95],'ivory',{rotation:leverR});
  m.box('lever_ball_bottom',[18.65,17.45,7.05],[20.55,18.1,8.95],'wine',{rotation:leverR});
  // North is the cabinet face: negative local X is the observer's right.
  for(const e of m.elements.filter(e=>e.name.startsWith('lever_'))){
    [e.from[0],e.to[0]]=[16-e.to[0],16-e.from[0]];
    if(e.rotation)e.rotation={...e.rotation,origin:[16-e.rotation.origin[0],...e.rotation.origin.slice(1)]};
  }
  for(const x of[1.2,14.4])for(const y of[-6,20])m.box('brass_fastener_'+x+'_'+y,[x,y,.35],[x+.45,y+.45,.7],'gold_light');
  m.display.gui={rotation:[18,-28,0],translation:[0,-1,0],scale:[.37,.37,.37]};
  m.display.fixed={rotation:[0,180,0],translation:[0,-1,0],scale:[.4,.4,.4]};
  m.display.ground={rotation:[0,0,0],translation:[0,5.4,0],scale:[.3,.3,.3]};
  m.save();return m;
}

// Legal tangent boxes: cardinal base plus one of -45/-22.5/0/22.5/45.
function tangent(m,name,angle,r,width,depth,y0,y1,texture){
  let q=Math.round(angle/90),residual=angle-q*90;q=((q%4)+4)%4;
  const c=8;let from,to;
  if(q===0){from=[c-width/2,y0,c-r-depth/2];to=[c+width/2,y1,c-r+depth/2];}
  if(q===1){from=[c-r-depth/2,y0,c-width/2];to=[c-r+depth/2,y1,c+width/2];}
  if(q===2){from=[c-width/2,y0,c+r-depth/2];to=[c+width/2,y1,c+r+depth/2];}
  if(q===3){from=[c+r-depth/2,y0,c-width/2];to=[c+r+depth/2,y1,c+width/2];}
  m.box(name,from,to,texture,residual?{rotation:rotation('y',residual,[8,y0,8])}:{});
}
function disc(m,name,r,y0,y1,texture,step=1){
  // Non-overlapping strips give a clean pixel silhouette without coincident top faces.
  const bands=[];
  for(let z=-r;z<r-.0001;z+=step){
    const z1=Math.min(r,z+step),outer=Math.max(Math.abs(z),Math.abs(z1));
    const extent=Math.max(step/2,Math.round(Math.sqrt(Math.max(0,r*r-outer*outer))*2)/2);
    const last=bands.at(-1);if(last&&last.extent===extent)last.z1=z1;else bands.push({z0:z,z1,extent});
  }
  bands.forEach((b,i)=>m.box(name+'_'+i,[8-b.extent,y0,8+b.z0],[8+b.extent,y1,8+b.z1],texture));
}
export function rouletteWheel(){
  const m=new Model('prop_roulette_wheel',128);palette(m);
  m.swatch('mahogany','#6e3435',{kind:'wood',border:true});
  m.swatch('mahogany_lit','#914b42',{kind:'wood',border:true});
  m.swatch('mahogany_dark','#432938',{kind:'wood',border:true});
  m.swatch('red_pocket','#bd3d58',{border:true});
  m.swatch('black_pocket','#242538',{border:true});
  m.swatch('green_pocket','#379c89',{border:true});
  m.swatch('ball','#fff5d4',{border:true});
  tile(m,'golden_yuumi_pocket','#318d7d',c=>{cat(c,8,7,'#f3ce75');});
  tile(m,'red_number','#bd3d58',c=>{c.text('7',6,5,'#ffe9bc');});
  tile(m,'black_number','#242538',c=>{c.text('9',6,5,'#eadfc8');});
  disc(m,'lower_mahogany_platter',11.5,6.1,7.1,'mahogany_dark');
  disc(m,'recessed_bowl',9.65,7.12,7.65,'mahogany');
  // Eight side rails and a narrow inner golden lip, supported by the solid platter.
  for(let i=0;i<8;i++){
    tangent(m,'rim_wood_'+i,i*45,10.15,8.55,2.3,6.85,9.2,'mahogany_lit');
    tangent(m,'rim_top_gold_inlay_'+i,i*45,10.16,8.48,.23,9.2,9.34,'gold');
    tangent(m,'inner_brass_ball_rail_'+i,i*45,8.86,7.4,.28,8.6,8.95,'gold_light');
  }
  // Sixteen stylized pockets, deliberately decorative rather than game odds data.
  for(let i=0;i<16;i++){
    const a=i*22.5,material=i===0?'golden_yuumi_pocket':i%2?'red_number':'black_number';
    tangent(m,'pocket_divider_frame_'+i,a,7.4,2.94,2.35,7.72,8.3,'gold_shadow');
    tangent(m,'colored_number_pocket_'+i,a,7.4,2.64,2.07,8.3,8.48,material);
  }
  disc(m,'inner_polished_bowl',5.95,7.7,8.05,'mahogany_dark');
  disc(m,'spindle_cone_lower',4.55,8.05,8.55,'mahogany_lit');
  disc(m,'spindle_cone_middle',3.5,8.55,9.2,'mahogany');
  disc(m,'spindle_cone_upper',2.45,9.2,9.85,'gold_mid');
  m.box('spindle_octagonal_foot',[6.7,9.8,6.7],[9.3,10.4,9.3],'gold',{rotation:rotation('y',45,[8,9.8,8])});
  m.box('spindle_column',[7.25,10.35,7.25],[8.75,14.75,8.75],'gold_light');
  m.box('spinner_cross_east_west',[3.3,13.5,7.4],[12.7,14.4,8.6],'gold');
  m.box('spinner_cross_north_south',[7.4,13.5,3.3],[8.6,14.4,12.7],'gold');
  for(const [x,z,label]of[[3.3,8,'west'],[12.7,8,'east'],[8,3.3,'north'],[8,12.7,'south']]){
    m.box('spinner_'+label+'_cap',[x-.65,13.3,z-.65],[x+.65,14.7,z+.65],'gold_light');
    m.box('spinner_'+label+'_mint_jewel',[x-.38,14.7,z-.38],[x+.38,15.05,z+.38],'mint');
  }
  m.box('spindle_crown_base',[6.95,14.6,6.95],[9.05,15.2,9.05],'gold');
  m.box('spindle_rose_finial',[7.35,15.2,7.35],[8.65,16.25,8.65],'rose',{rotation:rotation('y',45,[8,15.2,8])});
  m.box('ivory_ball_core',[10.15,8.95,.5],[11.15,9.95,1.5],'ball');
  m.box('ivory_ball_cap',[10.35,9.95,.7],[10.95,10.12,1.3],'ivory');
  m.display.gui={rotation:[35,-30,0],translation:[0,0,0],scale:[.61,.61,.61]};
  m.display.ground={rotation:[0,0,0],translation:[0,.85,0],scale:[.4,.4,.4]};
  m.display.fixed={rotation:[90,0,0],translation:[0,0,0],scale:[.65,.65,.65]};
  m.save();return m;
}

export function derbyTrophy(){
  const m=new Model('prop_derby_trophy',128);palette(m);
  m.swatch('marble_white','#e9e7df',{kind:'stone',border:true});
  m.swatch('marble_shadow','#c3c5cd',{kind:'stone',border:true});
  m.swatch('marble_black','#353342',{kind:'stone',border:true});
  m.swatch('cat_gold','#e3b550',{border:true});
  m.swatch('cat_gold_lit','#ffdf8d',{border:true});
  m.swatch('cat_gold_shade','#bc8138',{border:true});
  m.swatch('leaf_gold','#d2a047',{border:true});
  tile(m,'champion_plaque','#67384d',c=>{
    c.rect(0,0,16,1,'#ffdb84').rect(0,15,16,1,'#c58d42');
    c.text('97',4,2,'#ffe3a0');c.text('CUP',2,9,'#f3cf88');
  });
  tile(m,'cat_eye','#e3b550',c=>{
    c.polygon([[3,5],[12,5],[10,10],[4,9]],'#4b5651');
    c.rect(5,6,5,3,'#74d7cb').rect(7,6,1,3,'#23333a').pixel(5,6,'#f0ffe5');
  });
  // Broad, grounded marble pedestal and inset award plaque.
  m.box('marble_lower_plinth',[-3,-4.8,1],[19,-3.4,15],'marble_shadow');
  m.box('marble_lower_bevel',[-2.5,-3.4,1.5],[18.5,-2.6,14.5],'marble_white');
  m.box('marble_pedestal_body',[-1.8,-2.6,2.2],[17.8,1.2,13.8],'marble_white');
  m.box('pedestal_black_inset',[-1.95,-2.45,2.05],[17.95,.5,2.3],'marble_black');
  m.box('plaque_gold_surround',[1.9,-2.1,1.78],[14.1,.4,2.13],'gold');
  m.box('plaque_97_champion',[2.35,-1.8,1.62],[13.65,.1,1.81],'wine',{faces:{north:'champion_plaque'}});
  for(const x of[-.8,16.25])m.box('plaque_mount_'+x,[x,-1.3,1.96],[x+.55,-.75,2.15],'gold_light');
  m.box('pedestal_upper_bead',[-2,1.2,2],[18,1.9,14],'gold_shadow');
  m.box('pedestal_upper_marble',[-1,1.9,3],[17,2.85,13],'marble_white');
  m.box('pedestal_top_gold',[.2,2.85,4.2],[15.8,3.4,11.8],'gold');
  m.box('stem_lower_step',[4,3.4,5.4],[12,4.4,10.6],'gold_shadow');
  m.box('stem_capital',[5.2,4.4,6],[10.8,5.6,10],'gold');
  m.box('sculpture_support_stem',[6.8,5.6,6.8],[9.2,15.8,9.2],'gold_mid');
  // Open laurel wreath behind the runner: swept stems and individually tilted leaves.
  for(const sign of[-1,1]){
    const side=sign<0?'left':'right';
    m.box(side+'_wreath_lower_branch',[8+sign*5-.45,5.1,10.9],[8+sign*5+.45,13.5,11.8],'gold_shadow',{rotation:rotation('z',-sign*22.5,[8+sign*5,9.2,11.35])});
    m.box(side+'_wreath_upper_branch',[8+sign*9-.4,11.4,10.85],[8+sign*9+.4,21.1,11.85],'gold_shadow',{rotation:rotation('z',sign*22.5,[8+sign*9,16.25,11.35])});
    for(let i=0;i<5;i++){
      const y=8+i*2.6,spread=5.5+Math.sin(i/4*Math.PI)*4,x=8+sign*spread;
      const r=rotation('z',sign*(i>2?-22.5:45),[x,y,11.3]);
      const leafZ=i*.035;
      m.box(side+'_laurel_leaf_'+i,[x-1.1,y-1.8,10.35+leafZ],[x+1.1,y+1.8,11.5+leafZ],'leaf_gold',{rotation:r});
      m.box(side+'_laurel_leaf_vein_'+i,[x-.16,y-1.35,10.19+leafZ],[x+.16,y+1.35,10.4+leafZ],'gold_light',{rotation:r});
    }
  }
  m.box('laurel_ribbon_left',[4.7,5.7,10.35],[7.9,7.5,11.2],'wine',{rotation:rotation('z',-22.5,[7.4,6.6,10.8])});
  m.box('laurel_ribbon_right',[8.1,5.7,10.35],[11.3,7.5,11.2],'wine',{rotation:rotation('z',22.5,[8.6,6.6,10.8])});
  // Leaping cat: narrow abdomen, proud shoulder, outstretched paws and curling tail.
  m.box('cat_hindquarters',[-.6,15.7,5.5],[4.6,20.4,10.5],'cat_gold');
  m.box('cat_elongated_body',[3.2,16.7,5.2],[12.5,21.3,10.8],'cat_gold',{rotation:rotation('z',-22.5,[7.8,19,8]),faces:{up:'cat_gold_lit',down:'cat_gold_shade'}});
  m.box('cat_chest',[10.3,18.3,5.05],[14.4,23.5,10.95],'cat_gold',{rotation:rotation('z',-22.5,[12.2,20.6,8])});
  m.box('cat_neck_collar',[12.3,20.4,4.95],[14.6,23,11.05],'rose',{rotation:rotation('z',-22.5,[13.4,21.5,8])});
  m.box('cat_head',[13,21,4.5],[18.6,25.5,11.5],'cat_gold',{faces:{up:'cat_gold_lit',down:'cat_gold_shade'}});
  m.box('cat_cheek',[17.1,20.8,5],[19.3,23.5,11],'cat_gold_lit');
  m.box('cat_muzzle',[18.65,21.35,6],[20.55,23.1,10],'cat_gold_lit');
  m.box('cat_small_nose',[20.5,22.15,7.1],[20.95,22.9,8.9],'wine');
  for(const [z,label]of[[4.2,'front'],[9.8,'back']]){
    m.box(label+'_cat_ear',[13.7,24.5,z],[16.1,28.1,z+2],'cat_gold',{rotation:rotation('z',-22.5,[14.9,25,z+1])});
    m.box(label+'_ear_inset',[14.1,25.15,z-.12],[15.65,27.15,z+.15],'cat_gold_shade',{rotation:rotation('z',-22.5,[14.9,25,z+1])});
  }
  m.box('front_eye_inlay',[16.15,22.45,4.34],[18.3,24.3,4.51],'cat_gold',{faces:{north:'cat_eye'}});
  m.box('back_eye_inlay',[16.15,22.45,11.49],[18.3,24.3,11.66],'cat_gold',{faces:{south:'cat_eye'}});
  m.box('collar_bell',[12.8,19.2,4.45],[14.2,20.9,5.85],'gold_light');
  m.box('collar_bell_slit',[13.4,19.35,4.32],[13.65,20.2,4.46],'gold_shadow');
  // Forelegs intentionally separated in depth so the running pose reads in three-quarter view.
  for(const [z,offset,label]of[[5.45,0,'front'],[9.1,-1.8,'back']]){
    m.box(label+'_front_upper_leg',[11.4+offset,14.7,z],[13.4+offset,20.4,z+1.5],'cat_gold',{rotation:rotation('z',45,[12.4+offset,18.9,z+.75])});
    m.box(label+'_front_forearm',[14+offset,13.7,z+.1],[18.4+offset,15.4,z+1.4],'cat_gold',{rotation:rotation('z',-22.5,[16+offset,14.5,z+.75])});
    m.box(label+'_front_paw',[17.8+offset,13.5,z-.28],[21+offset,15.05,z+1.75],'cat_gold_lit');
    m.box(label+'_hind_upper_leg',[-.3-offset,12.3,z],[2.5-offset,17.6,z+1.5],'cat_gold',{rotation:rotation('z',45,[1.1-offset,15.3,z+.75])});
    m.box(label+'_hind_hock',[-3.4-offset,11.8,z+.1],[.7-offset,13.6,z+1.4],'cat_gold',{rotation:rotation('z',-22.5,[-1.5-offset,12.7,z+.75])});
    m.box(label+'_hind_paw',[-4.5-offset,11.35,z-.25],[-1.4-offset,12.85,z+1.75],'cat_gold_lit');
  }
  m.box('tail_root',[-2.9,18.4,6.9],[.9,20.4,9.1],'cat_gold',{rotation:rotation('z',-22.5,[-.9,19.4,8])});
  m.box('tail_upward_sweep',[-4.7,19.3,7],[-2.7,24.1,9],'cat_gold',{rotation:rotation('z',22.5,[-3.7,21.5,8])});
  m.box('tail_curved_tip',[-6.2,23.2,7.1],[-2.9,25.15,8.9],'cat_gold_lit',{rotation:rotation('z',-22.5,[-4.5,24.1,8])});
  m.box('tail_tip_taper',[-6.7,22.5,7.3],[-5.5,24,8.7],'cat_gold_lit');
  m.display.gui={rotation:[12,-23,0],translation:[0,-1,0],scale:[.42,.42,.42]};
  m.display.ground={rotation:[0,0,0],translation:[0,4.3,0],scale:[.3,.3,.3]};
  m.display.fixed={rotation:[0,180,0],translation:[0,-1,0],scale:[.42,.42,.42]};
  m.save();return m;
}

for(const m of[slotCabinet(),rouletteWheel(),derbyTrophy()]){
  console.log(`${m.id}: ${m.elements.length} named cuboids, ${Object.keys(m.swatches).length} atlas tiles`);
}
