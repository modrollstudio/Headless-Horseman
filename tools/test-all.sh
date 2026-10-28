#!/usr/bin/env bash
# Runs the full verification suite: ./gradlew clean check, then the GameTest suites on both loaders,
# with and without Checks. Writes a short pass/fail summary to build/test-summary.txt; full gradle
# output for each step stays in build/test-all/<step>.log.
#
# Usage: tools/test-all.sh [--neoforge-only|--fabric-only] [--quick]
#   --neoforge-only  skip the Fabric GameTest steps
#   --fabric-only    skip the NeoForge GameTest steps
#   --quick          run only `./gradlew clean check`, skip every GameTest step
#
# Exit code is non-zero if any step failed.

set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

log_dir="build/test-all"
summary_file="build/test-summary.txt"

neoforge_only=0
fabric_only=0
quick=0
for arg in "$@"; do
    case "$arg" in
        --neoforge-only) neoforge_only=1 ;;
        --fabric-only) fabric_only=1 ;;
        --quick) quick=1 ;;
        *)
            echo "tools/test-all.sh: unknown option '$arg'" >&2
            exit 2
            ;;
    esac
done
if [[ "$neoforge_only" -eq 1 && "$fabric_only" -eq 1 ]]; then
    echo "tools/test-all.sh: --neoforge-only and --fabric-only are mutually exclusive" >&2
    exit 2
fi

mkdir -p "$log_dir"

fmt_duration() {
    local secs=$1 h m s
    h=$((secs / 3600))
    m=$(((secs % 3600) / 60))
    s=$((secs % 60))
    if [[ $h -gt 0 ]]; then
        printf '%dh%02dm%02ds' "$h" "$m" "$s"
    else
        printf '%dm%02ds' "$m" "$s"
    fi
}

# Parses one step's test output. Modes:
#   unit <dir>...        sums JUnit XML under each <dir>/TEST-*.xml (Gradle's `test` task reports)
#   neoforge-log <file>   greps a raw console log for vanilla GameTestServer's summary lines
#   fabric-xml <file>     parses the JUnit-like XML fabric-api writes for `runGametest`
# Prints "total<TAB>passed<TAB>requiredFailed<TAB>optionalFailed" on the first line, then one
# "name<TAB>firstErrorLine" line per failure (required failures first, optional ones prefixed
# "(optional) ").
parse_report() {
    python3 - "$@" <<'PYEOF'
import glob
import re
import sys
from xml.etree import ElementTree as ET


def first_line(text):
    if not text:
        return ""
    return text.strip().splitlines()[0].strip()


mode = sys.argv[1]
failures = []  # (name, message, optional)

if mode == "unit":
    total = failed = skipped = 0
    for pattern in sys.argv[2:]:
        for path in glob.glob(pattern):
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError:
                continue
            total += int(root.get("tests", 0) or 0)
            skipped += int(root.get("skipped", 0) or 0)
            for tc in root.findall("testcase"):
                err = tc.find("failure")
                if err is None:
                    err = tc.find("error")
                if err is None:
                    continue
                failed += 1
                name = "{}.{}".format(tc.get("classname", ""), tc.get("name", ""))
                msg = err.get("message") or err.text or ""
                failures.append((name, first_line(msg), False))
    passed = total - failed - skipped
    print("{}\t{}\t{}\t0".format(total, passed, failed))

elif mode == "fabric-xml":
    total = req_failed = opt_failed = 0
    try:
        root = ET.parse(sys.argv[2]).getroot()
    except Exception:
        root = None
    if root is not None:
        for tc in root.iter("testcase"):
            total += 1
            name = tc.get("name", "")
            failure = tc.find("failure")
            optional_fail = tc.find("skipped")
            if failure is not None:
                req_failed += 1
                failures.append((name, first_line(failure.get("message") or ""), False))
            elif optional_fail is not None:
                opt_failed += 1
                failures.append((name, first_line(optional_fail.get("message") or ""), True))
    passed = total - req_failed - opt_failed
    print("{}\t{}\t{}\t{}".format(total, passed, req_failed, opt_failed))

elif mode == "neoforge-log":
    text = open(sys.argv[2], errors="replace").read()

    def find_int(pattern):
        m = re.search(pattern, text)
        return int(m.group(1)) if m else 0

    total = find_int(r"(\d+)\s+GAME TESTS COMPLETE")
    req_failed = find_int(r"(\d+)\s+required tests failed")
    opt_failed = find_int(r"(\d+)\s+optional tests failed")
    passed = total - req_failed - opt_failed
    print("{}\t{}\t{}\t{}".format(total, passed, req_failed, opt_failed))

    for line in text.splitlines():
        if "failed at" not in line:
            continue
        if "(optional)" in line:
            m = re.search(r"\(optional\)\s+([\w.$]+) failed at [^.]+\.\s*(.*)$", line)
            if m:
                failures.append((m.group(1), first_line(m.group(2)), True))
        else:
            m = re.search(r"([\w.$]+) failed at [^!]+!\s*(.*)$", line)
            if m:
                failures.append((m.group(1), first_line(m.group(2)), False))
else:
    print("0\t0\t0\t0")

for name, msg, optional in failures:
    prefix = "(optional) " if optional else ""
    print("{}{}\t{}".format(prefix, name, msg))
PYEOF
}

