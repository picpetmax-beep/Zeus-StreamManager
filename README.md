# ZEUS Stream Manager 1.0.0

Paper 1.21.11 / Java 21 plugin source project.

## Build
Run `mvn package` from this folder. The jar will be in `target/ZEUS-Stream-Manager-1.0.0.jar`.

## Install
Copy the built jar into the server's `plugins` folder and restart Paper. Edit `plugins/ZEUSStreamManager/config.yml` to customize the Chat Manager command.

## Commands
- `/stream <https://link>` — register or update your stream URL.
- `/stream announce` — send an announcement request to online staff.
- `/stream info` — show your saved link.
- `/stream approve <player>` — staff approves and dispatches the configured announce command from console.
- `/stream deny <player>` — staff rejects the request.

Permission: `zeusstream.staff` (default: op).

## Chat Manager integration
Default config: `announce-command: "announce {player} is live: {link}"`.
The plugin executes this as the server console, equivalent to `announce <message>` (without the slash). If your Chat Manager requires a different message format, edit this setting. Confirm that the Chat Manager `/announce` command accepts console execution.

Requests and registered links are stored in `plugins/ZEUSStreamManager/data.yml`. The request cooldown is configurable in seconds.
