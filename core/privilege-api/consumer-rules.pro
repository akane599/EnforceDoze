# SPDX-License-Identifier: GPL-3.0-or-later
# The worker runs from the same APK in another process; both sides must agree on the parcelable.
-keep class io.github.akane599.enforcedoze.privilege.api.CommandOutcome { *; }
