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
  wire         _bits_T_114 = io_format == 2'h2;
  wire [3:0]   _GEN = _bits_T_114 ? dataReg[15:12] : dataReg[3:0];
  wire [3:0]   _GEN_0 = _bits_T_114 ? dataReg[11:8] : dataReg[7:4];
  wire [3:0]   _GEN_1 = _bits_T_114 ? dataReg[7:4] : dataReg[11:8];
  wire [3:0]   _GEN_2 = _bits_T_114 ? dataReg[3:0] : dataReg[15:12];
  wire [3:0]   _GEN_3 = _bits_T_114 ? dataReg[31:28] : dataReg[19:16];
  wire [3:0]   _GEN_4 = _bits_T_114 ? dataReg[27:24] : dataReg[23:20];
  wire [3:0]   _GEN_5 = _bits_T_114 ? dataReg[23:20] : dataReg[27:24];
  wire [3:0]   _GEN_6 = _bits_T_114 ? dataReg[19:16] : dataReg[31:28];
  wire [3:0]   _GEN_7 = _bits_T_114 ? dataReg[47:44] : dataReg[35:32];
  wire [3:0]   _GEN_8 = _bits_T_114 ? dataReg[43:40] : dataReg[39:36];
  wire [3:0]   _GEN_9 = _bits_T_114 ? dataReg[39:36] : dataReg[43:40];
  wire [3:0]   _GEN_10 = _bits_T_114 ? dataReg[35:32] : dataReg[47:44];
  wire [3:0]   _GEN_11 = _bits_T_114 ? dataReg[63:60] : dataReg[51:48];
  wire [3:0]   _GEN_12 = _bits_T_114 ? dataReg[59:56] : dataReg[55:52];
  wire [3:0]   _GEN_13 = _bits_T_114 ? dataReg[55:52] : dataReg[59:56];
  wire [3:0]   _GEN_14 = _bits_T_114 ? dataReg[51:48] : dataReg[63:60];
  wire         start = io_pixelData_ready & ~pendingReg;
  wire         _GEN_15 = pendingReg & io_tileRom_valid;
  wire         _GEN_16 = _GEN_15 & (~(&io_format) | toggleReg);
  always @(posedge clock) begin
    if (reset) begin
      pendingReg <= 1'h0;
      validReg <= 1'h0;
      toggleReg <= 1'h0;
    end
    else begin
      pendingReg <= ~_GEN_16 & (start | pendingReg);
      validReg <= _GEN_16 | ~start & validReg;
      toggleReg <= _GEN_15 ? ~toggleReg : ~start & toggleReg;
    end
    if (_GEN_15)
      dataReg <= {dataReg[63:0], io_tileRom_bits};
  end // always @(posedge)
  assign io_tileRom_ready = pendingReg;
  assign io_pixelData_valid = validReg;
  assign io_pixelData_bits_0 =
    (&io_format) ? {dataReg[67:64], dataReg[75:72]} : {4'h0, _GEN};
  assign io_pixelData_bits_1 =
    (&io_format) ? {dataReg[71:68], dataReg[79:76]} : {4'h0, _GEN_0};
  assign io_pixelData_bits_2 =
    (&io_format) ? {dataReg[83:80], dataReg[91:88]} : {4'h0, _GEN_1};
  assign io_pixelData_bits_3 =
    (&io_format) ? {dataReg[87:84], dataReg[95:92]} : {4'h0, _GEN_2};
  assign io_pixelData_bits_4 =
    (&io_format) ? {dataReg[99:96], dataReg[107:104]} : {4'h0, _GEN_3};
  assign io_pixelData_bits_5 =
    (&io_format) ? {dataReg[103:100], dataReg[111:108]} : {4'h0, _GEN_4};
  assign io_pixelData_bits_6 =
    (&io_format) ? {dataReg[115:112], dataReg[123:120]} : {4'h0, _GEN_5};
  assign io_pixelData_bits_7 =
    (&io_format) ? {dataReg[119:116], dataReg[127:124]} : {4'h0, _GEN_6};
  assign io_pixelData_bits_8 =
    (&io_format) ? {dataReg[3:0], dataReg[11:8]} : {4'h0, _GEN_7};
  assign io_pixelData_bits_9 =
    (&io_format) ? {dataReg[7:4], dataReg[15:12]} : {4'h0, _GEN_8};
  assign io_pixelData_bits_10 =
    (&io_format) ? {dataReg[19:16], dataReg[27:24]} : {4'h0, _GEN_9};
  assign io_pixelData_bits_11 =
    (&io_format) ? {dataReg[23:20], dataReg[31:28]} : {4'h0, _GEN_10};
  assign io_pixelData_bits_12 =
    (&io_format) ? {dataReg[35:32], dataReg[43:40]} : {4'h0, _GEN_11};
  assign io_pixelData_bits_13 =
    (&io_format) ? {dataReg[39:36], dataReg[47:44]} : {4'h0, _GEN_12};
  assign io_pixelData_bits_14 =
    (&io_format) ? {dataReg[51:48], dataReg[59:56]} : {4'h0, _GEN_13};
  assign io_pixelData_bits_15 =
    (&io_format) ? {dataReg[55:52], dataReg[63:60]} : {4'h0, _GEN_14};
endmodule

