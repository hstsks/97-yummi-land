import { Model } from '../tools/asset-kit.mjs';

// Minecraft Java 1.21.4 cuboids: the editable source of all five Priority 2 models.
// Front is north (-Z). Every round form is deliberately stepped, never a mesh export.
const C = {
  ink:'#29233F', gold:'#E9B94F', glint:'#FFF1B3', goldShade:'#A96C30',
  teal:'#48C8C6', tealShade:'#217E92', pink:'#EA8FB8', pinkShade:'#B85187',
  blue:'#548ED5', darkBlue:'#375B9F', paleBlue:'#A5D5E9', cream:'#FFF0D2',
  paper:'#F2DEB4', plum:'#574078', stitch:'#D1B7B6', white:'#FFFDF4'
};

function setup(id, names) {
  const m = new Model(id,64);
  for (const name of names) {
    const cloth=(id==='plushie_yuumi'&&['blue','darkBlue','paleBlue','cream'].includes(name))||['pink','pinkShade'].includes(name);
    m.swatch(name,C[name],{noise:false,kind:cloth?'cloth':undefined});
    // Restrained two-pixel reflection bands distinguish warm brass from soft cloth.
    if(['gold','goldShade'].includes(name)){
      const {x,y}=m.swatches[name];
      m.canvas.line(x+3,y+2,x+3,y+9,name==='gold'?C.glint:C.gold);
      m.canvas.line(x+4,y+2,x+8,y+2,name==='gold'?C.glint:C.gold);
      m.canvas.line(x+12,y+7,x+12,y+14,C.goldShade);
    }
  }
  m.display = {
    gui:{rotation:[20,35,0],translation:[0,0,0],scale:[0.8,0.8,0.8]},
    ground:{rotation:[0,0,0],translation:[0,3,0],scale:[0.5,0.5,0.5]},
    fixed:{rotation:[0,180,0],translation:[0,0,0],scale:[0.75,0.75,0.75]},
    head:{rotation:[0,180,0],translation:[0,0,0],scale:[1,1,1]},
    thirdperson_righthand:{rotation:[0,-90,0],translation:[0,2,0],scale:[0.6,0.6,0.6]},
    thirdperson_lefthand:{rotation:[0,90,0],translation:[0,2,0],scale:[0.6,0.6,0.6]},
    firstperson_righthand:{rotation:[0,-90,15],translation:[0,2,0],scale:[0.65,0.65,0.65]},
    firstperson_lefthand:{rotation:[0,90,-15],translation:[0,2,0],scale:[0.65,0.65,0.65]}
  };
  return m;
}
const b=(m,n,f,t,tex,rotation)=>m.box(n,f,t,tex,rotation?{rotation}:{});
const rot=(axis,angle,origin)=>({axis,angle,origin});

// An octagonal solid with axis-aligned stepped shoulders; silhouette reads round in game.
function octagon(m,name,cx,y,cz,width,height,depth,tex) {
  const inset=Math.min(width,depth)*0.19;
  b(m,name+'_core',[cx-width/2+inset,y,cz-depth/2],[cx+width/2-inset,y+height,cz+depth/2],tex);
  b(m,name+'_left',[cx-width/2,y,cz-depth/2+inset],[cx-width/2+inset,y+height,cz+depth/2-inset],tex);
  b(m,name+'_right',[cx+width/2-inset,y,cz-depth/2+inset],[cx+width/2,y+height,cz+depth/2-inset],tex);
}
function jewel(m,name,x,y,z,size=1) {
  b(m,name+'_setting',[x-size*.75,y-size*.75,z],[x+size*.75,y+size*.75,z+.35],'gold',rot('z',45,[x,y,z]));
  b(m,name+'_gem',[x-size*.51,y-size*.51,z-.15],[x+size*.51,y+size*.51,z+.05],'teal',rot('z',45,[x,y,z]));
  b(m,name+'_facet',[x-size*.15,y+size*.18,z-.20],[x+size*.12,y+size*.48,z-.14],'glint');
}

