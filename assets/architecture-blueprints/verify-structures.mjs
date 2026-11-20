import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { readStructure } from './lib/nbt.mjs';
import { getBlock, collisionBoxes, blockInfo } from './lib/registry.mjs';

// The contract below is deliberately independent of the design/compiler modules.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUTPUT = path.resolve(HERE, '../../yuumi-core/src/main/resources/structures/overhaul');
const REVIEW = path.join(HERE, 'review');
const DIMS = [[31,24,28],[39,28,38],[31,28,42],[37,28,36],[33,26,32],[45,32,45]];
const ORIGINS = [[-15,60,-28],[-19,60,-66],[-15,60,-108],[-18,60,-144],[-16,60,-176],[-22,60,-221]];
export const CONTRACT = [
  {file:'hub_grand_utility_hall.nbt',size:[29,26,25],origin:[-36,64,-44]},
  {file:'hub_guidebook_kiosk.nbt',size:[7,9,7],origin:[5,64,5]},
  ...[1,2,3].flatMap(chapter => DIMS.map((size,i) => ({file:`ch${chapter}_zone${i+1}.nbt`,chapter,zone:i+1,size,origin:ORIGINS[i]}))),
];
const FORBIDDEN = new Set(['jigsaw','structure_block','structure_void','command_block','chain_command_block','repeating_command_block','spawner','trial_spawner'].map(s=>'minecraft:'+s));
const EPS = 1e-7;
const equal = (a,b) => JSON.stringify(a) === JSON.stringify(b);
const nameAt = (s,x,y,z) => s.get(x,y,z)?.Name;
const isAir = (s,x,y,z) => nameAt(s,x,y,z)==='minecraft:air';
const boxesCache = new WeakMap();
function boxes(state) {
  if (!state) return [];
  if (!boxesCache.has(state)) boxesCache.set(state, collisionBoxes(state));
  return boxesCache.get(state);
}
function checkContext(errors,label) {
  return (ok,message) => { if (!ok) errors.push(`${label}: ${message}`); return !!ok; };
}

export function validatePalette(palette, errors = [], label = 'palette') {
  const check = checkContext(errors,label);
  palette.forEach((state,i) => {
    if (!check(typeof state.Name==='string' && /^minecraft:[a-z0-9_]+$/.test(state.Name),`entry ${i} has invalid Name`)) return;
    if (!check(!FORBIDDEN.has(state.Name),`forbidden ${state.Name}`)) return;
    let def;
    try { def = getBlock(state.Name); } catch { /* Report as a failed contract. */ }
    if (!check(!!def,`unknown block ${state.Name}`)) return;
    const props=state.Properties ?? {}, schemas=def.states ?? [];
    check(equal(Object.keys(props).sort(),schemas.map(s=>s.name).sort()),`${state.Name}: incomplete/extra properties ${JSON.stringify(props)}`);
    for (const schema of schemas) {
      const value=props[schema.name];
      const allowed = schema.values?.map(String) ?? (schema.type==='bool' ? ['true','false'] : Array.from({length:schema.num_values},(_,i)=>String(i)));
      check(typeof value==='string' && allowed.includes(value),`${state.Name}.${schema.name} invalid ${JSON.stringify(value)}`);
    }
  });
  return errors;
}

