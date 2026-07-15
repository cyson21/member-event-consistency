#!/usr/bin/env python3
"""Build deterministic portfolio evidence from Maven XML test reports."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import glob
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from typing import Iterable, Sequence
import xml.etree.ElementTree as ET


SCHEMA_VERSION = 1
COUNT_FIELDS = ("tests", "failures", "errors", "skipped")
TESTCASE_STATUSES = frozenset(("failure", "error", "skipped"))


class EvidenceError(ValueError):
    """Raised when test evidence cannot be generated safely."""


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _source_path(path: Path, repo_root: Path) -> str:
    try:
        return path.resolve().relative_to(repo_root.resolve()).as_posix()
    except ValueError as exc:
        raise EvidenceError(f"report is outside repo root: {path}") from exc


def discover_xml_files(inputs: Sequence[str], repo_root: Path) -> list[Path]:
    """Resolve files, directories, and glob patterns to unique XML paths."""
    discovered: dict[Path, None] = {}

    for input_value in inputs:
        expanded = os.path.expanduser(input_value)
        pattern = expanded if os.path.isabs(expanded) else str(repo_root / expanded)

        if glob.has_magic(pattern):
            candidates = [Path(match) for match in glob.glob(pattern, recursive=True)]
        else:
            candidate = Path(pattern)
            if not candidate.exists():
                raise EvidenceError(f"input path does not exist: {input_value}")
            candidates = [candidate]

        for candidate in candidates:
            if candidate.is_dir():
                xml_paths: Iterable[Path] = candidate.rglob("*.xml")
            elif candidate.is_file() and candidate.suffix.lower() == ".xml":
                xml_paths = (candidate,)
            else:
                continue

            for xml_path in xml_paths:
                discovered[xml_path.resolve()] = None

    if not discovered:
        joined_inputs = ", ".join(inputs)
        raise EvidenceError(f"no XML reports found for input: {joined_inputs}")

    return sorted(discovered, key=lambda path: _source_path(path, repo_root))


def _derived_counts(element: ET.Element) -> dict[str, int]:
    counts = {field: 0 for field in COUNT_FIELDS}
    testcases = [child for child in element.iter() if _local_name(child.tag) == "testcase"]
    counts["tests"] = len(testcases)

    for testcase in testcases:
        statuses = {
            _local_name(child.tag)
            for child in testcase
            if _local_name(child.tag) in TESTCASE_STATUSES
        }
        if len(statuses) > 1:
            testcase_name = testcase.get("name") or "<unnamed>"
            raise EvidenceError(
                f"testcase {testcase_name!r} has conflicting statuses: {sorted(statuses)}"
            )
        if "error" in statuses:
            counts["errors"] += 1
        elif "failure" in statuses:
            counts["failures"] += 1
        elif "skipped" in statuses:
            counts["skipped"] += 1

    return counts


def _parse_count(element: ET.Element, field: str, fallback: int, context: str) -> int:
    raw_value = element.get(field)
    if raw_value is None:
        return fallback

    try:
        value = int(raw_value)
    except ValueError as exc:
        raise EvidenceError(f"{context}: {field} must be an integer, got {raw_value!r}") from exc

    if value < 0:
        raise EvidenceError(f"{context}: {field} must not be negative")
    return value


def _parse_suite(element: ET.Element, source: str) -> dict[str, object]:
    name = (element.get("name") or "").strip()
    if not name:
        raise EvidenceError(f"{source}: testsuite is missing a name")

    context = f"{source} ({name})"
    derived = _derived_counts(element)
    counts = {
        field: _parse_count(element, field, derived[field], context)
        for field in COUNT_FIELDS
    }

    if derived["tests"] > 0:
        mismatches = [
            f"{field} declared={counts[field]} derived={derived[field]}"
            for field in COUNT_FIELDS
            if counts[field] != derived[field]
        ]
        if mismatches:
            raise EvidenceError(f"{context}: XML counts disagree with testcases: {', '.join(mismatches)}")

    non_passed = counts["failures"] + counts["errors"] + counts["skipped"]
    if non_passed > counts["tests"]:
        raise EvidenceError(
            f"{context}: failures + errors + skipped exceeds tests"
        )

    return {
        "name": name,
        "source": source,
        "tests": counts["tests"],
        "failures": counts["failures"],
        "errors": counts["errors"],
        "skipped": counts["skipped"],
        "passed": counts["tests"] - non_passed,
    }


def parse_xml_report(path: Path, repo_root: Path) -> list[dict[str, object]]:
    source = _source_path(path, repo_root)
    try:
        raw_xml = path.read_bytes()
        if b"<!DOCTYPE" in raw_xml.upper():
            raise EvidenceError(f"{source}: DOCTYPE declarations are not allowed")
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exc:
        raise EvidenceError(f"cannot parse XML report {source}: {exc}") from exc

    root_name = _local_name(root.tag)
    if root_name == "testsuite":
        suite_elements = [root]
    elif root_name == "testsuites":
        all_suite_elements = [
            element for element in root.iter() if _local_name(element.tag) == "testsuite"
        ]
        suite_elements = [
            element
            for element in all_suite_elements
            if not any(_local_name(child.tag) == "testsuite" for child in element)
        ]
    else:
        raise EvidenceError(f"{source}: unsupported root element {root_name!r}")

    if not suite_elements:
        raise EvidenceError(f"{source}: no testsuite elements found")

    return [_parse_suite(element, source) for element in suite_elements]


def _validate_metadata(name: str, value: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise EvidenceError(f"{name} must not be empty")
    if any(ord(character) < 32 or ord(character) == 127 for character in normalized):
        raise EvidenceError(f"{name} must not contain control characters")
    return normalized


def current_utc_timestamp(environ: dict[str, str] | None = None) -> str:
    effective_environ = os.environ if environ is None else environ
    source_date_epoch = effective_environ.get("SOURCE_DATE_EPOCH", "").strip()
    if source_date_epoch:
        try:
            timestamp = int(source_date_epoch)
            if timestamp < 0:
                raise ValueError("negative epoch")
            current = datetime.fromtimestamp(timestamp, timezone.utc)
        except (ValueError, OverflowError, OSError) as exc:
            raise EvidenceError(
                f"invalid SOURCE_DATE_EPOCH value: {source_date_epoch!r}"
            ) from exc
    else:
        current = datetime.now(timezone.utc)
    return current.replace(microsecond=0).isoformat().replace("+00:00", "Z")


def validate_utc_timestamp(value: str) -> str:
    try:
        datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")
    except ValueError as exc:
        raise EvidenceError(
            "generated_at_utc must use UTC format YYYY-MM-DDTHH:MM:SSZ"
        ) from exc
    return value


def resolve_git_commit(repo_root: Path) -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=repo_root,
            check=True,
            capture_output=True,
            text=True,
            timeout=5,
        )
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
        raise EvidenceError("cannot resolve git commit; pass --git-commit explicitly") from exc
    return _validate_metadata("git_commit", result.stdout)


def build_report(
    xml_paths: Sequence[Path],
    *,
    repo_root: Path,
    project: str,
    git_commit: str,
    generated_at_utc: str,
    scope: str,
) -> dict[str, object]:
    suites = [
        suite
        for path in xml_paths
        for suite in parse_xml_report(path, repo_root)
    ]
    suites.sort(key=lambda suite: (str(suite["name"]), str(suite["source"])))

    totals = {
        field: sum(int(suite[field]) for suite in suites)
        for field in ("tests", "failures", "errors", "skipped", "passed")
    }
    if totals["tests"] == 0:
        raise EvidenceError("report contains no test cases")

    return {
        "schema_version": SCHEMA_VERSION,
        "project": _validate_metadata("project", project),
        "git_commit": _validate_metadata("git_commit", git_commit),
        "generated_at_utc": validate_utc_timestamp(generated_at_utc),
        "scope": _validate_metadata("scope", scope),
        "totals": totals,
        "suites": suites,
    }


def write_json_atomic(report: dict[str, object], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    file_descriptor, temporary_name = tempfile.mkstemp(
        dir=output_path.parent,
        prefix=f".{output_path.name}.",
        suffix=".tmp",
        text=True,
    )
    temporary_path = Path(temporary_name)

    try:
        with os.fdopen(file_descriptor, "w", encoding="utf-8") as output_file:
            json.dump(report, output_file, ensure_ascii=False, indent=2)
            output_file.write("\n")
            output_file.flush()
            os.fsync(output_file.fileno())
        os.replace(temporary_path, output_path)
    except BaseException:
        temporary_path.unlink(missing_ok=True)
        raise


def resolve_output_path(output: str, repo_root: Path) -> Path:
    output_path = Path(output)
    if not output_path.is_absolute():
        output_path = repo_root / output_path
    try:
        output_path.resolve().relative_to(repo_root.resolve())
    except ValueError as exc:
        raise EvidenceError(f"output is outside repo root: {output}") from exc
    return output_path


def create_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Generate stable JSON evidence from Maven Surefire/Failsafe XML."
    )
    parser.add_argument(
        "--input",
        action="append",
        required=True,
        help="XML file, directory, or glob pattern; repeat for multiple inputs",
    )
    parser.add_argument("--output", required=True, help="destination JSON path")
    parser.add_argument("--project", required=True, help="project identifier")
    parser.add_argument("--scope", required=True, help="test evidence scope")
    parser.add_argument(
        "--git-commit",
        help="commit represented by the report; defaults to git rev-parse HEAD",
    )
    parser.add_argument(
        "--generated-at-utc",
        help="fixed generation time for reproducible runs (YYYY-MM-DDTHH:MM:SSZ)",
    )
    parser.add_argument(
        "--repo-root",
        default=".",
        help="repository root used to resolve inputs and stable source paths",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = create_argument_parser().parse_args(argv)
    repo_root = Path(args.repo_root).resolve()

    try:
        xml_paths = discover_xml_files(args.input, repo_root)
        report = build_report(
            xml_paths,
            repo_root=repo_root,
            project=args.project,
            git_commit=args.git_commit or resolve_git_commit(repo_root),
            generated_at_utc=args.generated_at_utc or current_utc_timestamp(),
            scope=args.scope,
        )
        output_path = resolve_output_path(args.output, repo_root)
        write_json_atomic(report, output_path)
    except (EvidenceError, OSError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    totals = report["totals"]
    print(
        f"wrote {output_path} "
        f"({totals['tests']} tests, {totals['passed']} passed, "
        f"{totals['failures']} failures, {totals['errors']} errors, "
        f"{totals['skipped']} skipped)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
