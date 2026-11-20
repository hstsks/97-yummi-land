import {Voxel, block} from '../lib/voxel.mjs';

// The six footprints are a placement contract, not interchangeable room presets.
const ZONES = [
  {size:[31,24,28], origin:[-15,60,-28], role:'Expedition vestibule'},
  {size:[39,28,38], origin:[-19,60,-66], role:'Two-storey combat nave'},
  {size:[31,28,42], origin:[-15,60,-108], role:'Chasm bridge and hidden archive'},
  {size:[37,28,36], origin:[-18,60,-144], role:'Champion rotunda and sanctuary'},
  {size:[33,26,32], origin:[-16,60,-176], role:'Fourfold puzzle atrium'},
  {size:[45,32,45], origin:[-22,60,-221], role:'Great cathedral and reliquary'},
];
const THEMES = {
  1:{name:'Starlight Astral Archive', wall:'deepslate_tiles', trim:'polished_deepslate', pillar:'quartz_pillar', cap:'chiseled_quartz_block', rib:'quartz_block', roof:'dark_prismarine', floor:'polished_deepslate', alternate:'deepslate_tiles', edge:'quartz_bricks', inset:'cyan_stained_glass', light:'sea_lantern', stair:'dark_oak_stairs', rail:'dark_oak_fence', accent:'gold_block'},
  2:{name:'Crimson Titan Foundry', wall:'polished_blackstone_bricks', trim:'gilded_blackstone', pillar:'polished_blackstone', cap:'chiseled_polished_blackstone', rib:'crimson_hyphae', roof:'polished_blackstone_bricks', floor:'polished_blackstone_bricks', alternate:'cracked_polished_blackstone_bricks', edge:'waxed_cut_copper', inset:'waxed_copper_grate', light:'ochre_froglight', stair:'waxed_cut_copper_stairs', rail:'iron_bars', accent:'waxed_copper_block'},
  3:{name:'Void-Glass Eclipsed Cathedral', wall:'quartz_bricks', trim:'smooth_quartz', pillar:'purpur_pillar', cap:'chiseled_quartz_block', rib:'smooth_quartz', roof:'quartz_bricks', floor:'smooth_quartz', alternate:'quartz_bricks', edge:'purpur_block', inset:'purple_stained_glass', light:'pearlescent_froglight', stair:'purpur_stairs', rail:'quartz_wall', accent:'amethyst_block'},
};
// There is no vanilla quartz wall: sculpt its railing from posts and slabs.
THEMES[3].rail='purpur_slab';
const air=()=>block('air');
const stairs=(name,facing,half='bottom')=>block(name,{facing,half,shape:'straight',waterlogged:'false'});
const pane=(name,axis='z')=>block(name,{north:axis==='z',south:axis==='z',east:axis==='x',west:axis==='x',waterlogged:false});
function put(v,x,y,z,s){if(v.inside(x,y,z))v.set(x,y,z,s);}
function line(v,a,b,s){const n=Math.max(...a.map((q,i)=>Math.abs(q-b[i])));for(let i=0;i<=n;i++){const p=a.map((q,j)=>Math.round(q+(b[j]-q)*(n?i/n:0)));put(v,...p,s);}}
function ring(v,c,rx,ry,rz,plane,s){
  const steps=Math.ceil(Math.max(rx,ry,rz)*20);
  for(let i=0;i<=steps;i++){const a=i*2*Math.PI/steps;let p;
    if(plane==='xy')p=[c[0]+Math.round(rx*Math.cos(a)),c[1]+Math.round(ry*Math.sin(a)),c[2]];
    else if(plane==='yz')p=[c[0],c[1]+Math.round(ry*Math.sin(a)),c[2]+Math.round(rz*Math.cos(a))];
    else p=[c[0]+Math.round(rx*Math.cos(a)),c[1],c[2]+Math.round(rz*Math.sin(a))];
    put(v,...p,s);
  }
}
function disc(v,c,r,y,s){for(let x=-r;x<=r;x++)for(let z=-r;z<=r;z++)if(x*x+z*z<=r*r)put(v,c[0]+x,y,c[1]+z,s);}
function anchor(v,name,kind,local){v.meta.anchors.push({name,kind,local});}
function roofHeight(v,x){const m=(v.W-1)/2,t=(x-m)/m;return Math.min(v.H-1,Math.round(v.H-9+8*Math.sqrt(Math.max(0,1-t*t))));}

