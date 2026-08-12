package protocol

import config.LoggerConfigs
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.awt.SystemColor

object WebsocketClient {
    private var address = ""
    private lateinit var session: DefaultClientWebSocketSession
    private val _messages =
        MutableSharedFlow<String>(
            replay = 0,
            extraBufferCapacity = 100,
        )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val messages: SharedFlow<String> = _messages
    private val client =
        HttpClient(CIO) {
            install(WebSockets)
        }

    /**
     * Updates the websocket address used for subsequent connections.
     *
     * @param newAddress The websocket endpoint to connect to.
     */
    fun setAddress(newAddress: String) {
        address = newAddress
    }

    /**
     * Returns the currently configured websocket address.
     *
     * @return The active websocket address.
     */
    fun getAddress(): String = address

    /**
     * Opens the websocket connection and starts forwarding incoming text frames to listeners.
     */
    suspend fun connect() {
        session = client.webSocketSession(address)
        scope.launch {
            while (true) {
                val frame = session.incoming.receive()
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    LoggerConfigs.websocketLogger.i { "Received: $text" }
                    _messages.emit(text)
                } else {
                    LoggerConfigs.websocketLogger.w { "Non text received: ${SystemColor.text}" }
                }
            }
        }
    }

    /**
     * Sends a text frame over the active websocket connection.
     *
     * @param text The text payload to send.
     */
    suspend fun sendMessage(text: String) {
        session.send(Frame.Text(text))
        LoggerConfigs.websocketLogger.i { "Message sent $text" }
    }

    /**
     * Performs an HTTP POST request with form-encoded parameters and returns the response body.
     *
     * @param body The form parameters to send.
     * @param url The destination URL.
     * @return The response body as text.
     */
    suspend fun httpPost(
        body: Parameters,
        url: String,
    ): String {
        val response =
            client.post(url) {
                setBody(FormDataContent(body))
            }
        return response.bodyAsText()
    }

    /**
     * Waits for the first websocket message matching the provided predicate.
     *
     * @param predicate The condition a message must satisfy.
     * @return The first matching websocket message.
     */
    suspend fun waitForMessage(predicate: (String) -> Boolean): String = messages.first { message -> predicate(message) }

    /**
     * Closes the websocket session and underlying HTTP client.
     */
    suspend fun close() {
        session.close()
        client.close()
    }
}
