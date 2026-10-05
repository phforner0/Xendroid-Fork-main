package xendroid.compose.patches

/** A memory write of one patch: [size] bytes at [address]. */
data class PatchWrite(val address: Long, val size: Int)

/** One `[[patch]]` as the emulator will read it. */
data class PatchSpec(val name: String, val enabled: Boolean, val writes: List<PatchWrite>)

data class PatchFileSpec(val titleId: String, val titleName: String, val hashes: List<String>, val patches: List<PatchSpec>)

/** Why a patch file cannot go to the emulator, with the line (1-based) that says so. */
class PatchFileException(val line: Int, reason: String) : IllegalArgumentException("Line $line: $reason")

/** Two patches that are on together and change the same memory; which one wins is not defined. */
data class PatchConflict(val first: String, val second: String, val address: Long)

/**
 * L11: reads a `.patch.toml` the way the emulator's PatchDB does (patch_db.cc) and refuses what
 * would make it misbehave. PatchDB dereferences without checking: no `[[patch]]` table, a
 * write without an integer `address`, a `value` missing or of the wrong kind, a `name` or
 * `is_enabled` of the wrong type all crash the core at launch. So a file the user brings is
 * accepted only when every one of those is right. The grammar is the TOML subset the patch
 * catalog uses: comments, `[[patch]]` and `[[patch.<type>]]` headers, bare keys, basic and
 * literal strings (also multi-line), integers (decimal, 0x, 0o, 0b), floats, booleans and
 * arrays of strings (also over several lines).
 */
object PatchFileCheck {
    /** The write types PatchDB knows, with their fixed sizes (0: from the value). */
    val WRITE_TYPES = mapOf("be8" to 1, "be16" to 2, "be32" to 4, "be64" to 8, "f32" to 4, "f64" to 8,
        "string" to 0, "u16string" to 0, "array" to 0)

    private sealed interface Value {
        data class Str(val text: String) : Value
        data class Num(val value: Long) : Value
        data class Real(val value: Double) : Value
        data class Bool(val value: Boolean) : Value
        data class Strings(val items: List<String>) : Value
    }

    private class Table(val line: Int) { val values = LinkedHashMap<String, Pair<Int, Value>>() }

    private class Patch(val table: Table) { val writes = ArrayList<Pair<String, Table>>() }

    fun read(text: String): PatchFileSpec {
        val top = Table(1)
        val patches = ArrayList<Patch>()
        var current: Table = top
        val lines = text.replace("\r\n", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            val number = i + 1
            val line = lines[i].trim()
            i++
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[")) {
                val header = Regex("^\\[\\[\\s*([A-Za-z0-9_.]+)\\s*]]\\s*(#.*)?$").matchEntire(line)
                    ?: throw PatchFileException(number, "only [[patch]] and [[patch.<type>]] headers are used in patch files")
                val name = header.groupValues[1]
                current = when {
                    name == "patch" -> Table(number).also { patches += Patch(it) }
                    name.startsWith("patch.") -> {
                        val type = name.removePrefix("patch.")
                        if (type !in WRITE_TYPES) throw PatchFileException(number, "\"$type\" is not a write type the emulator knows")
                        val owner = patches.lastOrNull() ?: throw PatchFileException(number, "a write needs a [[patch]] above it")
                        Table(number).also { owner.writes += type to it }
                    }
                    else -> throw PatchFileException(number, "unknown table [[$name]]")
                }
                continue
            }
            val key = Regex("^([A-Za-z0-9_-]+)\\s*=").find(line)
                ?: throw PatchFileException(number, "expected key = value")
            val scanner = Scanner(lines, i - 1, lines[i - 1].indexOf('=') + 1)
            val value = scanner.value()
            scanner.endOfLine()
            i = scanner.row + 1
            val keyName = key.groupValues[1]
            if (current.values.containsKey(keyName)) throw PatchFileException(number, "\"$keyName\" is set twice")
            current.values[keyName] = number to value
        }
        return build(top, patches)
    }

