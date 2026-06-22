derive_pll_clocks
derive_clock_uncertainty

# Define clock group for pll_video
set_clock_groups -exclusive \
  -group [get_clocks {emu|pll_video|pll_video_inst|altera_pll_i|general[0].gpll~PLL_OUTPUT_COUNTER|divclk}]

# Static game selector crossing into the CPU clock domain. The 4-bit payload is
# captured after the synchronized load toggle; only the CDC launch/capture arcs
# are cut here.
set_false_path \
  -from [get_registers {*|Cave:cave|gameIndexReg*}] \
  -to [get_registers {*|Cave:cave|gameIndexCpuReg*}]

set_false_path \
  -from [get_registers {*|Cave:cave|gameIndexCpuLoadToggle*}] \
  -to [get_registers {*|Cave:cave|gameIndexCpuToggleSync0*}]

# The latched game index is a static board/profile selector after ROM load or
# menu fallback. It feeds several 96 MHz decode/memory paths, but it is not a
# cycle-by-cycle datapath.
set_false_path \
  -from [get_registers -nowarn {*|Cave:cave|gameIndexReg[*]}]

# Program ROM reads return from the 96 MHz memory/cache side through
# CaveProgramRomReadFreezer and are held until the 32 MHz CPU-side toggle
# clears the latched value. Relax only the return-to-dinReg arc by one 96 MHz
# source cycle; keep it timed instead of false-pathing the whole boundary.
set_multicycle_path -start -setup \
  -from [get_registers -nowarn {*|Cave:cave|CaveProgramRomReadFreezer:main_io_progRom_freezer|*}] \
  -to [get_registers -nowarn {*|Cave:cave|Main:main|dinReg*}] \
  2
set_multicycle_path -start -hold \
  -from [get_registers -nowarn {*|Cave:cave|CaveProgramRomReadFreezer:main_io_progRom_freezer|*}] \
  -to [get_registers -nowarn {*|Cave:cave|Main:main|dinReg*}] \
  1

set_multicycle_path -start -setup \
  -from [get_registers -nowarn {*|Cave:cave|MemSys:memSys|CaveReadCache:progRomCache|*}] \
  -to [get_registers -nowarn {*|Cave:cave|Main:main|dinReg*}] \
  2
set_multicycle_path -start -hold \
  -from [get_registers -nowarn {*|Cave:cave|MemSys:memSys|CaveReadCache:progRomCache|*}] \
  -to [get_registers -nowarn {*|Cave:cave|Main:main|dinReg*}] \
  1
