---
status: green
id: it2-java25-docker-back
wave: IT-2
pr: IT-2
---

# it2-java25-docker-back

## Summary

IT-2 (BE back-services full `verify` on Temurin 25 + Docker): remaining CPQ services after IT-1's crm/quote/gateway slice. **Docs-only** evidence. No Java, no pom, no dual-read flip, no Jacoco floor change. Exclusive file: this note.

**GREEN:** `./mvnw -B clean verify -pl services/pricing-service,services/product-service,services/pricebook-service,services/configuration-engine,services/approval-routing,services/document-generation,services/contract-asset-management -am` → **BUILD SUCCESS** (exit 0). Pricing `jacoco:0.8.14:check` → **All coverage checks have been met.** Floor **0.80** not lowered. No `-Punit-tests`, no `-Werror`, no `-nowarn`, no `-T1C`. Did **not** chmod `docker.sock`. Did **not** edit `docs/forge/00-orchestrator-status.md`.

## GitNexus

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/be-it2` (`test/forge-it2-java25-back`)  
Index: stale vs tip (plan 38: **skip analyze**). UNKNOWN ≠ unused; `rg` confirm before commit.

Docs-only impl note — no indexed code symbols. Pre-edit impact on `it2-java25-docker-back`: **UNKNOWN** (`target-not-found`). Confirmed with `rg` + `git diff`: dual-read YAML and Jacoco `<minimum>0.80</minimum>` hunks are empty.

```bash
rg -n 'legacy-local-read-enabled' backend/services/quote-service/src/main/resources/application.yml
rg -n '<minimum>' backend/services/pricing-service/pom.xml backend/services/crm-service/pom.xml
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq
```

Pre-commit (this worktree, file staged): **Diff touched 1 file(s) but no indexed symbols overlap those hunks — not a clean tree.** No `partial`/`truncated`. Checkout confirmed: worktree `docs/forge/impl/it2-java25-docker-back.md` only (primary `main` working tree was not the diff). Docs-only hunk after `rg` confirm. `.grok-it2/` run logs are untracked and not committed.

## Toolchain

```text
JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
```

PATH `java` is host **27** — Maven used `JAVA_HOME` Temurin 25 only (`javac [debug parameters release 25]`). `newgrp docker` wrapped `./mvnw`. Docker engine **29.7.2** (API 1.55). Did **not** chmod `docker.sock`. IT-1 (`be-it1`) ran concurrently; this slice did **not** need to serialize.

Tip at run: `origin/main` @ `10093e56` (`docs/forge-38-jdk-ng-upgrade-test`).

## Files

| Path | Change |
|------|--------|
| `docs/forge/impl/it2-java25-docker-back.md` | This note |

## Command

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version
cd backend
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B clean verify -pl services/pricing-service,services/product-service,services/pricebook-service,services/configuration-engine,services/approval-routing,services/document-generation,services/contract-asset-management -am
EOF
```

Wall: 2026-09-15T08:32:47 → 08:41:35-05:00 (**08:38 min**). Maven **BUILD SUCCESS**. Failsafe reports: **0**. `jacoco.skip` / `unit-tests` profile: **not** set (no skip line in the log).

## Per-service counts (surefire XML)

| Module | Tests run | Failures | Errors | Skipped | Status |
|--------|----------:|---------:|-------:|--------:|--------|
| libs/common (`-am`) | 86 | 0 | 0 | 0 | SUCCESS |
| libs/security (`-am`) | 13 | 0 | 0 | 0 | SUCCESS |
| libs/rules-engine (`-am`) | 56 | 0 | 0 | 0 | SUCCESS |
| pricing-service | 101 | 0 | 0 | 0 | SUCCESS |
| pricebook-service | 7 | 0 | 0 | 0 | SUCCESS |
| product-service | 28 | 0 | 0 | 0 | SUCCESS |
| configuration-engine | 76 | 0 | 0 | 0 | SUCCESS |
| approval-routing | 20 | 0 | 0 | 0 | SUCCESS |
| document-generation | 13 | 0 | 0 | 0 | SUCCESS |
| contract-asset-management | 18 | 0 | 0 | 0 | SUCCESS |
| **Slice total** | **418** | **0** | **0** | **0** | **BUILD SUCCESS** |

