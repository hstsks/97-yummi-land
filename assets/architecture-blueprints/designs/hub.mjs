import { Voxel, block } from '../lib/voxel.mjs';

// The hall is a functional open cloister: every decorative layer is kept above
// the service circulation, and interaction coordinates are stamped last.
const stair = (name, facing, half = 'bottom', extra = {}) =>
  block(name, { facing, half, shape: 'straight', waterlogged: 'false', ...extra });
const slab = (name, type = 'bottom') => block(name, { type, waterlogged: 'false' });
const pillar = (name, axis = 'y') => block(name, { axis });
const leaves = () => block('cherry_leaves', { persistent: 'true', distance: '1', waterlogged: 'false' });
const chain = (axis = 'y') => block('chain', { axis, waterlogged: 'false' });
const hangingLamp = () => block('lantern', { hanging: 'true', waterlogged: 'false' });

function pier(v, x, z, height = 7) {
  v.set(x, 1, z, 'chiseled_quartz_block');
  v.fill(x, 2, z, x, height - 1, z, pillar('quartz_pillar'));
  v.set(x, height, z, 'gold_block');
  for (const [dx, dz, facing] of [[-1, 0, 'east'], [1, 0, 'west'], [0, -1, 'south'], [0, 1, 'north']]) {
    if (v.inside(x + dx, height, z + dz)) v.set(x + dx, height, z + dz, stair('smooth_quartz_stairs', facing, 'top'));
  }
}

// Pointed arch: attached stone archivolt, offset cherry intrados and a gold boss.
function arch(v, axis, start, end, fixed, spring, rise, inward) {
  const mid = (start + end) / 2;
  const radius = (end - start) / 2;
  for (let t = start; t <= end; t++) {
    const y = spring + Math.round(rise * Math.pow(1 - Math.abs(t - mid) / radius, 0.65));
    const x = axis === 'x' ? t : fixed;
    const z = axis === 'x' ? fixed : t;
    v.set(x, y, z, 'smooth_quartz');
    v.set(x, y + 1, z, slab('smooth_quartz_slab'));
    const facing = axis === 'x' ? (t < mid ? 'east' : 'west') : (t < mid ? 'south' : 'north');
    v.set(x, y - 1, z, stair('quartz_stairs', facing, 'top'));
    const ix = axis === 'x' ? x : x + inward;
    const iz = axis === 'x' ? z + inward : z;
    if (v.inside(ix, y, iz)) v.set(ix, y, iz, pillar('stripped_cherry_log', axis));
  }
  v.set(axis === 'x' ? Math.round(mid) : fixed, spring + rise, axis === 'x' ? fixed : Math.round(mid), 'ochre_froglight');
}

function hallFloor(v) {
  for (let x = 0; x < v.W; x++) for (let z = 0; z < v.L; z++) {
    const border = x === 0 || x === 28 || z === 0 || z === 24;
    const inset = x === 1 || x === 27 || z === 1 || z === 23;
    let mat = border ? 'polished_diorite' : inset ? 'chiseled_quartz_block' : (x + z) % 5 === 0 ? 'calcite' : 'smooth_quartz';
    const dx = Math.abs(x - 14), dz = Math.abs(z - 12);
    if (dx + dz === 7 || dx === dz && dx >= 3 && dx <= 5) mat = 'gold_block';
    if (dx + dz === 8) mat = 'polished_diorite';
    if ((x === 8 || x === 20) && z >= 3 && z <= 21) mat = 'cherry_planks';
    if ((z === 8 || z === 20) && x >= 3 && x <= 25) mat = 'cherry_planks';
    if ([6, 22].includes(x) && [4, 12, 20].includes(z)) mat = 'ochre_froglight';
    v.set(x, 0, z, mat);
  }
}

