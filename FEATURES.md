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

## Campus (home tab)

Built for a college student: every screen below comes from one weekly timetable.

| Feature | Status |
|---|---|
| Weekly timetable with lectures, labs and tutorials; paste a whole week as text ("Mon 9-10 DSA L 203") | 🆕 |
| Semester dates; holidays (single days or ranges), cancelled, rescheduled and extra classes | 🆕 |
| Attendance per subject: %, classes you can safely skip, classes you must attend to recover, projection to semester end, what skipping the next class does | 🆕 |
| Per-subject requirement (e.g. 75%), count labs by hours, carry over counts from before tracking | 🆕 |
| "Did you attend?" notification after every class with Present / Absent / No class buttons; unmarked-class queue | 🆕 |
| Leave-now and class reminders with room numbers | 🆕 |
| Automatic wake-up alarm before each day's first class (or a free-day time), recomputed for holidays and changes | 🆕 |
| Wake-up challenges (walk, squats, math, typing…), backup alarm, "still awake?" check, sleep reminder, on-time history | 🆕 |
| Alarm reliability: alarm-clock scheduling, readiness check (exact alarms, battery optimisation, full-screen, volume, battery), night battery warnings, fallback alarm before first unlock after a reboot, missed-alarm recovery when the phone turns back on | 🆕 |
| Library: opening hours, free slots between classes, one-tap study-block planning into the timeline, check-in/out with app blocking, hours per day/week | 🆕 |
| Study sheets: ready-made 196-problem DSA sheet (LeetCode links, by topic and difficulty), CS core (OS/DBMS/CN/OOP/system design), AI/ML interview, aptitude/HR, resume/projects; custom syllabus sheets by paste | 🆕 |
| Sheet tracking: status, difficulty, notes, time taken, stars, per-section progress, daily targets, streaks, activity heatmap, exam-date pacing | 🆕 |
| Spaced revision of solved items (3 → 7 → 15 → 30 → 60 days) with a review queue | 🆕 |
| Assignments, quizzes, mid-sem and end-sem with countdowns and reminders 1 day and 3 hours before | 🆕 |
| CGPA: semester SGPA and CGPA on the 10-point AA–FF scale, target planner ("SGPA needed in remaining credits") | 🆕 |
| Placement tracker: companies by stage, next rounds with reminders, notes | 🆕 |
| Daily score (0–100): wake-up, classes, deep study, problems solved, screen time, discipline | 🆕 |
| Discipline: private streak tracker behind the phone lock, urge SOS (breathing, reasons, actions), trigger and hour patterns, daily check-in, one-tap protection (web filter lock, risk-hour app blocking, strict mode); excluded from backups | 🆕 |

### Timetable editing

| Feature | Status |
|---|---|
| Week grid of any week (classes placed by time, holidays shaded, extra/moved classes outlined, absences faded), page through weeks | 🆕 |
| Subject week editor: any number of classes per day, time pickers, duration presets, type and room per class, copy a day to other days, clash warnings | 🆕 |
| Weekly schedule repeats until the semester ends; changes can apply from today so past attendance is kept | 🆕 |
| Day editor: holiday toggle, cancel / re-time / move / undo any class for that date only, extra classes, attendance marking | 🆕 |
| Library time = big free gaps only (90 min+ by default, editable), planned as one session per gap (optional split into focus blocks) | 🆕 |

### More for college

| Feature | Status |
|---|---|
| Full backup & restore of everything in one file (private data optional) — Campus → Settings | 🆕 |
| Classes shown on the main Today timeline with Present/Absent marking | 🆕 |
| "Chronora Campus" home-screen widget: next class and room, attendance, problems solved, next deadline | 🆕 |
| Study timer per subject/topic with today and this-week totals (counts toward the daily score) | 🆕 |
| Daily score history and a weekly review: score trend, study hours, problems, on-time wake-ups, sleep | 🆕 |
| Internal marks per subject (minors, mid-sems, quizzes…) with weights → predicted grade and marks needed for the next grade | 🆕 |
| Exam revision plan: a sheet's remaining topics spread day by day before the exam, shown on Today | 🆕 |
| Search covers sheet questions, deadlines, subjects and companies | 🆕 |
| Quick-add deadlines in plain words ("ML assignment due fri 11pm") | 🆕 |
| "Cancelled" button on class reminders | 🆕 |
| Sleep estimated from screen-off and first unlock, with a bedtime target for tomorrow's alarm | 🆕 |
| Attendance export/share as CSV | 🆕 |
| Rename sheets | 🆕 |

### Everything is editable

