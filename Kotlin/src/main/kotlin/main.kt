@file:Suppress("ktlint:standard:no-wildcard-imports")

import LoggerConfigs.generalLogger
import battle.BattleManager
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import lobby.LobbyHandler
import protocol.MessageDistributor
import protocol.PSInterface.connectAndLogin
import protocol.PSInterface.sendChallenge
import protocol.PSInterface.setAvatar
import protocol.PSMessage
import protocol.WebsocketClient
import protocol.WebsocketClient.close

fun main(): Unit =
    runBlocking(SupervisorJob()) {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                generalLogger.i("Application shutting down.")
                runBlocking { close() }
            },
        )

        System.setProperty(
            "jna.library.path",
            "/Users/jayden/Documents/MegaGliscor/poke-engine-ffi/target/release",
        )

        System.load(
            "/Users/jayden/Documents/MegaGliscor/poke-engine-ffi/target/release/libpoke_engine_ffi.dylib",
        )

        val engineBot =
            BotPlayer(
                Config.username,
                Config.password,
                Config.avatar,
                Config.address,
            )

        // 1. Connect raw websocket
        WebsocketClient.setAddress(engineBot.address)
        WebsocketClient.connect()

        // 2. Start parsing & routing messages
        MessageDistributor.start(this)

        // 3. Login
        connectAndLogin(engineBot)
        setAvatar(engineBot)

        // 4. Start battle manager & lobby handler
        val battleManager =
            BattleManager(
                send = { msg -> WebsocketClient.sendMessage(msg) },
                scope = this,
            )

        LobbyHandler(battleManager, this).start()

        // 5. Do battle stuffs
        sendChallenge("calamitycow", "gen9randombattle")

        // For Laddering: PSInterface.searchBattle("gen9randombattle")

        awaitCancellation()
    }
