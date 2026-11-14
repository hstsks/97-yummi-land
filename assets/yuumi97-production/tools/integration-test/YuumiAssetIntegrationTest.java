package kr.yuumi97.assettest;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipFile;

/** One-shot integration assertions. Deliberately refuses production and all players. */
public final class YuumiAssetIntegrationTest extends JavaPlugin {
    private static final Path EXPECTED_ROOT = Path.of(
            "E:/MinGem/assets/yuumi97-production/review/server-test");
    private static final Map<String, Integer> EXPECTED_PROPS = Map.of(
            "prop_yuumi_statue", 1, "prop_slot_cabinet", 7,
            "prop_roulette_wheel", 1, "prop_derby_trophy", 1);
    private static final int[][] STATUE_POSITIONS = {
            {0, 69, 0}, {-1, 70, 0}, {1, 70, 0}, {0, 70, 0}};
    private static final Material[] STATUE_ORIGINAL = {
            Material.GOLD_BLOCK, Material.PINK_CONCRETE,
            Material.PINK_CONCRETE, Material.SEA_LANTERN};
    private final List<Result> results = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final List<ChunkPoint> unloadedChunks = new ArrayList<>();
    private final Set<UUID> unloadedPropIds = new LinkedHashSet<>();
    private Plugin core;
    private Object hubManager;
    private Object items;
    private World hub;
    private NamespacedKey propKey, holoKey, statueKey;
    private Path serverRoot, packPath, baselinePath;
    private byte[] finalPackBytes;
    private String finalPackSha256;
    private Instant started;
    private boolean armed, finished, failed;

    @Override public void onEnable() {
        try {
            serverRoot = Path.of("").toAbsolutePath().normalize().toRealPath();
            Path worldContainer = getServer().getWorldContainer().toPath()
                    .toAbsolutePath().normalize().toRealPath();
            if (!Boolean.getBoolean("yuumi.asset.integration")
                    || !serverRoot.equals(EXPECTED_ROOT.toRealPath())
                    || !worldContainer.equals(serverRoot)) {
                getLogger().severe("REFUSED: requires isolated server-test cwd/worldContainer and "
                        + "-Dyuumi.asset.integration=true. No test files or world changes made.");
                return;
            }
            armed = true;
            getLogger().info("Armed for isolated one-shot test; waiting 100 ticks for core startup.");
            Bukkit.getScheduler().runTaskLater(this, () -> phase("initial", this::begin), 100L);
        } catch (Throwable error) {
            getLogger().severe("REFUSED before test setup: " + error);
        }
    }

    @Override public void onDisable() {
        if (armed && started != null && !finished) {
            failed = true;
            results.add(new Result("completion", "FAIL", "Plugin disabled before all phases finished"));
            finish();
        }
    }

    private void begin() throws Exception {
        started = Instant.now();
        noPlayers();
        core = Bukkit.getPluginManager().getPlugin("YuumiLandCore");
        check("core.enabled", core != null && core.isEnabled(), "YuumiLandCore must be enabled");
        hubManager = field(core, "hubManager");
        items = field(core, "items");
        hub = (World) call(hubManager, "getHubWorld");
        check("hub.loaded", hub != null && "yuumi_hub".equals(hub.getName()), "Expected yuumi_hub");
        // Confirms the core's 40-tick city task actually completed rather than building it here.
        Object data = field(core, "dataConfig");
        boolean cityBuilt = (boolean) data.getClass().getMethod("getBoolean", String.class)
                .invoke(data, "hub_v2.built");
        check("hub.city_already_built", cityBuilt, "No setup-hub/buildEntireHubCity call is made by this test");
        propKey = new NamespacedKey(core, "hub_3d_prop_id");
        holoKey = new NamespacedKey(core, "hub_prop_hologram");
        statueKey = new NamespacedKey(core, "hub_statue_replaced_blocks");
        packPath = serverRoot.resolve("resourcepack/Yuumi97Pack.zip");
        baselinePath = getDataFolder().toPath().resolve("baseline-pack.zip");
        check("pack.final_exists", Files.isRegularFile(packPath), packPath.toString());
        check("pack.baseline_exists", Files.isRegularFile(baselinePath), baselinePath.toString());
        check("pack.final_contains_four_props", packPropIds(packPath).equals(EXPECTED_PROPS.keySet()),
                "Final ZIP must contain exactly the four known prop item definitions");
        check("pack.baseline_contains_no_props", packPropIds(baselinePath).isEmpty(),
                "Baseline ZIP must contain no prop_* item definitions");
        finalPackBytes = Files.readAllBytes(packPath);
        finalPackSha256 = digest(finalPackBytes);
        Files.createDirectories(getDataFolder().toPath());
        Files.write(getDataFolder().toPath().resolve("final-pack.recovery.zip"), finalPackBytes);
        checkProps("startup", true);
        checkStatue("startup", true);
        checkHolograms("startup", true);
        checkInteractiveBlocks("startup");
        testItems();
        refresh(); checkProps("refresh_once", true);
        refresh(); checkProps("refresh_twice", true);
        prepareChunkRoundTrip();
    }

