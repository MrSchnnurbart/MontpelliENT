package fr.montpellient.server

import fr.montpellient.shared.model.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    install(WebSockets) {
        pingPeriod = kotlin.time.Duration.parse("15s")
        timeout = kotlin.time.Duration.parse("15s")
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }


    val serverState = ServerState()

    routing {
        get("/api/health") {
            call.respond(mapOf("status" to "OK", "version" to "1.0.0", "time" to Instant.now().toString()))
        }

        // --- CROUS & RU MONTPELLIER ---
        route("/api/crous") {
            get("/restaurants") {
                call.respond(serverState.restaurants)
            }

            get("/menus") {
                val restaurantId = call.request.queryParameters["restaurantId"]
                val list = if (restaurantId != null) {
                    serverState.menus.filter { it.restaurantId == restaurantId }
                } else {
                    serverState.menus
                }
                call.respond(list)
            }

            get("/affluence") {
                call.respond(serverState.affluenceMap.values.toList())
            }

            // Signalement participatif / crowdsourcing de l'affluence du restaurant
            post("/affluence/{restaurantId}") {
                val restaurantId = call.parameters["restaurantId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val report = call.receive<SharedAffluence>()
                val updated = report.copy(
                    restaurantId = restaurantId,
                    reportedAtEpochSec = Instant.now().epochSecond
                )
                serverState.affluenceMap[restaurantId] = updated
                call.respond(HttpStatusCode.Created, updated)
            }
        }

        // --- VIE DE CAMPUS & BU ---
        route("/api/campus") {
            get("/libraries") {
                call.respond(serverState.libraries)
            }

            get("/events") {
                call.respond(serverState.events)
            }
        }

        // --- NOTIFICATIONS PUSH ETHIQUES (WebSocket / UnifiedPush endpoint) ---
        webSocket("/api/push/stream") {
            for (frame in incoming) {
                // Keep-alive et souscription aux notifications
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (text == "PING") {
                        send(Frame.Text("PONG"))
                    }
                }
            }
        }

        post("/api/push/broadcast") {
            val payload = call.receive<PushNotificationPayload>()
            // Envoi broadcast à tous les clients connectés
            call.respond(HttpStatusCode.Accepted, mapOf("status" to "Broadcasted", "id" to payload.id))
        }
    }
}

class ServerState {
    val restaurants = listOf(
        SharedCrousRestaurant(
            id = "triolet",
            name = "RU Triolet",
            campus = "Campus Triolet (Sciences)",
            address = "Place Eugène Bataillon, 34095 Montpellier",
            openingHours = "11h30 - 13h45 (Midi)",
            latitude = 43.6318,
            longitude = 3.8647
        ),
        SharedCrousRestaurant(
            id = "richter",
            name = "RU Richter",
            campus = "Campus Richter (Droit & Eco)",
            address = "Rue Vendémiaire, 34000 Montpellier",
            openingHours = "11h30 - 14h00 (Midi) & 18h30 - 20h30 (Soir)",
            latitude = 43.6035,
            longitude = 3.8992
        ),
        SharedCrousRestaurant(
            id = "vert-bois",
            name = "RU Vert-Bois",
            campus = "Campus Route de Mende (Lettres Paul-Valéry)",
            address = "199 Rue Saint-Priest, 34090 Montpellier",
            openingHours = "11h30 - 13h45 (Midi)",
            latitude = 43.6335,
            longitude = 3.8690
        ),
        SharedCrousRestaurant(
            id = "occitanie",
            name = "Brasserie Occitanie",
            campus = "Campus Triolet",
            address = "Rue du Truel, 34090 Montpellier",
            openingHours = "08h00 - 16h00",
            latitude = 43.6322,
            longitude = 3.8631
        )
    )

    val menus = listOf(
        SharedCrousMenu(
            id = "menu-triolet-today",
            restaurantId = "triolet",
            dateIso = "2026-10-11",
            period = "midi",
            items = listOf(
                SharedCrousMealItem("Entrée", "Salade niçoise ou taboulé à la menthe"),
                SharedCrousMealItem("Plat", "Pavé de colin sauce aneth ou Lasagnes végétariennes"),
                SharedCrousMealItem("Accompagnement", "Riz basmati & Poêlée de légumes de saison"),
                SharedCrousMealItem("Dessert", "Yaourt bio d'Occitanie ou Pomme locale")
            )
        ),
        SharedCrousMenu(
            id = "menu-richter-today",
            restaurantId = "richter",
            dateIso = "2026-10-11",
            period = "midi",
            items = listOf(
                SharedCrousMealItem("Entrée", "Salade de carottes râpées bio"),
                SharedCrousMealItem("Plat", "Poulet rôti aux herbes de Provence ou Chili sin carne"),
                SharedCrousMealItem("Accompagnement", "Frites maison ou Haricots verts"),
                SharedCrousMealItem("Dessert", "Mousse au chocolat maison ou Fruit bio")
            )
        )
    )

    val affluenceMap = ConcurrentHashMap<String, SharedAffluence>().apply {
        put("triolet", SharedAffluence("triolet", SharedAffluenceLevel.LOW, 5, Instant.now().epochSecond))
        put("richter", SharedAffluence("richter", SharedAffluenceLevel.MEDIUM, 12, Instant.now().epochSecond))
        put("vert-bois", SharedAffluence("vert-bois", SharedAffluenceLevel.LOW, 3, Instant.now().epochSecond))
    }

    val libraries = listOf(
        SharedCampusLibrary("bu-sciences", "BU Sciences", "Triolet", "08h00 - 22h00", 1200, 480, true),
        SharedCampusLibrary("bu-richter", "BU Richter", "Richter", "08h30 - 20h30", 950, 210, true),
        SharedCampusLibrary("bu-medecine", "BU Médecine", "Centre-Ville", "08h00 - 20h00", 600, 85, true)
    )

    val events = listOf(
        SharedCampusEvent("ev-1", "Soirée d'intégration FdS", "Festif", "2026-10-15", "Maison des Étudiants Triolet", "Soirée associative avec concerts et stands étudiants."),
        SharedCampusEvent("ev-2", "Tournoi de Basket 3x3 SUAPS", "Sport", "2026-10-17", "Halle des Sports Veyrassi", "Inscriptions gratuites pour tous les étudiants UM."),
        SharedCampusEvent("ev-3", "Forum des Métiers de l'Informatique", "Carrière", "2026-10-22", "Bâtiment 36, Campus Triolet", "Rencontrez 30 entreprises et startups partenaires.")
    )
}
