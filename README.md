# DeskGlass

A frosted-glass desk companion for a Samsung Galaxy Tab S6 Lite: a home screen that fades between a clock, the weather and a calendar, with notes, checklists and an AI chat one tap away.

## Updates

Every push to `main` is built by GitHub Actions and published as a GitHub Release. The app checks this public repo's latest release on its own and offers to install it, so there is no need to download APKs by hand. First install: download the APK from the latest release once and allow installs from your browser.

The signing key in `keystore/` is committed on purpose. It is what lets each new APK install over the last one, so do not replace it.

## Status

- Home screen: clock, weather (Open-Meteo, set your city in Settings), month calendar
- In-app updater
- Next: calendar events, notes, checklists, Claude chat, the self-playing game wallpaper
