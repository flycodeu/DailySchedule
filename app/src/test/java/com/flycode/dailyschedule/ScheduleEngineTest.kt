package com.flycode.dailyschedule

import com.flycode.dailyschedule.model.DurationUnit
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

    private fun slotsWithAmount(amount: Double): List<SlotDose> =
        if (amount <= 0.0) {
            emptyList()
        } else {
            listOf(
                SlotDose(id = "s1", time = "08:00", amount = amount),
                SlotDose(id = "s2", time = "13:00", amount = amount),
                SlotDose(id = "s3", time = "20:00", amount = amount)
            )
        }

    @Test
    fun `day-by-day plan handles flexible daily amounts and rest days with cycle`() {
        val start = LocalDate.of(2026, 1, 1)
        val plan = PlanItem(
            id = "p1",
            title = "逐日方案",
            unit = "次",
            startDate = start,
            repeatCycle = true,
            stages = listOf(1.0, 1.0, 2.0, 0.0).mapIndexed { idx, amt ->
                PlanStage(id = "d$idx", durationValue = 1, durationUnit = DurationUnit.DAY, slots = slotsWithAmount(amt))
            }
        )

        val d1 = ScheduleEngine.resolveDay(plan, start)!!
        val d2 = ScheduleEngine.resolveDay(plan, start.plusDays(1))!!
        val d3 = ScheduleEngine.resolveDay(plan, start.plusDays(2))!!
        val d4 = ScheduleEngine.resolveDay(plan, start.plusDays(3))!!
        val d5 = ScheduleEngine.resolveDay(plan, start.plusDays(4))!!

        assertEquals(1.0, d1.activeSlots[0].amount, 1e-6)
        assertEquals(1.0, d2.activeSlots[0].amount, 1e-6)
        assertEquals(2.0, d3.activeSlots[0].amount, 1e-6)
        assertTrue(d4.isRestDay)
        assertEquals(2, d5.cycleNumber)
        assertEquals(1.0, d5.activeSlots[0].amount, 1e-6)
    }

    @Test
    fun `per-day override adjusts single day without affecting neighboring days`() {
        val start = LocalDate.of(2026, 5, 1)
        val tomorrow = start.plusDays(1)
        val dayAfter = start.plusDays(2)

        val plan = PlanItem(
            id = "p2",
            title = "常规计划",
            startDate = start,
            stages = listOf(
                PlanStage(id = "st1", durationValue = 30, durationUnit = DurationUnit.DAY, slots = slotsWithAmount(1.0))
            ),
            dayOverrides = mapOf(
                tomorrow.toString() to slotsWithAmount(2.0),
                dayAfter.toString() to emptyList()
            )
        )

        val rToday = ScheduleEngine.resolveDay(plan, start)!!
        val rTomorrow = ScheduleEngine.resolveDay(plan, tomorrow)!!
        val rDayAfter = ScheduleEngine.resolveDay(plan, dayAfter)!!
        val rDay4 = ScheduleEngine.resolveDay(plan, start.plusDays(3))!!

        assertFalse(rToday.isOverridden)
        assertEquals(1.0, rToday.activeSlots[0].amount, 1e-6)

        assertTrue(rTomorrow.isOverridden)
        assertEquals(2.0, rTomorrow.activeSlots[0].amount, 1e-6)

        assertTrue(rDayAfter.isOverridden)
        assertTrue(rDayAfter.isRestDay)

        assertFalse(rDay4.isOverridden)
        assertEquals(1.0, rDay4.activeSlots[0].amount, 1e-6)
    }

    @Test
    fun `multi-month stages follow exact calendar month lengths`() {
        val start = LocalDate.of(2026, 1, 31)
        val plan = PlanItem(
            id = "p3",
            title = "多月长期计划",
            startDate = start,
            repeatCycle = false,
            stages = listOf(
                PlanStage(id = "m1", durationValue = 1, durationUnit = DurationUnit.MONTH, slots = slotsWithAmount(2.0)),
                PlanStage(id = "m2", durationValue = 2, durationUnit = DurationUnit.MONTH, slots = slotsWithAmount(1.0))
            )
        )

        val lastDayStage1 = ScheduleEngine.resolveDay(plan, LocalDate.of(2026, 2, 27))!!
        assertEquals(0, lastDayStage1.stageIndex)
        assertEquals(28, lastDayStage1.stageTotalDays)
        assertEquals(2.0, lastDayStage1.activeSlots[0].amount, 1e-6)

        val firstDayStage2 = ScheduleEngine.resolveDay(plan, LocalDate.of(2026, 2, 28))!!
        assertEquals(1, firstDayStage2.stageIndex)
        assertEquals(1, firstDayStage2.dayInStage)
        assertEquals(1.0, firstDayStage2.activeSlots[0].amount, 1e-6)

        assertNotNull(ScheduleEngine.resolveDay(plan, LocalDate.of(2026, 4, 27)))
        assertNull(ScheduleEngine.resolveDay(plan, LocalDate.of(2026, 4, 28)))
    }
}
