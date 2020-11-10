package ivorgul.go.to.kiko.api

import ivorgul.go.to.kiko.model.ViewReservation

fun ViewReservation.toApiDto() = GetViewReservationResponse(
    id = id,
    time = timeFrom,
    status = status,
    currentTenant = currentTenant,
    newTenant = newTenant,
    objectId = objectId
)