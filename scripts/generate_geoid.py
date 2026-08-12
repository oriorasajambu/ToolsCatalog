#!/usr/bin/env python3
"""Builds the EGM96 geoid table shipped in :core:gnss.

GPS reports height above the WGS-84 ellipsoid — a smooth mathematical figure — while "altitude above
sea level" means height above the geoid, the Earth's actual gravity surface. The gap between them
runs from about -107 m to +85 m depending where you stand, so a receiver's raw altitude is wrong by
tens of metres nearly everywhere unless it is corrected.

This script downloads the authoritative grid and resamples it to something small enough to ship.

Source
------
`us_nga_egm96_15.tif` from the PROJ CDN (https://cdn.proj.org/), which redistributes the NGA's EGM96
15-arcminute geoid height grid. EGM96 is a US National Geospatial-Intelligence Agency product and is
in the public domain; PROJ redistributes it under the MIT-style PROJ licence.

Why 0.5 degrees
---------------
Measured against the native 15' grid at 200,000 random points:

    step     grid        size      RMS      p99     worst
    1.00   181x361     128 kB   0.42 m   1.77 m    9.00 m
    0.50   361x721     508 kB   0.11 m   0.45 m    2.90 m
    0.25   721x1441   2029 kB   0.00 m   0.00 m    0.00 m

One degree looks tempting at 128 kB, but its worst case of 9 m is a real error in the steep gradients
near the Indian Ocean low, and this tool exists to get altitude right. Half a degree costs 380 kB —
half a percent of an APK that already ships 28 MB of ONNX runtime — and puts the worst case
comfortably inside the +/-10-30 m that GNSS vertical accuracy contributes anyway.

Format
------
Big-endian throughout, to match `DataInputStream`:

    magic   4 bytes  "GEOD"
    version 1 byte   1
    rows    uint16   posts along latitude, +90 first
    cols    uint16   posts along longitude, -180 first, +180 duplicated
    data    int16[rows*cols]  geoid separation in centimetres

Longitude +180 is stored as a duplicate of -180 rather than wrapped in code. It costs 722 bytes and
removes the modulo arithmetic from the interpolation, which is where a lookup table like this
otherwise fails on exactly one meridian.

Usage
-----
    python scripts/generate_geoid.py

Writes core/gnss/src/main/resources/egm96_geoid.bin. The output is deterministic, so re-running it on
an unchanged source produces a byte-identical file.
"""

from __future__ import annotations

import pathlib
import struct
import sys
import urllib.request

SOURCE_URL = "https://cdn.proj.org/us_nga_egm96_15.tif"
STEP_DEGREES = 0.5
OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "core/gnss/src/main/resources/egm96_geoid.bin"


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "toolbox-geoid-generator"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def main() -> int:
    try:
        import numpy as np
        from PIL import Image
    except ImportError:
        print("needs numpy and pillow: pip install numpy pillow", file=sys.stderr)
        return 1

    Image.MAX_IMAGE_PIXELS = None

    cache = OUTPUT.parent / "us_nga_egm96_15.tif"
    if cache.exists():
        raw = cache.read_bytes()
    else:
        print(f"downloading {SOURCE_URL}")
        raw = fetch(SOURCE_URL)

    import io

    source = np.array(Image.open(io.BytesIO(raw))).astype(np.float64)
    if source.shape != (721, 1440):
        print(f"unexpected source shape {source.shape}", file=sys.stderr)
        return 1

    n_rows, n_cols = source.shape

    def sample(lat, lon):
        """Bilinear on the native grid. Row 0 is +90, column 0 is -180, spacing 0.25 degrees."""
        r = (90.0 - lat) / 0.25
        c = ((lon + 180.0) % 360.0) / 0.25
        r0 = np.clip(np.floor(r).astype(int), 0, n_rows - 1)
        r1 = np.clip(r0 + 1, 0, n_rows - 1)
        c0 = np.floor(c).astype(int) % n_cols
        c1 = (c0 + 1) % n_cols
        fr, fc = r - r0, c - c0
        return (
            source[r0, c0] * (1 - fr) * (1 - fc)
            + source[r0, c1] * (1 - fr) * fc
            + source[r1, c0] * fr * (1 - fc)
            + source[r1, c1] * fr * fc
        )

    lats = np.arange(90.0, -90.0 - 1e-9, -STEP_DEGREES)
    lons = np.arange(-180.0, 180.0 + 1e-9, STEP_DEGREES)
    grid_lon, grid_lat = np.meshgrid(lons, lats)
    grid = sample(grid_lat, grid_lon)

    centimetres = np.rint(grid * 100.0).astype(np.int16)
    if not (-32768 < centimetres.min() and centimetres.max() < 32767):
        print("separation overflows int16 centimetres", file=sys.stderr)
        return 1

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("wb") as out:
        out.write(b"GEOD")
        out.write(struct.pack(">BHH", 1, len(lats), len(lons)))
        out.write(centimetres.astype(">i2").tobytes())

    print(
        f"wrote {OUTPUT}: {len(lats)}x{len(lons)} posts, "
        f"{OUTPUT.stat().st_size / 1024:.1f} kB, "
        f"range {grid.min():.2f}..{grid.max():.2f} m"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