# Pulls the first line Gradle prints after "* What went wrong:", for steps that failed before
# producing any test report at all (compile errors, Spotless violations, a crashed JVM).
generic_failure_reason() {
    awk '/^\* What went wrong:/{flag=1; next} flag && NF {print; exit}' "$1"
}

step_names=() step_results=() step_times=() step_test_lines=() step_failure_blocks=()
overall_exit=0

# Runs one step, parses its report, and records a summary row.
# run_step <label> <log-file> <report-mode> <report-arg...> -- <gradle-args...>
run_step() {
    local label=$1 log=$2 mode=$3
    shift 3
    local -a report_args=()
    while [[ "$1" != "--" ]]; do
        report_args+=("$1")
        shift
    done
    shift # drop the --

    echo "==> $label"
    local start end elapsed result
    start=$(date +%s)
    ./gradlew "$@" --stacktrace > "$log" 2>&1
    local gradle_exit=$?
    end=$(date +%s)
    elapsed=$(fmt_duration $((end - start)))

    local report
    report=$(parse_report "$mode" "${report_args[@]}")
    local counts
    counts=$(echo "$report" | head -1)
    local total passed req_failed opt_failed
    IFS=$'\t' read -r total passed req_failed opt_failed <<< "$counts"
    local failure_lines
    failure_lines=$(echo "$report" | tail -n +2)

    if [[ $gradle_exit -eq 0 ]]; then
        result="PASS"
    else
        result="FAIL"
        overall_exit=1
        if [[ -z "$failure_lines" ]]; then
            local reason
            reason=$(generic_failure_reason "$log")
            failure_lines=$(printf '%s\t%s' "$label (build failure)" "${reason:-see $log}")
        fi
    fi

    local test_line
    if [[ "$total" -eq 0 && -z "$failure_lines" && $gradle_exit -eq 0 ]]; then
        test_line="no test report found"
    else
        test_line="$total tests, $req_failed failed"
        [[ "$opt_failed" -gt 0 ]] && test_line="$test_line, $opt_failed optional failed"
    fi

    step_names+=("$label")
    step_results+=("$result")
    step_times+=("$elapsed")
    step_test_lines+=("$test_line")
    step_failure_blocks+=("$failure_lines")

    echo "    $result  $test_line  ($elapsed)"
}

run_start=$(date +%s)

run_step "clean check" "$log_dir/clean-check.log" unit "build/test-results/test/TEST-*.xml" "*/build/test-results/test/TEST-*.xml" -- clean check

if [[ "$quick" -eq 0 ]]; then
    if [[ "$fabric_only" -eq 0 ]]; then
        run_step "neoforge (with Checks)" "$log_dir/neoforge-with-checks.log" neoforge-log \
            "$log_dir/neoforge-with-checks.log" -- :neoforge:runGameTestServer
        run_step "neoforge (without Checks)" "$log_dir/neoforge-without-checks.log" neoforge-log \
            "$log_dir/neoforge-without-checks.log" -- :neoforge:runGameTestServer -PwithoutChecks
    fi
    if [[ "$neoforge_only" -eq 0 ]]; then
        run_step "fabric (with Checks)" "$log_dir/fabric-with-checks.log" fabric-xml \
            "fabric/build/junit.xml" -- :fabric:runGametest
        run_step "fabric (without Checks)" "$log_dir/fabric-without-checks.log" fabric-xml \
            "fabric/build/junit.xml" -- :fabric:runGametest -PwithoutChecks
    fi
fi

run_end=$(date +%s)

{
    echo "Critfall: Headless Horseman — test run summary"
    echo "$(date -d "@$run_start" '+%Y-%m-%d %H:%M:%S') -> $(date -d "@$run_end" '+%H:%M:%S') (total $(fmt_duration $((run_end - run_start))))"
    echo
    printf '%-28s %-6s %-32s %s\n' "STEP" "RESULT" "TESTS" "TIME"
    for i in "${!step_names[@]}"; do
        printf '%-28s %-6s %-32s %s\n' "${step_names[$i]}" "${step_results[$i]}" "${step_test_lines[$i]}" "${step_times[$i]}"
    done

    any_failures=0
    for i in "${!step_names[@]}"; do
        [[ -n "${step_failure_blocks[$i]}" ]] && any_failures=1
    done
    if [[ "$any_failures" -eq 1 ]]; then
        echo
        echo "Failures:"
        for i in "${!step_names[@]}"; do
            [[ -z "${step_failure_blocks[$i]}" ]] && continue
            echo "  ${step_names[$i]}:"
            while IFS=$'\t' read -r name msg; do
                [[ -z "$name" ]] && continue
                echo "    - $name: $msg"
            done <<< "${step_failure_blocks[$i]}"
        done
    fi

    echo
    if [[ "$overall_exit" -eq 0 ]]; then
        echo "Overall: PASS"
    else
        echo "Overall: FAIL"
    fi
    echo "Full logs: $log_dir/"
} > "$summary_file"

cat "$summary_file"

exit $overall_exit
