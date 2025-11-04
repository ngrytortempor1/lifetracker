package com.lifetracker.app.ui.quickadd

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.lifetracker.app.R

object TaskQuickAddNotifier {
    private const val CHANNEL_ID = "task_quick_add_channel"
    internal const val NOTIFICATION_ID = 1001

    fun showQuickAddNotification(
        context: Context,
        lastAddedTitle: String? = null
    ) {
        createChannel(context)

        val inlineIntent = Intent(context, TaskQuickAddReceiver::class.java).apply {
            action = TaskQuickAddReceiver.ACTION_INLINE_ADD
        }
        val inlinePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                context,
                1,
                inlineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            PendingIntent.getBroadcast(
                context,
                1,
                inlineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val remoteInput = RemoteInput.Builder(TaskQuickAddReceiver.REMOTE_INPUT_KEY)
            .setLabel("タスク内容を入力")
            .build()

        val inlineAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "追加",
            inlinePendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val openIntent = Intent(context, TaskQuickAddActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(TaskQuickAddActivity.EXTRA_FROM_NOTIFICATION, true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "詳細",
            openPendingIntent
        ).build()

        val contentText = lastAddedTitle?.let { "追加しました: $it" } ?: "通知から素早く追加できます"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("タスクを追加")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .apply {
                lastAddedTitle?.let {
                    setRemoteInputHistory(arrayOf("追加: $it"))
                }
            }
            .addAction(inlineAction)
            .addAction(openAction)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS が未許可の場合は通知を表示しない
        }
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "タスククイック追加",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }
}
