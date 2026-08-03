package engine

import kotlinx.serialization.json.JsonObject
import uniffi.poke_engine_ffi.bestMoveMcts
import uniffi.poke_engine_ffi.debugRoundtripState
import uniffi.poke_engine_ffi.legalOptions

/**
 * High-level wrapper around the poke-engine FFI and state converters.
 * Handles the full pipeline: PS JSON → GameState → poke-engine state string → MCTS search.
 */
class PokeEngineWrapper {
    private val psToGameState = PSToGameStateConverter()
    private val gameStateToPokeEngine = GameStateToPokeEngineConverter()

    /**
     * Takes a Pokémon Showdown request JSON and returns the best move from MCTS analysis.
     *
     * @param requestJson The "side", "active", "opponent" fields from PS request
     * @param timeMsForSearch How many milliseconds to spend searching (default 1000 = 1 second)
     * @return The best move/switch recommendation as a string (e.g., "move 1", "switch 2")
     */
    fun getBestMove(
        requestJson: JsonObject,
        timeMsForSearch: Long = 1000,
    ): String? =
        try {
            // Convert PS JSON to GameState
            val gameState = psToGameState.convert(requestJson)

            // Convert GameState to poke-engine state string
            val stateStr = gameStateToPokeEngine.convert(gameState)

            // Validate state string by round-tripping it
            val roundTrippedStr = debugRoundtripState(stateStr)
            LoggerConfigs.generalLogger.d { "State round-trip successful" }

            // Get legal options to verify correctness
            val legalOptions = legalOptions(stateStr)
            LoggerConfigs.generalLogger.d { "Legal options for side 1: ${legalOptions[0]}" }

            // Run MCTS search
            val searchResult = bestMoveMcts(stateStr, timeMsForSearch.toULong(), 0U)
            LoggerConfigs.generalLogger.i { "MCTS completed: ${searchResult.iterations} iterations, ${searchResult.sideOne.size} options" }

            // Find best move (highest average score)
            val bestOption = searchResult.sideOne.maxByOrNull { it.averageScore }
            if (bestOption != null) {
                LoggerConfigs.generalLogger.i {
                    "Best move: ${bestOption.moveChoice} (score: ${bestOption.averageScore}, visits: ${bestOption.visits})"
                }
                convertEngineChoiceToPS(bestOption.moveChoice, gameState)
            } else {
                LoggerConfigs.generalLogger.w { "No moves available from MCTS" }
                null
            }
        } catch (e: Exception) {
            LoggerConfigs.generalLogger.e(e) { "Error in MCTS search" }
            null
        }

    /**
     * Converts poke-engine's move format to Pokémon Showdown format.
     * poke-engine returns: "move:MOVENAME" or "switch:1"
     * PS expects: "move 1" (1-indexed) or "switch 2" (1-indexed)
     */
    private fun convertEngineChoiceToPS(
        engineChoice: String,
        gameState: states.GameState,
    ): String =
        if (engineChoice.startsWith("switch:")) {
            val pokemonIndex = engineChoice.substring("switch:".length).toIntOrNull() ?: 0
            "switch ${pokemonIndex + 1}" // Convert 0-indexed to 1-indexed
        } else if (engineChoice.startsWith("move:")) {
            val moveName = engineChoice.substring("move:".length)
            // For now, we can't match moves since MoveState is Unknown placeholder
            // Just return move 1 as fallback
            LoggerConfigs.generalLogger.w { "Move selection not fully implemented yet: $moveName" }
            "move 1"
        } else {
            LoggerConfigs.generalLogger.w { "Unknown engine choice format: $engineChoice" }
            "move 1" // Fallback
        }
}
