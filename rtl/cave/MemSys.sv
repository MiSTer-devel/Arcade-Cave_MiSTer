// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Memory subsystem routing for ROM download, cached ROM reads, NVRAM, SDRAM, and DDR.
module MemSys(
  input         clock,
  input         reset,
  input  [3:0]  io_gameIndex,
  input  [31:0] io_gameConfig_eepromOffset,
  input  [31:0] io_gameConfig_sound_0_romOffset,
  input  [31:0] io_gameConfig_sound_1_romOffset,
  input  [31:0] io_gameConfig_layer_0_romOffset,
  input  [31:0] io_gameConfig_layer_1_romOffset,
  input  [31:0] io_gameConfig_layer_2_romOffset,
  input  [31:0] io_gameConfig_sprite_romOffset,
  input         io_prog_rom_wr,
  input  [26:0] io_prog_rom_addr,
  input  [15:0] io_prog_rom_din,
  output        io_prog_rom_wait_n,
  input         io_prog_nvram_rd,
  input         io_prog_nvram_wr,
  input  [26:0] io_prog_nvram_addr,
  input  [15:0] io_prog_nvram_din,
  output [15:0] io_prog_nvram_dout,
  output        io_prog_nvram_wait_n,
  output        io_prog_nvram_valid,
  input         io_prog_done,
  input         io_progRom_rd,
  input  [19:0] io_progRom_addr,
  output [15:0] io_progRom_dout,
  output        io_progRom_wait_n,
  output        io_progRom_valid,
  input         io_eeprom_rd,
  input         io_eeprom_wr,
  input  [6:0]  io_eeprom_addr,
  input  [15:0] io_eeprom_din,
  output [15:0] io_eeprom_dout,
  output        io_eeprom_wait_n,
  output        io_eeprom_valid,
  input         io_soundRom_0_rd,
  input  [24:0] io_soundRom_0_addr,
  output [7:0]  io_soundRom_0_dout,
  output        io_soundRom_0_wait_n,
  output        io_soundRom_0_valid,
  input         io_soundRom_1_rd,
  input  [24:0] io_soundRom_1_addr,
  output [7:0]  io_soundRom_1_dout,
  output        io_soundRom_1_wait_n,
  output        io_soundRom_1_valid,
  input         io_layerTileRom_0_rd,
  input  [31:0] io_layerTileRom_0_addr,
  output [63:0] io_layerTileRom_0_dout,
  output        io_layerTileRom_0_wait_n,
  output        io_layerTileRom_0_valid,
  input         io_layerTileRom_1_rd,
  input  [31:0] io_layerTileRom_1_addr,
  output [63:0] io_layerTileRom_1_dout,
  output        io_layerTileRom_1_wait_n,
  output        io_layerTileRom_1_valid,
  input         io_layerTileRom_2_rd,
  input  [31:0] io_layerTileRom_2_addr,
  output [63:0] io_layerTileRom_2_dout,
  output        io_layerTileRom_2_wait_n,
  output        io_layerTileRom_2_valid,
  input         io_spriteTileRom_rd,
  input  [31:0] io_spriteTileRom_addr,
  output [63:0] io_spriteTileRom_dout,
  output        io_spriteTileRom_wait_n,
  output        io_spriteTileRom_valid,
  input  [7:0]  io_spriteTileRom_burstLength,
  output        io_spriteTileRom_burstDone,
  output        io_ddr_rd,
  output        io_ddr_wr,
  output [31:0] io_ddr_addr,
  output [7:0]  io_ddr_mask,
  output [63:0] io_ddr_din,
  input  [63:0] io_ddr_dout,
  input         io_ddr_wait_n,
  input         io_ddr_valid,
  output [7:0]  io_ddr_burstLength,
  input         io_ddr_burstDone,
  output        io_sdram_rd,
  output        io_sdram_wr,
  output [24:0] io_sdram_addr,
  output [15:0] io_sdram_din,
  input  [15:0] io_sdram_dout,
  input         io_sdram_wait_n,
  input         io_sdram_valid,
  input         io_sdram_burstDone,
  input         io_spriteFrameBuffer_rd,
  input         io_spriteFrameBuffer_wr,
  input  [31:0] io_spriteFrameBuffer_addr,
  input  [7:0]  io_spriteFrameBuffer_mask,
  input  [63:0] io_spriteFrameBuffer_din,
  output [63:0] io_spriteFrameBuffer_dout,
  output        io_spriteFrameBuffer_wait_n,
  output        io_spriteFrameBuffer_valid,
  input  [7:0]  io_spriteFrameBuffer_burstLength,
  output        io_spriteFrameBuffer_burstDone,
  input         io_systemFrameBuffer_wr,
  input  [31:0] io_systemFrameBuffer_addr,
  input  [7:0]  io_systemFrameBuffer_mask,
  input  [63:0] io_systemFrameBuffer_din,
  output        io_systemFrameBuffer_wait_n,
  output        io_ready
);
  localparam [31:0] IOCTL_DOWNLOAD_BASE_ADDR = 32'h3000_0000;
  localparam [31:0] MAZINGER_SPRITE_DECRYPT_OFFSET = 32'h0100_0000;

  reg readyEnableReg;
  reg copyDmaBusyReg;
  reg copyDmaStartedReg;
  reg copyDmaDoneReg;
  reg decryptDmaBusyReg;
  reg decryptDmaStartedReg;
  reg decryptDmaDoneReg;

  wire        ddrDownloadBufferOutWr;
  wire [31:0] ddrDownloadBufferOutAddr;
  wire [63:0] ddrDownloadBufferOutDin;
  wire        ddrDownloadBufferOutBurstDone;

  wire        sdramDownloadBufferInWr;
  wire [31:0] sdramDownloadBufferInAddr;
  wire [63:0] sdramDownloadBufferInDin;
  wire        sdramDownloadBufferInWaitN;
  wire        sdramDownloadBufferOutWr;
  wire [24:0] sdramDownloadBufferOutAddr;
  wire [15:0] sdramDownloadBufferOutDin;
  wire        sdramDownloadBufferOutWaitN;
  wire        sdramDownloadBufferOutBurstDone;

  wire        copyDmaBusy;
  wire        copyDmaInRd;
  wire [31:0] copyDmaInAddr;
  wire [63:0] copyDmaInDout;
  wire        copyDmaInWaitN;
  wire        copyDmaInValid;
  wire        copyDmaInBurstDone;
  wire        copyDmaDone = ~copyDmaBusy & copyDmaBusyReg;

  wire        decryptDmaBusy;
  wire        decryptDmaRd;
  wire        decryptDmaWr;
  wire [31:0] decryptDmaAddr;
  wire [7:0]  decryptDmaMask;
  wire [63:0] decryptDmaDin;
  wire [63:0] decryptDmaDout;
  wire        decryptDmaWaitN;
  wire        decryptDmaValid;
  wire [7:0]  decryptDmaBurstLength;
  wire        decryptDmaBurstDone;
  wire        decryptDmaDone = ~decryptDmaBusy & decryptDmaBusyReg;

  wire        progRomCacheOutRd;
  wire [24:0] progRomCacheOutAddr;
  wire [15:0] progRomCacheOutDout;
  wire        progRomCacheOutWaitN;
  wire        progRomCacheOutValid;

  wire        eepromCacheInRd;
  wire        eepromCacheInWr;
  wire [6:0]  eepromCacheInAddr;
  wire [15:0] eepromCacheInDin;
  wire [15:0] eepromCacheInDout;
  wire        eepromCacheInWaitN;
  wire        eepromCacheInValid;
  wire        eepromCacheOutRd;
  wire        eepromCacheOutWr;
  wire [24:0] eepromCacheOutAddr;
  wire [15:0] eepromCacheOutDin;
  wire [15:0] eepromCacheOutDout;
  wire        eepromCacheOutWaitN;
  wire        eepromCacheOutValid;

  wire        soundRomCache0OutRd;
  wire [24:0] soundRomCache0OutAddr;
  wire [15:0] soundRomCache0OutDout;
  wire        soundRomCache0OutWaitN;
  wire        soundRomCache0OutValid;
  wire        soundRomCache1OutRd;
  wire [24:0] soundRomCache1OutAddr;
  wire [15:0] soundRomCache1OutDout;
  wire        soundRomCache1OutWaitN;
  wire        soundRomCache1OutValid;

  wire        layerRomCache0OutRd;
  wire [24:0] layerRomCache0OutAddr;
  wire [15:0] layerRomCache0OutDout;
  wire        layerRomCache0OutWaitN;
  wire        layerRomCache0OutValid;
  wire        layerRomCache1OutRd;
  wire [24:0] layerRomCache1OutAddr;
  wire [15:0] layerRomCache1OutDout;
  wire        layerRomCache1OutWaitN;
  wire        layerRomCache1OutValid;
  wire        layerRomCache2OutRd;
  wire [24:0] layerRomCache2OutAddr;
  wire [15:0] layerRomCache2OutDout;
  wire        layerRomCache2OutWaitN;
  wire        layerRomCache2OutValid;

  wire        gameIsMazinger;
  wire        copyDmaStart = io_prog_done & ~copyDmaStartedReg;
  wire        decryptDmaStart = copyDmaDoneReg & gameIsMazinger & ~decryptDmaStartedReg;
  wire [31:0] ddrDownloadAddr = ddrDownloadBufferOutAddr + IOCTL_DOWNLOAD_BASE_ADDR;
  wire [31:0] ddrCopyDmaAddr = copyDmaInAddr + IOCTL_DOWNLOAD_BASE_ADDR;
  wire [31:0] spriteRomReadOffset =
    gameIsMazinger ? MAZINGER_SPRITE_DECRYPT_OFFSET : io_gameConfig_sprite_romOffset;
  wire [31:0] spriteTileRomLocalAddr =
    gameIsMazinger ? {10'h000, io_spriteTileRom_addr[21:0]} : io_spriteTileRom_addr;
  wire [31:0] ddrSpriteTileRomAddr =
    spriteTileRomLocalAddr + (spriteRomReadOffset + IOCTL_DOWNLOAD_BASE_ADDR);

  wire [24:0] eepromSdramAddr =
    eepromCacheOutAddr + io_gameConfig_eepromOffset[24:0];
  wire [24:0] soundRom0SdramAddr =
    soundRomCache0OutAddr + io_gameConfig_sound_0_romOffset[24:0];
  wire [24:0] soundRom1SdramAddr =
    soundRomCache1OutAddr + io_gameConfig_sound_1_romOffset[24:0];
  wire [24:0] layerRom0SdramAddr =
    layerRomCache0OutAddr + io_gameConfig_layer_0_romOffset[24:0];
  wire [24:0] layerRom1SdramAddr =
    layerRomCache1OutAddr + io_gameConfig_layer_1_romOffset[24:0];
  wire [24:0] layerRom2SdramAddr =
    layerRomCache2OutAddr + io_gameConfig_layer_2_romOffset[24:0];

  CaveBoardProfile boardProfile(
    .game_index                  (io_gameIndex),
    .sound_device                (2'd0),
    .game_is_dfeveron            (),
    .game_is_dodonpachi          (),
    .game_is_donpachi            (),
    .game_is_esprade             (),
    .game_is_uopoko              (),
    .game_is_guwange             (),
    .game_is_gaia                (),
    .game_is_hotdogstorm         (),
    .game_is_mazinger            (gameIsMazinger),
    .board_uses_z80_sound        (),
    .board_is_vertical_clockwise (),
    .sound_is_ymz280b            (),
    .sound_is_oki                (),
    .sound_is_z80                ()
  );

  always @(posedge clock) begin
    copyDmaBusyReg <= copyDmaBusy;
    decryptDmaBusyReg <= decryptDmaBusy;
    if (reset) begin
      readyEnableReg <= 1'b0;
      copyDmaBusyReg <= 1'b0;
      copyDmaStartedReg <= 1'b0;
      copyDmaDoneReg <= 1'b0;
      decryptDmaBusyReg <= 1'b0;
      decryptDmaStartedReg <= 1'b0;
      decryptDmaDoneReg <= 1'b0;
    end
    else begin
      if (copyDmaStart)
        copyDmaStartedReg <= 1'b1;
      if (copyDmaDone)
        copyDmaDoneReg <= 1'b1;
      if (decryptDmaStart)
        decryptDmaStartedReg <= 1'b1;
      if (decryptDmaDone)
        decryptDmaDoneReg <= 1'b1;

      readyEnableReg <=
        readyEnableReg | (copyDmaDoneReg & (~gameIsMazinger | decryptDmaDoneReg));
    end
  end

  CaveDdrDownloadBurstBuffer ddrDownloadBuffer (
    .clock            (clock),
    .reset            (reset),
    .io_in_wr         (io_prog_rom_wr),
    .io_in_addr       (io_prog_rom_addr),
    .io_in_din        (io_prog_rom_din),
    .io_out_wr        (ddrDownloadBufferOutWr),
    .io_out_addr      (ddrDownloadBufferOutAddr),
    .io_out_din       (ddrDownloadBufferOutDin),
    .io_out_burstDone (ddrDownloadBufferOutBurstDone)
  );

  CaveSdramDownloadBurstBuffer sdramDownloadBuffer (
    .clock            (clock),
    .reset            (reset),
    .io_in_wr         (sdramDownloadBufferInWr),
    .io_in_addr       (sdramDownloadBufferInAddr),
    .io_in_din        (sdramDownloadBufferInDin),
    .io_in_wait_n     (sdramDownloadBufferInWaitN),
    .io_out_wr        (sdramDownloadBufferOutWr),
    .io_out_addr      (sdramDownloadBufferOutAddr),
    .io_out_din       (sdramDownloadBufferOutDin),
    .io_out_wait_n    (sdramDownloadBufferOutWaitN),
    .io_out_burstDone (sdramDownloadBufferOutBurstDone)
  );

  CaveRomCopyDma copyDma (
    .clock           (clock),
    .reset           (reset),
    .io_start        (copyDmaStart),
    .io_busy         (copyDmaBusy),
    .io_in_rd        (copyDmaInRd),
    .io_in_addr      (copyDmaInAddr),
    .io_in_dout      (copyDmaInDout),
    .io_in_wait_n    (copyDmaInWaitN),
    .io_in_valid     (copyDmaInValid),
    .io_in_burstDone (copyDmaInBurstDone),
    .io_out_wr       (sdramDownloadBufferInWr),
    .io_out_addr     (sdramDownloadBufferInAddr),
    .io_out_din      (sdramDownloadBufferInDin),
    .io_out_wait_n   (sdramDownloadBufferInWaitN)
  );

  MazingerSpriteDecryptDMA mazingerSpriteDecryptDma (
    .clock                (clock),
    .reset                (reset),
    .io_start             (decryptDmaStart),
    .io_busy              (decryptDmaBusy),
    .io_src_base          (io_gameConfig_sprite_romOffset + IOCTL_DOWNLOAD_BASE_ADDR),
    .io_dst_base          (MAZINGER_SPRITE_DECRYPT_OFFSET + IOCTL_DOWNLOAD_BASE_ADDR),
    .io_mem_rd            (decryptDmaRd),
    .io_mem_wr            (decryptDmaWr),
    .io_mem_addr          (decryptDmaAddr),
    .io_mem_mask          (decryptDmaMask),
    .io_mem_din           (decryptDmaDin),
    .io_mem_dout          (decryptDmaDout),
    .io_mem_wait_n        (decryptDmaWaitN),
    .io_mem_valid         (decryptDmaValid),
    .io_mem_burstLength   (decryptDmaBurstLength),
    .io_mem_burstDone     (decryptDmaBurstDone)
  );

  CaveReadCache #(
    .IN_ADDR_WIDTH  (20),
    .IN_DATA_WIDTH  (16),
    .OUT_ADDR_WIDTH (25),
    .INDEX_WIDTH    (7),
    .TAG_WIDTH      (11)
  ) progRomCache (
    .clock         (clock),
    .reset         (reset),
    .io_enable     (readyEnableReg),
    .io_in_rd      (io_progRom_rd),
    .io_in_addr    (io_progRom_addr),
    .io_in_dout    (io_progRom_dout),
    .io_in_wait_n  (io_progRom_wait_n),
    .io_in_valid   (io_progRom_valid),
    .io_out_rd     (progRomCacheOutRd),
    .io_out_addr   (progRomCacheOutAddr),
    .io_out_dout   (progRomCacheOutDout),
    .io_out_wait_n (progRomCacheOutWaitN),
    .io_out_valid  (progRomCacheOutValid)
  );

  CaveNvramWriteBackCache eepromCache (
    .clock         (clock),
    .reset         (reset),
    .io_enable     (readyEnableReg),
    .io_in_rd      (eepromCacheInRd),
    .io_in_wr      (eepromCacheInWr),
    .io_in_addr    (eepromCacheInAddr),
    .io_in_din     (eepromCacheInDin),
    .io_in_dout    (eepromCacheInDout),
    .io_in_wait_n  (eepromCacheInWaitN),
    .io_in_valid   (eepromCacheInValid),
    .io_out_rd     (eepromCacheOutRd),
    .io_out_wr     (eepromCacheOutWr),
    .io_out_addr   (eepromCacheOutAddr),
    .io_out_din    (eepromCacheOutDin),
    .io_out_dout   (eepromCacheOutDout),
    .io_out_wait_n (eepromCacheOutWaitN),
    .io_out_valid  (eepromCacheOutValid)
  );

  CaveReadCache #(
    .IN_ADDR_WIDTH  (25),
    .IN_DATA_WIDTH  (8),
    .OUT_ADDR_WIDTH (25),
    .INDEX_WIDTH    (2),
    .TAG_WIDTH      (21)
  ) soundRomCache_0 (
    .clock         (clock),
    .reset         (reset),
    .io_enable     (readyEnableReg),
    .io_in_rd      (io_soundRom_0_rd),
    .io_in_addr    (io_soundRom_0_addr),
    .io_in_dout    (io_soundRom_0_dout),
    .io_in_wait_n  (io_soundRom_0_wait_n),
    .io_in_valid   (io_soundRom_0_valid),
    .io_out_rd     (soundRomCache0OutRd),
    .io_out_addr   (soundRomCache0OutAddr),
    .io_out_dout   (soundRomCache0OutDout),
    .io_out_wait_n (soundRomCache0OutWaitN),
    .io_out_valid  (soundRomCache0OutValid)
  );

  CaveReadCache #(
    .IN_ADDR_WIDTH  (25),
    .IN_DATA_WIDTH  (8),
    .OUT_ADDR_WIDTH (25),
    .INDEX_WIDTH    (2),
    .TAG_WIDTH      (21)
  ) soundRomCache_1 (
    .clock         (clock),
    .reset         (reset),
    .io_enable     (readyEnableReg),
    .io_in_rd      (io_soundRom_1_rd),
    .io_in_addr    (io_soundRom_1_addr),
    .io_in_dout    (io_soundRom_1_dout),
    .io_in_wait_n  (io_soundRom_1_wait_n),
    .io_in_valid   (io_soundRom_1_valid),
    .io_out_rd     (soundRomCache1OutRd),
    .io_out_addr   (soundRomCache1OutAddr),
    .io_out_dout   (soundRomCache1OutDout),
    .io_out_wait_n (soundRomCache1OutWaitN),
    .io_out_valid  (soundRomCache1OutValid)
  );

  CaveReadCache #(
    .IN_ADDR_WIDTH  (32),
    .IN_DATA_WIDTH  (64),
    .OUT_ADDR_WIDTH (25),
    .INDEX_WIDTH    (3),
    .TAG_WIDTH      (27)
  ) layerRomCache_0 (
    .clock         (clock),
    .reset         (reset),
    .io_enable     (readyEnableReg),
    .io_in_rd      (io_layerTileRom_0_rd),
    .io_in_addr    (io_layerTileRom_0_addr),
    .io_in_dout    (io_layerTileRom_0_dout),
    .io_in_wait_n  (io_layerTileRom_0_wait_n),
    .io_in_valid   (io_layerTileRom_0_valid),
    .io_out_rd     (layerRomCache0OutRd),
    .io_out_addr   (layerRomCache0OutAddr),
    .io_out_dout   (layerRomCache0OutDout),
    .io_out_wait_n (layerRomCache0OutWaitN),
    .io_out_valid  (layerRomCache0OutValid)
  );

  CaveReadCache #(
    .IN_ADDR_WIDTH  (32),
    .IN_DATA_WIDTH  (64),
    .OUT_ADDR_WIDTH (25),
    .INDEX_WIDTH    (3),
    .TAG_WIDTH      (27)
  ) layerRomCache_1 (
    .clock         (clock),
    .reset         (reset),
    .io_enable     (readyEnableReg),
    .io_in_rd      (io_layerTileRom_1_rd),
    .io_in_addr    (io_layerTileRom_1_addr),
    .io_in_dout    (io_layerTileRom_1_dout),
    .io_in_wait_n  (io_layerTileRom_1_wait_n),
    .io_in_valid   (io_layerTileRom_1_valid),
    .io_out_rd     (layerRomCache1OutRd),
    .io_out_addr   (layerRomCache1OutAddr),
    .io_out_dout   (layerRomCache1OutDout),
    .io_out_wait_n (layerRomCache1OutWaitN),
    .io_out_valid  (layerRomCache1OutValid)
  );

  CaveReadCache #(
    .IN_ADDR_WIDTH  (32),
    .IN_DATA_WIDTH  (64),
    .OUT_ADDR_WIDTH (25),
    .INDEX_WIDTH    (3),
    .TAG_WIDTH      (27)
  ) layerRomCache_2 (
    .clock         (clock),
    .reset         (reset),
    .io_enable     (readyEnableReg),
    .io_in_rd      (io_layerTileRom_2_rd),
    .io_in_addr    (io_layerTileRom_2_addr),
    .io_in_dout    (io_layerTileRom_2_dout),
    .io_in_wait_n  (io_layerTileRom_2_wait_n),
    .io_in_valid   (io_layerTileRom_2_valid),
    .io_out_rd     (layerRomCache2OutRd),
    .io_out_addr   (layerRomCache2OutAddr),
    .io_out_dout   (layerRomCache2OutDout),
    .io_out_wait_n (layerRomCache2OutWaitN),
    .io_out_valid  (layerRomCache2OutValid)
  );

  CaveMainDdrArbiter ddrArbiter (
    .clock               (clock),
    .reset               (reset),
    .io_in_0_wr          (ddrDownloadBufferOutWr),
    .io_in_0_addr        (ddrDownloadAddr),
    .io_in_0_din         (ddrDownloadBufferOutDin),
    .io_in_0_burstDone   (ddrDownloadBufferOutBurstDone),
    .io_in_1_rd          (copyDmaInRd),
    .io_in_1_addr        (ddrCopyDmaAddr),
    .io_in_1_dout        (copyDmaInDout),
    .io_in_1_wait_n      (copyDmaInWaitN),
    .io_in_1_valid       (copyDmaInValid),
    .io_in_1_burstDone   (copyDmaInBurstDone),
    .io_in_2_wr          (io_systemFrameBuffer_wr),
    .io_in_2_addr        (io_systemFrameBuffer_addr),
    .io_in_2_mask        (io_systemFrameBuffer_mask),
    .io_in_2_din         (io_systemFrameBuffer_din),
    .io_in_2_wait_n      (io_systemFrameBuffer_wait_n),
    .io_in_3_rd          (io_spriteFrameBuffer_rd),
    .io_in_3_wr          (io_spriteFrameBuffer_wr),
    .io_in_3_addr        (io_spriteFrameBuffer_addr),
    .io_in_3_mask        (io_spriteFrameBuffer_mask),
    .io_in_3_din         (io_spriteFrameBuffer_din),
    .io_in_3_dout        (io_spriteFrameBuffer_dout),
    .io_in_3_wait_n      (io_spriteFrameBuffer_wait_n),
    .io_in_3_valid       (io_spriteFrameBuffer_valid),
    .io_in_3_burstLength (io_spriteFrameBuffer_burstLength),
    .io_in_3_burstDone   (io_spriteFrameBuffer_burstDone),
    .io_in_4_rd          (io_spriteTileRom_rd),
    .io_in_4_addr        (ddrSpriteTileRomAddr),
    .io_in_4_dout        (io_spriteTileRom_dout),
    .io_in_4_wait_n      (io_spriteTileRom_wait_n),
    .io_in_4_valid       (io_spriteTileRom_valid),
    .io_in_4_burstLength (io_spriteTileRom_burstLength),
    .io_in_4_burstDone   (io_spriteTileRom_burstDone),
    .io_in_5_rd          (decryptDmaRd),
    .io_in_5_wr          (decryptDmaWr),
    .io_in_5_addr        (decryptDmaAddr),
    .io_in_5_mask        (decryptDmaMask),
    .io_in_5_din         (decryptDmaDin),
    .io_in_5_dout        (decryptDmaDout),
    .io_in_5_wait_n      (decryptDmaWaitN),
    .io_in_5_valid       (decryptDmaValid),
    .io_in_5_burstLength (decryptDmaBurstLength),
    .io_in_5_burstDone   (decryptDmaBurstDone),
    .io_out_rd           (io_ddr_rd),
    .io_out_wr           (io_ddr_wr),
    .io_out_addr         (io_ddr_addr),
    .io_out_mask         (io_ddr_mask),
    .io_out_din          (io_ddr_din),
    .io_out_dout         (io_ddr_dout),
    .io_out_wait_n       (io_ddr_wait_n),
    .io_out_valid        (io_ddr_valid),
    .io_out_burstLength  (io_ddr_burstLength),
    .io_out_burstDone    (io_ddr_burstDone)
  );

  CaveMainSdramArbiter sdramArbiter (
    .clock             (clock),
    .reset             (reset),
    .io_in_0_wr        (sdramDownloadBufferOutWr),
    .io_in_0_addr      (sdramDownloadBufferOutAddr),
    .io_in_0_din       (sdramDownloadBufferOutDin),
    .io_in_0_wait_n    (sdramDownloadBufferOutWaitN),
    .io_in_0_burstDone (sdramDownloadBufferOutBurstDone),
    .io_in_1_rd        (progRomCacheOutRd),
    .io_in_1_addr      (progRomCacheOutAddr),
    .io_in_1_dout      (progRomCacheOutDout),
    .io_in_1_wait_n    (progRomCacheOutWaitN),
    .io_in_1_valid     (progRomCacheOutValid),
    .io_in_2_rd        (eepromCacheOutRd),
    .io_in_2_wr        (eepromCacheOutWr),
    .io_in_2_addr      (eepromSdramAddr),
    .io_in_2_din       (eepromCacheOutDin),
    .io_in_2_dout      (eepromCacheOutDout),
    .io_in_2_wait_n    (eepromCacheOutWaitN),
    .io_in_2_valid     (eepromCacheOutValid),
    .io_in_3_rd        (soundRomCache0OutRd),
    .io_in_3_addr      (soundRom0SdramAddr),
    .io_in_3_dout      (soundRomCache0OutDout),
    .io_in_3_wait_n    (soundRomCache0OutWaitN),
    .io_in_3_valid     (soundRomCache0OutValid),
    .io_in_4_rd        (soundRomCache1OutRd),
    .io_in_4_addr      (soundRom1SdramAddr),
    .io_in_4_dout      (soundRomCache1OutDout),
    .io_in_4_wait_n    (soundRomCache1OutWaitN),
    .io_in_4_valid     (soundRomCache1OutValid),
    .io_in_5_rd        (layerRomCache0OutRd),
    .io_in_5_addr      (layerRom0SdramAddr),
    .io_in_5_dout      (layerRomCache0OutDout),
    .io_in_5_wait_n    (layerRomCache0OutWaitN),
    .io_in_5_valid     (layerRomCache0OutValid),
    .io_in_6_rd        (layerRomCache1OutRd),
    .io_in_6_addr      (layerRom1SdramAddr),
    .io_in_6_dout      (layerRomCache1OutDout),
    .io_in_6_wait_n    (layerRomCache1OutWaitN),
    .io_in_6_valid     (layerRomCache1OutValid),
    .io_in_7_rd        (layerRomCache2OutRd),
    .io_in_7_addr      (layerRom2SdramAddr),
    .io_in_7_dout      (layerRomCache2OutDout),
    .io_in_7_wait_n    (layerRomCache2OutWaitN),
    .io_in_7_valid     (layerRomCache2OutValid),
    .io_out_rd         (io_sdram_rd),
    .io_out_wr         (io_sdram_wr),
    .io_out_addr       (io_sdram_addr),
    .io_out_din        (io_sdram_din),
    .io_out_dout       (io_sdram_dout),
    .io_out_wait_n     (io_sdram_wait_n),
    .io_out_valid      (io_sdram_valid),
    .io_out_burstDone  (io_sdram_burstDone)
  );

  CaveNvramEepromArbiter nvramArbiter (
    .clock          (clock),
    .reset          (reset),
    .io_in_0_rd     (io_prog_nvram_rd),
    .io_in_0_wr     (io_prog_nvram_wr),
    .io_in_0_addr   (io_prog_nvram_addr[6:0]),
    .io_in_0_din    (io_prog_nvram_din),
    .io_in_0_dout   (io_prog_nvram_dout),
    .io_in_0_wait_n (io_prog_nvram_wait_n),
    .io_in_0_valid  (io_prog_nvram_valid),
    .io_in_1_rd     (io_eeprom_rd),
    .io_in_1_wr     (io_eeprom_wr),
    .io_in_1_addr   (io_eeprom_addr),
    .io_in_1_din    (io_eeprom_din),
    .io_in_1_dout   (io_eeprom_dout),
    .io_in_1_wait_n (io_eeprom_wait_n),
    .io_in_1_valid  (io_eeprom_valid),
    .io_out_rd      (eepromCacheInRd),
    .io_out_wr      (eepromCacheInWr),
    .io_out_addr    (eepromCacheInAddr),
    .io_out_din     (eepromCacheInDin),
    .io_out_dout    (eepromCacheInDout),
    .io_out_wait_n  (eepromCacheInWaitN),
    .io_out_valid   (eepromCacheInValid)
  );

  assign io_prog_rom_wait_n = sdramDownloadBufferInWaitN;
  assign io_ready = readyEnableReg;
endmodule
