package fr.montpellient.core.designsystem.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object NotificationHelper {

    const val CHANNEL_TIMETABLE = "channel_timetable"
    const val CHANNEL_MAIL = "channel_mail"
    const val CHANNEL_GRADES = "channel_grades"
    const val CHANNEL_ASSIGNMENTS = "channel_assignments"
    const val CHANNEL_TEST = "channel_test"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val channels = listOf(
                NotificationChannel(
                    CHANNEL_TIMETABLE,
                    "Emploi du temps & Cours",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Changements de salle, annulations ou cours ajoutés"
                },
                NotificationChannel(
                    CHANNEL_MAIL,
                    "Messagerie UM",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Nouveaux e-mails universitaires reçus"
                },
                NotificationChannel(
                    CHANNEL_GRADES,
                    "Notes & Résultats",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Publication de nouvelles notes d'évaluation"
                },
                NotificationChannel(
                    CHANNEL_ASSIGNMENTS,
                    "Devoirs Moodle",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Rappels des dates limites de devoirs Moodle"
                },
                NotificationChannel(
                    CHANNEL_TEST,
                    "Tests de notification",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Canal utilisé pour vérifier le bon fonctionnement des alertes"
                }
            )

            manager.createNotificationChannels(channels)
        }
    }

    fun sendNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        message: String,
        intent: Intent? = null
    ) {
        createNotificationChannels(context)

        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                context,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setAutoCancel(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            manager?.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission POST_NOTIFICATIONS non accordée
        }
    }
}
