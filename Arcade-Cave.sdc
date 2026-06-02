derive_pll_clocks
derive_clock_uncertainty

# Define clock group for pll_video
set_clock_groups -exclusive \
  -group [get_clocks {emu|pll_video|pll_video_inst|altera_pll_i|general[0].gpll~PLL_OUTPUT_COUNTER|divclk}]