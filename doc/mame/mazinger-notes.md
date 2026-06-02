# Mazinger Z Reference Notes

Sources:
- MAME `src/mame/atlus/cave.cpp`, captured in `doc/mame/cave.cpp`
- MAME `src/mame/atlus/cave.h`, captured in `doc/mame/cave.h`
- Upstream MiSTer branch `upstream/mazinger`, commit `fb0eb71`

## Hardware

MAME lists Mazinger Z as Banpresto BP943A hardware:

- MC68000 main CPU
- Z80 audio CPU
- YM2203
- OKIM6295
- 93C46 EEPROM
- Cave tilemap chip 038
- Cave sprite chip 013

The current core already contains the 68000, Z80, YM2203, OKIM6295, EEPROM,
tilemap, and sprite building blocks. The first pass should be board glue, not a
new hardware block.

## Game Index

The retired `mazinger` branch used game index `8`.

## ROM Layout

The retired branch packed the MiSTer ROM image in this order:

- `mzp-0.u24`: main program ROM, offset `0x00000000`
- `mzp-1.924`: extra 68000 data ROM, offset `0x00080000`
- EEPROM: offset `0x00100000`
- `mzs.u21`: Z80 program ROM, offset `0x00100080`
- `bp943a-4.u64`: OKI sample ROM, offset `0x00120080`
- `bp943a-1.u60`: layer 0 graphics, offset `0x001a0080`
- `bp943a-0.u63`: layer 1 graphics, offset `0x003a0080`
- `bp943a-2.u56` and `bp943a-3.u55`: sprites, offset `0x005a0080`

## Main CPU Map

MAME maps Mazinger as:

- `0x000000-0x07ffff`: program ROM
- `0x100000-0x10ffff`: main RAM
- `0x200000-0x20ffff`: sprite RAM
- `0x300000-0x30007f`: sprite/video regs
- `0x300000-0x300007`: IRQ cause reads
- `0x30006e-0x30006f`: sound command/reply
- `0x400000-0x40ffff`: layer 1 8x8 VRAM
- `0x500000-0x50ffff`: layer 0 8x8 VRAM
- `0x600000-0x600005`: layer 1 regs
- `0x700000-0x700005`: layer 0 regs
- `0x800000-0x800001`: input 0
- `0x800002-0x800003`: input 1 and EEPROM
- `0x900000`: EEPROM write
- `0xc08000-0xc0ffff`: palette RAM
- `0xd00000-0xd7ffff`: extra data ROM

## Sound CPU Map

MAME maps Mazinger Z80 sound as:

- `0x0000-0x3fff`: fixed Z80 ROM
- `0x4000-0x7fff`: banked Z80 ROM, bank mask `0x07`
- `0xc000-0xc7ff`: RAM
- `0xf800-0xffff`: RAM

Z80 I/O:

- `0x00`: Z80 ROM bank write
- `0x10`: sound reply write to main CPU
- `0x30`: sound command low byte read from main CPU
- `0x50-0x51`: YM2203 write
- `0x52-0x53`: YM2203 read
- `0x70`: OKIM6295 write
- `0x74`: OKI sample bank write, low nibble selects low bank and high nibble selects high bank

## Known Risks

- MAME applies a Mazinger-specific sprite ROM address permutation in
  `mazinger_decrypt_cb`. If sprites are scrambled, the core needs a matching
  address transform or the MRA needs a compatible ROM pre-processing step.
- MAME uses `m_kludge = 3`, which asserts `unknown_irq` at vblank end. The old
  branch also added an `unknownIrq` set on falling vblank. Keep this behavior
  gated to Mazinger.
- The retired branch noted "no sound". The first boot target intentionally keeps
  Mazinger sound disabled, matching that branch. Add the Mazinger Z80/YM2203/OKI
  map and sound reply handling after video/boot behavior is proven on hardware.
- While sound is disabled, the main CPU sound reply read at `0x30006e` is still
  stubbed to `0x00ff`, matching MAME's empty sound-reply FIFO return. Leaving
  this read as stale bus data can make startup polling hang.
