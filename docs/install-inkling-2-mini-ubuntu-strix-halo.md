# Install Inkling 2 Mini on Ubuntu LTS
## Ryzen AI Max+ 395 (gfx1151) · 128 GB Unified Memory

Target: Strix Halo systems (AMD Ryzen AI Max+ 395, Radeon 8060S, 128 GB unified memory), Ubuntu 24.04 or 26.04 LTS.

Inkling 2 Mini is a looped MoE (26.4B total / ~3.4B active, effective ~5.4B with the loop, 48 effective layers with 40 sliding-window layers and 8 full-attention layers, multimodal vision via `mmproj-F16.gguf`). Native mixed-precision checkpoint is ~20 GB. Recommended GGUF for this machine: Unsloth UD-Q4_K_XL (~17.6 GB).

---

## 1. Check the host

Loom uses the AMD GPU device directly and requires Python 3.12+ and glibc 2.39+ for its Linux wheel. Confirmed working with Python 3.14 and glibc 2.43 on Ubuntu 26.04. Check permissions, groups, memory limits, power profile, disk space, and port availability from the same user account that will run the server.

```bash
python3 --version
id -nG
for node in /dev/kfd /dev/dri/renderD*; do
  if test -r "$node" && test -w "$node"; then
    printf '%s is accessible\n' "$node"
  else
    printf '%s is NOT accessible\n' "$node"
  fi
done
awk '{printf "%s: %.1f GiB\n", FILENAME, $1/1073741824}' \
  /sys/class/drm/card*/device/mem_info_{vram,gtt}_total
df -h "$HOME"
powerprofilesctl get 2>/dev/null || true
ss -ltnH 'sport = :8080 or sport = :8081'
```

### Key host notes

- **Device ACLs vs. `render`/`video` groups:** On Ubuntu Desktop, the active local GUI session often grants `rw` access to `/dev/kfd` and `/dev/dri/renderD128` via `systemd-logind` (`uaccess`) ACLs even when your user is not in `render` or `video`. If you plan to run Loom or `llama-server` over SSH, headless, or via a lingering `systemd --user` service, add yourself to both groups even if the interactive check above succeeds:

  ```bash
  sudo usermod -aG render,video "$USER"
  # Log out and back in (or reboot) for group membership to take effect
  ```

- **Memory / GTT aperture:** On a 128 GB Ubuntu host with default BIOS UMA (`0.5 GiB` VRAM), Linux TTM defaults to 50% of system RAM for GTT (`~61.3 GiB`, exposed in `/sys/module/ttm/parameters/pages_limit`). That easily fits a single Inkling 2 Mini instance (`~20.5 GiB` on Vulkan at 32K context). You only need to raise `ttm.pages_limit` / `amdgpu.gttsize` (or BIOS UMA carveout) if you push toward very long contexts (262K–1M) or load multiple large models concurrently.
- **Vulkan check:** Mesa RADV (`mesa-vulkan-drivers`, `/usr/share/vulkan/icd.d/radeon_icd.json`) is typically preinstalled on Ubuntu Desktop. `vulkaninfo --summary` is optional and requires `vulkan-tools` (`sudo apt install vulkan-tools`); without it, `llama-server --list-devices` (Section 3) verifies `Radeon 8060S Graphics (RADV STRIX_HALO)` directly.
- **Disk space & hardlinks:** The Loom package downloads ~18.8 GB of model, drafter, projector, and tokenizer assets. With `--materialize=auto` on the same filesystem, `loom-fetch` hardlinks the GGUF blobs between `~/.cache/huggingface/hub/blobs` and `~/models/Inkling-2-Mini/parameter/` so it does **not** double disk usage — note that reclaiming the 18 GB later requires removing both the model directory and the matching Hugging Face cache entries (or passing `--materialize=move`).
- **Port selection:** Both Loom and `llama-server` default to `127.0.0.1:8080`, which matches local clients such as `pi` / `acp-proxy` (while `opencrm-dev serve` uses `:18080`/`:18081` and `jev-local` uses `:8081`). Always check `ss -ltnH 'sport = :8080 or sport = :8081'` before starting — if you reconfigure to `:8081` to avoid a conflict, it may collide with `jev-local`.

---

