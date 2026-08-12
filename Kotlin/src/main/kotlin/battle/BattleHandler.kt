package battle

import config.LoggerConfigs
import engine.PokeEngineWrapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import protocol.PSMessage

class BattleHandler(
    val roomId: String,
    private val incoming: ReceiveChannel<PSMessage.RoomMessage>,
    private val send: suspend (String) -> Unit,
    scope: CoroutineScope,
) {
    private var rqid: Int? = null
    private val engine = PokeEngineWrapper()

    // PS sends tons of minor protocol messages. These are expected but don't need action yet.
    private val ignoredPrefixes =
        setOf(
            "gen",
            "tier",
            "rule",
            "t:",
            "teamsize",
            "start",
            "",
            "-damage",
            "-heal",
            "-status",
            "-curestatus",
            "-cureteam",
            "-boost",
            "-unboost",
            "-weather",
            "-fieldstart",
            "-fieldend",
            "-sidestart",
            "-sideend",
            "-crit",
            "-supereffective",
            "-resisted",
            "-immune",
            "-miss",
            "-fail",
            "-block",
            "-prepare",
            "-mustrecharge",
            "-hitcount",
            "-singlemove",
            "-singleturn",
            "-activate",
            "-end",
            "-start",
            "-ability",
            "-item",
            "-enditem",
            "-transform",
            "-mega",
            "-primal",
            "-burst",
            "-zpower",
            "-terastallize",
            "-hint",
            "upkeep",
            "replace",
            "-swapboost",
            "-invertboost",
            "-clearboost",
            "-copyboost",
            "-clearallboost",
            "-clearpositiveboost",
            "-endability",
        )

    val job: Job =
        scope.launch {
            try {
                LoggerConfigs.generalLogger.i { "BattleHandler started for $roomId" }
                for (msg in incoming) {
                    processBatch(msg)
                }
            } catch (e: CancellationException) {
                LoggerConfigs.generalLogger.i { "BattleHandler cancelled for $roomId" }
                throw e
            } finally {
                LoggerConfigs.generalLogger.i { "BattleHandler ended for $roomId" }
            }
        }

    private suspend fun processBatch(msg: PSMessage.RoomMessage) {
        for (line in msg.lines) {
            val parts = line.split("|")
            if (parts.size < 2) {
                // Empty lines or non-protocol noise — ignore silently
                continue
            }

            when (val cmd = parts[1]) {
                "init" -> {
                    handleInit(parts)
                }

                "poke" -> {
                    handlePokeInit(parts)
                }

                "player" -> {
                    handlePlayer(parts)
                }

                "turn", "move", "switch", "drag", "request", "faint" -> {
                    handleBattleUpdate(parts)
                }

                "win", "tie", "expire", "deinit" -> {
                    handleBattleEnd(cmd)
                    return
                }

                else -> {
                    handleUnknown(cmd, line)
                }
            }
        }
    }

    private fun handleUnknown(
        cmd: String,
        line: String,
    ) {
//        if (cmd in ignoredPrefixes) {
//            config.LoggerConfigs.websocketLogger.d { "[$roomId] ignored: $cmd" }
//        } else {
        LoggerConfigs.websocketLogger.w { "[$roomId] unknown protocol: $line" }
//        }
    }

    private fun handleInit(parts: List<String>) {
        val type = parts.getOrNull(2) ?: "unknown"
        LoggerConfigs.battleLogger.i { "Battle $roomId initialized (type: $type)" }
    }

    private fun handlePokeInit(parts: List<String>) {
        val player = parts.getOrNull(2) ?: "unknown"
        val mon = parts.getOrNull(3) ?: "unknown"
        val item = parts.getOrNull(4) ?: "unknown"
        LoggerConfigs.battleLogger.i { "[$roomId] Adding Pokemon $mon, $item, for $player" }
    }

    private fun handlePlayer(parts: List<String>) {
        val slot = parts.getOrNull(2) ?: return
        val name = parts.getOrNull(3) ?: return
        LoggerConfigs.battleLogger.i { "Battle $roomId — $slot = $name" }
    }

    private suspend fun handleBattleUpdate(parts: List<String>) {
        when (val action = parts.getOrNull(1) ?: return) {
            "turn" -> {
                handleTurn(parts)
            }

            "move" -> {
                handleMove(parts)
            }

            "switch", "drag" -> {
                handleSwitch(parts)
            }

            "request" -> {
                handleRequest(parts)
            }

            "faint" -> {
                handleFaint(parts)
            }
        }
    }

    private fun handleTurn(parts: List<String>) {
        val turn = parts.getOrNull(2)?.toIntOrNull() ?: return
        LoggerConfigs.battleLogger.i { "Battle $roomId — Turn $turn" }
    }

    private fun handleMove(parts: List<String>) {
        val pokemon = parts.getOrNull(2) ?: return
        val move = parts.getOrNull(3) ?: return
        LoggerConfigs.battleLogger.i { "Battle $roomId — $pokemon used $move" }
    }

    private fun handleSwitch(parts: List<String>) {
        val pokemon = parts.getOrNull(2) ?: return
        val details = parts.getOrNull(3) ?: return
        LoggerConfigs.battleLogger.i { "Battle $roomId — $pokemon switched in ($details)" }
    }

    private fun handleFaint(parts: List<String>) {
        val pokemon = parts.getOrNull(2) ?: return
        LoggerConfigs.battleLogger.i { "Battle $roomId — $pokemon fainted" }
    }

    /**
     * |request|{json} is sent when the server wants a decision from us.
     * The JSON contains active moves, forceSwitch flags, teamPreview, and rqid.
     */
    private suspend fun handleRequest(parts: List<String>) {
        val jsonStr = parts.getOrNull(2)

        // Empty request = "wait for opponent", clear state
        if (jsonStr.isNullOrBlank()) {
            LoggerConfigs.battleLogger.d { "Battle $roomId — empty request (waiting)" }
            return
        }

        val request = Json.parseToJsonElement(jsonStr).jsonObject
        rqid = request["rqid"]?.jsonPrimitive?.int

        val wait = request["wait"]?.jsonPrimitive?.boolean == true
        if (wait) {
            LoggerConfigs.battleLogger.i { "Battle $roomId — waiting for opponent" }
            return
        }

        val teamPreview = request["teamPreview"]?.jsonPrimitive?.boolean == true
        val forceSwitch = request["forceSwitch"]?.jsonArray
        val active = request["active"]?.jsonArray

        when {
            teamPreview -> {
                LoggerConfigs.battleLogger.i { "Battle $roomId — team preview" }
                // Default: keep current order. Replace with engine later.
                sendChoice("team 123456")
            }

            forceSwitch != null -> {
                LoggerConfigs.battleLogger.i { "Battle $roomId — force switch required" }
                // Default: switch to slot 2. Replace with engine later.
                sendChoice("switch 2")
            }

            active != null -> {
                LoggerConfigs.battleLogger.i { "Battle $roomId — move request (${active.size} active)" }
                // Use poke-engine MCTS to find the best move
                val bestChoice = engine.getBestMove(request)
                if (bestChoice != null) {
                    sendChoice(bestChoice)
                } else {
                    LoggerConfigs.battleLogger.w { "Engine failed to find best move, defaulting to move 1" }
                    sendChoice("move 1")
                }
            }

            else -> {
                LoggerConfigs.websocketLogger.w { "Battle $roomId — unrecognized request: $jsonStr" }
            }
        }
    }

    /**
     * Sends a choice to PS. Format: >roomid\n|/choose choice
     */
    private suspend fun sendChoice(choice: String) {
        val id = rqid?.toString() ?: ""
        // PS accepts: roomid|/choose move 1
        // The rqid is optional but good practice.
        val payload =
            if (id.isNotEmpty()) {
                "$roomId|/choose $choice|$id"
            } else {
                "$roomId|/choose $choice"
            }
        send(payload)
        LoggerConfigs.battleLogger.i { "Battle $roomId — sent: $choice (rqid=$rqid)" }
    }

    private suspend fun handleBattleEnd(reason: String) {
        LoggerConfigs.battleLogger.i { "Battle $roomId ended ($reason)" }
        job.cancel()
    }

    fun stop() {
        job.cancel()
    }
}
