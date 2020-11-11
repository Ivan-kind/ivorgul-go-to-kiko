package ivorgul.go.to.kiko.module

import com.typesafe.config.ConfigFactory
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.BadRequestException
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import ivorgul.go.to.kiko.api.ReserveViewRequest
import ivorgul.go.to.kiko.api.toApiDto
import ivorgul.go.to.kiko.config.hikari
import ivorgul.go.to.kiko.model.Notifications
import ivorgul.go.to.kiko.model.RentObjects
import ivorgul.go.to.kiko.model.Tenants
import ivorgul.go.to.kiko.model.ViewReservations
import ivorgul.go.to.kiko.scheduler.SendNotificationScheduler
import ivorgul.go.to.kiko.service.ViewReservationService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

// TODO return error message in response
// TODO use not in-memory DB like Postgres + migration like flyway
// TODO use some Auth system (do not send user id in request params)
fun Application.myModule(sendNotificationScheduler: SendNotificationScheduler) {
    Database.connect(hikari())
    val config = ConfigFactory.load()
    val durationMinutes = config.getInt("kiko.reservation.duration-minutes")
    val windowDays = config.getInt("kiko.reservation.window-days")
    val reservationStartAtHours = config.getInt("kiko.reservation.start-at-hours")
    val reservationEndAtHours = config.getInt("kiko.reservation.end-at-hours")
    val viewReservationService = ViewReservationService(
        durationMinutes = durationMinutes,
        windowDays = windowDays,
        reservationEndAtHours = reservationEndAtHours,
        reservationStartAtHours = reservationStartAtHours
    )
    transaction {
        SchemaUtils.create(RentObjects, Tenants, ViewReservations, Notifications)
    }
    install(ContentNegotiation) {
        jackson {}
    }
    sendNotificationScheduler.startNotificationScheduler()
    routing {
        route("/view-reservations") {
            get("/") {
                val rentObjectId =  call.parseParam(
                    paramName = "rent-object-id",
                    required = true
                ) { UUID.fromString(it) }!!
                val currUserId = call.getUserId()
                call.respond(viewReservationService.getViewReservations(rentObjectId, currUserId).map { it.toApiDto() })
            }
            get("/{id}") {
                val currUserId = call.getUserId()
                val id =  call.parseParam(
                    paramName = "id",
                    required = true
                ) { UUID.fromString(it) }!!
                call.respond(viewReservationService.getViewReservation(id, currUserId).toApiDto())
            }
            post("/") {
                val currUserId = call.getUserId()
                val request = call.receive<ReserveViewRequest>()
                val id = viewReservationService.reserveView(
                    reservationStartTime = request.time,
                    reservationObjectId = request.objectId,
                    currUserId = currUserId
                )
                call.respond(
                    status = HttpStatusCode.Created,
                    message = mapOf("id" to id)
                )
            }
            post("/{id}/approve") {
                val currUserId = call.getUserId()
                val id = UUID.fromString(call.parameters["id"])
                viewReservationService.approveReservation(id, currUserId)
                call.respond(HttpStatusCode.OK)
            }
            post("/{id}/reject") {
                val currUserId = call.getUserId()
                val id =  call.parseParam(
                    paramName = "id",
                    required = true
                ) { UUID.fromString(it) }!!
                viewReservationService.rejectReservation(id, currUserId)
                call.respond(HttpStatusCode.OK)
            }
            delete("/{id}") {
                val currUserId = call.getUserId()
                val id =  call.parseParam(
                    paramName = "id",
                    required = true
                ) { UUID.fromString(it) }!!
                viewReservationService.cancelReservation(id, currUserId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

fun ApplicationCall.getUserId(): UUID =
    parseParam(
        paramName = "user-id",
        required = true
    ) { UUID.fromString(it) }!!

fun <T> ApplicationCall.parseParam(
    paramName: String,
    required: Boolean = true,
    block: (String) -> T
): T? = parameters[paramName]
    ?.let { block(it) }
    ?: if (required) throw BadRequestException("Missing required param $paramName") else null