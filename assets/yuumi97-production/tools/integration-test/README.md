# Isolated asset integration test

This temporary plugin only runs when **both the working directory and Bukkit world container resolve to `E:/MinGem/assets/yuumi97-production/review/server-test`**, and Java was started with `-Dyuumi.asset.integration=true`. It refuses all other servers. It registers no commands, changes no authentication settings and requires zero connected players.

The primary agent must prepare a copied server at that exact path, with the final YuumiLandCore JAR and resource-pack ZIP. Use separate local test ports to avoid conflicts with any existing server. Do not deploy this test JAR to `E:/MinGem/server/plugins`.

## Compile with the portable JDK

```powershell
powershell -ExecutionPolicy Bypass -File E:\MinGem\assets\yuumi97-production\tools\integration-test\compile.ps1
```

The script compiles only this test plugin against the existing local Gradle cache (falling back to Paper server libraries). It does not run the project's build or deploy anything. Output: `tools/integration-test/dist/YuumiAssetIntegrationTest.jar`.

## Prepare and run the copied server

1. Copy that JAR to `E:/MinGem/assets/yuumi97-production/review/server-test/plugins/`.
2. Create `plugins/YuumiAssetIntegrationTest/` inside the test server. Copy `E:/MinGem/assets/yuumi97-production/backups/20261115-0218/server/resourcepack/Yuumi97Pack.zip` to its `baseline-pack.zip`. The plugin validates that the baseline contains no `prop_*` item definitions before replacing any files.
3. Launch the copied server with the portable JDK and the arming property:

```powershell
Set-Location -LiteralPath E:\MinGem\assets\yuumi97-production\review\server-test
& E:\MinGem\runtime\jdk-21\bin\java.exe -Dyuumi.asset.integration=true -Xms1G -Xmx4G -jar paper.jar nogui
```

The plugin waits 100 ticks, after the core's 40-tick hub task, and then executes once. It does not rebuild the city. Wait for `ASSET_INTEGRATION_TEST_COMPLETE PASS`, `PASS_WITH_SKIPS`, or `FAIL` in the server log. It deliberately leaves server shutdown under operator control.

## Assertions and restoration

- Exactly 10 tagged props, with 1 statue, 7 cabinets, 1 roulette and 1 trophy. Their model keys, NONE item transform and FIXED billboard are verified.
- Refresh twice without duplication; unload/reload feasible mount chunks without changing spawn or plugin tickets, confirm saved UUIDs, refresh and recheck counts. A refused unload becomes an explicit SKIP, not a false pass.
- All four replaced statue blocks and the ownership bitmask; all eight managed holograms at the expected coordinates; seven note blocks and the roulette interaction blocks remain intact.
- Replace **only the copied server's ZIP** with the baseline in a `try/finally`; verify zero props and restored blocks/holograms; restore the final ZIP and verify 10 props and final positions again.
- Hat is PAPER with the correct PDC/model, HEAD equippable, no camera overlay, no equipment asset, no damage and maximum stack size 1. Plushie is PAPER. Existing wand/bell/book preserve their real base materials, PDC IDs and model keys.

Any assertion or exception results in FAIL. The original final ZIP is kept in memory and in `final-pack.recovery.zip`. The test restores the final ZIP and refreshes in its `finally`, final cleanup, and interrupted `onDisable` paths. SHA-256 confirms byte-for-byte restoration. If the operating system kills the process before Java cleanup executes, restore `final-pack.recovery.zip` manually into the copied server's resourcepack directory; production is never the target.

Reports are UTF-8 `plugins/YuumiAssetIntegrationTest/report.json` and `report.txt`. Preserve those reports and relevant logs with the production review artifacts, then stop the copied server and remove the temporary test JAR. Do not retain the arming flag for ordinary launches.

This is a server API integration test. It does **not** verify Minecraft client rendering, resource-pack download/reload, skin fit, visual yaw, or player click/gameplay effects. The resource-pack HTTP server retains its startup cache during the temporary ZIP swap; that is outside this test's scope.
