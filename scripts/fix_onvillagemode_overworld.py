#!/usr/bin/env python3
"""Fixes a pre-existing bug in VillageManager.onVillageModeEnabled (introduced
in commit 0ca3f2a, unrelated to the jungle tree-clearing fix): it fetched
`player.level`/`player.level()` -- the player's CURRENT dimension -- instead
of the overworld that VillageData/StarterHouseData are keyed on, matching the
other three call sites in the same file. On several MC versions the field
access also fails to compile since `player.level` is typed `Level`, not
`ServerLevel`.

Rather than just casting, this replaces the buggy line with the same
overworld-fetching expression this file's OTHER call sites already use for
that MC version, keeping the method's actual behavior consistent with the
rest of VillageManager.
"""
import re
import sys

VERSIONS = [
    "1.16.5", "1.17.1", "1.18.2", "1.19.2", "1.20.1",
    "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.6",
    "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
    "26.1", "26.1.1", "26.1.2", "26.2",
]

BUGGY_LINES = [
    "        ServerLevel overworld = player.level;\n",
    "        ServerLevel overworld = player.level();\n",
]

# The correct, already-used-elsewhere-in-the-same-file expression, per version.
OVERWORLD_EXPR = {
    "1.16.5": "player.server.overworld()",
    "1.17.1": "player.server.overworld()",
    "1.18.2": "player.server.overworld()",
    "1.19.2": "player.server.overworld()",
    "1.20.1": "player.server.overworld()",
    "1.21.1": "player.server.overworld()",
    "1.21.3": "player.server.overworld()",
    "1.21.4": "player.server.overworld()",
    "1.21.5": "player.getServer().overworld()",
    "1.21.6": "player.getServer().overworld()",
    "1.21.7": "player.getServer().overworld()",
    "1.21.8": "player.getServer().overworld()",
    "1.21.9": "player.level().getServer().overworld()",
    "1.21.10": "player.level().getServer().overworld()",
    "1.21.11": "player.level().getServer().overworld()",
    "26.1": "player.level().getServer().overworld()",
    "26.1.1": "player.level().getServer().overworld()",
    "26.1.2": "player.level().getServer().overworld()",
    "26.2": "player.level().getServer().overworld()",
}

METHOD_ANCHOR = re.compile(
    r"(public static void onVillageModeEnabled\(ServerPlayer player\) \{\n)"
    r"        ServerLevel overworld = player\.level\(?\)?;\n"
)


def apply_fix(text, version, path):
    match = METHOD_ANCHOR.search(text)
    if not match:
        raise SystemExit(f"{path}: onVillageModeEnabled anchor not found")
    replacement = match.group(1) + f"        ServerLevel overworld = {OVERWORLD_EXPR[version]};\n"
    return text[:match.start()] + replacement + text[match.end():]


def main():
    dry_run = "--apply" not in sys.argv
    for version in VERSIONS:
        path = f"common/{version}/src/main/java/com/beginnersdelight/village/VillageManager.java"
        with open(path, encoding="utf-8") as fh:
            original = fh.read()
        updated = apply_fix(original, version, path)
        if dry_run:
            print(f"OK (dry-run): {path}")
        else:
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(updated)
            print(f"WROTE: {path}")


if __name__ == "__main__":
    main()
