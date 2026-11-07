#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# SPEC 12.4 and 15: no `service call` transaction numbers anywhere, and no reflection outside the
# worker process (core/privilege-worker). Upstream's Java code still has both until its classes are
# deleted (SPEC 12.1); those uses are frozen in forbidden-calls-baseline.txt as "count<TAB>file<TAB>match".
# A use that is new, or more of an existing one, fails. --update rewrites the baseline; run it only
# when deleting upstream code shrank it.
set -euo pipefail
cd "$(dirname "$0")/../.."

baseline=tools/ci/forbidden-calls-baseline.txt
service_call='service call'
reflection='getDeclaredMethod|getDeclaredField|getDeclaredConstructor|setAccessible|Class\.forName|java\.lang\.reflect|kotlin\.reflect\.full|HiddenApiBypass|newProcess'

scan() {
  local roots=(app/src core feature testing)
  local existing=()
  for r in "${roots[@]}"; do [[ -d $r ]] && existing+=("$r"); done
  {
    grep -roE --include='*.java' --include='*.kt' "$service_call" "${existing[@]}" || true
    grep -roE --include='*.java' --include='*.kt' "$reflection" "${existing[@]}" | grep -v '^core/privilege-worker/' || true
  } | sed -E 's/:/\t/' | sort | uniq -c | sed -E 's/^ *([0-9]+) /\1\t/' | sort -k2
}

current=$(scan)

if [[ ${1:-} == --update ]]; then
  printf '%s\n' "$current" | sed '/^$/d' > "$baseline"
  echo "Wrote $baseline"
  exit 0
fi

declare -A allowed=()
while IFS=$'\t' read -r count file match; do
  [[ -n ${file:-} ]] && allowed["$file"$'\t'"$match"]=$count
done < "$baseline"

status=0
while IFS=$'\t' read -r count file match; do
  [[ -z ${file:-} ]] && continue
  key="$file"$'\t'"$match"
  limit=${allowed[$key]:-0}
  if (( count > limit )); then
    echo "Forbidden: '$match' in $file ($count, baseline $limit)"
    status=1
  fi
done <<< "$current"

if (( status == 0 )); then
  echo "No new service call or reflection use ($(printf '%s\n' "$current" | sed '/^$/d' | wc -l) upstream entries frozen in the baseline)."
fi
exit $status
