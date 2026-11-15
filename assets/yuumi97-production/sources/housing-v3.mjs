import { Model, shade } from '../tools/asset-kit.mjs';

// V3 housing and hats. Named vanilla cuboids; front = north (-Z).
// Furniture bases touch Y=0 at a footprint centered on [8, 0, 8].
// Hats retain the V2 headband transform and 13.8-unit interior opening.
const C = {
  ink:'#29233F', slate:'#383C53', slateLight:'#555B72', wood:'#B98051', woodLight:'#E2B784', woodDark:'#765239',
  rope:'#D3B78A', ropeShade:'#AC8D64', pink:'#EA8FB8', pinkLight:'#FFD1DE', pinkShade:'#B85187',
  cream:'#FFF0D2', white:'#FFFDF4', whiteShade:'#DBDAE5', gold:'#E9B94F', goldShade:'#A96C30', glint:'#FFF1B3',
  teal:'#48C8C6', tealShade:'#217E92', green:'#77AC60', greenShade:'#3D7552', orange:'#F39636', orangeShade:'#C45F2C',
  red:'#BE405D', redShade:'#7F2E4B', black:'#242338', blackLight:'#43465A', blackShade:'#151827', hotPink:'#FF64B6', cyan:'#72F4E9'
};
const rot=(axis,angle,origin)=>({axis,angle,origin});
const b=(m,name,from,to,tex,rotation)=>m.box(name,from,to,tex,rotation?{rotation}:{});

function setup(id,size,names){
  const m=new Model(id,size);
  for(const name of names){
    const kind=name.startsWith('wood')?'wood':name.startsWith('pink')||name==='cream'||name.startsWith('white')?'cloth':undefined;
    m.swatch(name,C[name],{noise:false,kind});
    const {x,y}=m.swatches[name];
    if(name==='gold'||name==='goldShade'){
      m.canvas.line(x+2,y+2,x+12,y+2,C.glint).line(x+2,y+3,x+2,y+10,C.glint);
      m.canvas.line(x+12,y+7,x+12,y+13,C.goldShade);
    }
    if(name==='rope'||name==='ropeShade'){
      for(let yy=1;yy<16;yy+=3)m.canvas.line(x,y+yy,x+15,y+Math.min(15,yy+2),C.ropeShade);
    }
  }
  return m;
}

function oct(m,name,cx,y,cz,w,h,d,tex){
  const cut=Math.min(w,d)*.19;
  b(m,name+'_middle',[cx-w/2+cut,y,cz-d/2],[cx+w/2-cut,y+h,cz+d/2],tex);
  b(m,name+'_west',[cx-w/2,y,cz-d/2+cut],[cx-w/2+cut,y+h,cz+d/2-cut],tex);
  b(m,name+'_east',[cx+w/2-cut,y,cz-d/2+cut],[cx+w/2,y+h,cz+d/2-cut],tex);
}
function ring(m,name,x0,y,z0,x1,h,z1,t,tex){
  b(m,name+'_north',[x0,y,z0],[x1,y+h,z0+t],tex);
  b(m,name+'_south',[x0,y,z1-t],[x1,y+h,z1],tex);
  b(m,name+'_west',[x0,y,z0+t],[x0+t,y+h,z1-t],tex);
  b(m,name+'_east',[x1-t,y,z0+t],[x1,y+h,z1-t],tex);
}
function bell(m,name,cx,y,cz,s=1){
  oct(m,name+'_waist',cx,y,cz,1.9*s,.5*s,1.9*s,'goldShade');
  oct(m,name+'_bowl',cx,y+.45*s,cz,2.3*s,1.1*s,2.3*s,'gold');
  oct(m,name+'_shoulder',cx,y+1.55*s,cz,1.55*s,.5*s,1.55*s,'gold');
  b(m,name+'_loop',[cx-.26*s,y+1.95*s,cz-.21*s],[cx+.26*s,y+2.65*s,cz+.21*s],'goldShade');
  b(m,name+'_mouth',[cx-.76*s,y+.65*s,cz-1.17*s],[cx+.76*s,y+.86*s,cz-1.10*s],'ink');
  b(m,name+'_glint',[cx-.6*s,y+1.3*s,cz-1.18*s],[cx-.2*s,y+1.67*s,cz-1.09*s],'glint');
}
function pawFront(m,name,x,y,z,s,tex){
  b(m,name+'_pad',[x-.85*s,y,z],[x+.85*s,y+1.1*s,z+.18*s],tex);
  for(const [i,dx,dy] of [[0,-1.2,.9],[1,-.46,1.45],[2,.46,1.45],[3,1.2,.9]])
    b(m,name+'_toe_'+i,[x+(dx-.3)*s,y+dy*s,z],[x+(dx+.3)*s,y+(dy+.64)*s,z+.18*s],tex);
}
function floorDisplay(m,scale=.6){
  m.display.gui={rotation:[20,-35,0],translation:[0,-3,0],scale:[scale,scale,scale]};
  m.display.ground={rotation:[0,0,0],translation:[0,3,0],scale:[.4,.4,.4]};
  m.display.fixed={rotation:[0,180,0],translation:[0,-3,0],scale:[.6,.6,.6]};
}
function ear(m,name,cx,y,z,side,outer,inner){
  const layers=[[4.6,1.1],[3.9,1.15],[3.1,1.1],[2.3,1.0],[1.4,.85],[.6,.55]];
  let yy=y;
  for(const [i,[w,h]] of layers.entries()){
    const x=cx+side*i*.20;
    b(m,name+'_outer_'+i,[x-w/2,yy,z],[x+w/2,yy+h,z+1.8],outer);
    if(i<5){const wi=Math.max(.35,w-1.1);b(m,name+'_inner_'+i,[x-wi/2,yy+.1,z-.12],[x+wi/2,yy+h-.1,z+.05],inner);}
    yy+=h;
  }
}

