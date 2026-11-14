import fs from 'node:fs';
import path from 'node:path';
import {OUT} from '../tools/asset-kit.mjs';
for(const id of ['prowling_wand','book_of_thresholds','you_and_me_bell']){
 fs.writeFileSync(path.join(OUT,'models/gui',id+'.json'),JSON.stringify({parent:'minecraft:item/generated',textures:{layer0:`yuumi97:item/${id}`}},null,2)+'\n');
 fs.writeFileSync(path.join(OUT,'items',id+'.json'),JSON.stringify({model:{type:'minecraft:select',property:'minecraft:display_context',cases:[{when:'gui',model:{type:'minecraft:model',model:`yuumi97:gui/${id}`}}],fallback:{type:'minecraft:model',model:`yuumi97:item/${id}`}}},null,2)+'\n');
}
// The vanilla blocks atlas automatically discovers item/ and block/, not prop/.
// Atlas sources merge with the vanilla atlas; enumerate only our own nine sprites.
const atlasFile=path.join(OUT,'../minecraft/atlases/blocks.json');
fs.mkdirSync(path.dirname(atlasFile),{recursive:true});
const ids=['prop_yuumi_statue','prop_slot_cabinet','prop_roulette_wheel','prop_derby_trophy','prowling_wand','book_of_thresholds','you_and_me_bell','hat_yuumi_ears','plushie_yuumi'];
fs.writeFileSync(atlasFile,JSON.stringify({sources:ids.map(id=>({type:'minecraft:single',resource:`yuumi97:prop/${id}`}))},null,2)+'\n');
console.log('Created 3 GUI sprite / world 3D item definitions.');
