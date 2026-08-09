# Development plan outline

This document outlines basic progress w.r.t. different modules/features.

## General

* [ ] Project structure (all projects are created)
* [ ] CI (Github actions?)
* [ ] Tests (unit-tests, integration tests, coverage measurements, etc.)
* [ ] Automated publishing (to where? Github maven packages? Maven central?)
* [ ] Nicer readme, docs

## `gradle-build-recorder`

* [x] Basic implementation
  * currently only saves file downloads and task execution events
  * also saves stdout/stderr messages
* [ ] `async-profiler` support
* [ ] Redactions for command-line args
* [ ] Automated record upload
* [ ] Complex cases for Gradle (cancel build, etc.)

## `core`

* [x] Basic implementation
  * models for file downloads and task execution
  * simple model for log files
  * very basic recorders
* [ ] ???

## `gradle-recorder-plugin`

* [ ] Basic implementation (with configuration cache support, with known limitations etc.)
* [ ] Automated publishing (gradle plugin portal?)

## `recorder-viewer`

Planned as a single-page web application. Should allow viewing build recordings:

* supplied as external files (`gradle-build-recorder` outputs);
* embedded as results of `gradle-build-recorder` runs

Should use Vue 3 + SVG for drawing timeline.

* [ ] Basic implementation
* [ ] Embedded into `gradle-build-recorder`
* [ ] Standalone version
* [ ] ???

## `gradle-build-recorder-daemon`

A (ktor-based) server for storing build results. Should be able to store build results sent from `gradle-build-recorder` and provide an interface/API for uploading custom files. Should save build info into a database.

* [ ] Basic implementaiton
* [ ] Database storage
* [ ] Automatic startup from `gradle-build-recorder`
* [ ] Automatic build record consumption from `gradle-build-recorder`
* [ ] Per-project trend analysis
* [ ] ???
