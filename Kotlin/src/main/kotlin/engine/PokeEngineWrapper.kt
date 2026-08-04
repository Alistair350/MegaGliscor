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
    private var lastStateStr: String? = null

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
            lastStateStr = stateStr

            // Basic sanity-check on the serialized state string to avoid passing clearly invalid
            // states into the Rust engine (which can panic). The poke-engine format expects
            // each side to list 6 pokémon separated by '=' followed by the active index, so
            // require at least 7 '='-separated fields per side (6 pokémon + active index).
            val parts = stateStr.split("/")
            if (parts.size < 5) {
                LoggerConfigs.generalLogger.e { "Invalid state string: not enough '/'-separated sections" }
                return null
            }
            val side1Pieces = parts[0].split("=")
            val side2Pieces = parts[1].split("=")
            if (side1Pieces.size < 7 || side2Pieces.size < 7) {
                LoggerConfigs.generalLogger.e { "Invalid state string: each side must contain 6 pokemons and an active index (s1=${side1Pieces.size}, s2=${side2Pieces.size})" }
                return null
            }
            val activeIndex1 = side1Pieces.getOrNull(6)?.toIntOrNull() ?: -1
            val activeIndex2 = side2Pieces.getOrNull(6)?.toIntOrNull() ?: -1
            if (activeIndex1 !in 0..5 || activeIndex2 !in 0..5) {
                LoggerConfigs.generalLogger.e { "Invalid active index in serialized state (s1=$activeIndex1, s2=$activeIndex2)" }
                return null
            }

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
            try {
                lastStateStr?.let { java.io.File("/tmp/poke_engine_error_state.txt").writeText(it) }
                LoggerConfigs.generalLogger.d { "Wrote failing state string to /tmp/poke_engine_error_state.txt" }
            } catch (ioe: Exception) {
                LoggerConfigs.generalLogger.e(ioe) { "Failed to write failing state to /tmp" }
            }
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
            val moveNameRaw = engineChoice.substring("move:".length)
            val norm = moveNameRaw.filter { it.isLetterOrDigit() }.uppercase()

            // Search active moves for a matching KnownByName or Known move name
            val active = gameState.mySide.activePokemon()
            active.moves.forEachIndexed { idx, mv ->
                val candidate = when (mv) {
                    is states.pokemonstates.MoveState.KnownByName -> mv.name
                    is states.pokemonstates.MoveState.Known -> mv.move.name
                    else -> null
                }
                if (candidate != null) {
                    val candNorm = candidate.filter { it.isLetterOrDigit() }.uppercase()
                    if (candNorm == norm || candNorm.contains(norm) || norm.contains(candNorm)) {
                        return "move ${idx + 1}"
                    }
                }
            }

            LoggerConfigs.generalLogger.w { "Could not match engine move '$moveNameRaw' to active moves; returning fallback" }
            "move 1"
        } else {
            LoggerConfigs.generalLogger.w { "Unknown engine choice format: $engineChoice" }
            "move 1" // Fallback
        }
}