function catTower(){
  const m=setup('furn_cat_tower',128,['ink','wood','woodLight','woodDark','rope','ropeShade','pink','pinkLight','pinkShade','cream','gold','goldShade','glint','teal']);
  oct(m,'weighted_wooden_plinth',8,0,8,19,1.25,19,'woodDark');
  oct(m,'plinth_roundover',8,1.25,8,18.6,.45,18.6,'woodLight');
  // Real accessible hollow hideaway: open front, separate walls and a cushion inside.
  b(m,'hideaway_floor',[.8,1.7,1],[15.2,2.15,15.0],'wood');
  b(m,'hideaway_back',[.8,2.15,13.9],[15.2,10.15,15.0],'wood');
  b(m,'hideaway_left',[.8,2.15,1],[2.0,10.15,13.9],'wood');
  b(m,'hideaway_right',[14.0,2.15,1],[15.2,10.15,13.9],'wood');
  b(m,'den_inner_shadow',[2.05,2.19,13.72],[13.95,9.65,13.92],'woodDark');
  oct(m,'den_cushion_piping',8,2.15,8.5,10.5,.35,9.0,'pinkShade');
  oct(m,'den_cushion_loft',8,2.5,8.5,10.2,.75,8.7,'pink');
  oct(m,'den_cushion_top',8,3.25,8.5,8.7,.2,7.3,'pinkLight');
  // The entrance has stepped cat ears above its open mouth; eye and whisker inlays flank it.
  b(m,'cat_face_left_cheek',[1.5,2.15,.65],[4.1,8.45,1.35],'woodLight');
  b(m,'cat_face_right_cheek',[11.9,2.15,.65],[14.5,8.45,1.35],'woodLight');
  b(m,'cat_face_top',[1.5,8.45,.65],[14.5,10.0,1.35],'woodLight');
  b(m,'cat_ear_left_inner_step',[4.1,6.8,.65],[5.2,8.45,1.35],'woodLight');
  b(m,'cat_ear_right_inner_step',[10.8,6.8,.65],[11.9,8.45,1.35],'woodLight');
  b(m,'entrance_brow_bridge',[6.2,7.7,.65],[9.8,8.45,1.35],'woodLight');
  for(const x of [2.6,13.4]){
    b(m,'cat_eye_'+x,[x-.25,6.55,.50],[x+.25,7.35,.67],'ink');
    for(const yy of [4.5,5.2])b(m,'cat_whisker_'+x+'_'+yy,[x-.8,yy,.49],[x+.8,yy+.16,.65],'woodDark');
  }
  oct(m,'hideaway_roof_rim',8,10.0,8,15.8,.65,15.3,'woodDark');
  oct(m,'hideaway_roof_pink_cushion',8,10.65,8,15.3,.65,14.8,'pink');
  oct(m,'hideaway_roof_soft_top',8,11.3,8,13.8,.25,13.3,'pinkLight');
  // Front-right and rear-left scratching posts with separately modeled rope turns.
  for(const [index,x,z,y0,y1] of [[0,3.5,10.8,11.55,18],[1,12.6,10.8,11.55,18],[2,8,10.5,19.55,25.4]]){
    b(m,'scratcher_core_'+index,[x-1.05,y0,z-1.05],[x+1.05,y1,z+1.05],'woodDark');
    oct(m,'scratcher_foot_'+index,x,y0,z,2.75,.35,2.75,'woodLight');
    let k=0;
    for(let y=y0+.4;y<y1-.3;y+=.55){
      ring(m,'rope_wrap_'+index+'_'+k,x-1.14,y,z-1.14,x+1.14,.38,z+1.14,.27,k%3===0?'ropeShade':'rope');k++;
    }
    oct(m,'scratcher_cap_'+index,x,y1-.3,z,2.8,.3,2.8,'woodLight');
  }
  oct(m,'second_round_platform',8,18,8,18.2,.85,15.3,'woodDark');
  oct(m,'second_round_gold_seam',8,18.85,8,18.0,.18,15.1,'goldShade');
  oct(m,'second_round_cushion',8,19.03,8,17.7,.52,14.8,'pink');
  oct(m,'second_cushion_center',8,19.55,8,15.7,.18,12.8,'pinkLight');
  // Toy is visibly suspended beneath the front platform, clear of the rear posts.
  b(m,'toy_anchor',[13.6,17.65,2.7],[14.4,18.05,3.5],'gold');
  b(m,'toy_cord',[13.88,15.15,2.98],[14.12,17.65,3.22],'ropeShade');
  bell(m,'hanging_gold_bell',14,13.55,3.1,.64);
  b(m,'toy_ribbon_left',[12.5,15.1,2.7],[13.9,15.65,3.15],'pinkShade',rot('z',-22.5,[14,15.3,3]));
  b(m,'toy_ribbon_right',[14.1,15.1,2.7],[15.5,15.65,3.15],'pink',rot('z',22.5,[14,15.3,3]));
  // Highest perch is an open crown, not a solid cap over its cushion.
  oct(m,'crown_perch_base',8,25.4,8,14.4,.75,12.9,'woodDark');
  oct(m,'crown_perch_lower_trim',8,26.15,8,14.6,.32,13.1,'gold');
  oct(m,'crown_perch_pillow',8,26.47,8,12.9,.7,11.4,'pink');
  oct(m,'crown_perch_pillow_top',8,27.17,8,10.7,.22,9.2,'pinkLight');
  ring(m,'crown_low_rail',.9,26.6,1.65,15.1,.9,14.35,.45,'gold');
  for(const [i,x,z] of [[0,2,2],[1,8,1.8],[2,14,2],[3,2,14],[4,8,14.2],[5,14,14],[6,1.2,8],[7,14.8,8]]){
    b(m,'crown_point_stem_'+i,[x-.4,27.25,z-.4],[x+.4,29.0,z+.4],'gold');
    b(m,'crown_point_diamond_'+i,[x-.63,28.5,z-.42],[x+.63,29.76,z+.42],'gold',rot('z',45,[x,29.13,z]));
    b(m,'crown_point_jewel_'+i,[x-.3,28.83,z-.5],[x+.3,29.43,z-.39],i%2?'teal':'pinkShade',rot('z',45,[x,29.13,z]));
  }
  pawFront(m,'front_plinth_paw',8,.18,-1.61,.42,'gold');
  floorDisplay(m,.5);
  return m;
}

