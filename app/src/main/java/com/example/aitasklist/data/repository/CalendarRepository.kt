package com.example.aitasklist.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.TimeZone

class CalendarRepository(private val context: Context) {

    fun addToCalendar(title: String, description: String, startTime: Long, endTime: Long, calendarId: Long? = null): Pair<Long, String>? {
        val targetCalendarId: Long
        val accountName: String

        if (calendarId != null) {
            targetCalendarId = calendarId
            accountName = getAccountName(calendarId) ?: "Unknown Account"
        } else {
            val calendarInfo = getPrimaryCalendarInfo() ?: return null
            targetCalendarId = calendarInfo.first
            accountName = calendarInfo.second
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, endTime)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.CALENDAR_ID, targetCalendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment?.toLong()
            return if (eventId != null) Pair(eventId, accountName) else null
        } catch (e: SecurityException) {
            e.printStackTrace()
            return null
        }
    }

    private fun getAccountName(calendarId: Long): String? {
        val projection = arrayOf(CalendarContract.Calendars.ACCOUNT_NAME)
        val uri = android.content.ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
        
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return null
    }

    // ... (updateCalendarEvent, deleteCalendarEvent, getEventUri remain same)

    private fun getPrimaryCalendarInfo(): Pair<Long, String>? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        
        try {
            // Try to find the primary calendar
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.IS_PRIMARY} = 1",
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val accountName = cursor.getString(2) ?: "Unknown Account"
                    return Pair(id, accountName)
                }
            }

            // Fallback: Find any visible calendar
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_NAME),
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val accountName = cursor.getString(1) ?: "Unknown Account"
                    return Pair(id, accountName)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        
        return null
    }

    fun updateCalendarEvent(eventId: Long, title: String, description: String, startTime: Long, endTime: Long) {
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, endTime)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
        }

        val updateUri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        
        try {
            context.contentResolver.update(updateUri, values, null, null)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun deleteCalendarEvent(eventId: Long) {
        val deleteUri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        
        try {
            context.contentResolver.delete(deleteUri, null, null)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun getEventUri(eventId: Long): android.net.Uri? {
        val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val projection = arrayOf(CalendarContract.Events._ID)
        
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return uri
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return null
    }


    fun getWritableCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${CalendarContract.Calendars.VISIBLE} = 1"

        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameCol = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountCol = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Unknown"
                    val account = cursor.getString(accountCol) ?: "Unknown"
                    calendars.add(CalendarInfo(id, name, account))
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return calendars
    }

    fun saveDefaultCalendarId(id: Long) {
        val prefs = context.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("default_calendar_id", id).apply()
    }

    fun getDefaultCalendarId(): Long? {
        val prefs = context.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE)
        val id = prefs.getLong("default_calendar_id", -1)
        return if (id != -1L) id else null
    }
}

data class CalendarInfo(val id: Long, val displayName: String, val accountName: String)