function basement(v,t,ch){
  v.fill(0,0,0,v.W-1,0,v.L-1,'bedrock');
  v.fill(0,1,0,v.W-1,1,v.L-1,ch===2?'polished_basalt':'black_concrete');
  for(const x of [0,v.W-1])v.fill(x,1,0,x,5,v.L-1,t.wall);
  for(const z of [0,v.L-1])v.fill(0,1,z,v.W-1,5,z,t.wall);
  if(ch===2){
    // A continuous stone basin bounds every source below the walk deck.
    v.fill(1,2,1,v.W-2,2,v.L-2,'polished_basalt');
    v.fill(2,2,2,v.W-3,2,v.L-3,block('lava',{level:0}));
    for(const x of [1,v.W-2])v.fill(x,2,1,x,5,v.L-2,'polished_basalt');
    for(const z of [1,v.L-2])v.fill(1,2,z,v.W-2,5,z,'polished_basalt');
    for(let z=5;z<v.L-4;z+=7)for(const x of [4,v.W-5])v.fill(x,2,z,x,5,z,'polished_blackstone_bricks');
  }else{
    for(let z=4;z<v.L-3;z+=5)for(let x=4;x<v.W-3;x+=6){
      const yy=2+(x+z)%2;
      put(v,x,yy,z,t.light);
      put(v,x,yy+1,z,block(ch===1?'end_rod':'amethyst_cluster',ch===1?{facing:'up'}:{facing:'up',waterlogged:false}));
      if(ch===1){put(v,x+1,1,z+1,'blue_stained_glass');put(v,x+2,1,z+2,'light_blue_stained_glass');}
      else {put(v,x-1,yy,z,'amethyst_block');put(v,x+1,yy,z,pane('magenta_stained_glass_pane','x'));}
    }
  }
}

function flooring(v,t,ch,zone){
  const m=(v.W-1)/2;
  for(let z=0;z<v.L;z++)for(let x=0;x<v.W;x++){
    if(zone===3&&z>3&&z<v.L-4&&Math.abs(x-m)>3)continue;
    const edge=x<3||x>v.W-4||z<2||z>v.L-3;
    let s=edge?t.edge:((x+2*z)%7===0?t.alternate:t.floor);
    if(!edge&&(Math.abs(x-m)<=2||((x-m)**2+(z-Math.floor(v.L/2))**2<49)))s=t.inset;
    if(!edge&&(Math.abs(x-m)===4||(z%8===0&&Math.abs(x-m)>5)))s=t.edge;
    if(ch===3&&!edge&&Math.abs(x-m)>6&&z%10>=3&&z%10<=6)s='purple_stained_glass';
    v.set(x,6,z,s);
  }
  // Narrow luminous inlays are flush, so all gameplay anchors remain usable.
  for(let z=4;z<v.L-3;z+=8)for(const x of [m-5,m+5]){
    if(zone!==3){v.set(x,5,z,t.light);v.set(x,6,z,ch===2?'waxed_copper_grate':t.inset);}
  }
}

