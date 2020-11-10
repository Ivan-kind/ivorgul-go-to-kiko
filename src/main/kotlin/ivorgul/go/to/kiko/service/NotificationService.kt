package ivorgul.go.to.kiko.service

import io.ktor.features.BadRequestException
import ivorgul.go.to.kiko.model.Notification
import ivorgul.go.to.kiko.model.Notifications
import ivorgul.go.to.kiko.model.Tenant
import ivorgul.go.to.kiko.model.Tenants
import ivorgul.go.to.kiko.processor.NotificationProcessor
import ivorgul.go.to.kiko.type.NotificationStatus
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.*

class NotificationService : TimerTask() {

    fun saveNotification(
        tenantId: UUID,
        notificationContent: String
    ) {
        transaction {
            val tenant = Tenant(
                Tenants.select { Tenants.id.eq(tenantId) }.singleOrNull()
                    ?: throw BadRequestException("Cannot find tenant with id : $tenantId")
            )
            Notifications.insert {
                it[id] = UUID.randomUUID()
                it[status] = NotificationStatus.NEW.name
                it[address] = tenant.contacts
                it[type] = tenant.contactsType.name
                it[content] = notificationContent
            }
        }
    }

    override fun run() {
        logger.debug("start send notification process")
        transaction {
            Notifications.select {
                Notifications.status.eq(NotificationStatus.NEW.name)
            }.toList()
                .map { Notification(it) }
                .forEach { notification ->
                    kotlin.runCatching {
                        NotificationProcessor.getProcessor(notification.type).sendNotification(notification)
                    }.onFailure {
                        logger.error("Error on send notification id : ${notification.id}")
                        Notifications.update({ Notifications.id.eq(notification.id) }) {
                            it[status] = NotificationStatus.CANCELED.name
                        }
                    }.onSuccess {
                        logger.info("Notification with id : ${notification.id} sent")
                    }
                }
        }
    }

    companion object {
        val instance = NotificationService()
        private val logger = LoggerFactory.getLogger(NotificationService::class.java)
    }
}