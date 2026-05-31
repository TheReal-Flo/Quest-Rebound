# Runtime Rebinding Design — Immutable Superset + Software Routing

## Problem

OpenXR does not allow hot-swapping interaction bindings at runtime. The binding
lifecycle is one-shot per session:

1. `xrCreateActionSet` / `xrCreateAction`
2. `xrSuggestInteractionProfileBindings`
3. `xrAttachSessionActionSets`

After attach:

- `xrSuggestInteractionProfileBindings` → `XR_ERROR_ACTIONSETS_ALREADY_ATTACHED`
- `xrAttachSessionActionSets` (again) → `XR_ERROR_ACTIONSETS_ALREADY_ATTACHED`
- Actions cannot be added to or rebound within an attached action set.

This is why the current `MCOpenXRReload.reloadXRBindings()` (re-running
`loadDefaultBindings()` + `loadActionHandles()` on the live session) cannot work:
the re-suggest and re-attach calls fail on the already-attached session.

The only spec-compliant ways to *change* what OpenXR reports are to destroy and
recreate the objects that hold the bindings (session + action sets, or the whole
instance) — i.e. a VR re-init with a visible blip. We want true hot-swap with no
restart.

## Key Insight

In Vivecraft's `MCVR`/`MCOpenXR`, the OpenXR binding layer's *only* job is to fill
two arrays on each `VRInputAction`:

```java
public final DigitalData[] digitalData = new DigitalData[ControllerType.values().length];
public final AnalogData[]  analogData  = new AnalogData[ControllerType.values().length];
```

The per-frame loop is:

```java
xrSyncActions(session, syncInfo);
this.inputActions.values().forEach(this::readNewData);
```

`readNewData` → `readBoolean` / `readFloat` / `readVecData` are the **only** places
that dereference an action's `XrAction handle` (via `xrGetActionStateBoolean` /
`Float` / `Vector2f`) and write into `digitalData` / `analogData`.

Everything downstream — `isButtonPressed()`, `isButtonChanged()`,
`getAxis1D/2D/3D()` — reads **only** from those two arrays and never touches
OpenXR.

> **Therefore: if we control what gets written into `digitalData` / `analogData`,
> we control all of Vivecraft's input without ever re-suggesting or re-attaching
> bindings.**

## Approach: Immutable Superset + Software Routing

1. **Bind a fixed superset once.** Create one OpenXR action per *physical
   component* per hand (right: a/b click, trigger value, grip value, thumbstick
   x/y, thumbstick click, thumbrest touch; left: x/y click, menu click, trigger,
   grip, thumbstick, …). Suggest + attach these **once**, never again. This action
   set is immutable for the life of the session — fully spec-compliant.

2. **Route in software.** Each frame, read the raw physical actions, then copy
   their state into the logical `VRInputAction`s' `digitalData` / `analogData`
   according to an in-memory mapping table (logical action → physical component).

3. **Rebinding == mutating the table.** No OpenXR calls, no session recreate, no
   restart. Instant hot-swap.

This is the same model Steam Input uses: a stable physical-input layer, with the
logical remap done above it.

## The raw action set is derived 1:1 from the defaults

The raw superset must be a faithful 1:1 representation of what Vivecraft binds
today — not a hand-authored list of every conceivable component. So **generate it
from `XRBindings.getBinding(profile)`**:

- For each supported profile, walk its default `(logicalAction → physicalPath)`
  pairs.
- Emit one raw action per distinct **`(physicalPath, openxrType)`** pair, where
  `openxrType` is the `VRInputAction.type` (`boolean` / `vector1` / `vector2`) of
  the logical action Vivecraft binds to that path. (The same physical path can be
  read as a click *or* a value depending on the bound action's type — e.g. a bare
  `.../input/trigger` resolves to a thresholded click for a `boolean` action and to
  `trigger/value` for a `vector1` action. Keying on `(path, type)` preserves that
  exactly.)
- Dedupe across logical actions that share the same `(path, type)`.

This makes the raw set inherently 1:1 with current behaviour and auto-answers the
touch question: a `/touch` raw action exists **iff** some default binding uses it.

Observed in OpenXR-1.21.5 defaults:

- **Quest/Pico** (`quest2Bindings`, the unified `oculus/touch` target, and the
  Odyssey→Quest2 mapping) use **no `/touch`** — only `/click`, `/value`, bare
  `/trigger` and `/squeeze`, and `/thumbstick` (+ `thumbstick/click`).
- The only capacitive touch in the entire default set is **Vive's
  `/user/hand/right/input/trackpad/touch`**. So: no touch actions for Quest/Pico;
  one touch raw action appears automatically if/when Vive support is added.

## Integration Points (against OpenXR-1.21.5 source)

### 1. Create the raw actions (before attach, in an always-active set)

Raw actions must exist before `xrAttachSessionActionSets` and live in an action
set that is both **attached** and **active during `xrSyncActions`**.

`updateActiveActionSets()` always includes `GLOBAL`, `MOD`, `MIXED_REALITY`,
`TECHNICAL`. Putting raws in `GLOBAL` or `MOD` gives us attach + active **for
free** — no redirect of the attach call or the active-set buffer needed.

Easiest registration seam: `populateInputActions()` already concatenates
`MOD.getHiddenKeyBindings()`. Registering raws as hidden keybindings (with an
`actionSetOverride` of `GLOBAL`/`MOD`) makes them first-class `VRInputAction`s, so
`loadActionHandles()` creates their `XrAction` handles automatically by iterating
`inputActions`.

