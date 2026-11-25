"""Keep framework-free layers independent inside the single Android app module."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/java/dev/michelle/trails'
PACKAGE = 'dev.michelle.trails'
ALLOWED = {
    'domain': ('java', 'kotlin', 'kotlinx.coroutines', PACKAGE + '.domain'),
    'presentation': ('java', 'kotlin', 'kotlinx.coroutines', PACKAGE + '.domain', PACKAGE + '.presentation'),
}


def allowed(name, prefixes):
    if name.startswith('kotlinx.coroutines.android'):
        return False
    return any(name == prefix or name.startswith(prefix + '.') for prefix in prefixes)


errors = []
count = 0
for layer, prefixes in ALLOWED.items():
    for source in sorted((ROOT / layer).rglob('*.kt')):
        count += 1
        package = re.search(r'^package\s+([\w.]+)', source.read_text(), re.M)
        if package is None or not package.group(1).startswith(PACKAGE + '.' + layer):
            errors.append(f'{source.name}: unexpected package declaration')
        for imported in re.findall(r'^import\s+([\w.*]+)', source.read_text(), re.M):
            if not allowed(imported, prefixes):
                errors.append(f'{source.name}: {layer} depends on {imported}')
if errors:
    print('\n'.join(errors), file=sys.stderr)
    raise SystemExit(1)
assert count > 0, 'No source files found; check the source-root configuration.'
print(f'Architecture boundary check passed for {count} domain/presentation files.')