function hallShell(v) {
  for (const x of [1, 9, 19, 27]) for (const z of [1, 8, 16, 23]) pier(v, x, z);
  for (const z of [1, 23]) {
    for (const [a, b] of [[1, 9], [9, 19], [19, 27]]) arch(v, 'x', a, b, z, 7, a === 9 ? 5 : 3, z === 1 ? 1 : -1);
  }
  for (const x of [1, 27]) {
    for (const [a, b] of [[1, 8], [8, 16], [16, 23]]) arch(v, 'z', a, b, x, 7, 3, x === 1 ? 1 : -1);
  }
  // Longitudinal inner nave arcades meet the same four support stations.
  for (const x of [9, 19]) for (const [a, b] of [[1, 8], [8, 16], [16, 23]]) arch(v, 'z', a, b, x, 8, 3, x === 9 ? -1 : 1);

  // Stepped roof shells are genuinely pitched. Wooden ribs follow the shell
  // underside at structural bays, instead of a flat ceiling hidden by a roof.
  for (let x = 0; x <= 28; x++) for (let z = 0; z <= 24; z++) {
    let y;
    if (x < 8) y = 11 + Math.floor(x / 4);
    else if (x > 20) y = 11 + Math.floor((28 - x) / 4);
    else y = 12 + Math.round(6 * (1 - Math.abs(x - 14) / 6));
    const facing = x < 14 ? 'east' : 'west';
    v.set(x, y, z, x === 14 ? 'deepslate_tiles' : stair('deepslate_tile_stairs', facing));
    if ([1, 8, 16, 23].includes(z)) {
      v.set(x, y - 1, z, pillar('stripped_cherry_log', 'x'));
      if (x >= 8 && x <= 20) v.set(x, y - 2, z, stair('cherry_stairs', facing, 'top'));
    }
    if (z === 0 || z === 24) v.set(x, y, z, x === 14 ? 'gold_block' : stair('smooth_quartz_stairs', facing));
  }
  for (let z = 1; z <= 23; z++) {
    v.set(14, 19, z, slab('deepslate_tile_slab'));
    if (z % 4 === 0) v.set(14, 20, z, 'gold_block');
  }
  // Clear high lancets along the main nave, framed behind the arch piers.
  for (const x of [8, 20]) for (const [a, b] of [[3, 6], [10, 14], [18, 21]]) {
    v.fill(x, 9, a, x, 10, b, 'pink_stained_glass');
    v.fill(x, 11, a, x, 11, b, pillar('cherry_log', 'z'));
    for (const z of [a, b]) v.fill(x, 9, z, x, 10, z, 'quartz_pillar');
  }
  // A large connected chandelier fills the nave without entering headroom.
  v.fill(14, 11, 12, 14, 17, 12, chain());
  for (let d = -3; d <= 3; d++) {
    v.set(14 + d, 10, 12, d === 0 ? 'ochre_froglight' : pillar('cherry_log', 'x'));
    v.set(14, 10, 12 + d, d === 0 ? 'ochre_froglight' : pillar('cherry_log', 'z'));
  }
  for (const [x, z] of [[11, 12], [17, 12], [14, 9], [14, 15]]) {
    v.set(x, 9, z, hangingLamp());
    v.set(x, 11, z, 'gold_block');
  }
  for (const [x, z] of [[5, 4], [23, 4], [5, 20], [23, 20]]) {
    v.set(x, 7, z, chain());
    v.set(x, 6, z, hangingLamp());
  }
  // Carefully bounded sakura clusters sit on the side roofs, away from clocks.
  for (const [cx, cz] of [[3, 21], [25, 3], [25, 21]]) {
    v.fill(cx, 11, cz, cx, 13, cz, pillar('cherry_log'));
    for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) {
      if (Math.abs(dx) + Math.abs(dz) <= 3) v.set(cx + dx, 13, cz + dz, leaves());
      if (Math.abs(dx) + Math.abs(dz) <= 2) v.set(cx + dx, 14, cz + dz, leaves());
    }
  }
}

function clockTower(v) {
  // Four piers carry a hollow square tower; there is no solid cube in the bay.
  for (const [x, z] of [[3, 3], [9, 3], [3, 9], [9, 9]]) {
    v.fill(x, 1, z, x, 12, z, pillar('quartz_pillar'));
    v.set(x, 11, z, 'gold_block');
  }
  for (let y = 12; y <= 20; y++) for (let x = 3; x <= 9; x++) for (let z = 3; z <= 9; z++) {
    if (x !== 3 && x !== 9 && z !== 3 && z !== 9) continue;
    const corner = (x === 3 || x === 9) && (z === 3 || z === 9);
    v.set(x, y, z, y === 12 || y === 18 || y === 20 ? 'gold_block' : corner ? pillar('quartz_pillar') : 'smooth_quartz');
  }
  // Flat 3×3 blue dial backing with a gold 5×5 frame on all four faces.
  for (let t = 4; t <= 8; t++) for (let y = 14; y <= 18; y++) {
    const mat = t === 4 || t === 8 || y === 14 || y === 18 ? 'gold_block' : 'blue_concrete';
    v.set(t, y, 3, mat); v.set(t, y, 9, mat);
    v.set(3, y, t, mat); v.set(9, y, t, mat);
  }
  // Cap tapers into a gold feline crown, ears rising to local Y=25.
  for (let x = 3; x <= 9; x++) for (let z = 3; z <= 9; z++) v.set(x, 21, z, stair('deepslate_tile_stairs', x < 6 ? 'east' : 'west'));
  v.fill(4, 22, 4, 8, 22, 8, 'gold_block');
  v.fill(5, 23, 5, 7, 23, 7, 'ochre_froglight');
  v.set(5, 24, 6, 'gold_block'); v.set(7, 24, 6, 'gold_block');
  v.set(4, 24, 6, 'gold_block'); v.set(8, 24, 6, 'gold_block');
  v.set(4, 25, 6, 'gold_block'); v.set(8, 25, 6, 'gold_block');
  v.set(6, 24, 6, 'ochre_froglight');
}

