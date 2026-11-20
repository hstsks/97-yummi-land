/** Four original 32px pixel masters. No existing icon or shared catalog writes.
 * Usage: node assets/yuumi97-production/sources/hero-icons.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Canvas,OUT,REVIEW} from '../tools/asset-kit.mjs';

const P={ink:'#201B31',goldDark:'#9D7031',gold:'#E8B84E',goldLight:'#FFE6A0',white:'#FFF8E8',obsidian:'#34303E',edge:'#65576B',lavaDark:'#AD431B',lava:'#FF6B1A',hot:'#FFC54C',heat:'#FFF3B0',wine:'#892F4F',quartz:'#ECF3F8',quartzShade:'#9AAFC4',moon:'#91E5F6',moonDark:'#438AAE',moonLight:'#E0FBFC',water:'#00E3C4',waterDark:'#167E91',violetDark:'#421767',violet:'#7932B2',violetLight:'#BF79EC',violetPale:'#EBC8FC',paper:'#F3E8EE',paperShade:'#C2AACA',navy:'#243F73',navyLight:'#4B6CAC',pink:'#FF85C0',pinkShade:'#B34F86',pinkLight:'#FFD2E5',jelly:'#E65BA5',sky:'#79CDEB'};
const rect=(c,x,y,w,h,k)=>c.rect(x,y,w,h,k);
const poly=(c,p,k)=>c.polygon(p,k);
const line=(c,x,y,x1,y1,k,w=1)=>c.line(x,y,x1,y1,k,w);
const ellipse=(c,x,y,rx,ry,k)=>c.ellipse(x,y,rx,ry,k);
const dot=(c,x,y,k)=>c.pixel(x,y,k);
function spark(c,x,y,k=P.white){line(c,x-1,y,x+1,y,k);line(c,x,y-1,x,y+1,k);}

function greatsword(){
  const c=new Canvas(32,32);
  // Forged, deliberately asymmetric blade silhouette. The broad charcoal mass
  // remains visible on both sides of the narrow orange channel at 16px too.
  poly(c,[[9,18],[12,13],[11,11],[15,10],[17,6],[20,6],[22,2],[28,1],[30,4],[27,9],[27,12],[23,13],[22,17],[18,19],[16,23]],P.ink);
  poly(c,[[11,18],[14,13],[13,12],[16,11],[18,8],[21,8],[23,4],[27,3],[28,4],[25,9],[25,11],[22,12],[20,16],[17,18],[15,21]],P.obsidian);
  line(c,14,12,17,11,P.edge);line(c,18,8,21,8,P.edge);line(c,23,4,27,3,P.edge);line(c,26,10,23,12,P.edge);
  line(c,14,19,25,5,P.lavaDark,3);line(c,14,18,25,5,P.lava,2);line(c,15,18,25,5,P.hot,1);
  dot(c,24,5,P.heat);dot(c,23,7,P.heat);dot(c,20,11,P.heat);dot(c,17,15,P.heat);
  line(c,20,12,23,12,P.lavaDark);line(c,18,13,16,11,P.lavaDark);dot(c,22,12,P.lava);
  // Leather grip and a hollow golden pommel loop.
  line(c,6,27,12,20,P.ink,5);line(c,6,27,12,20,P.wine,3);line(c,7,25,9,27,P.goldDark);line(c,9,22,11,24,P.goldDark);
  ellipse(c,5,28,2,2,P.ink);ellipse(c,5,28,1,2,P.gold);dot(c,5,28,[0,0,0,0]);dot(c,5,26,P.goldLight);
  // Hooked crossguard expresses the roaring cat claws around the ruby joint.
  poly(c,[[9,22],[4,17],[5,14],[7,15],[7,18],[12,20],[16,23],[20,24],[19,27],[16,26]],P.ink);
  poly(c,[[9,21],[5,17],[6,15],[6,18],[12,21],[16,24],[19,25],[17,25]],P.gold);
  line(c,6,16,6,18,P.goldLight);line(c,8,19,11,21,P.goldLight);line(c,15,24,18,25,P.goldLight);
  poly(c,[[10,19],[14,19],[15,22],[12,25],[9,22]],P.goldDark);poly(c,[[12,19],[14,22],[12,24],[10,22]],P.goldLight);
  poly(c,[[12,20],[13,22],[12,23],[11,22]],P.wine);dot(c,12,21,P.pinkLight);
  return c;
}
function staff(){
  const c=new Canvas(32,32);
  line(c,7,28,18,14,P.ink,5);line(c,7,28,18,14,P.quartzShade,3);line(c,7,27,17,14,P.quartz,1);
  for(const [x,y]of [[8,26],[11,22],[14,18]]){line(c,x-1,y-1,x+1,y+1,P.gold,2);dot(c,x-1,y-1,P.goldLight);}
  poly(c,[[5,27],[8,26],[10,28],[7,30],[5,29]],P.goldDark);poly(c,[[6,28],[8,27],[8,29],[7,29]],P.moon);
  // Open crescent, not a filled circular badge. Its interior stays transparent.
  poly(c,[[26,2],[21,1],[16,3],[12,7],[11,11],[13,15],[17,18],[23,18],[28,14],[24,15],[20,14],[17,11],[17,8],[20,5],[24,4],[28,5]],P.ink);
  poly(c,[[25,2],[21,2],[16,4],[13,8],[12,11],[14,14],[18,17],[23,17],[26,15],[22,16],[18,13],[16,11],[16,8],[19,5],[23,3],[27,4]],P.moonDark);
  poly(c,[[24,2],[20,3],[16,5],[14,8],[14,12],[17,15],[21,16],[24,16],[22,17],[18,16],[15,14],[13,11],[14,7],[17,4],[21,2]],P.moon);
  line(c,20,3,16,6,P.moonLight);line(c,15,7,14,10,P.moonLight);line(c,15,13,18,15,P.moonLight);dot(c,22,16,P.moonLight);
  // Small gold chalice with a cyan open liquid surface, stem and foot.
  poly(c,[[19,8],[26,8],[27,11],[25,14],[24,15],[24,16],[26,17],[20,17],[22,15],[20,13]],P.ink);
  poly(c,[[20,9],[25,9],[25,12],[23,14],[21,12]],P.gold);ellipse(c,23,9,3,1,P.goldLight);line(c,21,9,25,9,P.water);
  dot(c,21,9,P.waterDark);dot(c,23,9,P.moonLight);line(c,23,13,23,16,P.goldLight);line(c,21,16,25,16,P.gold);
  spark(c,28,7,P.moonLight);
  return c;
}
function orb(){
  const c=new Canvas(32,32);
  // Exactly four gilt orbital cards. They have distinct negative spaces from the
  // central sphere; a tiny rune is sufficient at native inventory resolution.
  const card=(name,points,inside,rx,ry)=>{poly(c,points,P.ink);poly(c,inside,P.gold);rect(c,rx,ry,2,3,P.paper);dot(c,rx,ry+1,P.violet);};
  card('left',[[2,8],[6,6],[7,13],[3,15]],[[3,9],[5,8],[6,12],[4,13]],4,9);
  card('right',[[25,6],[29,8],[28,15],[24,13]],[[26,8],[28,9],[27,13],[25,12]],26,9);
  card('upper_left',[[7,2],[12,1],[13,7],[8,8]],[[8,3],[11,2],[12,6],[9,7]],9,3);
  card('upper_right',[[20,1],[25,3],[24,8],[19,6]],[[21,2],[24,4],[23,7],[20,5]],21,3);
  // Open book with a real crease and alternating fore-edge pages.
  poly(c,[[2,23],[8,18],[16,22],[24,18],[30,23],[25,28],[16,31],[6,28]],P.ink);
  poly(c,[[3,23],[8,20],[16,23],[24,20],[29,23],[25,27],[16,30],[7,27]],P.goldDark);
  poly(c,[[4,22],[8,19],[16,23],[24,19],[28,22],[24,26],[16,28],[8,26]],P.paperShade);
  poly(c,[[5,22],[8,20],[15,23],[15,27],[8,25]],P.paper);poly(c,[[17,23],[24,20],[27,22],[24,25],[17,27]],P.paper);
  line(c,8,22,12,24,P.violetDark);line(c,8,24,12,25,P.goldDark);line(c,20,23,24,22,P.violetDark);line(c,20,25,24,24,P.goldDark);
  line(c,16,23,16,28,P.gold);line(c,7,27,15,29,P.goldLight);line(c,17,29,25,27,P.gold);
  // Faceted purple orb is the dominant shape, well separated from guidebook.
  ellipse(c,16,14,8,8,P.ink);ellipse(c,16,13,7,7,P.violetDark);ellipse(c,16,12,6,6,P.violet);
  poly(c,[[11,10],[13,7],[18,7],[21,10],[20,14],[17,16],[12,14]],P.violetLight);
  poly(c,[[12,8],[15,7],[18,8],[15,9],[12,12],[10,12]],P.violetPale);
  poly(c,[[11,15],[15,17],[20,14],[22,12],[21,17],[17,20],[13,18]],P.violet);
  spark(c,14,10,P.white);spark(c,18,15,P.white);dot(c,12,14,P.violetPale);dot(c,19,10,P.white);dot(c,16,18,P.violetPale);
  dot(c,16,3,P.violetPale);dot(c,29,18,P.goldLight);dot(c,3,18,P.goldLight);
  return c;
}
function guide(){
  const c=new Canvas(32,32);
  poly(c,[[5,5],[12,2],[28,6],[28,25],[23,29],[7,30],[3,26],[3,8]],P.ink);
  poly(c,[[5,8],[11,4],[26,7],[26,24],[22,27],[6,28],[4,25],[4,9]],P.navy);
  poly(c,[[9,7],[13,4],[25,7],[25,24],[10,28]],P.goldDark);
  poly(c,[[10,8],[13,5],[24,8],[24,23],[11,26]],P.pinkShade);
  poly(c,[[11,8],[14,6],[23,9],[23,23],[12,25]],P.pink);
  line(c,12,9,12,23,P.pinkLight);line(c,14,7,22,9,P.pinkLight);
  // Spine bands and exposed cream page edge make this clearly a closed book.
  line(c,5,9,5,25,P.navyLight);for(const y of [10,16,23])line(c,4,y,8,y+1,P.gold);
  poly(c,[[8,28],[11,27],[24,25],[22,27],[9,29]],P.paper);line(c,11,28,22,26,P.paperShade);
  line(c,9,7,13,5,P.goldLight);line(c,11,26,24,23,P.gold);line(c,24,8,24,22,P.gold);
  // Navy cat-ear cartouche and a raised pink four-toed paw.
  poly(c,[[13,15],[13,10],[16,12],[20,11],[22,14],[22,20],[18,23],[14,21]],P.goldLight);
  poly(c,[[14,15],[14,12],[16,14],[20,12],[21,15],[21,20],[18,22],[15,20]],P.navy);
  rect(c,15,15,1,2,P.pinkLight);rect(c,17,14,1,2,P.pinkLight);rect(c,19,14,1,2,P.pinkLight);rect(c,20,16,1,2,P.pinkLight);
  poly(c,[[16,18],[18,17],[20,19],[19,21],[17,20],[15,21],[15,19]],P.jelly);line(c,16,18,18,18,P.pinkLight);dot(c,17,18,P.white);
  // Golden fore-edge clasp and the sky-blue lower ribbon.
  rect(c,23,15,5,4,P.ink);rect(c,23,16,4,2,P.gold);dot(c,24,16,P.goldLight);dot(c,26,16,P.goldDark);
  poly(c,[[10,26],[13,26],[13,30],[11,29],[10,30]],P.ink);poly(c,[[11,27],[12,27],[12,29],[11,28]],P.sky);
  spark(c,28,3,P.goldLight);
  return c;
}

function validate(id,c){
  let visible=0,minX=32,minY=32,maxX=-1,maxY=-1;const colors=new Set();
  for(let y=0;y<32;y++)for(let x=0;x<32;x++){
    const o=(y*32+x)*4,a=c.data[o+3];if(a!==0&&a!==255)throw Error('Non-binary alpha '+id);
    if(a){visible++;minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);colors.add(c.data.subarray(o,o+3).toString('hex'));}
  }
  if(minX<1||minY<1||maxX>30||maxY>30||visible<50||visible>950)throw Error('Invalid silhouette/margin '+id+' '+JSON.stringify({minX,minY,maxX,maxY,visible}));
  return{id,dimensions:[32,32],bitDepth:8,colorType:6,binaryAlpha:true,transparentMargin:1,visiblePixels:visible,paletteColors:colors.size,bounds:[minX,minY,maxX,maxY]};
}
export function buildHeroIcons(){
  const icons=[['weapon_berserker_greatsword',greatsword()],['weapon_priest_staff',staff()],['weapon_arcane_orb_tome',orb()],['guidebook',guide()]],reports=icons.map(([id,c])=>validate(id,c));
  for(const[id,c]of icons)c.save(path.join(OUT,'textures/item',id+'.png'));
  const board=new Canvas(1080,390);board.rect(0,0,1080,390,'#202330');board.text('HERO RELICS / ORIGINAL 32PX MASTERS',16,12,'#FFF0D2',2);
  icons.forEach(([id,c],i)=>{
    const x=12+i*268;for(let y=0;y<256;y+=32)for(let xx=0;xx<256;xx+=32)board.rect(x+xx,50+y,32,32,(xx+y)%64?'#3B3C4B':'#2B2E3C');
    board.blit(c,x,50,8);board.text(id,x,318,'#EADAA7',1);
    board.rect(x,341,32,32,'#F1EDE2');board.blit(c,x,341);board.rect(x+44,341,32,32,'#252538');board.blit(c,x+44,341);
    const small=new Canvas(16,16);for(let yy=0;yy<16;yy++)for(let xx=0;xx<16;xx++){const o=((yy*2+1)*32+xx*2+1)*4;small.pixel(xx,yy,Array.from(c.data.subarray(o,o+4)));}
    board.blit(small,x+92,349);board.text('32 / 32 / 16',x+122,352,'#ADB3C3',1);
  });
  board.save(path.join(REVIEW,'hero-icons.png'));
  fs.writeFileSync(path.join(REVIEW,'hero-icons-validation.json'),JSON.stringify({count:4,antialiasing:false,icons:reports},null,2)+'\n');
  return reports;
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1]))console.log(JSON.stringify(buildHeroIcons(),null,2));
