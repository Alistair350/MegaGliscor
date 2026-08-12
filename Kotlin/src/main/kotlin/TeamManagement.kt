object TeamManagement {
    private val STAT_ORDER = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")

    private data class NameInfo(
        val nickname: String,
        val species: String,
        val item: String,
        val gender: String,
    )

    private data class PokemonSet(
        val nickname: String,
        val species: String,
        val item: String,
        val ability: String,
        val moves: List<String>,
        val nature: String,
        val evs: Map<String, Int>,
        val gender: String,
        val ivs: Map<String, Int>,
        val teraType: String,
    )

    /**
     * Converts a pokepaste string to a packed string format.
     *
     * @param content the string of the team to be converted
     * @return the final packed string
     */
    fun pokepasteToPackedString(content: String): String {
        val blocks =
            content
                .replace("\r\n", "\n")
                .split(Regex("\n[ \t]*\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        return (
            blocks
                .joinToString("") { block ->
                    parseBlock(block).toPackedString()
                }
        ).removeSuffix("]")
    }

    fun pokepasteToPackedStringFromResource(resourcePath: String): String {
        val path = resourcePath.removePrefix("/")
        val stream =
            this::class.java.getResourceAsStream("/$path")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream(path)
                ?: error("Resource not found: $resourcePath")
        val content = stream.bufferedReader().use { it.readText() }
        return pokepasteToPackedString(content)
    }

    private fun parseBlock(block: String): PokemonSet {
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val nameInfo = parseFirstLine(lines.firstOrNull() ?: "")
        val details = parseDetails(lines.drop(1))
        return PokemonSet(
            nickname = nameInfo.nickname,
            species = nameInfo.species,
            item = nameInfo.item,
            gender = nameInfo.gender,
            ability = details.ability,
            nature = details.nature,
            teraType = details.teraType,
            evs = details.evs,
            ivs = details.ivs,
            moves = details.moves,
        )
    }

    private fun parseFirstLine(line: String): NameInfo {
        val atIndex = line.lastIndexOf(" @ ")
        val namePart = if (atIndex >= 0) line.substring(0, atIndex).trim() else line
        val item = if (atIndex >= 0) normalizeId(line.substring(atIndex + 3)) else ""

        val (nameWithoutGender, gender) = extractGender(namePart)
        val (nickname, species) = extractNicknameAndSpecies(nameWithoutGender)

        return NameInfo(nickname, species, item, gender)
    }

    private fun extractGender(namePart: String): Pair<String, String> {
        val match = Regex("""(.+) \((M|F)\)$""").find(namePart)
        return if (match != null) {
            match.groupValues[1].trim() to match.groupValues[2]
        } else {
            namePart to ""
        }
    }

    private fun extractNicknameAndSpecies(name: String): Pair<String, String> {
        val match = Regex("""(.+) \((.+)\)$""").find(name)
        return if (match != null) {
            val nickname = match.groupValues[1].trim()
            val species = match.groupValues[2].trim()
            if (species.equals(nickname, ignoreCase = true)) nickname to "" else nickname to species
        } else {
            name to ""
        }
    }

    private data class Details(
        val ability: String = "",
        val nature: String = "",
        val teraType: String = "",
        val evs: Map<String, Int> = STAT_ORDER.associateWith { 0 },
        val ivs: Map<String, Int> = STAT_ORDER.associateWith { 31 },
        val moves: List<String> = emptyList(),
    )

    private fun parseDetails(lines: List<String>): Details {
        val evs = STAT_ORDER.associateWith { 0 }.toMutableMap()
        val ivs = STAT_ORDER.associateWith { 31 }.toMutableMap()
        val moves = mutableListOf<String>()
        var ability = ""
        var nature = ""
        var teraType = ""

        for (line in lines) {
            when {
                line.startsWith("Ability: ") -> ability = normalizeId(line.removePrefix("Ability: "))
                line.startsWith("Tera Type: ") -> teraType = normalizeId(line.removePrefix("Tera Type: "))
                line.startsWith("EVs: ") -> parseStats(line.removePrefix("EVs: "), evs, 0)
                line.startsWith("IVs: ") -> parseStats(line.removePrefix("IVs: "), ivs, 31)
                line.endsWith(" Nature") -> nature = line.removeSuffix(" Nature").trim()
                line.startsWith("- ") -> moves.add(normalizeMove(line.removePrefix("- ")))
            }
        }

        return Details(ability, nature, teraType, evs, ivs, moves)
    }

    private fun parseStats(
        statText: String,
        target: MutableMap<String, Int>,
        defaultValue: Int,
    ) {
        statText.split("/").map { it.trim() }.forEach { stat ->
            val spaceIndex = stat.indexOf(' ')
            if (spaceIndex > 0) {
                val value = stat.substring(0, spaceIndex).trim().toIntOrNull() ?: defaultValue
                val statName = stat.substring(spaceIndex + 1).trim()
                if (statName in STAT_ORDER) {
                    target[statName] = value
                }
            }
        }
    }

    /** Lowercases and strips spaces/hyphens/apostrophes — Showdown ID format */
    private fun normalizeId(text: String): String =
        text
            .trim()
            .lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("'", "")

    private fun normalizeMove(move: String): String =
        move
            .trim()
            .lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("'", "")

    private fun PokemonSet.toPackedString(): String {
        val evString = formatStats(evs, defaultValue = 0, omitValue = 0)
        val ivString = formatStats(ivs, defaultValue = 31, omitValue = 31)
        val movesString = moves.joinToString(",")
        val lastField = if (teraType.isNotEmpty()) ",,,,,$teraType" else ""

        val parts =
            listOf(
                nickname,
                species,
                item,
                ability,
                movesString,
                nature,
                evString,
                gender,
                ivString,
                "",
                "",
                lastField,
            )
        return parts.joinToString("|") + "]"
    }

    private fun formatStats(
        stats: Map<String, Int>,
        defaultValue: Int,
        omitValue: Int,
    ): String {
        val values = STAT_ORDER.map { stats[it] ?: defaultValue }
        return if (values.all { it == omitValue }) {
            ""
        } else {
            values.joinToString(",") { if (it == omitValue) "" else it.toString() }
        }
    }
}