### 2. Bind the raw actions

The existing `@Redirect` on `XRBindings.getBinding(headset)` (in `MCOpenXRMixin`)
is the natural hook. `loadDefaultBindings` sizes its suggested-binding buffer off
the returned list (`calloc(defaultBindings.length + 6)`), so returning the **raw**
`(rawActionName → physicalPath)` pairs sizes and binds them correctly.

Vivecraft's logical actions still receive handles (from `loadActionHandles`) but
are bound to nothing, so their native `xrGetActionState*` reports `isActive=false`
— which is fine, because we overwrite their data in step 3.

### 3. Reroute the read

`@Redirect` / `@Inject` around
`this.inputActions.values().forEach(this::readNewData)`:

- Read the raw actions natively first (real handles → their own `digitalData` /
  `analogData`).
- For each **logical** action, copy the mapped raw action's state across (with type
  conversion + gating, below). Skip the native `xrGetActionState*` call for logical
  actions entirely, keeping total OpenXR call count roughly unchanged (good for the
  Pico perf concern noted in the README).

## Gotchas (priority order)

1. **Context gating — the non-obvious killer.** Today, INGAME binds don't fire in a
   GUI because Vivecraft only includes the relevant action sets in `xrSyncActions`,
   so inactive actions report `isActive=false`. Once we route from an always-active
   raw set, that gating is gone. We **must replicate it in software**: when mapping
   into a logical action, check whether that action's `actionSet` is in the
   currently-active list (same logic `updateActiveActionSets()` builds from
   `mc.screen`, keyboard/radial visibility, `ingameBindingsInGui`, etc.) and force
   `isActive=false` if not. Miss this → movement binds fire inside menus.

2. **Type conversion.** boolean-logical ← float-physical (trigger) needs a threshold
   (Vivecraft uses `0.5` in `isButtonPressed`). vector2 (thumbstick) only sensibly
   maps to another stick. Define the legal mapping matrix in the UI.
   `digitalToAnalog` already handles bool→analog downstream; we mainly own
   analog→bool (threshold) and like→like.

3. **Handedness / `activeOrigin`.** `HandedKeyBinding` resolves which hand acted via
   `activeOrigin`. Raws are already per-hand (right.a vs left.x), so set the
   logical's `digitalData[hand].activeOrigin` from the corresponding raw. For handed
   actions, populate both hand indices from the matching per-hand raws.

4. **`isChanged` / deltas.** Recompute `isChanged` and `analogData.deltaX/deltaY`
   relative to the *logical* action's previous-frame value, not the raw's —
   otherwise edges get mangled when a binding changes mid-session.

5. **Asymmetric profiles.** Oculus Touch (Quest/Pico are unified onto it here):
   a/b right-only, x/y and menu left-only, `system` reserved on most runtimes. Build
   the raw action + binding list per profile (we already iterate
   `supportedHeadsets()`); never suggest a component a profile lacks.

6. **Grip/aim/haptics untouched.** The `+6` pose/haptic bindings in
   `loadDefaultBindings` aren't user-rebindable; keep Vivecraft's handling verbatim.

## Relevant source (verbatim references)

- `MCOpenXR.readBoolean/readFloat/readVecData` — the only OpenXR state reads; write
  into `digitalData[i]` / `analogData[i]` indexed by `ControllerType.ordinal()`.
- `MCOpenXR.loadDefaultBindings` — buffer sized `calloc(defaultBindings.length + 6)`;
  iterates `XRBindings.getBinding(headset)` pairs (`left`=action name via
  `getInputActionByName`, `right`=physical path); single `xrAttachSessionActionSets`.
- `MCOpenXR.updateActiveActionSets` — always adds `GLOBAL`, `MOD`, `MIXED_REALITY`,
  `TECHNICAL`; screen/keyboard/radial-conditional for the rest.
- `MCVR.populateInputActions` — builds `inputActions` from
  `mc.options.keyMappings` + `MOD.getHiddenKeyBindings()`.
- `VRInputAction` — `handle`, `type` (`boolean`/`vector1`/`vector2`/`vector3`),
  `actionSet`, `digitalData[]`, `analogData[]`; `isButtonPressed()` thresholds axes
  at `0.5`.

## Comparison: rejected alternative

**Session/instance recreate** (re-run Vivecraft's full VR teardown + init so the
existing `loadDefaultBindings` redirect feeds new bindings on a fresh attach).
Works and is spec-compliant, but produces a visible re-init blip and depends on
locating Vivecraft's teardown entry point. The superset approach achieves true
zero-blip hot-swap and is preferred.

## Suggested build order

1. Raw-action registration (hidden keybindings in `GLOBAL`/`MOD`), with the raw set
   **generated from `XRBindings.getBinding(profile)`** as distinct `(path, type)`
   pairs, + per-profile raw binding list returned from the `getBinding` redirect.
2. `readNewData` reroute with **context gating** baked in (the spine — everything
   hangs off this).
3. Type-conversion + handedness handling.
4. Wire the existing `DefaultBindingManager` mapping table to mutate live (drop the
   `reloadXRBindings()` re-attach entirely — it becomes a table swap).
5. UI: restrict the rebinding matrix to legal type combinations.
