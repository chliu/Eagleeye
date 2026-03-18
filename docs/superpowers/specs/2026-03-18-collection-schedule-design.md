# Collection Schedule Change: 17:10 → 15:30 Taiwan Time

**Date:** 2026-03-18

## Background

The daily collector runs Mon–Fri via macOS launchd. The previous schedule (17:10 Taiwan time / 09:10 UTC) had a 3.5-hour buffer after TAIFEX close (13:45). The new schedule moves collection earlier to 15:30 Taiwan time (07:30 UTC), giving a 1.75-hour buffer — sufficient for normal publication windows while collecting data sooner.

## Change

**File:** `deploy/com.eagleeye.collector.plist`

Update `StartCalendarInterval` for all 5 weekday entries:

| Field   | Before | After |
|---------|--------|-------|
| `Hour`  | `9`    | `7`   |
| `Minute`| `10`   | `30`  |

Update comment: `15:30 Taiwan time (UTC+8 = 07:30 UTC)`

## Market Close Reference

| Market | Close time (Taiwan) | Buffer at 15:30 |
|--------|---------------------|-----------------|
| TWSE   | 13:30               | 2h 00m          |
| TAIFEX | 13:45               | 1h 45m          |

## Deployment

After updating and installing the plist:

```bash
launchctl unload ~/Library/LaunchAgents/com.eagleeye.collector.plist
launchctl load   ~/Library/LaunchAgents/com.eagleeye.collector.plist
```

Verify with:

```bash
launchctl print gui/$(id -u)/com.eagleeye.collector
```

## Scope

- One file changed: `deploy/com.eagleeye.collector.plist`
- No application code changes
- No changes to backfill script or shell commands
