// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module YM2203(
  input         clock,
  input         reset,
  input         io_cpu_wr,
  input         io_cpu_addr,
  input  [7:0]  io_cpu_din,
  output [7:0]  io_cpu_dout,
  output        io_irq,
  output        io_audio_valid,
  output [15:0] io_audio_bits_psg,
  output [15:0] io_audio_bits_fm
);
  wire       ym_cen;
  wire       ym_irq_n;
  wire [9:0] ym_psg_snd;

  CaveClockEnable #(
    .STEP (17'h2000)
  ) clock_enable (
    .clock  (clock),
    .enable (ym_cen)
  );

  jt03 ym2203 (
    .rst        (reset),
    .clk        (clock),
    .cen        (ym_cen),
    .din        (io_cpu_din),
    .addr       (io_cpu_addr),
    .cs_n       (1'b0),
    .wr_n       (~io_cpu_wr),
    .dout       (io_cpu_dout),
    .irq_n      (ym_irq_n),
    .IOA_in     (8'h00),
    .IOB_in     (8'h00),
    .psg_A      (),
    .psg_B      (),
    .psg_C      (),
    .fm_snd     (io_audio_bits_fm),
    .psg_snd    (ym_psg_snd),
    .snd        (),
    .snd_sample (io_audio_valid),
    .debug_view ()
  );

  assign io_irq = ~ym_irq_n;
  assign io_audio_bits_psg = {1'b0, ym_psg_snd, 5'b0};
endmodule
