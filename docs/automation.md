# Automation API

Tasker, MacroDroid, Automate and any other app that can send an intent can drive EnforceDoze
(SPEC 11.1). Each call carries the client's own token instead of a permission; ADR 0033 explains
why.

## Setting up

1. Settings › Automation › turn on **Automation API**. While it is off, the receiver is disabled
   and nothing reaches the app.
2. Make a token. Tasker is offered with one tap when it is installed. Any other app goes through
   **Another app**.
3. The token is shown once. Copy it into the client: in Tasker, the global variable `%ED_TOKEN`.
   EnforceDoze keeps only a fingerprint (SHA-256) of it.
4. **New token** or **Remove** revokes the old token at once.

## Sending a call

Every call is a broadcast **with the package set**. Android delivers no implicit broadcasts to
manifest receivers, so a call without the package never arrives.

| Field | Value |
| --- | --- |
| Action | `io.github.akane599.enforcedoze.action.<NAME>` |
| Package | `io.github.akane599.enforcedoze` |
| Target | Broadcast receiver |
| Extra `token` | the client's token (`token:%ED_TOKEN` in Tasker) |

| Name | Extras | Does |
| --- | --- | --- |
| `APPLY_PROFILE` | `id`: a profile id (`everyday`, `pocket`, `night`, `classic`, `strict`, or a custom profile's id) | Applies that profile, as the Profiles screen does. |
| `APPLY_PLAN` | none | Follows the saved plan again. |
| `PAUSE` | none | Back to stock settings until `RESUME`. |
| `RESUME` | none | Back to the profile or plan in use. |
| `RESTORE_STOCK` | none | Restores the stock settings, as Settings › Restore stock does. |
| `SENSORS_OFF` | `on`: `true` turns Sensors Off on, `false` turns it off; required | The quick settings tile's switch. Sensors Off also stops the camera and microphone, so there is no default. |
| `GET_STATUS` | none | Replies with a status broadcast (below). |

Every call runs through the same commands and guardrails as a tap in the app. Calls, alarms and
emergency alerts keep working whatever a client sends.

## Limits and the log

- Calls that change something (all but `GET_STATUS`) share one limit: at most one every 30 s,
  across all clients. A call inside the 30 s is logged and dropped. The limit runs on the phone's
  uptime clock, so setting the time back does not hold it shut.
- A call that cannot run as sent (no `id`, an unknown profile, `on` missing or not `true`/`false`)
  is rejected before the limit, so it does not block the next good call.
- A call with an unknown token gets no reply and changes nothing. Its extras are not even read.
- Dropped and rejected calls are logged at most once a minute for each client and reason, so a loop
  in a task cannot fill the log.
- A sender that shares its identity (`BroadcastOptions.setShareIdentityEnabled`, API 34+) must be
  the app the token was made for. Otherwise the call is rejected.
- Every call becomes a journal event of kind `AUTOMATION`: the client's package, the action and
  the result (`done`, `unavailable: …`, `failed: …`, `dropped: …`, `rejected: …`). The evidence
  export includes them.

## The status reply

`GET_STATUS` sends `io.github.akane599.enforcedoze.action.STATUS` to the calling client's package
only. Its extras:

| Extra | Value |
| --- | --- |
| `plan` | the saved plan as one line, such as `everyday \| night 23:00-07:00`, or absent |
| `profile` | the profile in use |
| `mode` | `MANAGED`, `PAUSED` or `HANDS_OFF` (after "Return to previous setup") |
| `tier` | the privilege tier (`T0` … `T3`) |
| `verdict` | the last session's verdict |
| `deep_share` | the last session's share of time in deep idle, 0–1 |
| `drain` | the last session's drain in %/h |
| `warning` | the first open warning's code, or absent |

In Tasker, a profile on the event **Intent Received** with that action gets the extras as local
variables: `%plan`, `%profile`, `%mode`, `%tier`, `%verdict`, `%deep_share`, `%drain`, `%warning`.

## Tasker examples

These tasks hold no token: they read `%ED_TOKEN`. They follow Tasker's task export format (action
877, Send Intent). Importing them on a phone has not been checked yet (`docs/questions.md` Q12); if
Tasker refuses one, build the same Send Intent action by hand from the table above.

Apply a profile:

```xml
<TaskerData sr="" dvi="1" tv="6.3.13">
  <Task sr="task1">
    <id>1</id>
    <nme>EnforceDoze apply profile</nme>
    <Action sr="act0" ve="7">
      <code>877</code>
      <Str sr="arg0" ve="3">io.github.akane599.enforcedoze.action.APPLY_PROFILE</Str>
      <Int sr="arg1" val="0"/>
      <Str sr="arg2" ve="3"/>
      <Str sr="arg3" ve="3"/>
      <Str sr="arg4" ve="3">token:%ED_TOKEN</Str>
      <Str sr="arg5" ve="3">id:everyday</Str>
      <Str sr="arg6" ve="3"/>
      <Str sr="arg7" ve="3">io.github.akane599.enforcedoze</Str>
      <Str sr="arg8" ve="3"/>
      <Int sr="arg9" val="0"/>
    </Action>
  </Task>
</TaskerData>
```

Pause and resume use the same action with `PAUSE` or `RESUME` in `arg0` and no `arg5`. Ask for the
status with `GET_STATUS`, then read the reply in a profile on **Intent Received** with the action
`io.github.akane599.enforcedoze.action.STATUS`.
