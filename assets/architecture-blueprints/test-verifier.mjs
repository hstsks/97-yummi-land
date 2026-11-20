import assert from 'node:assert/strict';
import fs from 'node:fs';
import { normalizeBlock } from './lib/registry.mjs';
import { encodeStructure, readStructure } from './lib/nbt.mjs';
import { validatePalette, validateStructure, hasFullTop, bodyClear, walkEdge, traceRoute, createNavigation, effectiveBookshelves, verifyGate } from './verify-structures.mjs';

function fixture(size) {
  const [W,H,L]=size,grid=Array(W*H*L).fill(normalizeBlock('air'));
  return {W,H,L,size,grid,get(x,y,z){return x>=0&&x<W&&y>=0&&y<H&&z>=0&&z<L?grid[(y*L+z)*W+x]:undefined;},set(x,y,z,name,props){grid[(y*L+z)*W+x]=normalizeBlock(name,props);},fill(x0,y0,z0,x1,y1,z1,name,props){for(let x=x0;x<=x1;x++)for(let y=y0;y<=y1;y++)for(let z=z0;z<=z1;z++)this.set(x,y,z,name,props);}};
}
const results=[];
function test(name,run) {run();results.push({name,passed:true});}

test('registry rejects impossible and incomplete states',()=>{
  assert.equal(validatePalette([normalizeBlock('quartz_stairs')]).length,0);
  assert(validatePalette([{Name:'minecraft:bookshelf',Properties:{facing:'north'}}]).length>0);
  assert(validatePalette([{Name:'minecraft:quartz_stairs',Properties:{facing:'north',half:'bottom',shape:'straight'}}]).length>0);
  assert(validatePalette([{Name:'minecraft:trial_spawner'}]).length>0);
  assert.equal(hasFullTop(normalizeBlock('stone')),true);
  assert.equal(hasFullTop(normalizeBlock('copper_grate')),true);
  assert.equal(hasFullTop(normalizeBlock('quartz_stairs')),false);
});

test('typed NBT catches missing AIR, duplicates and wrong DataVersion',()=>{
  const f=fixture([5,5,5]);f.fill(0,0,0,4,0,4,'stone');
  const bytes=encodeStructure(f),spec={file:'test.nbt',size:f.size};
  assert.equal(validateStructure(readStructure(bytes),spec).length,0);
  let s=readStructure(bytes);s.blocks.pop();assert(validateStructure(s,spec).some(e=>e.includes('explicit cells')));
  s=readStructure(bytes);s.blocks[1].pos=[...s.blocks[0].pos];assert(validateStructure(s,spec).some(e=>e.includes('duplicate')));
  s=readStructure(bytes);s.root.value.DataVersion.value=4188;assert(validateStructure(s,spec).some(e=>e.includes('DataVersion')));
  assert.throws(()=>readStructure(bytes.subarray(0,bytes.length-8)));
});

test('enchanting power counts actual bookshelf offsets and transmission',()=>{
  const f=fixture([29,5,25]);
  f.set(14,1,4,'enchanting_table');
  for(let x=12;x<=16;x++)for(const y of [1,2])f.set(x,y,2,'bookshelf');
  for(const x of [12,16])for(let z=3;z<=5;z++)for(const y of [1,2])f.set(x,y,z,'bookshelf');
  assert.equal(effectiveBookshelves(f).length,22);
  f.set(14,1,3,'stone');assert.equal(effectiveBookshelves(f).length,19);
  f.set(14,1,3,'air');f.set(14,1,2,'chiseled_bookshelf');assert.equal(effectiveBookshelves(f).length,21);
});

test('gate checker catches one blocked voxel and partial stair flooring',()=>{
  const f=fixture([31,24,28]);f.fill(0,6,0,30,6,27,'stone');
  let errors=[];verifyGate(f,0,'fixture',errors);assert.equal(errors.length,0);
  f.set(15,10,0,'chain');errors=[];verifyGate(f,0,'fixture',errors);assert(errors.some(e=>e.includes('aperture')));
  f.set(15,10,0,'air');f.set(15,6,0,'quartz_stairs');errors=[];verifyGate(f,0,'fixture',errors);assert(errors.some(e=>e.includes('full support')));
});

function staircase() {
  const f=fixture([7,9,13]);f.fill(0,0,0,6,0,12,'stone');
  f.fill(2,4,1,4,4,4,'stone');
  for(let step=0;step<4;step++)f.fill(2,1+step,8-step,4,1+step,8-step,'quartz_stairs',{facing:'north',half:'bottom',shape:'straight'});
  const points=[[3.5,1,9.5]];
  for(let step=0;step<4;step++)points.push([3.5,1.5+step,8.85-step],[3.5,2+step,8.35-step]);
  points.push([3.5,5,4.5],[3.5,5,2.5]);
  return {f,route:{name:'staircase',points}};
}
test('half-block navigation climbs actual stairs without jumping',()=>{
  const {f,route}=staircase();
  assert.deepEqual(traceRoute(f,route),[]);
  const nav=createNavigation(f,{minFeet:1,maxFeet:5}),seen=nav.flood(nav.nearest([3.5,1,10.5]));
  assert.equal(nav.reachable(seen,[3.5,5,2.5]),true);
  assert.equal(walkEdge(f,[3.5,1,9.5],[3.5,2,8.35]),false);
});
test('routes reject reversed stairs, low beams, lava and missing support',()=>{
  let {f,route}=staircase();
  f.fill(2,1,8,4,1,8,'quartz_stairs',{facing:'south',half:'bottom',shape:'straight'});
  assert(traceRoute(f,route).length>0);
  ({f,route}=staircase());f.set(3,3,9,'stone');assert.equal(bodyClear(f,[3.5,1.5,9.5]),false);
  ({f,route}=staircase());f.set(3,1,10,'lava');assert.equal(bodyClear(f,[3.5,1,10.5]),false);
  ({f,route}=staircase());f.fill(2,1,8,4,1,8,'air');assert(traceRoute(f,route).some(e=>e.includes('unsupported')));
});
const report=JSON.stringify({passed:results.length,tests:results},null,2);
fs.mkdirSync(new URL('./review/',import.meta.url),{recursive:true});
fs.writeFileSync(new URL('./review/validator-tests.json',import.meta.url),report+'\n');
console.log(report);
