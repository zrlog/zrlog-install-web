#!/usr/bin/env python3
"""Exercise release staging and inspect the complete bundle without uploading."""

import hashlib
import io
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def main():
    model = ET.parse(ROOT / "pom.xml").getroot()
    publisher = next(plugin for plugin in model.findall("m:build/m:plugins/m:plugin", NS)
                     if plugin.findtext("m:artifactId", namespaces=NS) == "central-publishing-maven-plugin")
    if publisher.findtext("m:version", namespaces=NS) != "0.12.0":
        raise AssertionError("Bundle check requires verified Central Publishing 0.12.0 skipUpload support")
    artifact = model.findtext("m:artifactId", namespaces=NS)
    version = model.findtext("m:version", namespaces=NS).removesuffix("-SNAPSHOT") + "-bundle-check"
    with tempfile.TemporaryDirectory(prefix="zrlog-install-release-bundle-") as temporary:
        directory = Path(temporary)
        project = directory / "project"
        project.mkdir()
        for filename in filter(None, subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")):
            source = ROOT / filename
            if source.is_file():
                destination = project / filename
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)
        settings = directory / "settings.xml"
        settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"/>', encoding="utf-8")
        command = [str(project / "mvnw"), "-B", "-ntp", "-s", str(settings), "-gs", str(settings),
                   f"-Dmaven.repo.local={directory / 'repository'}"]
        subprocess.run(command + ["org.codehaus.mojo:versions-maven-plugin:2.21.0:set",
                                  f"-DnewVersion={version}", "-DgenerateBackupPoms=false",
                                  "-DupdateBuildOutputTimestamp=false"], cwd=project, check=True)
        subprocess.run(command + ["-PnodeBuild", "clean", "deploy", "-DskipTests", "-Dgpg.skip=true",
                                  "-DskipUpload=true", "-DignorePublishedComponents=false",
                                  "-DcentralBaseUrl=http://127.0.0.1:9"], cwd=project, check=True)
        bundle = project / "target/central-publishing/central-bundle.zip"
        with zipfile.ZipFile(bundle) as archive:
            names = {name for name in archive.namelist() if not name.endswith("/")}
            prefix = f"com/hibegin/{artifact}/{version}/{artifact}-{version}"
            required = {prefix + suffix for suffix in (".pom", ".jar", "-sources.jar", "-javadoc.jar")}
            algorithms = ("md5", "sha1", "sha256", "sha512")
            expected = required | {name + "." + algorithm for name in required for algorithm in algorithms}
            if not required <= names:
                raise AssertionError(f"Missing release files: {required - names}")
            for name in names:
                parts = PurePosixPath(name).parts
                if len(parts) != 5 or parts[:4] != ("com", "hibegin", artifact, version):
                    raise AssertionError(f"Unexpected bundle path: {name}")
                if name not in expected:
                    raise AssertionError(f"Unexpected bundle file: {name}")
            for name in required:
                for algorithm in algorithms:
                    if name + "." + algorithm not in names and algorithm not in ("md5", "sha1"):
                        continue
                    checksum = archive.read(name + "." + algorithm).decode().strip()
                    if checksum != hashlib.new(algorithm, archive.read(name)).hexdigest():
                        raise AssertionError(f"Checksum mismatch: {name}")
            with zipfile.ZipFile(io.BytesIO(archive.read(prefix + "-javadoc.jar"))) as javadoc:
                if "index.html" not in javadoc.namelist():
                    raise AssertionError("Javadoc index is missing")
        print("PASS: POM, JAR, sources, nonempty Javadocs and checksums; no repository metadata")
        print("PASS: original checkout unchanged; no credentials, signature, upload, tag or release ref")


if __name__ == "__main__":
    main()
