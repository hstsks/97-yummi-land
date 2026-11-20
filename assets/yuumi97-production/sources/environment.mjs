/**
 * Yuumi97 v3 environmental props. Negative Z is the presentation face.
 * Named Minecraft cuboids are the editable source of truth; all five bases sit
 * on Y=0 around [8,0,8]. No Java, pack builder or shared helper is modified.
 */
import {Model, Canvas} from '../tools/asset-kit.mjs';

const rot=(axis,angle,origin)=>({axis,angle,origin,rescale:false});
function tile(m,name,color,paint){
  m.swatch(name,color,{noise:false});
  const t=m.swatches[name],c=new Canvas(16,16);
  c.rect(0,0,16,16,color);paint(c);m.canvas.blit(c,t.x,t.y);
}
function materials(m){
  m.swatch('gold','#DDAA49',{border:true});
  m.swatch('gold_lit','#FFE3A1',{border:true});
  m.swatch('gold_dark','#976332',{border:true});
  m.swatch('ink','#29283C',{border:true});
  m.swatch('deep','#151F30',{noise:false});
  m.swatch('ivory','#F4E8CD',{border:true});
  m.swatch('rose','#E787B0',{border:true});
  m.swatch('rose_dark','#A74D7F',{border:true});
  m.swatch('mint','#72DCD0',{border:true});
  m.swatch('mint_lit','#D1FFF0',{noise:false});
  m.swatch('teal','#278A9C',{border:true});
  m.swatch('violet','#8B69BD',{border:true});
  m.swatch('violet_lit','#D7B2EF',{border:true});
  m.swatch('steel','#68778D',{border:true});
  m.swatch('coral','#EC776D',{border:true});
  m.swatch('leaf','#70B46F',{border:true});
  m.swatch('leaf_dark','#357558',{border:true});
}
function display(m,scale=.48){
  m.display.gui={rotation:[22,-30,0],translation:[0,-1,0],scale:[scale,scale,scale]};
  m.display.ground={rotation:[0,0,0],translation:[0,4,0],scale:[.32,.32,.32]};
  m.display.fixed={rotation:[0,180,0],translation:[0,-2,0],scale:[.6,.6,.6]};
}
function octagon(m,name,cx,cz,rx,rz,y0,y1,texture,options={}){
  const cut=.58;
  m.box(name+'_middle',[cx-rx,y0,cz-rz*cut],[cx+rx,y1,cz+rz*cut],texture,options);
  m.box(name+'_front',[cx-rx*cut,y0,cz-rz],[cx+rx*cut,y1,cz-rz*cut],texture,options);
  m.box(name+'_back',[cx-rx*cut,y0,cz+rz*cut],[cx+rx*cut,y1,cz+rz],texture,options);
}
function roundFace(m,name,x,y,z,r,depth,texture){
  m.box(name+'_wide',[x-r,y-r*.55,z],[x+r,y+r*.55,z+depth],texture);
  m.box(name+'_top',[x-r*.55,y+r*.55,z],[x+r*.55,y+r,z+depth],texture);
  m.box(name+'_bottom',[x-r*.55,y-r,z],[x+r*.55,y-r*.55,z+depth],texture);
}
function paw(m,name,x,y,z,s,texture='gold_lit'){
  roundFace(m,name+'_pad',x,y,z,s*1.1,.3,texture);
  for(const [i,dx,dy] of [[0,-1.7,1.1],[1,-.65,2],[2,.65,2],[3,1.7,1.1]])
    roundFace(m,name+'_toe_'+i,x+dx*s,y+dy*s,z,s*.48,.3,texture);
}
function steppedBall(m,name,x,y,z,r,lower,upper='mint'){
  const levels=[[-1,-.68,.53],[-.68,-.28,.85],[-.28,.28,1],[.28,.68,.85],[.68,1,.53]];
  for(let i=0;i<levels.length;i++){
    const[a,b,width]=levels[i];octagon(m,name+'_shell_'+i,x,z,r*width,r*width,y+r*a,y+r*b,i<2?lower:i===2?'gold':upper);
  }
  m.box(name+'_specular',[x-r*.45,y+r*.45,z-r*.77],[x-r*.05,y+r*.65,z-r*.6],'mint_lit',{shade:false});
}
function ring(m,name,cx,cz,r,thickness,y0,y1,texture){
  const width=2*r*Math.tan(Math.PI/8)+.045;
  for(let i=0;i<8;i++){
    const angle=i*45,q=Math.round(angle/90),residual=angle-q*90,k=((q%4)+4)%4;
    let from,to;
    if(k===0){from=[cx-width/2,y0,cz-r-thickness/2];to=[cx+width/2,y1,cz-r+thickness/2];}
    if(k===1){from=[cx-r-thickness/2,y0,cz-width/2];to=[cx-r+thickness/2,y1,cz+width/2];}
    if(k===2){from=[cx-width/2,y0,cz+r-thickness/2];to=[cx+width/2,y1,cz+r+thickness/2];}
    if(k===3){from=[cx+r-thickness/2,y0,cz-width/2];to=[cx+r+thickness/2,y1,cz+width/2];}
    m.box(name+'_'+i,from,to,texture,residual?{rotation:rot('y',residual,[cx,y0,cz])}:{});
  }
}
function crystal(m,name,x,y,z,w,h,base='teal',lit='mint_lit',rotation){
  const opts=rotation?{rotation}:{};
  octagon(m,name+'_waist',x,z,w*.5,w*.5,y+h*.27,y+h*.72,base,opts);
  octagon(m,name+'_upper',x,z,w*.34,w*.34,y+h*.72,y+h*.87,lit,opts);
  m.box(name+'_tip',[x-w*.17,y+h*.87,z-w*.17],[x+w*.17,y+h,z+w*.17],lit,opts);
  octagon(m,name+'_lower',x,z,w*.33,w*.33,y+h*.12,y+h*.27,base,opts);
  m.box(name+'_point',[x-w*.14,y,z-w*.14],[x+w*.14,y+h*.12,z+w*.14],base,opts);
  m.box(name+'_facet_glint',[x-w*.22,y+h*.35,z-w*.53],[x-w*.08,y+h*.68,z-w*.48],lit,opts);
}
function rivets(m,name,xs,ys,z){for(const x of xs)for(const y of ys)m.box(name+'_'+x+'_'+y,[x-.18,y-.18,z],[x+.18,y+.18,z+.22],'gold_lit');}
function linePixels(m,name,points,z,thickness,texture){
  for(let i=0;i+1<points.length;i++){
    const a=points[i],b=points[i+1],n=Math.max(1,Math.ceil(Math.max(Math.abs(b[0]-a[0]),Math.abs(b[1]-a[1]))/.55));
    for(let j=0;j<n;j++){
      const t=(j+.5)/n,x=a[0]+(b[0]-a[0])*t,y=a[1]+(b[1]-a[1])*t;
      m.box(name+'_'+i+'_'+j,[x-thickness/2,y-thickness/2,z],[x+thickness/2,y+thickness/2,z+.25],texture,{shade:false});
    }
  }
}

