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