    private fun build(top: Table, patches: List<Patch>): PatchFileSpec {
        fun string(table: Table, key: String, required: Boolean): String? {
            val (line, value) = table.values[key] ?: return if (required) {
                throw PatchFileException(table.line, "\"$key\" is missing")
            } else null
            return (value as? Value.Str)?.text ?: throw PatchFileException(line, "\"$key\" must be text in quotes")
        }
        val titleName = string(top, "title_name", required = true)!!
        val titleId = string(top, "title_id", required = true)!!.uppercase()
        if (!titleId.matches(Regex("[0-9A-F]{8}"))) throw PatchFileException(top.values.getValue("title_id").first, "title_id must be 8 hex digits")
        val (hashLine, hashValue) = top.values["hash"] ?: throw PatchFileException(1, "\"hash\" is missing")
        val hashes = when (hashValue) {
            is Value.Str -> listOf(hashValue.text)
            is Value.Strings -> hashValue.items
            else -> throw PatchFileException(hashLine, "\"hash\" must be text or a list of texts")
        }
        if (hashes.isEmpty() || hashes.any { !it.matches(Regex("[0-9A-Fa-f]{1,16}")) }) {
            throw PatchFileException(hashLine, "hash must list the game executable's hash (up to 16 hex digits each)")
        }
        if (patches.isEmpty()) throw PatchFileException(1, "there is no [[patch]]")
        val specs = patches.map { patch ->
            val name = string(patch.table, "name", required = true)!!
            string(patch.table, "desc", required = false)
            string(patch.table, "author", required = false)
            val enabled = patch.table.values["is_enabled"]?.let { (line, value) ->
                (value as? Value.Bool)?.value ?: throw PatchFileException(line, "is_enabled must be true or false")
            } ?: false
            PatchSpec(name, enabled, patch.writes.map { (type, table) -> write(type, table) })
        }
        return PatchFileSpec(titleId, titleName, hashes.map { it.uppercase() }, specs)
    }

    private fun write(type: String, table: Table): PatchWrite {
        val (addressLine, address) = table.values["address"] ?: throw PatchFileException(table.line, "the write has no address")
        // PatchDB keeps the low 32 bits (static_cast<uint32_t>); one bundled file relies on it.
        val at = (address as? Value.Num)?.value?.and(0xFFFFFFFFL)
            ?: throw PatchFileException(addressLine, "address must be a whole number (like 0x82000000)")
        val (valueLine, value) = table.values["value"] ?: throw PatchFileException(table.line, "the write has no value")
        val size = when (type) {
            "be8", "be16", "be32", "be64" -> if (value is Value.Num) WRITE_TYPES.getValue(type)
                else throw PatchFileException(valueLine, "a $type value must be a whole number")
            "f32", "f64" -> if (value is Value.Num || value is Value.Real) WRITE_TYPES.getValue(type)
                else throw PatchFileException(valueLine, "a $type value must be a number")
            "string" -> (value as? Value.Str)?.text?.toByteArray(Charsets.UTF_8)?.size
                ?: throw PatchFileException(valueLine, "a string value must be text in quotes")
            "u16string" -> (value as? Value.Str)?.text?.length?.times(2)
                ?: throw PatchFileException(valueLine, "a u16string value must be text in quotes")
            else -> {
                val hex = (value as? Value.Str)?.text?.removePrefix("0x")
                    ?: throw PatchFileException(valueLine, "an array value must be hex digits in quotes")
                if (!hex.matches(Regex("[0-9A-Fa-f]+"))) throw PatchFileException(valueLine, "an array value must be hex digits")
                (hex.length + 1) / 2
            }
        }
        if (size <= 0) throw PatchFileException(valueLine, "the value writes nothing")
        return PatchWrite(at, size)
    }

    /**
     * Patches that are on together and write overlapping memory. Files whose hash lists do not
     * meet target different versions of the executable, so only one of them ever applies.
     */
    fun conflicts(files: List<Pair<String, PatchFileSpec>>): List<PatchConflict> {
        data class On(val label: String, val hashes: Set<String>, val writes: List<PatchWrite>, val file: Int)
        val on = files.flatMapIndexed { index, (label, spec) ->
            spec.patches.filter { it.enabled && it.writes.isNotEmpty() }
                .map { On("${it.name} ($label)", spec.hashes.toSet(), it.writes, index) }
        }
        val found = ArrayList<PatchConflict>()
        for (a in on.indices) for (b in a + 1 until on.size) {
            val x = on[a]
            val y = on[b]
            if (x.file != y.file && x.hashes.isNotEmpty() && y.hashes.isNotEmpty() && x.hashes.intersect(y.hashes).isEmpty()) continue
            val overlap = x.writes.firstNotNullOfOrNull { w ->
                y.writes.firstOrNull { v -> w.address < v.address + v.size && v.address < w.address + w.size }
                    ?.let { maxOf(w.address, it.address) }
            } ?: continue
            found += PatchConflict(x.label, y.label, overlap)
        }
        return found
    }

    /** Reads one value starting at [row]/[col]; arrays and multi-line strings may span lines. */
    private class Scanner(private val lines: List<String>, var row: Int, private var col: Int) {
        private fun peek(): Char? = lines[row].getOrNull(col)
        private fun fail(reason: String): Nothing = throw PatchFileException(row + 1, reason)