export function gachaMachine(){
  const m=new Model('prop_gacha_machine',128);materials(m);
  m.swatch('enamel','#644066',{border:true});
  m.swatch('enamel_side','#794B74',{border:true});
  tile(m,'service','#45384F',c=>{c.rect(1,1,14,14,'#715368').rect(2,2,12,12,'#49364F');for(let y=4;y<13;y+=2)c.rect(4,y,8,1,'#282539');});
  tile(m,'showcase_back','#39728A',c=>{c.rect(1,1,14,14,'#488F9F');c.line(0,10,10,0,'#8DDED8',2);c.line(6,15,15,6,'#5FB4C0');c.rect(0,14,16,2,'#34546D');});
  tile(m,'marquee','#3D304C',c=>{c.text('97',1,2,'#FFE6A6',2);c.rect(1,13,14,1,'#ED8EB5');});
  tile(m,'capsule_label','#E7D7BA',c=>{c.rect(1,1,14,1,'#AD7840');c.text('PAW',2,5,'#6B3C5C');c.rect(1,13,14,1,'#AD7840');});
  octagon(m,'foundation_shadow',8,8,9.8,8.2,0,.8,'ink');
  octagon(m,'foundation_gold_bead',8,8,10,8.4,.8,1.4,'gold');
  m.box('lower_cabinet',[-.6,1.4,1],[16.6,10.7,15],'enamel',{faces:{east:'enamel_side',west:'enamel_side',south:'service'}});
  m.box('front_pink_inset',[.4,2,.6],[15.6,9.8,1.04],'rose');
  m.box('front_cream_control_panel',[1.1,5.1,.3],[14.9,9.4,.64],'ivory');
  m.box('control_label',[1.5,6.1,.2],[5.2,8.8,.32],'ivory',{faces:{north:'capsule_label'}});
  for(const x of[-.7,16])m.box('cabinet_gold_corner_'+x,[x,1.6,.75],[x+.7,10.5,1.6],'gold');
  m.box('coin_slot_brass',[12,6.1,-.05],[14.2,8.7,.32],'gold_dark');
  m.box('coin_slot_opening',[12.85,6.55,-.14],[13.35,8.25,-.02],'deep');
  roundFace(m,'dial_recess',8.2,7.3,-.12,2,.35,'gold_dark');
  roundFace(m,'dial_cast_brass',8.2,7.3,-.5,1.73,.4,'gold');
  roundFace(m,'dial_inner_hub',8.2,7.3,-.78,.85,.31,'gold_lit');
  m.box('dial_turn_handle',[6.55,6.97,-1.13],[9.85,7.62,-.74],'ivory',{rotation:rot('z',22.5,[8.2,7.3,-.94])});
  m.box('payout_black_mouth',[4.2,2.1,.15],[11.8,4.6,.66],'deep');
  m.box('payout_mouth_upper_bead',[3.9,4.45,-.05],[12.1,4.95,.7],'gold');
  m.box('payout_tray_floor',[3.9,1.95,-1],[12.1,2.45,1.4],'steel');
  m.box('payout_tray_lip',[3.9,1.95,-1.2],[12.1,2.85,-.85],'gold');
  for(const x of[3.9,11.65])m.box('payout_tray_side_'+x,[x,2.4,-1],[x+.45,3.5,1.1],'gold');
  steppedBall(m,'dispensed_capsule',8,3.2,.8,.8,'rose','mint_lit');
  m.box('showcase_lower_shelf',[-1,10.4,.5],[17,11.35,15.5],'gold');
  m.box('showcase_interior_floor',[.5,11.35,2],[15.5,11.7,14.4],'gold_lit');
  m.box('showcase_blue_back',[.5,11.4,14.2],[15.5,25.5,14.65],'teal',{faces:{north:'showcase_back'}});
  for(const x of[-.6,15.9]){
    m.box('showcase_front_upright_'+x,[x,11.2,.65],[x+.7,25.9,1.6],'gold');
    m.box('showcase_back_upright_'+x,[x,11.2,14.5],[x+.7,25.9,15.2],'gold');
    m.box('showcase_side_cyan_sill_'+x,[x+.12,11.55,1.6],[x+.5,11.85,14.5],'mint');
    m.box('showcase_side_cyan_brow_'+x,[x+.12,25.05,1.6],[x+.5,25.4,14.5],'mint');
  }
  const balls=[
    [2.8,13.6,4.2,1.75,'rose','mint_lit'],[7.7,13.4,4.3,1.55,'violet','mint'],[12.7,13.65,4.2,1.8,'coral','ivory'],
    [4.8,17.2,4.6,1.7,'teal','mint_lit'],[10.4,17.5,4.8,1.95,'rose_dark','rose'],[7.3,21.5,5.3,1.65,'gold','ivory'],
    [3,14,10,1.8,'leaf','mint_lit'],[8,14.1,10.4,2,'violet','violet_lit'],[13,14.2,10.5,1.65,'teal','ivory'],
    [3.4,18.4,10.5,1.85,'coral','ivory'],[12.3,19.4,10.5,1.8,'gold','mint'],[8.1,21.3,11.4,1.7,'rose','mint_lit']
  ];
  balls.forEach((a,i)=>steppedBall(m,'showcase_capsule_'+i,...a));
  // Narrow glints describe the clear front pane without hiding the capsule pile.
  m.box('glass_front_bottom_glint',[.5,11.9,.62],[15.5,12.15,.79],'mint_lit',{shade:false});
  m.box('glass_front_left_glint',[.55,20.5,.65],[.83,24.8,.83],'mint_lit',{shade:false});
  m.box('glass_front_diagonal_glint',[1.8,20.3,.65],[2.08,24.7,.83],'mint_lit',{rotation:rot('z',-22.5,[1.9,22.5,.74]),shade:false});
  m.box('showcase_roof_gold',[-1,25.4,.2],[17,26.4,15.7],'gold');
  m.box('marquee_cream_cap',[-.5,26.4,.75],[16.5,27.1,15.1],'gold_lit');
  m.box('front_central_badge',[4.4,24.2,-.05],[11.6,27.3,.35],'ink',{faces:{north:'marquee'}});
  for(const[x,s,label] of[[2.3,-1,'left'],[13.7,1,'right']]){
    const r=rot('z',s*22.5,[x,26.5,7.8]);
    m.box(label+'_ear_base',[x-2.1,26.5,6.2],[x+2.1,28.1,9.3],'gold',{rotation:r});
    m.box(label+'_ear_upper',[x-1.4,28.1,6.5],[x+1.4,29.5,9.1],'gold',{rotation:r});
    m.box(label+'_ear_tip',[x-.6,29.5,6.9],[x+.6,30.8,8.7],'gold_lit',{rotation:r});
    m.box(label+'_ear_pink',[x-1.35,27.2,5.98],[x+1.35,28.4,6.28],'rose',{rotation:r});
    m.box(label+'_ear_pink_tip',[x-.6,28.4,6.28],[x+.6,29.55,6.58],'rose',{rotation:r});
  }
  rivets(m,'front_brass_rivet',[1.2,14.8],[2.9,9.4],.15);
  display(m,.44);m.save();return m;
}

