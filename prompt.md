# AI Task List Android App Prompt

Create a modern Android application called **"AI Task List"** using **Kotlin** and **Jetpack Compose**. The app should leverage **Material3** design principles and support **Dark Mode**.

## Core Features

1.  **Smart Task Management**
    -   Users can add tasks manually or generate them using AI.
    -   Each task should track:
        -   `id` (UUID)
        -   `content` (String)
        -   `isCompleted` (Boolean)
        -   `createdAt` (Long timestamp)
        -   `scheduledDate` (Long timestamp)
        -   `calendarEventId` (Long?, nullable for calendar sync)
    -   Support toggling completion, deleting tasks, and clearing all completed tasks.

2.  **AI Integration (Gemini SDK)**
    -   Integrate the **Google Gemini SDK** (`com.google.ai.client.generativeai`).
    -   Implement a feature where users can type natural language requests (e.g., "Plan a party for Friday") and the AI breaks it down into actionable sub-tasks.
    -   Include a toggle to enable/disable splitting the input into multiple tasks.

3.  **Local Persistence (Room Database)**
    -   Use **Room** for local data storage.
    -   Create a `Task` entity and a `TaskDao` with methods to:
        -   Insert (single and list)
        -   Update
        -   Delete
        -   Get all tasks (as a `Flow`)
        -   Delete all completed tasks

4.  **Calendar Integration**
    -   Allow users to sync tasks to their device's default calendar.
    -   Implement a `CalendarRepository` that uses `ContentResolver` to:
        -   Read available calendars.
        -   Insert events (default duration: 1 hour).
        -   Update event details (title, time) when the task changes.
        -   Delete events when the task is deleted or completed.
    -   Store the `calendarEventId` in the `Task` entity to maintain the link.

5.  **Home Screen Widget**
    -   Create a `RemoteViews` based App Widget (`TaskWidgetProvider`) that displays the current list of tasks on the home screen.
    -   The widget should update when data changes.

## Architecture & Tech Stack

-   **Language**: Kotlin
-   **UI**: Jetpack Compose (Material3)
-   **Architecture**: MVVM (Model-View-ViewModel)
-   **Concurrency**: Coroutines & Flow
-   **Dependency Injection**: Manual DI (or Hilt if preferred, but Manual is currently used in `TaskApplication`).
-   **Build System**: Gradle (Kotlin DSL) with Version Catalogs or standard dependencies.

## Key Files Structure

-   `model/Task.kt`: The data entity.
-   `data/dao/TaskDao.kt`: Room DAO.
-   `data/repository/CalendarRepository.kt`: Handles calendar ContentProvider operations.
-   `data/remote/GeminiRepository.kt`: Handles AI API calls.
-   `viewmodel/TaskViewModel.kt`: Manages UI state (`TaskUiState`) and business logic.
-   `ui/TaskListScreen.kt`: Main composable screen.
-   `TaskApplication.kt`: Application class for initializing the database.

## Additional Context
-   The app should handle permissions for Calendar access (`READ_CALENDAR`, `WRITE_CALENDAR`).
-   Ensure the UI is responsive and handles loading/error states for AI operations.
