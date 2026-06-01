// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Shared tilemap layer renderer used by the three Cave background layers.
module CaveLayerProcessor #(
  parameter [4:0] LAYER_OFFSET_LARGE = 5'h12,
  parameter [4:0] LAYER_OFFSET_SMALL = 5'h0A
) (
  input         clock,
  input         io_ctrl_enable,
  input  [1:0]  io_ctrl_format,
  input         io_ctrl_regs_tileSize,
  input         io_ctrl_regs_enable,
  input         io_ctrl_regs_flipX,
  input         io_ctrl_regs_flipY,
  input         io_ctrl_regs_rowScrollEnable,
  input         io_ctrl_regs_rowSelectEnable,
  input  [8:0]  io_ctrl_regs_scroll_x,
  input  [8:0]  io_ctrl_regs_scroll_y,
  output [11:0] io_ctrl_vram8x8_addr,
  input  [31:0] io_ctrl_vram8x8_dout,
  output [9:0]  io_ctrl_vram16x16_addr,
  input  [31:0] io_ctrl_vram16x16_dout,
  output [8:0]  io_ctrl_lineRam_addr,
  input  [31:0] io_ctrl_lineRam_dout,
  output        io_ctrl_tileRom_rd,
  output [31:0] io_ctrl_tileRom_addr,
  input  [63:0] io_ctrl_tileRom_dout,
  input         io_video_clockEnable,
  input  [8:0]  io_video_pos_x,
  input  [8:0]  io_video_pos_y,
  input         io_video_vBlank,
  input  [8:0]  io_video_regs_size_x,
  input  [8:0]  io_video_regs_size_y,
  input  [8:0]  io_spriteOffset_x,
  input  [8:0]  io_spriteOffset_y,
  output [1:0]  io_pen_priority,
  output [5:0]  io_pen_palette,
  output [7:0]  io_pen_color
);
  reg [8:0] lineEffectReg_rowSelect;
  reg [8:0] lineEffectReg_rowScroll;

  reg [1:0]  tileReg_priority;
  reg [5:0]  tileReg_colorCode;
  reg [17:0] tileReg_code;
  reg [1:0]  priorityReg;
  reg [5:0]  colorReg;
  reg [7:0]  pixReg_0;
  reg [7:0]  pixReg_1;
  reg [7:0]  pixReg_2;
  reg [7:0]  pixReg_3;
  reg [7:0]  pixReg_4;
  reg [7:0]  pixReg_5;
  reg [7:0]  pixReg_6;
  reg [7:0]  pixReg_7;

  wire layerEnable = io_ctrl_enable & (|io_ctrl_format) & io_ctrl_regs_enable;

  wire [4:0] layerOffsetBase =
    io_ctrl_regs_tileSize ? LAYER_OFFSET_LARGE : LAYER_OFFSET_SMALL;
  wire [4:0] layerOffsetX5 =
    io_ctrl_regs_flipX ? (layerOffsetBase + 5'h01) : layerOffsetBase;
  wire [8:0] layerOffsetX = {4'h0, layerOffsetX5};
  wire [8:0] layerOffsetY = {8'hF7, io_ctrl_regs_flipY};

  wire [8:0] normalPosX =
    io_video_pos_x + io_ctrl_regs_scroll_x - io_spriteOffset_x - layerOffsetX;
  wire [8:0] flippedPosX =
    layerOffsetX + (io_video_regs_size_x - io_video_pos_x + io_ctrl_regs_scroll_x - io_spriteOffset_x);
  wire [8:0] pos_x = io_ctrl_regs_flipX ? flippedPosX : normalPosX;

  wire [8:0] normalPosY =
    io_video_pos_y + io_ctrl_regs_scroll_y - io_spriteOffset_y - layerOffsetY;
  wire [8:0] flippedPosY =
    io_video_regs_size_y - io_video_pos_y + io_ctrl_regs_scroll_y - io_spriteOffset_y + layerOffsetY;
  wire [8:0] pos_y = io_ctrl_regs_flipY ? flippedPosY : normalPosY;

  wire [8:0] rowScroll = io_ctrl_regs_rowScrollEnable ? lineEffectReg_rowScroll : 9'h000;
  wire [8:0] effectivePosX = rowScroll + pos_x;
  wire [8:0] effectivePosY = io_ctrl_regs_rowSelectEnable ? lineEffectReg_rowSelect : pos_y;

  wire [3:0] tileOffset_x =
    io_ctrl_regs_tileSize ? effectivePosX[3:0] : {1'b0, effectivePosX[2:0]};
  wire [3:0] tileOffset_y =
    io_ctrl_regs_tileSize ? effectivePosY[3:0] : {1'b0, effectivePosY[2:0]};

  wire [4:0] largeColumnDelta = io_ctrl_regs_flipX ? 5'h1F : 5'h01;
  wire [5:0] smallColumnDelta = io_ctrl_regs_flipX ? 6'h3F : 6'h01;
  wire [4:0] largeColumn = effectivePosX[8:4] + largeColumnDelta;
  wire [5:0] smallColumn = effectivePosX[8:3] + smallColumnDelta;
  wire [11:0] vramAddr =
    io_ctrl_regs_tileSize
      ? {2'b00, effectivePosY[8:4], largeColumn}
      : {effectivePosY[8:3], smallColumn};

  wire format4bpp = io_ctrl_format == 2'h1;
  wire format6bpp = io_ctrl_format == 2'h2;
  wire format8bpp = &io_ctrl_format;
  wire formatLinearTile = format6bpp | format8bpp;
  wire [31:0] pixels4bppBits =
    tileOffset_y[0] ? io_ctrl_tileRom_dout[31:0] : io_ctrl_tileRom_dout[63:32];
  wire [7:0] pixel8bpp_0 = {io_ctrl_tileRom_dout[55:52], io_ctrl_tileRom_dout[63:60]};
  wire [7:0] pixel8bpp_1 = {io_ctrl_tileRom_dout[51:48], io_ctrl_tileRom_dout[59:56]};
  wire [7:0] pixel8bpp_2 = {io_ctrl_tileRom_dout[39:36], io_ctrl_tileRom_dout[47:44]};
  wire [7:0] pixel8bpp_3 = {io_ctrl_tileRom_dout[35:32], io_ctrl_tileRom_dout[43:40]};
  wire [7:0] pixel8bpp_4 = {io_ctrl_tileRom_dout[23:20], io_ctrl_tileRom_dout[31:28]};
  wire [7:0] pixel8bpp_5 = {io_ctrl_tileRom_dout[19:16], io_ctrl_tileRom_dout[27:24]};
  wire [7:0] pixel8bpp_6 = {io_ctrl_tileRom_dout[7:4], io_ctrl_tileRom_dout[15:12]};
  wire [7:0] pixel8bpp_7 = {io_ctrl_tileRom_dout[3:0], io_ctrl_tileRom_dout[11:8]};
  wire [7:0] pixel6bpp_0 = {2'b00, pixel8bpp_0[7:6], pixel8bpp_0[3:0]};
  wire [7:0] pixel6bpp_1 = {2'b00, pixel8bpp_1[7:6], pixel8bpp_1[3:0]};
  wire [7:0] pixel6bpp_2 = {2'b00, pixel8bpp_2[7:6], pixel8bpp_2[3:0]};
  wire [7:0] pixel6bpp_3 = {2'b00, pixel8bpp_3[7:6], pixel8bpp_3[3:0]};
  wire [7:0] pixel6bpp_4 = {2'b00, pixel8bpp_4[7:6], pixel8bpp_4[3:0]};
  wire [7:0] pixel6bpp_5 = {2'b00, pixel8bpp_5[7:6], pixel8bpp_5[3:0]};
  wire [7:0] pixel6bpp_6 = {2'b00, pixel8bpp_6[7:6], pixel8bpp_6[3:0]};
  wire [7:0] pixel6bpp_7 = {2'b00, pixel8bpp_7[7:6], pixel8bpp_7[3:0]};

  reg [7:0] penColor;
  reg [25:0] tileRomAddr;

  always @(*) begin
    case (tileOffset_x[2:0])
      3'd0: penColor = pixReg_0;
      3'd1: penColor = pixReg_1;
      3'd2: penColor = pixReg_2;
      3'd3: penColor = pixReg_3;
      3'd4: penColor = pixReg_4;
      3'd5: penColor = pixReg_5;
      3'd6: penColor = pixReg_6;
      default: penColor = pixReg_7;
    endcase
  end

  always @(*) begin
    tileRomAddr = 26'h0000000;

    if (io_ctrl_regs_tileSize & formatLinearTile)
      tileRomAddr = {tileReg_code, tileOffset_y[3], ~tileOffset_x[3], tileOffset_y[2:0], 3'b000};
    if (io_ctrl_regs_tileSize & format4bpp)
      tileRomAddr = {1'b0, tileReg_code, tileOffset_y[3], ~tileOffset_x[3], tileOffset_y[2:1], 3'b000};
    if (~io_ctrl_regs_tileSize & formatLinearTile)
      tileRomAddr = {2'b00, tileReg_code, tileOffset_y[2:0], 3'b000};
    if (~io_ctrl_regs_tileSize & format4bpp)
      tileRomAddr = {3'b000, tileReg_code, tileOffset_y[2:1], 3'b000};
  end

  wire latchTile =
    io_video_clockEnable
    & (io_ctrl_regs_flipX
       ? tileOffset_x == 4'h5
       : (io_ctrl_regs_tileSize ? tileOffset_x == 4'hA : tileOffset_x == 4'h2));
  wire latchColor =
    io_video_clockEnable
    & (io_ctrl_regs_flipX
       ? tileOffset_x == 4'h0
       : (io_ctrl_regs_tileSize ? (&tileOffset_x) : tileOffset_x == 4'h7));
  wire latchPix =
    io_video_clockEnable
    & (io_ctrl_regs_flipX ? tileOffset_x[2:0] == 3'h0 : (&tileOffset_x[2:0]));

  always @(posedge clock) begin
    if (io_video_clockEnable) begin
      lineEffectReg_rowSelect <= io_ctrl_lineRam_dout[24:16];
      lineEffectReg_rowScroll <= io_ctrl_lineRam_dout[8:0];
    end

    if (latchTile) begin
      tileReg_priority <=
        io_ctrl_regs_tileSize ? io_ctrl_vram16x16_dout[15:14] : io_ctrl_vram8x8_dout[15:14];
      tileReg_colorCode <=
        io_ctrl_regs_tileSize ? io_ctrl_vram16x16_dout[13:8] : io_ctrl_vram8x8_dout[13:8];
      tileReg_code <=
        io_ctrl_regs_tileSize
          ? {2'b00, io_ctrl_vram16x16_dout[31:16]}
          : {io_ctrl_vram8x8_dout[1:0], io_ctrl_vram8x8_dout[31:16]};
    end

    if (latchColor) begin
      priorityReg <= tileReg_priority;
      colorReg <= tileReg_colorCode;
    end

    if (latchPix) begin
      pixReg_0 <=
        format8bpp
          ? pixel8bpp_0
          : format6bpp ? pixel6bpp_0 : {4'h0, pixels4bppBits[31:28]};
      pixReg_1 <=
        format8bpp
          ? pixel8bpp_1
          : format6bpp ? pixel6bpp_1 : {4'h0, pixels4bppBits[27:24]};
      pixReg_2 <=
        format8bpp
          ? pixel8bpp_2
          : format6bpp ? pixel6bpp_2 : {4'h0, pixels4bppBits[23:20]};
      pixReg_3 <=
        format8bpp
          ? pixel8bpp_3
          : format6bpp ? pixel6bpp_3 : {4'h0, pixels4bppBits[19:16]};
      pixReg_4 <=
        format8bpp
          ? pixel8bpp_4
          : format6bpp ? pixel6bpp_4 : {4'h0, pixels4bppBits[15:12]};
      pixReg_5 <=
        format8bpp
          ? pixel8bpp_5
          : format6bpp ? pixel6bpp_5 : {4'h0, pixels4bppBits[11:8]};
      pixReg_6 <=
        format8bpp
          ? pixel8bpp_6
          : format6bpp ? pixel6bpp_6 : {4'h0, pixels4bppBits[7:4]};
      pixReg_7 <=
        format8bpp
          ? pixel8bpp_7
          : format6bpp ? pixel6bpp_7 : {4'h0, pixels4bppBits[3:0]};
    end
  end

  assign io_ctrl_vram8x8_addr = vramAddr;
  assign io_ctrl_vram16x16_addr = vramAddr[9:0];
  assign io_ctrl_lineRam_addr =
    io_ctrl_regs_flipY ? (pos_y + 9'h001) : (pos_y - 9'h001);
  assign io_ctrl_tileRom_rd = layerEnable & ~io_video_vBlank;
  assign io_ctrl_tileRom_addr = {6'h00, tileRomAddr};

  assign io_pen_priority = layerEnable ? priorityReg : 2'h0;
  assign io_pen_palette = layerEnable ? colorReg : 6'h00;
  assign io_pen_color = layerEnable ? penColor : 8'h00;
endmodule
