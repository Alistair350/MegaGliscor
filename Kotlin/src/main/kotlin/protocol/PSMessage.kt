package protocol

sealed class PSMessage {
    abstract val raw: String

    data class RoomMessage(
        val roomId: String,
        val lines: List<String>,
        override val raw: String,
    ) : PSMessage()

    data class GlobalMessage(
        val lines: List<String>,
        override val raw: String,
    ) : PSMessage()
}

object PSParser {
    /**
     * PS sends batches separated by \n.
     * Room batches start with ">roomid".
     * Global batches have no ">" prefix on the first line.
     *
     * @param batch The raw websocket batch to parse.
     * @return The parsed PS message.
     */
    fun parse(batch: String): PSMessage {
        val lines = batch.split("\n").map { it.removeSuffix("\r") }
        val first = lines.firstOrNull()?.trim() ?: return PSMessage.GlobalMessage(emptyList(), batch)

        return if (first.startsWith(">")) {
            val roomId = first.removePrefix(">")
            PSMessage.RoomMessage(roomId, lines.drop(1), batch)
        } else {
            PSMessage.GlobalMessage(lines, batch)
        }
    }
}
