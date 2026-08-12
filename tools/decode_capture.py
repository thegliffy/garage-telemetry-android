#!/usr/bin/env python3
"""Decode captured Ioniq 5 Mode 22 frames and compare the competing byte-offset theories.

Capture on the phone (app connected to the adapter, car awake):

    adb logcat -c && adb logcat -d -s Elm327:V > capture.txt

Then:

    python3 tools/decode_capture.py capture.txt
    python3 tools/decode_capture.py --baseline          # replay the 54% SOC reference capture

Why this exists: at 54% SOC two decode conventions for 220105 both landed within rounding
of the dash, so the capture could not separate them. They diverge sharply at a different
SOC, so a 100% capture settles it. See IoniqUds.kt / garagepi's ioniq_mode22.py.
"""

from __future__ import annotations

import argparse
import re
import sys

# Reference capture taken with the car parked, dash reading 54%, Long Range (77.4 kWh).
BASELINE = {
    "220101": "03E 0: 62 01 01 EF FB E7 1: EF 6E 00 00 00 00 00 2: 00 0C 1B EB 1E 1D 1D "
              "3: 1E 1D 1D 1D 00 49 BA 4: B9 B9 33 00 00 90 00 5: 01 25 F9 00 01 21 31 "
              "6: 00 00 D6 79 00 00 CE 7: 06 00 93 CB 23 00 02 8: C9 00 00 00 00 06 A1",
    "220105": "02E 0: 62 01 05 FF FB 74 1: 0F 01 2C 01 01 2C 1D 2: 1E 1D 1E 1D 1D 1D 6C "
              "3: 34 6C 34 00 00 4B 20 4: 00 03 95 7E 40 E6 00 5: 6D 00 00 00 00 00 00 "
              "6: 00 1D 1D 1E 1E AA AA",
    "22E011": "031 0: 62 E0 11 FF FF FF 1: F8 01 01 00 00 00 8E 2: 3A A9 0A 03 1B CA 39 "
              "3: A9 7F BC FF 22 60 00 4: 9A 19 01 01 01 00 05 5: 00 00 E9 00 07 00 00 "
              "6: 00 00 00 00 00 00 00 7: 00 AA AA AA AA AA AA",
}

GROSS_KWH = 77.4   # Long Range pack, gross
USABLE_KWH = 74.0  # approximate usable


def hex_tokens(raw: str) -> list[int]:
    """Same filter the app uses: only standalone 1-2 digit hex bytes.

    Drops the ISO-TP length header ("03E", 3 chars) and the "0:"/"1:" line
    prefixes, leaving just the reassembled payload.
    """
    return [int(t, 16) for t in re.split(r"\s+", raw.upper()) if re.fullmatch(r"[0-9A-F]{1,2}", t)]


def strip_header(payload: list[int]) -> list[int] | None:
    """Drop the `62 <did_hi> <did_lo>` positive-response echo."""
    if len(payload) < 4 or payload[0] != 0x62:
        return None
    return payload[3:]


def idx(letters: str) -> int:
    """Torque Pro letter index: a=0 ... z=25, aa=26, ab=27, ..."""
    s = letters.lower()
    return ord(s[0]) - 97 if len(s) == 1 else 26 * (ord(s[0]) - 97 + 1) + (ord(s[1]) - 97)


def u16(d: list[int], hi: int, lo: int) -> int:
    return (d[hi] << 8) | d[lo]


def s16(d: list[int], hi: int, lo: int) -> int:
    v = u16(d, hi, lo)
    return v - 65536 if v > 32767 else v


def s8(d: list[int], i: int) -> int:
    return d[i] - 256 if d[i] > 127 else d[i]


