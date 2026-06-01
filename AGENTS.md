- Planning-only: when planning architecture or dependency choices, perform fresh
web research before deciding. Check official docs, GitHub repository status, recent releases,
deprecation/archive notices, license changes, security advisories, and credible alternatives. Record
the verification date and rationale in the plan.

- when prompt for a feature request ask the user if frontend or cli integration is also needed
- Kotlin style:
	- For invalid caller input, prefer explicit `require(...)` checks near the start of the function.
	- Do not hide invalid input by silently normalizing or ignoring it unless lenient behavior is intentionally required.
