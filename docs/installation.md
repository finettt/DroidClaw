# Installing the Linux environment

DroidClaw can run real Linux commands inside a pinned Ubuntu 24.04.5 base image. The image is selected for the device ABI: `arm64-v8a` uses the Ubuntu `arm64` image, and `x86_64` uses `amd64`. Commands run through PRoot, which lets the guest file system live inside the app data folder without root.

## Enabling the environment

1. Open the app menu and go to **Agent Settings**.
2. Tap **Linux environment**.
3. Tap **Install Ubuntu**.

DroidClaw downloads the pinned Ubuntu base archive, verifies its SHA-256 digest, unpacks it, writes resolver and APT source configuration, and marks the image ready. The screen shows each state and the download progress. When the image is ready the screen shows the ready message.

The image and downloaded archive live in the app-private `files/distro/` directory. They are excluded from Android backup. Tap **Delete image** to remove both.

Enabling the environment does not change how shell commands run. It only provides the image. The shell backend must also be set to the local device backend so `execute_shell` runs through the image.

## Disk footprint and limit

**Disk limit (MB)** defaults to 2048 MB. Before installation DroidClaw checks that the app-data partition has room for the limit, the archive, and a safety margin. The same host-side limit is available to later distro commands.

The limit is advisory, not a filesystem quota. PRoot cannot stop a process inside Ubuntu from allocating more space. DroidClaw measures the image from Android and can reject later distro operations when the image is over the configured limit, but it cannot provide a hard quota.

To keep the image small:

- Run `apt clean` after installing packages.
- Remove packages you no longer need with `apt remove`.
- Raise the disk limit in settings when a project needs more room.

## Using apt and pip

The image is a standard Ubuntu base, so APT and pip work inside it.

- Install a package: `apt update && apt install -y <name>`
- Run a Python package manager: `pip install <name>` (install pip first with the package manager if it is not present)

APT operations need network access on the device. PRoot does not add a GPU or hardware acceleration, so compute-heavy commands run at CPU translation speed.

## Limitations

- PRoot is a translation layer, not a full virtual machine. Some commands that need kernel features, device files, or a privileged mount may not work.
- There is no GPU acceleration. Graphics or compute workloads run on the CPU.
- The disk limit is advisory. A process inside the image can still use more space than the limit until DroidClaw measures it and rejects the next operation.
- The agent runs as a foreground service. On Android 12 and later the Phantom Process Killer can still stop background work when the system recovers memory, so a long-running distro command may be killed if the app stays in the background too long. Keep the app in the foreground for long commands, and expect them to resume or fail rather than run unattended.
