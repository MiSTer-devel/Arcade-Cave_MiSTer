set sys_clk "emu|pll|pll_inst|altera_pll_i|general[0].gpll~PLL_OUTPUT_COUNTER|divclk"
set video_clk "emu|pll|pll_inst|altera_pll_i|general[1].gpll~PLL_OUTPUT_COUNTER|divclk"
set cpu_clk "emu|pll|pll_inst|altera_pll_i|general[2].gpll~PLL_OUTPUT_COUNTER|divclk"

# Decouple clock groups
set_clock_groups -asynchronous \
  -group [get_clocks $sys_clk] \
  -group [get_clocks $video_clk] \
  -group [get_clocks $cpu_clk]

# Set a multicycle path relationship between the system and CPU clocks
set_multicycle_path -start -from [get_clocks $sys_clk] -to [get_clocks $cpu_clk] -setup 2
set_multicycle_path -start -from [get_clocks $sys_clk] -to [get_clocks $cpu_clk] -hold 1

# Set a multicycle path relationship between the SDRAM ports and the system clock
set_multicycle_path -start -from [get_ports {SDRAM_A* SDRAM_BA* SDRAM_D* SDRAM_CKE SDRAM_n* SDRAM_DQ[*]}] -to [get_clocks $sys_clk] -setup 2
set_multicycle_path -start -from [get_ports {SDRAM_A* SDRAM_BA* SDRAM_D* SDRAM_CKE SDRAM_n* SDRAM_DQ[*]}] -to [get_clocks $sys_clk] -hold 1
