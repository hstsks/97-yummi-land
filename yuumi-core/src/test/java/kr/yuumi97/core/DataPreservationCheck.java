package kr.yuumi97.core;

import kr.yuumi97.core.rpg.CombatClass;
import kr.yuumi97.core.rpg.LifeProfession;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Audits an explicit stopped-server baseline; never edits either snapshot or vault directory. */
public final class DataPreservationCheck {
    private static final List<String> STABLE_PATHS=List.of(
            "altar","bank","wallets","plots","trust","codex","vault_pages","casino.jackpot",
            "stocks.holdings","dungeon.stats","dungeon.completion-xp","social.titles","social.smith");
    private static final List<String> OLD_PROFILE_KEYS=List.of("jobs","active_job","changes","change_ready_at");

    public static void main(String[] args)throws Exception {
        selfCheck();
        if(args.length==0||args.length==1&&args[0].equals("--self-test")){
            System.out.println("DATA_COMPARATOR_SELF_CHECK PASS");return;
        }
        if(args.length!=4)throw new IllegalArgumentException("Expected: beforeDataYaml afterDataYaml baselineVaultDirectory afterVaultDirectory");
        Path beforeFile=Path.of(args[0]).toAbsolutePath().normalize(),afterFile=Path.of(args[1]).toAbsolutePath().normalize();
        Path beforeVaults=Path.of(args[2]).toAbsolutePath().normalize(),afterVaults=Path.of(args[3]).toAbsolutePath().normalize();
        Path log=projectRoot(beforeFile).resolve("build/verification-core/data-preservation.log");
        Files.createDirectories(log.getParent());
        StringBuilder report=new StringBuilder("Data preservation audit ").append(Instant.now()).append('\n')
                .append("Before data: ").append(beforeFile).append('\n').append("After data: ").append(afterFile).append('\n')
                .append("Baseline vaults: ").append(beforeVaults).append('\n').append("After vaults: ").append(afterVaults).append('\n');
        try {
            byte[] beforeBytes=Files.readAllBytes(beforeFile),afterBytes=Files.readAllBytes(afterFile);
            long leaves=compare(yaml(beforeBytes),yaml(afterBytes),report);
            require(Files.isDirectory(beforeVaults),"Baseline vault directory does not exist (create an empty directory when no vaults exist)");
            int files=0;Map<Path,String> hashes=new LinkedHashMap<>();
            try(var walk=Files.walk(beforeVaults)){
                for(Path baseline:walk.filter(Files::isRegularFile).sorted().toList()){
                    require(!Files.isSymbolicLink(baseline),"Baseline vault must not be a symlink: "+baseline);
                    Path relative=beforeVaults.relativize(baseline),current=afterVaults.resolve(relative).normalize();
                    require(current.startsWith(afterVaults)&&!Files.isSymbolicLink(current),"Unsafe vault path: "+relative);
                    require(Files.isRegularFile(current),"Missing existing vault: "+relative);
                    byte[] old=Files.readAllBytes(baseline),now=Files.readAllBytes(current);
                    require(Arrays.equals(old,now),"Vault bytes changed: "+relative);
                    hashes.put(current,hash(now));files++;report.append("PASS vault ").append(relative).append(" SHA-256 ").append(hash(now)).append('\n');
                }
            }
            require(Arrays.equals(beforeBytes,Files.readAllBytes(beforeFile)),"Before YAML changed during audit");
            require(Arrays.equals(afterBytes,Files.readAllBytes(afterFile)),"After YAML changed during audit; rerun after the server stops");
            for(var file:hashes.entrySet())require(file.getValue().equals(hash(Files.readAllBytes(file.getKey()))),"Vault changed during audit: "+file.getKey());
            report.append("Before data SHA-256: ").append(hash(beforeBytes)).append('\n').append("After data SHA-256: ").append(hash(afterBytes)).append('\n')
                    .append("DATA_PRESERVATION PASS: ").append(leaves).append(" preserved scalar values; ").append(files).append(" byte-identical vault files\n");
        }catch(Exception failure){report.append("DATA_PRESERVATION FAILED: ").append(failure.getMessage()).append('\n');
            Files.writeString(log,report,StandardCharsets.UTF_8);System.err.print(report);throw failure;}
        Files.writeString(log,report,StandardCharsets.UTF_8);System.out.print(report);
    }

