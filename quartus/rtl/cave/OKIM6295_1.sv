module OKIM6295_1(
  input         clock,
  input         reset,
  input         io_cpu_wr,
  input  [7:0]  io_cpu_din,
  output [7:0]  io_cpu_dout,
  output [17:0] io_rom_addr,
  input  [7:0]  io_rom_dout,
  input         io_rom_valid,
  output        io_audio_valid,
  output [13:0] io_audio_bits
);

  wire        _adpcm_ss = 1'h1;
  wire        _adpcm_wrn;
  reg  [15:0] adpcm_cen_counter;
  reg         adpcm_cen_clockEnable;
  wire [16:0] adpcm_cen_next = 17'({1'h0, adpcm_cen_counter} + 17'h10E5);
  always @(posedge clock) begin
    adpcm_cen_counter <= adpcm_cen_next[15:0];
    adpcm_cen_clockEnable <= adpcm_cen_next[16];
  end // always @(posedge)
  assign _adpcm_wrn = ~io_cpu_wr;
  jt6295 adpcm (
    .rst      (reset),
    .clk      (clock),
    .cen      (adpcm_cen_clockEnable),
    .ss       (_adpcm_ss),
    .wrn      (_adpcm_wrn),
    .din      (io_cpu_din),
    .dout     (io_cpu_dout),
    .rom_addr (io_rom_addr),
    .rom_data (io_rom_dout),
    .rom_ok   (io_rom_valid),
    .sound    (io_audio_bits),
    .sample   (io_audio_valid)
  );
endmodule

