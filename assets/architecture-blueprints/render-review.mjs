import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {readStructure} from './lib/nbt.mjs';
import {Raster} from './lib/raster.mjs';
import {iso,plan,longitudinal,colorFor} from './lib/render.mjs';
import {blockInfo} from './lib/registry.mjs';
const base=path.dirname(fileURLToPath(import.meta.url)),input=path.resolve(base,'../../yuumi-core/src/main/resources/structures/overhaul'),out=path.join(base,'review');
fs.mkdirSync(out,{recursive:true});
const manifest=JSON.parse(fs.readFileSync(path.join(input,'manifest.json'),'utf8'));
const entries=manifest.structures.map(entry=>({entry,s:readStructure(path.join(input,entry.file))}));
const themes=[{name:'STARLIGHT ASTRAL ARCHIVE',accent:'#68e0df',tag:'OBSERVER GALLERIES / COSMIC GLASS / ARMILLARY'}, {name:'CRIMSON TITAN FOUNDRY',accent:'#efa65c',tag:'COPPER MACHINE HALLS / SEALED LAVA / CRUCIBLES'}, {name:'VOID-GLASS ECLIPSED CATHEDRAL',accent:'#cca0f1',tag:'FLOATING TRACERY / SHATTERED GLASS / ECLIPSE'}];
const roles=['GATE LOBBY','TWIN GALLERIES','CHASM BRIDGE','BOSS + SANCTUARY','PUZZLE ATRIUM','GREAT CATHEDRAL'];
function header(c,title,sub){c.rect(0,0,c.width,12,'#9ddccf');c.text('YUUMI 97LAND  /  ARCHITECTURE STUDIO',52,42,3,'#94b8c5');c.text(title,52,92,5,'#eef4ed');c.text(sub,52,150,2,'#95a9be');}
function panel(c,x,y,w,h,title,subtitle,accent='#99d5c9'){c.rect(x,y,w,h,'#172438');c.rect(x,y,w,3,accent);c.text(title,x+20,y+20,3,accent);if(subtitle)c.text(subtitle,x+20,y+53,2,'#9cacbf');}
function lightmap(c,s,rect){const [W,H,L]=s.size,k=Math.min(rect.w/W,rect.h/L),ox=rect.x,oy=rect.y;for(let z=0;z<L;z++)for(let x=0;x<W;x++){const lamps=[];for(let y=0;y<H;y++)if(blockInfo(s.get(x,y,z)).emitLight>0)lamps.push(y);c.rect(ox+x*k,oy+z*k,k-.3,k-.3,lamps.length?'#dfbc72':'#22354a');}c.text('EMISSIVE BLOCK POSITIONS - NOT SIMULATED LIGHT',rect.x,rect.y+L*k+12,2,'#a0b5c2');}
function seamDiagrams(c,group,x,y,accent){
 c.text('ACTUAL SEAM FACES / 9 X 8 AIR + FLOOR',x,y-30,2,accent);
 for(let gate=0;gate<5;gate++){
   const a=group[gate],b=group[gate+1],left=x+gate*320;
   c.text(`G${gate+1}  Z${a.entry.origin[2]} / ${a.entry.origin[2]-1}`,left,y,2,'#b9ccd6');
   for(let dx=-4;dx<=4;dx++)for(let yy=6;yy<=14;yy++){
     const sa=a.s.get((a.s.size[0]-1)/2+dx,yy,0),sb=b.s.get((b.s.size[0]-1)/2+dx,yy,b.s.size[2]-1),px=left+(dx+4)*21,py=y+32+(14-yy)*19;
     c.rect(px,py,10,17,sa.Name==='minecraft:air'?'#2c4f65':colorFor(sa));c.rect(px+10,py,10,17,sb.Name==='minecraft:air'?'#3e6879':colorFor(sb));
   }
 }
}
const dungeonSheet=new Raster(3600,2990);
header(dungeonSheet,'THREE REALMS / EIGHTEEN CONNECTED MODULES','ACTUAL GZIP NBT DATA / 1 BLOCK GRID / PARTIAL SHELL CUTAWAYS / DIAGRAM COLORS - NO SHADER OR TEXTURE CLAIM');
for(let theme=1;theme<=3;theme++){
 const {name,accent,tag}=themes[theme-1],top=200+(theme-1)*910,group=entries.filter(x=>x.entry.id.startsWith(`ch${theme}_`));
 dungeonSheet.text(`0${theme}  ${name}`,52,top,4,accent);dungeonSheet.text(tag,52,top+44,2,'#9cacbf');
 for(let zone=1;zone<=6;zone++){const {entry,s}=group[zone-1],x=40+(zone-1)*590,y=top+80;panel(dungeonSheet,x,y,575,510,`Z${zone} / ${roles[zone-1]}`,`${s.size.join(' X ')} / ORIGIN ${entry.origin.join(', ')}`,accent);iso(dungeonSheet,s,{x:x+12,y:y+82,w:551,h:390});dungeonSheet.text(entry.file,x+20,y+480,2,'#7f96aa');}
 longitudinal(dungeonSheet,group,{x:180,y:top+644,w:1600,h:215});
 seamDiagrams(dungeonSheet,group,1830,top+647,accent);
 dungeonSheet.text('Y66 WALK PLANE',52,top+610,2,accent);
}
dungeonSheet.text('15 ADJACENT SEAMS / 9 WIDE X 8 HIGH / EXPLICIT AIR / END WALLS CLOSED / DYNAMIC GATES BELONG TO RUNTIME',52,2950,2,'#91b7c3');
dungeonSheet.save(path.join(out,'megadungeon-ch1-ch2-ch3-cutaways.png'));
const hall=entries.find(x=>x.entry.id==='hub_grand_utility_hall'),kiosk=entries.find(x=>x.entry.id==='hub_guidebook_kiosk');
const hubSheet=new Raster(2600,2200);header(hubSheet,'THE GUILD HALL + GUIDEBOOK KIOSK','ACTUAL NBT VOXELS / FOUR-SIDED ACCESS / FIXED INTERACTION ANCHORS / CLOCK AND HOLOGRAM MOUNTS');
panel(hubSheet,40,205,1230,850,'01 / CHERRY-PLATINUM GUILD HALL','29 X 26 X 25 / WORLD ORIGIN -36,64,-44');iso(hubSheet,hall.s,{x:55,y:295,w:1200,h:730},{cutaway:false});
panel(hubSheet,1295,205,1265,850,'02 / WORKING BAYS - CUTAWAY','NORTH ENCHANTING / WEST FORGE / EAST VAULT / CENTRAL FONT');iso(hubSheet,hall.s,{x:1310,y:295,w:1235,h:730},{cutaway:true});
panel(hubSheet,40,1080,815,680,'03 / FLOOR PLAN','NORTH IS UP / SLICE Y=2');const p=plan(hubSheet,hall.s,{x:70,y:1168,w:740,h:540},2);for(const a of hall.entry.anchors.filter(a=>a.kind==='entityFeet')){const [x,,z]=a.local;hubSheet.rect(p.ox+x*p.k-4,p.oy+z*p.k-4,8,8,'#70f1ce');}
panel(hubSheet,880,1080,815,680,'04 / GUIDEBOOK PAVILION','7 X 9 X 7 / LECTERN SOUTH / RESERVED AIR Y3-5');iso(hubSheet,kiosk.s,{x:900,y:1170,w:775,h:535},{cutaway:false});
panel(hubSheet,1720,1080,840,680,'05 / LIGHT PLACEMENT','NATIVE EMISSIVE BLOCKS IN MOLDINGS AND CLOCK');lightmap(hubSheet,hall.s,{x:1760,y:1175,w:740,h:490});
panel(hubSheet,40,1785,2520,345,'06 / BINDING CONTRACT','BLOCK COORDINATES AND ENTITY FEET ARE DISTINCT');
const lines=['SMITH NPC  -30.5 / 65 / -36.5     VAULT NPC  -12.5 / 65 / -36.5','CARPENTER  -12.5 / 65 / -28.5     ENCHANT TABLE CELL  -22 / 65 / -40','LECTERN CELL  8 / 66 / 8     HOLOGRAM AIR  LOCAL Y3..5','CLOCK OUTWARD PLANES  X3 / X10 / Z3 / Z10 - SEE MANIFEST CLOCKMOUNTS','GREEN FLOOR MARKERS = ENTITY FEET ANCHORS / AIR IS SERIALIZED IN EVERY EMPTY CELL'];
lines.forEach((line,i)=>hubSheet.text(line,70,1880+i*40,3,i===4?'#8faabd':'#d4e1de'));hubSheet.save(path.join(out,'hub-utility-hall-sheet.png'));
// Full-size module sheets keep details legible independently of the overview.
for(const {entry,s} of entries){const c=new Raster(1600,1200);header(c,entry.id.replaceAll('_',' '),`${s.size.join(' X ')} / ${entry.world.toUpperCase()} / ORIGIN ${entry.origin.join(', ')} / SOURCE SHA256 ${entry.sha256.slice(0,16)}`);iso(c,s,{x:30,y:210,w:1540,h:920},{cutaway:entry.id!=='hub_guidebook_kiosk'});c.text('SCHEMATIC COLOR RENDER FROM EXPORTED NBT / ROOF AND NEAR WALLS SELECTIVELY SECTIONED',40,1150,2,'#91a7b7');c.save(path.join(out,`${entry.id}.png`));}
fs.writeFileSync(path.join(out,'render-evidence.json'),JSON.stringify({source:'Final disk NBT parsed through independent reader',format:'CPU isometric/color diagrams, no Minecraft client or shader simulation',cutaway:'Near south/east wall above basal floor and upper east roof shell/ribs removed; far north facade and interior sculpture retained. Hall east roof cut at Y8. Long sections overlay center slice with darkened west wall elevation. Gate diagrams show both actual adjacent block faces side by side in each cell.',sources:entries.map(({entry})=>({file:entry.file,sha256:entry.sha256})),outputs:['hub-utility-hall-sheet.png','megadungeon-ch1-ch2-ch3-cutaways.png',...entries.map(({entry})=>`${entry.id}.png`)]},null,2)+'\n');
console.log('Rendered 2 overview sheets and 20 individual module sheets from exported NBT');