function typedList(tag, subtype) { return tag?.type==='list' && tag.value?.type===subtype && Array.isArray(tag.value.value); }
export function validateStructure(s, spec, errors=[]) {
  const check=checkContext(errors,spec.file), root=s.root;
  check(root?.type==='compound','root must be Compound');
  const tags=root?.value ?? {};
  check(tags.DataVersion?.type==='int' && tags.DataVersion.value===4189,'DataVersion must be Int 4189');
  check(typedList(tags.size,'int') && equal(tags.size.value.value,spec.size),'size must be exact List<Int>');
  check(typedList(tags.palette,'compound'),'palette must be List<Compound>');
  check(typedList(tags.blocks,'compound'),'blocks must be List<Compound>');
  check(tags.entities?.type==='list' && tags.entities.value?.value?.length===0,'entities must be empty List');
  check(equal(s.size,spec.size),'decoded dimensions differ from contract');
  check(s.size?.every(n=>Number.isInteger(n)&&n>0&&n<=45),'dimension outside 1..45');
  validatePalette(s.palette,errors,spec.file);
  const [w,h,l]=spec.size, volume=w*h*l, positions=new Uint8Array(volume);
  check(s.blocks.length===volume,`requires ${volume} explicit cells including AIR; got ${s.blocks.length}`);
  for (let i=0;i<s.blocks.length;i++) {
    const b=s.blocks[i], p=b.pos;
    if (!check(Array.isArray(p)&&p.length===3&&p.every(Number.isInteger),`block ${i} has invalid pos`)) continue;
    const [x,y,z]=p;
    if (!check(x>=0&&x<w&&y>=0&&y<h&&z>=0&&z<l,`out of bounds ${p}`)) continue;
    const key=(y*l+z)*w+x;
    check(positions[key]===0,`duplicate position ${p}`); positions[key]=1;
    check(Number.isInteger(b.state)&&b.state>=0&&b.state<s.palette.length,`invalid palette index at ${p}`);
    const typed=tags.blocks?.value?.value?.[i];
    check(typedList(typed?.pos,'int') && typed?.state?.type==='int',`block ${i} requires pos List<Int> and state Int`);
  }
  check(positions.every(n=>n===1),'missing explicitly written AIR/solid cells');
  for (const p of tags.palette?.value?.value ?? []) {
    check(p.Name?.type==='string','palette Name must be String');
    if (p.Properties) check(p.Properties.type==='compound' && Object.values(p.Properties.value).every(t=>t.type==='string'),'palette Properties values must be String');
  }
  return errors;
}

export function hasFullTop(state) {
  const tops=boxes(state).filter(b=>Math.abs(b[4]-1)<EPS);
  if (!tops.length) return false;
  const xs=[...new Set([0,1,...tops.flatMap(b=>[b[0],b[3]])])].sort((a,b)=>a-b);
  const zs=[...new Set([0,1,...tops.flatMap(b=>[b[2],b[5]])])].sort((a,b)=>a-b);
  for(let ix=1;ix<xs.length;ix++) for(let iz=1;iz<zs.length;iz++) {
    const x=(xs[ix-1]+xs[ix])/2,z=(zs[iz-1]+zs[iz])/2;
    if(x<0||x>1||z<0||z>1) continue;
    if(!tops.some(b=>x>=b[0]-EPS&&x<=b[3]+EPS&&z>=b[2]-EPS&&z<=b[5]+EPS)) return false;
  }
  return true;
}

