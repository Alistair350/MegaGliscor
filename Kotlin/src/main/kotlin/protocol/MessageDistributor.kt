package protocol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap

object MessageDistributor {
    private val roomChannels = ConcurrentHashMap<String, Channel<PSMessage.RoomMessage>>()

    private val _globalMessages =
        MutableSharedFlow<PSMessage.GlobalMessage>(
            replay = 0,
            extraBufferCapacity = 100,
        )
    val globalMessages: SharedFlow<PSMessage.GlobalMessage> = _globalMessages

    /** Emitted when we auto-discover a battle room we haven't registered yet. */
    private val _newBattleRooms =
        MutableSharedFlow<String>(
            replay = 0,
            extraBufferCapacity = 10,
        )
    val newBattleRooms: SharedFlow<String> = _newBattleRooms

    private var started = false

    /**
     * Starts consuming websocket batches and routes them to room or global listeners.
     *
     * @param scope The coroutine scope used to collect websocket messages.
     */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true

        WebsocketClient.messages
            .onEach { rawBatch ->
                when (val msg = PSParser.parse(rawBatch)) {
                    is PSMessage.RoomMessage -> routeToRoom(msg)
                    is PSMessage.GlobalMessage -> _globalMessages.emit(msg)
                }
            }.launchIn(scope)
    }

    /**
     * Sends a room message to the registered channel, auto-registering battle rooms when the
     * first init batch arrives.
     *
     * @param msg The room message to route.
     */
    private suspend fun routeToRoom(msg: PSMessage.RoomMessage) {
        val channel = roomChannels[msg.roomId]
        if (channel != null) {
            channel.send(msg)
            return
        }

        // Auto-register battle rooms on first sight
        if (msg.roomId.startsWith("battle-") && msg.lines.any { it.startsWith("|init|battle") }) {
            val newChannel = registerRoom(msg.roomId)
            newChannel.send(msg)
            _newBattleRooms.emit(msg.roomId)
            LoggerConfigs.websocketLogger.i { "Auto-registered battle room ${msg.roomId}" }
            return
        }

        LoggerConfigs.websocketLogger.w { "No handler for room ${msg.roomId}" }
    }

    /**
     * Registers a room so future battle messages can be delivered to its channel.
     *
     * @param roomId The room identifier to register.
     * @return The channel associated with the room.
     */
    fun registerRoom(roomId: String): Channel<PSMessage.RoomMessage> {
        val channel = Channel<PSMessage.RoomMessage>(capacity = Channel.BUFFERED)
        roomChannels[roomId] = channel
        return channel
    }

    /**
     * Unregisters a room and closes its message channel.
     *
     * @param roomId The room identifier to unregister.
     */
    fun unregisterRoom(roomId: String) {
        roomChannels.remove(roomId)?.close()
    }
}
