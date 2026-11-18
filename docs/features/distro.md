# Linux distro bootstrap

DroidClaw can install a pinned Ubuntu 24.04.5 base image from **Agent Settings → Linux environment**. The image is selected for the device ABI: `arm64-v8a` uses Ubuntu `arm64`, and `x86_64` uses `amd64`.

## Installation

Tap **Install Ubuntu**. DroidClaw downloads the pinned Ubuntu base archive, verifies its SHA-256 digest, unpacks it, writes resolver and APT source configuration, and marks the image ready. The screen shows the current state and download progress.

The image and downloaded archive live in the app-private `files/distro/` directory. They are excluded from Android backup. **Delete image** removes both.

The bootstrap only installs the image. It does not change the shell backend or route `execute_shell` through PRoot.

## Disk limit

**Disk limit (MB)** defaults to 2048 MB. Before installation, DroidClaw checks that the app-data partition has room for this limit, the archive, and a safety margin. The same host-side limit is available to later distro command integration.

This limit is advisory, not a filesystem quota. PRoot cannot stop a process inside Ubuntu from allocating more space. DroidClaw measures the image from Android and can reject later distro operations when the image is over the configured limit, but it cannot provide hard quota enforcement.

## Verification

The feature was verified locally with the full unit suite, the distro unit
tests (Robolectric SDK 30), and the real-image instrumented bootstrap on API 35.
CI runs the unit suite, lint, and the instrumented shards on API 35 and 36.
