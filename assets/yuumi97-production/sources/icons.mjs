/**
 * Yuumi 97Land — deliberately drawn 32px inventory icon masters.
 * All contours, facets and small motifs are placed on the pixel grid.
 * No antialiasing, image scaling, external font or generated-image dependency.
 * Run: node assets/yuumi97-production/sources/icons.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { Canvas, ROOT, OUT, REVIEW } from '../tools/asset-kit.mjs';
import { icons as EXPECTED, newIcons } from './asset-contract.mjs';
import { drawV3 } from './icons-v3.mjs';

const P = {
  ink:'#29233F', ink2:'#48405D', gold:'#E9B94F', goldD:'#A86B35', goldS:'#765039',
  goldL:'#FFF1B3', cream:'#FFF0D2', white:'#FFFDF2', teal:'#48C8C6', tealD:'#207F92',
  tealL:'#B6F4DF', pink:'#EA8FB8', pinkD:'#A64E82', pinkL:'#FFD2D8',
  blue:'#548ED5', blueD:'#345494', blueL:'#B3D7F3', violet:'#8F5EC0', violetD:'#503876',
  violetL:'#D7A3E8', green:'#68BD83', greenD:'#327C65', silver:'#B9CAD2', silverL:'#EDF4EE',
};
const icons = new Map();
const r = (c,x,y,w,h,k)=>c.rect(x,y,w,h,k);
const p = (c,xy,k)=>c.polygon(xy,k);
const e = (c,x,y,rx,ry,k)=>c.ellipse(x,y,rx,ry,k);
const l = (c,x,y,x2,y2,k,w=1)=>c.line(x,y,x2,y2,k,w);
const px = (c,x,y,k)=>c.pixel(x,y,k);
function icon(id, draw) {const c=new Canvas(32,32);draw(c);icons.set(id,c);return c;}
function spark(c,x,y,k=P.goldL,size=2) {
  l(c,x-size,y,x+size,y,k);l(c,x,y-size,x,y+size,k);
  if(size>2){px(c,x-1,y-1,k);px(c,x+1,y+1,k);}
}
function gem(c,x,y,rx,ry,colors=[P.ink,P.tealD,P.teal,P.tealL]) {
  p(c,[[x,y-ry],[x+rx,y],[x,y+ry],[x-rx,y]],colors[0]);
  p(c,[[x,y-ry+1],[x+rx-1,y],[x,y+ry-1],[x-rx+1,y]],colors[1]);
  p(c,[[x,y-ry+1],[x,y+ry-2],[x-rx+1,y]],colors[2]);
  l(c,x,y-ry+2,x-rx+2,y,colors[3]);px(c,x,y-ry+2,P.white);
}
function paw(c,x,y,s=1,base=P.pink,light=P.pinkL,shadow=P.pinkD) {
  // Compact 9 x 9 mark, with four separately readable toe pads.
  p(c,[[x+2*s,y+5*s],[x+4*s,y+3*s],[x+6*s,y+5*s],[x+7*s,y+7*s],[x+5*s,y+8*s],[x+3*s,y+7*s],[x+s,y+8*s],[x,y+6*s]],base);
  r(c,x+2*s,y+5*s,3*s,s,light);r(c,x+5*s,y+7*s,2*s,s,shadow);
  r(c,x,y+2*s,s,2*s,base);r(c,x+2*s,y,s,2*s,light);r(c,x+5*s,y,s,2*s,base);r(c,x+7*s,y+2*s,s,2*s,base);
}
function fish(c,x,y,light=P.cream,body=P.gold) {
  p(c,[[x,y+2],[x+2,y],[x+6,y],[x+8,y+2],[x+10,y],[x+10,y+5],[x+8,y+3],[x+5,y+5],[x+2,y+4]],body);
  l(c,x+2,y+1,x+6,y+1,light);px(c,x+2,y+2,P.ink);l(c,x+5,y+2,x+5,y+3,light);
}
const DIGITS = {
  '0':['111','101','101','101','111'], '1':['010','110','010','010','111'],
  '2':['111','001','111','100','111'], '3':['111','001','111','001','111'],
  '7':['111','001','010','010','010'], '9':['111','101','111','001','111'],
  K:['101','101','110','101','101'],
};
function digits(c,text,x,y,k) {for(const ch of text){const d=DIGITS[ch];if(d)for(let yy=0;yy<5;yy++)for(let xx=0;xx<3;xx++)if(d[yy][xx]==='1')px(c,x+xx,y+yy,k);x+=4;}}

// Foil stick family: sealed diagonal packet, narrow torn opening and edible tip.
function packet(c,{base,lit,dark,trim=P.cream,mark}) {
  p(c,[[3,25],[17,8],[20,7],[27,13],[27,16],[13,29],[9,30]],P.ink);
  p(c,[[5,25],[18,10],[20,9],[25,14],[25,16],[12,28],[9,28]],dark);
  p(c,[[5,24],[18,9],[21,10],[12,25],[8,27]],base);
  p(c,[[7,24],[19,10],[21,11],[9,26]],lit);
  p(c,[[13,22],[21,13],[24,15],[16,24]],base);
  l(c,4,25,10,30,P.ink);l(c,6,25,11,28,trim);l(c,7,24,12,27,dark);
  p(c,[[18,8],[20,5],[23,4],[26,6],[28,8],[26,12],[24,13]],P.ink);
  p(c,[[20,7],[21,5],[24,6],[26,8],[25,11],[24,11]],P.cream);
  l(c,22,6,25,9,'#F6BB8C');px(c,23,5,P.white);px(c,25,7,P.white);
  l(c,18,9,24,14,trim);l(c,18,10,24,15,P.ink2);
  mark(c);
}
icon('churu_salmon',c=>{
  packet(c,{base:'#DB657E',lit:'#FFA5AA',dark:'#993E66',mark:c=>{
    p(c,[[10,18],[16,12],[21,16],[15,22]],P.cream);
    p(c,[[12,18],[16,14],[19,17],[15,20]],'#F59C69');
    l(c,13,17,16,19,'#FFE2B9');l(c,15,15,18,17,'#FFE2B9');px(c,11,23,P.goldL);
  }});
});
icon('churu_tuna',c=>{
  packet(c,{base:P.blue,lit:P.blueL,dark:P.blueD,mark:c=>{
    p(c,[[10,18],[16,12],[21,16],[15,23]],'#D9EFF0');
    p(c,[[12,18],[14,16],[17,16],[18,17],[20,16],[19,20],[17,19],[15,21]],P.tealD);
    px(c,14,17,P.white);l(c,12,23,14,25,P.gold);l(c,10,23,12,25,P.goldL);
  }});
  px(c,6,19,P.white);
});
icon('churu_golden',c=>{
  packet(c,{base:P.gold,lit:P.goldL,dark:P.goldD,trim:P.white,mark:c=>{
    p(c,[[10,18],[16,12],[22,17],[15,24]],P.violetD);
    p(c,[[12,18],[13,15],[16,17],[18,14],[19,19],[15,22]],P.goldL);
    l(c,14,20,18,16,P.gold);px(c,15,19,P.pink);l(c,7,24,10,26,P.white);
  }});
  spark(c,5,6,P.goldL,2);spark(c,27,24,P.goldL,2);px(c,9,4,P.gold);
});

icon('casino_chip',c=>{
  e(c,16,17,13,13,P.ink);e(c,16,16,12,12,P.goldD);e(c,16,15,12,11,P.gold);
  e(c,16,15,10,9,P.goldL);e(c,16,16,9,9,P.ink);e(c,16,15,8,8,'#B84866');
  e(c,15,14,7,7,'#DC6C87');l(c,8,7,12,5,P.white);l(c,5,12,5,16,P.goldL);
  paw(c,12,11,1,P.cream,P.white,P.pinkL);
  for(const [x,y] of [[16,4],[27,15],[16,27],[5,15]]){px(c,x,y,P.white);}
  l(c,23,24,26,21,P.goldD);l(c,10,26,15,28,P.goldD);
});

icon('jackpot_crown',c=>{
  p(c,[[3,8],[8,10],[13,7],[18,8],[24,7],[28,10],[26,25],[6,26]],P.ink);
  p(c,[[4,9],[11,12],[16,8],[22,12],[27,10],[25,21],[7,22]],'#8C596B');
  p(c,[[2,6],[7,9],[8,14],[13,11],[16,4],[20,11],[24,13],[27,6],[30,5],[27,26],[5,27]],P.ink);
  p(c,[[4,8],[7,10],[9,17],[14,12],[16,7],[19,13],[24,17],[28,8],[25,24],[7,25]],P.gold);
  p(c,[[4,8],[7,10],[9,17],[7,22],[6,22]],P.goldL);
  p(c,[[16,7],[17,12],[15,17],[12,17]],P.goldL);
  p(c,[[27,10],[26,22],[23,23],[24,18]],P.goldD);
  r(c,6,23,20,3,P.goldD);l(c,7,22,25,22,P.goldL);l(c,7,26,24,26,P.ink);
  gem(c,16,19,3,4,[P.ink,P.pinkD,P.pink,P.pinkL]);gem(c,9,20,2,2);gem(c,23,20,2,2);
  px(c,16,4,P.goldL);px(c,3,6,P.goldL);px(c,29,5,P.goldL);
  spark(c,4,3,P.goldL,1);
});

icon('torn_page',c=>{
  p(c,[[7,3],[22,3],[27,8],[26,23],[22,22],[22,26],[17,25],[15,29],[11,27],[7,28],[5,25],[5,6]],P.ink);
  p(c,[[7,5],[21,4],[25,9],[24,21],[20,21],[20,25],[17,23],[14,27],[11,25],[7,26],[7,22]],'#CDB184');
  p(c,[[8,5],[21,5],[23,9],[22,20],[18,22],[16,24],[12,23],[8,25],[7,9]],P.cream);
  p(c,[[8,5],[19,5],[19,7],[10,7],[8,20]],P.white);
  p(c,[[21,4],[21,9],[26,9]],P.goldD);p(c,[[22,6],[22,8],[25,8]],P.goldL);
  l(c,10,9,19,9,P.gold);l(c,10,21,16,21,P.goldD);
  p(c,[[15,11],[19,15],[15,19],[11,15]],P.tealD);p(c,[[15,12],[18,15],[15,18],[12,15]],P.teal);
  r(c,14,14,2,3,P.goldL);px(c,15,11,P.white);px(c,11,18,P.gold);
  l(c,9,24,11,23,'#E5C99E');l(c,18,19,21,19,'#B79C78');
});

icon('book_of_thresholds',c=>{
  // Three-quarter closed volume: a real page block, clasp and raised front crest.
  p(c,[[8,3],[25,3],[28,6],[28,25],[23,29],[5,29],[3,26],[3,9]],P.ink);
  p(c,[[5,22],[25,20],[26,24],[22,27],[6,27]],'#CABFA9');
  p(c,[[6,22],[24,21],[24,24],[21,26],[6,26]],P.cream);
  l(c,8,24,21,24,'#B6A58E');l(c,8,26,20,26,P.white);
  p(c,[[7,4],[24,4],[26,6],[24,22],[6,24],[4,22],[4,9]],P.blueD);
  p(c,[[8,5],[23,5],[24,6],[22,21],[8,22],[6,20],[6,9]],P.tealD);
  p(c,[[9,6],[22,6],[21,9],[10,9],[8,19],[7,18],[7,10]],P.teal);
  l(c,5,9,5,21,P.goldD);l(c,7,6,7,9,P.goldL);l(c,8,5,12,5,P.goldL);
  p(c,[[19,5],[23,5],[23,9],[21,8],[21,7],[19,7]],P.gold);
  p(c,[[7,19],[9,20],[9,22],[12,21],[12,23],[6,24]],P.gold);
  p(c,[[21,19],[24,18],[24,22],[20,22]],P.goldD);px(c,22,20,P.goldL);
  // Cat-ear crest embracing the central turquoise diamond.
  p(c,[[10,11],[10,9],[13,11],[17,11],[20,8],[20,13],[18,17],[14,19],[10,16]],P.ink);
  p(c,[[11,11],[13,12],[17,12],[19,10],[19,13],[17,16],[14,17],[11,15]],P.gold);
  gem(c,15,14,3,4,[P.goldD,P.tealD,P.teal,P.tealL]);
  r(c,24,13,3,4,P.ink);r(c,24,14,2,2,P.gold);px(c,24,14,P.goldL);
  l(c,27,7,27,22,P.goldD);px(c,8,5,P.white);
});

icon('zoomies_skewer',c=>{
  l(c,4,28,26,5,P.ink,3);l(c,4,27,26,5,'#B78657');l(c,5,26,24,7,P.goldL);
  // Grilled fish, tail and head are separate from the bamboo skewer.
  p(c,[[8,17],[6,12],[12,14],[15,10],[21,9],[26,12],[27,16],[23,20],[18,22],[12,20],[8,22]],P.ink);
  p(c,[[9,17],[8,14],[12,16],[16,12],[21,11],[24,12],[25,16],[22,19],[18,20],[13,18],[10,20]],'#D77945');
  p(c,[[12,16],[16,12],[21,11],[24,12],[23,14],[18,14],[14,17]],'#FFD492');
  p(c,[[14,18],[18,16],[23,15],[24,17],[21,19],[18,20]],'#F1A65F');
  l(c,17,13,20,17,'#925B43');l(c,14,16,17,19,'#925B43');px(c,24,14,P.ink);px(c,23,13,P.white);
  l(c,8,15,10,17,P.goldL);l(c,10,18,9,20,P.goldD);px(c,21,19,'#A35D3B');
  p(c,[[5,11],[3,8],[6,8],[7,5],[9,9],[8,11]],P.greenD);p(c,[[5,9],[6,8],[7,10],[7,6],[8,9]],P.green);
  spark(c,27,24,P.tealL,2);px(c,3,18,P.teal);
});

icon('kneading_bento',c=>{
  p(c,[[5,5],[27,5],[30,9],[29,26],[25,29],[4,28],[2,24],[2,9]],P.ink);
  p(c,[[4,8],[7,6],[26,6],[28,9],[27,25],[24,27],[5,26],[3,23]],'#984856');
  r(c,5,8,21,16,'#C56665');l(c,5,7,26,7,P.pinkL);l(c,4,9,4,23,'#E29484');l(c,7,26,24,26,'#6D3948');
  r(c,6,9,12,14,'#5A564B');r(c,19,9,7,14,P.ink2);
  // Leaf bed with individually placed crinkles.
  p(c,[[6,18],[8,19],[10,18],[12,19],[14,18],[17,20],[17,23],[6,23]],P.greenD);
  l(c,7,21,11,21,P.green);l(c,12,22,16,20,P.green);px(c,8,19,P.tealL);
  // Cat-shaped rice mound.
  p(c,[[7,15],[7,10],[10,12],[13,12],[16,10],[17,15],[16,19],[13,21],[10,20],[8,18]],P.cream);
  p(c,[[8,14],[8,12],[10,14],[14,13],[16,12],[15,16],[10,18],[8,17]],P.white);
  px(c,10,15,P.ink);px(c,14,15,P.ink);px(c,12,17,P.pinkD);l(c,11,18,13,18,P.ink2);
  // Salmon slices and two tamago cubes in their own compartment.
  r(c,20,10,5,5,'#EC8A67');l(c,20,10,24,10,'#FFD0A6');l(c,20,12,24,13,P.cream);px(c,24,14,'#B85645');
  r(c,20,17,5,5,P.gold);l(c,20,17,23,17,P.goldL);l(c,22,18,22,21,P.goldD);px(c,20,20,P.goldL);
  px(c,27,12,P.gold);px(c,27,22,P.gold);
});

icon('catnip_elixir',c=>{
  // Corked potion glass with a faceted heel, bright liquid surface and leaf inside.
  p(c,[[12,2],[20,2],[21,5],[19,7],[20,11],[25,15],[28,21],[26,27],[22,30],[10,30],[6,27],[4,21],[7,15],[12,11],[12,7],[10,5]],P.ink);
  r(c,12,3,8,3,'#B1825B');r(c,13,3,5,1,'#E8C294');px(c,18,5,'#855E49');
  r(c,13,7,6,5,P.tealD);r(c,14,7,2,4,P.blueL);
  p(c,[[13,11],[19,11],[24,16],[26,21],[24,26],[21,28],[11,28],[8,26],[6,21],[8,17]],P.tealD);
  p(c,[[13,12],[19,12],[23,17],[25,21],[23,25],[20,27],[11,27],[8,25],[7,21],[9,17]],'#79B8C7');
  p(c,[[9,19],[13,18],[19,18],[23,19],[24,22],[22,26],[11,26],[8,23]],P.greenD);
  e(c,16,19,7,2,P.tealL);p(c,[[9,20],[15,21],[23,20],[22,25],[19,27],[12,26]],'#45B18A');
  p(c,[[16,24],[13,21],[13,17],[16,19],[20,16],[20,21],[17,24]],P.greenD);l(c,16,23,18,19,P.tealL);px(c,14,19,P.tealL);
  l(c,9,16,7,20,P.white);l(c,8,22,9,24,P.blueL);px(c,12,14,P.white);px(c,23,23,P.tealL);
  r(c,11,9,10,3,P.violetD);r(c,12,9,8,1,P.violetL);p(c,[[19,10],[22,11],[22,16],[19,14]],P.violet);px(c,20,12,P.pinkL);
  spark(c,27,8,P.tealL,2);px(c,4,13,P.goldL);
});

icon('royal_feast_can',c=>{
  // Open tin: raised lid behind, metal rolled rim, visible marbled fish, royal label.
  e(c,17,7,10,5,P.ink);e(c,17,7,9,4,P.silver);e(c,17,7,7,3,'#7C97AD');
  l(c,12,5,20,5,P.silverL);e(c,18,7,3,1,P.ink2);e(c,18,6,2,1,P.silverL);
  p(c,[[4,13],[28,13],[27,25],[24,28],[8,28],[4,24]],P.ink);
  p(c,[[5,15],[27,15],[26,24],[23,26],[8,26],[5,23]],P.goldD);
  r(c,6,16,20,8,P.gold);r(c,7,18,18,6,P.blueD);l(c,7,17,24,17,P.goldL);
  l(c,6,16,6,22,P.goldL);l(c,25,18,25,23,'#8F632F');
  e(c,16,13,12,5,P.ink);e(c,16,13,11,4,P.silver);e(c,16,13,9,3,'#E88479');
  p(c,[[8,12],[13,10],[16,12],[13,15],[8,14]],'#F5B49A');
  p(c,[[16,12],[20,11],[24,12],[23,14],[18,15]],'#D8646A');
  l(c,10,11,12,14,P.cream);l(c,18,12,20,14,P.pinkL);l(c,7,12,10,10,P.white);
  l(c,7,25,23,25,P.goldL);p(c,[[9,20],[11,21],[12,19],[14,21],[16,19],[15,23],[10,23]],P.goldL);
  digits(c,'97',18,19,P.goldL);px(c,27,18,P.silverL);
});

icon('boss_core_rat',c=>{
  // Cheese wedge, fractured rune crystal and orbiting sewer magic.
  p(c,[[6,11],[20,5],[28,11],[27,24],[13,29],[4,22]],P.ink);
  p(c,[[6,12],[20,7],[26,12],[13,18]],P.goldL);
  p(c,[[5,13],[13,18],[13,27],[6,22]],'#D6973C');
  p(c,[[14,18],[27,13],[26,23],[14,27]],'#F0BA4E');
  p(c,[[7,12],[20,8],[24,11],[14,15]],'#FFE08B');
  l(c,14,19,14,25,P.goldL);l(c,6,14,6,20,'#F7C764');
  e(c,19,11,2,1,'#C5893E');e(c,10,14,2,1,'#C5893E');e(c,22,21,2,2,'#BA753E');px(c,23,20,'#8F5B37');e(c,9,21,1,2,'#8E5D3E');
  p(c,[[17,16],[20,14],[23,16],[22,19],[18,22],[16,20]],P.ink);gem(c,20,18,3,4,[P.ink,P.violetD,P.violet,P.violetL]);
  l(c,7,7,10,5,P.violet);l(c,10,5,14,5,P.violetL);px(c,7,8,P.violetD);
  l(c,3,16,2,20,P.violetL);l(c,22,28,26,26,P.violet);spark(c,28,5,P.violetL,2);
  px(c,6,27,P.violetL);px(c,17,2,P.goldL);
});

icon('boss_core_hound',c=>{
  // Obsidian collar ring, four metal studs, flame core and rising tongues.
  p(c,[[8,8],[10,3],[13,7],[16,2],[19,7],[24,4],[24,11],[28,14],[26,25],[21,29],[10,28],[4,22],[4,13]],P.ink);
  p(c,[[9,11],[11,6],[14,10],[16,5],[18,11],[23,7],[22,13],[25,16],[23,24],[18,27],[11,25],[6,20]],'#B54241');
  p(c,[[11,11],[13,8],[15,13],[17,8],[20,13],[23,14],[22,21],[18,25],[11,23],[8,18]],'#EF7F3D');
  e(c,16,18,11,10,P.ink);e(c,16,18,9,8,'#74424F');e(c,16,17,8,7,'#AF4C45');
  e(c,16,17,6,6,P.ink);e(c,16,17,5,5,'#E5773F');e(c,16,17,3,4,P.gold);e(c,15,16,2,2,P.goldL);
  l(c,10,12,13,10,'#D7785B');l(c,8,17,8,21,'#A75B58');l(c,20,24,23,21,'#512D42');
  for(const [x,y,dx,dy] of [[5,16,-3,0],[26,17,3,0],[16,8,0,-3],[16,27,0,3]]){
    p(c,[[x-2,y-2],[x+2,y-2],[x+dx,y+dy],[x+2,y+2],[x-2,y+2]],P.ink);
    p(c,[[x-1,y-1],[x+1,y-1],[x+dx,y+dy],[x,y+1]],P.silver);
    px(c,x-1,y-1,P.silverL);
  }
  px(c,6,8,P.goldL);px(c,27,7,'#EF7F3D');px(c,3,25,'#EF7F3D');
});

icon('boss_core_void',c=>{
  // Pointed void geode: readable cut crystal silhouette, eye and separate shards.
  p(c,[[16,2],[24,7],[29,16],[25,25],[16,30],[7,24],[3,15],[8,7]],P.ink);
  p(c,[[16,4],[23,8],[27,16],[23,24],[16,28],[8,23],[5,15],[9,8]],P.violetD);
  p(c,[[16,4],[16,14],[9,8],[5,15],[8,22],[12,18],[16,28],[17,17],[27,16],[23,8]],P.violet);
  p(c,[[16,4],[22,8],[19,11],[16,13]],P.violetL);p(c,[[9,9],[12,11],[9,15],[6,15]],'#B778DD');
  p(c,[[8,16],[12,11],[19,11],[24,16],[19,22],[12,22]],P.ink);
  p(c,[[9,16],[13,12],[18,12],[23,16],[18,21],[13,21]],P.tealD);
  p(c,[[10,16],[13,13],[18,13],[22,16],[18,20],[13,20]],P.tealL);
  p(c,[[13,14],[18,14],[19,16],[18,19],[13,19],[12,17]],P.teal);
  r(c,15,14,3,6,P.ink);px(c,14,14,P.white);px(c,17,19,P.blueD);
  l(c,7,19,10,23,P.violetL);l(c,22,23,18,26,'#5D387F');
  p(c,[[3,4],[6,7],[3,9],[2,6]],P.violetL);p(c,[[28,25],[30,27],[28,30],[26,28]],P.teal);px(c,26,3,P.violetL);
});

icon('you_and_me_bell',c=>{
  // Bow knot, short ribbon tails, hammered gold bell body and dark acoustic slit.
  p(c,[[4,5],[7,3],[12,5],[16,8],[20,4],[26,3],[29,6],[27,10],[21,12],[24,16],[20,17],[16,12],[12,17],[8,15],[11,11],[6,10]],P.ink);
  p(c,[[5,5],[7,4],[12,6],[15,9],[11,10],[7,9]],P.pink);p(c,[[18,9],[22,5],[26,4],[27,6],[26,9],[21,10]],P.pinkD);
  p(c,[[7,5],[11,6],[12,8],[7,7]],P.pinkL);p(c,[[21,7],[25,5],[25,7],[22,9]],P.pink);
  p(c,[[12,10],[15,11],[12,15],[10,14]],P.pinkD);p(c,[[18,10],[21,12],[22,15],[20,14]],P.pink);
  r(c,14,7,5,5,P.ink);r(c,15,8,3,3,P.pink);px(c,15,8,P.pinkL);
  r(c,14,12,5,3,P.goldD);r(c,15,12,3,1,P.goldL);
  e(c,16,22,10,8,P.ink);e(c,16,21,9,7,P.goldD);e(c,15,20,8,6,P.gold);
  p(c,[[9,18],[12,15],[17,15],[19,17],[12,18],[10,21],[8,21]],P.goldL);
  p(c,[[21,17],[24,20],[23,24],[20,26],[16,27],[15,25],[20,23]],'#C18336');
  l(c,9,24,23,24,P.goldS);e(c,16,23,2,2,P.ink);l(c,16,23,16,28,P.ink);l(c,11,27,21,27,P.goldD);
  r(c,11,17,2,2,P.white);px(c,22,22,P.goldL);px(c,8,23,P.goldL);
});

icon('prowling_wand',c=>{
  // Diagonal wrapped staff terminating in a paw and floating star core.
  l(c,4,28,20,12,P.ink,5);l(c,4,27,20,11,P.goldD,3);l(c,5,26,20,11,P.teal,2);
  for(const [x,y] of [[6,24],[9,21],[12,18],[15,15]]){l(c,x-1,y-1,x+2,y+2,P.pink,2);px(c,x,y-1,P.pinkL);}
  l(c,3,27,5,29,P.goldL);l(c,4,25,7,28,P.ink2);px(c,3,28,P.gold);
  p(c,[[13,10],[13,6],[15,4],[18,5],[19,2],[22,2],[24,5],[27,4],[30,6],[29,10],[28,11],[28,14],[24,18],[18,17],[15,14]],P.ink);
  p(c,[[14,7],[15,5],[17,6],[18,10],[17,12],[15,11]],P.pink);
  p(c,[[20,4],[22,4],[23,7],[22,9],[20,8]],P.pinkL);
  p(c,[[26,6],[28,6],[28,8],[26,10],[24,9]],P.pink);
  p(c,[[18,12],[20,10],[24,11],[27,13],[25,16],[21,17],[17,14]],P.pinkD);
  p(c,[[18,12],[20,11],[24,12],[25,14],[22,15],[18,14]],P.pink);px(c,19,12,P.pinkL);
  // Tiny gold star lies above the central jelly, rather than a yellow blob.
  p(c,[[22,8],[23,10],[26,11],[23,12],[22,15],[21,12],[19,11],[21,10]],P.gold);
  px(c,22,10,P.goldL);px(c,21,11,P.white);l(c,15,16,17,18,P.goldL);
  spark(c,8,7,P.tealL,2);px(c,11,3,P.goldL);px(c,29,21,P.goldL);
});

icon('final_chapter_tome',c=>{
  // Open spellbook, two visibly distinct curved page fans and magical arc.
  l(c,3,10,5,6,P.tealD);l(c,5,6,10,4,P.teal);l(c,11,3,20,3,P.tealL);l(c,21,4,26,6,P.teal);l(c,26,6,28,10,P.tealD);
  p(c,[[3,14],[11,12],[16,15],[22,12],[29,14],[30,27],[23,29],[16,28],[9,29],[2,26]],P.ink);
  p(c,[[3,16],[10,14],[16,17],[22,14],[28,15],[28,26],[22,27],[16,26],[10,27],[3,25]],P.violetD);
  p(c,[[4,12],[10,10],[16,13],[22,10],[28,12],[27,24],[22,25],[16,24],[10,25],[4,23]],P.ink);
  p(c,[[5,13],[10,11],[15,14],[15,23],[10,22],[5,23]],'#DBC8AD');
  p(c,[[17,14],[22,11],[27,13],[26,23],[22,22],[17,23]],P.cream);
  p(c,[[5,13],[10,12],[14,14],[14,21],[10,20],[6,21]],P.cream);
  p(c,[[18,14],[22,12],[26,13],[25,21],[22,20],[18,21]],P.white);
  l(c,16,14,16,27,P.goldD);px(c,16,13,P.goldL);l(c,5,24,10,26,P.gold);l(c,22,26,27,24,P.gold);
  // Conjugate runes: geometric, legible clusters instead of pretend tiny text.
  l(c,8,15,11,16,P.violet);l(c,8,16,8,18,P.violet);l(c,8,18,12,19,P.violet);px(c,11,17,P.pink);
  p(c,[[22,14],[24,16],[22,18],[20,16]],P.teal);px(c,22,16,P.blueD);l(c,19,20,24,19,P.goldD);
  p(c,[[13,6],[16,4],[19,6],[18,10],[16,12],[14,10]],P.violetD);gem(c,16,8,3,4,[P.violetD,P.violet,P.violetL,P.pinkL]);
  px(c,7,8,P.goldL);px(c,25,9,P.goldL);
});

icon('nine_lives_charm',c=>{
  // Jade cat medallion, gold ears, nine perimeter notches and a curled tail.
  p(c,[[13,3],[19,3],[20,6],[18,9],[14,9],[12,6]],P.ink);r(c,14,4,4,3,P.gold);r(c,15,5,2,2,P.ink);
  p(c,[[6,8],[11,11],[16,9],[21,11],[27,8],[27,21],[24,26],[17,30],[9,26],[5,20]],P.ink);
  p(c,[[7,10],[11,13],[16,11],[21,13],[26,10],[25,21],[22,25],[17,28],[10,25],[7,20]],P.goldD);
  p(c,[[8,12],[12,14],[16,12],[21,14],[25,12],[24,21],[21,24],[17,27],[11,24],[8,20]],P.greenD);
  p(c,[[10,14],[12,15],[16,13],[21,15],[23,14],[22,21],[19,24],[16,25],[12,23],[10,20]],'#42AA8B');
  l(c,10,16,10,19,P.tealL);l(c,12,14,16,13,P.tealL);px(c,22,18,P.green);
  // The central 9 is an actual bent gold tail.
  e(c,16,18,4,4,P.ink);e(c,16,18,3,3,P.gold);e(c,16,18,1,1,P.greenD);l(c,19,18,19,23,P.gold);l(c,18,24,15,24,P.gold);
  l(c,14,16,16,15,P.goldL);px(c,19,23,P.goldL);
  for(const [x,y] of [[9,10],[13,11],[17,10],[22,11],[25,17],[24,22],[20,26],[14,26],[8,22]])px(c,x,y,P.goldL);
  spark(c,3,5,P.tealL,1);spark(c,28,27,P.tealL,1);
});

icon('slot_seven',c=>{
  p(c,[[4,4],[28,4],[29,10],[24,15],[19,23],[18,29],[8,29],[9,23],[15,14],[17,12],[4,12],[2,10]],P.ink);
  p(c,[[4,5],[27,5],[27,10],[22,16],[17,24],[17,27],[10,27],[11,23],[17,14],[20,10],[4,10]],P.goldD);
  p(c,[[5,5],[27,5],[27,8],[24,12],[20,17],[16,24],[16,27],[10,27],[12,22],[19,12],[22,9],[4,9]],P.gold);
  p(c,[[5,7],[25,7],[25,9],[22,13],[18,20],[14,25],[12,25],[14,21],[21,10],[5,10]],'#C74366');
  l(c,5,6,26,6,P.goldL);l(c,6,7,24,7,P.white);l(c,23,10,16,21,'#F4889B');
  l(c,13,24,15,24,'#FFB7AE');l(c,10,28,17,28,P.ink2);spark(c,5,20,P.goldL,2);px(c,27,21,P.goldL);
});

icon('slot_yuumi',c=>{
  // Yuumi identity: swept blue ears, cream muzzle, green eyes and gold brow bell.
  p(c,[[2,5],[8,7],[11,11],[21,11],[25,7],[30,5],[29,20],[26,26],[21,29],[11,29],[5,25],[2,19]],P.ink);
  p(c,[[3,7],[8,9],[11,13],[21,13],[25,9],[29,7],[28,19],[25,24],[20,27],[11,27],[6,23],[3,18]],P.blueD);
  p(c,[[4,8],[8,10],[11,14],[21,14],[25,10],[28,8],[27,17],[24,22],[10,23],[5,18]],P.blue);
  p(c,[[5,10],[8,12],[8,15],[5,17]],P.pinkD);p(c,[[24,12],[27,10],[26,17],[23,15]],P.pink);px(c,6,11,P.pinkL);px(c,26,11,P.pinkL);
  p(c,[[9,16],[11,14],[16,16],[21,14],[24,17],[26,20],[24,25],[20,27],[11,27],[7,24],[5,20]],P.cream);
  p(c,[[10,16],[12,15],[16,17],[20,15],[23,17],[23,21],[18,22],[12,21],[7,22],[7,20]],P.white);
  // Large eyes retain distinct vertical pupils at native 32 px.
  r(c,9,17,5,4,P.greenD);r(c,19,17,5,4,P.greenD);r(c,10,17,3,3,'#8DDA92');r(c,20,17,3,3,'#8DDA92');
  r(c,12,18,1,3,P.ink);r(c,20,18,1,3,P.ink);px(c,10,17,P.white);px(c,22,17,P.white);
  p(c,[[14,22],[18,22],[16,24]],P.pink);px(c,16,24,P.ink2);l(c,13,25,15,25,P.ink2);l(c,17,25,19,25,P.ink2);
  l(c,6,22,10,23,'#C2B7A7');l(c,23,23,27,22,'#C2B7A7');px(c,8,25,P.white);
  p(c,[[14,13],[14,9],[12,7],[13,4],[16,2],[19,4],[20,7],[18,10],[18,13]],P.ink);
  p(c,[[15,12],[15,9],[13,7],[14,4],[16,3],[18,5],[19,7],[17,9],[17,12]],P.gold);
  r(c,15,4,2,3,P.goldL);px(c,16,8,P.goldD);l(c,14,13,18,13,P.goldL);
});

icon('slot_paw',c=>{
  // Thick ivory enamel around five separate pink pads.
  const pads=[[6,13,4,5],[12,7,4,5],[21,7,4,5],[26,13,4,5]];
  for(const [x,y,rx,ry] of pads){e(c,x,y,rx,ry,P.ink);e(c,x,y,rx-1,ry-1,P.cream);e(c,x,y,rx-2,ry-2,P.pink);px(c,x-1,y-2,P.pinkL);}
  p(c,[[14,13],[19,13],[23,17],[28,23],[26,28],[21,30],[16,27],[11,30],[6,28],[4,24],[7,19]],P.ink);
  p(c,[[14,14],[19,14],[22,18],[27,23],[25,27],[21,29],[16,26],[11,29],[7,27],[5,24],[8,19]],P.cream);
  p(c,[[14,16],[18,16],[21,20],[24,23],[23,26],[20,27],[16,24],[12,27],[9,26],[8,23],[11,19]],P.pinkD);
  p(c,[[14,16],[18,16],[21,20],[23,23],[20,25],[16,23],[12,25],[9,24],[11,20]],P.pink);
  p(c,[[14,17],[17,17],[18,19],[13,20],[11,23],[10,22]],P.pinkL);
  px(c,8,25,P.white);l(c,20,28,23,27,P.gold);px(c,5,11,P.white);px(c,12,4,P.white);
});

function chipRim(c,base,dark,light,trim) {
  e(c,16,18,13,12,P.ink);e(c,16,18,12,11,dark);l(c,7,25,10,27,P.ink2);
  e(c,16,15,13,12,P.ink);e(c,16,15,12,11,base);e(c,16,15,10,9,trim);e(c,16,15,8,7,dark);e(c,15,14,7,6,base);
  // Radial casino edge inserts, deliberately chunky enough at half scale.
  r(c,13,4,6,2,trim);r(c,13,25,6,2,trim);r(c,4,13,2,5,trim);r(c,27,13,2,5,trim);
  p(c,[[6,7],[8,6],[11,9],[9,11]],trim);p(c,[[23,6],[26,8],[23,11],[21,9]],trim);
  p(c,[[6,22],[8,24],[11,21],[9,19]],trim);p(c,[[24,20],[26,22],[24,24],[21,21]],trim);
  l(c,10,6,12,5,light);l(c,5,10,5,12,light);l(c,12,28,19,28,dark);px(c,25,8,light);
}
icon('chip_100',c=>{
  chipRim(c,'#D95370','#873950','#FFB1B9',P.cream);
  r(c,10,11,12,8,'#AA405E');digits(c,'100',10,12,P.cream);l(c,12,20,20,20,P.pinkL);
  px(c,16,8,P.goldL);px(c,15,22,P.goldL);px(c,17,22,P.goldL);
});
icon('chip_1000',c=>{
  chipRim(c,'#3F70B7','#253E77','#ADD9F5',P.gold);
  gem(c,16,11,3,3,[P.goldD,P.tealD,P.teal,P.tealL]);digits(c,'1K',13,15,P.goldL);
  l(c,12,22,14,22,P.goldL);l(c,18,22,20,22,P.goldL);px(c,9,14,P.blueL);px(c,23,14,P.blueL);
});
icon('chip_10000',c=>{
  chipRim(c,'#8752B5','#4D326C','#D6A9E8','#A7DDE8');
  p(c,[[12,9],[19,9],[21,11],[16,15],[11,11]],P.ink);p(c,[[13,10],[18,10],[19,11],[16,13],[12,11]],P.tealL);px(c,14,10,P.white);
  digits(c,'10K',10,16,P.cream);l(c,12,23,14,23,P.gold);l(c,18,23,20,23,P.gold);px(c,16,24,P.goldL);
});

icon('recall_bookmark',c=>{
  // Silken bookmark: thread loop, gold piping, compass gem, split pointed tail.
  e(c,16,5,5,4,P.ink);e(c,16,5,4,3,P.goldD);e(c,16,5,2,2,P.ink);l(c,13,3,16,2,P.goldL);
  p(c,[[8,7],[24,7],[25,29],[20,28],[16,24],[12,28],[7,30]],P.ink);
  p(c,[[9,8],[23,8],[23,27],[19,25],[16,22],[12,25],[9,27]],P.violetD);
  p(c,[[10,9],[21,9],[21,24],[17,21],[15,21],[11,25]],P.violet);
  l(c,9,9,9,26,P.gold);l(c,23,9,23,27,P.goldD);l(c,10,8,22,8,P.goldL);
  l(c,11,10,11,20,P.violetL);l(c,11,21,14,20,P.violetL);l(c,20,10,20,19,P.violetD);
  gem(c,16,15,5,6,[P.ink,P.goldD,P.gold,P.goldL]);gem(c,16,15,3,4,[P.goldD,P.tealD,P.teal,P.tealL]);
  l(c,16,12,16,16,P.white);px(c,16,18,P.cream);px(c,13,22,P.gold);px(c,20,22,P.gold);
  spark(c,27,5,P.tealL,2);px(c,4,23,P.teal);
});

icon('starlight_stone',c=>{
  // Asymmetrical cut gem with broad low-frequency facets and a star trapped inside.
  p(c,[[14,4],[21,7],[28,16],[25,24],[17,29],[8,26],[3,18],[6,10]],P.ink);
  p(c,[[14,6],[20,8],[26,16],[23,23],[17,27],[9,24],[5,18],[8,11]],P.blueD);
  p(c,[[14,6],[17,13],[8,11],[5,18],[13,18],[17,27],[23,23],[18,17],[26,16],[20,8]],P.teal);
  p(c,[[14,6],[17,13],[8,11]],P.tealL);p(c,[[8,12],[13,17],[5,18]],'#8DE4E5');
  p(c,[[17,13],[20,8],[25,16],[18,17]],'#63BCD8');p(c,[[6,19],[13,19],[15,25],[10,24]],'#4588B8');
  p(c,[[19,18],[24,17],[22,22],[17,26]],'#397BBD');
  l(c,10,9,13,7,P.white);l(c,8,12,6,16,P.blueL);l(c,21,22,18,25,P.blueL);
  p(c,[[16,10],[18,15],[22,17],[18,19],[16,24],[14,19],[10,17],[14,15]],P.goldD);
  p(c,[[16,12],[17,16],[20,17],[17,18],[16,22],[15,18],[12,17],[15,16]],P.goldL);px(c,16,17,P.white);
  spark(c,27,5,P.goldL,2);spark(c,3,27,P.tealL,1);px(c,5,4,P.gold);
});

icon('starlight_scroll',c=>{
  // Rolled vellum with clear paper thickness, cat paw seal and hanging ribbon.
  p(c,[[5,4],[27,4],[29,7],[27,10],[25,10],[25,25],[28,27],[27,30],[5,30],[3,27],[5,24],[6,10],[3,9],[2,6]],P.ink);
  p(c,[[7,8],[25,8],[23,25],[25,28],[6,28],[8,25]],'#CCAB7F');
  p(c,[[9,9],[23,9],[22,25],[24,27],[7,27],[10,24]],P.cream);
  p(c,[[10,10],[22,10],[21,12],[11,12],[10,22],[9,23]],P.white);
  r(c,5,5,22,4,P.goldD);r(c,6,5,20,2,P.goldL);e(c,5,7,2,2,P.gold);px(c,5,6,P.white);e(c,27,7,1,2,P.goldS);
  r(c,5,26,22,3,P.goldD);r(c,6,26,20,1,P.goldL);e(c,5,27,2,2,P.gold);px(c,5,26,P.white);
  l(c,12,14,20,14,P.gold);l(c,11,23,19,23,P.goldD);
  paw(c,12,15,1,P.pink,P.pinkL,P.pinkD);
  p(c,[[21,23],[26,23],[27,29],[24,28],[22,30]],P.ink);p(c,[[22,24],[25,24],[26,28],[24,27],[23,28]],P.teal);
  e(c,23,23,3,3,P.goldD);e(c,23,22,2,2,P.gold);px(c,22,21,P.goldL);px(c,23,22,P.pinkD);
});

drawV3({icon,P,r,p,e,l,px,spark,gem,paw,fish});
if(icons.size!==57 || EXPECTED.some(id=>!icons.has(id)))throw new Error('Icon catalog must exactly match the locked 57-icon contract.');

const textureDir=path.join(OUT,'textures','item');
const reviewDir=path.join(REVIEW,'icons');
fs.mkdirSync(textureDir,{recursive:true});fs.mkdirSync(reviewDir,{recursive:true});
for(const id of EXPECTED)await icons.get(id).save(path.join(textureDir,id+'.png'));

// Inspection sheets include light and dark grounds, native resolution, and nearest enlargement.
function checker(c,x,y,w,h,cell=8,light=false){for(let yy=0;yy<h;yy+=cell)for(let xx=0;xx<w;xx+=cell)c.rect(x+xx,y+yy,Math.min(cell,w-xx),Math.min(cell,h-yy),((xx/cell+yy/cell)%2)?(light?'#E7E4DD':'#343342'):(light?'#F5F1E6':'#292936'));}
const rows=Math.ceil(EXPECTED.length/6);
const native=new Canvas(600,rows*54+40);native.rect(0,0,native.w,native.h,'#1E202C');native.text('YUUMI 97LAND / 57 ICONS / NATIVE 32PX',12,10,'#FFF0D2',1);
const large=new Canvas(1660,rows*305+65);large.rect(0,0,large.w,large.h,'#1E202C');large.text('YUUMI 97LAND / PIXEL MASTERS / 8X NEAREST',20,15,'#FFF0D2',2);
for(let i=0;i<EXPECTED.length;i++){
  const id=EXPECTED[i],c=icons.get(id),col=i%6,row=Math.floor(i/6);
  const nx=col*98+8,ny=row*54+33;checker(native,nx,ny,32,32,4);checker(native,nx+36,ny,32,32,4,true);native.blit(c,nx,ny);native.blit(c,nx+36,ny);
  native.text(String(i+1).padStart(2,'0'),nx+74,ny+12,P.goldL,1);
  const x=col*274+10,y=row*305+58;checker(large,x,y,256,256,32);large.blit(c,x,y,8);large.text(String(i+1).padStart(2,'0')+' '+id.toUpperCase(),x,y+265,'#D7D9DE',1);
}
await native.save(path.join(reviewDir,'icons-native-32.png'));
await large.save(path.join(reviewDir,'icons-contact-8x.png'));
const half=new Canvas(960,rows*100+50);half.rect(0,0,half.w,half.h,'#1E202C');half.text('16PX GUI STRESS CHECK / NATIVE 16 AND 4X NEAREST',12,12,P.cream,1);
const validation=[];
for(let i=0;i<EXPECTED.length;i++){
  const id=EXPECTED[i],src=icons.get(id),small=new Canvas(16,16);
  for(let y=0;y<16;y++)for(let x=0;x<16;x++){
    const o=((y*2+1)*32+x*2+1)*4;small.pixel(x,y,Array.from(src.data.subarray(o,o+4)));
  }
  const x=(i%6)*158+10,y=Math.floor(i/6)*100+38;
  checker(half,x,y,16,16,4);half.blit(small,x,y);checker(half,x+30,y,64,64,8);half.blit(small,x+30,y,4);
  half.text(String(i+1).padStart(2,'0')+' '+id.toUpperCase().slice(0,31),x,y+74,P.goldL,1);
  let opaque=0,partialAlpha=0,minX=32,minY=32,maxX=-1,maxY=-1;const colors=new Set();
  for(let yy=0;yy<32;yy++)for(let xx=0;xx<32;xx++){
    const o=(yy*32+xx)*4,a=src.data[o+3];if(a!==0&&a!==255)partialAlpha++;
    if(a){opaque++;minX=Math.min(minX,xx);minY=Math.min(minY,yy);maxX=Math.max(maxX,xx);maxY=Math.max(maxY,yy);colors.add(src.data.subarray(o,o+3).toString('hex'));}
  }
  const encoded=fs.readFileSync(path.join(textureDir,id+'.png'));
  const dimensions=[encoded.readUInt32BE(16),encoded.readUInt32BE(20)],bitDepth=encoded[24],colorType=encoded[25];
  if(dimensions[0]!==32||dimensions[1]!==32||bitDepth!==8||colorType!==6||partialAlpha!==0||opaque<50||opaque>950)throw new Error('Invalid pixel master '+id);
  if(minX<1||minY<1||maxX>30||maxY>30)throw new Error('Missing transparent contour margin '+id);
  validation.push({id,dimensions,bitDepth,colorType,opaquePixels:opaque,partialAlphaPixels:partialAlpha,colors:colors.size,bounds:[minX,minY,maxX,maxY],transparentContourMargin:true});
}
await half.save(path.join(reviewDir,'icons-stress-16.png'));
// New assets have a dedicated readable board; source catalog supplies full IDs.
const v3=new Canvas(1200,Math.ceil(newIcons.length/5)*240+45);
v3.rect(0,0,v3.w,v3.h,'#1E202C');v3.text('V3 / 30 NEW PIXEL MASTERS / 4X AND NATIVE 32',15,14,P.cream,2);
for(let i=0;i<newIcons.length;i++){
  const id=newIcons[i],c=icons.get(id),x=(i%5)*240+12,y=Math.floor(i/5)*240+50;
  checker(v3,x,y,128,128,16);v3.blit(c,x,y,4);checker(v3,x+138,y,32,32,4,true);v3.blit(c,x+138,y);
  const small=new Canvas(16,16);for(let yy=0;yy<16;yy++)for(let xx=0;xx<16;xx++){const o=((yy*2+1)*32+xx*2+1)*4;small.pixel(xx,yy,Array.from(c.data.subarray(o,o+4)));}
  checker(v3,x+138,y+50,64,64,8);v3.blit(small,x+138,y+50,4);
  v3.text(String(i+28).padStart(2,'0')+' '+id,x,y+139,P.goldL,1);
  v3.text('32PX 4X / 32PX / 16PX 4X',x,y+155,'#ADB5C7',1);
}
v3.save(path.join(reviewDir,'icons-v3-contact.png'));
fs.writeFileSync(path.join(reviewDir,'validation.json'),JSON.stringify({count:icons.size,exactCatalogMatch:true,antialiasing:false,masterSize:32,guiStressSize:16,icons:validation},null,2)+'\n');
fs.writeFileSync(path.join(reviewDir,'catalog.json'),JSON.stringify(EXPECTED.map((id,i)=>({index:i+1,id,width:32,height:32,master:'textures/item/'+id+'.png'})),null,2)+'\n');
fs.writeFileSync(path.join(reviewDir,'notes.md'),"# 57 inventory icons\n\n27 original masters are preserved; 30 v3 masters are added. All are 32x32 RGBA8, binary alpha, nearest-only review scaling and one-pixel transparent contour margin. Full IDs are in catalog.json; the dedicated v3 board covers all 30 new images. Sixteen-pixel reduction tests silhouette and palette, not tiny text.\n\nThis generator records technical evidence only. Actual visual observations and revisions are recorded separately in review/v3-visual-review.md after opening the generated images. Minecraft client validation is separate.\n");
console.log(JSON.stringify({icons:icons.size,textureDir,reviewDir,ids:EXPECTED}));
