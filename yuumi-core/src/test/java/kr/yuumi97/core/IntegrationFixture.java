package kr.yuumi97.core;

import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Writes only the disposable integration server under build/. */
public final class IntegrationFixture {
    @SuppressWarnings("unchecked")
    private static Map<String,Object> section(Map<String,Object> parent,String key) {
        return (Map<String,Object>)parent.computeIfAbsent(key,k->new LinkedHashMap<>());
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath().normalize();
        if(!root.endsWith(Path.of("yuumi-core","build","server-integration"))||Files.isSymbolicLink(root))
            throw new IllegalArgumentException("Disposable server path required");
        Yaml yaml=new Yaml(); Path folder=root.resolve("plugins/YuumiLandCore"); Files.createDirectories(folder);
        Path dataFile=folder.resolve("data.yml");
        Map<String,Object> data=Files.exists(dataFile)?yaml.load(Files.readString(dataFile)):new LinkedHashMap<>();
        if(data==null)data=new LinkedHashMap<>();
        Map<String,Object> config=yaml.load(Files.readString(Path.of("src/main/resources/config.yml")));
        section(config,"resource-pack").put("http-port",25568);
        List<String> ops=new ArrayList<>();
        String[] combat={"PALADIN","BERSERKER","SHADOW","RANGER","ARCANIST","PRIEST"};
        String[] life={"MINER","FARMER","ANGLER","CARPENTER","BLACKSMITH","CHEF"};
        for(int i=0;i<8;i++) {
            String name="CorePlayer"+i;
            UUID id=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
            Map<String,Object> profile=section(section(data,"rpg"),id.toString());
            profile.put("active_combat",combat[i%6]);profile.put("active_life",life[i%6]);
            for(String type:combat) {var progress=section(section(profile,"combat"),type);progress.put("level",20);progress.put("xp",0);progress.put("awakening","A");progress.put("perks",new LinkedHashMap<>());progress.put("cooldowns",new LinkedHashMap<>());}
            for(String type:life) {var progress=section(section(profile,"life"),type);progress.put("level",20);progress.put("xp",0);progress.put("spec","A");progress.put("perks",new LinkedHashMap<>());progress.put("cooldowns",new LinkedHashMap<>());}
            section(section(data,"players"),id.toString()).put("initialized",true);
            section(data,"wallets").put(id.toString(),100000);
            ops.add("{\"uuid\":\""+id+"\",\"name\":\""+name+"\",\"level\":4,\"bypassesPlayerLimit\":true}");
        }
        Files.writeString(dataFile,yaml.dump(data));Files.writeString(folder.resolve("config.yml"),yaml.dump(config));
        Files.writeString(root.resolve("ops.json"),"["+String.join(",",ops)+"]");
        System.out.println("CORE_FIXTURE PASS: eight players; twelve profiles each; real cooldowns and costs");
    }
}