function sideBays(v,t,ch){
  const spring=v.H-9;
  for(const x of [0,v.W-1])v.fill(x,6,0,x,spring,v.L-1,t.wall);
  for(const x of [1,v.W-2]){
    v.fill(x,6,1,x,7,v.L-2,t.trim);
    v.fill(x,spring-1,1,x,spring,v.L-2,t.edge);
    for(let z=2;z<v.L-2;z++){
      const yTop=spring-2;
      for(let y=8;y<=yTop;y++){
        let material=t.wall;
        if(ch===1){material=(y===13||y===14)?'dark_oak_planks':block('chiseled_bookshelf',{facing:x===1?'east':'west',slot_0_occupied:true,slot_1_occupied:(z%3!==0),slot_2_occupied:true,slot_3_occupied:true,slot_4_occupied:(z%2===0),slot_5_occupied:true});}
        else if(ch===2){material=(z%7<2||y%5===0)?'waxed_cut_copper':'waxed_oxidized_copper';}
        else {material=(z%7<2||y<9)?'quartz_bricks':((y+z)%5===0?'magenta_stained_glass':'purple_stained_glass');}
        v.set(x,y,z,material);
      }
    }
  }
  for(let z=3;z<v.L-2;z+=7){
    for(const x of [2,v.W-3]){
      v.fill(x-1,6,z-1,x+1,7,z+1,t.cap);
      v.fill(x,8,z,x,spring-2,z,block(t.pillar,t.pillar.endsWith('pillar')?{axis:'y'}:{}));
      v.fill(x-1,spring-1,z-1,x+1,spring-1,z+1,t.cap);
      put(v,x,spring,z,t.light);
      for(const dz of [-1,1])put(v,x,spring-2,z+dz,stairs(ch===2?'polished_blackstone_brick_stairs':'quartz_stairs',dz<0?'north':'south','top'));
    }
    if(ch===2)for(const x of [2,v.W-3])gear(v,x,12,z+3,2,x===2?'east':'west');
    if(ch===3)for(const x of [3,v.W-4]){
      put(v,x,10,z+2,block('end_rod',{facing:'up'}));
      put(v,x,9,z+2,'purpur_block');
      for(let h=0;h<3;h++)put(v,x+ (x===3?1:-1),16+h,z+2+h,pane(h%2?'magenta_stained_glass_pane':'pink_stained_glass_pane','z'));
    }
  }
  // Horizontal cornices and their recessed light bands bridge the bays.
  for(const x of [2,v.W-3])for(let z=1;z<v.L-1;z++){
    put(v,x,13,z,stairs(ch===2?'waxed_cut_copper_stairs':ch===3?'purpur_stairs':'dark_oak_stairs',x===2?'east':'west','top'));
    if(z%7===0)put(v,x,12,z,block(ch===2?'lantern':'soul_lantern',{hanging:true,waterlogged:false}));
  }
}

function vault(v,t,ch){
  const m=(v.W-1)/2;
  for(let x=0;x<v.W;x++){
    const y=roofHeight(v,x);
    for(let z=0;z<v.L;z++)v.set(x,y,z,t.roof);
    // Rib bands follow the same shell surface, with visibly deeper voussoirs.
    for(let z=3;z<v.L;z+=7){put(v,x,y-1,z,t.rib);if(x%4===0)put(v,x,y-2,z,t.cap);}
  }
  for(let z=0;z<v.L;z++)put(v,m,v.H-2,z,ch===2?'waxed_cut_copper':'chiseled_quartz_block');
  // Slender diagonal ribs meet at a boss instead of ending in mid-air.
  for(let z=3;z<v.L-7;z+=7)for(let i=0;i<=7;i++){
    const xa=Math.round(3+(m-3)*i/7),xb=v.W-1-xa;
    for(const x of [xa,xb])put(v,x,roofHeight(v,x)-2,z+i,t.rib);
  }
  for(let z=6;z<v.L-3;z+=7)put(v,m,v.H-3,z,t.light);
}

function endWalls(v,t,ch,zone){
  const m=(v.W-1)/2;
  for(const z of [0,v.L-1]){
    for(let x=0;x<v.W;x++)v.fill(x,6,z,x,roofHeight(v,x),z,t.wall);
    const open=(z===0?zone!==6:zone!==1);
    if(open){
      for(const x of [m-6,m+6])v.fill(x,6,z,x,15,z,t.cap);
      for(let x=m-5;x<=m+5;x++){
        const y=15+Math.round(4*Math.sqrt(1-((x-m)/5)**2));
        put(v,x,y,z,t.rib);put(v,x,y+1,z,ch===2?'waxed_cut_copper':t.cap);
      }
      for(const x of [m-7,m+7])put(v,x,13,z,t.light);
    }else{
      // Closed end walls have radial decoration; they are never exit portals.
      ring(v,[m,14,z],6,5,0,'xy',t.edge);
      for(const x of [m-8,m+8])v.fill(x,7,z,x,17,z,t.pillar);
    }
  }
}

