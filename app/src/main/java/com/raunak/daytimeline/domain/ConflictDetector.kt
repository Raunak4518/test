package com.raunak.daytimeline.domain

import com.raunak.daytimeline.data.TaskEntity

object ConflictDetector {
    fun maxOverlapMinutes(candidate: TaskEntity, existing: List<TaskEntity>): Int {
        var max = 0
        existing.forEach {
            val overlap = minOf(candidate.endMinute, it.endMinute) - maxOf(candidate.startMinute, it.startMinute)
            if (overlap > max) max = overlap
        }
        return max.coerceAtLeast(0)
    }
}
