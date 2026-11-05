
## What counts

- The launcher (`com.sadique.launcher`) doing something it shouldn't: reaching the network, exposing
  data to other apps, or being made to open something it shouldn't.
- The tools (`tools/tv.py`, `disable.sh`, `restore.sh`, `theme.sh`, `recovery.py`) running commands
  on a device or computer they weren't pointed at, or mishandling paths and input.
- A debloat list or the prompt leading to a TV that can't be restored with `tools/restore.sh`.
- Anything in a pull request that runs code on a contributor's or maintainer's machine.

ADB itself is powerful by design. Turning on USB or network debugging lets any computer on your
network ask to connect, and the TV asks you to allow it. Turn debugging off when you're done.

## What happens next

You'll get a reply within a few days. Fixes go into `main` with credit to you, unless you'd rather
stay anonymous. Please give a fix a reasonable chance to ship before you publish details.
