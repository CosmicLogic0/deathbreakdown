# CI helpers (copied from the LogicLib repos)
- `server-test.sh` (from logiclib): starts a server, waits for "Done (", stops it, fails if the expected line is missing or Death Breakdown / LogicLib logged an error.
- `get-paper.sh` (from logiclib-paper): downloads the newest Paper jar for a Minecraft version.
- `*.yml.ref`: LogicLib's original workflows, kept as the reference for this repo's workflows
  (server boot test, headless client boot test via headlesshq/mc-runtime-test, Paper boot on all 10 versions, base64 export of the tested jar).
