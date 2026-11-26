package com.flycode.dailyschedule.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs

enum class DurationUnit(val label: String) {
    DAY("天"),
    WEEK("周"),
    MONTH("月");

    fun addTo(date: LocalDate, value: Int): LocalDate {
        val safe = value.coerceAtLeast(1).toLong()
        return when (this) {
            DAY -> date.plusDays(safe)
            WEEK -> date.plusWeeks(safe)
            MONTH -> date.plusMonths(safe)
        }
    }
}

enum class PatternMode(val label: String, val hint: String) {
    PER_STEP_DOSE("每步用量", "每个数字代表该阶段每次的数量，0 为停用/休息"),
    PER_STEP_FREQUENCY("每步次数", "每个数字代表该阶段每日提醒次数，0 为停用/休息"),
    WITHIN_DAY_SLOTS("单日时段", "将数字依次填入当天的各个时间段，0 自动跳过提醒")
}

data class SlotDose(
    val id: String = UUID.randomUUID().toString().take(8),
    val time: String = "08:00",
    val label: String = "",
    val amount: Double = 1.0
) {
    val parsedTime: LocalTime
        get() = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(8, 0))
}

data class PlanStage(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = "",
    val durationValue: Int = 7,
    val durationUnit: DurationUnit = DurationUnit.DAY,
    val slots: List<SlotDose> = emptyList()
) {
    val isRestStage: Boolean
        get() = slots.none { it.amount > 0.0 }
}

data class PlanItem(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val unit: String = "次",
    val note: String = "",
    val colorIndex: Int = 0,
    val startDate: LocalDate = LocalDate.now(),
    val repeatCycle: Boolean = false,
    val active: Boolean = true,
    val stages: List<PlanStage> = emptyList()
)

data class CheckInRecord(
    val planId: String,
    val date: String,
    val slotId: String,
    val checkedAtMillis: Long
) {
    val key: String get() = checkInKey(planId, date, slotId)
}

fun checkInKey(planId: String, date: LocalDate, slotId: String): String =
    checkInKey(planId, date.toString(), slotId)

fun checkInKey(planId: String, dateIso: String, slotId: String): String =
    "$planId|$dateIso|$slotId"

data class ResolvedDayPlan(
    val plan: PlanItem,
    val date: LocalDate,
    val stageIndex: Int,
    val totalStages: Int,
    val stage: PlanStage,
    val stageStartDate: LocalDate,
    val stageEndDateInclusive: LocalDate,
    val dayInStage: Int,
    val stageTotalDays: Int,
    val cycleNumber: Int,
    val isRestDay: Boolean,
    val activeSlots: List<SlotDose>
)

data class AppSettings(
    val githubRepo: String = "",
    val githubMirrorPrefix: String = "",
    val autoCheckUpdates: Boolean = true,
    val reminderLeadMinutes: Int = 0
)

data class AppState(
    val plans: List<PlanItem> = emptyList(),
    val checkIns: Map<String, CheckInRecord> = emptyMap(),
    val settings: AppSettings = AppSettings()
)

object ScheduleEngine {

    fun resolveDay(plan: PlanItem, targetDate: LocalDate): ResolvedDayPlan? {
        if (!plan.active || plan.stages.isEmpty() || targetDate.isBefore(plan.startDate)) {
            return null
        }

        val hasMonthUnit = plan.stages.any { it.durationUnit == DurationUnit.MONTH }
        if (!hasMonthUnit && plan.repeatCycle) {
            val cycleDays = plan.stages.sumOf { stage ->
                val v = stage.durationValue.coerceAtLeast(1).toLong()
                when (stage.durationUnit) {
                    DurationUnit.DAY -> v
                    DurationUnit.WEEK -> v * 7L
                    DurationUnit.MONTH -> v * 30L
                }
            }
            if (cycleDays > 0) {
                val totalElapsed = ChronoUnit.DAYS.between(plan.startDate, targetDate)
                val cycleNumber = (totalElapsed / cycleDays).toInt() + 1
                val cycleStart = plan.startDate.plusDays((cycleNumber - 1).toLong() * cycleDays)
                return resolveInsidePass(plan, targetDate, cycleStart, cycleNumber)
            }
        }

        var passStart = plan.startDate
        var cycleNumber = 1
        val maxIterations = 2400
        repeat(maxIterations) {
            val resolved = resolveInsidePass(plan, targetDate, passStart, cycleNumber)
            if (resolved != null) return resolved
            if (!plan.repeatCycle) return null
            val nextPassStart = endOfPass(plan.stages, passStart)
            if (!nextPassStart.isAfter(passStart)) return null
            passStart = nextPassStart
            cycleNumber++
        }
        return null
    }

    private fun endOfPass(stages: List<PlanStage>, start: LocalDate): LocalDate {
        var cursor = start
        for (stage in stages) {
            cursor = stage.durationUnit.addTo(cursor, stage.durationValue)
        }
        return cursor
    }

