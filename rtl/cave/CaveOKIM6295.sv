// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module CaveOKIM6295 #(
  parameter INTERPOL = 1
)(
  input         clock,
  input         reset,
  input  [16:0] io_cen_step,
  input         io_cpu_wr,
  input  [7:0]  io_cpu_din,
  input         io_wait_for_rom,
  output [7:0]  io_cpu_dout,
  output [17:0] io_rom_addr,
  input  [7:0]  io_rom_dout,
  input         io_rom_valid,
  output        io_audio_valid,
  output [13:0] io_audio_bits
);
  reg        adpcm_cen;
  reg [15:0] cenAccumulator;
  wire [16:0] cenNext = {1'b0, cenAccumulator} + io_cen_step;
  wire        hold_for_rom = io_wait_for_rom & cenNext[16] & ~io_rom_valid;

  always @(posedge clock) begin
    if (reset) begin
      cenAccumulator <= 16'h0;
      adpcm_cen <= 1'b0;
    end
    else if (hold_for_rom) begin
      adpcm_cen <= 1'b0;
    end
    else begin
      cenAccumulator <= cenNext[15:0];
      adpcm_cen <= cenNext[16];
    end
  end

  jt6295 #(
    .INTERPOL (INTERPOL)
  ) adpcm (
    .rst      (reset),
    .clk      (clock),
    .cen      (adpcm_cen),
    .ss       (1'b1),
    .wrn      (~io_cpu_wr),
    .din      (io_cpu_din),
    .dout     (io_cpu_dout),
    .rom_addr (io_rom_addr),
    .rom_data (io_rom_dout),
    .rom_ok   (io_rom_valid),
    .sound    (io_audio_bits),
    .sample   (io_audio_valid)
  );
endmodule