function overlaps(a,b) { return a[0]<b[3]-EPS&&a[3]>b[0]+EPS&&a[1]<b[4]-EPS&&a[4]>b[1]+EPS&&a[2]<b[5]-EPS&&a[5]>b[2]+EPS; }
function hazard(state) {
  return state && (['minecraft:lava','minecraft:fire','minecraft:soul_fire','minecraft:powder_snow'].includes(state.Name) || (state.Name.endsWith('campfire')&&state.Properties?.lit==='true'));
}
function eachBox(s,area,fn) {
  for(let x=Math.max(0,Math.floor(area[0]));x<=Math.min(s.size[0]-1,Math.floor(area[3]-EPS));x++)
    for(let y=Math.max(0,Math.floor(area[1])-1);y<=Math.min(s.size[1]-1,Math.floor(area[4]-EPS));y++)
      for(let z=Math.max(0,Math.floor(area[2]));z<=Math.min(s.size[2]-1,Math.floor(area[5]-EPS));z++) {
        const state=s.get(x,y,z);
        if(hazard(state) && fn([x,y,z,x+1,y+1,z+1],state)===false) return false;
        for(const b of boxes(state)) if(fn([b[0]+x,b[1]+y,b[2]+z,b[3]+x,b[4]+y,b[5]+z],state)===false) return false;
      }
  return true;
}
function bodyArea([x,y,z]) { return [x-.3,y+EPS,z-.3,x+.3,y+1.8,z+.3]; }
export function bodyClear(s,p) {
  const a=bodyArea(p);
  // Entrance thresholds can support a player whose body extends beyond the
  // template footprint. Outside is AIR here; support still must come from NBT.
  if(a[1]<0||a[4]>s.size[1]) return false;
  return eachBox(s,a,b=>!overlaps(a,b));
}
function supported(s,p) {
  const [x,y,z]=p; let support=false;
  eachBox(s,[x-.3,y-.01,z-.3,x+.3,y+.01,z+.3],(b,state)=>{
    if(!hazard(state)&&Math.abs(b[4]-y)<EPS&&b[0]<x+.3-EPS&&b[3]>x-.3+EPS&&b[2]<z+.3-EPS&&b[5]>z-.3+EPS) support=true;
  });
  return support;
}
// A walk step rises before crossing a riser; descending crosses before lowering.
// Checking the swept AABBs catches a reversed stair or a beam between endpoints.
export function walkEdge(s,a,b) {
  if(Math.abs(a[1]-b[1])>.60001) return false;
  const top=Math.max(a[1],b[1]);
  const sweeps=[
    [a[0]-.3,a[1]+EPS,a[2]-.3,a[0]+.3,top+1.8,a[2]+.3],
    [Math.min(a[0],b[0])-.3,top+EPS,Math.min(a[2],b[2])-.3,Math.max(a[0],b[0])+.3,top+1.8,Math.max(a[2],b[2])+.3],
    [b[0]-.3,b[1]+EPS,b[2]-.3,b[0]+.3,top+1.8,b[2]+.3],
  ];
  return sweeps.every(a=>eachBox(s,a,b=>!overlaps(a,b)));
}

export function createNavigation(s,{minFeet=1,maxFeet=16}={}) {
  const nx=2*s.size[0],nz=2*s.size[2],columns=new Map(),nodes=[];
  for(let ix=0;ix<=nx;ix++) for(let iz=0;iz<=nz;iz++) {
    const x=ix/2,z=iz/2,heights=new Set();
    eachBox(s,[x-.3,minFeet-1,z-.3,x+.3,maxFeet+.01,z+.3],(b,state)=>{
      if(!hazard(state)&&b[4]>=minFeet-EPS&&b[4]<=maxFeet+EPS&&b[0]<x+.3-EPS&&b[3]>x-.3+EPS&&b[2]<z+.3-EPS&&b[5]>z-.3+EPS) heights.add(b[4]);
    });
    const ids=[];
    for(const y of heights) if(bodyClear(s,[x,y,z])&&supported(s,[x,y,z])) { ids.push(nodes.length);nodes.push({p:[x,y,z],ix,iz}); }
    if(ids.length) columns.set(`${ix},${iz}`,ids);
  }
  function nearest(p,maxDistance=.8) {
    let best=-1,dist=maxDistance;
    for(let i=0;i<nodes.length;i++) {
      const q=nodes[i].p,d=Math.hypot(q[0]-p[0],q[1]-p[1],q[2]-p[2]);
      if(d<dist&&Math.abs(q[1]-p[1])<.51) {best=i;dist=d;}
    }
    return best;
  }
  function flood(start) {
    const seen=new Set(),queue=[];
    if(start>=0) {seen.add(start);queue.push(start);}
    for(let k=0;k<queue.length;k++) {
      const id=queue[k],a=nodes[id];
      for(const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]]) for(const j of columns.get(`${a.ix+dx},${a.iz+dz}`)??[]) {
        if(!seen.has(j)&&walkEdge(s,a.p,nodes[j].p)) {seen.add(j);queue.push(j);}
      }
    }
    return seen;
  }
  return {nodes,nearest,flood,reachable:(seen,p,r=.8)=>{const n=nearest(p,r);return n>=0&&seen.has(n);}};
}

