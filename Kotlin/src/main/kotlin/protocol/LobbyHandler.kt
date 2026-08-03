// lobby/LobbyHandler.kt
package lobby

import battle.BattleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.*
import protocol.MessageDistributor
import protocol.PSMessage

class LobbyHandler(
    private val battleManager: BattleManager,
    private val scope: CoroutineScope,
) {
    fun start() {
        MessageDistributor.globalMessages
            .onEach { handleGlobalMessage(it) }
            .launchIn(scope)
    }

    private suspend fun handleGlobalMessage(msg: PSMessage.GlobalMessage) {
        msg.lines.forEach { line ->
            when {
                line.startsWith("|updatesearch|") -> handleUpdateSearch(line)
                line.startsWith("|challenges|") -> handleChallenges(line)
                // TODO: Add more handlers for other global messages as needed
            }
        }
    }

    private suspend fun handleUpdateSearch(line: String) {
        val games =
            Json
                .parseToJsonElement(line.removePrefix("|updatesearch|"))
                .jsonObject["games"] as? JsonArray
                ?: return

        games
            .mapNotNull { (it as? JsonPrimitive)?.content }
            .filter { it.startsWith("battle-") }
            .forEach { battleManager.startBattle(it) }
    }

    private fun handleChallenges(line: String) {
        // TODO: auto-accept logic
    }
}
