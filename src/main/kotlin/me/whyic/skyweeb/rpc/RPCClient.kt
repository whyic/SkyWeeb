package me.whyic.skyweeb.rpc

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.concurrent.CompletableFuture

data class DiscordButton(val label: String, val url: String)

object RPCClient {
    private var connection: Closeable? = null
    private var input: DataInputStream? = null
    private var output: OutputStream? = null
    private val logger = LoggerFactory.getLogger("SkyWeeb-IPC")
    @Volatile private var isConnected = false
    @Volatile private var isConnecting = false
    private var lastFailureLogged = false

    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    /** Directories where Discord may place its discord-ipc-N socket on Linux/macOS. */
    private fun unixSocketDirs(): List<String> {
        val dirs = mutableListOf<String>()
        val env = System.getenv()
        listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP").forEach { key ->
            env[key]?.takeIf { it.isNotBlank() }?.let { dirs.add(it.trimEnd('/')) }
        }
        dirs.add("/tmp")

        val runtime = env["XDG_RUNTIME_DIR"]?.takeIf { it.isNotBlank() }?.trimEnd('/')
        if (runtime != null) {
            dirs.add("$runtime/app/com.discordapp.Discord")      // Flatpak
            dirs.add("$runtime/app/com.discordapp.DiscordCanary")
            dirs.add("$runtime/snap.discord")                     // Snap
            dirs.add("$runtime/snap.discord-canary")
        }
        return dirs.distinct()
    }

    private fun openWindowsPipe(index: Int): Boolean {
        val raf = RandomAccessFile("\\\\.\\pipe\\discord-ipc-$index", "rw")
        connection = raf
        input = DataInputStream(FileInputStream(raf.fd))
        output = FileOutputStream(raf.fd)
        return true
    }

    private fun openUnixSocket(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
        try {
            channel.connect(UnixDomainSocketAddress.of(path))
        } catch (e: Exception) {
            try { channel.close() } catch (ignored: Exception) {}
            throw e
        }
        connection = channel
        input = DataInputStream(Channels.newInputStream(channel))
        output = Channels.newOutputStream(channel)
        return true
    }

    /** Tries discord-ipc-0..9 in every known location and returns true once a connection is open. */
    private fun openConnection(): Boolean {
        var lastError: Exception? = null
        for (i in 0..9) {
            if (isWindows) {
                try {
                    if (openWindowsPipe(i)) return true
                } catch (e: Exception) {
                    lastError = e
                }
            } else {
                for (dir in unixSocketDirs()) {
                    val path = "$dir/discord-ipc-$i"
                    try {
                        if (openUnixSocket(path)) {
                            logger.info("Connected to Discord IPC at {}", path)
                            return true
                        }
                    } catch (e: Exception) {
                        lastError = e
                        logger.debug("Failed to open {}: {}", path, e.toString())
                    }
                }
            }
        }
        if (!lastFailureLogged) {
            lastFailureLogged = true
            logger.warn(
                "Could not find a Discord IPC socket (is Discord running?). Last error: {}",
                lastError?.toString() ?: "none found"
            )
        }
        return false
    }


    private const val CLIENT_ID = "1439506215095898142"

    fun start() {
        if (isConnected || isConnecting) return

        isConnecting = true
        CompletableFuture.runAsync {
            try {
                if (!openConnection()) {
                    stop()
                    return@runAsync
                }

                val handshake = JsonObject()
                handshake.addProperty("v", 1)
                handshake.addProperty("client_id", CLIENT_ID)

                sendPayload(0, handshake.toString())
                readPayload()

                isConnected = true
                lastFailureLogged = false
                logger.info("Discord IPC handshake accepted.")
            } catch (e: Exception) {
                logger.error("Discord IPC handshake failed", e)
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
            connection?.close()
        } catch (e: Exception) {
        }
        connection = null
        input = null
        output = null
    }

    fun updateActivity(action: PresenceBuilder.() -> Unit) {
        if (!isConnected || output == null) return

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
            logger.error("Failed to send Discord presence payload", e)
            stop()
        }
    }

    private fun sendPayload(opcode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(opcode)
        buffer.putInt(bytes.size)
        buffer.put(bytes)
        output?.write(buffer.array())
        output?.flush()
    }

    private fun readPayload() {
        val header = ByteArray(8)
        input?.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val length = buffer.getInt(4)
        val data = ByteArray(length)
        input?.readFully(data)
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
