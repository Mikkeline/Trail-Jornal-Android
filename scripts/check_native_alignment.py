"""Check 64-bit native ELF LOAD alignment in an APK/AAR; runtime and ZIP alignment are separate checks."""
from pathlib import Path
import argparse
import struct
import zipfile


def check_elf(data):
    if data[:4] != b'\x7fELF' or len(data) < 64 or data[4] != 2 or data[5] not in (1, 2):
        raise ValueError('Not a valid 64-bit ELF binary')
    order = '<' if data[5] == 1 else '>'
    offset = struct.unpack_from(order + 'Q', data, 32)[0]
    size, count = struct.unpack_from(order + 'HH', data, 54)
    if size < 56 or offset + size * count > len(data):
        raise ValueError('Invalid program-header table')
    load_segments = 0
    for index in range(count):
        header = offset + index * size
        if struct.unpack_from(order + 'I', data, header)[0] != 1:
            continue
        load_segments += 1
        file_offset, address = struct.unpack_from(order + 'QQ', data, header + 8)
        alignment = struct.unpack_from(order + 'Q', data, header + 48)[0]
        if alignment < 16384 or file_offset % 16384 != address % 16384:
            raise ValueError(f'LOAD segment is not 16-KB aligned (alignment={alignment})')
    if load_segments == 0:
        raise ValueError('No LOAD segments found')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--require-native', action='store_true')
    args = parser.parse_args()
    failures = []
    checked = 0
    with zipfile.ZipFile(args.archive) as archive:
        for name in archive.namelist():
            if not name.endswith('.so') or not any('/' + abi + '/' in '/' + name for abi in ('arm64-v8a', 'x86_64')):
                continue
            checked += 1
            try:
                check_elf(archive.read(name))
                print(f'PASS: {name}')
            except (ValueError, struct.error) as error:
                failures.append(f'{name}: {error}')
    if args.require_native and checked == 0:
        failures.append('Expected 64-bit native libraries were not found')
    if failures:
        raise SystemExit('\n'.join(failures))
    print(f'Checked {checked} 64-bit native libraries. Device testing and APK ZIP alignment are still required.')


if __name__ == '__main__':
    main()