    private fun resolveInsidePass(
        plan: PlanItem,
        targetDate: LocalDate,
        passStart: LocalDate,
        cycleNumber: Int
    ): ResolvedDayPlan? {
        var cursor = passStart
        plan.stages.forEachIndexed { index, stage ->
            val nextCursor = stage.durationUnit.addTo(cursor, stage.durationValue)
            if (!targetDate.isBefore(cursor) && targetDate.isBefore(nextCursor)) {
                val totalDays = ChronoUnit.DAYS.between(cursor, nextCursor).toInt().coerceAtLeast(1)
                val dayInStage = ChronoUnit.DAYS.between(cursor, targetDate).toInt() + 1
                val activeSlots = stage.slots
                    .filter { it.amount > 0.0 }
                    .sortedBy { it.parsedTime }
                return ResolvedDayPlan(
                    plan = plan,
                    date = targetDate,
                    stageIndex = index,
                    totalStages = plan.stages.size,
                    stage = stage,
                    stageStartDate = cursor,
                    stageEndDateInclusive = nextCursor.minusDays(1),
                    dayInStage = dayInStage,
                    stageTotalDays = totalDays,
                    cycleNumber = cycleNumber,
                    isRestDay = activeSlots.isEmpty(),
                    activeSlots = activeSlots
                )
            }
            cursor = nextCursor
        }
        return null
    }

    fun resolveAllForDate(plans: List<PlanItem>, date: LocalDate): List<ResolvedDayPlan> =
        plans.mapNotNull { resolveDay(it, date) }

    fun planEndDate(plan: PlanItem): LocalDate? {
        if (plan.repeatCycle || plan.stages.isEmpty()) return null
        return endOfPass(plan.stages, plan.startDate).minusDays(1)
    }

    fun formatAmount(amount: Double): String {
        if (amount <= 0.0) return "0"
        val rounded = amount.toLong()
        return if (abs(amount - rounded.toDouble()) < 1e-6) {
            rounded.toString()
        } else {
            amount.toBigDecimal().stripTrailingZeros().toPlainString()
        }
    }
}

object PatternParser {

    fun parseNumbers(raw: String): List<Double> {
        val tokens = raw
            .trim()
            .split(Regex("[,，\\s/;；|]+"))
            .filter { it.isNotBlank() }
        return tokens.mapNotNull { token ->
            token.toDoubleOrNull()?.takeIf { it >= 0.0 }
        }
    }

    fun generateStages(
        rawPattern: String,
        mode: PatternMode,
        stepDurationValue: Int,
        stepDurationUnit: DurationUnit,
        templateTimes: List<SlotDose>,
        defaultPerSlotAmount: Double = 1.0
    ): List<PlanStage> {
        val numbers = parseNumbers(rawPattern)
        if (numbers.isEmpty()) return emptyList()

        val baseTimes = templateTimes.ifEmpty {
            listOf(
                SlotDose(id = "t1", time = "08:00", label = "早", amount = defaultPerSlotAmount),
                SlotDose(id = "t2", time = "13:00", label = "中", amount = defaultPerSlotAmount),
                SlotDose(id = "t3", time = "20:00", label = "晚", amount = defaultPerSlotAmount)
            )
        }
        val duration = stepDurationValue.coerceAtLeast(1)

        return when (mode) {
            PatternMode.PER_STEP_DOSE -> numbers.mapIndexed { idx, value ->
                val slots = if (value <= 0.0) {
                    emptyList()
                } else {
                    baseTimes.mapIndexed { slotIdx, t ->
                        t.copy(id = "s${idx}_$slotIdx", amount = value)
                    }
                }
                PlanStage(
                    name = if (value <= 0.0) "休息阶段" else "阶段 ${idx + 1}",
                    durationValue = duration,
                    durationUnit = stepDurationUnit,
                    slots = slots
                )
            }

            PatternMode.PER_STEP_FREQUENCY -> numbers.mapIndexed { idx, value ->
                val count = value.toInt().coerceAtLeast(0)
                val slots = baseTimes.take(count).mapIndexed { slotIdx, t ->
                    t.copy(
                        id = "s${idx}_$slotIdx",
                        amount = if (t.amount > 0.0) t.amount else defaultPerSlotAmount
                    )
                }
                PlanStage(
                    name = if (count == 0) "休息阶段" else "每日 $count 次",
                    durationValue = duration,
                    durationUnit = stepDurationUnit,
                    slots = slots
                )
            }

            PatternMode.WITHIN_DAY_SLOTS -> {
                val defaultTimes = listOf("08:00" to "早", "12:30" to "中", "18:30" to "晚", "22:00" to "睡前")
                val slots = numbers.mapIndexed { idx, value ->
                    val existing = templateTimes.getOrNull(idx)
                    val fallback = defaultTimes.getOrElse(idx) {
                        val hour = (8 + idx * 3).coerceAtMost(23)
                        String.format("%02d:00", hour) to "时段${idx + 1}"
                    }
                    SlotDose(
                        id = "d_$idx",
                        time = existing?.time ?: fallback.first,
                        label = existing?.label?.ifBlank { fallback.second } ?: fallback.second,
                        amount = value
                    )
                }
                listOf(
                    PlanStage(
                        name = "单日循环",
                        durationValue = duration,
                        durationUnit = stepDurationUnit,
                        slots = slots
                    )
                )
            }
        }
    }
}
