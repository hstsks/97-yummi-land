package kr.yuumi97.core.dungeon.mob;

import kr.yuumi97.core.util.TextUtil;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** ZIP-only socket loading. Animation uses the artist's 180-yaw / -pitch golden-fixture convention. */
public final class MonsterRig {
    public enum Pose {IDLE,WALK,WIND_UP,STRIKE,RECOVER,HIT_FLASH,DEATH}
    public record Socket(String model,double x,double y,double z){}
    public record Definition(double scale,String root,Map<String,Socket> sockets){}
    public static final class Catalog {
        private final Map<String,Definition> definitions=new HashMap<>();
        private final Set<String> models=new HashSet<>();
        public Catalog(JavaPlugin plugin){
            File file=new File(plugin.getServer().getWorldContainer(),"resourcepack/Yuumi97Pack.zip");
            if(!file.isFile())file=new File(plugin.getDataFolder(),"Yuumi97Pack.zip");
            if(!file.isFile())return;
            try(ZipFile zip=new ZipFile(file)){
                zip.stream().map(java.util.zip.ZipEntry::getName).filter(n->n.startsWith("assets/yuumi97/items/")&&n.endsWith(".json"))
                    .forEach(n->models.add("yuumi97:"+n.substring(n.lastIndexOf('/')+1,n.length()-5)));
                var entry=zip.getEntry("assets/yuumi97/monster_rigs.json");if(entry==null)return;
                YamlConfiguration json=new YamlConfiguration();
                try(InputStream in=zip.getInputStream(entry)){json.loadFromString(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
                if(json.getInt("schemaVersion")!=1||json.getInt("unitsPerBlock")!=16||!"bukkit".equals(json.getString("yawConvention"))||json.getInt("posePitchToLocalX")!=-1)
                    throw new IllegalArgumentException("unsupported rig convention");
                if(!json.getIntegerList("modelOrigin").equals(List.of(8,8,8))||!json.getIntegerList("rootAnchor").equals(List.of(8,0,8))||!json.getIntegerList("front").equals(List.of(0,0,-1)))throw new IllegalArgumentException("unsupported rig pivots");
                for(MobType type:MobType.values()){
                    ConfigurationSection section=json.getConfigurationSection("rigs."+type.id);if(section==null)continue;
                    double scale=section.getDouble("baseScale",type.scale);if(!Double.isFinite(scale)||scale<=0||scale>8)throw new IllegalArgumentException(type.id+" scale");
                    Map<String,Socket> sockets=new LinkedHashMap<>();ConfigurationSection ss=section.getConfigurationSection("sockets");
                    if(ss!=null)for(String key:ss.getKeys(false)){
                        var v=ss.getDoubleList(key+".offset");if(v.size()!=3||v.stream().anyMatch(d->!Double.isFinite(d)||Math.abs(d)>96))throw new IllegalArgumentException(type.id+" socket");
                        sockets.put(key,new Socket(ss.getString(key+".model","yuumi97:"+type.id+"_"+key),v.get(0),v.get(1),v.get(2)));
                    }
                    if(!sockets.keySet().equals(type.boss()?Set.of("head","weapon"):Set.of("arm")))throw new IllegalArgumentException(type.id+" incomplete rig");
                    definitions.put(type.id,new Definition(scale,section.getString("rootModel","yuumi97:"+type.id+(type.boss()?"_core":"_body")),Map.copyOf(sockets)));
                }
            }catch(Exception ex){plugin.getLogger().warning("[Dungeon] Rig manifest fallback: "+ex.getMessage());definitions.clear();}
        }
        public int manifestCount(){return definitions.size();}
        public boolean hasModel(String name){return models.contains(name);}
        public Definition definition(MobType type){
            Definition delivered=definitions.get(type.id);if(delivered!=null)return delivered;
            Map<String,Socket> sockets;
            if(type.boss()){
                double head=switch(type){case ASTRO,ARCHONS->23;case NOVARION->25;case VULCANTUS,ECLIPSIA->24;default->27;};
                double[] weapon=switch(type){case ASTRO->new double[]{0,23,7};case NOVARION->new double[]{0,24,14};case VULCANTUS->new double[]{0,23,0};case IGNION->new double[]{0,25,0};case ARCHONS->new double[]{0,19,0};default->new double[]{0,20,0};};
                sockets=Map.of("head",new Socket("yuumi97:"+type.id+"_head",0,head,0),"weapon",new Socket("yuumi97:"+type.id+"_weapon",weapon[0],weapon[1],weapon[2]));
            }else{
                double[] arm=switch(type){case MIMIC->new double[]{0,15,6};case SENTINEL->new double[]{-10,21,0};case LIBRARIAN->new double[]{0,24,-2};case GOLEM->new double[]{0,22,0};case IMP->new double[]{0,19,0};case HOUND->new double[]{0,10,-11};case BOMBARDIER->new double[]{0,21,-1};case JUGGERNAUT->new double[]{0,23,0};case STALKER->new double[]{0,13.5,-1.6};case CHOIR->new double[]{0,15,0};case OCULUS->new double[]{0,14,2};default->new double[]{0,20,-1};};
                sockets=Map.of("arm",new Socket("yuumi97:"+type.id+"_arm",arm[0],arm[1],arm[2]));
            }
            return new Definition(type.scale,"yuumi97:"+type.id+(type.boss()?"_core":"_body"),sockets);
        }
    }
    private record Part(Display display,String role,Socket socket,boolean model){}
    private final LivingEntity base;private final MobType type;private final Definition definition;
    private final List<Part> parts=new ArrayList<>();private final TextDisplay label;
    private Pose pose=Pose.IDLE;private long poseStart,poseEnd;private double lastX,lastZ;private boolean removed;
    public MonsterRig(LivingEntity base,MobType type,Catalog catalog,Consumer<Entity> tag){
        this.base=base;this.type=type;definition=catalog.definition(type);lastX=base.getLocation().getX();lastZ=base.getLocation().getZ();
        parts.add(part("root",new Socket(definition.root(),0,8,0),catalog,tag));
        definition.sockets().forEach((key,value)->parts.add(part(key,value,catalog,tag)));
        label=base.getWorld().spawn(base.getLocation().add(0,3,0),TextDisplay.class);tag.accept(label);
        label.setBillboard(Display.Billboard.CENTER);label.setSeeThrough(false);label.setLineWidth(240);label.setPersistent(false);
    }
    private Part part(String role,Socket socket,Catalog catalog,Consumer<Entity> tag){
        Display display;boolean model=catalog.hasModel(socket.model());
        if(model){ItemDisplay item=base.getWorld().spawn(base.getLocation(),ItemDisplay.class);ItemStack stack=new ItemStack(Material.PAPER);
            stack.editMeta(meta->meta.setItemModel(NamespacedKey.fromString(socket.model())));item.setItemStack(stack);item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);display=item;
        }else{BlockDisplay block=base.getWorld().spawn(base.getLocation(),BlockDisplay.class);Material material=role.equals("head")?Material.GILDED_BLACKSTONE:type.chapter==1?Material.WAXED_COPPER_BLOCK:type.chapter==2?Material.POLISHED_BLACKSTONE:Material.AMETHYST_BLOCK;
            block.setBlock(material.createBlockData());display=block;}
        tag.accept(display);display.setPersistent(false);display.setInterpolationDelay(0);display.setInterpolationDuration(2);display.setTeleportDuration(2);
        display.setBrightness(new Display.Brightness(15,15));display.setViewRange(1.5f);return new Part(display,role,socket,model);
    }
    public Pose pose(){return pose;}
    public void pose(Pose pose,long tick,int duration){this.pose=pose;poseStart=tick;poseEnd=tick+duration;}
    public void tick(long tick,Player target,double hp,double max,double poise,double maxPoise){
        if(removed)return;if(tick>=poseEnd&&pose!=Pose.DEATH)pose=Pose.IDLE;
        Location feet=base.getLocation();double travel=Math.hypot(feet.getX()-lastX,feet.getZ()-lastZ);lastX=feet.getX();lastZ=feet.getZ();
        if((pose==Pose.IDLE||pose==Pose.WALK))pose=travel>.01?Pose.WALK:Pose.IDLE;
        float progress=(float)Math.max(0,Math.min(1,(tick-poseStart)/(double)Math.max(1,poseEnd-poseStart)));
        float pitch=switch(pose){case WIND_UP->-65*progress;case STRIKE->-65+120*progress;case RECOVER->55*(1-progress);case DEATH->75*progress;default->pose==Pose.WALK?(float)(Math.sin(tick*.3)*12):0;};
        Quaternionf heading=new Quaternionf().rotateY((float)Math.toRadians(180-feet.getYaw()));float scale=(float)definition.scale();
        float bounce=pose==Pose.WALK?(float)Math.abs(Math.sin(tick*.25))*.06f:0;
        if(pose==Pose.WIND_UP)bounce+=(float)Math.sin(tick*2)*.025f;
        float pulse=pose==Pose.HIT_FLASH?1+.05f*(1-progress):pose==Pose.DEATH?1-progress*.65f:1;
        for(Part part:parts){
            if(!part.display().isValid())continue;part.display().teleport(feet.clone().setDirection(new org.bukkit.util.Vector(0,0,1)));
            Vector3f offset=new Vector3f((float)part.socket.x()/16,(float)part.socket.y()/16,(float)part.socket.z()/16).mul(scale);heading.transform(offset);offset.y+=bounce;
            Quaternionf rotation=new Quaternionf(heading);
            if(part.role.equals("head")&&target!=null){Location look=feet.clone();look.setDirection(target.getEyeLocation().toVector().subtract(feet.clone().add(0,scale*1.5,0).toVector()));
                rotation=new Quaternionf().rotateY((float)Math.toRadians(180-look.getYaw())).rotateX((float)Math.toRadians(-look.getPitch()));}
            else if(!part.role.equals("root"))rotation.rotateX((float)Math.toRadians(-pitch));
            Vector3f size=new Vector3f(scale*pulse);
            if(!part.model){
                size.mul(part.role.equals("root")?new Vector3f(.7f,1.35f,.6f):part.role.equals("head")?new Vector3f(.65f,.65f,.65f):new Vector3f(.22f,1f,.25f));
                if(part.role.equals("root"))offset.y=bounce+size.y*.5f;
                offset.sub(rotation.transform(new Vector3f(size).mul(.5f)));
            }
            part.display().setTransformation(new Transformation(offset,rotation,size,new Quaternionf()));
        }
        label.teleport(feet.clone().add(0,definition.scale()*2+.6,0));
        label.text(TextUtil.mm("<"+(type.boss()?"gold":"white")+">"+type.title+"</"+(type.boss()?"gold":"white")+">\n<red>♥ "+(int)Math.ceil(hp)+"/"+(int)Math.ceil(max)+" <aqua>◆ "+(int)Math.ceil(poise)+"/"+(int)Math.ceil(maxPoise)));
    }
    public void remove(){if(removed)return;removed=true;parts.forEach(p->p.display.remove());label.remove();}
    public int partCount(){return parts.size();}
}