function kotatsu(){
  const m=setup('furn_kotatsu',128,['ink','wood','woodLight','woodDark','pink','pinkLight','pinkShade','cream','white','gold','goldShade','glint','teal','tealShade','green','greenShade','orange','orangeShade']);
  // Separate quilts have woven check swatches and shallow raised piping at their sewn seams.
  for(const [name,base,a,c] of [['quilt_pink',C.pink,C.pinkLight,C.cream],['quilt_cream',C.cream,C.pinkLight,C.pink]]){
    m.swatch(name,base,{noise:false,kind:'cloth'});
    const {x,y}=m.swatches[name];
    for(let yy=0;yy<16;yy+=4)for(let xx=0;xx<16;xx+=4){m.canvas.rect(x+xx,y+yy,4,4,((xx+yy)/4)%2?a:c);m.canvas.pixel(x+xx+1,y+yy+1,shade(base,16));}
    for(let q=1;q<16;q+=4)m.canvas.line(x+q,y,x+q,y+15,shade(base,-16));
  }
  // Four feet contact the floor; blanket skirt also touches Y=0 and centers on 8,8.
  for(const x of [1.9,12.3])for(const z of [1.9,12.3]){
    b(m,'table_leg_'+x+'_'+z,[x,0,z],[x+1.8,8.6,z+1.8],'woodDark');
    b(m,'table_foot_'+x+'_'+z,[x-.12,0,z-.12],[x+1.92,.45,z+1.92],'wood');
  }
  b(m,'table_crossframe_x',[2,6.9,6.8],[14,8.3,9.2],'woodDark');
  b(m,'table_crossframe_z',[6.8,6.9,2],[9.2,8.3,14],'woodDark');
  b(m,'quilt_covered_upper_pad',[.7,8.0,.7],[15.3,9.0,15.3],'quilt_cream');
  // Quilts flare out in three overlapping terraced rows; all four faces have visible checks.
  for(const [row,y0,y1,edge] of [[0,0,2.1,-2.5],[1,2.1,5.3,-1.6],[2,5.3,8.4,-.6]]){
    const opp=16-edge;
    b(m,'north_quilt_row_'+row,[edge,y0,edge],[opp,y1,edge+1.15],row===1?'quilt_cream':'quilt_pink');
    b(m,'south_quilt_row_'+row,[edge,y0,opp-1.15],[opp,y1,opp],row===1?'quilt_pink':'quilt_cream');
    b(m,'west_quilt_row_'+row,[edge,y0,edge+1.15],[edge+1.15,y1,opp-1.15],row===1?'quilt_pink':'quilt_cream');
    b(m,'east_quilt_row_'+row,[opp-1.15,y0,edge+1.15],[opp,y1,opp-1.15],row===1?'quilt_cream':'quilt_pink');
    for(const side of [0,1]){
      const zz=side?opp-.04:edge-.10;
      for(let xx=edge+.65;xx<opp-.3;xx+=3.4)b(m,'front_quilt_sewn_fold_'+row+'_'+side+'_'+xx,[xx,y0+.16,zz],[xx+.17,y1-.12,zz+.17],'pinkShade');
    }
  }
  ring(m,'blanket_lower_soft_hem',-2.5,0,-2.5,18.5,.34,18.5,.75,'pinkShade');
  for(const x of [-1.3,2.1,5.5,8.9,12.3,15.7]){
    b(m,'north_hem_stitch_'+x,[x,.11,-2.62],[x+.65,.22,-2.45],'cream');
    b(m,'south_hem_stitch_'+x,[x,.11,18.45],[x+.65,.22,18.62],'cream');
  }
  // Dark under-bevel, wide wood top and narrow inlaid edge remain distinct from quilt.
  b(m,'tabletop_under_bevel',[-.6,8.85,-.6],[16.6,9.15,16.6],'woodDark');
  b(m,'tabletop_wood_slab',[-1,9.15,-1],[17,10.15,17],'wood');
  b(m,'tabletop_warm_top',[-.75,10.15,-.75],[16.75,10.32,16.75],'woodLight');
  ring(m,'tabletop_gold_inlay',-.48,10.32,-.48,16.48,.035,16.48,.12,'goldShade');
  for(const x of [3.65,8.1,12.55])b(m,'top_plank_join_'+x,[x,10.325,-.6],[x+.065,10.36,16.6],'wood');
  // An open woven basket with multiple individual mandarins and green leaf accents.
  oct(m,'fruit_basket_base',7.9,10.37,10.6,7.2,.35,5.7,'woodDark');
  ring(m,'basket_lower_weave',4.55,10.72,7.95,11.25,.65,13.25,.37,'wood');
  ring(m,'basket_upper_weave',4.35,11.37,7.75,11.45,.42,13.45,.38,'woodLight');
  ring(m,'basket_rolled_rim',4.2,11.79,7.6,11.6,.32,13.6,.46,'wood');
  for(let x=4.8;x<11.3;x+=.85){
    b(m,'basket_front_warp_'+x,[x,10.68,7.52],[x+.18,12.1,7.75],'woodLight');
    b(m,'basket_rear_warp_'+x,[x,10.68,13.45],[x+.18,12.1,13.68],'woodLight');
  }
  for(const [i,x,z,y] of [[0,5.6,9.2,11.5],[1,8.15,9.05,11.6],[2,10.3,10.5,11.55],[3,6.6,11.7,11.55],[4,8.8,11.6,11.7],[5,7.7,10.4,13.1]]){
    oct(m,'mandarin_lower_'+i,x,y,z,2.1,.7,2.1,'orangeShade');
    oct(m,'mandarin_body_'+i,x,y+.7,z,2.35,.72,2.35,'orange');
    oct(m,'mandarin_top_'+i,x,y+1.42,z,1.8,.26,1.8,'orange');
    b(m,'mandarin_stem_'+i,[x-.16,y+1.68,z-.16],[x+.16,y+1.88,z+.16],'greenShade');
    b(m,'mandarin_leaf_'+i,[x+.02,y+1.72,z-.26],[x+.9,y+1.87,z+.26],'green',rot('y',i%2?22.5:-22.5,[x,y+1.8,z]));
  }
  for(const [i,x,z] of [[0,3.0,3.6],[1,12.7,4.5]]){
    oct(m,'tea_saucer_'+i,x,10.37,z,3.6,.26,3.6,'tealShade');
    oct(m,'tea_saucer_glaze_'+i,x,10.63,z,3.25,.13,3.25,'teal');
    oct(m,'teacup_foot_'+i,x,10.76,z,1.65,.3,1.65,'cream');
    oct(m,'teacup_body_'+i,x,11.06,z,2.4,1.6,2.4,'cream');
    oct(m,'green_tea_surface_'+i,x,12.67,z,1.95,.035,1.95,'greenShade');
    oct(m,'tea_reflection_'+i,x-.15,12.708,z-.25,1.05,.02,.45,'green');
    ring(m,'cup_gilded_lip_'+i,x-1.23,12.55,z-1.23,x+1.23,.2,z+1.23,.22,'gold');
    b(m,'cup_handle_top_'+i,[x+1.05,12.0,z-.45],[x+1.95,12.3,z+.45],'cream');
    b(m,'cup_handle_outer_'+i,[x+1.65,11.35,z-.45],[x+1.98,12.15,z+.45],'cream');
    b(m,'cup_handle_base_'+i,[x+1.05,11.2,z-.45],[x+1.9,11.5,z+.45],'cream');
  }
  floorDisplay(m,.67);
  return m;
}

