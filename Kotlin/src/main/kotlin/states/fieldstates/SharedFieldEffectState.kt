package states.fieldstates

sealed class SharedFieldEffectState {
    data class TrickRoom(
        var turns: Int,
    ) : SharedFieldEffectState()

    data class MagicRoom(
        var turns: Int,
    ) : SharedFieldEffectState()

    data class WonderRoom(
        var turns: Int,
    ) : SharedFieldEffectState()

    data class Gravity(
        var turns: Int,
    ) : SharedFieldEffectState()
}
