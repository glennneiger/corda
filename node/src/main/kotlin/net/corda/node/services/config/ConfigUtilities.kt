package net.corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigRenderOptions
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.nodeapi.internal.*
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.config.toProperties
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.loadKeyStore
import net.corda.nodeapi.internal.crypto.save
import org.slf4j.LoggerFactory
import java.nio.file.Path

fun configOf(vararg pairs: Pair<String, Any?>): Config = ConfigFactory.parseMap(mapOf(*pairs))
operator fun Config.plus(overrides: Map<String, Any?>): Config = ConfigFactory.parseMap(overrides).withFallback(this)

object ConfigHelper {

    private const val CORDA_PROPERTY_PREFIX = "corda."

    private val log = LoggerFactory.getLogger(javaClass)
    fun loadConfig(baseDirectory: Path,
                   configFile: Path = baseDirectory / "node.conf",
                   allowMissingConfig: Boolean = false,
                   configOverrides: Config = ConfigFactory.empty()): Config {
        val parseOptions = ConfigParseOptions.defaults()
        val defaultConfig = ConfigFactory.parseResources("reference.conf", parseOptions.setAllowMissing(false))
        val appConfig = ConfigFactory.parseFile(configFile.toFile(), parseOptions.setAllowMissing(allowMissingConfig))

        val systemOverrides = ConfigFactory.systemProperties().cordaEntriesOnly()
        val finalConfig = systemOverrides
                .withFallback(configOf(
                // Add substitution values here
                "baseDirectory" to baseDirectory.toString()))
                .withFallback(configOverrides)
                .withFallback(appConfig)
                .withFallback(defaultConfig)
                .resolve()


        log.info("Config:\n${finalConfig.root().render(ConfigRenderOptions.defaults())}")

        val entrySet = finalConfig.entrySet().filter { entry -> entry.key.contains("\"") }
        for (mutableEntry in entrySet) {
            val key = mutableEntry.key
            log.error("Config files should not contain \" in property names. Please fix: $key")
        }

        return finalConfig
    }

    private fun Config.cordaEntriesOnly(): Config {

        return ConfigFactory.parseMap(toProperties().filterKeys { (it as String).startsWith(CORDA_PROPERTY_PREFIX) }.mapKeys { (it.key as String).removePrefix(CORDA_PROPERTY_PREFIX) })
    }
}

/**
 * Strictly for dev only automatically construct a server certificate/private key signed from
 * the CA certs in Node resources. Then provision KeyStores into certificates folder under node path.
 */
// TODO Move this to KeyStoreConfigHelpers
fun NodeConfiguration.configureWithDevSSLCertificate() = configureDevKeyAndTrustStores(myLegalName)

// TODO Move this to KeyStoreConfigHelpers
fun SSLConfiguration.configureDevKeyAndTrustStores(myLegalName: CordaX500Name) {
    certificatesDirectory.createDirectories()
    if (!trustStoreFile.exists()) {
        loadKeyStore(javaClass.classLoader.getResourceAsStream("certificates/$DEV_CA_TRUST_STORE_FILE"), DEV_CA_TRUST_STORE_PASS).save(trustStoreFile, trustStorePassword)
    }
    if (!sslKeystore.exists() || !nodeKeystore.exists()) {
        val (nodeKeyStore) = createDevKeyStores(myLegalName)

        // Move distributed service composite key (generated by IdentityGenerator.generateToDisk) to keystore if exists.
        val distributedServiceKeystore = certificatesDirectory / "distributedService.jks"
        if (distributedServiceKeystore.exists()) {
            val serviceKeystore = X509KeyStore.fromFile(distributedServiceKeystore, DEV_CA_KEY_STORE_PASS)
            nodeKeyStore.update {
                serviceKeystore.aliases().forEach {
                    if (serviceKeystore.internal.isKeyEntry(it)) {
                        setPrivateKey(it, serviceKeystore.getPrivateKey(it, DEV_CA_PRIVATE_KEY_PASS), serviceKeystore.getCertificateChain(it))
                    } else {
                        setCertificate(it, serviceKeystore.getCertificate(it))
                    }
                }
            }
        }
    }
}