function neonSign(){
  const m=setup('furn_neon_sign',128,['ink','slate','slateLight','gold','goldShade','glint','hotPink','pinkLight','pinkShade','cyan','teal','tealShade']);
  // Free-standing sign: broad centered feet, two posts, rear braces and power box.
  for(const [i,x] of [[0,1],[1,15]]){
    b(m,'steel_foot_'+i,[x-2,0,4.1],[x+2,.7,11.9],'ink');
    b(m,'foot_top_bevel_'+i,[x-1.7,.7,4.45],[x+1.7,.92,11.55],'slateLight');
    b(m,'upright_'+i,[x-.55,.92,7.45],[x+.55,8.3,8.55],'slate');
    b(m,'upright_collar_'+i,[x-.8,4.3,7.2],[x+.8,4.75,8.8],'goldShade');
    b(m,'back_brace_'+i,[x-.3,2.0,8.2],[x+.3,7.5,8.75],'slate',rot('x',22.5,[x,6.7,8.5]));
  }
  b(m,'board_dark_back',[-6.0,6,7.2],[22.0,26.6,9.0],'ink');
  b(m,'board_slate_face',[-5.5,6.5,6.95],[21.5,26.1,7.25],'slate');
  for(const [name,f,t] of [
    ['frame_bottom',[-6.35,5.75,6.35],[22.35,6.65,9.25]],
    ['frame_top',[-6.35,25.95,6.35],[22.35,26.85,9.25]],
    ['frame_left',[-6.35,6.65,6.35],[-5.45,25.95,9.25]],
    ['frame_right',[21.45,6.65,6.35],[22.35,25.95,9.25]]])b(m,name,f,t,'slateLight');
  for(const x of [-5.85,21.85])for(const y of [6.2,26.4])b(m,'frame_gold_fastener_'+x+'_'+y,[x-.23,y-.23,6.19],[x+.23,y+.23,6.38],'gold');
  b(m,'rear_power_box',[4.7,8.0,9.0],[11.3,11.8,10.1],'slateLight');
  for(let i=0;i<5;i++)b(m,'power_vent_'+i,[5.4+i*1.05,8.65,10.07],[5.8+i*1.05,11.1,10.22],'ink');
  b(m,'rear_power_cable',[10.5,2.2,8.65],[10.8,8.3,9.0],'ink');
  // Rectilinear neon strokes have a dark outer jacket and bright raised inner core.
  const tube=(name,x0,y0,x1,y1,color,width=.65)=>{
    const dx=x1-x0,dy=y1-y0;
    if(dx!==0&&dy!==0){
      const steps=Math.max(Math.abs(dx),Math.abs(dy));
      for(let i=0;i<steps;i++){const t=i/steps;const xx=x0+dx*t,yy=y0+dy*t;tube(name+'_'+i,xx,yy,xx+dx/steps,yy,color,width);}
      return;
    }
    const xa=Math.min(x0,x1)-width/2,xb=Math.max(x0,x1)+width/2,ya=Math.min(y0,y1)-width/2,yb=Math.max(y0,y1)+width/2;
    b(m,name+'_socket',[xa-.14,ya-.14,6.72],[xb+.14,yb+.14,6.98],'ink');
    b(m,name+'_tube',[xa,ya,6.43],[xb,yb,6.74],color);
    b(m,name+'_highlight',[xa+.14,ya+.14,6.37],[Math.max(xa+.2,xb-.14),Math.max(ya+.2,yb-.14),6.48],color==='hotPink'?'pinkLight':'glint');
  };
  // Winking cat silhouette occupies the left half; ears and whiskers stay distinct.
  const contour=[[-3,12],[-3,20],[-1,24],[2,21],[5,21],[8,24],[9,20],[9,12],[6,10],[0,10],[-3,12]];
  for(let i=0;i<contour.length-1;i++)tube('cat_outline_'+i,...contour[i],...contour[i+1],'hotPink',.58);
  tube('open_eye_upper',-.6,17.6,1.1,17.6,'cyan',.58);
  tube('open_eye_stem',1.1,15.9,1.1,17.6,'cyan',.58);
  tube('wink_upper',5,17.2,6.4,17.7,'cyan',.58);
  tube('wink_lower',5,17.2,6.4,16.7,'cyan',.58);
  tube('cat_nose',3,15.15,3.6,15.15,'gold',.6);
  tube('cat_mouth_left',3.3,14.6,2.25,13.6,'gold',.4);
  tube('cat_mouth_right',3.3,14.6,4.35,13.6,'gold',.4);
  for(const [i,ya] of [[0,14.5],[1,13.3]]){
    tube('whisker_left_'+i,-4.4,ya,-1.1,ya+.5,'cyan',.35);
    tube('whisker_right_'+i,7.0,ya+.5,9.8,ya,'cyan',.35);
  }
  // Large 97 is built as true raised tubes, recognizable from across a plot.
  // North-facing text is viewed along +Z: reflect only the numeral group about X=15.65.
  // This keeps the cat/board composition while presenting a readable 97 to the viewer.
  for(const [name,a,c,d,e] of [['nine_top',11.3,21.8,14.5,21.8],['nine_left',11.3,17.5,11.3,21.8],['nine_mid',11.3,17.5,14.5,17.5],['nine_right',14.5,12.0,14.5,21.8],['nine_bottom',11.3,12.0,14.5,12.0],['seven_top',16.5,21.8,20.0,21.8],['seven_diag',20.0,21.8,17.1,12.0]])tube(name,31.3-a,c,31.3-d,e,'cyan',.72);
  tube('gold_underline',11.0,9.3,20.0,9.3,'gold',.38);
  for(const [i,x,y] of [[0,11.4,24.3],[1,18.6,24.4],[2,-2.7,8.2]]){
    tube('spark_vertical_'+i,x,y-.65,x,y+.65,'gold',.28);
    tube('spark_horizontal_'+i,x-.65,y,x+.65,y,'gold',.28);
  }
  floorDisplay(m,.49);
  return m;
}

