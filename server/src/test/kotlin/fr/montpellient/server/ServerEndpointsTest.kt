package fr.montpellient.server

import fr.montpellient.shared.model.SharedAffluence
import fr.montpellient.shared.model.SharedAffluenceLevel
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ServerEndpointsTest {

    @Test
    fun testHealthEndpoint() = testApplication {
        application {
            module()
        }

        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("OK"))
    }

    @Test
    fun testCrousEndpoints() = testApplication {
        application {
            module()
        }

        val res = client.get("/api/crous/restaurants")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("RU Triolet"))

        val menus = client.get("/api/crous/menus?restaurantId=triolet")
        assertEquals(HttpStatusCode.OK, menus.status)
        assertTrue(menus.bodyAsText().contains("Salade niçoise"))

        val affluence = client.get("/api/crous/affluence")
        assertEquals(HttpStatusCode.OK, affluence.status)
        assertTrue(affluence.bodyAsText().contains("triolet"))
    }

    @Test
    fun testReportAffluence() = testApplication {
        application {
            module()
        }

        val postRes = client.post("/api/crous/affluence/triolet") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "restaurantId": "triolet",
                    "level": "HIGH",
                    "waitingMinutes": 25,
                    "reportedAtEpochSec": 1000
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Created, postRes.status)

        val updated = client.get("/api/crous/affluence")
        assertTrue(updated.bodyAsText().contains("HIGH"))
    }
}
