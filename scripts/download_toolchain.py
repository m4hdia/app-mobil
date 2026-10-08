"""Resume official Windows build-tool downloads inside .tools; no global changes."""
import concurrent.futures
import hashlib
import pathlib
import sys
import subprocess
import re
import time
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1] / '.tools'
SOURCES = {
    'gradle-full': ('https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip', 'sha256', 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6'),
    'jdk-lite': ('https://download.bell-sw.com/java/17.0.20+10/bellsoft-jdk17.0.20+10-windows-amd64-lite.zip', 'sha1', 'f5f55178432f30b334215a5e7cfeabf373f2e0a1'),
    'sdk-full': ('https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip', None, None),
}
CHUNK = 65536
LEGACY = 1048576

def download(name, source):
    url, algorithm, checksum = source
    root = ROOT / name
    root.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={'Range': 'bytes=0-0', 'Accept-Encoding': 'identity'})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=25) as response:
                if response.status != 206: raise RuntimeError('Server must support range requests')
                size = int(response.headers['Content-Range'].split('/')[-1])
                final_url = response.url
            break
        except Exception:
            if attempt == 4: raise
            time.sleep(1)
    print(f'{name}: {size // 1024 // 1024} MB', flush=True)
    count = (size + CHUNK - 1) // CHUNK
    def segment(i):
        start, end = i * CHUNK, min((i + 1) * CHUNK, size) - 1
        old = root / f'{start // LEGACY:04}.part'
        if old.exists() and old.stat().st_size == min(LEGACY, size - start // LEGACY * LEGACY): return
        target = root / f'small-{i:05}.part'
        if target.exists() and target.stat().st_size == end - start + 1: return
        partial = target.with_suffix('.partial')
        for attempt in range(2):
            try:
                offset = partial.stat().st_size if partial.exists() else 0
                if offset == end - start + 1:
                    partial.replace(target)
                    return
                resume = start + offset
                # curl's Windows TLS backend has been more reliable on slow connections.
                # Keep a hard wall-clock deadline and preserve received bytes between attempts.
                headers = target.with_suffix('.headers')
                incoming = target.with_suffix('.download')
                transfer = subprocess.run(['curl.exe', '-sS', '-L', '--fail', '--connect-timeout', '10', '--max-time', '35',
                    '--range', f'{resume}-{end}', '-D', str(headers), '-o', str(incoming), final_url],
                    stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True, timeout=40)
                header_text = headers.read_text(errors='replace') if headers.exists() else ''
                ranges = re.findall(r'(?im)^content-range:\s*bytes\s+(\d+)-(\d+)/(\d+)', header_text)
                if not ranges or tuple(map(int, ranges[-1])) != (resume, end, size): raise RuntimeError(transfer.stderr.strip()[-180:] or 'Invalid range response')
                if not incoming.exists() or incoming.stat().st_size > end - resume + 1: raise RuntimeError('Invalid segment length')
                with partial.open('ab') as out: out.write(incoming.read_bytes())
                if partial.stat().st_size != end - start + 1: raise RuntimeError('Incomplete segment')
                partial.replace(target)
                return
            except Exception:
                if attempt == 1: raise
                time.sleep(1)
    remaining = list(range(count))
    completed = 0
    for retry_round in range(3):
        failed = []
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            pending = {pool.submit(segment, i): i for i in remaining}
            for future in concurrent.futures.as_completed(pending):
                try:
                    future.result()
                    completed += 1
                    if completed % 64 == 0: print(f'{name}: {completed}/{count} segments', flush=True)
                except Exception as error:
                    failed.append(pending[future])
                    print(f'{name}: segment {pending[future]} deferred ({error})', flush=True)
        if not failed: break
        remaining = failed
        print(f'{name}: retrying {len(failed)} incomplete segments', flush=True)
    if failed: raise RuntimeError(f'{len(failed)} segments remain incomplete; rerun to resume')
    archive = ROOT / f'{name}.zip'
    with archive.open('wb') as output:
        for i in range(count):
            start = i * CHUNK
            old = root / f'{start // LEGACY:04}.part'
            if old.exists() and old.stat().st_size == min(LEGACY, size - start // LEGACY * LEGACY):
                with old.open('rb') as source_file:
                    source_file.seek(start % LEGACY)
                    output.write(source_file.read(min(CHUNK, size - start)))
            else: output.write((root / f'small-{i:05}.part').read_bytes())
    if algorithm:
        with archive.open('rb') as archive_file:
            if hashlib.file_digest(archive_file, algorithm).hexdigest() != checksum: raise RuntimeError('Vendor checksum mismatch')
    with zipfile.ZipFile(archive) as package:
        if package.testzip(): raise RuntimeError('Corrupt ZIP')
        package.extractall(ROOT / ('sdk' if name == 'sdk-full' else name + '-extracted'))
    print(f'{name}: verified and extracted', flush=True)

if __name__ == '__main__':
    failed = False
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        tasks = {pool.submit(download, name, source): name for name, source in SOURCES.items() if len(sys.argv) == 1 or name in sys.argv[1:]}
        for future in concurrent.futures.as_completed(tasks):
            try: future.result()
            except Exception as error:
                failed = True
                print(f'{tasks[future]} FAILED: {error}', flush=True)
    sys.exit(1 if failed else 0)