function hatDisplay(m,guiScale){
  // Vanilla HEAD renderer scale is applied by the game; do not apply 0.625 a second time.
  m.display.head={rotation:[0,180,0],translation:[0,0,0],scale:[1,1,1]};
  m.display.gui={rotation:[15,30,0],translation:[0,-7,0],scale:[guiScale,guiScale,guiScale]};
  m.display.ground={rotation:[0,0,0],translation:[0,-2,0],scale:[.5,.5,.5]};
  m.display.fixed={rotation:[0,180,0],translation:[0,-7,0],scale:[.65,.65,.65]};
  m.display.thirdperson_righthand={rotation:[0,-90,0],translation:[0,-3,0],scale:[.55,.55,.55]};
  m.display.thirdperson_lefthand={rotation:[0,90,0],translation:[0,-3,0],scale:[.55,.55,.55]};
  m.display.firstperson_righthand={rotation:[0,-70,10],translation:[1,-5,0],scale:[.55,.55,.55]};
  m.display.firstperson_lefthand={rotation:[0,70,-10],translation:[1,-5,0],scale:[.55,.55,.55]};
}
function chefHat(){
  const m=setup('hat_chef_toque',64,['ink','white','whiteShade','cream','pink','pinkShade','gold','goldShade','glint','red','redShade']);
  // Same 13.8-unit hole and Y=13.7 lower edge as hat_yuumi_ears.
  ring(m,'toque_open_headband',.3,13.7,.3,15.7,2.0,15.7,.8,'white');
  ring(m,'red_ribbon_band',.18,14.0,.18,15.82,1.2,15.82,.35,'red');
  ring(m,'ribbon_upper_gold_stitch',.11,15.05,.11,15.89,.13,15.89,.15,'gold');
  // Inner cloth closes the stepped crown's corner gaps above the head opening.
  // Its underside is Y=15.2, leaving the original open headband below untouched.
  b(m,'toque_inner_cloth_lining',[1.1,15.2,1.1],[14.9,17.8,14.9],'whiteShade');
  // Tall pleated cloth tapers into a softly stepped, pillowy chef crown.
  oct(m,'toque_lower_cloth',8,15.2,8,15.2,2.4,15.2,'whiteShade');
  oct(m,'toque_main_cloth',8,17.6,8,17.1,4.9,17.1,'white');
  oct(m,'toque_upper_puff',8,22.5,8,18.3,2.5,18.3,'white');
  oct(m,'toque_top_soft_edge',8,25.0,8,16.9,1.4,16.9,'white');
  oct(m,'toque_crown_top',8,26.4,8,13.2,.7,13.2,'cream');
  for(const [i,x] of [2.0,4.4,6.8,9.2,11.6,14.0].entries()){
    b(m,'front_pleat_shadow_'+i,[x-.28,16.0,-.50],[x+.28,24.9,-.28],'whiteShade');
    b(m,'front_pleat_ridge_'+i,[x-.11,16.2,-.66],[x+.25,25.2,-.45],'white');
    b(m,'rear_pleat_shadow_'+i,[x-.28,16.0,16.28],[x+.28,24.9,16.50],'whiteShade');
    b(m,'rear_pleat_ridge_'+i,[x-.25,16.2,16.45],[x+.11,25.2,16.66],'white');
  }
  for(const [i,z] of [3,6.3,9.6,12.9].entries()){
    b(m,'west_pleat_shadow_'+i,[-.50,17.8,z-.22],[-.28,24.7,z+.22],'whiteShade');
    b(m,'east_pleat_shadow_'+i,[16.28,17.8,z-.22],[16.50,24.7,z+.22],'whiteShade');
  }
  // Puffy lobes separate the crown silhouette; ears stand clear above the cloth.
  for(const [i,x,z] of [[0,3.5,6.0],[1,12.5,6.0],[2,5.5,11.7],[3,11.5,11.7]])oct(m,'top_pillow_lobe_'+i,x,26.0,z,5.0,1.45,5.0,'white');
  ear(m,'chef_left_cat_ear',1.7,24.3,5.2,-1,'white','pink');
  ear(m,'chef_right_cat_ear',14.3,24.3,5.2,1,'white','pink');
  b(m,'red_bow_left',[4.5,14.0,-.15],[7.15,15.7,.28],'red',rot('z',-22.5,[7.15,14.9,.08]));
  b(m,'red_bow_right',[8.85,14.0,-.15],[11.5,15.7,.28],'red',rot('z',22.5,[8.85,14.9,.08]));
  b(m,'red_bow_left_fold',[4.8,14.4,-.26],[6.8,14.8,-.09],'redShade',rot('z',-22.5,[7.15,14.9,.08]));
  b(m,'red_bow_right_fold',[9.2,14.4,-.26],[11.2,14.8,-.09],'redShade',rot('z',22.5,[8.85,14.9,.08]));
  // Gold fish badge has a fat center, tail fork, fin and a dark eye.
  b(m,'fish_badge_body',[6.5,14.25,-.50],[8.75,15.55,-.12],'gold');
  b(m,'fish_badge_head',[8.75,14.55,-.50],[9.35,15.25,-.12],'gold');
  b(m,'fish_badge_tail_upper',[5.5,14.9,-.46],[6.6,15.7,-.15],'goldShade',rot('z',-22.5,[6.4,14.9,-.3]));
  b(m,'fish_badge_tail_lower',[5.5,14.1,-.46],[6.6,14.9,-.15],'goldShade',rot('z',22.5,[6.4,14.9,-.3]));
  b(m,'fish_badge_dorsal',[7.2,15.4,-.43],[8.0,15.9,-.14],'gold');
  b(m,'fish_badge_eye',[8.70,14.94,-.57],[8.96,15.20,-.46],'ink');
  b(m,'fish_badge_gloss',[6.85,15.25,-.57],[7.9,15.48,-.46],'glint');
  hatDisplay(m,.65);
  return m;
}