export function traceRoute(s,route,errors=[]) {
  const check=checkContext(errors,route.name??'route'),points=route.points;
  if(!check(Array.isArray(points)&&points.length>=2,'requires at least two feet waypoints')) return errors;
  for(let i=0;i<points.length;i++) {
    const p=points[i];
    if(!check(Array.isArray(p)&&p.length===3&&p.every(Number.isFinite),`invalid waypoint ${i}`)) continue;
    check(bodyClear(s,p),`body collision/hazard at ${p}`);
    check(supported(s,p),`unsupported feet at ${p}`);
    if(!i) continue;
    const a=points[i-1],dist=Math.hypot(p[0]-a[0],p[2]-a[2]),n=Math.max(1,Math.ceil(dist/.5));
    if(Math.abs(a[1]-p[1])>.60001) {check(false,`step exceeds 0.6 at waypoint ${i}`);continue;}
    // Long same-height runs are checked every half block. Height changes are a
    // single physical step; their authored horizontal span may not exceed 1m.
    if(Math.abs(a[1]-p[1])>EPS) {check(dist<=1.00001&&walkEdge(s,a,p),`blocked stair step ${i}`);continue;}
    let previous=a;
    for(let j=1;j<=n;j++) {
      const q=a.map((v,k)=>v+(p[k]-v)*j/n);
      check(bodyClear(s,q)&&supported(s,q)&&walkEdge(s,previous,q),`blocked/unsupported segment ${i} sample ${j}: ${q}`);
      previous=q;
    }
  }
  return errors;
}

export function effectiveBookshelves(s,table=[14,1,4]) {
  const valid=[];
  for(let dx=-2;dx<=2;dx++) for(let dz=-2;dz<=2;dz++) for(let dy=0;dy<=1;dy++) {
    if(Math.abs(dx)!==2&&Math.abs(dz)!==2) continue;
    const p=[table[0]+dx,table[1]+dy,table[2]+dz];
    if(nameAt(s,...p)==='minecraft:bookshelf' && isAir(s,table[0]+Math.trunc(dx/2),table[1]+dy,table[2]+Math.trunc(dz/2))) valid.push(p);
  }
  return valid;
}
function count(s,name,region) {
  return s.blocks.filter(b=>s.palette[b.state]?.Name==='minecraft:'+name&&(!region||b.pos.every((n,i)=>n>=region[0][i]&&n<=region[1][i]))).length;
}
function adjacentReachable(nav,seen,pos) {
  return nav.nodes.some((n,i)=>seen.has(i)&&Math.abs(n.p[1]-pos[1])<.6&&Math.hypot(n.p[0]-(pos[0]+.5),n.p[2]-(pos[2]+.5))<=1.6);
}

