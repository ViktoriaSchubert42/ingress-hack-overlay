# Play Store listing

Copy these into Play Console → Grow users → Store presence → Main store listing.
Limits: app name 30, short description 80, full description 4000 characters.

---

## English (en-US)

### App name (30/30)

```
Hack Timer Overlay for Ingress
```

### Short description

```
Burnout, hacking speed, hacks left and drone timers floating over Ingress.
```

### Full description

```
Keep track of every wait while you play Ingress Prime, without leaving the game.

Hack Timer Overlay for Ingress puts small hexagon buttons on top of the game. Each button is a timer or counter for one thing agents usually have to remember. The buttons only appear while Ingress is open and hide everywhere else.

THE TIMERS
• Portal Burnout Refresh: 4-hour timer for a burned-out portal.
• Hacking Speed Timer: the wait until you can hack the same portal again. Set the portal type and its heat sinks, and the app works out the cooldown.
• Remaining Hacks Countdown: counts how many hacks a portal has left. Set its multi-hacks, and the app works out the number of hacks.
• Drone Timer Cooldown: the wait until your drone can make a long jump again.

Add as many timers and counters as you need, for example one cooldown per portal you are farming. Only the drone timer is limited to one.

EASY TO USE
• Tap a timer to start it. Long-press resets it without starting.
• Tap the hacks counter after each hack. Long-press resets it.
• Yellow means ready, white means running.
• When a timer ends, your phone vibrates and you get a notification, even when the screen is off.
• Drag the row of buttons anywhere on the screen.
• Hide or show the buttons from a Quick Settings tile.

PORTAL MOD CALCULATOR
Pick the multi-hacks (common, rare, very rare) or heat sinks in the four mod slots. The app applies the game's rules: the strongest mod counts fully and every other one counts half.

MAKE IT YOURS
• Choose Enlightened or Resistance, and the heat sink and multi-hack buttons show in your faction's color.
• Change button size, color, and a horizontal or vertical row.

PRIVACY
No internet access, no ads, no tracking. Everything stays on your phone.

The app is a timer helper. It does not read, change or automate the game in any way.

Open source: https://github.com/ViktoriaSchubert42/ingress-hack-overlay

"Ingress" is the property of Google Inc. This application and GoofyGecko are NOT affiliated in any way with Google Inc, Niantic Labs or Ingress Project.
```

---

## German (de-DE)

### App-Name (30/30)

```
Hack Timer Overlay for Ingress
```

### Kurzbeschreibung

```
Burnout-, Hack-Cooldown-, Hack- und Drohnen-Timer direkt über Ingress.
```

### Vollständige Beschreibung

```
Behalte beim Spielen von Ingress Prime jede Wartezeit im Blick, ohne das Spiel zu verlassen.

Hack Timer Overlay for Ingress legt kleine Sechseck-Buttons über das Spiel. Jeder Button ist ein Timer oder Zähler für etwas, das sich Agents sonst merken müssen. Die Buttons erscheinen nur, während Ingress geöffnet ist, und sind sonst ausgeblendet.

DIE TIMER
• Portal Burnout Refresh: 4-Stunden-Timer für ein ausgebranntes Portal.
• Hacking Speed Timer: die Wartezeit, bis du dasselbe Portal wieder hacken kannst. Wähle Portaltyp und Heat Sinks, und die App berechnet den Cooldown.
• Remaining Hacks Countdown: zählt, wie viele Hacks ein Portal noch hat. Wähle die Multi-Hacks, und die App berechnet die Anzahl der Hacks.
• Drone Timer Cooldown: die Wartezeit, bis deine Drohne wieder weit springen kann.

Füge so viele Timer und Zähler hinzu, wie du brauchst, zum Beispiel einen Cooldown pro Portal, das du farmst. Nur den Drohnen-Timer gibt es einmal.

EINFACH ZU BEDIENEN
• Tippe auf einen Timer, um ihn zu starten. Langes Drücken setzt ihn zurück, ohne ihn zu starten.
• Tippe nach jedem Hack auf den Hack-Zähler. Langes Drücken setzt ihn zurück.
• Gelb heißt bereit, Weiß heißt läuft.
• Wenn ein Timer abläuft, vibriert dein Handy und du bekommst eine Benachrichtigung, auch bei ausgeschaltetem Bildschirm.
• Verschiebe die Button-Reihe an jede Stelle des Bildschirms.
• Blende die Buttons über eine Schnelleinstellungs-Kachel ein oder aus.

PORTAL-MOD-RECHNER
Wähle die Multi-Hacks (common, rare, very rare) oder Heat Sinks in den vier Mod-Slots. Die App wendet die Spielregeln an: Der stärkste Mod zählt voll, jeder weitere zur Hälfte.

NACH DEINEM GESCHMACK
• Wähle Enlightened oder Resistance, und die Heat-Sink- und Multi-Hack-Buttons erscheinen in der Farbe deiner Fraktion.
• Ändere Größe, Farbe und ob die Buttons waagrecht oder senkrecht angeordnet sind.

DATENSCHUTZ
Kein Internetzugriff, keine Werbung, kein Tracking. Alles bleibt auf deinem Handy.

Die App ist eine Timer-Hilfe. Sie liest, verändert oder automatisiert das Spiel in keiner Weise.

Open Source: https://github.com/ViktoriaSchubert42/ingress-hack-overlay

"Ingress" ist Eigentum von Google Inc. Diese App und GoofyGecko stehen in KEINERLEI Verbindung zu Google Inc, Niantic Labs oder Ingress Project.
```

---

## Other console answers

| Section | Answer |
|---|---|
| App or game | App |
| Category | Tools |
| Free or paid | Free |
| Privacy policy URL | https://viktoriaschubert42.github.io/ingress-hack-overlay/privacy.html |
| App access | All functionality is available without special access |
| Ads | No, the app does not contain ads |
| Content rating | Questionnaire: utility app, answer "No" to every content question |
| Target audience | 13–15, 16–17 and 18+ (not under 13) |
| News app | No |
| Government app | No |
| Financial features | None |
| Health | None |
| Data safety | No data collected, no data shared. Data is not encrypted in transit (none is sent). Users can't request deletion (nothing is stored off-device). |

### Foreground service declaration (special use)

Type: **Special use**

Description:

```
The app shows floating timer buttons over the game Ingress Prime while the user plays. The foreground service keeps these overlay buttons on screen and counts the timers down. It runs only after the user taps "Start overlay" and stops when the user taps "Stop overlay". It shows a permanent notification with the running timers.
```

Video: record a short screen video (about 30 seconds) showing: tap "Start overlay" → the notification appears → open Ingress → the buttons appear → tap a timer and it counts down. Upload it to YouTube as "Unlisted" and paste the link.

### Exact alarm

Nothing to declare. The app uses SCHEDULE_EXACT_ALARM, which the user grants in Android settings.
