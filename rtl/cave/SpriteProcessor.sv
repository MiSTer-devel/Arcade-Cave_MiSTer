// Sprite list scheduler feeding the sprite decoder and blitter.
module SpriteProcessor(
  input          clock,
  input          reset,
  input          io_ctrl_enable,
  input  [1:0]   io_ctrl_format,
  input          io_ctrl_start,
  input          io_ctrl_zoom,
  input  [1:0]   io_ctrl_regs_bank,
  input          io_ctrl_regs_fixed,
  input          io_ctrl_regs_hFlip,
  output         io_ctrl_vram_rd,
  output [11:0]  io_ctrl_vram_addr,
  input  [127:0] io_ctrl_vram_dout,
  output         io_ctrl_tileRom_rd,
  output [31:0]  io_ctrl_tileRom_addr,
  input  [63:0]  io_ctrl_tileRom_dout,
  input          io_ctrl_tileRom_wait_n,
  input          io_ctrl_tileRom_valid,
  output [7:0]   io_ctrl_tileRom_burstLength,
  input          io_ctrl_tileRom_burstDone,
  input  [8:0]   io_video_regs_size_x,
  input  [8:0]   io_video_regs_size_y,
  output         io_frameBuffer_wr,
  output [16:0]  io_frameBuffer_addr,
  output [15:0]  io_frameBuffer_din,
  input          io_frameBuffer_wait_n,
  output         io_ctrl_frameReady,
  output [63:0] io_debug
);
  localparam [2:0] STATE_IDLE    = 3'd0;
  localparam [2:0] STATE_LOAD    = 3'd1;
  localparam [2:0] STATE_LATCH   = 3'd2;
  localparam [2:0] STATE_CHECK   = 3'd3;
  localparam [2:0] STATE_READY   = 3'd4;
  localparam [2:0] STATE_PENDING = 3'd5;
  localparam [2:0] STATE_NEXT    = 3'd6;
  localparam [2:0] STATE_DONE    = 3'd7;

  reg [2:0]  stateReg;
  reg [9:0]  spriteCounter;
  reg [15:0] tileCounter;
  reg        readPendingReg;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  reg [2:0]  debugAbcExactFlags;
  reg [2:0]  debugAbcExactFlagsLatched;
  reg [2:0]  debugAbcWriteFlags;
  reg [2:0]  debugAbcWriteFlagsLatched;
  reg [2:0]  debugAbcSlotFlags;
  reg [2:0]  debugAbcSlotFlagsLatched;
  reg [2:0]  debugAbcActiveFlags;
  reg [7:0]  debugAbcExactHistory;
  reg [7:0]  debugAbcWriteHistory;
  reg [7:0]  debugAbcSlot160Code;
  reg [7:0]  debugAbcSlot161Code;
  reg [7:0]  debugAbcSlot162Code;
  reg [7:0]  debugAbcSlot160CodeLatched;
  reg [7:0]  debugAbcSlot161CodeLatched;
  reg [7:0]  debugAbcSlot162CodeLatched;
