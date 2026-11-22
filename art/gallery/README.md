# CurseForge gallery (1920x1080)

- `mod-0*.png`, `plugin-0*.png`: drawn graphics (title cards and labeled explainers), made by `art/source/gallery.py`.
  The game pictures inside them are crops of the real screenshots below.
- `shots/`: real in-game screenshots taken automatically by the **Gallery screenshots** workflow
  (`.github/workflows/shots.yml` + the CI-only helper in `shots/`): Superflat (Classic Flat) world, noon, clear weather,
  Fancy graphics, 1920x1080, HUD hidden unless the shot is about the HUD. Every death is real game damage.
  Mod shots use the released NeoForge 1.21.1 jar; plugin shots use a vanilla-looking client on a Superflat Paper 1.21.1
  server with the released plugin + LogicLib Plugin. The recap screens were taken at GUI scale 4 and cropped (16:9) to
  the table.
