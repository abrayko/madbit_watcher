package ru.hfart.madbitwatcher

import android.app.*
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import ru.hfart.madbitwatcher.R
import kotlin.random.Random

class HandleNotifications {

    companion object {
        const val NOTIFICATION_ID = 256

        fun createNotification(context: Service) : Notification {
            val channelId = createChannel(context)
            val notification = buildNotification(context, channelId)
            return notification
        }

        private fun buildNotification(context: Service, channelId: String): Notification {
            val activity = MainActivity::class.java
            val intent = Intent(context, activity)
            val mainActivity = PendingIntent.getActivity(context, Random.nextInt(), intent, 0)

            val notification =
                NotificationCompat.Builder(context, channelId)
                    .setContentIntent(mainActivity)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(context.getString(R.string.notification_title))
                    .setContentText(context.getString(R.string.notification_content))
                    .setOngoing(true) // нельзя убрать из шторки уведолмлений
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .build()
            return  notification
        }

        private fun createChannel(context: Service): String {
            val channelId = context.getString(R.string.madbit_watcher_channel_id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelName = context.getString(R.string.madbit_watcher_channel_name)
                val manager =
                    context.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
                val channel =
                    NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                manager?.createNotificationChannel(channel)
            }
            return channelId
        }

    }

}
