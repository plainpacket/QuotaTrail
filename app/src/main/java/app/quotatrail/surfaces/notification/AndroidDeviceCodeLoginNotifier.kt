package app.quotatrail.surfaces.notification

import app.quotatrail.domain.auth.DeviceCodeLoginNotifier

class AndroidDeviceCodeLoginNotifier(
    private val notificationSink: NotificationSink,
    private val orchestrator: DeviceCodeLoginNotificationCoordinator = DeviceCodeLoginNotificationCoordinator(),
    private val registry: DeviceCodeLoginNotificationRegistry = GlobalDeviceCodeLoginNotificationRegistry,
) : DeviceCodeLoginNotifier {
    override fun waitingForAuthorization(attemptId: String, userCode: String, verificationUri: String) {
        registry.updateLatest(attemptId = attemptId, userCode = userCode)
        notificationSink.post(
            orchestrator.waitingForAuthorization(
                attemptId = attemptId,
                userCode = userCode,
                verificationUri = verificationUri,
            ),
        )
    }

    override fun connected(accountDisplayName: String?) {
        registry.clear()
        notificationSink.post(orchestrator.connected(accountDisplayName))
    }

    override fun expired() {
        registry.clear()
        notificationSink.post(orchestrator.expired())
    }

    override fun failed(safeMessageKey: String) {
        registry.clear()
        notificationSink.post(orchestrator.failed(safeMessageKey))
    }

    override fun validationFailed(safeMessageKey: String) {
        registry.clear()
        notificationSink.post(orchestrator.validationFailed(safeMessageKey))
    }

    override fun cancelled() {
        registry.clear()
        notificationSink.cancel(NotificationCoordinator.AUTH_LOGIN_NOTIFICATION_ID)
    }
}
