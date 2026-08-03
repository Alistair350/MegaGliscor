package states

import states.fieldstates.SharedFieldState
import states.fieldstates.SideState

data class GameState(
    val mySide: SideState,
    val opponentSide: SideState,
    var field: SharedFieldState,
    var turn: Int = 0,
    val history: MutableList<BattleEvent> = mutableListOf(),
)
