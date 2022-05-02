set sys_clk "emu|pll_sys|pll_sys_inst|altera_pll_i|general[0].gpll~PLL_OUTPUT_COUNTER|divclk"
set sdram_clk "emu|pll_sys|pll_sys_inst|altera_pll_i|general[1].gpll~PLL_OUTPUT_COUNTER|divclk"
set video_clk "emu|pll_video|pll_video_inst|altera_pll_i|general[0].gpll~PLL_OUTPUT_COUNTER|divclk"

# Decouple clock groups
set_false_path -from [get_clocks $sys_clk] -to [get_clocks $video_clk]
set_false_path -from [get_clocks $video_clk] -to [get_clocks $sys_clk]

set_false_path -from [get_clocks $sdram_clk] -to [get_clocks $video_clk]
set_false_path -from [get_clocks $video_clk] -to [get_clocks $sdram_clk]

# Data access delay (tAC) plus a small margin to allow for propagation delay
set_input_delay -clock $sdram_clk -max [expr 6.0 + 0.5] [get_ports {SDRAM_DQ[*]}]

# Data output hold time (tOH)
set_input_delay -clock $sdram_clk -min 2.5 [get_ports {SDRAM_DQ[*]}]

# Data input setup time (tIS) plus a small margin to allow for propagation delay
set_output_delay -clock $sdram_clk -max [expr 1.5 + 0.5] [get_ports {SDRAM_A* SDRAM_BA* SDRAM_D* SDRAM_CKE SDRAM_n*}]

# Data input hold time (tIH)
set_output_delay -clock $sdram_clk -min -0.8 [get_ports {SDRAM_A* SDRAM_BA* SDRAM_D* SDRAM_CKE SDRAM_n*}]

# Set a multicycle path relationship between the system and SDRAM clocks
set_multicycle_path -end -from [get_clocks $sys_clk] -to [get_clocks $sdram_clk] -setup 2
set_multicycle_path -end -from [get_clocks $sys_clk] -to [get_clocks $sdram_clk] -hold 1