        private fun skipSpaces(acrossLines: Boolean) {
            while (true) {
                while (peek() == ' ' || peek() == '\t') col++
                if (acrossLines && (peek() == null || peek() == '#') && row + 1 < lines.size) {
                    row++; col = 0; continue
                }
                return
            }
        }

        fun endOfLine() {
            skipSpaces(acrossLines = false)
            val rest = lines[row].substring(col.coerceAtMost(lines[row].length))
            if (rest.isNotEmpty() && !rest.startsWith("#")) fail("unexpected \"${rest.take(20)}\" after the value")
        }

        fun value(): Value {
            skipSpaces(acrossLines = false)
            val line = lines[row]
            return when {
                line.startsWith("\"\"\"", col) -> Value.Str(multiLine("\"\"\""))
                line.startsWith("'''", col) -> Value.Str(multiLine("'''"))
                peek() == '"' -> Value.Str(basic())
                peek() == '\'' -> Value.Str(literal())
                peek() == '[' -> Value.Strings(array())
                else -> scalar()
            }
        }

        private fun basic(): String {
            col++
            val out = StringBuilder()
            while (true) {
                val c = peek() ?: fail("text without its closing quote")
                col++
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> out.append(escape())
                    else -> out.append(c)
                }
            }
        }

        private fun escape(): String {
            val c = peek() ?: fail("text ends in a backslash")
            col++
            return when (c) {
                'n' -> "\n"; 't' -> "\t"; 'r' -> "\r"; 'b' -> "\b"; 'f' -> "\u000C"; '"' -> "\""; '\\' -> "\\"
                'u', 'U' -> {
                    val digits = if (c == 'u') 4 else 8
                    val hex = lines[row].substring(col, (col + digits).coerceAtMost(lines[row].length))
                    if (hex.length != digits || !hex.matches(Regex("[0-9A-Fa-f]+"))) fail("bad \\$c escape")
                    col += digits
                    val point = hex.toLong(16).takeIf { it <= Int.MAX_VALUE && Character.isValidCodePoint(it.toInt()) }
                        ?: fail("bad \\$c escape")
                    String(Character.toChars(point.toInt()))
                }
                else -> fail("unknown escape \\$c")
            }
        }

        private fun literal(): String {
            col++
            val end = lines[row].indexOf('\'', col)
            if (end < 0) fail("text without its closing quote")
            return lines[row].substring(col, end).also { col = end + 1 }
        }

        private fun multiLine(delimiter: String): String {
            col += 3
            val out = StringBuilder()
            while (true) {
                val end = lines[row].indexOf(delimiter, col)
                if (end >= 0) {
                    out.append(lines[row], col, end)
                    col = end + 3
                    return out.toString()
                }
                out.append(lines[row], col, lines[row].length).append('\n')
                if (row + 1 >= lines.size) fail("multi-line text without its closing $delimiter")
                row++; col = 0
            }
        }

        private fun array(): List<String> {
            col++
            val items = ArrayList<String>()
            while (true) {
                skipSpaces(acrossLines = true)
                when (peek()) {
                    ']' -> { col++; return items }
                    '"' -> items += basic()
                    '\'' -> items += literal()
                    else -> fail("a list here may only hold texts in quotes")
                }
                skipSpaces(acrossLines = true)
                when (peek()) {
                    ',' -> col++
                    ']' -> { col++; return items }
                    else -> fail("expected , or ] in the list")
                }
            }
        }

        private fun scalar(): Value {
            val token = Regex("^[^\\s#,\\]]+").find(lines[row].substring(col))?.value ?: fail("a value is missing")
            col += token.length
            val clean = token.replace("_", "")
            return when {
                token == "true" -> Value.Bool(true)
                token == "false" -> Value.Bool(false)
                // Like the emulator's parser: 64-bit signed, and no sign on hex/octal/binary.
                clean.matches(Regex("0x[0-9A-Fa-f]+")) -> Value.Num(clean.drop(2).toLongOrNull(16) ?: fail("the number is too large"))
                clean.matches(Regex("0o[0-7]+")) -> Value.Num(clean.drop(2).toLongOrNull(8) ?: fail("the number is too large"))
                clean.matches(Regex("0b[01]+")) -> Value.Num(clean.drop(2).toLongOrNull(2) ?: fail("the number is too large"))
                clean.matches(Regex("[+-]?[0-9]+")) -> Value.Num(clean.toLongOrNull() ?: fail("the number is too large"))
                clean.matches(Regex("[+-]?([0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?|inf|nan)")) ->
                    Value.Real(clean.replace("inf", "Infinity").replace("nan", "NaN").toDouble())
                else -> fail("\"${token.take(20)}\" is not a value TOML knows")
            }
        }
    }
}
