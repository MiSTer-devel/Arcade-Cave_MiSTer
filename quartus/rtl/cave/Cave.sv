module Cave(
  input         clock,
  input         reset,
  input         cpuClock,
  input         cpuReset,
  input         videoClock,
  input         videoReset,
  input  [3:0]  options_offset_x,
  input  [3:0]  options_offset_y,
  input         options_rotate,
  input         options_compatibility,
  input         options_service,
  input         options_layer_0,
  input         options_layer_1,
  input         options_layer_2,
  input         options_sprite,
  input         options_flipVideo,
  input  [3:0]  options_gameIndex,
  input         player_0_up,
  input         player_0_down,
  input         player_0_left,
  input         player_0_right,
  input  [3:0]  player_0_buttons,
  input         player_0_start,
  input         player_0_coin,
  input         player_0_pause,
  input         player_1_up,
  input         player_1_down,
  input         player_1_left,
  input         player_1_right,
  input  [3:0]  player_1_buttons,
  input         player_1_start,
  input         player_1_coin,
  input         player_1_pause,
  input         ioctl_download,
  input         ioctl_upload,
  input         ioctl_rd,
  input         ioctl_wr,
  output        ioctl_wait_n,
  input  [7:0]  ioctl_index,
  input  [26:0] ioctl_addr,
  output [15:0] ioctl_din,
  input  [15:0] ioctl_dout,
  output        led_power,
  output        led_disk,
  output        led_user,
  output        frameBufferCtrl_enable,
  output [11:0] frameBufferCtrl_hSize,
  output [11:0] frameBufferCtrl_vSize,
  output [4:0]  frameBufferCtrl_format,
  output [31:0] frameBufferCtrl_baseAddr,
  output [13:0] frameBufferCtrl_stride,
  input         frameBufferCtrl_vBlank,
  input         frameBufferCtrl_lowLat,
  output        frameBufferCtrl_forceBlank,
  output        video_clockEnable,
  output        video_displayEnable,
  output [8:0]  video_pos_x,
  output [8:0]  video_pos_y,
  output        video_hSync,
  output        video_vSync,
  output        video_hBlank,
  output        video_vBlank,
  output [8:0]  video_regs_size_x,
  output [8:0]  video_regs_size_y,
  output [8:0]  video_regs_frontPorch_x,
  output [8:0]  video_regs_frontPorch_y,
  output [8:0]  video_regs_retrace_x,
  output [8:0]  video_regs_retrace_y,
  output        video_changeMode,
  output [23:0] rgb,
  output [15:0] audio,
  output        sdram_cke,
  output        sdram_cs_n,
  output        sdram_ras_n,
  output        sdram_cas_n,
  output        sdram_we_n,
  output        sdram_oe_n,
  output [1:0]  sdram_bank,
  output [12:0] sdram_addr,
  output [15:0] sdram_din,
  input  [15:0] sdram_dout,
  output        ddr_rd,
  output        ddr_wr,
  output [31:0] ddr_addr,
  output [7:0]  ddr_mask,
  output [63:0] ddr_din,
  input  [63:0] ddr_dout,
  input         ddr_wait_n,
  input         ddr_valid,
  output [7:0]  ddr_burstLength,
  input         ddr_burstDone
);

  wire         _systemFrameBuffer_io_forceBlank;
  wire [63:0]  _gpu_io_layerCtrl_0_tileRom_dout;
  wire [63:0]  _gpu_io_layerCtrl_1_tileRom_dout;
  wire [63:0]  _gpu_io_layerCtrl_2_tileRom_dout;
  wire         _gpu_io_spriteCtrl_start;
  wire         _gpu_io_spriteCtrl_zoom;
  wire [1:0]   _gpu_io_gameConfig_layer_1_paletteBank;
  wire [15:0]  _gpu_io_spriteLineBuffer_dout;
  wire         _gpu_io_spriteFrameBuffer_wait_n;
  wire         _gpu_io_layerCtrl_0_tileRom_rd;
  wire [31:0]  _gpu_io_layerCtrl_0_tileRom_addr;
  wire         _gpu_io_layerCtrl_1_tileRom_rd;
  wire [31:0]  _gpu_io_layerCtrl_1_tileRom_addr;
  wire         _gpu_io_layerCtrl_2_tileRom_rd;
  wire [31:0]  _gpu_io_layerCtrl_2_tileRom_addr;
  wire [8:0]   _gpu_io_spriteLineBuffer_addr;
  wire         _gpu_io_spriteFrameBuffer_wr;
  wire [16:0]  _gpu_io_spriteFrameBuffer_addr;
  wire [15:0]  _gpu_io_spriteFrameBuffer_din;
  wire         _gpu_io_systemFrameBuffer_wr;
  wire [16:0]  _gpu_io_systemFrameBuffer_addr;
  wire [31:0]  _gpu_io_systemFrameBuffer_din;
  wire         _sound_io_rom_1_freezer_io_in_rd = 1'h1;
  wire [7:0]   _sound_io_rom_0_dout;
  wire         _sound_io_rom_0_wait_n;
  wire         _sound_io_rom_0_valid;
  wire [7:0]   _sound_io_rom_1_dout;
  wire         _sound_io_rom_1_valid;
  wire         _sound_io_rom_0_rd;
  wire [24:0]  _sound_io_rom_0_addr;
  wire [24:0]  _sound_io_rom_1_addr;
  wire [11:0]  _main_io_gpuMem_layer_0_vram8x8_addr;
  wire [9:0]   _main_io_gpuMem_layer_0_vram16x16_addr;
  wire [8:0]   _main_io_gpuMem_layer_0_lineRam_addr;
  wire [11:0]  _main_io_gpuMem_layer_1_vram8x8_addr;
  wire [9:0]   _main_io_gpuMem_layer_1_vram16x16_addr;
  wire [8:0]   _main_io_gpuMem_layer_1_lineRam_addr;
  wire [11:0]  _main_io_gpuMem_layer_2_vram8x8_addr;
  wire [9:0]   _main_io_gpuMem_layer_2_vram16x16_addr;
  wire [8:0]   _main_io_gpuMem_layer_2_lineRam_addr;
  wire         _main_io_gpuMem_sprite_vram_rd;
  wire [11:0]  _main_io_gpuMem_sprite_vram_addr;
  wire [14:0]  _main_io_gpuMem_paletteRam_addr;
  wire [15:0]  _main_io_soundCtrl_oki_0_dout;
  wire [15:0]  _main_io_soundCtrl_oki_1_dout;
  wire [15:0]  _main_io_soundCtrl_ymz_dout;
  wire         _main_io_soundCtrl_irq;
  wire [15:0]  _main_io_progRom_dout;
  wire         _main_io_progRom_valid;
  wire [15:0]  _main_io_eeprom_dout;
  wire         _main_io_eeprom_wait_n;
  wire         _main_io_eeprom_valid;
  wire         _main_io_gpuMem_layer_0_regs_tileSize;
  wire         _main_io_gpuMem_layer_0_regs_enable;
  wire         _main_io_gpuMem_layer_0_regs_flipX;
  wire         _main_io_gpuMem_layer_0_regs_flipY;
  wire         _main_io_gpuMem_layer_0_regs_rowScrollEnable;
  wire         _main_io_gpuMem_layer_0_regs_rowSelectEnable;
  wire [8:0]   _main_io_gpuMem_layer_0_regs_scroll_x;
  wire [8:0]   _main_io_gpuMem_layer_0_regs_scroll_y;
  wire [31:0]  _main_io_gpuMem_layer_0_vram8x8_dout;
  wire [31:0]  _main_io_gpuMem_layer_0_vram16x16_dout;
  wire [31:0]  _main_io_gpuMem_layer_0_lineRam_dout;
  wire         _main_io_gpuMem_layer_1_regs_tileSize;
  wire         _main_io_gpuMem_layer_1_regs_enable;
  wire         _main_io_gpuMem_layer_1_regs_flipX;
  wire         _main_io_gpuMem_layer_1_regs_flipY;
  wire         _main_io_gpuMem_layer_1_regs_rowScrollEnable;
  wire         _main_io_gpuMem_layer_1_regs_rowSelectEnable;
  wire [8:0]   _main_io_gpuMem_layer_1_regs_scroll_x;
  wire [8:0]   _main_io_gpuMem_layer_1_regs_scroll_y;
  wire [31:0]  _main_io_gpuMem_layer_1_vram8x8_dout;
  wire [31:0]  _main_io_gpuMem_layer_1_vram16x16_dout;
  wire [31:0]  _main_io_gpuMem_layer_1_lineRam_dout;
  wire         _main_io_gpuMem_layer_2_regs_tileSize;
  wire         _main_io_gpuMem_layer_2_regs_enable;
  wire         _main_io_gpuMem_layer_2_regs_flipX;
  wire         _main_io_gpuMem_layer_2_regs_flipY;
  wire         _main_io_gpuMem_layer_2_regs_rowScrollEnable;
  wire         _main_io_gpuMem_layer_2_regs_rowSelectEnable;
  wire [8:0]   _main_io_gpuMem_layer_2_regs_scroll_x;
  wire [8:0]   _main_io_gpuMem_layer_2_regs_scroll_y;
  wire [31:0]  _main_io_gpuMem_layer_2_vram8x8_dout;
  wire [31:0]  _main_io_gpuMem_layer_2_vram16x16_dout;
  wire [31:0]  _main_io_gpuMem_layer_2_lineRam_dout;
  wire [8:0]   _main_io_gpuMem_sprite_regs_offset_x;
  wire [8:0]   _main_io_gpuMem_sprite_regs_offset_y;
  wire [1:0]   _main_io_gpuMem_sprite_regs_bank;
  wire         _main_io_gpuMem_sprite_regs_fixed;
  wire         _main_io_gpuMem_sprite_regs_hFlip;
  wire [127:0] _main_io_gpuMem_sprite_vram_dout;
  wire [15:0]  _main_io_gpuMem_paletteRam_dout;
  wire         _main_io_soundCtrl_oki_0_wr;
  wire [15:0]  _main_io_soundCtrl_oki_0_din;
  wire         _main_io_soundCtrl_oki_1_wr;
  wire [15:0]  _main_io_soundCtrl_oki_1_din;
  wire         _main_io_soundCtrl_nmk_wr;
  wire [22:0]  _main_io_soundCtrl_nmk_addr;
  wire [15:0]  _main_io_soundCtrl_nmk_din;
  wire         _main_io_soundCtrl_ymz_rd;
  wire         _main_io_soundCtrl_ymz_wr;
  wire [22:0]  _main_io_soundCtrl_ymz_addr;
  wire [15:0]  _main_io_soundCtrl_ymz_din;
  wire         _main_io_soundCtrl_req;
  wire [15:0]  _main_io_soundCtrl_data;
  wire         _main_io_progRom_rd;
  wire [19:0]  _main_io_progRom_addr;
  wire         _main_io_eeprom_rd;
  wire         _main_io_eeprom_wr;
  wire [6:0]   _main_io_eeprom_addr;
  wire [15:0]  _main_io_eeprom_din;
  wire         _main_io_spriteFrameBufferSwap;
  wire         _videoSys_io_prog_video_wr;
  wire         _videoSys_io_prog_done;
  wire         _videoSys_io_video_clockEnable;
  wire         _videoSys_io_video_displayEnable;
  wire [8:0]   _videoSys_io_video_pos_x;
  wire [8:0]   _videoSys_io_video_pos_y;
  wire         _videoSys_io_video_hBlank;
  wire         _videoSys_io_video_vBlank;
  wire [8:0]   _videoSys_io_video_regs_size_x;
  wire [8:0]   _videoSys_io_video_regs_size_y;
  wire         _memSys_io_prog_rom_wr;
  wire         _memSys_io_prog_nvram_rd;
  wire         _memSys_io_prog_nvram_wr;
  wire         _memSys_io_prog_done;
  wire         _memSys_io_progRom_rd;
  wire [19:0]  _memSys_io_progRom_addr;
  wire         _memSys_io_eeprom_rd;
  wire         _memSys_io_eeprom_wr;
  wire [6:0]   _memSys_io_eeprom_addr;
  wire [15:0]  _memSys_io_eeprom_din;
  wire         _memSys_io_soundRom_0_rd;
  wire [24:0]  _memSys_io_soundRom_0_addr;
  wire         _memSys_io_soundRom_1_rd;
  wire [24:0]  _memSys_io_soundRom_1_addr;
  wire         _memSys_io_layerTileRom_0_rd;
  wire [31:0]  _memSys_io_layerTileRom_0_addr;
  wire         _memSys_io_layerTileRom_1_rd;
  wire [31:0]  _memSys_io_layerTileRom_1_addr;
  wire         _memSys_io_layerTileRom_2_rd;
  wire [31:0]  _memSys_io_layerTileRom_2_addr;
  wire         _memSys_io_spriteTileRom_rd;
  wire [31:0]  _memSys_io_spriteTileRom_addr;
  wire [7:0]   _memSys_io_spriteTileRom_burstLength;
  wire         _memSys_io_spriteFrameBuffer_rd;
  wire         _memSys_io_spriteFrameBuffer_wr;
  wire [31:0]  _memSys_io_spriteFrameBuffer_addr;
  wire [7:0]   _memSys_io_spriteFrameBuffer_mask;
  wire [63:0]  _memSys_io_spriteFrameBuffer_din;
  wire [7:0]   _memSys_io_spriteFrameBuffer_burstLength;
  wire         _memSys_io_systemFrameBuffer_wr;
  wire [31:0]  _memSys_io_systemFrameBuffer_addr;
  wire [7:0]   _memSys_io_systemFrameBuffer_mask;
  wire [63:0]  _memSys_io_systemFrameBuffer_din;
  wire         _memSys_io_prog_rom_wait_n;
  wire [15:0]  _memSys_io_prog_nvram_dout;
  wire         _memSys_io_prog_nvram_wait_n;
  wire         _memSys_io_prog_nvram_valid;
  wire [15:0]  _memSys_io_progRom_dout;
  wire         _memSys_io_progRom_wait_n;
  wire         _memSys_io_progRom_valid;
  wire [15:0]  _memSys_io_eeprom_dout;
  wire         _memSys_io_eeprom_wait_n;
  wire         _memSys_io_eeprom_valid;
  wire [7:0]   _memSys_io_soundRom_0_dout;
  wire         _memSys_io_soundRom_0_wait_n;
  wire         _memSys_io_soundRom_0_valid;
  wire [7:0]   _memSys_io_soundRom_1_dout;
  wire         _memSys_io_soundRom_1_wait_n;
  wire         _memSys_io_soundRom_1_valid;
  wire [63:0]  _memSys_io_layerTileRom_0_dout;
  wire         _memSys_io_layerTileRom_0_wait_n;
  wire         _memSys_io_layerTileRom_0_valid;
  wire [63:0]  _memSys_io_layerTileRom_1_dout;
  wire         _memSys_io_layerTileRom_1_wait_n;
  wire         _memSys_io_layerTileRom_1_valid;
  wire [63:0]  _memSys_io_layerTileRom_2_dout;
  wire         _memSys_io_layerTileRom_2_wait_n;
  wire         _memSys_io_layerTileRom_2_valid;
  wire [63:0]  _memSys_io_spriteTileRom_dout;
  wire         _memSys_io_spriteTileRom_wait_n;
  wire         _memSys_io_spriteTileRom_valid;
  wire         _memSys_io_spriteTileRom_burstDone;
  wire [63:0]  _memSys_io_spriteFrameBuffer_dout;
  wire         _memSys_io_spriteFrameBuffer_wait_n;
  wire         _memSys_io_spriteFrameBuffer_valid;
  wire         _memSys_io_spriteFrameBuffer_burstDone;
  wire         _memSys_io_systemFrameBuffer_wait_n;
  wire         _memSys_io_ready;
  wire         _sdram_1_io_mem_rd;
  wire         _sdram_1_io_mem_wr;
  wire [24:0]  _sdram_1_io_mem_addr;
  wire [15:0]  _sdram_1_io_mem_din;
  wire [15:0]  _sdram_1_io_mem_dout;
  wire         _sdram_1_io_mem_wait_n;
  wire         _sdram_1_io_mem_valid;
  wire         _sdram_1_io_mem_burstDone;
  wire         _ddr_1_io_mem_rd;
  wire         _ddr_1_io_mem_wr;
  wire [31:0]  _ddr_1_io_mem_addr;
  wire [7:0]   _ddr_1_io_mem_mask;
  wire [63:0]  _ddr_1_io_mem_din;
  wire [7:0]   _ddr_1_io_mem_burstLength;
  wire [63:0]  _ddr_1_io_mem_dout;
  wire         _ddr_1_io_mem_wait_n;
  wire         _ddr_1_io_mem_valid;
  wire         _ddr_1_io_mem_burstDone;
  wire         _dipsRegs_io_mem_wr;
  wire [1:0]   _dipsRegs_io_mem_addr;
  wire [15:0]  _dipsRegs_io_regs_0;
  reg          vBlank_r;
  reg          vBlank;
  reg          vBlankFalling_REG;
  reg  [3:0]   gameIndexReg;
  reg          gameIndexReg_latched;
  reg          gameIndexReg_REG;
  wire         _gameConfig_T = gameIndexReg == 4'h1;
  wire [31:0]  _gameConfig_T_1_layer_2_romOffset = _gameConfig_T ? 32'h900080 : 32'h0;
  wire         _gameConfig_T_2 = gameIndexReg == 4'h2;
  wire [31:0]  _gameConfig_T_3_layer_1_romOffset =
    _gameConfig_T_2 ? 32'h480080 : 32'h700080;
  wire [1:0]   _gameConfig_T_3_layer_2_format =
    _gameConfig_T_2 ? 2'h1 : {2{_gameConfig_T}};
  wire [31:0]  _gameConfig_T_3_layer_2_romOffset =
    _gameConfig_T_2 ? 32'h580080 : _gameConfig_T_1_layer_2_romOffset;
  wire         _GEN = _gameConfig_T_2 | _gameConfig_T;
  wire [1:0]   _gameConfig_T_3_sprite_format = _GEN ? 2'h2 : 2'h1;
  wire [31:0]  _gameConfig_T_3_sprite_romOffset =
    _gameConfig_T_2 ? 32'h5C0080 : {10'h2, _gameConfig_T, 21'h100080};
  wire         _gameConfig_T_4 = gameIndexReg == 4'h3;
  wire         _GEN_0 = _gameConfig_T_4 | ~_gameConfig_T_2;
  wire [31:0]  _gameConfig_T_5_eepromOffset = _GEN_0 ? 32'h100000 : 32'h80000;
  wire [31:0]  _gameConfig_T_5_sound_0_romOffset = _GEN_0 ? 32'h100080 : 32'h80080;
  wire [31:0]  _gameConfig_T_5_layer_0_romOffset = _GEN_0 ? 32'h500080 : 32'h380080;
  wire [31:0]  _gameConfig_T_5_layer_1_romOffset =
    _gameConfig_T_4 ? 32'hD00080 : _gameConfig_T_3_layer_1_romOffset;
  wire [31:0]  _gameConfig_T_5_layer_2_romOffset =
    _gameConfig_T_4 ? 32'h1500080 : _gameConfig_T_3_layer_2_romOffset;
  wire [1:0]   _gameConfig_T_5_sprite_format =
    _gameConfig_T_4 ? 2'h3 : _gameConfig_T_3_sprite_format;
  wire [31:0]  _gameConfig_T_5_sprite_romOffset =
    _gameConfig_T_4 ? 32'h1900080 : _gameConfig_T_3_sprite_romOffset;
  wire         _gameConfig_T_6 = gameIndexReg == 4'h6;
  wire [31:0]  _gameConfig_T_7_eepromOffset =
    _gameConfig_T_6 ? 32'h0 : _gameConfig_T_5_eepromOffset;
  wire [31:0]  _gameConfig_T_7_sound_0_romOffset =
    _gameConfig_T_6 ? 32'h100000 : _gameConfig_T_5_sound_0_romOffset;
  wire [31:0]  _gameConfig_T_7_layer_0_romOffset =
    _gameConfig_T_6 ? 32'hD00000 : _gameConfig_T_5_layer_0_romOffset;
  wire [31:0]  _gameConfig_T_7_layer_1_romOffset =
    _gameConfig_T_6 ? 32'h1100000 : _gameConfig_T_5_layer_1_romOffset;
  wire [31:0]  _gameConfig_T_7_layer_2_romOffset =
    _gameConfig_T_6 ? 32'h1500000 : _gameConfig_T_5_layer_2_romOffset;
  wire [1:0]   _gameConfig_T_7_sprite_format =
    _gameConfig_T_6 ? 2'h1 : _gameConfig_T_5_sprite_format;
  wire [31:0]  _gameConfig_T_7_sprite_romOffset =
    _gameConfig_T_6 ? 32'h1900000 : _gameConfig_T_5_sprite_romOffset;
  wire         _gameConfig_T_8 = gameIndexReg == 4'h5;
  wire         _GEN_1 = _gameConfig_T_8 | _gameConfig_T_6 | _gameConfig_T_4;
  wire         _GEN_2 = _GEN_1 | ~_gameConfig_T_2;
  wire [1:0]   _gameConfig_T_9_sound_0_device = _GEN_2 ? 2'h1 : 2'h2;
  wire [31:0]  _gameConfig_T_9_sound_1_romOffset = _GEN_2 ? 32'h0 : 32'h280080;
  wire [31:0]  _gameConfig_T_9_layer_0_romOffset =
    _gameConfig_T_8 ? 32'h500080 : _gameConfig_T_7_layer_0_romOffset;
  wire [31:0]  _gameConfig_T_9_layer_1_romOffset =
    _gameConfig_T_8 ? 32'hD00080 : _gameConfig_T_7_layer_1_romOffset;
  wire [1:0]   _gameConfig_T_9_layer_2_format =
    _GEN_1 ? 2'h3 : _gameConfig_T_3_layer_2_format;
  wire [31:0]  _gameConfig_T_9_layer_2_romOffset =
    _gameConfig_T_8 ? 32'h1100080 : _gameConfig_T_7_layer_2_romOffset;
  wire [1:0]   _gameConfig_T_9_sprite_format =
    _gameConfig_T_8 ? 2'h3 : _gameConfig_T_7_sprite_format;
  wire [31:0]  _gameConfig_T_9_sprite_romOffset =
    _gameConfig_T_8 ? 32'h1500080 : _gameConfig_T_7_sprite_romOffset;
  wire         _gameConfig_T_10 = gameIndexReg == 4'h7;
  wire [1:0]   _gameConfig_T_11_sound_0_device =
    _gameConfig_T_10 ? 2'h3 : _gameConfig_T_9_sound_0_device;
  wire [31:0]  _gameConfig_T_11_sound_1_romOffset =
    _gameConfig_T_10 ? 32'h140080 : _gameConfig_T_9_sound_1_romOffset;
  wire [1:0]   _gameConfig_T_11_layer_0_format = _gameConfig_T_10 ? 2'h1 : {_GEN_1, 1'h1};
  wire [31:0]  _gameConfig_T_11_layer_0_romOffset =
    _gameConfig_T_10 ? 32'h1C0080 : _gameConfig_T_9_layer_0_romOffset;
  wire [1:0]   _gameConfig_T_11_layer_1_format = _gameConfig_T_10 ? 2'h1 : {_GEN_1, 1'h1};
  wire [31:0]  _gameConfig_T_11_layer_1_romOffset =
    _gameConfig_T_10 ? 32'h240080 : _gameConfig_T_9_layer_1_romOffset;
  wire [1:0]   _gameConfig_T_11_layer_2_format =
    _gameConfig_T_10 ? 2'h1 : _gameConfig_T_9_layer_2_format;
  wire [31:0]  _gameConfig_T_11_layer_2_romOffset =
    _gameConfig_T_10 ? 32'h2C0080 : _gameConfig_T_9_layer_2_romOffset;
  wire [31:0]  _gameConfig_T_11_sprite_romOffset =
    _gameConfig_T_10 ? 32'h340080 : _gameConfig_T_9_sprite_romOffset;
  wire         _gameConfig_T_12 = gameIndexReg == 4'h4;
  wire [8:0]   gameConfig_granularity =
    _gameConfig_T_12
    | ~(_gameConfig_T_10 | ~(_GEN_1 | ~(_gameConfig_T_2 | ~_gameConfig_T)))
      ? 9'h100
      : 9'h10;
  wire         _GEN_3 = _gameConfig_T_12 | _gameConfig_T_10 | _gameConfig_T_8;
  wire [31:0]  gameConfig_eepromOffset =
    _GEN_3 ? 32'h100000 : _gameConfig_T_7_eepromOffset;
  wire [1:0]   gameConfig_sound_0_device =
    _gameConfig_T_12 ? 2'h1 : _gameConfig_T_11_sound_0_device;
  wire [31:0]  gameConfig_sound_0_romOffset =
    _GEN_3 ? 32'h100080 : _gameConfig_T_7_sound_0_romOffset;
  wire [31:0]  gameConfig_sound_1_romOffset =
    _gameConfig_T_12 ? 32'h0 : _gameConfig_T_11_sound_1_romOffset;
  wire [1:0]   gpu_io_layerCtrl_0_format =
    _gameConfig_T_12 ? 2'h3 : _gameConfig_T_11_layer_0_format;
  wire [31:0]  gameConfig_layer_0_romOffset =
    _gameConfig_T_12 ? 32'h300080 : _gameConfig_T_11_layer_0_romOffset;
  wire [1:0]   gameConfig_layer_0_paletteBank =
    _gameConfig_T_12 ? 2'h1 : {1'h0, ~_gameConfig_T_10};
  wire [1:0]   gpu_io_layerCtrl_1_format =
    _gameConfig_T_12 ? 2'h0 : _gameConfig_T_11_layer_1_format;
  wire [31:0]  gameConfig_layer_1_romOffset =
    _gameConfig_T_12 ? 32'h0 : _gameConfig_T_11_layer_1_romOffset;
  wire         _GEN_4 = _gameConfig_T_12 | _gameConfig_T_10;
  wire [1:0]   gpu_io_layerCtrl_2_format =
    _gameConfig_T_12 ? 2'h0 : _gameConfig_T_11_layer_2_format;
  wire [31:0]  gameConfig_layer_2_romOffset =
    _gameConfig_T_12 ? 32'h0 : _gameConfig_T_11_layer_2_romOffset;
  wire [1:0]   gameConfig_layer_2_paletteBank =
    _GEN_4 ? 2'h0 : {1'h0, _gameConfig_T_8 | _gameConfig_T_6 | _gameConfig_T_4 | _GEN};
  wire [1:0]   gpu_io_spriteCtrl_format = _GEN_4 ? 2'h1 : _gameConfig_T_9_sprite_format;
  wire [31:0]  gameConfig_sprite_romOffset =
    _gameConfig_T_12 ? 32'h700080 : _gameConfig_T_11_sprite_romOffset;
  wire         _memSys_io_prog_done_T_2 = ioctl_index == 8'h0;
  wire         memSys_io_prog_rom_writeEnable = ioctl_download & _memSys_io_prog_done_T_2;
  wire         _memSys_io_prog_nvram_writeEnable_T = ioctl_index == 8'h2;
  wire         memSys_io_prog_nvram_readEnable =
    ioctl_upload & _memSys_io_prog_nvram_writeEnable_T;
  wire         memSys_io_prog_nvram_writeEnable =
    ioctl_download & _memSys_io_prog_nvram_writeEnable_T;
  wire         _GEN_5 =
    memSys_io_prog_nvram_readEnable | memSys_io_prog_nvram_writeEnable
      ? _memSys_io_prog_nvram_wait_n
      : ~memSys_io_prog_rom_writeEnable | _memSys_io_prog_rom_wait_n;
  reg  [15:0]  memSys_io_prog_nvram_ioctl_din_r;
  reg          memSys_io_prog_done_REG;
  wire         _videoSys_io_prog_done_T_2 = ioctl_index == 8'h3;
  wire         videoSys_io_prog_video_writeEnable =
    ioctl_download & _videoSys_io_prog_done_T_2;
  reg          videoSys_io_prog_done_REG;
  wire         _GEN_6 = cpuReset | ~_memSys_io_ready;
  wire         _gameIndexReg_T_2 = ioctl_download & ioctl_wr & ioctl_index == 8'h1;
  wire         _gameIndexReg_T_6 =
    ~ioctl_download & gameIndexReg_REG & ~gameIndexReg_latched;
  always @(posedge clock) begin
    vBlank_r <= _videoSys_io_video_vBlank;
    vBlank <= vBlank_r;
    vBlankFalling_REG <= vBlank;
    if (_gameIndexReg_T_6)
      gameIndexReg <= options_gameIndex;
    else if (_gameIndexReg_T_2)
      gameIndexReg <= ioctl_dout[3:0];
    gameIndexReg_REG <= ioctl_download;
    if (_memSys_io_prog_nvram_valid)
      memSys_io_prog_nvram_ioctl_din_r <= _memSys_io_prog_nvram_dout;
    memSys_io_prog_done_REG <= ioctl_download;
    videoSys_io_prog_done_REG <= ioctl_download;
    if (reset)
      gameIndexReg_latched <= 1'h0;
    else
      gameIndexReg_latched <=
        _gameIndexReg_T_6 | _gameIndexReg_T_2 | gameIndexReg_latched;
  end // always @(posedge)
  assign _dipsRegs_io_mem_wr =
    ioctl_download & ioctl_index == 8'hFE & ioctl_addr[26:3] == 24'h0 & ioctl_wr;
  assign _dipsRegs_io_mem_addr = ioctl_addr[2:1];
  RegisterFile dipsRegs (
    .clock       (clock),
    .io_mem_wr   (_dipsRegs_io_mem_wr),
    .io_mem_addr (_dipsRegs_io_mem_addr),
    .io_mem_din  (ioctl_dout),
    .io_regs_0   (_dipsRegs_io_regs_0)
  );
  DDR ddr_1 (
    .clock              (clock),
    .reset              (reset),
    .io_mem_rd          (_ddr_1_io_mem_rd),
    .io_mem_wr          (_ddr_1_io_mem_wr),
    .io_mem_addr        (_ddr_1_io_mem_addr),
    .io_mem_mask        (_ddr_1_io_mem_mask),
    .io_mem_din         (_ddr_1_io_mem_din),
    .io_mem_dout        (_ddr_1_io_mem_dout),
    .io_mem_wait_n      (_ddr_1_io_mem_wait_n),
    .io_mem_valid       (_ddr_1_io_mem_valid),
    .io_mem_burstLength (_ddr_1_io_mem_burstLength),
    .io_mem_burstDone   (_ddr_1_io_mem_burstDone),
    .io_ddr_rd          (ddr_rd),
    .io_ddr_wr          (ddr_wr),
    .io_ddr_addr        (ddr_addr),
    .io_ddr_mask        (ddr_mask),
    .io_ddr_din         (ddr_din),
    .io_ddr_dout        (ddr_dout),
    .io_ddr_wait_n      (ddr_wait_n),
    .io_ddr_valid       (ddr_valid),
    .io_ddr_burstLength (ddr_burstLength)
  );
  SDRAM sdram_1 (
    .clock            (clock),
    .reset            (reset),
    .io_mem_rd        (_sdram_1_io_mem_rd),
    .io_mem_wr        (_sdram_1_io_mem_wr),
    .io_mem_addr      (_sdram_1_io_mem_addr),
    .io_mem_din       (_sdram_1_io_mem_din),
    .io_mem_dout      (_sdram_1_io_mem_dout),
    .io_mem_wait_n    (_sdram_1_io_mem_wait_n),
    .io_mem_valid     (_sdram_1_io_mem_valid),
    .io_mem_burstDone (_sdram_1_io_mem_burstDone),
    .io_sdram_cs_n    (sdram_cs_n),
    .io_sdram_ras_n   (sdram_ras_n),
    .io_sdram_cas_n   (sdram_cas_n),
    .io_sdram_we_n    (sdram_we_n),
    .io_sdram_oe_n    (sdram_oe_n),
    .io_sdram_bank    (sdram_bank),
    .io_sdram_addr    (sdram_addr),
    .io_sdram_din     (sdram_din),
    .io_sdram_dout    (sdram_dout)
  );
  assign _memSys_io_prog_rom_wr = memSys_io_prog_rom_writeEnable & ioctl_wr;
  assign _memSys_io_prog_nvram_rd = memSys_io_prog_nvram_readEnable & ioctl_rd;
  assign _memSys_io_prog_nvram_wr = memSys_io_prog_nvram_writeEnable & ioctl_wr;
  assign _memSys_io_prog_done =
    ~ioctl_download & memSys_io_prog_done_REG & _memSys_io_prog_done_T_2;
  MemSys memSys (
    .clock                            (clock),
    .reset                            (reset),
    .io_gameConfig_eepromOffset       (gameConfig_eepromOffset),
    .io_gameConfig_sound_0_romOffset  (gameConfig_sound_0_romOffset),
    .io_gameConfig_sound_1_romOffset  (gameConfig_sound_1_romOffset),
    .io_gameConfig_layer_0_romOffset  (gameConfig_layer_0_romOffset),
    .io_gameConfig_layer_1_romOffset  (gameConfig_layer_1_romOffset),
    .io_gameConfig_layer_2_romOffset  (gameConfig_layer_2_romOffset),
    .io_gameConfig_sprite_romOffset   (gameConfig_sprite_romOffset),
    .io_prog_rom_wr                   (_memSys_io_prog_rom_wr),
    .io_prog_rom_addr                 (ioctl_addr),
    .io_prog_rom_din                  (ioctl_dout),
    .io_prog_rom_wait_n               (_memSys_io_prog_rom_wait_n),
    .io_prog_nvram_rd                 (_memSys_io_prog_nvram_rd),
    .io_prog_nvram_wr                 (_memSys_io_prog_nvram_wr),
    .io_prog_nvram_addr               (ioctl_addr),
    .io_prog_nvram_din                (ioctl_dout),
    .io_prog_nvram_dout               (_memSys_io_prog_nvram_dout),
    .io_prog_nvram_wait_n             (_memSys_io_prog_nvram_wait_n),
    .io_prog_nvram_valid              (_memSys_io_prog_nvram_valid),
    .io_prog_done                     (_memSys_io_prog_done),
    .io_progRom_rd                    (_memSys_io_progRom_rd),
    .io_progRom_addr                  (_memSys_io_progRom_addr),
    .io_progRom_dout                  (_memSys_io_progRom_dout),
    .io_progRom_wait_n                (_memSys_io_progRom_wait_n),
    .io_progRom_valid                 (_memSys_io_progRom_valid),
    .io_eeprom_rd                     (_memSys_io_eeprom_rd),
    .io_eeprom_wr                     (_memSys_io_eeprom_wr),
    .io_eeprom_addr                   (_memSys_io_eeprom_addr),
    .io_eeprom_din                    (_memSys_io_eeprom_din),
    .io_eeprom_dout                   (_memSys_io_eeprom_dout),
    .io_eeprom_wait_n                 (_memSys_io_eeprom_wait_n),
    .io_eeprom_valid                  (_memSys_io_eeprom_valid),
    .io_soundRom_0_rd                 (_memSys_io_soundRom_0_rd),
    .io_soundRom_0_addr               (_memSys_io_soundRom_0_addr),
    .io_soundRom_0_dout               (_memSys_io_soundRom_0_dout),
    .io_soundRom_0_wait_n             (_memSys_io_soundRom_0_wait_n),
    .io_soundRom_0_valid              (_memSys_io_soundRom_0_valid),
    .io_soundRom_1_rd                 (_memSys_io_soundRom_1_rd),
    .io_soundRom_1_addr               (_memSys_io_soundRom_1_addr),
    .io_soundRom_1_dout               (_memSys_io_soundRom_1_dout),
    .io_soundRom_1_wait_n             (_memSys_io_soundRom_1_wait_n),
    .io_soundRom_1_valid              (_memSys_io_soundRom_1_valid),
    .io_layerTileRom_0_rd             (_memSys_io_layerTileRom_0_rd),
    .io_layerTileRom_0_addr           (_memSys_io_layerTileRom_0_addr),
    .io_layerTileRom_0_dout           (_memSys_io_layerTileRom_0_dout),
    .io_layerTileRom_0_wait_n         (_memSys_io_layerTileRom_0_wait_n),
    .io_layerTileRom_0_valid          (_memSys_io_layerTileRom_0_valid),
    .io_layerTileRom_1_rd             (_memSys_io_layerTileRom_1_rd),
    .io_layerTileRom_1_addr           (_memSys_io_layerTileRom_1_addr),
    .io_layerTileRom_1_dout           (_memSys_io_layerTileRom_1_dout),
    .io_layerTileRom_1_wait_n         (_memSys_io_layerTileRom_1_wait_n),
    .io_layerTileRom_1_valid          (_memSys_io_layerTileRom_1_valid),
    .io_layerTileRom_2_rd             (_memSys_io_layerTileRom_2_rd),
    .io_layerTileRom_2_addr           (_memSys_io_layerTileRom_2_addr),
    .io_layerTileRom_2_dout           (_memSys_io_layerTileRom_2_dout),
    .io_layerTileRom_2_wait_n         (_memSys_io_layerTileRom_2_wait_n),
    .io_layerTileRom_2_valid          (_memSys_io_layerTileRom_2_valid),
    .io_spriteTileRom_rd              (_memSys_io_spriteTileRom_rd),
    .io_spriteTileRom_addr            (_memSys_io_spriteTileRom_addr),
    .io_spriteTileRom_dout            (_memSys_io_spriteTileRom_dout),
    .io_spriteTileRom_wait_n          (_memSys_io_spriteTileRom_wait_n),
    .io_spriteTileRom_valid           (_memSys_io_spriteTileRom_valid),
    .io_spriteTileRom_burstLength     (_memSys_io_spriteTileRom_burstLength),
    .io_spriteTileRom_burstDone       (_memSys_io_spriteTileRom_burstDone),
    .io_ddr_rd                        (_ddr_1_io_mem_rd),
    .io_ddr_wr                        (_ddr_1_io_mem_wr),
    .io_ddr_addr                      (_ddr_1_io_mem_addr),
    .io_ddr_mask                      (_ddr_1_io_mem_mask),
    .io_ddr_din                       (_ddr_1_io_mem_din),
    .io_ddr_dout                      (_ddr_1_io_mem_dout),
    .io_ddr_wait_n                    (_ddr_1_io_mem_wait_n),
    .io_ddr_valid                     (_ddr_1_io_mem_valid),
    .io_ddr_burstLength               (_ddr_1_io_mem_burstLength),
    .io_ddr_burstDone                 (_ddr_1_io_mem_burstDone),
    .io_sdram_rd                      (_sdram_1_io_mem_rd),
    .io_sdram_wr                      (_sdram_1_io_mem_wr),
    .io_sdram_addr                    (_sdram_1_io_mem_addr),
    .io_sdram_din                     (_sdram_1_io_mem_din),
    .io_sdram_dout                    (_sdram_1_io_mem_dout),
    .io_sdram_wait_n                  (_sdram_1_io_mem_wait_n),
    .io_sdram_valid                   (_sdram_1_io_mem_valid),
    .io_sdram_burstDone               (_sdram_1_io_mem_burstDone),
    .io_spriteFrameBuffer_rd          (_memSys_io_spriteFrameBuffer_rd),
    .io_spriteFrameBuffer_wr          (_memSys_io_spriteFrameBuffer_wr),
    .io_spriteFrameBuffer_addr        (_memSys_io_spriteFrameBuffer_addr),
    .io_spriteFrameBuffer_mask        (_memSys_io_spriteFrameBuffer_mask),
    .io_spriteFrameBuffer_din         (_memSys_io_spriteFrameBuffer_din),
    .io_spriteFrameBuffer_dout        (_memSys_io_spriteFrameBuffer_dout),
    .io_spriteFrameBuffer_wait_n      (_memSys_io_spriteFrameBuffer_wait_n),
    .io_spriteFrameBuffer_valid       (_memSys_io_spriteFrameBuffer_valid),
    .io_spriteFrameBuffer_burstLength (_memSys_io_spriteFrameBuffer_burstLength),
    .io_spriteFrameBuffer_burstDone   (_memSys_io_spriteFrameBuffer_burstDone),
    .io_systemFrameBuffer_wr          (_memSys_io_systemFrameBuffer_wr),
    .io_systemFrameBuffer_addr        (_memSys_io_systemFrameBuffer_addr),
    .io_systemFrameBuffer_mask        (_memSys_io_systemFrameBuffer_mask),
    .io_systemFrameBuffer_din         (_memSys_io_systemFrameBuffer_din),
    .io_systemFrameBuffer_wait_n      (_memSys_io_systemFrameBuffer_wait_n),
    .io_ready                         (_memSys_io_ready)
  );
  assign _videoSys_io_prog_video_wr = videoSys_io_prog_video_writeEnable & ioctl_wr;
  assign _videoSys_io_prog_done =
    ~ioctl_download & videoSys_io_prog_done_REG & _videoSys_io_prog_done_T_2;
  VideoSys videoSys (
    .clock                      (clock),
    .reset                      (reset),
    .io_videoClock              (videoClock),
    .io_videoReset              (videoReset),
    .io_prog_video_wr           (_videoSys_io_prog_video_wr),
    .io_prog_video_addr         (ioctl_addr),
    .io_prog_video_din          (ioctl_dout),
    .io_prog_done               (_videoSys_io_prog_done),
    .io_options_offset_x        (options_offset_x),
    .io_options_offset_y        (options_offset_y),
    .io_options_compatibility   (options_compatibility),
    .io_video_clockEnable       (_videoSys_io_video_clockEnable),
    .io_video_displayEnable     (_videoSys_io_video_displayEnable),
    .io_video_pos_x             (_videoSys_io_video_pos_x),
    .io_video_pos_y             (_videoSys_io_video_pos_y),
    .io_video_hSync             (video_hSync),
    .io_video_vSync             (video_vSync),
    .io_video_hBlank            (_videoSys_io_video_hBlank),
    .io_video_vBlank            (_videoSys_io_video_vBlank),
    .io_video_regs_size_x       (_videoSys_io_video_regs_size_x),
    .io_video_regs_size_y       (_videoSys_io_video_regs_size_y),
    .io_video_regs_frontPorch_x (video_regs_frontPorch_x),
    .io_video_regs_frontPorch_y (video_regs_frontPorch_y),
    .io_video_regs_retrace_x    (video_regs_retrace_x),
    .io_video_regs_retrace_y    (video_regs_retrace_y),
    .io_video_changeMode        (video_changeMode)
  );
  Main main (
    .clock                                  (cpuClock),
    .reset                                  (_GEN_6),
    .io_videoClock                          (videoClock),
    .io_spriteClock                         (clock),
    .io_gameIndex                           (gameIndexReg),
    .io_options_service                     (options_service),
    .io_player_0_up                         (player_0_up),
    .io_player_0_down                       (player_0_down),
    .io_player_0_left                       (player_0_left),
    .io_player_0_right                      (player_0_right),
    .io_player_0_buttons                    (player_0_buttons),
    .io_player_0_start                      (player_0_start),
    .io_player_0_coin                       (player_0_coin),
    .io_player_0_pause                      (player_0_pause),
    .io_player_1_up                         (player_1_up),
    .io_player_1_down                       (player_1_down),
    .io_player_1_left                       (player_1_left),
    .io_player_1_right                      (player_1_right),
    .io_player_1_buttons                    (player_1_buttons),
    .io_player_1_start                      (player_1_start),
    .io_player_1_coin                       (player_1_coin),
    .io_player_1_pause                      (player_1_pause),
    .io_dips_0                              (_dipsRegs_io_regs_0),
    .io_video_vBlank                        (_videoSys_io_video_vBlank),
    .io_gpuMem_layer_0_regs_tileSize        (_main_io_gpuMem_layer_0_regs_tileSize),
    .io_gpuMem_layer_0_regs_enable          (_main_io_gpuMem_layer_0_regs_enable),
    .io_gpuMem_layer_0_regs_flipX           (_main_io_gpuMem_layer_0_regs_flipX),
    .io_gpuMem_layer_0_regs_flipY           (_main_io_gpuMem_layer_0_regs_flipY),
    .io_gpuMem_layer_0_regs_rowScrollEnable
      (_main_io_gpuMem_layer_0_regs_rowScrollEnable),
    .io_gpuMem_layer_0_regs_rowSelectEnable
      (_main_io_gpuMem_layer_0_regs_rowSelectEnable),
    .io_gpuMem_layer_0_regs_scroll_x        (_main_io_gpuMem_layer_0_regs_scroll_x),
    .io_gpuMem_layer_0_regs_scroll_y        (_main_io_gpuMem_layer_0_regs_scroll_y),
    .io_gpuMem_layer_0_vram8x8_addr         (_main_io_gpuMem_layer_0_vram8x8_addr),
    .io_gpuMem_layer_0_vram8x8_dout         (_main_io_gpuMem_layer_0_vram8x8_dout),
    .io_gpuMem_layer_0_vram16x16_addr       (_main_io_gpuMem_layer_0_vram16x16_addr),
    .io_gpuMem_layer_0_vram16x16_dout       (_main_io_gpuMem_layer_0_vram16x16_dout),
    .io_gpuMem_layer_0_lineRam_addr         (_main_io_gpuMem_layer_0_lineRam_addr),
    .io_gpuMem_layer_0_lineRam_dout         (_main_io_gpuMem_layer_0_lineRam_dout),
    .io_gpuMem_layer_1_regs_tileSize        (_main_io_gpuMem_layer_1_regs_tileSize),
    .io_gpuMem_layer_1_regs_enable          (_main_io_gpuMem_layer_1_regs_enable),
    .io_gpuMem_layer_1_regs_flipX           (_main_io_gpuMem_layer_1_regs_flipX),
    .io_gpuMem_layer_1_regs_flipY           (_main_io_gpuMem_layer_1_regs_flipY),
    .io_gpuMem_layer_1_regs_rowScrollEnable
      (_main_io_gpuMem_layer_1_regs_rowScrollEnable),
    .io_gpuMem_layer_1_regs_rowSelectEnable
      (_main_io_gpuMem_layer_1_regs_rowSelectEnable),
    .io_gpuMem_layer_1_regs_scroll_x        (_main_io_gpuMem_layer_1_regs_scroll_x),
    .io_gpuMem_layer_1_regs_scroll_y        (_main_io_gpuMem_layer_1_regs_scroll_y),
    .io_gpuMem_layer_1_vram8x8_addr         (_main_io_gpuMem_layer_1_vram8x8_addr),
    .io_gpuMem_layer_1_vram8x8_dout         (_main_io_gpuMem_layer_1_vram8x8_dout),
    .io_gpuMem_layer_1_vram16x16_addr       (_main_io_gpuMem_layer_1_vram16x16_addr),
    .io_gpuMem_layer_1_vram16x16_dout       (_main_io_gpuMem_layer_1_vram16x16_dout),
    .io_gpuMem_layer_1_lineRam_addr         (_main_io_gpuMem_layer_1_lineRam_addr),
    .io_gpuMem_layer_1_lineRam_dout         (_main_io_gpuMem_layer_1_lineRam_dout),
    .io_gpuMem_layer_2_regs_tileSize        (_main_io_gpuMem_layer_2_regs_tileSize),
    .io_gpuMem_layer_2_regs_enable          (_main_io_gpuMem_layer_2_regs_enable),
    .io_gpuMem_layer_2_regs_flipX           (_main_io_gpuMem_layer_2_regs_flipX),
    .io_gpuMem_layer_2_regs_flipY           (_main_io_gpuMem_layer_2_regs_flipY),
    .io_gpuMem_layer_2_regs_rowScrollEnable
      (_main_io_gpuMem_layer_2_regs_rowScrollEnable),
    .io_gpuMem_layer_2_regs_rowSelectEnable
      (_main_io_gpuMem_layer_2_regs_rowSelectEnable),
    .io_gpuMem_layer_2_regs_scroll_x        (_main_io_gpuMem_layer_2_regs_scroll_x),
    .io_gpuMem_layer_2_regs_scroll_y        (_main_io_gpuMem_layer_2_regs_scroll_y),
    .io_gpuMem_layer_2_vram8x8_addr         (_main_io_gpuMem_layer_2_vram8x8_addr),
    .io_gpuMem_layer_2_vram8x8_dout         (_main_io_gpuMem_layer_2_vram8x8_dout),
    .io_gpuMem_layer_2_vram16x16_addr       (_main_io_gpuMem_layer_2_vram16x16_addr),
    .io_gpuMem_layer_2_vram16x16_dout       (_main_io_gpuMem_layer_2_vram16x16_dout),
    .io_gpuMem_layer_2_lineRam_addr         (_main_io_gpuMem_layer_2_lineRam_addr),
    .io_gpuMem_layer_2_lineRam_dout         (_main_io_gpuMem_layer_2_lineRam_dout),
    .io_gpuMem_sprite_regs_offset_x         (_main_io_gpuMem_sprite_regs_offset_x),
    .io_gpuMem_sprite_regs_offset_y         (_main_io_gpuMem_sprite_regs_offset_y),
    .io_gpuMem_sprite_regs_bank             (_main_io_gpuMem_sprite_regs_bank),
    .io_gpuMem_sprite_regs_fixed            (_main_io_gpuMem_sprite_regs_fixed),
    .io_gpuMem_sprite_regs_hFlip            (_main_io_gpuMem_sprite_regs_hFlip),
    .io_gpuMem_sprite_vram_rd               (_main_io_gpuMem_sprite_vram_rd),
    .io_gpuMem_sprite_vram_addr             (_main_io_gpuMem_sprite_vram_addr),
    .io_gpuMem_sprite_vram_dout             (_main_io_gpuMem_sprite_vram_dout),
    .io_gpuMem_paletteRam_addr              (_main_io_gpuMem_paletteRam_addr),
    .io_gpuMem_paletteRam_dout              (_main_io_gpuMem_paletteRam_dout),
    .io_soundCtrl_oki_0_wr                  (_main_io_soundCtrl_oki_0_wr),
    .io_soundCtrl_oki_0_din                 (_main_io_soundCtrl_oki_0_din),
    .io_soundCtrl_oki_0_dout                (_main_io_soundCtrl_oki_0_dout),
    .io_soundCtrl_oki_1_wr                  (_main_io_soundCtrl_oki_1_wr),
    .io_soundCtrl_oki_1_din                 (_main_io_soundCtrl_oki_1_din),
    .io_soundCtrl_oki_1_dout                (_main_io_soundCtrl_oki_1_dout),
    .io_soundCtrl_nmk_wr                    (_main_io_soundCtrl_nmk_wr),
    .io_soundCtrl_nmk_addr                  (_main_io_soundCtrl_nmk_addr),
    .io_soundCtrl_nmk_din                   (_main_io_soundCtrl_nmk_din),
    .io_soundCtrl_ymz_rd                    (_main_io_soundCtrl_ymz_rd),
    .io_soundCtrl_ymz_wr                    (_main_io_soundCtrl_ymz_wr),
    .io_soundCtrl_ymz_addr                  (_main_io_soundCtrl_ymz_addr),
    .io_soundCtrl_ymz_din                   (_main_io_soundCtrl_ymz_din),
    .io_soundCtrl_ymz_dout                  (_main_io_soundCtrl_ymz_dout),
    .io_soundCtrl_req                       (_main_io_soundCtrl_req),
    .io_soundCtrl_data                      (_main_io_soundCtrl_data),
    .io_soundCtrl_irq                       (_main_io_soundCtrl_irq),
    .io_progRom_rd                          (_main_io_progRom_rd),
    .io_progRom_addr                        (_main_io_progRom_addr),
    .io_progRom_dout                        (_main_io_progRom_dout),
    .io_progRom_valid                       (_main_io_progRom_valid),
    .io_eeprom_rd                           (_main_io_eeprom_rd),
    .io_eeprom_wr                           (_main_io_eeprom_wr),
    .io_eeprom_addr                         (_main_io_eeprom_addr),
    .io_eeprom_din                          (_main_io_eeprom_din),
    .io_eeprom_dout                         (_main_io_eeprom_dout),
    .io_eeprom_wait_n                       (_main_io_eeprom_wait_n),
    .io_eeprom_valid                        (_main_io_eeprom_valid),
    .io_spriteFrameBufferSwap               (_main_io_spriteFrameBufferSwap)
  );
  ReadDataFreezer main_io_progRom_freezer (
    .clock          (clock),
    .reset          (reset),
    .io_targetClock (cpuClock),
    .io_in_rd       (_main_io_progRom_rd),
    .io_in_addr     (_main_io_progRom_addr),
    .io_in_dout     (_main_io_progRom_dout),
    .io_in_valid    (_main_io_progRom_valid),
    .io_out_rd      (_memSys_io_progRom_rd),
    .io_out_addr    (_memSys_io_progRom_addr),
    .io_out_dout    (_memSys_io_progRom_dout),
    .io_out_wait_n  (_memSys_io_progRom_wait_n),
    .io_out_valid   (_memSys_io_progRom_valid)
  );
  DataFreezer main_io_eeprom_freezer (
    .clock          (clock),
    .reset          (reset),
    .io_targetClock (cpuClock),
    .io_in_rd       (_main_io_eeprom_rd),
    .io_in_wr       (_main_io_eeprom_wr),
    .io_in_addr     (_main_io_eeprom_addr),
    .io_in_din      (_main_io_eeprom_din),
    .io_in_dout     (_main_io_eeprom_dout),
    .io_in_wait_n   (_main_io_eeprom_wait_n),
    .io_in_valid    (_main_io_eeprom_valid),
    .io_out_rd      (_memSys_io_eeprom_rd),
    .io_out_wr      (_memSys_io_eeprom_wr),
    .io_out_addr    (_memSys_io_eeprom_addr),
    .io_out_din     (_memSys_io_eeprom_din),
    .io_out_dout    (_memSys_io_eeprom_dout),
    .io_out_wait_n  (_memSys_io_eeprom_wait_n),
    .io_out_valid   (_memSys_io_eeprom_valid)
  );
  Sound sound (
    .clock                        (cpuClock),
    .reset                        (_GEN_6),
    .io_ctrl_oki_0_wr             (_main_io_soundCtrl_oki_0_wr),
    .io_ctrl_oki_0_din            (_main_io_soundCtrl_oki_0_din),
    .io_ctrl_oki_0_dout           (_main_io_soundCtrl_oki_0_dout),
    .io_ctrl_oki_1_wr             (_main_io_soundCtrl_oki_1_wr),
    .io_ctrl_oki_1_din            (_main_io_soundCtrl_oki_1_din),
    .io_ctrl_oki_1_dout           (_main_io_soundCtrl_oki_1_dout),
    .io_ctrl_nmk_wr               (_main_io_soundCtrl_nmk_wr),
    .io_ctrl_nmk_addr             (_main_io_soundCtrl_nmk_addr),
    .io_ctrl_nmk_din              (_main_io_soundCtrl_nmk_din),
    .io_ctrl_ymz_rd               (_main_io_soundCtrl_ymz_rd),
    .io_ctrl_ymz_wr               (_main_io_soundCtrl_ymz_wr),
    .io_ctrl_ymz_addr             (_main_io_soundCtrl_ymz_addr),
    .io_ctrl_ymz_din              (_main_io_soundCtrl_ymz_din),
    .io_ctrl_ymz_dout             (_main_io_soundCtrl_ymz_dout),
    .io_ctrl_req                  (_main_io_soundCtrl_req),
    .io_ctrl_data                 (_main_io_soundCtrl_data),
    .io_ctrl_irq                  (_main_io_soundCtrl_irq),
    .io_gameIndex                 (gameIndexReg),
    .io_gameConfig_sound_0_device (gameConfig_sound_0_device),
    .io_rom_0_rd                  (_sound_io_rom_0_rd),
    .io_rom_0_addr                (_sound_io_rom_0_addr),
    .io_rom_0_dout                (_sound_io_rom_0_dout),
    .io_rom_0_wait_n              (_sound_io_rom_0_wait_n),
    .io_rom_0_valid               (_sound_io_rom_0_valid),
    .io_rom_1_addr                (_sound_io_rom_1_addr),
    .io_rom_1_dout                (_sound_io_rom_1_dout),
    .io_rom_1_valid               (_sound_io_rom_1_valid),
    .io_audio                     (audio)
  );
  ReadDataFreezer_1 sound_io_rom_0_freezer (
    .clock          (clock),
    .reset          (reset),
    .io_targetClock (cpuClock),
    .io_in_rd       (_sound_io_rom_0_rd),
    .io_in_addr     (_sound_io_rom_0_addr),
    .io_in_dout     (_sound_io_rom_0_dout),
    .io_in_wait_n   (_sound_io_rom_0_wait_n),
    .io_in_valid    (_sound_io_rom_0_valid),
    .io_out_rd      (_memSys_io_soundRom_0_rd),
    .io_out_addr    (_memSys_io_soundRom_0_addr),
    .io_out_dout    (_memSys_io_soundRom_0_dout),
    .io_out_wait_n  (_memSys_io_soundRom_0_wait_n),
    .io_out_valid   (_memSys_io_soundRom_0_valid)
  );
  ReadDataFreezer_1 sound_io_rom_1_freezer (
    .clock          (clock),
    .reset          (reset),
    .io_targetClock (cpuClock),
    .io_in_rd       (_sound_io_rom_1_freezer_io_in_rd),
    .io_in_addr     (_sound_io_rom_1_addr),
    .io_in_dout     (_sound_io_rom_1_dout),
    .io_in_wait_n   (/* unused */),
    .io_in_valid    (_sound_io_rom_1_valid),
    .io_out_rd      (_memSys_io_soundRom_1_rd),
    .io_out_addr    (_memSys_io_soundRom_1_addr),
    .io_out_dout    (_memSys_io_soundRom_1_dout),
    .io_out_wait_n  (_memSys_io_soundRom_1_wait_n),
    .io_out_valid   (_memSys_io_soundRom_1_valid)
  );
  assign _gpu_io_spriteCtrl_start = ~vBlank & vBlankFalling_REG;
  assign _gpu_io_spriteCtrl_zoom =
    _gameConfig_T_12 | _gameConfig_T_10 | _GEN_1 | ~_gameConfig_T_2
    & gameIndexReg != 4'h1;
  assign _gpu_io_gameConfig_layer_1_paletteBank = {1'h0, ~_GEN_4};
  GPU gpu (
    .clock                               (clock),
    .reset                               (reset),
    .io_videoClock                       (videoClock),
    .io_layerCtrl_0_enable               (options_layer_0),
    .io_layerCtrl_0_format               (gpu_io_layerCtrl_0_format),
    .io_layerCtrl_0_regs_tileSize        (_main_io_gpuMem_layer_0_regs_tileSize),
    .io_layerCtrl_0_regs_enable          (_main_io_gpuMem_layer_0_regs_enable),
    .io_layerCtrl_0_regs_flipX           (_main_io_gpuMem_layer_0_regs_flipX),
    .io_layerCtrl_0_regs_flipY           (_main_io_gpuMem_layer_0_regs_flipY),
    .io_layerCtrl_0_regs_rowScrollEnable (_main_io_gpuMem_layer_0_regs_rowScrollEnable),
    .io_layerCtrl_0_regs_rowSelectEnable (_main_io_gpuMem_layer_0_regs_rowSelectEnable),
    .io_layerCtrl_0_regs_scroll_x        (_main_io_gpuMem_layer_0_regs_scroll_x),
    .io_layerCtrl_0_regs_scroll_y        (_main_io_gpuMem_layer_0_regs_scroll_y),
    .io_layerCtrl_0_vram8x8_addr         (_main_io_gpuMem_layer_0_vram8x8_addr),
    .io_layerCtrl_0_vram8x8_dout         (_main_io_gpuMem_layer_0_vram8x8_dout),
    .io_layerCtrl_0_vram16x16_addr       (_main_io_gpuMem_layer_0_vram16x16_addr),
    .io_layerCtrl_0_vram16x16_dout       (_main_io_gpuMem_layer_0_vram16x16_dout),
    .io_layerCtrl_0_lineRam_addr         (_main_io_gpuMem_layer_0_lineRam_addr),
    .io_layerCtrl_0_lineRam_dout         (_main_io_gpuMem_layer_0_lineRam_dout),
    .io_layerCtrl_0_tileRom_rd           (_gpu_io_layerCtrl_0_tileRom_rd),
    .io_layerCtrl_0_tileRom_addr         (_gpu_io_layerCtrl_0_tileRom_addr),
    .io_layerCtrl_0_tileRom_dout         (_gpu_io_layerCtrl_0_tileRom_dout),
    .io_layerCtrl_1_enable               (options_layer_1),
    .io_layerCtrl_1_format               (gpu_io_layerCtrl_1_format),
    .io_layerCtrl_1_regs_tileSize        (_main_io_gpuMem_layer_1_regs_tileSize),
    .io_layerCtrl_1_regs_enable          (_main_io_gpuMem_layer_1_regs_enable),
    .io_layerCtrl_1_regs_flipX           (_main_io_gpuMem_layer_1_regs_flipX),
    .io_layerCtrl_1_regs_flipY           (_main_io_gpuMem_layer_1_regs_flipY),
    .io_layerCtrl_1_regs_rowScrollEnable (_main_io_gpuMem_layer_1_regs_rowScrollEnable),
    .io_layerCtrl_1_regs_rowSelectEnable (_main_io_gpuMem_layer_1_regs_rowSelectEnable),
    .io_layerCtrl_1_regs_scroll_x        (_main_io_gpuMem_layer_1_regs_scroll_x),
    .io_layerCtrl_1_regs_scroll_y        (_main_io_gpuMem_layer_1_regs_scroll_y),
    .io_layerCtrl_1_vram8x8_addr         (_main_io_gpuMem_layer_1_vram8x8_addr),
    .io_layerCtrl_1_vram8x8_dout         (_main_io_gpuMem_layer_1_vram8x8_dout),
    .io_layerCtrl_1_vram16x16_addr       (_main_io_gpuMem_layer_1_vram16x16_addr),
    .io_layerCtrl_1_vram16x16_dout       (_main_io_gpuMem_layer_1_vram16x16_dout),
    .io_layerCtrl_1_lineRam_addr         (_main_io_gpuMem_layer_1_lineRam_addr),
    .io_layerCtrl_1_lineRam_dout         (_main_io_gpuMem_layer_1_lineRam_dout),
    .io_layerCtrl_1_tileRom_rd           (_gpu_io_layerCtrl_1_tileRom_rd),
    .io_layerCtrl_1_tileRom_addr         (_gpu_io_layerCtrl_1_tileRom_addr),
    .io_layerCtrl_1_tileRom_dout         (_gpu_io_layerCtrl_1_tileRom_dout),
    .io_layerCtrl_2_enable               (options_layer_2),
    .io_layerCtrl_2_format               (gpu_io_layerCtrl_2_format),
    .io_layerCtrl_2_regs_tileSize        (_main_io_gpuMem_layer_2_regs_tileSize),
    .io_layerCtrl_2_regs_enable          (_main_io_gpuMem_layer_2_regs_enable),
    .io_layerCtrl_2_regs_flipX           (_main_io_gpuMem_layer_2_regs_flipX),
    .io_layerCtrl_2_regs_flipY           (_main_io_gpuMem_layer_2_regs_flipY),
    .io_layerCtrl_2_regs_rowScrollEnable (_main_io_gpuMem_layer_2_regs_rowScrollEnable),
    .io_layerCtrl_2_regs_rowSelectEnable (_main_io_gpuMem_layer_2_regs_rowSelectEnable),
    .io_layerCtrl_2_regs_scroll_x        (_main_io_gpuMem_layer_2_regs_scroll_x),
    .io_layerCtrl_2_regs_scroll_y        (_main_io_gpuMem_layer_2_regs_scroll_y),
    .io_layerCtrl_2_vram8x8_addr         (_main_io_gpuMem_layer_2_vram8x8_addr),
    .io_layerCtrl_2_vram8x8_dout         (_main_io_gpuMem_layer_2_vram8x8_dout),
    .io_layerCtrl_2_vram16x16_addr       (_main_io_gpuMem_layer_2_vram16x16_addr),
    .io_layerCtrl_2_vram16x16_dout       (_main_io_gpuMem_layer_2_vram16x16_dout),
    .io_layerCtrl_2_lineRam_addr         (_main_io_gpuMem_layer_2_lineRam_addr),
    .io_layerCtrl_2_lineRam_dout         (_main_io_gpuMem_layer_2_lineRam_dout),
    .io_layerCtrl_2_tileRom_rd           (_gpu_io_layerCtrl_2_tileRom_rd),
    .io_layerCtrl_2_tileRom_addr         (_gpu_io_layerCtrl_2_tileRom_addr),
    .io_layerCtrl_2_tileRom_dout         (_gpu_io_layerCtrl_2_tileRom_dout),
    .io_spriteCtrl_enable                (options_sprite),
    .io_spriteCtrl_format                (gpu_io_spriteCtrl_format),
    .io_spriteCtrl_start                 (_gpu_io_spriteCtrl_start),
    .io_spriteCtrl_zoom                  (_gpu_io_spriteCtrl_zoom),
    .io_spriteCtrl_regs_offset_x         (_main_io_gpuMem_sprite_regs_offset_x),
    .io_spriteCtrl_regs_offset_y         (_main_io_gpuMem_sprite_regs_offset_y),
    .io_spriteCtrl_regs_bank             (_main_io_gpuMem_sprite_regs_bank),
    .io_spriteCtrl_regs_fixed            (_main_io_gpuMem_sprite_regs_fixed),
    .io_spriteCtrl_regs_hFlip            (_main_io_gpuMem_sprite_regs_hFlip),
    .io_spriteCtrl_vram_rd               (_main_io_gpuMem_sprite_vram_rd),
    .io_spriteCtrl_vram_addr             (_main_io_gpuMem_sprite_vram_addr),
    .io_spriteCtrl_vram_dout             (_main_io_gpuMem_sprite_vram_dout),
    .io_spriteCtrl_tileRom_rd            (_memSys_io_spriteTileRom_rd),
    .io_spriteCtrl_tileRom_addr          (_memSys_io_spriteTileRom_addr),
    .io_spriteCtrl_tileRom_dout          (_memSys_io_spriteTileRom_dout),
    .io_spriteCtrl_tileRom_wait_n        (_memSys_io_spriteTileRom_wait_n),
    .io_spriteCtrl_tileRom_valid         (_memSys_io_spriteTileRom_valid),
    .io_spriteCtrl_tileRom_burstLength   (_memSys_io_spriteTileRom_burstLength),
    .io_spriteCtrl_tileRom_burstDone     (_memSys_io_spriteTileRom_burstDone),
    .io_gameConfig_granularity           (gameConfig_granularity),
    .io_gameConfig_layer_0_paletteBank   (gameConfig_layer_0_paletteBank),
    .io_gameConfig_layer_1_paletteBank   (_gpu_io_gameConfig_layer_1_paletteBank),
    .io_gameConfig_layer_2_paletteBank   (gameConfig_layer_2_paletteBank),
    .io_options_rotate                   (options_rotate),
    .io_options_flipVideo                (options_flipVideo),
    .io_video_clockEnable                (_videoSys_io_video_clockEnable),
    .io_video_displayEnable              (_videoSys_io_video_displayEnable),
    .io_video_pos_x                      (_videoSys_io_video_pos_x),
    .io_video_pos_y                      (_videoSys_io_video_pos_y),
    .io_video_vBlank                     (_videoSys_io_video_vBlank),
    .io_video_regs_size_x                (_videoSys_io_video_regs_size_x),
    .io_video_regs_size_y                (_videoSys_io_video_regs_size_y),
    .io_spriteLineBuffer_addr            (_gpu_io_spriteLineBuffer_addr),
    .io_spriteLineBuffer_dout            (_gpu_io_spriteLineBuffer_dout),
    .io_spriteFrameBuffer_wr             (_gpu_io_spriteFrameBuffer_wr),
    .io_spriteFrameBuffer_addr           (_gpu_io_spriteFrameBuffer_addr),
    .io_spriteFrameBuffer_din            (_gpu_io_spriteFrameBuffer_din),
    .io_spriteFrameBuffer_wait_n         (_gpu_io_spriteFrameBuffer_wait_n),
    .io_systemFrameBuffer_wr             (_gpu_io_systemFrameBuffer_wr),
    .io_systemFrameBuffer_addr           (_gpu_io_systemFrameBuffer_addr),
    .io_systemFrameBuffer_din            (_gpu_io_systemFrameBuffer_din),
    .io_paletteRam_addr                  (_main_io_gpuMem_paletteRam_addr),
    .io_paletteRam_dout                  (_main_io_gpuMem_paletteRam_dout),
    .io_rgb                              (rgb)
  );
  Crossing gpu_io_layerCtrl_0_tileRom_crossing (
    .clock          (clock),
    .io_targetClock (videoClock),
    .io_in_rd       (_gpu_io_layerCtrl_0_tileRom_rd),
    .io_in_addr     (_gpu_io_layerCtrl_0_tileRom_addr),
    .io_in_dout     (_gpu_io_layerCtrl_0_tileRom_dout),
    .io_out_rd      (_memSys_io_layerTileRom_0_rd),
    .io_out_addr    (_memSys_io_layerTileRom_0_addr),
    .io_out_dout    (_memSys_io_layerTileRom_0_dout),
    .io_out_wait_n  (_memSys_io_layerTileRom_0_wait_n),
    .io_out_valid   (_memSys_io_layerTileRom_0_valid)
  );
  Crossing gpu_io_layerCtrl_1_tileRom_crossing (
    .clock          (clock),
    .io_targetClock (videoClock),
    .io_in_rd       (_gpu_io_layerCtrl_1_tileRom_rd),
    .io_in_addr     (_gpu_io_layerCtrl_1_tileRom_addr),
    .io_in_dout     (_gpu_io_layerCtrl_1_tileRom_dout),
    .io_out_rd      (_memSys_io_layerTileRom_1_rd),
    .io_out_addr    (_memSys_io_layerTileRom_1_addr),
    .io_out_dout    (_memSys_io_layerTileRom_1_dout),
    .io_out_wait_n  (_memSys_io_layerTileRom_1_wait_n),
    .io_out_valid   (_memSys_io_layerTileRom_1_valid)
  );
  Crossing gpu_io_layerCtrl_2_tileRom_crossing (
    .clock          (clock),
    .io_targetClock (videoClock),
    .io_in_rd       (_gpu_io_layerCtrl_2_tileRom_rd),
    .io_in_addr     (_gpu_io_layerCtrl_2_tileRom_addr),
    .io_in_dout     (_gpu_io_layerCtrl_2_tileRom_dout),
    .io_out_rd      (_memSys_io_layerTileRom_2_rd),
    .io_out_addr    (_memSys_io_layerTileRom_2_addr),
    .io_out_dout    (_memSys_io_layerTileRom_2_dout),
    .io_out_wait_n  (_memSys_io_layerTileRom_2_wait_n),
    .io_out_valid   (_memSys_io_layerTileRom_2_valid)
  );
  SpriteFrameBuffer spriteFrameBuffer (
    .clock                 (clock),
    .reset                 (reset),
    .io_videoClock         (videoClock),
    .io_enable             (_memSys_io_ready),
    .io_swap               (_main_io_spriteFrameBufferSwap),
    .io_video_pos_y        (_videoSys_io_video_pos_y),
    .io_video_hBlank       (_videoSys_io_video_hBlank),
    .io_lineBuffer_addr    (_gpu_io_spriteLineBuffer_addr),
    .io_lineBuffer_dout    (_gpu_io_spriteLineBuffer_dout),
    .io_frameBuffer_wr     (_gpu_io_spriteFrameBuffer_wr),
    .io_frameBuffer_addr   (_gpu_io_spriteFrameBuffer_addr),
    .io_frameBuffer_din    (_gpu_io_spriteFrameBuffer_din),
    .io_frameBuffer_wait_n (_gpu_io_spriteFrameBuffer_wait_n),
    .io_ddr_rd             (_memSys_io_spriteFrameBuffer_rd),
    .io_ddr_wr             (_memSys_io_spriteFrameBuffer_wr),
    .io_ddr_addr           (_memSys_io_spriteFrameBuffer_addr),
    .io_ddr_mask           (_memSys_io_spriteFrameBuffer_mask),
    .io_ddr_din            (_memSys_io_spriteFrameBuffer_din),
    .io_ddr_dout           (_memSys_io_spriteFrameBuffer_dout),
    .io_ddr_wait_n         (_memSys_io_spriteFrameBuffer_wait_n),
    .io_ddr_valid          (_memSys_io_spriteFrameBuffer_valid),
    .io_ddr_burstLength    (_memSys_io_spriteFrameBuffer_burstLength),
    .io_ddr_burstDone      (_memSys_io_spriteFrameBuffer_burstDone)
  );
  assign _systemFrameBuffer_io_forceBlank = ~_memSys_io_ready;
  SystemFrameBuffer systemFrameBuffer (
    .clock                         (clock),
    .reset                         (reset),
    .io_videoClock                 (videoClock),
    .io_enable                     (_memSys_io_ready),
    .io_rotate                     (options_rotate),
    .io_forceBlank                 (_systemFrameBuffer_io_forceBlank),
    .io_video_vBlank               (_videoSys_io_video_vBlank),
    .io_video_regs_size_x          (_videoSys_io_video_regs_size_x),
    .io_video_regs_size_y          (_videoSys_io_video_regs_size_y),
    .io_frameBufferCtrl_enable     (frameBufferCtrl_enable),
    .io_frameBufferCtrl_hSize      (frameBufferCtrl_hSize),
    .io_frameBufferCtrl_vSize      (frameBufferCtrl_vSize),
    .io_frameBufferCtrl_baseAddr   (frameBufferCtrl_baseAddr),
    .io_frameBufferCtrl_stride     (frameBufferCtrl_stride),
    .io_frameBufferCtrl_vBlank     (frameBufferCtrl_vBlank),
    .io_frameBufferCtrl_lowLat     (frameBufferCtrl_lowLat),
    .io_frameBufferCtrl_forceBlank (frameBufferCtrl_forceBlank),
    .io_frameBuffer_wr             (_gpu_io_systemFrameBuffer_wr),
    .io_frameBuffer_addr           (_gpu_io_systemFrameBuffer_addr),
    .io_frameBuffer_din            (_gpu_io_systemFrameBuffer_din),
    .io_ddr_wr                     (_memSys_io_systemFrameBuffer_wr),
    .io_ddr_addr                   (_memSys_io_systemFrameBuffer_addr),
    .io_ddr_mask                   (_memSys_io_systemFrameBuffer_mask),
    .io_ddr_din                    (_memSys_io_systemFrameBuffer_din),
    .io_ddr_wait_n                 (_memSys_io_systemFrameBuffer_wait_n)
  );
  assign ioctl_wait_n = videoSys_io_prog_video_writeEnable | _GEN_5;
  assign ioctl_din =
    memSys_io_prog_nvram_readEnable ? memSys_io_prog_nvram_ioctl_din_r : 16'h0;
  assign led_power = 1'h0;
  assign led_disk = ioctl_download;
  assign led_user = _memSys_io_ready;
  assign frameBufferCtrl_format = 5'h6;
  assign video_clockEnable = _videoSys_io_video_clockEnable;
  assign video_displayEnable = _videoSys_io_video_displayEnable;
  assign video_pos_x = _videoSys_io_video_pos_x;
  assign video_pos_y = _videoSys_io_video_pos_y;
  assign video_hBlank = _videoSys_io_video_hBlank;
  assign video_vBlank = _videoSys_io_video_vBlank;
  assign video_regs_size_x = _videoSys_io_video_regs_size_x;
  assign video_regs_size_y = _videoSys_io_video_regs_size_y;
  assign sdram_cke = 1'h1;
endmodule