function wand() {
  const m=setup('prowling_wand',['ink','gold','glint','goldShade','teal','tealShade','pink','pinkShade','cream']);
  b(m,'dark_wood_shaft',[7.25,-3,7.25],[8.75,13.5,8.75],'tealShade');
  b(m,'mint_front_inlay',[7.55,-1.8,7.12],[8.45,12.7,7.28],'teal');
  // The wrap turns around all four faces: a visible helix instead of flat painted stripes.
  for(let i=0;i<6;i++){
    const y=-1.5+i*2.1;
    b(m,`ribbon_front_${i}`,[7.10,y,7.05],[8.90,y+.42,7.24],'pink',rot('z',22.5,[8,y+.2,7.15]));
    b(m,`ribbon_east_${i}`,[8.72,y+.50,7.10],[8.95,y+.92,8.90],'pink',rot('x',22.5,[8.85,y+.7,8]));
    b(m,`ribbon_back_${i}`,[7.10,y+1.00,8.76],[8.90,y+1.42,8.95],'pinkShade',rot('z',-22.5,[8,y+1.2,8.85]));
    b(m,`ribbon_west_${i}`,[7.05,y+1.50,7.10],[7.28,y+1.92,8.90],'pinkShade',rot('x',-22.5,[7.15,y+1.7,8]));
  }
  octagon(m,'pommel',8,-3.6,8,2.5,.9,2.5,'gold');
  octagon(m,'neck_socket',8,12.4,8,3.3,1.15,3.3,'gold');
  b(m,'socket_glint',[6.8,13.3,6.75],[9.2,13.55,7.02],'glint');
  // Cat paw: three stepped palm lobes and four separate plump toes.
  octagon(m,'paw_palm',8,13.4,8,5.3,2.7,2.5,'pinkShade');
  b(m,'paw_upper_bridge',[6.0,15.7,7.45],[10.0,17.65,8.80],'pinkShade');
  b(m,'paw_pad_center',[6.2,14.0,6.45],[9.8,16.9,7.15],'pink');
  b(m,'paw_pad_lower',[6.8,13.6,6.50],[9.2,14.2,7.1],'pink');
  for(const [i,x,y,w] of [[0,4.95,16.1,1.7],[1,6.95,17.3,1.8],[2,9.05,17.3,1.8],[3,11.05,16.1,1.7]]){
    octagon(m,`toe_${i}`,x,y,7.75,w,2.15,2.25,'pink');
    b(m,`toe_gloss_${i}`,[x-w*.21,y+1.45,6.58],[x+w*.20,y+1.79,6.68],'cream');
  }
  b(m,'palm_gloss',[6.8,15.7,6.38],[8.0,16.15,6.48],'cream');
  // Gold star floats clear of the paw. Cross and 45-degree rhombus create eight rays.
  b(m,'star_four_rays',[7.48,20.1,7.35],[8.52,23.9,8.65],'gold');
  b(m,'star_lateral_rays',[6.1,21.48,7.35],[9.9,22.52,8.65],'gold');
  b(m,'star_diagonal_core',[6.88,20.88,7.30],[9.12,23.12,8.70],'goldShade',rot('z',45,[8,22,8]));
  b(m,'star_luminous_face',[7.35,21.35,7.18],[8.65,22.65,7.35],'glint',rot('z',45,[8,22,7.26]));
  b(m,'left_magic_spark',[4.8,21.2,7.7],[5.4,21.8,8.3],'teal',rot('z',45,[5.1,21.5,8]));
  b(m,'right_magic_spark',[10.7,19.9,7.7],[11.2,20.4,8.3],'glint',rot('z',45,[10.95,20.15,8]));
  m.display.gui={rotation:[0,0,-24],translation:[0,-2,0],scale:[.64,.64,.64]};
  m.display.ground={rotation:[0,0,0],translation:[0,6,0],scale:[.45,.45,.45]};
  for(const key of ['thirdperson_righthand','thirdperson_lefthand'])m.display[key]={rotation:[0,0,-10],translation:[0,3.5,0],scale:[.6,.6,.6]};
  m.display.firstperson_righthand={rotation:[0,-15,-12],translation:[2,-1,-2],scale:[.65,.65,.65]};
  m.display.firstperson_lefthand={rotation:[0,15,12],translation:[2,-1,-2],scale:[.65,.65,.65]};
  return m;
}

