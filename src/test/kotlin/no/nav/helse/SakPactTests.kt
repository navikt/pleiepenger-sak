package no.nav.helse

import au.com.dius.pact.consumer.Pact
import au.com.dius.pact.consumer.PactProviderRuleMk2
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.model.RequestResponsePact
import org.junit.Rule
import au.com.dius.pact.consumer.PactVerification
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import no.nav.helse.sak.gateway.SakGateway
import no.nav.helse.sak.v1.MeldingV1
import no.nav.helse.sak.v1.MetadataV1
import no.nav.helse.sak.v1.SakV1Service
import no.nav.helse.systembruker.Response
import no.nav.helse.systembruker.SystembrukerGateway
import no.nav.helse.systembruker.SystembrukerService
import org.junit.Test
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*
import kotlin.test.assertEquals

private const val provider = "sak"
private const val consumer = "pleiepenger-sak"
private const val jwt = "dummy"
private const val aktoerId = "1831212532188"
private const val sakId = "137662692"
private const val correlationId = "50e93afa-a22d-448a-8fbe-a4a4dae67eb0"

private val logger: Logger = LoggerFactory.getLogger("nav.SakPactTests")

class SakPactTests {
    @Rule
    @JvmField
    val mockProvider = PactProviderRuleMk2(provider, this)

    init {
        System.setProperty("pact.rootDir", "${System.getProperty("user.dir")}/pacts")
    }

    @Pact(consumer = consumer)
    @SuppressWarnings("unused")
    fun oppretteSakPact(builder: PactDslWithProvider): RequestResponsePact {

        val requestBody = PactDslJsonBody()
            .stringValue("tema", "OMS")
            .stringValue("applikasjon", "FS22")
            .stringMatcher("aktoerId", "\\d+", aktoerId)

        val headers = mapOf(
            Pair(HttpHeaders.ContentType, "application/json"),
            Pair(HttpHeaders.Accept, "application/json"),
            Pair(HttpHeaders.Authorization, "Bearer $jwt")
        )

        return builder
            .given("Klar for aa opprette nye saker")
            .uponReceiving("Request for aa opprette ny sak")
            .path("/api/v1/saker")
            .method("POST")
            .headers(headers)
            .matchHeader(
                HttpHeaders.XCorrelationId,
                "\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b",
                correlationId
            )
            .body(requestBody)
            .willRespondWith()
            .headers(mapOf(Pair(HttpHeaders.ContentType, "application/json")))
            .status(201)
            .body(
                PactDslJsonBody().stringMatcher("id", "\\d+", sakId)
            )
            .toPact()
    }

    @Test
    @PactVerification(provider)
    fun opprettelseAvSakFungerer() {
        val sakService = sakService()
        runBlocking {
            val actualSakId = sakService.opprettSak(
                melding = MeldingV1(aktoerId = aktoerId),
                metaData = MetadataV1(
                    version = 1,
                    correlationId = correlationId,
                    requestId = UUID.randomUUID().toString()
                )
            )
            assertEquals(sakId, actualSakId.value)
        }
    }

    private fun sakService(): SakV1Service {
        return SakV1Service(sakGateway = sakGateway())
    }

    private fun sakGateway(): SakGateway {
        val httpClient = HttpClient(Apache) {
            install(JsonFeature) {
                serializer = JacksonSerializer { ObjectMapper.sak(this) }
            }
        }

        return SakGateway(
            sakBaseUrl = URL(mockProvider.url),
            systembrukerService = SystembrukerService(
                systembrukerGateway = mockSystembrukerGateway()
            ),
            httpClient = httpClient
        )
    }

    private fun mockSystembrukerGateway(): SystembrukerGateway {
        val mock = Mockito.mock(SystembrukerGateway::class.java)
        runBlocking {
            Mockito.`when`(mock.getToken()).thenReturn(
                Response(
                    accessToken = jwt,
                    expiresIn = 5000
                )
            )
        }
        return mock
    }
}

fun main(args: Array<String>) {
    val reqid = UUID.randomUUID().toString()
    println("reqid = ${reqid}")
    val corrid = UUID.randomUUID().toString()
    println("corrid = $corrid")
}

