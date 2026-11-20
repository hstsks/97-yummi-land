import fs from 'node:fs';
const load = name => JSON.parse(fs.readFileSync(new URL(`../data/${name}`, import.meta.url), 'utf8'));
export const registry = new Map(load('blocks.json').map(b => [b.name, b]));
const collision = load('blockCollisionShapes.json');
const cache = new Map();
export const getBlock = name => registry.get(name.replace(/^minecraft:/, ''));
export const valuesFor = s => s.values?.map(String) ?? (s.type === 'bool' ? ['true','false'] : Array.from({length:s.num_values},(_,i)=>String(i)));
export function normalizeBlock(name, properties = {}) {
  if(typeof name === 'object') return normalizeBlock(name.Name,name.Properties ?? {});
  const b = getBlock(name);
  if(!b) throw new Error(`Unknown 1.21.4 block: ${name}`);
  for(const k of Object.keys(properties)) if(!b.states.some(s=>s.name===k)) throw new Error(`Illegal property ${name}.${k}`);
  let index=b.defaultState-b.minStateId; const defaults={};
  for(const s of [...b.states].reverse()) {const values=valuesFor(s); defaults[s.name]=values[index%values.length]; index=Math.floor(index/values.length);}
  const props={};
  for(const s of b.states) {const value=String(properties[s.name] ?? defaults[s.name]);if(!valuesFor(s).includes(value)) throw new Error(`Illegal value ${name}.${s.name}=${value}`);props[s.name]=value;}
  const key=b.name+JSON.stringify(props);
  if(!cache.has(key)) cache.set(key,Object.freeze({Name:`minecraft:${b.name}`,...(b.states.length?{Properties:Object.freeze(props)}:{})}));
  return cache.get(key);
}
export function stateId(state) {
  const b=getBlock(state.Name); if(!b) throw new Error(`Unknown ${state.Name}`);
  let index=0;
  for(const s of b.states) {const vals=valuesFor(s), v=vals.indexOf(state.Properties?.[s.name]); if(v<0) throw new Error(`Missing/invalid ${state.Name}.${s.name}`);index=index*vals.length+v;}
  return b.minStateId+index;
}
export const blockInfo = state => getBlock(typeof state==='string'?state:state.Name);
export function collisionBoxes(state) {
  if(!state) return [];
  const b=getBlock(state.Name), entry=collision.blocks[b.name];
  return collision.shapes[Array.isArray(entry)?entry[stateId(state)-b.minStateId]:entry] ?? [];
}