function stockHat(){
  const m=setup('hat_stock_tophat',64,['ink','black','blackLight','blackShade','gold','goldShade','glint','teal','tealShade','green','cream']);
  // Brim is a hollow ring below the crown; it does not cut through the player's head.
  ring(m,'silk_brim_inner_ring',-2.3,13.7,-2.3,18.3,.7,18.3,3.4,'blackShade');
  // Corner steps soften the square silhouette without a non-vanilla mesh.
  b(m,'brim_north_roundover',[.2,13.65,-2.9],[15.8,14.25,-2.3],'black');
  b(m,'brim_south_roundover',[.2,13.65,18.3],[15.8,14.25,18.9],'black');
  b(m,'brim_west_roundover',[-2.9,13.65,.2],[-2.3,14.25,15.8],'black');
  b(m,'brim_east_roundover',[18.3,13.65,.2],[18.9,14.25,15.8],'black');
  ring(m,'brim_upper_gold_piping',-2.27,14.4,-2.27,18.27,.13,18.27,.18,'goldShade');
  ring(m,'top_hat_hollow_crown',.3,14.4,.3,15.7,10.8,15.7,.8,'black');
  ring(m,'crown_tapered_upper',-.15,25.2,-.15,16.15,1.7,16.15,1.25,'black');
  // A recessed solid lining closes the four spaces between square walls and stepped lid.
  // It is confined to the very top; the lower crown/head cavity remains hollow.
  b(m,'silk_top_inner_lining',[1.1,26.85,1.1],[14.9,27.07,14.9],'blackLight');
  oct(m,'silk_top_lid',8,26.9,8,16.3,.65,16.3,'blackLight');
  oct(m,'silk_top_inset',8,27.55,8,14.9,.12,14.9,'black');
  ring(m,'silk_lower_band_shadow',.15,15.45,.15,15.85,3.15,15.85,.33,'blackShade');
  ring(m,'wide_gold_ribbon',.06,15.8,.06,15.94,2.4,15.94,.32,'gold');
  ring(m,'ribbon_upper_reflection',-.01,17.97,-.01,16.01,.2,16.01,.25,'glint');
  ring(m,'ribbon_lower_shadow',-.01,15.8,-.01,16.01,.22,16.01,.25,'goldShade');
  b(m,'front_silk_vertical_sheen',[1.2,18.7,.17],[2.3,25.7,.36],'blackLight');
  b(m,'rear_silk_vertical_sheen',[12.9,18.7,15.64],[14.1,25.7,15.82],'blackLight');
  for(const z of [3.1,12.1])b(m,'right_silk_soft_highlight_'+z,[15.64,18.8,z],[15.82,25.8,z+.55],'blackLight');
  // Coin rosette, engraved crown and short ribbon tabs are pinned at the front-left.
  const coin=[4.05,19.15,-.37];
  const disc=(name,x,y,z,w,h,d,tex)=>{
    b(m,name+'_center',[x-w*.32,y-h*.5,z-d*.5],[x+w*.32,y+h*.5,z+d*.5],tex);
    b(m,name+'_left',[x-w*.5,y-h*.32,z-d*.5],[x-w*.32,y+h*.32,z+d*.5],tex);
    b(m,name+'_right',[x+w*.32,y-h*.32,z-d*.5],[x+w*.5,y+h*.32,z+d*.5],tex);
  };
  disc('coin_reeded_rim',...coin,4.2,4.2,.55,'goldShade');
  disc('coin_golden_face',coin[0],coin[1],-.73,3.7,3.7,.25,'gold');
  disc('coin_inner_field',coin[0],coin[1],-.9,2.8,2.8,.12,'goldShade');
  b(m,'coin_crown_bar',[3.0,18.6,-1.05],[5.1,19.0,-.87],'glint');
  for(const [i,x,y] of [[0,3.15,19.0],[1,4.0,19.35],[2,4.85,19.0]])b(m,'coin_crown_peak_'+i,[x-.23,19.0,-1.05],[x+.23,y+.75,-.87],'glint');
  b(m,'coin_ribbon_left',[2.85,16.1,-.5],[3.7,17.6,-.2],'tealShade',rot('z',-22.5,[3.5,17.6,-.35]));
  b(m,'coin_ribbon_right',[4.35,16.1,-.5],[5.2,17.6,-.2],'teal',rot('z',22.5,[4.5,17.6,-.35]));
  // Bold emerald rising market arrow, staged above the gold band.
  b(m,'arrow_dark_stem',[10.15,17.1,-.22],[11.35,23.2,.17],'tealShade',rot('z',-22.5,[10.75,18,-.02]));
  b(m,'arrow_emerald_inlay',[10.4,17.2,-.39],[11.1,23.1,-.15],'teal',rot('z',-22.5,[10.75,18,-.02]));
  b(m,'arrow_head_left',[10.5,22.5,-.42],[14.2,23.6,-.02],'green',rot('z',22.5,[13.2,23.05,-.2]));
  b(m,'arrow_head_right',[13.2,20.9,-.42],[14.3,24.1,-.02],'teal',rot('z',22.5,[13.2,23.05,-.2]));
  b(m,'arrow_glint',[13.0,23.0,-.58],[13.45,23.45,-.35],'glint');
  // Alternating flat and edge-on chain links join the hat band to a hollow monocle.
  for(let i=0;i<7;i++){
    const x=16.3+Math.sin(i*.48)*.72,y=17.3-i*.73,z=-.6;
    if(i%2===0){
      b(m,'chain_link_left_'+i,[x-.4,y-.5,z-.10],[x-.19,y+.5,z+.12],'gold');
      b(m,'chain_link_right_'+i,[x+.19,y-.5,z-.10],[x+.4,y+.5,z+.12],'goldShade');
      b(m,'chain_link_top_'+i,[x-.19,y+.29,z-.10],[x+.19,y+.5,z+.12],'gold');
      b(m,'chain_link_bottom_'+i,[x-.19,y-.5,z-.10],[x+.19,y-.29,z+.12],'gold');
    }else b(m,'chain_edge_link_'+i,[x-.13,y-.52,z-.35],[x+.13,y+.52,z+.35],'gold');
  }
  const mx=15.5,my=10.75,mz=-.7;
  b(m,'monocle_top',[mx-1.2,my+1.5,mz],[mx+1.2,my+1.85,mz+.38],'gold');
  b(m,'monocle_bottom',[mx-1.2,my-1.85,mz],[mx+1.2,my-1.5,mz+.38],'goldShade');
  b(m,'monocle_left',[mx-1.85,my-1.2,mz],[mx-1.5,my+1.2,mz+.38],'gold');
  b(m,'monocle_right',[mx+1.5,my-1.2,mz],[mx+1.85,my+1.2,mz+.38],'gold');
  for(const [i,dx,dy] of [[0,-1.45,-1.45],[1,1.45,-1.45],[2,-1.45,1.45],[3,1.45,1.45]])b(m,'monocle_corner_'+i,[mx+dx-.35,my+dy-.35,mz],[mx+dx+.35,my+dy+.35,mz+.38],'gold');
  b(m,'monocle_upper_glint',[mx-.8,my+1.55,mz-.10],[mx+.3,my+1.77,mz+.04],'glint');
  hatDisplay(m,.65);
  return m;
}

for(const model of [catTower(),kotatsu(),neonSign(),chefHat(),stockHat()]){
  model.save();
  console.log(JSON.stringify({id:model.id,elements:model.elements.length,atlas:[model.atlasSize,model.atlasSize],swatches:Object.keys(model.swatches).length}));
}
