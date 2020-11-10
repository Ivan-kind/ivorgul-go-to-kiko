package ivorgul.go.to.kiko.scheduler

import ivorgul.go.to.kiko.service.NotificationService
import java.util.*

class SendNotificationScheduler {

    fun startNotificationScheduler() {
        Timer().scheduleAtFixedRate(NotificationService.instance, 0, 30 * 1000)
    }
}