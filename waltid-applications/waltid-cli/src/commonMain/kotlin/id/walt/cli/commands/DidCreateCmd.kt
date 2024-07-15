package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.*
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import kotlinx.coroutines.runBlocking

class DidCreateCmd : CliktCommand(
    name = "create",
    help = """Create a Decentralized Identifier (DID).
        
        Example usage:
        --------------
        waltid did create 
        waltid did create -k myKey.json
        waltid did create -m jwk
    """
) {

    init {
        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    val print: PrettyPrinter = PrettyPrinter(this)

    private val method by option("-m", "--method")
        .help("The DID method to be used.")
        .enum<DidMethod>(ignoreCase = true)
        .default(DidMethod.KEY)

    private val keyFile by option("-k", "--key")
        .help("The subject's key to be used. If none is provided, a new one will be generated.")
        .file(canBeDir = false)
    private val useJwkJcsPub by option("-j", "--useJwkJcsPub")
        .help("Flag to enable JWK_JCS-Pub encoding (default=off). Applies only to the did:key method and is relevant in the context of EBSI.")
        .flag(default = false)

    private val webDomain by option("-wd", "--web-domain")
        .help("The domain name to use when creating a did:web (required in this case).")

    private val webPath by option("-wp", "--web-path")
        .help("The URL path to append when creating a did:web (optional).")
        .default("")

    override fun run() {
        runBlocking {
            val key = KeyUtil(this@DidCreateCmd).getKey(keyFile)

            val jwk = key.exportJWKPretty()

            print.green("DID Subject key to be used:")
            print.box(jwk)

            if (method == DidMethod.WEB) {
                if (webDomain == null || webDomain == "")
                    throw IllegalArgumentException("The web domain cannot be null or any empty string when creating a did:web.")
            }

            val result = if (useJwkJcsPub && method == DidMethod.KEY) {
                DidUtil.createDid(method, key, DidKeyCreateOptions(key.keyType, useJwkJcsPub))
            } else if (method == DidMethod.WEB) {
                if (webDomain == null || webDomain == "")
                    throw IllegalArgumentException("The web domain cannot be null or any empty string when creating a did:web.")
                DidUtil.createDid(
                    method,
                    key,
                    DidWebCreateOptions(domain = webDomain!!, path = webPath, keyType = key.keyType)
                )
            } else
                DidUtil.createDid(method, key)

            print.green("DID created:")
            // print.box(result) // Can't be used because truncates long DIDs
            print.plain(result)
        }
    }
}
