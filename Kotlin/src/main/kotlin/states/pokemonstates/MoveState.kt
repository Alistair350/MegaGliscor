package states.pokemonstates

sealed class MoveState {

    data class Known(
        val move: Move,
        var currentPP: Int,
        val maxPP: Int,
        var disabled: Boolean = false
    ) : MoveState()

    // KnownByName: allows storing moves by raw name (used during parsing from PS)
    data class KnownByName(
        val name: String,
        var currentPP: Int,
        val maxPP: Int,
        var disabled: Boolean = false,
    ) : MoveState()

    data object Unknown : MoveState()
}