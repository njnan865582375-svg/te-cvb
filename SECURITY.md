# Security

TE cvb is a self-lock / kiosk tool. It is intended for devices you own.

## Known risks

- The remote SSH feature stores the SSH password in SharedPreferences in plain text.
- SSH host keys are not verified.
- Freezing apps uses `pm disable-user`. If TE cvb is uninstalled or its data is
  cleared while apps are frozen, those apps can remain disabled.
- Device owner mode can prevent uninstall and hide Home/Recents. Removing it
  requires ADB / a factory reset.
- Shizuku and root modes execute shell commands. Only grant them to builds you trust.

## Reporting

Open a private security advisory on GitHub, or contact the maintainers through
the community links in README.md. Do not include passwords, private keys, or
device identifiers in public issues.