    private static Path projectRoot(Path baseline){
        for(Path p=baseline.getParent();p!=null;p=p.getParent())if(Files.isRegularFile(p.resolve("build.gradle.kts")))return p;
        Path cwd=Path.of("").toAbsolutePath().normalize();
        if(Files.isRegularFile(cwd.resolve("build.gradle.kts")))return cwd;
        if(Files.isRegularFile(cwd.resolve("yuumi-core/build.gradle.kts")))return cwd.resolve("yuumi-core");
        throw new IllegalArgumentException("Cannot locate yuumi-core for the audit log");
    }
    private static Map<?,?> yaml(byte[] bytes){LoaderOptions options=new LoaderOptions();options.setCodePointLimit(32*1024*1024);options.setAllowDuplicateKeys(false);
        Object loaded=new Yaml(new SafeConstructor(options)).load(new ByteArrayInputStream(bytes));require(loaded instanceof Map<?,?>,"Core YAML must contain a mapping");return (Map<?,?>)loaded;}
    private static Map<?,?> map(Object value){return value instanceof Map<?,?> m?m:Map.of();}
    private static Object at(Map<?,?> root,String path){Object value=root;for(String key:path.split("\\.")){if(!(value instanceof Map<?,?> m)||!m.containsKey(key))return null;value=m.get(key);}return value;}

