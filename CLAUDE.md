# Claude Code Configuration for eagleeye


## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.


## Available Skills

Skills are loaded from `.claude/skills/` (symlinked from claude-code-java).

To use a skill, load it first, then invoke with natural language:

### 1. Git Commit Messages
**Load**: `view .claude/skills/git-commit/SKILL.md`

**Use cases**:
- "Commit staged changes"
- "Create commit for bug fix #123"
- "Generate conventional commit message"

**Example**:
```
> view .claude/skills/git-commit/SKILL.md
> "Commit these changes"
→ fix(plugin-loader): prevent NPE when directory missing
```

### 2. Test Quality (JUnit 5 + AssertJ)
**Load**: `view .claude/skills/test-quality/SKILL.md`

**Use cases**:
- "Add tests for PluginManager.loadAll()"
- "Review existing tests in PluginLoaderTest"
- "Improve test coverage for lifecycle module"

**Example**:
```
> view .claude/skills/test-quality/SKILL.md
> "Add unit tests for ExtensionFactory with edge cases"
→ Generates JUnit 5 tests with AssertJ assertions
```

### 3. Issue Triage
**Load**: `view .claude/skills/issue-triage/SKILL.md`

**Use cases**:
- "Triage the last 10 issues"
- "Check recent bug reports"
- "Prioritize open feature requests"

**Example**:
```
> view .claude/skills/issue-triage/SKILL.md
> "Triage issues from eagleeye, last 15"
→ Categorizes, labels, suggests responses
```

## MCP Servers (Optional)

MCP servers enhance capabilities with structured, token-efficient operations:

| Server | Benefits |
|--------|----------|
| GitHub MCP | Issue management, PR creation |
| Filesystem MCP | Structured file tree navigation |
| Git MCP | Commit history, blame, log parsing |

To configure MCP servers, run from claude-code-java:
```bash
./scripts/configure-mcp.sh /path/to/this/project
```

See [MCP documentation](https://modelcontextprotocol.io/) for details.

## Common Workflows

### Daily Development Flow
```bash
# 1. Start session
claude code .

# 2. Work on feature/fix
# ... make code changes ...

# 3. Add tests (load test-quality skill)
> view .claude/skills/test-quality/SKILL.md
> "Add tests for new functionality in class X"

# 4. Commit (load git-commit skill)
> view .claude/skills/git-commit/SKILL.md
> "Commit staged changes"

# 5. Push and create PR
> "Push changes and create PR for issue #123"
```

### Weekly Maintenance
```bash
# Monday morning: Issue triage
claude code .

> view .claude/skills/issue-triage/SKILL.md
> "Triage the last 20 issues, categorize and prioritize"

# Review suggested actions
> "Apply labels and post responses as suggested"
```

### Code Review
```bash
# Review PR
> "Review PR #456 focusing on:
   - Test coverage (use test-quality skill)
   - Commit message quality (use git-commit skill)
   - Code patterns and best practices"
```

## Token Budget Guidelines

To optimize token usage:

1. **Load skills once per session** - Skills stay in context
2. **Batch operations** - Process multiple issues/tests together
3. **Use MCP when available** - More efficient than bash commands
4. **Targeted file reads** - Only read files you need

### Target Token Usage

| Task | Without Skills | With Skills | Savings |
|------|----------------|-------------|---------|
| Commit message | ~800 tokens | ~300 tokens | 62% |
| Add 3 tests | ~2000 tokens | ~800 tokens | 60% |
| Triage 10 issues | ~5000 tokens | ~2000 tokens | 60% |

## What to Avoid

1. **Don't reload skills repeatedly** - Load once per session
2. **Don't process issues one-by-one** - Batch them
3. **Don't over-engineer** - Use skills for appropriate tasks
4. **Don't ignore skill guidelines** - They're optimized for tokens

## Project-Specific Notes

### Build Commands
```bash
# Maven
mvn clean install
mvn test
mvn jacoco:report

# Check test coverage
open target/site/jacoco/index.html
```

### Testing Strategy
- Target: 80%+ coverage on core logic
- Focus: Business logic, not boilerplate
- Tools: JUnit 5, AssertJ, Mockito

### Commit Guidelines
- Follow Conventional Commits
- Reference issues: "Fixes #123"
- Keep subject under 50 chars

### Issue Management
- Label all new issues within 48h
- Respond to questions within 1 week
- Close stale (>90 days, no activity) issues

## Resources

- [claude-code-java](https://github.com/decebals/claude-code-java) - Skill repository
- [Claude Code Docs](https://code.claude.com/docs) - Official documentation
- [Conventional Commits](https://www.conventionalcommits.org/) - Commit format
- [AssertJ Docs](https://assertj.github.io/doc/) - Assertion library

## Tips & Tricks

### Quick skill loading
```bash
# Add to your shell alias
alias cc-commit='echo "view .claude/skills/git-commit/SKILL.md"'
alias cc-test='echo "view .claude/skills/test-quality/SKILL.md"'
alias cc-triage='echo "view .claude/skills/issue-triage/SKILL.md"'
```

### Session continuity
```bash
# Save context at end of session
> "Summarize what we worked on today for next session"

# Resume next day
> "Review yesterday's summary and continue"
```

### Measure your wins
```bash
# Track token usage
> /token usage

# Compare before/after adopting skills
# Document savings in team retrospectives
```

### Use JDK 25

> JDK 25 is the latest LTS release, and is recommended for eagleeye.

### Use Springboot 4.x.x

> Springboot 4.x.x is the latest LTS release, and is recommended for eagleeye.

---

**Last updated**: 2026-03-16
**claude-code-java version**: v0.1
