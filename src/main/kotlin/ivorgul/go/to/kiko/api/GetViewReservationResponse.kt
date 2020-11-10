package ivorgul.go.to.kiko.api

import ivorgul.go.to.kiko.type.ViewReservationStatus
import java.sql.Timestamp
import java.util.*

data class GetViewReservationResponse(
    val id: UUID,
    val time: Timestamp,
    val status: ViewReservationStatus,
    val currentTenant: UUID,
    val newTenant: UUID,
    val objectId: UUID
)