function finalizeSeams(v,t,zone){
  const m=(v.W-1)/2;
  for(const z of [0,v.L-1]){
    if(z===0?zone===6:zone===1)continue;
    const zs=z===0?[0,1]:[v.L-2,v.L-1];
    for(const zz of zs){v.fill(m-4,6,zz,m+4,6,zz,t.edge);v.fill(m-4,7,zz,m+4,14,zz,'air');}
  }
}

function gear(v,x,y,z,r,facing){
  for(let dy=-r-1;dy<=r+1;dy++)for(let dz=-r-1;dz<=r+1;dz++){
    const d=Math.sqrt(dy*dy+dz*dz);
    if((d>=r-.7&&d<=r+.25)||(Math.max(Math.abs(dy),Math.abs(dz))===r+1&&(dy===0||dz===0)))put(v,x,y+dy,z+dz,'waxed_cut_copper');
  }
  put(v,x,y,z,'gold_block');
  for(const dz of [-1,1])put(v,x,y,z+dz,block('waxed_copper_trapdoor',{facing,half:'bottom',open:true,powered:false,waterlogged:false}));
}

function armillary(v,c,r){
  ring(v,c,r,r,0,'xy','gold_block');ring(v,c,0,r,r,'yz','gold_block');
  ring(v,c,r,0,r,'xz',block('dark_oak_fence',{north:true,south:true,east:true,west:true,waterlogged:false}));
  // Tilted zodiac band, sampled into genuine blocks rather than a texture.
  for(let i=0;i<128;i++){const a=i*Math.PI/64;put(v,c[0]+Math.round(r*Math.cos(a)),c[1]+Math.round(r*.6*Math.sin(a)),c[2]+Math.round(r*.8*Math.sin(a)),'waxed_cut_copper');}
  put(v,...c,'sea_lantern');
  for(const dx of [-1,1])put(v,c[0]+dx,c[1],c[2],'cyan_stained_glass');
  for(let y=c[1]+r+1;y<v.H-2;y++)put(v,c[0],y,c[2],block('chain',{axis:'y',waterlogged:false}));
}

function voidCrown(v,c,r){
  ring(v,c,r,0,r,'xz','purpur_block');
  ring(v,[c[0],c[1]+1,c[2]],r-1,0,r-1,'xz','amethyst_block');
  for(let i=0;i<8;i++){
    const a=i*Math.PI/4,x=c[0]+Math.round(r*Math.cos(a)),z=c[2]+Math.round(r*Math.sin(a));
    put(v,x,c[1]-1,z,block('end_rod',{facing:'down'}));put(v,x,c[1]-2,z,'pearlescent_froglight');
    for(let y=c[1]+1;y<roofHeight(v,x);y++)if(i%2===0)put(v,x,y,z,block('chain',{axis:'y',waterlogged:false}));
  }
  put(v,...c,'pearlescent_froglight');
  for(const dx of [-2,2])for(let h=0;h<3;h++)put(v,c[0]+dx,c[1]-h,c[2]+Math.sign(dx)*h,pane('magenta_stained_glass_pane','x'));
}

function crucible(v,c,r){
  const [cx,cy,cz]=c;
  // Glazed lava is fully enclosed. Copper grates are an upper safety cover.
  disc(v,[cx,cz],r-1,cy-2,'polished_blackstone_bricks');
  for(let y=cy-1;y<=cy+1;y++)for(let x=-r;x<=r;x++)for(let z=-r;z<=r;z++){
    const d=Math.sqrt(x*x+z*z);if(d>r)continue;
    const material=(d>r-1||y===cy+1)?(y===cy+1?'waxed_copper_grate':'polished_blackstone_bricks'):'lava';
    put(v,cx+x,y,cz+z,material);
  }
  for(const [dx,dz] of [[-r,0],[r,0],[0,-r],[0,r]])for(let y=cy+2;y<roofHeight(v,cx+dx);y++)put(v,cx+dx,y,cz+dz,block('chain',{axis:'y',waterlogged:false}));
}

