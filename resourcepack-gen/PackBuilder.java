import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Yuumi's 97Land Procedural Pixel-Art Resource Pack Builder (Minecraft 1.21.4, pack_format 46).
 *
 * Generates:
 *  - server/resourcepack/Yuumi97Pack.zip
 *  - server/resourcepack/Yuumi97Pack.sha1
 *  - server/server-icon.png (64x64 server list icon)
 *
 * Designed to be clean and data-driven so any developer or AI model (Gemini, Claude, GPT)
 * can add new custom items in minutes by appending a SpriteSpec entry.
 */
public class PackBuilder {

    public record SpriteSpec(
            String id,
            boolean handheld,
            Map<Character, Integer> palette,
            String[] rows
    ) {}

    public static void main(String[] args) throws Exception {
        Path rootDir = args.length > 0 ? Paths.get(args[0]) : Paths.get("..").toAbsolutePath().normalize();
        Path outDir = rootDir.resolve("server").resolve("resourcepack");
        Path serverDir = rootDir.resolve("server");
        Files.createDirectories(outDir);
        Files.createDirectories(serverDir);

        Map<String, SpriteSpec> sprites = buildCatalog();
        addV3Sprites(sprites);
        Map<String, byte[]> entries = new LinkedHashMap<>();

        // 1. pack.mcmeta (Minecraft 1.21.4 -> pack_format 46)
        String mcmeta = """
                {
                  "pack": {
                    "pack_format": 46,
                    "description": "\\u00A7d\\u00A7l[유미의 97랜드] \\u00A7f공식 고양이 마을 & 카지노 리소스팩"
                  }
                }
                """;
        entries.put("pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));

        // 2. pack.png (64x64 cute Yuumi cat emblem) & server-icon.png
        BufferedImage packIcon = renderServerIcon64(sprites.get("slot_yuumi"));
        entries.put("pack.png", toPngBytes(packIcon));
        ImageIO.write(packIcon, "PNG", serverDir.resolve("server-icon.png").toFile());

        // 3. All baseline custom item textures, models, and 1.21.4 item definitions
        for (SpriteSpec spec : sprites.values()) {
            BufferedImage img = render16(spec);
            entries.put("assets/yuumi97/textures/item/" + spec.id() + ".png", toPngBytes(img));

            String parent = spec.handheld() ? "minecraft:item/handheld" : "minecraft:item/generated";
            String modelJson = """
                    {
                      "parent": "%s",
                      "textures": {
                        "layer0": "yuumi97:item/%s"
                      }
                    }
                    """.formatted(parent, spec.id());
            entries.put("assets/yuumi97/models/item/" + spec.id() + ".json", modelJson.getBytes(StandardCharsets.UTF_8));

            String itemDefJson = """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "yuumi97:item/%s"
                      }
                    }
                    """.formatted(spec.id());
            entries.put("assets/yuumi97/items/" + spec.id() + ".json", itemDefJson.getBytes(StandardCharsets.UTF_8));
        }

        // 4. Overlay custom 3D JSON models & high-detail PNG textures from resourcepack-gen/overrides/ (e.g. authored by Codex / Blockbench)
        Path overridesDir = rootDir.resolve("resourcepack-gen").resolve("overrides");
        int overrideCount = 0;
        if (Files.isDirectory(overridesDir)) {
            try (Stream<Path> walk = Files.walk(overridesDir)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String fileName = file.getFileName().toString();
                    if (fileName.equalsIgnoreCase("README.md") || fileName.equals(".gitkeep")) continue;
                    String rel = overridesDir.relativize(file).toString().replace('\\', '/');
                    entries.put(rel, Files.readAllBytes(file));
                    overrideCount++;

                    // Auto-generate 1.21.4 item definition wrapper if a new 3D model is added under models/item/<id>.json
                    String modelPrefix = "assets/yuumi97/models/item/";
                    if (rel.startsWith(modelPrefix) && rel.endsWith(".json")) {
                        String modelId = rel.substring(modelPrefix.length(), rel.length() - 5);
                        String itemDefPath = "assets/yuumi97/items/" + modelId + ".json";
                        if (!entries.containsKey(itemDefPath) && !Files.exists(overridesDir.resolve(itemDefPath))) {
                            String autoDef = """
                                    {
                                      "model": {
                                        "type": "minecraft:model",
                                        "model": "yuumi97:item/%s"
                                      }
                                    }
                                    """.formatted(modelId);
                            entries.put(itemDefPath, autoDef.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
            }
        }

        Path zipPath = outDir.resolve("Yuumi97Pack.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                zos.putNextEntry(ze);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }

        // Compute SHA-1 hash for Paper resource pack verification
        byte[] zipBytes = Files.readAllBytes(zipPath);
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest(zipBytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        Path shaPath = outDir.resolve("Yuumi97Pack.sha1");
        Files.writeString(shaPath, hex.toString(), StandardCharsets.UTF_8);

        System.out.println("[PackBuilder] Built " + sprites.size() + " baseline sprites + " + overrideCount + " custom overrides -> " + zipPath);
        System.out.println("[PackBuilder] SHA-1: " + hex + " (" + zipBytes.length + " bytes)");
    }

    private static BufferedImage render16(SpriteSpec spec) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        String[] rows = spec.rows();
        for (int y = 0; y < 16; y++) {
            String row = y < rows.length ? rows[y] : "";
            for (int x = 0; x < 16; x++) {
                char c = x < row.length() ? row.charAt(x) : '.';
                if (c == '.' || c == ' ') {
                    img.setRGB(x, y, 0x00000000);
                } else {
                    Integer rgb = spec.palette().get(c);
                    if (rgb == null) {
                        img.setRGB(x, y, 0xFFFF00FF);
                    } else {
                        img.setRGB(x, y, 0xFF000000 | rgb);
                    }
                }
            }
        }
        return img;
    }

    private static BufferedImage renderServerIcon64(SpriteSpec yuumiFace) {
        BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Magical night-sky gradient background
        for (int y = 0; y < 64; y++) {
            float t = y / 63f;
            int r = (int) (36 + t * 48);
            int gr = (int) (22 + t * 28);
            int b = (int) (78 + t * 65);
            g.setColor(new Color(r, gr, b));
            g.fillRect(0, y, 64, 1);
        }

        // Gold ornate border
        g.setColor(new Color(0xF6C945));
        g.drawRect(1, 1, 61, 61);
        g.setColor(new Color(0xFFF3A8));
        g.drawRect(2, 2, 59, 59);

        // Scale 16x16 Yuumi face to 48x48 centered
        BufferedImage face = render16(yuumiFace);
        g.drawImage(face, 8, 8, 48, 48, null);
        g.dispose();
        return icon;
    }

    private static byte[] toPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    private static Map<Character, Integer> pal(Object... kv) {
        Map<Character, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Character) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    /** Distinct silhouettes and palettes; never depends on the concurrently authored overrides. */
    private static void addV3Sprites(Map<String, SpriteSpec> sprites) {
        // These pre-v3 rewards used to depend entirely on overrides. Public gacha/workshops
        // must also work in a fallback-only pack while the asset session is still running.
        SpriteSpec cat = sprites.get("slot_yuumi");
        sprites.put("plushie_yuumi", new SpriteSpec("plushie_yuumi", false, cat.palette(), cat.rows()));
        register(sprites, "hat_yuumi_ears", false, pal('O',0x5D4579,'B',0x81B8EA,'P',0xEEA3C3,'W',0xFFF0D7,'G',0xF0CA58),
                "................","..OO........OO..","..OBO......OBO..","..OBPO....OPBO..",
                "..OBBPO..OPBBO..","...OBBPOOPBBO...","...OBBBBBBBBO...","....OBBBBBBO....",
                "....OOOOOOOO....","...OGGGGGGGGO...","....OGGGGGGO....","......OWO.......",
                "......OGO.......",".......O........","................","................");
        String[] ids = {"labyrinth_key", "ancient_cat_coin", "gacha_capsule", "class_emblem", "respec_potion",
                "charm_crimson_claw", "charm_starlight_aegis", "charm_golden_whisker", "charm_vampiric_purr",
                "furn_cat_tower", "furn_kotatsu", "furn_neon_sign", "hat_chef_toque", "hat_stock_tophat",
                "monster_soul_core", "unidentified_geode", "golden_catnip", "deep_sea_fillet", "rune_scroll", "michelin_feast",
                "weapon_paladin_sword", "weapon_shadow_daggers", "weapon_ranger_bow", "weapon_arcane_grimoire",
                "tool_miner_drill", "tool_smith_hammer", "tool_farmer_scythe", "tool_angler_harpoon", "tool_carpenter_axe", "tool_chef_wok"};
        int[] colors = {0x66D9EE,0xFFD052,0xFFB869,0xBA91F4,0x8BDD7D,0xEB526D,0x8EAFFF,0xFFD05B,0xB178D3,
                0xF2A9CF,0xE88F65,0x62E7E2,0xFFF1D7,0x564C86,0x9B83E3,0xDDB5F6,0xDFCB54,0xF78C9E,0x9EC7FF,0xC78B52,
                0xF3D06B,0xBC8DE8,0x8CE6C2,0x9D95EF,0x79CADD,0xE88F51,0xE4BF65,0x67BFE7,0xC59E6F,0xED815D};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            char[][] grid = new char[16][16];
            for (char[] row : grid) java.util.Arrays.fill(row, '.');
            if (i >= 20) {
                // Tools keep their own heads, bow/grimoire/dual-blades each has a recognizable silhouette.
                if (id.endsWith("bow")) {
                    for (int y = 2; y <= 13; y++) {
                        int x = 5 + (int)Math.round(4 * Math.sin((y - 2) * Math.PI / 11));
                        grid[y][x] = 'C'; grid[y][4] = 'W';
                    }
                } else if (id.endsWith("grimoire")) {
                    for (int y=3;y<13;y++) for(int x=3;x<13;x++) grid[y][x] = x==7||x==8 ? 'W' : 'C';
                } else {
                    for(int k=0;k<10;k++) { grid[13-k][3+k]='C'; if(k>2) grid[13-k][4+k]='L'; }
                    grid[12][3]='G'; grid[13][2]='G';
                    if (i>=24) for(int x=6;x<14;x++) for(int y=2;y<5;y++) grid[y][x]=(x+y)%3==0?'L':'C';
                    if (id.contains("daggers")) for(int k=0;k<7;k++) grid[13-k][12-k]='W';
                    if (id.endsWith("scythe")) for(int x=3;x<13;x++) grid[2][x]='G';
                }
            } else if (id.contains("key")) {
                for(int y=2;y<7;y++) for(int x=5;x<11;x++) if(y==2||y==6||x==5||x==10) grid[y][x]='G';
                for(int y=7;y<14;y++) grid[y][8]='C'; grid[11][10]='G'; grid[13][10]='G'; grid[11][9]='G'; grid[13][9]='G';
            } else if (id.startsWith("hat_")) {
                for(int y=3;y<11;y++) for(int x=5;x<12;x++) grid[y][x]=(x==5||y==3)?'L':'C';
                for(int x=2;x<15;x++) {grid[11][x]='G';grid[12][x]='C';}
            } else if (id.startsWith("furn_")) {
                for(int y=5;y<12;y++) for(int x=3;x<13;x++) grid[y][x]=(y==5||x==3)?'L':'C';
                grid[13][4]='G';grid[13][11]='G';
                if(id.endsWith("tower")) for(int y=1;y<5;y++) for(int x=6;x<10;x++) grid[y][x]='C';
                if(id.endsWith("sign")) for(int x=5;x<11;x++) grid[8][x]=(x%2==0)?'W':'G';
            } else {
                for(int y=2;y<14;y++) for(int x=2;x<14;x++) {
                    double d=Math.hypot(x-7.5,y-7.5);
                    if(d<5.8) grid[y][x]=d>4.6?'G':x+y<14?'L':'C';
                }
                // Unique central rune identifies materials and four charms even in a fallback-only pack.
                for(int bit=0;bit<5;bit++) if(((i+1)&(1<<bit))!=0) grid[6+bit/3][6+bit%3]='W';
            }
            String[] rows = new String[16];
            for(int y=0;y<16;y++) rows[y]=new String(grid[y]);
            register(sprites,id,i>=20,pal('C',colors[i],'L',blendWhite(colors[i]),'G',0x644459,'W',0xFFF7DD),rows);
        }
    }

    private static int blendWhite(int c) {
        return (((((c>>16)&255)+255)/2)<<16) | (((((c>>8)&255)+255)/2)<<8) | (((c&255)+255)/2);
    }

    private static void register(Map<String, SpriteSpec> map, String id, boolean handheld, Map<Character, Integer> p, String... rows) {
        map.put(id, new SpriteSpec(id, handheld, p, rows));
    }

    private static Map<String, SpriteSpec> buildCatalog() {
        Map<String, SpriteSpec> m = new LinkedHashMap<>();

        // 1. churu_salmon (연어맛 츄르 - 100 츄르)
        register(m, "churu_salmon", false,
                pal('O', 0x4A1E2B, 'P', 0xFF6B8B, 'L', 0xFFA5B9, 'W', 0xFFF5F7, 'C', 0xFFD2A6, 'S', 0xFFAE73, 'G', 0xFFD966),
                "...........CC...",
                "..........CCSC..",
                "..........CSCC..",
                ".........OOOO...",
                "........OPLPPO..",
                ".......OPLLWPPO.",
                "......OPLWWWPPO.",
                ".....OPLPWPLPPO.",
                "....OPLPPGGPPPO.",
                "...OPLPPPGGPPO..",
                "..OPLPPPPPPPO...",
                ".OPLPPPPPPPO....",
                ".OPPPPPPPPO.....",
                "..OOOOOOOo......".replace('o', 'O'),
                "...OOOOO........",
                "................"
        );

        // 2. churu_tuna (참치맛 츄르 - 1,000 츄르)
        register(m, "churu_tuna", false,
                pal('O', 0x162C46, 'B', 0x3B9EFF, 'L', 0x82C6FF, 'W', 0xF0F8FF, 'C', 0xFFE3C2, 'S', 0xFFC58C, 'G', 0xFFD966),
                "...........CC...",
                "..........CCSC..",
                "..........CSCC..",
                ".........OOOO...",
                "........OBLBBO..",
                ".......OBLLWBBO.",
                "......OBLWWWBBO.",
                ".....OBLBWBLBBO.",
                "....OBLBBGGBBBO.",
                "...OBLBBBGGBBO..",
                "..OBLBBBBBBBO...",
                ".OBLBBBBBBBO....",
                ".OBBBBBBBBO.....",
                "..OOOOOOOO......",
                "...OOOOO........",
                "................"
        );

        // 3. churu_golden (전설의 황금 츄르 - 10,000 츄르)
        register(m, "churu_golden", false,
                pal('O', 0x3B2308, 'G', 0xF7B519, 'Y', 0xFFE97D, 'W', 0xFFFFFF, 'V', 0x9E47FF, 'P', 0xE0A8FF),
                "..W........YY...",
                ".WWW......YYGY..",
                "..W.......YGYY..",
                ".........OOOO...",
                "........OGYGGO..",
                ".......OGYYWGGO.",
                "......OGYPVPGGO.",
                ".....OGYGVGYGGO.",
                "....OGYGGPPGGGO.",
                "...OGYGGGGGGGO..",
                "..OGYGGGGGGGO...",
                ".OGYGGGGGGGO..W.",
                ".OGGGGGGGGO..WWW",
                "..OOOOOOOO....W.",
                "...OOOOO........",
                "................"
        );

        // 4. casino_chip (황금 냥냥 카지노 칩)
        register(m, "casino_chip", false,
                pal('O', 0x3A1212, 'G', 0xF6C438, 'Y', 0xFFF099, 'R', 0xD92B4B, 'W', 0xFFF5F8, 'P', 0xFF8FB1),
                ".....OOOOOO.....",
                "...OOGGYGGGOO...",
                "..OGRRRRRRRRGO..",
                ".OGRRWW..WWRRGO.",
                ".OYRRWW..WWRRYO.",
                "OGRRRRRRRRRRRRGO",
                "OGRRWWPPPPWWRRGO",
                "OYRRWPPPPPPWRRYO",
                "OYRRWPPPPPPWRRYO",
                "OGRRWWPPPPWWRRGO",
                "OGRRRRRRRRRRRRGO",
                ".OYRRRRRRRRRRYO.",
                ".OGRRRRRRRRRRGO.",
                "..OGRRRRRRRRGO..",
                "...OOGGYGGGOO...",
                ".....OOOOOO....."
        );

        // 5. jackpot_crown (97랜드 잭팟 제왕의 고양이 왕관)
        register(m, "jackpot_crown", false,
                pal('O', 0x3D2507, 'G', 0xF5B82E, 'Y', 0xFFF08A, 'D', 0xC98312, 'P', 0xFF6B9E, 'C', 0x45E6FF, 'R', 0xFF2E55),
                "................",
                "..OO........OO..",
                ".OYYO..OO..OYYO.",
                ".OYPYOOYYOOYPYO.",
                "OOYPYYGYYGYYPYOO",
                "OGYYYYYYYYYYYYGO",
                "OGYGGGGGGGGGGYGO",
                "OGGGRGGGGGGCGGGO",
                "OGGRRRGGGGCCCGGO",
                "OGGGRGGGGGGCGGGO",
                "ODGGGGGGGGGGGGDO",
                "ODDDDDDDDDDDDDDO",
                ".OOOOOOOOOOOOOO.",
                "................",
                "................",
                "................"
        );

        // 6. torn_page (문턱의 서: 찢어진 마법 책장)
        register(m, "torn_page", false,
                pal('O', 0x4A3823, 'P', 0xF5E6C8, 'L', 0xFFF7E5, 'S', 0xD9C29C, 'C', 0x42E8FF, 'G', 0xF7C643),
                "...OOOOOOOOO....",
                "..OLLLLLLLLSO...",
                "..OLGGGGGGLLSO..",
                "..OLPPPPPPPLSO..",
                "..OLPCCCPPPLSO..",
                "..OLPCGCFPPLSO..".replace('F', 'C'),
                "..OLPCCCPPPLSO..",
                "..OLPPPPPPPLSO..",
                "..OLGGGGGGPLSO..",
                "..OLPPPPPPPLOO..",
                "..OLPCCCPPSOO...",
                "..OLPPPPPSO.....",
                "..OLPPPSOO......",
                "...OOOOO........",
                "................",
                "................"
        );

        // 7. book_of_thresholds (유미의 문턱의 서 - 종합 메뉴 마도서)
        register(m, "book_of_thresholds", false,
                pal('O', 0x141833, 'B', 0x2C429E, 'L', 0x4C6FE0, 'G', 0xF7C643, 'Y', 0xFFF3A1, 'C', 0x52F3FF, 'W', 0xFAF6EB),
                "....OOOOOOOOOO..",
                "...OGGLLLLLLGYO.",
                "..OGLBBBBBBBGYO.",
                "..OBBBYYBBYYBGO.",
                "..OBBYYYYYYYYGO.",
                "..OBBYYCYYCYYGO.",
                "..OBBYYYYYYYYGO.",
                "..OBBBYYYYYYBGO.",
                "..OGLBBBBBBBGYO.",
                "..OGGLLLLLLGGYO.",
                "..OOWWWWWWWWWOO.",
                "..OOWWWWWWWWWOO.",
                "...OOOOOOOOOOO..",
                "................",
                "................",
                "................"
        );

        // 8. zoomies_skewer (슈우우웅~ 생선 꼬치구이)
        register(m, "zoomies_skewer", false,
                pal('O', 0x382010, 'S', 0xC9935B, 'F', 0xE88A38, 'L', 0xFFC278, 'W', 0xFFF6E8, 'E', 0x52FFB8),
                "..E.........OO..",
                ".EEE.......OSSO.",
                "..E.......OSSOO.",
                "........OOOOO...",
                ".......OFLLFFO..",
                "......OFLLWWFFO.",
                ".....OFLFFFFFSO.",
                "....OFFFOOFFFO..",
                "....OOFFOOFFO...",
                "...OSSOFOOFO....",
                "..OSSOO..OO..E..",
                ".OSSO.......EEE.",
                ".OOO.........E..",
                "................",
                "................",
                "................"
        );

        // 9. kneading_bento (유미의 꾹꾹이 특선 도시락)
        register(m, "kneading_bento", false,
                pal('O', 0x361B12, 'R', 0xB83232, 'W', 0xFFFFFF, 'N', 0x2B2B2B, 'S', 0xFF7B54, 'Y', 0xFFE055, 'G', 0x5CD65C),
                "................",
                "..OOOOOOOOOOOO..",
                ".ORRRRRRRRRRRRO.",
                "OROWWOOWWOSSSSRO",
                "ORWWWWWWWWSSSSRO",
                "ORWWNWWWNWSSSSRO",
                "ORWWWWWWWWOOOOOO",
                "ORWWNNNNWWYYYYRO",
                "ORGGGGGGGGYYYYRO",
                "ORGGGGGGGGYYYYRO",
                ".ORRRRRRRRRRRRO.",
                "..OOOOOOOOOOOO..",
                "................",
                "................",
                "................",
                "................"
        );

        // 10. catnip_elixir (사르르 별빛 캣닢 영약)
        register(m, "catnip_elixir", false,
                pal('O', 0x1A2E2B, 'C', 0xC79258, 'G', 0x9CE8FF, 'M', 0x3BF5A5, 'L', 0xAEFFD8, 'V', 0xB066FF, 'W', 0xFFFFFF),
                "......OOOO......",
                ".....OCCCCO.....",
                "......OOOO......",
                ".....OGGGGO.....",
                "....OG....GO....",
                "...OG..LL..GO...",
                "..OG.LMMMML.GO..",
                "..OGMMWMMVMMGO..",
                "..OGMMMWMVMMGO..",
                "..OGMMMMMMMMGO..",
                "...OGMMMMMMGO...",
                "....OGLLLLGO....",
                ".....OOOOOO.....",
                "................",
                "................",
                "................"
        );

        // 11. royal_feast_can (97산 황실 참치 대뱃살 캔)
        register(m, "royal_feast_can", false,
                pal('O', 0x38250A, 'G', 0xF5C242, 'Y', 0xFFF2A6, 'S', 0xC4CDD6, 'T', 0xFF7A6B, 'L', 0xFFB4A8, 'B', 0x2E58A6),
                "................",
                "....OOOOOOOO....",
                "..OOSSSSSSSSOO..",
                ".OSSTTTTTLLLSSO.",
                ".OSTTTLLLLTTLSO.",
                ".OOSSSSSSSSSSOO.",
                ".OGYYYYYYYYYYGO.",
                ".OGBBBBBBBBBBGO.",
                ".OGBYYGBBGYYBGO.",
                ".OGBBBBBBBBBBGO.",
                ".OGGGGGGGGGGGGO.",
                "..OOOOOOOOOOOO..",
                "................",
                "................",
                "................",
                "................"
        );

        // 12. boss_core_rat (1티어 소환석: 지하수로 쥐대왕의 치즈 핵)
        register(m, "boss_core_rat", false,
                pal('O', 0x2B1438, 'V', 0x9938FF, 'P', 0xDF9EFF, 'Y', 0xFFCC33, 'L', 0xFFEE88, 'H', 0xC98714),
                "....VV....VV....",
                "..VV..OOOO..VV..",
                ".V..OOLLLLOO..V.",
                "...OLLYYYYYLO...",
                "..OLYYHHYYYYLO..",
                ".OLLYHHHHYYYYHO.",
                ".OLYYHHPPHYYYHO.",
                ".OYYYYPPPPYYHHO.",
                ".OYYYYPPPPYYYYO.",
                ".OHYYYYYYYYHHYO.",
                "..OHYYYYYYHHHO..",
                "...OOHHHHHHOO...",
                ".V...OOOOOO...V.",
                "..VV........VV..",
                "................",
                "................"
        );

        // 13. boss_core_hound (2티어 소환석: 지옥불 사냥개의 목줄 핵)
        register(m, "boss_core_hound", false,
                pal('O', 0x2E0B0B, 'R', 0xE62E2E, 'F', 0xFF7A1F, 'Y', 0xFFE659, 'S', 0xDCE3EA, 'D', 0x6E1919),
                "......FF..FF....",
                "...FF.YY..YY.FF.",
                "..F..OOOOOOOO..F",
                ".S..ORRRRRRRRO..",
                "SSSORRYYYYYYRRO.",
                ".OOORYFFFFFFYROO",
                ".ORRYFFOOOOFFYRO",
                ".ORRYFOYYYYOFYRO",
                ".ODRYFOYYYYOFYDO",
                ".ODRRYFFOOFFYRDO",
                ".OOODRYFFFFYRDOO",
                ".SSSODRRRRRRRDO.",
                ".S...OOOOOOOO..S",
                ".......YYYY...SS",
                "................",
                "................"
        );

        // 14. boss_core_void (3티어 소환석: 공허 침식자의 수정 핵)
        register(m, "boss_core_void", false,
                pal('O', 0x120624, 'D', 0x3D1473, 'V', 0x8C3BFF, 'P', 0xE8A8FF, 'C', 0x47F6FF, 'W', 0xFFFFFF),
                ".......OO.......",
                ".....OOVVOO.....",
                "....OVVPPPVO....",
                "...OVPPCCCCVO...",
                "..OVPCCWWWWCVO..",
                ".OVPCWWOOWWWCVO.",
                ".OVPCWWOOOOCCVO.",
                "OOVPCWWOOOOCCVOO",
                "OOVPCCWWOOWWCVOO",
                ".OVPCCWWWWWWCVO.",
                ".ODVPCCCCCCCVDO.",
                "..ODVPPPPPPVD0..".replace('0', 'O'),
                "...ODVVVVVVDO...",
                "....OODDDDOO....",
                "......OOOO......",
                "................"
        );

        // 15. you_and_me_bell (『너랑 유미랑!』 우정의 황금 방울)
        register(m, "you_and_me_bell", false,
                pal('O', 0x3B1D1A, 'R', 0xFF5C8A, 'P', 0xFFA6C1, 'G', 0xF7C137, 'Y', 0xFFF29C, 'D', 0xC48416),
                "................",
                "...OOO....OOO...",
                "..OPPPO..OPPPO..",
                "..OPRROOORRPPO..",
                "...OOORRRROOO...",
                ".....OYYYYO.....",
                "....OYYGGYYO....",
                "...OYYGGGGYYO...",
                "...OYGGGGGGYO...",
                "...OGGGOOGGGO...",
                "...ODDDOODDD0...".replace('0', 'O'),
                "....OO.OO.OO....",
                ".......OO.......",
                "................",
                "................",
                "................"
        );

        // 16. prowling_wand (『사르르 탄환』 유도 마법봉 - handheld)
        register(m, "prowling_wand", true,
                pal('O', 0x261438, 'G', 0xF5C242, 'Y', 0xFFF5A8, 'P', 0xFF6BB5, 'C', 0x57F2FF, 'B', 0x4B5FD9),
                "..........OO.OO.",
                ".........OYYOYYO",
                "........OYYCCCYO",
                "........OYCPPCYO",
                "........OYCCCCYO",
                ".........OYYYYO.",
                ".......OOOGGGO..",
                "......OGBGOOO...",
                ".....OGBO.......",
                "....OGBO........",
                "...OGBO.........",
                "..OGBO..........",
                ".OGGO...........",
                "OGYO............",
                "OOO.............",
                "................"
        );

        // 17. final_chapter_tome (『대단원』 파동의 비전서)
        register(m, "final_chapter_tome", false,
                pal('O', 0x211038, 'V', 0x7A2EE6, 'P', 0xDF8FFF, 'G', 0xF7C643, 'W', 0xFFF9EB, 'C', 0x59F7FF),
                "....CCCCCCCC....",
                "..CC..PPPP..CC..",
                ".C..PPPPPPPP..C.",
                "...OOOOOOOOOO...",
                "..OGWWWWOGWWWO..",
                ".OGWWVVWOGWVVWO.",
                ".OGWVVWWOGWWVWO.",
                ".OGWWWWGOGWWWW0.".replace('0', 'O'),
                ".OGWWCCWOGWCCWO.",
                ".OGWWWWGOGWWWW0.".replace('0', 'O'),
                ".OOVVVVGOGVVVVO.",
                "..OOOOOGOGOOOO..",
                ".......OOO......",
                "................",
                "................",
                "................"
        );

        // 18. nine_lives_charm (『아홉 개의 목숨』 수호 부적)
        register(m, "nine_lives_charm", false,
                pal('O', 0x162E26, 'J', 0x2ED99B, 'L', 0x9CFFE0, 'G', 0xF7C43B, 'Y', 0xFFF5A6, 'C', 0x5CF2FF),
                "..C..........C..",
                "................",
                "....OO....OO....",
                "...OLLO..OLLO...",
                "..C.OJJOOJJO..C.",
                "...OJJLLLLJJO...",
                "...OJLGGGGJLO...",
                "..COJLGYYGLJOC..",
                "...OJLGGGGJLO...",
                "...OJJJJJJJJO...",
                "....OGGGGGGO....",
                "..C..OJJJJO...C.",
                "......OOOO......",
                ".......CC.......",
                "................",
                "................"
        );

        // 19. slot_seven (카지노 슬롯 777 심볼)
        register(m, "slot_seven", false,
                pal('O', 0x3B0D1E, 'R', 0xFF2457, 'G', 0xFFD23F, 'W', 0xFFFFFF),
                "................",
                "..OOOOOOOOOOOO..",
                ".OGGGGGGGGGGGGO.",
                ".OGWWWWWWWWWWRGO",
                ".OGRRRRRRRRWRRGO",
                "..OOOOOOOOWRRGO.",
                "........OWRRGO..",
                ".......OWRRGO...",
                "......OWRRGO....",
                ".....OWRRGO.....",
                "....OWRRGO......",
                "....ORRGO.......",
                "....OGGO........",
                "....OOO.........",
                "................",
                "................"
        );

        // 20. slot_yuumi (마법 고양이 유미 심볼 & 서버 아이콘 베이스)
        register(m, "slot_yuumi", false,
                pal('O', 0x1D1A36, 'B', 0x6279E8, 'L', 0x99AEFF, 'W', 0xFFFDF9, 'Y', 0xFFE34D, 'G', 0x6BFF8C, 'P', 0xFF8FB1),
                ".......YY.......",
                "......YYYY......",
                ".OO....YY....OO.",
                ".OBOO..YY..OOBO.",
                ".OBLBOOOOOOBLBO.",
                ".OBLLBBYYBBLLBO.",
                ".OBLWWWWWWWWLBO.",
                ".OBWWGGWWGGWWBO.",
                ".OBWWGOWWGOWWBO.",
                ".OWWWWWPPWWWWWO.",
                "..OWWWWWWWWWWO..",
                "...OOWWWWWWO0...".replace('0', 'O'),
                ".....OOOOOO.....",
                "................",
                "................",
                "................"
        );

        // 21. slot_paw (핑크 젤리 발바닥 심볼)
        register(m, "slot_paw", false,
                pal('O', 0x3D1C2E, 'W', 0xFFF5F8, 'P', 0xFF70A6, 'L', 0xFFACC7),
                "................",
                "....OO....OO....",
                "...OPPO..OPPO...",
                "..OOLLOOOOLLOO..",
                ".OPPOOWWWWOOPPO.",
                ".OLLOWWWWWWOLLO.",
                "..OOWWPPPPWWOO..",
                "..OWWPLLLPPWWO..",
                "..OWWPLLLPPWWO..",
                "..OWWPPPPPPWWO..",
                "...OWWWWWWWWO...",
                "....OOOOOOOO....",
                "................",
                "................",
                "................",
                "................"
        );

        // 22. chip_100 (100 카지노 칩 - 코랄 레드)
        register(m, "chip_100", false,
                pal('O', 0x3A101D, 'R', 0xE63956, 'L', 0xFF758F, 'W', 0xFFF8FA, 'G', 0xFFD166),
                "................",
                ".....OOOOOO.....",
                "...OOWWRRWWO0...".replace('0', 'O'),
                "..OWRRRRRRRRWO..",
                ".OWRROGGGGORRWO.",
                ".ORROGLLLLGORRO.",
                ".ORROGLWWLGORRO.",
                ".OWROGLWWLGORWO.",
                ".OWROGLWWLGORWO.",
                ".ORROGLLLLGORRO.",
                ".OWRROGGGGORRWO.",
                "..OWRRRRRRRRWO..",
                "...OOWWRRWWOO...",
                ".....OOOOOO.....",
                "................",
                "................"
        );

        // 23. chip_1000 (1,000 카지노 칩 - 에메랄드 민트)
        register(m, "chip_1000", false,
                pal('O', 0x0E332B, 'E', 0x10B981, 'M', 0x6EE7B7, 'W', 0xF0FDF9, 'G', 0xFBBF24),
                "................",
                ".....OOOOOO.....",
                "...OOWWEEWWOO...",
                "..OWEEEEEEEEWO..",
                ".OWEEOGGGGOEEWO.",
                ".OEEOGMMMMGOEEO.",
                ".OEEOGMWWMGOEEO.",
                ".OWEOGMGGMGOEWO.",
                ".OWEOGMWWMGOEWO.",
                ".OEEOGMMMMGOEEO.",
                ".OWEEOGGGGOEEWO.",
                "..OWEEEEEEEEWO..",
                "...OOWWEEWWOO...",
                ".....OOOOOO.....",
                "................",
                "................"
        );

        // 24. chip_10000 (10,000 VIP 카지노 칩 - 블랙 & 골드)
        register(m, "chip_10000", false,
                pal('O', 0x120D1D, 'B', 0x292040, 'G', 0xF59E0B, 'Y', 0xFDE68A, 'P', 0xEC4899),
                "................",
                ".....OOOOOO.....",
                "...OOYYBBYYOO...",
                "..OYBBBBBBBBYO..",
                ".OYBBOGGGGOBBYO.",
                ".OBBOGYYYYGOBBO.",
                ".OBBOGYPPYGOBBO.",
                ".OYBOGYPPYGOBYO.",
                ".OYBOGYPPYGOBYO.",
                ".OBBOGYYYYGOBBO.",
                ".OYBBOGGGGOBBYO.",
                "..OYBBBBBBBBYO..",
                "...OOYYBBYYOO...",
                ".....OOOOOO.....",
                "................",
                "................"
        );

        // 25. recall_bookmark (문턱의 서 귀환 책갈피)
        register(m, "recall_bookmark", false,
                pal('O', 0x1E1238, 'V', 0x6D28D9, 'L', 0xA78BFA, 'G', 0xFBBF24, 'Y', 0xFEF08A, 'C', 0x67E8F9),
                ".......GG.......",
                "......GYYG......",
                ".....OOOOOO.....",
                "....OGLLLLGO....",
                "....OGLVVLGO....",
                "....OGVCCVGO....",
                "....OGVYYVGO....",
                "....OGVCCVGO....",
                "....OGLVVLGO....",
                "....OGVVVVGO....",
                "....OGGGGGGO....",
                "....OVVVVVVO....",
                "....OVO..OVO....",
                "....OO....OO....",
                "...C........C...",
                "................"
        );

        // 26. starlight_stone (별빛 세공석)
        register(m, "starlight_stone", false,
                pal('O', 0x1A163B, 'B', 0x4F46E5, 'C', 0x38BDF8, 'L', 0xBAE6FD, 'W', 0xFFFFFF, 'Y', 0xFDE047),
                ".......YY.......",
                "..Y....OO....Y..",
                "......OLLO......",
                ".....OLWWLO.....",
                "....OLWCCWLO....",
                "...OLWCCCCWLO...",
                "..OLWCCWWCCWLO..",
                "..OBCCCWWCCCBO..",
                "...OBCCCCCCBO...",
                "....OBBBBBBO....",
                ".....OBBBBO.....",
                "..Y...OOOO...Y..",
                ".......YY.......",
                "................",
                "................",
                "................"
        );

        // 27. starlight_scroll (유미의 세공 보호권)
        register(m, "starlight_scroll", false,
                pal('O', 0x3B2314, 'G', 0xF59E0B, 'Y', 0xFEF3C7, 'W', 0xFFFBEB, 'P', 0xF472B6, 'C', 0x38BDF8),
                "................",
                "..OOOOOOOOOOOO..",
                ".OGYYYYYYYYYYGO.",
                "..OOOOOOOOOOOO..",
                "...OWWWWWWWWO...",
                "...OWWP..PWWO...",
                "...OWWPPPPWWO...",
                "...OWWPPPPWWO...",
                "...OWWWPPWWWO...",
                "...OWWCCCCWWO...",
                "...OWWWWWWWWO...",
                "..OOOOOOOOOOOO..",
                ".OGYYYYYYYYYYGO.",
                "..OOOOOOOOOOOO..",
                "................",
                "................"
        );

        return m;
    }
}
