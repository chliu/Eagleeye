# Collection Schedule Change Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the daily collector from 17:10 Taiwan time (09:10 UTC) to 15:30 Taiwan time (07:30 UTC).

**Architecture:** Two file edits — update `Hour` and `Minute` in all 5 `StartCalendarInterval` weekday entries in `deploy/com.eagleeye.collector.plist`, update the comment, and fix the stale time string in `deploy/install.sh`. Then run `install.sh` to deploy (it handles the launchd reload via `bootout`/`bootstrap` internally).

**Tech Stack:** macOS launchd plist (XML), bash

---

## Chunk 1: Update plist and install.sh, then deploy

**Files:**
- Modify: `deploy/com.eagleeye.collector.plist`
- Modify: `deploy/install.sh`

- [ ] **Step 1: Edit the plist comment**

In `deploy/com.eagleeye.collector.plist`, update the comment from:
```xml
<!-- Run Mon–Fri at 17:10 Taiwan time (UTC+8 = 09:10 UTC) -->
```
to:
```xml
<!-- Run Mon–Fri at 15:30 Taiwan time (UTC+8 = 07:30 UTC) -->
```

- [ ] **Step 2: Edit the 5 weekday schedule entries in the plist**

Change all 5 `<dict>` entries in `StartCalendarInterval` — `Hour` from `9` to `7`, `Minute` from `10` to `30`:
```xml
<dict><key>Weekday</key><integer>1</integer><key>Hour</key><integer>7</integer><key>Minute</key><integer>30</integer></dict>
<dict><key>Weekday</key><integer>2</integer><key>Hour</key><integer>7</integer><key>Minute</key><integer>30</integer></dict>
<dict><key>Weekday</key><integer>3</integer><key>Hour</key><integer>7</integer><key>Minute</key><integer>30</integer></dict>
<dict><key>Weekday</key><integer>4</integer><key>Hour</key><integer>7</integer><key>Minute</key><integer>30</integer></dict>
<dict><key>Weekday</key><integer>5</integer><key>Hour</key><integer>7</integer><key>Minute</key><integer>30</integer></dict>
```

- [ ] **Step 3: Update the echo message in install.sh**

In `deploy/install.sh` line 66, change:
```bash
echo "  Collector runs automatically Mon–Fri at 17:10 Taiwan time."
```
to:
```bash
echo "  Collector runs automatically Mon–Fri at 15:30 Taiwan time."
```

- [ ] **Step 4: Verify both files**

```bash
grep "Taiwan" deploy/com.eagleeye.collector.plist
grep "Hour\|Minute" deploy/com.eagleeye.collector.plist
grep "Taiwan" deploy/install.sh
```

Expected:
- plist comment contains `15:30 Taiwan time (UTC+8 = 07:30 UTC)`
- all 5 entries show `<integer>7</integer>` for Hour and `<integer>30</integer>` for Minute
- install.sh line contains `15:30 Taiwan time`

- [ ] **Step 5: Commit**

```bash
git add deploy/com.eagleeye.collector.plist deploy/install.sh
git commit -m "chore(deploy): reschedule collector to 15:30 Taiwan time (07:30 UTC)"
```

- [ ] **Step 6: Deploy (run in a terminal — requires sudo for some steps)**

```bash
bash deploy/install.sh
```

`install.sh` builds the JARs, installs them, copies the updated plist to `~/Library/LaunchAgents/`, and reloads the launchd agent via `bootout`/`bootstrap`.

- [ ] **Step 7: Verify the new schedule is active**

```bash
launchctl print gui/$(id -u)/com.eagleeye.collector
```

Expected: the job is listed, `last exit code = 0` (or blank if it hasn't run yet).
