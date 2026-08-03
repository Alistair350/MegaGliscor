package engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import states.GameState
import states.PokemonState
import states.fieldstates.SharedFieldState
import states.fieldstates.SideState
import states.fieldstates.WeatherState
import states.fieldstates.WeatherType
import states.fieldstates.TerrainState
import states.fieldstates.TerrainType
import states.pokemonstates.MoveState
import states.pokemonstates.StatusState
import states.pokemonstates.Status
import states.pokemonstates.StatState
import states.pokemonstates.StatBoosts
import states.pokemonstates.Stat
import states.pokemonstates.Species
import states.pokemonstates.AbilityState
import states.pokemonstates.UnknownAbility
import states.pokemonstates.ItemState
import states.pokemonstates.UnknownItem

/**
 * Converts Pokémon Showdown request JSON into the existing GameState structure.
 * Reuses PokemonState, SideState, SharedFieldState, etc.
 */
class PSToGameStateConverter {
    fun convert(requestJson: JsonObject): GameState {
        val side = requestJson["side"]?.jsonObject ?: JsonObject(emptyMap())
        val active = requestJson["active"]?.jsonArray ?: JsonArray(emptyList())
        val opponent = requestJson["opponent"]?.jsonObject ?: JsonObject(emptyMap())

        val ourSide = convertSide(side, active, true)
        val opponentSide = convertSide(opponent, JsonArray(emptyList()), false)

        val field = SharedFieldState(
            weather = WeatherState(WeatherType.NONE, 0),
            terrain = TerrainState(TerrainType.NONE, 0)
        )

        return GameState(
            mySide = ourSide,
            opponentSide = opponentSide,
            field = field,
            turn = 0,
            history = mutableListOf()
        )
    }

    /**
     * Converts a side (ours or opponent's) from PS format to SideState.
     */
    private fun convertSide(
        sideJson: JsonObject,
        activeJson: JsonArray,
        isOurSide: Boolean,
    ): SideState {
        val pokemonArray = sideJson["pokemon"]?.jsonArray ?: JsonArray(emptyList())

        if (pokemonArray.isEmpty()) {
            return SideState(
                team = mutableListOf(createEmptyPokemon()),
                activeIndex = 0
            )
        }

        val team = pokemonArray.mapIndexed { index, pkmnJson ->
            val activeMoveData = if (isOurSide && index == 0) {
                activeJson.getOrNull(0)?.jsonObject
            } else {
                null
            }
            convertPokemon(pkmnJson.jsonObject, activeMoveData)
        }.toMutableList()

        return SideState(
            team = team,
            activeIndex = 0
        )
    }

    /**
     * Converts a single Pokémon from PS format to PokemonState.
     */
    private fun convertPokemon(
        pkmnJson: JsonObject,
        activeMoveData: JsonObject?,
    ): PokemonState {
        val details = pkmnJson["details"]?.jsonPrimitive?.content ?: "unknown"
        val condition = pkmnJson["condition"]?.jsonPrimitive?.content ?: "0/0"
        val stats = pkmnJson["stats"]?.jsonObject ?: JsonObject(emptyMap())
        val moves = pkmnJson["moves"]?.jsonArray ?: JsonArray(emptyList())
        val ability = pkmnJson["ability"]?.jsonPrimitive?.content ?: "NONE"
        val item = pkmnJson["item"]?.jsonPrimitive?.content ?: "NONE"
        val statusStr = pkmnJson["status"]?.jsonPrimitive?.content ?: "None"

        // Parse details: "Species" or "Species, L50" or "Species, L50, M"
        val speciesName = details.split(",").getOrNull(0)?.trim()?.lowercase()?.replace(" ", "") ?: "unknown"
        val level = details.split(",").getOrNull(1)?.trim()?.removePrefix("L")?.toIntOrNull() ?: 50

        // Parse condition: "100/100" or "0/0"
        val conditionParts = condition.split("/")
        val hp = conditionParts.getOrNull(0)?.toIntOrNull() ?: 0
        val maxHp = conditionParts.getOrNull(1)?.toIntOrNull() ?: 100

        // Stats from PS (these are final stats)
        val atk = stats["atk"]?.jsonPrimitive?.content?.toIntOrNull() ?: 100
        val def = stats["def"]?.jsonPrimitive?.content?.toIntOrNull() ?: 100
        val spa = stats["spa"]?.jsonPrimitive?.content?.toIntOrNull() ?: 100
        val spd = stats["spd"]?.jsonPrimitive?.content?.toIntOrNull() ?: 100
        val spe = stats["spe"]?.jsonPrimitive?.content?.toIntOrNull() ?: 100

        // Convert moves - use Unknown placeholder for now
        val moveList = (0 until 4).map { MoveState.Unknown as MoveState }.toMutableList()

        // Extract boosts if this is our active Pokémon
        val boosts = if (activeMoveData != null) {
            val boostObj = activeMoveData["boosts"]?.jsonObject ?: JsonObject(emptyMap())
            StatBoosts(
                boosts = mutableMapOf(
                    Stat.ATTACK to (boostObj["atk"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0),
                    Stat.DEFENSE to (boostObj["def"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0),
                    Stat.SP_ATTACK to (boostObj["spa"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0),
                    Stat.SP_DEFENSE to (boostObj["spd"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0),
                    Stat.SPEED to (boostObj["spe"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0),
                )
            )
        } else {
            StatBoosts()
        }

        // Convert status
        val psStatus = when (statusStr.uppercase()) {
            "BRN" -> Status.BURN
            "PSN" -> Status.POISON
            "TOX" -> Status.TOXIC
            "PAR" -> Status.PARALYSIS
            "SLP" -> Status.SLEEP
            "FRZ" -> Status.FREEZE
            else -> Status.NONE
        }

        val statState = StatState(
            knownBuild = null,
            estimatedStats = null,
            statBoosts = boosts
        )

        return PokemonState(
            species = Species.UNKNOWN,
            hp = hp,
            maxHp = maxHp,
            stats = statState,
            status = StatusState(psStatus, null),
            moves = moveList,
            item = UnknownItem as ItemState,
            ability = UnknownAbility as AbilityState
        )
    }

    private fun createEmptyPokemon(): PokemonState {
        return PokemonState(
            species = Species.UNKNOWN,
            hp = 0,
            maxHp = 1,
            stats = StatState(null, null, StatBoosts()),
            status = StatusState(Status.NONE, null),
            moves = mutableListOf(),
            item = UnknownItem as ItemState,
            ability = UnknownAbility as AbilityState
        )
    }
}
