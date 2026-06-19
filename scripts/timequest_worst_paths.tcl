# Custom TimeQuest report hook for the active Cave branch.
# Quartus runs this from Arcade-Cave.qsf after the normal STA reports.

set report_dir "output_files"
set report_file [file join $report_dir "timequest_worst_setup_paths.rpt"]

file mkdir $report_dir
if {[file exists $report_file]} {
  file delete $report_file
}

proc append_line {text} {
  global report_file
  set fp [open $report_file a]
  puts $fp $text
  close $fp
}

proc report_setup_paths {label clock_check from_clock to_clock} {
  global report_file

  append_line ""
  append_line "============================================================"
  append_line $label
  append_line "============================================================"

  if {[string length $clock_check] != 0} {
    set clocks [get_clocks $clock_check]
    if {[llength $clocks] == 0} {
      append_line "Clock not found: $clock_check"
      return
    }
  }

  set cmd [list report_timing \
    -setup \
    -npaths 25 \
    -detail full_path \
    -panel_name $label \
    -file $report_file \
    -append]

  if {[string length $from_clock] != 0} {
    lappend cmd -from_clock $from_clock
  }

  if {[string length $to_clock] != 0} {
    lappend cmd -to_clock $to_clock
  }

  if {[catch {eval $cmd} err]} {
    append_line "report_timing failed: $err"
  }
}

set core96_clock {emu|pll|pll_inst|altera_pll_i|general[0].gpll~PLL_OUTPUT_COUNTER|divclk}
set core32_clock {emu|pll|pll_inst|altera_pll_i|general[1].gpll~PLL_OUTPUT_COUNTER|divclk}

append_line "Arcade-Cave custom TimeQuest worst setup path report"

report_setup_paths \
  "Worst setup paths - all clocks" \
  "" \
  "" \
  ""

report_setup_paths \
  "Worst setup paths ending at 96 MHz core clock" \
  $core96_clock \
  "" \
  $core96_clock

report_setup_paths \
  "Worst setup paths ending at 32 MHz core clock" \
  $core32_clock \
  "" \
  $core32_clock

report_setup_paths \
  "Worst setup paths - 96 MHz core clock to itself" \
  $core96_clock \
  $core96_clock \
  $core96_clock

report_setup_paths \
  "Worst setup paths - 32 MHz core clock to itself" \
  $core32_clock \
  $core32_clock \
  $core32_clock

append_line ""
append_line "End of custom TimeQuest report."
