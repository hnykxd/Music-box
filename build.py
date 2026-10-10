#!/usr/bin/env python3
"""Build the unobfuscated Minecraft 26.3 mod with JDK 25, without Gradle.

Usage: python build.py --java-home /path/to/jdk25 --minecraft-home /path/to/.minecraft
Minecraft 26.3 must have been installed by the launcher. Dependencies are cached
under .tools; this script never modifies the Minecraft installation.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
CACHE = ROOT / '.tools' / 'build-deps'
BUILD = ROOT / 'build'

def fetch(url, dest, sha1=None):
    if not dest.exists():
        dest.parent.mkdir(parents=True, exist_ok=True)
        print('Downloading', dest.name, flush=True)
        with urllib.request.urlopen(url, timeout=90) as source:
            data = source.read()
        if sha1 and hashlib.sha1(data).hexdigest() != sha1:
            raise RuntimeError('Checksum mismatch: ' + dest.name)
        dest.write_bytes(data)
    return dest

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--minecraft-home', default=str(Path(os.environ.get('APPDATA', Path.home())) / '.minecraft'))
    args = parser.parse_args()
    java_home = Path(args.java_home) if args.java_home else None
    if java_home is None and os.name == 'nt':
        runtimes = Path(os.environ['LOCALAPPDATA']) / 'Packages' / 'Microsoft.4297127D64EC6_8wekyb3d8bbwe' / 'LocalCache' / 'Local' / 'runtime'
        candidates = list(runtimes.glob('java-runtime-epsilon/**/javac.exe'))
        if candidates:
            java_home = candidates[0].parent.parent
    javac = str(java_home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')) if java_home else shutil.which('javac')
    if not javac:
        parser.error('Install JDK 25 and provide --java-home or JAVA_HOME')
    mc = Path(args.minecraft_home)
    version = mc / 'versions' / '26.3'
    manifest = json.loads((version / '26.3.json').read_text(encoding='utf-8'))
    client = version / '26.3.jar'
    if not client.exists():
        parser.error('Install Minecraft 26.3 first')
    cp = [client]
    for lib in manifest['libraries']:
        artifact = lib.get('downloads', {}).get('artifact')
        if not artifact or 'natives-' in artifact['path']:
            continue
        local = mc / 'libraries' / artifact['path']
        cp.append(local if local.exists() else fetch(artifact['url'], CACHE / artifact['path'], artifact.get('sha1')))
    for name, url in {
        'fabric-loader-0.19.5.jar': 'https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar',
        'sponge-mixin-0.17.4+mixin.0.8.7.jar': 'https://maven.fabricmc.net/net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar',
        'fabric-api-0.161.0+26.3.jar': 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.161.0+26.3/fabric-api-0.161.0+26.3.jar',
    }.items():
        jar = fetch(url, CACHE / name)
        cp.append(jar)
        if name.startswith('fabric-api-'):
            with zipfile.ZipFile(jar) as archive:
                for entry in archive.namelist():
                    if entry.endswith('.jar'):
                        nested = CACHE / 'fabric-api' / Path(entry).name
                        nested.parent.mkdir(parents=True, exist_ok=True)
                        nested.write_bytes(archive.read(entry))
                        cp.append(nested)
    classes = BUILD / 'classes' / 'direct'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    sources = sorted((ROOT / 'src' / 'main' / 'java').rglob('*.java')) + sorted((ROOT / 'src' / 'client' / 'java').rglob('*.java'))
    if not sources:
        raise RuntimeError('No Java sources found')
    command = ['--release', '25', '-encoding', 'UTF-8', '-proc:none', '-classpath', os.pathsep.join(map(str, cp)), '-d', str(classes)] + list(map(str, sources))
    argfile = BUILD / 'javac.args'
    argfile.write_text('\n'.join('"' + arg.replace('\\', '/').replace('"', '\\"') + '"' for arg in command), encoding='utf-8')
    subprocess.run([javac, '@' + str(argfile)], check=True)
    output = BUILD / 'libs' / 'musicbox-1.1.0.jar'
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(classes.rglob('*')):
            if path.is_file():
                archive.write(path, path.relative_to(classes).as_posix())
        for folder in [ROOT / 'src' / 'main' / 'resources', ROOT / 'src' / 'client' / 'resources']:
            for path in sorted(folder.rglob('*')):
                if path.is_file():
                    data = path.read_bytes()
                    if path.name == 'fabric.mod.json':
                        data = data.replace(b'${version}', b'1.1.0')
                    archive.writestr(path.relative_to(folder).as_posix(), data)
    print('Built', output)

if __name__ == '__main__':
    main()
