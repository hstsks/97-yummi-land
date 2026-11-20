// NODE_PATH=build/integration-client/node_modules node src/test/integration/core-client.cjs
// Only the offline disposable Paper server is reachable by this test runner.
const mineflayer=require('mineflayer'),fs=require('node:fs'),path=require('node:path');
const {Vec3}=require('vec3');
const folder=path.resolve(__dirname,'../../../build/verification-core');fs.mkdirSync(folder,{recursive:true});
const clients=new Set(),results=[];const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const combat=[['PALADIN','weapon_paladin_sword'],['BERSERKER','weapon_berserker_greatsword'],['SHADOW','weapon_shadow_daggers'],['RANGER','weapon_ranger_bow'],['ARCANIST','weapon_arcane_orb_tome'],['PRIEST','weapon_priest_staff']];
const life=[['MINER','tool_miner_drill'],['FARMER','tool_farmer_scythe'],['ANGLER','tool_angler_harpoon'],['CARPENTER','tool_carpenter_axe'],['BLACKSMITH','tool_smith_hammer'],['CHEF','tool_chef_wok']];
function record(name,ok,detail,strict=true){const item={name,passed:!!ok,detail};results.push(item);console.log(JSON.stringify(item));fs.writeFileSync(path.join(folder,'client-results-'+(process.env.CORE_SUITE||'basic')+'.json'),JSON.stringify(results,null,2));if(!ok&&strict)throw Error(name+': '+JSON.stringify(detail));}
async function cmd(bot,text,ms=120){bot.chat(text);await sleep(ms);}
async function probe(bot,action,timeout=12000){return new Promise((resolve,reject)=>{
 const parts=new Map(),timer=setTimeout(()=>{bot.removeListener('messagestr',receive);reject(Error('Probe timeout '+action+' '+bot.logs.slice(-4).join('|')))},timeout);
 function receive(text){let data;
  if(text.startsWith('CORE_PROBE_CHUNK ')){const match=text.match(/^CORE_PROBE_CHUNK (\S+) (\d+)\/(\d+) (.*)$/);if(!match)return;const key=match[1],index=Number(match[2])-1,total=Number(match[3]);let items=parts.get(key);if(!items){items=Array(total).fill(null);parts.set(key,items)}items[index]=match[4];if(items.some(x=>x===null))return;data=Buffer.from(items.join(''),'base64').toString('utf8');}
  else{const start=text.indexOf('CORE_PROBE ');if(start<0)return;data=text.slice(start+11);}
  let item;try{item=JSON.parse(data)}catch{return}clearTimeout(timer);bot.removeListener('messagestr',receive);if(item.kind==='failure')reject(Error(item.value));else resolve(item.value);
 }bot.on('messagestr',receive);bot.chat('/coreprobe '+action);
});}
async function join(index){const bot=mineflayer.createBot({host:'127.0.0.1',port:25567,username:'CorePlayer'+index,auth:'offline',version:'1.21.4',viewDistance:'tiny'});clients.add(bot);bot.logs=[];bot.bars=[];bot.bossbars=[];bot.on('bossBarCreated',bar=>bot.bossbars.push(bar));bot.on('messagestr',t=>{bot.logs.push(t);fs.appendFileSync(path.join(folder,'client-messages.jsonl'),JSON.stringify({player:index,text:t})+'\n')});bot.on('actionBar',c=>bot.bars.push(c.toString()));bot.on('error',e=>console.error(bot.username,e.message));await new Promise((resolve,reject)=>{const t=setTimeout(()=>reject(Error('Login timeout')),30000);bot.once('spawn',()=>{clearTimeout(t);resolve()})});await sleep(800);return bot;}
async function tp(bot,x,y,z,world='yuumi_hub'){const dimension=world==='world'?'overworld':world;await cmd(bot,'/minecraft:execute in minecraft:'+dimension+' run minecraft:tp @s '+x+' '+y+' '+z,400);}
async function equip(bot,id){await cmd(bot,'/minecraft:clear @s');await cmd(bot,'/yuumi give '+id,300);const inventory=await probe(bot,'inventory');const entry=inventory.find(x=>x.id===id);if(!entry)throw Error('Server did not mint '+id+': '+JSON.stringify(inventory));const slot=entry.slot<9?entry.slot+36:entry.slot===40?45:entry.slot;let item=bot.inventory.slots[slot];if(!item){await sleep(250);item=bot.inventory.slots[slot]}if(!item)throw Error('Client inventory missing '+id);await bot.equip(item,'hand');await sleep(150);const state=await probe(bot,'status');if(state.held!==id)throw Error('Wrong signature equipped '+id+': '+JSON.stringify(state));return item;}
function dummy(bot){return Object.values(bot.entities).filter(e=>e.name==='vindicator').sort((a,b)=>a.position.distanceSquared(new Vec3(-6,65,-30.5))-b.position.distanceSquared(new Vec3(-6,65,-30.5)))[0];}
async function aim(bot){await tp(bot,-3.5,65,-30.5);await sleep(200);const e=dummy(bot);if(!e||e.position.distanceTo(new Vec3(-6,65,-30.5))>.2)throw Error('Training dummy missing');await bot.lookAt(e.position.offset(0,1,0),true);await sleep(200);return e;}
function swap(bot){bot._client.write('block_dig',{status:6,location:{x:0,y:0,z:0},face:0,sequence:0});}
function setSneak(bot,on){bot.setControlState('sneak',on);bot._client.write('entity_action',{entityId:bot.entity.id,actionId:on?0:1,jumpBoost:0});}
function damageFrames(bot,from){return bot.bars.slice(from).filter(s=>s.includes('DMG'));}
async function interactEntity(bot,at){const e=Object.values(bot.entities).filter(e=>e.name==='interaction').sort((a,b)=>a.position.distanceSquared(at)-b.position.distanceSquared(at))[0];if(!e||e.position.distanceTo(at)>1.5)throw Error('Interaction missing at '+at);await bot.lookAt(e.position.offset(0,.5,0),true);await bot.activateEntity(e);await sleep(300);}
async function basic(){const bot=await join(0);await cmd(bot,'/minecraft:gamemode survival @s');await cmd(bot,'/minecraft:gamerule sendCommandFeedback true');await tp(bot,.5,65,12.5);
 let books=await probe(bot,'books');record('guide canonicalizes old books, duplicates and cursor',books.filter(x=>x.id==='guidebook').reduce((n,x)=>n+x.amount,0)===1&&!books.some(x=>x.id==='book_of_thresholds'),books);
 record('all twelve signature metadata survive six application orders',await probe(bot,'metadata')===true);
 const warps=await probe(bot,'warps');record('every public and home destination has safe landing',Object.keys(warps).length===15,warps);
 await cmd(bot,'/공방',500);record('facility command instantly warps to guild',bot.entity.position.distanceTo(new Vec3(-21.5,65,-18.5))<4,bot.entity.position);
 await cmd(bot,'/가이드',300);record('guide menu opens',!!bot.currentWindow,bot.currentWindow?.title);if(bot.currentWindow)bot.closeWindow(bot.currentWindow);
 if(process.env.CORE_WORLD_ONLY){record('world geometry and eighteen rigs',!!(await probe(bot,'worldcheck',90000)));return bot;}
 for(let i=0;i<6;i++){
  const [cls,id]=combat[i],[prof]=life[i];await probe(bot,'profile '+cls+' '+prof+' 20 A');await equip(bot,id);await aim(bot);await probe(bot,'reset');const before=await probe(bot,'dummyhits');
  bot.swingArm('right');await sleep(1000);const after=await probe(bot,'dummyhits');record(cls+' left-click applies new accepted training damage',after.some(x=>x.sequence>(before.find(y=>y.index===x.index)?.sequence||0)),{before,after,frames:bot.bars.slice(-8)});
  const state=await probe(bot,'status');record(cls+' combat and life slots coexist',state.combat===cls&&state.life===prof,state);
  if(i>=3){record(cls+' uses noncharging PAPER and zero ammunition',bot.heldItem?.name==='paper'&&!bot.inventory.items().some(x=>/arrow/.test(x.name)),bot.heldItem?.name);}
  await probe(bot,'reset');await aim(bot);bot.activateItem();await sleep(350);bot.deactivateItem();let primary=await probe(bot,'status');record(cls+' right-click primary consumes combat cooldown',Number(primary.combatCooldowns?.primary)>Date.now(),primary);
  for(const branch of ['A','B','NONE']){await probe(bot,'profile '+cls+' '+prof+' '+(branch==='NONE'?5:20)+' '+branch);await equip(bot,id);await aim(bot);await probe(bot,'reset');swap(bot);await sleep(350);const f=await probe(bot,'status');record(cls+' '+branch+' F retains held signature',f.held===id,f);record(cls+' '+branch+' F commits finisher',Number(f.combatCooldowns?.finisher)>Date.now()||Number(f.combatCooldowns?.ultimate)>Date.now(),f);await sleep(cls==='SHADOW'&&branch==='B'?1600:500);}
 }
 for(let i=0;i<6;i++){const[prof,id]=life[i],[cls]=combat[i];await probe(bot,'profile '+cls+' '+prof+' 20 A');await equip(bot,id);await tp(bot,-3.5,65,-30.5);await bot.look(0,-.3,true);await probe(bot,'reset');
  if(prof==='BLACKSMITH')await cmd(bot,'/minecraft:item replace entity @s armor.chest with minecraft:iron_chestplate[minecraft:damage=100]');
  if(prof==='ANGLER'){await tp(bot,103.5,65,17.5);await bot.look(0,.3,true);bot.activateItem();await sleep(500);bot.deactivateItem();const state=await probe(bot,'status');record('ANGLER plain right-click preserves native rod path',!state.lifeCooldowns?.primary,state);setSneak(bot,true);await sleep(100);bot.activateItem();await sleep(150);bot.deactivateItem();setSneak(bot,false);}
  else{bot.activateItem();await sleep(180);bot.deactivateItem();}
  let state=await probe(bot,'status');record(prof+' right-click uses life cooldown or preserves empty harvest',prof==='FARMER'?!state.lifeCooldowns?.primary:Number(state.lifeCooldowns?.primary)>Date.now(),state);
  for(const branch of ['A','B','NONE']){await probe(bot,'profile '+cls+' '+prof+' '+(branch==='NONE'?5:20)+' '+branch);await equip(bot,id);await tp(bot,-3.5,65,-30.5);await bot.look(0,-.3,true);await probe(bot,'reset');swap(bot);await sleep(300);state=await probe(bot,'status');record(prof+' '+branch+' F retains life signature',state.held===id,state);if(bot.currentWindow)bot.closeWindow(bot.currentWindow);}
 }
 // NPC entity interaction must not cast a ground-targeted arcanist skill.
 await probe(bot,'profile ARCANIST BLACKSMITH 20 A');await equip(bot,'weapon_arcane_orb_tome');await tp(bot,-14.5,65,-7.5);await probe(bot,'reset');await interactEntity(bot,new Vec3(-14.5,65,-10.5));bot.activateItem();await sleep(100);bot.deactivateItem();const npc=await probe(bot,'status');record('NPC interaction precedes primary skill',!!bot.currentWindow&&!npc.combatCooldowns?.primary,npc);if(bot.currentWindow)bot.closeWindow(bot.currentWindow);
 await tp(bot,.5,65,12.5);await cmd(bot,'/가이드',200);await bot.clickWindow(10,1,0);await sleep(500);record('guide right-click starts private wayfinding',bot.bossbars.some(bar=>String(bar.title).includes('길잡이')));await cmd(bot,'/길잡이');
 const world=await probe(bot,'worldcheck',90000);record('three chapters six zones eighteen rigs and physical gates',world.rigs===18&&world.bones===42,world);
 if(process.env.CORE_SUITE==='fallback'){
  const models=await probe(bot,'models');record('absent authored assets select procedural maps, vanilla rigs and legacy arcane model',world.blueprints===0&&world.manifestRigs===0&&models.arcane==='yuumi97:weapon_arcane_grimoire',{world,models});
  await equip(bot,'guidebook');await probe(bot,'outside');let place=await probe(bot,'status');setSneak(bot,true);await sleep(150);bot.activateItem();await sleep(100);bot.deactivateItem();await sleep(1000);await tp(bot,place.position[0]+1,place.position[1],place.position[2],'world');setSneak(bot,false);await sleep(2500);record('moving cancels guidebook expedition recall',(await probe(bot,'status')).world==='world');
  await tp(bot,place.position[0],place.position[1],place.position[2],'world');setSneak(bot,true);await sleep(150);bot.activateItem();await sleep(100);bot.deactivateItem();await sleep(1200);record('guidebook return keeps the full three-second channel',(await probe(bot,'status')).world==='world');await sleep(2200);setSneak(bot,false);record('guidebook shift-right-click returns to hub after channel',(await probe(bot,'status')).world==='yuumi_hub');
  const recovered=await join(8);await sleep(3500);const state=await probe(recovered,'status'),inventory=await probe(recovered,'inventory');record('second final-build restart retains hub and original mode',state.world==='yuumi_hub'&&state.mode==='SURVIVAL',state);record('second final-build restart cannot refund the tier key twice',inventory.filter(x=>x.id==='labyrinth_key').reduce((n,x)=>n+x.amount,0)===1,inventory);
 }
 return bot;
}
async function main(){
 if(process.env.CORE_SUITE==='dungeon'||process.env.CORE_SUITE==='restart')await require('./dungeon-client.cjs').run({join,probe,cmd,tp,interactEntity,record,sleep,clients});
 else if(process.env.CORE_SUITE==='scenario'){const bot=await join(0);const result=await probe(bot,'scenariocheck',150000);record('three solo puzzles, checkpoint replay and boss mechanic success/failure',result.success===true||result.status==='PASS',result);}
 else if(process.env.CORE_SUITE==='lifevfx'||process.env.CORE_SUITE==='polish'){const bot=await join(0);for(const action of process.env.CORE_SUITE==='lifevfx'?['lifepolishcheck','vfxcheck']:['combatcheck','lifepolishcheck','vfxcheck','rpgworldcheck','scenariocheck']){try{const result=await probe(bot,action,150000);record(action+' real Paper positive regression',result.success===true||result.status==='PASS',result,false);}catch(error){record(action+' real Paper positive regression',false,String(error),false);}}if(results.some(x=>!x.passed))throw Error('One or more Paper regression helpers failed');}
 else {const bot=await basic();if(process.env.CORE_SUITE==='extended')for(const action of ['combatcheck','lifepolishcheck','vfxcheck','rpgworldcheck','scenariocheck']){const result=await probe(bot,action,150000);record(action+' real Paper positive regression',result.success===true||result.status==='PASS',result);}}
 console.log('CORE_CLIENT_CHECKS PASS '+results.length);
}
main().catch(e=>{console.error(e.stack);process.exitCode=1}).finally(async()=>{for(const bot of clients)bot.quit('Core tests complete');await sleep(500)});
