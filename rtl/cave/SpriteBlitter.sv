// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module SpriteBlitter(
  input         clock,
  input         reset,
  input         io_enable,
  output        io_busy,
  output        io_config_ready,
  input         io_config_valid,
  input  [1:0]  io_config_bits_sprite_priority,
  input  [5:0]  io_config_bits_sprite_colorCode,
  input         io_config_bits_sprite_hFlip,
  input         io_config_bits_sprite_vFlip,
  input  [17:0] io_config_bits_sprite_pos_x,
  input  [17:0] io_config_bits_sprite_pos_y,
  input  [7:0]  io_config_bits_sprite_cols,
  input  [7:0]  io_config_bits_sprite_rows,
  input  [15:0] io_config_bits_sprite_zoom_x,
  input  [15:0] io_config_bits_sprite_zoom_y,
  input         io_config_bits_hFlip,
  input  [8:0]  io_video_regs_size_x,
  input  [8:0]  io_video_regs_size_y,
  output        io_pixelData_ready,
  input         io_pixelData_valid,
  input  [7:0]  io_pixelData_bits_0,
  input  [7:0]  io_pixelData_bits_1,
  input  [7:0]  io_pixelData_bits_2,
  input  [7:0]  io_pixelData_bits_3,
  input  [7:0]  io_pixelData_bits_4,
  input  [7:0]  io_pixelData_bits_5,
  input  [7:0]  io_pixelData_bits_6,
  input  [7:0]  io_pixelData_bits_7,
  input  [7:0]  io_pixelData_bits_8,
  input  [7:0]  io_pixelData_bits_9,
  input  [7:0]  io_pixelData_bits_10,
  input  [7:0]  io_pixelData_bits_11,
  input  [7:0]  io_pixelData_bits_12,
  input  [7:0]  io_pixelData_bits_13,
  input  [7:0]  io_pixelData_bits_14,
  input  [7:0]  io_pixelData_bits_15,
  output        io_frameBuffer_wr,
  output [16:0] io_frameBuffer_addr,
  output [15:0] io_frameBuffer_din,
  input         io_frameBuffer_wait_n
);
  wire       pisoRead;
  wire       pisoWrite;
  wire       pisoEmpty;
  wire       pisoAlmostEmpty;
  wire [7:0] pisoPixel;

  reg         busyReg;
  reg  [1:0]  configReg_sprite_priority;
  reg  [5:0]  configReg_sprite_colorCode;
  reg         configReg_sprite_hFlip;
  reg         configReg_sprite_vFlip;
  reg  [17:0] configReg_sprite_pos_x;
  reg  [17:0] configReg_sprite_pos_y;
  reg  [7:0]  configReg_sprite_cols;
  reg  [7:0]  configReg_sprite_rows;
  reg  [15:0] configReg_sprite_zoom_x;
  reg  [15:0] configReg_sprite_zoom_y;
  reg         configReg_hFlip;

  reg  [11:0] x;
  reg  [11:0] y;
  reg  [19:0] posReg_x;
  reg  [19:0] posReg_y;
  reg  [1:0]  penReg_priority;
  reg  [5:0]  penReg_palette;
  reg  [7:0]  penReg_color;
  reg         validReg;

  wire blitAdvance = busyReg & ~pisoEmpty & io_frameBuffer_wait_n;
  wire xAtEnd =
    x == 12'({configReg_sprite_cols, 4'h0} - 12'h1) | configReg_sprite_cols == 8'h0;
  wire xWrap = blitAdvance & xAtEnd;
  wire yAtEnd =
    y == 12'({configReg_sprite_rows, 4'h0} - 12'h1) | configReg_sprite_rows == 8'h0;
  wire blitDone = xWrap & yAtEnd;
  wire configReady = ~busyReg | blitDone;
  wire latchConfig = configReady & io_config_valid;

  wire [27:0] scaledX = 28'({16'h0, x} * {12'h0, configReg_sprite_zoom_x});
  wire [27:0] scaledY = 28'({16'h0, y} * {12'h0, configReg_sprite_zoom_y});
  wire [27:0] flippedX =
    configReg_sprite_hFlip
      ? 28'(28'({8'h0, configReg_sprite_cols, 12'h0} - scaledX) - 28'h100)
      : scaledX;
  wire [27:0] flippedY =
    configReg_sprite_vFlip
      ? 28'(28'({8'h0, configReg_sprite_rows, 12'h0} - scaledY) - 28'h100)
      : scaledY;
  wire [27:0] adjustedX =
    28'({{10{configReg_sprite_pos_x[17]}}, configReg_sprite_pos_x} + flippedX);
  wire [27:0] adjustedY =
    28'({{10{configReg_sprite_pos_y[17]}}, configReg_sprite_pos_y} + flippedY);

  wire pixelDataReady = io_frameBuffer_wait_n & (pisoEmpty | pisoAlmostEmpty);
  wire [19:0] maxVisibleX = {11'h0, 9'(io_video_regs_size_x - 9'h1)};
  wire [19:0] maxVisibleY = {11'h0, 9'(io_video_regs_size_y - 9'h1)};
  wire pixelVisible =
    posReg_x <= maxVisibleX
    & posReg_y <= maxVisibleY
    & validReg
    & (|penReg_color);

  wire [16:0] normalFrameBufferAddr = 17'({posReg_y[7:0], 9'h0} + posReg_x[16:0]);
  wire [16:0] flippedFrameBufferAddr =
    17'({8'(8'(io_video_regs_size_y[7:0] - posReg_y[7:0]) - 8'h1), 9'h0}
        + 17'(17'({8'h0, io_video_regs_size_x} - posReg_x[16:0]) - 17'h1));

  always @(posedge clock) begin
    if (reset) begin
      busyReg <= 1'b0;
      x <= 12'h0;
      y <= 12'h0;
    end
    else begin
      busyReg <= latchConfig | ~blitDone & busyReg;
      if (blitAdvance)
        x <= xAtEnd ? 12'h0 : 12'(x + 12'h1);
      if (xWrap)
        y <= yAtEnd ? 12'h0 : 12'(y + 12'h1);
    end

    if (latchConfig) begin
      configReg_sprite_priority <= io_config_bits_sprite_priority;
      configReg_sprite_colorCode <= io_config_bits_sprite_colorCode;
      configReg_sprite_hFlip <= io_config_bits_sprite_hFlip;
      configReg_sprite_vFlip <= io_config_bits_sprite_vFlip;
      configReg_sprite_pos_x <= io_config_bits_sprite_pos_x;
      configReg_sprite_pos_y <= io_config_bits_sprite_pos_y;
      configReg_sprite_cols <= io_config_bits_sprite_cols;
      configReg_sprite_rows <= io_config_bits_sprite_rows;
      configReg_sprite_zoom_x <= io_config_bits_sprite_zoom_x;
      configReg_sprite_zoom_y <= io_config_bits_sprite_zoom_y;
      configReg_hFlip <= io_config_bits_hFlip;
    end

    if (io_frameBuffer_wait_n) begin
      posReg_x <= adjustedX[27:8];
      posReg_y <= adjustedY[27:8];
      penReg_priority <= configReg_sprite_priority;
      penReg_palette <= configReg_sprite_colorCode;
      penReg_color <= pisoPixel;
      validReg <= ~pisoEmpty;
    end
  end

  assign pisoRead = busyReg & io_frameBuffer_wait_n;
  assign pisoWrite = pixelDataReady & io_pixelData_valid;

  CaveSpritePixelShiftBuffer pixelShiftBuffer (
    .clock            (clock),
    .reset            (reset),
    .io_rd            (pisoRead),
    .io_wr            (pisoWrite),
    .io_isEmpty       (pisoEmpty),
    .io_isAlmostEmpty (pisoAlmostEmpty),
    .io_din_0         (io_pixelData_bits_0),
    .io_din_1         (io_pixelData_bits_1),
    .io_din_2         (io_pixelData_bits_2),
    .io_din_3         (io_pixelData_bits_3),
    .io_din_4         (io_pixelData_bits_4),
    .io_din_5         (io_pixelData_bits_5),
    .io_din_6         (io_pixelData_bits_6),
    .io_din_7         (io_pixelData_bits_7),
    .io_din_8         (io_pixelData_bits_8),
    .io_din_9         (io_pixelData_bits_9),
    .io_din_10        (io_pixelData_bits_10),
    .io_din_11        (io_pixelData_bits_11),
    .io_din_12        (io_pixelData_bits_12),
    .io_din_13        (io_pixelData_bits_13),
    .io_din_14        (io_pixelData_bits_14),
    .io_din_15        (io_pixelData_bits_15),
    .io_dout          (pisoPixel)
  );

  assign io_busy = busyReg;
  assign io_config_ready = configReady;
  assign io_pixelData_ready = pixelDataReady;
  assign io_frameBuffer_wr = io_enable & pixelVisible;
  assign io_frameBuffer_addr = configReg_hFlip ? flippedFrameBufferAddr : normalFrameBufferAddr;
  assign io_frameBuffer_din = {penReg_priority, penReg_palette, penReg_color};
endmodule
