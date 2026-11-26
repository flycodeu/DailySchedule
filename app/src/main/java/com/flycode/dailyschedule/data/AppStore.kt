package com.flycode.dailyschedule.data

import android.content.Context
import com.flycode.dailyschedule.BuildConfig
import com.flycode.dailyschedule.model.AppSettings
import com.flycode.dailyschedule.model.AppState
import com.flycode.dailyschedule.model.CheckInRecord
import com.flycode.dailyschedule.model.DurationUnit
import com.flycode.dailyschedule.model.PlanItem
import com.flycode.dailyschedule.model.PlanStage
import com.flycode.dailyschedule.model.SlotDose
import com.flycode.dailyschedule.model.checkInKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors

object AppStore {

    private const val FILE_NAME = "daily_schedule_v1.json"
    private val lock = Any()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var appContext: Context? = null

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun init(context: Context): AppState {
        synchronized(lock) {
            val ctx = context.applicationContext
            appContext = ctx
            val file = File(ctx.filesDir, FILE_NAME)
            if (file.exists()) {
                val parsed = runCatching { fromJson(file.readText()) }.getOrNull()
                if (parsed != null) {
                    _state.value = parsed.withDefaultRepo()
                    return _state.value
                }
            }
            val initial = AppState(
                settings = AppSettings(githubRepo = BuildConfig.GITHUB_REPO)
            )
            _state.value = initial
            persistLocked(initial)
            return initial
        }
    }

    fun current(context: Context): AppState {
        if (appContext == null) {
            return init(context)
        }
        return _state.value
    }

    fun upsertPlan(context: Context, plan: PlanItem) {
        update(context) { current ->
            val exists = current.plans.any { it.id == plan.id }
            val nextPlans = if (exists) {
                current.plans.map { if (it.id == plan.id) plan else it }
            } else {
                current.plans + plan
            }
            current.copy(plans = nextPlans)
        }
    }

    fun deletePlan(context: Context, planId: String) {
        update(context) { current ->
            current.copy(
                plans = current.plans.filterNot { it.id == planId },
                checkIns = current.checkIns.filterValues { it.planId != planId }
            )
        }
    }

    fun togglePlanActive(context: Context, planId: String) {
        update(context) { current ->
            current.copy(
                plans = current.plans.map {
                    if (it.id == planId) it.copy(active = !it.active) else it
                }
            )
        }
    }

    fun setDayOverride(
        context: Context,
        planId: String,
        date: LocalDate,
        slots: List<SlotDose>?
    ) {
        val dateIso = date.toString()
        update(context) { current ->
            current.copy(
                plans = current.plans.map { plan ->
                    if (plan.id != planId) {
                        plan
                    } else {
                        val nextOverrides = plan.dayOverrides.toMutableMap()
                        if (slots == null) {
                            nextOverrides.remove(dateIso)
                        } else {
                            nextOverrides[dateIso] = slots
                        }
                        plan.copy(dayOverrides = nextOverrides)
                    }
                }
            )
        }
    }

    fun setCheckIn(
        context: Context,
        planId: String,
        date: LocalDate,
        slotId: String,
        checked: Boolean
    ) {
        val dateIso = date.toString()
        val key = checkInKey(planId, dateIso, slotId)
        update(context) { current ->
            val next = current.checkIns.toMutableMap()
            if (checked) {
                next[key] = CheckInRecord(
                    planId = planId,
                    date = dateIso,
                    slotId = slotId,
                    checkedAtMillis = System.currentTimeMillis()
                )
            } else {
                next.remove(key)
            }
            current.copy(checkIns = next)
        }
    }

    fun toggleCheckIn(context: Context, planId: String, date: LocalDate, slotId: String): Boolean {
        val key = checkInKey(planId, date, slotId)
        val currentlyChecked = current(context).checkIns.containsKey(key)
        setCheckIn(context, planId, date, slotId, !currentlyChecked)
        return !currentlyChecked
    }

