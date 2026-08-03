package states.pokemonstates

sealed interface AbilityState

data class KnownAbility(
    val ability: Ability,
) : AbilityState

data object UnknownAbility : AbilityState

enum class Ability
