# Chronora feature coverage

Chronora aims to replace the paid tiers of Todoist, TickTick, Forest, Freedom, one sec, Opal,
Alarmy, Habitify, Structured, Sunsama and similar apps with one **free, offline-first** Android app.
Everything below runs on the phone with no account, server or subscription.

Legend: ✅ available · 🆕 added in this change · ⛔ needs internet/others (out of scope for offline core)

## Planning

| Feature | Status | Where |
|---|---|---|
| Tasks: title, notes, date, start/end, duration, priority, tags, colour, checklist, complete, duplicate, delete | ✅ | Today tab, task editor |
| Recurring tasks (daily, weekdays, weekends, weekly, custom days) | ✅ | `RecurrenceEngine` |
| Natural-language quick add: dates, weekdays, "in 3 days", ISO dates, ranges, "at 7pm", "for 90m", recurrence, p1–p4, #tags, "remind 10m before" | 🆕 | `QuickAddParser`, Free Pro Suite → Quick add |
| Voice-to-task (uses the device's offline speech recogniser when installed) | 🆕 | Quick add → mic |
| Live parse preview before adding | 🆕 | Quick add |
| Day timeline, calendar month view, conflict detection, drag and resize | ✅ | Today, Calendar, `ConflictDetector` |
| Automatic scheduling into free time, workload warnings | ✅ | Smart plan, `SmartPlanningEngine`, `WorkloadEngine` |
| Task dependencies (blocked tasks, cycle detection) | ✅ | Power Center |
| Templates (deep work, exam revision, morning reset, shutdown) | ✅ | Power Center |
| Projects, goals with milestones | ✅ | Productivity tab |
| ICS import/export, CSV export, JSON backup/restore | ✅ | Power Center, Power tools |

## Execution

| Feature | Status | Where |
|---|---|---|
| Pomodoro: custom focus/break lengths, cycles, long break, task-linked | ✅ | Focus tab |
| Pomodoro skip phase and +5 min | 🆕 | Focus tab, notification |
| Timer keeps running with the screen off; lock-screen countdown with Pause / Skip / +5 min / Stop | 🆕 | `FocusSessionService` |
| Phase-change alerts ("Focus complete, take a break") | 🆕 | `FocusSessionService` |
| Ambient focus sounds generated on-device: white, pink and brown noise, rain, ocean, fan, 40 Hz tone | 🆕 | Free Pro Suite → Sounds |
| App blocking during focus sessions (25/50/90/180 min) | 🆕 | Free Pro Suite → Focus Guard |
| Block apps automatically while a Pomodoro focus phase runs | 🆕 | Sounds → toggle |
| Recurring block schedules, including overnight windows | 🆕 | Focus Guard |
| Allowlist mode (block everything except chosen apps) | 🆕 | Focus Guard |
| Multiple saved block lists | 🆕 | Focus Guard |
| Daily app time limits | 🆕 | Focus Guard (needs usage access) |
| Mindful pause (one sec style): breathing screen before an app opens, then a time-boxed grant | 🆕 | Focus Guard |
| Locked mode, rationed emergency unlocks with a wait delay | 🆕 | Focus Guard |
| Screen-time summary and blocked-attempt counts | 🆕 | Focus Guard |
| Alarms: backup alarms, wake-check, snooze policy, bedtime, naps, odd/even weeks | ✅ | Alarms |
| Alarm missions: math, typing, memory, shake, squats, walking/steps, photo, barcode/QR | ✅ | Alarms |
| Task reminders with Complete / Snooze actions; boot and time-zone recovery | ✅ | `ReminderReceiver`, `BootReceiver` |
| Location reminders: arrive / leave / both, radius, once or every visit; GPS only, no Play Services | 🆕 | Free Pro Suite → Places |
| Habits: weekly targets, active days, streaks, reminders | ✅ | Productivity tab |
| Routines with steps, durations, per-step completion, history | ✅ | Productivity tab |
| App lock (device credential or biometric) | ✅ | Settings |

## Memory and review

| Feature | Status | Where |
|---|---|---|
| Notes: folders, tags, pinning, wiki backlinks | ✅ | Notes |
| Journal: mood, energy, wins, blockers, gratitude, history | ✅ | Reflection |
| Daily and weekly reviews | ✅ | Power Center |
| Study: flashcards with spaced repetition, exam countdown plans | ✅ | Power Center → Study |
| Analytics: completion, planned vs done, focus logs, activity heatmap, time entries | ✅ | Analytics |
| Global search across tasks | ✅ | Search |
| Home-screen timeline widget | ✅ | `DayTimelineWidget` |

## Also included

| Feature | Status | Where |
|---|---|---|
| Unified search across tasks, notes, journal, habits, goals and routines, with filters like "unfinished dsa tasks", "notes #exam", "everything yesterday", "last week" | 🆕 | Free Pro Suite → Search |
| Week view with daily load and overload highlight, plus a 3-week agenda | 🆕 | Free Pro Suite → Week |
| Focus Garden: XP, levels, a plant per completed Pomodoro (bigger for longer sessions), withered plants for abandoned sessions, focus-day streaks, badges | 🆕 | Free Pro Suite → Garden |
| Energy-aware planning: #deep/#hard work in peak hours, #easy/#admin outside, priority order, buffers, #fixed pins a task | 🆕 | Free Pro Suite → Energy plan |
| Private journal encrypted with AES-256-GCM and a hardware-backed Keystore key | 🆕 | Free Pro Suite → Private journal |
| Website blocking in Chrome, Firefox, Samsung Internet, Edge, Brave, Opera, DuckDuckGo, Vivaldi and Kiwi (domains or path prefixes; during focus or all day) | 🆕 | Focus Guard → Blocked websites |
| "Chronora Focus" home-screen widget: live Pomodoro countdown, Start/Pause, Quick add, garden level, habits done today | 🆕 | `FocusWidget` |

## Requires internet or third parties (not part of the offline core)

| Feature | Status |
|---|---|
| Email-to-task, Gmail/Outlook/Slack integrations | ⛔ |
| Cloud sync and collaboration (shared projects, comments) | ⛔ (JSON and ICS files can be moved between devices manually) |
| Cloud AI assistant (the offline rule-based planner covers scheduling) | ⛔ |

## Permissions used by the new features

| Permission | Why | Required? |
|---|---|---|
| Accessibility service "Chronora Focus Guard" | Detects which app opens so the block screen can cover it; in supported browsers it reads only the address bar. | Only for app blocking |
| Usage access | Daily limits and screen-time stats | Only for limits |
| Fine and background location | Arrive/leave reminders while the app is closed | Only for Places |
| Foreground service (media playback) | Keeps the timer and focus sounds alive with the screen off | Automatic |
