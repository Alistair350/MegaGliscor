@file:Suppress("ktlint:standard:no-wildcard-imports")

import TeamManagement.pokepasteToPackedStringFromResource
import battle.BattleManager
import config.Config
import config.LoggerConfigs
import config.LoggerConfigs.generalLogger
import kotlinx.coroutines.*
import lobby.LobbyHandler
import protocol.MessageDistributor
import protocol.PSInterface.connectAndLogin
import protocol.PSInterface.sendChallenge
import protocol.PSInterface.setAvatar
import protocol.PSInterface.setTeam
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

//      5. Optionally set team if format reqiures one
        val team =
            pokepasteToPackedStringFromResource("/gen9teams/blimbal")
        LoggerConfigs.generalLogger.i { "Setting team: $team" }
        setTeam(team)

//        setTeam(
//            "Kyurem||leftovers|pressure|substitute,earthpower,freezedry,protect|Timid|52,,,204,,252||,0,,,,|||,,,,,ground]Corviknight||rockyhelmet|pressure|defog,bravebird,roost,uturn|Impish|248,,252,,8,|||||,,,,,dragon]Ting-Lu||leftovers|vesselofruin|earthquake,payback,rest,sleeptalk|Careful|252,,4,,252,|||||,,,,,water]Dondozo||leftovers|unaware|curse,waterfall,bodypress,rest|Careful|248,,8,,252,|||||,,,,,dark]Slowking-Galar||shucaberry|regenerator|futuresight,sludgebomb,icebeam,chillyreception|Relaxed|252,,252,,4,||,0,,,,0|||,,,,,water]Cinderace||heavydutyboots|blaze|pyroball,willowisp,uturn,courtchange|Jolly|232,24,,,,252|||||,,,,,flying",
//        )

        // 6. Do battle stuffs
        sendChallenge("calamitycow", "gen9ou")

        // For Laddering: PSInterface.searchBattle("gen9randombattle")

        awaitCancellation()
    }