Campus → Settings holds every number and list the Campus features use: revision gaps, study-block and break lengths, walking buffer, minimum free slot, library reminders, deadline and interview reminder times, deadline/exam types, placement stages, daily-score weights and grade letters, on-time tolerance, attendance warning margin, afternoon cut-off for pasted timetables and lock-in durations. Also editable: the CGPA grade scale, wake-up snoozes, hold-to-dismiss time, battery-check interval and missed-alarm window (Wake-up tab), the Discipline milestones, triggers, actions, check-in time, urge timer, lock length and guarded apps, every web-filter category list, the adult keywords, the anti-bypass list and download sources, digest and report times, Shorts/Reels screen ids, and Focus Guard pause, unlock and session lengths. Settings saved by older versions are upgraded with defaults.

## Digital wellbeing (Free Pro Suite → Wellbeing)

Covers what Google Digital Wellbeing, StayFree, ActionDash, ScreenZen, Opal, one sec, AppBlock, Lock Me Out and YourHour offer, including their paid tiers.

| Feature | Status |
|---|---|
| Dashboard: screen time, pickups (unlocks), app opens, notifications, first pickup, longest session | 🆕 |
| Hourly chart, 7-day chart with goal line, daily average, change vs previous week, any of the last 7 days | 🆕 |
| Per-app detail: 7-day usage and opens, plus every limit for that app in one place | 🆕 |
| App timers with separate weekday and weekend limits, enforced even while the app stays open | 🆕 |
| Warning notification 1–15 minutes before a limit | 🆕 |
| Group limits (Social, Video, Games, News from Android categories, or custom groups) | 🆕 |
| Total daily screen-time limit | 🆕 |
| Open-count limits (max launches per day) | 🆕 |
| Session limits with enforced cooldown breaks | 🆕 |
| Mindful pause that grows with every open (ScreenZen style) | 🆕 |
| Block YouTube Shorts, Instagram Reels, Facebook Reels, Snapchat Spotlight and the TikTok feed while the rest of the app works (best-effort) | 🆕 |
| Bedtime mode: schedule, allowed apps, Do Not Disturb, grayscale (grayscale needs a one-time ADB grant) | 🆕 |
| Do Not Disturb during focus sessions | 🆕 |
| Notification counts per app; quiet apps held back and delivered as a digest 1–4×/day | 🆕 |
| Strict mode: during blocks, Chronora's settings, uninstall and accessibility pages are covered | 🆕 |
| Screen-time and pickup goals, under-goal streak, daily report notification, weekly comparison on Sundays | 🆕 |

## Web filter & firewall (Free Pro Suite → Web filter)

| Feature | Status |
|---|---|
| System-wide DNS filtering through a local DNS-only VPN (all apps and browsers, no Chronora server) | 🆕 |
| Category blocklists bundled offline: adult, gambling, dating, social media, video/streaming, ads & trackers | 🆕 |
| Adult keyword detection for unlisted sites, with false-positive exceptions (Essex, JavaScript…) | 🆕 |
| Forced SafeSearch (Google, Bing, DuckDuckGo, Yandex) and YouTube Restricted Mode | 🆕 |
| Blocks DNS-over-HTTPS resolvers, web proxies and VPN sites that would bypass the filter | 🆕 |
| Family upstream DNS as a second layer: Cloudflare for Families, CleanBrowsing Family, AdGuard Family; also Quad9 and plain resolvers | 🆕 |
| Custom block and allow lists (subdomains included) | 🆕 |
| Import hosts, plain or AdBlock lists from a file, or download StevenBlack porn / gambling / social / unified lists once, then filter offline | 🆕 |
| Per-app firewall: no internet, Wi-Fi only or mobile data only (Android 10+) | 🆕 |
| Commitment lock: loosening protection needs a 5 min to 24 h wait | 🆕 |
| Always-on VPN support, start on boot, blocked-today counter, recent block log, website tester | 🆕 |

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
| Usage access | Screen time, pickups, app timers and limits | For wellbeing stats and limits |
| Notification access | Notification counts and the digest | Only for those features |
| Do Not Disturb access | Bedtime and focus DND | Only for DND |
| WRITE_SECURE_SETTINGS (granted once via ADB) | Grayscale at bedtime | Optional |
| Fine and background location | Arrive/leave reminders while the app is closed | Only for Places |
| Foreground service (media playback) | Keeps the timer and focus sounds alive with the screen off | Automatic |
| VPN (local, DNS only) | Web filter and app firewall; DNS goes to the chosen resolver, other traffic is untouched | Only for the web filter |
