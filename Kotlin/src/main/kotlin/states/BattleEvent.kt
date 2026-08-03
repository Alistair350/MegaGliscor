package states

import states.pokemonstates.Move
import states.pokemonstates.Species

sealed class BattleEvent {
    data class MoveUsed(
        val pokemon: Species,
        val move: Move,
    ) : BattleEvent()

    data class DamageTaken(
        val pokemon: Species,
        val amount: Int,
    ) : BattleEvent()

    data class Switched(
        val from: Species,
        val to: Species,
    ) : BattleEvent()
}