    private void prepareChunkRoundTrip() throws Exception {
        noPlayers();
        // Deduplicate all mount chunks. Spawn/forced/plugin-ticket chunks may refuse unload.
        Set<ChunkPoint> chunks = new LinkedHashSet<>();
        for (int[] p : new int[][]{{0,0},{-1,0},{44,-16},{44,-20},{44,-24},{44,-28},
                {44,-18},{44,-22},{44,-26},{79,-23},{72,-22}}) {
            chunks.add(new ChunkPoint(p[0] >> 4, p[1] >> 4));
        }
        hub.save();
        for (ChunkPoint p : chunks) {
            if (hub.isChunkForceLoaded(p.x, p.z) || !hub.getPluginChunkTickets(p.x, p.z).isEmpty()) {
                notes.add("Chunk " + p + " retained: forced or plugin ticket; test did not remove tickets.");
                continue;
            }
            Chunk chunk = hub.getChunkAt(p.x, p.z);
            Set<UUID> ids = new LinkedHashSet<>();
            for (var entity : chunk.getEntities()) {
                if (entity instanceof ItemDisplay && entity.getPersistentDataContainer()
                        .has(propKey, PersistentDataType.STRING)) ids.add(entity.getUniqueId());
            }
            boolean accepted = hub.unloadChunk(p.x, p.z, true);
            if (accepted && !hub.isChunkLoaded(p.x, p.z)) {
                unloadedChunks.add(p); unloadedPropIds.addAll(ids);
            } else {
                notes.add("Chunk " + p + " declined synchronous unload; existing tickets unchanged.");
            }
        }
        if (unloadedChunks.isEmpty()) {
            results.add(new Result("chunks.unload_reload", "SKIP", "No mount chunk could be unloaded without changing tickets"));
            phase("pack_roundtrip", this::packRoundTrip);
            return;
        }
        results.add(new Result("chunks.unload", "PASS", unloadedChunks.toString()));
        Bukkit.getScheduler().runTaskLater(this, () -> phase("chunks.reload", () -> {
            noPlayers();
            for (ChunkPoint p : unloadedChunks) {
                hub.loadChunk(p.x, p.z);
                hub.getChunkAt(p.x, p.z).getEntities();
            }
            Bukkit.getScheduler().runTaskLater(this, () -> phase("chunks.verify", () -> {
                noPlayers();
                Set<UUID> restored = new LinkedHashSet<>();
                for (ItemDisplay entity : hub.getEntitiesByClass(ItemDisplay.class)) {
                    if (entity.isValid() && entity.getPersistentDataContainer().has(propKey, PersistentDataType.STRING))
                        restored.add(entity.getUniqueId());
                }
                check("chunks.persistent_entities_restored", restored.containsAll(unloadedPropIds),
                        "Expected saved prop UUIDs: " + unloadedPropIds.size());
                checkProps("after_chunk_reload", true);
                refresh(); checkProps("after_chunk_refresh", true);
                packRoundTrip();
            }), 20L);
        }), 20L);
    }

