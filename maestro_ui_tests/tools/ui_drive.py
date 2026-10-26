#!/usr/bin/env python3
"""Ticket-agnostic UI driver that does not need the Maestro CLI.

Maestro 2.4 binds one fixed host port (7001) for its driver, so a second QA
session on the same Mac cannot run flows while another one is testing. This
script drives the app straight through adb: `uiautomator dump` for the
hierarchy, `input tap` for taps, `input swipe` for a drag with an explicit
start element. Taps are resolved from the dump, never guessed.

Safety: every action first checks the screen for a money confirmation
("Confirm and Stake", "Confirm Action", "Confirm and Send", "Slide to
confirm"). If one is on screen the script refuses to touch anything.

usage (ANDROID_SERIAL picks the device, default emulator-5554):
  ui_drive.py dump [--filter TEXT]        list text/id/bounds top-to-bottom
  ui_drive.py rows                        token rows of the list on screen
  ui_drive.py tap-text "^Manage$"         tap the element whose text matches
  ui_drive.py tap-id pinned --row "TRX"   tap an id inside the row of a text
  ui_drive.py drag-row "USDT TON" --to "USDT TRC20"   drag by the reorder handle
  ui_drive.py shot out.png
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5554")
PKG = os.environ.get("APP_ID", "com.ton_keeper.debug")
MONEY = ("Confirm and Stake", "Confirm Action", "Confirm and Send", "Slide to confirm")


def adb(*args, binary=False):
    cmd = ["adb", "-s", SERIAL] + list(args)
    out = subprocess.run(cmd, capture_output=True)
    return out.stdout if binary else out.stdout.decode("utf-8", "replace")


def dump():
    """Return [(y, x, text, rid, bounds)] sorted top-to-bottom."""
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml:
            break
        time.sleep(1)
    else:
        raise SystemExit("uiautomator dump failed")
    root = ET.fromstring(xml[xml.index("<hierarchy"):])
    nodes = []
    for n in root.iter("node"):
        b = n.get("bounds") or ""
        m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", b)
        if not m:
            continue
        x1, y1, x2, y2 = (int(g) for g in m.groups())
        text = (n.get("text") or "").strip() or (n.get("content-desc") or "").strip()
        rid = (n.get("resource-id") or "").split("/")[-1]
        nodes.append({
            "y": y1, "x": x1, "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
            "x1": x1, "y1": y1, "x2": x2, "y2": y2,
            "text": text, "rid": rid, "clickable": n.get("clickable") == "true",
        })
    nodes.sort(key=lambda n: (n["y"], n["x"]))
    return nodes


def guard(nodes):
    for n in nodes:
        if n["text"] in MONEY:
            raise SystemExit(f"REFUSED: money confirmation on screen ({n['text']!r})")


def find_text(nodes, pattern):
    rx = re.compile(pattern)
    hits = [n for n in nodes if n["text"] and rx.search(n["text"])]
    if not hits:
        raise SystemExit(f"no element matching {pattern!r}")
    return hits


def tap(node):
    adb("shell", "input", "tap", str(node["cx"]), str(node["cy"]))
    time.sleep(1.5)


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    cmd = sys.argv[1]
    args = sys.argv[2:]
    nodes = dump()

    if cmd == "dump":
        flt = args[1] if len(args) > 1 and args[0] == "--filter" else None
        for n in nodes:
            if not n["text"] and not n["rid"]:
                continue
            if flt and flt.lower() not in (n["text"] + n["rid"]).lower():
                continue
            print(f"y={n['y']:<5} x={n['x']:<5} click={int(n['clickable'])} "
                  f"id={n['rid']:<12} {n['text'][:46]}")
        return

    if cmd == "rows":
        for n in nodes:
            if n["rid"] == "title" and n["text"]:
                print(f"{n['y']:>5}  {n['text']}")
        return

    if cmd == "shot":
        png = adb("exec-out", "screencap", "-p", binary=True)
        open(args[0], "wb").write(png)
        print(f"{args[0]} ({len(png)} bytes)")
        return

    guard(nodes)

    if cmd == "tap-text":
        tap(find_text(nodes, args[0])[0])
        print(f"tapped {args[0]}")
        return

    if cmd == "tap-id":
        rid = args[0]
        row = args[args.index("--row") + 1] if "--row" in args else None
        cands = [n for n in nodes if n["rid"] == rid]
        if not cands:
            raise SystemExit(f"no element with id {rid}")
        if row:
            anchor = find_text(nodes, row)[0]
            # same row = vertical bands overlap
            cands = [n for n in cands if n["y1"] < anchor["y2"] and n["y2"] > anchor["y1"]]
            if not cands:
                raise SystemExit(f"no id={rid} in the row of {row!r}")
        tap(cands[0])
        print(f"tapped id={rid}" + (f" in row {row!r}" if row else ""))
        return

    if cmd == "drag-row":
        src = find_text(nodes, args[0])[0]
        dst = find_text(nodes, args[args.index("--to") + 1])[0]
        handles = [n for n in nodes if n["rid"] == "reorder"
                   and n["y1"] < src["y2"] and n["y2"] > src["y1"]]
        if not handles:
            raise SystemExit(f"no reorder handle in the row of {args[0]!r}")
        h = handles[0]
        # long, slow drag so ItemTouchHelper follows it
        adb("shell", "input", "swipe", str(h["cx"]), str(h["cy"]),
            str(h["cx"]), str(dst["cy"]), "1200")
        time.sleep(2)
        print(f"dragged {args[0]!r} to the row of {args[args.index('--to') + 1]!r}")
        return

    raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