export function verifyHub(s,errors) {
  const check=checkContext(errors,'hub_grand_utility_hall.nbt');
  const quantities={anvil:3,smithing_table:2,grindstone:2,stonecutter:1,blast_furnace:3,enchanting_table:1,brewing_stand:2,cartography_table:1,loom:1,ender_chest:2,crafting_table:3,smoker:3,lava_cauldron:1};
  for(const [name,n] of Object.entries(quantities)) check(count(s,name)===n,`expected exactly ${n} ${name}`);
  for(const z of [10,11,12]) check(nameAt(s,3,1,z)==='minecraft:anvil',`anvil position 3,1,${z}`);
  for(const z of [10,11]) check(nameAt(s,25,1,z)==='minecraft:ender_chest',`ender chest position 25,1,${z}`);
  check(nameAt(s,14,1,4)==='minecraft:enchanting_table','enchanting table position');
  check(nameAt(s,25,1,19)==='minecraft:lava_cauldron','disposal cauldron position');
  const shelves=effectiveBookshelves(s); check(shelves.length>=15,`only ${shelves.length} effective enchanting bookshelves`);
  for(const [x,z] of [[5,7],[23,7],[23,15]]) {
    check(nameAt(s,x,0,z)==='minecraft:chiseled_quartz_block',`NPC pad marker ${x},0,${z}`);
    for(let y=1;y<=4;y++) check(isAir(s,x,y,z),`NPC clearance ${x},${y},${z}`);
  }
  check(count(s,'sea_lantern',[[13,0,11],[15,3,13]])>=1,'central sanctuary requires sea lantern');
  check(s.blocks.some(b=>b.pos[0]>=13&&b.pos[0]<=15&&b.pos[2]>=11&&b.pos[2]<=13&&s.palette[b.state].Name==='minecraft:quartz_stairs'&&s.palette[b.state].Properties?.waterlogged==='true'),'central sanctuary requires waterlogged quartz stairs');
  for(const b of s.blocks) if(s.palette[b.state].Name==='minecraft:cherry_leaves') check(s.palette[b.state].Properties?.persistent==='true','cherry leaves must be persistent');
  for(let y=15;y<=17;y++) for(let t=4;t<=8;t++) for(const [x,z] of [[3,t],[9,t],[t,3],[t,9]]) check(hasFullTop(s.get(x,y,z)),`clock mount plane must be full-faced ${x},${y},${z}`);
  const nav=createNavigation(s,{minFeet:1,maxFeet:2}),seed=nav.nearest([14.5,1,23.5],3),seen=nav.flood(seed);
  check(seed>=0,'south entry lacks walkable seed');
  for(const [side,predicate] of [['north',n=>n.p[2]===.5],['south',n=>n.p[2]===24.5],['west',n=>n.p[0]===.5],['east',n=>n.p[0]===28.5]]) check(nav.nodes.some((n,i)=>seen.has(i)&&n.p[1]===1&&predicate(n)),`no threshold-free ${side} entrance connected to hall`);
  for(const p of [[5.5,1,7.5],[23.5,1,7.5],[23.5,1,15.5]]) check(nav.reachable(seen,p),`NPC pad unreachable ${p}`);
  for(const b of s.blocks) if(Object.hasOwn(quantities,s.palette[b.state].Name.slice(10))) check(adjacentReachable(nav,seen,b.pos),`fixture has no reachable interaction neighbor ${s.palette[b.state].Name} at ${b.pos}`);
  return {effectiveBookshelves:shelves.length,walkableSamples:nav.nodes.length,reachableSamples:seen.size};
}

export function verifyKiosk(s,errors) {
  const check=checkContext(errors,'hub_guidebook_kiosk.nbt'),lectern=s.get(3,2,3);
  check(lectern?.Name==='minecraft:lectern'&&lectern.Properties?.facing==='south','lectern must be at 3,2,3 facing south');
  for(let y=3;y<=5;y++) check(isAir(s,3,y,3),`hologram obstruction at 3,${y},3`);
  const nav=createNavigation(s,{minFeet:1,maxFeet:2}),seed=nav.nearest([3.5,1,.5],1),seen=nav.flood(seed);
  for(const [side,p] of [['north',[3.5,1,.5]],['south',[3.5,1,6.5]],['west',[.5,1,3.5]],['east',[6.5,1,3.5]]]) check(nav.reachable(seen,p,1),`four-way stair route missing ${side}`);
  check(adjacentReachable(nav,seen,[3,2,3]),'lectern cannot be reached from base stairs');
  return {walkableSamples:nav.nodes.length,reachableSamples:seen.size};
}

