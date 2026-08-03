package states.fieldstates

enum class TerrainType {
    NONE,
    ELECTRIC,
    GRASSY,
    PSYCHIC,
    MISTY,
}

data class TerrainState(
    var type: TerrainType = TerrainType.NONE,
    var turnsRemaining: Int = 0,
)
