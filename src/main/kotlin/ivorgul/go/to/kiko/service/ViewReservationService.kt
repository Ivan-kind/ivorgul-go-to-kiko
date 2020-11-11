package ivorgul.go.to.kiko.service

import io.ktor.features.BadRequestException
import io.ktor.features.NotFoundException
import ivorgul.go.to.kiko.model.RentObject
import ivorgul.go.to.kiko.model.RentObjects
import ivorgul.go.to.kiko.model.ViewReservation
import ivorgul.go.to.kiko.model.ViewReservations
import ivorgul.go.to.kiko.type.ViewReservationStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

const val DAY_IN_MS = 24 * 60 * 60 * 1000

class ViewReservationService(
    private val durationMinutes: Int,
    private val windowDays: Int,
    private val reservationStartAtHours: Int,
    private val reservationEndAtHours: Int
) {

    fun reserveView(reservationStartTime: Timestamp, reservationObjectId: UUID, currUserId: UUID): UUID =
        transaction {
            val reservationEndTime = getTimeTo(reservationStartTime)
            val rentObject = RentObject(
                row = RentObjects.select {
                    RentObjects.id.eq(reservationObjectId)
                }.singleOrNull() ?: throw NotFoundException("Cannot find rent object with id : $reservationObjectId")
            )
            validateReservationTime(reservationStartTime, reservationEndTime, reservationObjectId)
            UUID.randomUUID().also { reservationId ->
                ViewReservations.insert {
                    it[id] = reservationId
                    it[timeFrom] = reservationStartTime.time
                    it[timeTo] = reservationEndTime.time
                    it[status] = ViewReservationStatus.CREATED.name
                    it[currentTenant] = rentObject.currentTenant
                    it[newTenant] = currUserId
                    it[objectId] = reservationObjectId
                }
                NotificationService.instance.saveNotification(
                    tenantId = rentObject.currentTenant,
                    notificationContent = "OBJECT RESERVED"
                )
            }
        }.also {
            logger.info("Reservation was created with id : $it")
        }

    fun getViewReservation(id: UUID, currUserId: UUID) =
        transaction {
            ViewReservation(
                row = ViewReservations.select {
                    ViewReservations.id.eq(id)
                        .and(
                            ViewReservations.currentTenant.eq(currUserId)
                                .or(ViewReservations.newTenant.eq(currUserId))
                        )
                }.singleOrNull()
                    ?: throw NotFoundException("Cannot find reservation by id: $id")
            )
        }

    fun getViewReservations(rentObjectId: UUID, currUserId: UUID) =
        transaction {
            ViewReservations.select {
                ViewReservations.objectId.eq(rentObjectId)
                    .and(
                        ViewReservations.currentTenant.eq(currUserId)
                            .or(ViewReservations.newTenant.eq(currUserId))
                    )
            }.toList().map { ViewReservation(it) }
        }

    fun approveReservation(id: UUID, currUserId: UUID) {
        transaction {
            ViewReservation(
                ViewReservations.select {
                    ViewReservations.id.eq(id).and(ViewReservations.currentTenant.eq(currUserId))
                }.singleOrNull() ?: throw NotFoundException("Cannot find reservation id : $id")
            )
                .also { reservation ->
                    ViewReservations.update({ ViewReservations.id.eq(id) }) {
                        it[status] = ViewReservationStatus.APPROVED.name
                    }
                    NotificationService.instance
                        .saveNotification(reservation.newTenant, "RESERVATION APPROVED")
                }
        }.also {
            logger.info("Reservation with id : $id approved")
        }
    }

    fun rejectReservation(id: UUID, currUserId: UUID) {
        transaction {
            ViewReservation(ViewReservations.select {
                ViewReservations.id.eq(id).and(ViewReservations.currentTenant.eq(currUserId))
            }.singleOrNull() ?: throw NotFoundException("Cannot find reservation id : $id"))
                .also { reservation ->
                    ViewReservations.update({ ViewReservations.id.eq(id) }) {
                        it[status] = ViewReservationStatus.REJECTED.name
                    }
                    NotificationService.instance
                        .saveNotification(reservation.newTenant, "RESERVATION REJECTED")
                }
        }.also {
            logger.info("Reservation with id : $id rejected")
        }
    }

    fun cancelReservation(id: UUID, currUserId: UUID) {
        transaction {
            ViewReservation(ViewReservations.select {
                ViewReservations.id.eq(id)
                    .and(ViewReservations.newTenant.eq(currUserId))
            }.singleOrNull() ?: throw NotFoundException("Cannot find reservation id : $id"))
                .also { reservation ->
                    if (reservation.status == ViewReservationStatus.REJECTED) {
                        logger.debug("Cannot delete rejected reservation with id : $id")
                        throw BadRequestException("Cannot cancel reservation that already rejected")
                    }
                    ViewReservations.deleteWhere {
                        ViewReservations.id.eq(id)
                    }
                    NotificationService.instance
                        .saveNotification(reservation.currentTenant, "RESERVATION CANCELED")
                }
        }.also {
            logger.info("Reservation with id : $id canceled")
        }
    }

    private fun validateReservationTime(
        reservationStartTimestamp: Timestamp,
        reservationEndTimestamp: Timestamp,
        rentObjectId: UUID
    ) {
        val reservationSrartDate = reservationStartTimestamp.toLocalDateTime()
        val reservationStartTime = reservationSrartDate.toLocalTime()
        val reservationEndTime = reservationEndTimestamp.toLocalDateTime().toLocalTime()
        if (reservationStartTime.isBefore(LocalTime.of(reservationStartAtHours, 0, 0))
            || reservationEndTime.isAfter(LocalTime.of(reservationEndAtHours, 0, 0))
            || reservationSrartDate.toLocalDate().isBefore(LocalDate.now())
            || reservationSrartDate.toLocalDate().isAfter(LocalDate.now().plusDays(windowDays.toLong()))
            || reservationStartTimestamp.before(Timestamp(System.currentTimeMillis() + DAY_IN_MS))
        ) {
            logger.debug("Reservation time $reservationStartTimestamp is incorrect.")
            throw BadRequestException(
                "Incorrect reservation time [start - $reservationStartTime end - $reservationEndTime]"
            )
        }
        if (ViewReservations.select {
                ViewReservations.objectId.eq(rentObjectId)
                    .and(
                        ViewReservations.timeFrom.between(reservationStartTimestamp.time, reservationEndTimestamp.time)
                                or (ViewReservations.timeTo.between(
                            reservationStartTimestamp.time,
                            reservationEndTimestamp.time
                        ))
                    )
            }.any()) {
            logger.debug("Reservation time : $reservationStartTimestamp already exists")
            throw BadRequestException("Reservation time already reserved for object id : $rentObjectId")
        }
    }

    private fun getTimeTo(timeFrom: Timestamp): Timestamp =
        Timestamp(timeFrom.time + (durationMinutes * 60 * 1000))

    companion object {
        private val logger = LoggerFactory.getLogger(ViewReservationService::class.java)
    }
}