function lavaChannels(v){
  const top=Math.min(v.H-10,22);
  for(const x of [2,v.W-3])for(const z of [8,v.L-9]){
    // Stone-backed glass tube: every horizontal neighbour is a barrier to fluid.
    v.fill(x-1,2,z-1,x+1,top+1,z+1,'polished_blackstone_bricks');
    for(let y=3;y<=top;y++){
      v.set(x,y,z,block('lava',{level:0}));
      v.set(x+(x<v.W/2?1:-1),y,z,'orange_stained_glass');
    }
    v.fill(x-2,top,z-2,x+2,top,z+2,'waxed_cut_copper');
    for(const dx of [-1,1])for(let y=top+1;y<roofHeight(v,x+dx);y++)put(v,x+dx,y,z,block('chain',{axis:'y',waterlogged:false}));
  }
}

function galleries(v,t){
  for(const x0 of [4,29]){
    v.fill(x0,14,4,x0+5,14,22,t.edge);
    // Head clearance is reserved before balustrades and luminaires are placed.
    v.fill(x0,15,4,x0+5,17,22,'air');
    for(const z of [7,14,21])for(const x of [x0,x0+5]){
      v.fill(x,7,z,x,13,z,t.pillar);put(v,x,13,z,stairs('quartz_stairs',x0===4?'east':'west','top'));
    }
    const inward=x0===4?10:28;
    for(let z=4;z<=22;z++)put(v,inward,15,z,t.name.includes('Foundry')?pane('iron_bars','z'):block('dark_oak_fence',{north:true,south:true,east:false,west:false,waterlogged:false}));
    for(const z of [5,12,19]){put(v,inward,15,z,t.cap);put(v,inward,16,z,block('lantern',{hanging:false,waterlogged:false}));}
  }
  for(const x0 of [6,30]){
    const points=[[x0+1.5,7,31.5]];
    // Eight full-block rises, each made of two traversable half-block treads.
    for(let i=0;i<8;i++){
      const y=7+i,z=30-i;
      v.fill(x0,y,z,x0+2,y,z,stairs(t.stair,'north'));
      if(y>7)v.fill(x0,7,z,x0+2,y-1,z,t.wall);
      v.fill(x0,y+1,z,x0+2,y+3,z,'air');
      points.push([x0+1.5,y+.5,z+.85],[x0+1.5,y+1,z+.35]);
      for(const x of [x0-1,x0+3]){put(v,x,y,z,t.edge);put(v,x,y+1,z,pane('iron_bars','z'));}
    }
    points.push([x0+1.5,15,22.5],[x0+1.5,15,10.5]);
    v.meta.routes.push({name:x0===6?'west-gallery-stair':'east-gallery-stair',points});
    anchor(v,x0===6?'west-gallery-mob-feet':'east-gallery-mob-feet','entityFeet',[x0+1.5,15,10.5]);
  }
}

function bridge(v,t,ch){
  const m=(v.W-1)/2;
  for(let z=4;z<v.L-4;z++)for(const x of [m-4,m+4]){
    if(x===m-4&&z>=19&&z<=21)continue;
    put(v,x,6,z,t.edge);
    put(v,x,7,z,ch===3?block('purpur_slab',{type:'bottom',waterlogged:false}):pane('iron_bars','z'));
    if(z%7===0){put(v,x,7,z,t.cap);put(v,x,8,z,block(ch===1?'soul_lantern':'lantern',{hanging:false,waterlogged:false}));}
  }
  // Side branch doubles as a small broken-off reading/maintenance chapel.
  v.fill(3,6,17,7,6,23,t.edge);v.fill(7,6,19,m-3,6,21,t.floor);
  v.fill(3,7,17,m-3,9,23,'air');
  for(let z=17;z<=23;z++)put(v,2,7,z,t.cap);
  for(const z of [16,24])for(let x=3;x<=7;x++)put(v,x,7,z,t.cap);
  put(v,4,7,18,block('chest',{facing:'south',type:'single',waterlogged:false}));
  put(v,3,10,18,t.light);
  v.fill(2,10,17,7,10,17,t.rib);v.fill(2,10,23,7,10,23,t.rib);
  anchor(v,'hidden-treasure-chest','block',[4,7,18]);
  anchor(v,'hidden-treasure-approach','entityFeet',[5.5,7,20.5]);
  v.meta.routes.push({name:'treasure-branch',points:[[m+.5,7,20.5],[5.5,7,20.5]]});
}