def find_speed(frames: dict[str, str], known_kmh: float) -> None:
    """Locate the vehicle-speed byte in the VMCU frame using a known dash speed.

    The Ioniq 5 does not answer standard Mode 01 `010D`, so speed has to come from the
    VMCU (22E004) and the offset must be derived rather than guessed — guessing is what
    produced the wrong 220105 display-SOC decode.
    """
    raw = frames.get("22E004")
    if not raw:
        print("No 22E004 frame in this capture — is the app polling the VMCU query?")
        return

    payload = hex_tokens(raw)
    d = strip_header(payload)
    if d is None:
        print(f"22E004 response not parseable: {raw}")
        return

    print(f"22E004 payload ({len(d)} bytes) vs known speed {known_kmh:g} km/h")
    print("candidates within 2 km/h:\n")

    tol = 2.0
    hits = 0
    for i, b in enumerate(d):
        for label, val in (("raw", float(b)), ("/2", b / 2.0), ("*2", b * 2.0)):
            if abs(val - known_kmh) <= tol:
                print(f"  data[{i:2d}] (letter {_letters(i):>2}) = 0x{b:02X} -> {val:g} km/h via {label}")
                hits += 1
    for i in range(len(d) - 1):
        for order, v in (("BE", (d[i] << 8) | d[i + 1]), ("LE", (d[i + 1] << 8) | d[i])):
            for label, val in (("/10", v / 10.0), ("/100", v / 100.0), ("/128", v / 128.0)):
                if abs(val - known_kmh) <= tol:
                    print(f"  data[{i:2d}],data[{i+1:2d}] {order} = 0x{v:04X} -> {val:.2f} km/h via {label}")
                    hits += 1

    if hits == 0:
        print("  none — check the speed you passed, or capture again while holding a steady speed")
    else:
        print("\nTake two captures at clearly different speeds and keep only the offset")
        print("that tracks both; a single capture will always throw up coincidences.")


