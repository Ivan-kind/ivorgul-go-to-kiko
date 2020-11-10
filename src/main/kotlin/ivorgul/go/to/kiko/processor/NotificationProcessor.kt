package ivorgul.go.to.kiko.processor

import ivorgul.go.to.kiko.model.Notification
import ivorgul.go.to.kiko.type.NotificationType

interface NotificationProcessor {

    fun sendNotification(notification: Notification)

    companion object {
        private val EMAIL_NOTIFICATION_PROCESSOR = EmailNotificationProcessor()
        private val OTHER_NOTIFICATION_PROCESSOR = OtherNotificationProcessor()

        fun getProcessor(notificationType: NotificationType) =
            when (notificationType) {
                NotificationType.EMAIL -> EMAIL_NOTIFICATION_PROCESSOR
                NotificationType.OTHER -> OTHER_NOTIFICATION_PROCESSOR
            }
    }
}