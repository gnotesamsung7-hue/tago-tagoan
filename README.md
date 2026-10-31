# Matthew and Xian’s Tago Tagoan

First-person hide-and-seek horror game for Android, with 4 stages and local Wi-Fi multiplayer.

Every push builds the APK with GitHub Actions. Get it from the **Releases** section on the right side of this page and open `TagoTagoan.apk` on each phone.

**Wi-Fi play:** put every phone on the same Wi-Fi (or the host phone’s hotspot). One phone taps *Wi-Fi multiplayer → Host a game*, the others tap that game in the list (or type the host’s address).

To update the game, replace `app/src/main/assets/index.html` and commit — a new APK is built automatically.

Fonts: Baloo 2 and Creepster, SIL Open Font License (see `app/src/main/assets/fonts`).

## Play on Meta Quest (VR)

1. In this repo on GitHub: **Settings → Pages → Build and deployment → Source: Deploy from a branch → Branch: `main`, folder `/docs` → Save**.
2. After a minute, the game is live at `https://gnotesamsung7-hue.github.io/<this-repo-name>/`.
3. On the Quest, open that address in the **Quest Browser**, start any game, then tap **🥽** (top right) to enter VR.

To update the VR version, replace `docs/index.html` with the new game file (it is the same file as `app/src/main/assets/index.html`).

**VR controls:** left stick walk (where you look) • left trigger run • right stick snap-turn, pull down to turn around • right hand = flashlight • Ⓐ hide / get out • Ⓑ hold breath • right trigger search. Multo Hunt: right trigger fires the plasma gun, Ⓐ catches, left hand holds the ghost meter.
