// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Video pixel pipeline: layers, sprites, palette lookup, and framebuffer writeout.
module GPU(
  input          clock,
  input          reset,
  input          io_videoClock,
  input          io_layerCtrl_0_enable,
  input  [1:0]   io_layerCtrl_0_format,
  input          io_layerCtrl_0_regs_tileSize,
  input          io_layerCtrl_0_regs_enable,
  input          io_layerCtrl_0_regs_flipX,
  input          io_layerCtrl_0_regs_flipY,
  input          io_layerCtrl_0_regs_rowScrollEnable,
  input          io_layerCtrl_0_regs_rowSelectEnable,
  input  [8:0]   io_layerCtrl_0_regs_scroll_x,
  input  [8:0]   io_layerCtrl_0_regs_scroll_y,
  output [11:0]  io_layerCtrl_0_vram8x8_addr,
  input  [31:0]  io_layerCtrl_0_vram8x8_dout,
  output [9:0]   io_layerCtrl_0_vram16x16_addr,
  input  [31:0]  io_layerCtrl_0_vram16x16_dout,
  output [8:0]   io_layerCtrl_0_lineRam_addr,
  input  [31:0]  io_layerCtrl_0_lineRam_dout,
  output         io_layerCtrl_0_tileRom_rd,
  output [31:0]  io_layerCtrl_0_tileRom_addr,
  input  [63:0]  io_layerCtrl_0_tileRom_dout,
  input          io_layerCtrl_1_enable,
  input  [1:0]   io_layerCtrl_1_format,
  input          io_layerCtrl_1_regs_tileSize,
  input          io_layerCtrl_1_regs_enable,
  input          io_layerCtrl_1_regs_flipX,
  input          io_layerCtrl_1_regs_flipY,
  input          io_layerCtrl_1_regs_rowScrollEnable,
  input          io_layerCtrl_1_regs_rowSelectEnable,
  input  [8:0]   io_layerCtrl_1_regs_scroll_x,
  input  [8:0]   io_layerCtrl_1_regs_scroll_y,
  output [11:0]  io_layerCtrl_1_vram8x8_addr,
  input  [31:0]  io_layerCtrl_1_vram8x8_dout,
  output [9:0]   io_layerCtrl_1_vram16x16_addr,
  input  [31:0]  io_layerCtrl_1_vram16x16_dout,
  output [8:0]   io_layerCtrl_1_lineRam_addr,
  input  [31:0]  io_layerCtrl_1_lineRam_dout,
  output         io_layerCtrl_1_tileRom_rd,
  output [31:0]  io_layerCtrl_1_tileRom_addr,
  input  [63:0]  io_layerCtrl_1_tileRom_dout,
  input          io_layerCtrl_2_enable,
  input  [1:0]   io_layerCtrl_2_format,
  input          io_layerCtrl_2_regs_tileSize,
  input          io_layerCtrl_2_regs_enable,
  input          io_layerCtrl_2_regs_flipX,
  input          io_layerCtrl_2_regs_flipY,
  input          io_layerCtrl_2_regs_rowScrollEnable,
  input          io_layerCtrl_2_regs_rowSelectEnable,
  input  [8:0]   io_layerCtrl_2_regs_scroll_x,
  input  [8:0]   io_layerCtrl_2_regs_scroll_y,
  output [11:0]  io_layerCtrl_2_vram8x8_addr,
  input  [31:0]  io_layerCtrl_2_vram8x8_dout,
  output [9:0]   io_layerCtrl_2_vram16x16_addr,
  input  [31:0]  io_layerCtrl_2_vram16x16_dout,
  output [8:0]   io_layerCtrl_2_lineRam_addr,
  input  [31:0]  io_layerCtrl_2_lineRam_dout,
  output         io_layerCtrl_2_tileRom_rd,
  output [31:0]  io_layerCtrl_2_tileRom_addr,
  input  [63:0]  io_layerCtrl_2_tileRom_dout,
  input          io_pwrinst2Layer2_enable,
  input          io_pwrinst2Layer2_regs_tileSize,
  input          io_pwrinst2Layer2_regs_enable,
  input          io_pwrinst2Layer2_regs_flipX,
  input          io_pwrinst2Layer2_regs_flipY,
  input          io_pwrinst2Layer2_regs_rowScrollEnable,
  input          io_pwrinst2Layer2_regs_rowSelectEnable,
  input  [8:0]   io_pwrinst2Layer2_regs_scroll_x,
  input  [8:0]   io_pwrinst2Layer2_regs_scroll_y,
  output [11:0]  io_pwrinst2Layer2_vram8x8_addr,
  input  [31:0]  io_pwrinst2Layer2_vram8x8_dout,
  output [9:0]   io_pwrinst2Layer2_vram16x16_addr,
  input  [31:0]  io_pwrinst2Layer2_vram16x16_dout,
  output [8:0]   io_pwrinst2Layer2_lineRam_addr,
  input  [31:0]  io_pwrinst2Layer2_lineRam_dout,
  output         io_pwrinst2Layer2_tileRom_rd,
  output [31:0]  io_pwrinst2Layer2_tileRom_addr,
  input  [63:0]  io_pwrinst2Layer2_tileRom_dout,
  input          io_spriteCtrl_enable,
  input  [1:0]   io_spriteCtrl_format,
  input          io_spriteCtrl_pwrinst2,
  input          io_spriteCtrl_start,
  input          io_spriteCtrl_zoom,
  input  [8:0]   io_spriteCtrl_regs_offset_x,
  input  [8:0]   io_spriteCtrl_regs_offset_y,
  input  [1:0]   io_spriteCtrl_regs_bank,
  input          io_spriteCtrl_regs_fixed,
  input          io_spriteCtrl_regs_hFlip,
  output         io_spriteCtrl_vram_rd,
  output [11:0]  io_spriteCtrl_vram_addr,
  input  [127:0] io_spriteCtrl_vram_dout,
  output         io_spriteCtrl_tileRom_rd,
  output [31:0]  io_spriteCtrl_tileRom_addr,
  input  [63:0]  io_spriteCtrl_tileRom_dout,
  input          io_spriteCtrl_tileRom_wait_n,
  input          io_spriteCtrl_tileRom_valid,
  output [7:0]   io_spriteCtrl_tileRom_burstLength,
  input          io_spriteCtrl_tileRom_burstDone,
  input  [8:0]   io_gameConfig_granularity,
  input  [1:0]   io_gameConfig_layer_0_paletteBank,
  input  [1:0]   io_gameConfig_layer_1_paletteBank,
  input  [1:0]   io_gameConfig_layer_2_paletteBank,
  input          io_options_rotate,
  input          io_options_rotateClockwise,
  input          io_options_flipVideo,
  input          io_video_clockEnable,
  input          io_video_displayEnable,
  input  [8:0]   io_video_pos_x,
  input  [8:0]   io_video_pos_y,
  input          io_video_vBlank,
  input  [8:0]   io_video_regs_size_x,
  input  [8:0]   io_video_regs_size_y,
  output [8:0]   io_spriteLineBuffer_addr,
  input  [15:0]  io_spriteLineBuffer_dout,
  output         io_spriteFrameBuffer_wr,
  output [16:0]  io_spriteFrameBuffer_addr,
  output [15:0]  io_spriteFrameBuffer_din,
  input          io_spriteFrameBuffer_wait_n,
  output         io_spriteCtrl_frameReady,
  output         io_systemFrameBuffer_wr,
  output [16:0]  io_systemFrameBuffer_addr,
  output [31:0]  io_systemFrameBuffer_din,
  output [14:0]  io_paletteRam_addr,
  input  [15:0]  io_paletteRam_dout,
  output [23:0]  io_rgb
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  ,
  output [63:0]  io_debug_video,
  output [63:0]  io_debug_readout,
  output [23:0]  io_debug_source_rgb
`endif
);
  wire [1:0]  layer0PenPriority;
  wire [5:0]  layer0PenPalette;
  wire [7:0]  layer0PenColor;
  wire [1:0]  layer1PenPriority;
  wire [5:0]  layer1PenPalette;
  wire [7:0]  layer1PenColor;
  wire [1:0]  layer2PenPriority;
  wire [5:0]  layer2PenPalette;
  wire [7:0]  layer2PenColor;
  wire [1:0]  pwrinst2Layer2PenPriority;
  wire [5:0]  pwrinst2Layer2PenPalette;
  wire [7:0]  pwrinst2Layer2PenColor;

  wire [1:0]  spritePenPriority = io_spriteLineBuffer_dout[15:14];
  wire [5:0]  spritePenPalette = io_spriteLineBuffer_dout[13:8];
  wire [7:0]  spritePenColor = io_spriteLineBuffer_dout[7:0];
  wire [15:0] paletteColor;
  wire [23:0] videoRgb888 = {
    paletteColor[9:5],
    paletteColor[9:7],
    paletteColor[14:10],
    paletteColor[14:12],
    paletteColor[4:0],
    paletteColor[4:2]
  };
  wire [23:0] framebufferRgb888 = {
    paletteColor[4:0],
    paletteColor[4:2],
    paletteColor[14:10],
    paletteColor[14:12],
    paletteColor[9:5],
    paletteColor[9:7]
  };

  wire [8:0] flippedVideoX = io_video_regs_size_x - io_video_pos_x - 9'h001;
  wire [8:0] flippedVideoY = io_video_regs_size_y - io_video_pos_y - 9'h001;
  wire [8:0] layerVideoPosX = io_spriteCtrl_pwrinst2 ? (io_video_pos_x + 9'h070) : io_video_pos_x;
  wire [17:0] videoX = {9'h000, io_video_pos_x};
  wire [17:0] videoY = {9'h000, io_video_pos_y};
  wire [17:0] videoSizeX = {9'h000, io_video_regs_size_x};
  wire [17:0] videoSizeY = {9'h000, io_video_regs_size_y};
  wire [17:0] flippedX = {9'h000, flippedVideoX};
  wire [17:0] flippedY = {9'h000, flippedVideoY};

  wire [17:0] normalFramebufferAddr =
    io_options_flipVideo
      ? (flippedY * videoSizeX) + flippedX
      : (videoY * videoSizeX) + videoX;
  wire        rotateClockwise = io_options_rotateClockwise ^ io_options_flipVideo;
  wire [17:0] rotatedFramebufferAddr =
    rotateClockwise
      ? (videoX * videoSizeY) + flippedY
      : (flippedX * videoSizeY) + videoY;
  wire [17:0] nextSystemFramebufferAddr =
    io_options_rotate ? rotatedFramebufferAddr : normalFramebufferAddr;

  reg        systemFramebufferWrReg;
  reg [17:0] systemFramebufferAddrReg;
  reg [31:0] systemFramebufferDinReg;

  wire activeDisplayPixel = io_video_clockEnable & io_video_displayEnable;
  wire [63:0] spriteDebug;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  wire [3:0]  mixerDebugSelectedPen;
  wire [5:0]  mixerDebugSelectedPalette;
  wire [7:0]  mixerDebugSelectedColor;
  wire [3:0]  mixerDebugVisibleMask;

  reg          debugSpriteVisibleReg;
  reg  [5:0]  debugSpritePaletteReg;
  reg  [7:0]  debugSpriteColorReg;
  reg          debugVideoVBlankReg;
  reg  [7:0]  debugTopRgbFrame;
  reg  [7:0]  debugMidRgbFrame;
  reg  [7:0]  debugMidLeftRgbFrame;
  reg  [7:0]  debugMidRightRgbFrame;
  reg  [7:0]  debugPrevMidRgb;
  reg  [7:0]  debugTopMixFrame;
  reg  [7:0]  debugTopPaletteColorFrame;
  reg  [7:0]  debugTopPaletteAddrFrame;
  reg          debugTopSampleValid;
  reg          debugMidSampleValid;
  reg          debugMidLeftSampleValid;
  reg          debugMidRightSampleValid;
  reg  [7:0]  debugFlashFlagsLatched;
  reg  [7:0]  debugTopRgbLatched;
  reg  [7:0]  debugMidRgbLatched;
  reg  [7:0]  debugPrevMidRgbLatched;
  reg  [7:0]  debugTopMixLatched;
  reg  [7:0]  debugTopPaletteColorLatched;
  reg  [7:0]  debugTopPaletteAddrLatched;
  reg  [7:0]  debugFlashMismatchHistory;
  wire [63:0] layer2DebugDesc;
  wire [63:0] layer2DebugRom;
  wire [7:0]  debugSpriteShade = debugSpriteColorReg ^ {debugSpritePaletteReg, 2'b00};
  wire        debugVideoVBlankRising = io_video_vBlank & ~debugVideoVBlankReg;
  wire        debugOutputBright =
    (|videoRgb888[23:21]) |
    (|videoRgb888[15:13]) |
    (|videoRgb888[7:5]);
  wire [7:0]  debugRgbCompact = {videoRgb888[23:21], videoRgb888[15:13], videoRgb888[7:6]};
  wire [8:0]  debugMidX = {1'b0, io_video_regs_size_x[8:1]};
  wire [8:0]  debugLeftX = {2'b00, io_video_regs_size_x[8:2]};
  wire [8:0]  debugRightX = debugMidX + debugLeftX;
  wire [8:0]  debugTopY = 9'h008;
  wire [8:0]  debugMidY = {1'b0, io_video_regs_size_y[8:1]};
  wire        debugTopSamplePixel =
    activeDisplayPixel &
    (io_video_pos_y == debugTopY) &
    (io_video_pos_x == debugMidX);
  wire        debugMidSamplePixel =
    activeDisplayPixel &
    (io_video_pos_y == debugMidY) &
    (io_video_pos_x == debugMidX);
  wire        debugMidLeftSamplePixel =
    activeDisplayPixel &
    (io_video_pos_y == debugMidY) &
    (io_video_pos_x == debugLeftX);
  wire        debugMidRightSamplePixel =
    activeDisplayPixel &
    (io_video_pos_y == debugMidY) &
    (io_video_pos_x == debugRightX);
  wire        debugMidUniform =
    debugMidSampleValid &
    debugMidLeftSampleValid &
    debugMidRightSampleValid &
    (debugMidRgbFrame == debugMidLeftRgbFrame) &
    (debugMidRgbFrame == debugMidRightRgbFrame);
  wire        debugTopVsMidMismatch =
    debugTopSampleValid &
    debugMidUniform &
    (debugTopRgbFrame != debugMidRgbFrame);
  wire        debugTopMatchesPrevMid =
    debugTopVsMidMismatch & (debugTopRgbFrame == debugPrevMidRgb);
  wire [23:0] debugFillRgb =
    (io_video_pos_x[3] ^ io_video_pos_y[3]) ? 24'h181818 : 24'h080808;
  wire [23:0] debugRawSpriteRgb =
    debugSpriteVisibleReg
      ? {8'h80 | debugSpriteShade[7:1], 8'h20 | debugSpriteShade[6:2], 8'h20}
      : debugFillRgb;
`endif

  always @(posedge io_videoClock) begin
    systemFramebufferWrReg <= activeDisplayPixel;
    systemFramebufferAddrReg <= nextSystemFramebufferAddr;
    systemFramebufferDinReg <= {8'h00, framebufferRgb888};
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
    if (io_video_clockEnable) begin
      debugSpriteVisibleReg <= io_video_displayEnable & (|spritePenColor);
      debugSpritePaletteReg <= spritePenPalette;
      debugSpriteColorReg <= spritePenColor;
      debugVideoVBlankReg <= io_video_vBlank;

      if (reset) begin
        debugTopRgbFrame <= 8'h00;
        debugMidRgbFrame <= 8'h00;
        debugMidLeftRgbFrame <= 8'h00;
        debugMidRightRgbFrame <= 8'h00;
        debugPrevMidRgb <= 8'h00;
        debugTopMixFrame <= 8'h00;
        debugTopPaletteColorFrame <= 8'h00;
        debugTopPaletteAddrFrame <= 8'h00;
        debugTopSampleValid <= 1'b0;
        debugMidSampleValid <= 1'b0;
        debugMidLeftSampleValid <= 1'b0;
        debugMidRightSampleValid <= 1'b0;
        debugFlashFlagsLatched <= 8'h00;
        debugTopRgbLatched <= 8'h00;
        debugMidRgbLatched <= 8'h00;
        debugPrevMidRgbLatched <= 8'h00;
        debugTopMixLatched <= 8'h00;
        debugTopPaletteColorLatched <= 8'h00;
        debugTopPaletteAddrLatched <= 8'h00;
        debugFlashMismatchHistory <= 8'h00;
      end
      else if (debugVideoVBlankRising) begin
        debugFlashMismatchHistory <= {
          debugFlashMismatchHistory[6:0],
          debugTopVsMidMismatch
        };
        if (debugTopVsMidMismatch) begin
          debugFlashFlagsLatched <= {
            debugTopMatchesPrevMid,
            debugMidUniform,
            debugTopRgbFrame == debugMidRgbFrame,
            debugTopSampleValid,
            debugMidSampleValid,
            debugMidLeftSampleValid,
            debugMidRightSampleValid,
            1'b1
          };
          debugTopRgbLatched <= debugTopRgbFrame;
          debugMidRgbLatched <= debugMidRgbFrame;
          debugPrevMidRgbLatched <= debugPrevMidRgb;
          debugTopMixLatched <= debugTopMixFrame;
          debugTopPaletteColorLatched <= debugTopPaletteColorFrame;
          debugTopPaletteAddrLatched <= debugTopPaletteAddrFrame;
        end
        if (debugMidUniform)
          debugPrevMidRgb <= debugMidRgbFrame;
        debugTopRgbFrame <= 8'h00;
        debugMidRgbFrame <= 8'h00;
        debugMidLeftRgbFrame <= 8'h00;
        debugMidRightRgbFrame <= 8'h00;
        debugTopMixFrame <= 8'h00;
        debugTopPaletteColorFrame <= 8'h00;
        debugTopPaletteAddrFrame <= 8'h00;
        debugTopSampleValid <= 1'b0;
        debugMidSampleValid <= 1'b0;
        debugMidLeftSampleValid <= 1'b0;
        debugMidRightSampleValid <= 1'b0;
      end
      else begin
        if (debugTopSamplePixel & debugOutputBright) begin
          debugTopRgbFrame <= debugRgbCompact;
          debugTopMixFrame <= {mixerDebugSelectedPen, mixerDebugVisibleMask};
          debugTopPaletteColorFrame <= {mixerDebugSelectedPalette[3:0], mixerDebugSelectedColor[3:0]};
          debugTopPaletteAddrFrame <= io_paletteRam_addr[7:0];
          debugTopSampleValid <= 1'b1;
        end
        if (debugMidSamplePixel & debugOutputBright) begin
          debugMidRgbFrame <= debugRgbCompact;
          debugMidSampleValid <= 1'b1;
        end
        if (debugMidLeftSamplePixel & debugOutputBright) begin
          debugMidLeftRgbFrame <= debugRgbCompact;
          debugMidLeftSampleValid <= 1'b1;
        end
        if (debugMidRightSamplePixel & debugOutputBright) begin
          debugMidRightRgbFrame <= debugRgbCompact;
          debugMidRightSampleValid <= 1'b1;
        end
      end
    end
`endif
  end

  SpriteProcessor spriteProcessor (
    .clock                       (clock),
    .reset                       (reset),
    .io_ctrl_enable              (io_spriteCtrl_enable),
    .io_ctrl_format              (io_spriteCtrl_format),
    .io_ctrl_pwrinst2            (io_spriteCtrl_pwrinst2),
    .io_ctrl_start               (io_spriteCtrl_start),
    .io_ctrl_zoom                (io_spriteCtrl_zoom),
    .io_ctrl_regs_bank           (io_spriteCtrl_regs_bank),
    .io_ctrl_regs_fixed          (io_spriteCtrl_regs_fixed),
    .io_ctrl_regs_hFlip          (io_spriteCtrl_regs_hFlip),
    .io_ctrl_vram_rd             (io_spriteCtrl_vram_rd),
    .io_ctrl_vram_addr           (io_spriteCtrl_vram_addr),
    .io_ctrl_vram_dout           (io_spriteCtrl_vram_dout),
    .io_ctrl_tileRom_rd          (io_spriteCtrl_tileRom_rd),
    .io_ctrl_tileRom_addr        (io_spriteCtrl_tileRom_addr),
    .io_ctrl_tileRom_dout        (io_spriteCtrl_tileRom_dout),
    .io_ctrl_tileRom_wait_n      (io_spriteCtrl_tileRom_wait_n),
    .io_ctrl_tileRom_valid       (io_spriteCtrl_tileRom_valid),
    .io_ctrl_tileRom_burstLength (io_spriteCtrl_tileRom_burstLength),
    .io_ctrl_tileRom_burstDone   (io_spriteCtrl_tileRom_burstDone),
    .io_video_regs_size_x        (io_video_regs_size_x),
    .io_video_regs_size_y        (io_video_regs_size_y),
    .io_frameBuffer_wr           (io_spriteFrameBuffer_wr),
    .io_frameBuffer_addr         (io_spriteFrameBuffer_addr),
    .io_frameBuffer_din          (io_spriteFrameBuffer_din),
    .io_frameBuffer_wait_n       (io_spriteFrameBuffer_wait_n),
    .io_ctrl_frameReady          (io_spriteCtrl_frameReady),
    .io_debug                    (spriteDebug)
  );

  CaveLayerProcessor #(
    .LAYER_OFFSET_LARGE (5'h12),
    .LAYER_OFFSET_SMALL (5'h0A)
  ) layerProcessor_0 (
    .clock                        (io_videoClock),
    .io_ctrl_enable               (io_layerCtrl_0_enable),
    .io_ctrl_format               (io_layerCtrl_0_format),
    .io_ctrl_zero4bppPenF         (1'b0),
    .io_ctrl_regs_tileSize        (io_layerCtrl_0_regs_tileSize),
    .io_ctrl_regs_enable          (io_layerCtrl_0_regs_enable),
    .io_ctrl_regs_flipX           (io_layerCtrl_0_regs_flipX),
    .io_ctrl_regs_flipY           (io_layerCtrl_0_regs_flipY),
    .io_ctrl_regs_rowScrollEnable (io_layerCtrl_0_regs_rowScrollEnable),
    .io_ctrl_regs_rowSelectEnable (io_layerCtrl_0_regs_rowSelectEnable),
    .io_ctrl_regs_scroll_x        (io_layerCtrl_0_regs_scroll_x),
    .io_ctrl_regs_scroll_y        (io_layerCtrl_0_regs_scroll_y),
    .io_ctrl_vram8x8_addr         (io_layerCtrl_0_vram8x8_addr),
    .io_ctrl_vram8x8_dout         (io_layerCtrl_0_vram8x8_dout),
    .io_ctrl_vram16x16_addr       (io_layerCtrl_0_vram16x16_addr),
    .io_ctrl_vram16x16_dout       (io_layerCtrl_0_vram16x16_dout),
    .io_ctrl_lineRam_addr         (io_layerCtrl_0_lineRam_addr),
    .io_ctrl_lineRam_dout         (io_layerCtrl_0_lineRam_dout),
    .io_ctrl_tileRom_rd           (io_layerCtrl_0_tileRom_rd),
    .io_ctrl_tileRom_addr         (io_layerCtrl_0_tileRom_addr),
    .io_ctrl_tileRom_dout         (io_layerCtrl_0_tileRom_dout),
    .io_video_clockEnable         (io_video_clockEnable),
    .io_video_pos_x               (layerVideoPosX),
    .io_video_pos_y               (io_video_pos_y),
    .io_video_vBlank              (io_video_vBlank),
    .io_video_regs_size_x         (io_video_regs_size_x),
    .io_video_regs_size_y         (io_video_regs_size_y),
    .io_spriteOffset_x            (io_spriteCtrl_regs_offset_x),
    .io_spriteOffset_y            (io_spriteCtrl_regs_offset_y),
    .io_pen_priority              (layer0PenPriority),
    .io_pen_palette               (layer0PenPalette),
    .io_pen_color                 (layer0PenColor)
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
    ,
    .io_debug_desc                (),
    .io_debug_rom                 ()
`endif
  );

  CaveLayerProcessor #(
    .LAYER_OFFSET_LARGE (5'h11),
    .LAYER_OFFSET_SMALL (5'h09)
  ) layerProcessor_1 (
    .clock                        (io_videoClock),
    .io_ctrl_enable               (io_layerCtrl_1_enable),
    .io_ctrl_format               (io_layerCtrl_1_format),
    .io_ctrl_zero4bppPenF         (1'b0),
    .io_ctrl_regs_tileSize        (io_layerCtrl_1_regs_tileSize),
    .io_ctrl_regs_enable          (io_layerCtrl_1_regs_enable),
    .io_ctrl_regs_flipX           (io_layerCtrl_1_regs_flipX),
    .io_ctrl_regs_flipY           (io_layerCtrl_1_regs_flipY),
    .io_ctrl_regs_rowScrollEnable (io_layerCtrl_1_regs_rowScrollEnable),
    .io_ctrl_regs_rowSelectEnable (io_layerCtrl_1_regs_rowSelectEnable),
    .io_ctrl_regs_scroll_x        (io_layerCtrl_1_regs_scroll_x),
    .io_ctrl_regs_scroll_y        (io_layerCtrl_1_regs_scroll_y),
    .io_ctrl_vram8x8_addr         (io_layerCtrl_1_vram8x8_addr),
    .io_ctrl_vram8x8_dout         (io_layerCtrl_1_vram8x8_dout),
    .io_ctrl_vram16x16_addr       (io_layerCtrl_1_vram16x16_addr),
    .io_ctrl_vram16x16_dout       (io_layerCtrl_1_vram16x16_dout),
    .io_ctrl_lineRam_addr         (io_layerCtrl_1_lineRam_addr),
    .io_ctrl_lineRam_dout         (io_layerCtrl_1_lineRam_dout),
    .io_ctrl_tileRom_rd           (io_layerCtrl_1_tileRom_rd),
    .io_ctrl_tileRom_addr         (io_layerCtrl_1_tileRom_addr),
    .io_ctrl_tileRom_dout         (io_layerCtrl_1_tileRom_dout),
    .io_video_clockEnable         (io_video_clockEnable),
    .io_video_pos_x               (layerVideoPosX),
    .io_video_pos_y               (io_video_pos_y),
    .io_video_vBlank              (io_video_vBlank),
    .io_video_regs_size_x         (io_video_regs_size_x),
    .io_video_regs_size_y         (io_video_regs_size_y),
    .io_spriteOffset_x            (io_spriteCtrl_regs_offset_x),
    .io_spriteOffset_y            (io_spriteCtrl_regs_offset_y),
    .io_pen_priority              (layer1PenPriority),
    .io_pen_palette               (layer1PenPalette),
    .io_pen_color                 (layer1PenColor)
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
    ,
    .io_debug_desc                (),
    .io_debug_rom                 ()
`endif
  );

  CaveLayerProcessor #(
    .LAYER_OFFSET_LARGE (5'h10),
    .LAYER_OFFSET_SMALL (5'h08)
  ) layerProcessor_2 (
    .clock                        (io_videoClock),
    .io_ctrl_enable               (io_layerCtrl_2_enable),
    .io_ctrl_format               (io_layerCtrl_2_format),
    .io_ctrl_zero4bppPenF         (io_spriteCtrl_pwrinst2),
    .io_ctrl_regs_tileSize        (io_layerCtrl_2_regs_tileSize),
    .io_ctrl_regs_enable          (io_layerCtrl_2_regs_enable),
    .io_ctrl_regs_flipX           (io_layerCtrl_2_regs_flipX),
    .io_ctrl_regs_flipY           (io_layerCtrl_2_regs_flipY),
    .io_ctrl_regs_rowScrollEnable (io_layerCtrl_2_regs_rowScrollEnable),
    .io_ctrl_regs_rowSelectEnable (io_layerCtrl_2_regs_rowSelectEnable),
    .io_ctrl_regs_scroll_x        (io_layerCtrl_2_regs_scroll_x),
    .io_ctrl_regs_scroll_y        (io_layerCtrl_2_regs_scroll_y),
    .io_ctrl_vram8x8_addr         (io_layerCtrl_2_vram8x8_addr),
    .io_ctrl_vram8x8_dout         (io_layerCtrl_2_vram8x8_dout),
    .io_ctrl_vram16x16_addr       (io_layerCtrl_2_vram16x16_addr),
    .io_ctrl_vram16x16_dout       (io_layerCtrl_2_vram16x16_dout),
    .io_ctrl_lineRam_addr         (io_layerCtrl_2_lineRam_addr),
    .io_ctrl_lineRam_dout         (io_layerCtrl_2_lineRam_dout),
    .io_ctrl_tileRom_rd           (io_layerCtrl_2_tileRom_rd),
    .io_ctrl_tileRom_addr         (io_layerCtrl_2_tileRom_addr),
    .io_ctrl_tileRom_dout         (io_layerCtrl_2_tileRom_dout),
    .io_video_clockEnable         (io_video_clockEnable),
    .io_video_pos_x               (layerVideoPosX),
    .io_video_pos_y               (io_video_pos_y),
    .io_video_vBlank              (io_video_vBlank),
    .io_video_regs_size_x         (io_video_regs_size_x),
    .io_video_regs_size_y         (io_video_regs_size_y),
    .io_spriteOffset_x            (io_spriteCtrl_regs_offset_x),
    .io_spriteOffset_y            (io_spriteCtrl_regs_offset_y),
    .io_pen_priority              (layer2PenPriority),
    .io_pen_palette               (layer2PenPalette),
    .io_pen_color                 (layer2PenColor)
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
    ,
    .io_debug_desc                (layer2DebugDesc),
    .io_debug_rom                 (layer2DebugRom)
`endif
  );

  CaveLayerProcessor #(
    .LAYER_OFFSET_LARGE (5'h10),
    .LAYER_OFFSET_SMALL (5'h08)
  ) pwrinst2Layer2Processor (
    .clock                        (io_videoClock),
    .io_ctrl_enable               (io_pwrinst2Layer2_enable),
    .io_ctrl_format               (2'h1),
    .io_ctrl_zero4bppPenF         (1'b0),
    .io_ctrl_regs_tileSize        (io_pwrinst2Layer2_regs_tileSize),
    .io_ctrl_regs_enable          (io_pwrinst2Layer2_regs_enable),
    .io_ctrl_regs_flipX           (io_pwrinst2Layer2_regs_flipX),
    .io_ctrl_regs_flipY           (io_pwrinst2Layer2_regs_flipY),
    .io_ctrl_regs_rowScrollEnable (io_pwrinst2Layer2_regs_rowScrollEnable),
    .io_ctrl_regs_rowSelectEnable (io_pwrinst2Layer2_regs_rowSelectEnable),
    .io_ctrl_regs_scroll_x        (io_pwrinst2Layer2_regs_scroll_x),
    .io_ctrl_regs_scroll_y        (io_pwrinst2Layer2_regs_scroll_y),
    .io_ctrl_vram8x8_addr         (io_pwrinst2Layer2_vram8x8_addr),
    .io_ctrl_vram8x8_dout         (io_pwrinst2Layer2_vram8x8_dout),
    .io_ctrl_vram16x16_addr       (io_pwrinst2Layer2_vram16x16_addr),
    .io_ctrl_vram16x16_dout       (io_pwrinst2Layer2_vram16x16_dout),
    .io_ctrl_lineRam_addr         (io_pwrinst2Layer2_lineRam_addr),
    .io_ctrl_lineRam_dout         (io_pwrinst2Layer2_lineRam_dout),
    .io_ctrl_tileRom_rd           (io_pwrinst2Layer2_tileRom_rd),
    .io_ctrl_tileRom_addr         (io_pwrinst2Layer2_tileRom_addr),
    .io_ctrl_tileRom_dout         (io_pwrinst2Layer2_tileRom_dout),
    .io_video_clockEnable         (io_video_clockEnable),
    .io_video_pos_x               (layerVideoPosX),
    .io_video_pos_y               (io_video_pos_y),
    .io_video_vBlank              (io_video_vBlank),
    .io_video_regs_size_x         (io_video_regs_size_x),
    .io_video_regs_size_y         (io_video_regs_size_y),
    .io_spriteOffset_x            (io_spriteCtrl_regs_offset_x),
    .io_spriteOffset_y            (io_spriteCtrl_regs_offset_y),
    .io_pen_priority              (pwrinst2Layer2PenPriority),
    .io_pen_palette               (pwrinst2Layer2PenPalette),
    .io_pen_color                 (pwrinst2Layer2PenColor)
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
    ,
    .io_debug_desc                (),
    .io_debug_rom                 ()
`endif
  );

  ColorMixer colorMixer (
    .clock                             (io_videoClock),
    .io_gameConfig_granularity         (io_gameConfig_granularity),
    .io_gameConfig_layer_0_format      (io_layerCtrl_0_format),
    .io_gameConfig_layer_0_paletteBank (io_gameConfig_layer_0_paletteBank),
    .io_gameConfig_layer_1_format      (io_layerCtrl_1_format),
    .io_gameConfig_layer_1_paletteBank (io_gameConfig_layer_1_paletteBank),
    .io_gameConfig_layer_2_format      (io_layerCtrl_2_format),
    .io_gameConfig_layer_2_paletteBank (io_gameConfig_layer_2_paletteBank),
    .io_gameConfig_pwrinst2            (io_spriteCtrl_pwrinst2),
    .io_spritePen_priority             (spritePenPriority),
    .io_spritePen_palette              (spritePenPalette),
    .io_spritePen_color                (spritePenColor),
    .io_layer0Pen_priority             (layer0PenPriority),
    .io_layer0Pen_palette              (layer0PenPalette),
    .io_layer0Pen_color                (layer0PenColor),
    .io_layer1Pen_priority             (layer1PenPriority),
    .io_layer1Pen_palette              (layer1PenPalette),
    .io_layer1Pen_color                (layer1PenColor),
    .io_layer2Pen_priority             (layer2PenPriority),
    .io_layer2Pen_palette              (layer2PenPalette),
    .io_layer2Pen_color                (layer2PenColor),
    .io_pwrinst2Layer2Pen_priority     (pwrinst2Layer2PenPriority),
    .io_pwrinst2Layer2Pen_palette      (pwrinst2Layer2PenPalette),
    .io_pwrinst2Layer2Pen_color        (pwrinst2Layer2PenColor),
    .io_paletteRam_addr                (io_paletteRam_addr),
    .io_paletteRam_dout                (io_paletteRam_dout),
    .io_dout                           (paletteColor)
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
    ,
    .io_debug_selectedPen              (mixerDebugSelectedPen),
    .io_debug_selectedPalette          (mixerDebugSelectedPalette),
    .io_debug_selectedColor            (mixerDebugSelectedColor),
    .io_debug_visibleMask              (mixerDebugVisibleMask)
`endif
  );

  assign io_spriteLineBuffer_addr = io_video_pos_x;
  assign io_systemFrameBuffer_wr = systemFramebufferWrReg;
  assign io_systemFrameBuffer_addr = systemFramebufferAddrReg[16:0];
  assign io_systemFrameBuffer_din = systemFramebufferDinReg;
  assign io_rgb = videoRgb888;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  wire [63:0] gpuFlashDebug = {
    debugFlashMismatchHistory,
    debugTopPaletteAddrLatched,
    debugTopPaletteColorLatched,
    debugTopMixLatched,
    debugPrevMidRgbLatched,
    debugMidRgbLatched,
    debugTopRgbLatched,
    debugFlashFlagsLatched
  };
  assign io_debug_readout = io_spriteCtrl_pwrinst2 ? layer2DebugRom : gpuFlashDebug;
  assign io_debug_video = io_spriteCtrl_pwrinst2 ? layer2DebugDesc : gpuFlashDebug;
  assign io_debug_source_rgb = io_video_displayEnable ? debugRawSpriteRgb : 24'h000000;
`endif
endmodule