function utilities(v) {
  // Tall recessed nonflammable forge surround and its visible flue.
  v.fill(2, 1, 15, 2, 5, 19, 'polished_diorite');
  v.fill(2, 6, 16, 3, 12, 18, 'deepslate_tiles');
  for (const z of [15, 19]) v.fill(3, 1, z, 3, 4, z, 'polished_diorite');
  v.fill(3, 4, 15, 3, 4, 19, pillar('quartz_pillar', 'z'));
  v.set(3, 3, 17, 'ochre_froglight');
  for (const z of [16, 17, 18]) v.set(3, 1, z, block('blast_furnace', { facing: 'east', lit: 'true' }));
  for (const z of [10, 11, 12]) v.set(3, 1, z, block('anvil', { facing: 'north' }));
  for (const z of [5, 6]) v.set(3, 1, z, 'smithing_table');
  for (const z of [13, 14]) v.set(3, 1, z, block('grindstone', { face: 'floor', facing: 'east' }));
  v.set(3, 1, 15, block('stonecutter', { facing: 'east' }));

  // U-shaped active bookshelf ring. Every shelf is distance two from the
  // table, and the intervening x13..15/z3..5 cells remain actual AIR.
  const shelves = [];
  for (let x = 12; x <= 16; x++) shelves.push([x, 2]);
  for (let z = 3; z <= 6; z++) shelves.push([12, z], [16, z]);
  for (const [x, z] of shelves) for (const y of [1, 2]) v.set(x, y, z, 'bookshelf');
  for (const [x, z] of shelves) v.set(x, 3, z, slab('cherry_slab'));
  v.set(14, 1, 4, 'enchanting_table');
  for (const x of [10, 18]) v.set(x, 1, 4, block('brewing_stand', { has_bottle_0: 'true', has_bottle_1: 'true', has_bottle_2: 'true' }));
  v.set(10, 1, 6, 'cartography_table');
  v.set(18, 1, 6, block('loom', { facing: 'west' }));

  // East furnishings are wall-side so both NPC pads and through-circulation
  // remain independent. Ender-chest lids have two complete air blocks above.
  for (const z of [10, 11]) v.set(25, 1, z, block('ender_chest', { facing: 'west', waterlogged: 'false' }));
  for (const z of [13, 14, 15]) v.set(25, 1, z, 'crafting_table');
  for (const z of [16, 17, 18]) v.set(25, 1, z, block('smoker', { facing: 'west', lit: 'true' }));
  v.fill(26, 1, 16, 26, 5, 18, 'polished_diorite');
  v.fill(25, 4, 16, 25, 4, 18, pillar('quartz_pillar', 'z'));
  v.set(25, 3, 17, 'ochre_froglight');
  v.set(25, 1, 19, 'lava_cauldron');
  for (const [x, z] of [[24, 19], [26, 19], [24, 20], [25, 20], [26, 20]]) v.set(x, 1, z, block('cherry_fence', { north: z === 20 ? 'true' : 'false', south: z === 19 ? 'true' : 'false', east: x < 26 && z === 20 ? 'true' : 'false', west: x > 24 && z === 20 ? 'true' : 'false', waterlogged: 'false' }));

  // Water is contained by four waterlogged stair basins and closed corners.
  v.set(14, 0, 12, 'sea_lantern');
  for (let x = 13; x <= 15; x++) for (let z = 11; z <= 13; z++) {
    if (x === 14 && z === 12) v.set(x, 1, z, block('water', { level: '0' }));
    else if (x === 14) v.set(x, 1, z, stair('quartz_stairs', z === 11 ? 'north' : 'south', 'bottom', { waterlogged: 'true' }));
    else if (z === 12) v.set(x, 1, z, stair('quartz_stairs', x === 13 ? 'west' : 'east', 'bottom', { waterlogged: 'true' }));
    else v.set(x, 1, z, 'chiseled_quartz_block');
  }
  // Fixed pads are air reservations, not decorative pedestals under NPC feet.
  for (const [x, z] of [[5, 7], [23, 7], [23, 15]]) {
    v.fill(x - 1, 1, z - 1, x + 1, 4, z + 1, 'air');
    v.set(x, 0, z, 'chiseled_quartz_block');
  }
}

