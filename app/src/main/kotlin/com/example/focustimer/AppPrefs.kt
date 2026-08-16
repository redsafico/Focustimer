package com.example.focustimer

import android.content.Context

object AppPrefs {
    private const val PREFS = "focus_timer_prefs"
    private const val SELECTED = "selected_packages"

    fun getSelectedPackages(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(SELECTED, emptySet())
            ?.toSet()
            ?: emptySet()
    }

    fun setSelectedPackages(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(SELECTED, packages.toSet())
            .apply()
    }
}
