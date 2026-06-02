// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module SpriteDecoder(
  input         clock,
  input         reset,
  input  [1:0]  io_format,
  output        io_tileRom_ready,
  input         io_tileRom_valid,
  input  [63:0] io_tileRom_bits,
  input         io_pixelData_ready,
  output        io_pixelData_valid,
  output [7:0]  io_pixelData_bits_0,
  output [7:0]  io_pixelData_bits_1,
  output [7:0]  io_pixelData_bits_2,
  output [7:0]  io_pixelData_bits_3,
  output [7:0]  io_pixelData_bits_4,
  output [7:0]  io_pixelData_bits_5,
  output [7:0]  io_pixelData_bits_6,
  output [7:0]  io_pixelData_bits_7,
  output [7:0]  io_pixelData_bits_8,
  output [7:0]  io_pixelData_bits_9,
  output [7:0]  io_pixelData_bits_10,
  output [7:0]  io_pixelData_bits_11,
  output [7:0]  io_pixelData_bits_12,
  output [7:0]  io_pixelData_bits_13,
  output [7:0]  io_pixelData_bits_14,
  output [7:0]  io_pixelData_bits_15
);
  reg          pendingReg;
  reg          validReg;
  reg          toggleReg;
  reg  [127:0] dataReg;

  wire format4bppMsb = io_format == 2'h2;
  wire format8bpp = &io_format;

  wire start = io_pixelData_ready & ~pendingReg;
  wire tileRomFire = pendingReg & io_tileRom_valid;
  wire rowComplete = tileRomFire & (~format8bpp | toggleReg);

  wire [3:0] pixel4bpp_0  = format4bppMsb ? dataReg[15:12] : dataReg[3:0];
  wire [3:0] pixel4bpp_1  = format4bppMsb ? dataReg[11:8]  : dataReg[7:4];
  wire [3:0] pixel4bpp_2  = format4bppMsb ? dataReg[7:4]   : dataReg[11:8];
  wire [3:0] pixel4bpp_3  = format4bppMsb ? dataReg[3:0]   : dataReg[15:12];
  wire [3:0] pixel4bpp_4  = format4bppMsb ? dataReg[31:28] : dataReg[19:16];
  wire [3:0] pixel4bpp_5  = format4bppMsb ? dataReg[27:24] : dataReg[23:20];
  wire [3:0] pixel4bpp_6  = format4bppMsb ? dataReg[23:20] : dataReg[27:24];
  wire [3:0] pixel4bpp_7  = format4bppMsb ? dataReg[19:16] : dataReg[31:28];
  wire [3:0] pixel4bpp_8  = format4bppMsb ? dataReg[47:44] : dataReg[35:32];
  wire [3:0] pixel4bpp_9  = format4bppMsb ? dataReg[43:40] : dataReg[39:36];
  wire [3:0] pixel4bpp_10 = format4bppMsb ? dataReg[39:36] : dataReg[43:40];
  wire [3:0] pixel4bpp_11 = format4bppMsb ? dataReg[35:32] : dataReg[47:44];
  wire [3:0] pixel4bpp_12 = format4bppMsb ? dataReg[63:60] : dataReg[51:48];
  wire [3:0] pixel4bpp_13 = format4bppMsb ? dataReg[59:56] : dataReg[55:52];
  wire [3:0] pixel4bpp_14 = format4bppMsb ? dataReg[55:52] : dataReg[59:56];
  wire [3:0] pixel4bpp_15 = format4bppMsb ? dataReg[51:48] : dataReg[63:60];

  always @(posedge clock) begin
    if (reset) begin
      pendingReg <= 1'b0;
      validReg <= 1'b0;
      toggleReg <= 1'b0;
    end
    else begin
      pendingReg <= ~rowComplete & (start | pendingReg);
      validReg <= rowComplete | ~start & validReg;
      toggleReg <= tileRomFire ? ~toggleReg : ~start & toggleReg;
    end

    if (tileRomFire)
      dataReg <= {dataReg[63:0], io_tileRom_bits};
  end

  assign io_tileRom_ready = pendingReg;
  assign io_pixelData_valid = validReg;

  assign io_pixelData_bits_0 =
    format8bpp ? {dataReg[67:64], dataReg[75:72]} : {4'h0, pixel4bpp_0};
  assign io_pixelData_bits_1 =
    format8bpp ? {dataReg[71:68], dataReg[79:76]} : {4'h0, pixel4bpp_1};
  assign io_pixelData_bits_2 =
    format8bpp ? {dataReg[83:80], dataReg[91:88]} : {4'h0, pixel4bpp_2};
  assign io_pixelData_bits_3 =
    format8bpp ? {dataReg[87:84], dataReg[95:92]} : {4'h0, pixel4bpp_3};
  assign io_pixelData_bits_4 =
    format8bpp ? {dataReg[99:96], dataReg[107:104]} : {4'h0, pixel4bpp_4};
  assign io_pixelData_bits_5 =
    format8bpp ? {dataReg[103:100], dataReg[111:108]} : {4'h0, pixel4bpp_5};
  assign io_pixelData_bits_6 =
    format8bpp ? {dataReg[115:112], dataReg[123:120]} : {4'h0, pixel4bpp_6};
  assign io_pixelData_bits_7 =
    format8bpp ? {dataReg[119:116], dataReg[127:124]} : {4'h0, pixel4bpp_7};
  assign io_pixelData_bits_8 =
    format8bpp ? {dataReg[3:0], dataReg[11:8]} : {4'h0, pixel4bpp_8};
  assign io_pixelData_bits_9 =
    format8bpp ? {dataReg[7:4], dataReg[15:12]} : {4'h0, pixel4bpp_9};
  assign io_pixelData_bits_10 =
    format8bpp ? {dataReg[19:16], dataReg[27:24]} : {4'h0, pixel4bpp_10};
  assign io_pixelData_bits_11 =
    format8bpp ? {dataReg[23:20], dataReg[31:28]} : {4'h0, pixel4bpp_11};
  assign io_pixelData_bits_12 =
    format8bpp ? {dataReg[35:32], dataReg[43:40]} : {4'h0, pixel4bpp_12};
  assign io_pixelData_bits_13 =
    format8bpp ? {dataReg[39:36], dataReg[47:44]} : {4'h0, pixel4bpp_13};
  assign io_pixelData_bits_14 =
    format8bpp ? {dataReg[51:48], dataReg[59:56]} : {4'h0, pixel4bpp_14};
  assign io_pixelData_bits_15 =
    format8bpp ? {dataReg[55:52], dataReg[63:60]} : {4'h0, pixel4bpp_15};
endmodule