`endif
  reg        frameReadyReg;

  reg [1:0]  spriteReg_priority;
  reg [5:0]  spriteReg_colorCode;
  reg [17:0] spriteReg_code;
  reg        spriteReg_hFlip;
  reg        spriteReg_vFlip;
  reg [17:0] spriteReg_pos_x;
  reg [17:0] spriteReg_pos_y;
  reg [7:0]  spriteReg_cols;
  reg [7:0]  spriteReg_rows;
  reg [15:0] spriteReg_zoom_x;
  reg [15:0] spriteReg_zoom_y;
  reg [15:0] numTilesReg;

  wire        fifoDeqReady;
  wire        fifoFlush = stateReg == STATE_IDLE;
  wire        fifoDeqValid;
  wire [63:0] fifoDeqBits;
  wire [6:0]  fifoCount;

  wire        blitterConfigReady;
  wire        blitterConfigValid = stateReg == STATE_READY;
  wire        blitterPixelDataReady;
  wire        blitterPixelDataValid;
  wire        blitterBusy;
  wire [7:0]  blitterPixelData0;
  wire [7:0]  blitterPixelData1;
  wire [7:0]  blitterPixelData2;
  wire [7:0]  blitterPixelData3;
  wire [7:0]  blitterPixelData4;
  wire [7:0]  blitterPixelData5;
  wire [7:0]  blitterPixelData6;
  wire [7:0]  blitterPixelData7;
  wire [7:0]  blitterPixelData8;
  wire [7:0]  blitterPixelData9;
  wire [7:0]  blitterPixelData10;
  wire [7:0]  blitterPixelData11;
  wire [7:0]  blitterPixelData12;
  wire [7:0]  blitterPixelData13;
  wire [7:0]  blitterPixelData14;
  wire [7:0]  blitterPixelData15;

  wire spriteEnabled = (|spriteReg_cols) & (|spriteReg_rows);
  wire is8bpp = &io_ctrl_format;
  wire [5:0] tileRomBurstLength = is8bpp ? 6'h20 : 6'h10;
  wire [3:0] tileRomAddrShift = is8bpp ? 4'h8 : 4'h7;
  wire [17:0] tileRomTileIndex = spriteReg_code + {2'b00, tileCounter};
  wire [32:0] tileRomAddr = {15'h0000, tileRomTileIndex} << tileRomAddrShift;

  wire tileCounterWrap = (tileCounter == (numTilesReg - 16'h0001)) | (numTilesReg == 16'h0000);
  wire tileRomRead = (stateReg == STATE_PENDING) & ~readPendingReg & (fifoCount < 7'h21);
  wire effectiveRead = tileRomRead & io_ctrl_tileRom_wait_n;
  wire acceptedFrameStart = io_ctrl_start & (stateReg == STATE_IDLE);
  wire frameDone = (stateReg == STATE_DONE) & ~blitterBusy;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  wire acceptedBlitterConfig = blitterConfigValid & blitterConfigReady;
  wire debugAbcSlot160 = spriteCounter == 10'h160;
  wire debugAbcSlot161 = spriteCounter == 10'h161;
  wire debugAbcSlot162 = spriteCounter == 10'h162;
  wire [2:0] debugAbcSlotMatchFlags = {
    debugAbcSlot162,
    debugAbcSlot161,
    debugAbcSlot160
  };
  wire debugAbcCodeA = spriteReg_code == 18'h00041;
  wire debugAbcCodeB = spriteReg_code == 18'h00042;
  wire debugAbcCodeC = spriteReg_code == 18'h00043;
  wire [2:0] debugAbcExactMatchFlags = {
    debugAbcSlot162 & debugAbcCodeC,
    debugAbcSlot161 & debugAbcCodeB,
    debugAbcSlot160 & debugAbcCodeA
  };
`endif

  wire [17:0] normalFixedPosX = {io_ctrl_vram_dout[47:32], 2'b00};
  wire [17:0] normalIntegerPosX = {io_ctrl_vram_dout[41:32], 8'h00};
  wire [17:0] zoomFixedPosX = {io_ctrl_vram_dout[15:0], 2'b00};
  wire [17:0] zoomIntegerPosX = {io_ctrl_vram_dout[9:0], 8'h00};
  wire [17:0] normalFixedPosY = {io_ctrl_vram_dout[63:48], 2'b00};
  wire [17:0] normalIntegerPosY = {io_ctrl_vram_dout[57:48], 8'h00};
  wire [17:0] zoomFixedPosY = {io_ctrl_vram_dout[31:16], 2'b00};
  wire [17:0] zoomIntegerPosY = {io_ctrl_vram_dout[25:16], 8'h00};

  reg [2:0] nextState;

  always @(*) begin
    nextState = stateReg;

    case (stateReg)
      STATE_IDLE:
        nextState = io_ctrl_start ? STATE_LOAD : STATE_IDLE;
      STATE_LOAD:
        nextState = STATE_LATCH;
      STATE_LATCH:
        nextState = STATE_CHECK;
      STATE_CHECK:
        nextState = spriteEnabled ? STATE_READY : STATE_NEXT;
      STATE_READY:
        nextState = blitterConfigReady ? STATE_PENDING : STATE_READY;
      STATE_PENDING:
        nextState = (effectiveRead & tileCounterWrap) ? STATE_NEXT : STATE_PENDING;
      STATE_NEXT:
        nextState = (&spriteCounter) ? STATE_DONE : STATE_LOAD;
      default:
        nextState = (~blitterBusy) ? STATE_IDLE : STATE_DONE;
    endcase
  end

  always @(posedge clock) begin
    if (reset) begin
      stateReg <= STATE_IDLE;
      readPendingReg <= 1'b0;
      spriteCounter <= 10'h000;
      tileCounter <= 16'h0000;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
      debugAbcExactFlags <= 3'b000;
      debugAbcExactFlagsLatched <= 3'b000;
      debugAbcWriteFlags <= 3'b000;
      debugAbcWriteFlagsLatched <= 3'b000;
      debugAbcSlotFlags <= 3'b000;
      debugAbcSlotFlagsLatched <= 3'b000;
      debugAbcActiveFlags <= 3'b000;
      debugAbcExactHistory <= 8'h00;
      debugAbcWriteHistory <= 8'h00;
      debugAbcSlot160Code <= 8'h00;
      debugAbcSlot161Code <= 8'h00;
      debugAbcSlot162Code <= 8'h00;
      debugAbcSlot160CodeLatched <= 8'h00;
      debugAbcSlot161CodeLatched <= 8'h00;
      debugAbcSlot162CodeLatched <= 8'h00;
`endif
      frameReadyReg <= 1'b0;
    end
    else begin
      stateReg <= nextState;
      readPendingReg <= ~io_ctrl_tileRom_burstDone & (effectiveRead | readPendingReg);

      if (stateReg == STATE_NEXT)
        spriteCounter <= spriteCounter + 10'h001;

      if (effectiveRead)
        tileCounter <= tileCounterWrap ? 16'h0000 : tileCounter + 16'h0001;

      if (acceptedFrameStart) begin
        frameReadyReg <= 1'b0;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
        debugAbcExactFlagsLatched <= debugAbcExactFlags;
        debugAbcWriteFlagsLatched <= debugAbcWriteFlags;
        debugAbcSlotFlagsLatched <= debugAbcSlotFlags;
        debugAbcExactHistory <= {
          debugAbcExactHistory[6:0],
          debugAbcExactFlags == 3'b111
        };
        debugAbcWriteHistory <= {
          debugAbcWriteHistory[6:0],
          debugAbcWriteFlags == 3'b111
        };
        debugAbcSlot160CodeLatched <= debugAbcSlot160Code;
        debugAbcSlot161CodeLatched <= debugAbcSlot161Code;
        debugAbcSlot162CodeLatched <= debugAbcSlot162Code;
        debugAbcExactFlags <= 3'b000;
        debugAbcWriteFlags <= 3'b000;
        debugAbcSlotFlags <= 3'b000;
        debugAbcActiveFlags <= 3'b000;
        debugAbcSlot160Code <= 8'h00;
        debugAbcSlot161Code <= 8'h00;
        debugAbcSlot162Code <= 8'h00;
`endif
      end

`ifdef CAVE_ENABLE_DEBUG_OVERLAY
      if (stateReg == STATE_CHECK) begin
        debugAbcSlotFlags <= debugAbcSlotFlags | (debugAbcSlotMatchFlags & {3{spriteEnabled}});

        if (debugAbcSlot160)
          debugAbcSlot160Code <= spriteReg_code[7:0];
        if (debugAbcSlot161)
          debugAbcSlot161Code <= spriteReg_code[7:0];
        if (debugAbcSlot162)
          debugAbcSlot162Code <= spriteReg_code[7:0];
      end

      if (acceptedBlitterConfig) begin
        debugAbcActiveFlags <= debugAbcExactMatchFlags;
        debugAbcExactFlags <= debugAbcExactFlags | debugAbcExactMatchFlags;
      end

      if (io_frameBuffer_wr)
        debugAbcWriteFlags <= debugAbcWriteFlags | debugAbcActiveFlags;
`endif

      if (frameDone)
        frameReadyReg <= 1'b1;
    end

    if (stateReg == STATE_LATCH) begin
      spriteReg_priority <= io_ctrl_zoom ? io_ctrl_vram_dout[37:36] : io_ctrl_vram_dout[5:4];
      spriteReg_colorCode <= io_ctrl_zoom ? io_ctrl_vram_dout[45:40] : io_ctrl_vram_dout[13:8];
      spriteReg_code <=
        io_ctrl_zoom
          ? {io_ctrl_vram_dout[33:32], io_ctrl_vram_dout[63:48]}
          : {io_ctrl_vram_dout[1:0], io_ctrl_vram_dout[31:16]};
      spriteReg_hFlip <= io_ctrl_zoom ? io_ctrl_vram_dout[35] : io_ctrl_vram_dout[3];
      spriteReg_vFlip <= io_ctrl_zoom ? io_ctrl_vram_dout[34] : io_ctrl_vram_dout[2];
      spriteReg_pos_x <=
        io_ctrl_zoom
          ? (io_ctrl_regs_fixed ? zoomFixedPosX : zoomIntegerPosX)
          : (io_ctrl_regs_fixed ? normalFixedPosX : normalIntegerPosX);
      spriteReg_pos_y <=
        io_ctrl_zoom
          ? (io_ctrl_regs_fixed ? zoomFixedPosY : zoomIntegerPosY)
          : (io_ctrl_regs_fixed ? normalFixedPosY : normalIntegerPosY);
      spriteReg_cols <= io_ctrl_zoom ? {3'h0, io_ctrl_vram_dout[108:104]} : {3'h0, io_ctrl_vram_dout[76:72]};
      spriteReg_rows <= io_ctrl_zoom ? {3'h0, io_ctrl_vram_dout[100:96]} : {3'h0, io_ctrl_vram_dout[68:64]};
      spriteReg_zoom_x <= io_ctrl_zoom ? io_ctrl_vram_dout[79:64] : 16'h0100;
      spriteReg_zoom_y <= io_ctrl_zoom ? io_ctrl_vram_dout[95:80] : 16'h0100;
    end

    if (stateReg == STATE_CHECK)
      numTilesReg <= {8'h00, spriteReg_cols} * {8'h00, spriteReg_rows};
  end

  CaveSyncQueue #(
    .ADDR_WIDTH (6),
    .DATA_WIDTH (64),
    .DEPTH      (64)
  ) fifo (
    .clock        (clock),
    .reset        (reset),
    .io_enq_ready (),
    .io_enq_valid (io_ctrl_tileRom_valid),
    .io_enq_bits  (io_ctrl_tileRom_dout),
    .io_deq_ready (fifoDeqReady),
    .io_deq_valid (fifoDeqValid),
    .io_deq_bits  (fifoDeqBits),
    .io_count     (fifoCount),
    .io_flush     (fifoFlush)
  );

  SpriteBlitter blitter (
    .clock                           (clock),
    .reset                           (reset),
    .io_enable                       (io_ctrl_enable),
    .io_busy                         (blitterBusy),
    .io_config_ready                 (blitterConfigReady),
    .io_config_valid                 (blitterConfigValid),
    .io_config_bits_sprite_priority  (spriteReg_priority),
    .io_config_bits_sprite_colorCode (spriteReg_colorCode),
    .io_config_bits_sprite_hFlip     (spriteReg_hFlip),
    .io_config_bits_sprite_vFlip     (spriteReg_vFlip),
    .io_config_bits_sprite_pos_x     (spriteReg_pos_x),
    .io_config_bits_sprite_pos_y     (spriteReg_pos_y),
    .io_config_bits_sprite_cols      (spriteReg_cols),
    .io_config_bits_sprite_rows      (spriteReg_rows),
    .io_config_bits_sprite_zoom_x    (spriteReg_zoom_x),
    .io_config_bits_sprite_zoom_y    (spriteReg_zoom_y),
    .io_config_bits_hFlip            (io_ctrl_regs_hFlip),
    .io_video_regs_size_x            (io_video_regs_size_x),
    .io_video_regs_size_y            (io_video_regs_size_y),
    .io_pixelData_ready              (blitterPixelDataReady),
    .io_pixelData_valid              (blitterPixelDataValid),
    .io_pixelData_bits_0             (blitterPixelData0),
    .io_pixelData_bits_1             (blitterPixelData1),
    .io_pixelData_bits_2             (blitterPixelData2),
    .io_pixelData_bits_3             (blitterPixelData3),
    .io_pixelData_bits_4             (blitterPixelData4),
    .io_pixelData_bits_5             (blitterPixelData5),
    .io_pixelData_bits_6             (blitterPixelData6),
    .io_pixelData_bits_7             (blitterPixelData7),
    .io_pixelData_bits_8             (blitterPixelData8),
    .io_pixelData_bits_9             (blitterPixelData9),
    .io_pixelData_bits_10            (blitterPixelData10),
    .io_pixelData_bits_11            (blitterPixelData11),
    .io_pixelData_bits_12            (blitterPixelData12),
    .io_pixelData_bits_13            (blitterPixelData13),
    .io_pixelData_bits_14            (blitterPixelData14),
    .io_pixelData_bits_15            (blitterPixelData15),
    .io_frameBuffer_wr               (io_frameBuffer_wr),
    .io_frameBuffer_addr             (io_frameBuffer_addr),
    .io_frameBuffer_din              (io_frameBuffer_din),
    .io_frameBuffer_wait_n           (io_frameBuffer_wait_n)
  );

  SpriteDecoder decoder (
    .clock                (clock),
    .reset                (reset),
    .io_format            (io_ctrl_format),
    .io_tileRom_ready     (fifoDeqReady),
    .io_tileRom_valid     (fifoDeqValid),
    .io_tileRom_bits      (fifoDeqBits),
    .io_pixelData_ready   (blitterPixelDataReady),
    .io_pixelData_valid   (blitterPixelDataValid),
    .io_pixelData_bits_0  (blitterPixelData0),
    .io_pixelData_bits_1  (blitterPixelData1),
    .io_pixelData_bits_2  (blitterPixelData2),
    .io_pixelData_bits_3  (blitterPixelData3),
    .io_pixelData_bits_4  (blitterPixelData4),
    .io_pixelData_bits_5  (blitterPixelData5),
    .io_pixelData_bits_6  (blitterPixelData6),
    .io_pixelData_bits_7  (blitterPixelData7),
    .io_pixelData_bits_8  (blitterPixelData8),
    .io_pixelData_bits_9  (blitterPixelData9),
    .io_pixelData_bits_10 (blitterPixelData10),
    .io_pixelData_bits_11 (blitterPixelData11),
    .io_pixelData_bits_12 (blitterPixelData12),
    .io_pixelData_bits_13 (blitterPixelData13),
    .io_pixelData_bits_14 (blitterPixelData14),
    .io_pixelData_bits_15 (blitterPixelData15)
  );

  assign io_ctrl_vram_rd = stateReg == STATE_LOAD;
  assign io_ctrl_vram_addr = {io_ctrl_regs_bank, spriteCounter};
  assign io_ctrl_tileRom_rd = tileRomRead;
  assign io_ctrl_tileRom_addr = tileRomAddr[31:0];
  assign io_ctrl_tileRom_burstLength = {2'b00, tileRomBurstLength};
  assign io_ctrl_frameReady = frameReadyReg;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  assign io_debug = {
    debugAbcSlot162CodeLatched,
    debugAbcSlot161CodeLatched,
    debugAbcSlot160CodeLatched,
    debugAbcWriteHistory,
    debugAbcExactHistory,
    {5'b00000, debugAbcSlotFlagsLatched},
    {5'b00000, debugAbcWriteFlagsLatched},
    {5'b00000, debugAbcExactFlagsLatched}
  };
`else
  assign io_debug = 64'd0;
`endif
endmodule
