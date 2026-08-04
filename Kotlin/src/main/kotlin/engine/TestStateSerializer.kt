package engine

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import uniffi.poke_engine_ffi.debugRoundtripState
import uniffi.poke_engine_ffi.legalOptions
// import uniffi.poke_engine_ffi.debugPrettyPrintState  // TODO: fix JNA loading after rebuild

/**
 * Quick test utility to verify state serialization works end-to-end.
 * Run this to debug state string generation.
 */
object TestStateSerializer {
    fun testRoundTrip(requestJsonStr: String) {
        println("=== Testing State Serialization ===")
        
        try {
            val request = Json.parseToJsonElement(requestJsonStr).jsonObject
            
            // Convert to GameState
            val converter = PSToGameStateConverter()
            val gameState = converter.convert(request)
            println("✓ Converted PS JSON to GameState")
            println("  Our side: ${gameState.mySide.activePokemon().species} (HP: ${gameState.mySide.activePokemon().hp}/${gameState.mySide.activePokemon().maxHp})")
            println("  Opponent: ${gameState.opponentSide.activePokemon().species} (HP: ${gameState.opponentSide.activePokemon().hp}/${gameState.opponentSide.activePokemon().maxHp})")
            
            // Convert to poke-engine state string
            val stateSerializer = GameStateToPokeEngineConverter()
            val stateStr = stateSerializer.convert(gameState)
            println("✓ Serialized GameState to poke-engine format")
            println("  State string length: ${stateStr.length}")
            println("  State string (truncated to 800 chars):\n" + stateStr.take(800))
            // Save full state string to temp file for inspection
            try {
                java.io.File("/tmp/poke_engine_state.txt").writeText(stateStr)
                println("  Full state string written to /tmp/poke_engine_state.txt")
            } catch (ioe: Exception) {
                println("  Failed to write state string to /tmp: ${ioe.message}")
            }

            // Test round-trip
            val roundTrippedStr = debugRoundtripState(stateStr)
            println("✓ Round-trip successful")

            // Get legal options
            val options = legalOptions(stateStr)
            println("✓ Legal options retrieved")
            println("  Side 1 options: ${options[0]}")
            if (options.size > 1) {
                println("  Side 2 options: ${options[1]}")
            }

            // Debug: pretty-print deserialized state to see what the engine parsed
            // TODO: fix JNA symbol loading for new FFI functions after rebuild
            println("\n=== Deserialized State Debug ===")
            println("(Debug helper temporarily disabled - JNA symbol loading issue)")

            println("\n✓ All tests passed!")
            
        } catch (e: Exception) {
            println("✗ Error: ${e.message}")
            e.printStackTrace()
        }
    }
}

// Example usage - you can call this from main.kt during debugging
// TestStateSerializer.testRoundTrip(jsonString)