function sanctuary(v,t,ch){
  const m=(v.W-1)/2;
  disc(v,[m,20],10,6,t.edge);disc(v,[m,20],8,6,t.floor);
  ring(v,[m,6,20],7,0,7,'xz',ch===2?'waxed_copper_block':t.inset);
  for(const x of [m-9,m+9]){put(v,x,6,20,t.light);put(v,x,7,20,block('lantern',{hanging:false,waterlogged:false}));}
  v.fill(m-5,7,3,m+5,7,9,t.edge);
  for(let x=m-3;x<=m+3;x++){
    put(v,x,7,10,stairs(t.stair,'north'));put(v,x,7,2,stairs(t.stair,'south'));
  }
  // Central checkpoint stays vacant; the fire occupies a safely inset side brazier.
  v.fill(m-2,8,4,m+2,11,8,'air');
  put(v,m-4,8,5,'polished_blackstone');put(v,m-4,9,5,block('campfire',{facing:'south',lit:true,signal_fire:false,waterlogged:false}));
  put(v,m+4,8,5,'chiseled_quartz_block');put(v,m+4,9,5,t.light);
  for(const x of [m-5,m+5])for(const z of [3,9]){put(v,x,8,z,t.cap);put(v,x,9,z,block('lantern',{hanging:false,waterlogged:false}));}
  anchor(v,'champion-arena','entityFeet',[m+.5,7,20.5]);
  anchor(v,'checkpoint-floor','block',[m,7,6]);anchor(v,'checkpoint-feet','entityFeet',[m+.5,8,6.5]);
  v.meta.routes.push({name:'raised-sanctuary',points:[[m+.5,7,12.5],[m+.5,7,11.5],[m+.5,7.5,10.85],[m+.5,8,10.35],[m+.5,8,6.5],[m+.5,8,3.5],[m+.5,8,2.65],[m+.5,7.5,2.15],[m+.5,7,1.5]]});
}

function puzzle(v,t,ch){
  const m=(v.W-1)/2;
  ring(v,[m,6,16],6,0,6,'xz',t.edge);
  v.set(m,6,16,t.light);anchor(v,'puzzle-focus','block',[m,6,16]);
  for(const dx of [-9,9])for(const dz of [-7,7]){
    const x=m+dx,z=16+dz;
    v.fill(x-1,6,z-1,x+1,6,z+1,t.edge);v.set(x,6,z,ch===2?'waxed_oxidized_copper':t.light);
    v.fill(x-1,7,z-1,x+1,10,z+1,'air');
    anchor(v,`puzzle-pad-${dx}-${dz}`,'block',[x,6,z]);
    v.meta.reservedAir.push({name:`puzzle-${dx}-${dz}`,min:[x-1,7,z-1],max:[x+1,10,z+1]});
  }
  // Ocular ceiling frames the central puzzle without occupying its runtime space.
  ring(v,[m,v.H-4,16],6,0,6,'xz',t.accent);
  for(const dx of [-6,6])put(v,m+dx,v.H-5,16,t.light);
}

function boss(v,t,ch){
  const m=(v.W-1)/2;
  disc(v,[m,22],15,6,t.edge);disc(v,[m,22],13,6,t.floor);
  ring(v,[m,6,22],11,0,11,'xz',t.inset);ring(v,[m,6,22],6,0,6,'xz',t.edge);
  for(const dx of [-10,10])for(const z of [13,31]){
    const x=m+dx;
    v.fill(x-2,6,z-2,x+2,7,z+2,t.cap);
    v.fill(x-1,7,z-1,x+1,22,z+1,t.pillar);
    for(const y of [9,18])v.fill(x-1,y,z-1,x+1,y,z+1,t.trim);
    v.fill(x-2,23,z-2,x+2,23,z+2,t.cap);
    put(v,x,24,z,t.light);
    anchor(v,`cover-${dx}-${z}`,'block',[x,7,z]);
  }
  // Flush dais preserves the specified boss and return foot elevations.
  v.fill(m-4,6,10,m+4,6,14,t.edge);v.fill(m-3,6,3,m+3,6,7,t.edge);
  v.fill(m-2,7,10,m+2,11,14,'air');v.fill(m-2,7,3,m+2,11,7,'air');
  anchor(v,'boss-spawn-feet','entityFeet',[m+.5,7,12.5]);anchor(v,'return-feet','entityFeet',[m+.5,7,5.5]);
  if(ch===1)observatory(v,m);
  else if(ch===2)titan(v,m);
  else roseWindow(v,m);
}

