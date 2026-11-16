# Backlog

Optional work, left until the required work and the SPEC 15 checklist are done (ADR 0023). The
owner picks which items get built. One line each: what it is, and where the spec asks for it.

- **Forced-idle delay alarm**: lever B with a non-zero `delaySec`, one exact alarm per screen-off,
  off by default and labelled "wakes the phone once". Until it is built, the lever shows "waiting"
  with the reason. SPEC 1.3, 3.2, 5.3, 8.2.
- **Owner-phone check**: a 30-minute daytime check on the S25 Ultra (install, onboarding, quick
  proof, compressed walk, restore stock), plus at most one ordinary night. Only after asking the
  owner. SPEC 13.4, 14 (P7).
- **Tasker/Locale plugin for the automation API**: an edit activity that mints the client's token, so
  the host is named by `getCallingPackage()` and no token is copied by hand. ADR 0033 keeps the
  plain intent with a token per client; the plugin is the more accurate follow-up. SPEC 11.1.
- **Allowlist actions in the automation API**: `ALLOWLIST_ADD` and `ALLOWLIST_REMOVE`, porting
  upstream's broadcasts (parity row 8). Each client gets its own switch, off by default. The calls
  refuse EnforceDoze and the client itself, refuse system entries, answer "unavailable" while lever D
  holds the list, and report from the read-back. Dropped for v1 in ADR 0036; Q13 lets the owner ask
  for it. SPEC 2.3, 11.1.