`@Tag("integration")` classes in these seven services: **0**. Testcontainers Maven deps are present in all seven poms; `org.testcontainers` / `@Container` / `PostgreSQLContainer` usage in `src/test` of this slice: **0** (`rg`). Docker was available; containers were not started by these tests.

## JaCoCo (pricing)

- Plugin **0.8.14**. Floor **`<minimum>0.80</minimum>`** unchanged in `pricing-service/pom.xml` and `crm-service/pom.xml`.
- `jacoco:0.8.14:prepare-agent` bound; `report` analyzed bundle **Pricing Service** **31** classes.
- `jacoco:0.8.14:check (check) @ pricing-service`: **All coverage checks have been met.**
- Line coverage: XML **639/757 ≈ 0.844**; csv **641/759 ≈ 0.845**. Matches VER-1 headroom; check now **ran** on `verify` (VER-1 gap closed for this slice).
- Floor **not** lowered. `jacoco.skip` **not** set.

## WARN-IT (Testcontainers / Ryuk / reuse)

| Signal | This run |
|--------|----------|
| Ryuk / `testcontainers.reuse` log lines | **0** (no container start) |
| `Could not find a valid Docker` | **0** |
| Committed `testcontainers.reuse.enabled` | **none** (untouched; plan forbids enabling) |
| Docker engine | **29.7.2** (available via `newgrp docker`) |

Ryuk/reuse noise expected only when a slice actually boots Testcontainers (IT-1 crm). IT-2 catalogs the **absence** of those lines so WARN-BE does not treat it as a suppressed log.

## Warning catalog (baseline for WARN-BE; not fixed here)

Third-party runtime (tests EXIT=0; no `--add-opens` added):

- Maven Jansi native-access (`org.fusesource.jansi.internal.JansiLoader` → `System::load`)
- Guava `sun.misc.Unsafe::objectFieldOffset` (`AbstractFuture$UnsafeAtomicHelper`, guava-33.2.1-jre)
- Mockito/ByteBuddy dynamic agent (`byte-buddy-agent-1.17.8.jar`) — **9** load sites (one per tested module that used Mockito)
- Protobuf `sun.misc.Unsafe::arrayBaseOffset` (`UnsafeUtil$MemoryAccessor`, protobuf-java-3.25.1) during pricing gRPC tests

In-repo javac (test sources only; production compile had **zero** `[WARNING]` lines in this log):

| File:line | Warning |
|-----------|---------|
| `configuration-engine/.../ConfigurationSessionControllerTest.java:63` | `MappingJackson2HttpMessageConverter` deprecated and marked for removal (2 diagnostics) |
| `approval-routing/.../ApprovalControllerTest.java:70` | same |
| `document-generation/.../DocumentControllerTest.java:60` | same |
| `contract-asset-management/.../SubscriptionControllerTest.java:67` | same |

IT-2 does **not** fix these (exclusive file is this note). WARN-BE owns the catalog + any source edits.

## Dual-read / holds

```yaml
# backend/services/quote-service/src/main/resources/application.yml:89
legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

YAML/env **untouched**. Default still **true**. `git diff` on that file and both Jacoco poms: **empty**.

- NgRx **held**; Activities API **not invented**; CI `#238` / Kafka `#241` **not started**
- **R2** price-book list-row delete **held**; subscriptions FE **held**; GLM-5 **not runtime**
- Boot **4.0.6**; Jacoco floor **0.80**; JAVA_HOME **25.0.4.1 LTS** — never 27
- Did **not** edit `docs/forge/00-orchestrator-status.md`
- Did **not** steal IT-1 / FE / w8 `arch-pi` panes

## Acceptance

- [x] `"$JAVA_HOME/bin/java" -version` → **25.0.4.1 LTS** (not 27)
- [x] `newgrp docker` + `./mvnw -B clean verify -pl … -am` **BUILD SUCCESS**
- [x] pricing `jacoco:check` = **All coverage checks have been met** (≥ 0.80; measured ≈ 0.844)
- [x] Per-service surefire counts recorded (418/0/0/0)
- [x] TC/Ryuk warning catalog recorded (zero container starts in this slice)
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] Jacoco **0.80** not lowered
- [x] Exclusive file: this impl note only
