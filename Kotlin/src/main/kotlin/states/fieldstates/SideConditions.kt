package states.fieldstates

data class SideConditions(
    var stealthRock: Boolean = false,
    var spikes: Int = 0,
    var toxicSpikes: Int = 0,
    var stickyWeb: Boolean = false,
    var reflectTurns: Int = 0,
    var lightScreenTurns: Int = 0,
    var auroraVeilTurns: Int = 0,
    var tailwindTurns: Int = 0,
)
