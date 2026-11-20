import {blockInfo,collisionBoxes} from './registry.mjs';
import {shade,rgb} from './raster.mjs';
const colors={air:'#000000',bedrock:'#202435',black_concrete:'#080e20',gold_block:'#deb956',sea_lantern:'#b7fff0',ochre_froglight:'#ffe0a0',pearlescent_froglight:'#f3dcff',verdant_froglight:'#d5f7c3',shroomlight:'#ffa05c',end_rod:'#f5e6ff',soul_lantern:'#58e9e4',lantern:'#ffbd61',water:'#3897bd',lava:'#ff751b',amethyst_block:'#9d74cd',amethyst_cluster:'#d6a1ff',crying_obsidian:'#4b2765',obsidian:'#251e35',cherry_leaves:'#e4a0c5',bookshelf:'#886844',chiseled_bookshelf:'#aa864e',dark_prismarine:'#276065',cyan_stained_glass:'#159eac',blue_stained_glass:'#385cbc',magenta_stained_glass:'#d65bbd',purple_stained_glass:'#8555c9',pink_stained_glass:'#e798cd',glass:'#a5dcdf',lapis_block:'#344a8a',waxed_copper_block:'#c97848',oxidized_copper:'#4dada0',waxed_oxidized_copper:'#4dada0',gilded_blackstone:'#766343',iron_bars:'#788da1',chain:'#61748b',polished_diorite:'#bfc5c9',calcite:'#d9d8cc',enchanting_table:'#743e69',lectern:'#bc8f59',crafting_table:'#9c7445',smithing_table:'#4e4949',grindstone:'#a7aaa8',stonecutter:'#aeb2b1',anvil:'#686b7b',ender_chest:'#287b77',brewing_stand:'#c9a85c',lava_cauldron:'#cf6f35',cartography_table:'#b4915d',loom:'#b89671'};
export function colorFor(state){const n=state.Name.slice(10);if(colors[n])return rgb(colors[n]);if(n.includes('glass_pane'))return colorFor({Name:state.Name.replace('_pane','')});if(n.includes('quartz'))return rgb(n.includes('pillar')?'#e2e0d5':'#d2d7da');if(n.includes('cherry'))return rgb(n.includes('stripped')?'#d2a1ab':'#ae7e8e');if(n.includes('deepslate'))return rgb(n.includes('tile')?'#3d4659':'#536074');if(n.includes('blackstone'))return rgb('#433c49');if(n.includes('purpur'))return rgb('#ba96ca');if(n.includes('copper'))return rgb(n.includes('oxidized')?'#51a99d':n.includes('weathered')?'#80986a':'#c28357');if(n.includes('crimson'))return rgb('#71364c');if(n.includes('dark_oak'))return rgb('#65503c');if(n.includes('spruce'))return rgb('#94754f');if(n.includes('oak'))return rgb('#b29468');if(n.includes('furnace')||n==='smoker')return rgb('#7a7b82');if(n.includes('campfire'))return rgb('#d78657');if(n.includes('stone'))return rgb('#9297a0');if(n.includes('amethyst'))return rgb('#b088dc');return rgb('#8a8295');}
Object.assign(colors,{orange_stained_glass:'#fca548',light_blue_stained_glass:'#6fcbea',blue_concrete:'#293c74',polished_basalt:'#484856'});
function visualBoxes(s){
 const n=s.Name.slice(10),p=s.Properties??{};
 if(n==='air')return [];
 if(n==='chain'){return p.axis==='x'?[[0,.42,.42,1,.58,.58]]:p.axis==='z'?[[.42,.42,0,.58,.58,1]]:[[.42,0,.42,.58,1,.58]];}
 if(n==='water'||n==='lava')return [[0,0,0,1,.88,1]];
 if(n.endsWith('copper_grate')){
   // Collision is a full cube, but the visible block is an open lattice.
   const result=[];
   for(const p of [0,.45,.9]){
     result.push([p,.9,0,p+.1,1,1],[0,.9,p,1,1,p+.1]);
     result.push([p,0,.9,p+.1,1,1],[0,p,.9,1,p+.1,1]);
     result.push([.9,p,0,1,p+.1,1],[.9,0,p,1,1,p+.1]);
   }
   return result;
 }
 if(n.includes('lantern'))return [[.25,.12,.25,.75,.8,.75],[.43,.8,.43,.57,1,.57]];
 if(n==='end_rod'){return p.facing==='east'||p.facing==='west'?[[0,.44,.44,1,.56,.56]]:p.facing==='south'||p.facing==='north'?[[.44,.44,0,.56,.56,1]]:[[.44,0,.44,.56,1,.56]];}
 if(n.includes('amethyst_cluster')||n.includes('amethyst_bud'))return [[.2,0,.2,.8,.8,.8]];
 const boxes=collisionBoxes(s);
 return boxes.length?boxes:[[.15,0,.15,.85,.5,.85]];
}
export function iso(canvas,s,rect,{cutaway=true,baseY=0,roof=false}={}){
 const [W,H,L]=s.size,cos=.8660254;
 const hall=W===29&&H===26&&L===25;
 const show=(x,y,z)=>{
   if(x<0||y<baseY||z<0||x>=W||y>=H||z>=L)return false;
   if(!cutaway)return true;
   if((z>=L-2||x>=W-2)&&y>0)return false;
   // Section only the vault shell/ribs on the east. Preserve sculptures,
   // the telescope, and full-height cover pillars below that section surface.
   const t=(x-(W-1)/2)/((W-1)/2),shellY=hall?8:Math.round(H-9+8*Math.sqrt(Math.max(0,1-t*t)))-2;
   if(!roof&&x>W/2&&z>3&&y>=shellY)return false;
   return true;
 };
 const scale=Math.min(rect.w/((W+L)*cos+2),rect.h/((W+L)*.5+H+2));
 const ox=rect.x+rect.w/2-(W-L)*cos*scale/2,oy=rect.y+rect.h/2+(H-(W+L)/2)*scale/2;
 const proj=(x,y,z)=>[ox+(x-z)*cos*scale,oy+((x+z)*.5-y)*scale];
 const cells=[];
 for(let y=baseY;y<H;y++)for(let z=0;z<L;z++)for(let x=0;x<W;x++){const state=s.get(x,y,z);if(state?.Name==='minecraft:air'||!show(x,y,z))continue;cells.push({x,y,z,state});}
 cells.sort((a,b)=>(a.x+a.z+a.y)-(b.x+b.z+b.y)||a.y-b.y||a.z-b.z);
 for(const {x,y,z,state} of cells){
   const info=blockInfo(state),n=state.Name,base=colorFor(state),emissive=info.emitLight>0,glass=n.includes('glass'),alpha=glass?.42:1;
   const noise=((x*73+z*37+y*11)%9-4)*.008;
   const boxes=visualBoxes(state);
   for(const [a,b,c,d,e,f] of boxes){
     const pts=[[x+a,y+b,z+c],[x+d,y+b,z+c],[x+d,y+b,z+f],[x+a,y+b,z+f],[x+a,y+e,z+c],[x+d,y+e,z+c],[x+d,y+e,z+f],[x+a,y+e,z+f]].map(p=>proj(...p));
     const faces=[{idx:[1,2,6,5],factor:.63,neighbor:[x+1,y,z],edge:d===1},{idx:[2,3,7,6],factor:.81,neighbor:[x,y,z+1],edge:f===1},{idx:[4,5,6,7],factor:1.08,neighbor:[x,y+1,z],edge:e===1}];
     for(const face of faces){const other=s.get(...face.neighbor);if(boxes.length===1&&face.edge&&show(...face.neighbor)&&other&&!blockInfo(other).transparent&&!other.Name.includes('glass')&&!other.Name.endsWith('copper_grate')&&other.Name!=='minecraft:air'&&collisionBoxes(other).some(q=>q.join(',')==='0,0,0,1,1,1'))continue;const poly=face.idx.map(i=>pts[i]);canvas.polygon(poly,shade(base,(emissive?1.12:face.factor)+noise),alpha);if(scale>7){for(let i=0;i<4;i++)canvas.line(...poly[i],...poly[(i+1)%4],shade(base,.48),.5,glass?.6:.22);}}
     if(n.includes('bookshelf')&&scale>4){const center=proj(x+.5,y+.5,z+1.01);for(let i=-2;i<=2;i++)canvas.line(center[0]+i*scale*.13,center[1]-scale*.2,center[0]+i*scale*.13,center[1]+scale*.13,['#8eb8ae','#c9956c','#77769b','#bdb28b','#9aaea0'][i+2],Math.max(1,scale*.06));}
   }
 }
}
export function plan(canvas,s,rect,yMax=2){
 const [W,H,L]=s.size,k=Math.min(rect.w/W,rect.h/L),ox=rect.x+(rect.w-W*k)/2,oy=rect.y+(rect.h-L*k)/2;
 for(let z=0;z<L;z++)for(let x=0;x<W;x++){let state;for(let y=Math.min(yMax,H-1);y>=0;y--){state=s.get(x,y,z);if(state.Name!=='minecraft:air')break;}canvas.rect(ox+x*k,oy+z*k,k-.5,k-.5,colorFor(state));}
 return {k,ox,oy};
}
export function longitudinal(canvas,entries,rect){
 const k=Math.min(rect.w/221,rect.h/33),oy=rect.y+rect.h;
 for(const {s,entry} of entries){const [W,H,L]=s.size,mid=(W-1)/2;for(let z=0;z<L;z++)for(let y=0;y<H;y++){
   // Back wall elevation behind a one-block central section shows both vault and walk plane.
   let state=s.get(mid,y,z),depth=false;if(state.Name==='minecraft:air'){state=s.get(1,y,z);depth=true;}
   if(state.Name==='minecraft:air')continue;const worldZ=entry.origin[2]+z,x=rect.x+(-worldZ-1)*k;canvas.rect(x,oy-(y+1)*k,k+.2,k+.2,shade(colorFor(state),depth?.48:1));
 }
 const seamX=rect.x+(-entry.origin[2]-1)*k;canvas.line(seamX,rect.y,seamX,oy,'#78bac9',1,.7);canvas.text(entry.id.split('_')[1],seamX-40,rect.y-22,2,'#becbd5');}
 canvas.line(rect.x,oy-7*k,rect.x+221*k,oy-7*k,'#76e3c9',1,.8);
 canvas.text('SOUTH  Z=-1',rect.x,oy+12,2);canvas.text('NORTH  Z=-221',rect.x+221*k-170,oy+12,2);
}