function observatory(v,m){
  // A volumetric stellar half-dome projects ten blocks from the north wall.
  for(let dx=-10;dx<=10;dx++)for(let dy=0;dy<=10;dy++){
    const d=Math.sqrt(dx*dx+dy*dy);
    if(d<=10){put(v,m+dx,19+dy,1,d>8.5?'quartz_block':((dx*3+dy)%7===0?'sea_lantern':'blue_stained_glass'));}
  }
  for(let dx=-10;dx<=10;dx++)for(let dy=0;dy<=10;dy++)for(let dz=1;dz<=10;dz++){
    const d=Math.sqrt(dx*dx+dy*dy+dz*dz);if(d<8.9||d>10.1)continue;
    const rib=dx===0||dy===0||Math.abs(Math.abs(dx)-dz)<=.5;
    put(v,m+dx,19+dy,1+dz,rib?'quartz_block':((dx+2*dy+dz)%13===0?'sea_lantern':'blue_stained_glass'));
  }
  // An open observation slit faces the nave; the telescope stands on its own deck.
  v.fill(m-3,19,10,m+3,22,11,'air');
  v.fill(m-9,18,2,m+9,18,13,'dark_prismarine');
  for(const x of [m-8,m+8])for(const z of [3,11])v.fill(x,7,z,x,17,z,'quartz_pillar');
  for(let x=m-9;x<=m+9;x++)put(v,x,19,14,block('dark_oak_fence',{east:true,west:true,north:false,south:false,waterlogged:false}));
  v.fill(m+4,19,11,m+4,23,11,'gold_block');
  for(let i=0;i<=6;i++){
    const y=23+Math.round(i*.65),z=10+i;
    for(const dx of [-1,0,1])put(v,m+4+dx,y,z,i%3===0?'gold_block':'quartz_block');
    put(v,m+4,y+1,z,'quartz_block');
  }
  v.fill(m+3,27,17,m+5,29,17,'cyan_stained_glass');
  ring(v,[m+4,28,17],2,2,0,'xy','gold_block');
  for(const dx of [-6,-3,3,6]){put(v,m+dx,15,1,'sea_lantern');put(v,m+dx,14,1,'end_rod');}
  v.meta.observatory={domeCenter:[m,19,1],radius:10,depth:10,telescopeLens:[m+4,28,17]};
}

function titan(v,m){
  // Heavy shoulders, recessed eye sockets, and a glazed incandescent furnace mouth.
  v.fill(m-8,12,1,m+8,15,4,'polished_blackstone_bricks');
  v.fill(m-5,15,1,m+5,25,3,'polished_blackstone');
  for(const x of [m-6,m+6])v.fill(x,16,2,x,23,4,'gilded_blackstone');
  v.fill(m-4,24,2,m+4,26,4,'waxed_cut_copper');
  for(const x of [m-3,m+3]){put(v,x,22,4,'ochre_froglight');put(v,x,23,4,stairs('polished_blackstone_stairs',x<m?'east':'west','top'));}
  v.fill(m-3,16,1,m+3,20,4,'obsidian');
  v.fill(m-2,17,2,m+2,19,2,'lava');v.fill(m-2,17,3,m+2,19,4,'orange_stained_glass');
  v.fill(m-4,14,4,m+4,15,5,'gilded_blackstone');
  for(const x of [m-8,m+8])v.fill(x,16,3,x,20,3,'waxed_cut_copper');
}

