# RTL Layout

This directory is the active core implementation for Quartus.

- `cave/` contains the Cave core SystemVerilog currently consumed by the build.
- `cave/CaveGameConfig.sv` is the hand-maintained game table extracted from
  the legacy Chisel configuration. Add new Cave board/game constants there
  before wiring deeper hardware differences.
- `cave/AudioMixer.sv`, `cave/LERP.sv`, `cave/PISO.sv`, and the
  `cave/RegisterFile*.sv` helpers have been rewritten as hand-maintained
  SystemVerilog while preserving the generated module interfaces.
- `cave/AudioPipeline.sv` is the hand-maintained YMZ280B per-channel sample
  pipeline for ADPCM fetch, decode, interpolation, level, and pan.
- `cave/ChannelController.sv` is the hand-maintained YMZ280B channel scheduler
  that walks the eight channels, tracks sample/nibble state, feeds the audio
  pipeline, and accumulates the channel audio output.
- `cave/CavePageFlippers.sv` and `cave/RequestQueue*.sv` are hand-maintained
  framebuffer helpers. The page flipper is now instantiated directly, while the
  request queues still preserve the original generated module interfaces.
- `cave/CaveSyncReadMem.sv` is the shared synchronous-read memory used directly
  by cache, queue, and YMZ channel-state storage.
- `cave/CaveSyncQueue.sv` is the shared synchronous FIFO used directly by the
  sprite processor and burst DMA helpers.
- `cave/CaveSinglePortRam.sv` and `cave/CaveTrueDualPortRam.sv` are shared
  wrappers around the existing Arcadia VHDL RAM blocks used directly by the
  core RAM instances.
- `cave/CaveDualClockFIFO.sv` is the shared wrapper around the existing Arcadia
  VHDL dual-clock FIFO block.
- `cave/CaveTileRomClockCrossing.sv` is the hand-maintained tile-ROM
  clock-domain bridge used by the layer processors.
- `cave/CaveVideoTiming.sv` is the shared implementation behind the original
  and compatibility `VideoTiming*` wrappers.
- `cave/VideoSys.sv` is the hand-maintained video register front-end and
  original/compatibility timing selector.
- `cave/Cave.sv` is the hand-maintained core top-level integration shell that
  connects IOCTL, game config, memory, video, sound, GPU, and framebuffers.
- `cave/Main.sv` is the hand-maintained 68k board front-end for per-game CPU
  memory maps, inputs, IRQs, EEPROM serial pins, pause, and video register
  mirrors. Its decode lattice intentionally still follows the generated shape
  until each board map can be extracted separately.
- `cave/MazingerMainMap.sv` groups the Mazinger Z 68k address decoder, IRQ
  read data, boot watchdog shadow RAM, and read-data mux so that Mazinger board
  behavior can move out of the shared `Main.sv` integration file.
- `cave/CaveCpuWrappers.sv` contains the hand-maintained wrappers around the
  existing `fx68k` main CPU and `T80s` sound CPU cores.
- `cave/DDR.sv` is the hand-maintained burst bridge between the core's shared
  burst-memory port and the MiSTer DDR service interface.
- `cave/SDRAM.sv` is the hand-maintained SDRAM command sequencer for the
  core's 16-bit MiSTer-side SDRAM interface.
- `cave/EEPROM.sv` is the hand-maintained serial EEPROM front-end for the
  NVRAM memory port used by the main CPU input path.
- `cave/ColorMixer.sv` is the hand-maintained layer priority and palette
  address selector used by the GPU pixel path.
- `cave/GPU.sv` is the hand-maintained video-pipeline integration shell that
  wires sprites, layers, the color mixer, RGB expansion, and framebuffer output.
- `cave/CaveLayerProcessor.sv` is the shared hand-maintained tilemap renderer
  instantiated directly by `GPU.sv` for the three background layers.
- `cave/MemSys.sv` is the hand-maintained memory routing shell for ROM
  download, copy DMA, ROM/NVRAM caches, and DDR/SDRAM arbitration.
- `cave/SpriteDecoder.sv` is the hand-maintained tile-ROM-to-pixel decoder
  for 4bpp, 4bpp MSB, and 8bpp sprite rows.
- `cave/SpriteBlitter.sv` is the hand-maintained sprite pixel writer that
  applies sprite zoom/flip transforms and writes visible pixels to the line
  framebuffer.
- `cave/SpriteProcessor.sv` is the hand-maintained sprite-list scheduler that
  fetches sprite entries and tile-ROM bursts, then feeds the sprite decoder and
  blitter.
- `cave/SpriteFrameBuffer.sv` is the hand-maintained sprite framebuffer
  integration wrapper around the line buffer RAM, page flipper, DDR DMAs, write
  request queue, and DDR arbiter.
- `cave/SystemFrameBuffer.sv` is the hand-maintained HDMI-rotation framebuffer
  integration wrapper around the page flipper and cross-clock write request
  queue.
- `cave/CaveBurstBuffers.sv` contains the hand-maintained burst-width adapters
  for the DDR/SDRAM download paths.
- `cave/CaveBurstDMAs.sv` contains the hand-maintained burst DMA controllers.
- `cave/CaveBurstMemArbiters.sv` contains the hand-maintained priority
  arbiters for the shared burst memory ports.
- `cave/CaveAsyncMemArbiters.sv` and `cave/CaveDataFreezers.sv` contain the
  async memory arbiters and response-stretching helpers for CPU/audio clock
  crossings.
- `cave/CaveReadCache.sv` is the shared cache used directly by program ROM,
  sound ROM, and layer tile ROM reads.
- `cave/CaveNvramWriteBackCache.sv` is the hand-maintained two-way write-back
  cache used by the EEPROM/NVRAM memory path.
- `cave/CaveClockEnable.sv` and `cave/CaveOKIM6295.sv` are shared
  hand-maintained sound helpers used by the OKI/YM wrapper modules.
- `cave/CaveYMZ280BHelpers.sv` contains the hand-maintained YMZ280B ADPCM
  decoder and linear interpolator used by the audio pipeline.
- `cave/YMZ280B.sv` is the hand-maintained YMZ280B register front-end and
  channel-controller wrapper.
- `cave/Sound.sv` is the hand-maintained sound PCB integration wrapper for
  the Z80 sound CPU, OKI/YM sound chips, banking, ROM arbitration, and mixer.
- `cave/NMK112.sv` is the hand-maintained OKI sample ROM banking controller.
- `arcadia/` contains VHDL memory helpers used by the Cave HDL.
- `fx68k/`, `t80/`, `jt03/`, and `jt6295/` are third-party CPU and sound blocks.
- The PLL and reset wrappers live at the `rtl/` root.

The old Chisel source is kept under `../legacy/chisel/` as reference material
only. New core work should land in `rtl/` unless it is specifically updating
that legacy regeneration path.
