# Daylight verification

## Automated checks

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
# With an unlocked Android emulator/phone connected:
.\gradlew.bat connectedDebugAndroidTest
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`.

The unit suite tests fixed/interval/random planning; random feasibility, spacing, quiet gaps, and stability across 366 days; quiet-hour boundaries; collision suppression; DST transitions; timezone changes; response review deferral and restart recovery; checkbox completion; required fields; numbers; rating limits; and summary contents.

Robolectric tests exercise Room CRUD, form snapshots and cascade deletion, response/reflection persistence, backup round trips, duplicate/invalid imports, durable occurrence claims, AlarmManager registration and cancellation, notification content rotation, all bottom destinations, and dark-mode persistence. The instrumented smoke test runs onboarding and navigation in a real Android runtime.

Database regressions also cover coherent observed form/question and response/answer snapshots, rejection of incomplete or rewritten review drafts, protection against recreating deleted entries, and question ownership across forms. Check VERIFICATION.md for execution status.

## Phone checklist

Use a fresh install on Android 13 or later, and ideally another Android 8–12 device. Do not clear app data on a phone containing your only copy of personal entries.

- [ ] Complete all four onboarding pages; confirm no unexpected permission prompt.
- [ ] Add a quote with source/reference. Search, favorite, pause, edit, and delete it.
- [ ] Add verified Arabic Quran text with translation; reopen it and confirm exact preservation and readable RTL text.
- [ ] Confirm Quran/Hadith categories have no generated samples.
- [ ] Add an enabled motivation entry. Create a fixed reminder two minutes from now outside quiet hours.
- [ ] From Settings, deny notification permission once; confirm the app explains the disabled state.
- [ ] Grant notifications. Enable optional precise reminders if desired. Confirm delivery with the app closed.
- [ ] Expand the notification; test Open, Favorite, and Mark as read independently.
- [ ] Add several entries in the category. Verify rotation does not repeat one before unseen items.
- [ ] Create an interval reminder; inspect the next occurrences.
- [ ] Preview a random schedule. Reopen the app and confirm the day's times do not shuffle.
- [ ] Try an impossible random count/spacing/window. Confirm it is rejected.
- [ ] Pause a reminder; verify its future occurrences disappear.
- [ ] Set quiet hours across midnight. Confirm content reminders do not occur within them.
- [ ] Create a form with checkbox, multiple choice, short/long text, number, yes/no, and rating questions.
- [ ] Reorder, delete, and edit questions. Check a required field is enforced.
- [ ] Submit a morning checklist. Set its evening review a few minutes ahead for this test.
- [ ] Receive the evening notification, expand it, and open the exact saved response.
- [ ] Check/uncheck goals, write a reflection and tomorrow's intention, and save. Confirm completion percentage changes.
- [ ] Open history; confirm dates, answers, and reflection text persist.
- [ ] Edit the original form template; confirm historical question wording stays unchanged.
- [ ] Rotate while editing content, a form, and a response. Confirm draft text and selections survive.
- [ ] Switch Light, Dark, and System. Check every screen at large Android font size and landscape orientation.
- [ ] Use TalkBack: bottom navigation, labeled fields, checkboxes, radio buttons, and icon actions have intelligible labels.
- [ ] Export a JSON backup. Reimport it; confirm no duplicate records and no overwritten settings.
- [ ] Import malformed JSON and a structurally invalid backup; confirm no data changes.
- [ ] Import an archive with new forms; confirm reminders are paused and old response reviews are not scheduled.
- [ ] Restart the phone. Unlock it and verify a future reminder still arrives.
- [ ] Change timezone and clock; reopen Schedule and verify future local fixed times update.
- [ ] Swipe away the app; confirm reminders remain registered. Force-stop it separately; reopen to restore alarms.
- [ ] Enable battery saver/Doze and test both exact access and flexible timing; record any manufacturer-specific delays.
- [ ] Revoke precise-alarm access, reopen the app, and verify flexible reminders resume.
- [ ] Verify saved content and responses after activity recreation and process death (`adb shell am kill com.daylight.app` while backgrounded).
- [ ] Check empty library, empty category, no search results, empty form history, and deleted notification destinations.

Physical device delivery, OEM battery behavior, real TalkBack usability, and visual inspection cannot be established by planner unit tests alone. Record the Android version/device and observed timing when performing these checks.
