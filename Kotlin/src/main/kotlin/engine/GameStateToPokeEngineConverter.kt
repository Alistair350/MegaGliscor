package engine

import states.GameState
import states.PokemonState
import states.fieldstates.SideState

/**
 * Converts the existing GameState structure into poke-engine's serialized state string format.
 *
 * Format: side1/side2/weather/terrain/trick_room/team_preview
 *
 * Each side contains 6 Pokémon, then various side state fields.
 * Each Pokémon contains full battle stats, boosts, moves, etc.
 */
class GameStateToPokeEngineConverter {
    fun convert(state: GameState): String {
        val side1Str = convertSide(state.mySide)
        val side2Str = convertSide(state.opponentSide)

        val weatherStr = "${state.field.weather.type.name.lowercase()};${state.field.weather.turnsRemaining}"
        val terrainStr = "${state.field.terrain.type.name.lowercase()};${state.field.terrain.turnsRemaining}"
        val trickRoomStr = "false;0" // Not exposed in shared field state yet

        return "$side1Str/$side2Str/$weatherStr/$terrainStr/$trickRoomStr/false"
    }

    /**
     * Converts a SideState to poke-engine format.
     *
     * Format per side:
     * p0=p1=p2=p3=p4=p5=active_index=side_conditions=wish0=wish1=force_switch=saved_move=baton_pass=shed_tail=force_trapped=last_used_move=slow_uturn/
     */
    private fun convertSide(side: SideState): String {
        // Serialize 6 Pokémon slots
        val allPokemon = side.team.toMutableList()

        // Pad to 6 slots
        while (allPokemon.size < 6) {
            allPokemon.add(createEmptyPokemon())
        }

        val pokemonStrs = allPokemon.take(6).map { convertPokemon(it) }

        val activeIndex = side.activeIndex

        // Side conditions (19 fields, all zeros for now)
        val sideConditionsStr = (0 until 19).joinToString(";") { "0" }

        // Wishes (2 fields: wish_turns, wish_hp)
        val wisheStr = "0=0="

        // Status fields
        val forceSwitch = "false"
        val savedMove = "NONE"
        val baton = "false"
        val shedTail = "false"
        val forceTrapped = "false"
        val lastUsedMove = "switch:0"
        val slowUturn = "false"

        val statusStr = "$forceSwitch=$savedMove=$baton=$shedTail=$forceTrapped=$lastUsedMove=$slowUturn"

        return pokemonStrs.joinToString("=") + "=$activeIndex=$sideConditionsStr=$wisheStr$statusStr"
    }

    /**
     * Converts a single PokemonState to poke-engine's Pokémon string.
     *
     * Format:
     * name,level,type1,type2,base_type1,base_type2,hp,maxhp,ability,base_ability,item,nature,evs,
     * atk,def,spa,spd,spe,status,rest_turns,sleep_turns,weight_kg,
     * m0,m1,m2,m3,terastallized,tera_type
     */
    private fun convertPokemon(pokemon: PokemonState): String {
        // For now, use placeholder values we don't have from GameState
        val type1 = "Normal" // Placeholder - would need Pokédex
        val type2 = "Typeless"
        val baseAbility = "NONE" // We don't have base ability
        val nature = "SERIOUS"
        val evs = "" // Use poke-engine default
        val weight = "50.0"
        val restTurns = 0
        val sleepTurns = 0

        // Convert status to poke-engine format
        val statusStr = when (pokemon.status.status) {
            states.pokemonstates.Status.BURN -> "Burn"
            states.pokemonstates.Status.POISON -> "Poison"
            states.pokemonstates.Status.TOXIC -> "Toxic"
            states.pokemonstates.Status.PARALYSIS -> "Paralysis"
            states.pokemonstates.Status.SLEEP -> "Sleep"
            states.pokemonstates.Status.FREEZE -> "Freeze"
            states.pokemonstates.Status.REST -> "Rest"
            states.pokemonstates.Status.NONE -> "None"
        }

        // Moves (pad to 4, use placeholders)
        val moveStrs = (0 until 4).map { "NONE;false;0" }

        val speciesName = pokemon.species.name.lowercase()

        return buildString {
            append("$speciesName,${50},$type1,$type2,$type1,$type2,")
            append("${pokemon.hp},${pokemon.maxHp},$baseAbility,$baseAbility,NONE,$nature,$evs,")
            append("${pokemon.stats.statBoosts.get(states.pokemonstates.Stat.ATTACK)},")
            append("${pokemon.stats.statBoosts.get(states.pokemonstates.Stat.DEFENSE)},")
            append("${pokemon.stats.statBoosts.get(states.pokemonstates.Stat.SP_ATTACK)},")
            append("${pokemon.stats.statBoosts.get(states.pokemonstates.Stat.SP_DEFENSE)},")
            append("${pokemon.stats.statBoosts.get(states.pokemonstates.Stat.SPEED)},")
            append("$statusStr,$restTurns,$sleepTurns,$weight,")
            append(moveStrs.joinToString(",") + ",")
            append("false,Normal")
        }
    }

    private fun createEmptyPokemon(): PokemonState {
        return PokemonState(
            species = states.pokemonstates.Species.UNKNOWN,
            hp = 0,
            maxHp = 1,
            stats = states.pokemonstates.StatState(null, null, states.pokemonstates.StatBoosts()),
            status = states.pokemonstates.StatusState(states.pokemonstates.Status.NONE, null),
            moves = mutableListOf(),
            item = states.pokemonstates.UnknownItem as states.pokemonstates.ItemState,
            ability = states.pokemonstates.UnknownAbility as states.pokemonstates.AbilityState
        )
    }
}
