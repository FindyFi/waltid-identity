package id.walt.crypto.keys.aws

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.InternalAPI
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.alternativeParsing
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.kotlincrypto.core.InternalKotlinCryptoApi
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import kotlin.io.encoding.ExperimentalEncodingApi

private val logger = KotlinLogging.logger { }

class AWSKey(
    val config: AWSKeyMetadata,
    val id: String,
    private var _publicKey: String? = null,
    private var _keyType: KeyType? = null
) : Key() {


    override var keyType: KeyType
        get() = _keyType!!
        set(value) {
            _keyType = value
        }

    override val hasPrivateKey: Boolean
        get() = false

    override fun toString(): String = "[AWS ${keyType.name} key @AWS]"

    override suspend fun getKeyId(): String = getPublicKey().getKeyId()

    override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    override suspend fun exportJWK(): String {
        TODO("Not yet implemented")
    }

    override suspend fun exportJWKObject(): JsonObject = Json.parseToJsonElement(_publicKey!!).jsonObject

    override suspend fun exportPEM(): String {
        TODO("Not yet implemented")
    }

    private val AwsSigningAlgorithm by lazy {
        when (keyType) {
            KeyType.secp256r1 -> "ECC_NIST_P256"
            KeyType.RSA -> "RSA_2048"
            else -> throw KeyTypeNotSupportedException(keyType.name)
        }
    }
    override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        val body = """
{
"KeyId":"$id",
"Message":"${plaintext.encodeBase64()}",
"MessageType":"RAW",
"SigningAlgorithm":"ECDSA_SHA_256"
}
""".trimIndent().trimMargin()
        println("body : $body")
        val headers = buildSigV4Headers(
            HttpMethod.Post,
            payload = body,
            config = config
        )
        val signature = client.post("https://kms.${config.region}.amazonaws.com/") {
            headers {
                headers.forEach { (key, value) -> append(key, value) } // Append each SigV4 header to the request
                append(HttpHeaders.Host, "kms.${config.region}.amazonaws.com")
                append("X-Amz-Target", "TrentService.Sign") // Specific KMS action for CreateKey
            }
            setBody(body) // Set the JSON body
        }.awsJsonDataBody()
        println("signature : ${signature["Signature"]?.jsonPrimitive?.content}")
        return signature["Signature"]?.jsonPrimitive?.content?.toByteArray() ?: throw Error("failed to sign")
    }

    override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>
    ): String {
        TODO("Not yet implemented")
    }

    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?
    ): Result<ByteArray> {
        TODO("Not yet implemented")
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        TODO("Not yet implemented")
    }

    override suspend fun getPublicKey(): Key {
        TODO("Not yet implemented")
    }

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun getMeta(): KeyMeta {
        TODO("Not yet implemented")
    }

    companion object {


    }

}

@OptIn(InternalKotlinCryptoApi::class, ExperimentalStdlibApi::class, InternalAPI::class, ExperimentalEncodingApi::class)
suspend fun main() {

    val ISO_DATETIME_BASIC by lazy {
        LocalDateTime.Format {
            date(LocalDate.Formats.ISO_BASIC)
            alternativeParsing({ char('t') }) { char('T') }
            time(LocalTime.Format {
                hour()
                minute()
                second()
            })
            char('Z')
        }
    }
    val time = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    println(time.format(ISO_DATETIME_BASIC)) // 20241002T162417Z

    val endpoint = "kms.eu-central-1.amazonaws.com"
    val algorithm = "AWS4-HMAC-SHA256"

    val credential =
        "AKIAWFMCC3FM2U2TQU5F/20241002/eu-central-1/kms/aws4_request" //AKIAWFMCC3FMR43YUFO6/20130728/us-east-1/kms/aws4_request
    val signedHeaders = "content-type;host;x-amz-date;x-amz-algorithm;x-amz-target"
    val method = "POST"
    val AWS_SECRET_ACCESS_KEY = ""
    val canonicalUri = "/"
    val canonicalQueryString = ""


    val canonicalHeaders =
        "content-type:application/x-amz-json-1.1\nhost:$endpoint\nx-amz-date:${time.format(ISO_DATETIME_BASIC)}\nx-amz-algorithm:AWS4-HMAC-SHA256\nx-amz-target:TrentService.ListKeys\n".trimIndent()


    val digestHashedPayload = SHA256()
    val HashedPayload = digestHashedPayload.digest("".encodeToByteArray()).toHexString()


    val canonicalRequest = listOf(
        method,
        canonicalUri,
        canonicalQueryString,
        canonicalHeaders,
        signedHeaders,
        HashedPayload
    ).joinToString("\n")

    val digest = SHA256()
    val hashedcanonicalRequest = digest.digest(canonicalRequest.encodeToByteArray()).toHexString()


    val stringToSign = """
        $algorithm
        ${time.format(ISO_DATETIME_BASIC)}
        20241002/eu-central-1/kms/aws4_request
        $hashedcanonicalRequest
    """.trimIndent()
    println("Canonical headers:\n$canonicalHeaders")
    println("========================================")
    println("Canonical Request:\n$canonicalRequest")
    println("========================================")
    println("Hashed Canonical Request: $hashedcanonicalRequest")
    println("========================================")
    println("String to Sign:\n$stringToSign")
    println("========================================")


    val macDate = HmacSHA256("AWS4$AWS_SECRET_ACCESS_KEY".encodeToByteArray())
    val DateKey = macDate.doFinal("20241002".encodeToByteArray())
    println("dateKey : $DateKey")

    val macDateRegionKey = HmacSHA256(DateKey)
    val DateRegionKey = macDateRegionKey.doFinal("eu-central-1".encodeToByteArray())
    println("dateRegion : $DateRegionKey")


    val macDateRegionServiceKey = HmacSHA256(DateRegionKey)
    val DateRegionServiceKey = macDateRegionServiceKey.doFinal("kms".encodeToByteArray())
    println("dateReginService : $DateRegionServiceKey")

    val macSigningKey = HmacSHA256(DateRegionServiceKey)
    val SigningKey = macSigningKey.doFinal("aws4_request".encodeToByteArray())
    println("SigningKey : $SigningKey")

    val macsignature = HmacSHA256(SigningKey)
    val signature = macsignature.doFinal(stringToSign.encodeToByteArray())
    println("the signature is $signature , in hex ${signature.toHexString()}")
    println("========================================")


    val authorizationRequest =
        "AWS4-HMAC-SHA256 Credential=$credential,SignedHeaders=$signedHeaders,Signature=${signature.toHexString()}"


    println("Authorization Request: $authorizationRequest")

    val http = HttpClient {
        install(ContentNegotiation) { json() }
        defaultRequest {
            header(HttpHeaders.Date, time.format(ISO_DATETIME_BASIC))
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }

    val response = http.post("https://$endpoint") {
        header(HttpHeaders.ContentType, "application/x-amz-json-1.1")
        header(HttpHeaders.Host, endpoint)
        header(HttpHeaders.Authorization, authorizationRequest)
        header("X-Amz-Date", time.format(ISO_DATETIME_BASIC))
        header("X-Amz-Algorithm", "AWS4-HMAC-SHA256")
        header("X-Amz-Target", "TrentService.ListKeys")


    }

    response.bodyAsText().let { println(it) }

}