function book(){
  const m=setup('book_of_thresholds',['ink','gold','glint','goldShade','teal','tealShade','pink','cream','paper','plum']);
  m.swatch('embossed_cover',C.tealShade,{noise:false,kind:'cloth'});
  {
    const {x,y}=m.swatches.embossed_cover;
    m.canvas.rect(x+1,y+1,14,1,C.gold).rect(x+1,y+14,14,1,C.gold);
    m.canvas.rect(x+1,y+1,1,14,C.gold).rect(x+14,y+1,1,14,C.gold);
    for(const xx of [3,10])for(const yy of [3,10]){
      m.canvas.line(x+xx,y+yy,x+xx+2,y+yy,C.glint);
      m.canvas.line(x+xx+1,y+yy,x+xx+1,y+yy+2,C.glint);
      m.canvas.pixel(x+xx+2,y+yy+2,C.teal);
    }
  }
  m.swatch('illuminated_folio',C.cream,{noise:false});
  {
    const {x,y}=m.swatches.illuminated_folio;
    m.canvas.rect(x+2,y+2,12,1,C.gold).rect(x+2,y+13,12,1,C.gold);
    for(let yy=4;yy<12;yy+=3){
      m.canvas.line(x+3,y+yy,x+6,y+yy,C.tealShade);
      m.canvas.line(x+8,y+yy,x+12,y+yy,C.goldShade);
      m.canvas.pixel(x+4,y+yy+1,C.tealShade).pixel(x+10,y+yy-1,C.goldShade);
    }
  }
  // Closed back cover, thick sewn spine, then a cover and folio layers gently opened toward the reader.
  b(m,'back_cover',[2.2,1.2,8.2],[13.8,15.0,9.25],'tealShade');
  b(m,'back_cover_gold_border',[2.0,1.0,8.3],[14.0,1.55,9.4],'gold');
  // Raise and inset the spine cap relative to both covers: no coplanar top surfaces.
  b(m,'spine',[1.8,1.04,5.0],[3.2,15.16,9.18],'plum');
  for(const y of [2.0,5.0,10.7,13.8])b(m,`spine_raised_rib_${y}`,[1.65,y,4.9],[3.35,y+.55,9.4],'gold');
  b(m,'page_block',[3.15,1.65,5.5],[13.35,14.55,8.15],'paper');
  for(let i=0;i<5;i++)b(m,`foreedge_page_${i}`,[13.34,1.9+i*2.5,5.55],[13.53,2.08+i*2.5,8.13],'cream');
  for(let i=0;i<5;i++)b(m,`bottom_page_${i}`,[3.3,1.6,5.65+i*.46],[13.25,1.78,5.8+i*.46],'cream');
  // A deliberate 22.5-degree opening on the spine hinge, all furniture belongs to that cover.
  const hinge=[2.75,8,5.4], opening=rot('y',22.5,hinge);
  b(m,'open_front_cover',[2.5,1.1,4.50],[14.05,15.05,5.40],'plum',opening);
  b(m,'front_inset_panel',[3.25,2.05,4.30],[13.1,14.05,4.55],'tealShade',opening);
  b(m,'front_leather_center',[4.05,2.85,4.21],[12.3,13.25,4.36],'embossed_cover',opening);
  for(const [name,f,t] of [
    ['cover_left_binding',[2.5,1.05,4.25],[3.2,15.1,4.65]],
    ['cover_right_binding',[13.35,1.05,4.25],[14.1,15.1,4.65]],
    ['cover_bottom_binding',[2.5,1.05,4.25],[14.1,1.75,4.65]],
    ['cover_top_binding',[2.5,14.4,4.25],[14.1,15.1,4.65]]]) b(m,name,f,t,'gold',opening);
  for(const x of [3.0,12.2])for(const y of [1.6,12.5]){
    b(m,`corner_plate_${x}_${y}`,[x,y,4.01],[x+1.3,y+1.9,4.30],'gold',opening);
    b(m,`corner_rivet_${x}_${y}`,[x+.42,y+.65,3.90],[x+.84,y+1.05,4.04],'glint',opening);
  }
  b(m,'central_jewel_surround',[6.15,5.6,3.85],[10.15,10.6,4.22],'gold',opening);
  b(m,'central_jewel_dark_bevel',[6.65,6.0,3.55],[9.65,10.15,3.88],'tealShade',opening);
  b(m,'central_jewel',[7.05,6.5,3.30],[9.25,9.7,3.62],'teal',opening);
  b(m,'jewel_top_facet',[7.15,9.05,3.22],[8.1,9.6,3.34],'glint',opening);
  // Fanned loose folios reveal gold runes in the narrow opening, separate from the cover surface.
  for(let i=0;i<3;i++){
    const angle=i===0?0:22.5;
    const r=angle?rot('y',angle,[3.05,8,5.8+i*.36]):undefined;
    m.box(`loose_folio_${i}`,[3.05,2.0+i*.08,5.62+i*.36],[13.10-i*.32,14.25-i*.16,5.81+i*.36],'cream',{rotation:r,faces:{north:'illuminated_folio'}});
  }
  // Brass clasps and a silk bookmark establish a real object rather than a single box.
  b(m,'right_clasp_base',[13.35,7.15,6.1],[14.25,8.8,8.6],'gold');
  b(m,'clasp_tip',[13.65,7.55,5.4],[14.3,8.4,6.3],'glint');
  b(m,'silk_bookmark',[10.6,-.25,6.4],[11.65,2.0,6.65],'pink');
  b(m,'bookmark_end_left',[10.6,-.8,6.4],[10.98,-.2,6.65],'pink');
  b(m,'bookmark_end_right',[11.27,-.8,6.4],[11.65,-.2,6.65],'pink');
  for(let i=0;i<4;i++){
    const yy=3.6+i*2.1;
    b(m,`spine_rune_bar_${i}`,[1.54,yy,6.3],[1.7,yy+.3,7.6],'glint');
    b(m,`spine_rune_stem_${i}`,[1.54,yy-.3,6.75],[1.7,yy+.65,7.05],'glint');
  }
  m.display.gui={rotation:[18,-35,-7],translation:[0,0,0],scale:[.84,.84,.84]};
  m.display.fixed={rotation:[0,180,0],translation:[0,0,1],scale:[.8,.8,.8]};
  m.display.firstperson_righthand={rotation:[0,-25,8],translation:[0,0,-2],scale:[.6,.6,.6]};
  m.display.firstperson_lefthand={rotation:[0,25,-8],translation:[0,0,-2],scale:[.6,.6,.6]};
  return m;
}

