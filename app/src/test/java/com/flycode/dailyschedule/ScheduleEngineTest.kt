package com.flycode.dailyschedule

import com.flycode.dailyschedule.model.DurationUnit
import com.flycode.dailyschedule.model.PatternMode
import com.flycode.dailyschedule.model.PatternParser
import com.flycode.dailyschedule.model.PlanItem
import com.flycode.dailyschedule.model.PlanStage
import com.flycode.dailyschedule.model.ScheduleEngine
import com.flycode.dailyschedule.model.SlotDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleEngineTest {

    private val threeSlots = listOf(
        SlotDose(id = "s1", time = "08:00", label = "早", amount = 1.0),
        SlotDose(id = "s2", time = "13:00", label = "中", amount = 1.0),
        SlotDose(id = "s3", time = "20:00", label = "晚", amount = 1.0)
    )

    @Test
    fun `pattern 2 2 2 2 0 with repeatCycle handles active and rest days accurately`() {
        val stages = PatternParser.generateStages(
            rawPattern = "2, 2, 2, 2, 0",
            mode = PatternMode.PER_STEP_DOSE,
            stepDurationValue = 1,
            stepDurationUnit = DurationUnit.DAY,
            templateTimes = threeSlots
        )
        val start = LocalDate.of(2026, 1, 1)
        val plan = PlanItem(
            id = "p1",
            title = "阶段项目 A",
            unit = "次",
            startDate = start,
            repeatCycle = true,
            stages = stages
        )

        // Days 1..4 -> dose 2 across 3 slots
        for (offset in 0L..3L) {
            val resolved = ScheduleEngine.resolveDay(plan, start.plusDays(offset))
            assertNotNull(resolved)
            assertFalse(resolved!!.isRestDay)
            assertEquals(3, resolved.activeSlots.size)
            assertEquals(2.0, resolved.activeSlots[0].amount, 1e-6)
            assertEquals(1, resolved.cycleNumber)
        }

        // Day 5 -> rest day (0)
        val day5 = ScheduleEngine.resolveDay(plan, start.plusDays(4))
        assertNotNull(day5)
        assertTrue(day5!!.isRestDay)
        assertTrue(day5.activeSlots.isEmpty())
        assertEquals(4, day5.stageIndex)

        // Day 6 -> wraps around to cycle 2, stage 0
        val day6 = ScheduleEngine.resolveDay(plan, start.plusDays(5))
        assertNotNull(day6)
        assertEquals(2, day6!!.cycleNumber)
        assertEquals(0, day6.stageIndex)
        assertEquals(2.0, day6.activeSlots[0].amount, 1e-6)
    }

    @Test
    fun `pattern 2 1 1 1 0 weekly tapering stops when repeatCycle is false`() {
        val stages = PatternParser.generateStages(
            rawPattern = "2，1，1，1，0",
            mode = PatternMode.PER_STEP_DOSE,
            stepDurationValue = 1,
            stepDurationUnit = DurationUnit.WEEK,
            templateTimes = threeSlots
        )
        val start = LocalDate.of(2026, 3, 1)
        val plan = PlanItem(
            id = "p2",
            title = "周阶段方案",
            startDate = start,
            repeatCycle = false,
            stages = stages
        )

        val week1Day3 = ScheduleEngine.resolveDay(plan, start.plusDays(2))!!
        assertEquals(0, week1Day3.stageIndex)
        assertEquals(2.0, week1Day3.activeSlots[0].amount, 1e-6)
        assertEquals(3, week1Day3.dayInStage)
        assertEquals(7, week1Day3.stageTotalDays)

        val week3Day1 = ScheduleEngine.resolveDay(plan, start.plusWeeks(2))!!
        assertEquals(2, week3Day1.stageIndex)
        assertEquals(1.0, week3Day1.activeSlots[0].amount, 1e-6)

        val week5Day7 = ScheduleEngine.resolveDay(plan, start.plusWeeks(4).plusDays(6))!!
        assertTrue(week5Day7.isRestDay)

        // After 5 weeks, plan ends
        assertNull(ScheduleEngine.resolveDay(plan, start.plusWeeks(5)))
    }

    @Test
    fun `within-day 2 0 2 0 skips zero slots`() {
        val stages = PatternParser.generateStages(
            rawPattern = "2, 0, 2, 0",
            mode = PatternMode.WITHIN_DAY_SLOTS,
            stepDurationValue = 30,
            stepDurationUnit = DurationUnit.DAY,
            templateTimes = emptyList()
        )
        val start = LocalDate.of(2026, 4, 1)
        val plan = PlanItem(
            id = "p3",
            title = "早晚各2",
            startDate = start,
            stages = stages
        )
        val resolved = ScheduleEngine.resolveDay(plan, start.plusDays(10))!!
        assertEquals(2, resolved.activeSlots.size)
        assertEquals("08:00", resolved.activeSlots[0].time)
        assertEquals("18:30", resolved.activeSlots[1].time)
        assertEquals(2.0, resolved.activeSlots[0].amount, 1e-6)
        assertEquals(2.0, resolved.activeSlots[1].amount, 1e-6)
    }

    @Test
    fun `multi-month stages follow exact calendar month lengths`() {
        val start = LocalDate.of(2026, 1, 31)
        val plan = PlanItem(
            id = "p4",
            title = "多月长期计划",
            startDate = start,
            repeatCycle = false,
            stages = listOf(
                PlanStage(id = "m1", durationValue = 1, durationUnit = DurationUnit.MONTH, slots = threeSlots),
                PlanStage(id = "m2", durationValue = 2, durationUnit = DurationUnit.MONTH, slots = threeSlots.take(1))
            )
        )

        // 2026-01-31 + 1 month = 2026-02-28 (Stage 1 ends on Feb 27; Stage 2 starts Feb 28)
        val lastDayStage1 = ScheduleEngine.resolveDay(plan, LocalDate.of(2026, 2, 27))!!
        assertEquals(0, lastDayStage1.stageIndex)
        assertEquals(28, lastDayStage1.stageTotalDays)

        val firstDayStage2 = ScheduleEngine.resolveDay(plan, LocalDate.of(2026, 2, 28))!!
        assertEquals(1, firstDayStage2.stageIndex)
        assertEquals(1, firstDayStage2.dayInStage)
        assertEquals(1, firstDayStage2.activeSlots.size)

        // End of stage 2: Feb 28 + 2 months = Apr 28 exclusive -> Apr 27 inclusive
        assertNotNull(ScheduleEngine.resolveDay(plan, LocalDate.of(2026, 4, 27)))
        assertNull(ScheduleEngine.resolveDay(plan, LocalDate.of(2026, 4, 28)))
    }
}
