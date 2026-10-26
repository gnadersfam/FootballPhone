FOOTBALL STATS - PHONE APP (separate from The Lounge). STAGE 1
================================================================
What this stage gives you
 - A separate Android phone app called "Football Stats" (it does not touch The Lounge on your Firestick).
 - Bottom tabs: Tonight, Leagues, Referees, Stats, Settings. Only Tonight and Settings work in this stage;
   the other three say they are coming.
 - Tonight: matches today and tomorrow as cards (club badges, home/draw/away bar, likeliest score, "Close match"
   tag, model v bookmakers marker, referee), then "Next up". Tap a match to see its numbers.
 - Settings: all 7 PC themes, a box to type or paste your football-data.org key, Refresh now.
 - Refreshes by itself when the app opens and then every hour while it is open. Refresh now is top right.
 - The numbers come from the same engine code as your Firestick app, so the percentages match the PC app.

STEP BY STEP (one time only)
 1. On github.com make a NEW repository. Name it  FootballPhone  and make it Public. (Public is fine: no keys or
    logins are in these files.)
 2. In the new repo: Add file > Upload files. Drag in everything from this folder EXCEPT the .github folder:
    the "app" folder, build.gradle.kts, settings.gradle.kts, gradle.properties, .gitignore, README-stage1.txt.
    Press Commit.
 3. Add the build recipe by hand (Windows skips folders that start with a dot):
    Add file > Create new file. In the name box type exactly   .github/workflows/build.yml
    Open build.yml from this folder in Notepad, copy everything, paste it in, press Commit.
 4. Open the Actions tab. Wait for a green tick (about 5 minutes). If it goes red, copy the red error text to me.
 5. On your phone, open this address in the browser and download the file:
    https://github.com/gnadersfam/FootballPhone/releases/download/latest/FootballPhone.apk
    (If you named the repo something else, use that name instead.)
 6. Open the downloaded file. Android will ask once to allow installs from this source: say yes.
 7. Open Football Stats > Settings, paste your football-data.org key, press Save, then Refresh now.
    The first refresh takes a few minutes. After that the app updates itself.

TO CHECK ON YOUR PHONE
 - Does Tonight show matches with badges and percentages?  Do they match the PC app for the same matches?
 - Do the 7 themes switch in Settings?  Does Refresh now work, and does the top line show progress?
 - Tell me anything that looks wrong or does not open.

UNDO
 This is a brand-new app, so there is nothing to undo. To remove it, uninstall "Football Stats" from the phone.
