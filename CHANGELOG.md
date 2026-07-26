# Changelog

All notable changes to this project are documented in this file.

## [1.0.0] - 2026-07-26

### Added

1. Full app with desktop lightweight Tauri UI
   - Delivered a complete OpenTron application experience with a lightweight desktop shell powered by Tauri.
   - Integrated core frontend and backend workflows into a desktop-first runtime focused on low overhead and fast startup.
   - Enabled practical local operation with a compact native packaging approach suitable for daily use.

2. REST API external pipeline support (this change)
   - Added comprehensive OpenAPI specifications for backend REST endpoints in both JSON and YAML formats.
   - Introduced automated GitHub Actions workflow to regenerate and commit API specs whenever backend API code changes.
   - Improved interoperability for external CI/CD and orchestration pipelines by providing machine-readable API contracts.
