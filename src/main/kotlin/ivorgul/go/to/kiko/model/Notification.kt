package ivorgul.go.to.kiko.model

import ivorgul.go.to.kiko.type.NotificationStatus
import ivorgul.go.to.kiko.type.NotificationType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import java.util.*

object Notifications : Table() {
    val id = uuid("id")
    val status = varchar("status", 50)
    val address = varchar("address", 255)
    val type = varchar("type", 50)
    val content = text("content")

    override val primaryKey = PrimaryKey(id, name = "PK_notifications")
}

data class Notification(
    val id: UUID,
    val status: NotificationStatus,
    val address: String,
    val type: NotificationType,
    val content: String
) {
    constructor(row: ResultRow) : this(
        id = row[Notifications.id],
        status = NotificationStatus.valueOf(row[Notifications.status]),
        address = row[Notifications.address],
        type = NotificationType.valueOf(row[Notifications.type]),
        content = row[Notifications.content]
    )
}