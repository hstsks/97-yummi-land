import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {SOURCE} from '../tools/asset-kit.mjs';
import {models,heroItems,heroModels,vfx,floorModels,atlasSizes,landmarkMounts,monsterParts,singleReviewModels,monsterCatalog} from './asset-contract.mjs';

export const effectSpecs={
 vfx_slash_crescent_gold:{kind:'horizontal_arc',front:[0,0,-1],layers:3},
 vfx_slash_crimson_heavy:{kind:'horizontal_arc',front:[0,0,-1],friendlyFirePalette:true},
 vfx_titan_greatsword_drop:{kind:'downward',tip:[8,-14,8],maximumY:30,friendlyFirePalette:true},
 vfx_slash_crescent_shadow:{kind:'vertical',front:[0,0,-1]},
 vfx_shadow_mark_crest:{kind:'vertical',front:[0,0,-1]},
 vfx_star_arrow_volley:{kind:'directional',front:[0,0,-1],tails:3},
 vfx_astral_bow_sigil:{kind:'horizontal_ring',yRange:[7,9]},
 vfx_magic_circle_arcane:{kind:'floor_disc',yRange:[7.6,8.4],xzRange:[-14,30]},
 vfx_gravity_singularity:{kind:'volume',ringTilt:22.5},
 vfx_arcane_meteor:{kind:'volume',travel:[0,-1,-1]},
 vfx_magic_circle_sanctuary:{kind:'floor_disc',yRange:[7.6,8.4],xzRange:[-14,30]},
 vfx_holy_spear:{kind:'downward',tip:[8,-14,8],wingYRange:[12,24]},
 vfx_golden_fishing_vortex:{kind:'water_funnel',yRange:[0,8],xzRange:[-14,30]},
 vfx_feast_cauldron_aura:{kind:'floor',anchor:[8,0,8],steamYRange:[18,28]}
};
export function writeReviewCatalog(){
 const catalog={unitsPerBlock:16,modelOrigin:[8,8,8],front:[0,0,-1],singleReviewModels,rigReviewIds:monsterCatalog.map(m=>m.id),models:{}};
 for(const id of models){
  let spec={kind:'object'};
  if(floorModels.includes(id))spec={kind:'floor',anchor:[8,0,8]};
  if(id in landmarkMounts)spec=landmarkMounts[id];
  if(heroItems.includes(id))spec={kind:'hand',anchorSource:'hero-grip-anchors.json'};
  if(id==='prop_wayfinder_arrow')spec={kind:'directional',front:[0,0,-1]};
  if(id in effectSpecs)spec=effectSpecs[id];
  if(monsterParts.includes(id))spec={...spec,kind:'bone'};
  catalog.models[id]={atlasSize:atlasSizes[id],pivot:[8,8,8],...spec};
 }
 fs.writeFileSync(path.join(SOURCE,'model-review-contract.json'),JSON.stringify(catalog,null,2)+'\n');return catalog;
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1]))writeReviewCatalog();