function bell(){
  const m=setup('you_and_me_bell',['ink','gold','glint','goldShade','teal','pink','pinkShade','cream']);
  // Horizontal stepped cross sections keep the golden bell round from all directions.
  const sections=[[2.9,3.5,3.4],[3.5,4.5,5.8],[4.5,5.8,7.5],[5.8,7.8,8.3],[7.8,9.1,7.5],[9.1,10.1,5.8],[10.1,10.7,3.4]];
  for(const [i,[lo,hi,w]] of sections.entries()) octagon(m,`bell_shell_${i}`,8,lo,8,w,hi-lo,w,i<2?'goldShade':'gold');
  // The lower slit and two dark terminals are shallow inlaid geometry with brass edges.
  b(m,'sound_slit',[5.65,5.35,4.18],[10.35,5.62,4.31],'ink');
  b(m,'slit_left_hole',[5.55,4.9,4.17],[6.1,5.8,4.33],'ink');
  b(m,'slit_right_hole',[9.9,4.9,4.17],[10.45,5.8,4.33],'ink');
  b(m,'slit_bottom_glint',[6.1,5.03,4.16],[9.9,5.22,4.30],'glint');
  b(m,'curved_highlight_tall',[5.55,6.3,3.78],[5.95,7.7,3.95],'glint');
  b(m,'curved_highlight_top',[5.85,8.3,4.23],[7.3,8.65,4.41],'glint');
  b(m,'brass_loop_left',[6.85,10.3,7.45],[7.4,12.5,8.55],'goldShade');
  b(m,'brass_loop_right',[8.6,10.3,7.45],[9.15,12.5,8.55],'gold');
  b(m,'brass_loop_top',[7.4,12.1,7.45],[8.6,12.65,8.55],'glint');
  // Folded ribbons are asymmetric in depth and have visible dark interior folds.
  b(m,'left_bow_loop',[2.8,12.2,7.0],[6.9,15.3,8.3],'pink',rot('z',-22.5,[6.9,13.6,7.65]));
  b(m,'left_bow_fold',[3.25,12.8,6.88],[6.55,14.4,7.05],'pinkShade',rot('z',-22.5,[6.9,13.6,7.65]));
  b(m,'right_bow_loop',[9.1,12.2,7.0],[13.2,15.3,8.3],'pink',rot('z',22.5,[9.1,13.6,7.65]));
  b(m,'right_bow_fold',[9.45,12.8,6.88],[12.75,14.4,7.05],'pinkShade',rot('z',22.5,[9.1,13.6,7.65]));
  b(m,'bow_knot',[6.7,12.1,6.55],[9.3,14.7,8.65],'pinkShade');
  b(m,'knot_front',[7.2,12.4,6.30],[8.8,14.4,6.60],'pink');
  b(m,'knot_gloss',[7.3,13.9,6.21],[8.25,14.13,6.34],'cream');
  b(m,'ribbon_tail_left',[4.7,8.8,8.75],[6.0,12.8,9.1],'pink',rot('z',-22.5,[5.4,12.5,8.9]));
  b(m,'ribbon_tail_right',[10,8.7,8.7],[11.35,12.8,9.05],'pinkShade',rot('z',22.5,[10.7,12.5,8.9]));
  jewel(m,'cat_bond_gem',8,7.65,3.7,.8);
  m.display.gui={rotation:[10,-15,0],translation:[0,-.5,0],scale:[.95,.95,.95]};
  m.display.ground={rotation:[0,0,0],translation:[0,2.5,0],scale:[.5,.5,.5]};
  return m;
}

