package kr.yuumi97.core.dungeon;

import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class PartyManager implements CommandExecutor {
    public static final class Party {
        public final UUID id = UUID.randomUUID();
        private UUID leader;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        Party(UUID leader) { this.leader = leader; members.add(leader); }
        public UUID leader() { return leader; }
        public List<UUID> members() { return List.copyOf(members); }
    }
    private record Invite(UUID partyId, long deadline) {}
    private final Map<UUID, Party> parties = new HashMap<>();
    private final Map<UUID, UUID> membership = new HashMap<>();
    private final Map<UUID, Invite> invitations = new HashMap<>();
    private final Predicate<UUID> busy;
    private final Supplier<Location> gate;

    public PartyManager(Predicate<UUID> busy, Supplier<Location> gate) { this.busy = busy; this.gate = gate; }
    public Party getParty(UUID player) { return parties.get(membership.get(player)); }
    public Party ensureSolo(Player player) {
        Party p = getParty(player.getUniqueId());
        if (p == null) { p = new Party(player.getUniqueId()); parties.put(p.id, p); membership.put(player.getUniqueId(), p.id); }
        return p;
    }
    public Collection<Player> onlineMembers(Player player) {
        Party p = getParty(player.getUniqueId());
        if (p == null) return List.of(player);
        List<Player> out = new ArrayList<>();
        for (UUID id : p.members) { Player member = Bukkit.getPlayer(id); if (member != null) out.add(member); }
        return out;
    }
    private boolean locked(Party p) { return p.members.stream().anyMatch(busy); }
    public void transferDisconnectedLeader(UUID player) {
        Party p = getParty(player);
        if (p == null || !p.leader.equals(player)) return;
        for (UUID id : p.members) {
            Player candidate = Bukkit.getPlayer(id);
            if (!id.equals(player) && candidate != null && candidate.isOnline()) {
                p.leader = id; tell(p, "<yellow>파티장이 " + candidate.getName() + "님으로 변경되었습니다."); break;
            }
        }
    }
    public void guide(Player player) {
        Location loc = gate.get();
        if (loc != null) player.setCompassTarget(loc);
        player.sendMessage(TextUtil.msg("🗝 미궁 입장은 북쪽 성소 <yellow>미궁 수문장 유령냥 (18, 66, -55)</yellow>을 방문하세요. 파티: /파티 생성|초대|수락|탈퇴|추방|정보"));
    }
    private void tell(Party p, String message) {
        for (UUID id : p.members) { Player member = Bukkit.getPlayer(id); if (member != null) member.sendMessage(TextUtil.msg(message)); }
    }
    private String name(UUID id) { String name = Bukkit.getOfflinePlayer(id).getName(); return name == null ? id.toString().substring(0, 8) : name; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (label.equalsIgnoreCase("미궁")) { guide(player); return true; }
        String sub = args.length == 0 ? "정보" : args[0].toLowerCase(Locale.ROOT);
        Party p = getParty(player.getUniqueId());
        switch (sub) {
            case "생성", "create" -> {
                if (p != null) player.sendMessage(TextUtil.msg("<red>이미 파티에 소속되어 있습니다."));
                else { ensureSolo(player); player.sendMessage(TextUtil.msg("<green>파티를 생성했습니다. 혼자서도 입장할 수 있습니다.")); }
            }
            case "초대", "invite" -> {
                if (args.length < 2) { player.sendMessage(TextUtil.msg("/파티 초대 <이름>")); return true; }
                if (p == null) p = ensureSolo(player);
                if (!p.leader.equals(player.getUniqueId()) || locked(p)) { player.sendMessage(TextUtil.msg("<red>입장 대기/진행 중이 아닌 파티장만 초대할 수 있습니다.")); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || target.equals(player) || getParty(target.getUniqueId()) != null || p.members.size() >= 8) {
                    player.sendMessage(TextUtil.msg("<red>초대 대상/파티 인원을 확인하세요. 최대 8인입니다.")); return true;
                }
                invitations.put(target.getUniqueId(), new Invite(p.id, System.currentTimeMillis() + 60_000));
                target.sendMessage(TextUtil.msg("<yellow>" + player.getName() + "님의 파티 초대입니다. 60초 안에 /파티 수락"));
                player.sendMessage(TextUtil.msg("<green>파티 초대를 보냈습니다."));
            }
            case "수락", "accept" -> {
                Invite invitation = invitations.remove(player.getUniqueId());
                Party invited = invitation == null ? null : parties.get(invitation.partyId);
                if (p != null || invited == null || invitation.deadline <= System.currentTimeMillis() || invited.members.size() >= 8 || locked(invited)) {
                    player.sendMessage(TextUtil.msg("<red>유효한 초대가 없거나 파티가 입장 중입니다.")); return true;
                }
                invited.members.add(player.getUniqueId()); membership.put(player.getUniqueId(), invited.id);
                tell(invited, "<green>" + player.getName() + "님이 파티에 합류했습니다.");
            }
            case "탈퇴", "leave" -> {
                if (p == null) return true;
                if (locked(p)) { player.sendMessage(TextUtil.msg("<red>입장 동의 또는 미궁 진행 중에는 명단을 바꿀 수 없습니다. 귀환 후 탈퇴하세요.")); return true; }
                p.members.remove(player.getUniqueId()); membership.remove(player.getUniqueId());
                if (p.members.isEmpty()) parties.remove(p.id);
                else { if (p.leader.equals(player.getUniqueId())) p.leader = p.members.iterator().next(); tell(p, "<yellow>" + player.getName() + "님이 탈퇴했습니다."); }
                player.sendMessage(TextUtil.msg("파티에서 탈퇴했습니다."));
            }
            case "추방", "kick" -> {
                if (p == null || args.length < 2 || !p.leader.equals(player.getUniqueId()) || locked(p)) return true;
                UUID target = null;
                for (UUID id : p.members) if (name(id).equalsIgnoreCase(args[1])) target = id;
                if (target == null || target.equals(player.getUniqueId())) return true;
                p.members.remove(target); membership.remove(target);
                Player kicked = Bukkit.getPlayer(target); if (kicked != null) kicked.sendMessage(TextUtil.msg("<yellow>파티에서 추방되었습니다."));
                tell(p, "<yellow>" + name(target) + "님을 파티에서 추방했습니다.");
            }
            default -> {
                if (p == null) { guide(player); return true; }
                player.sendMessage(TextUtil.msg("<gold>파티장 " + name(p.leader) + " │ " + p.members.size() + "/8인"));
                for (UUID id : p.members) player.sendMessage(TextUtil.mm("<gray> · " + name(id) + (Bukkit.getPlayer(id) == null ? " <dark_gray>(오프라인)" : " <green>(온라인)")));
            }
        }
        return true;
    }
}
