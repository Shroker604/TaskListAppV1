package com.example.aitasklist.scheduler

data class TimeSlot(
    val start: Long,
    val end: Long
) {
    val durationMillis: Long
        get() = end - start
}

class CalendarGapManager {

    /**
     * Finds free time slots for the given day, considering existing events.
     * @param dayStartMillis Start of the window (e.g. 9:00 AM)
     * @param dayEndMillis End of the window (e.g. 5:00 PM)
     * @param events List of busy time ranges (Start, End)
     * @return List of available TimeSlots
     */
    fun findGaps(
        dayStartMillis: Long,
        dayEndMillis: Long,
        events: List<TimeSlot>
    ): List<TimeSlot> {
        // 1. Normalize and Clip Events
        val sortedEvents = events
            .filter { it.end > dayStartMillis && it.start < dayEndMillis } // Filter out of bounds
            .map { 
                TimeSlot(
                    start = maxOf(it.start, dayStartMillis),
                    end = minOf(it.end, dayEndMillis)
                )
            }
            .sortedBy { it.start }

        // 2. Merge Overlapping Events
        val mergedEvents = mutableListOf<TimeSlot>()
        for (event in sortedEvents) {
            if (mergedEvents.isEmpty()) {
                mergedEvents.add(event)
            } else {
                val lastEvent = mergedEvents.last()
                if (event.start < lastEvent.end) {
                    // Overlap found, extend the last event
                    // We use maxOf for end because the new event might be completely inside the old one
                    val newEnd = maxOf(lastEvent.end, event.end)
                    mergedEvents[mergedEvents.lastIndex] = TimeSlot(lastEvent.start, newEnd)
                } else {
                    mergedEvents.add(event)
                }
            }
        }

        // 3. Find Negative Space (Gaps)
        val gaps = mutableListOf<TimeSlot>()
        var cursor = dayStartMillis

        for (busyBlock in mergedEvents) {
            if (busyBlock.start > cursor) {
                // Gap found!
                gaps.add(TimeSlot(cursor, busyBlock.start))
            }
            cursor = maxOf(cursor, busyBlock.end)
        }

        // Check for final gap after last event
        if (cursor < dayEndMillis) {
            gaps.add(TimeSlot(cursor, dayEndMillis))
        }

        return gaps
    }
}
