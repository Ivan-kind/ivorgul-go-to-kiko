package ivorgul.go.to.kiko.model

import ivorgul.go.to.kiko.type.ViewReservationStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import java.sql.Timestamp
import java.util.*

object ViewReservations : Table("view_reservations") {
    val id = uuid("id")
    val timeFrom = long("time_from")
    val timeTo = long("time_to")
    val status = varchar("status", 50)
    val currentTenant = uuid("current_tenant")
    val newTenant = uuid("new_tenant")
    val objectId = uuid("object_id")

    override val primaryKey = PrimaryKey(id, name = "PK_view_reservations")
}

data class ViewReservation(
    val id: UUID,
    val timeFrom: Timestamp,
    val timeTo: Timestamp,
    val status: ViewReservationStatus,
    val currentTenant: UUID,
    val newTenant: UUID,
    val objectId: UUID
) {
    constructor(row: ResultRow) : this(
        id = row[ViewReservations.id],
        currentTenant = row[ViewReservations.currentTenant],
        timeFrom = Timestamp(row[ViewReservations.timeFrom]),
        timeTo = Timestamp(row[ViewReservations.timeTo]),
        status = ViewReservationStatus.valueOf(row[ViewReservations.status]),
        newTenant = row[ViewReservations.newTenant],
        objectId = row[ViewReservations.objectId]
    )
}