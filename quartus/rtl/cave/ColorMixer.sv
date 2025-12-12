module ColorMixer(
  input         clock,
  input  [8:0]  io_gameConfig_granularity,
  input  [1:0]  io_gameConfig_layer_0_paletteBank,
  input  [1:0]  io_gameConfig_layer_1_paletteBank,
  input  [1:0]  io_gameConfig_layer_2_paletteBank,
  input  [1:0]  io_spritePen_priority,
  input  [5:0]  io_spritePen_palette,
  input  [7:0]  io_spritePen_color,
  input  [1:0]  io_layer0Pen_priority,
  input  [5:0]  io_layer0Pen_palette,
  input  [7:0]  io_layer0Pen_color,
  input  [1:0]  io_layer1Pen_priority,
  input  [5:0]  io_layer1Pen_palette,
  input  [7:0]  io_layer1Pen_color,
  input  [1:0]  io_layer2Pen_priority,
  input  [5:0]  io_layer2Pen_palette,
  input  [7:0]  io_layer2Pen_color,
  output [14:0] io_paletteRam_addr,
  input  [15:0] io_paletteRam_dout,
  output [15:0] io_dout
);

  wire [1:0]  _index_T_1 =
    (|io_layer0Pen_color) & io_layer0Pen_priority == 2'h0
      ? 2'h2
      : {1'h0, (|io_spritePen_color) & io_spritePen_priority == 2'h0};
  wire [2:0]  _index_T_2 =
    (|io_layer1Pen_color) & io_layer1Pen_priority == 2'h0 ? 3'h4 : {1'h0, _index_T_1};
  wire [3:0]  _index_T_3 =
    (|io_layer2Pen_color) & io_layer2Pen_priority == 2'h0 ? 4'h8 : {1'h0, _index_T_2};
  wire [3:0]  _index_T_4 =
    (|io_spritePen_color) & io_spritePen_priority == 2'h1 ? 4'h1 : _index_T_3;
  wire [3:0]  _index_T_5 =
    (|io_layer0Pen_color) & io_layer0Pen_priority == 2'h1 ? 4'h2 : _index_T_4;
  wire [3:0]  _index_T_6 =
    (|io_layer1Pen_color) & io_layer1Pen_priority == 2'h1 ? 4'h4 : _index_T_5;
  wire [3:0]  _index_T_7 =
    (|io_layer2Pen_color) & io_layer2Pen_priority == 2'h1 ? 4'h8 : _index_T_6;
  wire [3:0]  _index_T_8 =
    (|io_spritePen_color) & io_spritePen_priority == 2'h2 ? 4'h1 : _index_T_7;
  wire [3:0]  _index_T_9 =
    (|io_layer0Pen_color) & io_layer0Pen_priority == 2'h2 ? 4'h2 : _index_T_8;
  wire [3:0]  _index_T_10 =
    (|io_layer1Pen_color) & io_layer1Pen_priority == 2'h2 ? 4'h4 : _index_T_9;
  wire [3:0]  _index_T_11 =
    (|io_layer2Pen_color) & io_layer2Pen_priority == 2'h2 ? 4'h8 : _index_T_10;
  wire [3:0]  _index_T_12 =
    (|io_spritePen_color) & (&io_spritePen_priority) ? 4'h1 : _index_T_11;
  wire [3:0]  _index_T_13 =
    (|io_layer0Pen_color) & (&io_layer0Pen_priority) ? 4'h2 : _index_T_12;
  wire [3:0]  _index_T_14 =
    (|io_layer1Pen_color) & (&io_layer1Pen_priority) ? 4'h4 : _index_T_13;
  wire [3:0]  index =
    (|io_layer2Pen_color) & (&io_layer2Pen_priority) ? 4'h8 : _index_T_14;
  wire        _paletteRamAddr_T_56 = io_gameConfig_granularity == 9'h10;
  wire [14:0] _paletteRamAddr_T_9 = _paletteRamAddr_T_56 ? 15'h3F0 : 15'h3F00;
  wire        _paletteRamAddr_T_58 = io_gameConfig_granularity == 9'h40;
  wire [14:0] _paletteRamAddr_T_11 = _paletteRamAddr_T_58 ? 15'hFC0 : _paletteRamAddr_T_9;
  wire [14:0] _paletteRamAddr_T_21 =
    _paletteRamAddr_T_56
      ? {5'h0, io_spritePen_palette, io_spritePen_color[3:0]}
      : {1'h0, io_spritePen_palette, io_spritePen_color};
  wire [14:0] _paletteRamAddr_T_23 =
    _paletteRamAddr_T_58
      ? {3'h0, io_spritePen_palette, io_spritePen_color[5:0]}
      : _paletteRamAddr_T_21;
  wire [14:0] _paletteRamAddr_T_33 =
    _paletteRamAddr_T_56
      ? {3'h0,
         io_gameConfig_layer_0_paletteBank,
         io_layer0Pen_palette,
         io_layer0Pen_color[3:0]}
      : {io_gameConfig_layer_0_paletteBank[0], io_layer0Pen_palette, io_layer0Pen_color};
  wire [14:0] _paletteRamAddr_T_35 =
    _paletteRamAddr_T_58
      ? {1'h0,
         io_gameConfig_layer_0_paletteBank,
         io_layer0Pen_palette,
         io_layer0Pen_color[5:0]}
      : _paletteRamAddr_T_33;
  wire [14:0] _paletteRamAddr_T_45 =
    _paletteRamAddr_T_56
      ? {3'h0,
         io_gameConfig_layer_1_paletteBank,
         io_layer1Pen_palette,
         io_layer1Pen_color[3:0]}
      : {io_gameConfig_layer_1_paletteBank[0], io_layer1Pen_palette, io_layer1Pen_color};
  wire [14:0] _paletteRamAddr_T_47 =
    _paletteRamAddr_T_58
      ? {1'h0,
         io_gameConfig_layer_1_paletteBank,
         io_layer1Pen_palette,
         io_layer1Pen_color[5:0]}
      : _paletteRamAddr_T_45;
  wire [14:0] _paletteRamAddr_T_57 =
    _paletteRamAddr_T_56
      ? {3'h0,
         io_gameConfig_layer_2_paletteBank,
         io_layer2Pen_palette,
         io_layer2Pen_color[3:0]}
      : {io_gameConfig_layer_2_paletteBank[0], io_layer2Pen_palette, io_layer2Pen_color};
  wire [14:0] _paletteRamAddr_T_59 =
    _paletteRamAddr_T_58
      ? {1'h0,
         io_gameConfig_layer_2_paletteBank,
         io_layer2Pen_palette,
         io_layer2Pen_color[5:0]}
      : _paletteRamAddr_T_57;
  wire [14:0] _paletteRamAddr_T_61 = index == 4'h0 ? _paletteRamAddr_T_11 : 15'h0;
  wire [14:0] _paletteRamAddr_T_63 =
    index == 4'h1 ? _paletteRamAddr_T_23 : _paletteRamAddr_T_61;
  wire [14:0] _paletteRamAddr_T_65 =
    index == 4'h2 ? _paletteRamAddr_T_35 : _paletteRamAddr_T_63;
  wire [14:0] _paletteRamAddr_T_67 =
    index == 4'h4 ? _paletteRamAddr_T_47 : _paletteRamAddr_T_65;
  reg  [15:0] io_dout_REG;
  always @(posedge clock)
    io_dout_REG <= io_paletteRam_dout;
  assign io_paletteRam_addr = index == 4'h8 ? _paletteRamAddr_T_59 : _paletteRamAddr_T_67;
  assign io_dout = io_dout_REG;
endmodule

