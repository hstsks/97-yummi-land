import {Model} from '../tools/asset-kit.mjs';

// Seated Yuumi monument: book rests on the existing quartz pillar at world Y69.
// ItemDisplay NONE, center(8,8,8), entity anchor Y70.6, scale2.
const m=new Model('prop_yuumi_statue',128);
for(const [name,color,options] of [
 ['gold','#E9B94F',{border:true}],['goldLight','#FFE3A0',{}],['goldDeep','#B5772F',{}],['goldShade','#CE9541',{}],
 ['ivory','#FFF0CA',{}],['ink','#3B2B40',{}],['pink','#D98B9B',{}],['teal','#48C8C6',{border:true}],
 ['tealDark','#1F6576',{}],['tealLight','#B3FAE1',{}],['book','#253F69',{border:true}],['bookDeep','#182941',{}],
 ['paper','#EED8A3',{}],['paperEdge','#D1B47B',{}],['rune','#67E5DA',{}],['marble','#E8E3D7',{kind:'stone'}],
 ['face','#E9B94F',{}],['pageWriting','#EED8A3',{}],['clasp','#E9B94F',{border:true}],['feather','#FFE3A0',{}]
])m.swatch(name,color,options);
const face=m.swatches.face;
// Muzzle and almond eyes are geometry, while this tile gives sculpted brow markings.
m.canvas.polygon([[face.x+3,face.y+1],[face.x+7,face.y+4],[face.x+12,face.y+1],[face.x+9,face.y+6],[face.x+7,face.y+8],[face.x+5,face.y+5]],'#FFF0CA');
const writing=m.swatches.pageWriting;
for(let y=3;y<=12;y+=3){m.canvas.line(writing.x+2,writing.y+y,writing.x+6,writing.y+y,'#AE8752');m.canvas.line(writing.x+9,writing.y+y,writing.x+13,writing.y+y,'#AE8752');}
m.canvas.polygon([[writing.x+7,writing.y+4],[writing.x+10,writing.y+7],[writing.x+7,writing.y+10],[writing.x+4,writing.y+7]],'#48C8C6');
const feather=m.swatches.feather;m.canvas.line(feather.x+2,feather.y+13,feather.x+12,feather.y+2,'#B5772F');
for(let p=4;p<12;p+=3)m.canvas.line(feather.x+p,feather.y+14-p,feather.x+p-2,feather.y+8-p,'#D19B45');
const bookRotation=(side)=>({origin:[8,-3,8],axis:'z',angle:side==='left'?-22.5:22.5});
for(const side of ['left','right']){
 const a=side==='left'?-5.5:8,b=side==='left'?8:21.5,r=bookRotation(side);
 m.box(`Book / ${side} leather cover`,[a,-4.8,-2],[b,-4.05,18],'book',{rotation:r});
 m.box(`Book / ${side} gilded outer rim`,[side==='left'?a:a+12.7,-4.04,-2],[side==='left'?a+.8:b,-3.5,18],'gold',{rotation:r});
 for(let y=0;y<4;y++)m.box(`Book / ${side} page layer ${y+1}`,[a+.8,-3.95+y*.32,-1.1],[b-.3,-3.67+y*.32,17.1],y%2?'paper':'paperEdge',{rotation:r});
 m.box(`Book / ${side} inscribed top page`,[a+1,-2.65,-.9],[b-.4,-2.48,16.9],'pageWriting',{rotation:r});
 for(const z of [-2,16.3]){
  m.box(`Book / ${side} ornate corner ${z}`,[side==='left'?a:a+10.9,-4.94,z],[side==='left'?a+2.6:b,-3.65,z+1.7],'gold',{rotation:r});
  m.box(`Book / ${side} corner gem ${z}`,[side==='left'?a+.7:a+11.7,-3.64,z+.4],[side==='left'?a+1.8:a+12.8,-3.35,z+1.2],'teal',{rotation:r});
 }
}
m.box('Book / carved spine',[7,-4.9,-2.6],[9,-2.3,18.3],'goldShade');
for(const z of [-1,3.5,8,12.5,16])m.box(`Book / spine band ${z}`,[6.8,-5.05,z],[9.2,-4.6,z+.55],'goldLight');
m.box('Book / front clasp',[6.6,-4.4,-3.1],[9.4,-2.3,-2.5],'gold');
m.box('Book / clasp inset',[7.2,-3.9,-3.17],[8.8,-2.8,-3.09],'teal');
// Warm golden body built from changing cross-sections; front is north(-Z).
m.box('Body / seated lower mass',[3.5,-.3,4.1],[12.5,7.5,13.2],'gold');
m.box('Body / broad haunch',[2.5,.1,6.5],[13.5,5.8,14.3],'goldShade');
m.box('Body / chest',[4.1,5.8,3.5],[11.9,13.5,11.8],'gold');
m.box('Body / shoulder taper',[4.8,12,4.8],[11.2,15.5,11.5],'goldLight');
m.box('Chest / ivory bib upper',[5.1,7.2,3.3],[10.9,12.7,3.55],'ivory');
m.box('Chest / ivory bib point',[6.4,4.4,3.2],[9.6,7.4,3.55],'ivory');
for(const [s,x]of [['left',3.1],['right',9.9]]){
 m.box(`Foreleg / ${s}`,[x,1.5,1.8],[x+3,9.7,5.7],'goldLight');
 m.box(`Paw / ${s} rounded top`,[x-.25,-.1,.4],[x+3.25,2.1,5.1],'goldLight');
 m.box(`Paw / ${s} lower step`,[x+.1,-.5,.8],[x+2.9,.2,4.6],'goldShade');
 for(let i=1;i<3;i++)m.box(`Paw / ${s} toe engraving ${i}`,[x+i,.1,.29],[x+i+.12,1.15,.43],'goldDeep');
}
// Tail curls behind the right haunch, then sweeps upward; no free-axis rotations.
m.box('Tail / root',[11.5,1.4,11.2],[15.8,4.8,14.5],'goldShade');
m.box('Tail / outer lower arc',[14.1,2.6,9.8],[17.5,6.8,13.3],'gold');
m.box('Tail / raised arc',[15.2,6.1,8.3],[18.1,10.3,11.8],'gold');
m.box('Tail / inward curl',[13.3,9.1,8],[17.1,12,11.1],'goldLight');
m.box('Tail / cream tip',[12.1,9.6,7.8],[14.4,11.6,10.5],'ivory');
// Head silhouette: narrow chin, wide cheeks, clipped upper corners, long ears.
m.box('Head / core',[2.8,13,1.9],[13.2,21.4,11.8],'gold',{faces:{north:'face'}});
m.box('Head / cheek left',[1.5,13.5,3],[4.1,18.5,10.5],'goldLight');
m.box('Head / cheek right',[11.9,13.5,3],[14.5,18.5,10.5],'goldLight');
m.box('Head / crown step',[4,21.3,3],[12,22.6,10.6],'gold');
m.box('Head / chin',[4.3,11.9,2.9],[11.7,14,9.4],'ivory');
m.box('Muzzle / left',[4.15,13.5,.6],[7.95,16.35,2.15],'ivory');
m.box('Muzzle / right',[8.05,13.5,.6],[11.85,16.35,2.15],'ivory');
m.box('Muzzle / nose',[7.1,15.3,.1],[8.9,16.4,.72],'pink');
m.box('Muzzle / smile center',[7.85,13.7,.42],[8.15,15.35,.64],'goldDeep');
for(const [side,x]of [['left',3.1],['right',9.3]]){
 m.box(`Eye / ${side} dark almond`,[x,17.2,1.69],[x+3.6,19.9,2.04],'ink');
 m.box(`Eye / ${side} jade iris`,[x+.5,17.5,1.48],[x+3,19.6,1.71],'teal');
 m.box(`Eye / ${side} vertical pupil`,[x+1.45,17.45,1.35],[x+2.02,19.55,1.49],'tealDark');
 m.box(`Eye / ${side} catchlight`,[x+.8,18.8,1.19],[x+1.55,19.5,1.36],'tealLight');
 m.box(`Eye / ${side} brow`,[x-.1,19.85,1.62],[x+3.7,20.15,2.03],'goldDeep');
}
for(const [side,x,ang]of [['left',.25,22.5],['right',12.1,-22.5]]){
 const origin=[x+1.8,20,7]; const r={origin,axis:'z',angle:ang};
 m.box(`Ear / ${side} lower flare`,[x,19.2,4.3],[x+3.65,25.2,9.6],'gold',{rotation:r});
 m.box(`Ear / ${side} pointed middle`,[x+.4,24.8,4.85],[x+3.25,28.4,9.1],'goldLight',{rotation:r});
 m.box(`Ear / ${side} tip`,[x+1,28.2,5.6],[x+2.65,30.3,8.5],'goldLight',{rotation:r});
 m.box(`Ear / ${side} inner enamel`,[x+.7,20.5,4.1],[x+3,25.4,4.4],'pink',{rotation:r});
 m.box(`Ear / ${side} inner taper`,[x+1.1,25.3,4.68],[x+2.6,27.4,4.96],'pink',{rotation:r});
}
// Gold antenna and suspended bell refer to this project's established Yuumi design.
m.box('Forehead / diadem',[6.3,20.3,1.5],[9.7,22,2.2],'goldLight');
m.box('Forehead / turquoise diamond',[7.1,20.3,1.12],[8.9,22.1,1.52],'teal',{rotation:{origin:[8,21.2,1.32],axis:'z',angle:45}});
m.box('Antenna / stem',[7.55,21.6,1.7],[8.45,25.2,2.6],'goldDeep');
m.box('Antenna / forward bridge',[7.55,24.6,-.5],[8.45,25.4,2.1],'gold');
m.box('Antenna / bell crown',[7,23.7,-1.8],[9,25.1,.3],'goldLight');
m.box('Antenna / bell body',[6.6,22.3,-2.2],[9.4,23.8,.7],'gold');
m.box('Antenna / bell lip',[6.3,21.85,-2.5],[9.7,22.4,1],'goldDeep');
m.box('Antenna / bell slit',[7.8,22.2,-2.57],[8.2,23.2,-2.48],'ink');
m.box('Antenna / clapper',[7.5,21.4,-1.3],[8.5,21.9,-.3],'goldLight');
for(const [side,x,sign]of [['left',1.1,-1],['right',14.9,1]])for(let j=0;j<3;j++){
 const from=[Math.min(x,x+sign*1.8),14+j*.65,2.55],to=[Math.max(x,x+sign*1.8),14.15+j*.65,2.8];
 m.box(`Whisker / ${side} ${j}`,from,to,'goldLight');
}
m.display.gui={rotation:[18,-25,0],translation:[0,-3,0],scale:[.43,.43,.43]};
m.display.ground={rotation:[0,0,0],translation:[0,5,0],scale:[.3,.3,.3]};
m.display.fixed={rotation:[0,180,0],translation:[0,-2,0],scale:[.4,.4,.4]};
m.save();
console.log(`${m.id}: ${m.elements.length} editable cuboids`);