    private void packRoundTrip() throws Exception {
        noPlayers();
        try {
            replacePack(Files.readAllBytes(baselinePath));
            refresh();
            checkProps("baseline_pack", false);
            checkStatue("baseline_pack", false);
            checkHolograms("baseline_pack", false);
            checkInteractiveBlocks("baseline_pack");
        } finally {
            // Runs even when a baseline assertion or refresh throws.
            restoreFinalPackAndRefresh();
        }
        check("pack.final_bytes_restored", finalPackSha256.equals(digest(Files.readAllBytes(packPath))),
                "Final ZIP SHA-256 " + finalPackSha256);
        checkProps("final_restored", true);
        checkStatue("final_restored", true);
        checkHolograms("final_restored", true);
        checkInteractiveBlocks("final_restored");
        finish();
    }

    private void checkProps(String phase, boolean present) throws Exception {
        Map<String,Integer> counts = new TreeMap<>();
        for (ItemDisplay entity : hub.getEntitiesByClass(ItemDisplay.class)) {
            if (!entity.isValid()) continue;
            String id = entity.getPersistentDataContainer().get(propKey, PersistentDataType.STRING);
            if (id == null) continue;
            counts.merge(id, 1, Integer::sum);
            if (present) {
                require(entity.getItemDisplayTransform() == ItemDisplay.ItemDisplayTransform.NONE,
                        phase + ": unexpected item transform for " + id);
                require(entity.getBillboard() == Display.Billboard.FIXED,
                        phase + ": unexpected billboard for " + id);
                require(entity.getItemStack().getType() == Material.PAPER,
                        phase + ": prop item must be PAPER");
                require(new NamespacedKey("yuumi97", id).equals(entity.getItemStack().getItemMeta().getItemModel()),
                        phase + ": prop model metadata mismatch for " + id);
            }
        }
        check(phase + ".prop_counts", counts.equals(present ? EXPECTED_PROPS : Map.of()), counts.toString());
    }

    private void checkStatue(String phase, boolean present) {
        for (int i=0;i<STATUE_POSITIONS.length;i++) {
            int[] p=STATUE_POSITIONS[i];
            Material actual=hub.getBlockAt(p[0],p[1],p[2]).getType();
            require(present ? actual.isAir() : actual == STATUE_ORIGINAL[i],
                    phase + ": wrong statue decoration at " + Arrays.toString(p) + " = " + actual);
        }
        int mask=hub.getPersistentDataContainer().getOrDefault(statueKey,PersistentDataType.INTEGER,0);
        check(phase + ".statue_four_blocks_and_marker", mask == (present?15:0), "replacement mask="+mask);
    }

    private void checkHolograms(String phase, boolean present) {
        Map<String,Location> expected=new LinkedHashMap<>();
        expected.put("fountain",new Location(hub,.5,present?74.2:71.8,.5));
        for(int z:new int[]{-16,-20,-24,-28}) expected.put("slot_1f_"+z,new Location(hub,44.5,present?69:68.1,z+.5));
        for(int z:new int[]{-18,-22,-26}) expected.put("slot_2f_"+z,new Location(hub,44.5,present?75:74.1,z+.5));
        Map<String,Integer> counts=new TreeMap<>();
        for(TextDisplay text:hub.getEntitiesByClass(TextDisplay.class)) {
            if(!text.isValid()) continue;
            String id=text.getPersistentDataContainer().get(holoKey,PersistentDataType.STRING);
            if(!expected.containsKey(id)) continue;
            counts.merge(id,1,Integer::sum);
            require(text.getLocation().distanceSquared(expected.get(id))<.0001,
                    phase+": hologram "+id+" at "+text.getLocation());
        }
        require(counts.size()==expected.size(),phase+": expected 8 managed holograms, got "+counts);
        check(phase+".holograms",counts.values().stream().allMatch(n->n==1),counts.toString());
    }