    public static long compare(Map<?,?> before,Map<?,?> after,StringBuilder report){
        long leaves=0;
        for(String path:STABLE_PATHS){Object old=at(before,path);if(old==null){report.append("SKIP absent baseline: ").append(path).append('\n');continue;}
            Object current=at(after,path);require(current!=null,"Missing preserved root: "+path);long count=unchanged(old,current,path);leaves+=count;
            report.append("PASS preserved ").append(path).append(": ").append(count).append(" scalar values\n");}
        int converted=0,existing=0;
        Map<?,?> oldRpg=map(before.get("rpg")),newRpg=map(after.get("rpg"));
        for(var player:oldRpg.entrySet()){
            String base="rpg."+player.getKey();Map<?,?> oldProfile=map(player.getValue()),newProfile=map(newRpg.get(player.getKey()));
            for(String domain:List.of("combat","life"))for(var profile:map(oldProfile.get(domain)).entrySet()){
                Map<?,?> growth=map(profile.getValue());if(!growth.containsKey("level")&&!growth.containsKey("xp"))continue;
                leaves+=growth(growth,map(map(newProfile.get(domain)).get(profile.getKey())),base+"."+domain+"."+profile.getKey());existing++;
            }
            for(var job:map(oldProfile.get("jobs")).entrySet()){
                String id=String.valueOf(job.getKey());String domain=CombatClass.parse(id)!=null?"combat":LifeProfession.parse(id)!=null?"life":null;
                require(domain!=null,"Unmapped existing RPG job: "+base+".jobs."+id);
                // Existing current-schema growth wins when a mixed-schema baseline already has it.
                if(map(map(oldProfile.get(domain)).get(id)).containsKey("level"))continue;
                leaves+=growth(map(job.getValue()),map(map(newProfile.get(domain)).get(id)),base+"."+domain+"."+id);converted++;
            }
            String active=String.valueOf(oldProfile.getOrDefault("active_job",null));
            if(!active.equals("null")){
                String domain=CombatClass.parse(active)!=null?"combat":LifeProfession.parse(active)!=null?"life":null;
                if(domain!=null&&!oldProfile.containsKey("active_"+domain))require(active.equals(newProfile.get("active_"+domain)),"Active slot not restored: "+base+".active_"+domain);
            }
        }
        for(var player:newRpg.entrySet())for(String obsolete:OLD_PROFILE_KEYS)
            require(!map(player.getValue()).containsKey(obsolete),"Obsolete RPG key remains: rpg."+player.getKey()+"."+obsolete);
        for(String obsolete:List.of("hub_v2","hub_v3","hub_v3_1"))require(!after.containsKey(obsolete),"Obsolete hub root remains: "+obsolete);
        for(var player:map(after.get("players")).entrySet())require(!map(player.getValue()).containsKey("starter_kit_v2"),"Obsolete starter key remains: players."+player.getKey());
        report.append("PASS RPG growth: ").append(converted).append(" converted jobs; ").append(existing).append(" existing dual profiles; obsolete keys absent; awakening/perk resets permitted\n")
                .append("INFO live stock prices/update timestamps and run bookkeeping are outside principal preservation\n");
        return leaves;
    }
    private static long growth(Map<?,?> old,Map<?,?> now,String path){
        require(now.containsKey("level"),"Missing preserved RPG profile: "+path);
        Object level=old.containsKey("level")?old.get("level"):1,xp=old.containsKey("xp")?old.get("xp"):0;
        unchanged(level,now.get("level"),path+".level");unchanged(xp,now.containsKey("xp")?now.get("xp"):0,path+".xp");return 2;
    }
    private static long unchanged(Object before,Object after,String path){
        if(before instanceof Map<?,?> old){require(after instanceof Map<?,?>,"Changed mapping type: "+path);Map<?,?> now=(Map<?,?>)after;long leaves=0;
            for(var item:old.entrySet()){require(now.containsKey(item.getKey()),"Removed existing key: "+path+"."+item.getKey());leaves+=unchanged(item.getValue(),now.get(item.getKey()),path+"."+item.getKey());}return leaves;}
        if(before instanceof List<?> old){require(after instanceof List<?> &&((List<?>)after).size()==old.size(),"Changed existing list: "+path);long leaves=0;
            for(int n=0;n<old.size();n++)leaves+=unchanged(old.get(n),((List<?>)after).get(n),path+"["+n+"]");return leaves;}
        boolean same=before instanceof Number a&&after instanceof Number b?new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))==0:
                before instanceof byte[] a&&after instanceof byte[] b?Arrays.equals(a,b):Objects.equals(before,after);
        require(same,"Changed preserved value: "+path);return 1;
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}

    /** Pure deterministic check, invoked by CoreChecks without reading or writing any files. */
    public static void selfCheck(){
        require(unchanged(Map.of("balance",7),Map.of("balance",7.0,"new",true),"bank")==1,"Numeric representation/new-key comparison");
        Map<String,Object> legacy=Map.of("rpg",Map.of("player",Map.of("active_job","PALADIN","jobs",Map.of(
                "PALADIN",Map.of("level",12,"xp",25,"awakening","A","perks",Map.of("power",9)),"MINER",Map.of("level",5,"xp",8)))));
        Map<String,Object> converted=Map.of("rpg",Map.of("player",Map.of("active_combat","PALADIN","combat",Map.of("PALADIN",Map.of("level",12,"xp",25.0)),"life",Map.of("MINER",Map.of("level",5,"xp",8)))));
        require(compare(legacy,converted,new StringBuilder())==4,"Legacy dual-slot conversion/perk reset");
        Map<String,Object> dual=Map.of("rpg",Map.of("player",Map.of("combat",Map.of("RANGER",Map.of("level",20,"xp",0)),"life",Map.of("CHEF",Map.of("level",14,"xp",24)))));
        require(compare(dual,dual,new StringBuilder())==4,"Existing dual growth");
        Map<String,Object> mixed=Map.of("rpg",Map.of("player",Map.of("jobs",Map.of("RANGER",Map.of("level",1)),"combat",Map.of("RANGER",Map.of("level",20,"xp",0)))));
        require(compare(mixed,Map.of("rpg",Map.of("player",Map.of("combat",Map.of("RANGER",Map.of("level",20,"xp",0))))),new StringBuilder())==2,"Existing dual profile must win over an old duplicate");
        reject(()->unchanged(Map.of("owner","A"),Map.of("owner","B"),"plots"),"Owner change");
        reject(()->unchanged(Map.of("balance",7),Map.of(),"bank"),"Missing money");
        reject(()->compare(legacy,legacy,new StringBuilder()),"Unconverted RPG keys");
        reject(()->compare(dual,Map.of("rpg",Map.of("player",Map.of("combat",Map.of("RANGER",Map.of("level",19,"xp",0)),"life",Map.of("CHEF",Map.of("level",14,"xp",24))))),new StringBuilder()),"Lost current-schema growth");
        Map<String,Object> oldStocks=Map.of("stocks",Map.of("prices",Map.of("A",100),"holdings",Map.of("player",Map.of("A",2))));
        Map<String,Object> newStocks=Map.of("stocks",Map.of("prices",Map.of("A",110),"holdings",Map.of("player",Map.of("A",2))));
        require(compare(oldStocks,newStocks,new StringBuilder())==1,"Live prices should not masquerade as principal changes");
    }
    private static void reject(Runnable action,String label){boolean rejected=false;try{action.run();}catch(IllegalStateException expected){rejected=true;}require(rejected,"Comparator missed: "+label);}
}
