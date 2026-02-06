package com.example.aitasklist.di

import android.content.Context
import com.example.aitasklist.data.remote.CloudGeminiRepository
import com.example.aitasklist.data.local.OnDeviceGeminiRepository
import com.example.aitasklist.data.repository.CalendarRepository
import com.example.aitasklist.data.local.AppDatabase
import com.example.aitasklist.scheduler.ReminderManager
import com.example.aitasklist.scheduler.AutoScheduler
import com.example.aitasklist.scheduler.CalendarGapManager
import com.example.aitasklist.domain.SortStrategyRegistry
import com.example.aitasklist.domain.SyncManager
import com.example.aitasklist.domain.BriefingManager
import com.example.aitasklist.data.UserPreferencesRepository

object ServiceLocator {

    @Volatile
    private var database: AppDatabase? = null

    @Volatile
    var calendarRepository: CalendarRepository? = null
        private set

    @Volatile
    var geminiRepository: com.example.aitasklist.data.remote.TaskGeneratorRepository? = null
        private set
        
    @Volatile
    var reminderManager: ReminderManager? = null
        private set

    @Volatile
    var userPreferencesRepository: UserPreferencesRepository? = null
        private set

    @Volatile
    var syncManager: SyncManager? = null
        private set
        
    @Volatile
    var briefingManager: BriefingManager? = null
        private set
        
    val sortStrategyRegistry: SortStrategyRegistry by lazy {
        SortStrategyRegistry()
    }

    fun provideTaskDao(context: Context) = getInstance(context).taskDao()

    fun getInstance(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            database ?: AppDatabase.getInstance(context).also { database = it }
        }
    }

    fun initialize(context: Context) {
        val app = context.applicationContext
        database = AppDatabase.getInstance(app)
        calendarRepository = CalendarRepository(app)
        // Initialize On-Device Repository by default (or based on flag in future)
        geminiRepository = OnDeviceGeminiRepository(app)
        reminderManager = ReminderManager(app)
        userPreferencesRepository = UserPreferencesRepository(app)
        
        val autoScheduler = AutoScheduler()
        val calendarGapManager = CalendarGapManager()
        
        syncManager = SyncManager(
            calendarRepository!!,
            database!!.taskDao(),
            autoScheduler,
            calendarGapManager,
            reminderManager!!
        )
        
        briefingManager = BriefingManager()
    }
}
