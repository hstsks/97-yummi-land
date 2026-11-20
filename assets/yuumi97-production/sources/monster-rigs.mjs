import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {OUT,SOURCE} from '../tools/asset-kit.mjs';
import {ASTRAL_RIGS,buildAstralMonsters} from './monsters-astral.mjs';
import {FOUNDRY_RIGS,buildFoundryMonsters} from './monsters-foundry.mjs';
import {VOID_RIGS,buildVoidMonsters} from './monsters-void.mjs';
export function buildMonsterRigs(){
 buildAstralMonsters();buildFoundryMonsters();buildVoidMonsters();
 const manifest={schemaVersion:1,unitsPerBlock:16,modelOrigin:[8,8,8],rootAnchor:[8,0,8],front:[0,0,-1],yawConvention:'bukkit',posePitchToLocalX:-1,
  rigs:{...ASTRAL_RIGS,...FOUNDRY_RIGS,...VOID_RIGS}};
 const bytes=JSON.stringify(manifest,null,2)+'\n';
 fs.writeFileSync(path.join(SOURCE,'monster-rig-sockets.json'),bytes);fs.writeFileSync(path.join(OUT,'monster_rigs.json'),bytes);
 console.log(`Generated ${Object.keys(manifest.rigs).length} assembled rig contracts.`);return manifest;
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1]))buildMonsterRigs();
