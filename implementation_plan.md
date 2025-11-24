
# Implementation Plan - Add Persistence with Room

The goal is to persist tasks so they are not lost when the app is closed. We will use the **Room Persistence Library**.

## User Review Required
> [!IMPORTANT]
> This change involves adding new dependencies (Room, KSP) and modifying the architecture to use a database.

## Proposed Changes

### Build Configuration
#### [MODIFY] [build.gradle.kts (Project)](file:///e:/AntiGravity%20Projects/Projects/TaskList%20AntiG/build.gradle.kts)
- Add KSP plugin version `1.9.21-1.0.15`.

#### [MODIFY] [build.gradle.kts (App)](file:///e:/AntiGravity%20Projects/Projects/TaskList%20AntiG/app/build.gradle.kts)
- Apply KSP plugin.
- Add Room dependencies (`room-runtime`, `room-ktx`, `room-compiler`).

### Data Layer
#### [MODIFY] [Task.kt](file:///e:/AntiGravity%20Projects/Projects/TaskList%20AntiG/app/src/main/java/com/example/aitasklist/Task.kt)
- Annotate with `@Entity`.
- Ensure `id` is `@PrimaryKey`.

#### [NEW] [TaskDao.kt](file:///e:/AntiGravity%20Projects/Projects/TaskList%20AntiG/app/src/main/java/com/example/aitasklist/TaskDao.kt)
- Interface for database operations (Get, Insert, Update, Delete).

#### [NEW] [AppDatabase.kt](file:///e:/AntiGravity%20Projects/Projects/TaskList%20AntiG/app/src/main/java/com/example/aitasklist/AppDatabase.kt)
- Abstract class extending `RoomDatabase`.

#### [NEW] [TaskApplication.kt](file:///e:/AntiGravity%20Projects/Projects/TaskList%20AntiG/app/src/main/java/com/example/aitasklist/TaskApplication.kt)
- Initialize the database singleton.

### UI/ViewModel Layer
#### [MODIFY] [TaskViewModel.kt](file:///e:/AntiGravity%20Projects/Projects/TaskList%20AntiG/app/src/main/java/com/example/aitasklist/TaskViewModel.kt)
- Convert to `AndroidViewModel` (or use Factory) to access `TaskDao`.
- Replace in-memory list with `dao.getAllTasks()` flow.
- Update actions (generate, toggle, remove) to use DAO.

#### [MODIFY] [AndroidManifest.xml](file:///e:/AntiGravity%20Projects/Projects/TaskList%20AntiG/app/src/main/AndroidManifest.xml)
- Register `TaskApplication`.

## Verification Plan
### Automated Tests
- Run `assembleDebug` to ensure KSP and Room are configured correctly.

### Manual Verification
1.  Install the app.
2.  Add some tasks.
3.  Close the app (swipe away).
4.  Re-open the app.
5.  Verify tasks are still there.
