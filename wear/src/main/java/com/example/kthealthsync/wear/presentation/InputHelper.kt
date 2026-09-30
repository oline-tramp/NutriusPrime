package com.example.kthealthsync.wear.presentation

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import androidx.wear.input.RemoteInputIntentHelper

object InputHelper {
    const val KEY_SEARCH_QUERY = "search_query"

    fun createSearchIntent(context: Context): Intent {
        val remoteInput = RemoteInput.Builder(KEY_SEARCH_QUERY)
            .setLabel("Назовите или введите продукт")
            .build()

        val samsungIntent = Intent("android.support.wearable.input.action.REMOTE_INPUT").apply {
            putExtra("android.support.wearable.input.extra.REMOTE_INPUTS", arrayOf(remoteInput))
            putExtra("android.support.wearable.input.extra.TITLE", "Поиск в Nutrius")
        }

        if (samsungIntent.resolveActivity(context.packageManager) != null) {
            return samsungIntent
        }

        val fallback = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(fallback, listOf(remoteInput))
        RemoteInputIntentHelper.putTitleExtra(fallback, "Поиск в Nutrius")
        return fallback
    }

    fun extractQuery(intent: Intent?): String? {
        if (intent == null) return null
        val bundle = RemoteInput.getResultsFromIntent(intent) ?: return null
        return bundle.getCharSequence(KEY_SEARCH_QUERY)?.toString()?.trim()
    }
}
