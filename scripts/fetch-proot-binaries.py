#!/usr/bin/env python3
"""Fetch, verify, and install the pinned Termux PRoot runtime binaries."""

import dataclasses
import hashlib
import io
import json
import os
from pathlib import Path
import tarfile
import urllib.error
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_ROOT = ROOT / "app" / "src" / "main" / "jniLibs"
CACHE_ROOT = ROOT / "build" / "proot-binaries" / "debs"
STAMP_PATH = OUTPUT_ROOT / ".proot-runtime.json"
BASE_URLS = (
    "https://packages.termux.dev/apt/termux-main",
    "https://grimler.se/termux/termux-main",
)
ABI_MAP = {
    "aarch64": "arm64-v8a",
    "x86_64": "x86_64",
}
TERMUX_PREFIX = "./data/data/com.termux/files/usr"


@dataclasses.dataclass(frozen=True)
class Pin:
    package: str
    version: str
    arch: str
    archive: str
    sha256: str
    member: str
    output: str


PINS = (
    Pin(
        "proot", "5.1.107.102", "aarch64",
        "pool/main/p/proot/proot_5.1.107.102_aarch64.deb",
        "10a394b585c2fc2b2c8502544f4e5f1be581dc8bfff79e8ba62f16207423e030",
        TERMUX_PREFIX + "/bin/proot", "libproot.so",
    ),
    Pin(
        "proot", "5.1.107.102", "x86_64",
        "pool/main/p/proot/proot_5.1.107.102_x86_64.deb",
        "2497064226c15971be8131c7facead94a06c225347796326a383c832dca57758",
        TERMUX_PREFIX + "/bin/proot", "libproot.so",
    ),
    Pin(
        "proot", "5.1.107.102", "aarch64",
        "pool/main/p/proot/proot_5.1.107.102_aarch64.deb",
        "10a394b585c2fc2b2c8502544f4e5f1be581dc8bfff79e8ba62f16207423e030",
        TERMUX_PREFIX + "/libexec/proot/loader", "libproot-loader.so",
    ),
    Pin(
        "proot", "5.1.107.102", "x86_64",
        "pool/main/p/proot/proot_5.1.107.102_x86_64.deb",
        "2497064226c15971be8131c7facead94a06c225347796326a383c832dca57758",
        TERMUX_PREFIX + "/libexec/proot/loader", "libproot-loader.so",
    ),
    Pin(
        "proot", "5.1.107.102", "aarch64",
        "pool/main/p/proot/proot_5.1.107.102_aarch64.deb",
        "10a394b585c2fc2b2c8502544f4e5f1be581dc8bfff79e8ba62f16207423e030",
        TERMUX_PREFIX + "/libexec/proot/loader32", "libproot-loader32.so",
    ),
    Pin(
        "proot", "5.1.107.102", "x86_64",
        "pool/main/p/proot/proot_5.1.107.102_x86_64.deb",
        "2497064226c15971be8131c7facead94a06c225347796326a383c832dca57758",
        TERMUX_PREFIX + "/libexec/proot/loader32", "libproot-loader32.so",
    ),
    Pin(
        "libtalloc", "2.5.0", "aarch64",
        "pool/main/libt/libtalloc/libtalloc_2.5.0_aarch64.deb",
        "556591f43bb773ad8777e1a29522640866a55f95dab71914418b94a8c58ad5a7",
        TERMUX_PREFIX + "/lib/libtalloc.so", "libtalloc.so",
    ),
    Pin(
        "libtalloc", "2.5.0", "x86_64",
        "pool/main/libt/libtalloc/libtalloc_2.5.0_x86_64.deb",
        "b8c6d95f20075dc1f9ec6573575b2444e8d526e48e0d8d6d5cf4e071e6e06530",
        TERMUX_PREFIX + "/lib/libtalloc.so", "libtalloc.so",
    ),
    Pin(
        "libandroid-shmem", "0.7", "aarch64",
        "pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb",
        "0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6",
        TERMUX_PREFIX + "/lib/libandroid-shmem.so", "libandroid-shmem.so",
    ),
    Pin(
        "libandroid-shmem", "0.7", "x86_64",
        "pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_x86_64.deb",
        "ffa9e4c87467b158b148d0ff92dda796aa038276c2075af3269cdcdb06f25797",
        TERMUX_PREFIX + "/lib/libandroid-shmem.so", "libandroid-shmem.so",
    ),
)


class FetchError(RuntimeError):
    pass


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def output_path(pin):
    return OUTPUT_ROOT / ABI_MAP[pin.arch] / pin.output


def expected_stamp():
    return [dataclasses.asdict(pin) for pin in PINS]