## 2. Recommended Path: Loom (hrx-loom)

The publisher provides a Strix Halo preset for Unsloth's UD-Q4_K_XL GGUF with DFlash speculative decoding and vision support.

### Install

If `uv` is installed (recommended — works on Ubuntu 24.04/26.04 and Python 3.14 without `python3-venv`, `ensurepip`, or `sudo`):

```bash
uv venv ~/.local/share/inkling-loom/venv
uv pip install --python ~/.local/share/inkling-loom/venv/bin/python "hrx-loom[hf]==0.2.0"
```

Standard `venv` fallback (requires `python3-venv` and either `python3-pip` or `ensurepip`):

```bash
python3 -m venv ~/.local/share/inkling-loom/venv
~/.local/share/inkling-loom/venv/bin/python -m pip install "hrx-loom[hf]==0.2.0"
```

> **Ubuntu 26.04 note:** Python 3.14 ships without `ensurepip` by default, so `python3 -m venv` creates a venv **without pip** unless `python3-pip` is installed separately (`sudo apt install python3-pip`). Use `uv` (recommended) or the `get-pip.py` fallback below.

If `uv` is unavailable and Ubuntu lacks `ensurepip` without APT access, create the environment with `python3 -m venv --without-pip ~/.local/share/inkling-loom/venv` and bootstrap pip with [get-pip.py](https://pip.pypa.io/en/stable/installation/#get-pip-py).

Optional: expose `loom-fetch` and `loom-serve` on `~/.local/bin` (already on `PATH` on Ubuntu):

```bash
mkdir -p ~/.local/bin
ln -sf ~/.local/share/inkling-loom/venv/bin/loom-fetch ~/.local/bin/loom-fetch
ln -sf ~/.local/share/inkling-loom/venv/bin/loom-serve ~/.local/bin/loom-serve
```

### Fetch the model

```bash
mkdir -p ~/models
cd ~/models

~/.local/share/inkling-loom/venv/bin/loom-fetch \
  huggingface:loom-fun/unsloth-Inkling-2-Mini-loom:chat \
  --materialize=auto --output=Inkling-2-Mini

# Verify hardlinks saved disk space (link count > 1 means shared with HF cache)
stat -c '%h links: %n' ~/models/Inkling-2-Mini/parameter/*.gguf
```

### Start Loom

```bash
~/.local/share/inkling-loom/venv/bin/loom-serve \
  --model="$HOME/models/Inkling-2-Mini" \
  '--device=amdgpu://?completion_spin_ns=4294967295' \
  --pool_backing=fixed --parameter_mode=preload \
  --listen=127.0.0.1:8080
```

- **`completion_spin_ns` tradeoff:** The value `4294967295` (max uint32) keeps the GPU busy-waiting continuously between decode steps — lowest per-token latency but highest sustained heat. This is ideal for single-client, short-reply workloads. For sustained generation with multiple clients, reduce to `500000000` (500 ms) so the GPU can briefly idle between tokens, lowering thermal pressure and allowing DPM to boost higher on the next token.
- **Backing mode tradeoff:** `--pool_backing=fixed` eagerly retains GPU memory (~32 GiB GTT on this preset) for lowest latency. Stop Loom before starting `llama-server` so both port and GTT memory are released. If you want Loom to share GPU memory more cooperatively with other workloads, use `--pool_backing=elastic --memory_bytes=40gib`.
- **Dashboard & API:** Open `http://127.0.0.1:8080/dashboard/` in a browser. The OpenAI-compatible base URL is `http://127.0.0.1:8080/v1`.

### Verify readiness, text, and vision

In a second terminal, check readiness (`/v1/status` reports model preparation progress; `/readyz` and `/v1/models` confirm availability), then run bounded text and vision checks:

```bash
curl --fail-with-body http://127.0.0.1:8080/v1/status
curl --fail-with-body http://127.0.0.1:8080/v1/models

# Text smoke test
curl --fail-with-body --max-time 120 http://127.0.0.1:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"Inkling-2-Mini","messages":[{"role":"user","content":"Reply with exactly: Inkling is ready"}],"temperature":0,"max_tokens":128,"reasoning_effort":"none"}'

# Vision smoke test (1x1 solid red PNG)
curl --fail-with-body --max-time 120 http://127.0.0.1:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"Inkling-2-Mini","messages":[{"role":"user","content":[{"type":"text","text":"Reply with one word naming the color of this image."},{"type":"image_url","image_url":{"url":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC"}}]}],"temperature":0,"max_tokens":128,"reasoning_effort":"none"}'
```

- **Thinking control:** Inkling 2 Mini controls thinking via `reasoning_effort` (`none` → `0`, `minimal` → `0.1`, `low` → `0.2`, `medium` → `0.7`, `high` → `0.9` [default], `xhigh`/`max` → `0.99`, or a numeric float `0`–`0.99`). Even at `"none"` (`0`), the model may still emit a brief `reasoning_content` block before `content`, so keeping `max_tokens` at `128` leaves room for the final answer. Using `reasoning_effort` works consistently across both Loom and `llama-server` (whereas `enable_thinking` is ignored by `llama-server`'s Jinja template).
- The preset enables DFlash speculative decoding and advertises a 262,144-token context. Loom runs without a ROCm SDK.

### Stop or run as a user service

Stop a foreground Loom server with `Ctrl+C` (or `pkill -f 'loom-serve.*Inkling-2-Mini'`).

For persistent background operation, verify the prerequisites first:

```bash
# User must be in render and video groups (uaccess ACLs don't apply to services)
id -nG | grep -qw render && id -nG | grep -qw video && echo "groups OK" || echo "MISSING: sudo usermod -aG render,video $USER  (then log out/in)"

# Enable lingering so systemd --user services survive logout
loginctl enable-linger "$USER"
```

Create `~/.config/systemd/user/inkling-loom.service`:

```ini
[Unit]
Description=Inkling 2 Mini (Loom)
After=network-online.target

[Service]
ExecStart=%h/.local/share/inkling-loom/venv/bin/loom-serve \
  --model=%h/models/Inkling-2-Mini \
  --device=amdgpu://?completion_spin_ns=4294967295 \
  --pool_backing=fixed --parameter_mode=preload \
  --listen=127.0.0.1:8080
Restart=on-failure
RestartSec=3

[Install]
WantedBy=default.target
```

Then manage it with:

```bash
systemctl --user daemon-reload
systemctl --user enable --now inkling-loom.service
systemctl --user status inkling-loom.service
```

---

## 3. Alternative: llama.cpp (Vulkan — simplest)

Build `b12649` (or newer) supports the `inkling2_loop` architecture and `inkling2v` vision projector.

### Download a prebuilt Vulkan binary

From the [llama.cpp releases](https://github.com/ggml-org/llama.cpp/releases):

```bash
mkdir -p ~/.local/opt ~/.local/bin
cd ~/.local/opt
wget https://github.com/ggml-org/llama.cpp/releases/download/b12649/llama-b12649-bin-ubuntu-vulkan-x64.tar.gz
tar -xzf llama-b12649-bin-ubuntu-vulkan-x64.tar.gz
ln -sf "$HOME/.local/opt/llama-b12649/llama-server" "$HOME/.local/bin/llama-server"
```

### Select model files

If you already fetched the Loom package, reuse its identical model and projector files (no extra download):

```bash
inkling_gguf="$HOME/models/Inkling-2-Mini/parameter/Inkling-2-Mini-UD-Q4_K_XL.gguf"
inkling_mmproj="$HOME/models/Inkling-2-Mini/parameter/mmproj-F16.gguf"
```

For a standalone Vulkan install without Loom, download the mainline quant and projector using `uvx` (or a dedicated venv with `huggingface_hub`):

```bash
inkling_dir="$HOME/models/inkling"
uvx --from huggingface_hub hf download unsloth/Inkling-2-Mini-GGUF \
  Mainline_Support/Inkling-2-Mini-UD-Q4_K_XL.gguf_file mmproj-F16.gguf \
  --local-dir "$inkling_dir"
inkling_gguf="$inkling_dir/Mainline_Support/Inkling-2-Mini-UD-Q4_K_XL.gguf"
inkling_mmproj="$inkling_dir/mmproj-F16.gguf"
mv "${inkling_gguf}_file" "$inkling_gguf"
```

### Launch server

Stop Loom first so port `8080` and GTT memory are free:

```bash
# If running foreground: Ctrl+C in its terminal
# If running as a service:
systemctl --user stop inkling-loom.service
# Or by process name:
pkill -f 'loom-serve.*Inkling-2-Mini'
```

Verify the Radeon 8060S device ID (`Vulkan0` on this host):

```bash
llama-server --list-devices
```

Optional env for better Vulkan throughput on some AMD setups:

```bash
export GGML_VK_ALLOW_GRAPHICS_QUEUE=1
```

Launch `llama-server`:

```bash
llama-server \
  --alias Inkling-2-Mini --device Vulkan0 \
  -m "$inkling_gguf" \
  --mmproj "$inkling_mmproj" \
  --jinja --no-reasoning-preserve -ngl 99 -c 32768 -np 1 \
  --temp 1.0 --top-p 1.0 --top-k 0 --min-p 0.0 \
  --host 127.0.0.1 --port 8080
```

- **Context sizing:** Because 40 of 48 layers use sliding-window attention (`1024`), `-c 32768` uses only ~`1.24 GiB` of KV cache (`~20.5 GiB` total device memory). On 61 GiB GTT you can raise `-c 65536` or `-c 131072` (and optionally `-ctk q8_0 -ctv q8_0`) comfortably.
- **Reasoning flags:** `--no-reasoning-preserve` prevents prior turns' `reasoning_content` from accumulating in multi-turn history. To request minimal thinking per call on `llama-server`, pass `"chat_template_kwargs":{"reasoning_effort":"none"}` (the GGUF Jinja template ignores `enable_thinking`).
- **Security:** Keep `--host 127.0.0.1` unless you also configure `--api-key`.

---

## 4. Alternative: llama.cpp (ROCm / HIP)

More setup, potentially faster on some workloads as `gfx1151` ROCm support matures.

- Use a recent `ubuntu-rocm` prebuilt from llama.cpp releases, or build from source targeting `gfx1151`.
- Ensure the user is in the `render` and `video` groups.
- Some stacks still need `HSA_OVERRIDE_GFX_VERSION=11.5.1`; test without it first.
- **Ubuntu version notes:** On Ubuntu 24.04, prefer the HWE kernel for newer ROCm support. Ubuntu 26.04 already ships kernel 7.0+ (no HWE needed), but prebuilt `ubuntu-rocm` tarballs may lag Ubuntu 26.04 / glibc 2.43 — Loom or Vulkan are lower-friction on 26.04 unless building from source or running ROCm inside a container.

Example launch (adjust binary and model paths):

```bash
./llama-server \
  --alias Inkling-2-Mini \
  -m "$HOME/models/Inkling-2-Mini/parameter/Inkling-2-Mini-UD-Q4_K_XL.gguf" \
  --mmproj "$HOME/models/Inkling-2-Mini/parameter/mmproj-F16.gguf" \
  -ngl 99 -c 32768 -np 1 --jinja --no-reasoning-preserve \
  --host 127.0.0.1 --port 8080
```

---

## 5. Performance Expectations (this hardware)

Check `powerprofilesctl get` (use `powerprofilesctl set performance` when plugged in for peak throughput).

### Measured throughput

| Backend | Config | Decode tok/s | TTFT | GPU temp | SCLK |
|---------|--------|-------------|------|----------|------|
| **Loom 0.2.0** (single client) | DPM auto, `-c 262144`, fixed pool | **~29 tok/s** (27–32, stable over 390 tokens) | 0.15 s | 65°C | 2843 MHz |
| **Loom 0.2.0** (3 concurrent clients) | Same config, 2× `pi` + Chrome active | ~7.6 tok/s | 5–10 s | 90°C | 2449–2555 MHz |
| **llama.cpp b12649** (Vulkan RADV) | `performance` profile, `-c 32768` | ~27.2 tok/s | — | — | — |
| **Published Loom card** (historical) | DFlash programs differ from current download | ~54 tok/s | — | — | — |

- **Published Loom package card** ([unsloth-Inkling-2-Mini-loom](https://huggingface.co/loom-fun/unsloth-Inkling-2-Mini-loom#performance-vs-llamacpp)): reports ~54 tok/s short-prompt decode and 1,992 tok/s for 1K-token prefill with DFlash, versus a historical llama.cpp ROCm comparison of 15.7 tok/s decode and 293 tok/s prefill. The publisher notes that its benchmarked Loom programs differ from the current download; treat the 54 tok/s as a historical figure. The measured ~29 tok/s single-client Loom decode on this host is consistent with the DFlash program change.

### Request contention (the #1 performance variable)

Throughput scales **inversely** with the number of concurrent generation clients. The autoregressive decode is fundamentally serial on a single GPU — multiple active requests time-share the GPU, so each sees proportionally lower tok/s. With 3 concurrent clients, per-request decode dropped from 29 to 7.6 tok/s (a **4× reduction**), TTFT inflated from 0.15s to 5–10s from queue waiting, and the GPU hit 90°C from sustained multi-request load (causing secondary DPM throttling to ~2500 MHz). The thermals were a symptom of contention, not an independent bottleneck.

For representative benchmarks, ensure **no other clients** have active requests (`pi` sessions, browser dashboard, other HTTP clients on `:8080`). Check active requests:

```bash
curl -sS http://127.0.0.1:8080/v1/status | python3 -c 'import sys,json; d=json.loads(sys.stdin.read()); print("active_requests:", d["data"][0]["loom"]["status"]["active_requests"])'
```

### Thermal and GPU monitoring

With a single client, SCLK should reach 2800+ MHz and temperature should stay below 80°C. If you see 90°C+ and SCLK stuck at ~2500 MHz, check for other active clients first — that is the most common cause.

```bash
# Monitor GPU clocks, temperature, and busy % during inference
watch -n1 'paste \
  <(cat /sys/class/drm/card1/device/hwmon/hwmon*/temp1_input | awk "{printf \"%.0f°C\", \$1/1000}") \
  <(grep "*" /sys/class/drm/card1/device/pp_dpm_sclk) \
  <(cat /sys/class/drm/card1/device/gpu_busy_percent | xargs -I{} echo "{}% busy")'
```

### Optional OS tuning

These have modest impact compared to managing request contention, but can help at the margin:

- **DPM force high** (requires `sudo`): `echo high | sudo tee /sys/class/drm/card1/device/power_dpm_force_performance_level` locks SCLK to 2900 MHz. With a single client, DPM auto already reaches 2843 MHz, so the gain is ~2%. Under multi-client load the gap is larger (~10%).
- **Swappiness:** `sudo sysctl vm.swappiness=10` reduces swap pressure when the model occupies ~32 GiB of GTT (default is 60).
- **Power profile:** `powerprofilesctl set performance` (already recommended above).

---

## 6. Quick Checklist

- [ ] `/dev/kfd` and `/dev/dri/renderD*` accessible (and user in `render,video` if running over SSH / `systemd --user`)
- [ ] `loginctl enable-linger "$USER"` if using `systemd --user` service
- [ ] `uv` or `venv` ready (on Ubuntu 26.04: `uv` recommended, or install `python3-pip` for venv+pip)
- [ ] Target port (`8080`) free (`ss -ltnH 'sport = :8080 or sport = :8081'`)
- [ ] Mesa RADV sees Radeon 8060S (`llama-server --list-devices` or optional `vulkaninfo --summary`)
- [ ] Loom fetch + serve works and `/v1/status` reports ready (~18.8 GB hardlinked with HF cache — verify with `stat -c '%h links: %n' ~/models/Inkling-2-Mini/parameter/*.gguf`)
- [ ] `/v1/models`, text `/v1/chat/completions`, and vision smoke test return expected responses
- [ ] Single-client decode reaches ~27–32 tok/s with GPU <80°C and SCLK >2800 MHz (if not, check for other active clients)
- [ ] Optional: stop Loom and benchmark Vulkan (`llama-server`) or ROCm for comparison

---

## References

- Official model: https://huggingface.co/thinkingmachines/Inkling-2-Mini
- Unsloth GGUF: https://huggingface.co/unsloth/Inkling-2-Mini-GGUF
- Loom package: https://huggingface.co/loom-fun/unsloth-Inkling-2-Mini-loom
- Loom docs: https://rocm.github.io/hrx-system/loom/
- llama.cpp releases: https://github.com/ggml-org/llama.cpp/releases