function buildHall() {
  const v = new Voxel('hub_grand_utility_hall', [29, 26, 25], [-36, 64, -44]);
  hallFloor(v); hallShell(v); clockTower(v); utilities(v);
  v.meta = {
    title: '문턱의 만능 길드 대공방', theme: 'cherry-platinum-gothic', world: 'yuumi_hub',
    anchors: [
      { name: 'entrance_south', kind: 'entityFeet', local: [14.5, 1, 24.5] },
      { name: 'entrance_west', kind: 'entityFeet', local: [0.5, 1, 12.5] },
      { name: 'entrance_east', kind: 'entityFeet', local: [28.5, 1, 12.5] },
      { name: 'entrance_north', kind: 'entityFeet', local: [21.5, 1, 0.5] },
      { name: 'smith_npc', kind: 'entityFeet', local: [5.5, 1, 7.5] },
      { name: 'vault_npc', kind: 'entityFeet', local: [23.5, 1, 7.5] },
      { name: 'carpenter_chef_npc', kind: 'entityFeet', local: [23.5, 1, 15.5] },
      { name: 'enchanting_table', kind: 'block', local: [14, 1, 4] },
      { name: 'enchanting_approach', kind: 'entityFeet', local: [14.5, 1, 6.5] },
      { name: 'ender_chest_approach', kind: 'entityFeet', local: [24.5, 1, 10.5] },
      { name: 'forge_approach', kind: 'entityFeet', local: [4.5, 1, 11.5] },
      { name: 'holy_water', kind: 'block', local: [14, 1, 12] },
      { name: 'disposal_cauldron', kind: 'block', local: [25, 1, 19] },
    ],
    routes: [
      { name: 'south_to_enchanting', points: [[14.5, 1, 24.5], [14.5, 1, 17.5], [11.5, 1, 17.5], [11.5, 1, 8.5], [14.5, 1, 8.5], [14.5, 1, 6.5]] },
      { name: 'west_to_smith', points: [[0.5, 1, 12.5], [2.5, 1, 12.5], [2.5, 1, 7.5], [5.5, 1, 7.5]] },
      { name: 'east_to_vault', points: [[28.5, 1, 12.5], [23.5, 1, 12.5], [23.5, 1, 7.5]] },
      { name: 'north_to_carpenter', points: [[21.5, 1, 0.5], [21.5, 1, 15.5], [23.5, 1, 15.5]] },
    ],
    reservations: [
      ...[[5, 7], [23, 7], [23, 15]].map(([x, z]) => ({ name: `npc_${x}_${z}`, min: [x - 1, 1, z - 1], max: [x + 1, 4, z + 1], require: 'air' })),
      { name: 'ender_lids', min: [25, 2, 10], max: [25, 3, 11], require: 'air' },
    ],
    clockMounts: [
      { face: 'north', plane: 3, center: [6.5, 16.5, 3], backing: [5, 15, 3, 7, 17, 3] },
      { face: 'south', plane: 10, center: [6.5, 16.5, 10], backing: [5, 15, 9, 7, 17, 9] },
      { face: 'west', plane: 3, center: [3, 16.5, 6.5], backing: [3, 15, 5, 3, 17, 7] },
      { face: 'east', plane: 10, center: [10, 16.5, 6.5], backing: [9, 15, 5, 9, 17, 7] },
    ],
    features: ['four-open-arcades', 'ribbed-pitched-nave', 'four-clock-dial-backs', '26-active-bookshelves', 'contained-waterlogged-basin', 'three-functional-service-bays'],
  };
  return v;
}

