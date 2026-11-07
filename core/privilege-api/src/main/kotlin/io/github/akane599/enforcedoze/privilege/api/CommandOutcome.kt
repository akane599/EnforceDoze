// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.api

import android.os.Parcel
import android.os.Parcelable

/**
 * Result of one worker command. [argv] is what ran (for the action log); [error] is set when the
 * worker refused or could not start the command, in which case nothing changed on the device.
 * Success here means only that the command exited 0: the engine still reads the state back
 * (SPEC 5.6).
 */
public data class CommandOutcome(
    val argv: List<String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val durationMillis: Long,
    val error: String?,
) : Parcelable {
    val isSuccess: Boolean get() = error == null && !timedOut && exitCode == 0

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeStringList(argv)
        dest.writeInt(exitCode)
        dest.writeString(stdout)
        dest.writeString(stderr)
        dest.writeInt(if (timedOut) 1 else 0)
        dest.writeLong(durationMillis)
        dest.writeString(error)
    }

    public companion object {
        /** Exit code used when the command never ran. */
        public const val NOT_RUN: Int = -1

        public fun rejected(argv: List<String>, reason: String): CommandOutcome =
            CommandOutcome(argv, NOT_RUN, "", "", timedOut = false, durationMillis = 0, error = reason)

        @JvmField
        public val CREATOR: Parcelable.Creator<CommandOutcome> = object : Parcelable.Creator<CommandOutcome> {
            override fun createFromParcel(source: Parcel): CommandOutcome = CommandOutcome(
                argv = source.createStringArrayList().orEmpty(),
                exitCode = source.readInt(),
                stdout = source.readString().orEmpty(),
                stderr = source.readString().orEmpty(),
                timedOut = source.readInt() != 0,
                durationMillis = source.readLong(),
                error = source.readString(),
            )

            override fun newArray(size: Int): Array<CommandOutcome?> = arrayOfNulls(size)
        }
    }
}