    private void checkInteractiveBlocks(String phase) {
        for(int z:new int[]{-16,-20,-24,-28}) require(hub.getBlockAt(44,66,z).getType()==Material.NOTE_BLOCK,phase+": missing 1F note block "+z);
        for(int z:new int[]{-18,-22,-26}) require(hub.getBlockAt(44,72,z).getType()==Material.NOTE_BLOCK,phase+": missing 2F note block "+z);
        require(hub.getBlockAt(79,65,-23).getType()==Material.LODESTONE,phase+": missing roulette lodestone");
        for(int z:new int[]{-24,-22}) require(hub.getBlockAt(79,65,z).getType()==Material.REDSTONE_LAMP,phase+": missing roulette lamp");
        check(phase+".interactive_blocks_preserved",true,"7 NOTE_BLOCK + LODESTONE + 2 REDSTONE_LAMP");
    }

    private void testItems() throws Exception {
        ItemStack hat=item("yuumiEarsHat");
        checkItem("hat",hat,Material.PAPER,"hat_yuumi_ears");
        ItemMeta meta=hat.getItemMeta();
        require(meta.hasEquippable(),"Hat missing equippable component");
        var eq=meta.getEquippable();
        check("hat.equippable",eq.getSlot()==EquipmentSlot.HEAD&&eq.getCameraOverlay()==null
                        &&eq.getModel()==null&&!eq.isDamageOnHurt()&&meta.hasMaxStackSize()&&meta.getMaxStackSize()==1,
                "HEAD, no overlay/equipment asset, no damage, max stack 1");
        checkItem("plushie",item("yuumiPlushie"),Material.PAPER,"plushie_yuumi");
        checkItem("wand",item("prowlingWand"),Material.BLAZE_ROD,"prowling_wand");
        checkItem("bell",item("youAndMeBell"),Material.BELL,"you_and_me_bell");
        checkItem("book",item("bookOfThresholds"),Material.KNOWLEDGE_BOOK,"book_of_thresholds");
    }

    private void checkItem(String label,ItemStack item,Material material,String id) throws Exception {
        String actualId=(String)items.getClass().getMethod("getCustomId",ItemStack.class).invoke(items,item);
        check(label+".identity",item.getType()==material&&id.equals(actualId)
                &&new NamespacedKey("yuumi97",id).equals(item.getItemMeta().getItemModel()),
                item.getType()+" / customId="+actualId+" / model="+item.getItemMeta().getItemModel());
    }

    private void noPlayers() {
        require(Bukkit.getOnlinePlayers().isEmpty(),"Refusing test mutation while a player is connected");
    }
    private void refresh() throws Exception {call(hubManager,"refreshCustom3DProps");}
    private ItemStack item(String name) throws Exception {return (ItemStack)call(items,name);}
    private Object field(Object target,String name) throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    private Object call(Object target,String name) throws Exception {
        try{return target.getClass().getMethod(name).invoke(target);}
        catch(InvocationTargetException e){
            if(e.getCause() instanceof Exception cause)throw cause;
            if(e.getCause() instanceof Error cause)throw cause;
            throw e;
        }
    }
    private Set<String> packPropIds(Path zip) throws Exception {
        Set<String> result=new LinkedHashSet<>();String prefix="assets/yuumi97/items/";
        try(ZipFile zf=new ZipFile(zip.toFile())) {
            zf.stream().map(e->e.getName()).filter(n->n.startsWith(prefix+"prop_")&&n.endsWith(".json"))
                    .forEach(n->result.add(n.substring(prefix.length(),n.length()-5)));
        }
        return result;
    }
    private void replacePack(byte[] bytes) throws Exception {
        Path temporary=packPath.resolveSibling("Yuumi97Pack.integration.tmp");
        Files.write(temporary,bytes);
        try{Files.move(temporary,packPath,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(temporary,packPath,StandardCopyOption.REPLACE_EXISTING);}
    }
    private void restoreFinalPackAndRefresh() throws Exception {
        if(finalPackBytes==null)return;
        replacePack(finalPackBytes);
        // Restoration is unconditional, even if the no-player prerequisite was later violated.
        if(hubManager!=null&&core!=null&&core.isEnabled())refresh();
    }
    private void phase(String label,CheckedRunnable action) {
        if(finished)return;
        try{action.run();}
        catch(Throwable error){
            failed=true;results.add(new Result(label,"FAIL",error.toString()));
            getLogger().severe(label+" FAILED: "+error);finish();
        }
    }
    private void check(String name,boolean ok,String detail) {
        results.add(new Result(name,ok?"PASS":"FAIL",detail));
        if(!ok){failed=true;throw new AssertionError(name+": "+detail);}
    }
    private void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private String digest(byte[] bytes) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}