export function stockBoard(){
  const m=new Model('prop_stock_board',128);materials(m);
  m.swatch('oak','#594037',{kind:'wood',border:true});
  m.swatch('oak_lit','#805843',{kind:'wood',border:true});
  m.swatch('oak_dark','#352B32',{kind:'wood',border:true});
  m.swatch('grid','#29464B',{noise:false});
  m.swatch('bull','#63E1AC',{noise:false});
  m.swatch('bear','#F07B80',{noise:false});
  tile(m,'ticker','#182A35',c=>{c.text('97',0,3,'#FFE3A1');c.text('UP',9,3,'#73E6B8');c.rect(1,11,5,1,'#E77F83');c.rect(9,11,6,1,'#6CDBB3');});
  tile(m,'oak_crest','#5C4037',c=>{c.rect(1,1,14,1,'#A97C4A');c.rect(1,14,14,1,'#A97C4A');c.text('97',4,5,'#E8BA6C');});
  for(const[x,n]of[[-3.5,'left'],[19.5,'right']]){
    m.box(n+'_foot_dark',[x-3.5,0,2.5],[x+3.5,.7,13.5],'ink');
    m.box(n+'_foot_oak',[x-3.2,.7,3],[x+3.2,1.6,13],'oak_lit');
    m.box(n+'_foot_gold_band',[x-3.25,1.45,2.95],[x+3.25,1.85,13.05],'gold');
    m.box(n+'_column',[x-.9,1.85,6.9],[x+.9,10,9.1],'oak');
    m.box(n+'_column_gold_front',[x-.35,2.1,6.6],[x+.35,9,6.95],'gold');
    m.box(n+'_column_shoe',[x-1.4,1.85,6.4],[x+1.4,3.1,9.6],'gold_dark');
  }
  m.box('stand_lower_cross_brace',[-3.5,4.2,7.4],[19.5,5,8.6],'oak_dark');
  m.box('board_solid_oak_back',[-7.1,8.6,5.2],[23.1,26,9.9],'oak',{faces:{south:'oak_crest'}});
  m.box('board_gold_outer_face',[-7.5,8.3,4.55],[23.5,26.3,5.6],'gold_dark');
  m.box('board_outer_gold_bead',[-7.25,8.55,4.25],[23.25,26.05,4.65],'gold');
  m.box('board_recess_oak',[-6.6,9.2,4],[22.6,25.4,4.5],'oak_dark');
  m.box('board_inner_gold_bead',[-6,9.8,3.78],[22,24.8,4.1],'gold_lit');
  m.box('board_dark_monitor',[-5.65,10.15,3.55],[21.65,24.45,3.83],'deep');
  for(const y of[13.1,16.1,19.1,22.1])m.box('monitor_horizontal_grid_'+y,[-5.3,y,3.48],[21.3,y+.1,3.56],'grid');
  for(const x of[-2,2,6,10,14,18])m.box('monitor_vertical_grid_'+x,[x,10.6,3.47],[x+.1,23.8,3.56],'grid');
  // Rising green candlesticks have independent wicks and solid bodies.
  for(const[i,x,y,h]of[[0,-4.4,12.1,2.5],[1,-1.5,13.2,3],[2,1.4,15.2,2.8],[3,4.3,17.2,3.5]]){
    m.box('bull_candle_wick_'+i,[x+.49,y-.85,3.07],[x+.66,y+h+1.1,3.32],'bull',{shade:false});
    m.box('bull_candle_body_'+i,[x,y,2.85],[x+1.15,y+h,3.19],'bull',{shade:false});
    m.box('bull_candle_highlight_'+i,[x+.12,y+.2,2.79],[x+.33,y+h-.2,2.87],'mint_lit',{shade:false});
  }
  linePixels(m,'bull_arrow_shaft',[[-3.5,16.5],[1,20.4],[4.4,22.2]],2.76,.42,'mint_lit');
  m.box('bull_arrow_head_tip',[4.2,21.3,2.7],[5.1,23.3,3.01],'mint_lit',{shade:false});
  m.box('bull_arrow_head_left',[3.2,22.3,2.7],[4.7,23.3,3.01],'mint_lit',{shade:false});
  linePixels(m,'bear_descending_chart',[[8.2,21.7],[10.3,19.5],[12.5,20.2],[15.3,15.8],[17.6,17.2],[20.4,12.3]],2.9,.56,'bear');
  for(const[i,x,y]of[[0,8.2,21.7],[1,12.5,20.2],[2,17.6,17.2],[3,20.4,12.3]])roundFace(m,'bear_chart_point_'+i,x,y,2.72,.37,.3,'coral');
  m.box('monitor_ticker_strip',[-5.25,10.5,3.13],[20.95,11.6,3.5],'deep',{faces:{north:'ticker'}});
  m.box('top_oak_crown',[-7.7,26.05,5],[23.7,26.85,10.2],'oak_lit');
  m.box('top_gold_cornice',[-7.95,26.85,4.8],[23.95,27.4,10.4],'gold_lit');
  m.box('bottom_gold_cornice',[-7.8,7.85,4.6],[23.8,8.4,10.25],'gold');
  for(const x of[-7.3,22.6]){
    m.box('side_raised_oak_flute_'+x,[x,9,4.07],[x+.65,25.55,4.3],'oak_lit');
    for(const y of[10,15.5,23.8])m.box('side_gold_binding_'+x+'_'+y,[x-.12,y,3.95],[x+.77,y+.45,4.4],'gold');
  }
  roundFace(m,'crest_gold_support',8,27.25,5,3,.7,'gold_dark');
  roundFace(m,'crest_oak_medallion',8,27.3,4.65,2.7,.45,'oak_dark');
  paw(m,'crest_cat_paw',8,27.1,4.2,.92);
  rivets(m,'frame_nail',[-6.75,22.75],[9,25.55],3.95);
  // North-facing viewers see local X in reverse. Make chart time read left to
  // right: green rises and coral falls in the actual presentation view.
  for(const e of m.elements.filter(e=>/^(bull_|bear_)/.test(e.name))){
    [e.from[0],e.to[0]]=[16-e.to[0],16-e.from[0]];
  }
  display(m,.42);m.save();return m;
}

