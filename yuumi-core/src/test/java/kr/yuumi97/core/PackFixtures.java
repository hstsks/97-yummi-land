package kr.yuumi97.core;

import kr.yuumi97.core.dungeon.mob.MobType;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Creates isolated missing-blueprint/model fixtures. Never invokes PackBuilder.main. */
public final class PackFixtures {
    private static final List<String> DOTS=List.of("weapon_berserker_greatsword","weapon_priest_staff","weapon_arcane_orb_tome","guidebook");
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("Expected: authoredJar authoredPack targetFallbackJar targetFallbackPack");
        Path sourceJar=Path.of(args[0]).toAbsolutePath().normalize(),sourcePack=Path.of(args[1]).toAbsolutePath().normalize();
        Path core=coreRoot(sourceJar),build=core.resolve("build").toAbsolutePath().normalize();Files.createDirectories(build);
        Path targetJar=output(args[2],build),targetPack=output(args[3],build);
        require(!targetJar.equals(targetPack),"Fixture outputs must differ");
        for(Path source:List.of(sourceJar,sourcePack)){require(Files.isRegularFile(source),"Missing authored input: "+source);
            require(!source.equals(targetJar)&&!source.equals(targetPack),"An output would overwrite an authored input");
            for(Path target:List.of(targetJar,targetPack))if(Files.exists(target))require(!Files.isSameFile(source,target),"An output aliases an authored input");}
        String jarHash=hash(Files.readAllBytes(sourceJar)),packHash=hash(Files.readAllBytes(sourcePack));
        Map<String,byte[]> jar=read(sourceJar),pack=read(sourcePack);int nbt=0;
        for(String name:new ArrayList<>(jar.keySet()))if(name.startsWith("structures/overhaul/")){if(name.endsWith(".nbt"))nbt++;jar.remove(name);}
        require(nbt==20,"Authored jar must contain twenty overhaul NBTs, found "+nbt);
        Set<String> removed=new LinkedHashSet<>();removed.add("assets/yuumi97/monster_rigs.json");removed.add("assets/yuumi97/items/prop_wayfinder_arrow.json");
        for(String name:pack.keySet())if(name.startsWith("assets/yuumi97/items/vfx_")&&name.endsWith(".json"))removed.add(name);
        int parts=0;for(MobType type:MobType.values())for(String suffix:type.boss()?List.of("core","head","weapon"):List.of("body","arm")){
            String path="assets/yuumi97/items/"+type.id+"_"+suffix+".json";require(pack.containsKey(path),"Missing authored rig definition "+path);removed.add(path);parts++;
        }
        require(parts==42,"Expected forty-two rig part definitions");
        require(removed.stream().filter(n->n.startsWith("assets/yuumi97/items/vfx_")).count()==14,"Expected fourteen authored VFX definitions");
        for(String name:removed)require(pack.remove(name)!=null,"Missing authored fixture input "+name);
        Map<String,byte[]> replacements=dots();pack.putAll(replacements);
        // The new orb definition is deliberately absent: the runtime must select its authored legacy grimoire.
        String arcane="assets/yuumi97/items/weapon_arcane_orb_tome.json";pack.remove(arcane);removed.add(arcane);
        require(pack.containsKey("assets/yuumi97/items/weapon_arcane_grimoire.json"),"Legacy arcane fallback definition is missing");
        write(targetJar,jar);write(targetPack,pack);
        Map<String,byte[]> writtenJar=read(targetJar),writtenPack=read(targetPack);
        require(writtenJar.keySet().stream().noneMatch(n->n.startsWith("structures/overhaul/")),"Blueprint fixture still contains overhaul resources");
        for(String absent:removed)require(!writtenPack.containsKey(absent),"Fallback availability remains true: "+absent);
        for(String id:DOTS){byte[] png=writtenPack.get("assets/yuumi97/textures/item/"+id+".png");BufferedImage image=ImageIO.read(new ByteArrayInputStream(png));require(image!=null&&image.getWidth()==16&&image.getHeight()==16,"Fallback is not 16x16: "+id);}
        for(var source:read(sourcePack).entrySet())if(!removed.contains(source.getKey())&&!replacements.containsKey(source.getKey()))
            require(Arrays.equals(source.getValue(),writtenPack.get(source.getKey())),"Changed retained pack entry: "+source.getKey());
        for(var source:read(sourceJar).entrySet())if(!source.getKey().startsWith("structures/overhaul/"))
            require(Arrays.equals(source.getValue(),writtenJar.get(source.getKey())),"Changed retained jar entry: "+source.getKey());
        require(jarHash.equals(hash(Files.readAllBytes(sourceJar)))&&packHash.equals(hash(Files.readAllBytes(sourcePack))),"An authored input changed during fixture creation");
        String report="PACK_FIXTURES PASS\nAuthored jar: "+sourceJar+" SHA-256 "+jarHash+"\nAuthored pack: "+sourcePack+" SHA-256 "+packHash+
                "\nFallback jar: "+targetJar+"\nFallback pack: "+targetPack+"\nRemoved: twenty NBTs and all overhaul resource entries; rig manifest + fourteen VFX + forty-two rig parts + wayfinder definitions.\n"+
                "Inserted: four reflected 16x16 PNGs and flat base models; three item definitions (new orb definition removed to exercise legacy grimoire). Retained inputs are byte-identical.\n";
        Path log=build.resolve("verification-core/pack-fixtures.log");Files.createDirectories(log.getParent());Files.writeString(log,report,StandardCharsets.UTF_8);System.out.print(report);
    }
    private static Path coreRoot(Path source){for(Path p=source.getParent();p!=null;p=p.getParent())if(Files.isRegularFile(p.resolve("build.gradle.kts")))return p;
        Path cwd=Path.of("").toAbsolutePath();if(Files.isRegularFile(cwd.resolve("build.gradle.kts")))return cwd;
        if(Files.isRegularFile(cwd.resolve("yuumi-core/build.gradle.kts")))return cwd.resolve("yuumi-core");throw new IllegalArgumentException("Cannot locate yuumi-core");}
    private static Path output(String argument,Path build)throws IOException {
        Path file=Path.of(argument).toAbsolutePath().normalize();require(file.startsWith(build)&&!file.equals(build),"Outputs must stay under yuumi-core/build: "+file);
        Path nearest=file.getParent();while(nearest!=null&&!Files.exists(nearest))nearest=nearest.getParent();
        require(nearest!=null&&nearest.toRealPath().startsWith(build.toRealPath()),"Output parent escapes build through a link: "+file);
        require(!Files.isSymbolicLink(file),"Fixture output cannot be a symlink");Files.createDirectories(file.getParent());return file;
    }
    private static Map<String,byte[]> read(Path file)throws IOException {
        Map<String,byte[]> entries=new LinkedHashMap<>();try(ZipFile zip=new ZipFile(file.toFile())){for(ZipEntry entry:Collections.list(zip.entries())){
            String name=entry.getName();require(!name.contains("\\")&&!name.startsWith("/")&&!Arrays.asList(name.split("/")).contains(".."),"Unsafe ZIP entry: "+name);
            try(InputStream in=zip.getInputStream(entry)){require(entries.putIfAbsent(name,in.readAllBytes())==null,"Duplicate ZIP entry: "+name);}
        }}return entries;
    }
    private static void write(Path target,Map<String,byte[]> entries)throws IOException {try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(target))){for(var entry:entries.entrySet()){
        ZipEntry out=new ZipEntry(entry.getKey());out.setTime(0);zip.putNextEntry(out);zip.write(entry.getValue());zip.closeEntry();}}
    }
    private static Map<String,byte[]> dots()throws Exception {
        Class<?> builder=Class.forName("PackBuilder");Method catalog=builder.getDeclaredMethod("buildCatalog");catalog.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,Object> sprites=(Map<String,Object>)catalog.invoke(null);
        for(String name:List.of("addGameplaySprites","addCoreSprites")){Method method=builder.getDeclaredMethod(name,Map.class);method.setAccessible(true);method.invoke(null,sprites);}
        Class<?> spec=Class.forName("PackBuilder$SpriteSpec");Method render=builder.getDeclaredMethod("render16",spec);render.setAccessible(true);
        Map<String,byte[]> entries=new LinkedHashMap<>();Set<Integer> distinct=new HashSet<>();
        for(String id:DOTS){Object sprite=Objects.requireNonNull(sprites.get(id),"Missing base sprite "+id);BufferedImage image=(BufferedImage)render.invoke(null,sprite);
            require(image.getWidth()==16&&image.getHeight()==16,"Unexpected dot dimensions: "+id);int[] rgba=image.getRGB(0,0,16,16,null,0,16);
            require(Arrays.stream(rgba).anyMatch(v->v>>>24!=0)&&distinct.add(Arrays.hashCode(rgba)),"Empty or duplicate base sprite "+id);
            ByteArrayOutputStream png=new ByteArrayOutputStream();require(ImageIO.write(image,"PNG",png),"PNG encoder unavailable");
            entries.put("assets/yuumi97/textures/item/"+id+".png",png.toByteArray());
            boolean handheld=(boolean)spec.getDeclaredMethod("handheld").invoke(sprite);
            entries.put("assets/yuumi97/models/item/"+id+".json",("{\"parent\":\"minecraft:item/"+(handheld?"handheld":"generated")+"\",\"textures\":{\"layer0\":\"yuumi97:item/"+id+"\"}}\n").getBytes(StandardCharsets.UTF_8));
            entries.put("assets/yuumi97/items/"+id+".json",("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"yuumi97:item/"+id+"\"}}\n").getBytes(StandardCharsets.UTF_8));
        }return entries;
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