function buildKiosk() {
  const v = new Voxel('hub_guidebook_kiosk', [7, 9, 7], [5, 64, 5]);
  v.fill(0, 0, 0, 6, 0, 6, 'polished_diorite');
  v.fill(2, 1, 2, 4, 1, 4, 'smooth_quartz');
  // Stairs are inset one cell: the perimeter remains a full standing landing,
  // so a 0.6m-wide player can enter each half tread without leaving the NBT.
  for (let d = 2; d <= 4; d++) {
    v.set(d, 1, 1, stair('smooth_quartz_stairs', 'south'));
    v.set(d, 1, 5, stair('smooth_quartz_stairs', 'north'));
    v.set(1, 1, d, stair('smooth_quartz_stairs', 'east'));
    v.set(5, 1, d, stair('smooth_quartz_stairs', 'west'));
  }
  for (const [x, z] of [[1, 1], [1, 5], [5, 1], [5, 5]]) {
    v.set(x, 1, z, 'gold_block');
    v.set(x, 2, z, 'chiseled_quartz_block');
    v.fill(x, 3, z, x, 4, z, pillar('stripped_cherry_log'));
    v.set(x, 5, z, 'quartz_pillar');
  }
  for (const z of [1, 5]) for (let x = 1; x <= 5; x++) v.set(x, 6, z, pillar('cherry_log', 'x'));
  for (const x of [1, 5]) for (let z = 1; z <= 5; z++) v.set(x, 6, z, pillar('cherry_log', 'z'));
  // A shallow stepped dome, whose silhouette is completed by two cat ears.
  for (let x = 0; x <= 6; x++) for (let z = 0; z <= 6; z++) {
    const r = Math.max(Math.abs(x - 3), Math.abs(z - 3));
    if (r === 3 && (x === 0 || x === 6) && (z === 0 || z === 6)) continue;
    if (r === 3) v.set(x, 6, z, slab('smooth_quartz_slab'));
    else if (r === 2) v.set(x, 6, z, 'smooth_quartz');
    else v.set(x, 7, z, x === 3 && z === 3 ? 'ochre_froglight' : 'smooth_quartz');
  }
  for (const x of [1, 5]) {
    v.set(x, 7, 2, 'gold_block'); v.set(x, 7, 3, 'smooth_quartz');
    v.set(x, 8, 2, 'gold_block');
    v.set(x, 7, 4, stair('cherry_stairs', x === 1 ? 'east' : 'west'));
  }
  for (const [x, z] of [[1, 3], [5, 3], [3, 1], [3, 5]]) v.set(x, 5, z, hangingLamp());
  v.set(3, 1, 3, 'gold_block');
  v.set(3, 2, 3, block('lectern', { facing: 'south', has_book: 'false', powered: 'false' }));
  v.fill(2, 3, 2, 4, 5, 4, 'air');
  v.meta = {
    title: '중앙 광장 만능 가이드북 키오스크', world: 'yuumi_hub',
    anchors: [
      { name: 'guidebook_lectern', kind: 'block', local: [3, 2, 3] },
      { name: 'lectern_approach_south', kind: 'entityFeet', local: [3.5, 2, 4.5] },
      { name: 'lectern_approach_north', kind: 'entityFeet', local: [3.5, 2, 2.5] },
      { name: 'lectern_approach_west', kind: 'entityFeet', local: [2.5, 2, 3.5] },
      { name: 'lectern_approach_east', kind: 'entityFeet', local: [4.5, 2, 3.5] },
    ],
    routes: [
      { name: 'south_stair', points: [[3.5, 1, 6.5], [3.5, 1.5, 5.8], [3.5, 2, 5.2], [3.5, 2, 4.5]] },
      { name: 'north_stair', points: [[3.5, 1, 0.5], [3.5, 1.5, 1.2], [3.5, 2, 1.8], [3.5, 2, 2.5]] },
      { name: 'west_stair', points: [[0.5, 1, 3.5], [1.2, 1.5, 3.5], [1.8, 2, 3.5], [2.5, 2, 3.5]] },
      { name: 'east_stair', points: [[6.5, 1, 3.5], [5.8, 1.5, 3.5], [5.2, 2, 3.5], [4.5, 2, 3.5]] },
    ],
    reservations: [{ name: 'guidebook_hologram', min: [2, 3, 2], max: [4, 5, 4], require: 'air' }],
    features: ['four-sided-stair-plinth', 'south-facing-lectern', 'supported-cat-ear-dome', 'reserved-3x3-hologram'],
  };
  return v;
}

export function buildHub() { return [buildHall(), buildKiosk()]; }