function roseWindow(v,m){
  // User-approved correction: a true diameter-19 disk spans local Y 11..29.
  for(let dx=-9;dx<=9;dx++)for(let dy=-9;dy<=9;dy++){
    const d=Math.sqrt(dx*dx+dy*dy);if(d>9.5)continue;
    const a=Math.atan2(dy,dx),spoke=Math.abs(Math.sin(a*4));
    let s=d>8.15?'crying_obsidian':d<2.8?'obsidian':spoke<.22?'quartz_block':((Math.floor((a+Math.PI)*8/Math.PI)+Math.floor(d))%2?'magenta_stained_glass':'purple_stained_glass');
    put(v,m+dx,20+dy,0,s);put(v,m+dx,20+dy,1,s);
    if(d>7&&d<8&&Math.abs(dx*dy)<5)put(v,m+dx,20+dy,2,'pearlescent_froglight');
  }
  v.meta.roseWindow={diameter:19,center:[m,20,0],bounds:{min:[m-9,11,0],max:[m+9,29,1]},decision:'User approved circular window; Y range expanded from 14..29 to 11..29.'};
}

function lobby(v,t,ch){
  const m=(v.W-1)/2;
  for(const x of [m-9,m+9]){
    v.fill(x-2,6,v.L-12,x+2,6,v.L-7,t.edge);
    put(v,x,7,v.L-9,block('anvil',{facing:'north'}));
    v.fill(x-1,7,v.L-13,x+1,7,v.L-11,'chiseled_quartz_block');
    put(v,x,6,v.L-12,'sea_lantern');
    put(v,x,7,v.L-12,block('quartz_stairs',{facing:x<m?'east':'west',half:'bottom',shape:'straight',waterlogged:true}));
  }
  anchor(v,'party-spawn-feet','entityFeet',[m+.5,7,21.5]);
  v.fill(m-2,7,20,m+2,10,23,'air');
}

export function buildDungeons(){
  const result=[];
  for(let ch=1;ch<=3;ch++)for(let zone=1;zone<=6;zone++){
    const spec=ZONES[zone-1],t=THEMES[ch],v=new Voxel(`ch${ch}_zone${zone}`,spec.size,spec.origin),m=(v.W-1)/2;
    v.meta={chapter:ch,zone,theme:t.name,role:spec.role,originMode:'slotOffset',slotStride:1024,anchors:[],routes:[],reservedAir:[],walkFloorLocalY:6,walkFeetLocalY:7,features:['three-depth wall bays','ribbed barrel vault','patterned luminous floor','theme-specific suspended sculpture']};
    basement(v,t,ch);flooring(v,t,ch,zone);sideBays(v,t,ch);vault(v,t,ch);endWalls(v,t,ch,zone);
    const cz=zone===6?25:Math.floor(v.L/2),cy=zone===6?24:v.H-8,r=zone===6?5:zone===1?3:4;
    if(ch===1)armillary(v,[m,cy,cz],r);
    else if(ch===2){crucible(v,[m,cy,cz],r);if([2,3,6].includes(zone))lavaChannels(v);}
    else voidCrown(v,[m,cy,cz],r);
    if(zone===1)lobby(v,t,ch);
    if(zone===2)galleries(v,t);
    if(zone===3)bridge(v,t,ch);
    if(zone===4)sanctuary(v,t,ch);
    if(zone===5)puzzle(v,t,ch);
    if(zone===6)boss(v,t,ch);
    finalizeSeams(v,t,zone);
    const endpoints=zone===1?[[m+.5,7,21.5],[m+.5,7,.5]]:zone===6?[[m+.5,7,v.L-.5],[m+.5,7,5.5]]:[[m+.5,7,v.L-.5],[m+.5,7,.5]];
    if(zone===4){v.meta.routes.push({name:'south-nave',points:[endpoints[0],[m+.5,7,12.5]]});}
    else v.meta.routes.push({name:'main-spine',points:endpoints});
    v.meta.seams={north:zone!==6?{z:0,min:[m-4,7,0],max:[m+4,14,0],floorY:6}:null,south:zone!==1?{z:v.L-1,min:[m-4,7,v.L-1],max:[m+4,14,v.L-1],floorY:6}:null};
    result.push(v);
  }
  return result;
}