    fun updateSettings(context: Context, transform: (AppSettings) -> AppSettings) {
        update(context) { current ->
            current.copy(settings = transform(current.settings))
        }
    }

    fun exportJson(context: Context): String = toJson(current(context)).toString(2)

    fun importJson(context: Context, rawJson: String): Result<Int> = runCatching {
        val imported = fromJson(rawJson).withDefaultRepo()
        synchronized(lock) {
            appContext = context.applicationContext
            _state.value = imported
            persistLocked(imported)
        }
        imported.plans.size
    }

    private fun update(context: Context, transform: (AppState) -> AppState) {
        val updated: AppState
        synchronized(lock) {
            if (appContext == null) init(context)
            updated = transform(_state.value)
            _state.value = updated
        }
        persistAsync(updated)
    }

    private fun persistAsync(data: AppState) {
        val ctx = appContext ?: return
        val snapshotJson = toJson(data).toString()
        ioExecutor.execute {
            writeSnapshot(ctx, snapshotJson)
        }
    }

    private fun persistLocked(data: AppState) {
        val ctx = appContext ?: return
        writeSnapshot(ctx, toJson(data).toString())
    }

    private fun writeSnapshot(ctx: Context, jsonText: String) {
        runCatching {
            val dir = ctx.filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, FILE_NAME)
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(jsonText)
            if (!tmp.renameTo(file)) {
                file.writeText(jsonText)
                tmp.delete()
            }
        }
    }

    private fun AppState.withDefaultRepo(): AppState =
        if (settings.githubRepo.isBlank()) {
            copy(settings = settings.copy(githubRepo = BuildConfig.GITHUB_REPO))
        } else {
            this
        }

    fun toJson(state: AppState): JSONObject = JSONObject().apply {
        put("plans", JSONArray().apply {
            state.plans.forEach { put(planToJson(it)) }
        })
        put("checkIns", JSONArray().apply {
            state.checkIns.values.forEach { record ->
                put(JSONObject().apply {
                    put("planId", record.planId)
                    put("date", record.date)
                    put("slotId", record.slotId)
                    put("checkedAtMillis", record.checkedAtMillis)
                })
            }
        })
        put("settings", JSONObject().apply {
            put("githubRepo", state.settings.githubRepo)
            put("githubMirrorPrefix", state.settings.githubMirrorPrefix)
            put("autoCheckUpdates", state.settings.autoCheckUpdates)
            put("remindersEnabled", state.settings.remindersEnabled)
            put("hidePermissionPrompts", state.settings.hidePermissionPrompts)
            put("reminderLeadMinutes", state.settings.reminderLeadMinutes)
        })
    }

    fun fromJson(raw: String): AppState {
        val root = JSONObject(raw)
        val plansArr = root.optJSONArray("plans") ?: JSONArray()
        val plans = buildList {
            for (i in 0 until plansArr.length()) {
                add(planFromJson(plansArr.getJSONObject(i)))
            }
        }
        val checkArr = root.optJSONArray("checkIns") ?: JSONArray()
        val checkIns = buildMap {
            for (i in 0 until checkArr.length()) {
                val obj = checkArr.getJSONObject(i)
                val record = CheckInRecord(
                    planId = obj.getString("planId"),
                    date = obj.getString("date"),
                    slotId = obj.getString("slotId"),
                    checkedAtMillis = obj.optLong("checkedAtMillis", 0L)
                )
                put(record.key, record)
            }
        }
        val settingsObj = root.optJSONObject("settings") ?: JSONObject()
        val settings = AppSettings(
            githubRepo = settingsObj.optString("githubRepo", BuildConfig.GITHUB_REPO),
            githubMirrorPrefix = settingsObj.optString("githubMirrorPrefix", ""),
            autoCheckUpdates = settingsObj.optBoolean("autoCheckUpdates", true),
            remindersEnabled = settingsObj.optBoolean("remindersEnabled", true),
            hidePermissionPrompts = settingsObj.optBoolean("hidePermissionPrompts", false),
            reminderLeadMinutes = settingsObj.optInt("reminderLeadMinutes", 0)
        )
        return AppState(plans = plans, checkIns = checkIns, settings = settings)
    }

    private fun planToJson(plan: PlanItem): JSONObject = JSONObject().apply {
        put("id", plan.id)
        put("title", plan.title)
        put("unit", plan.unit)
        put("note", plan.note)
        put("colorIndex", plan.colorIndex)
        put("startDate", plan.startDate.toString())
        put("repeatCycle", plan.repeatCycle)
        put("active", plan.active)
        put("stages", JSONArray().apply {
            plan.stages.forEach { stage ->
                put(JSONObject().apply {
                    put("id", stage.id)
                    put("name", stage.name)
                    put("durationValue", stage.durationValue)
                    put("durationUnit", stage.durationUnit.name)
                    put("slots", JSONArray().apply {
                        stage.slots.forEach { slot -> put(slotToJson(slot)) }
                    })
                })
            }
        })
        if (plan.dayOverrides.isNotEmpty()) {
            put("dayOverrides", JSONObject().apply {
                plan.dayOverrides.forEach { (dateIso, slots) ->
                    put(dateIso, JSONArray().apply {
                        slots.forEach { put(slotToJson(it)) }
                    })
                }
            })
        }
    }

    private fun slotToJson(slot: SlotDose): JSONObject = JSONObject().apply {
        put("id", slot.id)
        put("time", slot.time)
        put("label", slot.label)
        put("amount", slot.amount)
    }

    private fun slotFromJson(sl: JSONObject): SlotDose = SlotDose(
        id = sl.getString("id"),
        time = sl.getString("time"),
        label = sl.optString("label", ""),
        amount = sl.optDouble("amount", 1.0)
    )

    private fun planFromJson(obj: JSONObject): PlanItem {
        val stagesArr = obj.optJSONArray("stages") ?: JSONArray()
        val stages = buildList {
            for (i in 0 until stagesArr.length()) {
                val sObj = stagesArr.getJSONObject(i)
                val slotsArr = sObj.optJSONArray("slots") ?: JSONArray()
                val slots = buildList {
                    for (j in 0 until slotsArr.length()) {
                        add(slotFromJson(slotsArr.getJSONObject(j)))
                    }
                }
                val unitName = sObj.optString("durationUnit", DurationUnit.DAY.name)
                val unit = runCatching { DurationUnit.valueOf(unitName) }.getOrDefault(DurationUnit.DAY)
                add(
                    PlanStage(
                        id = sObj.getString("id"),
                        name = sObj.optString("name", ""),
                        durationValue = sObj.optInt("durationValue", 1).coerceAtLeast(1),
                        durationUnit = unit,
                        slots = slots
                    )
                )
            }
        }
        val overridesObj = obj.optJSONObject("dayOverrides")
        val dayOverrides = buildMap {
            if (overridesObj != null) {
                val keys = overridesObj.keys()
                while (keys.hasNext()) {
                    val dateKey = keys.next()
                    val arr = overridesObj.optJSONArray(dateKey) ?: JSONArray()
                    val slots = buildList {
                        for (j in 0 until arr.length()) {
                            add(slotFromJson(arr.getJSONObject(j)))
                        }
                    }
                    put(dateKey, slots)
                }
            }
        }
        return PlanItem(
            id = obj.getString("id"),
            title = obj.getString("title"),
            unit = obj.optString("unit", "次"),
            note = obj.optString("note", ""),
            colorIndex = obj.optInt("colorIndex", 0),
            startDate = runCatching { LocalDate.parse(obj.getString("startDate")) }
                .getOrDefault(LocalDate.now()),
            repeatCycle = obj.optBoolean("repeatCycle", false),
            active = obj.optBoolean("active", true),
            stages = stages,
            dayOverrides = dayOverrides
        )
    }
}