export function verifyGate(s,z,label,errors) {
  const check=checkContext(errors,label),mid=(s.size[0]-1)/2;
  for(let x=mid-4;x<=mid+4;x++) {
    check(hasFullTop(s.get(x,6,z)),`seam floor is not full support at ${x},6,${z}`);
    for(let y=7;y<=14;y++) check(isAir(s,x,y,z),`seam aperture obstruction at ${x},${y},${z}`);
  }
}
export function verifyDungeon(s,spec,errors) {
  const check=checkContext(errors,spec.file),[w,h,l]=s.size,mid=(w-1)/2;
  if(spec.zone!==1) verifyGate(s,l-1,spec.file+' south gate',errors);
  if(spec.zone!==6) verifyGate(s,0,spec.file+' north gate',errors);
  for(let x=0;x<w;x++) for(let z=0;z<l;z++) check(hasFullTop(s.get(x,0,z)),`bottom escape barrier missing ${x},0,${z}`);
  if(spec.zone===1||spec.zone===6) {
    const z=spec.zone===1?l-1:0;
    for(let x=mid-4;x<=mid+4;x++) for(let y=7;y<=8;y++) check(boxes(s.get(x,y,z)).length>0,`terminal wall not closed ${x},${y},${z}`);
  }
  const nav=createNavigation(s,{minFeet:7,maxFeet:15}),start=spec.zone===1?[mid+.5,7,21.5]:[mid+.5,7,l-1.5],seed=nav.nearest(start,1),seen=nav.flood(seed);
  check(seed>=0,'entry/spawn not walkable');
  const destination=spec.zone===6?[mid+.5,7,5.5]:[mid+.5,7,.5];
  check(nav.reachable(seen,destination,1),'main progression route is disconnected');
  if(spec.zone===2) {
    for(const x of [7.5,31.5]) check(nav.reachable(seen,[x,15,21.5],.8),`Y14 balcony unreachable via stairs at x=${x}`);
  }
  if(spec.zone===3) {
    for(let z=1;z<l-1;z++) for(let x=mid-3;x<=mid+3;x++) check(hasFullTop(s.get(x,6,z)),`seven-wide bridge floor hole ${x},6,${z}`);
    check(nav.reachable(seen,[5.5,7,20.5]),'treasure balcony unreachable');
  }
  if(spec.zone===4) check(nav.reachable(seen,[mid+.5,8,6.5]),'raised sanctuary unreachable');
  if(spec.zone===5) for(const x of [mid-9,mid+9]) for(const z of [9,23]) {
    check(hasFullTop(s.get(x,6,z)),`puzzle pedestal floor ${x},6,${z}`);
    for(let y=7;y<=10;y++) check(isAir(s,x,y,z),`puzzle reserved volume ${x},${y},${z}`);
    check(nav.reachable(seen,[x+.5,7,z+.5]),`puzzle pedestal unreachable ${x},${z}`);
  }
  if(spec.zone===6) {
    for(const x of [mid-10,mid+10]) for(const z of [13,31]) for(let y=7;y<=22;y++) {
      const state=s.get(x,y,z),info=state&&blockInfo(state);
      check(hasFullTop(state)&&info?.transparent===false,`opaque boss cover core missing ${x},${y},${z}`);
    }
    check(nav.reachable(seen,[mid+.5,7,12.5]),'boss platform unreachable');
    if(spec.chapter===3) {
      // Accepted clarification: a circular 19-block window uses Y11..29,
      // rather than compressing a 19-block diameter into the original 16 rows.
      const frames=new Set(['minecraft:obsidian','minecraft:crying_obsidian']);
      for(const z of [0,1]) for(let dx=-9;dx<=9;dx++) for(let dy=-9;dy<=9;dy++) {
        const r=Math.hypot(dx,dy);if(r>9.5) continue;
        const name=nameAt(s,mid+dx,20+dy,z);
        if(r>=8.5) check(frames.has(name),`19-block rose frame cropped/broken at ${mid+dx},${20+dy},${z}`);
        if(r<2) check(frames.has(name),`eclipse center missing at ${mid+dx},${20+dy},${z}`);
      }
      for(const [x,y] of [[mid-9,20],[mid+9,20],[mid,11],[mid,29]]) check(frames.has(nameAt(s,x,y,0)),`19-block rose cardinal extent missing ${x},${y},0`);
      for(const name of ['purple_stained_glass','magenta_stained_glass']) check(count(s,name,[[mid-9,11,0],[mid+9,29,1]])>=20,`rose window lacks ${name} panes`);
    }
  }
  const emissive=s.blocks.filter(b=>(blockInfo(s.palette[b.state])?.emitLight??0)>0).length;
  check(emissive>=10,'insufficient built-in luminous sources');
  if(spec.chapter===2) {
    for(const id of seen) {
      const [x,y,z]=nav.nodes[id].p;
      check(!hazard(s.get(Math.floor(x),Math.floor(y),Math.floor(z))),'walkable route touches lava/fire');
    }
    for(const b of s.blocks) if(s.palette[b.state].Name==='minecraft:lava'&&b.pos[1]>=6) {
      const [x,y,z]=b.pos;
      for(const [dx,dy,dz] of [[1,0,0],[-1,0,0],[0,0,1],[0,0,-1],[0,-1,0]]) {
        const other=s.get(x+dx,y+dy,z+dz);
        const fullCube=boxes(other).some(q=>q[0]<=0&&q[1]<=0&&q[2]<=0&&q[3]>=1&&q[4]>=1&&q[5]>=1);
        check(other?.Name==='minecraft:lava'||fullCube,`above-walkway lava lacks closed full-block containment at ${x},${y},${z} side ${dx},${dy},${dz}`);
      }
    }
  }
  return {walkableSamples:nav.nodes.length,reachableSamples:seen.size,emissiveBlocks:emissive};
}

