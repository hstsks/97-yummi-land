import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import crypto from 'node:crypto';
import {ROOT,REVIEW} from './asset-kit.mjs';
import {singleReviewModels,monsterCatalog} from '../sources/asset-contract.mjs';
const mode=process.argv[2]??'all';
if(!['all','models','rigs'].includes(mode))throw Error('Expected all, models or rigs');
fs.mkdirSync(REVIEW,{recursive:true});
fs.writeFileSync(path.join(REVIEW,'board-inputs.tsv'),[...singleReviewModels.map(id=>`model\t${id}`),...monsterCatalog.map(m=>`rig\t${m.id}\t${m.chapter}`)].join('\n')+'\n');
const java=path.join(ROOT,'runtime/jdk-21/bin/java.exe'),source=path.join(ROOT,'assets/yuumi97-production/tools/ReviewBoards.java');
const result=spawnSync(java,['-Djava.awt.headless=true',source,path.join(ROOT,'assets/yuumi97-production'),mode],{encoding:'utf8',maxBuffer:1024*1024});
process.stdout.write(result.stdout??'');process.stderr.write(result.stderr??'');
if(result.error||result.status!==0)throw result.error??Error(`Review composition failed (${result.status})`);
const outputs=result.stdout.trim().split(/\r?\n/).filter(p=>p.endsWith('.png')).map(p=>({path:path.relative(REVIEW,p).replaceAll('\\','/'),sha256:crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex')}));
const inputFiles=[];
if(mode!=='rigs')for(const id of singleReviewModels){
 for(const v of ['front','side','rear','threequarter','top'])inputFiles.push(`models/${id}/${v}.png`);
 const records=JSON.parse(fs.readFileSync(path.join(REVIEW,'contexts',id,'contexts.json'),'utf8')).contexts;
 for(const record of records)inputFiles.push(`contexts/${id}/context-${record.context}.png`);
}
if(mode!=='models')for(const m of monsterCatalog)for(const v of ['front','side','rear','threequarter','windup-minus60','windup-minus60-side','strike-plus55'])inputFiles.push(`rigs/${m.id}/${v}.png`);
const inputs=inputFiles.map(p=>({path:p,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(REVIEW,p))).digest('hex')}));
fs.writeFileSync(path.join(REVIEW,`boards-${mode}.json`),JSON.stringify({timestamp:new Date().toISOString(),mode,inputs,outputs},null,2)+'\n');
