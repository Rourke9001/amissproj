# Branch-protection rulesets

`protect-main.json` locks `main` down to the intended flow: changes arrive only
via pull request, the CI `build` check must be green (and the branch up to
date), and force-pushes/deletion are blocked. Review count is 0 because this is
a solo repo — GitHub does not let you approve your own PR.

**Not yet applied**: branch protection on a *private* repository requires
GitHub Pro (HTTP 403 "Upgrade to GitHub Pro or make this repository public").
Once the repo is public (the plan, when it's showcase-ready) — or on a Pro
plan — apply it with:

```powershell
gh api repos/Rourke9001/amissproj/rulesets -X POST --input .github/rulesets/protect-main.json
```

To update later: `gh api repos/Rourke9001/amissproj/rulesets` lists rulesets
(note the `id`), then `-X PUT repos/.../rulesets/<id>` with the edited file.
