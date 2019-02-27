package no.nav.helse

import au.com.dius.pact.consumer.Pact
import au.com.dius.pact.consumer.PactProviderRuleMk2
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.model.RequestResponsePact
import org.junit.Rule
import au.com.dius.pact.consumer.PactVerification
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
private const val jwt = "eyJraWQiOiJhN2YzMTI1YS1hYTY4LTRjOTItYWVmNy01OTcwNGZjNTVjOWYiLCJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzcnZwbGVpZXBlbmdlci1zYWsiLCJhdWQiOlsic3J2cGxlaWVwZW5nZXItc2FrIiwicHJlcHJvZC5sb2NhbCJdLCJ2ZXIiOiIxLjAiLCJuYmYiOjE1NTEyNzA5NjMsImF6cCI6InNydnBsZWllcGVuZ2VyLXNhayIsImlkZW50VHlwZSI6IlN5c3RlbXJlc3N1cnMiLCJhdXRoX3RpbWUiOjE1NTEyNzA5NjMsImlzcyI6Imh0dHBzOlwvXC9zZWN1cml0eS10b2tlbi1zZXJ2aWNlLm5haXMucHJlcHJvZC5sb2NhbCIsImV4cCI6MTU1MTI3NDU2MywiaWF0IjoxNTUxMjcwOTYzLCJqdGkiOiJiY2M1NGVjOS05ZGU1LTRiOGQtYjRlZi1kMjY0NTQxZDBiZWQifQ.dqW2eWzFrkwIpLqP_k5PA6wA1hlaaWV9MbtpZMWJHn01ar5hTPDulAY__Rm1WoB33t3ucqaO_dLcWC6ISBfNIkd9wmb590Aa57D8OcDNwp9D4qPnXczqD3VD5nMspvf4bfztvWxf3MvHYlZX5J9QILedYk1Qx2Mb-epBQRjbCOKtykCwckUvcTFYqflfVbi8-9mhtSGUYO_zdPL-lbvUSDDwyVfXGsVmjabiCVTo46NCcQotHxWxV3OrtojdNyh_RZNEWrt8QubC3WbxcOiZnoX-9D0JMqng6WXFbXoypj_VnErVlAJObLKOtzJA7l3kfJ9cdZEt547i2j97oz360Q"
private const val aktoerId = "1831212532188"
private const val sakId = "137662692"

private val logger: Logger = LoggerFactory.getLogger("nav.SakPactTests")

class SakPactTests {
    @Rule
    @JvmField
    val mockProvider = PactProviderRuleMk2(provider, this)

    init {
        System.setProperty("pact.rootDir","${System.getProperty("user.dir")}/pacts")
    }

    @Pact(consumer = consumer)
    @SuppressWarnings("unused")
    fun oppretteSakPact(builder : PactDslWithProvider) : RequestResponsePact {
        val body = """
        {
            "tema" : "OMS",
            "applikasjon" : "FS22",
            "aktoerId" : "$aktoerId",
            "orgnr" : null,
            "fagsakNr" : null
        }
        """.trimIndent()

        logger.info("Body=$body")
        val headers = mapOf(
            Pair(HttpHeaders.ContentType, "application/json"),
            Pair(HttpHeaders.Accept, "application/json")
        )

        return builder
            .given("Klar for aa opprette nye saker")
            .uponReceiving("Request for aa opprette ny sak")
            .path("/api/v1/saker")
            .method("POST")
            .headers(headers)
            .matchHeader(HttpHeaders.Authorization, "Bearer [A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*$")
            .matchHeader(HttpHeaders.XCorrelationId, "\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b")
            .body(body)
            .willRespondWith()
            .headers(mapOf(Pair(HttpHeaders.ContentType, "application/json")))
            .status(201)
            .body(
            """
                {
                    "id": "$sakId"
                }
            """.trimIndent())
            .toPact()
    }

    @Test
    @PactVerification(provider)
    fun opprettelseAvSakFungerer() {
        val sakService = sakService()
        runBlocking {
            val actualSakId = sakService.opprettSak(
                melding = MeldingV1(aktoerId = aktoerId),
                metaData = MetadataV1(version = 1, correlationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString())
            )
            assertEquals(sakId, actualSakId.value)
        }
    }

    private fun sakService() : SakV1Service {
        return SakV1Service(sakGateway = sakGateway())
    }

    private fun sakGateway() : SakGateway {
        val httpClient = HttpClient(Apache) {
            install(JsonFeature) {
                serializer = JacksonSerializer{ ObjectMapper.sak(this) }
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

    private fun mockSystembrukerGateway() : SystembrukerGateway {
        val mock = Mockito.mock(SystembrukerGateway::class.java)
        runBlocking {
            Mockito.`when`(mock.getToken()).thenReturn(Response(
                accessToken = jwt,
                expiresIn = 5000
            ))
        }
        return mock
    }


}



