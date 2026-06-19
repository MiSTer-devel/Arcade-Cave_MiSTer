// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Main owns the 68k-facing board logic: per-game CPU memory maps, input
// packing, IRQs, EEPROM serial pins, pause, and video-domain register mirrors.
// The chip-select lattice below is still kept close to the Chisel equations so
// each supported board map can be extracted and tested one at a time later.
module Main(
  input          clock,
  input          reset,
  input          io_videoClock,
  input          io_spriteClock,
  input  [3:0]   io_gameIndex,
  input          io_options_service,
  input          io_player_0_up,
  input          io_player_0_down,
  input          io_player_0_left,
  input          io_player_0_right,
  input  [3:0]   io_player_0_buttons,
  input          io_player_0_start,
  input          io_player_0_coin,
  input          io_player_0_pause,
  input          io_player_1_up,
  input          io_player_1_down,
  input          io_player_1_left,
  input          io_player_1_right,
  input  [3:0]   io_player_1_buttons,
  input          io_player_1_start,
  input          io_player_1_coin,
  input          io_player_1_pause,
  input  [15:0]  io_dips_0,
  input          io_video_vBlank,
  output         io_gpuMem_layer_0_regs_tileSize,
  output         io_gpuMem_layer_0_regs_enable,
  output         io_gpuMem_layer_0_regs_flipX,
  output         io_gpuMem_layer_0_regs_flipY,
  output         io_gpuMem_layer_0_regs_rowScrollEnable,
  output         io_gpuMem_layer_0_regs_rowSelectEnable,
  output [8:0]   io_gpuMem_layer_0_regs_scroll_x,
  output [8:0]   io_gpuMem_layer_0_regs_scroll_y,
  input  [11:0]  io_gpuMem_layer_0_vram8x8_addr,
  output [31:0]  io_gpuMem_layer_0_vram8x8_dout,
  input  [9:0]   io_gpuMem_layer_0_vram16x16_addr,
  output [31:0]  io_gpuMem_layer_0_vram16x16_dout,
  input  [8:0]   io_gpuMem_layer_0_lineRam_addr,
  output [31:0]  io_gpuMem_layer_0_lineRam_dout,
  output         io_gpuMem_layer_1_regs_tileSize,
  output         io_gpuMem_layer_1_regs_enable,
  output         io_gpuMem_layer_1_regs_flipX,
  output         io_gpuMem_layer_1_regs_flipY,
  output         io_gpuMem_layer_1_regs_rowScrollEnable,
  output         io_gpuMem_layer_1_regs_rowSelectEnable,
  output [8:0]   io_gpuMem_layer_1_regs_scroll_x,
  output [8:0]   io_gpuMem_layer_1_regs_scroll_y,
  input  [11:0]  io_gpuMem_layer_1_vram8x8_addr,
  output [31:0]  io_gpuMem_layer_1_vram8x8_dout,
  input  [9:0]   io_gpuMem_layer_1_vram16x16_addr,
  output [31:0]  io_gpuMem_layer_1_vram16x16_dout,
  input  [8:0]   io_gpuMem_layer_1_lineRam_addr,
  output [31:0]  io_gpuMem_layer_1_lineRam_dout,
  output         io_gpuMem_layer_2_regs_tileSize,
  output         io_gpuMem_layer_2_regs_enable,
  output         io_gpuMem_layer_2_regs_flipX,
  output         io_gpuMem_layer_2_regs_flipY,
  output         io_gpuMem_layer_2_regs_rowScrollEnable,
  output         io_gpuMem_layer_2_regs_rowSelectEnable,
  output [8:0]   io_gpuMem_layer_2_regs_scroll_x,
  output [8:0]   io_gpuMem_layer_2_regs_scroll_y,
  input  [11:0]  io_gpuMem_layer_2_vram8x8_addr,
  output [31:0]  io_gpuMem_layer_2_vram8x8_dout,
  input  [9:0]   io_gpuMem_layer_2_vram16x16_addr,
  output [31:0]  io_gpuMem_layer_2_vram16x16_dout,
  input  [8:0]   io_gpuMem_layer_2_lineRam_addr,
  output [31:0]  io_gpuMem_layer_2_lineRam_dout,
  output         io_gpuMem_pwrinst2_layer_2_regs_tileSize,
  output         io_gpuMem_pwrinst2_layer_2_regs_enable,
  output         io_gpuMem_pwrinst2_layer_2_regs_flipX,
  output         io_gpuMem_pwrinst2_layer_2_regs_flipY,
  output         io_gpuMem_pwrinst2_layer_2_regs_rowScrollEnable,
  output         io_gpuMem_pwrinst2_layer_2_regs_rowSelectEnable,
  output [8:0]   io_gpuMem_pwrinst2_layer_2_regs_scroll_x,
  output [8:0]   io_gpuMem_pwrinst2_layer_2_regs_scroll_y,
  input  [11:0]  io_gpuMem_pwrinst2_layer_2_vram8x8_addr,
  output [31:0]  io_gpuMem_pwrinst2_layer_2_vram8x8_dout,
  input  [9:0]   io_gpuMem_pwrinst2_layer_2_vram16x16_addr,
  output [31:0]  io_gpuMem_pwrinst2_layer_2_vram16x16_dout,
  input  [8:0]   io_gpuMem_pwrinst2_layer_2_lineRam_addr,
  output [31:0]  io_gpuMem_pwrinst2_layer_2_lineRam_dout,
  output [8:0]   io_gpuMem_sprite_regs_offset_x,
  output [8:0]   io_gpuMem_sprite_regs_offset_y,
  output [1:0]   io_gpuMem_sprite_regs_bank,
  output         io_gpuMem_sprite_regs_fixed,
  output         io_gpuMem_sprite_regs_hFlip,
  input          io_gpuMem_sprite_vram_rd,
  input  [11:0]  io_gpuMem_sprite_vram_addr,
  output [127:0] io_gpuMem_sprite_vram_dout,
  input  [14:0]  io_gpuMem_paletteRam_addr,
  output [15:0]  io_gpuMem_paletteRam_dout,
  output         io_soundCtrl_oki_0_wr,
  output [15:0]  io_soundCtrl_oki_0_din,
  input  [15:0]  io_soundCtrl_oki_0_dout,
  output         io_soundCtrl_oki_1_wr,
  output [15:0]  io_soundCtrl_oki_1_din,
  input  [15:0]  io_soundCtrl_oki_1_dout,
  output         io_soundCtrl_nmk_wr,
  output [22:0]  io_soundCtrl_nmk_addr,
  output [15:0]  io_soundCtrl_nmk_din,
  output         io_soundCtrl_ymz_rd,
  output         io_soundCtrl_ymz_wr,
  output [22:0]  io_soundCtrl_ymz_addr,
  output [15:0]  io_soundCtrl_ymz_din,
  input  [15:0]  io_soundCtrl_ymz_dout,
  output         io_soundCtrl_req,
  output [15:0]  io_soundCtrl_data,
  output         io_soundCtrl_reply_rd,
  input  [15:0]  io_soundCtrl_reply,
  input          io_soundCtrl_reply_empty,
  input          io_soundCtrl_irq,
  output         io_progRom_rd,
  output [21:0]  io_progRom_addr,
  input  [15:0]  io_progRom_dout,
  input          io_progRom_valid,
  output         io_eeprom_rd,
  output         io_eeprom_wr,
  output [6:0]   io_eeprom_addr,
  output [15:0]  io_eeprom_din,
  input  [15:0]  io_eeprom_dout,
  input          io_eeprom_wait_n,
  input          io_eeprom_valid,
  output         io_spriteFrameBufferSwap,
  output [63:0] io_debug_pipeline,
  output [63:0] io_debug_cpu,
  output [63:0] io_debug_writes,
  output [63:0] io_debug_data,
  output [63:0] io_debug_live,
  output [63:0] io_debug_palette
);

  wire        gameIsDFeveron;
  wire        gameIsDoDonPachi;
  wire        gameIsDonPachi;
  wire        gameIsEsprade;
  wire        gameIsUopoko;
  wire        gameIsGuwange;
  wire        gameIsGaia;
  wire        gameIsPwrInst2;
  wire        gameIsHotdogStorm = 1'b0;
  wire        gameIsMazinger = 1'b0;

  CaveBoardProfile boardProfile(
    .game_index                  (io_gameIndex),
    .sound_device                (2'd0),
    .game_is_dfeveron            (gameIsDFeveron),
    .game_is_dodonpachi          (gameIsDoDonPachi),
    .game_is_donpachi            (gameIsDonPachi),
    .game_is_esprade             (gameIsEsprade),
    .game_is_uopoko              (gameIsUopoko),
    .game_is_guwange             (gameIsGuwange),
    .game_is_gaia                (gameIsGaia),
    .game_is_pwrinst2            (gameIsPwrInst2),
    .board_uses_z80_sound        (),
    .board_is_vertical_clockwise (),
    .sound_is_ymz280b            (),
    .sound_is_oki                (),
    .sound_is_z80                ()
  );

  wire        mem_7_wr;
  wire        mem_6_wr;
  wire        mem_5_wr;
  wire        mem_4_wr;
  wire        mem_3_wr;
  wire        mem_2_wr;
  wire        mem_1_wr;
  wire [1:0]  _spriteRegs_io_mem_mask;
  wire [15:0] _spriteRegs_io_regs_0;
  wire [15:0] _spriteRegs_io_regs_1;
  wire [15:0] _spriteRegs_io_regs_4;
  wire [15:0] _spriteRegs_io_regs_5;
  wire [1:0]  _layerRegs_2_io_mem_addr;
  wire [15:0] _layerRegs_2_io_mem_dout;
  wire [15:0] _layerRegs_2_io_regs_0;
  wire [15:0] _layerRegs_2_io_regs_1;
  wire [15:0] _layerRegs_2_io_regs_2;
  wire [1:0]  _layerRegs_1_io_mem_addr;
  wire [15:0] _layerRegs_1_io_mem_dout;
  wire [15:0] _layerRegs_1_io_regs_0;
  wire [15:0] _layerRegs_1_io_regs_1;
  wire [15:0] _layerRegs_1_io_regs_2;
  wire [1:0]  _layerRegs_0_io_mem_addr;
  wire [15:0] _layerRegs_0_io_mem_dout;
  wire [15:0] _layerRegs_0_io_regs_0;
  wire [15:0] _layerRegs_0_io_regs_1;
  wire [15:0] _layerRegs_0_io_regs_2;
  wire [15:0] _paletteRam_io_portA_dout;
  wire [9:0]  _lineRam_2_io_portA_addr;
  wire [15:0] _lineRam_2_io_portA_dout;
  wire [15:0] _pwrinst2Layer2LineRam_io_portA_dout;
  wire [9:0]  _lineRam_1_io_portA_addr;
  wire [15:0] _lineRam_1_io_portA_dout;
  wire [9:0]  _lineRam_0_io_portA_addr;
  wire [15:0] _lineRam_0_io_portA_dout;
  wire [10:0] _vram16x16_2_io_portA_addr;
  wire [15:0] _vram16x16_2_io_portA_dout;
  wire [15:0] _pwrinst2Layer2Vram16_io_portA_dout;
  wire [10:0] _vram16x16_1_io_portA_addr;
  wire [15:0] _vram16x16_1_io_portA_dout;
  wire [10:0] _vram16x16_0_io_portA_addr;
  wire [15:0] _vram16x16_0_io_portA_dout;
  wire [15:0] _vram8x8_2_io_portA_dout;
  wire [15:0] _pwrinst2Layer2Vram8_io_portA_dout;
  wire [12:0] _vram8x8_1_io_portA_addr;
  wire [15:0] _vram8x8_1_io_portA_dout;
  wire [12:0] _vram8x8_0_io_portA_addr;
  wire [15:0] _vram8x8_0_io_portA_dout;
  wire [14:0] _spriteRam_io_portA_addr;
  wire [15:0] _spriteRam_io_portA_dout;
  wire [14:0] _pwrinst2SpriteExtraRam_addr;
  wire [15:0] _pwrinst2SpriteExtraRam_dout;
  wire [14:0] _mainRam_io_addr;
  wire [15:0] _mainRam_io_dout;
  wire        _eeprom_io_serial_sdo;
  wire        _cpu_io_vpa;
  wire [2:0]  _cpu_io_ipl;
  wire        _cpu_io_as;
  wire        _cpu_io_rw;
  wire        _cpu_io_uds;
  wire        _cpu_io_lds;
  wire [2:0]  _cpu_io_fc;
  wire [22:0] _cpu_io_addr;
  wire [15:0] _cpu_io_dout;
  wire        videoVBlankRising;
  wire        videoVBlankFalling;
  wire        pausePressed = io_player_0_pause | io_player_1_pause;
  wire        pauseActive;
  reg         videoIrq;
  reg         agalletIrq;
  reg         unknownIrq;
  reg  [15:0] dinReg;
  reg         dtackReg;
  wire        readStrobe;
  wire        writeStrobe;
  wire        eepromSerialCs;
  wire        eepromSerialSck;
  wire        eepromSerialSdi;
  reg         io_gpuMem_layer_0_regs_r_tileSize;
  reg         io_gpuMem_layer_0_regs_r_enable;
  reg         io_gpuMem_layer_0_regs_r_flipX;
  reg         io_gpuMem_layer_0_regs_r_flipY;
  reg         io_gpuMem_layer_0_regs_r_rowScrollEnable;
  reg         io_gpuMem_layer_0_regs_r_rowSelectEnable;
  reg  [8:0]  io_gpuMem_layer_0_regs_r_scroll_x;
  reg  [8:0]  io_gpuMem_layer_0_regs_r_scroll_y;
  reg         io_gpuMem_layer_0_regs_r_1_tileSize;
  reg         io_gpuMem_layer_0_regs_r_1_enable;
  reg         io_gpuMem_layer_0_regs_r_1_flipX;
  reg         io_gpuMem_layer_0_regs_r_1_flipY;
  reg         io_gpuMem_layer_0_regs_r_1_rowScrollEnable;
  reg         io_gpuMem_layer_0_regs_r_1_rowSelectEnable;
  reg  [8:0]  io_gpuMem_layer_0_regs_r_1_scroll_x;
  reg  [8:0]  io_gpuMem_layer_0_regs_r_1_scroll_y;
  reg         io_gpuMem_layer_1_regs_r_tileSize;
  reg         io_gpuMem_layer_1_regs_r_enable;
  reg         io_gpuMem_layer_1_regs_r_flipX;
  reg         io_gpuMem_layer_1_regs_r_flipY;
  reg         io_gpuMem_layer_1_regs_r_rowScrollEnable;
  reg         io_gpuMem_layer_1_regs_r_rowSelectEnable;
  reg  [8:0]  io_gpuMem_layer_1_regs_r_scroll_x;
  reg  [8:0]  io_gpuMem_layer_1_regs_r_scroll_y;
  reg         io_gpuMem_layer_1_regs_r_1_tileSize;
  reg         io_gpuMem_layer_1_regs_r_1_enable;
  reg         io_gpuMem_layer_1_regs_r_1_flipX;
  reg         io_gpuMem_layer_1_regs_r_1_flipY;
  reg         io_gpuMem_layer_1_regs_r_1_rowScrollEnable;
  reg         io_gpuMem_layer_1_regs_r_1_rowSelectEnable;
  reg  [8:0]  io_gpuMem_layer_1_regs_r_1_scroll_x;
  reg  [8:0]  io_gpuMem_layer_1_regs_r_1_scroll_y;
  reg         io_gpuMem_layer_2_regs_r_tileSize;
  reg         io_gpuMem_layer_2_regs_r_enable;
  reg         io_gpuMem_layer_2_regs_r_flipX;
  reg         io_gpuMem_layer_2_regs_r_flipY;
  reg         io_gpuMem_layer_2_regs_r_rowScrollEnable;
  reg         io_gpuMem_layer_2_regs_r_rowSelectEnable;
  reg  [8:0]  io_gpuMem_layer_2_regs_r_scroll_x;
  reg  [8:0]  io_gpuMem_layer_2_regs_r_scroll_y;
  reg         io_gpuMem_layer_2_regs_r_1_tileSize;
  reg         io_gpuMem_layer_2_regs_r_1_enable;
  reg         io_gpuMem_layer_2_regs_r_1_flipX;
  reg         io_gpuMem_layer_2_regs_r_1_flipY;
  reg         io_gpuMem_layer_2_regs_r_1_rowScrollEnable;
  reg         io_gpuMem_layer_2_regs_r_1_rowSelectEnable;
  reg  [8:0]  io_gpuMem_layer_2_regs_r_1_scroll_x;
  reg  [8:0]  io_gpuMem_layer_2_regs_r_1_scroll_y;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_tileSize;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_enable;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_flipX;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_flipY;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_rowScrollEnable;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_rowSelectEnable;
  reg  [8:0]  io_gpuMem_pwrinst2_layer_2_regs_r_scroll_x;
  reg  [8:0]  io_gpuMem_pwrinst2_layer_2_regs_r_scroll_y;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_1_tileSize;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_1_enable;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_1_flipX;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_1_flipY;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_1_rowScrollEnable;
  reg         io_gpuMem_pwrinst2_layer_2_regs_r_1_rowSelectEnable;
  reg  [8:0]  io_gpuMem_pwrinst2_layer_2_regs_r_1_scroll_x;
  reg  [8:0]  io_gpuMem_pwrinst2_layer_2_regs_r_1_scroll_y;
  wire        coin1PulseActive;
  wire        coin2PulseActive;
  wire        servicePulseActive;
  wire [15:0] inputDefaultP1;
  wire [15:0] inputDefaultP2;
  wire [15:0] inputPlayersWide;
  wire [15:0] inputGuwangeP1;
  wire [15:0] inputPort0;
  wire [15:0] inputP1DefaultOrGuwange;
  wire [15:0] inputPlayersWideOrGuwangeP1;
  wire [15:0] inputGuwangeSystem;
  wire [15:0] inputGaiaSystem;
  wire [15:0] inputPort1;
  wire [15:0] inputP2DefaultOrGuwange;
  wire [15:0] inputSharedSystem;
  wire        vblankForcedSpriteSwap =
    videoVBlankRising & (gameIsHotdogStorm | gameIsMazinger | pauseActive);
  wire [23:0] cpuByteAddr = {_cpu_io_addr, 1'b0};
  wire [1:0]  mainRam_io_mask = {_cpu_io_uds, _cpu_io_lds};

  CaveVBlankTracker vblankTracker(
    .clock   (clock),
    .vblank  (io_video_vBlank),
    .rising  (videoVBlankRising),
    .falling (videoVBlankFalling)
  );

  CaveCpuBusStrobes cpuBusStrobes(
    .clock        (clock),
    .as           (_cpu_io_as),
    .uds          (_cpu_io_uds),
    .lds          (_cpu_io_lds),
    .rw           (_cpu_io_rw),
    .read_strobe  (readStrobe),
    .write_strobe (writeStrobe)
  );

  CavePulseStretcher #(
    .COUNTER_WIDTH(22),
    .TERMINAL_COUNT(22'h30D3FF)
  ) coin1Pulse (
    .clock(clock),
    .reset(reset),
    .signal_in(io_player_0_coin),
    .pulse_active(coin1PulseActive)
  );

  CavePulseStretcher #(
    .COUNTER_WIDTH(22),
    .TERMINAL_COUNT(22'h30D3FF)
  ) coin2Pulse (
    .clock(clock),
    .reset(reset),
    .signal_in(io_player_1_coin),
    .pulse_active(coin2PulseActive)
  );

  CavePulseStretcher #(
    .COUNTER_WIDTH(27),
    .TERMINAL_COUNT(27'h4C4B3FF)
  ) servicePulse (
    .clock(clock),
    .reset(reset),
    .signal_in(io_options_service),
    .pulse_active(servicePulseActive)
  );

  CaveInputMapper inputMapper(
    .game_is_guwange            (gameIsGuwange),
    .game_is_gaia               (gameIsGaia),
    .eeprom_sdo                 (_eeprom_io_serial_sdo),
    .service_active             (servicePulseActive),
    .coin1_active               (coin1PulseActive),
    .coin2_active               (coin2PulseActive),
    .player0_up                 (io_player_0_up),
    .player0_down               (io_player_0_down),
    .player0_left               (io_player_0_left),
    .player0_right              (io_player_0_right),
    .player0_buttons            (io_player_0_buttons),
    .player0_start              (io_player_0_start),
    .player1_up                 (io_player_1_up),
    .player1_down               (io_player_1_down),
    .player1_left               (io_player_1_left),
    .player1_right              (io_player_1_right),
    .player1_buttons            (io_player_1_buttons),
    .player1_start              (io_player_1_start),
    .default_p1                 (inputDefaultP1),
    .default_p2                 (inputDefaultP2),
    .combined_players           (inputPlayersWide),
    .guwange_p1                 (inputGuwangeP1),
    .input0                     (inputPort0),
    .default_or_guwange_p1      (inputP1DefaultOrGuwange),
    .combined_or_guwange_p1     (inputPlayersWideOrGuwangeP1),
    .guwange_system             (inputGuwangeSystem),
    .gaia_system                (inputGaiaSystem),
    .input1                     (inputPort1),
    .default_or_guwange_p2      (inputP2DefaultOrGuwange),
    .shared_system              (inputSharedSystem)
  );

  CaveEepromSerialPins eepromSerialPins(
    .clock          (clock),
    .reset          (reset),
    .write_enable   (eepromMem_wr),
    .guwange_layout (gameIsGuwange),
    .data           (_cpu_io_dout),
    .serial_cs      (eepromSerialCs),
    .serial_sck     (eepromSerialSck),
    .serial_sdi     (eepromSerialSdi)
  );

  CavePauseToggle pauseToggle(
    .clock         (clock),
    .reset         (reset),
    .pause_pressed (pausePressed),
    .pause_active  (pauseActive)
  );
  wire        mazingerVideoIrqClear = 1'b0;
  wire        mazingerUnknownIrqClear = 1'b0;
  wire        mazingerProgRomRead = 1'b0;
  wire        mazingerMainRamRead = 1'b0;
  wire        mazingerMainRamWrite = 1'b0;
  wire        mazingerSpriteRamRead = 1'b0;
  wire        mazingerSpriteRamWrite = 1'b0;
  wire        mazingerLayer0Vram8Read = 1'b0;
  wire        mazingerLayer1Vram8Read = 1'b0;
  wire        mazingerLayer0Vram8Write = 1'b0;
  wire        mazingerLayer1Vram8Write = 1'b0;
  wire        mazingerLayer0RegsWrite = 1'b0;
  wire        mazingerLayer1RegsWrite = 1'b0;
  wire        mazingerPaletteRead = 1'b0;
  wire        mazingerPaletteWrite = 1'b0;
  wire        mazingerSpriteRegsWrite = 1'b0;
  wire        mazingerEepromWrite = 1'b0;
  wire [14:0] mazingerPaletteRamAddr = 15'd0;
  wire        mazingerExtraRomSelect = 1'b0;
  wire        mazingerDtack = 1'b0;
  wire        mazingerReadDataValid = 1'b0;
  wire [15:0] mazingerReadData = 16'd0;

  function [3:0] pwrinst2LayerMode;
    input [3:0] data;
    begin
      case (data)
        4'h1: pwrinst2LayerMode = 4'h0;
        4'h2: pwrinst2LayerMode = 4'h1;
        4'h4: pwrinst2LayerMode = 4'h2;
        default: pwrinst2LayerMode = 4'h3;
      endcase
    end
  endfunction

  function [15:0] pwrinst2ApplyMask;
    input [15:0] oldValue;
    input [15:0] newValue;
    input [1:0]  mask;
    begin
      pwrinst2ApplyMask = {
        mask[1] ? newValue[15:8] : oldValue[15:8],
        mask[0] ? newValue[7:0]  : oldValue[7:0]
      };
    end
  endfunction

  wire        pwrinst2ReadCycle = gameIsPwrInst2 & _cpu_io_as & _cpu_io_rw;
  wire        pwrinst2WriteCycle = gameIsPwrInst2 & _cpu_io_as & ~_cpu_io_rw;
  wire        pwrinst2ProgRomSelect = gameIsPwrInst2 & (cpuByteAddr < 24'h200000) & _cpu_io_rw;
  wire        pwrinst2ProgRomCycle = pwrinst2ProgRomSelect & _cpu_io_as;
  wire        pwrinst2ProgRomWrite = gameIsPwrInst2 & (cpuByteAddr < 24'h200000) & writeStrobe;
  wire        pwrinst2ProgRomWriteCycle = pwrinst2WriteCycle & (cpuByteAddr < 24'h200000);
  wire        pwrinst2MainRamSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'h400000) & (cpuByteAddr < 24'h410000);
  wire        pwrinst2Input0Select = gameIsPwrInst2 & (cpuByteAddr >= 24'h500000) & (cpuByteAddr < 24'h500002);
  wire        pwrinst2Input1Select = gameIsPwrInst2 & (cpuByteAddr >= 24'h500002) & (cpuByteAddr < 24'h500004);
  wire        pwrinst2Input0Read = pwrinst2Input0Select & pwrinst2ReadCycle;
  wire        pwrinst2Input1Read = pwrinst2Input1Select & pwrinst2ReadCycle;
  wire        pwrinst2ExtraRomSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'h600000) & (cpuByteAddr < 24'h700000);
  wire        pwrinst2ExtraRomRead = pwrinst2ExtraRomSelect & pwrinst2ReadCycle;
  wire        pwrinst2ExtraRomWrite = gameIsPwrInst2 & (cpuByteAddr >= 24'h600000) & (cpuByteAddr < 24'h700000) & writeStrobe;
  wire        pwrinst2ExtraRomWriteCycle = pwrinst2ExtraRomSelect & pwrinst2WriteCycle;
  wire        pwrinst2ProgRomRead = pwrinst2ProgRomCycle | pwrinst2ExtraRomRead;
  wire        pwrinst2ProgRomValid = pwrinst2ProgRomRead & io_progRom_valid;
  wire        pwrinst2EepromWrite = gameIsPwrInst2 & (cpuByteAddr >= 24'h700000) & (cpuByteAddr < 24'h700002) & writeStrobe;
  wire        pwrinst2EepromWriteCycle = pwrinst2WriteCycle & (cpuByteAddr >= 24'h700000) & (cpuByteAddr < 24'h700002);

  wire [23:0] pwrinst2Layer0Offset = cpuByteAddr - 24'h880000;
  wire [23:0] pwrinst2Layer1Offset = cpuByteAddr - 24'h900000;
  wire        pwrinst2Layer2Vram16Select = gameIsPwrInst2 & (cpuByteAddr >= 24'h800000) & (cpuByteAddr < 24'h801000);
  wire        pwrinst2Layer2LineSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'h801000) & (cpuByteAddr < 24'h801800);
  wire        pwrinst2Layer2ScratchSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'h801800) & (cpuByteAddr < 24'h804000);
  wire        pwrinst2Layer2Vram8Select = gameIsPwrInst2 & (cpuByteAddr >= 24'h804000) & (cpuByteAddr < 24'h808000);
  wire        pwrinst2Layer0Vram16Select = gameIsPwrInst2 & (cpuByteAddr >= 24'h880000) & (cpuByteAddr < 24'h881000);
  wire        pwrinst2Layer0LineSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'h881000) & (cpuByteAddr < 24'h881800);
  wire        pwrinst2Layer0ScratchSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'h881800) & (cpuByteAddr < 24'h884000);
  wire        pwrinst2Layer0Vram8Select = gameIsPwrInst2 & (cpuByteAddr >= 24'h884000) & (cpuByteAddr < 24'h888000);
  wire        pwrinst2Layer1Vram16Select = gameIsPwrInst2 & (cpuByteAddr >= 24'h900000) & (cpuByteAddr < 24'h901000);
  wire        pwrinst2Layer1LineSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'h901000) & (cpuByteAddr < 24'h901800);
  wire        pwrinst2Layer1ScratchSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'h901800) & (cpuByteAddr < 24'h904000);
  wire        pwrinst2Layer1Vram8Select = gameIsPwrInst2 & (cpuByteAddr >= 24'h904000) & (cpuByteAddr < 24'h908000);
  wire        pwrinst2Layer3Vram8Select = gameIsPwrInst2 & (cpuByteAddr >= 24'h980000) & (cpuByteAddr < 24'h988000);
  reg  [15:0] pwrinst2Layer2ScratchData;
  reg  [15:0] pwrinst2Layer0ScratchData;
  reg  [15:0] pwrinst2Layer1ScratchData;
  reg  [15:0] pwrinst2Layer2Regs0;
  reg  [15:0] pwrinst2Layer2Regs1;
  reg  [15:0] pwrinst2Layer2Regs2;
  reg         pwrinst2SyncReadPending;

  wire        pwrinst2SpriteListRamSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'hA00000) & (cpuByteAddr < 24'hA10000);
  wire        pwrinst2SpriteExtraRamSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'hA10000) & (cpuByteAddr < 24'hA20000);
  wire        pwrinst2SpriteRamSelect = pwrinst2SpriteListRamSelect | pwrinst2SpriteExtraRamSelect;
  wire        pwrinst2SpriteRegsSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'hA80000) & (cpuByteAddr < 24'hA80080);
  wire        pwrinst2SpriteRegsRead = pwrinst2SpriteRegsSelect & pwrinst2ReadCycle;
  wire        pwrinst2SpriteRegsReadStrobe = pwrinst2SpriteRegsSelect & readStrobe;
  wire        pwrinst2SpriteRegsWrite = pwrinst2SpriteRegsSelect & writeStrobe;
  wire        pwrinst2SpriteRegsWriteCycle = pwrinst2SpriteRegsSelect & pwrinst2WriteCycle;
  wire [23:0] pwrinst2SpriteRegsOffset = cpuByteAddr - 24'hA80000;
  wire        pwrinst2IrqCauseRead = pwrinst2SpriteRegsRead & (pwrinst2SpriteRegsOffset < 24'h8);
  wire        pwrinst2IrqCauseReadStrobe = pwrinst2SpriteRegsReadStrobe & (pwrinst2SpriteRegsOffset < 24'h8);
  wire        pwrinst2VideoIrqClear = pwrinst2IrqCauseReadStrobe & (pwrinst2SpriteRegsOffset == 24'h4);
  wire        pwrinst2UnknownIrqClear = pwrinst2IrqCauseReadStrobe & (pwrinst2SpriteRegsOffset == 24'h6);
  wire        pwrinst2Layer2RegsSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'hB00000) & (cpuByteAddr < 24'hB00006);
  wire        pwrinst2Layer0RegsSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'hB80000) & (cpuByteAddr < 24'hB80006);
  wire        pwrinst2Layer1RegsSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'hC00000) & (cpuByteAddr < 24'hC00006);
  wire        pwrinst2Layer3RegsSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'hC80000) & (cpuByteAddr < 24'hC80006);
  wire        pwrinst2SoundAckSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'hD80000) & (cpuByteAddr < 24'hD80002);
  wire        pwrinst2SoundAckRead = pwrinst2SoundAckSelect & pwrinst2ReadCycle;
  wire        pwrinst2SoundAckReadStrobe = pwrinst2SoundAckSelect & readStrobe;
  wire        pwrinst2SoundCmdSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'hE00000) & (cpuByteAddr < 24'hE00002);
  wire        pwrinst2SoundCmdRead = pwrinst2SoundCmdSelect & pwrinst2ReadCycle;
  wire        pwrinst2SoundWrite = pwrinst2SoundCmdSelect & writeStrobe;
  wire        pwrinst2SoundWriteCycle = pwrinst2SoundCmdSelect & pwrinst2WriteCycle;
  wire        pwrinst2EepromRead = pwrinst2ReadCycle & (cpuByteAddr >= 24'hE80000) & (cpuByteAddr < 24'hE80002);
  wire        pwrinst2PaletteSelect = gameIsPwrInst2 & (cpuByteAddr >= 24'hF00000) & (cpuByteAddr < 24'hF05000);
  wire [15:0] pwrinst2InputP1 =
    {5'h1F,
     ~io_player_0_buttons[3],
     ~servicePulseActive,
     ~coin1PulseActive,
     ~io_player_0_start,
     ~(io_player_0_buttons[2:0]),
     ~io_player_0_right,
     ~io_player_0_left,
     ~io_player_0_down,
     ~io_player_0_up};
  wire [15:0] pwrinst2InputP2 =
    {4'hF,
     _eeprom_io_serial_sdo,
     ~io_player_1_buttons[3],
     ~servicePulseActive,
     ~coin2PulseActive,
     ~io_player_1_start,
     ~(io_player_1_buttons[2:0]),
     ~io_player_1_right,
     ~io_player_1_left,
     ~io_player_1_down,
     ~io_player_1_up};
  wire [15:0] pwrinst2LayerRegDin =
    _cpu_io_addr[1:0] == 2'h2 ? {_cpu_io_dout[15:4], pwrinst2LayerMode(_cpu_io_dout[3:0])} : _cpu_io_dout;
  wire [15:0] layerRegsMemDin = gameIsPwrInst2 ? pwrinst2LayerRegDin : _cpu_io_dout;
  wire [15:0] pwrinst2Layer2RegsData =
    _cpu_io_addr[1:0] == 2'h0 ? pwrinst2Layer2Regs0 :
    _cpu_io_addr[1:0] == 2'h1 ? pwrinst2Layer2Regs1 :
    pwrinst2Layer2Regs2;
  wire        pwrinst2SyncReadSelect =
    pwrinst2ReadCycle &
    (pwrinst2MainRamSelect |
     pwrinst2Layer2Vram16Select | pwrinst2Layer2LineSelect | pwrinst2Layer2ScratchSelect |
     pwrinst2Layer2Vram8Select |
     pwrinst2Layer0Vram16Select | pwrinst2Layer0LineSelect | pwrinst2Layer0ScratchSelect |
     pwrinst2Layer0Vram8Select |
     pwrinst2Layer1Vram16Select | pwrinst2Layer1LineSelect | pwrinst2Layer1ScratchSelect |
     pwrinst2Layer1Vram8Select |
     pwrinst2Layer3Vram8Select |
     pwrinst2SpriteRamSelect |
     pwrinst2Layer2RegsSelect | pwrinst2Layer0RegsSelect | pwrinst2Layer1RegsSelect |
     pwrinst2Layer3RegsSelect |
     pwrinst2PaletteSelect);
  wire        pwrinst2Select =
    pwrinst2ProgRomSelect | pwrinst2ProgRomValid | pwrinst2ProgRomWriteCycle |
    pwrinst2MainRamSelect |
    pwrinst2Input0Read | pwrinst2Input1Read |
    pwrinst2ExtraRomRead | pwrinst2ExtraRomWriteCycle | pwrinst2EepromWriteCycle |
    pwrinst2Layer2Vram16Select | pwrinst2Layer2LineSelect | pwrinst2Layer2ScratchSelect |
    pwrinst2Layer2Vram8Select |
    pwrinst2Layer0Vram16Select | pwrinst2Layer0LineSelect | pwrinst2Layer0ScratchSelect |
    pwrinst2Layer0Vram8Select |
    pwrinst2Layer1Vram16Select | pwrinst2Layer1LineSelect | pwrinst2Layer1ScratchSelect |
    pwrinst2Layer1Vram8Select |
    pwrinst2Layer3Vram8Select |
    pwrinst2SpriteRamSelect | pwrinst2SpriteRegsRead | pwrinst2SpriteRegsWriteCycle |
    pwrinst2Layer2RegsSelect | pwrinst2Layer0RegsSelect | pwrinst2Layer1RegsSelect |
    pwrinst2Layer3RegsSelect | pwrinst2SoundAckRead | pwrinst2SoundCmdRead | pwrinst2SoundWriteCycle |
    pwrinst2EepromRead | pwrinst2PaletteSelect;
  wire        pwrinst2Dtack =
    pwrinst2ProgRomRead ? pwrinst2ProgRomValid :
    pwrinst2SyncReadSelect ? pwrinst2SyncReadPending :
    (_cpu_io_as & pwrinst2Select);
  wire [15:0] pwrinst2EepromData = {12'hFFF, _eeprom_io_serial_sdo ? 4'hF : 4'h7};
  wire [15:0] pwrinst2IrqCauseData =
    {13'h0,
     (pwrinst2SpriteRegsOffset == 24'h0) & ~agalletIrq,
     ~unknownIrq,
     ~videoIrq};
  wire [15:0] pwrinst2ReadData =
    pwrinst2ProgRomValid ? io_progRom_dout :
    pwrinst2MainRamSelect ? _mainRam_io_dout :
    pwrinst2Input0Read ? pwrinst2InputP1 :
    pwrinst2Input1Read ? pwrinst2InputP2 :
    pwrinst2ExtraRomRead ? 16'h0000 :
    pwrinst2Layer2Vram16Select ? _pwrinst2Layer2Vram16_io_portA_dout :
    pwrinst2Layer2LineSelect ? _pwrinst2Layer2LineRam_io_portA_dout :
    pwrinst2Layer2ScratchSelect ? pwrinst2Layer2ScratchData :
    pwrinst2Layer2Vram8Select ? _pwrinst2Layer2Vram8_io_portA_dout :
    pwrinst2Layer0Vram16Select ? _vram16x16_0_io_portA_dout :
    pwrinst2Layer0LineSelect ? _lineRam_0_io_portA_dout :
    pwrinst2Layer0ScratchSelect ? pwrinst2Layer0ScratchData :
    pwrinst2Layer0Vram8Select ? _vram8x8_0_io_portA_dout :
    pwrinst2Layer1Vram16Select ? _vram16x16_1_io_portA_dout :
    pwrinst2Layer1LineSelect ? _lineRam_1_io_portA_dout :
    pwrinst2Layer1ScratchSelect ? pwrinst2Layer1ScratchData :
    pwrinst2Layer1Vram8Select ? _vram8x8_1_io_portA_dout :
    pwrinst2Layer3Vram8Select ? _vram8x8_2_io_portA_dout :
    pwrinst2SpriteListRamSelect ? _spriteRam_io_portA_dout :
    pwrinst2SpriteExtraRamSelect ? _pwrinst2SpriteExtraRam_dout :
    pwrinst2SpriteRegsRead ? (pwrinst2IrqCauseRead ? pwrinst2IrqCauseData : 16'h0000) :
    pwrinst2Layer2RegsSelect ? pwrinst2Layer2RegsData :
    pwrinst2Layer0RegsSelect ? _layerRegs_0_io_mem_dout :
    pwrinst2Layer1RegsSelect ? _layerRegs_1_io_mem_dout :
    pwrinst2Layer3RegsSelect ? _layerRegs_2_io_mem_dout :
    pwrinst2SoundAckRead ? io_soundCtrl_reply :
    pwrinst2SoundCmdRead ? 16'h0000 :
    pwrinst2EepromRead ? pwrinst2EepromData :
    pwrinst2PaletteSelect ? _paletteRam_io_portA_dout :
    16'h0000;

`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  reg  [63:0] pwrinst2DebugMilestones;
  reg  [23:0] pwrinst2DebugLastAddr;
  reg  [23:0] pwrinst2DebugLastReadAddr;
  reg  [23:0] pwrinst2DebugLastWriteAddr;
  reg  [23:0] pwrinst2DebugFirstUnmappedAddr;
  reg  [15:0] pwrinst2DebugLastDin;
  reg  [15:0] pwrinst2DebugLastDout;
  reg  [7:0]  pwrinst2DebugLastCtrl;
  reg  [7:0]  pwrinst2DebugLastSelect;
  reg  [7:0]  pwrinst2DebugFirstUnmappedCtrl;
  reg  [7:0]  pwrinst2DebugFirstUnmappedSelect;
  reg         pwrinst2DebugFirstUnmappedValid;

  wire        pwrinst2DebugCpuCycle = gameIsPwrInst2 & (readStrobe | writeStrobe);
  wire        pwrinst2DebugUnmappedCycle = pwrinst2DebugCpuCycle & ~pwrinst2Select;
  wire [7:0]  pwrinst2DebugCtrlNow = {
    io_progRom_valid,
    pwrinst2Dtack,
    _cpu_io_rw,
    _cpu_io_as,
    _cpu_io_uds,
    _cpu_io_lds,
    readStrobe,
    writeStrobe
  };
  wire [7:0]  pwrinst2DebugSelectNow = {
    pwrinst2ProgRomSelect | pwrinst2ProgRomWrite | pwrinst2ProgRomValid,
    pwrinst2MainRamSelect,
    pwrinst2PaletteSelect,
    pwrinst2Layer0Vram16Select | pwrinst2Layer0LineSelect | pwrinst2Layer0ScratchSelect | pwrinst2Layer0Vram8Select,
    pwrinst2Layer1Vram16Select | pwrinst2Layer1LineSelect | pwrinst2Layer1ScratchSelect | pwrinst2Layer1Vram8Select,
    pwrinst2Layer2Vram16Select | pwrinst2Layer2LineSelect | pwrinst2Layer2ScratchSelect | pwrinst2Layer2Vram8Select | pwrinst2Layer3Vram8Select,
    pwrinst2SpriteRamSelect | pwrinst2SpriteRegsSelect,
    pwrinst2Input0Read | pwrinst2Input1Read | pwrinst2SoundAckRead | pwrinst2SoundCmdRead | pwrinst2SoundWrite | pwrinst2EepromRead | pwrinst2EepromWrite
  };
  wire [63:0] pwrinst2DebugLiveBits = {
    8'h00,
    cpuByteAddr,
    pwrinst2ReadData,
    pwrinst2DebugCtrlNow,
    pwrinst2DebugSelectNow
  };
  wire [63:0] pwrinst2DebugCpuBits = {
    pwrinst2DebugFirstUnmappedAddr,
    pwrinst2DebugLastAddr,
    pwrinst2DebugFirstUnmappedValid,
    pwrinst2SyncReadPending,
    pwrinst2Dtack,
    pwrinst2DebugUnmappedCycle,
    pwrinst2DebugFirstUnmappedCtrl[3:0],
    pwrinst2DebugLastSelect
  };
  wire [63:0] pwrinst2DebugWriteBits = {
    pwrinst2DebugLastWriteAddr,
    pwrinst2DebugLastReadAddr,
    pwrinst2DebugLastDout
  };
  wire [63:0] pwrinst2DebugDataBits = {
    pwrinst2DebugLastReadAddr,
    pwrinst2DebugLastWriteAddr,
    pwrinst2DebugLastDin
  };
  wire [63:0] pwrinst2DebugPaletteBits = {
    8'h00,
    pwrinst2DebugFirstUnmappedAddr,
    pwrinst2DebugFirstUnmappedCtrl,
    pwrinst2DebugFirstUnmappedSelect,
    pwrinst2DebugLastCtrl,
    pwrinst2DebugLastSelect
  };
`endif

  always @(posedge clock) begin
    if (reset | ~gameIsPwrInst2) begin
      pwrinst2Layer2ScratchData <= 16'h0000;
      pwrinst2Layer0ScratchData <= 16'h0000;
      pwrinst2Layer1ScratchData <= 16'h0000;
      pwrinst2Layer2Regs0 <= 16'h0000;
      pwrinst2Layer2Regs1 <= 16'h0000;
      pwrinst2Layer2Regs2 <= 16'h0000;
      pwrinst2SyncReadPending <= 1'b0;
    end
    else begin
      if (~pwrinst2SyncReadSelect)
        pwrinst2SyncReadPending <= 1'b0;
      else if (readStrobe)
        pwrinst2SyncReadPending <= 1'b1;
      if (pwrinst2Layer2ScratchSelect & writeStrobe)
        pwrinst2Layer2ScratchData <= pwrinst2ApplyMask(pwrinst2Layer2ScratchData, _cpu_io_dout, mainRam_io_mask);
      if (pwrinst2Layer0ScratchSelect & writeStrobe)
        pwrinst2Layer0ScratchData <= pwrinst2ApplyMask(pwrinst2Layer0ScratchData, _cpu_io_dout, mainRam_io_mask);
      if (pwrinst2Layer1ScratchSelect & writeStrobe)
        pwrinst2Layer1ScratchData <= pwrinst2ApplyMask(pwrinst2Layer1ScratchData, _cpu_io_dout, mainRam_io_mask);
      if (pwrinst2Layer2RegsSelect & writeStrobe) begin
        case (_cpu_io_addr[1:0])
          2'h0: pwrinst2Layer2Regs0 <= pwrinst2ApplyMask(pwrinst2Layer2Regs0, pwrinst2LayerRegDin, mainRam_io_mask);
          2'h1: pwrinst2Layer2Regs1 <= pwrinst2ApplyMask(pwrinst2Layer2Regs1, pwrinst2LayerRegDin, mainRam_io_mask);
          2'h2: pwrinst2Layer2Regs2 <= pwrinst2ApplyMask(pwrinst2Layer2Regs2, pwrinst2LayerRegDin, mainRam_io_mask);
          default: begin
          end
        endcase
      end
    end
  end

`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  always @(posedge clock) begin
    if (reset | ~gameIsPwrInst2) begin
      pwrinst2DebugMilestones <= 64'd0;
      pwrinst2DebugLastAddr <= 24'd0;
      pwrinst2DebugLastReadAddr <= 24'd0;
      pwrinst2DebugLastWriteAddr <= 24'd0;
      pwrinst2DebugFirstUnmappedAddr <= 24'd0;
      pwrinst2DebugLastDin <= 16'd0;
      pwrinst2DebugLastDout <= 16'd0;
      pwrinst2DebugLastCtrl <= 8'd0;
      pwrinst2DebugLastSelect <= 8'd0;
      pwrinst2DebugFirstUnmappedCtrl <= 8'd0;
      pwrinst2DebugFirstUnmappedSelect <= 8'd0;
      pwrinst2DebugFirstUnmappedValid <= 1'b0;
    end
    else begin
      if (pwrinst2DebugCpuCycle) begin
        pwrinst2DebugLastAddr <= cpuByteAddr;
        pwrinst2DebugLastCtrl <= pwrinst2DebugCtrlNow;
        pwrinst2DebugLastSelect <= pwrinst2DebugSelectNow;
      end
      if (readStrobe) begin
        pwrinst2DebugLastReadAddr <= cpuByteAddr;
        pwrinst2DebugMilestones[5] <= pwrinst2DebugMilestones[5] | (cpuByteAddr == 24'h100000);
      end
      if (writeStrobe) begin
        pwrinst2DebugLastWriteAddr <= cpuByteAddr;
        pwrinst2DebugLastDout <= _cpu_io_dout;
        pwrinst2DebugMilestones[6] <= pwrinst2DebugMilestones[6] | (cpuByteAddr == 24'h100000);
      end
      if (pwrinst2Dtack)
        pwrinst2DebugLastDin <= pwrinst2ReadData;
      if (pwrinst2DebugUnmappedCycle) begin
        pwrinst2DebugMilestones[63] <= 1'b1;
        if (~pwrinst2DebugFirstUnmappedValid) begin
          pwrinst2DebugFirstUnmappedValid <= 1'b1;
          pwrinst2DebugFirstUnmappedAddr <= cpuByteAddr;
          pwrinst2DebugFirstUnmappedCtrl <= pwrinst2DebugCtrlNow;
          pwrinst2DebugFirstUnmappedSelect <= pwrinst2DebugSelectNow;
        end
      end
      pwrinst2DebugMilestones[0] <= pwrinst2DebugMilestones[0] | (pwrinst2ProgRomValid & (cpuByteAddr == 24'h000000));
      pwrinst2DebugMilestones[1] <= pwrinst2DebugMilestones[1] | (pwrinst2ProgRomValid & (cpuByteAddr == 24'h000002));
      pwrinst2DebugMilestones[2] <= pwrinst2DebugMilestones[2] | (pwrinst2ProgRomValid & (cpuByteAddr == 24'h000004));
      pwrinst2DebugMilestones[3] <= pwrinst2DebugMilestones[3] | (pwrinst2ProgRomValid & (cpuByteAddr == 24'h000006));
      pwrinst2DebugMilestones[4] <= pwrinst2DebugMilestones[4] | (pwrinst2ProgRomValid & (cpuByteAddr >= 24'h000200));
      pwrinst2DebugMilestones[7] <= pwrinst2DebugMilestones[7] | (writeStrobe & (cpuByteAddr == 24'h40FFE8));
      pwrinst2DebugMilestones[8] <= pwrinst2DebugMilestones[8] | (pwrinst2PaletteSelect & writeStrobe);
      pwrinst2DebugMilestones[9] <= pwrinst2DebugMilestones[9] | (pwrinst2PaletteSelect & pwrinst2Dtack);
      pwrinst2DebugMilestones[10] <= pwrinst2DebugMilestones[10] | (pwrinst2Layer0Vram16Select & writeStrobe);
      pwrinst2DebugMilestones[11] <= pwrinst2DebugMilestones[11] | (pwrinst2Layer0Vram16Select & pwrinst2Dtack);
      pwrinst2DebugMilestones[12] <= pwrinst2DebugMilestones[12] | (pwrinst2Layer1Vram16Select & writeStrobe);
      pwrinst2DebugMilestones[13] <= pwrinst2DebugMilestones[13] | (pwrinst2Layer1Vram16Select & pwrinst2Dtack);
      pwrinst2DebugMilestones[14] <= pwrinst2DebugMilestones[14] | (pwrinst2Layer2Vram16Select & writeStrobe);
      pwrinst2DebugMilestones[15] <= pwrinst2DebugMilestones[15] | (pwrinst2Layer2Vram16Select & pwrinst2Dtack);
      pwrinst2DebugMilestones[16] <= pwrinst2DebugMilestones[16] | (pwrinst2Layer3Vram8Select & writeStrobe);
      pwrinst2DebugMilestones[17] <= pwrinst2DebugMilestones[17] | (pwrinst2Layer3Vram8Select & pwrinst2Dtack);
      pwrinst2DebugMilestones[18] <= pwrinst2DebugMilestones[18] | (pwrinst2SpriteListRamSelect & writeStrobe);
      pwrinst2DebugMilestones[19] <= pwrinst2DebugMilestones[19] | (pwrinst2SpriteListRamSelect & pwrinst2Dtack);
      pwrinst2DebugMilestones[20] <= pwrinst2DebugMilestones[20] | (pwrinst2SpriteExtraRamSelect & writeStrobe);
      pwrinst2DebugMilestones[21] <= pwrinst2DebugMilestones[21] | (pwrinst2SpriteExtraRamSelect & pwrinst2Dtack);
      pwrinst2DebugMilestones[22] <= pwrinst2DebugMilestones[22] | (pwrinst2MainRamSelect & writeStrobe);
      pwrinst2DebugMilestones[23] <= pwrinst2DebugMilestones[23] | (pwrinst2MainRamSelect & pwrinst2Dtack);
      pwrinst2DebugMilestones[24] <= pwrinst2DebugMilestones[24] | pwrinst2IrqCauseRead;
      pwrinst2DebugMilestones[25] <= pwrinst2DebugMilestones[25] | pwrinst2VideoIrqClear;
      pwrinst2DebugMilestones[26] <= pwrinst2DebugMilestones[26] | pwrinst2UnknownIrqClear;
      pwrinst2DebugMilestones[27] <= pwrinst2DebugMilestones[27] | pwrinst2SoundWrite;
      pwrinst2DebugMilestones[28] <= pwrinst2DebugMilestones[28] | pwrinst2SoundAckRead;
      pwrinst2DebugMilestones[29] <= pwrinst2DebugMilestones[29] | pwrinst2Input0Read;
      pwrinst2DebugMilestones[30] <= pwrinst2DebugMilestones[30] | pwrinst2Input1Read;
      pwrinst2DebugMilestones[31] <= pwrinst2DebugMilestones[31] | pwrinst2EepromRead;
      pwrinst2DebugMilestones[32] <= pwrinst2DebugMilestones[32] | pwrinst2Layer0RegsSelect;
      pwrinst2DebugMilestones[33] <= pwrinst2DebugMilestones[33] | pwrinst2Layer1RegsSelect;
      pwrinst2DebugMilestones[34] <= pwrinst2DebugMilestones[34] | pwrinst2Layer2RegsSelect;
      pwrinst2DebugMilestones[35] <= pwrinst2DebugMilestones[35] | pwrinst2Layer3RegsSelect;
      pwrinst2DebugMilestones[36] <= pwrinst2DebugMilestones[36] | pwrinst2SyncReadPending;
      pwrinst2DebugMilestones[37] <= pwrinst2DebugMilestones[37] | (pwrinst2SpriteRegsWrite & (pwrinst2SpriteRegsOffset == 24'h7A));
      pwrinst2DebugMilestones[38] <= pwrinst2DebugMilestones[38] | (pwrinst2SpriteRegsWrite & (pwrinst2SpriteRegsOffset == 24'h04));
      pwrinst2DebugMilestones[39] <= pwrinst2DebugMilestones[39] | (pwrinst2SpriteRegsWrite & (pwrinst2SpriteRegsOffset == 24'h06));
      pwrinst2DebugMilestones[40] <= pwrinst2DebugMilestones[40] | pwrinst2SoundCmdRead;
      pwrinst2DebugMilestones[41] <= pwrinst2DebugMilestones[41] | pwrinst2SoundWriteCycle;
    end
  end
`endif

  wire        cs_1 = cpuByteAddr > 24'h10FFFF & cpuByteAddr < 24'h200000;
  wire        cs_2 = cpuByteAddr < 24'h100000;
  wire        _GEN_0 = cs_2 & _cpu_io_rw & io_progRom_valid;
  wire        cs_3 = (|(_cpu_io_addr[22:19])) & cpuByteAddr < 24'h110000;
  wire        cs_4 = cpuByteAddr > 24'h2FFFFF & cpuByteAddr < 24'h300004;
  wire        cs_5 = (|(_cpu_io_addr[22:21])) & cpuByteAddr < 24'h410000;
  wire        cs_6 = cpuByteAddr > 24'h4FFFFF & cpuByteAddr < 24'h501000;
  wire        cs_7 = cpuByteAddr > 24'h500FFF & cpuByteAddr < 24'h501800;
  wire        cs_8 = cpuByteAddr > 24'h5017FF & cpuByteAddr < 24'h504000;
  reg  [15:0] tmp;
  wire        cs_9 = cpuByteAddr > 24'h503FFF & cpuByteAddr < 24'h508000;
  wire        cs_10 = cpuByteAddr > 24'h507FFF & cpuByteAddr < 24'h510000;
  reg  [15:0] tmp_1;
  wire        cs_11 = cpuByteAddr > 24'h5FFFFF & cpuByteAddr < 24'h601000;
  wire        cs_12 = cpuByteAddr > 24'h600FFF & cpuByteAddr < 24'h601800;
  wire        cs_13 = cpuByteAddr > 24'h6017FF & cpuByteAddr < 24'h604000;
  reg  [15:0] tmp_2;
  wire        cs_14 = cpuByteAddr > 24'h603FFF & cpuByteAddr < 24'h608000;
  wire        cs_15 = cpuByteAddr > 24'h607FFF & cpuByteAddr < 24'h610000;
  reg  [15:0] tmp_3;
  wire        cs_16 = cpuByteAddr > 24'h707FFF & cpuByteAddr < 24'h709000;
  wire        cs_17 = cpuByteAddr > 24'h710C11 & cpuByteAddr < 24'h710C20;
  wire        _GEN_1 = _cpu_io_addr[22] & cpuByteAddr < 24'h800008 & readStrobe;
  wire        cs_19 = _cpu_io_addr[22] & cpuByteAddr < 24'h800010;
  wire        _GEN_2 = cpuByteAddr > 24'h800007 & cpuByteAddr < 24'h800009 & writeStrobe;
  wire        _GEN_3 = gameIsDFeveron & _GEN_2;
  wire        cs_21 = cpuByteAddr > 24'h800009 & cpuByteAddr < 24'h800080;
  wire        cs_22 = cpuByteAddr > 24'h8FFFFF & cpuByteAddr < 24'h900006;
  wire        cs_23 = cpuByteAddr > 24'h9FFFFF & cpuByteAddr < 24'hA00006;
  wire        _GEN_4 = cpuByteAddr > 24'hAFFFFF & cpuByteAddr < 24'hB00001 & readStrobe;
  wire        _GEN_5 = cpuByteAddr > 24'hB00001 & cpuByteAddr < 24'hB00003 & readStrobe;
  wire        _GEN_6 =
    _GEN_5 | _GEN_4 | cs_23 | cs_22 | cs_21 | _GEN_2 | cs_19 & ~_cpu_io_rw | _GEN_1
    | cs_17 | cs_16 | cs_15 | cs_14 | cs_13 | cs_12 | cs_11 | cs_10 | cs_9 | cs_8 | cs_7
    | cs_6 | cs_5 | cs_4 | cs_3 | _GEN_0 | cs_1;
  wire        cs_26 = cpuByteAddr > 24'hBFFFFF & cpuByteAddr < 24'hC00001;
  wire        _GEN_7 = cs_26 & ~_cpu_io_rw;
  wire        _GEN_8 = gameIsDFeveron ? _GEN_7 | _GEN_6 | dtackReg : cs_1 | dtackReg;
  wire        cs_27 = cpuByteAddr < 24'h80000;
  wire        _GEN_10 = gameIsDonPachi ? cs_27 & readStrobe : gameIsDFeveron & cs_2 & readStrobe;
  wire        _GEN_11 = cs_27 & _cpu_io_rw & io_progRom_valid;
  wire        cs_28 = (|(_cpu_io_addr[22:19])) & cpuByteAddr < 24'h110000;
  wire        _GEN_12 = gameIsDonPachi ? cs_28 & readStrobe : gameIsDFeveron & cs_3 & readStrobe;
  wire        _GEN_13 = gameIsDonPachi ? cs_28 & writeStrobe : gameIsDFeveron & cs_3 & writeStrobe;
  wire        cs_29 = (|(_cpu_io_addr[22:20])) & cpuByteAddr < 24'h201000;
  wire        _GEN_14 = gameIsDonPachi ? cs_29 & readStrobe : gameIsDFeveron & cs_11 & readStrobe;
  wire        _GEN_15 = gameIsDonPachi ? cs_29 & writeStrobe : gameIsDFeveron & cs_11 & writeStrobe;
  wire        cs_30 = cpuByteAddr > 24'h200FFF & cpuByteAddr < 24'h201800;
  wire        _GEN_16 = gameIsDonPachi ? cs_30 & readStrobe : gameIsDFeveron & cs_12 & readStrobe;
  wire        _GEN_17 = gameIsDonPachi ? cs_30 & writeStrobe : gameIsDFeveron & cs_12 & writeStrobe;
  wire        cs_31 = cpuByteAddr > 24'h2017FF & cpuByteAddr < 24'h204000;
  reg  [15:0] tmp_4;
  wire        cs_32 = cpuByteAddr > 24'h203FFF & cpuByteAddr < 24'h208000;
  wire        _GEN_18 = gameIsDonPachi ? cs_32 & readStrobe : gameIsDFeveron & cs_14 & readStrobe;
  wire        _GEN_19 = gameIsDonPachi ? cs_32 & writeStrobe : gameIsDFeveron & cs_14 & writeStrobe;
  wire        cs_33 = cpuByteAddr > 24'h207FFF & cpuByteAddr < 24'h210000;
  reg  [15:0] tmp_5;
  wire        cs_34 = cpuByteAddr > 24'h2FFFFF & cpuByteAddr < 24'h301000;
  wire        _GEN_20 = gameIsDonPachi ? cs_34 & readStrobe : gameIsDFeveron & cs_6 & readStrobe;
  wire        _GEN_21 = gameIsDonPachi ? cs_34 & writeStrobe : gameIsDFeveron & cs_6 & writeStrobe;
  wire        cs_35 = cpuByteAddr > 24'h300FFF & cpuByteAddr < 24'h301800;
  wire        _GEN_22 = gameIsDonPachi ? cs_35 & readStrobe : gameIsDFeveron & cs_7 & readStrobe;
  wire        _GEN_23 = gameIsDonPachi ? cs_35 & writeStrobe : gameIsDFeveron & cs_7 & writeStrobe;
  wire        cs_36 = cpuByteAddr > 24'h3017FF & cpuByteAddr < 24'h304000;
  reg  [15:0] tmp_6;
  wire        cs_37 = cpuByteAddr > 24'h303FFF & cpuByteAddr < 24'h308000;
  wire        _GEN_24 = gameIsDonPachi ? cs_37 & readStrobe : gameIsDFeveron & cs_9 & readStrobe;
  wire        _GEN_25 = gameIsDonPachi ? cs_37 & writeStrobe : gameIsDFeveron & cs_9 & writeStrobe;
  wire        cs_38 = cpuByteAddr > 24'h307FFF & cpuByteAddr < 24'h310000;
  reg  [15:0] tmp_7;
  wire        cs_39 = (|(_cpu_io_addr[22:21])) & cpuByteAddr < 24'h410000;
  wire        cs_40 = cpuByteAddr > 24'h4FFFFF & cpuByteAddr < 24'h510000;
  wire        _GEN_26 = gameIsDonPachi ? cs_40 & readStrobe : gameIsDFeveron & cs_5 & readStrobe;
  wire        _GEN_27 = gameIsDonPachi ? cs_40 & writeStrobe : gameIsDFeveron & cs_5 & writeStrobe;
  wire        cs_41 = cpuByteAddr > 24'h5FFFFF & cpuByteAddr < 24'h600006;
  wire        _GEN_28 = gameIsDonPachi ? cs_41 & writeStrobe : gameIsDFeveron & cs_23 & writeStrobe;
  wire        cs_42 = cpuByteAddr > 24'h6FFFFF & cpuByteAddr < 24'h700006;
  wire        _GEN_29 = gameIsDonPachi ? cs_42 & writeStrobe : gameIsDFeveron & cs_22 & writeStrobe;
  wire        cs_43 = _cpu_io_addr[22] & cpuByteAddr < 24'h800006;
  wire        _GEN_30 = cpuByteAddr > 24'h8FFFFF & cpuByteAddr < 24'h900008 & readStrobe;
  wire        cs_45 = cpuByteAddr > 24'h8FFFFF & cpuByteAddr < 24'h900010;
  wire        _GEN_31 = gameIsDonPachi ? mem_1_wr : gameIsDFeveron & cs_19 & writeStrobe;
  assign mem_1_wr = cs_45 & writeStrobe;
  wire        _GEN_32 = cpuByteAddr > 24'h900007 & cpuByteAddr < 24'h900009 & writeStrobe;
  wire        _GEN_33 =
    gameIsDonPachi
      ? _GEN_32 | _GEN_3 | vblankForcedSpriteSwap
      : _GEN_3 | vblankForcedSpriteSwap;
  wire        cs_47 = cpuByteAddr > 24'h900009 & cpuByteAddr < 24'h900080;
  wire        cs_48 = cpuByteAddr > 24'hA07FFF & cpuByteAddr < 24'hA09000;
  wire        _GEN_34 = gameIsDonPachi ? cs_48 & readStrobe : gameIsDFeveron & cs_16 & readStrobe;
  wire        _GEN_35 = gameIsDonPachi ? cs_48 & writeStrobe : gameIsDFeveron & cs_16 & writeStrobe;
  wire [10:0] _GEN_36 = gameIsDonPachi ? _cpu_io_addr[10:0] : _cpu_io_addr[10:0];
  wire        cs_49 = cpuByteAddr > 24'hAFFFFF & cpuByteAddr < 24'hB00004;
  wire        cs_50 = cpuByteAddr > 24'hB0000F & cpuByteAddr < 24'hB00014;
  wire        cs_51 = cpuByteAddr > 24'hB0001F & cpuByteAddr < 24'hB00030;
  wire        _GEN_37 = cpuByteAddr > 24'hBFFFFF & cpuByteAddr < 24'hC00001 & readStrobe;
  wire        _GEN_38 = cpuByteAddr > 24'hC00001 & cpuByteAddr < 24'hC00003 & readStrobe;
  wire        _GEN_39 =
    _GEN_38 | _GEN_37 | cs_51 & ~_cpu_io_rw | cs_50 | cs_49 | cs_48 | cs_47 | _GEN_32
    | cs_45 & ~_cpu_io_rw | _GEN_30 | cs_43 | cs_42 | cs_41 | cs_40 | cs_39 | cs_38
    | cs_37 | cs_36 | cs_35 | cs_34 | cs_33 | cs_32 | cs_31 | cs_30 | cs_29 | cs_28
    | _GEN_11;
  wire        cs_54 = cpuByteAddr > 24'hCFFFFF & cpuByteAddr < 24'hD00001;
  wire        _GEN_40 = cs_54 & ~_cpu_io_rw;
  wire        _GEN_41 = _GEN_40 | _GEN_39;
  wire        _GEN_42 = gameIsDonPachi & _GEN_41 | _GEN_8;
  wire        cs_55 = cpuByteAddr < 24'h100000;
  wire        _GEN_44 = gameIsDoDonPachi ? cs_55 & readStrobe : _GEN_10;
  wire        _GEN_45 = cs_55 & _cpu_io_rw & io_progRom_valid;
  wire        cs_56 = (|(_cpu_io_addr[22:19])) & cpuByteAddr < 24'h110000;
  wire        _GEN_46 = gameIsDoDonPachi ? cs_56 & readStrobe : _GEN_12;
  wire        _GEN_47 = gameIsDoDonPachi ? cs_56 & writeStrobe : _GEN_13;
  wire        cs_57 = cpuByteAddr > 24'h2FFFFF & cpuByteAddr < 24'h300004;
  wire        _GEN_48 = gameIsDoDonPachi ? cs_57 & readStrobe : gameIsDFeveron & cs_4 & readStrobe;
  wire        _GEN_49 = gameIsDoDonPachi ? cs_57 & writeStrobe : gameIsDFeveron & cs_4 & writeStrobe;
  wire        cs_58 = (|(_cpu_io_addr[22:21])) & cpuByteAddr < 24'h410000;
  wire        _GEN_50 = gameIsDoDonPachi ? cs_58 & readStrobe : _GEN_26;
  wire        _GEN_51 = gameIsDoDonPachi ? cs_58 & writeStrobe : _GEN_27;
  wire        cs_59 = cpuByteAddr > 24'h4FFFFF & cpuByteAddr < 24'h501000;
  wire        _GEN_52 = gameIsDoDonPachi ? cs_59 & readStrobe : _GEN_20;
  wire        _GEN_53 = gameIsDoDonPachi ? cs_59 & writeStrobe : _GEN_21;
  wire        cs_60 = cpuByteAddr > 24'h500FFF & cpuByteAddr < 24'h501800;
  wire        _GEN_54 = gameIsDoDonPachi ? cs_60 & readStrobe : _GEN_22;
  wire        _GEN_55 = gameIsDoDonPachi ? cs_60 & writeStrobe : _GEN_23;
  wire        cs_61 = cpuByteAddr > 24'h5017FF & cpuByteAddr < 24'h504000;
  reg  [15:0] tmp_8;
  wire        cs_62 = cpuByteAddr > 24'h503FFF & cpuByteAddr < 24'h508000;
  wire        _GEN_56 = gameIsDoDonPachi ? cs_62 & readStrobe : _GEN_24;
  wire        _GEN_57 = gameIsDoDonPachi ? cs_62 & writeStrobe : _GEN_25;
  wire        cs_63 = cpuByteAddr > 24'h507FFF & cpuByteAddr < 24'h510000;
  reg  [15:0] tmp_9;
  wire        cs_65 = cpuByteAddr > 24'h5FFFFF & cpuByteAddr < 24'h601000;
  wire        _GEN_58 = gameIsDoDonPachi ? cs_65 & readStrobe : _GEN_14;
  wire        _GEN_59 = gameIsDoDonPachi ? cs_65 & writeStrobe : _GEN_15;
  wire        cs_66 = cpuByteAddr > 24'h600FFF & cpuByteAddr < 24'h601800;
  wire        _GEN_60 = gameIsDoDonPachi ? cs_66 & readStrobe : _GEN_16;
  wire        _GEN_61 = gameIsDoDonPachi ? cs_66 & writeStrobe : _GEN_17;
  wire        cs_67 = cpuByteAddr > 24'h6017FF & cpuByteAddr < 24'h604000;
  reg  [15:0] tmp_10;
  wire        cs_68 = cpuByteAddr > 24'h603FFF & cpuByteAddr < 24'h608000;
  wire        _GEN_62 = gameIsDoDonPachi ? cs_68 & readStrobe : _GEN_18;
  wire        _GEN_63 = gameIsDoDonPachi ? cs_68 & writeStrobe : _GEN_19;
  wire        cs_69 = cpuByteAddr > 24'h607FFF & cpuByteAddr < 24'h610000;
  reg  [15:0] tmp_11;
  wire        cs_70 = cpuByteAddr > 24'h6FFFFF & cpuByteAddr < 24'h710000;
  wire        _GEN_64 = gameIsDoDonPachi ? cs_70 & readStrobe : gameIsDonPachi & cs_39 & readStrobe;
  wire        _GEN_65 = gameIsDoDonPachi ? cs_70 & writeStrobe : gameIsDonPachi & cs_39 & writeStrobe;
  wire [12:0] _GEN_66 = gameIsDoDonPachi ? _cpu_io_addr[12:0] : _cpu_io_addr[12:0];
  wire        _GEN_67 = _cpu_io_addr[22] & cpuByteAddr < 24'h800008 & readStrobe;
  wire        cs_72 = _cpu_io_addr[22] & cpuByteAddr < 24'h800010;
  wire        _GEN_68 = gameIsDoDonPachi ? mem_2_wr : _GEN_31;
  assign mem_2_wr = cs_72 & writeStrobe;
  wire        _GEN_69 = cpuByteAddr > 24'h800007 & cpuByteAddr < 24'h800009 & writeStrobe;
  wire        _GEN_70 = gameIsDoDonPachi & _GEN_69;
  wire        cs_74 = cpuByteAddr > 24'h800009 & cpuByteAddr < 24'h800080;
  wire        cs_75 = cpuByteAddr > 24'h8FFFFF & cpuByteAddr < 24'h900006;
  wire        _GEN_71 = gameIsDoDonPachi ? cs_75 & writeStrobe : _GEN_29;
  wire        cs_76 = cpuByteAddr > 24'h9FFFFF & cpuByteAddr < 24'hA00006;
  wire        _GEN_72 = gameIsDoDonPachi ? cs_76 & writeStrobe : _GEN_28;
  wire        cs_77 = cpuByteAddr > 24'hAFFFFF & cpuByteAddr < 24'hB00006;
  wire        _GEN_73 = gameIsDoDonPachi ? cs_77 & writeStrobe : gameIsDonPachi & cs_43 & writeStrobe;
  wire        cs_78 = cpuByteAddr > 24'hBFFFFF & cpuByteAddr < 24'hC10000;
  wire        _GEN_74 = gameIsDoDonPachi ? cs_78 & readStrobe : _GEN_34;
  wire        _GEN_75 = gameIsDoDonPachi ? cs_78 & writeStrobe : _GEN_35;
  wire        _GEN_76 = cpuByteAddr > 24'hCFFFFF & cpuByteAddr < 24'hD00001 & readStrobe;
  wire        _GEN_77 = cpuByteAddr > 24'hD00001 & cpuByteAddr < 24'hD00003 & readStrobe;
  wire        _GEN_78 =
    _GEN_77 | _GEN_76 | cs_78 | cs_77 | cs_76 | cs_75 | cs_74 | _GEN_69 | cs_72
    & ~_cpu_io_rw | _GEN_67 | cs_70 | cs_69 | cs_68 | cs_67 | cs_66 | cs_65
    | cpuByteAddr > 24'h5FFEFF & cpuByteAddr < 24'h600000 & writeStrobe | cs_63 | cs_62 | cs_61 | cs_60
    | cs_59 | cs_58 | cs_57 | cs_56 | _GEN_45;
  wire        cs_81 = cpuByteAddr > 24'hDFFFFF & cpuByteAddr < 24'hE00001;
  wire        _GEN_79 = cs_81 & ~_cpu_io_rw;
  wire        _GEN_80 = _GEN_79 | _GEN_78;
  wire        _GEN_81 = _GEN_80 | _GEN_42;
  wire        _GEN_82 = gameIsDoDonPachi & _GEN_80 | _GEN_42;
  wire        cs_82 = cpuByteAddr < 24'h100000;
  wire        _GEN_84 = gameIsEsprade ? cs_82 & readStrobe : _GEN_44;
  wire        _GEN_85 = cs_82 & _cpu_io_rw & io_progRom_valid;
  wire        cs_83 = (|(_cpu_io_addr[22:19])) & cpuByteAddr < 24'h110000;
  wire        _GEN_86 = gameIsEsprade ? cs_83 & readStrobe : _GEN_46;
  wire        _GEN_87 = gameIsEsprade ? cs_83 & writeStrobe : _GEN_47;
  wire        cs_84 = cpuByteAddr > 24'h2FFFFF & cpuByteAddr < 24'h300004;
  wire        _GEN_88 = gameIsEsprade ? cs_84 & readStrobe : _GEN_48;
  wire        _GEN_89 = gameIsEsprade ? cs_84 & writeStrobe : _GEN_49;
  wire        cs_85 = (|(_cpu_io_addr[22:21])) & cpuByteAddr < 24'h410000;
  wire        _GEN_90 = gameIsEsprade ? cs_85 & readStrobe : _GEN_50;
  wire        _GEN_91 = gameIsEsprade ? cs_85 & writeStrobe : _GEN_51;
  wire        cs_86 = cpuByteAddr > 24'h4FFFFF & cpuByteAddr < 24'h501000;
  wire        _GEN_92 = gameIsEsprade ? cs_86 & readStrobe : _GEN_52;
  wire        _GEN_93 = gameIsEsprade ? cs_86 & writeStrobe : _GEN_53;
  wire        cs_87 = cpuByteAddr > 24'h500FFF & cpuByteAddr < 24'h501800;
  wire        _GEN_94 = gameIsEsprade ? cs_87 & readStrobe : _GEN_54;
  wire        _GEN_95 = gameIsEsprade ? cs_87 & writeStrobe : _GEN_55;
  wire        cs_88 = cpuByteAddr > 24'h5017FF & cpuByteAddr < 24'h504000;
  reg  [15:0] tmp_12;
  wire        cs_89 = cpuByteAddr > 24'h503FFF & cpuByteAddr < 24'h508000;
  wire        _GEN_96 = gameIsEsprade ? cs_89 & readStrobe : _GEN_56;
  wire        _GEN_97 = gameIsEsprade ? cs_89 & writeStrobe : _GEN_57;
  wire        cs_90 = cpuByteAddr > 24'h507FFF & cpuByteAddr < 24'h510000;
  reg  [15:0] tmp_13;
  wire        cs_91 = cpuByteAddr > 24'h5FFFFF & cpuByteAddr < 24'h601000;
  wire        _GEN_98 = gameIsEsprade ? cs_91 & readStrobe : _GEN_58;
  wire        _GEN_99 = gameIsEsprade ? cs_91 & writeStrobe : _GEN_59;
  wire        cs_92 = cpuByteAddr > 24'h600FFF & cpuByteAddr < 24'h601800;
  wire        _GEN_100 = gameIsEsprade ? cs_92 & readStrobe : _GEN_60;
  wire        _GEN_101 = gameIsEsprade ? cs_92 & writeStrobe : _GEN_61;
  wire        cs_93 = cpuByteAddr > 24'h6017FF & cpuByteAddr < 24'h604000;
  reg  [15:0] tmp_14;
  wire        cs_94 = cpuByteAddr > 24'h603FFF & cpuByteAddr < 24'h608000;
  wire        _GEN_102 = gameIsEsprade ? cs_94 & readStrobe : _GEN_62;
  wire        _GEN_103 = gameIsEsprade ? cs_94 & writeStrobe : _GEN_63;
  wire        cs_95 = cpuByteAddr > 24'h607FFF & cpuByteAddr < 24'h610000;
  reg  [15:0] tmp_15;
  wire        cs_96 = cpuByteAddr > 24'h6FFFFF & cpuByteAddr < 24'h701000;
  wire        cs_97 = cpuByteAddr > 24'h700FFF & cpuByteAddr < 24'h701800;
  wire        cs_98 = cpuByteAddr > 24'h7017FF & cpuByteAddr < 24'h704000;
  reg  [15:0] tmp_16;
  wire        cs_99 = cpuByteAddr > 24'h703FFF & cpuByteAddr < 24'h708000;
  wire        _GEN_104 = gameIsEsprade ? cs_99 & readStrobe : _GEN_64;
  wire        _GEN_105 = gameIsEsprade ? cs_99 & writeStrobe : _GEN_65;
  wire        cs_100 = cpuByteAddr > 24'h707FFF & cpuByteAddr < 24'h710000;
  reg  [15:0] tmp_17;
  wire        _GEN_106 = _cpu_io_addr[22] & cpuByteAddr < 24'h800008 & readStrobe;
  wire        cs_102 = _cpu_io_addr[22] & cpuByteAddr < 24'h800010;
  wire        _GEN_107 = gameIsEsprade ? mem_3_wr : _GEN_68;
  assign mem_3_wr = cs_102 & writeStrobe;
  wire        _GEN_108 = cpuByteAddr > 24'h800007 & cpuByteAddr < 24'h800009 & writeStrobe;
  wire        _GEN_109 = gameIsEsprade ? _GEN_108 | _GEN_70 | _GEN_33 : _GEN_70 | _GEN_33;
  wire        cs_104 = cpuByteAddr > 24'h800009 & cpuByteAddr < 24'h800080;
  wire        _GEN_110 = cpuByteAddr > 24'h800EFF & cpuByteAddr < 24'h800F04 & readStrobe;
  wire        cs_106 = cpuByteAddr > 24'h8FFFFF & cpuByteAddr < 24'h900006;
  wire        _GEN_111 = gameIsEsprade ? cs_106 & writeStrobe : _GEN_71;
  wire        cs_107 = cpuByteAddr > 24'h9FFFFF & cpuByteAddr < 24'hA00006;
  wire        _GEN_112 = gameIsEsprade ? cs_107 & writeStrobe : _GEN_72;
  wire        cs_108 = cpuByteAddr > 24'hAFFFFF & cpuByteAddr < 24'hB00006;
  wire        _GEN_113 = gameIsEsprade ? cs_108 & writeStrobe : _GEN_73;
  wire        cs_109 = cpuByteAddr > 24'hBFFFFF & cpuByteAddr < 24'hC10000;
  wire        _GEN_114 = gameIsEsprade ? cs_109 & readStrobe : _GEN_74;
  wire        _GEN_115 = gameIsEsprade ? cs_109 & writeStrobe : _GEN_75;
  wire        _GEN_116 = cpuByteAddr > 24'hCFFFFF & cpuByteAddr < 24'hD00001 & readStrobe;
  wire        _GEN_117 = cpuByteAddr > 24'hD00001 & cpuByteAddr < 24'hD00003 & readStrobe;
  wire        _GEN_118 =
    _GEN_117 | _GEN_116 | cs_109 | cs_108 | cs_107 | cs_106 | _GEN_110 | cs_104 | _GEN_108
    | cs_102 & ~_cpu_io_rw | _GEN_106 | cs_100 | cs_99 | cs_98 | cs_97 | cs_96 | cs_95
    | cs_94 | cs_93 | cs_92 | cs_91 | cs_90 | cs_89 | cs_88 | cs_87 | cs_86 | cs_85
    | cs_84 | cs_83 | _GEN_85;
  wire        cs_112 = cpuByteAddr > 24'hDFFFFF & cpuByteAddr < 24'hE00001;
  wire        _GEN_119 = cs_112 & ~_cpu_io_rw;
  wire        _GEN_120 = _GEN_119 | _GEN_118;
  wire        _GEN_121 = _GEN_120 | _GEN_82;
  wire        cs_113 = cpuByteAddr < 24'h100000;
  wire        _GEN_122 = gameIsGaia ? cs_113 & readStrobe : _GEN_84;
  wire        cs_115 = (|(_cpu_io_addr[22:19])) & cpuByteAddr < 24'h110000;
  wire        _GEN_123 = gameIsGaia ? cs_115 & readStrobe : _GEN_86;
  wire        _GEN_124 = gameIsGaia ? cs_115 & writeStrobe : _GEN_87;
  wire        cs_116 = cpuByteAddr > 24'h2FFFFF & cpuByteAddr < 24'h300004;
  wire        _GEN_125 = gameIsGaia ? cs_116 & readStrobe : _GEN_88;
  wire        _GEN_126 = gameIsGaia ? cs_116 & writeStrobe : _GEN_89;
  wire        cs_117 = (|(_cpu_io_addr[22:21])) & cpuByteAddr < 24'h410000;
  wire        _GEN_127 = gameIsGaia ? cs_117 & readStrobe : _GEN_90;
  wire        _GEN_128 = gameIsGaia ? cs_117 & writeStrobe : _GEN_91;
  wire        cs_118 = cpuByteAddr > 24'h4FFFFF & cpuByteAddr < 24'h501000;
  wire        _GEN_129 = gameIsGaia ? cs_118 & readStrobe : _GEN_92;
  wire        _GEN_130 = gameIsGaia ? cs_118 & writeStrobe : _GEN_93;
  wire        cs_119 = cpuByteAddr > 24'h500FFF & cpuByteAddr < 24'h501800;
  wire        _GEN_131 = gameIsGaia ? cs_119 & readStrobe : _GEN_94;
  wire        _GEN_132 = gameIsGaia ? cs_119 & writeStrobe : _GEN_95;
  reg  [15:0] tmp_18;
  wire        cs_121 = cpuByteAddr > 24'h503FFF & cpuByteAddr < 24'h508000;
  wire        _GEN_133 = gameIsGaia ? cs_121 & readStrobe : _GEN_96;
  wire        _GEN_134 = gameIsGaia ? cs_121 & writeStrobe : _GEN_97;
  reg  [15:0] tmp_19;
  wire        cs_123 = cpuByteAddr > 24'h5FFFFF & cpuByteAddr < 24'h601000;
  wire        _GEN_135 = gameIsGaia ? cs_123 & readStrobe : _GEN_98;
  wire        _GEN_136 = gameIsGaia ? cs_123 & writeStrobe : _GEN_99;
  wire        cs_124 = cpuByteAddr > 24'h600FFF & cpuByteAddr < 24'h601800;
  wire        _GEN_137 = gameIsGaia ? cs_124 & readStrobe : _GEN_100;
  wire        _GEN_138 = gameIsGaia ? cs_124 & writeStrobe : _GEN_101;
  reg  [15:0] tmp_20;
  wire        cs_126 = cpuByteAddr > 24'h603FFF & cpuByteAddr < 24'h608000;
  wire        _GEN_139 = gameIsGaia ? cs_126 & readStrobe : _GEN_102;
  wire        _GEN_140 = gameIsGaia ? cs_126 & writeStrobe : _GEN_103;
  reg  [15:0] tmp_21;
  wire        cs_128 = cpuByteAddr > 24'h6FFFFF & cpuByteAddr < 24'h701000;
  wire        _GEN_141 = gameIsGaia ? cs_128 & readStrobe : gameIsEsprade & cs_96 & readStrobe;
  wire        _GEN_142 =
    gameIsGaia ? cs_128 & writeStrobe : gameIsEsprade & cs_96 & writeStrobe;
  wire        cs_129 = cpuByteAddr > 24'h700FFF & cpuByteAddr < 24'h701800;
  wire        _GEN_143 = gameIsGaia ? cs_129 & readStrobe : gameIsEsprade & cs_97 & readStrobe;
  wire        _GEN_144 =
    gameIsGaia ? cs_129 & writeStrobe : gameIsEsprade & cs_97 & writeStrobe;
  reg  [15:0] tmp_22;
  wire        cs_131 = cpuByteAddr > 24'h703FFF & cpuByteAddr < 24'h708000;
  wire        _GEN_145 = gameIsGaia ? cs_131 & readStrobe : _GEN_104;
  wire        _GEN_146 = gameIsGaia ? cs_131 & writeStrobe : _GEN_105;
  reg  [15:0] tmp_23;
  wire        cs_134 = _cpu_io_addr[22] & cpuByteAddr < 24'h800010;
  wire        _GEN_147 = gameIsGaia ? mem_4_wr : _GEN_107;
  assign mem_4_wr = cs_134 & writeStrobe;
  wire        _GEN_148 = cpuByteAddr > 24'h800007 & cpuByteAddr < 24'h800009 & writeStrobe;
  wire        _GEN_149 = gameIsGaia & _GEN_148;
  wire        cs_137 = cpuByteAddr > 24'h8FFFFF & cpuByteAddr < 24'h900006;
  wire        _GEN_150 = gameIsGaia ? cs_137 & writeStrobe : _GEN_111;
  wire        cs_138 = cpuByteAddr > 24'h9FFFFF & cpuByteAddr < 24'hA00006;
  wire        _GEN_151 = gameIsGaia ? cs_138 & writeStrobe : _GEN_112;
  wire        cs_139 = cpuByteAddr > 24'hAFFFFF & cpuByteAddr < 24'hB00006;
  wire        _GEN_152 = gameIsGaia ? cs_139 & writeStrobe : _GEN_113;
  wire        cs_140 = cpuByteAddr > 24'hBFFFFF & cpuByteAddr < 24'hC10000;
  wire        _GEN_153 = gameIsGaia ? cs_140 & readStrobe : _GEN_114;
  wire        _GEN_154 = gameIsGaia ? cs_140 & writeStrobe : _GEN_115;
  wire        cs_146 = cpuByteAddr < 24'h100000;
  wire        _GEN_155 = gameIsGuwange ? cs_146 & readStrobe : _GEN_122;
  wire        cs_147 = (|(_cpu_io_addr[22:20])) & cpuByteAddr < 24'h210000;
  wire        _GEN_156 = gameIsGuwange ? cs_147 & readStrobe : _GEN_123;
  wire        _GEN_157 = gameIsGuwange ? cs_147 & writeStrobe : _GEN_124;
  wire        cs_150 = cpuByteAddr > 24'h2FFFFF & cpuByteAddr < 24'h300010;
  wire        _GEN_158 = gameIsGuwange ? mem_5_wr : _GEN_147;
  assign mem_5_wr = cs_150 & writeStrobe;
  wire        _GEN_159 = cpuByteAddr > 24'h300007 & cpuByteAddr < 24'h300009 & writeStrobe;
  wire        _GEN_160 =
    gameIsGuwange ? _GEN_159 | _GEN_149 | _GEN_109 : _GEN_149 | _GEN_109;
  wire        cs_154 = (|(_cpu_io_addr[22:21])) & cpuByteAddr < 24'h410000;
  wire        _GEN_161 = gameIsGuwange ? cs_154 & readStrobe : _GEN_127;
  wire        _GEN_162 = gameIsGuwange ? cs_154 & writeStrobe : _GEN_128;
  wire        cs_156 = cpuByteAddr > 24'h4FFFFF & cpuByteAddr < 24'h501000;
  wire        _GEN_163 = gameIsGuwange ? cs_156 & readStrobe : _GEN_129;
  wire        _GEN_164 = gameIsGuwange ? cs_156 & writeStrobe : _GEN_130;
  wire        cs_157 = cpuByteAddr > 24'h500FFF & cpuByteAddr < 24'h501800;
  wire        _GEN_165 = gameIsGuwange ? cs_157 & readStrobe : _GEN_131;
  wire        _GEN_166 = gameIsGuwange ? cs_157 & writeStrobe : _GEN_132;
  reg  [15:0] tmp_24;
  wire        cs_159 = cpuByteAddr > 24'h503FFF & cpuByteAddr < 24'h508000;
  wire        _GEN_167 = gameIsGuwange ? cs_159 & readStrobe : _GEN_133;
  wire        _GEN_168 = gameIsGuwange ? cs_159 & writeStrobe : _GEN_134;
  reg  [15:0] tmp_25;
  wire        cs_162 = cpuByteAddr > 24'h5FFFFF & cpuByteAddr < 24'h601000;
  wire        _GEN_169 = gameIsGuwange ? cs_162 & readStrobe : _GEN_135;
  wire        _GEN_170 = gameIsGuwange ? cs_162 & writeStrobe : _GEN_136;
  wire        cs_163 = cpuByteAddr > 24'h600FFF & cpuByteAddr < 24'h601800;
  wire        _GEN_171 = gameIsGuwange ? cs_163 & readStrobe : _GEN_137;
  wire        _GEN_172 = gameIsGuwange ? cs_163 & writeStrobe : _GEN_138;
  reg  [15:0] tmp_26;
  wire        cs_165 = cpuByteAddr > 24'h603FFF & cpuByteAddr < 24'h608000;
  wire        _GEN_173 = gameIsGuwange ? cs_165 & readStrobe : _GEN_139;
  wire        _GEN_174 = gameIsGuwange ? cs_165 & writeStrobe : _GEN_140;
  reg  [15:0] tmp_27;
  wire        cs_168 = cpuByteAddr > 24'h6FFFFF & cpuByteAddr < 24'h701000;
  wire        _GEN_175 = gameIsGuwange ? cs_168 & readStrobe : _GEN_141;
  wire        _GEN_176 = gameIsGuwange ? cs_168 & writeStrobe : _GEN_142;
  wire        cs_169 = cpuByteAddr > 24'h700FFF & cpuByteAddr < 24'h701800;
  wire        _GEN_177 = gameIsGuwange ? cs_169 & readStrobe : _GEN_143;
  wire        _GEN_178 = gameIsGuwange ? cs_169 & writeStrobe : _GEN_144;
  reg  [15:0] tmp_28;
  wire        cs_171 = cpuByteAddr > 24'h703FFF & cpuByteAddr < 24'h708000;
  wire        _GEN_179 = gameIsGuwange ? cs_171 & readStrobe : _GEN_145;
  wire        _GEN_180 = gameIsGuwange ? cs_171 & writeStrobe : _GEN_146;
  reg  [15:0] tmp_29;
  wire        cs_173 = _cpu_io_addr[22] & cpuByteAddr < 24'h800004;
  wire        _GEN_181 = gameIsGuwange ? cs_173 & readStrobe : _GEN_125;
  wire        _GEN_182 = gameIsGuwange ? cs_173 & writeStrobe : _GEN_126;
  wire        cs_174 = cpuByteAddr > 24'h8FFFFF & cpuByteAddr < 24'h900006;
  wire        _GEN_183 = gameIsGuwange ? cs_174 & writeStrobe : _GEN_150;
  wire        cs_175 = cpuByteAddr > 24'h9FFFFF & cpuByteAddr < 24'hA00006;
  wire        _GEN_184 = gameIsGuwange ? cs_175 & writeStrobe : _GEN_151;
  wire        cs_176 = cpuByteAddr > 24'hAFFFFF & cpuByteAddr < 24'hB00006;
  wire        _GEN_185 = gameIsGuwange ? cs_176 & writeStrobe : _GEN_152;
  wire        cs_177 = cpuByteAddr > 24'hBFFFFF & cpuByteAddr < 24'hC10000;
  wire        _GEN_186 = gameIsGuwange ? cs_177 & readStrobe : _GEN_153;
  wire        _GEN_187 = gameIsGuwange ? cs_177 & writeStrobe : _GEN_154;
  wire [14:0] _GEN_188 =
    gameIsGuwange | gameIsGaia | gameIsEsprade | gameIsDoDonPachi ? _cpu_io_addr[14:0] : {4'h0, _GEN_36};
  wire        cs_182 = cpuByteAddr < 24'h100000;
  wire        _GEN_189 = gameIsHotdogStorm ? cs_182 & readStrobe : _GEN_155;
  wire        cs_183 = cpuByteAddr > 24'h2FFFFF & cpuByteAddr < 24'h310000;
  wire        _GEN_190 = gameIsHotdogStorm ? cs_183 & readStrobe : _GEN_156;
  wire        _GEN_191 = gameIsHotdogStorm ? cs_183 & writeStrobe : _GEN_157;
  wire        cs_184 = cpuByteAddr > 24'h407FFF & cpuByteAddr < 24'h409000;
  wire        _GEN_192 = gameIsHotdogStorm ? cs_184 & readStrobe : _GEN_186;
  wire        _GEN_193 = gameIsHotdogStorm ? cs_184 & writeStrobe : _GEN_187;
  wire [14:0] _GEN_194 =
    gameIsHotdogStorm ? {4'h0, _cpu_io_addr[10:0]} : _GEN_188;
  wire        cs_186 = cpuByteAddr > 24'h87FFFF & cpuByteAddr < 24'h881000;
  wire        _GEN_195 = gameIsHotdogStorm ? cs_186 & readStrobe : _GEN_163;
  wire        _GEN_196 = gameIsHotdogStorm ? cs_186 & writeStrobe : _GEN_164;
  wire        cs_187 = cpuByteAddr > 24'h880FFF & cpuByteAddr < 24'h881800;
  wire        _GEN_197 = gameIsHotdogStorm ? cs_187 & readStrobe : _GEN_165;
  wire        _GEN_198 = gameIsHotdogStorm ? cs_187 & writeStrobe : _GEN_166;
  reg  [15:0] tmp_30;
  wire        cs_189 = cpuByteAddr > 24'h883FFF & cpuByteAddr < 24'h888000;
  wire        _GEN_199 = gameIsHotdogStorm ? cs_189 & readStrobe : _GEN_167;
  wire        _GEN_200 = gameIsHotdogStorm ? cs_189 & writeStrobe : _GEN_168;
  reg  [15:0] tmp_31;
  wire        cs_191 = cpuByteAddr > 24'h8FFFFF & cpuByteAddr < 24'h901000;
  wire        vram16x16_1_io_portA_rd =
    gameIsPwrInst2 ? pwrinst2Layer1Vram16Select & readStrobe :
    gameIsHotdogStorm ? cs_191 & readStrobe : _GEN_169;
  wire        vram16x16_1_io_portA_wr =
    gameIsPwrInst2 ? pwrinst2Layer1Vram16Select & writeStrobe :
    gameIsHotdogStorm ? cs_191 & writeStrobe : _GEN_170;
  wire        cs_192 = cpuByteAddr > 24'h900FFF & cpuByteAddr < 24'h901800;
  wire        lineRam_1_io_portA_rd =
    gameIsPwrInst2 ? pwrinst2Layer1LineSelect & readStrobe :
    gameIsHotdogStorm ? cs_192 & readStrobe : _GEN_171;
  wire        lineRam_1_io_portA_wr =
    gameIsPwrInst2 ? pwrinst2Layer1LineSelect & writeStrobe :
    gameIsHotdogStorm ? cs_192 & writeStrobe : _GEN_172;
  reg  [15:0] tmp_32;
  wire        cs_194 = cpuByteAddr > 24'h903FFF & cpuByteAddr < 24'h908000;
  wire        vram8x8_1_io_portA_rd =
    gameIsPwrInst2 ? pwrinst2Layer1Vram8Select & readStrobe
      : gameIsMazinger ? mazingerLayer1Vram8Read
      : gameIsHotdogStorm ? cs_194 & readStrobe : _GEN_173;
  wire        vram8x8_1_io_portA_wr =
    gameIsPwrInst2 ? pwrinst2Layer1Vram8Select & writeStrobe
      : gameIsMazinger ? mazingerLayer1Vram8Write
      : gameIsHotdogStorm ? cs_194 & writeStrobe : _GEN_174;
  reg  [15:0] tmp_33;
  wire        cs_196 = cpuByteAddr > 24'h97FFFF & cpuByteAddr < 24'h981000;
  wire        vram16x16_2_io_portA_rd =
    gameIsPwrInst2 ? 1'b0 :
    gameIsHotdogStorm ? cs_196 & readStrobe : _GEN_175;
  wire        vram16x16_2_io_portA_wr =
    gameIsPwrInst2 ? 1'b0 :
    gameIsHotdogStorm ? cs_196 & writeStrobe : _GEN_176;
  wire        cs_197 = cpuByteAddr > 24'h980FFF & cpuByteAddr < 24'h981800;
  wire        lineRam_2_io_portA_rd =
    gameIsPwrInst2 ? 1'b0 :
    gameIsHotdogStorm ? cs_197 & readStrobe : _GEN_177;
  wire        lineRam_2_io_portA_wr =
    gameIsPwrInst2 ? 1'b0 :
    gameIsHotdogStorm ? cs_197 & writeStrobe : _GEN_178;
  reg  [15:0] tmp_34;
  wire        cs_199 = cpuByteAddr > 24'h983FFF & cpuByteAddr < 24'h988000;
  wire        vram8x8_2_io_portA_rd =
    gameIsPwrInst2 ? pwrinst2Layer3Vram8Select & readStrobe
      : gameIsHotdogStorm ? cs_199 & readStrobe : _GEN_179;
  wire        vram8x8_2_io_portA_wr =
    gameIsPwrInst2 ? pwrinst2Layer3Vram8Select & writeStrobe
      : gameIsHotdogStorm ? cs_199 & writeStrobe : _GEN_180;
  wire [12:0] vram8x8_2_io_portA_addr =
    gameIsPwrInst2 ? _cpu_io_addr[12:0] :
    gameIsHotdogStorm | gameIsGuwange | gameIsGaia | gameIsEsprade
      ? _cpu_io_addr[12:0]
      : _GEN_66;
  reg  [15:0] tmp_35;
  wire        cs_202 = cpuByteAddr > 24'hA7FFFF & cpuByteAddr < 24'hA80010;
  wire        _GEN_201 = gameIsHotdogStorm ? mem_6_wr : _GEN_158;
  assign mem_6_wr = cs_202 & writeStrobe;
  wire        _GEN_202 = cpuByteAddr > 24'hA80007 & cpuByteAddr < 24'hA80009 & writeStrobe;
  wire        _GEN_203 = gameIsHotdogStorm & _GEN_202;
  wire        _GEN_204 = cpuByteAddr > 24'hA8006D & cpuByteAddr < 24'hA8006F & writeStrobe;
  wire        cs_206 = cpuByteAddr > 24'hAFFFFF & cpuByteAddr < 24'hB00006;
  wire        _GEN_205 = gameIsHotdogStorm ? cs_206 & writeStrobe : _GEN_183;
  wire        cs_207 = cpuByteAddr > 24'hB7FFFF & cpuByteAddr < 24'hB80006;
  wire        layerRegs_1_io_mem_wr =
    gameIsPwrInst2 ? pwrinst2Layer1RegsSelect & writeStrobe
      : gameIsMazinger ? mazingerLayer1RegsWrite
      : gameIsHotdogStorm ? cs_207 & writeStrobe : _GEN_184;
  wire        cs_208 = cpuByteAddr > 24'hBFFFFF & cpuByteAddr < 24'hC00006;
  wire        layerRegs_2_io_mem_wr =
    gameIsPwrInst2 ? pwrinst2Layer3RegsSelect & writeStrobe
      : gameIsHotdogStorm ? cs_208 & writeStrobe : _GEN_185;
  wire        cs_213 = cpuByteAddr > 24'hEFFFFF & cpuByteAddr < 24'hF10000;
  wire        _GEN_206 = gameIsHotdogStorm ? cs_213 & readStrobe : _GEN_161;
  wire        _GEN_207 = gameIsHotdogStorm ? cs_213 & writeStrobe : _GEN_162;
  wire        cs_214 = cpuByteAddr < 24'h100000;
  wire        cs_215 = (|(_cpu_io_addr[22:19])) & cpuByteAddr < 24'h110000;
  wire        mainRam_io_rd =
    gameIsPwrInst2 ? pwrinst2MainRamSelect & readStrobe
      : gameIsMazinger ? mazingerMainRamRead
      : gameIsUopoko ? cs_215 & readStrobe : _GEN_190;
  wire        mainRam_io_wr =
    gameIsPwrInst2 ? pwrinst2MainRamSelect & writeStrobe
      : gameIsMazinger ? mazingerMainRamWrite
      : gameIsUopoko ? cs_215 & writeStrobe : _GEN_191;
  wire        cs_216 = cpuByteAddr > 24'h2FFFFF & cpuByteAddr < 24'h300004;
  wire        cs_217 = (|(_cpu_io_addr[22:21])) & cpuByteAddr < 24'h410000;
  wire        spriteRam_io_portA_rd =
    gameIsPwrInst2 ? pwrinst2SpriteListRamSelect & readStrobe
      : gameIsMazinger ? mazingerSpriteRamRead
      : gameIsUopoko ? cs_217 & readStrobe : _GEN_206;
  wire        spriteRam_io_portA_wr =
    gameIsPwrInst2 ? pwrinst2SpriteListRamSelect & writeStrobe
      : gameIsMazinger ? mazingerSpriteRamWrite
      : gameIsUopoko ? cs_217 & writeStrobe : _GEN_207;
  wire        cs_218 = cpuByteAddr > 24'h4FFFFF & cpuByteAddr < 24'h501000;
  wire        vram16x16_0_io_portA_rd = gameIsPwrInst2 ? pwrinst2Layer0Vram16Select & readStrobe : gameIsUopoko ? cs_218 & readStrobe : _GEN_195;
  wire        vram16x16_0_io_portA_wr = gameIsPwrInst2 ? pwrinst2Layer0Vram16Select & writeStrobe : gameIsUopoko ? cs_218 & writeStrobe : _GEN_196;
  wire        cs_219 = cpuByteAddr > 24'h500FFF & cpuByteAddr < 24'h501800;
  wire        lineRam_0_io_portA_rd = gameIsPwrInst2 ? pwrinst2Layer0LineSelect & readStrobe : gameIsUopoko ? cs_219 & readStrobe : _GEN_197;
  wire        lineRam_0_io_portA_wr = gameIsPwrInst2 ? pwrinst2Layer0LineSelect & writeStrobe : gameIsUopoko ? cs_219 & writeStrobe : _GEN_198;
  reg  [15:0] tmp_36;
  wire        cs_221 = cpuByteAddr > 24'h503FFF & cpuByteAddr < 24'h508000;
  wire        vram8x8_0_io_portA_rd =
    gameIsPwrInst2 ? pwrinst2Layer0Vram8Select & readStrobe
      : gameIsMazinger ? mazingerLayer0Vram8Read
      : gameIsUopoko ? cs_221 & readStrobe : _GEN_199;
  wire        vram8x8_0_io_portA_wr =
    gameIsPwrInst2 ? pwrinst2Layer0Vram8Select & writeStrobe
      : gameIsMazinger ? mazingerLayer0Vram8Write
      : gameIsUopoko ? cs_221 & writeStrobe : _GEN_200;
  reg  [15:0] tmp_37;
  wire        cs_224 = cpuByteAddr > 24'h5FFFFF & cpuByteAddr < 24'h600010;
  wire        spriteRegs_io_mem_wr =
    gameIsPwrInst2 ? pwrinst2SpriteRegsWrite
      : gameIsMazinger ? mazingerSpriteRegsWrite
      : gameIsUopoko ? mem_7_wr : _GEN_201;
  wire [2:0]  spriteRegs_io_mem_addr =
    gameIsUopoko | gameIsHotdogStorm | gameIsGuwange | gameIsGaia | gameIsEsprade | gameIsDoDonPachi
    | gameIsDonPachi
      ? _cpu_io_addr[2:0]
      : _cpu_io_addr[2:0];
  assign mem_7_wr = cs_224 & writeStrobe;
  wire        _GEN_209 = cpuByteAddr > 24'h600007 & cpuByteAddr < 24'h600009 & writeStrobe;
  wire        cs_227 = cpuByteAddr > 24'h6FFFFF & cpuByteAddr < 24'h700006;
  wire        layerRegs_0_io_mem_wr =
    gameIsPwrInst2 ? pwrinst2Layer0RegsSelect & writeStrobe
      : gameIsMazinger ? mazingerLayer0RegsWrite
      : gameIsUopoko ? cs_227 & writeStrobe : _GEN_205;
  wire        cs_228 = _cpu_io_addr[22] & cpuByteAddr < 24'h810000;
  wire        paletteRam_io_portA_rd =
    gameIsPwrInst2 ? pwrinst2PaletteSelect & readStrobe
      : gameIsMazinger ? mazingerPaletteRead
      : gameIsUopoko ? cs_228 & readStrobe : _GEN_192;
  wire        paletteRam_io_portA_wr =
    gameIsPwrInst2 ? pwrinst2PaletteSelect & writeStrobe
      : gameIsMazinger ? mazingerPaletteWrite
      : gameIsUopoko ? cs_228 & writeStrobe : _GEN_193;
  wire [14:0] paletteRam_io_portA_addr =
    gameIsPwrInst2 ? _cpu_io_addr[14:0]
      : gameIsMazinger ? mazingerPaletteRamAddr
      : gameIsUopoko ? _cpu_io_addr[14:0] : _GEN_194;
  wire        _GEN_210 = videoVBlankRising | videoIrq;
  wire        _GEN_218 = cs_1 & readStrobe;
  wire [15:0] _GEN_219 = _GEN_218 ? 16'h0 : dinReg;
  wire [15:0] _GEN_220 = _GEN_0 ? io_progRom_dout : _GEN_219;
  wire [15:0] _GEN_221 = cs_3 ? _mainRam_io_dout : _GEN_220;
  wire [15:0] _GEN_222 = cs_4 ? io_soundCtrl_ymz_dout : _GEN_221;
  wire        _GEN_223 = cs_8 & readStrobe;
  wire [15:0] _GEN_224 = cs_5 ? _spriteRam_io_portA_dout : _GEN_222;
  wire [15:0] _GEN_225 = cs_6 ? _vram16x16_0_io_portA_dout : _GEN_224;
  wire [15:0] _GEN_226 = cs_7 ? _lineRam_0_io_portA_dout : _GEN_225;
  wire [15:0] _GEN_227 = _GEN_223 ? tmp : _GEN_226;
  wire        _GEN_228 = cs_10 & readStrobe;
  wire        _GEN_229 = cs_13 & readStrobe;
  wire [15:0] _GEN_230 = cs_9 ? _vram8x8_0_io_portA_dout : _GEN_227;
  wire [15:0] _GEN_231 = _GEN_228 ? tmp_1 : _GEN_230;
  wire [15:0] _GEN_232 = cs_11 ? _vram16x16_1_io_portA_dout : _GEN_231;
  wire [15:0] _GEN_233 = cs_12 ? _lineRam_1_io_portA_dout : _GEN_232;
  wire [15:0] _GEN_234 = _GEN_229 ? tmp_2 : _GEN_233;
  wire [15:0] _GEN_235 = cs_14 ? _vram8x8_1_io_portA_dout : _GEN_234;
  wire        _GEN_236 = cs_15 & readStrobe;
  wire        _GEN_237 = cs_17 & readStrobe;
  wire [15:0] _GEN_238 = _GEN_236 ? tmp_3 : _GEN_235;
  wire [15:0] _GEN_239 = cs_16 ? _paletteRam_io_portA_dout : _GEN_238;
  wire [15:0] _GEN_240 = _GEN_237 ? 16'h0 : _GEN_239;
  wire [23:0] _GEN_241 = {_cpu_io_addr, 1'h0};
  wire [23:0] _offset_T_17 = 24'(_GEN_241 - 24'h800000);
  wire        _GEN_242 = gameIsDFeveron & _GEN_1 & _offset_T_17 == 24'h4;
  wire        _GEN_243 = gameIsDFeveron & _GEN_1 & _offset_T_17 == 24'h6;
  wire [15:0] _GEN_244 =
    {13'h0, ~(_offset_T_17 == 24'h0 & agalletIrq), ~unknownIrq, ~videoIrq};
  wire [15:0] _GEN_245 = _GEN_1 ? _GEN_244 : _GEN_240;
  wire        _GEN_246 = cs_21 & readStrobe;
  wire [15:0] _GEN_247 = _GEN_246 ? 16'h0 : _GEN_245;
  wire [15:0] _GEN_248 = cs_22 ? _layerRegs_0_io_mem_dout : _GEN_247;
  wire [15:0] _GEN_249 = cs_23 ? _layerRegs_1_io_mem_dout : _GEN_248;
  wire [15:0] _GEN_250 = _GEN_4 ? inputPort0 : _GEN_249;
  wire [15:0] _GEN_251 = _GEN_5 ? inputPort1 : _GEN_250;
  wire [15:0] _GEN_252 = gameIsDFeveron ? _GEN_251 : _GEN_219;
  wire        _GEN_253 =
    gameIsDFeveron ? _GEN_7 | _cpu_io_as & (_GEN_6 | dtackReg) : cs_1 | _cpu_io_as & dtackReg;
  wire [15:0] _GEN_254 = _GEN_11 ? io_progRom_dout : _GEN_252;
  wire [15:0] _GEN_255 = cs_28 ? _mainRam_io_dout : _GEN_254;
  wire [15:0] _GEN_256 = cs_29 ? _vram16x16_1_io_portA_dout : _GEN_255;
  wire [15:0] _GEN_257 = cs_30 ? _lineRam_1_io_portA_dout : _GEN_256;
  wire        _GEN_258 = cs_31 & readStrobe;
  wire        _GEN_259 = cs_33 & readStrobe;
  wire [15:0] _GEN_260 = _GEN_258 ? tmp_4 : _GEN_257;
  wire [15:0] _GEN_261 = cs_32 ? _vram8x8_1_io_portA_dout : _GEN_260;
  wire [15:0] _GEN_262 = _GEN_259 ? tmp_5 : _GEN_261;
  wire [15:0] _GEN_263 = cs_34 ? _vram16x16_0_io_portA_dout : _GEN_262;
  wire        _GEN_264 = cs_36 & readStrobe;
  wire        _GEN_265 = cs_38 & readStrobe;
  wire [15:0] _GEN_266 = cs_35 ? _lineRam_0_io_portA_dout : _GEN_263;
  wire [15:0] _GEN_267 = _GEN_264 ? tmp_6 : _GEN_266;
  wire [15:0] _GEN_268 = cs_37 ? _vram8x8_0_io_portA_dout : _GEN_267;
  wire [15:0] _GEN_269 = _GEN_265 ? tmp_7 : _GEN_268;
  wire [15:0] _GEN_270 = cs_39 ? _vram8x8_2_io_portA_dout : _GEN_269;
  wire [15:0] _GEN_271 = cs_40 ? _spriteRam_io_portA_dout : _GEN_270;
  wire [15:0] _GEN_272 = cs_41 ? _layerRegs_1_io_mem_dout : _GEN_271;
  wire [15:0] _GEN_273 = cs_42 ? _layerRegs_0_io_mem_dout : _GEN_272;
  wire [15:0] _GEN_274 = cs_43 ? _layerRegs_2_io_mem_dout : _GEN_273;
  wire [23:0] _offset_T_43 = 24'(_GEN_241 + 24'h700000);
  wire        _GEN_275 = gameIsDonPachi & _GEN_30;
  wire        _GEN_276 =
    _GEN_275 ? ~(_offset_T_43 == 24'h4 | _GEN_242) & _GEN_210 : ~_GEN_242 & _GEN_210;
  wire        _GEN_277 =
    _GEN_275 ? ~(_offset_T_43 == 24'h6 | _GEN_243) & unknownIrq : ~_GEN_243 & unknownIrq;
  wire [15:0] _GEN_278 =
    {13'h0, ~(_offset_T_43 == 24'h0 & agalletIrq), ~unknownIrq, ~videoIrq};
  wire [15:0] _GEN_279 = _GEN_30 ? _GEN_278 : _GEN_274;
  wire        _GEN_280 = cs_47 & readStrobe;
  wire [15:0] _GEN_281 = _GEN_280 ? 16'h0 : _GEN_279;
  wire [15:0] _GEN_282 = cs_48 ? _paletteRam_io_portA_dout : _GEN_281;
  wire [15:0] _GEN_283 = cs_49 ? io_soundCtrl_oki_0_dout : _GEN_282;
  wire [15:0] _GEN_284 = cs_50 ? io_soundCtrl_oki_1_dout : _GEN_283;
  wire [15:0] _GEN_285 = _GEN_37 ? inputPort0 : _GEN_284;
  wire [15:0] _GEN_286 = _GEN_38 ? inputPort1 : _GEN_285;
  wire [15:0] _GEN_287 = gameIsDonPachi ? _GEN_286 : _GEN_252;
  wire        _GEN_288 = gameIsDonPachi ? _GEN_40 | _cpu_io_as & (_GEN_39 | _GEN_8) : _GEN_253;
  wire [15:0] _GEN_289 = _GEN_45 ? io_progRom_dout : _GEN_287;
  wire [15:0] _GEN_290 = cs_56 ? _mainRam_io_dout : _GEN_289;
  wire [15:0] _GEN_291 = cs_57 ? io_soundCtrl_ymz_dout : _GEN_290;
  wire [15:0] _GEN_292 = cs_58 ? _spriteRam_io_portA_dout : _GEN_291;
  wire [15:0] _GEN_293 = cs_59 ? _vram16x16_0_io_portA_dout : _GEN_292;
  wire [15:0] _GEN_294 = cs_60 ? _lineRam_0_io_portA_dout : _GEN_293;
  wire        _GEN_295 = cs_61 & readStrobe;
  wire        _GEN_296 = cs_63 & readStrobe;
  wire        _GEN_297 = cs_67 & readStrobe;
  wire        _GEN_298 = cs_69 & readStrobe;
  wire [15:0] _GEN_299 = _GEN_295 ? tmp_8 : _GEN_294;
  wire [15:0] _GEN_300 = cs_62 ? _vram8x8_0_io_portA_dout : _GEN_299;
  wire [15:0] _GEN_301 = _GEN_296 ? tmp_9 : _GEN_300;
  wire [15:0] _GEN_302 = cs_65 ? _vram16x16_1_io_portA_dout : _GEN_301;
  wire [15:0] _GEN_303 = cs_66 ? _lineRam_1_io_portA_dout : _GEN_302;
  wire [15:0] _GEN_304 = _GEN_297 ? tmp_10 : _GEN_303;
  wire [15:0] _GEN_305 = cs_68 ? _vram8x8_1_io_portA_dout : _GEN_304;
  wire [15:0] _GEN_306 = _GEN_298 ? tmp_11 : _GEN_305;
  wire [15:0] _GEN_307 = cs_70 ? _vram8x8_2_io_portA_dout : _GEN_306;
  wire [23:0] _offset_T_70 = 24'(_GEN_241 - 24'h800000);
  wire        _GEN_308 = gameIsDoDonPachi & _GEN_67 & _offset_T_70 == 24'h4;
  wire        _GEN_309 = gameIsDoDonPachi & _GEN_67 & _offset_T_70 == 24'h6;
  wire [15:0] _GEN_310 =
    {13'h0, ~(_offset_T_70 == 24'h0 & agalletIrq), ~unknownIrq, ~videoIrq};
  wire [15:0] _GEN_311 = _GEN_67 ? _GEN_310 : _GEN_307;
  wire        _GEN_312 = cs_74 & readStrobe;
  wire [15:0] _GEN_313 = _GEN_312 ? 16'h0 : _GEN_311;
  wire [15:0] _GEN_314 = cs_75 ? _layerRegs_0_io_mem_dout : _GEN_313;
  wire [15:0] _GEN_315 = cs_76 ? _layerRegs_1_io_mem_dout : _GEN_314;
  wire [15:0] _GEN_316 = cs_77 ? _layerRegs_2_io_mem_dout : _GEN_315;
  wire [15:0] _GEN_317 = cs_78 ? _paletteRam_io_portA_dout : _GEN_316;
  wire [15:0] _GEN_318 = _GEN_76 ? inputPort0 : _GEN_317;
  wire [15:0] _GEN_319 = _GEN_77 ? inputPort1 : _GEN_318;
  wire [15:0] _GEN_320 = gameIsDoDonPachi ? _GEN_319 : _GEN_287;
  wire        _GEN_321 = gameIsDoDonPachi ? _GEN_79 | _cpu_io_as & (_GEN_78 | _GEN_42) : _GEN_288;
  wire [15:0] _GEN_322 = _GEN_85 ? io_progRom_dout : _GEN_320;
  wire [15:0] _GEN_323 = cs_83 ? _mainRam_io_dout : _GEN_322;
  wire [15:0] _GEN_324 = cs_84 ? io_soundCtrl_ymz_dout : _GEN_323;
  wire [15:0] _GEN_325 = cs_85 ? _spriteRam_io_portA_dout : _GEN_324;
  wire [15:0] _GEN_326 = cs_86 ? _vram16x16_0_io_portA_dout : _GEN_325;
  wire        _GEN_327 = cs_88 & readStrobe;
  wire        _GEN_328 = cs_90 & readStrobe;
  wire [15:0] _GEN_329 = cs_87 ? _lineRam_0_io_portA_dout : _GEN_326;
  wire [15:0] _GEN_330 = _GEN_327 ? tmp_12 : _GEN_329;
  wire [15:0] _GEN_331 = cs_89 ? _vram8x8_0_io_portA_dout : _GEN_330;
  wire [15:0] _GEN_332 = _GEN_328 ? tmp_13 : _GEN_331;
  wire        _GEN_333 = cs_93 & readStrobe;
  wire        _GEN_334 = cs_95 & readStrobe;
  wire        _GEN_335 = cs_98 & readStrobe;
  wire        _GEN_336 = cs_100 & readStrobe;
  wire [15:0] _GEN_337 = cs_91 ? _vram16x16_1_io_portA_dout : _GEN_332;
  wire [15:0] _GEN_338 = cs_92 ? _lineRam_1_io_portA_dout : _GEN_337;
  wire [15:0] _GEN_339 = _GEN_333 ? tmp_14 : _GEN_338;
  wire [15:0] _GEN_340 = cs_94 ? _vram8x8_1_io_portA_dout : _GEN_339;
  wire [15:0] _GEN_341 = _GEN_334 ? tmp_15 : _GEN_340;
  wire [15:0] _GEN_342 = cs_96 ? _vram16x16_2_io_portA_dout : _GEN_341;
  wire [15:0] _GEN_343 = cs_97 ? _lineRam_2_io_portA_dout : _GEN_342;
  wire [15:0] _GEN_344 = _GEN_335 ? tmp_16 : _GEN_343;
  wire [15:0] _GEN_345 = cs_99 ? _vram8x8_2_io_portA_dout : _GEN_344;
  wire [15:0] _GEN_346 = _GEN_336 ? tmp_17 : _GEN_345;
  wire [23:0] _offset_T_100 = 24'(_GEN_241 - 24'h800000);
  wire        _GEN_347 = gameIsEsprade & _GEN_106;
  wire        _GEN_348 =
    _GEN_347 ? ~(_offset_T_100 == 24'h4 | _GEN_308) & _GEN_276 : ~_GEN_308 & _GEN_276;
  wire        _GEN_349 =
    _GEN_347 ? ~(_offset_T_100 == 24'h6 | _GEN_309) & _GEN_277 : ~_GEN_309 & _GEN_277;
  wire [15:0] _GEN_350 =
    {13'h0, ~(_offset_T_100 == 24'h0 & agalletIrq), ~unknownIrq, ~videoIrq};
  wire        _GEN_351 = _GEN_110 | cs_104 & readStrobe;
  wire [15:0] _GEN_352 = _GEN_106 ? _GEN_350 : _GEN_346;
  wire [15:0] _GEN_353 = _GEN_351 ? 16'h0 : _GEN_352;
  wire [15:0] _GEN_354 = cs_106 ? _layerRegs_0_io_mem_dout : _GEN_353;
  wire [15:0] _GEN_355 = cs_107 ? _layerRegs_1_io_mem_dout : _GEN_354;
  wire [15:0] _GEN_356 = cs_108 ? _layerRegs_2_io_mem_dout : _GEN_355;
  wire [15:0] _GEN_357 = cs_109 ? _paletteRam_io_portA_dout : _GEN_356;
  wire        _GEN_358 = gameIsEsprade & _GEN_120 | _GEN_82;
  wire        _GEN_359 = cs_113 & _cpu_io_rw & io_progRom_valid;
  wire [15:0] _GEN_360 = _GEN_116 ? inputPort0 : _GEN_357;
  wire [15:0] _GEN_361 = _GEN_117 ? inputPort1 : _GEN_360;
  wire [15:0] _GEN_362 = gameIsEsprade ? _GEN_361 : _GEN_320;
  wire [15:0] _GEN_363 = _GEN_359 ? io_progRom_dout : _GEN_362;
  wire [15:0] _GEN_364 = cs_115 ? _mainRam_io_dout : _GEN_363;
  wire [15:0] _GEN_365 = cs_116 ? io_soundCtrl_ymz_dout : _GEN_364;
  wire [15:0] _GEN_366 = cs_117 ? _spriteRam_io_portA_dout : _GEN_365;
  wire [15:0] _GEN_367 = cs_118 ? _vram16x16_0_io_portA_dout : _GEN_366;
  wire [15:0] _GEN_368 = cs_119 ? _lineRam_0_io_portA_dout : _GEN_367;
  wire [23:0] _offset_T_132 = 24'(_GEN_241 - 24'h800000);
  wire        _GEN_369 = _cpu_io_addr[22] & cpuByteAddr < 24'h800008 & readStrobe;
  wire        _GEN_370 = gameIsGaia & _GEN_369 & _offset_T_132 == 24'h4;
  wire        _GEN_371 = gameIsGaia & _GEN_369 & _offset_T_132 == 24'h6;
  wire [15:0] _GEN_372 =
    {13'h0, ~(_offset_T_132 == 24'h0 & agalletIrq), ~unknownIrq, ~videoIrq};
  wire        cs_136 = cpuByteAddr > 24'h800009 & cpuByteAddr < 24'h800080;
  wire        _GEN_373 = cs_136 & readStrobe;
  wire        _GEN_374 = cpuByteAddr > 24'hD0000F & cpuByteAddr < 24'hD00011 & readStrobe;
  wire        _GEN_375 = cpuByteAddr > 24'hD00011 & cpuByteAddr < 24'hD00013 & readStrobe;
  wire        _GEN_376 = cpuByteAddr > 24'hD00013 & cpuByteAddr < 24'hD00015 & readStrobe;
  wire        _GEN_377 = cpuByteAddr > 24'hD00013 & cpuByteAddr < 24'hD00015 & writeStrobe;
  wire        _GEN_378 = gameIsDonPachi ? _GEN_41 | _GEN_8 : _GEN_253;
  wire        _GEN_379 = gameIsDoDonPachi ? _GEN_81 : _GEN_378;
  wire        _GEN_380 = gameIsEsprade ? _GEN_121 : _GEN_379;
  wire        _GEN_381 = cs_146 & _cpu_io_rw & io_progRom_valid;
  wire        _GEN_382 = cpuByteAddr > 24'h20FFFF & cpuByteAddr < 24'h300000 & readStrobe;
  wire [23:0] _offset_T_148 = 24'(_GEN_241 - 24'h300000);
  wire        _GEN_383 = cpuByteAddr > 24'h2FFFFF & cpuByteAddr < 24'h300008 & readStrobe;
  wire        _GEN_384 = gameIsGuwange & _GEN_383;
  wire        _GEN_385 =
    _GEN_384 ? ~(_offset_T_148 == 24'h4 | _GEN_370) & _GEN_348 : ~_GEN_370 & _GEN_348;
  wire        _GEN_386 =
    _GEN_384 ? ~(_offset_T_148 == 24'h6 | _GEN_371) & _GEN_349 : ~_GEN_371 & _GEN_349;
  wire [15:0] _GEN_387 =
    {13'h0, ~(_offset_T_148 == 24'h0 & agalletIrq), ~unknownIrq, ~videoIrq};
  wire        cs_152 = cpuByteAddr > 24'h300009 & cpuByteAddr < 24'h300080;
  wire        _GEN_388 = cpuByteAddr > 24'h30007F & cpuByteAddr < 24'h400000 & readStrobe;
  wire        _GEN_389 = _GEN_388 | cs_152 & readStrobe;
  wire        _GEN_390 = cpuByteAddr > 24'h40FFFF & cpuByteAddr < 24'h500000 & readStrobe;
  wire [15:0] _GEN_391 = _GEN_116 ? inputP1DefaultOrGuwange : _GEN_357;
  wire [15:0] _GEN_392 = _GEN_117 ? inputP2DefaultOrGuwange : _GEN_391;
  wire [15:0] _GEN_393 = _GEN_76 ? inputP1DefaultOrGuwange : _GEN_317;
  wire [15:0] _GEN_394 = _GEN_77 ? inputP2DefaultOrGuwange : _GEN_393;
  wire [15:0] _GEN_395 = _GEN_37 ? inputP1DefaultOrGuwange : _GEN_284;
  wire [15:0] _GEN_396 = _GEN_38 ? inputP2DefaultOrGuwange : _GEN_395;
  wire [15:0] _GEN_397 = _GEN_5 ? inputP2DefaultOrGuwange : _GEN_250;
  wire [15:0] _GEN_398 = gameIsDFeveron ? _GEN_397 : _GEN_219;
  wire [15:0] _GEN_399 = gameIsDonPachi ? _GEN_396 : _GEN_398;
  wire [15:0] _GEN_400 = gameIsDoDonPachi ? _GEN_394 : _GEN_399;
  wire [15:0] _GEN_401 = gameIsEsprade ? _GEN_392 : _GEN_400;
  wire        _GEN_402 = cpuByteAddr > 24'h507FFF & cpuByteAddr < 24'h600000 & readStrobe;
  wire        _GEN_403 = cpuByteAddr > 24'h607FFF & cpuByteAddr < 24'h700000 & readStrobe;
  wire        cs_178 = cpuByteAddr > 24'hD0000F & cpuByteAddr < 24'hD00015;
  wire        _GEN_404 = cs_178 & readStrobe;
  wire        cs_179 = cpuByteAddr > 24'hD0000F & cpuByteAddr < 24'hD00011;
  wire        _GEN_405 = cpuByteAddr > 24'hD0000F & cpuByteAddr < 24'hD00011 & readStrobe;
  wire        _GEN_406 = cpuByteAddr > 24'hD00011 & cpuByteAddr < 24'hD00013 & readStrobe;
  wire        _GEN_407 = gameIsDoDonPachi ? _GEN_81 : _GEN_288;
  wire        _GEN_408 = gameIsEsprade ? _GEN_121 : _GEN_407;
  wire        _GEN_409 = cs_182 & _cpu_io_rw & io_progRom_valid;
  wire        _GEN_410 = cpuByteAddr > 24'h5FFFFF & cpuByteAddr < 24'h600001 & readStrobe;
  wire [23:0] _offset_T_200 = 24'(_GEN_241 + 24'h580000);
  wire        _GEN_411 = cpuByteAddr > 24'hA7FFFF & cpuByteAddr < 24'hA80008 & readStrobe;
  wire        _GEN_412 = gameIsHotdogStorm & _GEN_411 & _offset_T_200 == 24'h4;
  wire        _GEN_413 = gameIsHotdogStorm & _GEN_411 & _offset_T_200 == 24'h6;
  wire [15:0] _GEN_414 =
    {13'h0, ~(_offset_T_200 == 24'h0 & agalletIrq), ~unknownIrq, ~videoIrq};
  wire        cs_204 = cpuByteAddr > 24'hA80009 & cpuByteAddr < 24'hA80080;
  wire        _GEN_415 = cs_204 & readStrobe;
  wire        _GEN_416 = cpuByteAddr > 24'hC7FFFF & cpuByteAddr < 24'hC80001 & readStrobe;
  wire        _GEN_417 = cpuByteAddr > 24'hC80001 & cpuByteAddr < 24'hC80003 & readStrobe;
  wire        cs_211 = cpuByteAddr > 24'hCFFFFF & cpuByteAddr < 24'hD00001;
  wire        cs_212 = cpuByteAddr > 24'hD00001 & cpuByteAddr < 24'hD00003;
  wire        _GEN_418 = cs_212 & readStrobe;
  wire        _GEN_419 = cs_214 & _cpu_io_rw & io_progRom_valid;
  wire [23:0] _offset_T_222 = 24'(_GEN_241 - 24'h600000);
  wire        _GEN_420 = cpuByteAddr > 24'h5FFFFF & cpuByteAddr < 24'h600008 & readStrobe;
  wire        _GEN_421 = gameIsUopoko & _GEN_420;
  wire        cs_226 = cpuByteAddr > 24'h600009 & cpuByteAddr < 24'h600080;
  wire        _GEN_422 = cpuByteAddr > 24'h8FFFFF & cpuByteAddr < 24'h900001 & readStrobe;
  wire        _GEN_423 = cpuByteAddr > 24'h900001 & cpuByteAddr < 24'h900003 & readStrobe;
  wire        cs_231 = cpuByteAddr > 24'h9FFFFF & cpuByteAddr < 24'hA00001;
  wire        _GEN_424 = gameIsEsprade ? _GEN_121 : _GEN_321;
  wire        _GEN_425 =
    gameIsEsprade ? _GEN_119 | _cpu_io_as & (_GEN_118 | _GEN_82) : _GEN_321;
  wire        _GEN_426 = gameIsDonPachi ? cs_54 & writeStrobe : gameIsDFeveron & cs_26 & writeStrobe;
  wire        _GEN_427 = gameIsDoDonPachi ? cs_81 & writeStrobe : _GEN_426;
  wire        _GEN_428 = gameIsEsprade ? cs_112 & writeStrobe : _GEN_427;
  wire        _GEN_429 = gameIsGuwange ? cs_179 & writeStrobe : _GEN_428;
  wire        _GEN_430 = gameIsHotdogStorm ? cs_211 & writeStrobe : _GEN_429;
  wire        eepromMem_wr =
    gameIsPwrInst2 ? pwrinst2EepromWrite
      : gameIsMazinger ? mazingerEepromWrite
      : gameIsUopoko ? cs_231 & writeStrobe : _GEN_430;
  wire        cs_120 = cpuByteAddr > 24'h5017FF & cpuByteAddr < 24'h504000;
  wire        cs_122 = cpuByteAddr > 24'h507FFF & cpuByteAddr < 24'h510000;
  wire        cs_125 = cpuByteAddr > 24'h6017FF & cpuByteAddr < 24'h604000;
  wire        cs_127 = cpuByteAddr > 24'h607FFF & cpuByteAddr < 24'h610000;
  wire        cs_130 = cpuByteAddr > 24'h7017FF & cpuByteAddr < 24'h704000;
  wire        cs_132 = cpuByteAddr > 24'h707FFF & cpuByteAddr < 24'h710000;
  wire        cs_158 = cpuByteAddr > 24'h5017FF & cpuByteAddr < 24'h504000;
  wire        cs_160 = cpuByteAddr > 24'h507FFF & cpuByteAddr < 24'h510000;
  wire        cs_164 = cpuByteAddr > 24'h6017FF & cpuByteAddr < 24'h604000;
  wire        cs_166 = cpuByteAddr > 24'h607FFF & cpuByteAddr < 24'h610000;
  wire        cs_170 = cpuByteAddr > 24'h7017FF & cpuByteAddr < 24'h704000;
  wire        cs_172 = cpuByteAddr > 24'h707FFF & cpuByteAddr < 24'h710000;
  wire        cs_188 = cpuByteAddr > 24'h8817FF & cpuByteAddr < 24'h884000;
  wire        cs_190 = cpuByteAddr > 24'h887FFF & cpuByteAddr < 24'h890000;
  wire        cs_193 = cpuByteAddr > 24'h9017FF & cpuByteAddr < 24'h904000;
  wire        cs_195 = cpuByteAddr > 24'h907FFF & cpuByteAddr < 24'h910000;
  wire        cs_198 = cpuByteAddr > 24'h9817FF & cpuByteAddr < 24'h984000;
  wire        cs_200 = cpuByteAddr > 24'h987FFF & cpuByteAddr < 24'h990000;
  wire        cs_220 = cpuByteAddr > 24'h5017FF & cpuByteAddr < 24'h504000;
  wire        cs_222 = cpuByteAddr > 24'h507FFF & cpuByteAddr < 24'h510000;
  wire        _GEN_431 = cs_120 & readStrobe;
  wire        _GEN_432 = cs_122 & readStrobe;
  wire [15:0] _GEN_433 = _GEN_431 ? tmp_18 : _GEN_368;
  wire [15:0] _GEN_434 = cs_121 ? _vram8x8_0_io_portA_dout : _GEN_433;
  wire [15:0] _GEN_435 = _GEN_432 ? tmp_19 : _GEN_434;
  wire        _GEN_436 = cs_125 & readStrobe;
  wire        _GEN_437 = cs_127 & readStrobe;
  wire        _GEN_438 = cs_130 & readStrobe;
  wire        _GEN_439 = cs_132 & readStrobe;
  wire [15:0] _GEN_440 = cs_123 ? _vram16x16_1_io_portA_dout : _GEN_435;
  wire [15:0] _GEN_441 = cs_124 ? _lineRam_1_io_portA_dout : _GEN_440;
  wire [15:0] _GEN_442 = _GEN_436 ? tmp_20 : _GEN_441;
  wire [15:0] _GEN_443 = cs_126 ? _vram8x8_1_io_portA_dout : _GEN_442;
  wire [15:0] _GEN_444 = _GEN_437 ? tmp_21 : _GEN_443;
  wire [15:0] _GEN_445 = cs_128 ? _vram16x16_2_io_portA_dout : _GEN_444;
  wire [15:0] _GEN_446 = cs_129 ? _lineRam_2_io_portA_dout : _GEN_445;
  wire [15:0] _GEN_447 = _GEN_438 ? tmp_22 : _GEN_446;
  wire [15:0] _GEN_448 = cs_131 ? _vram8x8_2_io_portA_dout : _GEN_447;
  wire [15:0] _GEN_449 = _GEN_439 ? tmp_23 : _GEN_448;
  wire [15:0] _GEN_450 = _GEN_369 ? _GEN_372 : _GEN_449;
  wire [15:0] _GEN_451 = _GEN_373 ? 16'h0 : _GEN_450;
  wire [15:0] _GEN_452 = cs_137 ? _layerRegs_0_io_mem_dout : _GEN_451;
  wire [15:0] _GEN_453 = cs_138 ? _layerRegs_1_io_mem_dout : _GEN_452;
  wire        _GEN_454 =
    _GEN_376 | _GEN_375 | cpuByteAddr > 24'hD0000F & cpuByteAddr < 24'hD00011 & writeStrobe | _GEN_374
    | cs_140 | cs_139 | cs_138 | cs_137 | cs_136 | _GEN_148 | cs_134 & ~_cpu_io_rw
    | _GEN_369 | cs_132 | cs_131 | cs_130 | cs_129 | cs_128 | cs_127 | cs_126 | cs_125
    | cs_124 | cs_123 | cs_122 | cs_121 | cs_120 | cs_119 | cs_118 | cs_117 | cs_116
    | cs_115 | cpuByteAddr > 24'h57D & cpuByteAddr < 24'h582 & writeStrobe | _GEN_359;
  wire        _GEN_455 = _GEN_377 | _GEN_454 | _GEN_358;
  wire        _GEN_456 = gameIsGaia ? _GEN_455 : _GEN_380;
  wire [15:0] _GEN_457 = cs_139 ? _layerRegs_2_io_mem_dout : _GEN_453;
  wire [15:0] _GEN_458 = cs_140 ? _paletteRam_io_portA_dout : _GEN_457;
  wire [15:0] _GEN_459 = _GEN_374 ? inputPlayersWideOrGuwangeP1 : _GEN_458;
  wire [15:0] _GEN_460 = _GEN_375 ? inputSharedSystem : _GEN_459;
  wire [15:0] _GEN_461 = _GEN_376 ? io_dips_0 : _GEN_460;
  wire [15:0] _GEN_462 = gameIsGaia ? _GEN_461 : _GEN_401;
  wire [15:0] _GEN_463 = _GEN_381 ? io_progRom_dout : _GEN_462;
  wire [15:0] _GEN_464 = cs_147 ? _mainRam_io_dout : _GEN_463;
  wire [15:0] _GEN_465 = _GEN_382 ? 16'h0 : _GEN_464;
  wire [15:0] _GEN_466 = _GEN_383 ? _GEN_387 : _GEN_465;
  wire [15:0] _GEN_467 = _GEN_389 ? 16'h0 : _GEN_466;
  wire [15:0] _GEN_468 = cs_154 ? _spriteRam_io_portA_dout : _GEN_467;
  wire [15:0] _GEN_469 = _GEN_390 ? 16'h0 : _GEN_468;
  wire        _GEN_470 = cs_158 & readStrobe;
  wire        _GEN_471 = cs_160 & readStrobe;
  wire        _GEN_472 = cs_164 & readStrobe;
  wire        _GEN_473 = cs_166 & readStrobe;
  wire        _GEN_474 = cs_170 & readStrobe;
  wire        _GEN_475 = cs_172 & readStrobe;
  wire        _GEN_476 =
    _GEN_405 | cs_179 & ~_cpu_io_rw | cs_178 | cs_177 | cs_176 | cs_175 | cs_174 | cs_173
    | cs_172 | cs_171 | cs_170 | cs_169 | cs_168 | _GEN_403 | cs_166 | cs_165 | cs_164
    | cs_163 | cs_162 | _GEN_402 | cs_160 | cs_159 | cs_158 | cs_157 | cs_156 | _GEN_390
    | cs_154 | _GEN_388 | cs_152 | _GEN_159 | cs_150 & ~_cpu_io_rw | _GEN_383 | _GEN_382
    | cs_147 | _GEN_381;
  wire        _GEN_477 = _GEN_406 | _GEN_476 | _GEN_456;
  wire        _GEN_478 = gameIsGaia ? _GEN_455 : _GEN_408;
  wire        _GEN_479 = gameIsGuwange ? _GEN_477 : _GEN_478;
  wire        _GEN_480 = cs_188 & readStrobe;
  wire        _GEN_481 = cs_190 & readStrobe;
  wire        _GEN_482 = cs_193 & readStrobe;
  wire        _GEN_483 = cs_195 & readStrobe;
  wire        _GEN_484 = cs_198 & readStrobe;
  wire        _GEN_485 = cs_200 & readStrobe;
  wire        _GEN_486 =
    cs_212 | cs_211 & ~_cpu_io_rw | _GEN_417 | _GEN_416 | cs_208 | cs_207 | cs_206
    | _GEN_204 | cs_204 | _GEN_202 | cs_202 & ~_cpu_io_rw | _GEN_411 | cs_200 | cs_199
    | cs_198 | cs_197 | cs_196 | cs_195 | cs_194 | cs_193 | cs_192 | cs_191 | cs_190
    | cs_189 | cs_188 | cs_187 | cs_186 | _GEN_410 | cs_184 | cs_183 | _GEN_409;
  wire [15:0] _GEN_487 = cs_156 ? _vram16x16_0_io_portA_dout : _GEN_469;
  wire [15:0] _GEN_488 = cs_157 ? _lineRam_0_io_portA_dout : _GEN_487;
  wire [15:0] _GEN_489 = _GEN_470 ? tmp_24 : _GEN_488;
  wire [15:0] _GEN_490 = cs_159 ? _vram8x8_0_io_portA_dout : _GEN_489;
  wire [15:0] _GEN_491 = _GEN_471 ? tmp_25 : _GEN_490;
  wire [15:0] _GEN_492 = _GEN_402 ? 16'h0 : _GEN_491;
  wire [15:0] _GEN_493 = cs_162 ? _vram16x16_1_io_portA_dout : _GEN_492;
  wire [15:0] _GEN_494 = cs_163 ? _lineRam_1_io_portA_dout : _GEN_493;
  wire [15:0] _GEN_495 = _GEN_472 ? tmp_26 : _GEN_494;
  wire [15:0] _GEN_496 = cs_165 ? _vram8x8_1_io_portA_dout : _GEN_495;
  wire [15:0] _GEN_497 = _GEN_473 ? tmp_27 : _GEN_496;
  wire [15:0] _GEN_498 = _GEN_403 ? 16'h0 : _GEN_497;
  wire [15:0] _GEN_499 = cs_168 ? _vram16x16_2_io_portA_dout : _GEN_498;
  wire [15:0] _GEN_500 = cs_169 ? _lineRam_2_io_portA_dout : _GEN_499;
  wire        _GEN_501 = gameIsGaia ? _GEN_455 : _GEN_424;
  wire        _GEN_502 = gameIsGuwange ? _GEN_477 : _GEN_501;
  wire        _GEN_503 =
    gameIsHotdogStorm ? cs_213 | _GEN_486 | _GEN_479 : _GEN_502;
  wire        _GEN_504 =
    gameIsGaia ? _GEN_377 | _cpu_io_as & (_GEN_454 | _GEN_358) : _GEN_425;
  wire        _GEN_505 =
    gameIsGuwange ? _GEN_406 | _cpu_io_as & (_GEN_476 | _GEN_456) : _GEN_504;
  wire        _GEN_506 =
    gameIsHotdogStorm ? cs_213 | _cpu_io_as & (_GEN_486 | _GEN_479) : _GEN_505;
  always @(posedge clock) begin
    if (~cs_8 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp <= _cpu_io_dout;
    if (~cs_10 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_1 <= _cpu_io_dout;
    if (~cs_13 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_2 <= _cpu_io_dout;
    if (~cs_15 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_3 <= _cpu_io_dout;
    if (~cs_31 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_4 <= _cpu_io_dout;
    if (~cs_33 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_5 <= _cpu_io_dout;
    if (~cs_36 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_6 <= _cpu_io_dout;
    if (~cs_38 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_7 <= _cpu_io_dout;
    if (~cs_61 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_8 <= _cpu_io_dout;
    if (~cs_63 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_9 <= _cpu_io_dout;
    if (~cs_67 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_10 <= _cpu_io_dout;
    if (~cs_69 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_11 <= _cpu_io_dout;
    if (~cs_88 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_12 <= _cpu_io_dout;
    if (~cs_90 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_13 <= _cpu_io_dout;
    if (~cs_93 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_14 <= _cpu_io_dout;
    if (~cs_95 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_15 <= _cpu_io_dout;
    if (~cs_98 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_16 <= _cpu_io_dout;
    if (~cs_100 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_17 <= _cpu_io_dout;
    if (~cs_120 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_18 <= _cpu_io_dout;
    if (~cs_122 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_19 <= _cpu_io_dout;
    if (~cs_125 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_20 <= _cpu_io_dout;
    if (~cs_127 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_21 <= _cpu_io_dout;
    if (~cs_130 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_22 <= _cpu_io_dout;
    if (~cs_132 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_23 <= _cpu_io_dout;
    if (~cs_158 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_24 <= _cpu_io_dout;
    if (~cs_160 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_25 <= _cpu_io_dout;
    if (~cs_164 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_26 <= _cpu_io_dout;
    if (~cs_166 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_27 <= _cpu_io_dout;
    if (~cs_170 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_28 <= _cpu_io_dout;
    if (~cs_172 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_29 <= _cpu_io_dout;
    if (~cs_188 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_30 <= _cpu_io_dout;
    if (~cs_190 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_31 <= _cpu_io_dout;
    if (~cs_193 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_32 <= _cpu_io_dout;
    if (~cs_195 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_33 <= _cpu_io_dout;
    if (~cs_198 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_34 <= _cpu_io_dout;
    if (~cs_200 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_35 <= _cpu_io_dout;
    if (~cs_220 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_36 <= _cpu_io_dout;
    if (~cs_222 | readStrobe | ~writeStrobe) begin
    end
    else
      tmp_37 <= _cpu_io_dout;
    if (reset) begin
      videoIrq <= 1'h0;
      agalletIrq <= 1'h0;
      unknownIrq <= 1'h0;
      dinReg <= 16'h0;
      dtackReg <= 1'h0;
    end
    else begin
      videoIrq <=
        gameIsPwrInst2
          ? (videoIrq | videoVBlankRising) & ~pwrinst2VideoIrqClear
        : gameIsMazinger
          ? videoVBlankRising | (videoIrq & ~mazingerVideoIrqClear)
          : _GEN_421 ? ~(_offset_T_222 == 24'h4 | _GEN_412) & _GEN_385 : ~_GEN_412 & _GEN_385;
      agalletIrq <= videoVBlankRising | (~videoVBlankFalling & agalletIrq);
      unknownIrq <=
        gameIsPwrInst2
          ? unknownIrq & ~pwrinst2UnknownIrqClear
        : gameIsMazinger
          ? (unknownIrq | videoVBlankRising) & ~mazingerUnknownIrqClear
          : _GEN_421 ? ~(_offset_T_222 == 24'h6 | _GEN_413) & _GEN_386 : ~_GEN_413 & _GEN_386;
      if (gameIsPwrInst2) begin
        if (pwrinst2Dtack)
          dinReg <= pwrinst2ReadData;
      end
      else if (gameIsMazinger) begin
        if (mazingerReadDataValid)
          dinReg <= mazingerReadData;
      end
      else if (gameIsUopoko) begin
        if (_GEN_423)
          dinReg <= inputPort1;
        else if (_GEN_422)
          dinReg <= inputPort0;
        else if (cs_228)
          dinReg <= _paletteRam_io_portA_dout;
        else if (cs_227)
          dinReg <= _layerRegs_0_io_mem_dout;
        else if (cs_226 & readStrobe)
          dinReg <= 16'h0;
        else if (_GEN_420)
          dinReg <=
            {13'h0, ~(_offset_T_222 == 24'h0 & agalletIrq), ~unknownIrq, ~videoIrq};
        else if (cs_222 & readStrobe)
          dinReg <= tmp_37;
        else if (cs_221)
          dinReg <= _vram8x8_0_io_portA_dout;
        else if (cs_220 & readStrobe)
          dinReg <= tmp_36;
        else if (cs_219)
          dinReg <= _lineRam_0_io_portA_dout;
        else if (cs_218)
          dinReg <= _vram16x16_0_io_portA_dout;
        else if (cs_217)
          dinReg <= _spriteRam_io_portA_dout;
        else if (cs_216)
          dinReg <= io_soundCtrl_ymz_dout;
        else if (cs_215)
          dinReg <= _mainRam_io_dout;
        else if (_GEN_419)
          dinReg <= io_progRom_dout;
        else if (gameIsHotdogStorm) begin
          if (cs_213)
            dinReg <= _spriteRam_io_portA_dout;
          else if (_GEN_418)
            dinReg <= 16'h0;
          else if (_GEN_417)
            dinReg <= inputPort1;
          else if (_GEN_416)
            dinReg <= inputPort0;
          else if (cs_208)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_207)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_206)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_415)
            dinReg <= 16'h0;
          else if (_GEN_411)
            dinReg <= _GEN_414;
          else if (_GEN_485)
            dinReg <= tmp_35;
          else if (cs_199)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_484)
            dinReg <= tmp_34;
          else if (cs_197)
            dinReg <= _lineRam_2_io_portA_dout;
          else if (cs_196)
            dinReg <= _vram16x16_2_io_portA_dout;
          else if (_GEN_483)
            dinReg <= tmp_33;
          else if (cs_194)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_482)
            dinReg <= tmp_32;
          else if (cs_192)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_191)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_481)
            dinReg <= tmp_31;
          else if (cs_189)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_480)
            dinReg <= tmp_30;
          else if (cs_187)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_186)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (_GEN_410)
            dinReg <= 16'h0;
          else if (cs_184)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_183)
            dinReg <= _mainRam_io_dout;
          else if (_GEN_409)
            dinReg <= io_progRom_dout;
          else if (gameIsGuwange) begin
            if (_GEN_406)
              dinReg <= inputGuwangeSystem;
            else if (_GEN_405)
              dinReg <= inputGuwangeP1;
            else if (_GEN_404)
              dinReg <= 16'h0;
            else if (cs_177)
              dinReg <= _paletteRam_io_portA_dout;
            else if (cs_176)
              dinReg <= _layerRegs_2_io_mem_dout;
            else if (cs_175)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_174)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (cs_173)
              dinReg <= io_soundCtrl_ymz_dout;
            else if (_GEN_475)
              dinReg <= tmp_29;
            else if (cs_171)
              dinReg <= _vram8x8_2_io_portA_dout;
            else
              dinReg <= _GEN_474 ? tmp_28 : _GEN_500;
          end
          else if (gameIsGaia) begin
            if (_GEN_376)
              dinReg <= io_dips_0;
            else if (_GEN_375)
              dinReg <= inputGaiaSystem;
            else if (_GEN_374)
              dinReg <= inputPlayersWide;
            else if (cs_140)
              dinReg <= _paletteRam_io_portA_dout;
            else if (cs_139)
              dinReg <= _layerRegs_2_io_mem_dout;
            else if (cs_138)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_137)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (_GEN_373)
              dinReg <= 16'h0;
            else if (_GEN_369)
              dinReg <= _GEN_372;
            else
              dinReg <= _GEN_449;
          end
          else if (gameIsEsprade) begin
            if (_GEN_117)
              dinReg <= inputDefaultP2;
            else if (_GEN_116)
              dinReg <= inputDefaultP1;
            else if (cs_109)
              dinReg <= _paletteRam_io_portA_dout;
            else if (cs_108)
              dinReg <= _layerRegs_2_io_mem_dout;
            else if (cs_107)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_106)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (_GEN_351)
              dinReg <= 16'h0;
            else if (_GEN_106)
              dinReg <= _GEN_350;
            else
              dinReg <= _GEN_346;
          end
          else if (gameIsDoDonPachi) begin
            if (_GEN_77)
              dinReg <= inputDefaultP2;
            else if (_GEN_76)
              dinReg <= inputDefaultP1;
            else if (cs_78)
              dinReg <= _paletteRam_io_portA_dout;
            else if (cs_77)
              dinReg <= _layerRegs_2_io_mem_dout;
            else if (cs_76)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_75)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (_GEN_312)
              dinReg <= 16'h0;
            else
              dinReg <= _GEN_311;
          end
          else if (gameIsDonPachi) begin
            if (_GEN_38)
              dinReg <= inputDefaultP2;
            else if (_GEN_37)
              dinReg <= inputDefaultP1;
            else if (cs_50)
              dinReg <= io_soundCtrl_oki_1_dout;
            else if (cs_49)
              dinReg <= io_soundCtrl_oki_0_dout;
            else if (cs_48)
              dinReg <= _paletteRam_io_portA_dout;
            else if (_GEN_280)
              dinReg <= 16'h0;
            else
              dinReg <= _GEN_279;
          end
          else if (gameIsDFeveron) begin
            if (_GEN_5)
              dinReg <= inputDefaultP2;
            else if (_GEN_4)
              dinReg <= inputPort0;
            else if (cs_23)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_22)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (_GEN_246)
              dinReg <= 16'h0;
            else
              dinReg <= _GEN_245;
          end
          else if (_GEN_218)
            dinReg <= 16'h0;
        end
        else if (gameIsGuwange) begin
          if (_GEN_406)
            dinReg <= inputGuwangeSystem;
          else if (_GEN_405)
            dinReg <= inputGuwangeP1;
          else if (_GEN_404)
            dinReg <= 16'h0;
          else if (cs_177)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_176)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_175)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_174)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (cs_173)
            dinReg <= io_soundCtrl_ymz_dout;
          else if (_GEN_475)
            dinReg <= tmp_29;
          else if (cs_171)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_474)
            dinReg <= tmp_28;
          else if (cs_169)
            dinReg <= _lineRam_2_io_portA_dout;
          else if (cs_168)
            dinReg <= _vram16x16_2_io_portA_dout;
          else if (_GEN_403)
            dinReg <= 16'h0;
          else if (_GEN_473)
            dinReg <= tmp_27;
          else if (cs_165)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_472)
            dinReg <= tmp_26;
          else if (cs_163)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_162)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_402)
            dinReg <= 16'h0;
          else if (_GEN_471)
            dinReg <= tmp_25;
          else if (cs_159)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_470)
            dinReg <= tmp_24;
          else if (cs_157)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_156)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (_GEN_390)
            dinReg <= 16'h0;
          else if (cs_154)
            dinReg <= _spriteRam_io_portA_dout;
          else if (_GEN_389)
            dinReg <= 16'h0;
          else if (_GEN_383)
            dinReg <= _GEN_387;
          else if (_GEN_382)
            dinReg <= 16'h0;
          else if (cs_147)
            dinReg <= _mainRam_io_dout;
          else if (_GEN_381)
            dinReg <= io_progRom_dout;
          else if (gameIsGaia) begin
            if (_GEN_376)
              dinReg <= io_dips_0;
            else if (_GEN_375)
              dinReg <= inputSharedSystem;
            else if (_GEN_374)
              dinReg <= inputPlayersWideOrGuwangeP1;
            else if (cs_140)
              dinReg <= _paletteRam_io_portA_dout;
            else if (cs_139)
              dinReg <= _layerRegs_2_io_mem_dout;
            else
              dinReg <= _GEN_453;
          end
          else if (gameIsEsprade) begin
            if (_GEN_117)
              dinReg <= inputP2DefaultOrGuwange;
            else if (_GEN_116)
              dinReg <= inputP1DefaultOrGuwange;
            else if (cs_109)
              dinReg <= _paletteRam_io_portA_dout;
            else if (cs_108)
              dinReg <= _layerRegs_2_io_mem_dout;
            else
              dinReg <= _GEN_355;
          end
          else if (gameIsDoDonPachi) begin
            if (_GEN_77)
              dinReg <= inputP2DefaultOrGuwange;
            else if (_GEN_76)
              dinReg <= inputP1DefaultOrGuwange;
            else if (cs_78)
              dinReg <= _paletteRam_io_portA_dout;
            else
              dinReg <= _GEN_316;
          end
          else if (gameIsDonPachi) begin
            if (_GEN_38)
              dinReg <= inputP2DefaultOrGuwange;
            else if (_GEN_37)
              dinReg <= inputP1DefaultOrGuwange;
            else
              dinReg <= _GEN_284;
          end
          else if (gameIsDFeveron) begin
            if (_GEN_5)
              dinReg <= inputP2DefaultOrGuwange;
            else
              dinReg <= _GEN_250;
          end
          else if (_GEN_218)
            dinReg <= 16'h0;
        end
        else if (gameIsGaia) begin
          if (_GEN_376)
            dinReg <= io_dips_0;
          else if (_GEN_375)
            dinReg <= inputGaiaSystem;
          else if (_GEN_374)
            dinReg <= inputPlayersWide;
          else if (cs_140)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_139)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_138)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_137)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_373)
            dinReg <= 16'h0;
          else if (_GEN_369)
            dinReg <= _GEN_372;
          else if (_GEN_439)
            dinReg <= tmp_23;
          else if (cs_131)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_438)
            dinReg <= tmp_22;
          else if (cs_129)
            dinReg <= _lineRam_2_io_portA_dout;
          else if (cs_128)
            dinReg <= _vram16x16_2_io_portA_dout;
          else if (_GEN_437)
            dinReg <= tmp_21;
          else if (cs_126)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_436)
            dinReg <= tmp_20;
          else if (cs_124)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_123)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_432)
            dinReg <= tmp_19;
          else if (cs_121)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_431)
            dinReg <= tmp_18;
          else if (cs_119)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_118)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_117)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_116)
            dinReg <= io_soundCtrl_ymz_dout;
          else if (cs_115)
            dinReg <= _mainRam_io_dout;
          else if (_GEN_359)
            dinReg <= io_progRom_dout;
          else if (gameIsEsprade) begin
            if (_GEN_117)
              dinReg <= inputPort1;
            else if (_GEN_116)
              dinReg <= inputPort0;
            else if (cs_109)
              dinReg <= _paletteRam_io_portA_dout;
            else if (cs_108)
              dinReg <= _layerRegs_2_io_mem_dout;
            else if (cs_107)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_106)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (_GEN_351)
              dinReg <= 16'h0;
            else if (_GEN_106)
              dinReg <= _GEN_350;
            else
              dinReg <= _GEN_346;
          end
          else if (gameIsDoDonPachi) begin
            if (_GEN_77)
              dinReg <= inputPort1;
            else if (_GEN_76)
              dinReg <= inputPort0;
            else if (cs_78)
              dinReg <= _paletteRam_io_portA_dout;
            else if (cs_77)
              dinReg <= _layerRegs_2_io_mem_dout;
            else if (cs_76)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_75)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (_GEN_312)
              dinReg <= 16'h0;
            else
              dinReg <= _GEN_311;
          end
          else if (gameIsDonPachi) begin
            if (_GEN_38)
              dinReg <= inputPort1;
            else if (_GEN_37)
              dinReg <= inputPort0;
            else if (cs_50)
              dinReg <= io_soundCtrl_oki_1_dout;
            else if (cs_49)
              dinReg <= io_soundCtrl_oki_0_dout;
            else if (cs_48)
              dinReg <= _paletteRam_io_portA_dout;
            else if (_GEN_280)
              dinReg <= 16'h0;
            else
              dinReg <= _GEN_279;
          end
          else if (gameIsDFeveron) begin
            if (_GEN_5)
              dinReg <= inputPort1;
            else if (_GEN_4)
              dinReg <= inputPort0;
            else if (cs_23)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_22)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (_GEN_246)
              dinReg <= 16'h0;
            else
              dinReg <= _GEN_245;
          end
          else if (_GEN_218)
            dinReg <= 16'h0;
        end
        else if (gameIsEsprade) begin
          if (_GEN_117)
            dinReg <= inputDefaultP2;
          else if (_GEN_116)
            dinReg <= inputDefaultP1;
          else if (cs_109)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_108)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_107)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_106)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_351)
            dinReg <= 16'h0;
          else if (_GEN_106)
            dinReg <= _GEN_350;
          else if (_GEN_336)
            dinReg <= tmp_17;
          else if (cs_99)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_335)
            dinReg <= tmp_16;
          else if (cs_97)
            dinReg <= _lineRam_2_io_portA_dout;
          else if (cs_96)
            dinReg <= _vram16x16_2_io_portA_dout;
          else if (_GEN_334)
            dinReg <= tmp_15;
          else if (cs_94)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_333)
            dinReg <= tmp_14;
          else if (cs_92)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_91)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_328)
            dinReg <= tmp_13;
          else if (cs_89)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_327)
            dinReg <= tmp_12;
          else if (cs_87)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_86)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_85)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_84)
            dinReg <= io_soundCtrl_ymz_dout;
          else if (cs_83)
            dinReg <= _mainRam_io_dout;
          else if (_GEN_85)
            dinReg <= io_progRom_dout;
          else if (gameIsDoDonPachi) begin
            if (_GEN_77)
              dinReg <= inputPort1;
            else if (_GEN_76)
              dinReg <= inputPort0;
            else if (cs_78)
              dinReg <= _paletteRam_io_portA_dout;
            else if (cs_77)
              dinReg <= _layerRegs_2_io_mem_dout;
            else if (cs_76)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_75)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (_GEN_312)
              dinReg <= 16'h0;
            else if (_GEN_67)
              dinReg <= _GEN_310;
            else
              dinReg <= _GEN_307;
          end
          else if (gameIsDonPachi) begin
            if (_GEN_38)
              dinReg <= inputPort1;
            else if (_GEN_37)
              dinReg <= inputPort0;
            else if (cs_50)
              dinReg <= io_soundCtrl_oki_1_dout;
            else if (cs_49)
              dinReg <= io_soundCtrl_oki_0_dout;
            else if (cs_48)
              dinReg <= _paletteRam_io_portA_dout;
            else if (_GEN_280)
              dinReg <= 16'h0;
            else if (_GEN_30)
              dinReg <= _GEN_278;
            else
              dinReg <= _GEN_274;
          end
          else if (gameIsDFeveron) begin
            if (_GEN_5)
              dinReg <= inputPort1;
            else if (_GEN_4)
              dinReg <= inputPort0;
            else if (cs_23)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_22)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (_GEN_246)
              dinReg <= 16'h0;
            else if (_GEN_1)
              dinReg <= _GEN_244;
            else
              dinReg <= _GEN_240;
          end
          else if (_GEN_218)
            dinReg <= 16'h0;
        end
        else if (gameIsDoDonPachi) begin
          if (_GEN_77)
            dinReg <= inputDefaultP2;
          else if (_GEN_76)
            dinReg <= inputDefaultP1;
          else if (cs_78)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_77)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_76)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_75)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_312)
            dinReg <= 16'h0;
          else if (_GEN_67)
            dinReg <= _GEN_310;
          else if (cs_70)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_298)
            dinReg <= tmp_11;
          else if (cs_68)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_297)
            dinReg <= tmp_10;
          else if (cs_66)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_65)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_296)
            dinReg <= tmp_9;
          else if (cs_62)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_295)
            dinReg <= tmp_8;
          else if (cs_60)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_59)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_58)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_57)
            dinReg <= io_soundCtrl_ymz_dout;
          else if (cs_56)
            dinReg <= _mainRam_io_dout;
          else if (_GEN_45)
            dinReg <= io_progRom_dout;
          else if (gameIsDonPachi) begin
            if (_GEN_38)
              dinReg <= inputPort1;
            else if (_GEN_37)
              dinReg <= inputPort0;
            else if (cs_50)
              dinReg <= io_soundCtrl_oki_1_dout;
            else if (cs_49)
              dinReg <= io_soundCtrl_oki_0_dout;
            else if (cs_48)
              dinReg <= _paletteRam_io_portA_dout;
            else if (_GEN_280)
              dinReg <= 16'h0;
            else if (_GEN_30)
              dinReg <= _GEN_278;
            else if (cs_43)
              dinReg <= _layerRegs_2_io_mem_dout;
            else if (cs_42)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (cs_41)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_40)
              dinReg <= _spriteRam_io_portA_dout;
            else
              dinReg <= _GEN_270;
          end
          else if (gameIsDFeveron) begin
            if (_GEN_5)
              dinReg <= inputPort1;
            else if (_GEN_4)
              dinReg <= inputPort0;
            else if (cs_23)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_22)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (_GEN_246)
              dinReg <= 16'h0;
            else if (_GEN_1)
              dinReg <= _GEN_244;
            else if (_GEN_237)
              dinReg <= 16'h0;
            else if (cs_16)
              dinReg <= _paletteRam_io_portA_dout;
            else if (_GEN_236)
              dinReg <= tmp_3;
            else if (cs_14)
              dinReg <= _vram8x8_1_io_portA_dout;
            else
              dinReg <= _GEN_234;
          end
          else if (_GEN_218)
            dinReg <= 16'h0;
        end
        else if (gameIsDonPachi) begin
          if (_GEN_38)
            dinReg <= inputDefaultP2;
          else if (_GEN_37)
            dinReg <= inputDefaultP1;
          else if (cs_50)
            dinReg <= io_soundCtrl_oki_1_dout;
          else if (cs_49)
            dinReg <= io_soundCtrl_oki_0_dout;
          else if (cs_48)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_280)
            dinReg <= 16'h0;
          else if (_GEN_30)
            dinReg <= _GEN_278;
          else if (cs_43)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_42)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (cs_41)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_40)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_39)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_265)
            dinReg <= tmp_7;
          else if (cs_37)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_264)
            dinReg <= tmp_6;
          else if (cs_35)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_34)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (_GEN_259)
            dinReg <= tmp_5;
          else if (cs_32)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_258)
            dinReg <= tmp_4;
          else if (cs_30)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_29)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (cs_28)
            dinReg <= _mainRam_io_dout;
          else if (_GEN_11)
            dinReg <= io_progRom_dout;
          else if (gameIsDFeveron) begin
            if (_GEN_5)
              dinReg <= inputPort1;
            else if (_GEN_4)
              dinReg <= inputPort0;
            else if (cs_23)
              dinReg <= _layerRegs_1_io_mem_dout;
            else if (cs_22)
              dinReg <= _layerRegs_0_io_mem_dout;
            else if (_GEN_246)
              dinReg <= 16'h0;
            else if (_GEN_1)
              dinReg <= _GEN_244;
            else if (_GEN_237)
              dinReg <= 16'h0;
            else if (cs_16)
              dinReg <= _paletteRam_io_portA_dout;
            else if (_GEN_236)
              dinReg <= tmp_3;
            else
              dinReg <= _GEN_235;
          end
          else if (_GEN_218)
            dinReg <= 16'h0;
        end
        else if (gameIsDFeveron) begin
          if (_GEN_5)
            dinReg <= inputDefaultP2;
          else if (_GEN_4)
            dinReg <= inputPort0;
          else if (cs_23)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_22)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_246)
            dinReg <= 16'h0;
          else if (_GEN_1)
            dinReg <= _GEN_244;
          else if (_GEN_237)
            dinReg <= 16'h0;
          else if (cs_16)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_236)
            dinReg <= tmp_3;
          else if (cs_14)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_229)
            dinReg <= tmp_2;
          else if (cs_12)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_11)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_228)
            dinReg <= tmp_1;
          else if (cs_9)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_223)
            dinReg <= tmp;
          else if (cs_7)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_6)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_5)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_4)
            dinReg <= io_soundCtrl_ymz_dout;
          else if (cs_3)
            dinReg <= _mainRam_io_dout;
          else if (_GEN_0)
            dinReg <= io_progRom_dout;
          else if (_GEN_218)
            dinReg <= 16'h0;
        end
        else if (_GEN_218)
          dinReg <= 16'h0;
      end
      else if (gameIsHotdogStorm) begin
        if (cs_213)
          dinReg <= _spriteRam_io_portA_dout;
        else if (_GEN_418)
          dinReg <= 16'h0;
        else if (_GEN_417)
          dinReg <= inputPort1;
        else if (_GEN_416)
          dinReg <= inputPort0;
        else if (cs_208)
          dinReg <= _layerRegs_2_io_mem_dout;
        else if (cs_207)
          dinReg <= _layerRegs_1_io_mem_dout;
        else if (cs_206)
          dinReg <= _layerRegs_0_io_mem_dout;
        else if (_GEN_415)
          dinReg <= 16'h0;
        else if (_GEN_411)
          dinReg <= _GEN_414;
        else if (_GEN_485)
          dinReg <= tmp_35;
        else if (cs_199)
          dinReg <= _vram8x8_2_io_portA_dout;
        else if (_GEN_484)
          dinReg <= tmp_34;
        else if (cs_197)
          dinReg <= _lineRam_2_io_portA_dout;
        else if (cs_196)
          dinReg <= _vram16x16_2_io_portA_dout;
        else if (_GEN_483)
          dinReg <= tmp_33;
        else if (cs_194)
          dinReg <= _vram8x8_1_io_portA_dout;
        else if (_GEN_482)
          dinReg <= tmp_32;
        else if (cs_192)
          dinReg <= _lineRam_1_io_portA_dout;
        else if (cs_191)
          dinReg <= _vram16x16_1_io_portA_dout;
        else if (_GEN_481)
          dinReg <= tmp_31;
        else if (cs_189)
          dinReg <= _vram8x8_0_io_portA_dout;
        else if (_GEN_480)
          dinReg <= tmp_30;
        else if (cs_187)
          dinReg <= _lineRam_0_io_portA_dout;
        else if (cs_186)
          dinReg <= _vram16x16_0_io_portA_dout;
        else if (_GEN_410)
          dinReg <= 16'h0;
        else if (cs_184)
          dinReg <= _paletteRam_io_portA_dout;
        else if (cs_183)
          dinReg <= _mainRam_io_dout;
        else if (_GEN_409)
          dinReg <= io_progRom_dout;
        else if (gameIsGuwange) begin
          if (_GEN_406)
            dinReg <= inputGuwangeSystem;
          else if (_GEN_405)
            dinReg <= inputGuwangeP1;
          else if (_GEN_404)
            dinReg <= 16'h0;
          else if (cs_177)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_176)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_175)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_174)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (cs_173)
            dinReg <= io_soundCtrl_ymz_dout;
          else if (_GEN_475)
            dinReg <= tmp_29;
          else if (cs_171)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_474)
            dinReg <= tmp_28;
          else if (cs_169)
            dinReg <= _lineRam_2_io_portA_dout;
          else if (cs_168)
            dinReg <= _vram16x16_2_io_portA_dout;
          else if (_GEN_403)
            dinReg <= 16'h0;
          else if (_GEN_473)
            dinReg <= tmp_27;
          else if (cs_165)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_472)
            dinReg <= tmp_26;
          else if (cs_163)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_162)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_402)
            dinReg <= 16'h0;
          else if (_GEN_471)
            dinReg <= tmp_25;
          else if (cs_159)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_470)
            dinReg <= tmp_24;
          else if (cs_157)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_156)
            dinReg <= _vram16x16_0_io_portA_dout;
          else
            dinReg <= _GEN_469;
        end
        else if (gameIsGaia) begin
          if (_GEN_376)
            dinReg <= io_dips_0;
          else if (_GEN_375)
            dinReg <= inputGaiaSystem;
          else if (_GEN_374)
            dinReg <= inputPlayersWide;
          else if (cs_140)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_139)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_138)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_137)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_373)
            dinReg <= 16'h0;
          else if (_GEN_369)
            dinReg <= _GEN_372;
          else if (_GEN_439)
            dinReg <= tmp_23;
          else if (cs_131)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_438)
            dinReg <= tmp_22;
          else if (cs_129)
            dinReg <= _lineRam_2_io_portA_dout;
          else if (cs_128)
            dinReg <= _vram16x16_2_io_portA_dout;
          else if (_GEN_437)
            dinReg <= tmp_21;
          else if (cs_126)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_436)
            dinReg <= tmp_20;
          else if (cs_124)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_123)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_432)
            dinReg <= tmp_19;
          else if (cs_121)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_431)
            dinReg <= tmp_18;
          else if (cs_119)
            dinReg <= _lineRam_0_io_portA_dout;
          else
            dinReg <= _GEN_367;
        end
        else if (gameIsEsprade) begin
          if (_GEN_117)
            dinReg <= inputDefaultP2;
          else if (_GEN_116)
            dinReg <= inputDefaultP1;
          else if (cs_109)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_108)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_107)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_106)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_351)
            dinReg <= 16'h0;
          else if (_GEN_106)
            dinReg <= _GEN_350;
          else if (_GEN_336)
            dinReg <= tmp_17;
          else if (cs_99)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_335)
            dinReg <= tmp_16;
          else if (cs_97)
            dinReg <= _lineRam_2_io_portA_dout;
          else if (cs_96)
            dinReg <= _vram16x16_2_io_portA_dout;
          else if (_GEN_334)
            dinReg <= tmp_15;
          else if (cs_94)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_333)
            dinReg <= tmp_14;
          else if (cs_92)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_91)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_328)
            dinReg <= tmp_13;
          else if (cs_89)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_327)
            dinReg <= tmp_12;
          else if (cs_87)
            dinReg <= _lineRam_0_io_portA_dout;
          else
            dinReg <= _GEN_326;
        end
        else if (gameIsDoDonPachi) begin
          if (_GEN_77)
            dinReg <= inputDefaultP2;
          else if (_GEN_76)
            dinReg <= inputDefaultP1;
          else if (cs_78)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_77)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_76)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_75)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_312)
            dinReg <= 16'h0;
          else if (_GEN_67)
            dinReg <= _GEN_310;
          else if (cs_70)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_298)
            dinReg <= tmp_11;
          else if (cs_68)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_297)
            dinReg <= tmp_10;
          else if (cs_66)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_65)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_296)
            dinReg <= tmp_9;
          else if (cs_62)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_295)
            dinReg <= tmp_8;
          else if (cs_60)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_59)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_58)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_57)
            dinReg <= io_soundCtrl_ymz_dout;
          else
            dinReg <= _GEN_290;
        end
        else if (gameIsDonPachi) begin
          if (_GEN_38)
            dinReg <= inputDefaultP2;
          else if (_GEN_37)
            dinReg <= inputDefaultP1;
          else if (cs_50)
            dinReg <= io_soundCtrl_oki_1_dout;
          else if (cs_49)
            dinReg <= io_soundCtrl_oki_0_dout;
          else if (cs_48)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_280)
            dinReg <= 16'h0;
          else if (_GEN_30)
            dinReg <= _GEN_278;
          else if (cs_43)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_42)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (cs_41)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_40)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_39)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_265)
            dinReg <= tmp_7;
          else if (cs_37)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_264)
            dinReg <= tmp_6;
          else if (cs_35)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_34)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (_GEN_259)
            dinReg <= tmp_5;
          else if (cs_32)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_258)
            dinReg <= tmp_4;
          else
            dinReg <= _GEN_257;
        end
        else if (gameIsDFeveron) begin
          if (_GEN_5)
            dinReg <= inputDefaultP2;
          else if (_GEN_4)
            dinReg <= inputPort0;
          else if (cs_23)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_22)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_246)
            dinReg <= 16'h0;
          else if (_GEN_1)
            dinReg <= _GEN_244;
          else if (_GEN_237)
            dinReg <= 16'h0;
          else if (cs_16)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_236)
            dinReg <= tmp_3;
          else if (cs_14)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_229)
            dinReg <= tmp_2;
          else if (cs_12)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_11)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_228)
            dinReg <= tmp_1;
          else if (cs_9)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_223)
            dinReg <= tmp;
          else if (cs_7)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_6)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_5)
            dinReg <= _spriteRam_io_portA_dout;
          else
            dinReg <= _GEN_222;
        end
        else if (_GEN_218)
          dinReg <= 16'h0;
      end
      else if (gameIsGuwange) begin
        if (_GEN_406)
          dinReg <= inputGuwangeSystem;
        else if (_GEN_405)
          dinReg <= inputGuwangeP1;
        else if (_GEN_404)
          dinReg <= 16'h0;
        else if (cs_177)
          dinReg <= _paletteRam_io_portA_dout;
        else if (cs_176)
          dinReg <= _layerRegs_2_io_mem_dout;
        else if (cs_175)
          dinReg <= _layerRegs_1_io_mem_dout;
        else if (cs_174)
          dinReg <= _layerRegs_0_io_mem_dout;
        else if (cs_173)
          dinReg <= io_soundCtrl_ymz_dout;
        else if (_GEN_475)
          dinReg <= tmp_29;
        else if (cs_171)
          dinReg <= _vram8x8_2_io_portA_dout;
        else if (_GEN_474)
          dinReg <= tmp_28;
        else if (cs_169)
          dinReg <= _lineRam_2_io_portA_dout;
        else if (cs_168)
          dinReg <= _vram16x16_2_io_portA_dout;
        else if (_GEN_403)
          dinReg <= 16'h0;
        else if (_GEN_473)
          dinReg <= tmp_27;
        else if (cs_165)
          dinReg <= _vram8x8_1_io_portA_dout;
        else if (_GEN_472)
          dinReg <= tmp_26;
        else if (cs_163)
          dinReg <= _lineRam_1_io_portA_dout;
        else if (cs_162)
          dinReg <= _vram16x16_1_io_portA_dout;
        else if (_GEN_402)
          dinReg <= 16'h0;
        else if (_GEN_471)
          dinReg <= tmp_25;
        else if (cs_159)
          dinReg <= _vram8x8_0_io_portA_dout;
        else if (_GEN_470)
          dinReg <= tmp_24;
        else if (cs_157)
          dinReg <= _lineRam_0_io_portA_dout;
        else if (cs_156)
          dinReg <= _vram16x16_0_io_portA_dout;
        else if (_GEN_390)
          dinReg <= 16'h0;
        else if (cs_154)
          dinReg <= _spriteRam_io_portA_dout;
        else if (_GEN_389)
          dinReg <= 16'h0;
        else if (_GEN_383)
          dinReg <= _GEN_387;
        else if (_GEN_382)
          dinReg <= 16'h0;
        else if (cs_147)
          dinReg <= _mainRam_io_dout;
        else if (_GEN_381)
          dinReg <= io_progRom_dout;
        else if (gameIsGaia) begin
          if (_GEN_376)
            dinReg <= io_dips_0;
          else if (_GEN_375)
            dinReg <= inputSharedSystem;
          else if (_GEN_374)
            dinReg <= inputPlayersWideOrGuwangeP1;
          else if (cs_140)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_139)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_138)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_137)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_373)
            dinReg <= 16'h0;
          else if (_GEN_369)
            dinReg <= _GEN_372;
          else if (_GEN_439)
            dinReg <= tmp_23;
          else if (cs_131)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_438)
            dinReg <= tmp_22;
          else if (cs_129)
            dinReg <= _lineRam_2_io_portA_dout;
          else if (cs_128)
            dinReg <= _vram16x16_2_io_portA_dout;
          else if (_GEN_437)
            dinReg <= tmp_21;
          else if (cs_126)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_436)
            dinReg <= tmp_20;
          else if (cs_124)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_123)
            dinReg <= _vram16x16_1_io_portA_dout;
          else
            dinReg <= _GEN_435;
        end
        else if (gameIsEsprade) begin
          if (_GEN_117)
            dinReg <= inputP2DefaultOrGuwange;
          else if (_GEN_116)
            dinReg <= inputP1DefaultOrGuwange;
          else if (cs_109)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_108)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_107)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_106)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_351)
            dinReg <= 16'h0;
          else if (_GEN_106)
            dinReg <= _GEN_350;
          else if (_GEN_336)
            dinReg <= tmp_17;
          else if (cs_99)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_335)
            dinReg <= tmp_16;
          else if (cs_97)
            dinReg <= _lineRam_2_io_portA_dout;
          else if (cs_96)
            dinReg <= _vram16x16_2_io_portA_dout;
          else if (_GEN_334)
            dinReg <= tmp_15;
          else if (cs_94)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_333)
            dinReg <= tmp_14;
          else if (cs_92)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_91)
            dinReg <= _vram16x16_1_io_portA_dout;
          else
            dinReg <= _GEN_332;
        end
        else if (gameIsDoDonPachi) begin
          if (_GEN_77)
            dinReg <= inputP2DefaultOrGuwange;
          else if (_GEN_76)
            dinReg <= inputP1DefaultOrGuwange;
          else if (cs_78)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_77)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_76)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_75)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_312)
            dinReg <= 16'h0;
          else if (_GEN_67)
            dinReg <= _GEN_310;
          else if (cs_70)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_298)
            dinReg <= tmp_11;
          else if (cs_68)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_297)
            dinReg <= tmp_10;
          else if (cs_66)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_65)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_296)
            dinReg <= tmp_9;
          else if (cs_62)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_295)
            dinReg <= tmp_8;
          else
            dinReg <= _GEN_294;
        end
        else if (gameIsDonPachi) begin
          if (_GEN_38)
            dinReg <= inputP2DefaultOrGuwange;
          else if (_GEN_37)
            dinReg <= inputP1DefaultOrGuwange;
          else if (cs_50)
            dinReg <= io_soundCtrl_oki_1_dout;
          else if (cs_49)
            dinReg <= io_soundCtrl_oki_0_dout;
          else if (cs_48)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_280)
            dinReg <= 16'h0;
          else if (_GEN_30)
            dinReg <= _GEN_278;
          else if (cs_43)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_42)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (cs_41)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_40)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_39)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_265)
            dinReg <= tmp_7;
          else if (cs_37)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_264)
            dinReg <= tmp_6;
          else if (cs_35)
            dinReg <= _lineRam_0_io_portA_dout;
          else
            dinReg <= _GEN_263;
        end
        else if (gameIsDFeveron) begin
          if (_GEN_5)
            dinReg <= inputP2DefaultOrGuwange;
          else if (_GEN_4)
            dinReg <= inputPort0;
          else if (cs_23)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_22)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_246)
            dinReg <= 16'h0;
          else if (_GEN_1)
            dinReg <= _GEN_244;
          else if (_GEN_237)
            dinReg <= 16'h0;
          else if (cs_16)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_236)
            dinReg <= tmp_3;
          else if (cs_14)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_229)
            dinReg <= tmp_2;
          else if (cs_12)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_11)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_228)
            dinReg <= tmp_1;
          else if (cs_9)
            dinReg <= _vram8x8_0_io_portA_dout;
          else
            dinReg <= _GEN_227;
        end
        else if (_GEN_218)
          dinReg <= 16'h0;
      end
      else if (gameIsGaia) begin
        if (_GEN_376)
          dinReg <= io_dips_0;
        else if (_GEN_375)
          dinReg <= inputGaiaSystem;
        else if (_GEN_374)
          dinReg <= inputPlayersWide;
        else if (cs_140)
          dinReg <= _paletteRam_io_portA_dout;
        else if (cs_139)
          dinReg <= _layerRegs_2_io_mem_dout;
        else if (cs_138)
          dinReg <= _layerRegs_1_io_mem_dout;
        else if (cs_137)
          dinReg <= _layerRegs_0_io_mem_dout;
        else if (_GEN_373)
          dinReg <= 16'h0;
        else if (_GEN_369)
          dinReg <= _GEN_372;
        else if (_GEN_439)
          dinReg <= tmp_23;
        else if (cs_131)
          dinReg <= _vram8x8_2_io_portA_dout;
        else if (_GEN_438)
          dinReg <= tmp_22;
        else if (cs_129)
          dinReg <= _lineRam_2_io_portA_dout;
        else if (cs_128)
          dinReg <= _vram16x16_2_io_portA_dout;
        else if (_GEN_437)
          dinReg <= tmp_21;
        else if (cs_126)
          dinReg <= _vram8x8_1_io_portA_dout;
        else if (_GEN_436)
          dinReg <= tmp_20;
        else if (cs_124)
          dinReg <= _lineRam_1_io_portA_dout;
        else if (cs_123)
          dinReg <= _vram16x16_1_io_portA_dout;
        else if (_GEN_432)
          dinReg <= tmp_19;
        else if (cs_121)
          dinReg <= _vram8x8_0_io_portA_dout;
        else if (_GEN_431)
          dinReg <= tmp_18;
        else if (cs_119)
          dinReg <= _lineRam_0_io_portA_dout;
        else if (cs_118)
          dinReg <= _vram16x16_0_io_portA_dout;
        else if (cs_117)
          dinReg <= _spriteRam_io_portA_dout;
        else if (cs_116)
          dinReg <= io_soundCtrl_ymz_dout;
        else if (cs_115)
          dinReg <= _mainRam_io_dout;
        else if (_GEN_359)
          dinReg <= io_progRom_dout;
        else if (gameIsEsprade) begin
          if (_GEN_117)
            dinReg <= inputPort1;
          else if (_GEN_116)
            dinReg <= inputPort0;
          else if (cs_109)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_108)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_107)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_106)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_351)
            dinReg <= 16'h0;
          else if (_GEN_106)
            dinReg <= _GEN_350;
          else if (_GEN_336)
            dinReg <= tmp_17;
          else if (cs_99)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_335)
            dinReg <= tmp_16;
          else if (cs_97)
            dinReg <= _lineRam_2_io_portA_dout;
          else if (cs_96)
            dinReg <= _vram16x16_2_io_portA_dout;
          else if (_GEN_334)
            dinReg <= tmp_15;
          else if (cs_94)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_333)
            dinReg <= tmp_14;
          else if (cs_92)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_91)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_328)
            dinReg <= tmp_13;
          else if (cs_89)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_327)
            dinReg <= tmp_12;
          else if (cs_87)
            dinReg <= _lineRam_0_io_portA_dout;
          else
            dinReg <= _GEN_326;
        end
        else if (gameIsDoDonPachi) begin
          if (_GEN_77)
            dinReg <= inputPort1;
          else if (_GEN_76)
            dinReg <= inputPort0;
          else if (cs_78)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_77)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_76)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_75)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_312)
            dinReg <= 16'h0;
          else if (_GEN_67)
            dinReg <= _GEN_310;
          else if (cs_70)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_298)
            dinReg <= tmp_11;
          else if (cs_68)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_297)
            dinReg <= tmp_10;
          else if (cs_66)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_65)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_296)
            dinReg <= tmp_9;
          else if (cs_62)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_295)
            dinReg <= tmp_8;
          else if (cs_60)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_59)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_58)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_57)
            dinReg <= io_soundCtrl_ymz_dout;
          else
            dinReg <= _GEN_290;
        end
        else if (gameIsDonPachi) begin
          if (_GEN_38)
            dinReg <= inputPort1;
          else if (_GEN_37)
            dinReg <= inputPort0;
          else if (cs_50)
            dinReg <= io_soundCtrl_oki_1_dout;
          else if (cs_49)
            dinReg <= io_soundCtrl_oki_0_dout;
          else if (cs_48)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_280)
            dinReg <= 16'h0;
          else if (_GEN_30)
            dinReg <= _GEN_278;
          else if (cs_43)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_42)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (cs_41)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_40)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_39)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_265)
            dinReg <= tmp_7;
          else if (cs_37)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_264)
            dinReg <= tmp_6;
          else if (cs_35)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_34)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (_GEN_259)
            dinReg <= tmp_5;
          else if (cs_32)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_258)
            dinReg <= tmp_4;
          else
            dinReg <= _GEN_257;
        end
        else if (gameIsDFeveron) begin
          if (_GEN_5)
            dinReg <= inputPort1;
          else if (_GEN_4)
            dinReg <= inputPort0;
          else if (cs_23)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_22)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_246)
            dinReg <= 16'h0;
          else if (_GEN_1)
            dinReg <= _GEN_244;
          else if (_GEN_237)
            dinReg <= 16'h0;
          else if (cs_16)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_236)
            dinReg <= tmp_3;
          else if (cs_14)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_229)
            dinReg <= tmp_2;
          else if (cs_12)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_11)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_228)
            dinReg <= tmp_1;
          else if (cs_9)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_223)
            dinReg <= tmp;
          else if (cs_7)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_6)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_5)
            dinReg <= _spriteRam_io_portA_dout;
          else
            dinReg <= _GEN_222;
        end
        else if (_GEN_218)
          dinReg <= 16'h0;
      end
      else if (gameIsEsprade) begin
        if (_GEN_117)
          dinReg <= inputDefaultP2;
        else if (_GEN_116)
          dinReg <= inputDefaultP1;
        else if (cs_109)
          dinReg <= _paletteRam_io_portA_dout;
        else if (cs_108)
          dinReg <= _layerRegs_2_io_mem_dout;
        else if (cs_107)
          dinReg <= _layerRegs_1_io_mem_dout;
        else if (cs_106)
          dinReg <= _layerRegs_0_io_mem_dout;
        else if (_GEN_351)
          dinReg <= 16'h0;
        else if (_GEN_106)
          dinReg <= _GEN_350;
        else if (_GEN_336)
          dinReg <= tmp_17;
        else if (cs_99)
          dinReg <= _vram8x8_2_io_portA_dout;
        else if (_GEN_335)
          dinReg <= tmp_16;
        else if (cs_97)
          dinReg <= _lineRam_2_io_portA_dout;
        else if (cs_96)
          dinReg <= _vram16x16_2_io_portA_dout;
        else if (_GEN_334)
          dinReg <= tmp_15;
        else if (cs_94)
          dinReg <= _vram8x8_1_io_portA_dout;
        else if (_GEN_333)
          dinReg <= tmp_14;
        else if (cs_92)
          dinReg <= _lineRam_1_io_portA_dout;
        else if (cs_91)
          dinReg <= _vram16x16_1_io_portA_dout;
        else if (_GEN_328)
          dinReg <= tmp_13;
        else if (cs_89)
          dinReg <= _vram8x8_0_io_portA_dout;
        else if (_GEN_327)
          dinReg <= tmp_12;
        else if (cs_87)
          dinReg <= _lineRam_0_io_portA_dout;
        else if (cs_86)
          dinReg <= _vram16x16_0_io_portA_dout;
        else if (cs_85)
          dinReg <= _spriteRam_io_portA_dout;
        else if (cs_84)
          dinReg <= io_soundCtrl_ymz_dout;
        else if (cs_83)
          dinReg <= _mainRam_io_dout;
        else if (_GEN_85)
          dinReg <= io_progRom_dout;
        else if (gameIsDoDonPachi) begin
          if (_GEN_77)
            dinReg <= inputPort1;
          else if (_GEN_76)
            dinReg <= inputPort0;
          else if (cs_78)
            dinReg <= _paletteRam_io_portA_dout;
          else if (cs_77)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_76)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_75)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_312)
            dinReg <= 16'h0;
          else if (_GEN_67)
            dinReg <= _GEN_310;
          else if (cs_70)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_298)
            dinReg <= tmp_11;
          else if (cs_68)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_297)
            dinReg <= tmp_10;
          else if (cs_66)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_65)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_296)
            dinReg <= tmp_9;
          else if (cs_62)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_295)
            dinReg <= tmp_8;
          else if (cs_60)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_59)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_58)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_57)
            dinReg <= io_soundCtrl_ymz_dout;
          else if (cs_56)
            dinReg <= _mainRam_io_dout;
          else
            dinReg <= _GEN_289;
        end
        else if (gameIsDonPachi) begin
          if (_GEN_38)
            dinReg <= inputPort1;
          else if (_GEN_37)
            dinReg <= inputPort0;
          else if (cs_50)
            dinReg <= io_soundCtrl_oki_1_dout;
          else if (cs_49)
            dinReg <= io_soundCtrl_oki_0_dout;
          else if (cs_48)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_280)
            dinReg <= 16'h0;
          else if (_GEN_30)
            dinReg <= _GEN_278;
          else if (cs_43)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_42)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (cs_41)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_40)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_39)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_265)
            dinReg <= tmp_7;
          else if (cs_37)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_264)
            dinReg <= tmp_6;
          else if (cs_35)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_34)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (_GEN_259)
            dinReg <= tmp_5;
          else if (cs_32)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_258)
            dinReg <= tmp_4;
          else if (cs_30)
            dinReg <= _lineRam_1_io_portA_dout;
          else
            dinReg <= _GEN_256;
        end
        else if (gameIsDFeveron) begin
          if (_GEN_5)
            dinReg <= inputPort1;
          else if (_GEN_4)
            dinReg <= inputPort0;
          else if (cs_23)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_22)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_246)
            dinReg <= 16'h0;
          else if (_GEN_1)
            dinReg <= _GEN_244;
          else if (_GEN_237)
            dinReg <= 16'h0;
          else if (cs_16)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_236)
            dinReg <= tmp_3;
          else if (cs_14)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_229)
            dinReg <= tmp_2;
          else if (cs_12)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_11)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_228)
            dinReg <= tmp_1;
          else if (cs_9)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_223)
            dinReg <= tmp;
          else if (cs_7)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_6)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_5)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_4)
            dinReg <= io_soundCtrl_ymz_dout;
          else
            dinReg <= _GEN_221;
        end
        else if (_GEN_218)
          dinReg <= 16'h0;
      end
      else if (gameIsDoDonPachi) begin
        if (_GEN_77)
          dinReg <= inputDefaultP2;
        else if (_GEN_76)
          dinReg <= inputDefaultP1;
        else if (cs_78)
          dinReg <= _paletteRam_io_portA_dout;
        else if (cs_77)
          dinReg <= _layerRegs_2_io_mem_dout;
        else if (cs_76)
          dinReg <= _layerRegs_1_io_mem_dout;
        else if (cs_75)
          dinReg <= _layerRegs_0_io_mem_dout;
        else if (_GEN_312)
          dinReg <= 16'h0;
        else if (_GEN_67)
          dinReg <= _GEN_310;
        else if (cs_70)
          dinReg <= _vram8x8_2_io_portA_dout;
        else if (_GEN_298)
          dinReg <= tmp_11;
        else if (cs_68)
          dinReg <= _vram8x8_1_io_portA_dout;
        else if (_GEN_297)
          dinReg <= tmp_10;
        else if (cs_66)
          dinReg <= _lineRam_1_io_portA_dout;
        else if (cs_65)
          dinReg <= _vram16x16_1_io_portA_dout;
        else if (_GEN_296)
          dinReg <= tmp_9;
        else if (cs_62)
          dinReg <= _vram8x8_0_io_portA_dout;
        else if (_GEN_295)
          dinReg <= tmp_8;
        else if (cs_60)
          dinReg <= _lineRam_0_io_portA_dout;
        else if (cs_59)
          dinReg <= _vram16x16_0_io_portA_dout;
        else if (cs_58)
          dinReg <= _spriteRam_io_portA_dout;
        else if (cs_57)
          dinReg <= io_soundCtrl_ymz_dout;
        else if (cs_56)
          dinReg <= _mainRam_io_dout;
        else if (_GEN_45)
          dinReg <= io_progRom_dout;
        else if (gameIsDonPachi) begin
          if (_GEN_38)
            dinReg <= inputPort1;
          else if (_GEN_37)
            dinReg <= inputPort0;
          else if (cs_50)
            dinReg <= io_soundCtrl_oki_1_dout;
          else if (cs_49)
            dinReg <= io_soundCtrl_oki_0_dout;
          else if (cs_48)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_280)
            dinReg <= 16'h0;
          else if (_GEN_30)
            dinReg <= _GEN_278;
          else if (cs_43)
            dinReg <= _layerRegs_2_io_mem_dout;
          else if (cs_42)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (cs_41)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_40)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_39)
            dinReg <= _vram8x8_2_io_portA_dout;
          else if (_GEN_265)
            dinReg <= tmp_7;
          else if (cs_37)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_264)
            dinReg <= tmp_6;
          else if (cs_35)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_34)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (_GEN_259)
            dinReg <= tmp_5;
          else if (cs_32)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_258)
            dinReg <= tmp_4;
          else if (cs_30)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_29)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (cs_28)
            dinReg <= _mainRam_io_dout;
          else if (_GEN_11)
            dinReg <= io_progRom_dout;
          else if (gameIsDFeveron)
            dinReg <= _GEN_251;
          else
            dinReg <= _GEN_219;
        end
        else if (gameIsDFeveron) begin
          if (_GEN_5)
            dinReg <= inputPort1;
          else if (_GEN_4)
            dinReg <= inputPort0;
          else if (cs_23)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_22)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_246)
            dinReg <= 16'h0;
          else if (_GEN_1)
            dinReg <= _GEN_244;
          else if (_GEN_237)
            dinReg <= 16'h0;
          else if (cs_16)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_236)
            dinReg <= tmp_3;
          else if (cs_14)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_229)
            dinReg <= tmp_2;
          else if (cs_12)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_11)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_228)
            dinReg <= tmp_1;
          else if (cs_9)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_223)
            dinReg <= tmp;
          else if (cs_7)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_6)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_5)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_4)
            dinReg <= io_soundCtrl_ymz_dout;
          else if (cs_3)
            dinReg <= _mainRam_io_dout;
          else if (_GEN_0)
            dinReg <= io_progRom_dout;
          else if (_GEN_218)
            dinReg <= 16'h0;
        end
        else if (_GEN_218)
          dinReg <= 16'h0;
      end
      else if (gameIsDonPachi) begin
        if (_GEN_38)
          dinReg <= inputDefaultP2;
        else if (_GEN_37)
          dinReg <= inputDefaultP1;
        else if (cs_50)
          dinReg <= io_soundCtrl_oki_1_dout;
        else if (cs_49)
          dinReg <= io_soundCtrl_oki_0_dout;
        else if (cs_48)
          dinReg <= _paletteRam_io_portA_dout;
        else if (_GEN_280)
          dinReg <= 16'h0;
        else if (_GEN_30)
          dinReg <= _GEN_278;
        else if (cs_43)
          dinReg <= _layerRegs_2_io_mem_dout;
        else if (cs_42)
          dinReg <= _layerRegs_0_io_mem_dout;
        else if (cs_41)
          dinReg <= _layerRegs_1_io_mem_dout;
        else if (cs_40)
          dinReg <= _spriteRam_io_portA_dout;
        else if (cs_39)
          dinReg <= _vram8x8_2_io_portA_dout;
        else if (_GEN_265)
          dinReg <= tmp_7;
        else if (cs_37)
          dinReg <= _vram8x8_0_io_portA_dout;
        else if (_GEN_264)
          dinReg <= tmp_6;
        else if (cs_35)
          dinReg <= _lineRam_0_io_portA_dout;
        else if (cs_34)
          dinReg <= _vram16x16_0_io_portA_dout;
        else if (_GEN_259)
          dinReg <= tmp_5;
        else if (cs_32)
          dinReg <= _vram8x8_1_io_portA_dout;
        else if (_GEN_258)
          dinReg <= tmp_4;
        else if (cs_30)
          dinReg <= _lineRam_1_io_portA_dout;
        else if (cs_29)
          dinReg <= _vram16x16_1_io_portA_dout;
        else if (cs_28)
          dinReg <= _mainRam_io_dout;
        else if (_GEN_11)
          dinReg <= io_progRom_dout;
        else if (gameIsDFeveron) begin
          if (_GEN_5)
            dinReg <= inputPort1;
          else if (_GEN_4)
            dinReg <= inputPort0;
          else if (cs_23)
            dinReg <= _layerRegs_1_io_mem_dout;
          else if (cs_22)
            dinReg <= _layerRegs_0_io_mem_dout;
          else if (_GEN_246)
            dinReg <= 16'h0;
          else if (_GEN_1)
            dinReg <= _GEN_244;
          else if (_GEN_237)
            dinReg <= 16'h0;
          else if (cs_16)
            dinReg <= _paletteRam_io_portA_dout;
          else if (_GEN_236)
            dinReg <= tmp_3;
          else if (cs_14)
            dinReg <= _vram8x8_1_io_portA_dout;
          else if (_GEN_229)
            dinReg <= tmp_2;
          else if (cs_12)
            dinReg <= _lineRam_1_io_portA_dout;
          else if (cs_11)
            dinReg <= _vram16x16_1_io_portA_dout;
          else if (_GEN_228)
            dinReg <= tmp_1;
          else if (cs_9)
            dinReg <= _vram8x8_0_io_portA_dout;
          else if (_GEN_223)
            dinReg <= tmp;
          else if (cs_7)
            dinReg <= _lineRam_0_io_portA_dout;
          else if (cs_6)
            dinReg <= _vram16x16_0_io_portA_dout;
          else if (cs_5)
            dinReg <= _spriteRam_io_portA_dout;
          else if (cs_4)
            dinReg <= io_soundCtrl_ymz_dout;
          else if (cs_3)
            dinReg <= _mainRam_io_dout;
          else if (_GEN_0)
            dinReg <= io_progRom_dout;
          else if (_GEN_218)
            dinReg <= 16'h0;
        end
        else if (_GEN_218)
          dinReg <= 16'h0;
      end
      else if (gameIsDFeveron) begin
        if (_GEN_5)
          dinReg <= inputDefaultP2;
        else if (_GEN_4)
          dinReg <= inputPort0;
        else if (cs_23)
          dinReg <= _layerRegs_1_io_mem_dout;
        else if (cs_22)
          dinReg <= _layerRegs_0_io_mem_dout;
        else if (_GEN_246)
          dinReg <= 16'h0;
        else if (_GEN_1)
          dinReg <= _GEN_244;
        else if (_GEN_237)
          dinReg <= 16'h0;
        else if (cs_16)
          dinReg <= _paletteRam_io_portA_dout;
        else if (_GEN_236)
          dinReg <= tmp_3;
        else if (cs_14)
          dinReg <= _vram8x8_1_io_portA_dout;
        else if (_GEN_229)
          dinReg <= tmp_2;
        else if (cs_12)
          dinReg <= _lineRam_1_io_portA_dout;
        else if (cs_11)
          dinReg <= _vram16x16_1_io_portA_dout;
        else if (_GEN_228)
          dinReg <= tmp_1;
        else if (cs_9)
          dinReg <= _vram8x8_0_io_portA_dout;
        else if (_GEN_223)
          dinReg <= tmp;
        else if (cs_7)
          dinReg <= _lineRam_0_io_portA_dout;
        else if (cs_6)
          dinReg <= _vram16x16_0_io_portA_dout;
        else if (cs_5)
          dinReg <= _spriteRam_io_portA_dout;
        else if (cs_4)
          dinReg <= io_soundCtrl_ymz_dout;
        else if (cs_3)
          dinReg <= _mainRam_io_dout;
        else if (_GEN_0)
          dinReg <= io_progRom_dout;
        else if (_GEN_218)
          dinReg <= 16'h0;
      end
      else if (_GEN_218)
        dinReg <= 16'h0;
      dtackReg <=
        gameIsPwrInst2
          ? pwrinst2Dtack
        : gameIsMazinger
          ? mazingerDtack
          : gameIsUopoko
          ? cs_231 & ~_cpu_io_rw | _cpu_io_as
            & (_GEN_423 | _GEN_422 | cs_228 | cs_227 | cs_226 | _GEN_209 | cs_224
               & ~_cpu_io_rw | _GEN_420 | cs_222 | cs_221 | cs_220 | cs_219 | cs_218
               | cs_217 | cs_216 | cs_215 | _GEN_419 | _GEN_503)
          : _GEN_506;
    end
  end // always @(posedge)
  always @(posedge io_videoClock) begin
    io_gpuMem_layer_0_regs_r_tileSize <= _layerRegs_0_io_regs_1[13];
    io_gpuMem_layer_0_regs_r_enable <= ~(_layerRegs_0_io_regs_2[4]);
    io_gpuMem_layer_0_regs_r_flipX <= ~(_layerRegs_0_io_regs_0[15]);
    io_gpuMem_layer_0_regs_r_flipY <= ~(_layerRegs_0_io_regs_1[15]);
    io_gpuMem_layer_0_regs_r_rowScrollEnable <= _layerRegs_0_io_regs_0[14];
    io_gpuMem_layer_0_regs_r_rowSelectEnable <= _layerRegs_0_io_regs_1[14];
    io_gpuMem_layer_0_regs_r_scroll_x <= _layerRegs_0_io_regs_0[8:0];
    io_gpuMem_layer_0_regs_r_scroll_y <= _layerRegs_0_io_regs_1[8:0];
    io_gpuMem_layer_0_regs_r_1_tileSize <= io_gpuMem_layer_0_regs_r_tileSize;
    io_gpuMem_layer_0_regs_r_1_enable <= io_gpuMem_layer_0_regs_r_enable;
    io_gpuMem_layer_0_regs_r_1_flipX <= io_gpuMem_layer_0_regs_r_flipX;
    io_gpuMem_layer_0_regs_r_1_flipY <= io_gpuMem_layer_0_regs_r_flipY;
    io_gpuMem_layer_0_regs_r_1_rowScrollEnable <=
      io_gpuMem_layer_0_regs_r_rowScrollEnable;
    io_gpuMem_layer_0_regs_r_1_rowSelectEnable <=
      io_gpuMem_layer_0_regs_r_rowSelectEnable;
    io_gpuMem_layer_0_regs_r_1_scroll_x <= io_gpuMem_layer_0_regs_r_scroll_x;
    io_gpuMem_layer_0_regs_r_1_scroll_y <= io_gpuMem_layer_0_regs_r_scroll_y;
    io_gpuMem_layer_1_regs_r_tileSize <= _layerRegs_1_io_regs_1[13];
    io_gpuMem_layer_1_regs_r_enable <= ~(_layerRegs_1_io_regs_2[4]);
    io_gpuMem_layer_1_regs_r_flipX <= ~(_layerRegs_1_io_regs_0[15]);
    io_gpuMem_layer_1_regs_r_flipY <= ~(_layerRegs_1_io_regs_1[15]);
    io_gpuMem_layer_1_regs_r_rowScrollEnable <= _layerRegs_1_io_regs_0[14];
    io_gpuMem_layer_1_regs_r_rowSelectEnable <= _layerRegs_1_io_regs_1[14];
    io_gpuMem_layer_1_regs_r_scroll_x <= _layerRegs_1_io_regs_0[8:0];
    io_gpuMem_layer_1_regs_r_scroll_y <= _layerRegs_1_io_regs_1[8:0];
    io_gpuMem_layer_1_regs_r_1_tileSize <= io_gpuMem_layer_1_regs_r_tileSize;
    io_gpuMem_layer_1_regs_r_1_enable <= io_gpuMem_layer_1_regs_r_enable;
    io_gpuMem_layer_1_regs_r_1_flipX <= io_gpuMem_layer_1_regs_r_flipX;
    io_gpuMem_layer_1_regs_r_1_flipY <= io_gpuMem_layer_1_regs_r_flipY;
    io_gpuMem_layer_1_regs_r_1_rowScrollEnable <=
      io_gpuMem_layer_1_regs_r_rowScrollEnable;
    io_gpuMem_layer_1_regs_r_1_rowSelectEnable <=
      io_gpuMem_layer_1_regs_r_rowSelectEnable;
    io_gpuMem_layer_1_regs_r_1_scroll_x <= io_gpuMem_layer_1_regs_r_scroll_x;
    io_gpuMem_layer_1_regs_r_1_scroll_y <= io_gpuMem_layer_1_regs_r_scroll_y;
    io_gpuMem_layer_2_regs_r_tileSize <= gameIsPwrInst2 ? 1'b0 : _layerRegs_2_io_regs_1[13];
    io_gpuMem_layer_2_regs_r_enable <= ~(_layerRegs_2_io_regs_2[4]);
    io_gpuMem_layer_2_regs_r_flipX <= ~(_layerRegs_2_io_regs_0[15]);
    io_gpuMem_layer_2_regs_r_flipY <= ~(_layerRegs_2_io_regs_1[15]);
    io_gpuMem_layer_2_regs_r_rowScrollEnable <= _layerRegs_2_io_regs_0[14];
    io_gpuMem_layer_2_regs_r_rowSelectEnable <= _layerRegs_2_io_regs_1[14];
    io_gpuMem_layer_2_regs_r_scroll_x <= _layerRegs_2_io_regs_0[8:0];
    io_gpuMem_layer_2_regs_r_scroll_y <= _layerRegs_2_io_regs_1[8:0];
    io_gpuMem_layer_2_regs_r_1_tileSize <= io_gpuMem_layer_2_regs_r_tileSize;
    io_gpuMem_layer_2_regs_r_1_enable <= io_gpuMem_layer_2_regs_r_enable;
    io_gpuMem_layer_2_regs_r_1_flipX <= io_gpuMem_layer_2_regs_r_flipX;
    io_gpuMem_layer_2_regs_r_1_flipY <= io_gpuMem_layer_2_regs_r_flipY;
    io_gpuMem_layer_2_regs_r_1_rowScrollEnable <=
      io_gpuMem_layer_2_regs_r_rowScrollEnable;
    io_gpuMem_layer_2_regs_r_1_rowSelectEnable <=
      io_gpuMem_layer_2_regs_r_rowSelectEnable;
    io_gpuMem_layer_2_regs_r_1_scroll_x <= io_gpuMem_layer_2_regs_r_scroll_x;
    io_gpuMem_layer_2_regs_r_1_scroll_y <= io_gpuMem_layer_2_regs_r_scroll_y;
    io_gpuMem_pwrinst2_layer_2_regs_r_tileSize <= pwrinst2Layer2Regs1[13];
    io_gpuMem_pwrinst2_layer_2_regs_r_enable <= gameIsPwrInst2 & ~pwrinst2Layer2Regs2[4];
    io_gpuMem_pwrinst2_layer_2_regs_r_flipX <= ~pwrinst2Layer2Regs0[15];
    io_gpuMem_pwrinst2_layer_2_regs_r_flipY <= ~pwrinst2Layer2Regs1[15];
    io_gpuMem_pwrinst2_layer_2_regs_r_rowScrollEnable <= pwrinst2Layer2Regs0[14];
    io_gpuMem_pwrinst2_layer_2_regs_r_rowSelectEnable <= pwrinst2Layer2Regs1[14];
    io_gpuMem_pwrinst2_layer_2_regs_r_scroll_x <= pwrinst2Layer2Regs0[8:0];
    io_gpuMem_pwrinst2_layer_2_regs_r_scroll_y <= pwrinst2Layer2Regs1[8:0];
    io_gpuMem_pwrinst2_layer_2_regs_r_1_tileSize <=
      io_gpuMem_pwrinst2_layer_2_regs_r_tileSize;
    io_gpuMem_pwrinst2_layer_2_regs_r_1_enable <=
      io_gpuMem_pwrinst2_layer_2_regs_r_enable;
    io_gpuMem_pwrinst2_layer_2_regs_r_1_flipX <=
      io_gpuMem_pwrinst2_layer_2_regs_r_flipX;
    io_gpuMem_pwrinst2_layer_2_regs_r_1_flipY <=
      io_gpuMem_pwrinst2_layer_2_regs_r_flipY;
    io_gpuMem_pwrinst2_layer_2_regs_r_1_rowScrollEnable <=
      io_gpuMem_pwrinst2_layer_2_regs_r_rowScrollEnable;
    io_gpuMem_pwrinst2_layer_2_regs_r_1_rowSelectEnable <=
      io_gpuMem_pwrinst2_layer_2_regs_r_rowSelectEnable;
    io_gpuMem_pwrinst2_layer_2_regs_r_1_scroll_x <=
      io_gpuMem_pwrinst2_layer_2_regs_r_scroll_x;
    io_gpuMem_pwrinst2_layer_2_regs_r_1_scroll_y <=
      io_gpuMem_pwrinst2_layer_2_regs_r_scroll_y;
  end // always @(posedge)
  assign _cpu_io_vpa = _cpu_io_as & (&_cpu_io_fc);
  assign _cpu_io_ipl = {2'h0, videoIrq | io_soundCtrl_irq | unknownIrq};
  CaveMain68kCpu cpu (
    .clock    (clock),
    .reset    (reset),
    .io_halt  (pauseActive),
    .io_as    (_cpu_io_as),
    .io_rw    (_cpu_io_rw),
    .io_uds   (_cpu_io_uds),
    .io_lds   (_cpu_io_lds),
    .io_dtack (dtackReg),
    .io_vpa   (_cpu_io_vpa),
    .io_ipl   (_cpu_io_ipl),
    .io_fc    (_cpu_io_fc),
    .io_addr  (_cpu_io_addr),
    .io_din   (dinReg),
    .io_dout  (_cpu_io_dout)
  );
  EEPROM eeprom (
    .clock         (clock),
    .reset         (reset),
    .io_mem_rd     (io_eeprom_rd),
    .io_mem_wr     (io_eeprom_wr),
    .io_mem_addr   (io_eeprom_addr),
    .io_mem_din    (io_eeprom_din),
    .io_mem_dout   (io_eeprom_dout),
    .io_mem_wait_n (io_eeprom_wait_n),
    .io_mem_valid  (io_eeprom_valid),
    .io_serial_cs  (eepromSerialCs),
    .io_serial_sck (eepromSerialSck),
    .io_serial_sdi (eepromSerialSdi),
    .io_serial_sdo (_eeprom_io_serial_sdo)
  );
  assign _mainRam_io_addr = _cpu_io_addr[14:0];
  CaveSinglePortRam #(
    .ADDR_WIDTH  (15),
    .DATA_WIDTH  (16),
    .DEPTH       (0),
    .MASK_ENABLE (1)
  ) mainRam (
    .clock (clock),
    .rd    (mainRam_io_rd),
    .wr    (mainRam_io_wr),
    .addr  (_mainRam_io_addr),
    .mask  (mainRam_io_mask),
    .din   (_cpu_io_dout),
    .dout  (_mainRam_io_dout)
  );
  assign _pwrinst2SpriteExtraRam_addr = _cpu_io_addr[14:0];
  CaveSinglePortRam #(
    .ADDR_WIDTH  (15),
    .DATA_WIDTH  (16),
    .DEPTH       (0),
    .MASK_ENABLE (1)
  ) pwrinst2SpriteExtraRam (
    .clock (clock),
    .rd    (pwrinst2SpriteExtraRamSelect & readStrobe),
    .wr    (pwrinst2SpriteExtraRamSelect & writeStrobe),
    .addr  (_pwrinst2SpriteExtraRam_addr),
    .mask  (mainRam_io_mask),
    .din   (_cpu_io_dout),
    .dout  (_pwrinst2SpriteExtraRam_dout)
  );
  assign _spriteRam_io_portA_addr = _cpu_io_addr[14:0];
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (15),
    .ADDR_WIDTH_B (12),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (128),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) spriteRam (
    .clock_a (clock),
    .rd_a    (spriteRam_io_portA_rd),
    .wr_a    (spriteRam_io_portA_wr),
    .addr_a  (_spriteRam_io_portA_addr),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_spriteRam_io_portA_dout),
    .clock_b (io_spriteClock),
    .rd_b    (io_gpuMem_sprite_vram_rd),
    .addr_b  (io_gpuMem_sprite_vram_addr),
    .dout_b  (io_gpuMem_sprite_vram_dout)
  );
  assign _vram8x8_0_io_portA_addr = _cpu_io_addr[12:0];
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (13),
    .ADDR_WIDTH_B (12),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) vram8x8_0 (
    .clock_a (clock),
    .rd_a    (vram8x8_0_io_portA_rd),
    .wr_a    (vram8x8_0_io_portA_wr),
    .addr_a  (_vram8x8_0_io_portA_addr),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_vram8x8_0_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_layer_0_vram8x8_addr),
    .dout_b  (io_gpuMem_layer_0_vram8x8_dout)
  );
  assign _vram8x8_1_io_portA_addr = _cpu_io_addr[12:0];
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (13),
    .ADDR_WIDTH_B (12),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) vram8x8_1 (
    .clock_a (clock),
    .rd_a    (vram8x8_1_io_portA_rd),
    .wr_a    (vram8x8_1_io_portA_wr),
    .addr_a  (_vram8x8_1_io_portA_addr),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_vram8x8_1_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_layer_1_vram8x8_addr),
    .dout_b  (io_gpuMem_layer_1_vram8x8_dout)
  );
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (13),
    .ADDR_WIDTH_B (12),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) vram8x8_2 (
    .clock_a (clock),
    .rd_a    (vram8x8_2_io_portA_rd),
    .wr_a    (vram8x8_2_io_portA_wr),
    .addr_a  (vram8x8_2_io_portA_addr),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_vram8x8_2_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_layer_2_vram8x8_addr),
    .dout_b  (io_gpuMem_layer_2_vram8x8_dout)
  );
  assign _vram16x16_0_io_portA_addr = _cpu_io_addr[10:0];
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (11),
    .ADDR_WIDTH_B (10),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) vram16x16_0 (
    .clock_a (clock),
    .rd_a    (vram16x16_0_io_portA_rd),
    .wr_a    (vram16x16_0_io_portA_wr),
    .addr_a  (_vram16x16_0_io_portA_addr),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_vram16x16_0_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_layer_0_vram16x16_addr),
    .dout_b  (io_gpuMem_layer_0_vram16x16_dout)
  );
  assign _vram16x16_1_io_portA_addr = _cpu_io_addr[10:0];
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (11),
    .ADDR_WIDTH_B (10),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) vram16x16_1 (
    .clock_a (clock),
    .rd_a    (vram16x16_1_io_portA_rd),
    .wr_a    (vram16x16_1_io_portA_wr),
    .addr_a  (_vram16x16_1_io_portA_addr),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_vram16x16_1_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_layer_1_vram16x16_addr),
    .dout_b  (io_gpuMem_layer_1_vram16x16_dout)
  );
  assign _vram16x16_2_io_portA_addr = _cpu_io_addr[10:0];
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (11),
    .ADDR_WIDTH_B (10),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) vram16x16_2 (
    .clock_a (clock),
    .rd_a    (vram16x16_2_io_portA_rd),
    .wr_a    (vram16x16_2_io_portA_wr),
    .addr_a  (_vram16x16_2_io_portA_addr),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_vram16x16_2_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_layer_2_vram16x16_addr),
    .dout_b  (io_gpuMem_layer_2_vram16x16_dout)
  );
  assign _lineRam_0_io_portA_addr = _cpu_io_addr[9:0];
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (10),
    .ADDR_WIDTH_B (9),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) lineRam_0 (
    .clock_a (clock),
    .rd_a    (lineRam_0_io_portA_rd),
    .wr_a    (lineRam_0_io_portA_wr),
    .addr_a  (_lineRam_0_io_portA_addr),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_lineRam_0_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_layer_0_lineRam_addr),
    .dout_b  (io_gpuMem_layer_0_lineRam_dout)
  );
  assign _lineRam_1_io_portA_addr = _cpu_io_addr[9:0];
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (10),
    .ADDR_WIDTH_B (9),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) lineRam_1 (
    .clock_a (clock),
    .rd_a    (lineRam_1_io_portA_rd),
    .wr_a    (lineRam_1_io_portA_wr),
    .addr_a  (_lineRam_1_io_portA_addr),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_lineRam_1_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_layer_1_lineRam_addr),
    .dout_b  (io_gpuMem_layer_1_lineRam_dout)
  );
  assign _lineRam_2_io_portA_addr = _cpu_io_addr[9:0];
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (10),
    .ADDR_WIDTH_B (9),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) lineRam_2 (
    .clock_a (clock),
    .rd_a    (lineRam_2_io_portA_rd),
    .wr_a    (lineRam_2_io_portA_wr),
    .addr_a  (_lineRam_2_io_portA_addr),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_lineRam_2_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_layer_2_lineRam_addr),
    .dout_b  (io_gpuMem_layer_2_lineRam_dout)
  );
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (13),
    .ADDR_WIDTH_B (12),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) pwrinst2Layer2Vram8 (
    .clock_a (clock),
    .rd_a    (pwrinst2Layer2Vram8Select & readStrobe),
    .wr_a    (pwrinst2Layer2Vram8Select & writeStrobe),
    .addr_a  (_cpu_io_addr[12:0]),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_pwrinst2Layer2Vram8_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_pwrinst2_layer_2_vram8x8_addr),
    .dout_b  (io_gpuMem_pwrinst2_layer_2_vram8x8_dout)
  );
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (11),
    .ADDR_WIDTH_B (10),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) pwrinst2Layer2Vram16 (
    .clock_a (clock),
    .rd_a    (pwrinst2Layer2Vram16Select & readStrobe),
    .wr_a    (pwrinst2Layer2Vram16Select & writeStrobe),
    .addr_a  (_cpu_io_addr[10:0]),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_pwrinst2Layer2Vram16_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_pwrinst2_layer_2_vram16x16_addr),
    .dout_b  (io_gpuMem_pwrinst2_layer_2_vram16x16_dout)
  );
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (10),
    .ADDR_WIDTH_B (9),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (32),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) pwrinst2Layer2LineRam (
    .clock_a (clock),
    .rd_a    (pwrinst2Layer2LineSelect & readStrobe),
    .wr_a    (pwrinst2Layer2LineSelect & writeStrobe),
    .addr_a  (_cpu_io_addr[9:0]),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_pwrinst2Layer2LineRam_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_pwrinst2_layer_2_lineRam_addr),
    .dout_b  (io_gpuMem_pwrinst2_layer_2_lineRam_dout)
  );
  CaveTrueDualPortRam #(
    .ADDR_WIDTH_A (15),
    .ADDR_WIDTH_B (15),
    .DATA_WIDTH_A (16),
    .DATA_WIDTH_B (16),
    .DEPTH_A      (0),
    .DEPTH_B      (0),
    .MASK_ENABLE  (1)
  ) paletteRam (
    .clock_a (clock),
    .rd_a    (paletteRam_io_portA_rd),
    .wr_a    (paletteRam_io_portA_wr),
    .addr_a  (paletteRam_io_portA_addr),
    .mask_a  (mainRam_io_mask),
    .din_a   (_cpu_io_dout),
    .dout_a  (_paletteRam_io_portA_dout),
    .clock_b (io_videoClock),
    .rd_b    (1'b1),
    .addr_b  (io_gpuMem_paletteRam_addr),
    .dout_b  (io_gpuMem_paletteRam_dout)
  );
  assign _layerRegs_0_io_mem_addr = _cpu_io_addr[1:0];
  CaveLayerRegisterFile layerRegs_0 (
    .clock       (clock),
    .io_mem_wr   (layerRegs_0_io_mem_wr),
    .io_mem_addr (_layerRegs_0_io_mem_addr),
    .io_mem_mask (mainRam_io_mask),
    .io_mem_din  (layerRegsMemDin),
    .io_mem_dout (_layerRegs_0_io_mem_dout),
    .io_regs_0   (_layerRegs_0_io_regs_0),
    .io_regs_1   (_layerRegs_0_io_regs_1),
    .io_regs_2   (_layerRegs_0_io_regs_2)
  );
  assign _layerRegs_1_io_mem_addr = _cpu_io_addr[1:0];
  CaveLayerRegisterFile layerRegs_1 (
    .clock       (clock),
    .io_mem_wr   (layerRegs_1_io_mem_wr),
    .io_mem_addr (_layerRegs_1_io_mem_addr),
    .io_mem_mask (mainRam_io_mask),
    .io_mem_din  (layerRegsMemDin),
    .io_mem_dout (_layerRegs_1_io_mem_dout),
    .io_regs_0   (_layerRegs_1_io_regs_0),
    .io_regs_1   (_layerRegs_1_io_regs_1),
    .io_regs_2   (_layerRegs_1_io_regs_2)
  );
  assign _layerRegs_2_io_mem_addr = _cpu_io_addr[1:0];
  CaveLayerRegisterFile layerRegs_2 (
    .clock       (clock),
    .io_mem_wr   (layerRegs_2_io_mem_wr),
    .io_mem_addr (_layerRegs_2_io_mem_addr),
    .io_mem_mask (mainRam_io_mask),
    .io_mem_din  (layerRegsMemDin),
    .io_mem_dout (_layerRegs_2_io_mem_dout),
    .io_regs_0   (_layerRegs_2_io_regs_0),
    .io_regs_1   (_layerRegs_2_io_regs_1),
    .io_regs_2   (_layerRegs_2_io_regs_2)
  );
  assign _spriteRegs_io_mem_mask = {_cpu_io_uds, _cpu_io_lds};
  CaveControlRegisterFile spriteRegs (
    .clock       (clock),
    .io_mem_wr   (spriteRegs_io_mem_wr),
    .io_mem_addr (spriteRegs_io_mem_addr),
    .io_mem_mask (_spriteRegs_io_mem_mask),
    .io_mem_din  (_cpu_io_dout),
    .io_regs_0   (_spriteRegs_io_regs_0),
    .io_regs_1   (_spriteRegs_io_regs_1),
    .io_regs_2   (/* unused */),
    .io_regs_3   (/* unused */),
    .io_regs_4   (_spriteRegs_io_regs_4),
    .io_regs_5   (_spriteRegs_io_regs_5)
  );
  assign io_gpuMem_layer_0_regs_tileSize = io_gpuMem_layer_0_regs_r_1_tileSize;
  assign io_gpuMem_layer_0_regs_enable = io_gpuMem_layer_0_regs_r_1_enable;
  assign io_gpuMem_layer_0_regs_flipX = io_gpuMem_layer_0_regs_r_1_flipX;
  assign io_gpuMem_layer_0_regs_flipY = io_gpuMem_layer_0_regs_r_1_flipY;
  assign io_gpuMem_layer_0_regs_rowScrollEnable =
    io_gpuMem_layer_0_regs_r_1_rowScrollEnable;
  assign io_gpuMem_layer_0_regs_rowSelectEnable =
    io_gpuMem_layer_0_regs_r_1_rowSelectEnable;
  assign io_gpuMem_layer_0_regs_scroll_x = io_gpuMem_layer_0_regs_r_1_scroll_x;
  assign io_gpuMem_layer_0_regs_scroll_y = io_gpuMem_layer_0_regs_r_1_scroll_y;
  assign io_gpuMem_layer_1_regs_tileSize = io_gpuMem_layer_1_regs_r_1_tileSize;
  assign io_gpuMem_layer_1_regs_enable = io_gpuMem_layer_1_regs_r_1_enable;
  assign io_gpuMem_layer_1_regs_flipX = io_gpuMem_layer_1_regs_r_1_flipX;
  assign io_gpuMem_layer_1_regs_flipY = io_gpuMem_layer_1_regs_r_1_flipY;
  assign io_gpuMem_layer_1_regs_rowScrollEnable =
    io_gpuMem_layer_1_regs_r_1_rowScrollEnable;
  assign io_gpuMem_layer_1_regs_rowSelectEnable =
    io_gpuMem_layer_1_regs_r_1_rowSelectEnable;
  assign io_gpuMem_layer_1_regs_scroll_x = io_gpuMem_layer_1_regs_r_1_scroll_x;
  assign io_gpuMem_layer_1_regs_scroll_y = io_gpuMem_layer_1_regs_r_1_scroll_y;
  assign io_gpuMem_layer_2_regs_tileSize = io_gpuMem_layer_2_regs_r_1_tileSize;
  assign io_gpuMem_layer_2_regs_enable = io_gpuMem_layer_2_regs_r_1_enable;
  assign io_gpuMem_layer_2_regs_flipX = io_gpuMem_layer_2_regs_r_1_flipX;
  assign io_gpuMem_layer_2_regs_flipY = io_gpuMem_layer_2_regs_r_1_flipY;
  assign io_gpuMem_layer_2_regs_rowScrollEnable =
    io_gpuMem_layer_2_regs_r_1_rowScrollEnable;
  assign io_gpuMem_layer_2_regs_rowSelectEnable =
    io_gpuMem_layer_2_regs_r_1_rowSelectEnable;
  assign io_gpuMem_layer_2_regs_scroll_x = io_gpuMem_layer_2_regs_r_1_scroll_x;
  assign io_gpuMem_layer_2_regs_scroll_y = io_gpuMem_layer_2_regs_r_1_scroll_y;
  assign io_gpuMem_pwrinst2_layer_2_regs_tileSize =
    io_gpuMem_pwrinst2_layer_2_regs_r_1_tileSize;
  assign io_gpuMem_pwrinst2_layer_2_regs_enable =
    io_gpuMem_pwrinst2_layer_2_regs_r_1_enable;
  assign io_gpuMem_pwrinst2_layer_2_regs_flipX =
    io_gpuMem_pwrinst2_layer_2_regs_r_1_flipX;
  assign io_gpuMem_pwrinst2_layer_2_regs_flipY =
    io_gpuMem_pwrinst2_layer_2_regs_r_1_flipY;
  assign io_gpuMem_pwrinst2_layer_2_regs_rowScrollEnable =
    io_gpuMem_pwrinst2_layer_2_regs_r_1_rowScrollEnable;
  assign io_gpuMem_pwrinst2_layer_2_regs_rowSelectEnable =
    io_gpuMem_pwrinst2_layer_2_regs_r_1_rowSelectEnable;
  assign io_gpuMem_pwrinst2_layer_2_regs_scroll_x =
    io_gpuMem_pwrinst2_layer_2_regs_r_1_scroll_x;
  assign io_gpuMem_pwrinst2_layer_2_regs_scroll_y =
    io_gpuMem_pwrinst2_layer_2_regs_r_1_scroll_y;
  assign io_gpuMem_sprite_regs_offset_x = _spriteRegs_io_regs_0[8:0];
  assign io_gpuMem_sprite_regs_offset_y = _spriteRegs_io_regs_1[8:0];
  assign io_gpuMem_sprite_regs_bank = _spriteRegs_io_regs_4[1:0];
  assign io_gpuMem_sprite_regs_fixed = |(_spriteRegs_io_regs_5[13:12]);
  assign io_gpuMem_sprite_regs_hFlip = _spriteRegs_io_regs_0[15];
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  assign io_debug_pipeline = gameIsPwrInst2 ? pwrinst2DebugMilestones : 64'd0;
  assign io_debug_cpu = gameIsPwrInst2 ? pwrinst2DebugCpuBits : 64'd0;
  assign io_debug_writes = gameIsPwrInst2 ? pwrinst2DebugWriteBits : 64'd0;
  assign io_debug_data = gameIsPwrInst2 ? pwrinst2DebugDataBits : 64'd0;
  assign io_debug_live = gameIsPwrInst2 ? pwrinst2DebugLiveBits : 64'd0;
  assign io_debug_palette = gameIsPwrInst2 ? pwrinst2DebugPaletteBits : 64'd0;
`else
  assign io_debug_pipeline = 64'd0;
  assign io_debug_cpu = 64'd0;
  assign io_debug_writes = 64'd0;
  assign io_debug_data = 64'd0;
  assign io_debug_live = 64'd0;
  assign io_debug_palette = 64'd0;
`endif
  assign io_soundCtrl_oki_0_wr = gameIsDonPachi & cs_49 & writeStrobe;
  assign io_soundCtrl_oki_0_din = _cpu_io_dout;
  assign io_soundCtrl_oki_1_wr = gameIsDonPachi & cs_50 & writeStrobe;
  assign io_soundCtrl_oki_1_din = _cpu_io_dout;
  assign io_soundCtrl_nmk_wr = gameIsDonPachi & cs_51 & writeStrobe;
  assign io_soundCtrl_nmk_addr = _cpu_io_addr;
  assign io_soundCtrl_nmk_din = _cpu_io_dout;
  assign io_soundCtrl_ymz_rd = gameIsUopoko ? cs_216 & readStrobe : _GEN_181;
  assign io_soundCtrl_ymz_wr = gameIsUopoko ? cs_216 & writeStrobe : _GEN_182;
  assign io_soundCtrl_ymz_addr = _cpu_io_addr;
  assign io_soundCtrl_ymz_din = _cpu_io_dout;
  assign io_soundCtrl_req = pwrinst2SoundWrite;
  assign io_soundCtrl_data = _cpu_io_dout;
  assign io_soundCtrl_reply_rd = pwrinst2SoundAckReadStrobe & ~io_soundCtrl_reply_empty;
  assign io_progRom_rd =
    gameIsPwrInst2 ? pwrinst2ProgRomRead
      : gameIsMazinger ? mazingerProgRomRead
      : gameIsUopoko ? cs_214 & readStrobe : _GEN_189;
  assign io_progRom_addr =
    gameIsPwrInst2
      ? {pwrinst2ExtraRomRead, _cpu_io_addr[19:0], 1'b0}
      : (gameIsMazinger & mazingerExtraRomSelect)
        ? {2'b01, cpuByteAddr[18:0]}
        : {2'b00, _cpu_io_addr[18:0], 1'b0};
  assign io_spriteFrameBufferSwap =
    gameIsPwrInst2 ? videoVBlankRising
      : gameIsMazinger ? videoVBlankRising
      : gameIsUopoko ? _GEN_209 | _GEN_203 | _GEN_160 : _GEN_203 | _GEN_160;
endmodule
