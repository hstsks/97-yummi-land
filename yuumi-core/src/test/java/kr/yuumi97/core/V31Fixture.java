package kr.yuumi97.core;

import kr.yuumi97.core.rpg.JobType;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Prepares only the disposable integration server; never accepts the production server path. */
public final class V31Fixture {
    @SuppressWarnings("unchecked")
    private static Map<String,Object> section(Map<String,Object> map,String key) {
        return (Map<String,Object>)map.computeIfAbsent(key,k->new LinkedHashMap<String,Object>());
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath().normalize();
        if(!root.endsWith(Path.of("yuumi-core","build","server-integration")))throw new IllegalArgumentException("Disposable server path required");
        Yaml yaml=new Yaml();
        Path folder=root.resolve("plugins/YuumiLandCore");
        Map<String,Object> data=yaml.load(Files.readString(folder.resolve("data.yml")));
        Map<String,Object> config=yaml.load(Files.readString(folder.resolve("config.yml")));
        section(config,"resource-pack").put("http-port",25568);
        Map<String,Object> rpg=section(config,"rpg");
        rpg.put("job-change-cost",0);rpg.put("job-change-cooldown-seconds",0);rpg.put("reawakening-cost",0);
        Map<String,Object> skills=section(rpg,"skills");
        List<String> ops=new ArrayList<>();
        for(JobType job:JobType.values()) {
            Map<String,Object> skill=section(skills,job.name());
            for(String key:List.of("primary-cooldown","A-cooldown","B-cooldown"))skill.put(key,0);
            String name="V31"+job.name();
            UUID id=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
            Map<String,Object> profile=section(section(data,"rpg"),id.toString());
            profile.put("active_job",job.name());profile.put("change_ready_at",0);
            profile.put("bookmarks",new LinkedHashMap<>());profile.remove("feast");
            Map<String,Object> progress=section(section(profile,"jobs"),job.name());
            progress.put("level",10);progress.put("xp",0);progress.put("awakening","A");progress.put("cooldowns",new LinkedHashMap<>());
            progress.put("perks",new LinkedHashMap<>());
            ops.add("{\"uuid\":\""+id+"\",\"name\":\""+name+"\",\"level\":4,\"bypassesPlayerLimit\":true}");
        }
        for(String rune:List.of("FIRE","FROST","THUNDER")){
            Map<String,Object> runeConfig=section(section(config,"runes"),rune);runeConfig.put("proc-chance",1);runeConfig.put("cooldown-seconds",0);
        }
        section(config,"runes").put("masterwork-chance",0);
        Files.writeString(folder.resolve("data.yml"),yaml.dump(data));
        Files.writeString(folder.resolve("config.yml"),yaml.dump(config));
        Files.writeString(root.resolve("ops.json"),"["+String.join(",",ops)+"]");
        System.out.println("V31 disposable fixture prepared: 10 Lv10 jobs, A awakening; test-only zero cooldown/cost, guaranteed rune procs");
    }
}
