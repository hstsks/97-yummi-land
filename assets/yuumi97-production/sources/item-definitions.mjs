import fs from 'node:fs';
import path from 'node:path';
import {OUT} from '../tools/asset-kit.mjs';
import {selectors,worldPointers,models} from './asset-contract.mjs';
for(const dir of ['models/gui','items'])fs.mkdirSync(path.join(OUT,dir),{recursive:true});
for(const id of selectors){
 fs.writeFileSync(path.join(OUT,'models/gui',id+'.json'),JSON.stringify({parent:'minecraft:item/generated',textures:{layer0:`yuumi97:item/${id}`}},null,2)+'\n');
 fs.writeFileSync(path.join(OUT,'items',id+'.json'),JSON.stringify({model:{type:'minecraft:select',property:'minecraft:display_context',cases:[{when:'gui',model:{type:'minecraft:model',model:`yuumi97:gui/${id}`}}],fallback:{type:'minecraft:model',model:`yuumi97:item/${id}`}}},null,2)+'\n');
}
for(const id of worldPointers){
 fs.writeFileSync(path.join(OUT,'items',id+'.json'),JSON.stringify({model:{type:'minecraft:model',model:`yuumi97:item/${id}`}},null,2)+'\n');
}
// The vanilla blocks atlas automatically discovers item/ and block/, not prop/.
// Atlas sources merge with vanilla; enumerate the exact locked prop sprite set.
const atlasFile=path.join(OUT,'../minecraft/atlases/blocks.json');
fs.mkdirSync(path.dirname(atlasFile),{recursive:true});
const ids=models;
fs.writeFileSync(atlasFile,JSON.stringify({sources:ids.map(id=>({type:'minecraft:single',resource:`yuumi97:prop/${id}`}))},null,2)+'\n');
console.log(`Created ${selectors.length} GUI/world selectors, ${worldPointers.length} prop pointers and ${ids.length} atlas entries.`);
