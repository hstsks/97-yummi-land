const {Vec3}=require('vec3');
exports.run=async function({join,probe,cmd,tp,interactEntity,record,sleep,clients}){
 if(process.env.CORE_SUITE==='restart'){
  const recovered=[];let p;for(let i=0;i<9;i++){const member=await join(i);if(i===8)p=member;await sleep(300);const state=await probe(member,'snapshot'),status=await probe(member,'status');recovered.push({player:member.username,active:state.run.active,...status});}await sleep(4200);const inventory=await probe(p,'inventory');
  record('restart ends runs and restores all nine hub/original modes',recovered.every(status=>!status.active&&status.world==='yuumi_hub'&&status.mode==='SURVIVAL'),recovered);
  record('system shutdown refunds exactly one tier key',inventory.filter(x=>x.id==='labyrinth_key').reduce((n,x)=>n+x.amount,0)===1,inventory);return;
 }
 const bots=[];for(let i=0;i<8;i++){const p=await join(i);bots.push(p);await cmd(p,'/minecraft:gamemode survival @s');await probe(p,'sustain true');await cmd(p,'/minecraft:clear @s');}
 const lead=bots[0];const lifeEvidence=await probe(lead,'rpgworldcheck',120000);record('six life professions positive fixtures and protection guards',lifeEvidence.success===true,lifeEvidence);record('owned, trusted and revoked HOME permissions',!!(await probe(lead,'homecheck')));await probe(lead,'outside');let outside=await probe(lead,'status');await cmd(lead,'/은행');let afterRemote=await probe(lead,'status');record('expedition bank command cannot warp or open remote bank',afterRemote.world===outside.world&&!lead.currentWindow,afterRemote);await tp(lead,.5,65,12.5);await probe(lead,'profile PALADIN MINER 1 NONE');await cmd(lead,'/party create');
 for(let i=1;i<8;i++){await cmd(lead,'/party invite '+bots[i].username);await cmd(bots[i],'/party accept');}
 await cmd(lead,'/party info');record('eight players join fixed party',lead.logs.some(x=>x.includes('8/8')),lead.logs.slice(-10));
 async function entry(group,tier){
  for(let i=0;i<group.length;i++)await tp(group[i],16.5+i*.45,66,-53.5);
  await interactEntity(group[0],new Vec3(18.5,66,-55.5));if(!group[0].currentWindow)throw Error('Gate menu missing');await group[0].clickWindow(18+tier*2,0,0);await sleep(300);
  for(const p of group){if(!p.currentWindow)throw Error('Consent menu missing '+p.username);await p.clickWindow(11,0,0);await sleep(150);}await sleep(1000);
  const snap=await probe(group[0],'snapshot');record('tier '+tier+' unanimous NPC entry',snap.run.active&&snap.run.chapter===tier,snap.run);return snap;
 }
 let snap=await entry(bots,1);const cx=snap.run.slot*1024;record('live run owns exactly 56 chunk tickets',snap.run.chunks===56,snap.run);
 async function next(zone){const boundary=[0,0,-28,-66,-108,-144,-176][zone];await sleep(2200);
  for(let i=0;i<bots.length;i++){const p=bots[i];await tp(p,cx-3.5+i,67,boundary+2.5,'yuumi_dungeon');await p.lookAt(new Vec3(cx-3.5+i,68,boundary-8),true);p.setControlState('forward',true);}
  await sleep(1700);for(const p of bots)p.setControlState('forward',false);await sleep(250);const state=await probe(lead,'snapshot');record('walk through physical gate into zone '+zone,state.run.stage===zone,state.run);return state;
 }
 await tp(lead,cx+.5,67,-20.5,'yuumi_dungeon');await interactEntity(lead,new Vec3(cx+.5,67,-22.5));snap=await next(2);
 record('eight-player HP multiplier is 4.85',snap.mobs.some(m=>Math.abs(m.maxHp-(m.type==='mob_clockwork_sentinel'?30:m.type==='mob_ink_librarian'?20:24)*4.85)<.001),snap.mobs);
 // A ninth player forms an independent solo run; eight-player scale stays frozen.
 const other=await join(8);await sleep(3500);await cmd(lead,'/minecraft:op CorePlayer8',300);await cmd(other,'/minecraft:gamemode survival @s');await probe(other,'sustain true');await cmd(other,'/minecraft:clear @s');await cmd(other,'/yuumi give labyrinth_key');
 const parallel=await entry([other],2);const cx2=parallel.run.slot*1024;record('parallel parties use different 1024-block slots',parallel.run.slot!==snap.run.slot&&Math.abs(cx2-cx)>=1024,parallel.run);
 await tp(other,cx2+.5,67,-20.5,'yuumi_dungeon');await interactEntity(other,new Vec3(cx2+.5,67,-22.5));await sleep(2300);await tp(other,cx2+.5,67,-31.5,'yuumi_dungeon');await sleep(500);
 let isolated=await probe(other,'snapshot'),target=isolated.mobs.find(m=>!m.dead);if(!target)throw Error('Parallel mob not activated');await probe(lead,'damage '+target.uuid+' 100000');let unchanged=await probe(other,'snapshot');record('cross-instance damage is rejected',unchanged.mobs.find(m=>m.uuid===target.uuid).hp===target.hp,{before:target,after:unchanged.mobs});
 async function killZone(zone){for(let attempt=0;attempt<6;attempt++){let state=await probe(lead,'snapshot');if(state.run.cleared.includes(zone)||state.run.awaitingBoon||state.run.reward)return state;
   for(const mob of state.mobs.filter(m=>!m.dead&&m.zone===zone))await probe(lead,'damage '+mob.uuid+' 100000');await sleep(3500);
  }throw Error('Zone '+zone+' did not clear');}
 await killZone(2);snap=await next(3);
 await tp(lead,cx-9.5,67,-88.5,'yuumi_dungeon');await interactEntity(lead,new Vec3(cx-9.5,67,-89.5));await interactEntity(lead,new Vec3(cx-9.5,67,-89.5));
 snap=await probe(lead,'snapshot');record('side treasure records one claim despite repeat clicks',snap.run.sideOwners.length===1,snap.run);
 await killZone(3);snap=await next(4);await killZone(4);await sleep(300);
 for(const p of bots){if(!p.currentWindow)throw Error('Boon GUI missing '+p.username);await p.clickWindow(10,0,0);await sleep(150);}await sleep(300);snap=await probe(lead,'snapshot');record('midboss activates camp and personal boons',snap.run.checkpoint&&!snap.run.awaitingBoon,snap.run);
 const beforeTime=snap.run.remainingSeconds;
 for(const p of bots){await probe(p,'sustain false');await cmd(p,'/minecraft:kill @s',100);}await sleep(1800);
 snap=await probe(lead,'snapshot');record('first wipe retries at checkpoint without resetting time',snap.run.retried&&snap.run.remainingSeconds<=beforeTime&&snap.run.stage===4&&snap.run.sideOwners.length===1,snap.run);
 for(const p of bots)await probe(p,'sustain true');snap=await next(5);
 const points=[[-8.5,-166.5],[-8.5,-152.5],[9.5,-166.5],[9.5,-152.5]];
 for(let i=0;i<4;i++){for(let turn=0;turn<4;turn++){snap=await probe(lead,'snapshot');if(snap.run.puzzleValues[i]===snap.run.puzzleTargets[i])break;const[x,z]=points[i];await tp(lead,cx+x,67,z+.8,'yuumi_dungeon');await interactEntity(lead,new Vec3(cx+x,67,z));}}
 await sleep(1300);snap=await probe(lead,'snapshot');record('constellation puzzle is solvable by one player',snap.run.cleared.includes(5),snap.run);snap=await next(6);
 const boss=snap.mobs.find(m=>m.type==='boss_novarion'&&!m.dead);if(!boss)throw Error('Final boss missing');record('final boss preserves eight-player scaling',Math.abs(boss.maxHp-1455)<.001,boss);
 await probe(lead,'damage '+boss.uuid+' 100000');await sleep(200);snap=await probe(lead,'snapshot');const phase=snap.mobs.find(m=>m.uuid===boss.uuid);record('large damage cannot skip second phase',phase&&!phase.dead&&phase.phase===2&&phase.hp>0,phase);await sleep(1500);
 await cmd(bots[7],'/minecraft:clear @s');await cmd(bots[7],'/minecraft:give @s minecraft:stone 2304');
 const offline=bots[6];offline.quit('Offline reward check');clients.delete(offline);await sleep(400);
 await probe(lead,'damage '+boss.uuid+' 100000');await sleep(1500);snap=await probe(lead,'snapshot');record('boss death opens personal reward and return portal',snap.run.reward,snap.run);
 let s=await probe(lead,'status');record('Adventure combat/completion XP is retained',s.levelCombat>1||s.combatXp>0,s);
 await tp(bots[7],cx+.5,67,-216,'yuumi_dungeon');await sleep(900);s=await probe(bots[7],'status');record('full inventory preserves pending rewards after return-portal settlement',s.pendingRewards>0&&s.world==='yuumi_hub',s);
 await cmd(bots[7],'/minecraft:clear @s');await cmd(bots[7],'/yuumi 보상',400);let inv=await probe(bots[7],'inventory'),coins=inv.filter(x=>x.id==='ancient_cat_coin').reduce((n,x)=>n+x.amount,0);await cmd(bots[7],'/yuumi 보상',300);inv=await probe(bots[7],'inventory');record('reward repeated claim cannot duplicate coins',coins>=4&&coins===inv.filter(x=>x.id==='ancient_cat_coin').reduce((n,x)=>n+x.amount,0),inv);
 const rejoined=await join(6);await sleep(3500);await tp(rejoined,cx+.5,67,-216,'yuumi_dungeon');await sleep(900);inv=await probe(rejoined,'inventory');record('offline participant receives preserved personal reward through return portal after reconnect',inv.some(x=>x.id==='ancient_cat_coin'&&x.amount>=4),inv);
 // Leave the independent tier2 run active; verifier shutdown must refund its consumed key once.
 const final=await probe(other,'snapshot');record('parallel run remained independent through other party completion',final.run.active&&final.run.chapter===2&&!final.run.reward,final.run);
};
