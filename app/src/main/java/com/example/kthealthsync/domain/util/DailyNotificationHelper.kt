package com.example.kthealthsync.domain.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.kthealthsync.MainActivity
import com.example.kthealthsync.R
import com.example.kthealthsync.domain.model.AppLanguage
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.NutrientGoals
import java.util.Locale

object DailyNotificationHelper {

    const val CHANNEL_ID = "daily_progress_channel_v2"
    private const val OLD_CHANNEL_ID = "daily_progress_channel"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID)
            } catch (_: Exception) {
            }

            val name = "Дневной прогресс"
            val descriptionText = "Закреплённое уведомление с прогрессом калорий и БЖУ"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(
        context: Context,
        summary: DailySummary,
        goals: NutrientGoals,
        language: AppLanguage = AppLanguage.RU
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) return
        }

        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Format values
        val currentKcal = summary.kcal.toInt()
        val goalKcal = goals.kcal.toInt()
        val remainingKcal = goalKcal - currentKcal
        val percentKcal = if (goalKcal > 0) ((summary.kcal / goals.kcal) * 100).toInt() else 0

        val currentProt = summary.protein.toInt()
        val goalProt = goals.protein.toInt()
        val percentProt = if (goalProt > 0) ((summary.protein / goals.protein) * 100).toInt() else 0

        val currentFat = summary.fat.toInt()
        val goalFat = goals.fat.toInt()
        val percentFat = if (goalFat > 0) ((summary.fat / goals.fat) * 100).toInt() else 0

        val currentCarb = summary.carb.toInt()
        val goalCarb = goals.carbs.toInt()
        val percentCarb = if (goalCarb > 0) ((summary.carb / goals.carbs) * 100).toInt() else 0

        val currentFiber = summary.fiber.toInt()
        val goalFiber = goals.fiber.toInt()

        val currentWaterL = String.format(Locale.US, "%.1f", summary.water / 1000.0)
        val goalWaterL = String.format(Locale.US, "%.1f", goals.water / 1000.0)

        // Localized strings
        val kcalUnit = when (language) {
            AppLanguage.EN -> "kcal"
            AppLanguage.CS -> "kcal"
            else -> "ккал"
        }
        val remainingText = when (language) {
            AppLanguage.EN -> if (remainingKcal > 0) "$percentKcal% • $remainingKcal $kcalUnit left" else "$percentKcal% • goal reached!"
            AppLanguage.CS -> if (remainingKcal > 0) "$percentKcal% • zbývá $remainingKcal $kcalUnit" else "$percentKcal% • cíl splněn!"
            else -> if (remainingKcal > 0) "$percentKcal% • ост. $remainingKcal $kcalUnit" else "$percentKcal% • цель выполнена!"
        }
        val btnAddLargeText = when (language) {
            AppLanguage.EN -> "Add food"
            AppLanguage.CS -> "Přidat jídlo"
            else -> "Добавить еду"
        }
        val btnScanLargeText = when (language) {
            AppLanguage.EN -> "Barcode"
            AppLanguage.CS -> "Čárový kód"
            else -> "Штрихкод"
        }
        val fiberLabel = when (language) {
            AppLanguage.EN -> "🥗 Fiber"
            AppLanguage.CS -> "🥗 Vláknina"
            else -> "🥗 Клетчатка"
        }
        val waterLabel = when (language) {
            AppLanguage.EN -> "💧 Water"
            AppLanguage.CS -> "💧 Voda"
            else -> "💧 Вода"
        }
        val protLabel = when (language) {
            AppLanguage.EN -> "🥩 Protein"
            AppLanguage.CS -> "🥩 Bílkoviny"
            else -> "🥩 Белки"
        }
        val fatLabel = when (language) {
            AppLanguage.EN -> "🥑 Fat"
            AppLanguage.CS -> "🥑 Tuky"
            else -> "🥑 Жиры"
        }
        val carbLabel = when (language) {
            AppLanguage.EN -> "🍞 Carbs"
            AppLanguage.CS -> "🍞 Sacharidy"
            else -> "🍞 Углеводы"
        }

        val gUnit = if (language == AppLanguage.EN) "g" else "г"
        val lUnit = if (language == AppLanguage.EN) "L" else "л"

        // PendingIntents
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_VIEW_TODAY
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val addIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_ADD_FOOD
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val addPendingIntent = PendingIntent.getActivity(
            context,
            1,
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val scanIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SCAN_BARCODE
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val scanPendingIntent = PendingIntent.getActivity(
            context,
            2,
            scanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 1. RemoteViews Small (Collapsed)
        val rvSmall = RemoteViews(context.packageName, R.layout.notification_daily_progress_small).apply {
            setTextViewText(R.id.notif_small_kcal_title, "$currentKcal / $goalKcal $kcalUnit")
            setTextViewText(R.id.notif_small_kcal_sub, "$percentKcal%")
            setProgressBar(R.id.notif_small_progress_kcal, 100, percentKcal.coerceIn(0, 100), false)
            setTextViewText(
                R.id.notif_small_macros,
                "🥩 $currentProt • 🍞 $currentCarb • 🥑 $currentFat $gUnit"
            )

            setOnClickPendingIntent(R.id.btn_notif_small_add, addPendingIntent)
            setOnClickPendingIntent(R.id.btn_notif_small_scan, scanPendingIntent)
        }

        // 2. RemoteViews Large (Expanded)
        val rvLarge = RemoteViews(context.packageName, R.layout.notification_daily_progress_large).apply {
            setTextViewText(R.id.notif_large_kcal_title, "$currentKcal / $goalKcal $kcalUnit")
            setTextViewText(R.id.notif_large_kcal_sub, remainingText)
            setProgressBar(R.id.notif_large_progress_kcal, 100, percentKcal.coerceIn(0, 100), false)

            // Labels
            setTextViewText(R.id.notif_label_protein, protLabel)
            setTextViewText(R.id.notif_label_fat, fatLabel)
            setTextViewText(R.id.notif_label_carbs, carbLabel)

            // Values & Progress
            setTextViewText(R.id.notif_val_protein, "$currentProt / $goalProt $gUnit ($percentProt%)")
            setProgressBar(R.id.notif_progress_protein, 100, percentProt.coerceIn(0, 100), false)

            setTextViewText(R.id.notif_val_fat, "$currentFat / $goalFat $gUnit ($percentFat%)")
            setProgressBar(R.id.notif_progress_fat, 100, percentFat.coerceIn(0, 100), false)

            setTextViewText(R.id.notif_val_carbs, "$currentCarb / $goalCarb $gUnit ($percentCarb%)")
            setProgressBar(R.id.notif_progress_carbs, 100, percentCarb.coerceIn(0, 100), false)

            setTextViewText(R.id.notif_val_fiber, "$fiberLabel: $currentFiber / $goalFiber $gUnit")
            setTextViewText(R.id.notif_val_water, "$waterLabel: $currentWaterL / $goalWaterL $lUnit")

            // Buttons
            setTextViewText(R.id.btn_notif_large_add_text, btnAddLargeText)
            setTextViewText(R.id.btn_notif_large_scan_text, btnScanLargeText)

            setOnClickPendingIntent(R.id.btn_notif_large_add, addPendingIntent)
            setOnClickPendingIntent(R.id.btn_notif_large_scan, scanPendingIntent)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(rvSmall)
            .setCustomBigContentView(rvLarge)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