export function dungeonObelisk(){
  const m=new Model('prop_dungeon_obelisk',128);materials(m);
  m.swatch('obsidian','#302A49',{kind:'stone',border:true});
  m.swatch('obsidian_lit','#4E4264',{kind:'stone',border:true});
  m.swatch('deepslate','#3E4655',{kind:'stone',border:true});
  tile(m,'rune_panel','#302A49',c=>{c.rect(7,1,2,14,'#72DCD0');c.line(3,4,7,1,'#B8F4DB');c.line(8,6,12,3,'#72DCD0');c.line(3,10,7,13,'#72DCD0');c.line(9,12,13,9,'#72DCD0');c.rect(6,5,4,2,'#D1FFF0');});
  tile(m,'stone_fracture','#3E4655',c=>{c.line(0,5,7,8,'#222B3A');c.line(7,8,10,15,'#222B3A');c.line(7,8,13,4,'#66707B');c.pixel(9,11,'#7A8F94');});
  octagon(m,'ground_deepslate_plinth',8,8,10,10,0,1.3,'deepslate');
  octagon(m,'plinth_obsidian_step',8,8,8.8,8.8,1.3,2.7,'obsidian');
  octagon(m,'plinth_gold_inlay',8,8,8.1,8.1,2.7,3.05,'gold_dark');
  octagon(m,'plinth_upper_stone',8,8,7.6,7.6,3.05,4.3,'obsidian_lit');
  for(const[x,z,n]of[[2,2,'front_left'],[14,2,'front_right'],[2,14,'back_left'],[14,14,'back_right']]){
    m.box(n+'_buttress',[x-1.35,3,z-1.35],[x+1.35,6.5,z+1.35],'deepslate',{faces:{north:'stone_fracture'}});
    m.box(n+'_gold_cap',[x-1.5,6.5,z-1.5],[x+1.5,7,z+1.5],'gold');
    crystal(m,n+'_small_crystal',x,7.25,z,.9,2.4,'violet','violet_lit');
  }
  octagon(m,'column_lower_foot',8,8,4.9,4.9,4.3,5.4,'gold_dark');
  octagon(m,'column_ancient_stone',8,8,4.3,4.3,5.4,14,'obsidian');
  octagon(m,'column_mid_taper',8,8,3.7,3.7,14,16.2,'obsidian_lit');
  octagon(m,'column_gold_collar',8,8,4.35,4.35,15.65,16.4,'gold');
  m.box('front_rune_recess',[5.25,6.15,3.55],[10.75,14.7,3.86],'deep');
  m.box('front_runic_inscription',[5.65,6.45,3.32],[10.35,14.45,3.57],'obsidian',{faces:{north:'rune_panel'}});
  m.box('rear_runic_inscription',[5.65,6.45,12.43],[10.35,14.45,12.68],'obsidian',{faces:{south:'rune_panel'}});
  for(const x of[3.45,11.95])m.box('pillar_gold_corner_'+x,[x,5.4,5.3],[x+.6,15.65,10.7],'gold_dark');
  // An open four-prong cage leaves the hovering core clearly separated in space.
  for(const[x,z,n]of[[3.6,4,'front_left'],[12.4,4,'front_right'],[3.6,12,'back_left'],[12.4,12,'back_right']]){
    m.box(n+'_cage_stone_prong',[x-.8,16.2,z-.8],[x+.8,25.7,z+.8],'obsidian');
    m.box(n+'_cage_gold_edge',[x-.38,16.2,z-1],[x+.38,26.1,z-.76],'gold');
    m.box(n+'_cage_top_taper',[x-.6,25.7,z-.6],[x+.6,28.1,z+.6],'gold');
    m.box(n+'_cage_spire_tip',[x-.28,28.1,z-.28],[x+.28,29.6,z+.28],'gold_lit');
  }
  crystal(m,'hovering_teal_core',8,17.4,8,4.5,8.8,'teal','mint_lit',rot('y',45,[8,21.8,8]));
  crystal(m,'violet_orbit_left',5.9,20.7,6.8,1,2.8,'violet','violet_lit',rot('z',-22.5,[5.9,22,6.8]));
  crystal(m,'violet_orbit_right',10.5,18.6,9.1,1.1,3,'violet','violet_lit',rot('z',22.5,[10.5,20,9.1]));
  ring(m,'lower_magic_orbit',8,8,3.9,.22,18.7,18.92,'mint');
  ring(m,'upper_gold_orbit',8,8,3.55,.18,25.3,25.5,'gold_lit');
  for(const[x,y,z]of[[1,9,8],[15,11,8],[8,28.6,8]])m.box('floating_rune_spark_'+x,[x-.3,y-.3,z-.3],[x+.3,y+.3,z+.3],'mint_lit',{rotation:rot('z',45,[x,y,z]),shade:false});
  display(m,.44);m.save();return m;
}

