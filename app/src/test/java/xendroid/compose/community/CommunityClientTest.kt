package xendroid.compose.community

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.compatibility.DeviceFacts
import xendroid.compose.community.CommunityException.Kind

/** A plain HTTP/1.1 server on the loopback address with canned answers, recording each request. */
class TinyHttpServer(private val answer: (Request) -> Answer) : AutoCloseable {
    data class Request(val method: String, val path: String, val headers: Map<String, String>, val body: String)
    data class Answer(val status: Int, val body: String = "", val headers: Map<String, String> = emptyMap())

    private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val base = "http://127.0.0.1:${socket.localPort}"
    val requests = CopyOnWriteArrayList<Request>()

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching { client.use(::serve) }
            }
        }
    }

    private fun serve(client: Socket) {
        val input = BufferedInputStream(client.getInputStream())
        val (method, path) = line(input)?.split(" ")?.let { it[0] to it[1] } ?: return
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val header = line(input) ?: return
            if (header.isEmpty()) break
            headers[header.substringBefore(':').trim().lowercase()] = header.substringAfter(':').trim()
        }
        val body = ByteArray(headers["content-length"]?.toInt() ?: 0)
        var read = 0
        while (read < body.size) {
            val n = input.read(body, read, body.size - read)
            if (n < 0) break
            read += n
        }
        val request = Request(method, path, headers, String(body, Charsets.UTF_8))
        requests += request
        val reply = answer(request)
        val bytes = reply.body.toByteArray()
        val head = buildString {
            append("HTTP/1.1 ${reply.status} X\r\n")
            reply.headers.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
        }
        client.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
    }

    private fun line(input: InputStream): String? {
        val out = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (out.isEmpty()) null else out.toString()
            if (c == '\n'.code) return out.toString().trimEnd('\r')
            out.append(c.toChar())
        }
    }

    override fun close() = socket.close()
}

class CommunityClientTest {
    @get:Rule val folder = TemporaryFolder()

    private val token = "Tok_en-".repeat(5)
    private val s23 = DeviceFacts("Adreno (TM) 740", "Qualcomm Vulkan 0.762", "system", "samsung", "SM-S911B", 34, 120, soc = "SM8550")

    private fun listFor(title: String, vararg configs: String) =
        """{"format":"xendroid-community-configs","version":1,"titleId":"$title","configs":[${configs.joinToString(",")}]}"""

    private val oneConfig = """{"id":"0123456789abcdef","titleId":"4D5307E6","name":"Steady 30","note":"Holds 30","result":"PLAYABLE",
        |"settings":{"GPU|framerate_limit":"30"},"device":{"model":"SM-S911B","gpu":"Adreno (TM) 740"},"appVersionCode":1,
        |"appBuild":"b","createdAt":"2026-10-01","votesUp":1,"votesDown":0}""".trimMargin()

    private fun expectFailure(kind: Kind, block: () -> Unit): CommunityException {
        try {
            block()
        } catch (e: CommunityException) {
            assertEquals(kind, e.kind)
            return e
        }
        fail("expected $kind")
        throw AssertionError()
    }

    @Test fun onlyHttpsOutsideTests() {
        for (url in listOf("http://community.example.org", "http://127.0.0.1:8780", "", "https://u:p@example.org")) {
            try {
                CommunityClient(url)
                fail(url)
            } catch (expected: IllegalArgumentException) {
            }
        }
        CommunityClient("https://community.example.org/xd")
    }

    @Test fun eachCallSendsOnlyWhatItNeeds() {
        TinyHttpServer { request ->
            when {
                request.method == "GET" -> TinyHttpServer.Answer(200, listFor("4D5307E6", oneConfig))
                request.path.endsWith("/vote") -> TinyHttpServer.Answer(200, """{"votesUp":2,"votesDown":0}""")
                request.method == "POST" -> TinyHttpServer.Answer(201, """{"id":"0123456789abcdef","deleteToken":"$token"}""")
                else -> TinyHttpServer.Answer(204)
            }
        }.use { server ->
            val client = CommunityClient(server.base + "/", allowLoopbackHttp = true)
            assertTrue(client.list("4d5307e6").contains("Steady 30"))
            val upload = CommunityConfigs.draft("4D5307E6", "Steady 30", "Holds 30", CompatStatus.PLAYABLE,
                mapOf("GPU|framerate_limit" to "30"), s23, 120, "1.2.0").upload!!
            assertEquals(CommunityConfigs.Receipt("0123456789abcdef", token), client.upload(upload))
            assertEquals(CommunityConfigs.Votes(2, 0), client.vote("0123456789abcdef", "a".repeat(32), 1))
            client.delete("0123456789abcdef", token)

            val (list, share, vote, delete) = server.requests
            assertEquals("GET" to "/v1/titles/4D5307E6/configs", list.method to list.path)
            assertEquals("", list.body)
            assertEquals("POST" to "/v1/configs", share.method to share.path)
            assertTrue(share.headers["content-type"]!!.startsWith("application/json"))
            assertEquals(CommunityConfigs.encodeUpload(upload), share.body)
            assertEquals("POST" to "/v1/configs/0123456789abcdef/vote", vote.method to vote.path)
            assertEquals("""{"voter":"${"a".repeat(32)}","vote":1}""", vote.body)
            assertEquals("DELETE" to "/v1/configs/0123456789abcdef", delete.method to delete.path)
            assertEquals(token, delete.headers["x-delete-token"])
            // No cookie, credential or other identity rides along.
            server.requests.forEach { request ->
                assertFalse(request.headers.keys.any { it in setOf("cookie", "authorization") || it.startsWith("x-client") })
                assertNull(if (request.method != "DELETE") request.headers["x-delete-token"] else null)
            }
        }
    }

