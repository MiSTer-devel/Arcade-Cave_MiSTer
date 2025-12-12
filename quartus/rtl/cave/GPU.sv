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
  input          io_spriteCtrl_enable,
  input  [1:0]   io_spriteCtrl_format,
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
  output         io_systemFrameBuffer_wr,
  output [16:0]  io_systemFrameBuffer_addr,
  output [31:0]  io_systemFrameBuffer_din,
  output [14:0]  io_paletteRam_addr,
  input  [15:0]  io_paletteRam_dout,
  output [23:0]  io_rgb
);

  wire [1:0]  _colorMixer_io_spritePen_priority;
  wire [5:0]  _colorMixer_io_spritePen_palette;
  wire [7:0]  _colorMixer_io_spritePen_color;
  wire [15:0] _colorMixer_io_dout;
  wire [1:0]  _layerProcessor_2_io_pen_priority;
  wire [5:0]  _layerProcessor_2_io_pen_palette;
  wire [7:0]  _layerProcessor_2_io_pen_color;
  wire [1:0]  _layerProcessor_1_io_pen_priority;
  wire [5:0]  _layerProcessor_1_io_pen_palette;
  wire [7:0]  _layerProcessor_1_io_pen_color;
  wire [1:0]  _layerProcessor_0_io_pen_priority;
  wire [5:0]  _layerProcessor_0_io_pen_palette;
  wire [7:0]  _layerProcessor_0_io_pen_color;
  reg         io_systemFrameBuffer_wr_REG;
  reg  [17:0] io_systemFrameBuffer_addr_REG;
  reg  [31:0] io_systemFrameBuffer_din_REG;
  wire [17:0] _GEN = {9'h0, io_video_pos_x};
  wire [17:0] _GEN_0 = {9'h0, io_video_regs_size_y};
  wire [17:0] _GEN_1 = {9'h0, 9'(9'(io_video_regs_size_y - io_video_pos_y) - 9'h1)};
  wire [17:0] _GEN_2 = {9'h0, 9'(9'(io_video_regs_size_x - io_video_pos_x) - 9'h1)};
  wire [17:0] _GEN_3 = {9'h0, io_video_pos_y};
  wire [17:0] _GEN_4 = {9'h0, io_video_regs_size_x};
  wire [17:0] _io_systemFrameBuffer_addr_T_13 =
    io_options_flipVideo
      ? 18'(18'(_GEN_1 * _GEN_4) + _GEN_2)
      : 18'(18'(_GEN_3 * _GEN_4) + _GEN);
  wire [17:0] _io_systemFrameBuffer_addr_T_6 =
    io_options_flipVideo
      ? 18'(18'(_GEN * _GEN_0) + _GEN_1)
      : 18'(18'(_GEN_2 * _GEN_0) + _GEN_3);
  always @(posedge io_videoClock) begin
    io_systemFrameBuffer_wr_REG <= io_video_clockEnable & io_video_displayEnable;
    io_systemFrameBuffer_addr_REG <=
      io_options_rotate
        ? _io_systemFrameBuffer_addr_T_6
        : _io_systemFrameBuffer_addr_T_13;
    io_systemFrameBuffer_din_REG <=
      {8'h0,
       _colorMixer_io_dout[4:0],
       _colorMixer_io_dout[4:2],
       _colorMixer_io_dout[14:10],
       _colorMixer_io_dout[14:12],
       _colorMixer_io_dout[9:5],
       _colorMixer_io_dout[9:7]};
  end // always @(posedge)
  SpriteProcessor spriteProcessor (
    .clock                       (clock),
    .reset                       (reset),
    .io_ctrl_enable              (io_spriteCtrl_enable),
    .io_ctrl_format              (io_spriteCtrl_format),
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
    .io_frameBuffer_wait_n       (io_spriteFrameBuffer_wait_n)
  );
  LayerProcessor layerProcessor_0 (
    .clock                        (io_videoClock),
    .io_ctrl_enable               (io_layerCtrl_0_enable),
    .io_ctrl_format               (io_layerCtrl_0_format),
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
    .io_video_pos_x               (io_video_pos_x),
    .io_video_pos_y               (io_video_pos_y),
    .io_video_vBlank              (io_video_vBlank),
    .io_video_regs_size_x         (io_video_regs_size_x),
    .io_video_regs_size_y         (io_video_regs_size_y),
    .io_spriteOffset_x            (io_spriteCtrl_regs_offset_x),
    .io_spriteOffset_y            (io_spriteCtrl_regs_offset_y),
    .io_pen_priority              (_layerProcessor_0_io_pen_priority),
    .io_pen_palette               (_layerProcessor_0_io_pen_palette),
    .io_pen_color                 (_layerProcessor_0_io_pen_color)
  );
  LayerProcessor_1 layerProcessor_1 (
    .clock                        (io_videoClock),
    .io_ctrl_enable               (io_layerCtrl_1_enable),
    .io_ctrl_format               (io_layerCtrl_1_format),
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
    .io_video_pos_x               (io_video_pos_x),
    .io_video_pos_y               (io_video_pos_y),
    .io_video_vBlank              (io_video_vBlank),
    .io_video_regs_size_x         (io_video_regs_size_x),
    .io_video_regs_size_y         (io_video_regs_size_y),
    .io_spriteOffset_x            (io_spriteCtrl_regs_offset_x),
    .io_spriteOffset_y            (io_spriteCtrl_regs_offset_y),
    .io_pen_priority              (_layerProcessor_1_io_pen_priority),
    .io_pen_palette               (_layerProcessor_1_io_pen_palette),
    .io_pen_color                 (_layerProcessor_1_io_pen_color)
  );
  LayerProcessor_2 layerProcessor_2 (
    .clock                        (io_videoClock),
    .io_ctrl_enable               (io_layerCtrl_2_enable),
    .io_ctrl_format               (io_layerCtrl_2_format),
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
    .io_video_pos_x               (io_video_pos_x),
    .io_video_pos_y               (io_video_pos_y),
    .io_video_vBlank              (io_video_vBlank),
    .io_video_regs_size_x         (io_video_regs_size_x),
    .io_video_regs_size_y         (io_video_regs_size_y),
    .io_spriteOffset_x            (io_spriteCtrl_regs_offset_x),
    .io_spriteOffset_y            (io_spriteCtrl_regs_offset_y),
    .io_pen_priority              (_layerProcessor_2_io_pen_priority),
    .io_pen_palette               (_layerProcessor_2_io_pen_palette),
    .io_pen_color                 (_layerProcessor_2_io_pen_color)
  );
  assign _colorMixer_io_spritePen_priority = io_spriteLineBuffer_dout[15:14];
  assign _colorMixer_io_spritePen_palette = io_spriteLineBuffer_dout[13:8];
  assign _colorMixer_io_spritePen_color = io_spriteLineBuffer_dout[7:0];
  ColorMixer colorMixer (
    .clock                             (io_videoClock),
    .io_gameConfig_granularity         (io_gameConfig_granularity),
    .io_gameConfig_layer_0_paletteBank (io_gameConfig_layer_0_paletteBank),
    .io_gameConfig_layer_1_paletteBank (io_gameConfig_layer_1_paletteBank),
    .io_gameConfig_layer_2_paletteBank (io_gameConfig_layer_2_paletteBank),
    .io_spritePen_priority             (_colorMixer_io_spritePen_priority),
    .io_spritePen_palette              (_colorMixer_io_spritePen_palette),
    .io_spritePen_color                (_colorMixer_io_spritePen_color),
    .io_layer0Pen_priority             (_layerProcessor_0_io_pen_priority),
    .io_layer0Pen_palette              (_layerProcessor_0_io_pen_palette),
    .io_layer0Pen_color                (_layerProcessor_0_io_pen_color),
    .io_layer1Pen_priority             (_layerProcessor_1_io_pen_priority),
    .io_layer1Pen_palette              (_layerProcessor_1_io_pen_palette),
    .io_layer1Pen_color                (_layerProcessor_1_io_pen_color),
    .io_layer2Pen_priority             (_layerProcessor_2_io_pen_priority),
    .io_layer2Pen_palette              (_layerProcessor_2_io_pen_palette),
    .io_layer2Pen_color                (_layerProcessor_2_io_pen_color),
    .io_paletteRam_addr                (io_paletteRam_addr),
    .io_paletteRam_dout                (io_paletteRam_dout),
    .io_dout                           (_colorMixer_io_dout)
  );
  assign io_spriteLineBuffer_addr = io_video_pos_x;
  assign io_systemFrameBuffer_wr = io_systemFrameBuffer_wr_REG;
  assign io_systemFrameBuffer_addr = io_systemFrameBuffer_addr_REG[16:0];
  assign io_systemFrameBuffer_din = io_systemFrameBuffer_din_REG;
  assign io_rgb =
    {_colorMixer_io_dout[9:5],
     _colorMixer_io_dout[9:7],
     _colorMixer_io_dout[14:10],
     _colorMixer_io_dout[14:12],
     _colorMixer_io_dout[4:0],
     _colorMixer_io_dout[4:2]};
endmodule

