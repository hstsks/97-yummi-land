// Locked art contracts. Never infer these catalogs from concurrently edited Java.
import fs from 'node:fs';
export const landmarkMounts = JSON.parse(fs.readFileSync(new URL('./landmarks-v31-mounts.json',import.meta.url),'utf8')).models;
export const landmarks = Object.keys(landmarkMounts);
export const baselineModels = ['prop_yuumi_statue','prop_slot_cabinet','prop_roulette_wheel','prop_derby_trophy','prowling_wand','book_of_thresholds','you_and_me_bell','hat_yuumi_ears','plushie_yuumi'];
export const baselineIcons = ['churu_salmon','churu_tuna','churu_golden','casino_chip','jackpot_crown','torn_page','book_of_thresholds','zoomies_skewer','kneading_bento','catnip_elixir','royal_feast_can','boss_core_rat','boss_core_hound','boss_core_void','you_and_me_bell','prowling_wand','final_chapter_tome','nine_lives_charm','slot_seven','slot_yuumi','slot_paw','chip_100','chip_1000','chip_10000','recall_bookmark','starlight_stone','starlight_scroll'];
export const environment = ['prop_gacha_machine','prop_stock_board','prop_dungeon_obelisk','prop_boon_altar','prop_feast_cauldron'];
export const furniture = ['furn_cat_tower','furn_kotatsu','furn_neon_sign'];
export const hats = ['hat_chef_toque','hat_stock_tophat'];
export const weapons = ['weapon_paladin_sword','weapon_shadow_daggers','weapon_ranger_bow','weapon_arcane_grimoire','tool_miner_drill','tool_smith_hammer','tool_farmer_scythe','tool_angler_harpoon','tool_carpenter_axe','tool_chef_wok'];
export const pureIcons = ['labyrinth_key','ancient_cat_coin','gacha_capsule','class_emblem','respec_potion','charm_crimson_claw','charm_starlight_aegis','charm_golden_whisker','charm_vampiric_purr','monster_soul_core','unidentified_geode','golden_catnip','deep_sea_fillet','rune_scroll','michelin_feast'];
export const newGui = [...furniture,...hats,...weapons];
export const existingModels = [...baselineModels,...environment,...newGui,...landmarks];
export const existingExtraIcons = [...pureIcons,...newGui];
export const existingIcons = [...baselineIcons,...existingExtraIcons];
export const existingSelectors = ['prowling_wand','book_of_thresholds','you_and_me_bell',...newGui];
export const heroItems = ['weapon_berserker_greatsword','weapon_priest_staff','weapon_arcane_orb_tome','guidebook'];
export const heroModels = [...heroItems,'prop_wayfinder_arrow'];
export const vfx = ['vfx_slash_crescent_gold','vfx_slash_crimson_heavy','vfx_titan_greatsword_drop','vfx_slash_crescent_shadow','vfx_shadow_mark_crest','vfx_star_arrow_volley','vfx_astral_bow_sigil','vfx_magic_circle_arcane','vfx_gravity_singularity','vfx_arcane_meteor','vfx_magic_circle_sanctuary','vfx_holy_spear','vfx_golden_fishing_vortex','vfx_feast_cauldron_aura'];
export const monsterCatalog = [
 ['mob_grimoire_mimic',1,1.15],['mob_clockwork_sentinel',1,1.35],['mob_ink_librarian',1,1.30],['mob_nebula_golem',1,1.75],['boss_archivist_astro',1,1.65],['boss_novarion',1,2.40],
 ['mob_cinder_imp',2,1.05],['mob_slag_hound',2,1.30],['mob_obsidian_bombardier',2,1.45],['mob_chained_juggernaut',2,1.80],['boss_warden_vulcantus',2,1.85],['boss_ignion',2,2.65],
 ['mob_mirror_stalker',3,1.30],['mob_void_choir',3,1.35],['mob_abyssal_oculus',3,1.50],['mob_eclipse_templar',3,1.55],['boss_twin_archons',3,1.80],['boss_eclipsia',3,2.55]
].map(([id,chapter,baseScale])=>({id,chapter,baseScale,parts:id.startsWith('boss_')?['core','head','weapon']:['body','arm']}));
export const monsterParts = monsterCatalog.flatMap(m=>m.parts.map(p=>m.id+'_'+p));
export const newModels = [...heroModels,...vfx,...monsterParts];
export const models = [...existingModels,...newModels];
export const newIcons = [...heroItems];
export const icons = [...existingIcons,...newIcons];
export const selectors = [...existingSelectors,...heroItems];
export const worldPointers = [...environment,...landmarks,'prop_wayfinder_arrow',...vfx,...monsterParts];
export const guiTargets = Object.fromEntries(selectors.map(id=>[id,heroItems.includes(id)?`item/${id}_gui`:`gui/${id}`]));
export const floorModels = [...environment,...furniture,...landmarks.filter(id=>landmarkMounts[id].kind==='floor'),...monsterCatalog.map(m=>m.id+'_'+m.parts[0]),'vfx_feast_cauldron_aura'];
export const atlasSizes = Object.fromEntries([
 ...[...baselineModels.filter(id=>id.startsWith('prop_')),...environment,...furniture,...landmarks,...vfx,...monsterParts,'weapon_arcane_orb_tome'].map(id=>[id,128]),
 ...[...baselineModels.filter(id=>!id.startsWith('prop_')),...hats,...weapons,'weapon_berserker_greatsword','weapon_priest_staff','guidebook','prop_wayfinder_arrow'].map(id=>[id,64])
]);
export const singleReviewModels = [...heroModels,...vfx];
export const expectedOverrideFiles = [
 'assets/minecraft/atlases/blocks.json','assets/yuumi97/monster_rigs.json',
 ...models.flatMap(id=>[`assets/yuumi97/models/item/${id}.json`,`assets/yuumi97/textures/prop/${id}.png`]),
 ...icons.map(id=>`assets/yuumi97/textures/item/${id}.png`),
 ...selectors.map(id=>`assets/yuumi97/models/${guiTargets[id]}.json`),
 ...[...selectors,...worldPointers].map(id=>`assets/yuumi97/items/${id}.json`)
].sort();
export const contexts = ['gui','ground','fixed','head','firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand'];
export const target = 'Minecraft Java 1.21.4 / pack_format 46';