    @Test fun refusalsAreToldApart() {
        var status = 400
        TinyHttpServer { TinyHttpServer.Answer(status, """{"error":"name must be 1-60 characters"}""") }.use { server ->
            val client = CommunityClient(server.base, allowLoopbackHttp = true)
            assertEquals("name must be 1-60 characters", expectFailure(Kind.REJECTED) { client.list("4D5307E6") }.detail)
            status = 403; expectFailure(Kind.FORBIDDEN) { client.delete("0123456789abcdef", token) }
            status = 404; expectFailure(Kind.NOT_FOUND) { client.vote("0123456789abcdef", "a".repeat(32), -1) }
            status = 429; expectFailure(Kind.BUSY) { client.list("4D5307E6") }
            status = 500; expectFailure(Kind.SERVER) { client.list("4D5307E6") }
            status = 200; expectFailure(Kind.INVALID_ANSWER) { client.vote("0123456789abcdef", "a".repeat(32), 1) }
        }
    }

    @Test fun aRedirectIsNotFollowedAndAHugeAnswerNotRead() {
        TinyHttpServer { request ->
            if (request.path.contains("4D5307E6")) TinyHttpServer.Answer(302, "", mapOf("Location" to "http://127.0.0.1:9/elsewhere"))
            else TinyHttpServer.Answer(200, "x".repeat(CommunityConfigs.MAX_LIST_BYTES + 1))
        }.use { server ->
            val client = CommunityClient(server.base, allowLoopbackHttp = true)
            expectFailure(Kind.SERVER) { client.list("4D5307E6") }
            expectFailure(Kind.INVALID_ANSWER) { client.list("415607E6") }
            assertEquals(2, server.requests.size)
        }
    }

    @Test fun noServerIsANetworkFailure() {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }  // free, then closed
        expectFailure(Kind.NETWORK) { CommunityClient("http://127.0.0.1:$port", allowLoopbackHttp = true).list("4D5307E6") }
    }

    @Test fun badArgumentsNeverReachTheNetwork() {
        TinyHttpServer { TinyHttpServer.Answer(200) }.use { server ->
            val client = CommunityClient(server.base, allowLoopbackHttp = true)
            listOf<() -> Unit>(
                { client.list("../../etc") },
                { client.vote("../x", "a".repeat(32), 1) },
                { client.vote("0123456789abcdef", "not-hex", 1) },
                { client.vote("0123456789abcdef", "a".repeat(32), 2) },
                { client.delete("0123456789abcdef", "short") },
            ).forEach { call ->
                try {
                    call()
                    fail()
                } catch (expected: IllegalArgumentException) {
                }
            }
            assertEquals(0, server.requests.size)
        }
    }

    @Test fun theServiceKeepsTokensVotesAndTheLastList() {
        var listed = listFor("4D5307E6", oneConfig)
        var deleteStatus = 204
        TinyHttpServer { request ->
            when {
                request.method == "GET" -> TinyHttpServer.Answer(200, listed)
                request.path.endsWith("/vote") -> TinyHttpServer.Answer(200, """{"votesUp":2,"votesDown":0}""")
                request.method == "POST" -> TinyHttpServer.Answer(201, """{"id":"fedcba9876543210","deleteToken":"$token"}""")
                else -> TinyHttpServer.Answer(deleteStatus, if (deleteStatus == 404) """{"error":"no such config"}""" else "")
            }
        }.use { server ->
            val store = CommunityStore(File(folder.root, "community"))
            val service = CommunityService(CommunityClient(server.base, allowLoopbackHttp = true), store, "127.0.0.1", 120, "1.2.0")
            assertNull(service.cached("4D5307E6", s23).fetchedAt)
            assertEquals(0, server.requests.size)                       // opening the screen asks nothing

            val view = service.refresh("4D5307E6", s23)
            assertEquals(listOf("0123456789abcdef"), view.listing.entries.map { it.config.id })
            assertEquals(CommunityConfigs.Match.MODEL, view.listing.entries.single().match)
            assertEquals(view.listing.entries, service.cached("4D5307E6", s23).listing.entries)

            service.vote("0123456789abcdef", 1)
            assertEquals(mapOf("0123456789abcdef" to 1), service.cached("4D5307E6", s23).myVotes)
            val voter = server.requests.last().body
            assertTrue(voter.contains(CommunityConfigs.voterId(store.secret(), "0123456789abcdef")))

            val draft = service.draft("4D5307E6", "Mine", "Works", CompatStatus.IN_GAME, mapOf("GPU|framerate_limit" to "30"), s23)
            service.share(draft.upload!!)
            assertEquals(setOf("fedcba9876543210"), service.cached("4D5307E6", s23).mine.keys)
            assertTrue(service.cached("415607E6", s23).mine.isEmpty())

            // Deleting one the server no longer has forgets it here too; another failure keeps it.
            deleteStatus = 500
            expectFailure(Kind.SERVER) { service.delete("fedcba9876543210") }
            assertEquals(1, store.uploads().size)
            deleteStatus = 404
            service.delete("fedcba9876543210")
            assertTrue(store.uploads().isEmpty())
            listed = listFor("4D5307E6")
            assertTrue(service.refresh("4D5307E6", s23).listing.entries.isEmpty())
        }
    }
}
