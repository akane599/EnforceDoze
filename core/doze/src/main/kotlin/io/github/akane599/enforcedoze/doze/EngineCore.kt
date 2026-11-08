// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.doze

import io.github.akane599.enforcedoze.parse.DeviceIdleSettings
import io.github.akane599.enforcedoze.profile.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * The engine's state and command handlers (ADR 0015). Every method runs inside the actor, one at a time:
 * events update the inputs, then [reconcile] computes the targets, plans and executes.
 */
@Suppress("TooManyFunctions")
internal class EngineCore(
    device: DozeDevice,
    private val store: EngineStore,
    private val journal: EngineJournal,
    private val clock: EngineClock,
    private val allowPackage: String,
    calls: DeviceCalls,
) {
    private val book = Book(PersistedState())
    private val observer = Observer(device, calls, book)
    private val executor = Executor(device, calls, observer, book, journal, clock) { persist() }
    private var saved: PersistedState? = null
    private var storeError: String? = null
    private var started = false
    private var profile: Profile? = null
    private var screenOn = true
    private var holdOffs: Set<HoldOff> = emptySet()
    private var writable = Writable.NOTHING
    private var previous: PreviousSnapshot? = null
    private var stock: StockSnapshot? = null

    private val mode: Mode get() = book.persisted.mode

    /** Restart rule (SPEC 12.4): load the book, read everything, log what drifted while stopped, then reconcile. */
    suspend fun start(start: EngineStart) {
        book.persisted = store.load()
        saved = book.persisted
        previous = store.previous()
        stock = store.stock()
        profile = start.profile
        screenOn = start.screenOn
        holdOffs = start.holdOffs
        writable = start.writable
        started = true
        start.profile?.let { book.persisted = book.persisted.copy(profileId = it.id) }
        observer.read(Lever.entries.toSet())
        // Paused or hands off, other writers are expected; drift only means something while managed.
        val drifted = if (mode == Mode.MANAGED) drifts(Lever.entries, whileStopped = true) else emptyList()
        capturePrevious()
        reconcile(Cause.START, corrections(drifted))
    }

    suspend fun setProfile(next: Profile, reason: SwitchReason) {
        profile = next
        book.persisted = book.persisted.copy(profileId = next.id)
        journal.event(EngineEvent.ProfileSwitched(clock.now(), next.id, reason))
        reconcile(Cause.PROFILE_SWITCH)
    }

    /** Screen off: sensors, forced idle, their read-backs, then one constants read for drift (SPEC 5.6, ADR 0015). */
    suspend fun screen(on: Boolean) {
        screenOn = on
        if (!started) return
        if (on) {
            reconcile(Cause.SCREEN_ON)
            return
        }
        reconcile(Cause.SCREEN_OFF)
        checkConstantsDrift()
    }

    suspend fun holdOff(holdOff: HoldOff, active: Boolean) {
        val next = if (active) holdOffs + holdOff else holdOffs - holdOff
        if (next == holdOffs) return
        holdOffs = next
        val now = clock.now()
        journal.event(if (active) EngineEvent.HoldOffStarted(now, holdOff) else EngineEvent.HoldOffEnded(now, holdOff))
        reconcile(Cause.HOLD_OFF)
    }

    suspend fun setWritable(next: Writable) {
        writable = next
        reconcile(Cause.PRIVILEGE)
    }

    /** The app's observer saw the global string change (ADR 0014); the engine's own writes read back as no drift. */
    suspend fun foreignWriteSeen() {
        if (started) checkConstantsDrift()
    }

    /** Pause or hand off, or resume: then guards are cleared and whatever changed meanwhile is read and re-applied. */
    suspend fun setMode(next: Mode) {
        changeMode(next)
        if (next != Mode.MANAGED || !started) {
            reconcile(Cause.OWNER)
            return
        }
        clearGuards()
        observer.read(Lever.entries.toSet())
        reconcile(Cause.OWNER, corrections(drifts(Lever.entries, whileStopped = false)))
    }

    /** The owner chose "keep re-applying" for [lever]: the guard is cleared and the lever re-applied now. */
    suspend fun clearGuard(lever: Lever) {
        book.persisted = book.persisted.copy(guarded = book.persisted.guarded - lever, corrections = book.persisted.corrections - lever)
        reconcile(Cause.OWNER, setOf(lever))
    }

    fun setReapplyOnDrift(on: Boolean) {
        book.persisted = book.persisted.copy(reapplyOnDrift = on)
    }

    suspend fun setAllowlistKeep(packages: Set<String>?) {
        book.persisted = book.persisted.copy(allowlistKeep = packages)
        reconcile(Cause.OWNER)
    }

    /** SPEC 5.6 step 3, ADR 0015: every lever to stock, verified against `stock.json`, then paused. */
    suspend fun restoreStock(): JobResult {
        if (!started) return JobResult(refused = NOT_STARTED)
        observer.read(Lever.entries.toSet())
        capturePrevious()
        // A string the engine did not write and could not save in previous.json is never deleted: that could not be undone.
        val keepString = previous == null && book.written[Lever.CONSTANTS] == null
        val targets = Targets.restoreStock(book.persisted.ownerAllowlist).let { if (keepString) it - Lever.CONSTANTS else it }
        val job = oneShot(targets, Cause.RESTORE_STOCK)
        val snapshot = stock
        val differences = snapshot?.let {
            observer.settle(ConstantsValue.Absent, it, DozeEngine.SETTLE_MILLIS, DozeEngine.SETTLE_POLL_MILLIS)
        }
        clearGuards()
        changeMode(Mode.PAUSED)
        reconcile(Cause.RESTORE_STOCK)
        return job.copy(
            skipped = if (keepString) mapOf(Lever.CONSTANTS to UNSAVED_STRING) else emptyMap(),
            stockDifferences = differences,
            unverified = when {
                snapshot == null -> "stock.json was never captured, so the values could not be compared with it"
                differences == null -> "dumpsys deviceidle could not be read, so the values could not be compared with stock.json"
                else -> null
            },
        )
    }

    /** SPEC 5.2, 11.4: the previous string back exactly, then hands off so the owner's setup is never fought. */
    suspend fun returnToPrevious(): JobResult {
        if (!started) return JobResult(refused = NOT_STARTED)
        val snapshot = previous ?: return JobResult(refused = "previous.json was never captured")
        val targets = Targets.returnToPrevious(snapshot, book.written, book.persisted.ownerAllowlist)
        observer.read(targets.keys)
        val ours = book.written[Lever.CONSTANTS]
        val job = oneShot(targets, Cause.RETURN_TO_PREVIOUS)
        // The owner's string is theirs again: booked as the engine's, a later profile without constants would delete it.
        val stillOurs = ours != null && ours != targets[Lever.CONSTANTS] && book.seen[Lever.CONSTANTS] == ours
        if (!stillOurs) book.forgetWritten(Lever.CONSTANTS)
        clearGuards()
        changeMode(Mode.HANDS_OFF)
        reconcile(Cause.RETURN_TO_PREVIOUS)
        return job
    }

    /**
     * The `stock.json` capture (SPEC 5.2, ADR 0012), with consent and the screen on: delete the string,
     * read the effective values, put the string back exactly. The put-back runs in a `finally`.
     */
    suspend fun captureStock(): JobResult {
        refuseCapture()?.let { return JobResult(refused = it) }
        observer.read(setOf(Lever.CONSTANTS))
        val current = (book.seen[Lever.CONSTANTS] as? Setting.Constants)?.value
            ?: return JobResult(refused = "the global string cannot be read, so it could not be put back exactly")
        capturePrevious()
        val written = book.written
        val actions = mutableListOf<ActionRecord>()
        val snapshot = try {
            readStock(current, actions)
        } finally {
            withContext(NonCancellable) {
                if (current is ConstantsValue.Exact) putBack(Setting.Constants(current), actions)
                book.persisted = book.persisted.copy(written = written)
            }
        }
        val captured = snapshot.getOrElse { return JobResult(actions, refused = it.message) }
        stock = captured
        store.saveStock(captured)
        reconcile(Cause.STOCK_CAPTURE)
        return JobResult(actions, stock = captured)
    }

    /** Writes the string found before the capture back, and once more if the read-back does not show it. */
    private suspend fun putBack(string: Setting.Constants, actions: MutableList<ActionRecord>) {
        repeat(PUT_BACK_TRIES) {
            actions += executor.execute(listOf(string), null, writeAhead = false) { Cause.STOCK_CAPTURE }
            if (book.seen[Lever.CONSTANTS] == string) return
        }
    }

    /** Deletes the string if there is one and reads what the phone falls back to. The caller puts the string back. */
    private suspend fun readStock(current: ConstantsValue, actions: MutableList<ActionRecord>): Result<StockSnapshot> {
        if (current is ConstantsValue.Exact) {
            actions += executor.execute(listOf(Setting.ABSENT), null, writeAhead = false) { Cause.STOCK_CAPTURE }
            val deleted = book.seen[Lever.CONSTANTS] == Setting.ABSENT
            if (!deleted) return Result.failure(IllegalStateException("the string could not be deleted"))
            awaitReload(current.text)
        }
        val values = if (observer.readDeviceIdle()) book.effective else null
        return values?.let { Result.success(StockSnapshot(clock.now(), it.mapValues { (_, value) -> value.raw })) }
            ?: Result.failure(IllegalStateException("dumpsys deviceidle could not be read"))
    }

    /** Saves the book if it changed or the last save failed; false when the store fails, which the status shows. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun persist(): Boolean {
        if (book.persisted == saved && storeError == null) return true
        return try {
            store.save(book.persisted)
            saved = book.persisted
            storeError = null
            true
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            storeError = e.toString()
            false
        } catch (e: Exception) {
            storeError = e.toString()
            false
        }
    }

    fun status(): EngineStatus {
        val targets = targets()
        return EngineStatus(
            started = started,
            mode = mode,
            profileId = profile?.id,
            screenOn = screenOn,
            holdOffs = holdOffs,
            levers = Lever.entries.associateWith { leverStatus(it, targets) },
            guarded = book.persisted.guarded,
            missingKeys = targets.missingKeys,
            forcedWithoutWorker = book.written[Lever.FORCE_IDLE] == Setting.FORCED && !writable.forceIdle,
            constantsWriter = book.writer,
            previousCaptured = previous != null,
            stockCaptured = stock != null,
            storeError = storeError,
        )
    }

    private fun targets(): TargetSet = Targets.compute(
        TargetInputs(
            profile = profile,
            mode = mode,
            screenOn = screenOn,
            holdOffs = holdOffs,
            guarded = book.persisted.guarded,
            knownKeys = book.knownKeys,
            snapshotsReady = previous != null && stock != null,
            allowPackage = allowPackage,
            allowlistKeep = book.persisted.allowlistKeep,
            ownerAllowlist = book.persisted.ownerAllowlist,
            written = book.written,
        ),
    )

    /**
     * Plans and executes. A lever about to be written that has not been read since the start (or since a
     * timed-out call) is read first, and what that read shows is checked for drift (SPEC 12.4).
     */
    private suspend fun reconcile(cause: Cause, corrections: Set<Lever> = emptySet()): List<ActionRecord> {
        if (!started) return emptyList()
        var fixes = corrections
        var plan = Planner.plan(targets().wanted, book.written, writable, fixes)
        // Corrections were decided on a read made in this same command; reading them again would count twice.
        val unread = plan.map { it.lever }.filter { it !in book.seen && it !in corrections }.toSet()
        if (unread.isNotEmpty()) {
            observer.read(unread)
            fixes = fixes + corrections(drifts(unread, whileStopped = false))
            plan = Planner.plan(targets().wanted, book.written, writable, fixes)
        }
        captureOwnerAllowlist(plan)
        val actions = executor.execute(plan, stock) { lever -> if (lever in fixes) Cause.DRIFT else cause }
        releaseOwnerAllowlist()
        return actions
    }

    /** Writes [targets] where the device differs, books the rest as already there. */
    private suspend fun oneShot(targets: Map<Lever, Setting>, cause: Cause): JobResult {
        val notWritable = targets.keys.filterNot { writable.can(it) }.toSet()
        val plan = Planner.order(targets).mapNotNull { lever ->
            targets[lever]?.takeIf { it.lever !in notWritable && book.seen[lever] != it }
        }
        targets.values.filter { book.seen[it.lever] == it }.forEach { book.wrote(it) }
        captureOwnerAllowlist(plan)
        val actions = executor.execute(plan, stock) { cause }
        releaseOwnerAllowlist()
        return JobResult(actions, notWritable = notWritable)
    }

    private suspend fun checkConstantsDrift() {
        // While the engine holds a string, every foreign change is logged, guarded or not (SPEC 5.6, 5.7).
        if (mode != Mode.MANAGED || (book.written[Lever.CONSTANTS] as? Setting.Constants)?.value !is ConstantsValue.Exact) return
        observer.read(setOf(Lever.CONSTANTS))
        val fixes = corrections(drifts(listOf(Lever.CONSTANTS), whileStopped = false))
        if (fixes.isNotEmpty()) reconcile(Cause.DRIFT, fixes)
    }

    /**
     * Levers whose read differs from the engine's last write, each logged as drift with the writer when
     * known. A read that already shows what the engine wants now is not drift (a release retried after a
     * timeout, a forced idle a reboot ended).
     */
    private suspend fun drifts(levers: Collection<Lever>, whileStopped: Boolean): List<Lever> {
        val wanted = targets().wanted
        return levers.filter { lever -> isDrift(lever, wanted[lever], whileStopped) }
    }

    private suspend fun isDrift(lever: Lever, wanted: Setting?, whileStopped: Boolean): Boolean {
        val expected = book.written[lever] ?: return false
        if (wanted != null && book.seen[lever] == wanted) return false
        val found = drift(expected) ?: return false
        val writer = book.writer.takeIf { lever == Lever.CONSTANTS }
        journal.event(EngineEvent.Drift(clock.now(), lever, expected.toString(), found, writer, whileStopped))
        return true
    }

    /** What the device shows instead of [expected], or null when it matches or cannot be judged. */
    private fun drift(expected: Setting): String? {
        val seen = book.seen[expected.lever]
        return when {
            seen != null -> seen.takeIf { it != expected }?.toString()

            expected is Setting.Constants -> {
                book.constantsCheck = observer.differences(expected.value, stock)
                book.constantsCheck?.takeIf { it.isNotEmpty() }?.joinToString("; ")
            }

            else -> null
        }
    }

    /**
     * Which drifted levers to re-apply (SPEC 5.6, 5.7): only with "Re-apply on drift" on, only where the
     * engine still wants what it wrote, and never a third time within an hour: that trips the guard.
     */
    private suspend fun corrections(drifted: List<Lever>): Set<Lever> {
        if (drifted.isEmpty() || !book.persisted.reapplyOnDrift) return emptySet()
        val wanted = targets().wanted
        val now = clock.now()
        val fixes = mutableSetOf<Lever>()
        for (lever in drifted) {
            if (lever in book.persisted.guarded || wanted[lever] == null || wanted[lever] != book.written[lever]) continue
            val recent = LoopGuard.recent(book.persisted.corrections[lever].orEmpty(), now)
            val state = book.persisted
            if (recent.size >= LoopGuard.LIMIT) {
                book.persisted = state.copy(guarded = state.guarded + lever, corrections = state.corrections + (lever to recent))
                journal.event(EngineEvent.LoopGuardTripped(now, lever))
            } else {
                book.persisted = state.copy(corrections = state.corrections + (lever to recent + now))
                fixes += lever
            }
        }
        return fixes
    }

    /** `previous.json` (SPEC 5.2): the string as found, before the engine ever wrote it. Needs the exact string. */
    private suspend fun capturePrevious() {
        if (previous != null || book.written[Lever.CONSTANTS] != null) return
        val constants = (book.seen[Lever.CONSTANTS] as? Setting.Constants)?.value ?: return
        val snapshot = PreviousSnapshot(
            capturedAt = clock.now(),
            constants = constants,
            writer = book.writer,
            effective = book.effective?.mapValues { it.value.raw }.orEmpty(),
            sensors = (book.seen[Lever.SENSORS] as? Setting.Sensors)?.value,
            forceIdle = (book.seen[Lever.FORCE_IDLE] as? Setting.ForceIdle)?.forced,
            allowlist = (book.seen[Lever.ALLOWLIST] as? Setting.Allowlist)?.packages,
        )
        previous = snapshot
        store.savePrevious(snapshot)
    }

    /** Before the first trim, the owner's user allowlist is kept so it can be put back (lever D). */
    private fun captureOwnerAllowlist(plan: List<Setting>) {
        if (plan.none { it is Setting.Allowlist } || book.persisted.ownerAllowlist != null) return
        val current = (book.seen[Lever.ALLOWLIST] as? Setting.Allowlist)?.packages ?: return
        book.persisted = book.persisted.copy(ownerAllowlist = current)
    }

    /** Once the owner's allowlist reads back as it was, the engine no longer holds it. */
    private fun releaseOwnerAllowlist() {
        val owner = book.persisted.ownerAllowlist ?: return
        val seen = book.seen[Lever.ALLOWLIST] as? Setting.Allowlist ?: return
        if (seen.packages == owner && book.written[Lever.ALLOWLIST] == seen) {
            book.forgetWritten(Lever.ALLOWLIST)
            book.persisted = book.persisted.copy(ownerAllowlist = null)
        }
    }

    private fun refuseCapture(): String? = when {
        !started -> NOT_STARTED
        !screenOn -> "the screen must be on"
        !writable.constants -> "WRITE_SECURE_SETTINGS is not held"
        else -> null
    }

    /** After the delete, waits until the string's keys stop showing its values, at most [DozeEngine.SETTLE_MILLIS]. */
    private suspend fun awaitReload(text: String) {
        val keys = DeviceIdleSettings.parse(text).values.size
        var waited = 0L
        while (waited < DozeEngine.SETTLE_MILLIS) {
            val left = if (observer.readDeviceIdle()) keys - observer.differences(ConstantsValue.Exact(text), null).orEmpty().size else 0
            if (left <= 0) return
            delay(DozeEngine.SETTLE_POLL_MILLIS)
            waited += DozeEngine.SETTLE_POLL_MILLIS
        }
    }

    private fun clearGuards() {
        book.persisted = book.persisted.copy(guarded = emptySet(), corrections = emptyMap())
    }

    private suspend fun changeMode(next: Mode) {
        if (next == mode) return
        book.persisted = book.persisted.copy(mode = next)
        journal.event(EngineEvent.ModeChanged(clock.now(), next))
    }

    private fun leverStatus(lever: Lever, targets: TargetSet): LeverStatus {
        val wanted = targets.wanted[lever]
        val seen = book.seen[lever]
        val base = LeverStatus(LeverState.UNUSED, wanted, book.written[lever], seen)
        val check = book.constantsCheck.takeIf { lever == Lever.CONSTANTS }
        return when {
            lever in book.persisted.guarded -> base.copy(state = LeverState.GUARDED)

            wanted == null -> targets.waiting[lever]?.let { base.copy(state = LeverState.WAITING, reason = it) }
                ?: base.copy(state = restingState(lever, targets).takeUnless { it == LeverState.APPLIED } ?: LeverState.UNUSED)

            !writable.can(lever) -> base.copy(state = LeverState.NOT_WRITABLE)

            seen != null && seen != wanted -> base.copy(state = LeverState.PARTLY, reason = "expected $wanted, reads $seen")

            !check.isNullOrEmpty() -> base.copy(state = LeverState.PARTLY, reason = check.joinToString("; "))

            seen == null && check == null -> base.copy(state = LeverState.UNKNOWN)

            else -> base.copy(state = restingState(lever, targets))
        }
    }

    /** The state of a lever that reads back as wanted: armed or held off when the profile uses it later. */
    private fun restingState(lever: Lever, targets: TargetSet): LeverState = when (lever) {
        in targets.armed -> LeverState.ARMED
        in targets.heldOff -> LeverState.HELD_OFF
        in targets.used -> LeverState.APPLIED
        else -> LeverState.UNUSED
    }

    private companion object {
        const val NOT_STARTED = "the engine has not started"
        const val UNSAVED_STRING =
            "the global string could not be read and is not in previous.json, so it was left as is: deleting it could not be undone"
        const val PUT_BACK_TRIES = 2
    }
}
