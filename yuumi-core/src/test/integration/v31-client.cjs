// NODE_PATH=build/integration-client/node_modules node src/test/integration/v31-client.cjs
// Only the disposable V31Fixture server is permitted; never connects to production.
const mineflayer = require('mineflayer')
const fs = require('node:fs'), path = require('node:path')
const { Vec3 } = require('vec3')
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
const jobs = [['PALADIN', 'weapon_paladin_sword'], ['SHADOW', 'weapon_shadow_daggers'], ['RANGER', 'weapon_ranger_bow'], ['ARCANIST', 'weapon_arcane_grimoire'], ['MINER', 'tool_miner_drill'], ['BLACKSMITH', 'tool_smith_hammer'], ['FARMER', 'tool_farmer_scythe'], ['ANGLER', 'tool_angler_harpoon'], ['CARPENTER', 'tool_carpenter_axe'], ['CHEF', 'tool_chef_wok']]
const anchors = [[-19.5, 65, -25.5], [-15.5, 65, -26.5], [-11.5, 65, -25.5]].map(point => new Vec3(...point))
const results = [], clients = new Set(), folder = path.resolve(__dirname, '../../../build/verification-v31')
const sequencePath = 'BukkitValues."yuumilandcore:yuumi_training_hit_sequence"'
fs.mkdirSync(folder, { recursive: true })
fs.writeFileSync(path.join(folder, 'client-actionbars.jsonl'), '')
function record(name, passed, detail) {
  results.push({ name, passed: !!passed, detail }); console.log(JSON.stringify(results.at(-1)))
  fs.writeFileSync(path.join(folder, 'client-results.json'), JSON.stringify(results, null, 2))
  if (!passed) throw Error(name + ': ' + JSON.stringify(detail))
}
function frame(text) {
  const match = text.match(/수련 허수아비 (\d+)\].*이번 타격: ([0-9.]+) DMG.*최근 5초 DPS: ([0-9.]+)/s)
  return match ? { dummy: Number(match[1]) - 1, hit: Number(match[2]), dps: Number(match[3]), at: Date.now() } : null
}
function latest(bot, target) { return [...bot.frames].reverse().find(entry => target === undefined || entry.dummy === target) }
function componentText(value) {
  if (value == null) return ''
  if (typeof value === 'string') return value
  if (typeof value !== 'object') return ''
  if (Array.isArray(value)) return value.map(componentText).join('')
  if (value.type && 'value' in value) return componentText(value.value)
  if ('text' in value || 'extra' in value) return componentText(value.text) + componentText(value.extra)
  return Object.values(value).map(componentText).join('')
}
async function command(bot, text, wait = 250) { bot.chat(text); await delay(wait) }
async function join(job) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25567, username: 'V31' + job, auth: 'offline', version: '1.21.4', viewDistance: 'tiny' })
  clients.add(bot); bot.logs = []; bot.frames = []; bot.effectEvents = []; bot.spawnEvents = []
  bot.on('messagestr', text => bot.logs.push(text))
  bot.on('actionBar', json => {
    const text = json.toString(); bot.logs.push(text)
    const parsed = frame(text); if (parsed) bot.frames.push(parsed)
    fs.appendFileSync(path.join(folder, 'client-actionbars.jsonl'), JSON.stringify({ name: bot.username, time: Date.now(), text }) + '\n')
  })
  bot.on('entityEffect', (entity, effect) => bot.effectEvents.push({ entity: entity.id, ...effect, at: Date.now() }))
  bot.on('entitySpawn', entity => bot.spawnEvents.push({ name: entity.name, at: Date.now(), y: entity.position.y }))
  bot._client.on('entity_metadata', packet => {
    if (bot.entities[packet.entityId]?.name !== 'text_display') return
    for (const entry of packet.metadata) {
      const text = componentText(entry.value), parsed = frame(text)
      if (!parsed) continue
      parsed.source = 'private-hologram'; bot.frames.push(parsed); bot.logs.push(text)
      fs.appendFileSync(path.join(folder, 'client-holograms.jsonl'), JSON.stringify({ name: bot.username, time: Date.now(), entity: packet.entityId, text }) + '\n')
    }
  })
  bot.on('error', error => console.error(bot.username, error.message))
  await new Promise((resolve, reject) => { const timer = setTimeout(() => reject(Error('Login timeout ' + bot.username)), 30000); bot.once('spawn', () => { clearTimeout(timer); resolve() }) })
  await delay(1200); await command(bot, '/minecraft:gamerule sendCommandFeedback true'); return bot
}
async function quit(bot) { clients.delete(bot); bot.quit('v3.1 check complete'); await delay(300) }
async function tp(bot, x, y, z) { await command(bot, `/minecraft:execute in minecraft:yuumi_hub run minecraft:tp @s ${x} ${y} ${z}`, 650) }
function entity(bot, type, at) { return Object.values(bot.entities).filter(entry => entry.name === type).sort((a, b) => a.position.distanceSquared(at) - b.position.distanceSquared(at))[0] }
function dummy(bot, index = 1) {
  const found = entity(bot, 'vindicator', anchors[index])
  if (!found || found.position.distanceTo(anchors[index]) > .1) throw Error('Dummy missing/moved: ' + index)
  return found
}
async function aimDummy(bot, index = 1) {
  await tp(bot, anchors[index].x, 65, anchors[index].z + 3)
  const target = dummy(bot, index); await bot.lookAt(target.position.offset(0, .7, 0), true); return target
}
async function scalar(bot, target, key) {
  return await new Promise((resolve, reject) => {
    const timer = setTimeout(() => { bot.removeListener('messagestr', receive); reject(Error('NBT scalar timeout ' + key + ' ' + bot.logs.slice(-3))) }, 4000)
    function receive(text, position, json) {
      if (!/commands\.data\.entity\.query/.test(JSON.stringify(json)) && !/following entity data|개체 데이터|엔티티 데이터/.test(text)) return
      const found = text.match(/(-?\d+(?:\.\d+)?)(?:[bslfd])?\s*$/i)
      if (!found) return
      clearTimeout(timer); bot.removeListener('messagestr', receive); resolve(Number(found[1]))
    }
    bot.on('messagestr', receive); bot.chat(`/minecraft:data get entity ${target.uuid} ${key}`)
  })
}
async function dataText(bot, target, key) {
  return await new Promise((resolve, reject) => {
    const timer = setTimeout(() => { bot.removeListener('messagestr', receive); reject(Error('NBT text timeout ' + key)) }, 4000)
    function receive(text, position, json) {
      if (!/commands\.data\.entity\.query/.test(JSON.stringify(json)) && !/following entity data|개체 데이터|엔티티 데이터/.test(text)) return
      clearTimeout(timer); bot.removeListener('messagestr', receive); resolve(text)
    }
    bot.on('messagestr', receive); bot.chat(`/minecraft:data get entity ${target.uuid} ${key}`)
  })
}
async function seq(bot, index = 1) { return scalar(bot, dummy(bot, index), sequencePath) }
async function sequences(bot) { const values = []; for (let index = 0; index < 3; index++) values.push(await seq(bot, index)); return values }
async function cleanDummies(bot) {
  for (let index = 0; index < 3; index++) {
    const target = dummy(bot, index)
    await command(bot, `/minecraft:effect clear ${target.uuid}`, 100)
    await command(bot, `/minecraft:data merge entity ${target.uuid} {Fire:0s}`, 100)
  }
  await delay(150)
}
async function npc(bot, at, atEvent = false) {
  const relay = entity(bot, 'interaction', new Vec3(...at))
  if (!relay || relay.position.distanceTo(new Vec3(...at)) > 1) throw Error('Missing NPC relay ' + at)
  if (atEvent) await bot.activateEntityAt(relay, relay.position.offset(0, .5, 0)); else await bot.activateEntity(relay)
  await delay(400)
}
async function equip(bot, id) {
  const slots = new Set(bot.inventory.items().map(item => item.slot)); await command(bot, '/yuumi give ' + id)
  const item = bot.inventory.items().find(entry => !slots.has(entry.slot)) || bot.inventory.items().at(-1)
  if (!item) throw Error('Give failed ' + id)
  await bot.equip(item, 'hand'); await delay(200)
}
async function vanillaWeapon(bot, material = 'iron_sword') {
  const slots = new Set(bot.inventory.items().map(item => item.slot)); await command(bot, '/minecraft:give @s ' + material)
  const item = bot.inventory.items().find(entry => entry.name === material && !slots.has(entry.slot))
  if (!item) throw Error('Vanilla weapon missing ' + material)
  await bot.equip(item, 'hand'); await delay(200)
}
async function use(bot, sneak = false, left = false, wait = 1800) {
  setSneak(bot, sneak); await delay(150)
  if (left) bot.swingArm('right'); else bot.activateItem()
  await delay(150); if (!left) bot.deactivateItem()
  setSneak(bot, false); await delay(wait)
}
function setSneak(bot, state) {
  bot.setControlState('sneak', state)
  // 1.21.4 still requires the legacy entity action; Mineflayer's new-input branch omits it.
  bot._client.write('entity_action', { entityId: bot.entity.id, actionId: bot.supportFeature('entityActionUsesStringMapper') ? (state ? 'start_sneaking' : 'stop_sneaking') : (state ? 0 : 1), jumpBoost: 0 })
}
async function awakening(bot, branch) {
  await tp(bot, -14.5, 65, -8); await npc(bot, [-14.5, 65, -10.5])
  if (!bot.currentWindow) throw Error('Teacher did not open')
  await bot.clickWindow(40, 0, 0); await delay(250)
  await bot.clickWindow(branch === 'A' ? 14 : 15, 0, 0); await delay(300)
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
}
async function freshDamage(bot, name, action, wait = 1800, index = 1) {
  const before = await seq(bot, index), at = Date.now(); await action(); await delay(wait)
  const after = await seq(bot, index)
  record(name, after > before, { before, after, newHits: after - before, frame: latest(bot, index), at }); return after - before
}
function selfEffects(bot, at) { return bot.effectEvents.filter(effect => effect.entity === bot.entity.id && effect.at >= at) }
async function hurtInArena(bot) {
  await tp(bot, .5, 65, -212.5); await bot.lookAt(new Vec3(.5, 64, -215), true)
  await command(bot, '/minecraft:effect give @s minecraft:instant_health 1 5 true')
  await command(bot, '/minecraft:effect clear @s'); await command(bot, '/minecraft:damage @s 8 minecraft:generic', 450); return bot.health
}
async function npcChecks(bot) {
  const sites = [['cashier', [63.5, 65, -9.5], [63.5, 66, -12], '환전'], ['blackjack', [77.5, 65, -16], [80.5, 66, -16], '블랙잭'], ['roulette', [77.5, 65, -21], [80.5, 66, -21], '룰렛'], ['vip', [77.5, 71, -22], [80.5, 72, -22], '블랙잭'], ['broker', [52.5, 71, -30.5], [52.5, 72, -33], '증권']]
  for (const [name, spot, target, title] of sites) {
    await tp(bot, ...spot)
    for (const atEvent of [false, true]) {
      await npc(bot, target, atEvent)
      record('NPC ' + name + ' ' + (atEvent ? 'interactAt' : 'interact'), bot.currentWindow && JSON.stringify(bot.currentWindow.title).includes(title), bot.currentWindow?.title)
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await delay(180)
    }
  }
  await tp(bot, 63.5, 65, -9.5)
  const relay = entity(bot, 'interaction', new Vec3(63.5, 66, -12)); let opens = 0
  const opened = () => opens++; bot.on('windowOpen', opened)
  bot._client.write('use_entity', { target: relay.id, mouse: 2, hand: 0, sneaking: false, x: 0, y: .5, z: 0, location: new Vec3(0, .5, 0) })
  bot._client.write('use_entity', { target: relay.id, mouse: 0, hand: 0, sneaking: false })
  await delay(500); bot.removeListener('windowOpen', opened)
  record('Same-tick NPC relay deduplication', opens === 1, opens)
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
  for (const [target, title] of [[[14.5, 65, -27.5], '칭호'], [[22.5, 65, -27.5], '가챠']]) {
    await tp(bot, target[0], 65, target[2] + 2.5); await npc(bot, target)
    record('Relocated ' + title, bot.currentWindow && JSON.stringify(bot.currentWindow.title).includes(title), bot.currentWindow?.title)
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
  }
}
async function checkA(bot, job) {
  await command(bot, '/minecraft:effect clear @s')
  if (job === 'PALADIN' || job === 'CHEF') {
    const before = await hurtInArena(bot), at = Date.now(); await use(bot, true, false, 2300)
    record(job + ' A heals actual missing health', bot.health > before + .5, { before, after: bot.health, at }); return
  }
  await aimDummy(bot); await cleanDummies(bot)
  if (job === 'SHADOW') {
    const effectsAt = Date.now(); await use(bot, true, false, 250)
    record('Shadow A invisibility/speed effects', selfEffects(bot, effectsAt).some(effect => effect.amplifier === 2), selfEffects(bot, effectsAt))
    const target = dummy(bot), before = await seq(bot); bot.attack(target); await delay(500)
    const afterHit = await seq(bot); await delay(5000); const afterMark = await seq(bot)
    record('Shadow A real hit then delayed mark', afterHit > before && afterMark > afterHit, { before, afterHit, afterMark })
  } else if (job === 'RANGER' || job === 'ARCANIST') {
    const spawned = bot.spawnEvents.length
    const hits = await freshDamage(bot, job + ' A actual repeated damage', () => use(bot, true, false, 0), job === 'RANGER' ? 5600 : 2300)
    record(job + ' A has multiple real hits', hits >= 3, hits)
    if (job === 'RANGER') record('Ranger A visible falling arrow displays', bot.spawnEvents.slice(spawned).filter(entry => entry.name === 'item_display' && entry.y >= 74).length >= 5, bot.spawnEvents.slice(spawned).length)
  } else {
    const at = Date.now(); await use(bot, true, false, 900); const effects = selfEffects(bot, at)
    const positive = job === 'ANGLER' ? effects.some(effect => effect.amplifier === 4) : job === 'MINER' ? effects.some(effect => effect.amplifier === 2) : effects.some(effect => effect.amplifier === 1)
    record(job + ' A produces real buff effect', positive, effects)
  }
}
async function checkB(bot, job) {
  await awakening(bot, 'B')
  if (job === 'ARCANIST') {
    const before = await hurtInArena(bot); await use(bot, true, false, 1300)
    record('Arcanist B heals actual missing health', bot.health > before + .5, { before, after: bot.health }); return
  }
  await aimDummy(bot); await cleanDummies(bot)
  await freshDamage(bot, job + ' B actual new damage', () => use(bot, true, false, 0), job === 'CARPENTER' ? 6500 : 5800)
}
async function nativeBow(bot) {
  await command(bot, '/minecraft:give @s arrow 16'); await aimDummy(bot); await cleanDummies(bot)
  const before = await seq(bot); bot.activateItem(); await delay(1000)
  const during = await seq(bot); bot.deactivateItem(); await delay(1000); const after = await seq(bot)
  record('Native bow holds before release then damages', during === before && after > during, { before, during, after })
}
async function nativeFishing(bot) {
  await tp(bot, 106.5, 65.5, 22.5); await bot.lookAt(new Vec3(111, 64.5, 28), true)
  setSneak(bot, false); bot.activateItem(); await delay(700)
  const hooks = Object.values(bot.entities).filter(entry => entry.name === 'fishing_bobber')
  record('Native fishing rod casts bobber', hooks.length > 0, hooks.map(entry => entry.id))
  bot.activateItem(); await delay(700)
  record('Native fishing rod reels bobber', hooks.every(entry => !bot.entities[entry.id]), hooks.map(entry => !!bot.entities[entry.id]))
}
async function runeChecks(bot) {
  await awakening(bot, 'A'); await command(bot, '/minecraft:clear @s')
  for (let index = 0; index < 9; index++) await command(bot, '/yuumi give monster_soul_core', 120)
  await command(bot, '/yuumi give starlight_stone')
  for (const [index, type] of ['FIRE', 'FROST', 'THUNDER'].entries()) {
    await vanillaWeapon(bot); await tp(bot, -82.5, 65, 9.3); await npc(bot, [-82.5, 65, 6.8])
    await bot.clickWindow(36, 0, 0); await delay(250); await bot.clickWindow(10 + index, 0, 0); await delay(300)
    await bot.clickWindow(24, 0, 0); await delay(250); await bot.clickWindow(10 + index, 0, 0); await delay(300)
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
    await aimDummy(bot); await cleanDummies(bot)
    const before = await sequences(bot), effectsAt = Date.now(); bot.attack(dummy(bot)); await delay(400); const middle = await seq(bot)
    record(type + ' rune adds real extra hit', middle >= before[1] + 2, { before: before[1], after: middle })
    if (type === 'FIRE') {
      const burning = await scalar(bot, dummy(bot), 'Fire'); await delay(1400); const afterDot = await seq(bot)
      record('Fire rune produces owned burning damage', burning > 0 && afterDot > middle, { burning, middle, afterDot })
    } else if (type === 'FROST') {
      const effects = bot.effectEvents.filter(effect => effect.entity === dummy(bot).id && effect.at >= effectsAt)
      // Remote mobs do not consistently receive entity_effect packets after an effect changes.
      const duration = await scalar(bot, dummy(bot), 'active_effects[{id:"minecraft:slowness"}].duration')
      record('Frost rune applies actual slowing effect', duration > 0, { duration, effects })
    } else {
      const left = await seq(bot, 0), right = await seq(bot, 2)
      record('Thunder rune damages both nearby dummies', left > before[0] && right > before[2], { before, after: [left, middle, right] })
    }
  }
}
async function isolationChecks() {
  const a = await join('PALADIN'), b = await join('ARCANIST')
  try {
    for (const bot of [a, b]) { await command(bot, '/minecraft:gamemode survival'); await command(bot, '/minecraft:clear @s'); await vanillaWeapon(bot) }
    await aimDummy(a, 0); await aimDummy(b, 1); await cleanDummies(a); await delay(5500); a.frames = []; b.frames = []
    const aSeq = await seq(a, 0); a.attack(dummy(a, 0)); await delay(500)
    record('Isolation player A hits target 1', await seq(a, 0) > aSeq, latest(a, 0))
    record('Uninvolved player B receives no practice HUD', b.frames.length === 0, b.frames)
    const aFirst = latest(a, 0), bSeq = await seq(b, 1); b.attack(dummy(b, 1)); await delay(450)
    record('Isolation player B hits target 2', await seq(b, 1) > bSeq, latest(b, 1)); const bFirst = latest(b, 1)
    record('Other target/player does not inflate A DPS', Math.abs(latest(a, 0).dps - aFirst.dps) < .15, { before: aFirst, after: latest(a, 0) })
    await aimDummy(a, 1); const second = await seq(a, 1); a.attack(dummy(a, 1)); await delay(450)
    record('Player A also hits target 2', await seq(a, 1) > second, latest(a, 1)); const aSecond = latest(a, 1)
    record('Per-player per-dummy DPS stays separate', aSecond && Math.abs(aSecond.dps - aSecond.hit / 5) < .15 && Math.abs(latest(b, 1).dps - bFirst.dps) < .15, { aTarget1: aFirst, aTarget2: aSecond, bTarget2: latest(b, 1) })
  } finally { await quit(a); await quit(b) }
}
async function poisonOwnershipChecks() {
  const a = await join('PALADIN'), b = await join('ARCANIST')
  const ownerPath = 'BukkitValues."yuumilandcore:yuumi_training_poison_owner"'
  const expiryPath = 'BukkitValues."yuumilandcore:yuumi_training_poison_expires"'
  async function splash(bot, amplifier, duration) {
    await tp(bot === a ? b : a, .5, 65, -8)
    await command(bot, '/minecraft:clear @s')
    await command(bot, `/minecraft:give @s minecraft:splash_potion[minecraft:potion_contents={custom_effects:[{id:"minecraft:poison",amplifier:${amplifier},duration:${duration}}]}]`)
    const item = bot.inventory.items().find(entry => entry.name === 'splash_potion')
    if (!item) throw Error('Custom native poison potion missing')
    await bot.equip(item, 'hand'); await aimDummy(bot); bot.activateItem(); await delay(1000)
  }
  try {
    for (const bot of [a, b]) await command(bot, '/minecraft:gamemode survival')
    await aimDummy(a); await aimDummy(b); await cleanDummies(a)
    await splash(a, 1, 1000)
    const target = dummy(a), firstOwner = await dataText(a, target, ownerPath), firstExpiry = await scalar(a, target, expiryPath)
    record('Poison stronger source A owns active effect', firstOwner.includes(a._client.uuid), { firstOwner, firstExpiry })
    await splash(b, 0, 200)
    const weakOwner = await dataText(a, target, ownerPath), weakExpiry = await scalar(a, target, expiryPath)
    record('Rejected weak poison B retains source A and TTL', weakOwner.includes(a._client.uuid) && weakExpiry === firstExpiry, { weakOwner, firstExpiry, weakExpiry })
    const retainedBefore = await seq(a); await delay(1200); const retainedAfter = await seq(a)
    record('Rejected weak poison keeps native DOT credited to A', retainedAfter > retainedBefore && latest(a, 1)?.source === 'private-hologram' && b.frames.length === 0, { retainedBefore, retainedAfter, aFrame: latest(a, 1), bFrames: b.frames })
    await splash(b, 2, 600)
    const strongOwner = await dataText(a, target, ownerPath), strongExpiry = await scalar(a, target, expiryPath)
    const amplifier = await scalar(a, target, 'active_effects[{id:"minecraft:poison"}].amplifier')
    record('Accepted stronger poison B changes source and TTL', strongOwner.includes(b._client.uuid) && strongExpiry !== firstExpiry && amplifier === 2, { strongOwner, firstExpiry, strongExpiry, amplifier })
    const before = await seq(b); await delay(1200)
    record('Accepted native poison causes real owned damage', await seq(b) > before && latest(b, 1)?.source === 'private-hologram', { before, after: await seq(b), frame: latest(b, 1) })
  } finally { await quit(a); await quit(b) }
}
async function main() {
  if (process.env.V31_POISON_ONLY) { await poisonOwnershipChecks(); console.log('V31_POISON_CHECKS PASS ' + results.length); return }
  const selected = process.env.V31_JOBS ? jobs.filter(([job]) => process.env.V31_JOBS.split(',').includes(job)) : jobs
  for (const [job, id] of selected) {
    const bot = await join(job)
    try {
      await command(bot, '/minecraft:gamemode survival'); await command(bot, '/minecraft:clear @s'); await command(bot, '/minecraft:effect clear @s'); await equip(bot, id)
      await awakening(bot, 'A') // Re-runs may persist B from the preceding pass; use the real teacher to restore A.
      if (job === 'PALADIN' && !process.env.V31_SKIP_NPC) await npcChecks(bot)
      await aimDummy(bot); await cleanDummies(bot)
      await freshDamage(bot, job + ' native melee actual damage', async () => { bot.attack(dummy(bot)) }, 400)
      await aimDummy(bot); await freshDamage(bot, job + ' primary actual new damage', () => use(bot, job === 'RANGER', job === 'ANGLER' || job === 'RANGER', 0), 2300)
      if (job === 'RANGER') await nativeBow(bot)
      if (job === 'ANGLER') await nativeFishing(bot)
      await delay(2500); await checkA(bot, job); await checkB(bot, job); await aimDummy(bot)
      for (let index = 0; index < 3; index++) record(job + ' dummy ' + (index + 1) + ' fixed', dummy(bot, index).position.distanceTo(anchors[index]) < .05, dummy(bot, index).position)
      record(job + ' no vanilla XP or soul drops', bot.experience.points === 0 && !bot.inventory.items().some(item => item.name === 'echo_shard'), { xp: bot.experience.points, inventory: bot.inventory.items().map(item => item.name) })
      if (job === 'BLACKSMITH') await runeChecks(bot)
    } finally { await quit(bot) }
  }
  if (!process.env.V31_SKIP_ISOLATION) await isolationChecks(); console.log('V31_CLIENT_CHECKS PASS ' + results.length)
}
module.exports = { join, quit, command, equip, aimDummy, scalar, seq, use, hurtInArena, componentText, frame }
if (require.main === module) main().catch(error => { console.error(error.stack); for (const bot of clients) bot.quit('test failure'); process.exitCode = 1 })