export function boonAltar(){
  const m=new Model('prop_boon_altar',128);materials(m);
  m.swatch('quartz','#DDD9D8',{kind:'stone',border:true});
  m.swatch('quartz_shadow','#AAA9BC',{kind:'stone',border:true});
  m.swatch('book_leather','#4F3D77',{border:true});
  m.swatch('paper','#F3E7C8',{border:true});
  tile(m,'script','#F3E7C8',c=>{for(const y of[3,6,9,12]){c.rect(2,y,5,1,'#AA917D');c.rect(9,y,5,1,'#AA917D');}c.rect(7,2,2,12,'#D9C5A4');c.rect(2,3,3,1,'#54AFAE');c.rect(10,9,3,1,'#54AFAE');});
  tile(m,'card_front','#E2B04F',c=>{c.rect(1,1,14,14,'#FFF0B5').rect(2,2,12,12,'#D8A247');c.ellipse(8,10,3,2,'#FFF2BA');for(const[x,y]of[[4,6],[7,4],[10,4],[13,6]])c.rect(x-1,y,2,2,'#FFF2BA');c.pixel(3,3,'#FFFDF0');});
  tile(m,'card_rune','#E2B04F',c=>{c.rect(1,1,14,14,'#FFF0B5').rect(2,2,12,12,'#D8A247');c.polygon([[8,3],[12,8],[8,13],[4,8]],'#73D9CB');c.line(8,4,6,8,'#DAFFF0');});
  octagon(m,'altar_ground_step',8,8,10,8.5,0,1,'quartz_shadow');
  octagon(m,'altar_gold_foundation',8,8,9.5,8,1,1.65,'gold');
  octagon(m,'altar_quartz_upper_step',8,8,8.8,7.3,1.65,3.2,'quartz');
  m.box('altar_main_quartz_pedestal',[2.4,3.2,3.6],[13.6,9.8,12.4],'ivory');
  for(const x of[2.15,13.15]){
    m.box('altar_corner_quartz_flute_'+x,[x,3.2,3.35],[x+.7,10,4.15],'quartz');
    m.box('altar_corner_gold_foot_'+x,[x-.25,3.2,3.1],[x+.95,4,4.4],'gold');
    m.box('altar_corner_gold_cap_'+x,[x-.25,9.3,3.1],[x+.95,10,4.4],'gold');
  }
  m.box('altar_front_gold_recess',[4,4.3,3.25],[12,9.3,3.65],'gold_dark');
  m.box('altar_front_purple_inset',[4.4,4.65,2.98],[11.6,8.95,3.32],'book_leather');
  paw(m,'altar_front_paw',8,6.05,2.6,.83);
  octagon(m,'altar_cap_gold',8,8,8.4,6.6,9.8,10.55,'gold');
  octagon(m,'altar_cap_quartz',8,8,8.7,6.8,10.55,11.35,'quartz');
  octagon(m,'altar_top_inlay',8,8,7.65,5.75,11.35,11.6,'gold_lit');
  // Open book uses discrete legal Z hinges around the spine, with actual page thickness.
  for(const[sign,label]of[[-1,'left'],[1,'right']]){
    const r=rot('z',sign*22.5,[8,12,8]);
    const x0=sign<0?1.4:8.1,x1=sign<0?7.9:14.6;
    m.box(label+'_book_cover',[x0,11.6,3.8],[x1,12.15,12.2],'book_leather',{rotation:r});
    m.box(label+'_book_gold_page_edge',[x0+.25,12.15,4.1],[x1-.2,12.45,11.9],'gold',{rotation:r});
    for(let i=0;i<3;i++)m.box(label+'_book_paper_layer_'+i,[x0+.34+i*.09,12.45+i*.17,4.2+i*.06],[x1-.28,12.61+i*.17,11.8-i*.06],'paper',{rotation:r});
    m.box(label+'_book_visible_script',[x0+.63,13.02,4.5],[x1-.43,13.09,11.5],'paper',{rotation:r,faces:{up:'script'}});
    for(const z of[3.8,11.5])m.box(label+'_book_corner_mount_'+z,[sign<0?x0:x1-.8,11.5,z],[sign<0?x0+.8:x1,12.3,z+.7],'gold_lit',{rotation:r});
  }
  m.box('book_central_spine',[7.7,11.9,3.8],[8.3,13,12.2],'gold_dark');
  m.box('book_ribbon_bookmark',[7.6,12.98,2.2],[8.4,13.17,8.2],'rose');
  m.box('book_ribbon_hanging_end',[7.6,10.7,2.1],[8.4,13.02,2.35],'rose');
  // Three physically separated bookmarks, each with a different legal card angle.
  for(const[x,y,angle,n]of[[1.6,20.5,-22.5,'left'],[8,22.25,0,'center'],[14.4,20.5,22.5,'right']]){
    const r=rot('z',angle,[x,y,7.3]);
    m.box(n+'_floating_bookmark_gold',[x-2.15,y-3.25,7],[x+2.15,y+3.25,7.65],'gold',{rotation:r});
    m.box(n+'_floating_bookmark_front',[x-1.9,y-2.95,6.78],[x+1.9,y+2.95,7.04],'gold',{rotation:r,faces:{north:n==='center'?'card_front':'card_rune'}});
    m.box(n+'_bookmark_upper_corner',[x-2.28,y+2.55,6.66],[x-1.52,y+3.32,7.68],'gold_lit',{rotation:r});
    m.box(n+'_bookmark_lower_corner',[x+1.52,y-3.32,6.66],[x+2.28,y-2.55,7.68],'gold_lit',{rotation:r});
    m.box(n+'_bookmark_tassel',[x-.16,y-4.6,7.14],[x+.16,y-3.25,7.43],'gold_lit',{rotation:r});
    m.box(n+'_bookmark_tassel_end',[x-.5,y-5,6.98],[x+.5,y-4.35,7.59],'rose',{rotation:r});
  }
  for(const[x,y,z]of[[-1.2,22,7],[4.9,26,8],[11.1,27.5,9],[17.3,22.8,7],[8,17,6.4]]){
    m.box('blessing_spark_vertical_'+x,[x-.13,y-.65,z],[x+.13,y+.65,z+.23],'mint_lit',{shade:false});
    m.box('blessing_spark_cross_'+x,[x-.65,y-.13,z],[x+.65,y+.13,z+.23],'gold_lit',{shade:false});
  }
  display(m,.44);m.save();return m;
}

