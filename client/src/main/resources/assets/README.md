# Asset Staging

This folder tree contains editable placeholders for the game's presentation assets. Replace the text descriptors with
real content when bespoke art or audio is ready.

```
assets/
  models/         # `.j3o` meshes or glTF exports for arena props, paddles, balls
  textures/       # Procedural gradients, normal maps, decal atlases
  particles/      # Sprite sheets used by the jME particle emitters (PNG recommended)
  fonts/          # Custom TTF/OTF fonts imported via JavaFX CSS @font-face
  audio/
    menu/         # Loopable ambience for the front-end
    match/        # In-game background loops and tension cues
    sfx/          # Impact, power-up, UI confirm sounds
    voice/        # Optional announcer or countdown voice-overs
  ui/icons/       # SVG/PNG icons for JavaFX controls and HUD glyphs
```

All files should be licensed for redistribution. During development you can drop placeholder WAV/OGG or PNG assets here and
trigger a hot reload from the presentation menu.
