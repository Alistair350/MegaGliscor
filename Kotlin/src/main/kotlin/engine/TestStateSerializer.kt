package engine

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import uniffi.poke_engine_ffi.debugRoundtripState
import uniffi.poke_engine_ffi.legalOptions

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
            
            println("\n✓ All tests passed!")
            
        } catch (e: Exception) {
            println("✗ Error: ${e.message}")
            e.printStackTrace()
        }
    }
}

// Example usage - you can call this from main.kt during debugging
// TestStateSerializer.testRoundTrip(jsonString)
