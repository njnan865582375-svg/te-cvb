package com.dsh.lock;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.widget.Toast;

import org.json.JSONArray;

public final class ShellHardening {
    private ShellHardening() {
    }

    public static void apply(final Context context, final int mode, final Runnable done) {
        if (mode != LockModes.SHIZUKU && mode != LockModes.ROOT) {
            done.run();
            return;
        }
        applyHome(context, mode, new Runnable() {
            @Override
            public void run() {
                applyDisplay(context, mode, done);
            }
        });
    }

    public static void disableSelectedApps(final Context context, final int mode,
                                           final Runnable done) {
        if (mode != LockModes.SHIZUKU && mode != LockModes.ROOT) {
            done.run();
            return;
        }
        final JSONArray packages = FreezePrefs.isFreezeAll(context)
                ? freezeablePackages(context) : FreezePrefs.getPackages(context);
        if (packages.length() == 0) {
            done.run();
            return;
        }
        StringBuilder command = new StringBuilder();
        for (int i = 0; i < packages.length(); i++) {
            try {
                String pkg = packages.getString(i);
                if (pkg.length() > 0) {
                    command.append("pm disable-user --user 0 ").append(pkg).append("; ");
                }
            } catch (Exception ignored) {
            }
        }
        if (command.length() == 0) {
            done.run();
            return;
        }
        run(context, mode, command.toString(), new AccessibilityShell.Callback() {
            @Override
            public void onResult(boolean success, String output) {
                if (success) {
                    FreezePrefs.setActivePackages(context, packages);
                    FreezePrefs.setActive(context, true);
                }
                done.run();
            }
        });
    }

    public static void restoreSelectedApps(final Context context, final int mode,
                                           final Runnable done) {
        if (!FreezePrefs.isActive(context)) {
            done.run();
            return;
        }
        JSONArray packages = FreezePrefs.getActivePackages(context);
        if (packages.length() == 0) {
            packages = FreezePrefs.getPackages(context);
        }
        StringBuilder command = new StringBuilder();
        for (int i = 0; i < packages.length(); i++) {
            try {
                String pkg = packages.getString(i);
                if (pkg.length() > 0) {
                    command.append("pm enable ").append(pkg).append("; ");
                }
            } catch (Exception ignored) {
            }
        }
        if (command.length() == 0) {
            FreezePrefs.setActive(context, false);
            done.run();
            return;
        }
        run(context, mode, command.toString(), new AccessibilityShell.Callback() {
            @Override
            public void onResult(boolean success, String output) {
                FreezePrefs.setActive(context, false);
                FreezePrefs.setActivePackages(context, new JSONArray());
                done.run();
            }
        });
    }