def _letters(i: int) -> str:
    return chr(97 + i) if i < 26 else chr(97 + (i // 26) - 1) + chr(97 + (i % 26))


def find_odometer(frames: dict[str, str], known: float) -> None:
    """Locate the odometer field in the cluster frame using the value on the dash.

    Odometer is the cleanest calibration target we have: it is a large distinctive
    number you can read exactly, so a coincidental match is far less likely than with
    a small value like speed. Trip distance is then just end-minus-start.
    """
    raw = frames.get("22B002")
    if not raw:
        print("No 22B002 frame in this capture — is the app polling the cluster query?")
        return

    d = strip_header(hex_tokens(raw))
    if d is None:
        print(f"22B002 response not parseable: {raw}")
        return

    print(f"22B002 payload ({len(d)} bytes) vs known odometer {known:g}")
    print("candidates within 1 unit (checking km and miles interpretations):\n")

    hits = 0
    # Odometers are 3-4 byte counters; include 2 for completeness on low-mileage cars.
    for width in (2, 3, 4):
        for i in range(len(d) - width + 1):
            be = int.from_bytes(bytes(d[i : i + width]), "big")
            le = int.from_bytes(bytes(d[i : i + width]), "little")
            for order, v in (("BE", be), ("LE", le)):
                for label, val in (("raw", float(v)), ("/10", v / 10.0)):
                    for unit, converted in (("", val), (" (as km→mi)", val / 1.609344)):
                        if abs(converted - known) <= 1.0:
                            print(
                                f"  data[{i}:{i+width}] {order} {width}B = 0x{v:0{width*2}X}"
                                f" -> {converted:.1f} via {label}{unit}"
                            )
                            hits += 1

    if hits == 0:
        print("  none — check the value you passed, and that it is the odometer")
        print("  (not trip A/B). Try again including a full frame in the capture.")
    else:
        print("\nIf several match, capture again after driving a few miles: only the")
        print("real odometer will have advanced by the distance you actually covered.")


def report(frames: dict[str, str]) -> None:
    soc = None

    if "220101" in frames:
        d = strip_header(hex_tokens(frames["220101"]))
        if d and len(d) >= 20:
            soc = d[idx("e")] / 2.0
            # Matches IoniqUds.decode220101: positive = discharge, negative = charge/regen.
            current = s16(d, idx("k"), idx("l")) / 10.0
            volts = u16(d, idx("m"), idx("n")) / 10.0
            power = current * volts / 1000.0
            flow = "discharging" if power > 0 else "charging" if power < 0 else "idle"
            print("220101 (BMS)")
            print(f"  HV_SOC (raw BMS)   = {soc:.1f} %      <- dashboard source")
            print(f"  PACK_VOLTAGE       = {volts:.1f} V")
            print(f"  PACK_CURRENT       = {current:.1f} A")
            print(f"  PACK_POWER         = {power:.2f} kW ({flow})")
            print(f"  BATT_TEMP max/min  = {s8(d, idx('o'))} / {s8(d, idx('p'))} C")
            if len(d) > idx("ad"):
                print(f"  AUX_VOLTAGE        = {d[idx('ad')] * 0.1:.1f} V")

    if "220105" in frames:
        d = strip_header(hex_tokens(frames["220105"]))
        if d:
            print("\n220105 (extended BMS)  -- the contested frame")
            if len(d) > idx("aa"):
                print(f"  HV_SOH             = {u16(d, idx('z'), idx('aa')) / 10.0:.1f} %")
            if len(d) > idx("af"):
                unshifted = d[idx("af")] / 2.0
                print(f"  display SOC via af unshifted (data[{idx('af')}]) = {unshifted:.1f} %"
                      f"   <- this app's reading")
            if len(d) > idx("ad"):
                shifted_kwh = u16(d, idx("ac"), idx("ad")) * 2 / 1000.0
                print(f"  remaining energy via ac:ad  = {shifted_kwh:.1f} kWh"
                      f"   <- garagepi's reading")
                if soc is not None and soc > 0:
                    implied = shifted_kwh / (soc / 100.0)
                    verdict = "PLAUSIBLE" if abs(implied - GROSS_KWH) < 8 else "WRONG for a 77.4 kWh pack"
                    print(f"      -> implies a {implied:.1f} kWh pack ({verdict})")
            if soc is not None:
                print(f"      expected remaining at {soc:.0f}% SOC:"
                      f" {GROSS_KWH * soc / 100:.1f} kWh gross / {USABLE_KWH * soc / 100:.1f} kWh usable")

    if "22E011" in frames:
        d = strip_header(hex_tokens(frames["22E011"]))
        if d and len(d) > idx("w"):
            print("\n22E011 (ICCU)")
            print(f"  AUX_SOC            = {d[idx('w')]} %")

    print("\nWhat a 100% capture proves:")
    print("  * af unshifted should read ~100% (byte 0xC8). If it stays near 0x6D/54%, af is not SOC.")
    print("  * remaining energy should read ~74-77 kWh. garagepi's ac:ad decode read 33.2 kWh at")
    print("    54% (implying a 61 kWh pack); if it does not land near 77 at 100%, it is confirmed wrong.")


def extract_from_log(text: str) -> dict[str, str]:
    """Pull the newest response for each PID out of `adb logcat -s Elm327:V` output."""
    frames: dict[str, str] = {}
    for pid in ("220101", "220105", "22E011", "22E004", "22B002"):
        matches = re.findall(rf"{pid} -> '([^']*)'", text)
        real = [m for m in matches if "62" in m.upper()]
        if real:
            frames[pid] = real[-1]
        elif matches:
            print(f"warning: {pid} responded but returned no data: '{matches[-1]}'", file=sys.stderr)
    return frames


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("logfile", nargs="?", help="output of: adb logcat -d -s Elm327:V")
    ap.add_argument("--baseline", action="store_true", help="replay the 54%% SOC reference capture")
    ap.add_argument(
        "--find-speed",
        type=float,
        metavar="KMH",
        help="locate the VMCU speed byte using the speed shown on the dash during the capture",
    )
    ap.add_argument(
        "--find-odometer",
        type=float,
        metavar="VALUE",
        help="locate the cluster odometer field using the reading on the dash",
    )
    args = ap.parse_args()

    if args.baseline:
        print("=== BASELINE: parked, dash 54%, Long Range ===\n")
        report(BASELINE)
        return 0

    if not args.logfile:
        ap.error("give a logfile, or --baseline")

    with open(args.logfile, encoding="utf-8", errors="replace") as fh:
        frames = extract_from_log(fh.read())

    if not frames:
        print("No Mode 22 frames found. Is the app connected and the car awake?", file=sys.stderr)
        return 1

    print(f"=== CAPTURE: {args.logfile} ===\n")
    if args.find_speed is not None:
        find_speed(frames, args.find_speed)
        return 0
    if args.find_odometer is not None:
        find_odometer(frames, args.find_odometer)
        return 0
    report(frames)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
