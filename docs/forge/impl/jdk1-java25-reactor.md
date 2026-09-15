---
status: done
id: jdk1-java25-reactor
wave: JDK25
pr: JDK1
---

# jdk1-java25-reactor

## Summary

JDK1 (BE only): retarget the Maven reactor from Java 21 to **OpenJDK 25 LTS**. Parent `java.version` / `maven.compiler.source` / `maven.compiler.target` and `maven-compiler-plugin` `<source>`/`<target>` are **25**. Spring Boot parent stays **4.0.6**. MapStruct stays **1.5.5.Final** (javac succeeded). JaCoCo on crm-service + pricing-service **0.8.11 → 0.8.14** (0.8.11 cannot instrument class file major 69). Dual-read YAML unchanged. No FE. No CI / Dockerfiles (JDK2). No `ARCHITECTURE.md`. No `00-orchestrator-status.md`. Spring AI **0.8.1** untouched.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / confirmation |
|--------|------|-------------------------|
| `java.version` | **UNKNOWN** → treat **HIGH** | Maven property, not a Java symbol. **rg:** pin only in `backend/pom.xml`; every reactor module inherits it |
| `maven.compiler.source` / `maven-compiler-plugin` | **UNKNOWN** → **HIGH** | **rg:** same parent pom only; child poms have no source/target override |
| `jacoco-maven-plugin` | **UNKNOWN** → treat **HIGH** | **rg:** hardcoded `0.8.11` only in `crm-service/pom.xml` and `pricing-service/pom.xml`. Coverage **0.80** not lowered |

**WARN:** UNKNOWN ≠ unused. Parent `java.version` serializes all 3 libs + 11 services. Confirmed with `rg` + Maven, not an empty caller set.

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/jdk1` (`feat/forge-jdk1-java25-reactor`)  
Index: GitNexus CLI (impact target-not-found → UNKNOWN)  
Impact CLI: `node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "java.version" --direction upstream --repo /home/username01/Projects/open-crm-cpq`

## Toolchain

```text
mise ls java → temurin-25.0.4+101.0.LTS (already installed; not 27.0.0 / latest)
JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
```

PATH `java` remains host **27** — Maven used `JAVA_HOME` Temurin 25 only. `newgrp docker` wrapped every `./mvnw` run.

## Files

| Path | Change |
|------|--------|
| `backend/pom.xml` | `java.version` / compiler source+target / plugin source+target **21 → 25**. Boot **4.0.6** unchanged |
| `backend/services/pricing-service/pom.xml` | JaCoCo **0.8.11 → 0.8.14** (Java 25 class files). Line **0.80** gate kept |
| `backend/services/crm-service/pom.xml` | JaCoCo **0.8.11 → 0.8.14**. Line **0.80** gate kept |
| `docs/forge/impl/jdk1-java25-reactor.md` | This note |

## Acceptance

- [x] `java -version` under `JAVA_HOME` is **25.0.4.1 LTS** (not 27)
- [x] Parent properties **25**; compiler plugin **25**; Boot **4.0.6**
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] `newgrp docker` + `JAVA_HOME=…temurin-25… ./mvnw -B -pl libs/common,libs/security,libs/rules-engine -am test`
- [x] `./mvnw -B -pl services/crm-service,services/gateway-service -am verify -Punit-tests`
- [x] Per-module `./mvnw -B -pl services/<mod> -am test` for pricing, product, quote, pricebook, configuration-engine, approval-routing, document-generation, contract-asset-management, ai-service
- [x] MapStruct **not** bumped (1.5.5.Final compiled on release 25)
- [x] JaCoCo bumped **only** because 0.8.11 failed report (`Unsupported class file major version 69`)
- [x] No entity leaks; no Spring AI bump; no FE; no CI/Dockerfiles; no `ARCHITECTURE.md`; no `00-orchestrator-status.md`

## Verify

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
cd backend
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B -pl libs/common,libs/security,libs/rules-engine -am test
./mvnw -B -pl services/crm-service,services/gateway-service -am verify -Punit-tests
./mvnw -B -pl services/pricing-service -am test
# … remaining services/<mod> -am test
EOF
```

Result (2026-09-15). JDK **25.0.4.1 LTS**. javac `[debug parameters release 25]`. Boot plugin `spring-boot:4.0.6:repackage`.

| Slice | Tests run | Failures | Errors | Skipped | Status |
|-------|----------:|---------:|-------:|--------:|--------|
| libs/common | 86 | 0 | 0 | 0 | SUCCESS |
| libs/security | 13 | 0 | 0 | 0 | SUCCESS |
| libs/rules-engine | 56 | 0 | 0 | 0 | SUCCESS |
| crm-service (`verify -Punit-tests`) | 143 | 0 | 0 | 0 | SUCCESS |
| gateway-service | 54 | 0 | 0 | 0 | SUCCESS |
| pricing-service (after JaCoCo 0.8.14) | 101 | 0 | 0 | 0 | SUCCESS |
| product-service | 28 | 0 | 0 | 0 | SUCCESS |
| quote-service | 31 | 0 | 0 | 0 | SUCCESS |
| pricebook-service | 7 | 0 | 0 | 0 | SUCCESS |
| configuration-engine | 76 | 0 | 0 | 0 | SUCCESS |
| approval-routing | 20 | 0 | 0 | 0 | SUCCESS |
| document-generation | 13 | 0 | 0 | 0 | SUCCESS |
| contract-asset-management | 18 | 0 | 0 | 0 | SUCCESS |
| ai-service | 191 | 0 | 0 | 0 | SUCCESS |

Pricing first `test` run: **101 tests green**, then `jacoco-maven-plugin:0.8.11:report` failed (`Unsupported class file major version 69`). After 0.8.14: `jacoco:0.8.14:report` analyzed Pricing 31 classes / CRM 30 classes. Pricing `jacoco:check@check`: **All coverage checks have been met** (0.80). Extra CRM `verify` (units + Testcontainers ITs, not skipped): **202** tests, `jacoco:0.8.14:check` **All coverage checks have been met**. Line **0.80** minimum not lowered.

Non-blocking JDK 25 warnings (tests still EXIT=0): Maven Jansi native-access, Guava `sun.misc.Unsafe`, Mockito dynamic agent / ByteBuddy. No `--add-opens` added (tests did not fail). Drools **10.2.0** DRL corpus compile green under 25.

### detect-changes (pre-commit)

```bash
cd /home/username01/Projects/open-crm-cpq/.worktrees/jdk1
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq
```

Result: **Diff touched 4 file(s) but no indexed symbols overlap those hunks — not a clean tree.** No `partial`/`truncated`. Pom property / plugin version edits are not Java symbols — proceed after `rg` confirm (java pins only in parent pom; JaCoCo versions only in crm + pricing poms). Impl note is docs-only.

## Out of scope (JDK2 / holds)

- `.github/workflows/ci.yml`, Jenkins Dockerfile, `docs/ci-contract.yaml`, service Dockerfiles (JDK2)
- `docs/ARCHITECTURE.md` (X1 FE after JDK2 ∧ NG4)
- `docs/forge/00-orchestrator-status.md`
- Dual-read flip; Activities API; #238; #241; R2; NgRx; Track 2 Spring AI; GLM-5 runtime
- Boot 4.1 / Boot 3; MapStruct 1.6 unless javac fails (it did not)
