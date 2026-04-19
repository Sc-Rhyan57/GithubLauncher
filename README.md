# GitHub Launcher

A mobile GitHub workspace for Android — edit files, browse repos, chat with Copilot and more.

## Features

- **Repository Browser** — Browse all repos you have access to, with permission indicators
- **Code Editor** — Syntax-highlighted editor with commit support (undo, save, delete)
- **GitHub Copilot Chat** — AI assistant integrated directly in the app
- **Notifications** — View and mark GitHub notifications as read
- **Discord RPC** — Show your editing activity in Discord (like VS Code)
- **Profile Editor** — Edit your GitHub profile if your token allows
- **Theme System** — Dark / Light / AMOLED + multiple color palettes (Metrolist-style)
- **App Console** — Hidden debug log viewer (tap footer 5x)

## Setup

1. Go to [GitHub Settings → Tokens](https://github.com/settings/tokens/new)
2. Create a Personal Access Token with the scopes you need:
   - `repo` — full repository access
   - `user` — read/write profile
   - `notifications` — read notifications
   - `copilot` — Copilot API access
3. Open the app and paste your token

## Build

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK
./gradlew :app:assembleRelease
```

Or use the **GitHub Actions** workflow (manually triggered).

## Discord RPC

When enabled, GitHub Launcher will display your activity in Discord:

```
GitHub Launcher
Editing MyFile.kt
Workspace: MyRepository
```

Requires your Discord account token (obtained via the GetDiscordToken companion app).

## License

GNU General Public License v3.0 — Free software. Use, study, share and improve it.

## Author

**Rhyan57** — [github.com/Sc-Rhyan57](https://github.com/Sc-Rhyan57)
