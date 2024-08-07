package id.walt.webwallet.db.models

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.oid4vc.data.CredentialFormat
import id.walt.webwallet.manifest.provider.ManifestProvider
import id.walt.webwallet.utils.JsonUtils
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object WalletCredentials : Table("credentials") {
    val wallet = reference("wallet", Wallets)
    val id = varchar("id", 256)

    val document = text("document")
    val disclosures = text("disclosures").nullable()

    val addedOn = timestamp("added_on")
    val manifest = text("manifest").nullable()

    val deletedOn = timestamp("deleted_on").nullable().default(null)
    val pending = bool("pending").default(false)
    val format = varchar("format", 32).default(CredentialFormat.jwt_vc_json.value)

    override val primaryKey = PrimaryKey(wallet, id)
}

@Serializable
data class WalletCredential(
    val wallet: UUID,
    val id: String,
    val document: String,
    val disclosures: String?,
    val addedOn: Instant,
    @Transient
    val manifest: String? = null,
    val deletedOn: Instant?,
    val pending: Boolean = false,
    val format: CredentialFormat,
    val parsedDocument: JsonObject? = parseDocument(document, id, format),
    @SerialName("manifest")
    val parsedManifest: JsonObject? = tryParseManifest(manifest),
) {

    companion object {
        fun parseDocument(document: String, id: String, format: CredentialFormat) =
            runCatching {
                when(format) {
                    CredentialFormat.ldp_vc -> Json.parseToJsonElement(document).jsonObject
                    CredentialFormat.jwt_vc, CredentialFormat.sd_jwt_vc, CredentialFormat.jwt_vc_json,
                    CredentialFormat.jwt_vc_json_ld -> document.decodeJws().payload
                        .run { jsonObject["vc"]?.jsonObject ?: jsonObject }
                    CredentialFormat.mso_mdoc -> null // TODO: parse mdoc as json object for wallet UI (?)
                    else -> throw IllegalArgumentException("Unknown credential format")
                }?.toMutableMap().also {
                    it?.putIfAbsent("id", JsonPrimitive(id))
                }?.let {
                    JsonObject(it)
                }
            }.onFailure { it.printStackTrace() }
                .getOrNull()

        fun tryParseManifest(manifest: String?) = runCatching {
            manifest?.let { ManifestProvider.json.decodeFromString<JsonObject>(it) }
        }.fold(onSuccess = {
            it
        }, onFailure = {
            null
        })

        fun parseIssuerDid(credential: JsonObject?, manifest: JsonObject? = null) =
            credential?.jsonObject?.get("issuer")?.let {
                if (it is JsonObject) it.jsonObject["id"]?.jsonPrimitive?.content
                else it.jsonPrimitive.content
            } ?: manifest?.jsonObject?.get("input")?.jsonObject?.get("issuer")?.jsonPrimitive?.content

        fun getManifestLogo(manifest: JsonObject?) =
            manifest?.let { JsonUtils.tryGetData(it, "display.card.logo.uri")?.jsonPrimitive?.content }

        fun getManifestIssuerName(manifest: JsonObject?) =
            manifest?.let { JsonUtils.tryGetData(it, "display.card.issuedBy")?.jsonPrimitive?.content }
    }


    constructor(result: ResultRow) : this(
        wallet = result[WalletCredentials.wallet].value,
        id = result[WalletCredentials.id],
        document = result[WalletCredentials.document],
        disclosures = result[WalletCredentials.disclosures],
        addedOn = result[WalletCredentials.addedOn].toKotlinInstant(),
        manifest = result[WalletCredentials.manifest],
        deletedOn = result[WalletCredentials.deletedOn]?.toKotlinInstant(),
        pending = result[WalletCredentials.pending],
        format = CredentialFormat.fromValue(result[WalletCredentials.format]) ?: throw IllegalArgumentException("Credential format couldn't be decoded")
    )
}
