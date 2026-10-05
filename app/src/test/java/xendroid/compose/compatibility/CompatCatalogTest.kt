package xendroid.compose.compatibility

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.compatibility.CompatCatalog.Freshness
import xendroid.compose.compatibility.CompatCatalog.Verified

class CompatCatalogTest {
    @get:Rule val folder = TemporaryFolder()

    private val origin = "https://catalog.example/xendroid/catalog.json"
    private val day = 24L * 3600 * 1000
    private val now = 1_759_363_200_000L                        // a fixed instant (2025-10-02 UTC)
    private val publisher: KeyPair = keyPair("secp256r1")
    private val keys = mapOf("k1" to publisher.public)

    private fun keyPair(curve: String): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(curve)) }.generateKeyPair()

    private fun payload(
        sequence: Long = 7,
        generatedAt: Long = now - day,
        expiresAt: Long = now + 30 * day,
        signedFor: String = origin,
        titles: String = """{"4d5307e6":[{"status":"PLAYABLE","build":"b1","gpu":"Adreno (TM) 740","date":"2026-09-30","count":2}]}""",
        extra: String = "",
    ) = """{"format":"xendroid-compat-catalog","version":1,"origin":"$signedFor","sequence":$sequence,"generatedAt":$generatedAt,""" +
        """"expiresAt":$expiresAt,"titles":$titles$extra}"""

    private fun envelope(payload: String, keyId: String = "k1", signer: KeyPair = publisher, signed: String = payload): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(signer.private); update(signed.toByteArray()); sign()
        }
        val b64 = Base64.getEncoder()
        return ("""{"format":"xendroid-catalog-envelope","version":1,"keyId":"$keyId",""" +
            """"payload":"${b64.encodeToString(payload.toByteArray())}","signature":"${b64.encodeToString(signature)}"}""").toByteArray()
    }

    private fun verify(bytes: ByteArray, minSequence: Long? = null) = CompatCatalog.verify(bytes, origin, keys, minSequence, now)
    private fun refusal(bytes: ByteArray, minSequence: Long? = null) = (verify(bytes, minSequence) as Verified.Refused).reason

    @Test fun onlyTheTrustedKeysSignatureOverTheExactBytesIsTaken() {
        val ok = verify(envelope(payload())) as Verified.Ok
        assertEquals("k1", ok.keyId)
        assertEquals(setOf("4D5307E6"), ok.payload.titles.keys)              // Title IDs upper-cased
        assertEquals("signed with a key this build does not trust", refusal(envelope(payload(), keyId = "k2")))
        assertEquals("the signature does not match", refusal(envelope(payload(), signer = keyPair("secp256r1"))))
        // One changed byte (a better result) after signing.
        val tampered = payload().replace("\"count\":2", "\"count\":9")
        assertEquals("the signature does not match", refusal(envelope(tampered, signed = payload())))
        assertEquals("not a catalog", refusal("<html>".toByteArray()))
        assertTrue(refusal(ByteArray(CompatCatalog.MAX_BYTES + 1)).startsWith("larger than"))
    }

    @Test fun aCatalogForAnotherAddressAnOlderOneOrAnUnknownFormatIsRefused() {
        assertTrue(refusal(envelope(payload(signedFor = "https://catalog.example/beta.json"))).startsWith("signed for another catalog"))
        assertEquals("older than the copy already here (6 < 7)", refusal(envelope(payload(sequence = 6)), minSequence = 7))
        assertTrue(verify(envelope(payload(sequence = 7)), minSequence = 7) is Verified.Ok)
        assertEquals("dated in the future; check the phone's clock", refusal(envelope(payload(generatedAt = now + 2 * day))))
        assertEquals("expires before it was made", refusal(envelope(payload(expiresAt = now - 2 * day))))
        // A field this build does not know could change what a result means: not read at all.
        assertEquals("the signed content is not a catalog this build reads", refusal(envelope(payload(extra = ""","fork":"other""""))))
        assertEquals("invalid Title ID 00000000", refusal(envelope(payload(titles = """{"00000000":[]}"""))))
        assertEquals("a damaged result for 4D5307E6", refusal(envelope(payload(titles =
            """{"4D5307E6":[{"status":"PLAYABLE","build":"b1","gpu":"g","date":"30/09/2026"}]}"""))))
    }

    @Test fun openSslSignaturesVerify() {
        // `openssl ecparam -name prime256v1 -genkey`, `openssl dgst -sha256 -sign key.pem payload.json`;
        // the private key was discarded after making this vector.
        val key = CompatCatalog.publicKey("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE16fKh/AtvLoFGgnLIV9k6pYLQpw1NYzBgtokpb4zR/qC4HTMXZu7j9skB1efnEqlKkl2VY5wAEAsB8i4xNJoBA==")!!
        val envelope = """{"format":"xendroid-catalog-envelope","version":1,"keyId":"ossl","payload":"eyJmb3JtYXQiOiJ4ZW5kcm9pZC1jb21wYXQtY2F0YWxvZyIsInZlcnNpb24iOjEsIm9yaWdpbiI6Imh0dHBzOi8vY2F0YWxvZy5leGFtcGxlL3hlbmRyb2lkL2NhdGFsb2cuanNvbiIsInNlcXVlbmNlIjo3LCJnZW5lcmF0ZWRBdCI6MTc1OTI3NjgwMDAwMCwiZXhwaXJlc0F0IjoxNzYxODY4ODAwMDAwLCJ0aXRsZXMiOnsiNEQ1MzA3RTYiOlt7InN0YXR1cyI6IlBMQVlBQkxFIiwiYnVpbGQiOiI3NzAxMWEwYyIsImdwdSI6IkFkcmVubyAoVE0pIDc0MCIsImRyaXZlciI6IlF1YWxjb21tIDAuNzYyIiwiYW5kcm9pZCI6MzQsImRhdGUiOiIyMDI2LTA5LTMwIiwiY291bnQiOjJ9XX19","signature":"MEQCIG2ppkpQdttMuW+urF/L1JaMi7FQyNV+q3DDgY+lQNGiAiAtjr9JApM3nvxQTjg4inZe447nj7NUQpYnlBgHPlempg=="}"""
        val ok = CompatCatalog.verify(envelope.toByteArray(), origin, mapOf("ossl" to key), null, now) as Verified.Ok
        assertEquals(7L, ok.payload.sequence)
        assertEquals(2, ok.payload.titles.getValue("4D5307E6").single().count)
    }

    @Test fun onlyP256KeysAndHttpsAddressesConfigureACatalog() {
        val p256 = Base64.getEncoder().encodeToString(publisher.public.encoded)
        val p384 = Base64.getEncoder().encodeToString(keyPair("secp384r1").public.encoded)
        assertNotNull(CompatCatalog.publicKey(p256))
        assertNull(CompatCatalog.publicKey(p384))
        assertNull(CompatCatalog.publicKey("not base64!"))
        assertNull(CatalogConfig.parse("", ""))                                  // this build: no publisher
        assertNull(CatalogConfig.parse(origin, ""))
        assertNull(CatalogConfig.parse("http://catalog.example/c.json", "k1:$p256"))
        assertNull(CatalogConfig.parse(origin, "k1:$p384"))                       // one bad key refuses all
        val config = CatalogConfig.parse(origin, " k1:$p256 , k2:$p256 ")!!
        assertEquals(setOf("k1", "k2"), config.keys.keys)
    }

    @Test fun aCopyIsFreshThenOutOfDateThenGone() {
        val p = (verify(envelope(payload(expiresAt = now + 30 * day))) as Verified.Ok).payload
        val fetched = now
        assertEquals(Freshness.FRESH, CompatCatalog.freshness(p, fetched, now + 7 * day))        // the client's limit first
        assertEquals(Freshness.STALE, CompatCatalog.freshness(p, fetched, now + 7 * day + 1))
        assertEquals(Freshness.STALE, CompatCatalog.freshness(p, fetched, now + 97 * day))
        assertEquals(Freshness.EXPIRED, CompatCatalog.freshness(p, fetched, now + 97 * day + 1))
        val short = (verify(envelope(payload(expiresAt = now + day))) as Verified.Ok).payload
        assertEquals(Freshness.STALE, CompatCatalog.freshness(short, fetched, now + 2 * day))     // the publisher's expiry first
    }

    @Test fun resultsAreGroupedBySetupThisPhonesFirstAndNeverMerged() {
        val titles = """{"4D5307E6":[
            {"status":"IN_GAME","build":"b0","gpu":"Mali-G715","date":"2026-09-29"},
            {"status":"PLAYABLE","build":"b0","gpu":"Adreno (TM) 740","driver":"Qualcomm 0.762","date":"2026-09-01","count":2},
            {"status":"PLAYABLE","build":"b1","gpu":"adreno (tm) 740 ","driver":"Qualcomm 0.762","date":"2026-09-20","count":3,"note":"steady"},
            {"status":"INTRO","build":"b1","gpu":"Adreno (TM) 740","driver":"Qualcomm 0.762","date":"2026-09-25"},
            {"status":"PLAYABLE","build":"b1","gpu":"Adreno (TM) 740","driver":"Turnip 25.1","date":"2026-09-10"}]}"""
        val p = (verify(envelope(payload(titles = titles))) as Verified.Ok).payload
        val results = CompatCatalog.resultsFor(p, "4d5307e6", "b1", "Adreno (TM) 740")
        assertEquals(listOf("b1|Qualcomm 0.762", "b1|Turnip 25.1", "b0|Qualcomm 0.762", "b0|"),
            results.map { "${it.build}|${it.driver}" })
        assertEquals(listOf(true, true, false, false), results.map { it.thisSetup })
        assertEquals(listOf(true, true, true, false), results.map { it.sameGpu })
        assertEquals("Playable ×3 · Intro or menus only ×1", results[0].summary)
        assertEquals("2026-09-25", results[0].latestDate)
        assertEquals(listOf("steady"), results[0].notes)
        assertFalse(results.any { it.counts.values.sum() > 4 })              // no setup absorbed another
        assertTrue(CompatCatalog.resultsFor(p, "41560817", "b1", null).isEmpty())
        assertTrue(CompatCatalog.resultsFor(p, "4D5307E6", "b1", null).none { it.thisSetup })   // GPU unknown: no match
    }

    @Test fun theKeptCopyIsVerifiedOnEveryReadAndNeverRolledBack() {
        var clock = now
        var served: () -> ByteArray = { envelope(payload(sequence = 7)) }
        val config = CatalogConfig(origin, keys)
        val dir = File(folder.root, "catalog")
        val store = CompatCatalogStore(dir, config, { url -> assertEquals(origin, url); served() }, clock = { clock })
        assertNull(store.copy())
        assertEquals(CompatCatalogStore.Refresh.Updated(7), store.refresh())
        assertEquals(7L, store.copy()!!.payload.sequence)
        clock += day
        assertEquals(CompatCatalogStore.Refresh.Unchanged, store.refresh())
        assertEquals(now + day, store.copy()!!.fetchedAt)                    // the same publication, checked again today
        served = { envelope(payload(sequence = 6)) }
        assertEquals(CompatCatalogStore.Refresh.Refused("older than the copy already here (6 < 7)"), store.refresh())
        served = { throw IOException("no route to host") }
        assertEquals(CompatCatalogStore.Refresh.Failed("no route to host"), store.refresh())
        assertEquals(7L, store.copy()!!.payload.sequence)                    // offline: the copy stays
        served = { envelope(payload(sequence = 8, generatedAt = clock - day, expiresAt = clock + 30 * day)) }
        assertEquals(CompatCatalogStore.Refresh.Updated(8), store.refresh())
        // Out of date for too long: not shown, yet still the floor for what is taken next.
        clock += 200 * day
        assertNull(store.copy())
        served = { envelope(payload(sequence = 7, generatedAt = clock - day, expiresAt = clock + 30 * day)) }
        assertTrue(store.refresh() is CompatCatalogStore.Refresh.Refused)
        // A changed copy on disk does not verify, so it is not used.
        val kept = File(dir, "catalog.json")
        kept.writeText(kept.readText().replace("k1", "k9"))
        assertNull(store.copy())
    }

    @Test fun downloadsAreCappedNotCut() {
        assertEquals(5, CatalogHttp.readCapped(ByteArrayInputStream(ByteArray(5)), 5).size)
        val error = runCatching { CatalogHttp.readCapped(ByteArrayInputStream(ByteArray(6)), 5) }.exceptionOrNull()
        assertTrue(error is IOException)
    }
}
