package states.fieldstates

import states.PokemonState

data class SideState(
    val team: MutableList<PokemonState>,
    var activeIndex: Int,
    var conditions: SideConditions = SideConditions(),
) {
    fun activePokemon(): PokemonState = team[activeIndex]
}