def outputs_are_current():
    try:
        stamp = json.loads(STAMP_PATH.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return False
    if stamp.get("pins") != expected_stamp():
        return False
    recorded_outputs = stamp.get("outputs", {})
    for pin in PINS:
        destination = output_path(pin)
        relative = str(destination.relative_to(OUTPUT_ROOT))
        try:
            if sha256_file(destination) != recorded_outputs[relative]:
                return False
        except (KeyError, OSError):
            return False
    print("PRoot runtime binaries are up to date.")
    return True


def download(pin):
    CACHE_ROOT.mkdir(parents=True, exist_ok=True)
    cached = CACHE_ROOT / Path(pin.archive).name
    if cached.exists():
        data = cached.read_bytes()
        actual = sha256(data)
        if actual != pin.sha256:
            raise FetchError(
                "SHA256 mismatch for cached {}: expected {}, got {}".format(
                    cached, pin.sha256, actual))
        print("Using verified cache: {}".format(cached))
        return data

    errors = []
    for base_url in BASE_URLS:
        url = base_url + "/" + pin.archive
        print("Downloading: {}".format(url))
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                data = response.read()
        except (OSError, urllib.error.URLError) as error:
            errors.append("{}: {}".format(url, error))
            print("Download failed: {}".format(errors[-1]))
            continue
        actual = sha256(data)
        if actual != pin.sha256:
            raise FetchError(
                "SHA256 mismatch for {}: expected {}, got {}".format(
                    url, pin.sha256, actual))
        print("Verified SHA256: {} ({})".format(actual, Path(pin.archive).name))
        temporary = cached.with_suffix(cached.suffix + ".tmp")
        temporary.write_bytes(data)
        os.replace(temporary, cached)
        return data
    raise FetchError("Unable to download {}\n{}".format(
        pin.archive, "\n".join(errors)))


def ar_members(data):
    if not data.startswith(b"!<arch>\n"):
        raise FetchError("Not an ar archive")
    members = {}
    offset = 8
    while offset < len(data):
        header = data[offset:offset + 60]
        if len(header) != 60 or header[58:60] != b"`\n":
            raise FetchError("Malformed ar member header at byte {}".format(offset))
        name = header[:16].decode("ascii").strip()
        if name.endswith("/"):
            name = name[:-1]
        try:
            size = int(header[48:58].decode("ascii").strip())
        except ValueError as error:
            raise FetchError("Malformed ar member size") from error
        offset += 60
        end = offset + size
        if end > len(data):
            raise FetchError("Truncated ar member: {}".format(name))
        members[name] = data[offset:end]
        offset = end + (size % 2)
    return members


def extract_member(deb_data, pin):
    members = ar_members(deb_data)
    data_names = [name for name in members if name.startswith("data.tar.")]
    if len(data_names) != 1:
        raise FetchError("Expected one data.tar.* member in {}".format(pin.archive))
    with tarfile.open(fileobj=io.BytesIO(members[data_names[0]]), mode="r:*") as archive:
        try:
            member = archive.getmember(pin.member)
        except KeyError as error:
            raise FetchError("Missing {} in {}".format(pin.member, pin.archive)) from error
        extracted = archive.extractfile(member)
        if extracted is None:
            raise FetchError("Unable to extract {} from {}".format(
                pin.member, pin.archive))
        data = extracted.read()
    if not data.startswith(b"\x7fELF"):
        raise FetchError("Extracted member is not an ELF file: {}".format(pin.member))
    print("Extracted: {} from {}".format(pin.member, Path(pin.archive).name))
    return data


def use_packaged_library_names(data, pin):
    if pin.output != "libproot.so":
        return data
    # This pinned PRoot declares libtalloc.so.2, but Android extracts the packaged
    # unversioned jniLibs name. Keep the requested library name and update only the
    # DT_NEEDED string, using its existing 15-byte string-table slot.
    original = b"libtalloc.so.2\0"
    replacement = b"libtalloc.so\0\0\0"
    if len(original) != len(replacement) or data.count(original) != 1:
        raise FetchError("Unexpected libtalloc dependency in {}".format(pin.archive))
    print("Updating PRoot DT_NEEDED: libtalloc.so.2 -> libtalloc.so")
    return data.replace(original, replacement, 1)


def install(data, pin):
    destination = output_path(pin)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".tmp")
    temporary.write_bytes(data)
    temporary.chmod(0o755)
    os.replace(temporary, destination)
    print("Installed: {}".format(destination))
    return sha256(data)


def write_stamp(output_hashes):
    OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)
    stamp = {"pins": expected_stamp(), "outputs": output_hashes}
    temporary = STAMP_PATH.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(stamp, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.replace(temporary, STAMP_PATH)


def main():
    if outputs_are_current():
        return 0
    output_hashes = {}
    for pin in PINS:
        deb_data = download(pin)
        data = extract_member(deb_data, pin)
        data = use_packaged_library_names(data, pin)
        digest = install(data, pin)
        relative = str(output_path(pin).relative_to(OUTPUT_ROOT))
        output_hashes[relative] = digest
    write_stamp(output_hashes)
    print("Installed {} pinned files.".format(len(PINS)))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except FetchError as error:
        print("ERROR: {}".format(error))
        raise SystemExit(1)
