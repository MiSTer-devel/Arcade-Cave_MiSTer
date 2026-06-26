// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module ColorMixer(
  input         clock,
  input  [8:0]  io_gameConfig_granularity,
  input  [1:0]  io_gameConfig_layer_0_format,
  input  [1:0]  io_gameConfig_layer_0_paletteBank,
  input  [1:0]  io_gameConfig_layer_1_format,
  input  [1:0]  io_gameConfig_layer_1_paletteBank,
  input  [1:0]  io_gameConfig_layer_2_format,
  input  [1:0]  io_gameConfig_layer_2_paletteBank,
  input         io_gameConfig_pwrinst2,
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
  input  [1:0]  io_pwrinst2Layer2Pen_priority,
  input  [5:0]  io_pwrinst2Layer2Pen_palette,
  input  [7:0]  io_pwrinst2Layer2Pen_color,
  output [14:0] io_paletteRam_addr,
  input  [15:0] io_paletteRam_dout,
  output [15:0] io_dout
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  ,
  output [3:0]  io_debug_selectedPen,
  output [5:0]  io_debug_selectedPalette,
  output [7:0]  io_debug_selectedColor,
  output [3:0]  io_debug_visibleMask
`endif
);
  localparam [4:0] PEN_FILL            = 5'h00;
  localparam [4:0] PEN_SPRITE          = 5'h01;
  localparam [4:0] PEN_LAYER0          = 5'h02;
  localparam [4:0] PEN_LAYER1          = 5'h04;
  localparam [4:0] PEN_LAYER2          = 5'h08;
  localparam [4:0] PEN_PWRINST2_LAYER2 = 5'h10;

  wire granularity16 = io_gameConfig_granularity == 9'h010;
  wire granularity64 = io_gameConfig_granularity == 9'h040;
  wire layer0Format6bpp = io_gameConfig_layer_0_format == 2'h2;
  wire layer1Format6bpp = io_gameConfig_layer_1_format == 2'h2;
  wire layer2Format6bpp = io_gameConfig_layer_2_format == 2'h2;
  wire [1:0] effectiveSpritePriority =
    io_gameConfig_pwrinst2 ? {1'b1, io_spritePen_priority[0]} : io_spritePen_priority;

  wire spriteVisible = |io_spritePen_color;
  wire layer0Visible = |io_layer0Pen_color;
  wire layer1Visible = |io_layer1Pen_color;
  wire layer2Visible = |io_layer2Pen_color;
  wire pwrinst2Layer2Visible = io_gameConfig_pwrinst2 & |io_pwrinst2Layer2Pen_color;

  wire spritePriority0 = spriteVisible & (effectiveSpritePriority == 2'h0);
  wire layer0Priority0 = layer0Visible & (io_layer0Pen_priority == 2'h0);
  wire layer1Priority0 = layer1Visible & (io_layer1Pen_priority == 2'h0);
  wire layer2Priority0 = layer2Visible & (io_layer2Pen_priority == 2'h0);
  wire spritePriority1 = spriteVisible & (effectiveSpritePriority == 2'h1);
  wire layer0Priority1 = layer0Visible & (io_layer0Pen_priority == 2'h1);
  wire layer1Priority1 = layer1Visible & (io_layer1Pen_priority == 2'h1);
  wire layer2Priority1 = layer2Visible & (io_layer2Pen_priority == 2'h1);
  wire spritePriority2 = spriteVisible & (effectiveSpritePriority == 2'h2);
  wire layer0Priority2 = layer0Visible & (io_layer0Pen_priority == 2'h2);
  wire layer1Priority2 = layer1Visible & (io_layer1Pen_priority == 2'h2);
  wire layer2Priority2 = layer2Visible & (io_layer2Pen_priority == 2'h2);
  wire spritePriority3 = spriteVisible & (&effectiveSpritePriority);
  wire layer0Priority3 = layer0Visible & (&io_layer0Pen_priority);
  wire layer1Priority3 = layer1Visible & (&io_layer1Pen_priority);
  wire layer2Priority3 = layer2Visible & (&io_layer2Pen_priority);

  wire [4:0] priority0Base =
    pwrinst2Layer2Visible ? PEN_PWRINST2_LAYER2 : PEN_FILL;
  wire [4:0] priority0Sprite = spritePriority0 ? PEN_SPRITE : priority0Base;
  wire [4:0] priority0Layer0 = layer0Priority0 ? PEN_LAYER0 : priority0Sprite;
  wire [4:0] priority0Layer1 = layer1Priority0 ? PEN_LAYER1 : priority0Layer0;
  wire [4:0] priority0Layer2 = layer2Priority0 ? PEN_LAYER2 : priority0Layer1;
  wire [4:0] priority1Sprite = spritePriority1 ? PEN_SPRITE : priority0Layer2;
  wire [4:0] priority1Layer0 = layer0Priority1 ? PEN_LAYER0 : priority1Sprite;
  wire [4:0] priority1Layer1 = layer1Priority1 ? PEN_LAYER1 : priority1Layer0;
  wire [4:0] priority1Layer2 = layer2Priority1 ? PEN_LAYER2 : priority1Layer1;
  wire [4:0] priority2Sprite = spritePriority2 ? PEN_SPRITE : priority1Layer2;
  wire [4:0] priority2Layer0 = layer0Priority2 ? PEN_LAYER0 : priority2Sprite;
  wire [4:0] priority2Layer1 = layer1Priority2 ? PEN_LAYER1 : priority2Layer0;
  wire [4:0] priority2Layer2 = layer2Priority2 ? PEN_LAYER2 : priority2Layer1;
  wire [4:0] priority3Sprite = spritePriority3 ? PEN_SPRITE : priority2Layer2;
  wire [4:0] priority3Layer0 = layer0Priority3 ? PEN_LAYER0 : priority3Sprite;
  wire [4:0] priority3Layer1 = layer1Priority3 ? PEN_LAYER1 : priority3Layer0;
  wire [4:0] selectedPen = layer2Priority3 ? PEN_LAYER2 : priority3Layer1;
  wire [4:0] finalSelectedPen = selectedPen;

  wire [14:0] fillAddr16 = io_gameConfig_pwrinst2 ? 15'h07F0 : 15'h03F0;
  wire [14:0] fillAddr64 = 15'h0FC0;
  wire [14:0] fillAddr256 = 15'h3F00;
  wire [14:0] fillAddr = granularity64 ? fillAddr64 : (granularity16 ? fillAddr16 : fillAddr256);

  wire [14:0] spriteAddr16 = {5'h00, io_spritePen_palette, io_spritePen_color[3:0]};
  wire [14:0] spriteAddr16PwrInst2 =
    {4'h0, io_spritePen_priority[1], io_spritePen_palette, io_spritePen_color[3:0]};
  wire [14:0] spriteAddr64 = {3'h0, io_spritePen_palette, io_spritePen_color[5:0]};
  wire [14:0] spriteAddr256 = {1'b0, io_spritePen_palette, io_spritePen_color};
  wire [14:0] spriteAddr =
    io_gameConfig_pwrinst2 ? spriteAddr16PwrInst2 :
    granularity64 ? spriteAddr64 : (granularity16 ? spriteAddr16 : spriteAddr256);

  wire [14:0] layer0Addr16 =
    {3'h0, io_gameConfig_layer_0_paletteBank, io_layer0Pen_palette, io_layer0Pen_color[3:0]};
  wire [14:0] layer0Addr16PwrInst2 =
    15'h0800 + {5'h00, io_layer0Pen_palette, io_layer0Pen_color[3:0]};
  wire [14:0] layer0Addr64 =
    {1'b0, io_gameConfig_layer_0_paletteBank, io_layer0Pen_palette, io_layer0Pen_color[5:0]};
  wire [14:0] layer0Addr6bpp =
    {3'h0, io_gameConfig_layer_0_paletteBank, io_layer0Pen_palette[3:0], io_layer0Pen_color[5:0]};
  wire [14:0] layer0Addr256 =
    {io_gameConfig_layer_0_paletteBank[0], io_layer0Pen_palette, io_layer0Pen_color};
  wire [14:0] layer0Addr =
    io_gameConfig_pwrinst2 ? layer0Addr16PwrInst2 :
    layer0Format6bpp ? layer0Addr6bpp : (granularity64 ? layer0Addr64 : (granularity16 ? layer0Addr16 : layer0Addr256));

  wire [14:0] layer1Addr16 =
    {3'h0, io_gameConfig_layer_1_paletteBank, io_layer1Pen_palette, io_layer1Pen_color[3:0]};
  wire [14:0] layer1Addr16PwrInst2 =
    15'h1000 + {5'h00, io_layer1Pen_palette, io_layer1Pen_color[3:0]};
  wire [14:0] layer1Addr64 =
    {1'b0, io_gameConfig_layer_1_paletteBank, io_layer1Pen_palette, io_layer1Pen_color[5:0]};
  wire [14:0] layer1Addr6bpp =
    {3'h0, io_gameConfig_layer_1_paletteBank, io_layer1Pen_palette[3:0], io_layer1Pen_color[5:0]};
  wire [14:0] layer1Addr256 =
    {io_gameConfig_layer_1_paletteBank[0], io_layer1Pen_palette, io_layer1Pen_color};
  wire [14:0] layer1Addr =
    io_gameConfig_pwrinst2 ? layer1Addr16PwrInst2 :
    layer1Format6bpp ? layer1Addr6bpp : (granularity64 ? layer1Addr64 : (granularity16 ? layer1Addr16 : layer1Addr256));

  wire [14:0] layer2Addr16 =
    {3'h0, io_gameConfig_layer_2_paletteBank, io_layer2Pen_palette, io_layer2Pen_color[3:0]};
  wire [14:0] layer2Addr16PwrInst2 =
    15'h2000 + {5'h00, io_layer2Pen_palette, io_layer2Pen_color[3:0]};
  wire [14:0] layer2Addr64 =
    {1'b0, io_gameConfig_layer_2_paletteBank, io_layer2Pen_palette, io_layer2Pen_color[5:0]};
  wire [14:0] layer2Addr6bpp =
    {3'h0, io_gameConfig_layer_2_paletteBank, io_layer2Pen_palette[3:0], io_layer2Pen_color[5:0]};
  wire [14:0] layer2Addr256 =
    {io_gameConfig_layer_2_paletteBank[0], io_layer2Pen_palette, io_layer2Pen_color};
  wire [14:0] layer2Addr =
    io_gameConfig_pwrinst2 ? layer2Addr16PwrInst2 :
    layer2Format6bpp ? layer2Addr6bpp : (granularity64 ? layer2Addr64 : (granularity16 ? layer2Addr16 : layer2Addr256));
  wire [14:0] pwrinst2Layer2Addr16 =
    15'h1800 + {5'h00, io_pwrinst2Layer2Pen_palette, io_pwrinst2Layer2Pen_color[3:0]};

  wire [14:0] addrFill = finalSelectedPen == PEN_FILL ? fillAddr : 15'h0000;
  wire [14:0] addrSprite = finalSelectedPen == PEN_SPRITE ? spriteAddr : addrFill;
  wire [14:0] addrLayer0 = finalSelectedPen == PEN_LAYER0 ? layer0Addr : addrSprite;
  wire [14:0] addrLayer1 = finalSelectedPen == PEN_LAYER1 ? layer1Addr : addrLayer0;
  wire [14:0] addrPwrInst2Layer2 =
    finalSelectedPen == PEN_PWRINST2_LAYER2 ? pwrinst2Layer2Addr16 : addrLayer1;
  wire [14:0] paletteRamAddr = finalSelectedPen == PEN_LAYER2 ? layer2Addr : addrPwrInst2Layer2;

`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  wire [5:0] selectedPalette =
    finalSelectedPen == PEN_SPRITE ? io_spritePen_palette :
    finalSelectedPen == PEN_LAYER0 ? io_layer0Pen_palette :
    finalSelectedPen == PEN_LAYER1 ? io_layer1Pen_palette :
    finalSelectedPen == PEN_PWRINST2_LAYER2 ? io_pwrinst2Layer2Pen_palette :
    finalSelectedPen == PEN_LAYER2 ? io_layer2Pen_palette :
                                     6'h00;
  wire [7:0] selectedColor =
    finalSelectedPen == PEN_SPRITE ? io_spritePen_color :
    finalSelectedPen == PEN_LAYER0 ? io_layer0Pen_color :
    finalSelectedPen == PEN_LAYER1 ? io_layer1Pen_color :
    finalSelectedPen == PEN_PWRINST2_LAYER2 ? io_pwrinst2Layer2Pen_color :
    finalSelectedPen == PEN_LAYER2 ? io_layer2Pen_color :
                                     8'h00;
  wire [3:0] visibleMask = {
    layer2Visible,
    layer1Visible | pwrinst2Layer2Visible,
    layer0Visible,
    spriteVisible
  };

  reg [3:0] debugSelectedPenReg;
  reg [5:0] debugSelectedPaletteReg;
  reg [7:0] debugSelectedColorReg;
  reg [3:0] debugVisibleMaskReg;
`endif

  reg [15:0] pixelReg;
  always @(posedge clock) begin
    pixelReg <= io_paletteRam_dout;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
    debugSelectedPenReg <= finalSelectedPen[3:0];
    debugSelectedPaletteReg <= selectedPalette;
    debugSelectedColorReg <= selectedColor;
    debugVisibleMaskReg <= visibleMask;
`endif
  end

  assign io_paletteRam_addr = paletteRamAddr;
  assign io_dout = pixelReg;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  assign io_debug_selectedPen = debugSelectedPenReg;
  assign io_debug_selectedPalette = debugSelectedPaletteReg;
  assign io_debug_selectedColor = debugSelectedColorReg;
  assign io_debug_visibleMask = debugVisibleMaskReg;
`endif
endmodule
