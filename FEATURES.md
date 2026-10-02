# Chronora feature coverage

Chronora aims to replace the paid tiers of Todoist, TickTick, Forest, Freedom, one sec, Opal,
Alarmy, Habitify, Structured, Sunsama and similar apps with one **free, offline-first** Android app.
Everything below runs on the phone with no account, server or subscription.

Legend: ✅ available · 🆕 added in this change · ⛔ needs internet/others (out of scope for offline core)

## App layout and design

- **Five tabs, each with its own colour:** Today (indigo), Plan (violet), Focus (coral), Campus (teal), You (amber). The top bar holds only Search and Alarms; settings sit behind the gear on You.
- **Every feature has one home:**
  - the calendar is in Plan;
  - the timer, Focus mode, sounds, stats and garden are in Focus;
  - habits are ticked straight from Today;
  - wellbeing, habits & goals, writing, tracking and smart tools are grouped as tiles on You.
- **Today:** a greeting, the week, a Now / Up next card, a Focus mode pill (only while it's on), habit bubbles to tick, and the animated day timeline.
- **No ± buttons anywhere:** every number opens a picker with a big readout and a ruler you drag, with a haptic tick per step.
- **Feedback:** actions confirm with a snackbar, many with Undo — habits, attendance, distractions, extra time, naps, Focus mode.
- **Locked states:**
  - A running focus session shows only the timer and its controls. Giving up asks first.
  - Focus mode, while on, shows its countdown and the paused apps but can't be edited. Strict timers and schedules show a lock.
  - Attendance needs one tap on ✓ or ✗, then locks into a coloured status. Changing it needs a confirmation.
  - A running nap is a countdown card with only Cancel.

## Trackers (You → Trackers, rings on Today)

- **Track anything, each in its own way:**
  - ticks;
  - counts (fruit servings);
  - amounts with units (water in ml);
  - minutes (walk);
  - 1–5 ratings (mood);
  - options with "good" answers (a meal can be Healthy, Okay, Junk or Skipped).
- **Every tracker has its own target and rules:**
  - "at least", "at most" or "just log";
  - per day, week or month;
  - which days it's due;
  - a time window, e.g. breakfast 08:00–09:00, with entries outside it marked;
  - reminders, colour, emoji and group.
- **Ready-made packs:**
  - Food: breakfast, lunch and dinner timed to your mess hours, plus water and fruits.
  - Morning walk, sleep, workout (3× a week), mood and reading.
- **Automatic trackers:** screen time, chosen apps' usage (e.g. short videos ≤ 30 min) and focus minutes, read from the phone.
- **Logging:**
  - one tap on Today or on a tracker card, with Undo;
  - from the reminder notification ("+250 ml", "Healthy", "Done");
  - past days are editable.
- **History:** each tracker has a progress ring, a streak (days off skipped), a 30-day chart and its entries.

## Alarm loudness

- **Loud by default:** alarms ring at 100% on the alarm stream.
- **Loudness boost:** Off, Loud, Louder or Max. The sound plays through a media player with Android's loudness enhancer, which pushes it past normal maximum volume.
- **Volume lock (on by default):** volume keys do nothing while it rings, and anything that lowers the alarm volume is reset within a third of a second.

## Focus mode (Digital Wellbeing-style)

- **One tap:** pauses the distracting apps you picked. Turn it on from the Home card, the Focus tab, More → Focus mode or the "Focus mode" Quick Settings tile.
- **How long:** until you turn it off, or for a timer. Timer lengths are editable (default 30 min, 1 h, 2 h).
- **Breaks:** take a break (default 5, 15 or 30 min); the apps pause again on their own when it ends.
- **Schedules:** e.g. Study, Mon–Fri 09:00–13:00. Overnight windows work.
- **Strict option:** no breaks, no emergency unlock, and no turning off before the timer ends. Strict never applies to "until off", so you can't lock yourself in by mistake.
- **App picker:** lists your most-used apps first, with today's time, plus search.
- **Notification:** an ongoing one shows the status, with Break and Turn off buttons.
- **Separate from the App blocker:** it has its own app list. Websites and Do Not Disturb follow it too.

## Commitment lock (private section)

- **Can't be undone early:** you choose 3–90 days and confirm by typing "I commit". Until the end date nothing can be loosened or shortened; you can only add time or turn more on.
- **Web filter stays on:** it is locked at its strictest. Unlock requests are refused, and it restarts by itself if it stops.
- **Shields:**
  - on-screen word detection in any app (two different listed words, so ordinary text isn't caught);
  - private and incognito tabs are closed;
  - only one chosen browser may open;
  - every short-video feed is blocked;
  - Night shield: only the phone, clock and chosen apps work, ending at your next alarm and starting your sleep goal earlier.
- **While locked:** strict mode is forced on, so settings, uninstall and the accessibility page are covered.
- **Make it airtight:** a checklist opens each relevant setting — blocking service, uninstall protection, always-on VPN, Private DNS off.
- **Block screen:** shows your own line to your future self. Blocks are counted per day.
- **Distracting apps blocked all day:** social, video, games and news apps are picked automatically and can be adjusted before locking. The list can only grow while locked.
- **The way back disappears:**
  - the Blocker, Limits and Web filter tabs and their tiles are hidden;
  - the private section's protection and settings cards are hidden;
  - per-app limit editing and backup Restore are gone;
  - a restore can't overwrite the lock or blocking settings.
- **System settings that could undo it close instantly:** Private DNS, date & time, developer options, reset.
- **Moving the phone's date forward pushes the end date by the same amount.**

## Screen time (YourHour-style)

- **Overview:**
  - A donut split by your top apps, with total time, your goal and the change from yesterday.
  - An addiction level (Champion → Achiever → Fit → Habitual → Dependent → Addicted), measured against your editable daily goal.
  - Unlocks, opens, notifications, first unlock, longest session and minutes per unlock.
  - An hourly chart, and a week chart with the goal line and the change from last week.
  - Every app with its icon, an animated bar, opens and its limit. Tap an app for timers, open limits, session limits, a mindful pause and blocking.
- **Timeline:** every app session of the day in order: app icon, start–end and length.
- **Usage bubble:** an optional floating pill over the app you're using, e.g. "Instagram · 42m", drawn by the accessibility service, so it needs no extra permission.
- **App blocker:** shows today's screen time at the top.
- **Limits and Web filter:** in the same page, as tabs.

## Planning

| Feature | Status | Where |
|---|---|---|
| Tasks: title, notes, date, start/end, duration, priority, tags, colour, checklist, complete, duplicate, delete | ✅ | Today tab, task editor |
| Recurring tasks (daily, weekdays, weekends, weekly, custom days) | ✅ | `RecurrenceEngine` |
| Natural-language quick add: dates, weekdays, "in 3 days", ISO dates, ranges, "at 7pm", "for 90m", recurrence, p1–p4, #tags, "remind 10m before" | 🆕 | `QuickAddParser`, More → Quick add |
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
| Ambient focus sounds generated on-device: white, pink and brown noise, rain, ocean, fan, 40 Hz tone | 🆕 | More → Sounds |
| App blocking during focus sessions (25/50/90/180 min) | 🆕 | More → App blocker |
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
| Location reminders: arrive / leave / both, radius, once or every visit; GPS only, no Play Services | 🆕 | More → Places |
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
| Unified search across tasks, notes, journal, habits, goals and routines, with filters like "unfinished dsa tasks", "notes #exam", "everything yesterday", "last week" | 🆕 | More → Search |
| Week view with daily load and overload highlight, plus a 3-week agenda | 🆕 | More → Week review |
| Focus Garden: XP, levels, a plant per completed Pomodoro (bigger for longer sessions), withered plants for abandoned sessions, focus-day streaks, badges | 🆕 | More → Garden |
| Energy-aware planning: #deep/#hard work in peak hours, #easy/#admin outside, priority order, buffers, #fixed pins a task | 🆕 | More → Energy plan |
| Private journal encrypted with AES-256-GCM and a hardware-backed Keystore key | 🆕 | More → Private journal |
| Website blocking in Chrome, Firefox, Samsung Internet, Edge, Brave, Opera, DuckDuckGo, Vivaldi and Kiwi (domains or path prefixes; during focus or all day) | 🆕 | Focus Guard → Blocked websites |
| "Chronora Focus" home-screen widget: live Pomodoro countdown, Start/Pause, Quick add, garden level, habits done today | 🆕 | `FocusWidget` |

## Campus (Campus tab)

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

## Digital wellbeing (More → Screen time)

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

## Web filter & firewall (More → Web filter)

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

## Best-in-class upgrades (compared against the top Play Store apps)

- **Habits (like Loop, Habitify, HabitNow):**
  - Habit strength score that recovers gradually after a miss.
  - Three frequencies: specific days, X times per week, every N days.
  - Skip days (long-press) that never break a streak.
  - Current and best streak, 30-day completion rate and total count.
  - 7-day tap strip and a 16-week heatmap.
  - Colours, a "why it matters" note, archive, and grouping into Morning, Afternoon, Evening and Anytime.
- **Tasks (like TickTick):**
  - Eisenhower priority matrix on the Today tab.
  - Editable rules: what counts as urgent, what counts as important, and how far ahead to look.
  - One-tap change of a task's importance.
- **Focus (like Forest, Focus To-Do):**
  - Progress ring with a plant that grows as you focus, and dots showing progress through the round.
  - Presets, plus editable focus, break and long-break lengths and round size.
  - Link a session to a task.
  - Today, streak and level stats, and a weekly focus chart.
- **Attendance (like BunkMate, BunkWise):**
  - "Only mark bunks" mode, where unmarked classes count as attended.
  - A "What if…" calculator: attend X, skip Y, see the resulting % and how many classes it takes to recover.
- **Wake-up (like Alarmy):**
  - The alarm screen shows a daily quote from an editable list.
  - It also shows a morning briefing: first class, room, and deadlines due today.


### Round 2 — every feature compared with its best Play Store app
- **Tasks and calendar (TickTick, Todoist)**
  - Quick-add bar with a live preview, plus a pinned quick-add notification with a text box.
  - Upcoming view, overdue rescue, and a priority matrix.
  - Repeating tasks are ticked per day.
  - Full editor with date and time pickers, colours and weekday repeats.
  - Month calendar with task, class and deadline markers.
- **Goals (Strides)**
  - Number or milestone goals.
  - Pace line, amount needed per day, projected finish date and history chart.
- **Routines (Routinery)**
  - Step-by-step player with a countdown ring, pause, skip and +1 minute.
  - Reorderable steps and streaks.
- **Notes (Keep)**
  - Colour cards in two columns, tickable checklists, labels, search, pin, archive and [[links]].
- **Journal (Daylio)**
  - One-tap mood, activities and a daily prompt.
  - Mood calendar and insights by weekday and activity.
  - Activities, prompts and mood names are editable.
- **Time tracking (Toggl)**
  - One running timer, projects and tags, and one-tap continue.
  - Day, week and month reports; manual and edited entries.
- **Blocker (AppBlock, Lock Me Out)**
  - Daily unlock limit and an optional unlock phrase.
  - Quick Settings focus tile.
- **Web filter (RethinkDNS)**
  - Lookups and blocked-share stats, most blocked sites and busiest apps.
  - Searchable log with one-tap Always allow.
- **Timetable (MyStudyLife)**
  - Rotating A/B (up to 4-week) timetables with an editable anchor week.
- **CGPA**
  - Percentage conversion with an editable formula and an SGPA trend chart.
  - Remaining credits from total programme credits; lowest and highest CGPA still possible.
  - Placement eligibility against editable cut-offs.
- **Placements (Huntr, Teal)**
  - Kanban board with stage history, apply-by reminders, contact, interest stars, prep checklist and minimum-CGPA eligibility.
  - Response-rate funnel.
- **Hostel mess**
  - Editable meal windows (default breakfast 8–9, lunch 12:30–2, dinner 7:30–9) with per-day selection.
  - "Closes soon" reminders and a status card on Campus Today; meals also show on the Today timeline.
  - Study slots are planned around meals, with a warning when classes cover a meal.
  - The wake-up alarm leaves time for breakfast before class.
- **Widgets**
  - Home-screen habit widget (tap to tick); rounded style across all widgets.


### Google Classroom (Campus → Classroom)
- **Two connections:**
  - Classroom notifications (and Classroom e-mails in Gmail) are read instantly, with no setup beyond notification access.
  - Optional direct Google Classroom sync: courses, coursework with due dates, your submission state, grades, materials and announcements. Read-only; syncs every few hours.
- **Understands each item:** assignment, quiz, question, material, announcement, private message or grade, plus title, course and due date ("Due tomorrow, 11:59 PM", "by 15 March 5pm", "Due Fri").
- **Links to your subjects:** each course is matched by code, shared words or acronym (DBMS ↔ Database Management Systems). You can override any match.
- **Ranks what needs you:** overdue, due soon, quizzes, new messages, grades, and announcements with important words (editable).
- **Study targets:** each piece of work's expected effort (editable per type) is spread evenly until the day before it's due, with a daily cap. One tap puts the blocks into today's free time.
- **Deadlines:** pending work becomes a Campus deadline with reminders. It is ticked off when you turn the work in or tick it in Chronora.
- **Actions from announcements:** "no class tomorrow", "test on 14 March", "extra class", "deadline extended" become one-tap timetable, exam or deadline updates.
- **Alerts:** urgent items right away, everything else in a morning digest at an editable time.
- **Also:** paste any Classroom text to add it; snooze, hide, mark done, open in Classroom.


### Alarms, rebuilt (compared with Alarmy)
- **Challenges (missions):**
  - Math (5 levels, 1–8 problems), Memory tile grid (3×3 up to 5×5), Typing (editable phrases, live colouring), Shake, Squats, Steps.
  - Tap-the-moving-dot (new), Photo of a registered spot, QR/barcode scan.
  - Up to 5 per alarm, or none for a one-tap alarm.
- **Anti-cheat:** the sound drops while you work on a mission and returns to full after idle seconds (editable). Leaving mid-mission brings the alarm back. Back never dismisses.
- **Ringing screen:** big clock and date, Start mission / Snooze. After the missions comes a good-morning briefing: mission time, on-time streak, quote, first class and today's deadlines.
- **Sound:** any alarm ringtone, per-alarm volume (rings even in silent mode, then restores your volume), gradually louder (30s–5 min), repeating vibration patterns (pulse, heartbeat, strong).
- **Repeats:** day circles plus Once, Weekdays, Weekends, Every day, odd/even weeks, every N days.
- **Wake-up help:** snooze length and count (or off), wake-up check, backup alarm, bedtime reminder, delete after ringing.
- **Alarm list:** "Rings in 8h 42m" hero, one-tap naps (10 min–1.5 h), on/off switches, day dots and mission icons, preview, skip next, duplicate, presets.
- **Wake-up record:** on-time rate, streak, average minutes late, snoozes, and a daily bar chart.

## In-app updates (More → Update app)

- **Where updates come from:** every push to `main` has CI build the APK and publish it as a GitHub Release, tagged `v<version code>`.
- **Update app:** checks the latest release, shows its size and notes, downloads it with a progress bar, and installs it through Android's package installer.
- **Signing:** the same committed signing key is used for every build, so updates install over the existing app and keep its data.
- **Daily check:** a background check sends a notification when a new version is out; tapping it opens the update dialog.
- **Permission:** the first update needs "Install unknown apps" to be allowed for Chronora, one time only. On Android 12+ later updates can install without a confirmation screen.

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
| Install unknown apps | Installs Chronora's own updates | Only for Update app |
| VPN (local, DNS only) | Web filter and app firewall; DNS goes to the chosen resolver, other traffic is untouched | Only for the web filter |
