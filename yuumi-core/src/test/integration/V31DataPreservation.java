import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only production comparison; the only output is the audit log under yuumi-core/build. */
public final class V31DataPreservation {
    private static final String PREFIX = "plugins/YuumiLandCore/";
    private static final String DEFAULT_BACKUP = "backups/Yuumi97Land-20261115-220827844-2fea508d.zip";

    public static void main(String[] args) throws Exception {
        selfCheck();
        if (args.length == 1 && args[0].equals("--self-test")) {
            System.out.println("DATA_COMPARATOR_SELF_CHECK PASS");
            return;
        }
        Path root = (args.length > 0 ? Path.of(args[0]) : Path.of("")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("yuumi-core/settings.gradle.kts")))
            throw new IllegalArgumentException("Pass the MinGem repository root as the first argument.");
        Path backup = args.length > 1 ? Path.of(args[1]).toAbsolutePath().normalize() : root.resolve(DEFAULT_BACKUP);
        Path currentRoot = root.resolve("server/plugins/YuumiLandCore");
        Path log = root.resolve("yuumi-core/build/verification-v31/data-preservation.log");
        Files.createDirectories(log.getParent());
        StringBuilder report = new StringBuilder("\nData preservation audit ")
                .append(ZonedDateTime.now(ZoneId.of("Asia/Seoul"))).append('\n')
                .append("Backup: ").append(backup).append('\n');
        try (ZipFile zip = new ZipFile(backup.toFile())) {
            Map<String, ZipEntry> entries = new HashMap<>();
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName().replace('\\', '/');
                if (entries.putIfAbsent(name, entry) != null)
                    throw new IllegalStateException("Duplicate normalized backup entry: " + name);
            }
            ZipEntry oldData = entries.get(PREFIX + "data.yml");
            require(oldData != null, "Backup has no YuumiLandCore/data.yml");
            byte[] oldBytes;
            try (var input = zip.getInputStream(oldData)) { oldBytes = input.readAllBytes(); }
            Path currentData = currentRoot.resolve("data.yml");
            byte[] currentBytes = Files.readAllBytes(currentData);
            Map<?, ?> before = yaml(oldBytes), after = yaml(currentBytes);
            long leafCount = 0;
            for (String key : List.of("altar", "bank", "plots", "trust", "codex")) {
                if (!before.containsKey(key)) {
                    report.append("SKIP absent in baseline: ").append(key).append('\n');
                    continue;
                }
                require(after.containsKey(key), "Missing legacy root: " + key);
                long leaves = unchanged(before.get(key), after.get(key), key);
                leafCount += leaves;
                report.append("PASS legacy ").append(key).append(": ").append(leaves).append(" existing scalar values unchanged\n");
            }
            Map<?, ?> oldRpg = asMap(before.get("rpg")), newRpg = asMap(after.get("rpg"));
            int jobs = 0;
            for (var player : oldRpg.entrySet()) {
                Map<?, ?> oldJobs = asMap(asMap(player.getValue()).get("jobs"));
                if (oldJobs.isEmpty()) continue;
                Map<?, ?> newJobs = asMap(asMap(newRpg.get(player.getKey())).get("jobs"));
                for (var job : oldJobs.entrySet()) {
                    require(newJobs.containsKey(job.getKey()), "Missing existing RPG job: " + player.getKey() + "." + job.getKey());
                    leafCount += unchanged(job.getValue(), newJobs.get(job.getKey()), "rpg." + player.getKey() + ".jobs." + job.getKey());
                    jobs++;
                }
            }
            report.append("PASS existing RPG jobs preserved: ").append(jobs).append(" (new jobs/keys allowed)\n");
            Map<?, ?> oldCasino = asMap(before.get("casino")), newCasino = asMap(after.get("casino"));
            if (oldCasino.containsKey("jackpot")) {
                require(newCasino.containsKey("jackpot"), "Missing casino.jackpot");
                leafCount += unchanged(oldCasino.get("jackpot"), newCasino.get("jackpot"), "casino.jackpot");
                report.append("PASS casino.jackpot unchanged\n");
            } else report.append("SKIP absent in baseline: casino.jackpot\n");
            Map<Path, String> vaultHashes = new LinkedHashMap<>();
            for (var entry : entries.entrySet()) {
                if (entry.getValue().isDirectory() || !entry.getKey().startsWith(PREFIX + "vaults/")) continue;
                String relative = entry.getKey().substring(PREFIX.length());
                Path current = currentRoot.resolve(relative).normalize();
                require(current.startsWith(currentRoot), "Unsafe vault path in backup: " + relative);
                byte[] oldVault;
                try (var input = zip.getInputStream(entry.getValue())) { oldVault = input.readAllBytes(); }
                byte[] currentVault = Files.readAllBytes(current);
                require(Arrays.equals(oldVault, currentVault), "Vault bytes changed: " + relative);
                vaultHashes.put(current, hash(currentVault));
            }
            require(Arrays.equals(currentBytes, Files.readAllBytes(currentData)),
                    "Production data changed during this check; rerun after the server has stopped.");
            for (var vault : vaultHashes.entrySet()) require(vault.getValue().equals(hash(Files.readAllBytes(vault.getKey()))),
                    "Vault changed during this check: " + vault.getKey().getFileName());
            report.append("PASS vault file byte equality: ").append(vaultHashes.size()).append(" files\n")
                    .append("Baseline data SHA-256: ").append(hash(oldBytes)).append('\n')
                    .append("Current data SHA-256: ").append(hash(currentBytes)).append(" (new migration keys permitted)\n")
                    .append("DATA_PRESERVATION PASS: ").append(leafCount).append(" old scalar values; ")
                    .append(jobs).append(" existing jobs; ").append(vaultHashes.size()).append(" byte-identical vault files\n");
        } catch (Exception failure) {
            report.append("DATA_PRESERVATION FAILED: ").append(failure.getMessage()).append('\n');
            Files.writeString(log, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.err.print(report);
            throw failure;
        }
        Files.writeString(log, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.print(report);
    }

    private static Map<?, ?> yaml(byte[] bytes) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(32 * 1024 * 1024);
        Object loaded = new Yaml(new SafeConstructor(options)).load(new ByteArrayInputStream(bytes));
        require(loaded instanceof Map<?, ?>, "Core YAML must contain a mapping");
        return (Map<?, ?>) loaded;
    }
    private static Map<?, ?> asMap(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static long unchanged(Object before, Object after, String path) {
        if (before instanceof Map<?, ?> oldMap) {
            require(after instanceof Map<?, ?>, "Changed mapping type: " + path);
            Map<?, ?> newMap = (Map<?, ?>) after;
            long leaves = 0;
            for (var entry : oldMap.entrySet()) {
                require(newMap.containsKey(entry.getKey()), "Removed existing key: " + path + "." + entry.getKey());
                leaves += unchanged(entry.getValue(), newMap.get(entry.getKey()), path + "." + entry.getKey());
            }
            return leaves;
        }
        if (before instanceof List<?> oldList) {
            require(after instanceof List<?> && ((List<?>) after).size() == oldList.size(), "Changed existing list: " + path);
            List<?> newList = (List<?>) after;
            long leaves = 0;
            for (int i = 0; i < oldList.size(); i++) leaves += unchanged(oldList.get(i), newList.get(i), path + "[" + i + "]");
            return leaves;
        }
        boolean same = before instanceof Number a && after instanceof Number b
                ? new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0
                : before instanceof byte[] a && after instanceof byte[] b ? Arrays.equals(a, b) : Objects.equals(before, after);
        require(same, "Changed existing value: " + path);
        return 1;
    }
    private static String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void selfCheck() {
        require(unchanged(Map.of("balance", 7), Map.of("balance", 7.0, "new-key", true), "bank") == 1,
                "Comparator rejected harmless numeric representation/new keys");
        Map<String, Object> oldJob = Map.of("level", 20, "xp", 50, "awakening", "A", "perks", Map.of("power", 10));
        require(unchanged(Map.of("PALADIN", oldJob), Map.of("PALADIN", oldJob, "MINER", Map.of("level", 1)), "jobs") == 4,
                "Comparator rejected preserved nested job growth with a new job");
        for (Object changed : List.of(Map.of("balance", 8), Map.of("other", 7))) {
            boolean rejected = false;
            try { unchanged(Map.of("balance", 7), changed, "bank"); } catch (IllegalStateException expected) { rejected = true; }
            require(rejected, "Comparator failed to reject changed or removed legacy value");
        }
        boolean listRejected = false;
        try { unchanged(List.of("owner", "trusted"), List.of("trusted", "owner"), "trust"); }
        catch (IllegalStateException expected) { listRejected = true; }
        require(listRejected, "Comparator failed to reject changed list ordering");
    }
}
