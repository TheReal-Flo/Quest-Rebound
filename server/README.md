# Quest: Rebound Server

Node.js backend for sharing and discovering Quest: Rebound mappings.

## What it does

- Verifies account ownership with Minecraft's session server flow
- Stores users by Minecraft UUID, not username
- Accepts immutable JSON-only preset packages
- Lets clients discover, download, and report presets

## Authentication flow

1. Client `POST`s `/api/auth/challenge`
2. Server returns a short-lived random `challenge`
3. Client calls Minecraft `joinServer` with the local access token and that challenge
4. Client `POST`s `/api/auth/verify` with `{ challenge, username }`
5. Server verifies with:

```txt
https://sessionserver.mojang.com/session/minecraft/hasJoined?username=<name>&serverId=<challenge>
```

6. Server creates or updates the user and returns an app bearer token

## Package format

Preset upload is JSON-only. No multipart files, jars, or scripts are accepted.

```json
{
  "title": "Combat Layout",
  "description": "Fast movement and easy radial menu access",
  "minecraftVersion": "1.21.5",
  "modLoader": "fabric",
  "modVersion": "2.0",
  "files": [
    {
      "path": "interaction_profiles/oculus/touch_controller/combat.json",
      "content": {
        "bindings": [
          {
            "action": "/actions/ingame/in/key.attack",
            "inputPath": "/user/hand/right/input/trigger"
          }
        ]
      }
    },
    {
      "path": "config/rebound_sets.json",
      "content": {
        "profiles": {}
      }
    }
  ]
}
```

Allowed paths:

- `interaction_profiles/**/*.json`
- `config/rebound_settings.json`
- `config/rebound_sets.json`

## Development

```bash
npm install
npm run dev
```

Required environment variables are listed in [`.env.example`](./.env.example).
