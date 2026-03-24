# T-022 — Fix "TARGET TARGET" Typo

**Status**: ready
**Phase**: 0
**Effort**: tiny (< 10 min)
**Branch**: `claude/T-022-fix-target-target-typo`
**Blocks**: —

---

## Goal
`fragment_session.xml` has `android:text="TARGET TARGET"` — clearly a copy-paste error.
Fix it to read `"TARGET TEMP"`.

---

## Read these files first
- `app/src/main/res/layout/fragment_session.xml` (line ~398)

## Change only these files
- `app/src/main/res/layout/fragment_session.xml`

---

## Steps

1. Find `android:text="TARGET TARGET"` and change it to `android:text="TARGET TEMP"`.
2. Run `./gradlew assembleDebug` — must pass.

---

## Done when
- [ ] No occurrence of `"TARGET TARGET"` in the layout
- [ ] `./gradlew assembleDebug` passes
