package id.walt.webwallet.service.issuers

import id.walt.commons.config.ConfigManager
import id.walt.webwallet.config.RegistrationDefaultsConfig
import id.walt.webwallet.db.models.WalletIssuers
import kotlinx.serialization.Serializable
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class IssuerDataTransferObject(
    val wallet: UUID,
    val did: String,
    val description: String? = "no description",
    val uiEndpoint: String = "",
    val configurationEndpoint: String = "",
    val authorized: Boolean = false,
) {
    constructor(resultRow: ResultRow) : this(
        wallet = resultRow[WalletIssuers.wallet].value,
        did = resultRow[WalletIssuers.did],
        description = resultRow[WalletIssuers.description],
        uiEndpoint = resultRow[WalletIssuers.uiEndpoint],
        configurationEndpoint = resultRow[WalletIssuers.configurationEndpoint],
        authorized = resultRow[WalletIssuers.authorized],
    )

    companion object {
        fun default(wallet: UUID): IssuerDataTransferObject? {
            val defaultGenerationConfig by lazy { ConfigManager.getConfig<RegistrationDefaultsConfig>() }
            return if (defaultGenerationConfig.defaultIssuerConfig != null) {
                IssuerDataTransferObject(
                    wallet = wallet,
                    did = defaultGenerationConfig.defaultIssuerConfig!!.did,
                    description = defaultGenerationConfig.defaultIssuerConfig?.description ?: "",
                    uiEndpoint = defaultGenerationConfig.defaultIssuerConfig!!.uiEndpoint,
                    configurationEndpoint = defaultGenerationConfig.defaultIssuerConfig!!.configurationEndpoint,
                    authorized = defaultGenerationConfig.defaultIssuerConfig?.authorized ?: false
                )
            } else {
                null
            }
        }
    }
}
