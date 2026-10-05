# Win-FG engine provenance

Upstream: https://github.com/The412Banner/win-fg
Pinned revision: `fbc69ed6c5bf16b824d0a29bd8b171675101f9dd`.

The MIT engine and its embedded compute shaders are consumed directly. Vulkan layer,
training capture and compositor implementations are not imported into Xenia.
Preserve `LICENSE`, `THIRD-PARTY.md` and `NOTICE_FIDELITYFX_OPTICALFLOW.md`.

Runtime glue is XenDroid/Xenia-specific. Compiling the engine does not establish
hardware performance or display scanout validation.
