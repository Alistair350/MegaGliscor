package battle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import protocol.MessageDistributor

class BattleManager(
    private val send: suspend (String) -> Unit,
    private val scope: CoroutineScope,
) {
    private val activeBattles = mutableMapOf<String, BattleHandler>()

    init {
        // Auto-spawn handlers for battle rooms the distributor discovers
        MessageDistributor.newBattleRooms
            .onEach { roomId ->
                if (roomId !in activeBattles) {
                    startBattle(roomId)
                }
            }.launchIn(scope)
    }

    /**
     * Starts tracking a battle room and returns the handler that will process its messages.
     *
     * @param roomId The battle room identifier to track.
     * @return The newly created battle handler.
     * @throws IllegalStateException If the room is already being tracked.
     */
    fun startBattle(roomId: String): BattleHandler {
        if (roomId in activeBattles) {
            error("Battle $roomId already tracked")
        }

        val channel = MessageDistributor.registerRoom(roomId)
        val handler = BattleHandler(roomId, channel, send, scope)
        activeBattles[roomId] = handler

        // Clean up when the handler dies
        scope.launch {
            handler.job.join()
            activeBattles.remove(roomId)
            MessageDistributor.unregisterRoom(roomId)
        }

        return handler
    }

    /**
     * Stops the handler for a single battle room if it is currently active.
     *
     * @param roomId The battle room identifier to stop.
     */
    fun stopBattle(roomId: String) {
        activeBattles[roomId]?.stop()
    }

    /**
     * Stops every active battle handler and clears the registry.
     */
    fun stopAll() {
        activeBattles.values.forEach { it.stop() }
        activeBattles.clear()
    }
}
