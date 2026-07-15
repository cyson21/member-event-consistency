from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

import generate_report


class GenerateReportTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.repo_root = Path(self.temporary_directory.name)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def write_xml(self, relative_path: str, content: str) -> Path:
        path = self.repo_root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def build_report(self, paths: list[Path]) -> dict[str, object]:
        return generate_report.build_report(
            paths,
            repo_root=self.repo_root,
            project="member-event-consistency",
            git_commit="abc123",
            generated_at_utc="2026-07-15T00:00:00Z",
            scope="test",
        )

    def test_aggregates_totals_and_sorts_suites(self) -> None:
        beta = self.write_xml(
            "backend/target/surefire-reports/TEST-beta.xml",
            '<testsuite name="Beta" tests="3" failures="1" errors="0" skipped="0"/>',
        )
        alpha = self.write_xml(
            "backend/target/surefire-reports/TEST-alpha.xml",
            '<testsuite name="Alpha" tests="2" failures="0" errors="0" skipped="1"/>',
        )

        report = self.build_report([beta, alpha])

        self.assertEqual([suite["name"] for suite in report["suites"]], ["Alpha", "Beta"])
        self.assertEqual(
            report["totals"],
            {"tests": 5, "failures": 1, "errors": 0, "skipped": 1, "passed": 3},
        )

    def test_calculates_passed_after_all_non_passed_statuses(self) -> None:
        report_path = self.write_xml(
            "target/failsafe-reports/TEST-statuses.xml",
            '<testsuite name="Statuses" tests="7" failures="1" errors="2" skipped="1"/>',
        )

        suite = self.build_report([report_path])["suites"][0]

        self.assertEqual(suite["passed"], 3)

    def test_derives_missing_counts_from_testcases(self) -> None:
        report_path = self.write_xml(
            "target/surefire-reports/TEST-derived.xml",
            """<testsuite name="Derived">
                <testcase name="pass"/>
                <testcase name="failure"><failure/></testcase>
                <testcase name="error"><error/></testcase>
                <testcase name="skip"><skipped/></testcase>
            </testsuite>""",
        )

        suite = self.build_report([report_path])["suites"][0]

        self.assertEqual(
            {field: suite[field] for field in ("tests", "failures", "errors", "skipped", "passed")},
            {"tests": 4, "failures": 1, "errors": 1, "skipped": 1, "passed": 1},
        )

    def test_parses_namespaced_testsuites_document(self) -> None:
        report_path = self.write_xml(
            "target/failsafe-reports/TEST-group.xml",
            """<testsuites xmlns="urn:test">
                <testsuite name="Second" tests="2" failures="0" errors="0" skipped="0"/>
                <testsuite name="First" tests="1" failures="0" errors="0" skipped="0"/>
            </testsuites>""",
        )

        report = self.build_report([report_path])

        self.assertEqual([suite["name"] for suite in report["suites"]], ["First", "Second"])

    def test_nested_aggregate_suite_does_not_double_count_leaf_suites(self) -> None:
        report_path = self.write_xml(
            "target/surefire-reports/TEST-nested.xml",
            """<testsuites>
                <testsuite name="Aggregate" tests="2" failures="0" errors="0" skipped="0">
                    <testsuite name="First" tests="1" failures="0" errors="0" skipped="0">
                        <testcase name="first"/>
                    </testsuite>
                    <testsuite name="Second" tests="1" failures="0" errors="0" skipped="0">
                        <testcase name="second"/>
                    </testsuite>
                </testsuite>
            </testsuites>""",
        )

        report = self.build_report([report_path])

        self.assertEqual([suite["name"] for suite in report["suites"]], ["First", "Second"])
        self.assertEqual(report["totals"]["tests"], 2)

    def test_fails_when_declared_counts_disagree_with_testcases(self) -> None:
        report_path = self.write_xml(
            "target/surefire-reports/TEST-mismatch.xml",
            """<testsuite name="Mismatch" tests="2" failures="0" errors="0" skipped="0">
                <testcase name="only"/>
            </testsuite>""",
        )

        with self.assertRaisesRegex(generate_report.EvidenceError, "counts disagree"):
            self.build_report([report_path])

    def test_fails_when_testcase_has_conflicting_statuses(self) -> None:
        report_path = self.write_xml(
            "target/surefire-reports/TEST-conflict.xml",
            """<testsuite name="Conflict">
                <testcase name="ambiguous"><failure/><skipped/></testcase>
            </testsuite>""",
        )

        with self.assertRaisesRegex(generate_report.EvidenceError, "conflicting statuses"):
            self.build_report([report_path])

    def test_rejects_doctype_declaration(self) -> None:
        report_path = self.write_xml(
            "target/surefire-reports/TEST-doctype.xml",
            '<!DOCTYPE testsuite [<!ENTITY value "x">]><testsuite name="Unsafe" tests="0"/>',
        )

        with self.assertRaisesRegex(generate_report.EvidenceError, "DOCTYPE"):
            self.build_report([report_path])

    def test_discovers_surefire_and_failsafe_without_duplicate_paths(self) -> None:
        surefire = self.write_xml(
            "target/surefire-reports/TEST-unit.xml",
            '<testsuite name="Unit" tests="1" failures="0" errors="0" skipped="0"/>',
        )
        self.write_xml(
            "target/failsafe-reports/TEST-integration.xml",
            '<testsuite name="Integration" tests="1" failures="0" errors="0" skipped="0"/>',
        )

        paths = generate_report.discover_xml_files(
            ["target/*-reports/TEST-*.xml", str(surefire)],
            self.repo_root,
        )

        self.assertEqual(len(paths), 2)
        self.assertEqual(
            [path.name for path in paths],
            ["TEST-integration.xml", "TEST-unit.xml"],
        )

    def test_fails_when_no_xml_input_exists(self) -> None:
        with self.assertRaisesRegex(generate_report.EvidenceError, "no XML reports found"):
            generate_report.discover_xml_files(
                ["target/*-reports/TEST-*.xml"],
                self.repo_root,
            )

    def test_fails_on_malformed_xml(self) -> None:
        report_path = self.write_xml(
            "target/surefire-reports/TEST-broken.xml",
            '<testsuite name="Broken">',
        )

        with self.assertRaisesRegex(generate_report.EvidenceError, "cannot parse XML report"):
            self.build_report([report_path])

    def test_fails_when_status_counts_exceed_tests(self) -> None:
        report_path = self.write_xml(
            "target/surefire-reports/TEST-invalid.xml",
            '<testsuite name="Invalid" tests="1" failures="1" errors="1" skipped="0"/>',
        )

        with self.assertRaisesRegex(generate_report.EvidenceError, "exceeds tests"):
            self.build_report([report_path])

    def test_fails_on_unsupported_xml_root(self) -> None:
        report_path = self.write_xml(
            "target/surefire-reports/TEST-invalid-root.xml",
            "<report/>",
        )

        with self.assertRaisesRegex(generate_report.EvidenceError, "unsupported root"):
            self.build_report([report_path])

    def test_fails_when_report_contains_zero_testcases(self) -> None:
        report_path = self.write_xml(
            "target/surefire-reports/TEST-empty.xml",
            '<testsuite name="Empty" tests="0" failures="0" errors="0" skipped="0"/>',
        )

        with self.assertRaisesRegex(generate_report.EvidenceError, "no test cases"):
            self.build_report([report_path])

    def test_rejects_metadata_control_characters(self) -> None:
        report_path = self.write_xml(
            "target/surefire-reports/TEST-unit.xml",
            '<testsuite name="Unit" tests="1" failures="0" errors="0" skipped="0"/>',
        )

        with self.assertRaisesRegex(generate_report.EvidenceError, "control characters"):
            generate_report.build_report(
                [report_path],
                repo_root=self.repo_root,
                project="member-event-consistency\nspoofed",
                git_commit="abc123",
                generated_at_utc="2026-07-15T00:00:00Z",
                scope="test",
            )

    def test_rejects_output_outside_repo_root(self) -> None:
        outside = self.repo_root.parent / "outside.json"

        with self.assertRaisesRegex(generate_report.EvidenceError, "outside repo root"):
            generate_report.resolve_output_path(str(outside), self.repo_root)

    def test_cli_writes_stable_schema_and_key_order(self) -> None:
        self.write_xml(
            "target/surefire-reports/TEST-unit.xml",
            '<testsuite name="Unit" tests="2" failures="0" errors="0" skipped="0"/>',
        )
        output_path = self.repo_root / "target/portfolio-evidence/report.json"
        arguments = [
            "--repo-root",
            str(self.repo_root),
            "--input",
            "target/*-reports/TEST-*.xml",
            "--output",
            str(output_path),
            "--project",
            "member-event-consistency",
            "--scope",
            "unit-regression",
            "--git-commit",
            "abc123",
            "--generated-at-utc",
            "2026-07-15T00:00:00Z",
        ]

        self.assertEqual(generate_report.main(arguments), 0)
        first_output = output_path.read_text(encoding="utf-8")
        self.assertEqual(generate_report.main(arguments), 0)
        second_output = output_path.read_text(encoding="utf-8")
        parsed = json.loads(first_output)

        self.assertEqual(first_output, second_output)
        self.assertEqual(
            list(parsed),
            [
                "schema_version",
                "project",
                "git_commit",
                "generated_at_utc",
                "scope",
                "totals",
                "suites",
            ],
        )
        self.assertEqual(parsed["schema_version"], 1)

    def test_current_utc_timestamp_uses_source_date_epoch(self) -> None:
        self.assertEqual(
            generate_report.current_utc_timestamp({"SOURCE_DATE_EPOCH": "0"}),
            "1970-01-01T00:00:00Z",
        )

    def test_current_utc_timestamp_rejects_negative_epoch(self) -> None:
        with self.assertRaisesRegex(generate_report.EvidenceError, "SOURCE_DATE_EPOCH"):
            generate_report.current_utc_timestamp({"SOURCE_DATE_EPOCH": "-1"})


if __name__ == "__main__":
    unittest.main()