function steppedEar(m,name,x,y,z,side=1){
  const layers=[{w:4.8,h:1.2},{w:4.1,h:1.2},{w:3.3,h:1.2},{w:2.4,h:1.2},{w:1.5,h:1.0},{w:.65,h:.7}];
  let yy=y;
  for(let i=0;i<layers.length;i++){
    const {w,h}=layers[i],cx=x+side*i*.23;
    b(m,`${name}_fur_${i}`,[cx-w/2,yy,z],[cx+w/2,yy+h,z+2.0],'blue');
    if(i<5){const inner=Math.max(.35,w-1.1);b(m,`${name}_inner_${i}`,[cx-inner/2,yy+.08,z-.12],[cx+inner/2,yy+h-.08,z+.05],i<2?'pinkShade':'pink');}
    yy+=h;
  }
}
function hat(){
  const m=setup('hat_yuumi_ears',['ink','gold','glint','goldShade','teal','pink','pinkShade','blue','darkBlue','paleBlue','cream']);
  // HEAD transform matches the standard 0.625 head-item render scale.
  // The opening spans 13.8 units (0.539 blocks), clearing an 8-pixel head plus its skin overlay.
  b(m,'band_front',[.3,13.7,.3],[15.7,14.6,1.1],'darkBlue');
  b(m,'band_back',[.3,13.7,14.9],[15.7,14.6,15.7],'darkBlue');
  b(m,'band_left',[.3,13.7,1.1],[1.1,14.6,14.9],'darkBlue');
  b(m,'band_right',[14.9,13.7,1.1],[15.7,14.6,14.9],'darkBlue');
  b(m,'band_front_gold_seam',[.45,14.4,.20],[15.55,14.63,.42],'gold');
  b(m,'band_front_silk',[2,13.85,.18],[14,14.2,.38],'teal');
  steppedEar(m,'left_ear',2.8,14.5,5.9,-1);
  steppedEar(m,'right_ear',13.2,14.5,5.9,1);
  b(m,'left_ear_base_tuft',[.8,14.4,4.9],[4.5,15.1,6.0],'paleBlue');
  b(m,'right_ear_base_tuft',[11.5,14.4,4.9],[15.2,15.1,6.0],'paleBlue');
  b(m,'forehead_setting',[7.1,13.3,.3],[8.9,15.2,1.5],'gold',rot('z',45,[8,14.25,.9]));
  b(m,'forehead_teal',[7.55,13.7,.13],[8.45,14.8,.38],'teal');
  b(m,'antenna_lower',[7.7,15.1,2.0],[8.3,17.8,2.7],'gold',rot('z',-22.5,[8,15.1,2.35]));
  b(m,'antenna_upper',[8.3,17.5,2.0],[10.2,18.0,2.7],'gold');
  octagon(m,'antenna_bell',10.15,17.35,2.35,1.8,1.8,1.8,'gold');
  b(m,'bell_glint',[9.7,18.55,1.41],[10.18,18.92,1.55],'glint');
  b(m,'bell_slit',[9.52,17.72,1.40],[10.78,17.95,1.56],'goldShade');
  m.display.head={rotation:[0,180,0],translation:[0,0,0],scale:[1,1,1]};
  m.display.gui={rotation:[20,35,0],translation:[0,-5,0],scale:[.8,.8,.8]};
  m.display.ground={rotation:[0,0,0],translation:[0,-2,0],scale:[.5,.5,.5]};
  m.display.fixed={rotation:[0,180,0],translation:[0,-5,0],scale:[.8,.8,.8]};
  return m;
}