    private void finish() {
        if(finished)return;
        try {
            restoreFinalPackAndRefresh();
            if(finalPackBytes!=null) {
                boolean ok=finalPackSha256.equals(digest(Files.readAllBytes(packPath)));
                results.add(new Result("cleanup.final_zip_restored",ok?"PASS":"FAIL",finalPackSha256));
                failed|=!ok;
                if(hub!=null)checkProps("cleanup",true);
            }
        } catch(Throwable error) {
            failed=true;results.add(new Result("cleanup","FAIL",error.toString()));
            getLogger().severe("Cleanup failed; recovery ZIP retained in plugin data folder: "+error);
        } finally {
            finished=true;
            String status=failed?"FAIL":results.stream().anyMatch(r->r.status.equals("SKIP"))?"PASS_WITH_SKIPS":"PASS";
            try{writeReport(status);}catch(Throwable error){getLogger().severe("Report write failed: "+error);}
            getLogger().info("ASSET_INTEGRATION_TEST_COMPLETE "+status);
        }
    }

    private void writeReport(String status) throws Exception {
        Files.createDirectories(getDataFolder().toPath());
        StringBuilder json=new StringBuilder("{\n  \"status\": ").append(quote(status))
                .append(",\n  \"started\": ").append(quote(String.valueOf(started)))
                .append(",\n  \"finished\": ").append(quote(Instant.now().toString()))
                .append(",\n  \"server_root\": ").append(quote(serverRoot.toString()))
                .append(",\n  \"validation_boundary\": \"Server API integration only; no client rendering, resource download or gameplay click simulation\",")
                .append("\n  \"results\": [\n");
        StringBuilder plain=new StringBuilder("Status: ").append(status).append("\nServer: ").append(serverRoot).append('\n');
        for(int i=0;i<results.size();i++) {
            Result r=results.get(i);
            if(i>0)json.append(",\n");
            json.append("    {\"test\": ").append(quote(r.name)).append(", \"status\": ")
                    .append(quote(r.status)).append(", \"detail\": ").append(quote(r.detail)).append('}');
            plain.append(r.status).append(" | ").append(r.name).append(" | ").append(r.detail).append('\n');
        }
        json.append("\n  ],\n  \"notes\": [");
        for(int i=0;i<notes.size();i++){if(i>0)json.append(", ");json.append(quote(notes.get(i)));plain.append("NOTE | ").append(notes.get(i)).append('\n');}
        json.append("]\n}\n");
        Files.writeString(getDataFolder().toPath().resolve("report.json"),json,StandardCharsets.UTF_8);
        Files.writeString(getDataFolder().toPath().resolve("report.txt"),plain,StandardCharsets.UTF_8);
    }
    private static String quote(String s) {
        if(s==null)return "null";
        StringBuilder b=new StringBuilder("\"");
        for(char c:s.toCharArray())switch(c){
            case '"'->b.append("\\\"");case '\\'->b.append("\\\\");case '\n'->b.append("\\n");
            case '\r'->b.append("\\r");case '\t'->b.append("\\t");
            default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}
        }
        return b.append('"').toString();
    }
    @FunctionalInterface private interface CheckedRunnable {void run() throws Exception;}
    private record ChunkPoint(int x,int z) {}
    private record Result(String name,String status,String detail) {}
}