    private static JSONArray freezeablePackages(Context context) {
        JSONArray result = new JSONArray();
        try {
            PackageManager manager = context.getPackageManager();
            String self = context.getPackageName();
            for (ApplicationInfo app : manager.getInstalledApplications(0)) {
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    continue;
                }
                String pkg = app.packageName;
                if (pkg.equals(self)
                        || pkg.equals("moe.shizuku.privileged.api")
                        || pkg.equals("com.topjohnwu.magisk")) {
                    continue;
                }
                result.put(pkg);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static void restore(final Context context, final int mode, final Runnable done) {
        restoreDisplay(context, mode, new Runnable() {
            @Override
            public void run() {
                restoreHome(context, mode, done);
            }
        });
    }

    private static void applyHome(final Context context, final int mode,
                                  final Runnable done) {
        if (LockPrefs.isShellHomeActive(context)) {
            done.run();
            return;
        }
        run(context, mode, "cmd package get-home-activity --user 0",
                new AccessibilityShell.Callback() {
                    @Override
                    public void onResult(boolean success, String output) {
                        String home = component(output);
                        if (home.length() > 0) {
                            setHome(context, mode, home, done);
                            return;
                        }
                        String resolve = "cmd package resolve-activity --brief "
                                + "-a android.intent.action.MAIN "
                                + "-c android.intent.category.HOME";
                        run(context, mode, resolve, new AccessibilityShell.Callback() {
                            @Override
                            public void onResult(boolean ok, String result) {
                                String fallback = component(result);
                                if (fallback.length() == 0) {
                                    Toast.makeText(context, "桌面切换失败，锁机继续",
                                            Toast.LENGTH_SHORT).show();
                                    done.run();
                                    return;
                                }
                                setHome(context, mode, fallback, done);
                            }
                        });
                    }
                });
    }

    private static void setHome(final Context context, final int mode,
                                final String home, final Runnable done) {
        LockPrefs.setShellHome(context, home);
        LockPrefs.setShellMode(context, mode);
        String set = "cmd package set-home-activity --user 0 "
                + "com.dsh.lock/com.dsh.lock.MainActivity";
        run(context, mode, set, new AccessibilityShell.Callback() {
            @Override
            public void onResult(boolean ok, String result) {
                if (ok) {
                    LockPrefs.setShellHomeActive(context, true);
                } else {
                    LockPrefs.clearShellHome(context);
                    Toast.makeText(context, "桌面切换失败，锁机继续",
                            Toast.LENGTH_SHORT).show();
                }
                done.run();
            }
        });
    }

    private static void restoreHome(final Context context, final int mode,
                                    final Runnable done) {
        if (!LockPrefs.isShellHomeActive(context)) {
            done.run();
            return;
        }
        String home = LockPrefs.getShellHome(context);
        int storedMode = LockPrefs.getShellMode(context);
        int useMode = storedMode == LockModes.SHIZUKU || storedMode == LockModes.ROOT
                ? storedMode : mode;
        if (home.length() == 0) {
            LockPrefs.clearShellHome(context);
            done.run();
            return;
        }
        run(context, useMode, "cmd package set-home-activity --user 0 " + home,
                new AccessibilityShell.Callback() {
                    @Override
                    public void onResult(boolean success, String output) {
                        LockPrefs.clearShellHome(context);
                        done.run();
                    }
                });
    }

    private static void applyDisplay(final Context context, final int mode,
                                     final Runnable done) {
        if (LockPrefs.isShellDisplayActive(context)) {
            done.run();
            return;
        }
        String command = "echo POLICY=$(settings get global policy_control); "
                + "echo ROT=$(settings get system accelerometer_rotation); "
                + "settings put global policy_control 'immersive.full=*'; "
                + "settings put system accelerometer_rotation 0";
        run(context, mode, command, new AccessibilityShell.Callback() {
            @Override
            public void onResult(boolean success, String output) {
                String policy = "null";
                String rotation = "1";
                String[] lines = output == null ? new String[0] : output.split("\\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("POLICY=")) {
                        policy = line.substring("POLICY=".length()).trim();
                    } else if (line.startsWith("ROT=")) {
                        rotation = line.substring("ROT=".length()).trim();
                    }
                }
                if (policy.length() == 0) {
                    policy = "null";
                }
                if (rotation.length() == 0) {
                    rotation = "1";
                }
                if (success) {
                    LockPrefs.setShellPolicy(context, policy);
                    LockPrefs.setShellRotation(context, rotation);
                    LockPrefs.setShellDisplayActive(context, true);
                }
                done.run();
            }
        });
    }

    private static void restoreDisplay(final Context context, final int mode,
                                       final Runnable done) {
        if (!LockPrefs.isShellDisplayActive(context)) {
            done.run();
            return;
        }
        String policy = LockPrefs.getShellPolicy(context);
        String rotation = LockPrefs.getShellRotation(context);
        String command;
        if ("null".equals(policy) || policy.length() == 0) {
            command = "settings delete global policy_control; ";
        } else {
            command = "settings put global policy_control '" + policy + "'; ";
        }
        command += "settings put system accelerometer_rotation " + rotation;
        run(context, mode, command, new AccessibilityShell.Callback() {
            @Override
            public void onResult(boolean success, String output) {
                LockPrefs.clearShellDisplay(context);
                done.run();
            }
        });
    }

    private static void run(Context context, int mode, String command,
                            AccessibilityShell.Callback callback) {
        if (mode == LockModes.SHIZUKU) {
            ShizukuHelper.runCommand(command, callback);
        } else if (mode == LockModes.ROOT) {
            RootHelper.runCommand(command, callback);
        } else {
            callback.onResult(false, "");
        }
    }

    private static String component(String output) {
        if (output == null) {
            return "";
        }
        String[] lines = output.split("\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.length() > 2 && line.contains("/") && !line.contains(" ")) {
                return line;
            }
        }
        return "";
    }
}