function plushie(){
  const m=setup('plushie_yuumi',['ink','gold','glint','goldShade','teal','pink','pinkShade','blue','darkBlue','paleBlue','cream','stitch','white']);
  // Padded loaf body has inset top/bottom sections and a broad belly, with folded feet beneath.
  octagon(m,'loaf_lower',8,1.4,9.1,10.4,2.0,11.4,'paleBlue');
  octagon(m,'loaf_body',8,3.4,9.1,11.3,3.5,12.4,'blue');
  octagon(m,'loaf_upper',8,6.9,9.5,9.6,1.6,10.2,'blue');
  octagon(m,'back_cushion',8,8.5,10.1,7.9,.8,7.7,'paleBlue');
  b(m,'left_tucked_paw',[3.2,1.2,2.9],[6.25,2.9,6.3],'cream');
  b(m,'right_tucked_paw',[9.75,1.2,2.9],[12.8,2.9,6.3],'cream');
  for(const x of [4.1,5.2,10.7,11.8])b(m,`paw_stitch_${x}`,[x,1.55,2.81],[x+.13,2.25,2.99],'stitch');
  // Folded tail is a curved stepped silhouette wrapped around the flank, with a pale tip.
  b(m,'tail_root',[12.0,3.2,11.2],[14.0,5.6,14.1],'darkBlue');
  b(m,'tail_side',[13.0,2.2,6.7],[15.0,4.8,12.1],'blue');
  b(m,'tail_turn',[12.4,1.9,4.4],[14.7,4.3,7.4],'paleBlue');
  b(m,'tail_cream_tip',[10.9,1.8,3.8],[13.6,3.8,5.5],'cream');
  // Head and cheeks are rounded with three octagonal horizontal profiles.
  octagon(m,'head_lower',8,5.1,4.65,8.4,1.4,6.3,'cream');
  octagon(m,'head_main',8,6.5,4.65,10.1,4.1,7.3,'blue');
  octagon(m,'head_crown',8,10.6,4.9,8.9,1.6,6.6,'blue');
  octagon(m,'head_top',8,12.2,5.1,6.8,.65,5.3,'paleBlue');
  // White cheek masks remain distinct from the blue central bridge.
  b(m,'left_cheek_mask',[3.22,6.8,.91],[6.55,8.85,1.13],'cream');
  b(m,'right_cheek_mask',[9.45,6.8,.91],[12.78,8.85,1.13],'cream');
  b(m,'left_muzzle',[5.5,5.9,1.2],[8.1,7.55,1.65],'cream');
  b(m,'right_muzzle',[7.9,5.9,1.2],[10.5,7.55,1.65],'cream');
  b(m,'chin',[6.5,5.4,1.4],[9.5,6.3,1.75],'cream');
  // Embroidered large eyes with staggered corners, colored irises and paired catchlights.
  for(const [name,cx] of [['left',5.35],['right',10.65]]){
    b(m,`${name}_eye_outer`,[cx-1.25,8.15,.78],[cx+1.25,10.5,1.02],'ink');
    b(m,`${name}_eye_top`,[cx-.8,10.5,.78],[cx+.8,10.85,1.02],'ink');
    b(m,`${name}_eye_iris`,[cx-.85,8.4,.65],[cx+.85,10.35,.83],'teal');
    b(m,`${name}_eye_pupil`,[cx-.30,8.5,.52],[cx+.33,10.25,.69],'ink');
    b(m,`${name}_eye_glint`,[cx-.58,9.8,.43],[cx-.05,10.3,.58],'white');
    b(m,`${name}_eye_small_glint`,[cx+.40,8.7,.47],[cx+.64,8.98,.61],'glint');
  }
  b(m,'nose',[7.35,7.1,.84],[8.65,7.65,1.32],'pinkShade');
  b(m,'nose_lower',[7.65,6.85,.84],[8.35,7.15,1.32],'pinkShade');
  b(m,'nose_gloss',[7.5,7.5,.75],[8.1,7.66,.92],'pink');
  b(m,'mouth_vertical',[7.9,6.35,1.06],[8.1,6.95,1.26],'ink');
  b(m,'mouth_left',[7.15,6.25,1.06],[7.95,6.45,1.26],'ink');
  b(m,'mouth_right',[8.05,6.25,1.06],[8.85,6.45,1.26],'ink');
  for(const side of [-1,1]){
    const x=8+side*4.2;
    b(m,`whisker_upper_${side}`,[x-1.3,7.2,.7],[x+1.3,7.36,.9],'gold',rot('z',side*22.5,[x,7.3,.8]));
    b(m,`whisker_lower_${side}`,[x-1.2,6.8,.8],[x+1.2,6.96,1.0],'goldShade',rot('z',side*-22.5,[x,6.9,.9]));
  }
  steppedEar(m,'left_ear',4.0,11.8,4.1,-1);
  steppedEar(m,'right_ear',12.0,11.8,4.1,1);
  // Collar and tiny forehead charm carry the base champion's gold/blue contrast.
  b(m,'gold_collar_front',[4.5,4.8,2.9],[11.5,5.55,3.3],'gold');
  b(m,'collar_left',[3.6,4.8,3.2],[4.6,5.55,7.6],'goldShade');
  b(m,'collar_right',[11.4,4.8,3.2],[12.4,5.55,7.6],'gold');
  octagon(m,'collar_bell',8,3.6,2.7,1.6,1.6,1.7,'gold');
  b(m,'collar_bell_slit',[7.38,4.05,1.82],[8.62,4.22,2.03],'ink');
  jewel(m,'forehead_crest',8,11.35,1.2,.9);
  b(m,'forehead_antenna',[7.7,12.15,3.4],[8.3,14.9,4.1],'gold',rot('z',-22.5,[8,12.15,3.75]));
  b(m,'antenna_turn',[8.5,14.5,3.4],[9.9,15.0,4.1],'gold');
  octagon(m,'antenna_glowing_tip',10.0,14.1,3.75,1.5,1.5,1.5,'glint');
  // Embroidery is sparse and intentional, placed on the back seam rather than noisy fur dots.
  for(let i=0;i<5;i++)b(m,`back_seam_${i}`,[7.91,4.1+i*.65,15.21],[8.09,4.42+i*.65,15.38],'stitch');
  m.display.gui={rotation:[16,-25,0],translation:[0,-1,0],scale:[.76,.76,.76]};
  m.display.ground={rotation:[0,0,0],translation:[0,2.3,0],scale:[.55,.55,.55]};
  m.display.fixed={rotation:[0,180,0],translation:[0,-1,-2.5],scale:[.75,.75,.75]};
  m.display.thirdperson_righthand={rotation:[0,0,0],translation:[0,2,0],scale:[.5,.5,.5]};
  m.display.thirdperson_lefthand={rotation:[0,0,0],translation:[0,2,0],scale:[.5,.5,.5]};
  return m;
}

for(const build of [wand,book,bell,hat,plushie]){
  const model=build();
  await model.save();
  console.log(`${model.id}: ${model.elements.length} editable cuboids`);
}
