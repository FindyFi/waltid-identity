package id.walt.webwallet.web.controllers.exchange

import id.walt.webwallet.db.models.WalletCredential
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiResponses
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject

object OpenAPICommons {
    const val rootPath = "exchange"

    fun route(): OpenApiRoute.() -> Unit = {
        tags = listOf("Credential exchange")
    }

    fun usePresentationRequestResponse(): OpenApiResponses.() -> Unit = {
        HttpStatusCode.OK to {
            description = "Successfully presented credentials"
            body<JsonObject> {
                description = """{"redirectUri": String}"""
            }
        }
        HttpStatusCode.BadRequest to {
            description = "Presentation was not accepted"
            body<JsonObject> {
                description = """{"redirectUri": String?, "errorMessage": String}"""
            }
        }
    }

    fun useOfferRequestEndpointResponseParams(): OpenApiResponses.() -> Unit = {
        HttpStatusCode.OK to {
            body<List<WalletCredential>> {
                description = "List of credentials"
            }
        }
    }
}