export function feastCauldron(){
  const m=new Model('prop_feast_cauldron',128);materials(m);
  m.swatch('cast_iron','#323846',{border:true});
  m.swatch('iron_lit','#4C5660',{border:true});
  m.swatch('iron_dark','#1B2632',{border:true});
  m.swatch('wood','#80513B',{kind:'wood',border:true});
  m.swatch('ember','#B94736',{border:true});
  m.swatch('flame','#F48A3E',{noise:false});
  m.swatch('flame_lit','#FFE8A1',{noise:false});
  m.swatch('soup','#EABF69',{noise:false});
  tile(m,'soup_surface','#EFCB7D',c=>{c.rect(0,0,16,16,'#EFCB7D');for(const[x,y]of[[1,2],[8,3],[5,11],[12,12]])c.rect(x,y,3,1,'#FFF0B7');c.rect(3,6,3,2,'#DEA953');c.rect(12,5,2,2,'#E2B65F');});
  tile(m,'log_end','#A6764C',c=>{c.rect(1,1,14,14,'#6C4434').rect(3,3,10,10,'#BB8A54').rect(5,5,6,6,'#815238').rect(7,7,2,2,'#D2A166');});
  tile(m,'fish_skin','#E2B777',c=>{for(let y=1;y<16;y+=4){c.line(1,y,5,y+2,'#FCDE9A');c.line(8,y,12,y+2,'#B5804B');}c.rect(0,0,16,1,'#FFF0C3');});
  // Crossed, split logs and a stable black fire grate all meet the common ground.
  for(const z of[4.1,10.9]){
    m.box('hearth_log_x_'+z,[-1,0,z],[17,1.5,z+1.45],'wood',{faces:{east:'log_end',west:'log_end'}});
    m.box('hearth_log_glowing_split_'+z,[1.5,1.36,z+.55],[14.5,1.56,z+.86],'ember',{shade:false});
  }
  for(const x of[3.2,11.2]){
    m.box('hearth_log_z_'+x,[x,1.45,.9],[x+1.6,3,15.1],'wood',{faces:{north:'log_end',south:'log_end'}});
    m.box('hearth_log_ember_'+x,[x+.4,2.85,2.1],[x+.78,3.1,13.9],'ember',{shade:false});
  }
  octagon(m,'fire_bed_coals',8,8,6,6,1.65,2.45,'ember');
  for(const x of[2.5,5.2,8,10.8,13.5])m.box('iron_grate_bar_'+x,[x,3.2,1.7],[x+.5,3.8,14.3],'iron_dark');
  for(const[x,y,z,h]of[[1.1,2.2,6,3.4],[3,2.7,2.8,4.2],[6.3,2.5,1.6,4],[10.5,2.4,1.9,4.5],[13.6,2.3,3,3.5],[15,2.4,9,3.2],[11.5,2.8,14,3.1]]){
    m.box('fire_flame_lower_'+x,[x-.7,y,z-.65],[x+.7,y+h*.6,z+.65],'flame',{shade:false});
    m.box('fire_flame_upper_'+x,[x-.4,y+h*.6,z-.4],[x+.4,y+h,z+.4],'flame',{rotation:rot('z',22.5,[x,y+h*.6,z]),shade:false});
    m.box('fire_flame_yellow_'+x,[x-.3,y,z-.8],[x+.3,y+h*.52,z-.55],'flame_lit',{shade:false});
  }
  for(const[x,z]of[[3.2,4.2],[12.8,4.2],[3.2,11.8],[12.8,11.8]])m.box('cauldron_cast_foot_'+x+'_'+z,[x-.65,3,z-.65],[x+.65,6,z+.65],'cast_iron');
  octagon(m,'cauldron_curved_bottom',8,8,4.5,4.5,4.5,5.6,'iron_dark');
  ring(m,'cauldron_lower_bowl',8,8,4.8,1.2,5.4,6.8,'cast_iron');
  ring(m,'cauldron_lower_bulge',8,8,6.05,1.2,6.8,8.9,'cast_iron');
  ring(m,'cauldron_wide_belly',8,8,7.1,1.2,8.9,12,'cast_iron');
  ring(m,'cauldron_upper_shoulder',8,8,7.6,1,12,14.4,'iron_lit');
  ring(m,'cauldron_black_inner_lip',8,8,7.25,.65,14.05,15.05,'iron_dark');
  ring(m,'cauldron_rolled_lip',8,8,7.75,.72,14.5,15.1,'steel');
  ring(m,'cauldron_gold_rim_inlay',8,8,7.8,.17,15.12,15.3,'gold');
  octagon(m,'simmering_soup_volume',8,8,6.95,6.95,12.8,14.03,'soup');
  octagon(m,'cream_soup_surface',8,8,6.96,6.96,14.03,14.09,'soup_surface');
  // Each handle is an open, pointed ear in the front XY plane. A short cast
  // bridge anchors it to the bowl; the negative space stays visible from -Z.
  for(const[sign,label]of[[-1,'left'],[1,'right']]){
    const x=8+sign*9.2,socketX=8+sign*7.85;
    m.box(label+'_handle_cast_socket',[socketX-.8,12.3,6.6],[socketX+.8,14.2,9.4],'gold_dark');
    m.box(label+'_handle_mount_bridge',[Math.min(x,socketX)-.35,13.4,7.1],[Math.max(x,socketX)+.35,14.1,8.9],'gold');
    m.box(label+'_handle_lower_rail',[x-2.1,13.8,7.05],[x+2.1,14.5,8.95],'gold');
    m.box(label+'_handle_left_upright',[x-2.1,14.25,7.1],[x-1.4,16.4,8.9],'gold');
    m.box(label+'_handle_right_upright',[x+1.4,14.25,7.1],[x+2.1,16.4,8.9],'gold');
    m.box(label+'_handle_left_pointed_ear',[x-2.15,15.9,7.1],[x-1.35,18.85,8.9],'gold_lit',{rotation:rot('z',-45,[x-1.75,16.1,8])});
    m.box(label+'_handle_right_pointed_ear',[x+1.35,15.9,7.1],[x+2.15,18.85,8.9],'gold_lit',{rotation:rot('z',45,[x+1.75,16.1,8])});
  }
  // Fat cream bubbles, herb leaves and fish portions sit above the soup plane.
  for(const[x,z,r]of[[4.5,8,1],[10.3,10,.75],[12.3,5.5,.65],[7,12,.6]])octagon(m,'cream_bubble_'+x, x,z,r,r,14.1,14.38,'ivory');
  for(const[x,z,a]of[[4.1,5.4,-22.5],[10.7,11.8,22.5],[12.3,8.2,-22.5]]){
    m.box('herb_stem_'+x,[x-.13,14.15,z-1.2],[x+.13,14.55,z+1.2],'leaf_dark',{rotation:rot('y',a,[x,14.3,z])});
    m.box('herb_leaf_left_'+x,[x-.95,14.2,z-.6],[x-.1,14.55,z+.65],'leaf',{rotation:rot('y',a,[x,14.3,z])});
    m.box('herb_leaf_right_'+x,[x+.1,14.2,z-.25],[x+.95,14.55,z+1],'leaf',{rotation:rot('y',a,[x,14.3,z])});
  }
  m.box('golden_fish_fillet',[5.8,14.12,7.25],[9.2,14.8,9.2],'gold_lit',{rotation:rot('y',-22.5,[7.5,14.45,8.2]),faces:{up:'fish_skin'}});
  for(const x of[6.35,7.25,8.15])m.box('fish_fillet_grill_mark_'+x,[x,14.79,7.5],[x+.22,14.87,8.9],'gold_dark',{rotation:rot('y',-22.5,[7.5,14.45,8.2])});
  const skew=rot('z',-22.5,[10.7,14.2,7.2]);
  m.box('fish_skewer_stick',[10.5,12.9,7],[10.9,22,7.4],'wood',{rotation:skew});
  m.box('skewered_fish_body',[9.3,17.1,6.65],[12.1,19.1,7.75],'gold_lit',{rotation:skew,faces:{north:'fish_skin',south:'fish_skin'}});
  m.box('skewered_fish_head',[9.65,19.1,6.8],[11.75,19.9,7.6],'ivory',{rotation:skew});
  m.box('skewered_fish_tail_left',[9.35,15.95,6.7],[10.6,17.1,7.7],'gold',{rotation:skew});
  m.box('skewered_fish_tail_right',[10.8,15.95,6.7],[12.05,17.1,7.7],'gold',{rotation:skew});
  m.box('skewered_fish_eye',[10.1,19.27,6.58],[10.5,19.65,6.82],'ink',{rotation:skew});
  // Two small angular steam curls establish heat without obscuring the food.
  for(const[x,z,y]of[[4.6,9.6,17],[7,4.7,18.5]]){
    m.box('steam_lower_'+x,[x,y,z],[x+.3,y+1.5,z+.3],'ivory',{shade:false});
    m.box('steam_cross_'+x,[x,y+1.3,z],[x+1.2,y+1.6,z+.3],'ivory',{shade:false});
    m.box('steam_upper_'+x,[x+.9,y+1.6,z],[x+1.2,y+2.8,z+.3],'mint_lit',{shade:false});
  }
  paw(m,'cauldron_front_cast_paw',8,10.15,.25,.73,'gold');
  display(m,.51);m.save();return m;
}

for(const create of[gachaMachine,stockBoard,dungeonObelisk,boonAltar,feastCauldron]){
  const m=create();console.log(`${m.id}: ${m.elements.length} named cuboids, 128x128 RGBA atlas`);
}