export function verifyDelivery(directory=OUTPUT,{writeReport=true}={}) {
  const errors=[],check=checkContext(errors,'delivery'),loaded=new Map(),details=[];
  const files=fs.readdirSync(directory).filter(f=>f.endsWith('.nbt')).sort();
  check(equal(files,CONTRACT.map(c=>c.file).sort()),'NBT file set must equal the exact 20-name contract');
  let manifest;
  try { manifest=JSON.parse(fs.readFileSync(path.join(directory,'manifest.json'),'utf8')); }
  catch(e) {errors.push('manifest.json: '+e.message);}
  check(manifest?.slotStride===1024,'manifest slotStride must be 1024');
  check(manifest?.minecraftVersion==='1.21.4'&&manifest?.dataVersion===4189,'manifest must identify Minecraft 1.21.4 / DataVersion 4189');
  const entries=manifest?.structures??[];
  check(Array.isArray(entries)&&entries.length===20,'manifest must contain 20 structures');
  check(new Set(entries.map(e=>e.file)).size===entries.length,'manifest contains duplicate files');
  let totalBlocks=0;
  for(const spec of CONTRACT) {
    const file=path.join(directory,spec.file),before=errors.length;
    try {
      const bytes=fs.readFileSync(file);
      check(bytes[0]===0x1f&&bytes[1]===0x8b,`${spec.file}: missing gzip magic`);
      const s=readStructure(file);loaded.set(spec.file,s);
      validateStructure(s,spec,errors);totalBlocks+=s.blocks.length;
      const entry=entries.find(e=>e.file===spec.file);
      check(!!entry,`${spec.file}: missing manifest entry`);
      if(entry) {
        check(equal(entry.size,spec.size)&&equal(entry.origin,spec.origin),`${spec.file}: manifest dimensions/origin mismatch`);
        check(entry.sha256===crypto.createHash('sha256').update(bytes).digest('hex'),`${spec.file}: manifest SHA256 mismatch`);
        check(entry.blockCount===s.blocks.length&&entry.bytes===bytes.length,`${spec.file}: manifest cell/byte counts mismatch`);
        const counts={};for(const b of s.blocks){const name=s.palette[b.state]?.Name;counts[name]=(counts[name]??0)+1;}
        check(equal(Object.entries(entry.counts??{}).sort(),Object.entries(counts).sort()),`${spec.file}: manifest block material counts mismatch`);
        for(const a of entry.anchors??[]) check(equal(a.world,a.local.map((n,i)=>n+spec.origin[i])),`${spec.file}: anchor ${a.name} world/local mismatch`);
      }
      // Structural errors may make collision operations unsafe; retain all other files' diagnostics.
      if(errors.length!==before) {details.push({file:spec.file,valid:false});continue;}
      const result=spec.chapter?verifyDungeon(s,spec,errors):spec.file.startsWith('hub_grand')?verifyHub(s,errors):verifyKiosk(s,errors);
      for(const a of entry?.anchors??[]) if(a.kind==='entityFeet') check(bodyClear(s,a.local)&&supported(s,a.local),`${spec.file}: entityFeet anchor ${a.name} lacks safe clearance/support`);
      for(const r of [...(entry?.reservedAir??[]),...(entry?.reservations??[]).filter(r=>r.require==='air')]) {
        for(let x=r.min[0];x<=r.max[0];x++)for(let y=r.min[1];y<=r.max[1];y++)for(let z=r.min[2];z<=r.max[2];z++) check(isAir(s,x,y,z),`${spec.file}: declared AIR reservation ${r.name} blocked at ${x},${y},${z}`);
      }
      for(const route of entry?.routes??[]) traceRoute(s,{...route,name:`${spec.file} / ${route.name}`},errors);
      details.push({file:spec.file,valid:errors.length===before,explicitBlocks:s.blocks.length,paletteStates:s.palette.length,...result});
    } catch(e) {errors.push(`${spec.file}: ${e.stack??e.message}`);details.push({file:spec.file,valid:false});}
  }
  check(totalBlocks===704299,`expected 704299 explicit cells; got ${totalBlocks}`);
  const seams=[];
  for(const chapter of [1,2,3]) for(let zone=1;zone<=5;zone++) {
    const southSpec=CONTRACT.find(s=>s.chapter===chapter&&s.zone===zone),northSpec=CONTRACT.find(s=>s.chapter===chapter&&s.zone===zone+1);
    const a=loaded.get(southSpec.file),b=loaded.get(northSpec.file),before=errors.length;
    check(northSpec.origin[2]+northSpec.size[2]===southSpec.origin[2],`chapter ${chapter} seam ${zone}: gap/overlap in world Z`);
    if(a&&b) for(let dx=-4;dx<=4;dx++) for(let y=6;y<=14;y++) {
      const ax=(a.size[0]-1)/2+dx,bx=(b.size[0]-1)/2+dx;
      check(southSpec.origin[0]+ax===northSpec.origin[0]+bx,`chapter ${chapter} seam ${zone}: world X misalignment`);
      check(y===6?(hasFullTop(a.get(ax,y,0))&&hasFullTop(b.get(bx,y,b.size[2]-1))):(isAir(a,ax,y,0)&&isAir(b,bx,y,b.size[2]-1)),`chapter ${chapter} seam ${zone}: discontinuity dx=${dx}, y=${y}`);
    }
    seams.push({chapter,gate:zone,worldZ:southSpec.origin[2],valid:errors.length===before});
  }
  const report={valid:errors.length===0,structureCount:loaded.size,explicitBlocks:totalBlocks,seamCount:seams.length,playerCollision:{width:.6,height:1.8,maxStep:.6,horizontalSampling:.5},checks:details,seams,errors,limitations:['Static collision and tagged material checks; live Paper placement, fluid ticks, gameplay and shader-free lighting require Stream 03 runtime integration.']};
  if(writeReport) {fs.mkdirSync(REVIEW,{recursive:true});fs.writeFileSync(path.join(REVIEW,'verification-report.json'),JSON.stringify(report,null,2)+'\n');}
  return report;
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const directory=process.argv[2]?path.resolve(process.argv[2]):OUTPUT;
  try {
    const report=verifyDelivery(directory);
    console.log(JSON.stringify({valid:report.valid,structures:report.structureCount,explicitBlocks:report.explicitBlocks,seams:report.seamCount,errors:report.errors.length},null,2));
    if(!report.valid) {console.error(report.errors.slice(0,100).join('\n'));process.exitCode=1;}
  } catch(e) {console.error(e.stack??e);process.exitCode=1;}
}
