package protocol

import BotPlayer
import battle.BattleManager
import io.ktor.http.Parameters
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object PSInterface {
    /**
     * Connects to the server, logs in, and authenticates the current player session.
     *
     * @param player The player credentials and connection details.
     */
    suspend fun connectAndLogin(player: BotPlayer) {
        WebsocketClient.setAddress(player.address)
        WebsocketClient.connect()
        val challstr = returnChallstr()
        val assertion = getAssertion(player, challstr)
        WebsocketClient.sendMessage("|/trn ${player.user},0,$assertion")
        LoggerConfigs.websocketLogger.i { "Successfully logged in as ${player.user}" }
    }

    /**
     * Waits for the first challenge string sent by the server and returns it in login format.
     *
     * @return The parsed challenge string in login format.
     */
    suspend fun returnChallstr(): String {
        val message =
            MessageDistributor.globalMessages.first { msg ->
                msg.lines.any { line ->
                    val split = line.split("|")
                    split.size >= 4 && split[1] == "challstr"
                }
            }
        val line = message.lines.first { it.startsWith("|challstr|") }
        val split = line.split("|")
        return "${split[2]}|${split[3]}"
    }

    /**
     * One-shot helper for global messages (challenges, popups, etc.).
     *
     * @param predicate The condition the global message must satisfy.
     * @return The first matching global message.
     */
    suspend inline fun waitForGlobal(crossinline predicate: (PSMessage.GlobalMessage) -> Boolean): PSMessage.GlobalMessage =
        MessageDistributor.globalMessages.first { predicate(it) }

    /**
     * Exchanges the challenge string for a login assertion used to authenticate the session.
     *
     * @param player The player credentials used for login.
     * @param challstr The server-issued challenge string.
     * @return The assertion returned by the login endpoint.
     */
    suspend fun getAssertion(
        player: BotPlayer,
        challstr: String,
    ): String {
        val response =
            WebsocketClient.httpPost(
                Parameters.build {
                    append("name", player.user)
                    append("pass", player.password)
                    append("challstr", challstr)
                },
                "https://play.pokemonshowdown.com/api/login",
            )
        val json = response.removePrefix("]")
        val parsed = Json.parseToJsonElement(json).jsonObject

        val success = parsed["actionsuccess"]?.jsonPrimitive?.boolean ?: false
        if (!success) error("Login failed: ${parsed["message"]}")

        return parsed["assertion"]?.jsonPrimitive?.content
            ?: error("Assertion not found in response")
    }

    /**
     * Sends the avatar selection command for the current player.
     *
     * @param player The player whose avatar should be set.
     */
    suspend fun setAvatar(player: BotPlayer) {
        WebsocketClient.sendMessage("|/avatar ${player.avatar}")
    }

    /**
     * Requests matchmaking for the given format.
     *
     * @param format The battle format to search for.
     */
    suspend fun searchBattle(format: String) {
        WebsocketClient.sendMessage("|/search $format")
    }

    /**
     * Sends a battle challenge to another player.
     *
     * @param username The target player's username.
     * @param format The battle format to challenge them in.
     */
    suspend fun sendChallenge(
        username: String,
        format: String,
    ) {
        WebsocketClient.sendMessage("|/challenge $username, $format")
    }

    /**
     * Accepts an incoming battle challenge from the specified user.
     *
     * @param username The username of the player who challenged us.
     */
    suspend fun acceptChallenge(username: String) {
        WebsocketClient.sendMessage("|/accept $username")
    }
}
