import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {isDeepStrictEqual} from 'node:util';
import {createHash} from 'node:crypto';
import {readStructure,simplify} from './lib/nbt.mjs';
import {stateId} from './lib/registry.mjs';
const base=path.dirname(fileURLToPath(import.meta.url)),require=createRequire(import.meta.url);
// Optional independent audit. The normal build/verify/render have no npm dependency.
const modules=process.argv[2]?path.resolve(process.argv[2]):path.resolve(base,'../../yuumi-core/build/integration-client/node_modules');
const nbt=require(path.join(modules,'prismarine-nbt')),Block=require(path.join(modules,'prismarine-block'))('1.21.4');
const input=path.resolve(base,'../../yuumi-core/src/main/resources/structures/overhaul'),results=[];
for(const file of fs.readdirSync(input).filter(f=>f.endsWith('.nbt')).sort()){
 const bytes=fs.readFileSync(path.join(input,file)),own=readStructure(bytes),reference=await nbt.parse(bytes);
 if(!isDeepStrictEqual(nbt.simplify(reference.parsed),simplify(own.root)))throw new Error(`Independent NBT mismatch: ${file}`);
 for(const state of own.palette){const ref=Block.fromProperties(state.Name.slice(10),state.Properties??{},0);if(ref.stateId!==stateId(state))throw new Error(`Independent state-id mismatch: ${file} ${JSON.stringify(state)}`);}
 results.push({file,sha256:createHash('sha256').update(bytes).digest('hex'),nbtEquivalent:true,paletteStates:own.palette.length,stateIdsEquivalent:true});
}
const report={valid:true,reference:{parser:'prismarine-nbt',parserVersion:require(path.join(modules,'prismarine-nbt/package.json')).version,blocks:'prismarine-block',blocksVersion:require(path.join(modules,'prismarine-block/package.json')).version,minecraftVersion:'1.21.4'},results};
fs.writeFileSync(path.join(base,'review/independent-parser-report.json'),JSON.stringify(report,null,2)+'\n');console.log(`Independent NBT + state-id cross-check PASS: ${results.length} structures`);
