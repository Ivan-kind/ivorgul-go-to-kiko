package ivorgul.go.to.kiko.api

import java.sql.Timestamp
import java.util.UUID

data class ReserveViewRequest(
    val objectId: UUID,
    val time: Timestamp
)