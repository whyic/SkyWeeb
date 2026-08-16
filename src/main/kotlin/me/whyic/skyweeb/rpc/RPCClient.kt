package me.whyic.skyweeb.rpc

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture

data class DiscordButton(val label: String, val url: String)

object RPCClient {
    private var pipe: RandomAccessFile? = null
    private val logger = LoggerFactory.getLogger("SkyWeeb-IPC")
    private var isConnected = false
    private var isConnecting = false

    // TODO: confirm this is the correct/current Discord application Client ID —
    // it doesn't match either value seen elsewhere in the codebase (Config.kt default,
    // or what was showing in your in-game config screen). Only one hardcoded value
    // is actually used at runtime, and this is it.
    private const val CLIENT_ID = "1439506215095898142"

    fun start() {
        if (isConnected || isConnecting) return

        isConnecting = true
        CompletableFuture.runAsync {
            try {
                pipe = RandomAccessFile("\\\\.\\pipe\\discord-ipc-0", "rw")

                val handshake = JsonObject()
                handshake.addProperty("v", 1)
                handshake.addProperty("client_id", CLIENT_ID)

                sendPayload(0, handshake.toString())
                readPayload()

                isConnected = true
                logger.info(">>> RAW PIPE CONNECTED! Discord handshake accepted. <<<")
            } catch (e: Exception) {
                logger.error(">>> RAW PIPE FAILED <<<")
                stop()
            } finally {
                isConnecting = false
            }
        }
    }

    fun stop() {
        isConnected = false
        isConnecting = false
        try {
            pipe?.close()
        } catch (e: Exception) {
        }
        pipe = null
    }

    fun updateActivity(action: PresenceBuilder.() -> Unit) {
        if (!isConnected || pipe == null) return

        try {
            val builder = PresenceBuilder()
            builder.action()

            val args = JsonObject()
            val pid = java.lang.ProcessHandle.current().pid()
            args.addProperty("pid", pid)
            args.add("activity", builder.toJson())

            val payload = JsonObject()
            payload.addProperty("cmd", "SET_ACTIVITY")
            payload.add("args", args)
            payload.addProperty("nonce", System.currentTimeMillis().toString())

            sendPayload(1, payload.toString())
        } catch (e: Exception) {
            logger.error(">>> FAILED TO SEND PAYLOAD <<<", e)
            stop()
        }
    }

    private fun sendPayload(opcode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(opcode)
        buffer.putInt(bytes.size)
        buffer.put(bytes)
        pipe?.write(buffer.array())
    }

    private fun readPayload() {
        val header = ByteArray(8)
        pipe?.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val length = buffer.getInt(4)
        val data = ByteArray(length)
        pipe?.readFully(data)
    }

    class PresenceBuilder {
        private var details: String? = null
        private var state: String? = null
        private var startTimestamp: Long? = null
        private var largeImageKey: String? = null
        private var largeImageText: String? = null
        private val buttons = mutableListOf<DiscordButton>()

        fun setDetails(details: String?) {
            this.details = details
        }

        fun setState(state: String?) {
            this.state = state
        }

        fun setStartTimestamp(timestamp: Long) {
            this.startTimestamp = timestamp
        }

        fun setLargeImage(key: String?, text: String?) {
            this.largeImageKey = key
            this.largeImageText = text
        }

        fun addButton(button: DiscordButton) {
            buttons.add(button)
        }

        fun toJson(): JsonObject {
            val activity = JsonObject()

            if (!details.isNullOrBlank()) activity.addProperty("details", details)
            if (!state.isNullOrBlank()) activity.addProperty("state", state)

            if (startTimestamp != null) {
                val timestamps = JsonObject()
                timestamps.addProperty("start", startTimestamp!! / 1000)
                activity.add("timestamps", timestamps)
            }

            if (largeImageKey != null || largeImageText != null) {
                val assets = JsonObject()
                if (largeImageKey != null) assets.addProperty("large_image", largeImageKey)
                if (largeImageText != null) assets.addProperty("large_text", largeImageText)
                activity.add("assets", assets)
            }

            activity.addProperty("type", 0)

            if (buttons.isNotEmpty()) {
                val buttonsArray = JsonArray()
                buttons.forEach {
                    val btn = JsonObject()
                    btn.addProperty("label", it.label)
                    btn.addProperty("url", it.url)
                    buttonsArray.add(btn)
                }
                activity.add("buttons", buttonsArray)
            }
            return activity
        }
    }
}
