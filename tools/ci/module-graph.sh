#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Module boundaries of ADR 0009: nothing in the app process compiles against the worker or the
# hidden-API stubs (they reach the APK as a run-time dependency of :core:privilege only), and
# :core:parse stays a pure-JVM module with no project dependencies; ADR 0015 adds the engine modules.
set -euo pipefail
cd "$(dirname "$0")/../.."

status=0
tree() { ./gradlew -q "$1:dependencies" --configuration "$2"; }
# Gradle prints `project ':core:parse'` (quoted since Gradle 9) or `project :core:parse`.
has() { grep -qE "project '?$1'?( |\(|$)" <<< "$2"; }

forbid() { # <project> <configuration> <forbidden project path regex>...
  local project=$1 configuration=$2; shift 2
  local deps
  deps=$(tree "$project" "$configuration")
  for banned in "$@"; do
    if has "$banned" "$deps"; then
      echo "Boundary broken: $project $configuration depends on $banned"
      status=1
    fi
  done
}

require() { # <project> <configuration> <project path>: guards against a check that matches nothing
  if ! has "$3" "$(tree "$1" "$2")"; then
    echo "Expected $1 $2 to contain $3; the graph check cannot read the dependency report"
    status=1
  fi
}

require :app debugRuntimeClasspath :core:privilege-worker

forbid :app debugCompileClasspath :core:privilege-worker :core:hidden-api-stubs
forbid :core:privilege debugCompileClasspath :core:privilege-worker :core:hidden-api-stubs
forbid :core:privilege-worker debugRuntimeClasspath :core:hidden-api-stubs
forbid :core:parse runtimeClasspath ':core:[a-z-]+' ':feature:[a-z-]+' :app
# ADR 0015: profiles are pure JVM and depend on nothing but the parsers.
forbid :core:profile runtimeClasspath ':core:privilege[a-z-]*' ':core:doze[a-z-]*' ':core:data' ':feature:[a-z-]+' :app
require :core:profile runtimeClasspath :core:parse
# ADR 0015: the engine is pure JVM behind ports; only its Android adapters touch privilege or Android.
forbid :core:doze runtimeClasspath ':core:privilege[a-z-]*' ':core:doze-android' ':core:data' ':core:hidden-api-stubs' ':feature:[a-z-]+' :app
require :core:doze runtimeClasspath :core:profile
# The engine's Android side sits below the app and above the privilege layer, and sees no hidden API.
forbid :core:doze-android debugCompileClasspath :core:privilege-worker :core:hidden-api-stubs ':feature:[a-z-]+' :app
require :core:doze-android debugRuntimeClasspath :core:doze
forbid :core:privilege debugRuntimeClasspath ':core:doze[a-z-]*' ':core:data'
# Storage knows the engine's types and nothing of privilege or the adapters.
forbid :core:data debugRuntimeClasspath ':core:privilege[a-z-]*' ':core:doze-android' ':feature:[a-z-]+' :app
require :core:data debugRuntimeClasspath :core:doze

# Verdicts come from the records alone (SPEC 8.4): no engine, storage or privilege.
forbid :core:evidence runtimeClasspath ':core:privilege[a-z-]*' ':core:doze[a-z-]*' ':core:data' ':feature:[a-z-]+' :app
require :core:evidence runtimeClasspath :core:parse
# ADR 0026: the recommender sees sessions and profiles only; the adapters that build sessions live outside.
forbid :core:recommend runtimeClasspath ':core:privilege[a-z-]*' ':core:doze[a-z-]*' ':core:data' ':core:evidence' ':feature:[a-z-]+' :app
require :core:recommend runtimeClasspath :core:profile

if (( status == 0 )); then echo "Module boundaries hold."; fi
exit $status
