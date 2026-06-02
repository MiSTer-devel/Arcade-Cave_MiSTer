// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module MazingerBootWatchdog(
  input         clock,
  input         reset,
  input         game_active,
  input         boot_ram_select,
  input         boot_ram_write,
  input         boot_ram_word,
  input  [1:0]  boot_ram_mask,
  input  [15:0] boot_ram_din,
  output        cpu_reset,
  output [15:0] boot_ram_dout,
  output        watchdog_armed,
  output        watchdog_delay_active,
  output        watchdog_reset_active,
  output        boot_marker_write,
  output        watchdog_trip
);
  reg  [5:0]  watchdogDelayCounter;
  reg  [4:0]  watchdogResetCounter;
  reg         bootWatchdogArmed;
  reg  [15:0] bootRam0;
  reg  [15:0] bootRam1;

  assign boot_marker_write =
    game_active & bootWatchdogArmed & boot_ram_select & boot_ram_write
    & (boot_ram_din == 16'h5555);
  assign watchdog_trip = watchdogDelayCounter == 6'd1;
  assign watchdog_armed = bootWatchdogArmed;
  assign watchdog_delay_active = |watchdogDelayCounter;
  assign watchdog_reset_active = |watchdogResetCounter;
  assign cpu_reset = reset | watchdog_reset_active;
  assign boot_ram_dout = boot_ram_word ? bootRam1 : bootRam0;

  always @(posedge clock) begin
    if (reset) begin
      watchdogDelayCounter <= 6'd0;
      watchdogResetCounter <= 5'd0;
      bootWatchdogArmed <= 1'b1;
      bootRam0 <= 16'd0;
      bootRam1 <= 16'd0;
    end
    else begin
      if (~game_active) begin
        watchdogDelayCounter <= 6'd0;
        watchdogResetCounter <= 5'd0;
        bootWatchdogArmed <= 1'b1;
      end
      else if (boot_marker_write & (watchdogDelayCounter == 6'd0))
        watchdogDelayCounter <= 6'd32;
      else if (watchdog_trip) begin
        watchdogDelayCounter <= 6'd0;
        watchdogResetCounter <= 5'd16;
        bootWatchdogArmed <= 1'b0;
      end
      else if (watchdog_delay_active)
        watchdogDelayCounter <= watchdogDelayCounter - 6'd1;
      else if (watchdog_reset_active)
        watchdogResetCounter <= watchdogResetCounter - 5'd1;

      if (game_active & boot_ram_select & boot_ram_write) begin
        if (boot_ram_word) begin
          if (boot_ram_mask[1])
            bootRam1[15:8] <= boot_ram_din[15:8];
          if (boot_ram_mask[0])
            bootRam1[7:0] <= boot_ram_din[7:0];
        end
        else begin
          if (boot_ram_mask[1])
            bootRam0[15:8] <= boot_ram_din[15:8];
          if (boot_ram_mask[0])
            bootRam0[7:0] <= boot_ram_din[7:0];
        end
      end
    end
  end
endmodule
