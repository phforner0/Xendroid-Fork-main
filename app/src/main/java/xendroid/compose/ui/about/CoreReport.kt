package xendroid.compose.ui.about

/**
 * The core's device report ([xendroid.compose.Emulator.simple_device_info]) read for people. The
 * core writes a CPU block ("CPU [cortex-x4*1+cortex-a720*3+cortex-a720*4(armv9.2-a)]:" and one
 * "    * feature" line per CPU feature) and a GPU block ("GPU [name(Vulkan: 1.3.284)]:" and one line
 * per Vulkan extension): hundreds of lines, which About shows summed up, the lists on request.
 */
data class CoreReport(
    /** The CPU's cores by kind, in the core's order: ("Cortex-X4", 1), ("Cortex-A720", 7). */
    val cores: List<Pair<String, Int>> = emptyList(),
    val isa: String? = null,
    val cpuFeatures: List<String> = emptyList(),
    val gpu: String? = null,
    val vulkan: String? = null,
    val extensions: List<String> = emptyList(),
    /** Anything else the core said (a failure to read the GPU, for one). */
    val notes: List<String> = emptyList(),
) {
    /** "Cortex-X4 ×1 + Cortex-A720 ×7"; null when the core named no cores. */
    val coresLine: String? get() = cores.takeIf { it.isNotEmpty() }?.joinToString(" + ") { (name, n) -> "$name ×$n" }

    /** CPU features worth naming first, when the CPU has them (what emulation leans on). */
    val notableFeatures: List<String> get() = NOTABLE.filter { it in cpuFeatures }

    companion object {
        private val CPU = Regex("""^CPU \[(.*)]:$""")
        private val GPU = Regex("""^GPU \[(.*)\(Vulkan: ([0-9.]+)\)]:$""")
        private val ITEM = Regex("""^\s+\* (.+)$""")
        private val NOTABLE = listOf("sve2", "sve", "i8mm", "bf16", "atomics", "lse128", "crc32", "aes", "sha3", "fp16", "dotprod")

        fun parse(text: String?): CoreReport? {
            if (text.isNullOrBlank()) return null
            var report = CoreReport()
            var list: MutableList<String>? = null
            val cpuFeatures = mutableListOf<String>()
            val extensions = mutableListOf<String>()
            val notes = mutableListOf<String>()
            for (raw in text.lines()) {
                val line = raw.trimEnd()
                if (line.isBlank()) continue
                val item = ITEM.find(line)
                if (item != null && list != null) { list += item.groupValues[1].trim(); continue }
                val cpu = CPU.find(line)
                val gpu = GPU.find(line)
                when {
                    gpu != null -> { report = report.copy(gpu = gpu.groupValues[1].trim(), vulkan = gpu.groupValues[2]); list = extensions }
                    cpu != null -> { report = report.copy(cores = cores(cpu.groupValues[1]), isa = isa(cpu.groupValues[1])); list = cpuFeatures }
                    else -> { notes += line.trim(); list = null }
                }
            }
            return report.copy(cpuFeatures = cpuFeatures, extensions = extensions, notes = notes)
        }

        /** "cortex-x4*1+cortex-a720*3+cortex-a720*4(armv9.2-a)": cores of one name (variants
         *  apart in the core's count) added up, named as their makers write them. */
        private fun cores(spec: String): List<Pair<String, Int>> {
            val counts = LinkedHashMap<String, Int>()
            spec.substringBeforeLast('(').split('+').forEach { part ->
                val name = part.substringBefore('*').trim()
                val n = part.substringAfter('*', "1").trim().toIntOrNull() ?: 1
                if (name.isNotEmpty()) counts[pretty(name)] = (counts[pretty(name)] ?: 0) + n
            }
            return counts.toList()
        }

        private fun isa(spec: String): String? =
            spec.takeIf { it.endsWith(")") && '(' in it }?.substringAfterLast('(')?.removeSuffix(")")?.trim()?.ifEmpty { null }

        /** "cortex-a720" → "Cortex-A720"; names the core does not know stay as they are. */
        private fun pretty(name: String): String =
            name.split('-').joinToString("-") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }
}
